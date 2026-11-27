# Build instructions

## 0. One-time: restore whisper.cpp (required for offline AI)
```bash
git clone https://github.com/ggml-org/whisper.cpp app/src/main/cpp/whisper
git -C app/src/main/cpp/whisper checkout 2eeeba56e9edd762b4b38467bab96c2517163158   # v1.8.3, pinned by WhisperNote
```
Without it the app still builds, but the AI engine reports "not in this build". CI does this automatically.

## 1. Requirements
JDK 17, Android SDK platform 35, build-tools 35.0.0, NDK `28.2.13676358`, CMake `3.22.1`, Gradle 8.9 (wrapper pinned).

## 2. Commands
```bash
./gradlew :app:testDebugUnitTest     # JVM tests
./gradlew :app:lintDebug
./gradlew :app:assembleDebug         # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:connectedDebugAndroidTest   # Room/migration tests on a device/emulator
# optional config (gradle.properties or -P):
#   echora.backendUrl=https://api.example.com   echora.pricePlus=99  echora.pricePro=199
#   echora.paymentInstructions="Send ৳{AMOUNT} to ... then enter the transaction ID"
```
Signed release (never commit secrets):
```bash
export ECHORA_KEYSTORE_PATH=/path/release.jks ECHORA_KEYSTORE_PASSWORD=... ECHORA_KEY_ALIAS=... ECHORA_KEY_PASSWORD=...
./gradlew :app:assembleRelease
```

## 3. Termux (UNTESTED — written without access to a device)
Google's `aapt2`/SDK tools are x86_64 Linux binaries, so a stock Android SDK does not run in Termux on arm64.
Reliable options: build on a PC/Codespace/GitHub Actions (recommended), or:
```bash
pkg update && pkg install -y openjdk-17 git wget unzip cmake ninja
# Community aarch64 Android SDK/aapt2 (e.g. the termux-android-sdk / lzhiyong builds) must be installed first, then:
export ANDROID_HOME=$HOME/android-sdk JAVA_HOME=$PREFIX/lib/jvm/java-17-openjdk
cd NexVora-ECHORA
echo "android.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/35.0.0/aapt2" >> ~/.gradle/gradle.properties
./gradlew :app:assembleDebug --no-daemon
```
The NDK for arm64-hosted builds is likewise non-standard; if it fails, use GitHub Actions below.

## 4. Push to GitHub (SSH)
```bash
cd NexVora-ECHORA
git init -b main && git add . && git commit -m "NexVora ECHORA 1.0.0"
git remote add origin git@github.com:rahman2008-svg/NexVora-ECHORA.git     # create the empty repo first (do not overwrite others)
git push -u origin main
```
Then open the repo → Actions → "Android build". Release secrets: `ECHORA_KEYSTORE_BASE64`, `ECHORA_KEYSTORE_PASSWORD`, `ECHORA_KEY_ALIAS`, `ECHORA_KEY_PASSWORD`.
