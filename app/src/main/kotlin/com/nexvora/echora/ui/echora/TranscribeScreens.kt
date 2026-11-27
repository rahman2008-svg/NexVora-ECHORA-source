package com.nexvora.echora.ui.echora

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexvora.echora.data.TranscriptEntity
import com.nexvora.echora.di.AppContainer
import com.nexvora.echora.settings.AiMode
import com.nexvora.echora.transcription.*
import com.nexvora.echora.transcription.offline.DownloadState
import com.nexvora.echora.transcription.offline.ModelCatalog
import com.nexvora.echora.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun TranscribeScreen(c: AppContainer, onBack: () -> Unit, onModels: () -> Unit, onPremium: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val ui by c.transcribeController.ui.collectAsStateWithLifecycle()
    val st by c.settings.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }; c.transcribeController.select(uri) }
    }
    val cur = ModelCatalog.get(st.whisperModel)
    var showConsent by remember { mutableStateOf(false) }
    var consentThenStart by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        EchoraTopBar("Transcription", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SegmentedPills(listOf("bn" to "বাংলা", "en" to "English", "auto" to "Auto-detect"), st.transcribeLanguage, { l -> c.updateSettings { it.copy(transcribeLanguage = l) } })
            SegmentedPills(listOf(AiMode.OFFLINE to "Offline", AiMode.ONLINE to "Online", AiMode.AUTO to "Auto"), st.aiMode, { m -> c.updateSettings { it.copy(aiMode = m) } })
            Text(when (st.aiMode) {
                AiMode.OFFLINE -> "Offline: runs on this device with the downloaded Whisper model. Nothing is uploaded."
                AiMode.ONLINE -> "Online: your audio is uploaded to an external service to be transcribed."
                AiMode.AUTO -> "Auto: uses the offline model when installed; otherwise asks before using Online."
            }, color = TextMid, fontSize = 13.sp)
            SegmentedPills(ModelCatalog.families.map { it to it.replaceFirstChar { ch -> ch.uppercase() } }, cur.family,
                { fam -> c.updateSettings { it.copy(whisperModel = ModelCatalog.variant(fam, cur.quantized).id) } })
            Text("${cur.displayName} • ${cur.size} • " + if (c.modelManager.ready(cur.id)) "installed" else "not downloaded", color = TextLo, fontSize = 12.sp)
            Text("Tiny is fastest, Base is balanced, Small has higher accuracy potential. Bangla accuracy varies; small models can make mistakes — review the text.", color = TextLo, fontSize = 12.sp)

            GlassCard(Modifier.fillMaxWidth()) {
                Text(ui.audioName ?: "No audio selected", color = TextHi)
                OutlineAction("Choose audio file", { picker.launch(arrayOf("audio/*")) }, Modifier.padding(top = 8.dp), Icons.Outlined.FileUpload, enabled = !ui.running)
            }
            if (ui.running) {
                LinearProgressIndicator(progress = { ui.progress / 100f }, Modifier.fillMaxWidth(), color = Echora.Cyan)
                Text("${ui.engineLabel} • ${ui.progress}%", color = TextMid)
                OutlineAction("Cancel", { c.transcribeController.cancel() })
            } else GradientButton("Transcribe", { c.transcribeController.start() }, Modifier.fillMaxWidth(), Icons.Outlined.Translate, enabled = ui.audioUri != null)
            ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            ui.transcriptId?.let { id -> TranscriptEditor(id, c, Modifier.fillMaxWidth()) }
            Spacer(Modifier.height(24.dp))
        }
    }

    when (val p = ui.pending) {
        is Resolution.NeedModel -> AlertDialog({ c.transcribeController.dismissPending() }, title = { Text("Offline model needed") },
            text = { Text(p.reason + "\n\nDownload a model once (Tiny is about ${ModelCatalog.get("tiny").size}); after that transcription works without internet.") },
            confirmButton = { TextButton({ c.transcribeController.dismissPending(); onModels() }) { Text("Open Model Manager") } },
            dismissButton = { TextButton({ c.transcribeController.dismissPending() }) { Text("Cancel") } })
        Resolution.NeedConsent -> LaunchedEffect(Unit) { showConsent = true; consentThenStart = true; c.transcribeController.dismissPending() }
        is Resolution.OfferOnline -> AlertDialog({ c.transcribeController.dismissPending() }, title = { Text("Offline isn't available") },
            text = { Text(p.offlineReason + "\n\n" + if (p.onlineUsable) "You can transcribe this once using the online service instead. Your audio would be uploaded." else "Online is also unavailable: ${p.onlineReason}") },
            confirmButton = {
                if (p.onlineUsable) TextButton({ c.transcribeController.dismissPending(); if (st.onlineConsentGiven) c.transcribeController.start(true) else { showConsent = true; consentThenStart = true } }) { Text("Use online this time") }
                else TextButton({ c.transcribeController.dismissPending(); onModels() }) { Text("Open Model Manager") }
            },
            dismissButton = { Row { if (p.onlineUsable) TextButton({ c.transcribeController.dismissPending(); onModels() }) { Text("Download model") }; TextButton({ c.transcribeController.dismissPending() }) { Text("Cancel") } } })
        is Resolution.Unavailable -> AlertDialog({ c.transcribeController.dismissPending() }, title = { Text("Can't transcribe online") }, text = { Text(p.reason) },
            confirmButton = { TextButton({ c.transcribeController.dismissPending() }) { Text("OK") } })
        else -> {}
    }
    if (showConsent) OnlineConsentDialog(onDecline = { showConsent = false; consentThenStart = false }, onAccept = {
        showConsent = false
        scope.launch { c.settingsRepository.setOnlineConsent(c.settings.value, true); if (consentThenStart) c.transcribeController.start(true); consentThenStart = false }
    })
}

