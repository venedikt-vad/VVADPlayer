package com.vvad.vp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vvad.vp.data.CredentialsManager
import com.vvad.vp.data.NavidromeManager
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Composable
fun SettingsScreen(credentialsManager: CredentialsManager,
                   navidromeManager: NavidromeManager) {
    val scope = rememberCoroutineScope()

    // Observe persisted values
    val savedServer by credentialsManager.server.collectAsState(initial = "")
    val savedUser by credentialsManager.user.collectAsState(initial = "")
    val savedPass by credentialsManager.pass.collectAsState(initial = "")
    val savedHttps by credentialsManager.useHttps.collectAsState(initial = false)
    val lastStatus by credentialsManager.lastStatus.collectAsState(initial = "Not Connected")
    val lastTime by credentialsManager.lastCheckTime.collectAsState(initial = 0L)

    val savedFormat by credentialsManager.preferredFormat.collectAsState(initial = "raw")
    val savedBitrate by credentialsManager.maxBitrate.collectAsState(initial = 0)

    val formats = listOf("raw", "mp3", "ogg", "aac")
    val bitrates = listOf(0, 128, 192, 256, 320)
    // Local UI State
    var serverAddress by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var useHttps by remember { mutableStateOf(false) }

    // Load saved settings into fields once when data is available
    LaunchedEffect(savedServer) {
        if (serverAddress.isEmpty()) serverAddress = savedServer
        if (username.isEmpty()) username = savedUser
        if (password.isEmpty()) password = savedPass
        useHttps = savedHttps
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Navidrome Settings", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(value = serverAddress, onValueChange = { serverAddress = it }, label = { Text("Server Address") }, modifier = Modifier.fillMaxWidth())

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Use HTTPS")
            Switch(checked = useHttps, onCheckedChange = { useHttps = it })
        }

        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())

        Button(
            onClick = {
                scope.launch {
                    navidromeManager.testConnection(serverAddress, username, password, useHttps)
                } },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Connect") }

        // Color-coded status
        val statusColor = if (lastStatus == "Connected") Color(0xFF4CAF50) else Color(0xFFF44336)

        Card(
            colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.1f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Status: $lastStatus", color = statusColor, style = MaterialTheme.typography.bodyLarge)
                if (lastTime > 0) {
                    Text(text = "Last checked: ${getRelativeTime(lastTime)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        Text("Transcoding Settings", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Text("Preferred Format", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
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

        Spacer(modifier = Modifier.height(16.dp))

        // Bitrate Selection
        Text("Max Bitrate (kbps)", style = MaterialTheme.typography.labelMedium)
        if (savedFormat == "raw") {
            Text(
                text = "RAW playback uses the original file and ignores bitrate limits.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
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
