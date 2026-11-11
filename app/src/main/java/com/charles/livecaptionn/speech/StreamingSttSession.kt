package com.charles.livecaptionn.speech

data class SttFeedResult(
    val text: String,
    val accepted: Boolean
)

interface StreamingSttSession {
    fun feed(pcm: ByteArray, length: Int = pcm.size): SttFeedResult
    fun finish(): String
    fun close()
}

interface StreamingSttClient {
    suspend fun openSession(languageCode: String, sampleRate: Int): StreamingSttSession?
}
