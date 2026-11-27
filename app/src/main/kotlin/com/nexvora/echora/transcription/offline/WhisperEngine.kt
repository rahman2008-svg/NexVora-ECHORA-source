// JNI surface adapted from WhisperNote (MIT). Native symbol names must match cpp/bridge.cpp.
package com.nexvora.echora.transcription.offline

import androidx.annotation.Keep

@Keep
class WhisperEngine {
    @Keep interface Listener {
        fun onProgress(progress: Int)
        fun onSegment(start: Long, end: Long, text: String)
        fun onLanguage(language: String) {}
        fun isCancelled(): Boolean
    }
    external fun load(model: String)
    external fun unload()
    external fun transcribe(model: String, pcm: String, resumeMs: Long, language: String, listener: Listener, vadModel: String): String

    companion object {
        /** False when the APK was built without whisper.cpp (submodule missing) — callers must check before use. */
        val isNativeAvailable: Boolean by lazy { runCatching { System.loadLibrary("echora_whisper") }.isSuccess }
    }
}
