package com.vvad.vp.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "navidrome_settings")

enum class PrimaryColorOption(val key: String, val label: String, val argb: Long) {
    DEFAULT("default", "Default Red", 0xFF950400),
    CRIMSON("crimson", "Crimson", 0xFFD32F2F),
    ORANGE("orange", "Orange", 0xFFE65100),
    AMBER("amber", "Amber", 0xFFFF8F00),
    GREEN("green", "Green", 0xFF2E7D32),
    TEAL("teal", "Teal", 0xFF00695C),
    BLUE("blue", "Blue", 0xFF1565C0),
    INDIGO("indigo", "Indigo", 0xFF283593),
    PURPLE("purple", "Purple", 0xFF6A1B9A),
    PINK("pink", "Pink", 0xFFAD1457)
}

enum class NavItem(val key: String, val label: String) {
    HOME("home", "Home"),
    SEARCH("search", "Search"),
    ALBUMS("albums", "Albums"),
    PLAYLISTS("playlists", "Playlists"),
    ARTISTS("artists", "Artists")
}

enum class HomeRow(val key: String, val label: String) {
    AVAILABLE_OFFLINE("available_offline", "Available Offline"),
    RANDOM_ALBUMS("random_albums", "Random Albums"),
    RECENTLY_PLAYED("recently_played", "Recently Played"),
    MOST_PLAYED_ALBUMS("most_played_albums", "Most Played Albums"),
    MOST_PLAYED_ARTISTS("most_played_artists", "Most Played Artists"),
    YOUR_MIXES("your_mixes", "Your mixes")
}

class CredentialsManager(private val context: Context) {
    companion object {
        val SERVER = stringPreferencesKey("server")
        val USER = stringPreferencesKey("user")
        val PASS = stringPreferencesKey("pass")
        val USE_HTTPS = booleanPreferencesKey("use_https")
        val LAST_STATUS = stringPreferencesKey("last_status")
        val LAST_CHECK_TIME = longPreferencesKey("last_time")

        val PREFERRED_FORMAT = stringPreferencesKey("preferred_format")
        val MAX_BITRATE = intPreferencesKey("max_bitrate")

        val COVER_SIZE_SMALL = intPreferencesKey("cover_size_small")
        val COVER_SIZE_LARGE = intPreferencesKey("cover_size_large")

        val CACHE_SIZE_MB = intPreferencesKey("cache_size_mb")

        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val VIBRATION_INTENSITY = intPreferencesKey("vibration_intensity")

        val PLAYLIST_PREFIX = stringPreferencesKey("playlist_prefix")
        val HOME_ROW_ORDER = stringPreferencesKey("home_row_order")
        val HOME_HIDDEN_ROWS = stringSetPreferencesKey("home_hidden_rows")
        val PRIMARY_COLOR = stringPreferencesKey("primary_color")
        val NAV_ITEM_ORDER = stringPreferencesKey("nav_item_order")
        val NAV_HIDDEN_ITEMS = stringSetPreferencesKey("nav_hidden_items")
    }
    suspend fun getRawServer(): String = server.first()
    suspend fun getUsername(): String = user.first()
    suspend fun getPassword(): String = pass.first()
    suspend fun getUseHttps(): Boolean = useHttps.first()
    suspend fun getFullServerUrl(): String {
        val address = getRawServer()
        if (address.isBlank()) return ""
        val protocol = if (getUseHttps()) "https://" else "http://"
        // Ensure we don't double up on protocol if user entered it
        return if (address.startsWith("http")) address else "$protocol$address"
    }
    suspend fun getPreferredFormat(): String = preferredFormat.first()
    suspend fun getMaxBitrate(): Int = maxBitrate.first()
    suspend fun saveTranscodingSettings(format: String, bitrate: Int) {
        context.dataStore.edit {
            it[PREFERRED_FORMAT] = format
            it[MAX_BITRATE] = bitrate
        }
    }
    suspend fun saveCredentials(server: String, user: String, pass: String, https: Boolean) {
        context.dataStore.edit {
            it[SERVER] = server
            it[USER] = user
            it[PASS] = pass
            it[USE_HTTPS] = https
        }
    }
    suspend fun updateStatus(status: String) {
        context.dataStore.edit {
            it[LAST_STATUS] = status
            it[LAST_CHECK_TIME] = System.currentTimeMillis()
        }
    }

    // Add to companion object
    val COVER_SIZE_SMALL = intPreferencesKey("cover_size_small")
    val COVER_SIZE_LARGE = intPreferencesKey("cover_size_large")

    // Add flows
    val coverSizeSmall: Flow<Int> = context.dataStore.data.map { it[COVER_SIZE_SMALL] ?: 400 }
    val coverSizeLarge: Flow<Int> = context.dataStore.data.map { it[COVER_SIZE_LARGE] ?: 800 }
    val cacheSizeMb: Flow<Int> = context.dataStore.data.map { it[CACHE_SIZE_MB] ?: 512 }
    val isCacheUnlimited: Flow<Boolean> = context.dataStore.data.map { (it[CACHE_SIZE_MB] ?: 512) == -1 }

    // Add helper
    suspend fun getCoverSizeSmall(): Int = coverSizeSmall.first()
    suspend fun getCoverSizeLarge(): Int = coverSizeLarge.first()
    suspend fun getCacheSizeMb(): Int {
        val v = cacheSizeMb.first()
        return if (v == -1) 512 else v
    }
    suspend fun isCacheUnlimited(): Boolean = isCacheUnlimited.first()

