package com.nexvora.echora.recorder

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavHeaderTest {
    @Test fun headerDescribesPcm16() {
        val f = File.createTempFile("wav", ".wav"); f.deleteOnExit()
        RandomAccessFile(f, "rw").use { it.write(ByteArray(44 + 1000)); EchoraWavRecorder.writeHeader(it, 1000, 44100, 1) }
        val b = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(b.array(), 0, 4)); assertEquals(1036, b.getInt(4)); assertEquals("WAVE", String(b.array(), 8, 4))
        assertEquals(1, b.getShort(20).toInt()); assertEquals(1, b.getShort(22).toInt()); assertEquals(44100, b.getInt(24))
        assertEquals(88200, b.getInt(28)); assertEquals(16, b.getShort(34).toInt()); assertEquals("data", String(b.array(), 36, 4)); assertEquals(1000, b.getInt(40))
    }
}
