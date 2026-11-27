package com.nexvora.echora.transcription.offline

import android.app.ActivityManager
import android.content.Context
import com.nexvora.echora.transcription.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Local whisper.cpp transcription. Audio never leaves the device.
 * Only ONE model is resident in native memory at a time: switching models unloads the previous one first.
 */
class OfflineTranscriptionProvider(
    private val context: Context,
    private val models: ModelManager,
    private val engine: WhisperEngine = WhisperEngine(),
    private val modelId: () -> String,
) : TranscriptionProvider {
    override val id = "offline"
    override val isOnline = false
    private var loadedId: String? = null

    override suspend fun availability(): Availability = when {
        !WhisperEngine.isNativeAvailable ->
            Availability.Unavailable("The native whisper.cpp engine is not part of this build (see app/src/main/cpp/README.md).")
        !models.ready(modelId()) ->
            Availability.Unavailable("The ${ModelCatalog.get(modelId()).displayName} model is not downloaded.")
        else -> Availability.Ready
    }

    override suspend fun transcribe(request: TranscribeRequest, onProgress: (Int) -> Unit): TranscriptResult = mutex.withLock {
        (availability() as? Availability.Unavailable)?.let { throw TranscriptionException(it.reason) }
        val id = modelId(); val model = ModelCatalog.get(id)
        checkMemory(model)
        val pcm = File.createTempFile("echora_pcm_", ".f32", context.cacheDir)
        try {
            withContext(Dispatchers.IO) {
                onProgress(0)
                AudioDecoder(context).decode(request.audio, pcm)
            }
            val segments = ArrayList<Segment>()
            var detected: String? = null
            val job = currentCoroutineContext()
            val language = request.language.takeIf { it == "bn" || it == "en" } ?: ""   // "" => auto-detect
            val vad = if (request.skipSilence && models.vadReady) models.vadFile.path else ""
            val lang = withContext(Dispatchers.Default) {
                if (loadedId != null && loadedId != id) engine.unload()           // never hold two models
                try {
                    engine.transcribe(models.file(id).path, pcm.path, 0L, language, object : WhisperEngine.Listener {
                        override fun onProgress(progress: Int) = onProgress(progress)
                        override fun onSegment(start: Long, end: Long, text: String) { synchronized(segments) { segments += Segment(start, end, text.trim()) } }
                        override fun onLanguage(language: String) { detected = language }
                        override fun isCancelled() = !job.isActive
                    }, vad)
                } catch (e: IllegalStateException) {
                    engine.unload(); loadedId = null
                    throw TranscriptionException(e.message ?: "Transcription failed.", e)
                }
            }
            loadedId = id
            if (!job.isActive) throw kotlinx.coroutines.CancellationException("Transcription cancelled")
            onProgress(100)
            val clean = synchronized(segments) { segments.filter { it.text.isNotBlank() } }
            TranscriptResult(clean.joinToString(" ") { it.text }, lang.ifBlank { detected }, clean, "offline:whisper-${model.id}")
        } finally { pcm.delete() }
    }

    /** Releases native model memory (call on low-memory, on model deletion, or when leaving the transcription feature). */
    fun unload() { if (WhisperEngine.isNativeAvailable) runCatching { engine.unload() }; loadedId = null }

    private fun checkMemory(model: Model) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        // Rough guard: weights are mmapped/copied; need headroom of ~2x file size. Not a guarantee.
        if (info.availMem < model.bytes * 2 && info.lowMemory)
            throw TranscriptionException("Not enough free memory for the ${model.displayName} model. Close other apps or choose a smaller model.")
    }

    private companion object { val mutex = Mutex() }
}
