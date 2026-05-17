package com.vvad.vp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vvad.vp.data.PlaybackManager

@Composable
fun PlayerStripe(
    playbackManager: PlaybackManager,
    onStripeClick: () -> Unit
) {
    val track = playbackManager.currentTrack ?: return

    // Direct observation of mutableStateOf / mutableLongStateOf (this is the correct way)
    val isPlaying = playbackManager.isPlaying
    val currentPosition = playbackManager.currentPosition
    val duration = playbackManager.duration

    val progress = if (duration > 0L) {
        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        tonalElevation = 8.dp,
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onStripeClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.65f),
                            Color.Black.copy(alpha = 0.92f)
                        )
                    )
                )
        ) {
            // Progress bar
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.15f)
            )

            Row(
                modifier = Modifier
                    .padding(top = 3.dp)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album Art
                AsyncImage(
                    model = playbackManager.currentCoverArtUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(MaterialTheme.shapes.small)
                )

                // Track Info
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp)
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        color = Color.White
                    )
                    Text(
                        text = playbackManager.currentAlbumName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                        maxLines = 1
                    )
                }

                // Play/Pause Button
                IconButton(onClick = { playbackManager.togglePlayPause() }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}