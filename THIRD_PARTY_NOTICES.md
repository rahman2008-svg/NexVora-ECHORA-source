# Third-party notices

NexVora ECHORA includes or depends on third-party software that is **not** relicensed
under this project's license.

## FFmpeg / FFmpegKit

ECHORA uses an FFmpegKit-compatible Android wrapper through:

`com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1`

FFmpeg and the wrapper are distributed under their own applicable open-source
licenses. The exact obligations depend on the codecs and libraries present in
the binary used for a particular build. ECHORA's Apache-2.0 license does not replace
or override those terms.

Before distributing a release APK, verify the license configuration of the
resolved FFmpeg package and include any notices or source-offer requirements
that apply to that build.

## Android / Jetpack / Kotlin

AndroidX, Jetpack Compose, Kotlin, kotlinx.coroutines, and their transitive
dependencies remain subject to their respective upstream licenses.

This file is informational and is not a substitute for the license text shipped
by each dependency.

## Resound (Apache-2.0)
ECHORA is a modified derivative of **Resound** by Typezer∅ (https://github.com/MikereDD/Resound), Apache License 2.0
(see LICENSE). Changes: renamed package/branding, new theme and shell, removed the upstream self-updater, added the
recorder, noise reduction, transcription, Room/DataStore, premium/payment layers.

## WhisperNote (MIT)
Portions (`transcription/offline/ModelManager.kt`, `AudioDecoder.kt`, `WhisperEngine.kt`, `cpp/bridge.cpp`, `cpp/CMakeLists.txt`) are adapted
from **WhisperNote** (MIT; upstream URL is not recorded in the uploaded zip). Its MIT licence text and copyright notice must stay with these files:
the original licence is kept in `licenses/WhisperNote-LICENSE.md`.

## whisper.cpp / ggml (MIT) and Whisper model weights
whisper.cpp (https://github.com/ggml-org/whisper.cpp) is MIT. Whisper model weights (OpenAI) are MIT-licensed; ggml conversions are downloaded
at runtime from the pinned Hugging Face repository revision in `ModelCatalog` (not bundled). Silero VAD (MIT) is optional.

## Room, DataStore, Lifecycle (Apache-2.0); Node backend deps
express, helmet, express-rate-limit, jsonwebtoken, multer (MIT), better-sqlite3 (MIT) — see `backend/package.json`.
