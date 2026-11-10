package com.charles.livecaptionn

import com.charles.livecaptionn.settings.CaptionSettings
import com.charles.livecaptionn.settings.Language
import com.charles.livecaptionn.settings.SttBackend
import com.charles.livecaptionn.settings.TranslationBackend
import com.charles.livecaptionn.ui.MainUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainUiStateFilterTest {

    @Test
    fun availableSourceLanguages_whisperMlKit_includesMlKitLangs() {
        val state = defaultState().copy(
            settings = defaultSettings().copy(
                sttBackend = SttBackend.REMOTE_WHISPER,
                translationBackend = TranslationBackend.ML_KIT
            )
        )
        val sources = state.availableSourceLanguages
        assertTrue(sources.isNotEmpty())
        assertTrue(sources.any { it.code == "en" })
        assertTrue(sources.any { it.code == "vi" })
    }

    @Test
    fun availableTargetLanguages_mlKit_includesMlKitLangs() {
        val state = defaultState()
        val targets = state.availableTargetLanguages
        assertTrue(targets.isNotEmpty())
        assertTrue(targets.any { it.code == "en" })
    }

    @Test
    fun availableSourceLanguages_libre_withCatalog_returnsLibreLangs() {
        val state = defaultState().copy(
            settings = defaultSettings().copy(
                sttBackend = SttBackend.REMOTE_WHISPER,
                translationBackend = TranslationBackend.LIBRE_TRANSLATE
            ),
            libreLanguages = listOf(
                Language("en", "English"),
                Language("vi", "Vietnamese"),
                Language("es", "Spanish")
            )
        )
        val sources = state.availableSourceLanguages
        assertEquals(3, sources.size)
        assertTrue(sources.any { it.code == "en" })
    }

    @Test
    fun availableSourceLanguages_sherpa_returnsCatalogLanguages() {
        val state = defaultState().copy(
            settings = defaultSettings().copy(sttBackend = SttBackend.LOCAL_SHERPA)
        )
        val sources = state.availableSourceLanguages
        assertTrue(sources.any { it.code == "zh" })
        assertTrue(sources.any { it.code == "en" })
        assertTrue(sources.any { it.code == "ko" })
        assertTrue(sources.any { it.code == "ja" })
        // No streaming Sherpa model exists for these — must not be offered.
        assertTrue(sources.none { it.code == "de" })
        assertTrue(sources.none { it.code == "es" })
    }

    private fun defaultState() = MainUiState()
    private fun defaultSettings() = CaptionSettings()
}
