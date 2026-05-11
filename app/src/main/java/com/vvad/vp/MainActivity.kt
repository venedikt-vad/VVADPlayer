package com.vvad.vp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vvad.vp.data.CredentialsManager
import com.vvad.vp.ui.components.PlayerStripe
import com.vvad.vp.ui.screens.SettingsScreen
import com.vvad.vp.ui.theme.VVADPlayerTheme
import kotlinx.coroutines.launch
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.media3.common.util.UnstableApi
import com.vvad.vp.data.NavidromeManager
import com.vvad.vp.data.OfflineLibraryManager
import com.vvad.vp.data.PlaybackManager
import com.vvad.vp.ui.screens.AlbumScreen
import com.vvad.vp.ui.screens.ArtistScreen
import com.vvad.vp.ui.screens.HomeScreen
import com.vvad.vp.ui.screens.PlayerScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                var isPlayerVisible by remember { mutableStateOf(false) }

                Box(modifier = Modifier.fillMaxSize()) {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            ModalDrawerSheet {
                                Text("VVAD Player Menu", modifier = Modifier.padding(16.dp))
                                HorizontalDivider()
                                NavigationDrawerItem(
                                    label = { Text("Home") },
                                    selected = false,
                                    onClick = { navController.navigate("home"); scope.launch { drawerState.close() } },
                                    icon = { Icon(Icons.Default.Home, null) })
                                NavigationDrawerItem(
                                    label = { Text("Settings") },
                                    selected = false,
                                    onClick = { navController.navigate("settings"); scope.launch { drawerState.close() } },
                                    icon = { Icon(Icons.Default.Settings, null) })
                            }
                        }
                    ) {
                        Scaffold(
                            topBar = {
                                TopAppBar(title = { Text("VVAD Player") }, navigationIcon = {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(
                                            Icons.Default.Menu,
                                            null
                                        )
                                    }
                                })
                            },
                            bottomBar = {
                                PlayerStripe(
                                    playbackManager = playbackManager,
                                    onStripeClick = { isPlayerVisible = true }
                                )
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
                                    val albumId = backStackEntry.arguments?.getString("albumId")
                                    AlbumScreen(
                                        albumId = albumId,
                                        navidromeManager = navidromeManager,
                                        offlineLibraryManager = offlineLibraryManager,
                                        onBack = { navController.popBackStack() },
                                        onArtistClick = { id -> navController.navigate("artist/$id") },
                                        onTrackClick = { track, albumId, albumName, coverUrl ->
                                            playbackManager.play(track, albumId, albumName, coverUrl)
                                        }
                                    )
                                }
                                composable("artist/{artistId}") { backStackEntry ->
                                    val artistId = backStackEntry.arguments?.getString("artistId")
                                    ArtistScreen(artistId)
                                }
                            }
                        }
                    }
                    // Full Screen Player Overlay
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isPlayerVisible,
                        enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }),
                        exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it })
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
