package com.vvad.vp.ui.screens

import androidx.annotation.OptIn
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.vvad.vp.data.PlaybackManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(playbackManager: PlaybackManager, onClose: () -> Unit) {
    val track = playbackManager.currentTrack ?: return

    // Local state to hold the slider position during dragging
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    // Sync the local slider with the actual playback position ONLY when not dragging
    LaunchedEffect(playbackManager.currentPosition) {
        if (!isDragging) {
            sliderPosition = playbackManager.currentPosition.toFloat()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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

                Text(
                    text = playbackManager.currentAlbumName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f), // Takes available space
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1
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
            Text(track.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            Text(text = track.artists.joinToString(", ") { it.name },
                style = MaterialTheme.typography.titleLarge,
                color = Color.White.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Progress Bar
            Column(modifier = Modifier.fillMaxWidth()) {

                Slider(
                    value = sliderPosition,
                    onValueChange = {
                        isDragging = true
                        sliderPosition = it
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        playbackManager.seekTo(sliderPosition.toLong())
                    },
                    valueRange = 0f..playbackManager.duration.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Display loaded/played length (e.g., 01:23)
                    Text(
                        text = formatTime(playbackManager.currentPosition),
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