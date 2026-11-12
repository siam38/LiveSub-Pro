package com.charles.livecaptionn.translation.fast

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64

// Free Microsoft translation, BTE-style: Bing Translator's web endpoint
// first (token scraped from the public page, cached 8 min), with the Edge
// browser token + Cognitive Translator API as the fallback path.
class MicrosoftFastEngine(private val client: OkHttpClient) : FastTranslateEngine {

    override val name = "microsoft"
    override val maxItemsPerRequest = 20
    override val maxCharsPerRequest = 4500

    val pacer = EnginePacer(baseIntervalMs = 120, maxIntervalMs = 2000)

    @Volatile var lastError: String? = null
        private set

    private val anyAdapter = Moshi.Builder().build().adapter(Any::class.java)
    private val bodyAdapter = Moshi.Builder().build().adapter<List<Map<String, String>>>(
        Types.newParameterizedType(
            List::class.java,
            Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
        )
    )

    private data class BingTokens(val ig: String, val iid: String, val key: String, val token: String, val atMs: Long)

    @Volatile private var bing: BingTokens? = null

    @Volatile private var edgeToken: String = ""
    @Volatile private var edgeExpiresAtMs: Long = 0
    @Volatile private var edgeFailedUntilMs: Long = 0

    private val tokenMutex = kotlinx.coroutines.sync.Mutex()

    override suspend fun warmUp() {
        runCatching { ensureBingTokens(force = false) }
    }

