package com.charles.livecaptionn.overlay

data class OverlayTextDisplay(
    val showOriginal: Boolean,
    val originalText: String,
    val originalTextSizeSp: Float,
    val translatedText: String,
    val translatedTextSizeSp: Float
)

// One cue at a time. Translated line is the hero (full size, max 2 lines);
// the original, when bilingual mode is on, sits smaller above it.
fun computeOverlayTextDisplay(ui: OverlayUiState): OverlayTextDisplay {
    val isIdentical = ui.originalText.isNotBlank() &&
        ui.originalText.trim().equals(ui.transcriptText.trim(), ignoreCase = true)
    val showOriginal = ui.showOriginal && ui.originalText.isNotBlank() && !isIdentical
    val scaledOriginalSize = (ui.textSizeSp * 0.78f).coerceIn(12f, 20f)
    return OverlayTextDisplay(
        showOriginal = showOriginal,
        originalText = SubtitleCue.rolling(ui.originalText, maxChars = 72),
        originalTextSizeSp = scaledOriginalSize,
        translatedText = ui.transcriptText.ifBlank { "…" },
        translatedTextSizeSp = ui.textSizeSp
    )
}
