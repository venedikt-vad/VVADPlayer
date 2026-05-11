package com.vvad.vp.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.vvad.vp.ui.models.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@UnstableApi
class PlaybackManager(context: Context, private val navidromeManager: NavidromeManager) {
    companion object {
        // Keep a generous rewind window in memory so recent backward seeks don't need to rebuffer.
        private const val BACK_BUFFER_MS = 10 * 60 * 1000
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var playbackJob: Job? = null
    private var hasScrobbled = false

    var currentTrack by mutableStateOf<Track?>(null)
    var currentAlbumId by mutableStateOf<String?>(null)
    var currentAlbumName by mutableStateOf("")
    var currentCoverArtUrl by mutableStateOf("")
    var isPlaying by mutableStateOf(false)
    var currentPosition by mutableLongStateOf(0L)
    var bufferedPosition by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)

    private val extractorsFactory = AudioCache.buildExtractorsFactory()
    private val cacheDataSourceFactory = AudioCache.buildCacheDataSourceFactory(appContext)

    private val exoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true
        )
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    30000,
                    50000,
                    2500,
                    5000
                )
                .setBackBuffer(BACK_BUFFER_MS, true)
                .build()
        )
        .build()
        .apply {
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setAudioOffloadPreferences(
                    TrackSelectionParameters.AudioOffloadPreferences.Builder()
                        .setAudioOffloadMode(
                            TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                        )
                        .build()
                )
                .build()

            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlayingStatus: Boolean) {
                    this@PlaybackManager.isPlaying = isPlayingStatus
                    syncProgress()
                }

                override fun onPlaybackStateChanged(state: Int) {
                    syncProgress()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    syncProgress()
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    syncProgress()
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Log.e("Playback", "ExoPlayer error", error)
                    syncProgress()
                }
            })
        }

    init {
        syncProgress()

        scope.launch {
            while (true) {
                syncProgress()

                if (isPlaying && !hasScrobbled && currentPosition > 30000) {
                    currentTrack?.let { track ->
                        launch(Dispatchers.IO) {
                            scrobble(track.id)
                        }
                    }
                    hasScrobbled = true
                }

                delay(250)
            }
        }
    }

    fun seekTo(position: Long) {
        if (exoPlayer.playbackState == Player.STATE_IDLE) return
        if (!exoPlayer.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) return

        val resolvedDuration = resolveDurationMs()
        val target = if (resolvedDuration > 0L) {
            position.coerceIn(0L, resolvedDuration)
        } else {
            position.coerceAtLeast(0L)
        }

        currentPosition = target
        exoPlayer.seekTo(target)
        syncProgress()
    }

    fun play(track: Track, albumId: String, albumName: String, coverArtUrl: String) {
        playbackJob?.cancel()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()

        currentTrack = track
        currentAlbumId = albumId
        currentAlbumName = albumName
        currentCoverArtUrl = coverArtUrl
        currentPosition = 0L
        bufferedPosition = 0L
        duration = track.duration.toLong() * 1000L
        hasScrobbled = false

        playbackJob = scope.launch {
            try {
                val streamUrl = navidromeManager.getStreamUrl(track.id)
                val preferredFormat = navidromeManager.credentialsManager.getPreferredFormat()
                val preferredBitrate = navidromeManager.credentialsManager.getMaxBitrate()
                val mediaItem = MediaItem.Builder()
                    .setUri(streamUrl)
                    .setMimeType(preferredFormat.toAudioMimeType())
                    .setCustomCacheKey(AudioCache.buildTrackCacheKey(track.id, preferredFormat, preferredBitrate))
                    .build()
                val mediaSource = ProgressiveMediaSource.Factory(cacheDataSourceFactory, extractorsFactory)
                    .createMediaSource(mediaItem)

                exoPlayer.setMediaSource(mediaSource)
                exoPlayer.playbackParameters = PlaybackParameters.DEFAULT
                exoPlayer.playWhenReady = true
                exoPlayer.prepare()
                syncProgress()
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
        val token = md5(pass + salt)

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
        playbackJob?.cancel()
        exoPlayer.release()
    }

    private fun syncProgress() {
        currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
        bufferedPosition = exoPlayer.bufferedPosition.coerceAtLeast(currentPosition)
        duration = maxOf(resolveDurationMs(), currentPosition)
        isPlaying = exoPlayer.isPlaying
    }

    private fun resolveDurationMs(): Long {
        val playerDuration = exoPlayer.duration
        return if (playerDuration != C.TIME_UNSET && playerDuration > 0L) {
            playerDuration
        } else {
            (currentTrack?.duration?.toLong() ?: 0L) * 1000L
        }
    }

    private fun String.toAudioMimeType(): String? = when (lowercase()) {
        "mp3" -> MimeTypes.AUDIO_MPEG
        "aac" -> MimeTypes.AUDIO_AAC
        "ogg" -> MimeTypes.AUDIO_OGG
        "flac" -> MimeTypes.AUDIO_FLAC
        else -> null
    }
}
