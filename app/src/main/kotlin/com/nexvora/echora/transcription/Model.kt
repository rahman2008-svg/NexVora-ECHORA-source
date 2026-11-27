package com.nexvora.echora.transcription

import android.net.Uri

/** A timed piece of transcript. Only created from timestamps actually returned by an engine. */
data class Segment(val startMs: Long, val endMs: Long, val text: String)

data class TranscriptResult(
    val text: String,
    val language: String?,
    val segments: List<Segment>,     // empty when the engine returned no timestamps (never fabricated)
    val engine: String,              // offline:whisper-tiny | online:<model>
)

/** [language]: "auto" | "bn" | "en" (ISO-639-1 as understood by whisper). */
data class TranscribeRequest(val audio: Uri, val language: String = "auto", val skipSilence: Boolean = false)

sealed interface Availability {
    data object Ready : Availability
    data class Unavailable(val reason: String) : Availability
}

interface TranscriptionProvider {
    val id: String
    val isOnline: Boolean
    suspend fun availability(): Availability
    /** Suspends until done. Cancel the calling coroutine to cancel. [onProgress] is 0..100. */
    suspend fun transcribe(request: TranscribeRequest, onProgress: (Int) -> Unit): TranscriptResult
}

class ConsentRequiredException : Exception("Online transcription sends your audio to an external service. Please review and accept the notice first.")
class TranscriptionException(message: String, cause: Throwable? = null) : Exception(message, cause)
