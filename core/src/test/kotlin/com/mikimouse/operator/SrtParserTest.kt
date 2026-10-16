package com.mikimouse.operator

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SrtParserTest {
    private val sample = """
        1
        00:00:01,000 --> 00:00:03,500
        Hello world

        2
        00:00:04,000 --> 00:00:06,000
        Second line
        still second
    """.trimIndent()

    @Test fun parsesCues() {
        val cues = SrtParser.parseText(sample)
        assertEquals(2, cues.size)
        assertEquals(1000L, cues[0].startMs)
        assertEquals(3500L, cues[0].endMs)
        assertEquals("Hello world", cues[0].text)
        assertTrue(cues[1].text.contains("still second"))
    }

    @Test fun badBlocksSkipped() {
        assertTrue(SrtParser.parseText("garbage\n\nno times here").isEmpty())
    }
}
