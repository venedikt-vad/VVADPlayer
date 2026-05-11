package com.vvad.vp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vvad.vp.data.PlaybackManager
import androidx.compose.material.icons.filled.Pause


@Composable
fun PlayerStripe(playbackManager: PlaybackManager, onStripeClick: () -> Unit) {
    val track = playbackManager.currentTrack ?: return // Don't show if nothing is playing

    Surface(
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onStripeClick() }
    ) {
        Row(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = playbackManager.currentCoverArtUrl,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small)
            )

            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(track.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(playbackManager.currentAlbumName, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }

            IconButton(onClick = { playbackManager.togglePlayPause() }) {
                Icon(
                    imageVector = if (playbackManager.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause"
                )
            }
        }
    }
}

fun onNextTrack() = println("Next Track")
fun onPreviousTrack() = println("Previous Track")