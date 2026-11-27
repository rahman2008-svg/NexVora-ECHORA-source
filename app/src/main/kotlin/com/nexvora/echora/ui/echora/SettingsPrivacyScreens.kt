package com.nexvora.echora.ui.echora

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexvora.echora.core.secure.SecretKeys
import com.nexvora.echora.di.AppContainer
import com.nexvora.echora.feature.about.AboutDialog
import com.nexvora.echora.settings.AiMode
import com.nexvora.echora.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun EchoraSettingsScreen(c: AppContainer, onModels: () -> Unit, onPrivacy: () -> Unit) {
    val st by c.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var name by remember(st.displayName) { mutableStateOf(st.displayName) }
    var url by remember(st.onlineProviderUrl) { mutableStateOf(st.onlineProviderUrl) }
    var key by remember { mutableStateOf("") }; var keySaved by remember { mutableStateOf(!c.secrets.get(SecretKeys.ONLINE_API_KEY).isNullOrBlank()) }
    var showAbout by remember { mutableStateOf(false) }; var showConsent by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val urlOk = url.isBlank() || url.trim().startsWith("https://")

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        EchoraTopBar("Settings")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle("Profile")
                OutlinedTextField(name, { name = it.take(40); c.updateSettings { s -> s.copy(displayName = it.take(40).trim()) } }, Modifier.fillMaxWidth(), label = { Text("Your name (shown on Home)") }, singleLine = true)
            }
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle("Recording")
                Text("Sample rate", color = TextMid)
                SegmentedPills(listOf(16000 to "16 kHz", 44100 to "44.1 kHz", 48000 to "48 kHz"), st.recordSampleRate, { r -> c.updateSettings { it.copy(recordSampleRate = r) } })
                Text("Channels", color = TextMid, modifier = Modifier.padding(top = 8.dp))
                SegmentedPills(listOf(1 to "Mono", 2 to "Stereo"), st.recordChannels, { ch -> c.updateSettings { it.copy(recordChannels = ch) } })
                Text("Recordings are saved as lossless 16-bit WAV. Stereo needs a device with a stereo microphone setup.", color = TextLo, fontSize = 11.sp)
            }
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle("Transcription")
                SegmentedPills(listOf(AiMode.OFFLINE to "Offline", AiMode.ONLINE to "Online", AiMode.AUTO to "Auto"), st.aiMode, { m -> c.updateSettings { it.copy(aiMode = m) } })
                SegmentedPills(listOf("bn" to "বাংলা", "en" to "English", "auto" to "Auto"), st.transcribeLanguage, { l -> c.updateSettings { it.copy(transcribeLanguage = l) } }, Modifier.padding(top = 8.dp))
                InfoRow("Default model", com.nexvora.echora.transcription.offline.ModelCatalog.get(st.whisperModel).displayName)
                OutlineAction("Manage models", onModels)
            }
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle("Export")
                Text("Default conversion format", color = TextMid)
                SegmentedPills(listOf("wav" to "WAV", "m4a" to "M4A", "flac" to "FLAC", "mp3" to "MP3"), st.exportFormat, { f -> c.updateSettings { it.copy(exportFormat = f) } })
            }
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle("Online AI")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(st.onlineConsentGiven, { on -> if (on) showConsent = true else scope.launch { c.settingsRepository.setOnlineConsent(st.copy(), false) } })
                    Spacer(Modifier.width(8.dp)); Text("Allow sending audio to an online service", color = TextHi)
                }
                OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Backend URL (https://…)") }, singleLine = true, isError = !urlOk,
                    supportingText = { Text(if (urlOk) "Recommended: your own backend holds the provider key. Leave empty to use your own key below." else "Must start with https://") })
                OutlineAction("Save URL", { if (urlOk) c.updateSettings { it.copy(onlineProviderUrl = url.trim()) } }, enabled = urlOk)
                OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text(if (keySaved) "API key saved (hidden)" else "Your own OpenAI API key (optional)") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineAction("Save key", { if (key.isNotBlank()) { c.secrets.put(SecretKeys.ONLINE_API_KEY, key.trim()); key = ""; keySaved = true; msg = "Key stored encrypted on this device." } }, enabled = key.isNotBlank())
                    if (keySaved) OutlineAction("Remove key", { c.secrets.remove(SecretKeys.ONLINE_API_KEY); keySaved = false })
                }
                Text("A key you enter is encrypted with the Android Keystore and only used when you pick Online mode. Never ship a shared production key inside an app.", color = TextLo, fontSize = 11.sp)
                msg?.let { Text(it, color = Echora.Cyan, fontSize = 12.sp) }
            }
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle("General")
                InfoRow("Theme", "ECHORA Dark")
                InfoRow("Interface language", "English")
                Text("Bangla speech recognition is supported; the app's own menus are English for now. The recording notification is required by Android while recording in the background.", color = TextLo, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) { OutlineAction("Privacy & data", onPrivacy); OutlineAction("About", { showAbout = true }) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (showAbout) AboutDialog { showAbout = false }
    if (showConsent) OnlineConsentDialog({ showConsent = false }, { showConsent = false; scope.launch { c.settingsRepository.setOnlineConsent(c.settings.value, true) } })
}

