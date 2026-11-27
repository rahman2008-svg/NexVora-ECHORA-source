package com.nexvora.echora.transcription

import android.content.Context
import android.net.Uri
import com.nexvora.echora.data.TranscriptEntity
import com.nexvora.echora.di.AppContainer
import com.nexvora.echora.settings.AiMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TranscribeUi(
    val audioUri: Uri? = null, val audioName: String? = null, val audioId: Long? = null,
    val running: Boolean = false, val progress: Int = 0, val engineLabel: String? = null,
    val result: TranscriptResult? = null, val transcriptId: Long? = null,
    val error: String? = null,
    /** Non-null => UI must show a decision/consent dialog; nothing has been sent anywhere. */
    val pending: Resolution? = null,
)

/** Runs transcription in the application scope so leaving the screen does not cancel it; the user cancels explicitly. */
class TranscribeController(private val context: Context, private val c: AppContainer) {
    private val _ui = MutableStateFlow(TranscribeUi())
    val ui: StateFlow<TranscribeUi> = _ui
    private var job: Job? = null

    fun select(uri: Uri, name: String? = null, audioId: Long? = null) {
        if (_ui.value.running) return
        _ui.value = TranscribeUi(audioUri = uri, audioName = name ?: UriFiles.displayName(context, uri), audioId = audioId)
    }

    fun dismissPending() = _ui.update { it.copy(pending = null) }

    /** @param onlineConfirmed true only when the user just accepted "use online this time" / the upload notice. */
    fun start(onlineConfirmed: Boolean = false) {
        val s = _ui.value; val uri = s.audioUri ?: return
        if (s.running) return
        _ui.update { it.copy(error = null, pending = null, result = null, transcriptId = null, progress = 0) }
        job = c.appScope.launch {
            val st = c.settings.value
            when (val r = c.coordinator.resolve(st.aiMode, st.onlineConsentGiven, onlineConfirmed)) {
                is Resolution.Use -> run(r.provider, uri, st.transcribeLanguage, st.aiMode)
                else -> _ui.update { it.copy(pending = r) }
            }
        }
    }

    private suspend fun run(p: TranscriptionProvider, uri: Uri, lang: String, mode: AiMode) {
        _ui.update { it.copy(running = true, engineLabel = if (p.isOnline) "Online (audio is uploaded)" else "Offline (on this device)") }
        try {
            val res = p.transcribe(TranscribeRequest(uri, lang)) { pr -> _ui.update { it.copy(progress = pr) } }
            val s = _ui.value
            if (res.text.isBlank()) { _ui.update { it.copy(running = false, error = "No speech was detected.") }; return }
            val id = c.repository.saveTranscript(TranscriptEntity(
                audioId = s.audioId, title = s.audioName ?: "Transcript", language = res.language ?: lang, engine = res.engine,
                text = res.text, segmentsJson = SegmentsJson.encode(res.segments)))
            _ui.update { it.copy(running = false, result = res, transcriptId = id, progress = 100) }
        } catch (e: CancellationException) {
            _ui.update { it.copy(running = false, error = "Cancelled.") }; throw e
        } catch (e: ConsentRequiredException) {
            _ui.update { it.copy(running = false, pending = Resolution.NeedConsent) }
        } catch (e: Exception) {
            _ui.update { it.copy(running = false, error = e.message ?: "Transcription failed.") }
        }
    }

    fun cancel() { job?.cancel() }

    /** Saves edited text as a new version; segments are kept only if text is unchanged (timestamps would no longer match). */
    suspend fun saveEdit(newText: String) {
        val id = _ui.value.transcriptId ?: return
        val base = c.repository.getTranscript(id) ?: return
        val segs = if (newText.trim() == base.text.trim()) base.segmentsJson else "[]"
        val newId = c.repository.saveTranscriptVersion(base.copy(segmentsJson = segs), newText)
        _ui.update { it.copy(transcriptId = newId, result = it.result?.copy(text = newText, segments = if (segs == "[]") emptyList() else it.result.segments)) }
    }
}
