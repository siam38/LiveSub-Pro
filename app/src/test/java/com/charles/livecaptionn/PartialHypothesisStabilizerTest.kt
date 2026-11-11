package com.charles.livecaptionn

import com.charles.livecaptionn.speech.PartialHypothesisStabilizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartialHypothesisStabilizerTest {

    @Test
    fun collapseRepetitionLoops_collapsesRepeatedWords() {
        val input = "we are going to the the the the store"
        assertEquals(
            "we are going to the store",
            PartialHypothesisStabilizer.collapseRepetitionLoops(input)
        )
    }

    @Test
    fun collapseRepetitionLoops_collapsesRepeatedBigrams() {
        val input = "thank you thank you thank you thank you everyone"
        assertEquals(
            "thank you everyone",
            PartialHypothesisStabilizer.collapseRepetitionLoops(input)
        )
    }

    @Test
    fun collapseRepetitionLoops_collapsesRepeatedCjkRuns() {
        val input = "今天天气啊啊啊啊啊很好"
        assertEquals(
            "今天天气啊很好",
            PartialHypothesisStabilizer.collapseRepetitionLoops(input)
        )
    }

    @Test
    fun stabilizePartial_suppressesSingleCharTrailingDropJitter() {
        val stabilizer = PartialHypothesisStabilizer()
        val first = stabilizer.stabilizePartial("Good morning everyon")
        assertEquals("Good morning everyon", first)

        // Frame jitter drops the last character momentarily
        val second = stabilizer.stabilizePartial("Good morning everyo")
        assertEquals("Good morning everyo", second)

        // Next frame extends normally
        val third = stabilizer.stabilizePartial("Good morning everyone today")
        assertEquals("Good morning everyone today", third)
    }

    @Test
    fun shouldDispatchEarlyTranslation_triggersOnClauseAndRespectsDebounce() {
        val stabilizer = PartialHypothesisStabilizer(
            earlyTranslateIntervalMs = 900L,
            minCharsForEarlyTranslate = 14,
            minDeltaCharsForNextPreview = 10
        )

        // Short fragment: do not trigger
        assertFalse(stabilizer.shouldDispatchEarlyTranslation("Hello", nowMs = 1000L))

        // First meaningful clause (>= 4 words / >= 14 chars): triggers
        assertTrue(
            stabilizer.shouldDispatchEarlyTranslation(
                "Hello everyone welcome to",
                nowMs = 1200L
            )
        )

        // Too soon after previous preview (< 900ms): debounced
        assertFalse(
            stabilizer.shouldDispatchEarlyTranslation(
                "Hello everyone welcome to the live stream today",
                nowMs = 1600L
            )
        )

        // After debounce interval and sufficient new characters: triggers again
        assertTrue(
            stabilizer.shouldDispatchEarlyTranslation(
                "Hello everyone welcome to the live stream today",
                nowMs = 2300L
            )
        )
    }
}
