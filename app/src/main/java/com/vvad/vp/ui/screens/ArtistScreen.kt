package com.vvad.vp.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vvad.vp.data.NavidromeManager
import com.vvad.vp.ui.models.Album
import com.vvad.vp.ui.models.ArtistDetails

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    artistId: String,
    navidromeManager: NavidromeManager,
    topContentPadding: Dp = 0.dp,
    bottomContentPadding: Dp = 0.dp,
    onAlbumClick: (String) -> Unit,
    onBackClick: () -> Unit
) {
    var artistDetails by remember { mutableStateOf<ArtistDetails?>(null) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Albums", "Singles & EPs", "Featured In")

    LaunchedEffect(artistId) {
        artistDetails = navidromeManager.fetchArtist(artistId)
    }

    fun isSingleOrEP(album: Album): Boolean {
        return album.type != "album" ||
                album.name.contains("Single", ignoreCase = true) ||
                album.name.contains("EP", ignoreCase = true) ||
                album.name.contains(" - Single", ignoreCase = true)
    }

    val filteredAlbums = remember(selectedTabIndex, artistDetails) {
        val albums = artistDetails?.albums ?: emptyList()
        val artistName = artistDetails?.name.orEmpty()

        when (selectedTabIndex) {
            0 -> albums.filter { album ->
                !isSingleOrEP(album) &&
                        (album.artistId == artistId || album.artist.contains(artistName, ignoreCase = true))
            }

            1 -> albums.filter { album ->
                isSingleOrEP(album) &&
                        (album.artistId == artistId || album.artist.contains(artistName, ignoreCase = true))
            }

            2 -> { // === FEATURED IN ===
                albums.filter { album ->
                    // Artist is not the main album artist
                    album.artistId != artistId && !album.artist.contains(artistName, ignoreCase = true)
                }
            }

            else -> albums
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(artistDetails?.name ?: "Artist", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        artistDetails?.let { artist ->
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = artist.coverArtUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().blur(50.dp),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.6f), Color.Black)
                            )
                        )
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = topContentPadding + paddingValues.calculateTopPadding(),
                        bottom = bottomContentPadding + 16.dp,
                        start = 16.dp,
                        end = 16.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item(span = { GridItemSpan(2) }) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 24.dp)
                        ) {
                            AsyncImage(
                                model = artist.coverArtUrl,
                                contentDescription = artist.name,
                                modifier = Modifier
                                    .size(200.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = artist.name,
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White
                            )
                        }
                    }

                    item(span = { GridItemSpan(2) }) {
                        TabRow(
                            selectedTabIndex = selectedTabIndex,
                            containerColor = Color.Transparent,
                            contentColor = Color.White,
                            indicator = { tabPositions ->
                                if (selectedTabIndex < tabPositions.size) {
                                    TabRowDefaults.SecondaryIndicator(
                                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            divider = {}
                        ) {
                            tabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedTabIndex == index,
                                    onClick = { selectedTabIndex = index },
                                    text = {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                )
                            }
                        }
                    }

                    if (filteredAlbums.isEmpty()) {
                        item(span = { GridItemSpan(2) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No items found",
                                    color = Color.Gray,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else {
                        items(filteredAlbums) { album ->
                            AlbumGridItem(album = album, onClick = { onAlbumClick(album.id) })
                        }
                    }
                }
            }
        } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumGridItem(album: Album, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = album.coverArtUrl,
            contentDescription = album.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = album.name,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .basicMarquee(
                    iterations = Int.MAX_VALUE,
                    delayMillis = 2000
                )
        )

        album.year?.let { year ->
            Text(
                text = year.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}
