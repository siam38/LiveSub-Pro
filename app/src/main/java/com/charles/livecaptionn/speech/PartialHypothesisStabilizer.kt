package com.charles.livecaptionn.speech

/**
 * Cleans and stabilizes streaming transducer hypotheses:
 * - Suppresses acoustic repetition loops (consecutive 1..3 word n-grams or repeated CJK runs).
 * - Stabilizes partial tokens to avoid single-frame trailing jitter.
 * - Detects natural clause boundaries during long utterances so early preview translations
 *   can be dispatched without waiting up to 7 seconds for a final silence endpoint.
 */
class PartialHypothesisStabilizer(
    private val earlyTranslateIntervalMs: Long = 950L,
    private val minCharsForEarlyTranslate: Int = 14,
    private val minDeltaCharsForNextPreview: Int = 10
) {
    private var previousRawPartial: String = ""
    private var lastStablePartial: String = ""
    private var lastPreviewTranslatedText: String = ""
    private var lastPreviewTranslateTimeMs: Long = 0L

    fun resetUtterance() {
        previousRawPartial = ""
        lastStablePartial = ""
        lastPreviewTranslatedText = ""
        lastPreviewTranslateTimeMs = 0L
    }

    /**
     * Cleans a final transcript segment (collapses repetition loops and whitespace).
     */
    fun cleanFinal(rawText: String): String {
        val cleaned = collapseRepetitionLoops(normalizeWhitespace(rawText))
        resetUtterance()
        return cleaned
    }

    /**
     * Stabilizes an incremental partial hypothesis.
     */
    fun stabilizePartial(rawText: String): String {
        val normalized = collapseRepetitionLoops(normalizeWhitespace(rawText))
        if (normalized.isEmpty()) return ""

        // If the previous partial was longer and only the last 1-2 characters flickered,
        // prefer the common prefix if the new partial shortened by just 1 char due to frame noise.
        val prev = previousRawPartial
        previousRawPartial = normalized

        val candidate = if (prev.isNotEmpty() && normalized != prev) {
            val lcp = longestCommonPrefix(prev, normalized).trimEnd()
            if (lcp.length >= 6 && prev.length - lcp.length <= 2 && normalized.length < prev.length) {
                lcp
            } else {
                normalized
            }
        } else {
            normalized
        }

        lastStablePartial = candidate
        return candidate
    }

    /**
     * Determines whether an in-progress partial utterance has reached a meaningful clause
     * or length milestone that merits a debounced preview translation.
     */
    fun shouldDispatchEarlyTranslation(stabilizedPartial: String, nowMs: Long): Boolean {
        val text = stabilizedPartial.trim()
        if (text.isEmpty() || text == lastPreviewTranslatedText) return false

        val elapsed = nowMs - lastPreviewTranslateTimeMs
        if (lastPreviewTranslateTimeMs > 0L && elapsed < earlyTranslateIntervalMs) {
            return false
        }

        val wordCount = countSemanticUnits(text)
        val endsAtClause = CLAUSE_DELIMITERS.any { text.endsWith(it) }
        val deltaChars = text.length - lastPreviewTranslatedText.length

        val meetsInitialThreshold = lastPreviewTranslatedText.isEmpty() &&
            (wordCount >= 4 || text.length >= minCharsForEarlyTranslate || (endsAtClause && wordCount >= 2))

        val meetsIncrementalThreshold = lastPreviewTranslatedText.isNotEmpty() &&
            (deltaChars >= minDeltaCharsForNextPreview || (endsAtClause && deltaChars >= 4))

        if (meetsInitialThreshold || meetsIncrementalThreshold) {
            lastPreviewTranslatedText = text
            lastPreviewTranslateTimeMs = nowMs
            return true
        }
        return false
    }

    companion object {
        private val MULTI_SPACE_REGEX = Regex("\\s+")
        // Collapses 3+ identical consecutive words: "the the the" -> "the"
        private val REPEATED_WORD_REGEX = Regex(
            pattern = "\\b(\\p{L}+)(?:\\s+\\1){2,}\\b",
            options = setOf(RegexOption.IGNORE_CASE)
        )
        // Collapses 3+ identical consecutive 2-word phrases: "thank you thank you thank you" -> "thank you"
        private val REPEATED_BIGRAM_REGEX = Regex(
            pattern = "\\b(\\p{L}+\\s+\\p{L}+)(?:\\s+\\1){2,}\\b",
            options = setOf(RegexOption.IGNORE_CASE)
        )
        // Collapses 4+ identical consecutive CJK/non-space characters: "啊啊啊啊" -> "啊"
        private val REPEATED_CHAR_REGEX = Regex("([\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}])\\1{3,}")

        private val CLAUSE_DELIMITERS = charArrayOf(
            ',', ';', ':', '.', '?', '!', '，', '。', '？', '！', '、', '；'
        )

        fun normalizeWhitespace(input: String): String {
            return input.replace(MULTI_SPACE_REGEX, " ").trim()
        }

        fun collapseRepetitionLoops(input: String): String {
            if (input.length < 4) return input
            var out = input
            out = REPEATED_CHAR_REGEX.replace(out, "$1")
            out = REPEATED_BIGRAM_REGEX.replace(out, "$1")
            out = REPEATED_WORD_REGEX.replace(out, "$1")
            return out.trim()
        }

        fun countSemanticUnits(text: String): Int {
            var cjkCount = 0
            for (ch in text) {
                if (Character.UnicodeScript.of(ch.code) in CJK_SCRIPTS) {
                    cjkCount++
                }
            }
            val spaceSeparatedWords = text.split(' ').count { it.isNotBlank() }
            // Every ~2 CJK characters approximates 1 spoken word
            return maxOf(spaceSeparatedWords, (cjkCount + 1) / 2)
        }

        private fun longestCommonPrefix(a: String, b: String): String {
            val minLen = minOf(a.length, b.length)
            var i = 0
            while (i < minLen && a[i] == b[i]) {
                i++
            }
            return a.substring(0, i)
        }

        private val CJK_SCRIPTS = setOf(
            Character.UnicodeScript.HAN,
            Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA,
            Character.UnicodeScript.HANGUL
        )
    }
}