@Composable
fun PrivacyScreen(c: AppContainer, onBack: () -> Unit) {
    val st by c.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<Pair<String, suspend () -> Unit>?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }

    suspend fun deleteRecordings() { c.repository.audio().first().forEach { c.repository.deleteAudio(it.id) } }
    suspend fun deleteModels() { c.offlineProvider.unload(); c.modelManager.deleteAll(); c.repository.models().first().forEach { c.repository.unregisterModel(it.id) } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        EchoraTopBar("Privacy & Data", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle("How your audio is handled")
                Text("• Recording, editing, noise reduction and Offline transcription all run on this device.\n• Online transcription uploads audio to an external service only when you select Online (or confirm it in Auto) and have accepted the notice.\n• No analytics SDKs, no Firebase/Supabase, no automatic uploads.\n• Models, recordings and transcripts live in app-private storage; backup is disabled.\n• Permissions: microphone (only while you record), notifications (recording status), internet (model download, optional online AI, premium).", color = TextMid, fontSize = 13.sp)
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(st.onlineConsentGiven, { on -> scope.launch { c.settingsRepository.setOnlineConsent(c.settings.value, on) } })
                    Spacer(Modifier.width(8.dp)); Text(if (st.onlineConsentGiven) "Online upload consent: given" else "Online upload consent: not given", color = TextHi)
                }
            }
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle("Delete local data")
                val rows = listOf<Triple<String, String, suspend () -> Unit>>(
                    Triple("Delete all transcripts", "All transcripts and versions will be removed.", { c.repository.deleteAllTranscripts() }),
                    Triple("Delete all recordings", "Recordings made in ECHORA will be deleted. Imported originals are not touched.", { deleteRecordings() }),
                    Triple("Delete downloaded models", "Whisper models will be removed (you can download them again).", { deleteModels() }),
                    Triple("Remove API key", "Your stored online API key will be erased.", { c.secrets.remove(SecretKeys.ONLINE_API_KEY) }),
                    Triple("Delete ALL local data", "Everything above plus settings, payment history and cached plan. Your server-side plan is unaffected.", {
                        c.repository.deleteAllTranscripts(); deleteRecordings(); deleteModels(); c.secrets.remove(SecretKeys.ONLINE_API_KEY)
                        c.repository.clearPayments(); c.entitlements.clearLocal(); c.settingsRepository.clearAll() }),
                )
                rows.forEach { (label, desc, action) -> OutlineAction(label, { confirm = desc to action }, Modifier.fillMaxWidth().padding(vertical = 2.dp)) }
                msg?.let { Text(it, color = Echora.Cyan, fontSize = 12.sp) }
            }
        }
    }
    confirm?.let { (desc, action) ->
        AlertDialog({ confirm = null }, title = { Text("Are you sure?") }, text = { Text(desc + "\n\nThis cannot be undone.") },
            confirmButton = { TextButton({ scope.launch { runCatching { action() }.onSuccess { msg = "Done." }.onFailure { msg = "Failed: ${it.message}" }; confirm = null } }) { Text("Delete") } },
            dismissButton = { TextButton({ confirm = null }) { Text("Cancel") } })
    }
}
