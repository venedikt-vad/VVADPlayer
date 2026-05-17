package com.vvad.vp.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "navidrome_settings")

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

    // Add helper
    suspend fun getCoverSizeSmall(): Int = coverSizeSmall.first()
    suspend fun getCoverSizeLarge(): Int = coverSizeLarge.first()

    suspend fun saveCoverSizes(small: Int, large: Int) {
        context.dataStore.edit {
            it[COVER_SIZE_SMALL] = small.coerceIn(100, 2000)
            it[COVER_SIZE_LARGE] = large.coerceIn(300, 3000)
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
}