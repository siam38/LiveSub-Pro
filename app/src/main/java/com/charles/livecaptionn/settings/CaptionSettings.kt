package com.charles.livecaptionn.settings

import com.charles.livecaptionn.BuildConfig

data class CaptionSettings(
    val sourceLanguageCode: String = "zh",
    val targetLanguageCode: String = "en",
    val autoDetectSource: Boolean = false,
    val textSizeSp: Float = 22f,
    val overlayOpacity: Float = 0.65f,
    val showOriginal: Boolean = false,
    val serverBaseUrl: String = DEFAULT_BASE_URL,
    val overlayX: Int = 60,
    val overlayY: Int = 220,
    val overlayMinimized: Boolean = false,

    val audioSource: AudioSource = AudioSource.SYSTEM,
    val sttBackend: SttBackend = SttBackend.LOCAL_SHERPA,
    val translationBackend: TranslationBackend = TranslationBackend.ML_KIT,
    val sttBaseUrl: String = DEFAULT_STT_URL,
    val overlayWidthDp: Int = DEFAULT_OVERLAY_WIDTH_DP,
    val overlayHeightDp: Int = DEFAULT_OVERLAY_HEIGHT_DP,

    val overlayThemeId: String = "default",
    val overlayFontId: String = "default",

    val uiLanguageCode: String = "en",

    val onboardingComplete: Boolean = false,

    val saveHistory: Boolean = true,

    val borderlineWarningDismissed: Boolean = false,

    val simulatedCompatibilityTier: String? = null
) {
    companion object {
        val DEFAULT_BASE_URL: String = BuildConfig.DEFAULT_TRANSLATE_URL
        val DEFAULT_STT_URL: String = BuildConfig.DEFAULT_STT_URL
        const val DEFAULT_OVERLAY_WIDTH_DP = 360
        const val DEFAULT_OVERLAY_HEIGHT_DP = 120
        const val MIN_OVERLAY_WIDTH_DP = 180
        const val MIN_OVERLAY_HEIGHT_DP = 80
    }
}
