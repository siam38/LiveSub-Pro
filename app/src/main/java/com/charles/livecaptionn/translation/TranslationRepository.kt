package com.charles.livecaptionn.translation

interface TranslationRepository {
    suspend fun translate(
        text: String,
        sourceCode: String,
        targetCode: String,
        autoDetect: Boolean
    ): String

    // Final captions: backends that can race engines override this so the
    // finished line is translated as fast as possible.
    suspend fun translateUrgent(
        text: String,
        sourceCode: String,
        targetCode: String,
        autoDetect: Boolean
    ): String = translate(text, sourceCode, targetCode, autoDetect)

    suspend fun prewarm(sourceCode: String, targetCode: String) {}

    fun close() {}
}
