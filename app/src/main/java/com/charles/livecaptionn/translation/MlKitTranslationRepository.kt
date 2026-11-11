package com.charles.livecaptionn.translation

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device translation powered by Google ML Kit.
 *
 * ML Kit ships pre-trained Google Translate models that run entirely on the
 * phone once downloaded. First use of a given language pair triggers a
 * one-time ~30 MB download (handled transparently). After that the pipeline
 * is fully offline — no LibreTranslate server required.
 *
 * Supported languages are whatever `TranslateLanguage.getAllLanguages()`
 * exposes — roughly 59 codes (`en`, `vi`, `es`, `fr`, `de`, `ja`, `ko`, `zh`, etc.).
 * Cantonese (`yue`) transcripts are normalized to `zh` so ML Kit's Chinese
 * model translates them seamlessly.
 */
class MlKitTranslationRepository : TranslationRepository {

    // Bounded LRU of Translator instances so switching across many language pairs
    // closes old native translators instead of accumulating native heap memory.
    private val translators = object : LinkedHashMap<String, Translator>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Translator>): Boolean {
            if (size > MAX_ACTIVE_TRANSLATORS) {
                try { eldest.value.close() } catch (_: Throwable) {}
                return true
            }
            return false
        }
    }

    // Live partials and repeated phrases hit an exact-match LRU cache.
    private val cache = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, String>) = size > 300
    }

    override suspend fun translate(
        text: String,
        sourceCode: String,
        targetCode: String,
        autoDetect: Boolean
    ): String {
        val clean = text.trim()
        if (clean.isEmpty()) return clean
        if (targetCode.isBlank()) return clean

        val src = resolveMlKitLang(sourceCode)
        val dst = resolveMlKitLang(targetCode)
        if (src == null || dst == null) {
            Log.w(TAG, "ML Kit does not support pair $sourceCode → $targetCode; passing text through.")
            return clean
        }
        if (src == dst) return clean

        synchronized(cache) { cache["$src|$dst|$clean"] }?.let { return it }
        return try {
            val translator = translatorFor(src, dst)
            ensureModelDownloaded(translator)
            val translated = translateBlocking(translator, clean).trim()
            if (translated.isNotEmpty()) {
                synchronized(cache) { cache["$src|$dst|$clean"] = translated }
            }
            Log.d(TAG, "ML Kit $sourceCode→$targetCode ok: '${clean.take(40)}' → '${translated.take(40)}'")
            translated
        } catch (t: Throwable) {
            Log.w(TAG, "ML Kit translation failed for $sourceCode→$targetCode", t)
            ""
        }
    }

    override suspend fun prewarm(sourceCode: String, targetCode: String) {
        val src = resolveMlKitLang(sourceCode) ?: return
        val dst = resolveMlKitLang(targetCode) ?: return
        if (src == dst) return
        try {
            val translator = translatorFor(src, dst)
            ensureModelDownloaded(translator)
            Log.i(TAG, "ML Kit model ready for $sourceCode → $targetCode")
        } catch (t: Throwable) {
            Log.w(TAG, "ML Kit prewarm failed for $sourceCode → $targetCode", t)
        }
    }

    /**
     * Ensures the source→target model is downloaded, propagating failures
     * (unlike [prewarm], which swallows them for the caption service).
     */
    suspend fun requireModel(sourceCode: String, targetCode: String) {
        val src = resolveMlKitLang(sourceCode) ?: return
        val dst = resolveMlKitLang(targetCode) ?: return
        if (src == dst) return
        val translator = translatorFor(src, dst)
        ensureModelDownloaded(translator)
        Log.i(TAG, "ML Kit model ready for $sourceCode → $targetCode")
    }

    @Synchronized
    private fun translatorFor(src: String, dst: String): Translator {
        val key = "$src|$dst"
        translators[key]?.let { return it }
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(src)
            .setTargetLanguage(dst)
            .build()
        val translator = Translation.getClient(options)
        translators[key] = translator
        return translator
    }

    private suspend fun ensureModelDownloaded(translator: Translator) = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine<Unit> { cont ->
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener { if (cont.isActive) cont.resume(Unit) }
                .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
        }
    }

    private suspend fun translateBlocking(translator: Translator, text: String): String =
        suspendCancellableCoroutine { cont ->
            translator.translate(text)
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
        }

    override fun close() {
        val toClose = synchronized(this) {
            val current = translators.values.toList()
            translators.clear()
            current
        }
        toClose.forEach { translator ->
            try { translator.close() } catch (_: Throwable) {}
        }
    }

    companion object {
        private const val TAG = "MlKitTranslator"
        private const val MAX_ACTIVE_TRANSLATORS = 4

        internal fun resolveMlKitLang(code: String): String? {
            val normalized = when (val primary = code.trim().lowercase().substringBefore('-').substringBefore('_')) {
                "yue" -> "zh"
                "iw" -> "he"
                "in" -> "id"
                else -> primary
            }
            return TranslateLanguage.fromLanguageTag(normalized)
        }
    }
}
