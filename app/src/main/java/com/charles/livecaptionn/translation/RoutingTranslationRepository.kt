package com.charles.livecaptionn.translation

import com.charles.livecaptionn.data.SettingsRepository
import com.charles.livecaptionn.settings.TranslationBackend
import kotlinx.coroutines.flow.first

class RoutingTranslationRepository(
    private val settingsRepository: SettingsRepository,
    private val mlKit: TranslationRepository,
    private val libre: TranslationRepository,
    private val fastWeb: TranslationRepository? = null
) : TranslationRepository {

    private var cachedBackend: TranslationBackend? = null
    private var cachedDelegate: TranslationRepository? = null

    override suspend fun translate(
        text: String,
        sourceCode: String,
        targetCode: String,
        autoDetect: Boolean
    ): String = activeDelegate().translate(text, sourceCode, targetCode, autoDetect)

    override suspend fun translateUrgent(
        text: String,
        sourceCode: String,
        targetCode: String,
        autoDetect: Boolean
    ): String = activeDelegate().translateUrgent(text, sourceCode, targetCode, autoDetect)

    override suspend fun prewarm(sourceCode: String, targetCode: String) {
        activeDelegate().prewarm(sourceCode, targetCode)
    }

    private suspend fun activeDelegate(): TranslationRepository {
        val backend = settingsRepository.settingsFlow.first().translationBackend
        if (cachedBackend == backend && cachedDelegate != null) {
            return cachedDelegate!!
        }
        val delegate = when (backend) {
            TranslationBackend.ML_KIT -> mlKit
            TranslationBackend.FAST_WEB -> fastWeb ?: mlKit
            TranslationBackend.LIBRE_TRANSLATE -> libre
        }
        cachedBackend = backend
        cachedDelegate = delegate
        return delegate
    }

    override fun close() {
        mlKit.close()
        libre.close()
        fastWeb?.close()
    }
}
