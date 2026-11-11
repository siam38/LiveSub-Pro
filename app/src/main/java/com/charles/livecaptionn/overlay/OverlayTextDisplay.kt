package com.charles.livecaptionn.overlay

data class OverlayTextDisplay(
    val showOriginal: Boolean,
    val originalText: String,
    val originalTextSizeSp: Float,
    val translatedText: String,
    val translatedTextSizeSp: Float,
    val livePartialText: String = ""
)

fun computeOverlayTextDisplay(ui: OverlayUiState): OverlayTextDisplay {
    val isIdentical = ui.originalText.isNotBlank() &&
        ui.originalText.trim().equals(ui.transcriptText.trim(), ignoreCase = true)
    val showOriginal = ui.showOriginal && ui.originalText.isNotBlank() && !isIdentical
    val scaledOriginalSize = (ui.textSizeSp * 0.78f).coerceIn(12f, 20f)
    return OverlayTextDisplay(
        showOriginal = showOriginal,
        originalText = ui.originalText.trim().replace(Regex("\\s+"), " "),
        originalTextSizeSp = scaledOriginalSize,
        translatedText = ui.transcriptText.ifBlank { "…" },
        translatedTextSizeSp = ui.textSizeSp,
        livePartialText = ui.livePartialText.trim().replace(Regex("\\s+"), " ")
    )
}
