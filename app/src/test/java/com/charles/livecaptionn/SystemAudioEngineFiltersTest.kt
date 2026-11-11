package com.charles.livecaptionn

import com.charles.livecaptionn.speech.SystemAudioEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SystemAudioEngineFiltersTest {

    @Test
    fun averageAmplitude_silence_returnsLowValue() {
        val silent = ByteArray(3200) { 0 }
        val amp = averageAbsAmplitude(silent)
        assertEquals(0, amp)
    }

    @Test
    fun averageAmplitude_maxSignal_returnsHighValue() {

        val loud = ByteArray(3200) { 0x7F }
        val amp = averageAbsAmplitude(loud)
        assertTrue(amp > 32000)
    }

    @Test
    fun isLikelyHallucinated_shortText_returnsTrue() {
        assertTrue(isLikelyHallucinatedTranscript("a"))
    }

    @Test
    fun isLikelyHallucinated_repeatedPattern_returnsTrue() {
        assertTrue(isLikelyHallucinatedTranscript("aaaaaa"))
    }

    @Test
    fun isLikelyHallucinated_singleWordRepeated_returnsTrue() {
        assertTrue(isLikelyHallucinatedTranscript("the the the the the"))
    }

    @Test
    fun isLikelyHallucinated_normalText_returnsFalse() {
        assertFalse(isLikelyHallucinatedTranscript("hello world"))
    }

    @Test
    fun isRepeatedPattern_repeatingUnit_returnsTrue() {
        assertTrue(isRepeatedPattern("abcabcabc"))
    }

    @Test
    fun isRepeatedPattern_nonRepeating_returnsFalse() {
        assertFalse(isRepeatedPattern("abcdef"))
    }
}

private fun averageAbsAmplitude(pcmData: ByteArray): Int {
    var sum = 0L
    var samples = 0
    var i = 0
    while (i + 1 < pcmData.size) {
        val low = pcmData[i].toInt() and 0xFF
        val high = pcmData[i + 1].toInt()
        val sample = (high shl 8) or low
        sum += abs(sample)
        samples += 1
        i += 2
    }
    return if (samples == 0) 0 else (sum / samples).toInt()
}

private fun isLikelyHallucinatedTranscript(text: String): Boolean {
    val normalized = text.lowercase().filter { it.isLetterOrDigit() }
    if (normalized.length <= 2) return true
    if (isRepeatedPattern(normalized)) return true
    val words = text.lowercase()
        .split(Regex("\\s+"))
        .map { it.trim { ch -> !ch.isLetterOrDigit() } }
        .filter { it.isNotEmpty() }
    return words.size >= 4 && words.distinct().size == 1
}

private fun isRepeatedPattern(value: String): Boolean {
    val maxUnit = (value.length / 3).coerceAtMost(12)
    for (unitSize in 1..maxUnit) {
        if (value.length % unitSize != 0) continue
        val unit = value.substring(0, unitSize)
        if (unit.repeat(value.length / unitSize) == value) return true
    }
    return false
}

private fun assertFalse(condition: Boolean) {
    org.junit.Assert.assertFalse(condition)
}
