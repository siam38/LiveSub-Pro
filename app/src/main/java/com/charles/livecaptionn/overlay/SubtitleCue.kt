package com.charles.livecaptionn.overlay

object SubtitleCue {
    const val MAX_CUE_CHARS = 84

    fun rolling(text: String, maxChars: Int = MAX_CUE_CHARS): String {
        val clean = text.trim().replace(Regex("\\s+"), " ")
        if (clean.length <= maxChars) return clean
        val tail = clean.takeLast(maxChars)

        val sentenceStart = tail.indexOf(". ").takeIf { it in 1..(maxChars / 2) }
            ?: tail.indexOf("! ").takeIf { it in 1..(maxChars / 2) }
            ?: tail.indexOf("? ").takeIf { it in 1..(maxChars / 2) }
        if (sentenceStart != null) return tail.substring(sentenceStart + 2).trim()

        val space = tail.indexOf(' ')
        return if (space in 1..20) "… " + tail.substring(space + 1).trim() else "… " + tail.trim()
    }
}
