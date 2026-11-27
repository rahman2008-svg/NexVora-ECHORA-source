# NexVora ECHORA – Source Audit (2026-11-26)

## Resound-main (primary base) – Apache-2.0
Kotlin 2.0.21, Compose M3, minSdk 26 (not 28/9 – Android 8+), arm64-v8a only.
Existing: MediaCodec waveform editor, multitrack timeline, recorder + recovery, library, settings,
FFmpegKit (com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1 – license depends on codec build; must be verified before release),
signed self-updater pinned to upstream MikereDD/Resound repo + cert/key (MUST be removed/re-pointed for ECHORA),
REQUEST_INSTALL_PACKAGES + WRITE_SETTINGS permissions (review). No Room, no DataStore, no whisper, no premium.

## WhisperNote-main (reference) – MIT
whisper.cpp JNI bridge (bridge.cpp, 154 lines), CMake, NDK 28.2, ModelManager with verified SHA-256 + pinned HF revision
(ggerganov/whisper.cpp ggml-*.bin), VAD model, segment timestamps, language detect, ML Kit translate (not needed).
**Blocker:** app/src/main/cpp/whisper/ is EMPTY (git submodule not included in the zip).

## Environment blockers
No Android SDK/NDK, no Gradle, no network (github.com / dl.google.com unreachable): cannot compile, run tests,
fetch whisper.cpp, build APK, or push to GitHub from this sandbox.
