# Implementation status (honest report)

**Nothing in the Android app has been compiled or run.** The authoring sandbox had no Android SDK/NDK/Gradle and no network.
Expect compile errors on first build; send the log back and they are normally quick fixes. Only `node --check` (syntax) ran on the backend.

| Area | State |
|---|---|
| Resound editor, multitrack, library, FFmpeg | Preserved unchanged (original code, not re-verified here) |
| Package rename → com.nexvora.echora | Done by script; unverified by compiler |
| Self-updater / install permission | Removed |
| Theme + 5-tab shell, Home, Studio, Recorder, Library, Premium, Settings, Privacy, onboarding/splash | Written; unverified |
| WAV recorder + pause/resume + foreground service + permissions + low-storage/interruption handling | Written; unverified; background behaviour needs device testing |
| Noise reduction (FFT denoise via FFmpeg `afftdn`, preview A/B) | Written; unverified; requires FFmpeg build with `afftdn`/`loudnorm` |
| whisper.cpp JNI/CMake | Bridge+CMake ported from WhisperNote; **whisper.cpp source missing** (cpp/README.md); build auto-skips native part without it |
| Model manager Tiny/Base/Small (Std+Q5) | Ported with pinned HF revision + SHA-256 (from WhisperNote, not re-verified); not run |
| Offline / Online providers, Auto mode, consent gating | Written; coordinator unit-tested **on paper only** (tests not executed) |
| Bangla transcription | **Not tested** (needs a device + model). No accuracy claim is made. |
| Online API | OpenAI transcription endpoint/model names from memory, docs unreachable — verify |
| Room, DataStore, Keystore secret store | Written; instrumented tests written, not run |
| Premium Free/Plus/Pro | Client + backend written; gating applied only to NEW features (Convert FLAC/MP3, noise voice-enhancement). Resound features are not locked |
| Payments | Manual flow only. **No bKash/gateway integration** |
| Not implemented | Batch processing/transcription UI, streaming transcription, silence-skip (VAD) toggle, UI translation to Bangla, light theme, separate `RecordingEntity`/`SettingsEntity` (folded into `AudioEntity`/DataStore) |
| Tests written | JVM: exporters, coordinator, entitlement, reference validation, noise, WAV header · Android: Room · Backend: payments. **None executed** |
| APK | **Not built** |
| GitHub push / Actions / Release | **Not done** (no access). Workflow file is ready |
