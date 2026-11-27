# NexVora ECHORA — AI Voice & Audio Studio

*Your Voice. Your Intelligence. Your Creativity.*

Developer: **Prince AR Abdur Rahman** · Publisher: **NexVora Lab's Ofc** · Package `com.nexvora.echora` · v1.0.0
© 2026 NexVora Lab's Ofc. All Rights Reserved. (Based on Open-Source projects — see THIRD_PARTY_NOTICES.md.)

Native Android (Kotlin, Jetpack Compose, Room, DataStore, JNI/whisper.cpp). Offline-first.

**Read `STATUS.md` first**: it states exactly what is implemented, what is unverified (this source was written without a
compiler/Android SDK) and what is still missing.

## What it is
- Lossless WAV recorder (pause/resume, live waveform, foreground service) — new, `recorder/`
- Resound editor: trim/cut/split/merge/fade/speed/EQ/normalise/convert + multitrack timeline — preserved unchanged
- DSP noise reduction with before/after preview — `noise/` (classic FFT denoise, *not* AI)
- Offline Whisper (Tiny/Base/Small, Standard or Q5) Bangla + English via whisper.cpp — `transcription/offline`
- Online transcription via your backend proxy or your own key — `transcription/online`, `backend/`
- Transcript editor with versions and TXT/SRT/VTT export (never fakes timestamps)
- Room library, import/export via Storage Access Framework
- Free / Plus / Pro with **server-verified** entitlements and manual-payment workflow — `premium/`, `payment/`, `backend/`

## Layout
```
app/            Android app (package com.nexvora.echora)
  recorder/ noise/ transcription/{offline,online}/ data/ settings/ premium/ payment/ core/secure/ ui/echora/
  cpp/          JNI bridge + CMake (whisper.cpp source must be added: cpp/README.md)
backend/        Node.js service: entitlements, payments, admin verification, transcription proxy
docs/design/    ECHORA reference image
```
Resound's original packages (`feature/*`, `core/*`) keep their names to limit risk; see AUDIT.md.

Build: `BUILD_INSTRUCTIONS.md` · Privacy: `PRIVACY.md` · Payments: `PAYMENT_SETUP.md` · Licences: `THIRD_PARTY_NOTICES.md`
