package com.nexvora.echora.transcription.offline

import com.nexvora.echora.data.EchoraRepository
import com.nexvora.echora.data.ModelEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface DownloadState {
    data class Running(val percent: Int, val verifying: Boolean) : DownloadState
    data class Failed(val message: String) : DownloadState
}

/** Downloads run in the app scope (survive navigation), one at a time (ModelManager serialises), each cancellable. */
class ModelDownloadController(
    private val scope: CoroutineScope,
    private val models: ModelManager,
    private val repo: EchoraRepository,
    private val unloadActive: () -> Unit,
) {
    private val _state = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val state: StateFlow<Map<String, DownloadState>> = _state
    private val jobs = HashMap<String, Job>()

    fun download(id: String) {
        if (jobs[id]?.isActive == true) return
        _state.update { it + (id to DownloadState.Running(0, false)) }
        jobs[id] = scope.launch {
            try {
                models.download(id) { p -> _state.update { it + (id to DownloadState.Running(p.coerceAtLeast(0), p >= 100)) } }
                val m = ModelCatalog.get(id)
                repo.registerModel(ModelEntity(id, m.family, models.file(id).path, m.bytes))
                _state.update { it - id }
            } catch (e: CancellationException) { _state.update { it - id }; throw e }
            catch (e: Exception) { _state.update { it + (id to DownloadState.Failed(e.message ?: "Download failed. Check your connection and try again.")) } }
        }
    }

    fun cancel(id: String) { jobs[id]?.cancel() }

    fun delete(id: String) {
        unloadActive()                                  // free native memory before removing the file
        if (models.delete(id)) scope.launch { repo.unregisterModel(id) }
    }

    fun clearError(id: String) = _state.update { it - id }
}
