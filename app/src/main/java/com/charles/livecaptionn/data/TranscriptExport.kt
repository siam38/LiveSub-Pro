package com.charles.livecaptionn.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Immutable, self-contained data suitable for sharing or writing to a file. */
data class TranscriptExport(val entries: List<TranscriptEntry>) {
    fun asText(): String {
        // Readable local time instead of raw epoch milliseconds.
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return entries.asReversed().joinToString("\n\n") {
            "[${format.format(Date(it.timestamp))}] ${it.sourceLanguage} -> ${it.targetLanguage}\n${it.originalText}\n${it.translatedText}"
        }
    }

    fun asSrt(): String {
        if (entries.isEmpty()) return ""
        val ordered = entries.sortedBy { it.timestamp }
        val baseEpochMs = ordered.first().timestamp
        return ordered.mapIndexed { idx, entry ->
            val startMs = (entry.timestamp - baseEpochMs).coerceAtLeast(0L)
            val nextStartMs = ordered.getOrNull(idx + 1)?.let { (it.timestamp - baseEpochMs).coerceAtLeast(startMs + 800L) }
            val endMs = if (nextStartMs != null) {
                minOf(startMs + 4000L, (nextStartMs - 100L).coerceAtLeast(startMs + 800L))
            } else {
                startMs + 3000L
            }
            val body = if (entry.translatedText.isNotBlank() && entry.translatedText != entry.originalText) {
                "${entry.translatedText}\n${entry.originalText}"
            } else {
                entry.translatedText.ifBlank { entry.originalText }
            }
            "${idx + 1}\n${formatSrtTime(startMs)} --> ${formatSrtTime(endMs)}\n$body"
        }.joinToString("\n\n")
    }

    fun asJson(): String = JSONObject().put("entries", JSONArray().apply {
        entries.forEach { put(JSONObject().apply {
            put("id", it.id); put("timestamp", it.timestamp); put("originalText", it.originalText)
            put("translatedText", it.translatedText); put("sourceLanguage", it.sourceLanguage); put("targetLanguage", it.targetLanguage)
        }) }
    }).toString()

    private fun formatSrtTime(ms: Long): String {
        val hours = ms / 3_600_000L
        val minutes = (ms % 3_600_000L) / 60_000L
        val seconds = (ms % 60_000L) / 1_000L
        val millis = ms % 1_000L
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }
}
