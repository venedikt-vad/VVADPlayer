package com.vvad.vp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vvad.vp.data.CredentialsManager
import com.vvad.vp.data.NavidromeManager
import com.vvad.vp.data.OfflineLibraryManager
import com.vvad.vp.data.PlaybackManager
import com.vvad.vp.ui.components.PlayerStripe
import com.vvad.vp.ui.screens.*
import com.vvad.vp.ui.theme.VVADPlayerTheme

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private lateinit var playbackManager: PlaybackManager

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val credentialsManager = CredentialsManager(this)
        val offlineLibraryManager = OfflineLibraryManager(this)
        val navidromeManager = NavidromeManager(credentialsManager, offlineLibraryManager)

        playbackManager = PlaybackManager(this, navidromeManager)

        setContent {
            VVADPlayerTheme {

                val navController = rememberNavController()
                var isPlayerVisible by remember { mutableStateOf(false) }

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                Box(modifier = Modifier.fillMaxSize()) {

                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("VVAD") }
                            )
                        },

                        bottomBar = {
                            Column {

                                PlayerStripe(
                                    playbackManager = playbackManager,
                                    onStripeClick = { isPlayerVisible = true }
                                )

                                NavigationBar {

                                    NavigationBarItem(
                                        selected = currentRoute == "home",
                                        onClick = {
                                            navController.navigate("home") {
                                                popUpTo(navController.graph.startDestinationId)
                                                launchSingleTop = true
                                            }
                                        },
                                        icon = {
                                            Icon(Icons.Default.Home, contentDescription = "Home")
                                        },
                                        label = {
                                            Text("Home")
                                        }
                                    )

                                    NavigationBarItem(
                                        selected = currentRoute == "settings",
                                        onClick = {
                                            navController.navigate("settings") {
                                                launchSingleTop = true
                                            }
                                        },
                                        icon = {
                                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                                        },
                                        label = {
                                            Text("Settings")
                                        }
                                    )
                                }
                            }
                        }

                    ) { padding ->

                        NavHost(
                            navController = navController,
                            startDestination = "home",
                            modifier = Modifier.padding(padding),
                            enterTransition = { EnterTransition.None },
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                            popExitTransition = { ExitTransition.None }
                        ) {

                            composable("home") {
                                HomeScreen(
                                    navidromeManager = navidromeManager,
                                    offlineLibraryManager = offlineLibraryManager,
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
                                    navidromeManager
                                )
                            }

                            composable("album/{albumId}") { backStackEntry ->

                                val albumId =
                                    backStackEntry.arguments?.getString("albumId")

                                AlbumScreen(
                                    albumId = albumId,
                                    navidromeManager = navidromeManager,
                                    offlineLibraryManager = offlineLibraryManager,
                                    onBack = { navController.popBackStack() },
                                    onArtistClick = { id ->
                                        navController.navigate("artist/$id")
                                    },
                                    onTrackClick = { track, albumId, albumName, coverUrl ->
                                        playbackManager.play(
                                            track,
                                            albumId,
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
                                    onAlbumClick = { id ->
                                        navController.navigate("album/$id")
                                    },
                                    onBackClick = {
                                        navController.popBackStack()
                                    }
                                )
                            }
                        }
                    }

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
                            playbackManager = playbackManager,
                            onAlbumClick = { albumId ->
                                isPlayerVisible = false
                                navController.navigate("album/$albumId")
                            },
                            onArtistClick = { artistId ->
                                isPlayerVisible = false
                                navController.navigate("artist/$artistId")
                            },
                            onClose = { isPlayerVisible = false }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (::playbackManager.isInitialized) {
            playbackManager.release()
        }
    }
}