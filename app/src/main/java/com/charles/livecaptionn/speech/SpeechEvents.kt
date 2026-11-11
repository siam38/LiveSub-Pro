package com.charles.livecaptionn.speech

data class SpeechResult(
    val text: String,
    val isFinal: Boolean,
    // elapsedRealtime moment the speech in this final segment actually
    // ended, when the engine knows it (streaming). Lets the service report
    // true end-to-end latency instead of translation time alone.
    val segmentEndMs: Long? = null
)
