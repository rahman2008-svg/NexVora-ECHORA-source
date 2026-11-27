package com.nexvora.echora.ui.echora

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexvora.echora.di.AppContainer
import com.nexvora.echora.recorder.*
import com.nexvora.echora.ui.theme.*

@Composable
fun RecorderScreen(container: AppContainer, onBack: () -> Unit, onTranscribe: () -> Unit, onLibrary: () -> Unit) {
    val ctx = LocalContext.current
    val st by RecorderHub.recorder.state.collectAsStateWithLifecycle()
    val settings by container.settings.collectAsStateWithLifecycle()
    val recent by container.repository.recentAudio(1).collectAsStateWithLifecycle(emptyList())
    var denied by remember { mutableStateOf(false) }

    fun begin() = EchoraRecordingService.start(ctx, settings.recordSampleRate, settings.recordChannels)
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { begin() }   // proceed either way; the notification is optional UX
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        denied = !ok
        if (ok) { if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else begin() }
    }
    fun startTapped() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    val active = st.status == RecStatus.RECORDING || st.status == RecStatus.PAUSED
    val saved = recent.firstOrNull()?.takeIf { st.status == RecStatus.STOPPED && it.path == st.file }

    Column(Modifier.fillMaxSize().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        EchoraTopBar("Recorder", onBack)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (st.status == RecStatus.RECORDING) Color(0xFF2ECC71) else TextLo))
            Spacer(Modifier.width(8.dp)); Text("Offline recording • ${st.sampleRate / 1000.0} kHz • ${if (st.channels == 1) "mono" else "stereo"} WAV", color = TextMid)
        }
        Spacer(Modifier.height(16.dp))
        MicOrb(st.level, st.status == RecStatus.RECORDING, size = 190.dp)
        Text(formatDuration(st.elapsedMs), color = TextHi, fontSize = 46.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        LiveWaveform(st.levels, Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
        Chip("Audio level: " + when { st.level > .75f -> "High"; st.level > .35f -> "Good"; st.level > .05f -> "Low"; else -> "Silent" })
        Spacer(Modifier.height(24.dp))

        if (active) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleAction(if (st.status == RecStatus.PAUSED) Icons.Filled.PlayArrow else Icons.Filled.Pause, if (st.status == RecStatus.PAUSED) "Resume" else "Pause") {
                    EchoraRecordingService.send(ctx, if (st.status == RecStatus.PAUSED) EchoraRecordingService.ACTION_RESUME else EchoraRecordingService.ACTION_PAUSE)
                }
                CircleAction(Icons.Filled.Stop, "Stop", big = true) { EchoraRecordingService.send(ctx, EchoraRecordingService.ACTION_STOP) }
                CircleAction(Icons.Filled.Check, "Done") { EchoraRecordingService.send(ctx, EchoraRecordingService.ACTION_STOP); onLibrary() }
            }
        } else {
            GradientButton(if (st.status == RecStatus.STOPPED || st.status == RecStatus.ERROR) "Record again" else "Start recording", {
                RecorderHub.recorder.reset(); startTapped()
            }, Modifier.padding(horizontal = 32.dp).fillMaxWidth(), Icons.Filled.Mic)
        }
        Spacer(Modifier.height(16.dp))
        st.message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 24.dp)) }
        if (denied) Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
            Text("Microphone permission is needed to record. Audio stays on this device.", color = TextMid)
            OutlineAction("Open app settings", { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))) })
        }
        if (saved != null) GlassCard(Modifier.padding(horizontal = 24.dp).fillMaxWidth()) {
            Text("Saved: ${saved.displayName} (${formatDuration(saved.durationMs)})", color = TextHi)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlineAction("Transcribe", { container.transcribeController.select(Uri.fromFile(java.io.File(saved.path)), saved.displayName, saved.id); onTranscribe() }, icon = Icons.Outlined.Translate)
                OutlineAction("Library", onLibrary)
            }
        }
    }
}

@Composable
private fun CircleAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, big: Boolean = false, onClick: () -> Unit) =
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(if (big) 84.dp else 64.dp).clip(CircleShape).background(if (big) Echora.Gradient else androidx.compose.ui.graphics.Brush.linearGradient(listOf(Panel, PanelHi))).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(if (big) 40.dp else 30.dp))
        }
        Text(label, color = TextMid, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }


