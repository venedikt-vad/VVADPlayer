package com.vvad.vp.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaSession.ControllerInfo
import com.vvad.vp.MainActivity
import com.vvad.vp.R

@UnstableApi
class PlaybackService : MediaSessionService() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "playback_channel"
        const val NOTIFICATION_ID = 1
    }

    private var mediaSession: MediaSession? = null
    lateinit var playbackManager: PlaybackManager
        private set

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        val credentialsManager = CredentialsManager(this)
        val offlineLibraryManager = OfflineLibraryManager(this)
        val navidromeManager = NavidromeManager(credentialsManager, offlineLibraryManager)
        playbackManager = PlaybackManager(this, navidromeManager)

        mediaSession = MediaSession.Builder(this, playbackManager.exoPlayer)
            .build()

        startForeground(NOTIFICATION_ID, buildNotification())

        playbackManager.exoPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                updateNotification()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateNotification()
            }
        })
    }

    override fun onBind(intent: Intent?): IBinder? {
        super.onBind(intent)
        return binder
    }

    override fun onGetSession(controllerInfo: ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.release()
        if (::playbackManager.isInitialized) {
            playbackManager.release()
        }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = playbackManager.exoPlayer
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val track = playbackManager.currentTrack
        val title = track?.title ?: "VVAD Player"
        val artist = track?.artists?.joinToString(", ") { it.name } ?: "Now Playing"

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.vp_applogowhite)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }
}
