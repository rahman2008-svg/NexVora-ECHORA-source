package com.nexvora.echora.noise

import android.content.Context
import android.net.Uri
import com.nexvora.echora.transcription.offline.AudioDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Classical DSP noise reduction (FFT denoiser `afftdn` + high-pass + optional presence EQ + loudness normalisation).
 * This is NOT AI noise cancellation and the UI says so. Quality is best for steady noise (hum, hiss, fans).
 */
data class NoiseProfile(val noiseFloorDb: Double, val speechLevelDb: Double, val frames: Int) {
    val snrDb get() = speechLevelDb - noiseFloorDb
}

object NoiseReduction {
    /** Estimates noise floor (10th percentile of 20 ms frame RMS) and speech level (90th percentile) from real decoded audio. */
    suspend fun analyze(context: Context, audio: Uri): NoiseProfile = withContext(Dispatchers.IO) {
        val pcm = File.createTempFile("echora_noise_", ".f32", context.cacheDir)
        try {
            AudioDecoder(context).decode(audio, pcm, 0, 120_000.coerceAtLeast(1)).let { }   // first 2 minutes is enough
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // audio shorter than the analysis window: decode everything
            AudioDecoder(context).decode(audio, pcm)
        }
        try { profileFrom(pcm) } finally { pcm.delete() }
    }

    internal fun profileFrom(f32: File): NoiseProfile {
        val frame = 320                                     // 20 ms @ 16 kHz
        val dbs = ArrayList<Double>()
        f32.inputStream().buffered().use { ins ->
            val bytes = ByteArray(frame * 4)
            while (true) {
                var read = 0
                while (read < bytes.size) { val n = ins.read(bytes, read, bytes.size - read); if (n < 0) break; read += n }
                if (read < bytes.size) break
                val fb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                var s = 0.0; repeat(frame) { val v = fb.get().toDouble(); s += v * v }
                dbs += 20 * log10(sqrt(s / frame).coerceAtLeast(1e-6))
            }
        }
        require(dbs.isNotEmpty()) { "Audio too short to analyse." }
        dbs.sort()
        return NoiseProfile(dbs[(dbs.size * 0.10).toInt()], dbs[(dbs.size * 0.90).coerceAtMost(dbs.size - 1.0).toInt()], dbs.size)
    }

    /**
     * @param strength 0..1 (UI slider). Maps to 6..35 dB of reduction.
     * @param profile measured noise floor to seed the denoiser (clamped to afftdn's -80..-20 dB range).
     */
    fun filterChain(strength: Double, profile: NoiseProfile?, enhanceVoice: Boolean, normalize: Boolean): String {
        val s = strength.coerceIn(0.0, 1.0)
        val nr = (6 + s * 29).let { "%.1f".format(java.util.Locale.ROOT, it) }
        val nf = (profile?.noiseFloorDb ?: -45.0).coerceIn(-80.0, -20.0).let { "%.1f".format(java.util.Locale.ROOT, it) }
        val parts = mutableListOf("highpass=f=70", "afftdn=nr=$nr:nf=$nf:tn=1")
        if (enhanceVoice) { parts += "equalizer=f=3000:width_type=q:width=1.0:g=2"; parts += "acompressor=threshold=-24dB:ratio=2.5:attack=10:release=150" }
        if (normalize) parts += "loudnorm=I=-16:TP=-1.5:LRA=11"
        return parts.joinToString(",")
    }

    /** FFmpeg args. Writes 16-bit WAV so no generation loss; the original file is never touched. [previewSeconds] > 0 renders only a short preview. */
    fun args(inPath: String, outPath: String, chain: String, previewSeconds: Int = 0): List<String> =
        buildList { add("-y"); add("-i"); add(inPath); if (previewSeconds > 0) { add("-t"); add(previewSeconds.toString()) }; addAll(listOf("-vn", "-af", chain, "-c:a", "pcm_s16le", outPath)) }
}
