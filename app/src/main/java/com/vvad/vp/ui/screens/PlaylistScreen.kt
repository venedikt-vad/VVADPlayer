package com.vvad.vp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vvad.vp.data.NavidromeManager
import com.vvad.vp.data.PlaybackManager
import com.vvad.vp.ui.components.LocalAppHaptics
import com.vvad.vp.ui.components.PlaylistCover
import com.vvad.vp.ui.models.PlaylistDetails
import com.vvad.vp.ui.models.Track

@Composable
fun PlaylistScreen(
    playlistId: String?,
    navidromeManager: NavidromeManager,
    playbackManager: PlaybackManager?,
    topContentPadding: Dp = 0.dp,
    bottomContentPadding: Dp = 0.dp,
    currentTrackId: String?,
    onBack: () -> Unit,
    onTrackClick: (List<Track>, Int, String, String, String) -> Unit,
    onTrackDoubleClick: (Track, String, String, String) -> Unit,
    onTrackLongClick: (Track, String, String, String) -> Unit
) {
    var playlist by remember(playlistId) { mutableStateOf<PlaylistDetails?>(null) }
    var isLoading by remember(playlistId) { mutableStateOf(true) }
    val flashCounts = remember(playlistId) { mutableStateMapOf<Int, Int>() }
    val haptics = LocalAppHaptics.current

    LaunchedEffect(playlistId) {
        if (playlistId == null) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        playlist = navidromeManager.getPlaylist(playlistId)
        isLoading = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val coverArtUrl = playlist?.coverArtUrl.orEmpty()

        if (coverArtUrl.isNotBlank()) {
            AsyncImage(
                model = coverArtUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(20.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(0.4f), Color.Black.copy(0.9f))
                        )
                    )
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        }

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            playlist == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Playlist unavailable",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(0.7f)
                    )
                }
            }

            else -> {
                val details = playlist!!
                val tracks = details.tracks
                val latestDetails by rememberUpdatedState(details)
                val latestTracks by rememberUpdatedState(tracks)
                val latestCurrentTrackId by rememberUpdatedState(currentTrackId)
                val latestOnTrackClick by rememberUpdatedState(onTrackClick)
                val latestOnTrackDoubleClick by rememberUpdatedState(onTrackDoubleClick)
                val latestOnTrackLongClick by rememberUpdatedState(onTrackLongClick)

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = topContentPadding + 8.dp,
                        bottom = bottomContentPadding + 16.dp
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    item {
                        PlaylistCover(
                            coverArtUrl = details.coverArtUrl,
                            contentDescription = details.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .padding(24.dp)
                        )

                        Text(
                            text = details.name,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )

                        Text(
                            text = buildString {
                                append(tracks.size)
                                append(if (tracks.size == 1) " song" else " songs")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(0.6f),
                            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                        )
                    }

                    if (tracks.isEmpty()) {
                        item {
                            Text(
                                text = "This playlist is empty",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(0.6f)
                            )
                        }
                    }

                    itemsIndexed(tracks) { index, track ->
                        TrackItem(
                            track = track,
                            enabled = true,
                            isCurrentlyPlaying = track.id == currentTrackId,
                            flashTrigger = flashCounts[index] ?: 0,
                            playbackManager = playbackManager,
                            modifier = Modifier.pointerInput(track.id, index) {
                                detectTapGestures(
                                    onTap = {
                                        if (track.id == latestCurrentTrackId) {
                                            playbackManager?.togglePlayPause()
                                        } else {
                                            latestOnTrackClick(
                                                latestTracks,
                                                index,
                                                latestDetails.id,
                                                latestDetails.name,
                                                latestDetails.coverArtUrl
                                            )
                                        }
                                    },
                                    onDoubleTap = {
                                        flashCounts[index] = (flashCounts[index] ?: 0) + 1
                                        haptics?.performLongPress()
                                        latestOnTrackDoubleClick(
                                            track,
                                            latestDetails.id,
                                            latestDetails.name,
                                            latestDetails.coverArtUrl
                                        )
                                    },
                                    onLongPress = {
                                        flashCounts[index] = (flashCounts[index] ?: 0) + 1
                                        haptics?.performLongPress()
                                        latestOnTrackLongClick(
                                            track,
                                            latestDetails.id,
                                            latestDetails.name,
                                            latestDetails.coverArtUrl
                                        )
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp)
                .align(Alignment.TopStart)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }
    }
}
