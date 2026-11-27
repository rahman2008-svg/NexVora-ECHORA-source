/*
 * file:    AppContainer.kt
 * desc:    Manual DI container (no Hilt/Koin per repo convention). Resound singletons + ECHORA services.
 */
package com.nexvora.echora.di

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.nexvora.echora.BuildConfig
import com.nexvora.echora.core.audio.MediaCodecWaveformExtractor
import com.nexvora.echora.core.audio.WaveformExtractor
import com.nexvora.echora.core.ffmpeg.FFmpegKitRunner
import com.nexvora.echora.core.ffmpeg.FFmpegRunner
import com.nexvora.echora.core.secure.KeystoreSecretStore
import com.nexvora.echora.core.secure.SecretKeys
import com.nexvora.echora.core.secure.SecretStore
import com.nexvora.echora.data.EchoraDatabase
import com.nexvora.echora.data.EchoraRepository
import com.nexvora.echora.feature.record.AndroidRecorder
import com.nexvora.echora.feature.record.Recorder
import com.nexvora.echora.payment.ManualPaymentProvider
import com.nexvora.echora.premium.BackendApi
import com.nexvora.echora.premium.EntitlementRepository
import com.nexvora.echora.premium.HttpBackendApi
import com.nexvora.echora.settings.EchoraSettings
import com.nexvora.echora.settings.SettingsRepository
import com.nexvora.echora.transcription.TranscribeController
import com.nexvora.echora.transcription.TranscriptionCoordinator
import com.nexvora.echora.transcription.offline.ModelManager
import com.nexvora.echora.transcription.offline.OfflineTranscriptionProvider
import com.nexvora.echora.transcription.online.OnlineEndpoint
import com.nexvora.echora.transcription.online.OnlineTranscriptionProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AppContainer(private val appContext: Context) {
    // ---- Resound (unchanged) ----
    val ffmpeg: FFmpegRunner by lazy { FFmpegKitRunner() }
    val waveformExtractor: WaveformExtractor by lazy { MediaCodecWaveformExtractor(appContext) }
    val playerFactory: com.nexvora.echora.core.audio.AudioPlayerFactory =
        com.nexvora.echora.core.audio.PlayerRuntimeProvider.playerFactory()
    val recorder: Recorder by lazy { AndroidRecorder(appContext) }
    val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
    val recordingController: com.nexvora.echora.feature.record.RecordingController by lazy {
        com.nexvora.echora.feature.record.RecordingController(recorder, appScope, kotlinx.coroutines.Dispatchers.IO)
    }
    private val timelineScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate
    )
    val timelineSession: com.nexvora.echora.feature.timeline.TimelineSession by lazy {
        com.nexvora.echora.feature.timeline.TimelineDraftBinding.attach(appContext, timelineScope, waveformExtractor)
    }

    // ---- ECHORA ----
    val appContextPublic: Context get() = appContext
    val database: EchoraDatabase by lazy { EchoraDatabase.build(appContext) }
    val repository: EchoraRepository by lazy { EchoraRepository(database) }
    val secrets: SecretStore by lazy { KeystoreSecretStore(appContext) }
    val settingsRepository by lazy { SettingsRepository(appContext) }
    val settings: StateFlow<EchoraSettings> by lazy {
        settingsRepository.settings.stateIn(appScope, SharingStarted.Eagerly, EchoraSettings())
    }

    val modelManager: ModelManager by lazy { ModelManager(appContext) }

    /** Backend base URL: user-entered override in Settings, else the build-time default. HTTPS only. */
    fun backendUrl(): String = settings.value.onlineProviderUrl.ifBlank { BuildConfig.ECHORA_BACKEND_URL }.trim()
    val backendApi: BackendApi? get() = backendUrl().takeIf { it.startsWith("https://") }?.let { HttpBackendApi(it, secrets) }
    private val dynamicApi = com.nexvora.echora.premium.DynamicBackendApi { backendApi }
    val entitlements: EntitlementRepository by lazy { EntitlementRepository(repository, dynamicApi, secrets) }
    val payments: ManualPaymentProvider by lazy {
        ManualPaymentProvider(repository, dynamicApi, entitlements, BuildConfig.ECHORA_PAYMENT_INSTRUCTIONS.ifBlank { null })
    }

    /** Plan chosen on the Premium screen, consumed by the Payment screen. */
    @Volatile var pendingPlan: com.nexvora.echora.premium.Plan = com.nexvora.echora.premium.Plan.PLUS

    fun networkUp(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    val offlineProvider: OfflineTranscriptionProvider by lazy {
        OfflineTranscriptionProvider(appContext, modelManager) { settings.value.whisperModel }
    }
    val onlineProvider: OnlineTranscriptionProvider by lazy {
        OnlineTranscriptionProvider(
            appContext, secrets, ffmpeg,
            endpoint = {
                val url = backendUrl()
                when {
                    url.startsWith("https://") -> OnlineEndpoint.Proxy(url, { secrets.get(SecretKeys.ENTITLEMENT_TOKEN) }, { entitlements.deviceId() })
                    !secrets.get(SecretKeys.ONLINE_API_KEY).isNullOrBlank() -> OnlineEndpoint.OpenAiDirect()
                    else -> null
                }
            },
            consentGranted = { settings.value.onlineConsentGiven },
            isNetworkUp = ::networkUp,
        )
    }
    val modelDownloads: com.nexvora.echora.transcription.offline.ModelDownloadController by lazy {
        com.nexvora.echora.transcription.offline.ModelDownloadController(appScope, modelManager, repository) { offlineProvider.unload() }
    }
    val coordinator: TranscriptionCoordinator by lazy { TranscriptionCoordinator(offlineProvider, onlineProvider) }
    val transcribeController: TranscribeController by lazy { TranscribeController(appContext, this) }
}
