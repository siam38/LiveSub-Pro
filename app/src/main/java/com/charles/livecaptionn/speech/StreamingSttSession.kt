package com.charles.livecaptionn.speech

// One live recognition session: PCM in, partial/final text out.
// Implemented by the Vosk and Sherpa on-device engines.
interface StreamingSttSession {
    fun feed(pcm: ByteArray, length: Int = pcm.size): VoskStreamingSession.FeedResult
    fun finish(): String
    fun close()
}

// Opens streaming sessions for a language. Null = engine can't serve it
// (model missing or language unsupported).
interface StreamingSttClient {
    suspend fun openSession(languageCode: String, sampleRate: Int): StreamingSttSession?
}
