package com.nexvora.echora.transcription

import org.junit.Assert.*
import org.junit.Test

class ExportersTest {
    private val segs = listOf(Segment(0, 1500, " Hello "), Segment(3_723_004, 3_725_000, "বাংলা লেখা"))

    @Test fun srtUsesCommaAndRealTimestamps() {
        val s = TranscriptExporter.toSrt(segs)
        assertTrue(s.startsWith("1\n00:00:00,000 --> 00:00:01,500\nHello\n\n2\n"))
        assertTrue(s.contains("01:02:03,004 --> 01:02:05,000"))
        assertTrue(s.contains("বাংলা লেখা"))       // Unicode preserved
    }
    @Test fun vttUsesDotAndHeader() {
        val v = TranscriptExporter.toVtt(segs)
        assertTrue(v.startsWith("WEBVTT\n\n00:00:00.000 --> 00:00:01.500\nHello"))
    }
    @Test fun refusesToFabricateTimestamps() {
        assertThrows(IllegalArgumentException::class.java) { TranscriptExporter.toSrt(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { TranscriptExporter.toVtt(emptyList()) }
    }
    @Test fun txtTrimsAndEndsWithNewline() = assertEquals("hi\n", TranscriptExporter.toTxt("  hi  "))
    @Test fun segmentsJsonRoundTrip() {
        assertEquals(segs.map { it.copy(text = it.text) }, SegmentsJson.decode(SegmentsJson.encode(segs)))
        assertTrue(SegmentsJson.decode("not json").isEmpty())
    }
}
