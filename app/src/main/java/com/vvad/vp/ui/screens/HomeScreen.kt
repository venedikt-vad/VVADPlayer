package com.vvad.vp.ui.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vvad.vp.data.NavidromeManager
import com.vvad.vp.data.OfflineLibraryManager
import com.vvad.vp.ui.models.Album
import kotlinx.coroutines.delay

private enum class HomeMode {
    Online,
    Offline
}

@Composable
fun HomeScreen(
    navidromeManager: NavidromeManager,
    offlineLibraryManager: OfflineLibraryManager,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit
) {
    var randomAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var offlineAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var homeMode by remember { mutableStateOf(HomeMode.Online) }
    val isNetworkAvailable by rememberNetworkAvailability()

    LaunchedEffect(isNetworkAvailable) {
        while (true) {
            offlineAlbums = offlineLibraryManager.getOfflineAlbums()

            if (isNetworkAvailable) {
                val fetchedAlbums = navidromeManager.getRandomAlbums(limit = 10)
                if (fetchedAlbums.isNotEmpty()) {
                    randomAlbums = fetchedAlbums
                    homeMode = HomeMode.Online
                } else if (offlineAlbums.isNotEmpty()) {
                    homeMode = HomeMode.Offline
                } else {
                    randomAlbums = emptyList()
                    homeMode = HomeMode.Online
                }
            } else {
                homeMode = HomeMode.Offline
            }

            delay(if (homeMode == HomeMode.Offline) 15_000 else 60_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (homeMode == HomeMode.Offline) {
            Text(
                text = if (isNetworkAvailable) "Cached Albums" else "Offline Library",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = if (isNetworkAvailable) {
                    "Server is unreachable right now. Showing cached albums."
                } else {
                    "No network connection. Showing cached albums."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (offlineAlbums.isEmpty()) {
                Text(
                    text = "No cached albums available yet. Download an album while online to use it offline.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 130.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(offlineAlbums, key = { it.id }) { album ->
                        AlbumTile(
                            album = album,
                            onAlbumClick = { onAlbumClick(album.id) },
                            onArtistClick = {
                                if (album.artistId.isNotBlank()) {
                                    onArtistClick(album.artistId)
                                }
                            }
                        )
                    }
                }
            }
        } else {
            if (offlineAlbums.isNotEmpty()) {
                Text(
                    text = "Available Offline",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    items(offlineAlbums, key = { it.id }) { album ->
                        AlbumTile(
                            album = album,
                            onAlbumClick = { onAlbumClick(album.id) },
                            onArtistClick = {
                                if (album.artistId.isNotBlank()) {
                                    onArtistClick(album.artistId)
                                }
                            }
                        )
                    }
                }
            }

            Text(
                text = "Random Albums",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(randomAlbums, key = { it.id }) { album ->
                    AlbumTile(
                        album = album,
                        onAlbumClick = { onAlbumClick(album.id) },
                        onArtistClick = { onArtistClick(album.artistId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberNetworkAvailability(): State<Boolean> {
    val context = LocalContext.current
    val isAvailable = remember { mutableStateOf(context.isNetworkAvailable()) }

    DisposableEffect(context) {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

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
                isAvailable.value =
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
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
    val connectivityManager =
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumTile(
    album: Album,
    onAlbumClick: () -> Unit,
    onArtistClick: () -> Unit
) {
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
                .clickable { onAlbumClick() }
        )

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
                modifier = Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    delayMillis = 2000
                )
            )

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
