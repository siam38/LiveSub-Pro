package com.charles.livecaptionn.overlay

// Real-subtitle cue shaping: one cue at a time, at most two lines on screen.
// When a live sentence outgrows the cue, keep the newest words visible
// (rolling window, like YouTube live captions) instead of growing a paragraph.
object SubtitleCue {
    const val MAX_CUE_CHARS = 84

    fun rolling(text: String, maxChars: Int = MAX_CUE_CHARS): String {
        val clean = text.trim().replace(Regex("\\s+"), " ")
        if (clean.length <= maxChars) return clean
        val tail = clean.takeLast(maxChars)
        // Prefer starting the window at a sentence boundary inside the tail.
        val sentenceStart = tail.indexOf(". ").takeIf { it in 1..(maxChars / 2) }
            ?: tail.indexOf("! ").takeIf { it in 1..(maxChars / 2) }
            ?: tail.indexOf("? ").takeIf { it in 1..(maxChars / 2) }
        if (sentenceStart != null) return tail.substring(sentenceStart + 2).trim()
        // Otherwise start at a word boundary so we never cut a word in half.
        val space = tail.indexOf(' ')
        return if (space in 1..20) "… " + tail.substring(space + 1).trim() else "… " + tail.trim()
    }
}
