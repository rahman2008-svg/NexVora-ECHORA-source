plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
}

android {
    ndkVersion = "28.2.13676358"
    if (file("src/main/cpp/whisper/CMakeLists.txt").exists()) {
        externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    }
    signingConfigs {
        // Release signing only when secrets are provided via environment (never committed).
        if (System.getenv("ECHORA_KEYSTORE_PATH") != null) {
            create("release") {
                storeFile = file(System.getenv("ECHORA_KEYSTORE_PATH"))
                storePassword = System.getenv("ECHORA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ECHORA_KEY_ALIAS")
                keyPassword = System.getenv("ECHORA_KEY_PASSWORD")
            }
        }
    }
    sourceSets { getByName("androidTest").assets.srcDir("$projectDir/schemas") }
    namespace = "com.nexvora.echora"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nexvora.echora"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"


        // Public output folder under Music/ and test-variant switch (overridden by devtest).
        buildConfigField("String", "OUTPUT_SUBDIR", "\"ECHORA\"")
        buildConfigField("boolean", "IS_DEVTEST", "false")

        // FFmpeg ships large native libs — restrict ABIs to keep the APK sane.
        // Backend base URL (HTTPS) for entitlement/payments/online transcription proxy.
        // Set in ~/.gradle/gradle.properties or -Pechora.backendUrl=https://... . Empty = backend features disabled.
        buildConfigField("String", "ECHORA_BACKEND_URL", "\"${providers.gradleProperty("echora.backendUrl").getOrElse("")}\"")
        // Operator-provided official payment instructions shown before paying ({AMOUNT} is replaced). Empty = manual payments disabled.
        buildConfigField("String", "ECHORA_PAYMENT_INSTRUCTIONS", "\"${providers.gradleProperty("echora.paymentInstructions").getOrElse("")}\"")
        // Displayed plan prices (BDT/month). The BACKEND enforces the real amounts (PRICE_PLUS/PRICE_PRO) - keep them equal.
        buildConfigField("int", "ECHORA_PRICE_PLUS", providers.gradleProperty("echora.pricePlus").getOrElse("99"))
        buildConfigField("int", "ECHORA_PRICE_PRO", providers.gradleProperty("echora.pricePro").getOrElse("199"))
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        // Native whisper.cpp is only configured when the submodule is present (see cpp/README).
        if (file("src/main/cpp/whisper/CMakeLists.txt").exists()) {
            externalNativeBuild { cmake { cppFlags += "-std=c++17" } }
        }
    }

    buildTypes {
        // Isolated device-test variant: separate package, label, icon colour, public folder.
        create("devtest") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".devtest"
            isDebuggable = true
            matchingFallbacks += "debug"
            buildConfigField("String", "OUTPUT_SUBDIR", "\"ECHORA DEV TEST\"")
            buildConfigField("boolean", "IS_DEVTEST", "true")
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Kotlin sources live under src/main/kotlin per repo convention.
    sourceSets["main"].kotlin.srcDir("src/main/kotlin")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)

    // ECHORA additions
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    // Host-side unit tests (JUnit 4.13.2, already in the local Gradle cache).
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    // Real org.json for host tests of the update-manifest parser (Android provides it at runtime).
    testImplementation("org.json:json:20250517")

    // ── FFmpeg engine ───────────────────────────────────────────────────────
    // ffmpeg-kit was retired by arthenica and pulled from Maven Central. This is
    // a community republish rebuilt for the 16KB page size that Android 15 / API
    // 35 requires (the old 4KB-aligned 6.0 binaries SIGBUS on modern devices).
    // It keeps the original com.arthenica.ffmpegkit wrapper API and pulls
    // smart-exception transitively. Swap the coordinate if you prefer another
    // republish (e.g. io.github.maitrungduc1410:ffmpeg-kit-audio:6.0.1).
    implementation("com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
