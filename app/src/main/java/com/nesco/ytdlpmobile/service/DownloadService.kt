package com.nesco.ytdlpmobile.service

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
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.nesco.ytdlpmobile.App
import com.nesco.ytdlpmobile.domain.NotificationThrottle
import com.nesco.ytdlpmobile.domain.notificationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.nesco.ytdlpmobile.MainActivity
import com.nesco.ytdlpmobile.R

/**
 * Keeps the process alive and shows progress while a download runs.
 *
 * It holds no download logic. The ViewModel owns the download, and this
 * service only reports what the ViewModel already decided.
 */
class DownloadService : Service() {

    private var stopped = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching: Job? = null
    private var seenRunning = false
    private val throttle = NotificationThrottle()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val percent = intent
            ?.takeIf { it.hasExtra(EXTRA_PERCENT) }
            ?.getFloatExtra(EXTRA_PERCENT, -1f)
            ?.takeIf { it >= 0f }
        val converting = intent?.getBooleanExtra(EXTRA_CONVERTING, false) == true

        // Go to the foreground first, on every start. The system started
        // this process for a foreground service and kills it if none
        // appears, even when the only thing left to do is stop.
        val inForeground = runCatching { goForeground(percent, converting) }.isSuccess

        if (!inForeground || intent?.action == ACTION_STOP) {
            stopNow()
        } else {
            watchDownloads()
        }
        return START_NOT_STICKY
    }

    /**
     * From Android 15 a dataSync service may run 6 hours in 24. The system
     * calls this at the limit. The service must stop within a few seconds,
     * or the system kills the process.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopNow()
    }

    /**
     * Follow the download itself. The activity may be gone, and the bar
     * has to keep moving without it.
     */
    private fun watchDownloads() {
        if (watching != null) return
        val downloads = (application as App).downloads
        watching = scope.launch {
            downloads.state.collect { job ->
                if (job.running) {
                    seenRunning = true
                    val view = notificationView(job.percent, job.converting)
                    if (throttle.accept(view, SystemClock.elapsedRealtime())) {
                        runCatching { goForeground(job.percent, job.converting) }
                    }
                } else if (seenRunning) {
                    stopNow()
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Safe to call twice. A second call does nothing. */
    private fun stopNow() {
        if (stopped) return
        stopped = true
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun goForeground(percent: Float?, converting: Boolean) {
        // This one counts against the rate too, so the watcher waits for it.
        throttle.accept(notificationView(percent, converting), SystemClock.elapsedRealtime())
        // The platform takes the type from API 29, and minSdk is 29.
        startForeground(
            NOTIFICATION_ID,
            buildNotification(percent, converting),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Downloads",
            // Low importance, so the phone shows the bar but makes no sound.
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.setShowBadge(false)
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(percent: Float?, converting: Boolean): Notification {
        val text = when {
            converting -> "Converting"
            percent != null -> "Downloading ${percent.toInt()}%"
            else -> "Downloading"
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(openTheApp())
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        // ffmpeg reports no percent while it converts, so the bar moves on
        // its own rather than sitting still at the last number.
        if (percent == null) builder.setProgress(0, 0, true)
        else builder.setProgress(100, percent.toInt().coerceIn(0, 100), false)
        return builder.build()
    }

    private fun openTheApp(): PendingIntent {
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val ACTION_PROGRESS = "com.nesco.ytdlpmobile.action.PROGRESS"
        const val ACTION_STOP = "com.nesco.ytdlpmobile.action.STOP"

        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_PERCENT = "percent"
        private const val EXTRA_CONVERTING = "converting"

        /** Start the service, or update the bar it already shows. */
        fun showProgress(context: Context, percent: Float?, converting: Boolean) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_PROGRESS
                if (percent != null) putExtra(EXTRA_PERCENT, percent)
                putExtra(EXTRA_CONVERTING, converting)
            }
            // The system refuses a foreground start from the background.
            // The download still runs, so a refusal must not crash the app.
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        /**
         * Stop the service. This does nothing when it is not running, so
         * the caller may send it at any time.
         */
        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, DownloadService::class.java)) }
        }
    }
}
