package com.charles.livecaptionn

import com.charles.livecaptionn.overlay.SubtitleCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleCueTest {

    @Test
    fun shortText_passesThrough() {
        assertEquals("Hello world", SubtitleCue.rolling("Hello world"))
    }

    @Test
    fun longText_keepsNewestWords() {
        val long = (1..30).joinToString(" ") { "word$it" }
        val out = SubtitleCue.rolling(long)
        assertTrue(out.length <= SubtitleCue.MAX_CUE_CHARS + 2)
        assertTrue(out.endsWith("word30"))
    }

    @Test
    fun longText_prefersSentenceBoundary() {
        val text = "This is a fairly long opening sentence that keeps going on. Short ending here."
        val out = SubtitleCue.rolling(text, maxChars = 40)
        assertEquals("Short ending here.", out)
    }

    @Test
    fun whitespace_isCollapsed() {
        assertEquals("a b c", SubtitleCue.rolling("  a   b\n c "))
    }
}
