package com.charles.livecaptionn.settings

enum class TranslationBackend {
    ML_KIT,
    FAST_WEB,
    LIBRE_TRANSLATE;

    companion object {
        fun fromName(name: String?): TranslationBackend =
            entries.firstOrNull { it.name == name } ?: ML_KIT
    }
}