    suspend fun saveCoverSizes(small: Int, large: Int) {
        context.dataStore.edit {
            it[COVER_SIZE_SMALL] = small.coerceIn(100, 2000)
            it[COVER_SIZE_LARGE] = large.coerceIn(300, 3000)
        }
    }

    suspend fun saveCacheSizeMb(sizeMb: Int, unlimited: Boolean) {
        context.dataStore.edit {
            it[CACHE_SIZE_MB] = if (unlimited) -1 else sizeMb.coerceIn(100, 10000)
        }
    }

    val vibrationEnabled: Flow<Boolean> = context.dataStore.data.map { it[VIBRATION_ENABLED] ?: true }
    val vibrationIntensity: Flow<Int> = context.dataStore.data.map {
        (it[VIBRATION_INTENSITY] ?: 2).coerceIn(1, 3)
    }

    suspend fun saveVibrationSettings(enabled: Boolean, intensity: Int) {
        context.dataStore.edit {
            it[VIBRATION_ENABLED] = enabled
            it[VIBRATION_INTENSITY] = intensity.coerceIn(1, 3)
        }
    }

    val server: Flow<String> = context.dataStore.data.map { it[SERVER] ?: "" }
    val user: Flow<String> = context.dataStore.data.map { it[USER] ?: "" }
    val pass: Flow<String> = context.dataStore.data.map { it[PASS] ?: "" }
    val useHttps: Flow<Boolean> = context.dataStore.data.map { it[USE_HTTPS] ?: false }
    val lastStatus: Flow<String> = context.dataStore.data.map { it[LAST_STATUS] ?: "Not Connected" }
    val lastCheckTime: Flow<Long> = context.dataStore.data.map { it[LAST_CHECK_TIME] ?: 0L }

    val preferredFormat: Flow<String> = context.dataStore.data.map { it[PREFERRED_FORMAT] ?: "raw" }
    val maxBitrate: Flow<Int> = context.dataStore.data.map { it[MAX_BITRATE] ?: 0 } // 0 = no limit

    val playlistPrefix: Flow<String> = context.dataStore.data.map { it[PLAYLIST_PREFIX] ?: "" }

    suspend fun getPlaylistPrefix(): String = playlistPrefix.first()

    suspend fun savePlaylistPrefix(prefix: String) {
        context.dataStore.edit {
            it[PLAYLIST_PREFIX] = prefix
        }
    }

    val homeRowOrder: Flow<List<HomeRow>> = context.dataStore.data.map { prefs ->
        val parsed = prefs[HOME_ROW_ORDER].orEmpty()
            .split(',')
            .mapNotNull { key -> HomeRow.entries.firstOrNull { it.key == key } }
            .distinct()
        parsed + HomeRow.entries.filter { it !in parsed }
    }

    val homeHiddenRows: Flow<Set<HomeRow>> = context.dataStore.data.map { prefs ->
        prefs[HOME_HIDDEN_ROWS].orEmpty()
            .mapNotNull { key -> HomeRow.entries.firstOrNull { it.key == key } }
            .toSet()
    }

    suspend fun saveHomeRowOrder(order: List<HomeRow>) {
        context.dataStore.edit {
            it[HOME_ROW_ORDER] = order.joinToString(",") { row -> row.key }
        }
    }

    suspend fun saveHomeHiddenRows(hidden: Set<HomeRow>) {
        context.dataStore.edit {
            it[HOME_HIDDEN_ROWS] = hidden.map { row -> row.key }.toSet()
        }
    }

    val primaryColor: Flow<PrimaryColorOption> = context.dataStore.data.map { prefs ->
        val key = prefs[PRIMARY_COLOR]
        PrimaryColorOption.entries.firstOrNull { it.key == key } ?: PrimaryColorOption.DEFAULT
    }

    suspend fun savePrimaryColor(option: PrimaryColorOption) {
        context.dataStore.edit {
            it[PRIMARY_COLOR] = option.key
        }
    }

    val navItemOrder: Flow<List<NavItem>> = context.dataStore.data.map { prefs ->
        val parsed = prefs[NAV_ITEM_ORDER].orEmpty()
            .split(',')
            .mapNotNull { key -> NavItem.entries.firstOrNull { it.key == key } }
            .distinct()
        parsed + NavItem.entries.filter { it !in parsed }
    }

    val navHiddenItems: Flow<Set<NavItem>> = context.dataStore.data.map { prefs ->
        prefs[NAV_HIDDEN_ITEMS].orEmpty()
            .mapNotNull { key -> NavItem.entries.firstOrNull { it.key == key } }
            .filterNot { it == NavItem.HOME }
            .toSet()
    }

    suspend fun saveNavItemOrder(order: List<NavItem>) {
        context.dataStore.edit {
            it[NAV_ITEM_ORDER] = order.joinToString(",") { item -> item.key }
        }
    }

    suspend fun saveNavHiddenItems(hidden: Set<NavItem>) {
        context.dataStore.edit {
            it[NAV_HIDDEN_ITEMS] = hidden
                .filterNot { item -> item == NavItem.HOME }
                .map { item -> item.key }
                .toSet()
        }
    }
}