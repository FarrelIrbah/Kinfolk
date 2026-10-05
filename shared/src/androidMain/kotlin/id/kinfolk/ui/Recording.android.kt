package id.kinfolk.ui

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.io.File
import kotlin.math.sqrt

/** Keeps the app's microphone on with the screen off: a foreground service, as Android requires (androidApp's manifest). */
class RecordingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channel = "recording"
        val notifications = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) notifications.createNotificationChannel(NotificationChannel(channel, "Merekam", NotificationManager.IMPORTANCE_LOW))
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            android.app.PendingIntent.getActivity(this, 0, it, android.app.PendingIntent.FLAG_IMMUTABLE)
        }
        @Suppress("DEPRECATION")
        val n = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channel) else Notification.Builder(this))
            .setSmallIcon(applicationInfo.icon).setContentTitle("Kinfolk").setContentText("Merekam").setOngoing(true).setContentIntent(open).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) else startForeground(1, n)
        return START_NOT_STICKY
    }
}

/** One recording at a time, outliving the screen that started it. */
private object Mic {
    var recorder: MediaRecorder? = null
    var appointment: String? = null
    var failed = false
}

@Composable
actual fun rememberRecorder(): Recorder {
    val context = LocalContext.current
    // <appointment>-<n>.aac, one part per start; <appointment>.stopped once ended with Stop.
    val dir = remember(context) { File(context.filesDir, "visits").apply { mkdirs() } }
    fun parts(id: String) = dir.listFiles { f -> f.name.startsWith("$id-") }.orEmpty().sortedBy { it.name.substringAfterLast('-').substringBefore('.').toInt() }
    fun done(id: String) = File(dir, "$id.stopped")
    val started = remember { arrayOfNulls<(Boolean) -> Unit>(1) } // who asked, until the permission answer
    val asked = remember { arrayOfNulls<String>(1) }
    fun end() {
        Mic.recorder?.release()
        Mic.recorder = null
        context.stopService(Intent(context, RecordingService::class.java))
    }
    // The microphone first: the service is only started once recording runs, so it always reaches startForeground.
    fun begin(id: String): Boolean = try {
        end()
        Mic.failed = false
        Mic.appointment = id
        done(id).delete()
        val file = File(dir, "$id-${parts(id).size}.aac")
        @Suppress("DEPRECATION")
        Mic.recorder = (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            // ~0.5 MB a minute: an hour fits the 50 MB bucket limit.
            setAudioChannels(1); setAudioSamplingRate(16_000); setAudioEncodingBitRate(64_000)
            setOutputFile(file.absolutePath)
            setOnErrorListener { _, _, _ -> Mic.failed = true; end() } // e.g. the microphone went away
            prepare()
            start()
        }
        ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java))
        true
    } catch (_: Exception) {
        end()
        false
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        started[0]?.invoke(granted[Manifest.permission.RECORD_AUDIO] == true && begin(asked[0]!!))
    }
    return remember(context) {
        Recorder(
            start = { id, onStarted ->
                started[0] = onStarted
                asked[0] = id
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) onStarted(begin(id))
                // The notification is only shown with permission (Android 13+); recording goes on without it.
                else ask.launch(listOfNotNull(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS.takeIf { Build.VERSION.SDK_INT >= 33 }).toTypedArray())
            },
            pause = { runCatching { Mic.recorder?.pause() } },
            resume = { runCatching { Mic.recorder?.resume() } },
            level = { Mic.recorder?.let { r -> runCatching { sqrt(r.maxAmplitude / 32767f) }.getOrNull() } ?: 0f },
            failed = { Mic.failed },
            stop = {
                Mic.recorder?.let { r -> runCatching { r.stop() } }
                Mic.appointment?.takeUnless { Mic.failed }?.let { done(it).createNewFile() }
                end()
            },
            // Not while that recording is still running.
            parts = { id -> if (Mic.recorder != null && Mic.appointment == id) emptyList() else parts(id).map { it.readBytes() } },
            stopped = { id -> done(id).exists() },
            discard = { id -> if (Mic.appointment == id) end(); parts(id).forEach { it.delete() }; done(id).delete() },
        )
    }
}
