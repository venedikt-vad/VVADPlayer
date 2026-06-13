package com.vvad.vp.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vvad.vp.MainActivity
import com.vvad.vp.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DownloadService : android.app.Service() {

    companion object {
        const val CHANNEL_ID = "download_channel"
        const val NOTIFICATION_ID = 2
        const val ACTION_CANCEL = "com.vvad.vp.action.DOWNLOAD_CANCEL"
        const val ACTION_CANCEL_ALL = "com.vvad.vp.action.DOWNLOAD_CANCEL_ALL"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isForeground = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Immediate check before coroutine collector starts
        val initialState = DownloadManager.queueState.value
        if (initialState.isActive && !isForeground) {
            startForeground(NOTIFICATION_ID, buildNotification(initialState))
            isForeground = true
        }

        serviceScope.launch {
            DownloadManager.queueState.collect { state ->
                if (!isForeground && state.isActive) {
                    startForeground(NOTIFICATION_ID, buildNotification(state))
                    isForeground = true
                } else if (isForeground && !state.isActive) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    isForeground = false
                    stopSelf()
                } else if (isForeground) {
                    val manager = getSystemService(NotificationManager::class.java)
                    manager.notify(NOTIFICATION_ID, buildNotification(state))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                intent.getStringExtra("album_id")?.let { DownloadManager.cancel(it) }
            }
            ACTION_CANCEL_ALL -> {
                DownloadManager.cancelAll()
            }
        }

        // Robust check: ensure foreground if there's active work
        val currentState = DownloadManager.queueState.value
        if (currentState.isActive && !isForeground) {
            try {
                startForeground(NOTIFICATION_ID, buildNotification(currentState))
                isForeground = true
            } catch (e: Exception) {
                // Log or handle: foreground start failed (e.g., no notification permission)
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Album Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows album download progress"
            setShowBadge(true)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun updateNotification(state: QueueState) {
        if (!isForeground) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: QueueState): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val activeJob = state.activeJob
        val progress = (activeJob?.state as? DownloadJobState.Downloading)?.progress

        val title = if (activeJob != null) {
            "Downloading ${activeJob.albumName}"
        } else {
            "Downloads"
        }

        val text = if (progress != null) {
            if (progress.totalTracks > 0) {
                "${progress.currentTrack}/${progress.totalTracks} - ${progress.currentTrackName}"
            } else {
                "Starting..."
            }
        } else if (activeJob != null) {
            "Starting..."
        } else {
            ""
        }

        val max = progress?.totalTracks ?: 0
        val current = progress?.currentTrack ?: 0

        val cancelIntent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_CANCEL
            putExtra("album_id", activeJob?.albumId)
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, activeJob?.albumId?.hashCode() ?: 0,
            cancelIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.vp_applogowhite)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(max, current, max == 0)

        if (activeJob != null) {
            builder.addAction(
                R.drawable.ic_pause,
                "Cancel",
                cancelPendingIntent
            )
        }

        return builder.build()
    }
}
