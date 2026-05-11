// PlaybackManager.kt
package com.vvad.vp.data


import android.content.Context
import androidx.compose.runtime.*
import android.util.Log // Added for logging
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.vvad.vp.ui.models.Track
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

@UnstableApi
class PlaybackManager(context: Context, private val navidromeManager: NavidromeManager) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var playbackJob: Job? = null // Track the current loading task
    private var hasScrobbled = false


    var currentTrack by mutableStateOf<Track?>(null)
    var currentAlbumName by mutableStateOf("")
    var currentCoverArtUrl by mutableStateOf("")
    var isPlaying by mutableStateOf(false)
    var currentPosition by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)


    private val exoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true // This automatically handles Audio Focus for you
        )
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    30000, // minBufferMs
                    50000, // maxBufferMs
                    2500,  // bufferForPlaybackMs (This helps with the clicking!)
                    5000   // bufferForPlaybackAfterRebufferMs
                )
                .build()
        )
        .build().apply {
        addListener(object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlayingStatus: Boolean) {
                this@PlaybackManager.isPlaying = isPlayingStatus
            }
        })
    }

    init {
        // Coroutine to poll the current position every second
        scope.launch {
            while (true) {
                if (exoPlayer.duration > 0) {
                    // If ExoPlayer has the exact duration from the stream, use it
                    duration = exoPlayer.duration
                } else {
                    // Fallback: Use duration from track metadata (converted to ms)
                    duration = (currentTrack?.duration?.toLong() ?: 0L) * 1000
                }

                if (isPlaying) {
                    currentPosition = exoPlayer.currentPosition
                }

                if (isPlaying && !hasScrobbled && currentPosition > 30000) {
                    currentTrack?.let { track ->
                        launch(Dispatchers.IO) {
                            scrobble(track.id)
                        }
                    }
                    hasScrobbled = true
                }

                delay(500) // Poll twice a second for a smooth progress bar
            }
        }

        exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlayingStatus: Boolean) {
                this@PlaybackManager.isPlaying = isPlayingStatus
            }
            // Ensure duration is updated when a new track is ready
            override fun onPlaybackStateChanged(state: Int) {
                if (state == androidx.media3.common.Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                }
            }
        })
    }

    fun seekTo(position: Long) {
        if (exoPlayer.playbackState != androidx.media3.common.Player.STATE_IDLE) {
            exoPlayer.seekTo(position)
        }
    }

    fun play(track: Track, albumName: String, coverArtUrl: String) {
        playbackJob?.cancel()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()

        currentTrack = track
        currentAlbumName = albumName
        currentCoverArtUrl = coverArtUrl

        playbackJob = scope.launch {
            try {
                val streamUrl = navidromeManager.getStreamUrl(track.id)
                val mediaItem = MediaItem.fromUri(streamUrl)

                exoPlayer.setMediaItem(mediaItem)

                // "playWhenReady = true" tells it to play automatically once buffered.
                // By setting it here, ExoPlayer handles the transition from
                // BUFFERING to READY smoothly without manual interference.
                exoPlayer.playWhenReady = true
                exoPlayer.prepare()

            } catch (e: Exception) {
                Log.e("Playback", "Playback failed", e)
            }
        }
    }

    private suspend fun scrobble(trackId: String) {
        val baseUrl = navidromeManager.credentialsManager.getFullServerUrl()
        val user = navidromeManager.credentialsManager.getUsername()
        val pass = navidromeManager.credentialsManager.getPassword()
        val salt = (1..6).map { (('A'..'Z') + ('a'..'z') + ('0'..'9')).random() }.joinToString("")
        val token = md5(pass + salt) // Using your existing md5 util

        // submission=true is required for Navidrome to mark it played [cite: 895, 515]
        val scrobbleUrl = "$baseUrl/rest/scrobble?u=$user&t=$token&s=$salt" +
                "&v=1.16.1&c=VVADPlayer&id=$trackId&submission=true&f=json"

        try {
            val connection = java.net.URL(scrobbleUrl).openConnection() as java.net.HttpURLConnection
            if (connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                Log.d("PlaybackManager", "Scrobble successful for $trackId")
            }
        } catch (e: Exception) {
            Log.e("PlaybackManager", "Scrobble failed", e)
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    fun next() { /* Implement next track logic later */ }
    fun previous() { /* Implement previous track logic later */ }

    fun release() {
        exoPlayer.release()
    }
}