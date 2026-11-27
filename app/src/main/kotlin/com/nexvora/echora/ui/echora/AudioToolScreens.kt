package com.nexvora.echora.ui.echora

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexvora.echora.core.io.Outputs
import com.nexvora.echora.data.AudioEntity
import com.nexvora.echora.di.AppContainer
import com.nexvora.echora.noise.NoiseProfile
import com.nexvora.echora.noise.NoiseReduction
import com.nexvora.echora.premium.Entitlement
import com.nexvora.echora.premium.Feature
import com.nexvora.echora.transcription.UriFiles
import com.nexvora.echora.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun durationOf(ctx: android.content.Context, uri: Uri): Long = runCatching {
    val r = MediaMetadataRetriever(); try { r.setDataSource(ctx, uri); r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L } finally { r.release() }
}.getOrDefault(0L)

@Composable
fun NoiseScreen(c: AppContainer, onBack: () -> Unit, onPremium: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val ent by c.entitlements.entitlement.collectAsStateWithLifecycle(Entitlement.Free)
    val canEnhance = ent.allows(Feature.ADVANCED_PRESETS, System.currentTimeMillis())
    var uri by remember { mutableStateOf<Uri?>(null) }; var name by remember { mutableStateOf<String?>(null) }
    var profile by remember { mutableStateOf<NoiseProfile?>(null) }
    var strength by remember { mutableFloatStateOf(0.5f) }; var enhance by remember { mutableStateOf(false) }; var normalize by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf<String?>(null) }; var progress by remember { mutableStateOf<Float?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }; var err by remember { mutableStateOf<String?>(null) }
    var previewOrig by remember { mutableStateOf<File?>(null) }; var previewProc by remember { mutableStateOf<File?>(null) }
    val player = remember { arrayOfNulls<MediaPlayer>(1) }
    DisposableEffect(Unit) { onDispose { player[0]?.release(); previewOrig?.delete(); previewProc?.delete() } }
    fun play(f: File?) { f ?: return; runCatching { player[0]?.release(); player[0] = MediaPlayer().apply { setDataSource(f.path); prepare(); start() } }.onFailure { err = "Playback failed: ${it.message}" } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) { uri = u; name = UriFiles.displayName(ctx, u); profile = null; previewOrig = null; previewProc = null; msg = null; err = null }
    }
    fun chain() = NoiseReduction.filterChain(strength.toDouble(), profile, enhance && canEnhance, normalize)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        EchoraTopBar("Noise Reduction", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Signal-processing denoiser (FFT noise reduction + high-pass). This is classic DSP, not AI noise cancellation; it works best on steady noise such as hiss, hum and fans. Your original file is never changed.", color = TextMid, fontSize = 13.sp)
            GlassCard(Modifier.fillMaxWidth()) {
                Text(name ?: "No audio selected", color = TextHi)
                OutlineAction("Choose audio", { picker.launch(arrayOf("audio/*")) }, Modifier.padding(top = 8.dp), Icons.Outlined.FileUpload, enabled = busy == null)
            }
            if (uri != null) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("1. Analyse background noise", color = TextHi)
                    profile?.let { InfoRow("Noise floor", "%.1f dBFS".format(it.noiseFloorDb)); InfoRow("Speech level", "%.1f dBFS".format(it.speechLevelDb)); InfoRow("Estimated SNR", "%.0f dB".format(it.snrDb)) }
                    OutlineAction("Analyse", { scope.launch { busy = "Analysing…"; err = null
                        runCatching { NoiseReduction.analyze(ctx, uri!!) }.onSuccess { profile = it }.onFailure { err = "Analysis failed: ${it.message}" }; busy = null } }, Modifier.padding(top = 8.dp), enabled = busy == null)
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("2. Strength: ${(strength * 100).toInt()}%", color = TextHi)
                    Slider(strength, { strength = it }, colors = SliderDefaults.colors(thumbColor = Echora.Cyan, activeTrackColor = Echora.Purple))
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Switch(normalize, { normalize = it }); Spacer(Modifier.width(8.dp)); Text("Normalise loudness", color = TextHi) }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Switch(enhance && canEnhance, { if (canEnhance) enhance = it else onPremium() }); Spacer(Modifier.width(8.dp))
                        Text("Voice enhancement (EQ + compressor)" + if (canEnhance) "" else "  — Plus", color = TextHi)
                    }
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("3. Preview (first 10 s)", color = TextHi)
                    OutlineAction("Render preview", { scope.launch {
                        busy = "Rendering preview…"; err = null
                        runCatching {
                            val src = withContext(Dispatchers.IO) { UriFiles.copyToCache(ctx, uri!!) }
                            try {
                                val o = File.createTempFile("pv_o_", ".wav", ctx.cacheDir); val p = File.createTempFile("pv_p_", ".wav", ctx.cacheDir)
                                val r1 = c.ffmpeg.run(listOf("-y", "-i", src.path, "-t", "10", "-vn", "-c:a", "pcm_s16le", o.path))
                                val r2 = c.ffmpeg.run(NoiseReduction.args(src.path, p.path, chain(), previewSeconds = 10))
                                check(r1.isSuccess && r2.isSuccess) { (if (!r2.isSuccess) r2 else r1).logTail.takeLast(200) }
                                previewOrig?.delete(); previewProc?.delete(); previewOrig = o; previewProc = p
                            } finally { src.delete() }
                        }.onFailure { err = "Preview failed: ${it.message}" }
                        busy = null } }, Modifier.padding(top = 8.dp), enabled = busy == null)
                    if (previewOrig != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        OutlineAction("▶ Before", { play(previewOrig) }); OutlineAction("▶ After", { play(previewProc) }); OutlineAction("Stop", { player[0]?.release(); player[0] = null })
                    }
                }
                GradientButton("Process & save as WAV", { scope.launch {
                    busy = "Processing…"; progress = 0f; err = null; msg = null
                    runCatching {
                        val src = withContext(Dispatchers.IO) { UriFiles.copyToCache(ctx, uri!!) }
                        val total = durationOf(ctx, uri!!)
                        val tmp = Outputs.newTempFile(ctx, "denoised", "wav")
                        try {
                            val r = c.ffmpeg.run(NoiseReduction.args(src.path, tmp.path, chain()), total) { ratio, _ -> progress = ratio }
                            check(r.isSuccess) { r.logTail.takeLast(200) }
                            val base = (name ?: "audio").substringBeforeLast('.') + "-denoised"
                            val pub = withContext(Dispatchers.IO) { Outputs.publishToMusic(ctx, tmp, base, "wav") }
                            c.repository.addAudio(AudioEntity(path = pub.uri.toString(), displayName = base, mimeType = "audio/wav", durationMs = durationOf(ctx, pub.uri), sizeBytes = tmp.length().takeIf { it > 0 } ?: 0, source = "edit"))
                            msg = "Saved: ${pub.savedDescription}"
                        } finally { src.delete(); tmp.delete() }
                    }.onFailure { err = "Processing failed: ${it.message}" }
                    busy = null; progress = null } }, Modifier.fillMaxWidth(), enabled = busy == null)
            }
            busy?.let { Text(it, color = Echora.Cyan); progress?.let { p -> LinearProgressIndicator(progress = { p }, Modifier.fillMaxWidth(), color = Echora.Cyan) } ?: LinearProgressIndicator(Modifier.fillMaxWidth()) }
            msg?.let { Text(it, color = Echora.Cyan) }; err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private data class Fmt(val ext: String, val label: String, val codec: List<String>, val feature: Feature?, val note: String)
