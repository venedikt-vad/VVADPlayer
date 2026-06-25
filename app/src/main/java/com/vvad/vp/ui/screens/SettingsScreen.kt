package com.vvad.vp.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vvad.vp.data.AudioCache
import com.vvad.vp.data.CredentialsManager
import com.vvad.vp.data.NavidromeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    credentialsManager: CredentialsManager,
    navidromeManager: NavidromeManager,
    topContentPadding: Dp = 0.dp,
    bottomContentPadding: Dp = 0.dp,
    onNavigateToCachedAlbums: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val savedServer by credentialsManager.server.collectAsState(initial = "")
    val savedUser by credentialsManager.user.collectAsState(initial = "")
    val savedPass by credentialsManager.pass.collectAsState(initial = "")
    val savedHttps by credentialsManager.useHttps.collectAsState(initial = false)
    val lastStatus by credentialsManager.lastStatus.collectAsState(initial = "Not Connected")
    val lastTime by credentialsManager.lastCheckTime.collectAsState(initial = 0L)

    val savedFormat by credentialsManager.preferredFormat.collectAsState(initial = "raw")
    val savedBitrate by credentialsManager.maxBitrate.collectAsState(initial = 0)

    val savedSmallSize by credentialsManager.coverSizeSmall.collectAsState(initial = 400)
    val savedLargeSize by credentialsManager.coverSizeLarge.collectAsState(initial = 800)

    val formats = listOf("raw", "mp3", "ogg", "aac")
    val bitrates = listOf(0, 128, 192, 256, 320)

    var serverAddress by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var useHttps by remember { mutableStateOf(false) }

    var isTesting by remember { mutableStateOf(false) }
    var testMessage by remember { mutableStateOf("") }

    LaunchedEffect(savedServer) {
        if (serverAddress.isEmpty()) serverAddress = savedServer
        if (username.isEmpty()) username = savedUser
        if (password.isEmpty()) password = savedPass
        useHttps = savedHttps
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = 24.dp,
                top = topContentPadding + 24.dp,
                end = 24.dp,
                bottom = bottomContentPadding + 24.dp
            )
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        SettingsCategory(icon = Icons.Filled.Wifi, title = "Connection") {
            OutlinedTextField(
                value = serverAddress,
                onValueChange = { serverAddress = it },
                label = { Text("Server Address") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Use HTTPS")
                Switch(checked = useHttps, onCheckedChange = { useHttps = it })
            }

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    scope.launch {
                        isTesting = true
                        testMessage = "Testing connection..."

                        val success = navidromeManager.testConnection(
                            serverAddress.trim(),
                            username.trim(),
                            password,
                            useHttps
                        )

                        if (success) {
                            credentialsManager.saveCredentials(
                                serverAddress.trim(),
                                username.trim(),
                                password,
                                useHttps
                            )
                            testMessage = "Connected and credentials saved!"
                        } else {
                            testMessage = "Connection failed. Check Logcat."
                        }
                        isTesting = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isTesting && serverAddress.isNotBlank() && username.isNotBlank()
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connecting...")
                } else {
                    Text("Test & Save Connection")
                }
            }

            if (testMessage.isNotEmpty()) {
                Text(
                    text = testMessage,
                    color = if (testMessage.contains("✅") || testMessage.startsWith("C")) Color(0xFF4CAF50) else Color.Red,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            val statusColor = if (lastStatus == "Connected") Color(0xFF4CAF50) else Color(0xFFF44336)
            Card(
                colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Status: $lastStatus", color = statusColor, style = MaterialTheme.typography.bodyLarge)
                    if (lastTime > 0) {
                        Text("Last checked: ${getRelativeTime(lastTime)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        SettingsCategory(icon = Icons.Filled.MusicNote, title = "Transcoding") {
            Text("Preferred Format", style = MaterialTheme.typography.labelMedium)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                formats.forEach { format ->
                    FilterChip(
                        selected = savedFormat == format,
                        onClick = {
                            scope.launch {
                                val normalizedBitrate = if (format == "raw") 0 else savedBitrate
                                credentialsManager.saveTranscodingSettings(format, normalizedBitrate)
                            }
                        },
                        label = { Text(format.uppercase()) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Max Bitrate (kbps)", style = MaterialTheme.typography.labelMedium)
            if (savedFormat == "raw") {
                Text(
                    text = "RAW playback uses the original file and ignores bitrate limits.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                bitrates.forEach { br ->
                    FilterChip(
                        selected = if (savedFormat == "raw") br == 0 else savedBitrate == br,
                        onClick = {
                            if (savedFormat == "raw" && br != 0) return@FilterChip
                            scope.launch { credentialsManager.saveTranscodingSettings(savedFormat, br) }
                        },
                        label = { Text(if (br == 0) "Unlimited" else br.toString()) }
                    )
                }
            }
        }

        SettingsCategory(icon = Icons.Filled.Image, title = "Image Quality") {
            Text("Small covers (Home, grids)", style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(200, 300, 400, 512).forEach { size ->
                    FilterChip(
                        selected = savedSmallSize == size,
                        onClick = {
                            scope.launch { credentialsManager.saveCoverSizes(size, savedLargeSize) }
                        },
                        label = { Text("$size") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Large covers (Player, Album detail)", style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(600, 800, 1024, 1200).forEach { size ->
                    FilterChip(
                        selected = savedLargeSize == size,
                        onClick = {
                            scope.launch { credentialsManager.saveCoverSizes(savedSmallSize, size) }
                        },
                        label = { Text("$size") }
                    )
                }
            }
        }

        SettingsCategory(icon = Icons.Filled.Storage, title = "Cache") {
            OutlinedButton(
                onClick = onNavigateToCachedAlbums,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Manage Cached Albums")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            clearAllCaches(context)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
            ) {
                Text("Clear All Cached Tracks")
            }
        }
    }
}

@Composable
private fun SettingsCategory(
    icon: ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

private suspend fun clearAllCaches(context: Context) {
    AudioCache.resetCache()
    val appContext = context.applicationContext
    val cacheDir = File(appContext.filesDir, "audio_cache")
    val offlineCacheDir = File(appContext.filesDir, "offline_audio_cache")
    val offlineDir = File(appContext.filesDir, "offline")
    if (cacheDir.exists()) {
        cacheDir.deleteRecursively()
    }
    if (offlineCacheDir.exists()) {
        offlineCacheDir.deleteRecursively()
    }
    if (offlineDir.exists()) {
        offlineDir.deleteRecursively()
    }
}

fun getRelativeTime(time: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - time
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
        diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
        else -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
    }
}
