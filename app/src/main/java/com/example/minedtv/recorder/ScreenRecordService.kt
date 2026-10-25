package com.example.minedtv.recorder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.example.minedtv.MainActivity
import com.example.minedtv.R
import com.example.minedtv.util.Logger
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Foreground service that owns the screen capture while the user plays. */
class ScreenRecordService : Service() {
    private var engine: RecorderEngine? = null
    private var projection: MediaProjection? = null
    private val scope = MainScope()

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        // Feedback even when the user is inside a game.
        scope.launch {
            Recorder.events.collect { e ->
                when (e) {
                    is RecorderEvent.ClipSaved -> toast("Clip saved · ${e.seconds}s")
                    is RecorderEvent.ClipFailed -> toast(e.message)
                    RecorderEvent.ClipStarted -> Unit
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent)
            ACTION_CLIP -> {
                Logger.i("Clip requested from notification")
                Recorder.clip()
            }
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun start(intent: Intent) {
        if (engine != null) return
        val settings = Recorder.settings.value
        // Microphone and game-audio capture both need RECORD_AUDIO.
        val audioGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val micGranted = settings.mic && audioGranted
        val gameAudio = settings.gameAudio && audioGranted
        createChannel()
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        if (micGranted && Build.VERSION.SDK_INT >= 30) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        try {
            // Android 14+: the foreground service must be running before the projection is created.
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(settings.clipSeconds), type)
            val code = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val data = IntentCompat.getParcelableExtra(intent, EXTRA_RESULT_DATA, Intent::class.java)
                ?: error("Screen capture wasn't allowed")
            val p = getSystemService(MediaProjectionManager::class.java).getMediaProjection(code, data)
                ?: error("Screen capture wasn't allowed")
            p.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    if (projection == null) return // we stopped it ourselves
                    Logger.i("Screen capture ended by the system")
                    stopRecording()
                }
            }, Handler(Looper.getMainLooper()))
            projection = p
            val e = RecorderEngine(this, p, settings)
            try {
                e.start(micGranted, gameAudio)
            } catch (t: Throwable) {
                Thread { runCatching { e.stop() } }.start()
                throw t
            }
            engine = e
            Recorder.engine = e
            Recorder.status.value = RecorderStatus.Recording(System.currentTimeMillis(), e.width, e.height, e.hasMic, e.hasGameAudio)
        } catch (t: Throwable) {
            Logger.e("Couldn't start recording", t)
            stopRecording(error = t.message ?: "Couldn't start recording")
        }
    }

    private fun stopRecording(error: String? = null) {
        val e = engine
        val p = projection
        engine = null
        projection = null
        Recorder.engine = null
        Recorder.status.value = if (error != null) RecorderStatus.Error(error) else RecorderStatus.Idle
        Thread {
            runCatching { e?.stop() }.onFailure { Logger.e("Stop failed", it) }
            runCatching { p?.stop() }
        }.start()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (engine != null) stopRecording()
        scope.cancel()
        super.onDestroy()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notification(clipSeconds: Int): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val clip = PendingIntent.getService(this, 1, Intent(this, ScreenRecordService::class.java).setAction(ACTION_CLIP), flags)
        val stop = PendingIntent.getService(this, 2, Intent(this, ScreenRecordService::class.java).setAction(ACTION_STOP), flags)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("MinedTv is recording")
            .setContentText("Tap Clip it to save the last ${clipSeconds}s")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Clip it", clip)
            .addAction(0, "Stop", stop)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val ACTION_START = "com.example.minedtv.recorder.START"
        private const val ACTION_CLIP = "com.example.minedtv.recorder.CLIP"
        private const val ACTION_STOP = "com.example.minedtv.recorder.STOP"
        private const val EXTRA_RESULT_CODE = "resultCode"
        private const val EXTRA_RESULT_DATA = "resultData"
        private const val CHANNEL_ID = "recorder"
        private const val NOTIFICATION_ID = 42

        /** [resultCode]/[data] come from the screen-capture consent dialog. */
        fun start(context: Context, resultCode: Int, data: Intent) {
            Recorder.status.value = RecorderStatus.Starting
            ContextCompat.startForegroundService(
                context,
                Intent(context, ScreenRecordService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_RESULT_CODE, resultCode)
                    .putExtra(EXTRA_RESULT_DATA, data)
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ScreenRecordService::class.java).setAction(ACTION_STOP))
        }
    }
}
