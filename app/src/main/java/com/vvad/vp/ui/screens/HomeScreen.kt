package com.vvad.vp.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vvad.vp.data.NavidromeManager
import com.vvad.vp.ui.models.Album
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(navidromeManager: NavidromeManager,
               onAlbumClick: (String) -> Unit,   // Added parameters
               onArtistClick: (String) -> Unit
) {
    // State to hold the random albums
    var randomAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // Fetch random albums when the screen loads
    LaunchedEffect(Unit) {
        scope.launch {
            randomAlbums = navidromeManager.fetchRandomAlbums(limit = 10)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Random Albums",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Horizontally scrolling row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(randomAlbums) { album ->
                AlbumTile(
                    album = album,
                    onAlbumClick = { onAlbumClick(album.id) },
                    onArtistClick = { onArtistClick(album.artistId) }
                    )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumTile(album: Album,
              onAlbumClick: () -> Unit,
              onArtistClick: () -> Unit) {
    Column(
        modifier = Modifier.width(130.dp)
    ) {
        AsyncImage(
            model = album.coverArtUrl,
            contentDescription = album.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(130.dp)
                .clip(RoundedCornerShape(3.dp))
                .clickable { onAlbumClick() } // Click on image
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onArtistClick() } // Click on text area
        ) {
            // Album Name with Marquee
            Text(
                text = album.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE, // Keeps it scrolling forever
                    delayMillis = 2000          // Waits 2 seconds before starting
                )
            )

            // Artist Name with Marquee
            Text(
                text = album.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                modifier = Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    delayMillis = 2000
                )
            )
        }
    }
}