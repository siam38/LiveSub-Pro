package com.charles.livecaptionn.translation.fast

import com.squareup.moshi.Moshi
import kotlinx.coroutines.delay
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

// Free Google Translate web endpoint (client=gtx), as used by BTE.
// Many lines go in ONE request joined by a split token — that batching is
// most of why it feels instant on live captions.
class GoogleFastEngine(private val client: OkHttpClient) : FastTranslateEngine {

    override val name = "google"
    override val maxItemsPerRequest = 40
    override val maxCharsPerRequest = 3500
    override val reportsErrors = true

    val pacer = EnginePacer(baseIntervalMs = 130, maxIntervalMs = 2500)

    @Volatile private var cooldownUntil = 0L
    @Volatile private var rateLimitStreak = 0
    @Volatile var lastError: String? = null
        private set

    private val anyAdapter = Moshi.Builder().build().adapter(Any::class.java)

    override suspend fun warmUp() {
        // Preconnect only: opens DNS + TLS + a pooled connection so the first
        // real caption request skips the handshake.
        runCatching {
            client.newCall(
                Request.Builder().url("https://translate.googleapis.com/").get().build()
            ).awaitResponse().close()
        }
    }

    override suspend fun translateBatch(
        texts: List<String>,
        sourceCode: String,
        targetCode: String
    ): EngineBatchResult {
        if (texts.isEmpty()) return EngineBatchResult(emptyList())
        if (System.currentTimeMillis() < cooldownUntil) {
            throw FastEngineException("Google Translate is rate limited (cooldown)", status = 429)
        }
        val src = FastLang.google(sourceCode)
        val dst = FastLang.google(targetCode)
        val output = mutableListOf<String?>()
        var failedGroups = 0
        var lastFailure: Throwable? = null
        val groups = buildGroups(texts)
        for (group in groups) {
            if (System.currentTimeMillis() < cooldownUntil) {
                failedGroups++
                output.addAll(List(group.size) { null })
                continue
            }
            try {
                output.addAll(pacer.schedule { translateGroup(group, src, dst) })
            } catch (t: Throwable) {
                failedGroups++
                lastFailure = t
                output.addAll(List(group.size) { null })
            }
        }
        if (failedGroups > 0 && failedGroups == groups.size) {
            throw lastFailure ?: FastEngineException("Google Translate failed")
        }
        return EngineBatchResult(output, partialFailure = failedGroups > 0)
    }

    private fun buildGroups(texts: List<String>): List<List<String>> {
        val groups = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        var bytes = 0
        val separatorBytes = encodedSize(SEPARATOR)
        for (text in texts) {
            val addition = encodedSize(text) + separatorBytes
            if (current.isNotEmpty() &&
                (current.size >= maxItemsPerRequest || bytes + addition > MAX_ENCODED_QUERY_BYTES)
            ) {
                groups.add(current)
                current = mutableListOf()
                bytes = 0
            }
            current.add(text)
            bytes += addition
        }
        if (current.isNotEmpty()) groups.add(current)
        return groups
    }

    private suspend fun translateGroup(texts: List<String>, src: String, dst: String): List<String?> {
        if (texts.size == 1) {
            try {
                val single = requestTranslation(texts[0], src, dst)
                return listOf(single?.trim()?.takeIf { it.isNotEmpty() && it != texts[0].trim() })
            } catch (e: FastEngineException) {
                if (e.tooLong) return listOf(null)
                throw e
            }
        }
        var joined: String? = null
        try {
            joined = requestTranslation(texts.joinToString(SEPARATOR), src, dst)
        } catch (e: FastEngineException) {
            if (!e.tooLong) throw e
        }
        val parts = joined?.let { splitJoined(it, texts.size) }
        if (parts != null) {
            return parts.mapIndexed { i, part ->
                part.trim().takeIf { it.isNotEmpty() && it != texts[i].trim() }
            }
        }
        // Split token got mangled or the request was too long: halve and retry.
        val middle = (texts.size + 1) / 2
        val left = translateGroup(texts.subList(0, middle), src, dst)
        delay(130)
        val right = translateGroup(texts.subList(middle, texts.size), src, dst)
        return left + right
    }

