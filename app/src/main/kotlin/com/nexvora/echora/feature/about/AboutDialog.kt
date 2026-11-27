/*
 * file:    AboutDialog.kt
 * author:  Typezer∅
 * version: 0.8.0-dev.6
 * desc:    About dialog — app version (read from PackageInfo), a one-line
 *          description, links to the repo and changelog, and open-source
 *          attribution for the audio engine.
 */
package com.nexvora.echora.feature.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nexvora.echora.ui.theme.Accent
import com.nexvora.echora.core.io.Outputs
import com.nexvora.echora.ui.theme.TextLo

private const val REPO_URL =
    "https://github.com/rahman2008-svg/NexVora-ECHORA"
private const val CHANGELOG_URL =
    "https://github.com/rahman2008-svg/NexVora-ECHORA/blob/main/CHANGELOG.md"

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val version = remember {
        runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }

    fun open(url: String) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("NexVora ECHORA") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Version $version", style = MaterialTheme.typography.bodySmall, color = TextLo)
                Text(
                    "AI Voice & Audio Studio by Prince AR Abdur Rahman — NexVora Lab's Ofc. Exports save to ${Outputs.musicDirectoryDisplay}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Source on GitHub ↗",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Accent, // interactive link: Steel Blue (token swap only; dialog redesign is a separate task)
                    modifier = Modifier.clickable { open(REPO_URL) },
                )
                Text(
                    "Changelog ↗",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Accent, // interactive link: Steel Blue (token swap only; dialog redesign is a separate task)
                    modifier = Modifier.clickable { open(CHANGELOG_URL) },
                )
                Text(
                    "Audio engine: FFmpegKit (LGPL-3.0). Built with Jetpack Compose.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLo,
                )
                Text("© 2026 NexVora Lab's Ofc. Based on Resound by Typezer∅ (Apache-2.0); whisper.cpp (MIT). See THIRD_PARTY_NOTICES.", style = MaterialTheme.typography.bodySmall, color = TextLo)
            }
        },
    )
}
