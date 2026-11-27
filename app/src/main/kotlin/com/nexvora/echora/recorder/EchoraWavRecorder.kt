package com.nexvora.echora.recorder

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.sqrt

enum class RecStatus { IDLE, RECORDING, PAUSED, STOPPED, ERROR }

data class RecState(
    val status: RecStatus = RecStatus.IDLE,
    val elapsedMs: Long = 0,
    val level: Float = 0f,            // 0..1 RMS, for the live meter
    val levels: List<Float> = emptyList(), // rolling window for the live waveform
    val file: String? = null,
    val sampleRate: Int = 44100,
    val channels: Int = 1,
    val message: String? = null,
)

/**
 * Real 16-bit PCM WAV recorder on AudioRecord. Pause really stops capture (no silence is written).
 * The header is patched on stop, and on every ~1 s flush, so a killed process leaves a playable file.
 */
class EchoraWavRecorder {
    private val _state = MutableStateFlow(RecState())
    val state: StateFlow<RecState> = _state

    @Volatile private var paused = false
    @Volatile private var stopRequested = false
    private var thread: Thread? = null
    private var record: AudioRecord? = null

    @SuppressLint("MissingPermission") // caller verifies RECORD_AUDIO before start()
    @Synchronized
    fun start(out: File, sampleRate: Int = 44100, channels: Int = 1) {
        check(thread == null) { "Already recording" }
        require(channels == 1 || channels == 2)
        val mask = if (channels == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO
        val min = AudioRecord.getMinBufferSize(sampleRate, mask, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return fail("This sample rate/channel setup is not supported by the device.")
        if (out.parentFile?.usableSpace ?: 0L < MIN_FREE_BYTES) return fail("Not enough free storage to record.")
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, mask, AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 2, sampleRate * channels * 2))
        } catch (e: Exception) { return fail("Cannot open the microphone: ${e.message}") }
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return fail("Microphone unavailable (is another app using it?).") }
        record = rec; paused = false; stopRequested = false
        _state.value = RecState(RecStatus.RECORDING, file = out.path, sampleRate = sampleRate, channels = channels)
        thread = Thread({ loop(rec, out, sampleRate, channels, min) }, "echora-rec").also { it.start() }
    }

    @Synchronized fun pause() {
        val r = record ?: return
        if (_state.value.status != RecStatus.RECORDING) return
        paused = true
        runCatching { r.stop() }
        _state.value = _state.value.copy(status = RecStatus.PAUSED, level = 0f)
    }

    @Synchronized fun resume() {
        val r = record ?: return
        if (_state.value.status != RecStatus.PAUSED) return
        runCatching { r.startRecording() }.onFailure { return fail("Could not resume: ${it.message}") }
        paused = false
        _state.value = _state.value.copy(status = RecStatus.RECORDING)
    }

    /** Stops, finalizes the WAV header and returns the file (or null if nothing was captured). Blocks briefly. */
    fun stop(): File? {
        val t: Thread?
        synchronized(this) { stopRequested = true; t = thread }
        t?.join(3000)
        val s = _state.value
        return s.file?.let(::File)?.takeIf { it.exists() && it.length() > 44 }
    }

    private fun loop(rec: AudioRecord, out: File, rate: Int, ch: Int, minBuf: Int) {
        val buf = ShortArray(minBuf / 2)
        var bytes = 0L
        var lastFlush = 0L
        val window = ArrayDeque<Float>()
        var error: String? = null
        try {
            RandomAccessFile(out, "rw").use { raf ->
                raf.setLength(0); raf.write(ByteArray(44))
                rec.startRecording()
                val bb = ByteBuffer.allocate(buf.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                while (!stopRequested) {
                    if (paused) { Thread.sleep(40); continue }
                    val n = rec.read(buf, 0, buf.size)
                    if (n < 0) { error = "Audio input was interrupted (code $n). The recording so far was saved."; break }
                    if (n == 0) continue
                    bb.clear(); for (i in 0 until n) bb.putShort(buf[i]); raf.write(bb.array(), 0, n * 2)
                    bytes += n * 2L
                    var sum = 0.0; for (i in 0 until n) { val v = buf[i] / 32768.0; sum += v * v }
                    val rms = sqrt(sum / n)
                    val lvl = ((20 * log10(rms.coerceAtLeast(1e-5)) + 60) / 60).toFloat().coerceIn(0f, 1f)
                    window.addLast(lvl); while (window.size > 90) window.removeFirst()
                    val elapsed = bytes / (rate * ch * 2) * 1000 + (bytes / 2 % (rate * ch)) * 1000 / (rate * ch)
                    _state.value = _state.value.copy(elapsedMs = elapsed, level = lvl, levels = window.toList())
                    if (elapsed - lastFlush >= 1000) {
                        lastFlush = elapsed; writeHeader(raf, bytes, rate, ch)
                        if (out.parentFile?.usableSpace ?: 0L < MIN_FREE_BYTES / 2) { error = "Storage almost full. Recording stopped and saved."; break }
                    }
                }
                writeHeader(raf, bytes, rate, ch)
            }
        } catch (e: Exception) { error = "Recording failed: ${e.message}" }
        finally {
            runCatching { rec.stop() }; rec.release()
            synchronized(this) { thread = null; record = null }
            _state.value = _state.value.copy(
                status = if (error != null) RecStatus.ERROR else RecStatus.STOPPED, level = 0f, message = error)
        }
    }

    private fun fail(msg: String) { _state.value = RecState(RecStatus.ERROR, message = msg) }
    fun reset() { if (thread == null) _state.value = RecState() }

    companion object {
        const val MIN_FREE_BYTES = 50L * 1024 * 1024
        /** Canonical 44-byte PCM WAV header. */
        fun writeHeader(raf: RandomAccessFile, dataBytes: Long, rate: Int, ch: Int) {
            val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            h.put("RIFF".toByteArray()).putInt((36 + dataBytes).toInt()).put("WAVE".toByteArray())
            h.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(ch.toShort()).putInt(rate)
                .putInt(rate * ch * 2).putShort((ch * 2).toShort()).putShort(16)
            h.put("data".toByteArray()).putInt(dataBytes.toInt())
            val pos = raf.filePointer; raf.seek(0); raf.write(h.array()); raf.seek(pos)
        }
    }
}

/** Process-wide holder so the foreground service and the UI share one recorder. */
object RecorderHub { val recorder = EchoraWavRecorder() }
