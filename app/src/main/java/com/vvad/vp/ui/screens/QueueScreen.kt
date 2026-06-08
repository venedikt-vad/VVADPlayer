package com.vvad.vp.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vvad.vp.data.PlaybackManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    playbackManager: PlaybackManager,
    onBack: () -> Unit,
    bottomContentPadding: Dp = 0.dp
) {
    val queue = playbackManager.queue
    val currentIndex = playbackManager.currentQueueIndex

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Queue") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (queue.size > 1) {
                        IconButton(onClick = { playbackManager.clearQueueExceptCurrent() }) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Clear queue",
                                tint = Color.White
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        if (queue.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Queue is empty", color = Color.White.copy(alpha = 0.5f))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(bottom = bottomContentPadding)
            ) {
                itemsIndexed(
                    items = queue,
                    key = { index, entry -> "${entry.track.id}_$index" }
                ) { index, entry ->
                    val isCurrent = index == currentIndex

                    val row = @Composable {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { playbackManager.seekToQueueIndex(index) }
                                .background(
                                    if (isCurrent) Color.White.copy(alpha = 0.1f)
                                    else Color.Black
                                )
                                .padding(vertical = 8.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = entry.coverArtUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.White.copy(alpha = 0.1f))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.track.title,
                                    color = if (isCurrent) Color(0xFFE53935) else Color.White,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1
                                )
                                Text(
                                    text = entry.track.artists.joinToString(", ") { it.name },
                                    color = Color.White.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1
                                )
                            }
                            Text(
                                text = entry.albumName,
                                color = Color.White.copy(alpha = 0.4f),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                    }

                    if (isCurrent) {
                        row()
                    } else {
                        SwipeToDismissBox(
                            state = rememberSwipeToDismissBoxState(
                                confirmValueChange = { dismissValue ->
                                    if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                        playbackManager.removeFromQueue(entry.track.id)
                                        true
                                    } else {
                                        false
                                    }
                                }
                            ),
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                val color by animateColorAsState(
                                    targetValue = Color(0xFFE53935),
                                    label = "swipe-bg"
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(color)
                                        .padding(end = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(
                                        Icons.Default.DeleteSweep,
                                        contentDescription = "Remove",
                                        tint = Color.White
                                    )
                                }
                            },
                            content = { row() }
                        )
                    }

                    if (index < queue.lastIndex) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    }
                }
            }
        }
    }
}
