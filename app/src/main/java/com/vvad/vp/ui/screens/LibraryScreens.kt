package com.vvad.vp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.content.Context
import coil.compose.AsyncImage
import com.vvad.vp.data.NavidromeManager
import com.vvad.vp.ui.models.Album

private enum class AlbumSortOption(val label: String) {
    NAME("Name"),
    RECENTLY_PLAYED("Recently played"),
    MOST_PLAYED("Most played"),
    RELEASE_YEAR("Release year"),
    RECENTLY_ADDED("Recently added"),
    SONG_COUNT("Song count"),
    DURATION("Duration")
}

private enum class ArtistSortOption(val label: String) {
    MOST_PLAYED("Most played"),
    SONG_COUNT("Song count"),
    ALBUMS_COUNT("Albums count"),
    NAME("Name")
}

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
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            albums = emptyList()
            artists = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        delay(300)
        albums = navidromeManager.searchAlbums(trimmed)
        artists = navidromeManager.searchArtists(trimmed)
        isLoading = false
    }

    val trimmedQuery = query.trim()
    val filteredAlbums = remember(trimmedQuery, albums) {
        if (trimmedQuery.isBlank()) emptyList() else albums
    }
    val filteredArtists = remember(trimmedQuery, artists) {
        if (trimmedQuery.isBlank()) {
            emptyList()
        } else {
            val nonComma = artists.filter { !it.name.contains(',') }
            val comma = artists.filter { it.name.contains(',') }
            val topPlayed = nonComma.sortedByDescending { it.playCount ?: 0 }.take(10)
            val restNonComma = nonComma.filter { it !in topPlayed }
            topPlayed + restNonComma.sortedBy { it.name } + comma.sortedBy { it.name }
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
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                items(filteredArtists, key = { "artist-${it.id}" }) { artist ->
                                    ArtistTile(
                                        artist = artist,
                                        onClick = { onArtistClick(artist.artistId) }
                                    )
                                }
                            }
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
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("library_prefs", Context.MODE_PRIVATE) }

    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var sortOption by remember {
        mutableStateOf(
            try { AlbumSortOption.valueOf(prefs.getString("album_sort", "NAME") ?: "NAME") }
            catch (_: Exception) { AlbumSortOption.NAME }
        )
    }
    var ascending by remember { mutableStateOf(prefs.getBoolean("album_ascending", true)) }
    var isGrid by remember { mutableStateOf(prefs.getBoolean("album_grid", false)) }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    LaunchedEffect(sortOption, ascending) {
        isLoading = true
        albums = navidromeManager.getAlbums(
            sort = sortOption.toServerSort(),
            order = if (ascending) "ASC" else "DESC"
        )
        isLoading = false
        if (isGrid) gridState.animateScrollToItem(0)
        else listState.animateScrollToItem(0)
    }

    LaunchedEffect(sortOption, ascending, isGrid) {
        prefs.edit()
            .putString("album_sort", sortOption.name)
            .putBoolean("album_ascending", ascending)
            .putBoolean("album_grid", isGrid)
            .apply()
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
        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            albums.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No albums found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                SectionTitle("Albums")

                Spacer(modifier = Modifier.height(8.dp))

                SortBar(
                    sortLabel = sortOption.label,
                    sortOptions = AlbumSortOption.entries.map { it.label },
                    onSortSelected = { index ->
                        sortOption = AlbumSortOption.entries[index]
                    },
                    ascending = ascending,
                    onToggleAscending = { ascending = !ascending },
                    isGrid = isGrid,
                    onToggleLayout = { isGrid = !isGrid }
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (isGrid) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(160.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(albums, key = { it.id }) { album ->
                            LibraryAlbumGridTile(
                                album = album,
                                onAlbumClick = { onAlbumClick(album.id) },
                                onArtistClick = { onArtistClick(album.artistId) }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
            }
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
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("library_prefs", Context.MODE_PRIVATE) }

    var artists by remember { mutableStateOf<List<Album>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var sortOption by remember {
        mutableStateOf(
            try { ArtistSortOption.valueOf(prefs.getString("artist_sort", "NAME") ?: "NAME") }
            catch (_: Exception) { ArtistSortOption.NAME }
        )
    }
    var ascending by remember { mutableStateOf(prefs.getBoolean("artist_ascending", true)) }
    var isGrid by remember { mutableStateOf(prefs.getBoolean("artist_grid", false)) }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    LaunchedEffect(sortOption, ascending) {
        isLoading = true
        artists = navidromeManager.getArtists(
            sort = sortOption.toServerSort(),
            order = if (ascending) "ASC" else "DESC"
        )
        isLoading = false
        if (isGrid) gridState.animateScrollToItem(0)
        else listState.animateScrollToItem(0)
    }

    LaunchedEffect(sortOption, ascending, isGrid) {
        prefs.edit()
            .putString("artist_sort", sortOption.name)
            .putBoolean("artist_ascending", ascending)
            .putBoolean("artist_grid", isGrid)
            .apply()
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
        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            artists.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No artists found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                SectionTitle("Artists")

                Spacer(modifier = Modifier.height(8.dp))

                SortBar(
                    sortLabel = sortOption.label,
                    sortOptions = ArtistSortOption.entries.map { it.label },
                    onSortSelected = { index ->
                        sortOption = ArtistSortOption.entries[index]
                    },
                    ascending = ascending,
                    onToggleAscending = { ascending = !ascending },
                    isGrid = isGrid,
                    onToggleLayout = { isGrid = !isGrid }
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (isGrid) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(120.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(artists, key = { it.id }) { artist ->
                            LibraryArtistGridTile(
                                artist = artist,
                                onClick = { onArtistClick(artist.artistId) }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(artists, key = { it.id }) { artist ->
                            LibraryArtistRow(
                                artist = artist,
                                onClick = { onArtistClick(artist.artistId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== SORT FIELD MAPPINGS ====================

private fun AlbumSortOption.toServerSort(): String = when (this) {
    AlbumSortOption.NAME -> "name"
    AlbumSortOption.RECENTLY_PLAYED -> "play_date"
    AlbumSortOption.MOST_PLAYED -> "play_count"
    AlbumSortOption.RELEASE_YEAR -> "max_year"
    AlbumSortOption.RECENTLY_ADDED -> "recently_added"
    AlbumSortOption.SONG_COUNT -> "song_count"
    AlbumSortOption.DURATION -> "duration"
}

private fun ArtistSortOption.toServerSort(): String = when (this) {
    ArtistSortOption.NAME -> "name"
    ArtistSortOption.MOST_PLAYED -> "play_count"
    ArtistSortOption.SONG_COUNT -> "song_count"
    ArtistSortOption.ALBUMS_COUNT -> "album_count"
}

// ==================== SORTING HELPERS ====================

private fun sortAlbums(albums: List<Album>, option: AlbumSortOption, ascending: Boolean): List<Album> {
    val comparator: Comparator<Album> = when (option) {
        AlbumSortOption.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        AlbumSortOption.RECENTLY_PLAYED -> compareBy<Album> { it.playDate ?: "" }
        AlbumSortOption.MOST_PLAYED -> compareBy<Album> { it.playCount ?: Int.MIN_VALUE }
        AlbumSortOption.RELEASE_YEAR -> compareBy<Album> { it.year ?: Int.MIN_VALUE }
        AlbumSortOption.RECENTLY_ADDED -> compareBy<Album> { it.createdAt ?: "" }
        AlbumSortOption.SONG_COUNT -> compareBy<Album> { it.songCount ?: Int.MIN_VALUE }
        AlbumSortOption.DURATION -> compareBy<Album> { it.duration ?: Int.MIN_VALUE }
    }
    return if (ascending) albums.sortedWith(comparator) else albums.sortedWith(comparator.reversed())
}

private fun sortArtists(artists: List<Album>, option: ArtistSortOption, ascending: Boolean): List<Album> {
    val comparator: Comparator<Album> = when (option) {
        ArtistSortOption.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        ArtistSortOption.MOST_PLAYED -> compareBy<Album> { it.playCount ?: Int.MIN_VALUE }
        ArtistSortOption.SONG_COUNT -> compareBy<Album> { it.songCount ?: Int.MIN_VALUE }
        ArtistSortOption.ALBUMS_COUNT -> compareBy<Album> { it.albumCount ?: Int.MIN_VALUE }
    }
    return if (ascending) artists.sortedWith(comparator) else artists.sortedWith(comparator.reversed())
}

// ==================== SORT BAR ====================

@Composable
private fun SortBar(
    sortLabel: String,
    sortOptions: List<String>,
    onSortSelected: (Int) -> Unit,
    ascending: Boolean,
    onToggleAscending: () -> Unit,
    isGrid: Boolean,
    onToggleLayout: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            TextButton(onClick = { expanded = true }) {
                Text("Sort: $sortLabel")
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "Sort options",
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                sortOptions.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option,
                                fontWeight = if (option == sortLabel) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onSortSelected(index)
                            expanded = false
                        }
                    )
                }
            }
        }

        Row {
            IconButton(onClick = onToggleAscending) {
                Icon(
                    imageVector = if (ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = if (ascending) "Ascending" else "Descending"
                )
            }
            IconButton(onClick = onToggleLayout) {
                Icon(
                    imageVector = if (isGrid) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                    contentDescription = if (isGrid) "List view" else "Grid view"
                )
            }
        }
    }
}

// ==================== GRID TILES ====================

@Composable
private fun LibraryAlbumGridTile(
    album: Album,
    onAlbumClick: () -> Unit,
    onArtistClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        AsyncImage(
            model = album.coverArtUrl,
            contentDescription = album.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onAlbumClick() },
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = album.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = buildString {
                append(album.artist)
                album.year?.let {
                    append(" • ")
                    append(it)
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { onArtistClick() }
        )
    }
}

@Composable
private fun LibraryArtistGridTile(
    artist: Album,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = artist.coverArtUrl,
            contentDescription = artist.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape)
                .clickable { onClick() },
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
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
