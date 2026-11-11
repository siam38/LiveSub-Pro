package com.charles.livecaptionn

import com.charles.livecaptionn.speech.SherpaEndpointTuning
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaEndpointTuningTest {

    @Test
    fun pauseSeconds_endpointsAtShortPhrasePause() {
        // Segments must end at a short natural pause — the old 1.0s trailing
        // silence (plus final-only translation) made subtitles feel super slow.
        assertTrue(SherpaEndpointTuning.PAUSE_SECONDS <= 0.6f)
        assertTrue(SherpaEndpointTuning.PAUSE_SECONDS > 0.2f)
    }

    @Test
    fun maxSegmentSeconds_capsContinuousSpeechWellBelowSherpaDefault() {
        // Sherpa's rule3 default caps an utterance at 20s; subtitle-sized
        // segments must cut continuous speech far sooner than that.
        assertTrue(SherpaEndpointTuning.MAX_SEGMENT_SECONDS <= 8.0f)
        assertTrue(SherpaEndpointTuning.MAX_SEGMENT_SECONDS >= 5.0f)
    }
}