    override suspend fun translateBatch(
        texts: List<String>,
        sourceCode: String,
        targetCode: String
    ): EngineBatchResult {
        val safe = texts.map { it }
        if (safe.none { it.isNotBlank() }) return EngineBatchResult(List(safe.size) { null })
        val groups = buildGroups(safe)
        val output = mutableListOf<String?>()
        var failed = 0
        var lastFailure: Throwable? = null
        for (group in groups) {
            try {
                output.addAll(pacer.schedule { translateChunk(group, sourceCode, targetCode) })
            } catch (t: Throwable) {
                failed++
                lastFailure = t
                output.addAll(List(group.size) { null })
            }
        }
        if (failed > 0 && failed == groups.size) throw lastFailure ?: FastEngineException("Microsoft translation failed")
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

    private suspend fun translateChunk(texts: List<String>, sourceCode: String, targetCode: String): List<String?> {
        try {
            val out = translateWithBingWeb(texts, sourceCode, targetCode)
            lastError = null
            pacer.success()
            return out
        } catch (bingError: Throwable) {
            try {
                val out = translateWithEdgeToken(texts, sourceCode, targetCode)
                lastError = null
                pacer.success()
                return out
            } catch (edgeError: Throwable) {
                lastError = "unavailable"
                if (bingError is FastEngineException && bingError.status == 429) pacer.rateLimited()
                throw if (edgeError is FastEngineException && edgeError.status > 0) edgeError else bingError
            }
        }
    }

    // --- Bing web path ---

    private suspend fun ensureBingTokens(force: Boolean): BingTokens {
        val cached = bing
        if (!force && cached != null && System.currentTimeMillis() - cached.atMs < BING_TOKEN_TTL_MS) {
            return cached
        }
        tokenMutex.lock()
        try {
            val again = bing
            if (!force && again != null && System.currentTimeMillis() - again.atMs < BING_TOKEN_TTL_MS) {
                return again
            }
            val request = Request.Builder()
                .url(BING_HOME)
                .get()
                .header("User-Agent", USER_AGENT)
                .build()
            val html = client.newCall(request).awaitResponse().use { resp ->
                if (!resp.isSuccessful) throw FastEngineException("Bing page failed (${resp.code})", resp.code)
                resp.body?.string().orEmpty()
            }
            val ig = IG_REGEX.find(html)?.groupValues?.get(1).orEmpty()
            val abuse = ABUSE_REGEX.find(html)
            val iid = IID_REGEX.find(html)?.groupValues?.get(1) ?: "translator.5023"
            if (ig.isEmpty() || abuse == null) {
                throw FastEngineException("Bing translator tokens unavailable")
            }
            val tokens = BingTokens(
                ig = ig,
                iid = iid,
                key = abuse.groupValues[1],
                token = abuse.groupValues[2],
                atMs = System.currentTimeMillis()
            )
            bing = tokens
            bingCounter = 0
            return tokens
        } finally {
            tokenMutex.unlock()
        }
    }

    @Volatile private var bingCounter = 0

    private suspend fun translateWithBingWeb(
        texts: List<String>,
        sourceCode: String,
        targetCode: String
    ): List<String?> {
        val target = FastLang.microsoft(targetCode).ifBlank { "en" }
        val from = FastLang.microsoftWebFrom(sourceCode)
        ensureBingTokens(force = false)
        val flat = texts.map { it.replace(Regex("\\s*\\n+\\s*"), " ") }
        if (flat.none { it.isNotBlank() }) return List(texts.size) { null }
        return translateBingChunk(flat, from, target, depth = 0)
    }

    private suspend fun translateBingChunk(
        items: List<String>,
        from: String,
        target: String,
        depth: Int
    ): List<String?> {
        val usable = items.filter { it.isNotBlank() }
        if (usable.isEmpty()) return List(items.size) { null }
        if (items.size == 1) {
            val out = bingPost(items[0], from, target, retried = false)
            return listOf(sanitize(out, items[0]))
        }
        val joined = bingPost(items.joinToString(BING_SEPARATOR), from, target, retried = false)
        val parts = joined?.split(BING_SPLIT_REGEX)
        if (parts != null && parts.size == items.size) {
            return items.mapIndexed { i, input -> sanitize(parts[i], input) }
        }
        if (depth >= 4) return List(items.size) { null }
        val mid = (items.size + 1) / 2
        val left = translateBingChunk(items.subList(0, mid), from, target, depth + 1)
        val right = translateBingChunk(items.subList(mid, items.size), from, target, depth + 1)
        return left + right
    }

    private suspend fun bingPost(value: String, from: String, target: String, retried: Boolean): String? {
        val tokens = ensureBingTokens(force = false)
        bingCounter += 1
        val url = "https://www.bing.com/ttranslatev3?isVertical=1&IG=${tokens.ig}&IID=${tokens.iid}.$bingCounter"
        val body = FormBody.Builder()
            .add("fromLang", from)
            .add("to", target)
            .add("text", value)
            .add("token", tokens.token)
            .add("key", tokens.key)
            .build()
        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("User-Agent", USER_AGENT)
            .build()
        val payloadText = client.newCall(request).awaitResponse().use { resp ->
            if (resp.code == 429 || resp.code == 401) {
                pacer.rateLimited()
                throw FastEngineException("Bing rate limited (${resp.code})", resp.code)
            }
            resp.body?.string().orEmpty()
        }
        val payload = runCatching { anyAdapter.fromJson(payloadText) }.getOrNull()
        val statusCode = ((payload as? Map<*, *>)?.get("StatusCode") as? Number)?.toInt()
            ?: ((payload as? Map<*, *>)?.get("statusCode") as? Number)?.toInt()
            ?: 200
        if (statusCode == 205 && !retried) {
            ensureBingTokens(force = true)
            return bingPost(value, from, target, retried = true)
        }
        val list = payload as? List<*> ?: throw FastEngineException("Bing returned no translation")
        val translations = (list.firstOrNull() as? Map<*, *>)?.get("translations") as? List<*>
        return (translations?.firstOrNull() as? Map<*, *>)?.get("text") as? String
    }

    // --- Edge token path ---

    private suspend fun fetchEdgeToken(): String {
        if (System.currentTimeMillis() < edgeFailedUntilMs) {
            throw FastEngineException("Edge auth temporarily unavailable", status = 503)
        }
        val request = Request.Builder()
            .url(EDGE_AUTH_URL)
            .get()
            .header("User-Agent", USER_AGENT)
            .build()
        try {
            val token = client.newCall(request).awaitResponse().use { resp ->
                if (!resp.isSuccessful) throw FastEngineException("Edge auth failed (${resp.code})", resp.code)
                resp.body?.string().orEmpty().trim()
            }
            if (token.isEmpty()) throw FastEngineException("Edge auth returned empty token")
            edgeToken = token
            edgeExpiresAtMs = decodeJwtExpiryMs(token)
            edgeFailedUntilMs = 0
            return token
        } catch (t: Throwable) {
            edgeFailedUntilMs = System.currentTimeMillis() + EDGE_FAIL_COOLDOWN_MS
            throw t
        }
    }

    private suspend fun ensureEdgeToken(force: Boolean): String {
        val skewMs = 60_000L
        if (!force && edgeToken.isNotEmpty() && System.currentTimeMillis() + skewMs < edgeExpiresAtMs) {
            return edgeToken
        }
        return fetchEdgeToken()
    }

    private suspend fun translateWithEdgeToken(
        texts: List<String>,
        sourceCode: String,
        targetCode: String
    ): List<String?> {
        var token = ensureEdgeToken(force = false)
        try {
            return edgeRequest(texts, sourceCode, targetCode, token)
        } catch (e: FastEngineException) {
            if (e.status == 401 || e.status == 403) {
                edgeToken = ""
                edgeExpiresAtMs = 0
                token = ensureEdgeToken(force = true)
                return edgeRequest(texts, sourceCode, targetCode, token)
            }
            throw e
        }
    }

    private suspend fun edgeRequest(
        texts: List<String>,
        sourceCode: String,
        targetCode: String,
        token: String
    ): List<String?> {
        val url = buildEdgeUrl(sourceCode, targetCode)
        val json = bodyAdapter.toJson(texts.map { mapOf("Text" to it) })
        val request = Request.Builder()
            .url(url)
            .post(json.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $token")
            .header("User-Agent", USER_AGENT)
            .build()
        val payloadText = client.newCall(request).awaitResponse().use { resp ->
            if (!resp.isSuccessful) {
                if (resp.code == 429 || resp.code == 503) pacer.rateLimited()
                throw FastEngineException("Microsoft translation failed (${resp.code})", resp.code)
            }
            resp.body?.string().orEmpty()
        }
        val payload = runCatching { anyAdapter.fromJson(payloadText) }.getOrNull() as? List<*>
            ?: return List(texts.size) { null }
        return texts.mapIndexed { i, input ->
            val row = payload.getOrNull(i) as? Map<*, *>
            val translations = row?.get("translations") as? List<*>
            val text = (translations?.firstOrNull() as? Map<*, *>)?.get("text") as? String
            sanitize(text, input)
        }
    }

    private fun buildEdgeUrl(sourceCode: String, targetCode: String): String {
        val to = FastLang.microsoft(targetCode).ifBlank { "en" }
        val from = FastLang.microsoft(sourceCode)
        val base = "$API_URL?api-version=3.0&to=${java.net.URLEncoder.encode(to, "UTF-8")}"
        return if (from.isBlank()) base else base + "&from=${java.net.URLEncoder.encode(from, "UTF-8")}"
    }

    private fun sanitize(value: String?, input: String): String? {
        val out = value?.trim().orEmpty()
        return out.takeIf { it.isNotEmpty() && it != input.trim() }
    }

    companion object {
        private const val EDGE_AUTH_URL = "https://edge.microsoft.com/translate/auth"
        private const val API_URL = "https://api.cognitive.microsofttranslator.com/translate"
        private const val BING_HOME = "https://www.bing.com/translator"
        private const val BING_TOKEN_TTL_MS = 8 * 60_000L
        private const val EDGE_FAIL_COOLDOWN_MS = 2 * 60_000L
        private const val BING_SEPARATOR = "\n@@@\n"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0"

        private val BING_SPLIT_REGEX = Regex("\\s*@@@\\s*")
        private val IG_REGEX = Regex("IG:\"([A-Za-z0-9]+)\"")
        private val ABUSE_REGEX = Regex("params_AbusePreventionHelper\\s*=\\s*\\[([0-9]+),\\s*\"([^\"]+)\"")
        private val IID_REGEX = Regex("data-iid=\"([^\"]+)\"")

        internal fun decodeJwtExpiryMs(token: String): Long {
            return try {
                val parts = token.split(".")
                if (parts.size < 2) return System.currentTimeMillis() + 8 * 60_000L
                val payload = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
                val exp = Regex("\"exp\"\\s*:\\s*(\\d+)").find(payload)?.groupValues?.get(1)?.toLong()
                if (exp != null) exp * 1000 else System.currentTimeMillis() + 8 * 60_000L
            } catch (_: Throwable) {
                System.currentTimeMillis() + 8 * 60_000L
            }
        }
    }
}
