package com.nexvora.echora.ui.echora

import android.os.Environment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexvora.echora.di.AppContainer
import com.nexvora.echora.premium.Entitlement
import com.nexvora.echora.recorder.RecStatus
import com.nexvora.echora.recorder.RecorderHub
import com.nexvora.echora.settings.AiMode
import com.nexvora.echora.transcription.offline.ModelCatalog
import com.nexvora.echora.transcription.offline.WhisperEngine
import com.nexvora.echora.ui.theme.*

private data class QuickAction(val label: String, val icon: ImageVector, val route: Route)

@Composable
fun HomeScreen(container: AppContainer, go: (Route) -> Unit, openEditor: (String?) -> Unit) {
    val settings by container.settings.collectAsStateWithLifecycle()
    val ent by container.entitlements.entitlement.collectAsStateWithLifecycle(Entitlement.Free)
    val recent by container.repository.recentAudio(4).collectAsStateWithLifecycle(emptyList())
    val total by container.repository.totalAudioBytes().collectAsStateWithLifecycle(0L)
    val rec by RecorderHub.recorder.state.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()
    val plan = ent.effectivePlan(now)
    val modelReady = remember(settings.whisperModel, recent) { container.modelManager.ready(settings.whisperModel) }
    val actions = listOf(
        QuickAction("Record Audio", Icons.Filled.Mic, Route.Recorder), QuickAction("Transcribe Audio", Icons.Outlined.Translate, Route.Transcribe),
        QuickAction("Edit Audio", Icons.Outlined.ContentCut, Route.Editor), QuickAction("Reduce Noise", Icons.Outlined.GraphicEq, Route.Noise),
        QuickAction("Import Audio", Icons.Outlined.FileUpload, Route.Library), QuickAction("Voice Notes", Icons.Outlined.StickyNote2, Route.Library),
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).statusBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("NEXVORA ECHORA", color = TextHi, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, letterSpacing = 1.sp)
                Text(if (settings.displayName.isBlank()) "AI Voice & Audio Studio" else "Hello, ${settings.displayName}", color = TextMid, fontSize = 14.sp)
            }
            Chip(if (plan.name == "FREE") "Free plan" else "★ ${plan.name.lowercase().replaceFirstChar { it.uppercase() }}", Echora.Gold)
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            MicOrb(rec.level, rec.status == RecStatus.RECORDING, size = 150.dp, onClick = { go(Route.Recorder) })
            LiveWaveform(if (rec.status == RecStatus.RECORDING || rec.status == RecStatus.PAUSED) rec.levels else emptyList(), Modifier.padding(top = 8.dp))
            Text(if (rec.status == RecStatus.RECORDING) "Recording… ${formatDuration(rec.elapsedMs)}" else "Record • Transcribe • Edit • More", color = TextMid, fontSize = 13.sp)
        }
        GradientButton("Record", { go(Route.Recorder) }, Modifier.fillMaxWidth(), Icons.Filled.Mic)

        // Quick actions: every tile navigates to a working screen.
        actions.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { a ->
                    GlassCard(Modifier.weight(1f), onClick = { if (a.route == Route.Editor) openEditor(null) else go(a.route) }) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(a.icon, null, tint = Echora.Cyan); Spacer(Modifier.height(6.dp))
                            Text(a.label, color = TextHi, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                }
            }
        }

        SectionTitle("Recent projects")
        if (recent.isEmpty()) Text("No recordings yet. Tap Record or import audio in Library.", color = TextLo)
        recent.forEach { a ->
            GlassCard(Modifier.fillMaxWidth(), onClick = { go(Route.Library) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AudioFile, null, tint = Echora.Purple); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(a.displayName, color = TextHi, maxLines = 1); Text("${formatDuration(a.durationMs)} • ${formatBytes(a.sizeBytes)}", color = TextLo, fontSize = 12.sp) }
                }
            }
        }

        SectionTitle("Status")
        GlassCard(Modifier.fillMaxWidth()) {
            InfoRow("Processing mode", when (settings.aiMode) { AiMode.OFFLINE -> "Offline"; AiMode.ONLINE -> "Online"; AiMode.AUTO -> "Auto (offline first)" })
            InfoRow("AI engine", if (WhisperEngine.isNativeAvailable) "whisper.cpp ready" else "Native engine not in this build")
            InfoRow("Model (${ModelCatalog.get(settings.whisperModel).displayName})", if (modelReady) "Installed" else "Not downloaded")
            InfoRow("Library size", formatBytes(total))
            InfoRow("Free storage", formatBytes(Environment.getDataDirectory().usableSpace))
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun StudioScreen(go: (Route) -> Unit) {
    val tools = listOf(
        Triple(Route.Recorder, Icons.Filled.Mic, "Voice Recorder — lossless WAV, pause/resume, background"),
        Triple(Route.Editor, Icons.Outlined.ContentCut, "Audio Editor — trim, split, merge, fade, speed, EQ, convert"),
        Triple(Route.Multitrack, Icons.Outlined.ViewTimeline, "Multitrack — arrange and mix several clips"),
        Triple(Route.Noise, Icons.Outlined.GraphicEq, "Noise Reduction — DSP denoise with before/after preview"),
        Triple(Route.Transcribe, Icons.Outlined.Translate, "AI Transcription — Bangla & English, offline or online"),
        Triple(Route.Convert, Icons.Outlined.Transform, "Convert & Export — WAV, M4A, FLAC and more"),
        Triple(Route.Models, Icons.Outlined.Memory, "Model Manager — Whisper Tiny / Base / Small"),
    )
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        EchoraTopBar("Studio")
        LazyColumn(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(tools) { (r, icon, desc) ->
                GlassCard(Modifier.fillMaxWidth(), onClick = { go(r) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = Echora.Cyan, modifier = Modifier.size(30.dp)); Spacer(Modifier.width(14.dp))
                        Text(desc, color = TextHi)
                    }
                }
            }
        }
    }
}
