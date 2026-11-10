package com.charles.livecaptionn.settings

enum class SttBackend(val label: String) {
    LOCAL_SHERPA("On-device Sherpa (Zipformer)"),
    LOCAL_VOSK("On-device Vosk (streaming)"),
    REMOTE_WHISPER("Remote Whisper");

    companion object {
        fun fromName(name: String): SttBackend =
            entries.firstOrNull { it.name == name } ?: LOCAL_VOSK
    }
}
