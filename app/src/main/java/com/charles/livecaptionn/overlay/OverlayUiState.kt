package com.charles.livecaptionn.overlay

import com.charles.livecaptionn.speech.RecognitionStatus

data class OverlayUiState(
    val originalText: String = "",
    val translatedText: String = "",
    val transcriptText: String = "",
    val livePartialText: String = "",
    val status: RecognitionStatus = RecognitionStatus.IDLE,

    val statusDetail: String? = null,
    val textSizeSp: Float = 20f,
    val opacity: Float = 0.65f,
    val showOriginal: Boolean = true,
    val minimized: Boolean = false,
    val sourceCode: String = "zh",
    val targetCode: String = "en",
    val themeId: String = OverlayThemeCatalog.FREE_THEME_ID,
    val fontId: String = OverlayFontCatalog.FREE_FONT_ID
)
