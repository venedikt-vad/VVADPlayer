package com.vvad.vp.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import android.app.NotificationManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vvad.vp.data.DownloadAction
import com.vvad.vp.data.DownloadJobState
import com.vvad.vp.data.DownloadManager
import com.vvad.vp.data.DownloadService
import com.vvad.vp.data.OfflineLibraryManager
import com.vvad.vp.ui.models.Album
import kotlinx.coroutines.launch

@Composable
fun CachedAlbumsScreen(
    offlineLibraryManager: OfflineLibraryManager,
    topContentPadding: Dp = 0.dp,
    bottomContentPadding: Dp = 0.dp,
    onBack: () -> Unit,
    onAlbumClick: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var cachedAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var availabilityMap by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var albumMenu by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val albums = offlineLibraryManager.getOfflineAlbums()
        cachedAlbums = albums
        availabilityMap = albums.associate { album ->
            val availability = offlineLibraryManager.getAlbumAvailability(album.id)
            album.id to (availability?.cachedTrackCount ?: 0)
        }
    }

    val queueState by DownloadManager.queueState.collectAsState()

    LaunchedEffect(queueState) {
        val albums = offlineLibraryManager.getOfflineAlbums()
        cachedAlbums = albums
        availabilityMap = albums.associate { album ->
            val availability = offlineLibraryManager.getAlbumAvailability(album.id)
            album.id to (availability?.cachedTrackCount ?: 0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topContentPadding)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Default.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cached Albums", style = MaterialTheme.typography.titleLarge)
            }
        }

        if (cachedAlbums.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No cached albums yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = bottomContentPadding + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(cachedAlbums, key = { it.id }) { album ->
                    val cachedCount = availabilityMap[album.id] ?: 0
                    val isDownloading = queueState.activeJob?.albumId == album.id ||
                            queueState.queue.any { it.albumId == album.id }
                    val isFullyCached = album.songCount?.let { cachedCount >= it } == true

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAlbumClick(album.id) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = album.coverArtUrl,
                                contentDescription = album.name,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Crop
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = album.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1
                                )
                                Text(
                                    text = album.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                                Text(
                                    text = if (isDownloading) "Downloading..."
                                           else "$cachedCount / ${album.songCount ?: "?"} tracks cached",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isFullyCached) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Box {
                                Surface(
                                    modifier = Modifier
                                        .sizeIn(minWidth = 36.dp, minHeight = 36.dp)
                                        .clickable {
                                            albumMenu = if (albumMenu == album.id) null else album.id
                                        },
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(8.dp)
                                    ) {
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
                                                contentDescription = "Cached",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                DropdownMenu(
                                    expanded = albumMenu == album.id,
                                    onDismissRequest = { albumMenu = null }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Recache") },
                                        onClick = {
                                            albumMenu = null
                                            DownloadManager.enqueue(album.id, album.name, DownloadAction.RECACHE)
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                val nm = context.getSystemService(NotificationManager::class.java)
                                                if (nm.areNotificationsEnabled()) {
                                                    context.startForegroundService(Intent(context, DownloadService::class.java))
                                                }
                                            } else {
                                                context.startForegroundService(Intent(context, DownloadService::class.java))
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Remove from cache") },
                                        onClick = {
                                            albumMenu = null
                                            scope.launch {
                                                offlineLibraryManager.clearAlbumAudioCache(album.id)
                                                offlineLibraryManager.removeFromCache(album.id)
                                                cachedAlbums = offlineLibraryManager.getOfflineAlbums()
                                                availabilityMap = cachedAlbums.associate { a ->
                                                    val availability = offlineLibraryManager.getAlbumAvailability(a.id)
                                                    a.id to (availability?.cachedTrackCount ?: 0)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
