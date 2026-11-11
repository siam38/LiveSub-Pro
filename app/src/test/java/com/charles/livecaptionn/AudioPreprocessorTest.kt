package com.charles.livecaptionn

import com.charles.livecaptionn.speech.AudioPreprocessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class AudioPreprocessorTest {

    @Test
    fun processPcm16_emptyInput_returnsEmptyArray() {
        val processor = AudioPreprocessor()
        val result = processor.processPcm16(ShortArray(0), 0)
        assertEquals(0, result.size)
    }

    @Test
    fun processPcm16_removesConstantDcOffset() {
        val processor = AudioPreprocessor(enableDcBlocker = true, enableAgc = false)

        val dcSamples = ShortArray(1600) { 8000 }
        val output = processor.processPcm16(dcSamples, dcSamples.size)

        val tailAvg = output.takeLast(200).map { abs(it) }.average()
        assertTrue("Expected tail DC average < 0.02, got $tailAvg", tailAvg < 0.02)
    }

    @Test
    fun processPcm16_boostsQuietSystemAudioSignal() {
        val processor = AudioPreprocessor(enableDcBlocker = true, enableAgc = true)

        val quietWave = ShortArray(1600) { i ->
            (600.0 * sin(2.0 * Math.PI * 440.0 * i / 16000.0)).toInt().toShort()
        }

        processor.processPcm16(quietWave, quietWave.size)
        val out = processor.processPcm16(quietWave, quietWave.size)

        val rmsOut = sqrt(out.map { (it * it).toDouble() }.average()).toFloat()
        assertTrue("Expected AGC gain > 1.2f for quiet signal, got ${processor.lastAppliedGain}", processor.lastAppliedGain > 1.2f)
        assertTrue("Expected boosted RMS > 0.02f, got $rmsOut", rmsOut > 0.02f)
    }

    @Test
    fun processPcm16_doesNotBoostSilenceBelowNoiseGate() {
        val processor = AudioPreprocessor(enableDcBlocker = true, enableAgc = true)
        val nearSilence = ShortArray(1600) { i -> if (i % 2 == 0) 8 else -8 }
        val out = processor.processPcm16(nearSilence, nearSilence.size)

        val maxAbs = out.maxOf { abs(it) }
        assertTrue("Silence should remain near zero (< 0.002f), got $maxAbs", maxAbs < 0.002f)
    }

    @Test
    fun processPcm16_softLimitsLoudPeaksWithoutClipping() {
        val processor = AudioPreprocessor(enableDcBlocker = false, enableAgc = true)
        val loudWave = ShortArray(1600) { i ->
            (32000.0 * sin(2.0 * Math.PI * 300.0 * i / 16000.0)).toInt().toShort()
        }
        val out = processor.processPcm16(loudWave, loudWave.size)
        assertTrue(out.all { it in -0.991f..0.991f })
    }
}
