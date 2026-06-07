package com.vvad.vp.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.vvad.vp.data.PlaybackManager
import com.vvad.vp.ui.theme.VVADRed
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@kotlin.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    playbackManager: PlaybackManager,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onClose: () -> Unit
) {
    val track = playbackManager.currentTrack ?: return
    val currentAlbumId = playbackManager.currentAlbumId
    val interactionSource = remember { MutableInteractionSource() }

    // Local state to hold the slider position during dragging
    var sliderPosition by remember(track.id) { mutableFloatStateOf(0f) }
    var isDragging by remember(track.id) { mutableStateOf(false) }
    val sliderRangeMax = playbackManager.duration.toFloat().coerceAtLeast(1f)
    val displayedPosition = if (isDragging) sliderPosition.toLong() else playbackManager.currentPosition
    val bufferedPosition = playbackManager.bufferedPosition.coerceIn(0L, playbackManager.duration.coerceAtLeast(0L))
    val trackHorizontalInset = 10.dp
    val bufferedProgress = if (playbackManager.duration > 0L) {
        bufferedPosition.toFloat() / playbackManager.duration.toFloat()
    } else {
        0f
    }

    BackHandler(onBack = onClose)

    // Sync the local slider with the actual playback position ONLY when not dragging
    LaunchedEffect(track.id, playbackManager.currentPosition) {
        if (!isDragging) {
            sliderPosition = playbackManager.currentPosition.toFloat()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {}
            )
    ) {
        // 1. Blurred Background
        AsyncImage(
            model = playbackManager.currentCoverArtUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(30.dp) // API 31+ or requires modern Compose BOM
        )
        // Dark overlay for readability
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))

        // 2. Main Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Down arrow to close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                MarqueeText(
                    text = playbackManager.currentAlbumName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .weight(1f)
                        .let { base ->
                            if (!currentAlbumId.isNullOrBlank()) {
                                base.clickable { onAlbumClick(currentAlbumId) }
                            } else {
                                base
                            }
                        },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                // Empty Box to balance the layout so the title stays centered
                Box(modifier = Modifier.size(48.dp))
            }

            // Large Cover Art
            AsyncImage(
                model = playbackManager.currentCoverArtUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Track Info
            MarqueeText(
                text = track.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center // Centered song name
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                track.artists.forEachIndexed { index, artist ->
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = if (artist.id.isNotBlank()) {
                            Modifier.clickable { onArtistClick(artist.id) }
                        } else {
                            Modifier
                        },
                        maxLines = 1
                    )
                    if (index < track.artists.size - 1) {
                        Text(
                            text = ", ",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Progress Bar
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    LinearProgressIndicator(
                        progress = { bufferedProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = trackHorizontalInset)
                            .height(4.dp),
                        color = VVADRed.copy(alpha = 0.35f),
                        trackColor = VVADRed.copy(alpha = 0.14f)
                    )
                    Slider(
                        value = sliderPosition.coerceIn(0f, sliderRangeMax),
                        onValueChange = {
                            isDragging = true
                            sliderPosition = it.coerceIn(0f, sliderRangeMax)
                        },
                        onValueChangeFinished = {
                            isDragging = false
                            playbackManager.seekTo(sliderPosition.toLong())
                        },
                        modifier = Modifier.fillMaxWidth(),
                        valueRange = 0f..sliderRangeMax,
                        colors = SliderDefaults.colors(
                            thumbColor = VVADRed,
                            activeTrackColor = VVADRed,
                            inactiveTrackColor = Color.Transparent
                        )
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Display loaded/played length (e.g., 01:23)
                    Text(
                        text = formatTime(displayedPosition),
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall
                    )

                    // Display total length from metadata (e.g., 04:50)
                    Text(
                        text = formatTime(playbackManager.duration),
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Playback Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { playbackManager.previous() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(48.dp))
                }

                FloatingActionButton(
                    onClick = { playbackManager.togglePlayPause() },
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    shape = androidx.compose.foundation.shape.CircleShape
                ) {
                    Icon(
                        imageVector = if (playbackManager.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(onClick = { playbackManager.next() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(48.dp))
                }
            }
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

@kotlin.OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MarqueeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign = TextAlign.Start
) {
    Text(
        text = text,
        style = style,
        fontWeight = fontWeight,
        color = color,
        maxLines = 1,
        textAlign = textAlign,
        modifier = modifier.basicMarquee(
            iterations = Int.MAX_VALUE,
            delayMillis = 2000
        )
    )
}
