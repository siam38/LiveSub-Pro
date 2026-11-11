package com.charles.livecaptionn.settings

enum class TranslationBackend {
    ML_KIT,
    LIBRE_TRANSLATE;

    companion object {
        fun fromName(name: String?): TranslationBackend =
            entries.firstOrNull { it.name == name } ?: ML_KIT
    }
}
