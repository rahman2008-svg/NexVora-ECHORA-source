package com.nexvora.echora.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class AiMode { OFFLINE, ONLINE, AUTO }
enum class ThemeMode { DARK, SYSTEM }

data class EchoraSettings(
    val displayName: String = "",
    val themeMode: ThemeMode = ThemeMode.DARK,
    val uiLanguage: String = "system",          // "system" | "en" | "bn"
    val recordSampleRate: Int = 44100,
    val recordChannels: Int = 1,
    val recordQuality: String = "high",         // label only; WAV is lossless, rate/channels decide size
    val exportFormat: String = "wav",
    val aiMode: AiMode = AiMode.AUTO,
    val whisperModel: String = "tiny",
    val transcribeLanguage: String = "auto",    // "auto" | "bn" | "en"
    val onlineProviderUrl: String = "",         // backend proxy base URL (overrides BuildConfig when set)
    val onlineConsentAtMs: Long = 0L,           // 0 = never consented
    val notificationsEnabled: Boolean = true,
    val onboardingDone: Boolean = false,
) { val onlineConsentGiven get() = onlineConsentAtMs > 0L }

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("echora_settings")

class SettingsRepository(private val context: Context) {
    private object K {
        val name = stringPreferencesKey("display_name")
        val theme = stringPreferencesKey("theme")
        val lang = stringPreferencesKey("ui_language")
        val rate = intPreferencesKey("rec_rate")
        val ch = intPreferencesKey("rec_channels")
        val quality = stringPreferencesKey("rec_quality")
        val exportFmt = stringPreferencesKey("export_format")
        val ai = stringPreferencesKey("ai_mode")
        val model = stringPreferencesKey("whisper_model")
        val tLang = stringPreferencesKey("transcribe_language")
        val provider = stringPreferencesKey("online_provider_url")
        val consent = longPreferencesKey("online_consent_at")
        val notif = booleanPreferencesKey("notifications")
        val onboarding = booleanPreferencesKey("onboarding_done")
    }

    val settings: Flow<EchoraSettings> = context.dataStore.data.map { p ->
        val d = EchoraSettings()
        EchoraSettings(
            displayName = p[K.name] ?: d.displayName,
            themeMode = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            uiLanguage = p[K.lang] ?: d.uiLanguage,
            recordSampleRate = p[K.rate] ?: d.recordSampleRate,
            recordChannels = p[K.ch] ?: d.recordChannels,
            recordQuality = p[K.quality] ?: d.recordQuality,
            exportFormat = p[K.exportFmt] ?: d.exportFormat,
            aiMode = p[K.ai]?.let { runCatching { AiMode.valueOf(it) }.getOrNull() } ?: d.aiMode,
            whisperModel = p[K.model] ?: d.whisperModel,
            transcribeLanguage = p[K.tLang] ?: d.transcribeLanguage,
            onlineProviderUrl = p[K.provider] ?: d.onlineProviderUrl,
            onlineConsentAtMs = p[K.consent] ?: 0L,
            notificationsEnabled = p[K.notif] ?: d.notificationsEnabled,
            onboardingDone = p[K.onboarding] ?: d.onboardingDone,
        )
    }

    suspend fun update(block: (EchoraSettings) -> EchoraSettings, current: EchoraSettings) {
        val n = block(current)
        context.dataStore.edit { p ->
            p[K.name] = n.displayName; p[K.theme] = n.themeMode.name; p[K.lang] = n.uiLanguage
            p[K.rate] = n.recordSampleRate; p[K.ch] = n.recordChannels; p[K.quality] = n.recordQuality
            p[K.exportFmt] = n.exportFormat; p[K.ai] = n.aiMode.name; p[K.model] = n.whisperModel
            p[K.tLang] = n.transcribeLanguage; p[K.provider] = n.onlineProviderUrl
            p[K.consent] = n.onlineConsentAtMs; p[K.notif] = n.notificationsEnabled
            p[K.onboarding] = n.onboardingDone
        }
    }

    /** Records explicit consent to send audio to an external service. Revocable. */
    suspend fun setOnlineConsent(current: EchoraSettings, granted: Boolean) =
        update({ it.copy(onlineConsentAtMs = if (granted) System.currentTimeMillis() else 0L) }, current)

    suspend fun clearAll() { context.dataStore.edit { it.clear() } }
}
