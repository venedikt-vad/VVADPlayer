package com.vvad.vp.ui.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.vvad.vp.data.NavidromeManager
import com.vvad.vp.data.OfflineLibraryManager
import com.vvad.vp.ui.models.Album
import kotlinx.coroutines.delay

private enum class HomeMode {
    Online, Offline
}

@Composable
fun HomeScreen(
    navidromeManager: NavidromeManager,
    offlineLibraryManager: OfflineLibraryManager,
    topContentPadding: Dp = 0.dp,
    bottomContentPadding: Dp = 0.dp,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit
) {
    var randomAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var offlineAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var recentlyPlayed by remember { mutableStateOf<List<Album>>(emptyList()) }
    var mostPlayedAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var mostPlayedArtists by remember { mutableStateOf<List<Album>>(emptyList()) }
    var homeMode by remember { mutableStateOf(HomeMode.Online) }

    val isNetworkAvailable by rememberNetworkAvailability()

    LaunchedEffect(isNetworkAvailable) {
        while (true) {
            offlineAlbums = offlineLibraryManager.getOfflineAlbums()

            if (isNetworkAvailable) {
                val fetchedAlbums = navidromeManager.getRandomAlbums(limit = 10)
                recentlyPlayed = navidromeManager.getRecentlyPlayedAlbums(limit = 8)
                mostPlayedAlbums = navidromeManager.getMostPlayedAlbums(limit = 8)
                mostPlayedArtists = navidromeManager.getMostPlayedArtists(limit = 8)

                randomAlbums = if (fetchedAlbums.isNotEmpty()) fetchedAlbums else emptyList()
                homeMode = HomeMode.Online
            } else {
                homeMode = HomeMode.Offline
            }

            delay(if (homeMode == HomeMode.Offline) 15_000L else 60_000L)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = topContentPadding + 16.dp,
            end = 16.dp,
            bottom = bottomContentPadding + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        if (homeMode == HomeMode.Offline) {
            item {
                Text(
                    text = if (isNetworkAvailable) "Cached Albums" else "Offline Library",
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            item {
                Text(
                    text = if (isNetworkAvailable) {
                        "Server is unreachable right now. Showing cached albums."
                    } else {
                        "No network connection. Showing cached albums."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (offlineAlbums.isEmpty()) {
                item {
                    Text(
                        text = "No cached albums available yet. Download an album while online to use it offline.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(offlineAlbums, key = { it.id }, contentType = { "album" }) { album ->
                    AlbumTile(
                        album = album,
                        onAlbumClick = { onAlbumClick(album.id) },
                        onArtistClick = {
                            if (album.artistId.isNotBlank()) onArtistClick(album.artistId)
                        }
                    )
                }
            }
        } else {
            // Available Offline
            if (offlineAlbums.isNotEmpty()) {
                item { Text("Available Offline", style = MaterialTheme.typography.titleLarge) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(offlineAlbums, key = { it.id }, contentType = { "album" }) { album ->
                            AlbumTile(
                                album = album,
                                onAlbumClick = { onAlbumClick(album.id) },
                                onArtistClick = {
                                    if (album.artistId.isNotBlank()) onArtistClick(album.artistId)
                                }
                            )
                        }
                    }
                }
            }

            randomAlbumsSection(randomAlbums, onAlbumClick, onArtistClick)
            if (recentlyPlayed.isNotEmpty()) {
                section("Recently Played", recentlyPlayed, onAlbumClick, onArtistClick)
            }
            if (mostPlayedAlbums.isNotEmpty()) {
                section("Most Played Albums", mostPlayedAlbums, onAlbumClick, onArtistClick)
            }
            if (mostPlayedArtists.isNotEmpty()) {
                artistsSection(mostPlayedArtists, onArtistClick)
            }
        }
    }
}

// ==================== LAZY LIST SECTIONS ====================

private fun LazyListScope.randomAlbumsSection(
    albums: List<Album>,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit
) {
    item { Text("Random Albums", style = MaterialTheme.typography.titleLarge) }
    item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(albums, key = { it.id }, contentType = { "album" }) { album ->
                AlbumTile(
                    album = album,
                    onAlbumClick = { onAlbumClick(album.id) },
                    onArtistClick = { onArtistClick(album.artistId) }
                )
            }
        }
    }
}

private fun LazyListScope.section(
    title: String,
    albums: List<Album>,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit
) {
    item { Text(title, style = MaterialTheme.typography.titleLarge) }
    item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(albums, key = { it.id }, contentType = { "album" }) { album ->
                AlbumTile(
                    album = album,
                    onAlbumClick = { onAlbumClick(album.id) },
                    onArtistClick = { onArtistClick(album.artistId) }
                )
            }
        }
    }
}

private fun LazyListScope.artistsSection(
    artists: List<Album>,
    onArtistClick: (String) -> Unit
) {
    item { Text("Most Played Artists", style = MaterialTheme.typography.titleLarge) }
    item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(artists, key = { it.id }, contentType = { "artist" }) { artist ->
                ArtistTile(
                    artist = artist,
                    onClick = { onArtistClick(artist.artistId) }
                )
            }
        }
    }
}

// ==================== NETWORK AVAILABILITY ====================

@Composable
private fun rememberNetworkAvailability(): State<Boolean> {
    val context = LocalContext.current
    val isAvailable = remember { mutableStateOf(context.isNetworkAvailable()) }

    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isAvailable.value = context.isNetworkAvailable()
            }

            override fun onLost(network: Network) {
                isAvailable.value = context.isNetworkAvailable()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                isAvailable.value = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        onDispose {
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        }
    }

    return isAvailable
}

private fun Context.isNetworkAvailable(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

// ==================== OPTIMIZED ALBUM TILE ====================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumTile(
    album: Album,
    onAlbumClick: () -> Unit,
    onArtistClick: () -> Unit
) {
    var imageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

    val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.7f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Column(modifier = Modifier.width(130.dp)) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(RoundedCornerShape(3.dp))
                .clickable { onAlbumClick() }
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(album.coverArtUrl)
                    .crossfade(300)
                    .size(200)
                    .build(),
                contentDescription = album.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onState = { imageState = it }
            )

            if (imageState is AsyncImagePainter.State.Loading ||
                imageState is AsyncImagePainter.State.Empty) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.Gray.copy(alpha = pulseAlpha))
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onArtistClick() }
        ) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, delayMillis = 2000)
            )

            Text(
                text = album.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, delayMillis = 2000)
            )
        }
    }
}

// ==================== OPTIMIZED ARTIST TILE ====================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistTile(
    artist: Album,
    onClick: () -> Unit
) {
    var imageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

    val pulseAlpha by rememberInfiniteTransition(label = "artist_pulse").animateFloat(
        initialValue = 0.7f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(110.dp)
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .clickable { onClick() }
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(artist.coverArtUrl)
                    .crossfade(300)
                    .size(200)
                    .build(),
                contentDescription = artist.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onState = { imageState = it }
            )

            if (imageState is AsyncImagePainter.State.Loading ||
                imageState is AsyncImagePainter.State.Empty) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color.Gray.copy(alpha = pulseAlpha))
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, delayMillis = 2000)
        )
    }
}
