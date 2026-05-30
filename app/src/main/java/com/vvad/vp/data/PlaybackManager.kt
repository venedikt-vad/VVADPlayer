package com.vvad.vp.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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
        private const val BACK_BUFFER_MS = 10 * 60 * 1000
        private const val PREVIOUS_RESTART_THRESHOLD_MS = 5_000L
    }

    data class QueueEntry(
        val track: Track,
        val albumId: String,
        val albumName: String,
        val coverArtUrl: String
    )

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var playbackJob: Job? = null
    private var hasScrobbled = false

    var queue by mutableStateOf<List<QueueEntry>>(emptyList())
        private set
    var currentQueueIndex by mutableIntStateOf(-1)
        private set
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

    val exoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true
        )
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(30000, 50000, 2500, 5000)
                .setBackBuffer(BACK_BUFFER_MS, true)
                .build()
        )
        .setMediaSourceFactory(
            ProgressiveMediaSource.Factory(cacheDataSourceFactory, extractorsFactory)
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
                    updateCurrentTrackFromPlayer()
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

    private fun updateCurrentTrackFromPlayer() {
        val index = exoPlayer.currentMediaItemIndex
        if (index in queue.indices) {
            val entry = queue[index]
            currentQueueIndex = index
            currentTrack = entry.track
            currentAlbumId = entry.albumId
            currentAlbumName = entry.albumName
            currentCoverArtUrl = entry.coverArtUrl
            hasScrobbled = false
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
        replaceQueue(
            tracks = listOf(track),
            startIndex = 0,
            albumId = albumId,
            albumName = albumName,
            coverArtUrl = coverArtUrl
        )
    }

    fun replaceQueue(
        tracks: List<Track>,
        startIndex: Int,
        albumId: String,
        albumName: String,
        coverArtUrl: String
    ) {
        if (tracks.isEmpty()) return

        val newQueue = tracks.map { track ->
            QueueEntry(
                track = track,
                albumId = albumId,
                albumName = albumName,
                coverArtUrl = coverArtUrl
            )
        }
        queue = newQueue

        playbackJob?.cancel()
        playbackJob = scope.launch {
            try {
                val mediaItems = newQueue.map { entry -> buildMediaItem(entry) }

                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.setMediaItems(mediaItems, startIndex.coerceIn(0, mediaItems.lastIndex), C.TIME_UNSET)
                exoPlayer.playbackParameters = PlaybackParameters.DEFAULT
                exoPlayer.playWhenReady = true
                exoPlayer.prepare()
            } catch (e: Exception) {
                Log.e("Playback", "replaceQueue failed", e)
            }
        }
    }

    fun appendToQueue(track: Track, albumId: String, albumName: String, coverArtUrl: String) {
        val entry = QueueEntry(
            track = track,
            albumId = albumId,
            albumName = albumName,
            coverArtUrl = coverArtUrl
        )

        if (queue.isEmpty() || currentQueueIndex !in queue.indices || currentTrack == null) {
            replaceQueue(listOf(track), 0, albumId, albumName, coverArtUrl)
            return
        }

        queue = queue + entry
        scope.launch {
            try {
                val mediaItem = buildMediaItem(entry)
                exoPlayer.addMediaItem(mediaItem)
            } catch (e: Exception) {
                Log.e("Playback", "appendToQueue failed", e)
            }
        }
    }

    private suspend fun buildMediaItem(entry: QueueEntry): MediaItem {
        val track = entry.track
        val streamUrl = navidromeManager.getStreamUrl(track.id)
        val preferredFormat = navidromeManager.credentialsManager.getPreferredFormat()
        val preferredBitrate = navidromeManager.credentialsManager.getMaxBitrate()
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artists.joinToString(", ") { it.name })
            .apply {
                if (entry.coverArtUrl.isNotBlank()) setArtworkUri(Uri.parse(entry.coverArtUrl))
            }
            .build()
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(streamUrl)
            .setMimeType(preferredFormat.toAudioMimeType())
            .setCustomCacheKey(AudioCache.buildTrackCacheKey(track.id, preferredFormat, preferredBitrate))
            .setMediaMetadata(metadata)
            .build()
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

    fun next() {
        if (exoPlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT)) {
            exoPlayer.seekToNext()
        }
    }

    fun previous() {
        if (currentPosition > PREVIOUS_RESTART_THRESHOLD_MS || currentQueueIndex <= 0) {
            exoPlayer.seekTo(0)
        } else if (exoPlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS)) {
            exoPlayer.seekToPrevious()
        }
    }

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
