package com.vvad.vp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import android.graphics.Color
import androidx.core.view.WindowCompat
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.res.painterResource
import coil.ImageLoader
import coil.request.CachePolicy
import com.vvad.vp.data.CredentialsManager
import com.vvad.vp.data.NavidromeManager
import com.vvad.vp.data.OfflineLibraryManager
import com.vvad.vp.data.PlaybackManager
import com.vvad.vp.data.PlaybackService
import com.vvad.vp.ui.components.PlayerStripe
import com.vvad.vp.ui.screens.*
import com.vvad.vp.ui.theme.VVADPlayerTheme

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private var playbackManager by mutableStateOf<PlaybackManager?>(null)
    private var serviceBound = false
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PlaybackService.LocalBinder
            playbackManager = binder.getService().playbackManager
            serviceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playbackManager = null
            serviceBound = false
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Force black status & navigation bars
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK

        // Make icons white
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        val credentialsManager = CredentialsManager(this)
        val offlineLibraryManager = OfflineLibraryManager(this)
        val navidromeManager = NavidromeManager(credentialsManager, offlineLibraryManager)

        val serviceIntent = Intent(this, PlaybackService::class.java)
        startForegroundService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        setContent {
            VVADPlayerTheme {

                val navController = rememberNavController()
                var isPlayerVisible by remember { mutableStateOf(false) }

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                var topBarHeightPx by remember { mutableIntStateOf(0) }
                var bottomBarHeightPx by remember { mutableIntStateOf(0) }
                val density = LocalDensity.current
                val showTopBar = currentRoute == "home"
                val topSafePadding = if (showTopBar) {
                    with(density) { topBarHeightPx.toDp() }
                } else {
                    0.dp
                }
                val bottomSafePadding = with(density) { bottomBarHeightPx.toDp() }
                val selectedNavRoute = when {
                    currentRoute == "home" -> "home"
                    currentRoute == "search" -> "search"
                    currentRoute == "albums" || currentRoute?.startsWith("album") == true -> "albums"
                    currentRoute == "artists" || currentRoute?.startsWith("artist") == true -> "artists"
                    else -> null
                }

                val navigateToTopLevel: (String) -> Unit = fun(route: String) {
                    if (currentRoute == route) {
                        return
                    }

                    if (currentRoute == "settings") {
                        navController.popBackStack()
                        if (route == "home") {
                            return
                        }
                    }

                    if (navController.popBackStack(route, inclusive = false)) {
                        Log.d("NAV", "Popped back from '$currentRoute' to existing '$route'")
                        return
                    }

                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }

                    Log.d("NAV", "Navigating from '$currentRoute' to '$route'")
                }

                Box(modifier = Modifier.fillMaxSize()) {

                    Scaffold(

                        topBar = {
                            if (showTopBar) {
                                GlassBarSurface(
                                    modifier = Modifier
                                        .zIndex(1f)
                                        .fillMaxWidth()
                                        .onSizeChanged { topBarHeightPx = it.height },
                                    tonalElevation = 2.dp,
                                    shadowElevation = 4.dp
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(36.dp)
                                            .padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.vp_applogowhite),  // ← your svg name without .svg
                                            contentDescription = "VVAD Logo",
                                            modifier = Modifier
                                                .height(28.dp)           // Adjust size as needed
                                                .aspectRatio(2.5f)       // Change based on your logo's shape
                                        )

                                        // Optional: Add spacing if needed
//                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier.weight(1f)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clickable {
                                                    navController.navigate("settings") {
                                                        launchSingleTop = true
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Settings,
                                                contentDescription = "Settings"
                                            )
                                        }
                                    }
                                }
                            }
                        },

                        bottomBar = {
                            Column {
                                GlassBarSurface(
                                    modifier = Modifier
                                        .zIndex(1f)
                                        .fillMaxWidth()
                                        .onSizeChanged { bottomBarHeightPx = it.height },
                                    tonalElevation = 16.dp,
                                    shadowElevation = 8.dp
                                ) {
                                    Column {
                                        playbackManager?.let { pm ->
                                            PlayerStripe(
                                                playbackManager = pm,
                                                onStripeClick = { isPlayerVisible = true }
                                            )
                                        }

                                        NavigationBar(
                                            containerColor = androidx.compose.ui.graphics.Color.Transparent,
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        ) {
                                            NavigationBarItem(
                                                selected = selectedNavRoute == "home",
                                                onClick = {
                                                    navigateToTopLevel("home")
                                                },
                                                icon = {
                                                    Icon(Icons.Default.Home, contentDescription = "Home")
                                                },
                                                label = {
                                                    Text("Home")
                                                }
                                            )

                                            NavigationBarItem(
                                                selected = selectedNavRoute == "search",
                                                onClick = {
                                                    navigateToTopLevel("search")
                                                },
                                                icon = {
                                                    Icon(Icons.Default.Search, contentDescription = "Search")
                                                },
                                                label = {
                                                    Text("Search")
                                                }
                                            )

                                            NavigationBarItem(
                                                selected = selectedNavRoute == "albums",
                                                onClick = {
                                                    navigateToTopLevel("albums")
                                                },
                                                icon = {
                                                    Icon(Icons.Default.Album, contentDescription = "Albums")
                                                },
                                                label = {
                                                    Text("Albums")
                                                }
                                            )

                                            NavigationBarItem(
                                                selected = selectedNavRoute == "artists",
                                                onClick = {
                                                    navigateToTopLevel("artists")
                                                },
                                                icon = {
                                                    Icon(Icons.Default.Person, contentDescription = "Artists")
                                                },
                                                label = {
                                                    Text("Artists")
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                    ) { innerPadding ->

                        NavHost(
                            navController = navController,
                            startDestination = "home",
                            modifier = Modifier
                                .fillMaxSize(),
//                                .padding(bottom = innerPadding.calculateBottomPadding()),
                            enterTransition = { EnterTransition.None },
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                            popExitTransition = { ExitTransition.None }
                        ) {

                            composable("home") {
                                HomeScreen(
                                    navidromeManager = navidromeManager,
                                    offlineLibraryManager = offlineLibraryManager,
                                    topContentPadding = topSafePadding,
                                    bottomContentPadding = bottomSafePadding,
                                    onAlbumClick = { albumId ->
                                        navController.navigate("album/$albumId")
                                    },
                                    onArtistClick = { artistId ->
                                        navController.navigate("artist/$artistId")
                                    }
                                )
                            }

                            composable("settings") {
                                SettingsScreen(
                                    credentialsManager,
                                    navidromeManager,
                                    topContentPadding = topSafePadding,
                                    bottomContentPadding = bottomSafePadding

                                    )
                            }

                            composable("search") {
                                SearchScreen(
                                    navidromeManager = navidromeManager,
                                    topContentPadding = topSafePadding,
                                    bottomContentPadding = bottomSafePadding,
                                    onAlbumClick = { albumId ->
                                        navController.navigate("album/$albumId")
                                    },
                                    onArtistClick = { artistId ->
                                        navController.navigate("artist/$artistId")
                                    }
                                )
                            }

                            composable("albums") {
                                AlbumsScreen(
                                    navidromeManager = navidromeManager,
                                    topContentPadding = topSafePadding,
                                    bottomContentPadding = bottomSafePadding,
                                    onAlbumClick = { albumId ->
                                        navController.navigate("album/$albumId")
                                    },
                                    onArtistClick = { artistId ->
                                        navController.navigate("artist/$artistId")
                                    }
                                )
                            }

                            composable("artists") {
                                ArtistsScreen(
                                    navidromeManager = navidromeManager,
                                    topContentPadding = topSafePadding,
                                    bottomContentPadding = bottomSafePadding,
                                    onArtistClick = { artistId ->
                                        navController.navigate("artist/$artistId")
                                    }
                                )
                            }

                            composable("album/{albumId}") { backStackEntry ->

                                val albumId =
                                    backStackEntry.arguments?.getString("albumId")

                                val pm = playbackManager
                                AlbumScreen(
                                    albumId = albumId,
                                    navidromeManager = navidromeManager,
                                    offlineLibraryManager = offlineLibraryManager,
                                    playbackManager = pm,
                                    topContentPadding = topSafePadding,
                                    bottomContentPadding = bottomSafePadding,
                                    currentTrackId = pm?.currentTrack?.id,
                                    onBack = { navController.popBackStack() },
                                    onArtistClick = { id ->
                                        navController.navigate("artist/$id")
                                    },
                                    onCurrentTrackClick = {
                                        pm?.togglePlayPause()
                                    },
                                    onTrackClick = { tracks, selectedIndex, selectedAlbumId, albumName, coverUrl ->
                                        pm?.replaceQueue(
                                            tracks,
                                            selectedIndex,
                                            selectedAlbumId,
                                            albumName,
                                            coverUrl
                                        )
                                    },
                                    onTrackLongClick = { track, queuedAlbumId, albumName, coverUrl ->
                                        pm?.appendToQueue(
                                            track,
                                            queuedAlbumId,
                                            albumName,
                                            coverUrl
                                        )
                                    }
                                )
                            }

                            composable("artist/{artistId}") { backStackEntry ->

                                val artistId =
                                    backStackEntry.arguments?.getString("artistId") ?: ""

                                ArtistScreen(
                                    artistId = artistId,
                                    navidromeManager = navidromeManager,
                                    topContentPadding = topSafePadding,
                                    bottomContentPadding = bottomSafePadding,
                                    onAlbumClick = { id ->
                                        navController.navigate("album/$id")
                                    },
                                    onBackClick = {
                                        navController.popBackStack()
                                    }
                                )
                            }

                            composable("queue") {
                                playbackManager?.let { pm ->
                                    QueueScreen(
                                        playbackManager = pm,
                                        onBack = { navController.popBackStack() },
                                        bottomContentPadding = bottomSafePadding
                                    )
                                }
                            }
                        }
                    }

                    playbackManager?.let { pm ->
                        AnimatedVisibility(
                            visible = isPlayerVisible,
                            enter = androidx.compose.animation.slideInVertically(
                                initialOffsetY = { it }
                            ),
                            exit = androidx.compose.animation.slideOutVertically(
                                targetOffsetY = { it }
                            )
                        ) {
                            PlayerScreen(
                                playbackManager = pm,
                                onAlbumClick = { albumId ->
                                    isPlayerVisible = false
                                    navController.navigate("album/$albumId")
                                },
                                onArtistClick = { artistId ->
                                    isPlayerVisible = false
                                    navController.navigate("artist/$artistId")
                                },
                                onClose = { isPlayerVisible = false },
                                onQueueClick = {
                                    isPlayerVisible = false
                                    navController.navigate("queue")
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }
}

@Composable
private fun GlassBarSurface(
    modifier: Modifier = Modifier,
    tonalElevation: androidx.compose.ui.unit.Dp = 0.dp,
    shadowElevation: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    Surface(
        color = androidx.compose.ui.graphics.Color.Transparent,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clipToBounds()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
        ) {
            content()
        }
    }
}
