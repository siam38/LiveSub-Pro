package com.charles.livecaptionn.translation.fast

import android.util.Log
import com.charles.livecaptionn.translation.TranslationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

// TranslationRepository backed by the BTE fast-web system: Google +
// Microsoft (Bing/Edge) + Yandex free endpoints behind one manager.
// Needs the network; falls back to "" (no translation) when fully offline,
// exactly how the Libre backend signals failure.
class FastTranslationRepository : TranslationRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(9, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    val manager = FastTranslationManager(
        engines = listOf(
            GoogleFastEngine(client),
            MicrosoftFastEngine(client),
            YandexFastEngine(client)
        ),
        scope = scope
    )

    override suspend fun translate(
        text: String,
        sourceCode: String,
        targetCode: String,
        autoDetect: Boolean
    ): String {
        val clean = text.trim()
        if (clean.isEmpty() || targetCode.isBlank()) return clean
        val src = if (autoDetect || sourceCode.isBlank()) "auto" else sourceCode
        if (FastLang.base(src) == FastLang.base(targetCode)) return clean
        return manager.translate(clean, src, targetCode, urgent = false) ?: ""
    }

    override suspend fun translateUrgent(
        text: String,
        sourceCode: String,
        targetCode: String,
        autoDetect: Boolean
    ): String {
        val clean = text.trim()
        if (clean.isEmpty() || targetCode.isBlank()) return clean
        val src = if (autoDetect || sourceCode.isBlank()) "auto" else sourceCode
        if (FastLang.base(src) == FastLang.base(targetCode)) return clean
        return manager.translate(clean, src, targetCode, urgent = true) ?: ""
    }

    override suspend fun prewarm(sourceCode: String, targetCode: String) {
        // Warm connections + tokens in the background, like BTE does on load,
        // so the first caption doesn't pay DNS/TLS/token setup.
        scope.launch {
            runCatching { manager.warmUp(sourceCode, targetCode) }
                .onFailure { Log.w(TAG, "Fast web prewarm failed", it) }
        }
    }

    override fun close() {
        scope.cancel()
        client.dispatcher.executorService.shutdown()
    }

    companion object {
        private const val TAG = "FastWebTranslator"
    }
}
