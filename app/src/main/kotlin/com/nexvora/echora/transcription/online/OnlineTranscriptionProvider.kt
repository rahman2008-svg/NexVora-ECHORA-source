package com.nexvora.echora.transcription.online

import android.content.Context
import com.nexvora.echora.core.ffmpeg.FFmpegRunner
import com.nexvora.echora.core.secure.SecretKeys
import com.nexvora.echora.core.secure.SecretStore
import com.nexvora.echora.transcription.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Where online audio goes. NEVER silently selected:
 *  - [Proxy]: your own backend (recommended for production; the backend holds the provider key).
 *  - [OpenAiDirect]: the user's OWN OpenAI key, stored encrypted on-device. Convenience/BYOK only.
 */
sealed interface OnlineEndpoint {
    data class Proxy(val baseUrl: String, val bearer: () -> String?, val deviceId: () -> String) : OnlineEndpoint
    data class OpenAiDirect(val model: String = DEFAULT_MODEL) : OnlineEndpoint
    companion object { const val DEFAULT_MODEL = "gpt-4o-mini-transcribe" }
}

class OnlineTranscriptionProvider(
    private val context: Context,
    private val secrets: SecretStore,
    private val ffmpeg: FFmpegRunner,
    private val endpoint: () -> OnlineEndpoint?,
    private val consentGranted: () -> Boolean,
    private val isNetworkUp: () -> Boolean,
    private val connectTimeoutMs: Int = 20_000,
    private val readTimeoutMs: Int = 120_000,
) : TranscriptionProvider {
    override val id = "online"
    override val isOnline = true

    override suspend fun availability(): Availability {
        val e = endpoint() ?: return Availability.Unavailable("No online provider is configured. Set a backend URL or your own API key in Settings.")
        if (e is OnlineEndpoint.OpenAiDirect && secrets.get(SecretKeys.ONLINE_API_KEY).isNullOrBlank())
            return Availability.Unavailable("Add your API key in Settings → Online AI.")
        if (!isNetworkUp()) return Availability.Unavailable("No network connection.")
        return Availability.Ready
    }

    override suspend fun transcribe(request: TranscribeRequest, onProgress: (Int) -> Unit): TranscriptResult {
        if (!consentGranted()) throw ConsentRequiredException()
        (availability() as? Availability.Unavailable)?.let { throw TranscriptionException(it.reason) }
        val ep = endpoint()!!
        val src = withContext(Dispatchers.IO) { UriFiles.copyToCache(context, request.audio) }
        var upload: File? = null
        try {
            upload = withContext(Dispatchers.IO) { prepareUpload(src) }
            var attempt = 0
            while (true) {
                val active = currentCoroutineContext()
                try { return withContext(Dispatchers.IO) { post(ep, upload, request.language, onProgress) { active.isActive } } }
                catch (e: RetryableException) {
                    if (++attempt > MAX_RETRIES) throw TranscriptionException(e.message ?: "Server unavailable.", e)
                    delay(1500L * (1 shl (attempt - 1)))     // 1.5s, 3s
                } catch (e: IOException) {
                    if (++attempt > MAX_RETRIES) throw TranscriptionException("Network error: ${e.message}", e)
                    delay(1500L * (1 shl (attempt - 1)))
                }
            }
        } finally { src.delete(); upload?.takeIf { it != src }?.delete() }
    }

    /** Provider upload limit is ~25 MB; re-encode large files to mono 16 kHz AAC (smaller, fine for speech). */
    private suspend fun prepareUpload(src: File): File {
        if (src.length() <= MAX_UPLOAD_BYTES / 2) return src
        val out = File.createTempFile("echora_up_", ".m4a", context.cacheDir)
        val r = ffmpeg.run(listOf("-y", "-i", src.path, "-vn", "-ac", "1", "-ar", "16000", "-c:a", "aac", "-b:a", "48k", out.path))
        if (!r.isSuccess) { out.delete(); throw TranscriptionException("Could not compress the audio for upload.") }
        if (out.length() > MAX_UPLOAD_BYTES) { out.delete(); throw TranscriptionException("Audio is too long for online transcription (25 MB limit). Trim or split it first.") }
        return out
    }

    private fun post(ep: OnlineEndpoint, file: File, language: String, onProgress: (Int) -> Unit, isActive: () -> Boolean): TranscriptResult {
        val (url, model, auth) = when (ep) {
            is OnlineEndpoint.Proxy -> {
                require(ep.baseUrl.startsWith("https://")) { "Backend URL must use HTTPS." }
                Triple(ep.baseUrl.trimEnd('/') + "/v1/transcribe", "server-selected", ep.bearer()?.let { "Bearer $it" })
            }
            is OnlineEndpoint.OpenAiDirect ->
                Triple(OPENAI_URL, ep.model, "Bearer " + (secrets.get(SecretKeys.ONLINE_API_KEY) ?: ""))
        }
        val boundary = "echora" + UUID.randomUUID().toString().replace("-", "")
        val fields = buildList {
            if (ep is OnlineEndpoint.OpenAiDirect) {
                add("model" to model)
                // whisper-1 can return segment timestamps; the gpt-4o-* transcribe models return text only.
                if (model == "whisper-1") { add("response_format" to "verbose_json"); add("timestamp_granularities[]" to "segment") }
                else add("response_format" to "json")
            }
            if (language == "bn" || language == "en") add("language" to language)
        }
        val head = StringBuilder().apply {
            fields.forEach { (k, v) -> append("--$boundary\r\nContent-Disposition: form-data; name=\"$k\"\r\n\r\n$v\r\n") }
            append("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"\r\nContent-Type: application/octet-stream\r\n\r\n")
        }.toString().toByteArray()
        val tail = "\r\n--$boundary--\r\n".toByteArray()
        val total = head.size + file.length() + tail.size

        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            connectTimeout = connectTimeoutMs; readTimeout = readTimeoutMs
            setFixedLengthStreamingMode(total)
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            auth?.let { setRequestProperty("Authorization", it) }
            if (ep is OnlineEndpoint.Proxy) setRequestProperty("X-Device-Id", ep.deviceId())
        }
        try {
            c.outputStream.use { out ->
                out.write(head)
                file.inputStream().use { input ->
                    val buf = ByteArray(32 * 1024); var sent = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        if (!isActive()) throw CancellationException("Upload cancelled")
                        out.write(buf, 0, n); sent += n
                        onProgress((sent * 90 / file.length().coerceAtLeast(1)).toInt())   // upload = 0..90
                    }
                }
                out.write(tail)
            }
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            when (code) {
                in 200..299 -> { onProgress(100); return parse(body, ep, model) }
                401, 403 -> throw TranscriptionException("The provider rejected the credentials (invalid API key or token).")
                402 -> throw TranscriptionException("Online transcription allowance used up for your plan. Upgrade or use offline mode.")
                413 -> throw TranscriptionException("Audio file too large for the provider.")
                429 -> throw RetryableException("Rate limit or quota reached (HTTP 429). ${errMsg(body)}")
                in 500..599 -> throw RetryableException("Provider server error (HTTP $code).")
                else -> throw TranscriptionException("Request failed (HTTP $code). ${errMsg(body)}")
            }
        } finally { c.disconnect() }
    }

    private fun parse(body: String, ep: OnlineEndpoint, model: String): TranscriptResult {
        val j = runCatching { JSONObject(body) }.getOrElse { throw TranscriptionException("Unexpected response from the server.") }
        val text = j.optString("text").trim()
        val segs = j.optJSONArray("segments")?.let { a ->
            List(a.length()) { i -> a.getJSONObject(i).let { Segment((it.getDouble("start") * 1000).toLong(), (it.getDouble("end") * 1000).toLong(), it.getString("text").trim()) } }
        }.orEmpty()
        return TranscriptResult(text, j.optString("language").ifBlank { null }, segs,
            "online:" + if (ep is OnlineEndpoint.Proxy) "backend" else model)
    }

    private fun errMsg(body: String) = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") ?: JSONObject(body).optString("error") }.getOrNull().orEmpty()

    private class RetryableException(msg: String) : Exception(msg)

    companion object {
        /** Verify against https://developers.openai.com/api/docs/guides/speech-to-text before release (not reachable when this was written). */
        const val OPENAI_URL = "https://api.openai.com/v1/audio/transcriptions"
        const val MAX_UPLOAD_BYTES = 25L * 1024 * 1024
        const val MAX_RETRIES = 2
    }
}
