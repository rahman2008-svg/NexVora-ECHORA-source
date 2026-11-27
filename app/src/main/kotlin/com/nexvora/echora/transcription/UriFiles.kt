package com.nexvora.echora.transcription

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

object UriFiles {
    fun displayName(context: Context, uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "audio"

    /** Copies a (possibly content://) Uri into cache so native/HTTP code can read it as a File. Caller deletes it. */
    fun copyToCache(context: Context, uri: Uri, suffix: String = ".bin"): File {
        val out = File.createTempFile("echora_", suffix, context.cacheDir)
        try {
            val input = if (uri.scheme == "file") File(uri.path!!).inputStream() else context.contentResolver.openInputStream(uri)
            (input ?: throw TranscriptionException("Cannot open the selected audio.")).use { i -> out.outputStream().use { i.copyTo(it) } }
        } catch (t: Throwable) { out.delete(); throw t }
        return out
    }
}
