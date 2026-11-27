package com.nexvora.echora.recorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.nexvora.echora.R
import com.nexvora.echora.ResoundApp
import com.nexvora.echora.data.AudioEntity
import kotlinx.coroutines.*
import java.io.File

/**
 * Keeps recording alive when the app is in the background. Android requires a visible notification
 * and the `microphone` foreground-service type. The file is registered in the library when recording stops.
 */
class EchoraRecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording(intent)
            ACTION_PAUSE -> RecorderHub.recorder.pause()
            ACTION_RESUME -> RecorderHub.recorder.resume()
            ACTION_STOP -> stopRecording()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startRecording(i: Intent) {
        ensureChannel()
        ServiceCompat.startForeground(this, NOTIF_ID, notification("Recording…"), if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
        val dir = File(filesDir, "recordings").apply { mkdirs() }
        val file = File(dir, "ECHORA_${System.currentTimeMillis()}.wav")
        RecorderHub.recorder.start(file, i.getIntExtra(EXTRA_RATE, 44100), i.getIntExtra(EXTRA_CHANNELS, 1))
        if (RecorderHub.recorder.state.value.status == RecStatus.ERROR) { stopForegroundAndSelf(); return }
        // keep the notification in sync with the state
        scope.launch {
            RecorderHub.recorder.state.collect { s ->
                when (s.status) {
                    RecStatus.RECORDING, RecStatus.PAUSED ->
                        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification((if (s.status == RecStatus.PAUSED) "Paused " else "Recording ") + fmt(s.elapsedMs), s.status == RecStatus.PAUSED))
                    RecStatus.ERROR -> { saveIfAny(); stopForegroundAndSelf() }   // interruption/low storage: keep what we have
                    else -> {}
                }
            }
        }
    }

    private fun stopRecording() {
        scope.launch(Dispatchers.IO) { saveIfAny(); withContext(Dispatchers.Main) { stopForegroundAndSelf() } }
    }

    private var saved = false
    private suspend fun saveIfAny() {
        if (saved) return
        val s = RecorderHub.recorder.state.value
        val f = if (s.status == RecStatus.ERROR) s.file?.let(::File)?.takeIf { it.length() > 44 } else RecorderHub.recorder.stop()
        if (f == null) return
        saved = true
        val bytes = (f.length() - 44).coerceAtLeast(0)
        val dur = bytes * 1000 / (s.sampleRate * s.channels * 2L).coerceAtLeast(1)
        (application as ResoundApp).container.repository.addAudio(
            AudioEntity(path = f.path, displayName = "Recording " + java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
                mimeType = "audio/wav", durationMs = dur, sizeBytes = f.length(), sampleRate = s.sampleRate, channels = s.channels, source = "record"))
    }

    private fun stopForegroundAndSelf() { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE); stopSelf() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun fmt(ms: Long) = "%02d:%02d".format(ms / 60000, ms / 1000 % 60)

    private fun action(a: String, title: String): NotificationCompat.Action =
        NotificationCompat.Action(0, title, PendingIntent.getService(this, a.hashCode(), Intent(this, EchoraRecordingService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE))

    private fun notification(text: String, paused: Boolean = false): Notification =
        NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("NexVora ECHORA")
            .setContentText(text).setOngoing(true).setOnlyAlertOnce(true).setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(if (paused) action(ACTION_RESUME, "Resume") else action(ACTION_PAUSE, "Pause")).addAction(action(ACTION_STOP, "Stop")).build()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26)
            getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Recording", NotificationManager.IMPORTANCE_LOW))
    }

    companion object {
        const val ACTION_START = "echora.START"; const val ACTION_PAUSE = "echora.PAUSE"
        const val ACTION_RESUME = "echora.RESUME"; const val ACTION_STOP = "echora.STOP"
        const val EXTRA_RATE = "rate"; const val EXTRA_CHANNELS = "channels"
        private const val CHANNEL = "echora_recording"; private const val NOTIF_ID = 42

        fun start(c: Context, rate: Int, channels: Int) = androidx.core.content.ContextCompat.startForegroundService(c,
            Intent(c, EchoraRecordingService::class.java).setAction(ACTION_START).putExtra(EXTRA_RATE, rate).putExtra(EXTRA_CHANNELS, channels))
        fun send(c: Context, action: String) = c.startService(Intent(c, EchoraRecordingService::class.java).setAction(action))
    }
}
