package com.charles.livecaptionn.speech

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Real-time 16 kHz mono PCM signal conditioner for streaming transducer ASR.
 *
 * Performs:
 * 1. First-order high-pass DC-offset removal (cutoff ~12 Hz at 16 kHz).
 * 2. Envelope-tracked Adaptive Gain Control (AGC) to recover low-volume system-audio streams
 *    without amplifying background silence below the noise floor.
 * 3. Soft-knee peak limiting to prevent hard digital clipping into the Zipformer feature extractor.
 */
class AudioPreprocessor(
    private val enableDcBlocker: Boolean = true,
    private val enableAgc: Boolean = true,
    private val targetRms: Float = 0.085f,
    private val noiseGateRms: Float = 0.0035f,
    private val maxGain: Float = 4.8f,
    private val minGain: Float = 0.85f
) {
    private var prevInput: Float = 0f
    private var prevDcOutput: Float = 0f
    private var currentGain: Float = 1.0f

    @Volatile
    var lastRmsLevel: Float = 0f
        private set

    @Volatile
    var lastPeakLevel: Float = 0f
        private set

    @Volatile
    var lastAppliedGain: Float = 1.0f
        private set

    fun reset() {
        prevInput = 0f
        prevDcOutput = 0f
        currentGain = 1.0f
        lastRmsLevel = 0f
        lastPeakLevel = 0f
        lastAppliedGain = 1.0f
    }

    /**
     * Converts raw signed 16-bit PCM samples `[0, readCount)` into normalized conditioned floats.
     */
    fun processPcm16(pcmBuffer: ShortArray, readCount: Int): FloatArray {
        val count = readCount.coerceIn(0, pcmBuffer.size)
        if (count == 0) return FloatArray(0)

        val out = FloatArray(count)
        var sumSquares = 0.0
        var peak = 0f

        var xPrev = prevInput
        var yPrev = prevDcOutput

        for (i in 0 until count) {
            val raw = (pcmBuffer[i] / 32768f).coerceIn(-1.0f, 1.0f)
            val dcCleaned = if (enableDcBlocker) {
                val y = raw - xPrev + DC_BLOCKER_POLE * yPrev
                xPrev = raw
                yPrev = y
                y.coerceIn(-1.0f, 1.0f)
            } else {
                raw
            }
            out[i] = dcCleaned
            val absSample = abs(dcCleaned)
            if (absSample > peak) peak = absSample
            sumSquares += (dcCleaned * dcCleaned).toDouble()
        }

        prevInput = xPrev
        prevDcOutput = yPrev

        val rms = sqrt(sumSquares / count).toFloat()
        lastRmsLevel = (rms / 0.20f).coerceIn(0f, 1f)
        lastPeakLevel = peak

        if (!enableAgc) {
            lastAppliedGain = 1.0f
            return out
        }

        val desiredGain = if (rms > noiseGateRms) {
            val rawTargetGain = (targetRms / max(rms, 1e-4f)).coerceIn(minGain, maxGain)
            // Prevent gain from pushing frame peaks far into saturation
            val headroomGain = if (peak > 1e-4f) (0.94f / peak) else maxGain
            min(rawTargetGain, max(minGain, headroomGain))
        } else {
            // Smoothly relax gain toward unity during silence so breath/noise isn't boosted
            1.0f
        }

        val smoothing = if (desiredGain < currentGain) AGC_ATTACK else AGC_RELEASE
        val startGain = currentGain
        val endGain = startGain + smoothing * (desiredGain - startGain)
        val gainStep = (endGain - startGain) / count

        var g = startGain
        for (i in 0 until count) {
            val amplified = out[i] * g
            out[i] = softLimit(amplified)
            g += gainStep
        }

        currentGain = endGain.coerceIn(minGain, maxGain)
        lastAppliedGain = currentGain
        return out
    }

    private fun softLimit(sample: Float): Float {
        val absVal = abs(sample)
        if (absVal <= SOFT_KNEE_THRESHOLD) return sample
        val sign = if (sample >= 0f) 1f else -1f
        val excess = absVal - SOFT_KNEE_THRESHOLD
        val compressed = SOFT_KNEE_THRESHOLD + excess / (1f + excess * 3.5f)
        return sign * min(0.99f, compressed)
    }

    companion object {
        private const val DC_BLOCKER_POLE = 0.995f
        private const val AGC_ATTACK = 0.35f
        private const val AGC_RELEASE = 0.12f
        private const val SOFT_KNEE_THRESHOLD = 0.85f
    }
}