@Composable
fun OnlineConsentDialog(onDecline: () -> Unit, onAccept: () -> Unit) = AlertDialog(onDecline, title = { Text("Send audio to an online service?") },
    text = { Text("Online transcription uploads your audio file to an external server (your configured backend, which forwards it to a speech-to-text provider, or the provider you set up with your own key). The audio leaves this device. Do not use it for audio you are not allowed to share. You can withdraw this consent any time in Settings → Privacy.") },
    confirmButton = { TextButton(onAccept) { Text("I understand, allow online") } }, dismissButton = { TextButton(onDecline) { Text("Keep it offline") } })

@Composable
fun TranscriptDialog(t: TranscriptEntity, c: AppContainer, onDismiss: () -> Unit) = Dialog(onDismiss) {
    Surface(shape = MaterialTheme.shapes.large, color = Panel) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            TranscriptEditor(t.id, c, Modifier.fillMaxWidth(), onDeleted = onDismiss)
            TextButton(onDismiss, Modifier.align(Alignment.End)) { Text("Close") }
        }
    }
}

/** Edit / search / copy / select-all / rename / delete / share / export (TXT, SRT, VTT). Edits are saved as NEW versions. */
@Composable
fun TranscriptEditor(transcriptId: Long, c: AppContainer, modifier: Modifier = Modifier, onDeleted: () -> Unit = {}) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope(); val clip = LocalClipboardManager.current
    var entity by remember(transcriptId) { mutableStateOf<TranscriptEntity?>(null) }
    LaunchedEffect(transcriptId) { entity = c.repository.getTranscript(transcriptId) }
    val t = entity ?: return
    var tf by remember(t.id) { mutableStateOf(TextFieldValue(t.text)) }
    var query by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf(false) }; var confirmDelete by remember { mutableStateOf(false) }
    val segments = remember(t.segmentsJson) { SegmentsJson.decode(t.segmentsJson) }
    val dirty = tf.text != t.text

    fun write(uri: Uri?, content: () -> String) {
        if (uri == null) return
        runCatching { ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(content().toByteArray(Charsets.UTF_8)) } }.onSuccess { msg = "Exported." }.onFailure { msg = "Export failed: ${it.message}" }
    }
    val txt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { write(it) { TranscriptExporter.toTxt(tf.text) } }
    val srt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-subrip")) { write(it) { TranscriptExporter.toSrt(segments) } }
    val vtt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/vtt")) { write(it) { TranscriptExporter.toVtt(segments) } }
    val timedOk = segments.isNotEmpty() && !dirty

    GlassCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(t.title, color = TextHi); Text("${t.language} • ${t.engine} • v${t.version}", color = TextLo, fontSize = 11.sp) }
            IconButton({ renaming = true }) { Icon(Icons.Outlined.Edit, "Rename") }
            IconButton({ confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete") }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search in transcript") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = { TextButton({
                if (query.isNotEmpty()) { val from = tf.selection.end.coerceAtMost(tf.text.length); val i = tf.text.indexOf(query, from, ignoreCase = true).let { if (it < 0) tf.text.indexOf(query, 0, ignoreCase = true) else it }
                    if (i >= 0) tf = tf.copy(selection = TextRange(i, i + query.length)) else msg = "Not found." }
            }) { Text("Find") } })
        OutlinedTextField(tf, { tf = it }, Modifier.fillMaxWidth().heightIn(min = 160.dp).padding(top = 8.dp), textStyle = LocalTextStyle.current.copy(fontSize = 16.sp, color = TextHi))
        FlowRowCompat {
            OutlineAction("Select all", { tf = tf.copy(selection = TextRange(0, tf.text.length)) })
            OutlineAction("Copy", { clip.setText(AnnotatedString(tf.text)); msg = "Copied." }, icon = Icons.Outlined.ContentCopy)
            OutlineAction("Share", { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, tf.text), "Share transcript")) }, icon = Icons.Outlined.Share)
            if (dirty) OutlineAction("Save version", { scope.launch { c.repository.saveTranscriptVersion(t, tf.text).let { entity = c.repository.getTranscript(it) }; msg = "Saved as a new version." } })
        }
        Text("Export", color = TextMid, modifier = Modifier.padding(top = 8.dp))
        FlowRowCompat {
            OutlineAction("TXT", { txt.launch("${t.title.take(40)}.txt") })
            OutlineAction("SRT", { if (timedOk) srt.launch("${t.title.take(40)}.srt") else msg = TranscriptExporter.NO_TIMESTAMPS + if (dirty) " (Edited text no longer matches the original timestamps.)" else "" })
            OutlineAction("VTT", { if (timedOk) vtt.launch("${t.title.take(40)}.vtt") else msg = TranscriptExporter.NO_TIMESTAMPS + if (dirty) " (Edited text no longer matches the original timestamps.)" else "" })
        }
        msg?.let { Text(it, color = Echora.Cyan, fontSize = 12.sp) }
    }
    if (renaming) { var n by remember { mutableStateOf(t.title) }
        AlertDialog({ renaming = false }, title = { Text("Rename transcript") }, text = { OutlinedTextField(n, { n = it }, singleLine = true) },
            confirmButton = { TextButton({ scope.launch { runCatching { c.repository.renameTranscript(t.id, n); entity = c.repository.getTranscript(t.id) }; renaming = false } }) { Text("Save") } },
            dismissButton = { TextButton({ renaming = false }) { Text("Cancel") } }) }
    if (confirmDelete) AlertDialog({ confirmDelete = false }, title = { Text("Delete transcript?") }, text = { Text("This version will be permanently deleted.") },
        confirmButton = { TextButton({ scope.launch { c.repository.deleteTranscript(t.id); confirmDelete = false; onDeleted() } }) { Text("Delete") } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowCompat(content: @Composable () -> Unit) = FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }

@Composable
fun ModelManagerScreen(c: AppContainer, onBack: () -> Unit) {
    val st by c.settings.collectAsStateWithLifecycle()
    val dl by c.modelDownloads.state.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }       // refresh readiness after downloads/deletes
    LaunchedEffect(dl) { tick++ }
    val installed = remember(tick) { ModelCatalog.models.filter { c.modelManager.ready(it.id) }.map { it.id }.toSet() }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        EchoraTopBar("Model Manager", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassCard(Modifier.fillMaxWidth()) {
                InfoRow("Models on device", formatBytes(c.modelManager.usedBytes()))
                InfoRow("Free storage", formatBytes(c.modelManager.freeBytes()))
                InfoRow("Engine", if (com.nexvora.echora.transcription.offline.WhisperEngine.isNativeAvailable) "whisper.cpp loaded" else "Not in this build (see cpp/README.md)")
                Text("Models are stored in app-private storage. Only one model is loaded in memory at a time. Downloads are checked against a pinned SHA-256.", color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
            ModelCatalog.models.forEach { m ->
                val state = dl[m.id]; val ready = m.id in installed; val active = st.whisperModel == m.id
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(m.displayName, color = TextHi); Text("${m.size} • multilingual (Bangla, English, …)", color = TextLo, fontSize = 12.sp) }
                        if (active) Chip("Selected")
                    }
                    when (state) {
                        is DownloadState.Running -> {
                            LinearProgressIndicator(progress = { state.percent / 100f }, Modifier.fillMaxWidth().padding(top = 8.dp), color = Echora.Cyan)
                            Text(if (state.verifying) "Verifying…" else "Downloading ${state.percent}%", color = TextMid, fontSize = 12.sp)
                            OutlineAction("Cancel", { c.modelDownloads.cancel(m.id) })
                        }
                        is DownloadState.Failed -> { Text(state.message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp); OutlineAction("Retry", { c.modelDownloads.download(m.id) }) }
                        null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            if (ready) {
                                if (!active) OutlineAction("Use", { c.updateSettings { it.copy(whisperModel = m.id) } })
                                OutlineAction("Delete", { c.modelDownloads.delete(m.id); tick++ }, icon = Icons.Outlined.Delete)
                            } else OutlineAction("Download (${m.size})", { c.modelDownloads.download(m.id) }, icon = Icons.Outlined.Download)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
