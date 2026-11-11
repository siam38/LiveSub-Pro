package com.charles.livecaptionn.translation

interface TranslationRepository {
    suspend fun translate(
        text: String,
        sourceCode: String,
        targetCode: String,
        autoDetect: Boolean
    ): String

    suspend fun prewarm(sourceCode: String, targetCode: String) {}

    fun close() {}
}
