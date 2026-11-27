package com.nexvora.echora.noise

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NoiseReductionTest {
    @Test fun chainScalesWithStrengthAndClampsNoiseFloor() {
        val low = NoiseReduction.filterChain(0.0, null, false, false)
        val high = NoiseReduction.filterChain(1.0, NoiseProfile(-120.0, -10.0, 10), true, true)
        assertTrue(low.contains("afftdn=nr=6.0")); assertTrue(high.contains("nr=35.0"))
        assertTrue(high.contains("nf=-80.0"))                               // clamped to afftdn's range
        assertTrue(high.contains("loudnorm")); assertTrue(high.contains("acompressor")); assertFalse(low.contains("loudnorm"))
    }
    @Test fun argsNeverOverwriteInputAndPreviewIsLimited() {
        val a = NoiseReduction.args("/in.wav", "/out.wav", "x", previewSeconds = 10)
        assertEquals("/in.wav", a[a.indexOf("-i") + 1]); assertEquals("/out.wav", a.last()); assertTrue(a.containsAll(listOf("-t", "10")))
    }
    @Test fun profileSeparatesNoiseFromSpeech() {
        val f = File.createTempFile("pcm", ".f32"); f.deleteOnExit()
        val bb = ByteBuffer.allocate(320 * 4 * 100).order(ByteOrder.LITTLE_ENDIAN)
        repeat(100) { frame -> val amp = if (frame % 2 == 0) 0.001f else 0.3f; repeat(320) { i -> bb.putFloat(if (i % 2 == 0) amp else -amp) } }
        f.writeBytes(bb.array())
        val p = NoiseReduction.profileFrom(f)
        assertTrue(p.noiseFloorDb < -50 && p.speechLevelDb > -15 && p.snrDb > 40)
    }
}
