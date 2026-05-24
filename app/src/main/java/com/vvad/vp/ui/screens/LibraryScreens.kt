package com.vvad.vp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vvad.vp.data.NavidromeManager
import com.vvad.vp.ui.models.Album

@Composable
fun SearchScreen(
    navidromeManager: NavidromeManager,
    topContentPadding: Dp = 0.dp,
    bottomContentPadding: Dp = 0.dp,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var artists by remember { mutableStateOf<List<Album>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        albums = navidromeManager.getAlbums()
        artists = navidromeManager.getArtists()
        isLoading = false
    }

    val trimmedQuery = query.trim()
    val filteredAlbums = remember(trimmedQuery, albums) {
        if (trimmedQuery.isBlank()) {
            emptyList()
        } else {
            albums.filter { album ->
                album.name.contains(trimmedQuery, ignoreCase = true) ||
                    album.artist.contains(trimmedQuery, ignoreCase = true)
            }.take(30)
        }
    }
    val filteredArtists = remember(trimmedQuery, artists) {
        if (trimmedQuery.isBlank()) {
            emptyList()
        } else {
            artists.filter { artist ->
                artist.name.contains(trimmedQuery, ignoreCase = true)
            }.take(30)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = 16.dp,
                top = topContentPadding + 12.dp,
                end = 16.dp,
                bottom = bottomContentPadding + 12.dp
            )
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            placeholder = {
                Text("Search albums and artists")
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            trimmedQuery.isBlank() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Type to search your library",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            filteredAlbums.isEmpty() && filteredArtists.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No matches found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (filteredArtists.isNotEmpty()) {
                        item {
                            SectionTitle("Artists")
                        }
                        items(filteredArtists, key = { "artist-${it.id}" }) { artist ->
                            LibraryArtistRow(
                                artist = artist,
                                onClick = { onArtistClick(artist.artistId) }
                            )
                        }
                    }

                    if (filteredAlbums.isNotEmpty()) {
                        item {
                            SectionTitle("Albums")
                        }
                        items(filteredAlbums, key = { "album-${it.id}" }) { album ->
                            LibraryAlbumRow(
                                album = album,
                                onAlbumClick = { onAlbumClick(album.id) },
                                onArtistClick = { onArtistClick(album.artistId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumsScreen(
    navidromeManager: NavidromeManager,
    topContentPadding: Dp = 0.dp,
    bottomContentPadding: Dp = 0.dp,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit
) {
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        albums = navidromeManager.getAlbums()
        isLoading = false
    }

    LibraryListScreen(
        title = "Albums",
        isLoading = isLoading,
        isEmpty = albums.isEmpty(),
        emptyText = "No albums found",
        topContentPadding = topContentPadding,
        bottomContentPadding = bottomContentPadding
    ) {
        items(albums, key = { it.id }) { album ->
            LibraryAlbumRow(
                album = album,
                onAlbumClick = { onAlbumClick(album.id) },
                onArtistClick = { onArtistClick(album.artistId) }
            )
        }
    }
}

@Composable
fun ArtistsScreen(
    navidromeManager: NavidromeManager,
    topContentPadding: Dp = 0.dp,
    bottomContentPadding: Dp = 0.dp,
    onArtistClick: (String) -> Unit
) {
    var artists by remember { mutableStateOf<List<Album>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        artists = navidromeManager.getArtists()
        isLoading = false
    }

    LibraryListScreen(
        title = "Artists",
        isLoading = isLoading,
        isEmpty = artists.isEmpty(),
        emptyText = "No artists found",
        topContentPadding = topContentPadding,
        bottomContentPadding = bottomContentPadding
    ) {
        items(artists, key = { it.id }) { artist ->
            LibraryArtistRow(
                artist = artist,
                onClick = { onArtistClick(artist.artistId) }
            )
        }
    }
}

@Composable
private fun LibraryListScreen(
    title: String,
    isLoading: Boolean,
    isEmpty: Boolean,
    emptyText: String,
    topContentPadding: Dp = 0.dp,
    bottomContentPadding: Dp = 0.dp,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    when {
        isLoading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topContentPadding, bottom = bottomContentPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        isEmpty -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topContentPadding, bottom = bottomContentPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = topContentPadding + 16.dp,
                    end = 16.dp,
                    bottom = bottomContentPadding + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    SectionTitle(title)
                }
                content()
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun LibraryAlbumRow(
    album: Album,
    onAlbumClick: () -> Unit,
    onArtistClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAlbumClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = album.coverArtUrl,
            contentDescription = album.name,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = buildString {
                    append(album.artist)
                    album.year?.let {
                        append(" • ")
                        append(it)
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onArtistClick() }
            )
        }
    }
}

@Composable
private fun LibraryArtistRow(
    artist: Album,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = artist.coverArtUrl,
            contentDescription = artist.name,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.size(12.dp))

        Column {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Artist",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
