package com.charles.livecaptionn.translation.fast

import com.squareup.moshi.Moshi
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.UUID

// Free Yandex Translate web endpoint (the srv=android one BTE uses).
// Takes several text fields in a single POST natively.
class YandexFastEngine(private val client: OkHttpClient) : FastTranslateEngine {

    override val name = "yandex"
    override val maxItemsPerRequest = 12
    override val maxCharsPerRequest = 2500

    val pacer = EnginePacer(baseIntervalMs = 200, maxIntervalMs = 3000)

    @Volatile var lastError: String? = null
        private set

    @Volatile private var ucid: String = ""
    @Volatile private var ucidCreatedAtMs: Long = 0
    @Volatile private var cooldownUntilMs: Long = 0

    private val anyAdapter = Moshi.Builder().build().adapter(Any::class.java)

    override suspend fun translateBatch(
        texts: List<String>,
        sourceCode: String,
        targetCode: String
    ): EngineBatchResult {
        if (texts.isEmpty()) return EngineBatchResult(emptyList())
        if (System.currentTimeMillis() < cooldownUntilMs) {
            throw FastEngineException("Yandex unavailable (cooldown)", status = 503)
        }
        val target = FastLang.yandex(targetCode).ifBlank { "en" }
        val source = FastLang.yandex(sourceCode)
        val lang = if (source.isNotBlank() && source != target) "$source-$target" else target
        val groups = buildGroups(texts)
        val output = mutableListOf<String?>()
        var failed = 0
        var lastFailure: Throwable? = null
        for (group in groups) {
            try {
                output.addAll(pacer.schedule { translateGroup(group, lang, retriedUcid = false) })
            } catch (t: Throwable) {
                failed++
                lastFailure = t
                output.addAll(List(group.size) { null })
            }
        }
        if (failed > 0 && failed == groups.size) throw lastFailure ?: FastEngineException("Yandex failed")
        return EngineBatchResult(output, partialFailure = failed > 0)
    }

    private fun buildGroups(texts: List<String>): List<List<String>> {
        val groups = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        var chars = 0
        for (text in texts) {
            if (current.isNotEmpty() &&
                (current.size >= maxItemsPerRequest || chars + text.length > maxCharsPerRequest)
            ) {
                groups.add(current)
                current = mutableListOf()
                chars = 0
            }
            current.add(text)
            chars += text.length
        }
        if (current.isNotEmpty()) groups.add(current)
        return groups
    }

    private fun getUcid(): String {
        if (ucid.isNotEmpty() && System.currentTimeMillis() - ucidCreatedAtMs < UCID_TTL_MS) return ucid
        ucid = UUID.randomUUID().toString().replace("-", "")
        ucidCreatedAtMs = System.currentTimeMillis()
        return ucid
    }

    private suspend fun translateGroup(texts: List<String>, lang: String, retriedUcid: Boolean): List<String?> {
        val query = "ucid=${getUcid()}&srv=android&lang=${URLEncoder.encode(lang, "UTF-8")}&format=text"
        val form = FormBody.Builder()
        texts.forEach { form.add("text", it) }
        val request = Request.Builder()
            .url("$TRANSLATE_URL?$query")
            .post(form.build())
            .header("User-Agent", USER_AGENT)
            .build()
        val (status, bodyText) = client.newCall(request).awaitResponse().use { resp ->
            resp.code to (resp.body?.string().orEmpty())
        }
        val payload = runCatching { anyAdapter.fromJson(bodyText) }.getOrNull() as? Map<*, *>
        val code = (payload?.get("code") as? Number)?.toInt()

        val ucidRejected = status == 403 || code == 401 || code == 403 || code == 404
        if (ucidRejected && !retriedUcid) {
            ucid = ""
            ucidCreatedAtMs = 0
            return translateGroup(texts, lang, retriedUcid = true)
        }
        if (status == 429 || status >= 500) pacer.rateLimited()
        if (status !in 200..299 || payload == null || (code != null && code != 200)) {
            lastError = if (status == 429) "rate-limited" else "unavailable"
            cooldownUntilMs = System.currentTimeMillis() + FAIL_COOLDOWN_MS
            throw FastEngineException("Yandex request failed ($status/code $code)", status)
        }
        val arr = payload["text"] as? List<*>
        lastError = null
        cooldownUntilMs = 0
        pacer.success()
        return texts.mapIndexed { i, input ->
            val out = (arr?.getOrNull(i) as? String)?.trim().orEmpty()
            out.takeIf { it.isNotEmpty() && it != input.trim() }
        }
    }

    companion object {
        private const val TRANSLATE_URL = "https://translate.yandex.net/api/v1/tr.json/translate"
        private const val UCID_TTL_MS = 6 * 60_000L
        private const val FAIL_COOLDOWN_MS = 60_000L
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
    }
}
