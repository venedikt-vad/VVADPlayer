package com.vvad.vp.ui.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vvad.vp.data.AlbumOfflineAvailability
import com.vvad.vp.data.NavidromeManager
import com.vvad.vp.data.OfflineLibraryManager
import com.vvad.vp.ui.models.AlbumDetails
import com.vvad.vp.ui.models.Track
import kotlinx.coroutines.launch

@Composable
fun AlbumScreen(
    albumId: String?,
    navidromeManager: NavidromeManager,
    offlineLibraryManager: OfflineLibraryManager,
    onBack: () -> Unit,
    onArtistClick: (String) -> Unit,
    onTrackClick: (Track, String, String, String) -> Unit
) {
    var album by remember { mutableStateOf<AlbumDetails?>(null) }
    var isDownloadedOffline by remember(albumId) { mutableStateOf(false) }
    var offlineAvailability by remember(albumId) { mutableStateOf<AlbumOfflineAvailability?>(null) }
    var cachedTrackIds by remember(albumId) { mutableStateOf<Set<String>>(emptySet()) }
    var isOfflineMode by remember(albumId) { mutableStateOf(false) }
    var isDownloading by remember(albumId) { mutableStateOf(false) }
    var downloadMessage by remember(albumId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val isNetworkAvailable by rememberNetworkAvailability()

    LaunchedEffect(albumId, isNetworkAvailable) {
        if (albumId != null) {
            val result = navidromeManager.getAlbum(albumId)
            album = result.album
            isDownloadedOffline = offlineLibraryManager.isAlbumDownloaded(albumId)
            offlineAvailability = offlineLibraryManager.getAlbumAvailability(albumId)
            cachedTrackIds = offlineLibraryManager.getCachedTrackIds(albumId)
            isOfflineMode = !isNetworkAvailable || result.fromOfflineCache
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
                    item {
                        AsyncImage(
                            model = details.coverArtUrl,
                            contentDescription = details.name,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(24.dp)
                        )

                        Text(
                            text = details.name,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )

                        Row(
                            modifier = Modifier
                                .padding(vertical = 6.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            details.artists.forEachIndexed { index, artist ->
                                Text(
                                    text = artist.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White.copy(0.8f),
                                    modifier = Modifier
                                        .clickable(enabled = artist.id.isNotBlank()) {
                                            onArtistClick(artist.id)
                                        }
                                )
                                if (index < details.artists.size - 1) {
                                    Text(
                                        text = " • ",
                                        color = Color.White.copy(0.5f),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }

                        details.year?.takeIf { it != 0 }?.let { year ->
                            Text(
                                "$year",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(0.5f)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        FilledTonalButton(
                            onClick = {
                                if (isDownloading) return@FilledTonalButton
                                scope.launch {
                                    isDownloading = true
                                    downloadMessage = null
                                    runCatching {
                                        offlineLibraryManager.downloadAlbumForOffline(details, navidromeManager)
                                    }.onSuccess { result ->
                                        isDownloadedOffline = result.downloadedTracks == result.totalTracks
                                        downloadMessage = "Saved ${result.downloadedTracks}/${result.totalTracks} tracks offline"
                                        album = result.album
                                        offlineAvailability = offlineLibraryManager.getAlbumAvailability(result.album.id)
                                        cachedTrackIds = offlineLibraryManager.getCachedTrackIds(result.album.id)
                                        isOfflineMode = false
                                    }.onFailure {
                                        downloadMessage = it.localizedMessage ?: "Offline download failed"
                                    }
                                    isDownloading = false
                                }
                            }
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (isDownloadedOffline) Icons.Default.DownloadDone else Icons.Default.Download,
                                    contentDescription = null
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isDownloadedOffline) "Available Offline" else "Download Offline")
                        }

                        downloadMessage?.let { message ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        } ?: offlineAvailability?.takeIf { it.cachedTrackCount > 0 }?.let { availability ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (availability.downloadedForOffline) {
                                    "All ${availability.totalTrackCount} tracks are available offline"
                                } else {
                                    "${availability.cachedTrackCount}/${availability.totalTrackCount} tracks are currently cached offline"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.75f)
                            )
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
                            val isTrackAvailableOffline = cachedTrackIds.contains(track.id)
                            val isTrackEnabled = !isOfflineMode || isTrackAvailableOffline
                            TrackItem(
                                track = track,
                                enabled = isTrackEnabled,
                                onArtistClick = onArtistClick,
                                modifier = if (isTrackEnabled) {
                                    Modifier.clickable {
                                        onTrackClick(track, details.id, details.name, details.coverArtUrl)
                                    }
                                } else {
                                    Modifier
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
    enabled: Boolean,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val titleColor = if (enabled) Color.White else Color.White.copy(alpha = 0.35f)
    val secondaryColor = if (enabled) Color.White.copy(0.7f) else Color.White.copy(alpha = 0.3f)
    val trailingColor = if (enabled) Color.White.copy(0.5f) else Color.White.copy(alpha = 0.25f)

    ListItem(
        modifier = modifier,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(track.title, color = titleColor) },
        supportingContent = {
            Row(modifier = Modifier.fillMaxWidth()) {
                track.artists.forEachIndexed { index, artist ->
                    Text(
                        text = artist.name,
                        color = secondaryColor,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                    )
                    if (index < track.artists.size - 1) {
                        Text(", ", color = secondaryColor, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        leadingContent = {
            Text(
                text = track.number.toString(),
                modifier = Modifier.width(24.dp),
                color = trailingColor,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        trailingContent = {
            val mins = track.duration / 60
            val secs = track.duration % 60
            Text("%d:%02d".format(mins, secs), color = trailingColor)
        }
    )
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
