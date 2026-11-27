package com.nexvora.echora.ui.echora

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexvora.echora.data.AudioEntity
import com.nexvora.echora.data.TranscriptEntity
import com.nexvora.echora.di.AppContainer
import com.nexvora.echora.feature.library.LibraryScreen
import com.nexvora.echora.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class LibTab(val label: String) { Audio("Audio"), Transcripts("Transcripts"), Exports("Exports") }

@Composable
fun LibraryHub(container: AppContainer, onTranscribe: () -> Unit, onEdit: (Uri) -> Unit) {
    var tab by remember { mutableStateOf(LibTab.Audio) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        EchoraTopBar("Library")
        SegmentedPills(LibTab.entries.map { it to it.label }, tab, { tab = it }, Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(8.dp))
        when (tab) {
            LibTab.Audio -> AudioList(container, onTranscribe, onEdit)
            LibTab.Transcripts -> TranscriptList(container)
            LibTab.Exports -> LibraryScreen(onOpenInEditor = { onEdit(it) })   // Resound's file library (exports in Music/)
        }
    }
}

@Composable
private fun AudioList(c: AppContainer, onTranscribe: () -> Unit, onEdit: (Uri) -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }; var favOnly by remember { mutableStateOf(false) }; var source by remember { mutableStateOf("") }
    var sortNewest by remember { mutableStateOf(true) }
    val items by c.repository.audio(q, favOnly, source).collectAsStateWithLifecycle(emptyList())
    val sorted = if (sortNewest) items else items.reversed()
    var rename by remember { mutableStateOf<AudioEntity?>(null) }; var delete by remember { mutableStateOf<AudioEntity?>(null) }
    var playingId by remember { mutableStateOf<Long?>(null) }
    val player = remember { arrayOfNulls<MediaPlayer>(1) }
    DisposableEffect(Unit) { onDispose { player[0]?.release() } }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val meta = withContext(Dispatchers.IO) {
                    val r = MediaMetadataRetriever()
                    try { r.setDataSource(ctx, uri); r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() to r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) } finally { r.release() }
                }
                if (meta.first == null) error("This file could not be decoded as audio on this device.")
                val size = ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
                c.repository.addAudio(AudioEntity(path = uri.toString(), displayName = com.nexvora.echora.transcription.UriFiles.displayName(ctx, uri), mimeType = meta.second ?: "audio/*", durationMs = meta.first!!, sizeBytes = size, source = "import"))
            }.onFailure { error = it.message ?: "Import failed." }
        }
    }

    Column(Modifier.padding(horizontal = 20.dp)) {
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search audio") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true)
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(favOnly, { favOnly = !favOnly }, { Text("Favorites") })
            FilterChip(source == "record", { source = if (source == "record") "" else "record" }, { Text("Voice notes") })
            FilterChip(!sortNewest, { sortNewest = !sortNewest }, { Text(if (sortNewest) "Newest" else "Oldest") })
            Spacer(Modifier.weight(1f))
            OutlineAction("Import", { picker.launch(arrayOf("audio/*")) }, icon = Icons.Outlined.FileUpload)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("Formats are checked by the device's decoder when you import (commonly WAV, MP3, M4A/AAC, FLAC, OGG).", color = TextLo, fontSize = 11.sp)
        if (sorted.isEmpty()) Text("Nothing here yet.", color = TextLo, modifier = Modifier.padding(top = 24.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(sorted, key = { it.id }) { a ->
                val uri = if (a.path.startsWith("content://")) Uri.parse(a.path) else Uri.fromFile(File(a.path))
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton({
                            if (playingId == a.id) { player[0]?.release(); player[0] = null; playingId = null }
                            else runCatching {
                                player[0]?.release()
                                player[0] = MediaPlayer().apply { setDataSource(ctx, uri); setOnCompletionListener { playingId = null }; prepare(); start() }; playingId = a.id
                            }.onFailure { error = "Cannot play this file: ${it.message}"; playingId = null }
                        }) { Icon(if (playingId == a.id) Icons.Filled.Stop else Icons.Filled.PlayArrow, "Play", tint = Echora.Cyan) }
                        Column(Modifier.weight(1f)) {
                            Text(a.displayName, color = TextHi, maxLines = 1)
                            Text("${formatDuration(a.durationMs)} • ${formatBytes(a.sizeBytes)} • ${a.source}" + if (a.sampleRate > 0) " • ${a.sampleRate / 1000.0} kHz" else "", color = TextLo, fontSize = 12.sp)
                        }
                        IconButton({ scope.launch { c.repository.setFavorite(a.id, !a.favorite) } }) { Icon(if (a.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder, "Favorite", tint = Echora.Gold) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton({ c.transcribeController.select(uri, a.displayName, a.id); onTranscribe() }) { Icon(Icons.Outlined.Translate, "Transcribe") }
                        IconButton({ onEdit(uri) }) { Icon(Icons.Outlined.ContentCut, "Edit") }
                        IconButton({ rename = a }) { Icon(Icons.Outlined.Edit, "Rename") }
                        IconButton({
                            val shareUri = if (uri.scheme == "file") FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", File(a.path)) else uri
                            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(a.mimeType.ifBlank { "audio/*" }).putExtra(Intent.EXTRA_STREAM, shareUri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share"))
                        }) { Icon(Icons.Outlined.Share, "Share") }
                        IconButton({ delete = a }) { Icon(Icons.Outlined.Delete, "Delete") }
                    }
                }
            }
        }
    }
    rename?.let { a ->
        var name by remember(a.id) { mutableStateOf(a.displayName) }
        AlertDialog({ rename = null }, title = { Text("Rename") }, text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton({ scope.launch { runCatching { c.repository.renameAudio(a.id, name) }; rename = null } }) { Text("Save") } },
            dismissButton = { TextButton({ rename = null }) { Text("Cancel") } })
    }
    delete?.let { a ->
        AlertDialog({ delete = null }, title = { Text("Delete audio?") },
            text = { Text(if (a.path.startsWith("content://")) "Removes it from ECHORA. The original imported file is not deleted." else "The recording file will be permanently deleted.") },
            confirmButton = { TextButton({ scope.launch { c.repository.deleteAudio(a.id); delete = null } }) { Text("Delete") } },
            dismissButton = { TextButton({ delete = null }) { Text("Cancel") } })
    }
}

@Composable
private fun TranscriptList(c: AppContainer) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    val items by c.repository.transcripts(q).collectAsStateWithLifecycle(emptyList())
    var open by remember { mutableStateOf<TranscriptEntity?>(null) }
    Column(Modifier.padding(horizontal = 20.dp)) {
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search transcripts") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true)
        if (items.isEmpty()) Text("No transcripts yet.", color = TextLo, modifier = Modifier.padding(top = 24.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(items, key = { it.id }) { t ->
                GlassCard(Modifier.fillMaxWidth(), onClick = { open = t }) {
                    Text(t.title + if (t.version > 1) "  (v${t.version})" else "", color = TextHi, maxLines = 1)
                    Text(t.text, color = TextMid, maxLines = 2, fontSize = 13.sp)
                    Text("${t.language} • ${t.engine}", color = TextLo, fontSize = 11.sp)
                }
            }
        }
    }
    open?.let { t -> TranscriptDialog(t, c, onDismiss = { open = null }) }
}
