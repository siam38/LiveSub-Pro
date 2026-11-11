package com.charles.livecaptionn.service

import com.charles.livecaptionn.speech.RecognitionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CaptionRuntimeLine(
    val id: Long,
    val originalText: String,
    val translatedText: String = "",
    val isFinal: Boolean = false
)

data class CaptionRuntimeState(
    val originalText: String = "",
    val translatedText: String = "",
    val previewTranslatedText: String = "",
    val transcriptLines: List<CaptionRuntimeLine> = emptyList(),
    val status: RecognitionStatus = RecognitionStatus.IDLE,
    // Overall latency of the last final cue: from the end of the spoken
    // segment to the translated subtitle being ready to show (endpoint
    // wait + final transcription + translation + hand-off).
    val lastLatencyMs: Long? = null,
    val appRamMb: Long = 0,
    val deviceFreeMb: Long = 0,
    val appCpuPct: Int = -1,
    val running: Boolean = false,
    val paused: Boolean = false,
    val lastError: String? = null
)

class CaptionRuntimeStore {
    private val mutable = MutableStateFlow(CaptionRuntimeState())
    val state: StateFlow<CaptionRuntimeState> = mutable.asStateFlow()

    fun update(transform: (CaptionRuntimeState) -> CaptionRuntimeState) {
        mutable.update(transform)
    }
}
