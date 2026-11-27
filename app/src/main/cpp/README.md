# whisper.cpp source (NOT included in the uploaded zip)

The WhisperNote zip shipped an EMPTY `whisper/` directory (git submodule not included). Native transcription
cannot build until it is restored. WhisperNote pinned **whisper.cpp v1.8.3**, commit
`2eeeba56e9edd762b4b38467bab96c2517163158` (the bridge uses the VAD API, so keep >= that version).

From the repository root:

    git clone https://github.com/ggml-org/whisper.cpp app/src/main/cpp/whisper
    git -C app/src/main/cpp/whisper checkout 2eeeba56e9edd762b4b38467bab96c2517163158
    # or, in a git repo:  git submodule add https://github.com/ggml-org/whisper.cpp app/src/main/cpp/whisper

`app/build.gradle.kts` enables the native build automatically once `whisper/CMakeLists.txt` exists.
Without it the app still builds; offline transcription then reports "native engine not built".
