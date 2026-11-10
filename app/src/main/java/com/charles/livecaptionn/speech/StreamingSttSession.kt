package com.charles.livecaptionn.speech

/** Result of feeding one PCM chunk: current text, and whether a pause
 *  boundary finalized it into a completed segment. */
data class SttFeedResult(
    val text: String,
    val accepted: Boolean
)

// One live recognition session: PCM in, partial/final text out.
interface StreamingSttSession {
    fun feed(pcm: ByteArray, length: Int = pcm.size): SttFeedResult
    fun finish(): String
    fun close()
}

// Opens streaming sessions for a language. Null = engine can't serve it
// (model missing or language unsupported).
interface StreamingSttClient {
    suspend fun openSession(languageCode: String, sampleRate: Int): StreamingSttSession?
}
