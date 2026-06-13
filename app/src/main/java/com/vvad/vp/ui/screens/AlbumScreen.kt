package com.vvad.vp.ui.screens

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.app.NotificationManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vvad.vp.data.AlbumOfflineAvailability
import com.vvad.vp.data.DownloadAction
import com.vvad.vp.data.DownloadJobState
import com.vvad.vp.data.DownloadManager
import com.vvad.vp.data.DownloadService
import com.vvad.vp.data.NavidromeManager
import com.vvad.vp.data.OfflineLibraryManager
import com.vvad.vp.data.PlaybackManager
import com.vvad.vp.ui.models.AlbumDetails
import com.vvad.vp.ui.models.Track
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
fun AlbumScreen(
    albumId: String?,
    navidromeManager: NavidromeManager,
    offlineLibraryManager: OfflineLibraryManager,
    playbackManager: PlaybackManager?,
    topContentPadding: Dp = 0.dp,
    bottomContentPadding: Dp = 0.dp,
    currentTrackId: String?,
    onBack: () -> Unit,
    onArtistClick: (String) -> Unit,
    onCurrentTrackClick: () -> Unit,
    onTrackClick: (List<Track>, Int, String, String, String) -> Unit,
    onTrackLongClick: (Track, String, String, String) -> Unit
) {
    var album by remember { mutableStateOf<AlbumDetails?>(null) }
    var isDownloadedOffline by remember(albumId) { mutableStateOf(false) }
    var offlineAvailability by remember(albumId) { mutableStateOf<AlbumOfflineAvailability?>(null) }
    var cachedTrackIds by remember(albumId) { mutableStateOf<Set<String>>(emptySet()) }
    var isOfflineMode by remember(albumId) { mutableStateOf(false) }
    var downloadMessage by remember(albumId) { mutableStateOf<String?>(null) }
    var showCacheMenu by remember(albumId) { mutableStateOf(false) }
    val flashCounts = remember(albumId) { mutableStateMapOf<String, Int>() }
    val scope = rememberCoroutineScope()
    val isNetworkAvailable by rememberNetworkAvailability()
    val context = LocalContext.current
    val queueState by DownloadManager.queueState.collectAsState()
    val isDownloading = queueState.activeJob?.albumId == albumId || queueState.queue.any { it.albumId == albumId }

    LaunchedEffect(albumId) {
        DownloadManager.queueState.collect { state ->
            val job = state.activeJob?.takeIf { it.albumId == albumId } ?: return@collect
            when (val s = job.state) {
                is DownloadJobState.Completed -> {
                    if (albumId != null) {
                        isDownloadedOffline = offlineLibraryManager.isAlbumDownloaded(albumId)
                        offlineAvailability = offlineLibraryManager.getAlbumAvailability(albumId)
                        cachedTrackIds = offlineLibraryManager.getCachedTrackIds(albumId)
                        downloadMessage = "Download complete"
                    }
                }
                is DownloadJobState.Failed -> downloadMessage = s.error
                is DownloadJobState.Cancelled -> downloadMessage = "Download cancelled"
                else -> {}
            }
        }
    }

    LaunchedEffect(albumId, isNetworkAvailable) {
        if (albumId != null) {
            val cachedAlbum = offlineLibraryManager.getCachedAlbum(albumId)
            if (cachedAlbum != null) {
                album = cachedAlbum
                isDownloadedOffline = offlineLibraryManager.isAlbumDownloaded(albumId)
                offlineAvailability = offlineLibraryManager.getAlbumAvailability(albumId)
                cachedTrackIds = offlineLibraryManager.getCachedTrackIds(albumId)
                isOfflineMode = !isNetworkAvailable
            }

            if (isNetworkAvailable) {
                val result = navidromeManager.getAlbum(albumId)
                result.album?.let { freshAlbum ->
                    album = freshAlbum
                    isDownloadedOffline = offlineLibraryManager.isAlbumDownloaded(albumId)
                    offlineAvailability = offlineLibraryManager.getAlbumAvailability(albumId)
                    cachedTrackIds = offlineLibraryManager.getCachedTrackIds(albumId)
                    isOfflineMode = result.fromOfflineCache
                }
            }
        }
    }

    album?.let { details ->
        val trackIndexById = remember(details.tracks) {
            details.tracks.mapIndexed { index, track -> track.id to index }.toMap()
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = details.coverArtUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(20.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(0.4f), Color.Black.copy(0.9f))
                        )
                    )
            )

            @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
            Scaffold(
                containerColor = Color.Transparent
            ) { padding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = topContentPadding + padding.calculateTopPadding(),
                        bottom = bottomContentPadding + 0.dp,
                        start = 0.dp,
                        end = 0.dp
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            downloadMessage?.let { message ->
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                            } ?: offlineAvailability?.takeIf { it.cachedTrackCount > 0 }?.let { availability ->
                                Text(
                                    text = if (availability.downloadedForOffline) {
                                        "All ${availability.totalTrackCount} tracks available offline"
                                    } else {
                                        "${availability.cachedTrackCount}/${availability.totalTrackCount} cached"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            val isFullyCached = cachedTrackIds.size >= details.tracks.size && details.tracks.isNotEmpty()

                            Box {
                                Surface(
                                    modifier = Modifier
                                        .sizeIn(minWidth = 36.dp, minHeight = 36.dp)
                                        .combinedClickable(
                                            onClick = {
                                                when {
                                                    isDownloading -> {}
                                                    isFullyCached -> showCacheMenu = true
                                                    else -> {
                                                        DownloadManager.enqueue(details.id, details.name, DownloadAction.DOWNLOAD)
                                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                            val nm = context.getSystemService(NotificationManager::class.java)
                                                            if (nm.areNotificationsEnabled()) {
                                                                context.startForegroundService(Intent(context, DownloadService::class.java))
                                                            } else {
                                                                downloadMessage = "Enable notifications to see download progress"
                                                            }
                                                        } else {
                                                            context.startForegroundService(Intent(context, DownloadService::class.java))
                                                        }
                                                    }
                                                }
                                            },
                                            onLongClick = { showCacheMenu = true }
                                        ),
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    tonalElevation = 0.dp
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                                        when {
                                            isDownloading -> CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp
                                            )
                                            isFullyCached -> Icon(
                                                imageVector = Icons.Default.DownloadDone,
                                                contentDescription = "Available Offline",
                                                modifier = Modifier.size(18.dp)
                                            )
                                            else -> Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = "Download Offline",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                DropdownMenu(
                                    expanded = showCacheMenu,
                                    onDismissRequest = { showCacheMenu = false }
                                ) {
                                DropdownMenuItem(
                                    text = { Text("Recache") },
                                    onClick = {
                                        showCacheMenu = false
                                        DownloadManager.enqueue(details.id, details.name, DownloadAction.RECACHE)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            val nm = context.getSystemService(NotificationManager::class.java)
                                            if (nm.areNotificationsEnabled()) {
                                                context.startForegroundService(Intent(context, DownloadService::class.java))
                                            } else {
                                                downloadMessage = "Enable notifications to see download progress"
                                            }
                                        } else {
                                            context.startForegroundService(Intent(context, DownloadService::class.java))
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        showCacheMenu = false
                                        scope.launch {
                                            offlineLibraryManager.removeFromCache(details.id)
                                            isDownloadedOffline = false
                                            offlineAvailability = null
                                            cachedTrackIds = emptySet()
                                            if (isNetworkAvailable) {
                                                val result = navidromeManager.getAlbum(details.id)
                                                result.album?.let { freshAlbum ->
                                                    album = freshAlbum
                                                    isOfflineMode = result.fromOfflineCache
                                                }
                                            }
                                            downloadMessage = "Removed from cache"
                                        }
                                    }
                                )
                            }
                            }
                        }
                    }

                    item {
                        AsyncImage(
                            model = details.coverArtUrl,
                            contentDescription = details.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .padding(24.dp)
                        )

                        Text(
                            text = details.name,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )

                        FlowRow(
                            modifier = Modifier
                                .padding(vertical = 6.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            details.artists.forEachIndexed { index, artist ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = artist.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White.copy(0.8f),
                                        modifier = Modifier.clickable(enabled = artist.id.isNotBlank()) {
                                            onArtistClick(artist.id)
                                        }
                                    )
                                    if (index < details.artists.size - 1) {
                                        Text(
                                            text = " \u2022 ",
                                            color = Color.White.copy(0.5f),
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                }
                            }
                        }

                        details.year?.takeIf { it != 0 }?.let { year ->
                            Text(
                                text = "$year",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(0.5f)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    val grouped = details.tracks.groupBy { it.discNumber }
                    grouped.forEach { (disc, tracks) ->
                        if (grouped.size > 1) {
                            item {
                                Text(
                                    text = "Disc $disc",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp, 8.dp),
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
                                isCurrentlyPlaying = track.id == currentTrackId,
                                flashTrigger = flashCounts[track.id] ?: 0,
                                playbackManager = playbackManager,
                                modifier = if (isTrackEnabled) {
                                    Modifier.combinedClickable(
                                        onClick = {
                                            if (track.id == currentTrackId) {
                                                onCurrentTrackClick()
                                            } else {
                                                onTrackClick(
                                                    details.tracks,
                                                    trackIndexById[track.id] ?: 0,
                                                    details.id,
                                                    details.name,
                                                    details.coverArtUrl
                                                )
                                            }
                                        },
                                        onLongClick = {
                                            flashCounts[track.id] = (flashCounts[track.id] ?: 0) + 1
                                            onTrackLongClick(
                                                track,
                                                details.id,
                                                details.name,
                                                details.coverArtUrl
                                            )
                                        }
                                    )
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
@OptIn(ExperimentalLayoutApi::class)
fun TrackItem(
    track: Track,
    enabled: Boolean,
    isCurrentlyPlaying: Boolean,
    flashTrigger: Int = 0,
    modifier: Modifier = Modifier,
    playbackManager: PlaybackManager? = null
) {
    val flashAlpha = remember(track.id) { Animatable(0f) }
    val titleColor = if (enabled) Color.White else Color.White.copy(alpha = 0.35f)
    val secondaryColor = if (enabled) Color.White.copy(0.7f) else Color.White.copy(alpha = 0.3f)
    val trailingColor = if (enabled) Color.White.copy(0.5f) else Color.White.copy(alpha = 0.25f)
    val baseHighlightAlpha = if (isCurrentlyPlaying) 0.1f else 0f
    val containerColor = Color.White.copy(
        alpha = (baseHighlightAlpha + flashAlpha.value).coerceAtMost(0.38f)
    )

    LaunchedEffect(track.id, flashTrigger) {
        if (flashTrigger <= 0) return@LaunchedEffect
        flashAlpha.snapTo(0f)
        flashAlpha.animateTo(
            targetValue = 0.28f,
            animationSpec = tween(durationMillis = 100)
        )
        flashAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 280)
        )
    }

    ListItem(
        modifier = modifier,
        colors = ListItemDefaults.colors(containerColor = containerColor),
        headlineContent = { Text(track.title, color = titleColor) },
        supportingContent = {
            FlowRow(modifier = Modifier.fillMaxWidth()) {
                track.artists.forEachIndexed { index, artist ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = artist.name,
                            color = secondaryColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (index < track.artists.size - 1) {
                            Text(
                                text = ", ",
                                color = secondaryColor,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (playbackManager != null) {
                    val isFav = playbackManager.isTrackFavorite(track.id)
                    IconButton(
                        onClick = { playbackManager.toggleFavorite(track) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isFav) "Unfavorite" else "Favorite",
                            tint = if (isFav) Color(0xFFE53935) else trailingColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                val mins = track.duration / 60
                val secs = track.duration % 60
                Text("%d:%02d".format(mins, secs), color = trailingColor)
            }
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