private val formats = listOf(
    Fmt("wav", "WAV", listOf("-c:a", "pcm_s16le"), null, "Lossless 16-bit PCM"),
    Fmt("m4a", "M4A (AAC)", listOf("-c:a", "aac", "-b:a", "192k"), null, "Compressed, widely compatible"),
    Fmt("flac", "FLAC", listOf("-c:a", "flac"), Feature.ADVANCED_EXPORT, "Lossless compressed"),
    Fmt("mp3", "MP3", listOf("-c:a", "libmp3lame", "-b:a", "192k"), Feature.ADVANCED_EXPORT, "Only if this build's FFmpeg includes an MP3 encoder"),
)

@Composable
fun ConvertScreen(c: AppContainer, onBack: () -> Unit, onPremium: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val ent by c.entitlements.entitlement.collectAsStateWithLifecycle(Entitlement.Free)
    val settings by c.settings.collectAsStateWithLifecycle()
    var uri by remember { mutableStateOf<Uri?>(null) }; var name by remember { mutableStateOf<String?>(null) }
    var fmt by remember(settings.exportFormat) { mutableStateOf(formats.firstOrNull { it.ext == settings.exportFormat } ?: formats[0]) }
    var busy by remember { mutableStateOf(false) }; var progress by remember { mutableStateOf<Float?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }; var err by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) { uri = u; name = UriFiles.displayName(ctx, u); msg = null; err = null } }
    val locked = fmt.feature != null && !ent.allows(fmt.feature!!, System.currentTimeMillis())

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        EchoraTopBar("Convert & Export", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(name ?: "No audio selected", color = TextHi)
                OutlineAction("Choose audio", { picker.launch(arrayOf("audio/*", "video/*")) }, Modifier.padding(top = 8.dp), Icons.Outlined.FileUpload, enabled = !busy)
                Text("Input formats are whatever this device and FFmpeg can decode (typically WAV, MP3, M4A/AAC, FLAC, OGG).", color = TextLo, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            }
            SegmentedPills(formats.map { it to it.label }, fmt, { fmt = it })
            Text(fmt.note + if (locked) " • Plus feature" else "", color = TextMid, fontSize = 13.sp)
            if (locked) OutlineAction("See Plus", onPremium)
            GradientButton("Convert & save .${fmt.ext}", { scope.launch {
                busy = true; progress = 0f; err = null; msg = null
                runCatching {
                    val src = withContext(Dispatchers.IO) { UriFiles.copyToCache(ctx, uri!!) }
                    val tmp = Outputs.newTempFile(ctx, "converted", fmt.ext)
                    try {
                        val r = c.ffmpeg.run(listOf("-y", "-i", src.path, "-vn") + fmt.codec + tmp.path, durationOf(ctx, uri!!)) { ratio, _ -> progress = ratio }
                        check(r.isSuccess && tmp.length() > 0) { if (fmt.ext == "mp3") "This build can't encode MP3 (${r.logTail.takeLast(120)})" else r.logTail.takeLast(200) }
                        val base = (name ?: "audio").substringBeforeLast('.')
                        val pub = withContext(Dispatchers.IO) { Outputs.publishToMusic(ctx, tmp, base, fmt.ext) }
                        c.repository.addAudio(AudioEntity(path = pub.uri.toString(), displayName = "$base.${fmt.ext}", mimeType = Outputs.mimeFor(fmt.ext), durationMs = durationOf(ctx, pub.uri), sizeBytes = tmp.length(), source = "export"))
                        msg = "Saved: ${pub.savedDescription}"
                    } finally { src.delete(); tmp.delete() }
                }.onFailure { err = "Conversion failed: ${it.message}" }
                busy = false; progress = null } }, Modifier.fillMaxWidth(), enabled = uri != null && !busy && !locked)
            if (busy) progress?.let { LinearProgressIndicator(progress = { it }, Modifier.fillMaxWidth(), color = Echora.Cyan) } ?: LinearProgressIndicator(Modifier.fillMaxWidth())
            msg?.let { Text(it, color = Echora.Cyan) }; err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("Transcript export (TXT / SRT / VTT) is in Library → Transcripts and after each transcription.", color = TextLo, fontSize = 12.sp)
        }
    }
}