    private suspend fun requestTranslation(text: String, src: String, dst: String): String? {
        val params = "client=gtx&sl=${URLEncoder.encode(src, "UTF-8")}" +
            "&tl=${URLEncoder.encode(dst, "UTF-8")}&dt=t"
        val query = "q=${URLEncoder.encode(text, "UTF-8")}"
        val usePost = query.length > MAX_ENCODED_QUERY_BYTES
        val request = if (usePost) {
            Request.Builder()
                .url("$ENDPOINT?$params")
                .post(FormBody.Builder().add("q", text).build())
                .header("User-Agent", USER_AGENT)
                .build()
        } else {
            Request.Builder()
                .url("$ENDPOINT?$params&$query")
                .get()
                .header("User-Agent", USER_AGENT)
                .build()
        }
        val response = try {
            client.newCall(request).awaitResponse()
        } catch (t: Throwable) {
            noteFailure(0)
            throw FastEngineException("Google Translate request failed: ${t.message}", status = 0)
        }
        response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val status = it.code
                if (status == 413 || status == 414 || (status == 400 && (usePost || query.length > 2000))) {
                    lastError = "unavailable"
                    throw FastEngineException("Google Translate rejected the request ($status)", status, tooLong = true)
                }
                val reason = if (status == 429) "rate-limited" else "unavailable"
                noteFailure(status)
                lastError = reason
                throw FastEngineException("Google Translate request failed ($status)", status)
            }
            val data = try {
                anyAdapter.fromJson(body)
            } catch (_: Throwable) {
                null
            }
            if (data !is List<*>) {
                // A 200 with a non-JSON body is Google's "unusual traffic" page.
                val captcha = body.contains("unusual traffic", true) || body.contains("recaptcha", true)
                noteFailure(if (captcha) 429 else 503)
                lastError = if (captcha) "captcha" else "unavailable"
                throw FastEngineException(
                    "Google Translate returned an unexpected response",
                    if (captcha) 429 else 503
                )
            }
            noteSuccess()
            return parseResponse(data)
        }
    }

    private fun parseResponse(data: List<*>): String {
        val chunks = data.firstOrNull() as? List<*> ?: return ""
        val merged = StringBuilder()
        for (chunk in chunks) {
            val piece = (chunk as? List<*>)?.firstOrNull() as? String
            if (piece != null) merged.append(piece)
        }
        return merged.toString()
    }

    private fun noteFailure(status: Int) {
        if (status == 429) {
            rateLimitStreak = minOf(rateLimitStreak + 1, 6)
            val cooldown = minOf(
                RATE_LIMIT_COOLDOWN_MAX_MS,
                RATE_LIMIT_COOLDOWN_BASE_MS * (1L shl (rateLimitStreak - 1))
            )
            cooldownUntil = System.currentTimeMillis() + cooldown
        }
        if (status == 429 || status >= 500) pacer.rateLimited()
    }

    private fun noteSuccess() {
        lastError = null
        rateLimitStreak = 0
        cooldownUntil = 0
        pacer.success()
    }

    companion object {
        private const val ENDPOINT = "https://translate.googleapis.com/translate_a/single"
        private const val SEPARATOR = "\n<<<BTE_SPLIT_TOKEN>>>\n"
        private const val MAX_ENCODED_QUERY_BYTES = 6000
        private const val RATE_LIMIT_COOLDOWN_BASE_MS = 20_000L
        private const val RATE_LIMIT_COOLDOWN_MAX_MS = 5 * 60_000L
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"

        private val SPLIT_PATTERN = Regex(
            "\\s*<{2,3}\\s*BTE[\\s_]*SPLIT[\\s_]*TOKEN\\s*>{2,3}\\s*",
            RegexOption.IGNORE_CASE
        )

        internal fun encodedSize(text: String): Int =
            try { URLEncoder.encode(text, "UTF-8").length } catch (_: Throwable) { text.length * 9 }

        internal fun splitJoined(translatedJoined: String, expectedCount: Int): List<String>? {
            val parts = translatedJoined.split(SPLIT_PATTERN)
            if (parts.size == expectedCount) return parts
            val trimmed = parts.filterIndexed { index, part ->
                part.isNotBlank() || (index > 0 && index < parts.size - 1)
            }
            return if (trimmed.size == expectedCount) trimmed else null
        }
    }
}
