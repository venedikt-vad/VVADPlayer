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

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val credentialsManager = CredentialsManager(this)

        setContent {
            VVADPlayerTheme {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            Text("VVAD Player Menu", modifier = Modifier.padding(16.dp))
                            HorizontalDivider()
                            NavigationDrawerItem(label = { Text("Home") }, selected = false, onClick = { navController.navigate("home"); scope.launch { drawerState.close() } }, icon = { Icon(Icons.Default.Home, null) })
                            NavigationDrawerItem(label = { Text("Settings") }, selected = false, onClick = { navController.navigate("settings"); scope.launch { drawerState.close() } }, icon = { Icon(Icons.Default.Settings, null) })
                        }
                    }
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(title = { Text("VVAD Player") }, navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, null) }
                            })
                        },
                        bottomBar = { PlayerStripe() }
                    ) { padding ->
                        NavHost(navController, "home", modifier = Modifier.padding(padding)) {
                            composable("home") { /* HomeScreen() */ }
                            composable("settings") { SettingsScreen(credentialsManager) }
                        }
                    }
                }
            }
        }
    }
}