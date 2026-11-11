package com.charles.livecaptionn

import com.charles.livecaptionn.speech.SherpaEndpointTuning
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaEndpointTuningTest {

    @Test
    fun pauseSeconds_endpointsAtShortPhrasePause() {

        assertTrue(SherpaEndpointTuning.PAUSE_SECONDS <= 0.6f)
        assertTrue(SherpaEndpointTuning.PAUSE_SECONDS > 0.2f)
    }

    @Test
    fun maxSegmentSeconds_capsContinuousSpeechWellBelowSherpaDefault() {

        assertTrue(SherpaEndpointTuning.MAX_SEGMENT_SECONDS <= 8.0f)
        assertTrue(SherpaEndpointTuning.MAX_SEGMENT_SECONDS >= 5.0f)
    }
}
