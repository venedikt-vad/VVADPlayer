package com.vvad.vp.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "navidrome_settings")

class CredentialsManager(private val context: Context) {
    companion object {
        val SERVER = stringPreferencesKey("server")
        val USER = stringPreferencesKey("user")
        val PASS = stringPreferencesKey("pass")
        val USE_HTTPS = booleanPreferencesKey("use_https")
    }

    suspend fun save(server: String, user: String, pass: String, https: Boolean) {
        context.dataStore.edit {
            it[SERVER] = server
            it[USER] = user
            it[PASS] = pass
            it[USE_HTTPS] = https
        }
    }

    val server: Flow<String> = context.dataStore.data.map { it[SERVER] ?: "" }
    val user: Flow<String> = context.dataStore.data.map { it[USER] ?: "" }
    val pass: Flow<String> = context.dataStore.data.map { it[PASS] ?: "" }
}