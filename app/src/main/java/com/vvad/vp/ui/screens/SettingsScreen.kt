package com.vvad.vp.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vvad.vp.data.AudioCache
import com.vvad.vp.data.CredentialsManager
import com.vvad.vp.data.HomeRow
import com.vvad.vp.data.NavItem
import com.vvad.vp.data.NavidromeManager
import com.vvad.vp.data.PrimaryColorOption
import com.vvad.vp.ui.components.LocalAppHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

private enum class SettingsSection(
    val title: String,
    val description: String,
    val icon: ImageVector
) {
    CONNECTION("Connection", "Server address and account", Icons.Filled.Wifi),
    PLAYBACK_QUALITY("Playback Quality", "Streaming and cover art quality", Icons.Filled.MusicNote),
    HOMEPAGE("Homepage", "Home rows, order, and mix filter", Icons.Filled.Home),
    NAVIGATION("Navigation", "Bottom navigation items and order", Icons.Filled.Menu),
    CACHE("Cache", "Offline files and local storage", Icons.Filled.Storage),
    SYSTEM("System", "Vibration feedback and app behavior", Icons.Filled.Settings)
}

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
    val haptics = LocalAppHaptics.current

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

    val savedPlaylistPrefix by credentialsManager.playlistPrefix.collectAsState(initial = "")
    var playlistPrefixInput by remember { mutableStateOf<String?>(null) }

    val formats = listOf("raw", "mp3", "ogg", "aac")
    val bitrates = listOf(128, 192, 256, 320)

    var serverAddress by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var useHttps by remember { mutableStateOf(false) }

    var isTesting by remember { mutableStateOf(false) }
    var testMessage by remember { mutableStateOf("") }

    var selectedSectionName by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedSection = selectedSectionName?.let { name ->
        SettingsSection.entries.firstOrNull { it.name == name }
    }
    val scrollState = rememberScrollState()

    BackHandler(enabled = selectedSection != null) {
        selectedSectionName = null
    }

    LaunchedEffect(selectedSectionName) {
        scrollState.scrollTo(0)
    }

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
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (selectedSection == null) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Choose a category to configure.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SettingsOverview(
                onSectionClick = { selectedSectionName = it.name }
            )
        } else {
            SettingsHeader(
                section = selectedSection,
                onBack = { selectedSectionName = null }
            )

            when (selectedSection) {
                SettingsSection.CONNECTION -> {
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
                    Text(
                        text = "Status: $lastStatus",
                        color = statusColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (lastTime > 0) {
                        Text(
                            text = "Last checked: ${getRelativeTime(lastTime)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                SettingsSection.PLAYBACK_QUALITY -> {
                    Text("Streaming", style = MaterialTheme.typography.titleMedium)
                    Text("Preferred Format", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        formats.forEach { format ->
                            OptionChip(
                                label = format.uppercase(),
                                selected = savedFormat == format,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    scope.launch {
                                        val normalizedBitrate = if (format == "raw") 0 else savedBitrate
                                        credentialsManager.saveTranscodingSettings(format, normalizedBitrate)
                                    }
                                }
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        bitrates.forEach { br ->
                            OptionChip(
                                label = br.toString(),
                                selected = savedBitrate == br,
                                enabled = savedFormat != "raw",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    scope.launch { credentialsManager.saveTranscodingSettings(savedFormat, br) }
                                }
                            )
                        }
                    }
                    OptionChip(
                        label = "Unlimited",
                        selected = savedBitrate == 0,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch { credentialsManager.saveTranscodingSettings(savedFormat, 0) }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Cover art", style = MaterialTheme.typography.titleMedium)
                    Text("Small covers (Home, grids)", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(200, 300, 400, 512).forEach { size ->
                            OptionChip(
                                label = size.toString(),
                                selected = savedSmallSize == size,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    scope.launch { credentialsManager.saveCoverSizes(size, savedLargeSize) }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Large covers (Player, Album detail)", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(600, 800, 1024, 1200).forEach { size ->
                            OptionChip(
                                label = size.toString(),
                                selected = savedLargeSize == size,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    scope.launch { credentialsManager.saveCoverSizes(savedSmallSize, size) }
                                }
                            )
                        }
                    }
                }

                SettingsSection.HOMEPAGE -> {
                    HomepageSettingsContent(
                        credentialsManager = credentialsManager,
                        playlistPrefixInput = playlistPrefixInput ?: savedPlaylistPrefix,
                        onPlaylistPrefixChange = { value ->
                            playlistPrefixInput = value
                            scope.launch { credentialsManager.savePlaylistPrefix(value) }
                        }
                    )
                }

                SettingsSection.NAVIGATION -> {
                    NavigationSettingsContent(credentialsManager)
                }

                SettingsSection.CACHE -> {
                    OutlinedButton(
                        onClick = onNavigateToCachedAlbums,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Manage Cached Albums")
                    }

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

                SettingsSection.SYSTEM -> {
                    SystemSettingsContent(
                        credentialsManager = credentialsManager,
                        onTestVibration = { haptics?.performLongPress() }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsOverview(
    onSectionClick: (SettingsSection) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsSection.entries.forEachIndexed { index, section ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSectionClick(section) }
                    .padding(vertical = 14.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = section.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(section.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = section.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (index < SettingsSection.entries.lastIndex) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun OptionChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        label = {
            Text(
                text = label,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}

@Composable
private fun SettingsHeader(
    section: SettingsSection,
    onBack: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Icon(
            imageVector = section.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(section.title, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun NavigationSettingsContent(credentialsManager: CredentialsManager) {
    val scope = rememberCoroutineScope()
    val savedOrder by credentialsManager.navItemOrder.collectAsState(initial = NavItem.entries.toList())
    val savedHiddenItems by credentialsManager.navHiddenItems.collectAsState(initial = emptySet())
    var order by remember { mutableStateOf(savedOrder) }
    var hiddenItems by remember { mutableStateOf(savedHiddenItems) }

    LaunchedEffect(savedOrder) {
        order = savedOrder
    }
    LaunchedEffect(savedHiddenItems) {
        hiddenItems = savedHiddenItems
    }

    Text("Bottom navigation", style = MaterialTheme.typography.titleMedium)
    Text(
        text = "Choose which tabs appear and in what order. Home is always shown.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    order.forEachIndexed { index, item ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = when {
                        item == NavItem.HOME -> "Always visible"
                        item in hiddenItems -> "Hidden"
                        else -> "Visible"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = {
                    val target = index - 1
                    if (target in order.indices) {
                        val updated = order.toMutableList()
                        val movedItem = updated.removeAt(index)
                        updated.add(target, movedItem)
                        order = updated
                        scope.launch { credentialsManager.saveNavItemOrder(updated) }
                    }
                },
                enabled = index > 0
            ) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
            }
            IconButton(
                onClick = {
                    val target = index + 1
                    if (target in order.indices) {
                        val updated = order.toMutableList()
                        val movedItem = updated.removeAt(index)
                        updated.add(target, movedItem)
                        order = updated
                        scope.launch { credentialsManager.saveNavItemOrder(updated) }
                    }
                },
                enabled = index < order.lastIndex
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
            }
            Switch(
                checked = item == NavItem.HOME || item !in hiddenItems,
                onCheckedChange = if (item == NavItem.HOME) {
                    null
                } else {
                    { visible ->
                        val updated = if (visible) hiddenItems - item else hiddenItems + item
                        hiddenItems = updated
                        scope.launch { credentialsManager.saveNavHiddenItems(updated) }
                    }
                },
                enabled = item != NavItem.HOME
            )
        }
        if (index < order.lastIndex) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }

    OutlinedButton(
        onClick = {
            val defaultOrder = NavItem.entries.toList()
            order = defaultOrder
            hiddenItems = emptySet()
            scope.launch {
                credentialsManager.saveNavItemOrder(defaultOrder)
                credentialsManager.saveNavHiddenItems(emptySet())
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Reset navigation")
    }
}

@Composable
private fun HomepageSettingsContent(
    credentialsManager: CredentialsManager,
    playlistPrefixInput: String,
    onPlaylistPrefixChange: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val savedOrder by credentialsManager.homeRowOrder.collectAsState(initial = HomeRow.entries.toList())
    val savedHiddenRows by credentialsManager.homeHiddenRows.collectAsState(initial = emptySet())
    var order by remember { mutableStateOf(savedOrder) }
    var hiddenRows by remember { mutableStateOf(savedHiddenRows) }

    LaunchedEffect(savedOrder) {
        order = savedOrder
    }
    LaunchedEffect(savedHiddenRows) {
        hiddenRows = savedHiddenRows
    }

    Text("Home rows", style = MaterialTheme.typography.titleMedium)
    Text(
        text = "Choose which rows appear on Home and in what order.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    order.forEachIndexed { index, row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (row in hiddenRows) "Hidden" else "Visible",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = {
                    val target = index - 1
                    if (target in order.indices) {
                        val updated = order.toMutableList()
                        val item = updated.removeAt(index)
                        updated.add(target, item)
                        order = updated
                        scope.launch { credentialsManager.saveHomeRowOrder(updated) }
                    }
                },
                enabled = index > 0
            ) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
            }
            IconButton(
                onClick = {
                    val target = index + 1
                    if (target in order.indices) {
                        val updated = order.toMutableList()
                        val item = updated.removeAt(index)
                        updated.add(target, item)
                        order = updated
                        scope.launch { credentialsManager.saveHomeRowOrder(updated) }
                    }
                },
                enabled = index < order.lastIndex
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
            }
            Switch(
                checked = row !in hiddenRows,
                onCheckedChange = { visible ->
                    val updated = if (visible) hiddenRows - row else hiddenRows + row
                    hiddenRows = updated
                    scope.launch { credentialsManager.saveHomeHiddenRows(updated) }
                }
            )
        }
        if (index < order.lastIndex) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Spacer(modifier = Modifier.height(8.dp))

    Text("Your mixes filter", style = MaterialTheme.typography.titleMedium)
    Text(
        text = "Playlists whose names start with this prefix appear in the \"Your mixes\" row on Home. Leave it empty to show all playlists.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedTextField(
        value = playlistPrefixInput,
        onValueChange = onPlaylistPrefixChange,
        label = { Text("Playlist name prefix") },
        placeholder = { Text("e.g. Mix - ") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SystemSettingsContent(
    credentialsManager: CredentialsManager,
    onTestVibration: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val vibrationEnabled by credentialsManager.vibrationEnabled.collectAsState(initial = true)
    val vibrationIntensity by credentialsManager.vibrationIntensity.collectAsState(initial = 2)
    val primaryColor by credentialsManager.primaryColor.collectAsState(initial = PrimaryColorOption.DEFAULT)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Vibration feedback", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Haptics for long presses, double taps, drag reorder, and swipe skips.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = vibrationEnabled,
            onCheckedChange = { enabled ->
                scope.launch {
                    credentialsManager.saveVibrationSettings(enabled, vibrationIntensity)
                }
            }
        )
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

    Text("Intensity", style = MaterialTheme.typography.titleMedium)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(1 to "Light", 2 to "Medium", 3 to "Strong").forEach { (level, label) ->
            FilterChip(
                selected = vibrationIntensity == level,
                onClick = {
                    scope.launch {
                        credentialsManager.saveVibrationSettings(vibrationEnabled, level)
                    }
                },
                enabled = vibrationEnabled,
                label = { Text(label) }
            )
        }
    }

    OutlinedButton(
        onClick = onTestVibration,
        enabled = vibrationEnabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Test vibration")
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

    Text("Primary color", style = MaterialTheme.typography.titleMedium)
    Text(
        text = "Accent color used throughout the app.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    PrimaryColorOption.entries.forEach { option ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    scope.launch { credentialsManager.savePrimaryColor(option) }
                }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(option.argb))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(option.label, modifier = Modifier.weight(1f))
            RadioButton(
                selected = primaryColor == option,
                onClick = {
                    scope.launch { credentialsManager.savePrimaryColor(option) }
                }
            )
        }
    }
    OutlinedButton(
        onClick = {
            scope.launch { credentialsManager.savePrimaryColor(PrimaryColorOption.DEFAULT) }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Reset to default")
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
