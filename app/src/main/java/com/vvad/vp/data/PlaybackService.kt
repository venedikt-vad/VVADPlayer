package com.vvad.vp.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaSession.ControllerInfo
import com.vvad.vp.MainActivity
import com.vvad.vp.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

@UnstableApi
class PlaybackService : MediaSessionService() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "playback_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY_PAUSE = "com.vvad.vp.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.vvad.vp.action.NEXT"
        const val ACTION_PREVIOUS = "com.vvad.vp.action.PREVIOUS"
    }

    private var mediaSession: MediaSession? = null
    lateinit var playbackManager: PlaybackManager
        private set

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressUpdateJob: Job? = null
    private var albumArtBitmap: Bitmap? = null
    private var lastCoverArtUrl: String? = null

    inner class LocalBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        val credentialsManager = CredentialsManager(this)
        val offlineLibraryManager = OfflineLibraryManager(this, credentialsManager)
        val navidromeManager = NavidromeManager(credentialsManager, offlineLibraryManager)
        playbackManager = PlaybackManager(this, navidromeManager)

        playbackManager.setTrackChangeListener(object : TrackChangeListener {
            override fun onTrackChanged() {
                loadAlbumArt()
                updateNotification()
            }
        })

        mediaSession = MediaSession.Builder(this, playbackManager.exoPlayer)
            .build()

        startForeground(NOTIFICATION_ID, buildNotification())

        playbackManager.exoPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                loadAlbumArt()
                updateNotification()
            }

            override fun onPlaybackStateChanged(state: Int) {
                updateNotification()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateNotification()
                if (isPlaying) startProgressUpdates() else stopProgressUpdates()
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> playbackManager.togglePlayPause()
            ACTION_NEXT -> playbackManager.next()
            ACTION_PREVIOUS -> playbackManager.previous()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        super.onBind(intent)
        return binder
    }

    override fun onGetSession(controllerInfo: ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        progressUpdateJob?.cancel()
        serviceScope.cancel()
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

    private fun buildActionIntent(action: String): PendingIntent {
        return PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, PlaybackService::class.java).apply { this.action = action },
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun loadAlbumArt() {
        val coverUrl = playbackManager.currentCoverArtUrl
        if (coverUrl == lastCoverArtUrl) return
        lastCoverArtUrl = coverUrl

        if (coverUrl.isBlank()) {
            albumArtBitmap = null
            return
        }

        serviceScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    val url = URL(coverUrl)
                    val conn = url.openConnection().apply {
                        connectTimeout = 5000
                        readTimeout = 5000
                    }
                    BitmapFactory.decodeStream(conn.getInputStream())
                } catch (_: Exception) {
                    null
                }
            }
            albumArtBitmap = bitmap
            updateNotification()
        }
    }

    private fun startProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = serviceScope.launch {
            while (true) {
                delay(1000)
                updateNotification()
            }
        }
    }

    private fun stopProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
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

        val prevAction = NotificationCompat.Action(
            R.drawable.ic_skip_previous,
            "Previous",
            buildActionIntent(ACTION_PREVIOUS)
        )

        val playPauseAction = NotificationCompat.Action(
            if (playbackManager.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
            if (playbackManager.isPlaying) "Pause" else "Play",
            buildActionIntent(ACTION_PLAY_PAUSE)
        )

        val nextAction = NotificationCompat.Action(
            R.drawable.ic_skip_next,
            "Next",
            buildActionIntent(ACTION_NEXT)
        )

        val position = playbackManager.currentPosition
        val totalDuration = playbackManager.duration
        val progress = if (totalDuration > 0) {
            (position * 1000 / totalDuration).toInt().coerceIn(0, 1000)  // 0-1000 for smoother notification progress
        } else 0

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.vp_applogowhite)
            .setContentTitle(title)
            .setContentText(artist)
            .setLargeIcon(albumArtBitmap)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession?.sessionCompatToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            // Use explicit progress (matches PlayerScreen behavior; falls back to metadata)
            .setProgress(1000, progress, totalDuration <= 0)
            // Optional: Show elapsed / remaining time in content info (visible in expanded view)
            .setContentInfo("${formatTime(position)} / ${formatTime(totalDuration)}")
            .build()
    }

    private fun formatTime(milliseconds: Long): String {
        if (milliseconds <= 0) return "00:00"
        val totalSeconds = milliseconds / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }
}
