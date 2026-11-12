package com.charles.livecaptionn.translation.fast

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

// Ported from BiliBili-To-English (MIT, by LazyScar): the free-web-engine
// layer behind its live captions. See FastTranslationManager for the rest.

class FastEngineException(
    message: String,
    val status: Int = 0,
    val tooLong: Boolean = false
) : Exception(message)

data class EngineBatchResult(
    val outputs: List<String?>,
    val partialFailure: Boolean = false
)

interface FastTranslateEngine {
    val name: String
    val maxItemsPerRequest: Int
    val maxCharsPerRequest: Int
    val reportsErrors: Boolean get() = false

    suspend fun translateBatch(texts: List<String>, sourceCode: String, targetCode: String): EngineBatchResult

    suspend fun warmUp() {}
}

// Adaptive pacing per engine, same idea as BTE's RateGovernor: requests are
// spaced by an interval that doubles when the engine rate-limits and relaxes
// back down as requests succeed.
class EnginePacer(
    private val baseIntervalMs: Long,
    private val maxIntervalMs: Long
) {
    private val paceMutex = Mutex()
    private val inFlight = Semaphore(4)
    private var lastStartMs = 0L

    @Volatile var minIntervalMs: Long = baseIntervalMs
        private set

    @Volatile var consecutiveFailures: Int = 0
        private set

    suspend fun <T> schedule(block: suspend () -> T): T {
        inFlight.withPermit {
            paceMutex.withLock {
                val wait = minIntervalMs - (System.currentTimeMillis() - lastStartMs)
                if (wait > 0) delay(wait)
                lastStartMs = System.currentTimeMillis()
            }
            return block()
        }
    }

    fun rateLimited() {
        consecutiveFailures = minOf(consecutiveFailures + 1, 6)
        minIntervalMs = minOf(maxIntervalMs, maxOf(minIntervalMs * 2, baseIntervalMs * 2))
    }

    fun success() {
        consecutiveFailures = 0
        if (minIntervalMs > baseIntervalMs) {
            val step = maxOf(20L, minIntervalMs / 4)
            minIntervalMs = maxOf(baseIntervalMs, minIntervalMs - step)
        }
    }
}

// Rolling per-engine health, used to order the fallback chain and to park a
// failing engine in cooldown instead of hammering it.
class EngineHealth {
    private var ok = 0.0
    private var fail = 0.0
    private var avgMs = 0L
    private var cooldownUntil = 0L

    @Synchronized
    fun note(success: Boolean, ms: Long) {
        if (ok + fail > 8) {
            ok *= 0.7
            fail *= 0.7
        }
        if (success) {
            ok += 1
            cooldownUntil = 0
            avgMs = if (avgMs > 0) (avgMs * 0.7 + ms * 0.3).toLong() else ms
        } else {
            fail += 1
            cooldownUntil = System.currentTimeMillis() +
                minOf(5 * 60_000L, 15_000L * minOf(fail, 8.0).toLong())
        }
    }

    @Synchronized
    fun score(): Double {
        val inCooldown = System.currentTimeMillis() < cooldownUntil
        val total = ok + fail
        val failRate = if (total > 0) fail / total else 0.0
        val latency = if (avgMs > 0) avgMs.toDouble() else 400.0
        return (if (inCooldown) 1e6 else 0.0) + failRate * 1500 + latency
    }
}

suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { try { cancel() } catch (_: Throwable) {} }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }
    })
}

// Per-engine language code mapping. App codes are base tags ("zh", "yue");
// each free endpoint wants its own flavour.
object FastLang {
    fun base(code: String): String =
        code.trim().lowercase().substringBefore('-').substringBefore('_')

    fun google(code: String): String {
        val c = code.trim().lowercase()
        if (c == "auto") return "auto"
        return when {
            c == "yue" -> "yue"
            c.startsWith("zh") -> if (isTraditional(c)) "zh-TW" else "zh-CN"
            else -> base(c).ifBlank { "auto" }
        }
    }

    fun microsoft(code: String): String {
        val c = code.trim().lowercase()
        if (c.isBlank() || c == "auto" || c == "auto-detect") return ""
        if (c.startsWith("zh")) return if (isTraditional(c)) "zh-Hant" else "zh-Hans"
        return code.trim()
    }

    fun microsoftWebFrom(code: String): String {
        val mapped = microsoft(code)
        return mapped.ifBlank { "auto-detect" }
    }

    fun yandex(code: String): String {
        val c = code.trim().lowercase()
        if (c.isBlank() || c == "auto" || c == "auto-detect") return ""
        return when (base(c)) {
            "zh", "yue" -> "zh"
            else -> base(c)
        }
    }

    private fun isTraditional(c: String): Boolean =
        c.contains("hant") || c.contains("-tw") || c.contains("-hk") || c.contains("-mo") ||
            c.contains("_tw") || c.contains("_hk")
}
