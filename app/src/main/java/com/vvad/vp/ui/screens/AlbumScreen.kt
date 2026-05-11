package com.vvad.vp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vvad.vp.data.NavidromeManager
import com.vvad.vp.ui.models.AlbumDetails
import com.vvad.vp.ui.models.Track

@Composable
fun AlbumScreen(
    albumId: String?,
    navidromeManager: NavidromeManager,
    onBack: () -> Unit,
    onArtistClick: (String) -> Unit,
    onTrackClick: (Track, String, String) -> Unit
) {
    var album by remember { mutableStateOf<AlbumDetails?>(null) }

    LaunchedEffect(albumId) {
        if (albumId != null) {
            album = navidromeManager.fetchAlbum(albumId)
        }
    }

    album?.let { details ->
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Blurred Background Image
            AsyncImage(
                model = details.coverArtUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(20.dp) // Blur the logo
            )

            // Dark overlay for readability
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Black.copy(0.4f), Color.Black.copy(0.9f)))
            ))

            // 2. Content
            @OptIn(ExperimentalMaterial3Api::class)
            Scaffold(
                containerColor = Color.Transparent
            ) { padding ->
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Full-width Logo/Cover
                    item {
                        AsyncImage(
                            model = details.coverArtUrl,
                            contentDescription = details.name,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(24.dp)
                        )

                        Text(details.name, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                        Text(details.artist, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(0.7f))
                        if (details.year != 0) {
                            Text("${details.year}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.5f))
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // Group tracks by disc number
                    val grouped = details.tracks.groupBy { it.discNumber }
                    grouped.forEach { (disc, tracks) ->
                        if (grouped.size > 1) {
                            item {
                                Text(
                                    "Disc $disc",
                                    modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        items(tracks) { track ->
                            TrackItem(
                                track = track,
                                onArtistClick = onArtistClick,
                                modifier = Modifier.clickable {
                                    onTrackClick(track, details.name, details.coverArtUrl)
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
}

@Composable
fun TrackItem(
    track: Track,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        modifier = modifier,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(track.title, color = Color.White) },
        supportingContent = {
            Row(modifier = Modifier.fillMaxWidth()) {
                track.artists.forEachIndexed { index, artist ->
                    Text(
                        text = artist.name,
                        color = Color.White.copy(0.7f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickable { onArtistClick(artist.id) }
                    )
                    if (index < track.artists.size - 1) {
                        Text(", ", color = Color.White.copy(0.7f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        leadingContent = {
            Text(
                text = track.number.toString(),
                modifier = Modifier.width(24.dp),
                color = Color.White.copy(0.5f),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        trailingContent = {
            val mins = track.duration / 60
            val secs = track.duration % 60
            Text("%d:%02d".format(mins, secs), color = Color.White.copy(0.5f))
        }
    )
}