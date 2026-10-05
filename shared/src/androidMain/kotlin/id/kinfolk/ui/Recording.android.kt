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
}

@Composable
actual fun rememberRecorder(): Recorder {
    val context = LocalContext.current
    val file = remember(context) { File(context.filesDir, "visit.m4a") }
    val started = remember { arrayOfNulls<(Boolean) -> Unit>(1) } // who asked, until the permission answer
    // The microphone first: the service is only started once recording runs, so it always reaches startForeground.
    fun begin(): Boolean = try {
        Mic.recorder?.release()
        Mic.recorder = null
        file.delete()
        @Suppress("DEPRECATION")
        Mic.recorder = (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            // ~0.5 MB a minute: an hour fits the 50 MB bucket limit.
            setAudioChannels(1); setAudioSamplingRate(16_000); setAudioEncodingBitRate(64_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java))
        true
    } catch (_: Exception) {
        Mic.recorder?.release()
        Mic.recorder = null
        false
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        started[0]?.invoke(granted[Manifest.permission.RECORD_AUDIO] == true && begin())
    }
    return remember(context) {
        Recorder(
            start = { onStarted ->
                started[0] = onStarted
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) onStarted(begin())
                // The notification is only shown with permission (Android 13+); recording goes on without it.
                else ask.launch(listOfNotNull(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS.takeIf { Build.VERSION.SDK_INT >= 33 }).toTypedArray())
            },
            pause = { runCatching { Mic.recorder?.pause() } },
            resume = { runCatching { Mic.recorder?.resume() } },
            level = { Mic.recorder?.let { r -> runCatching { sqrt(r.maxAmplitude / 32767f) }.getOrNull() } ?: 0f },
            stop = {
                val r = Mic.recorder
                Mic.recorder = null
                val ok = r != null && runCatching { r.stop() }.isSuccess
                r?.release()
                context.stopService(Intent(context, RecordingService::class.java))
                // ponytail: the file stays until the next recording, for #48 to resume or resend it.
                if (ok) file.readBytes() else null
            },
        )
    }
}
