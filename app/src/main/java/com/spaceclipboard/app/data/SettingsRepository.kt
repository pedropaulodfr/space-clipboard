package com.spaceclipboard.app.data

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.spaceclipboard.app.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "space_clipboard_settings",
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val SERVER_PORT = intPreferencesKey("server_port")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { prefs ->
            AppSettings(
                deviceId = prefs[Keys.DEVICE_ID].orEmpty(),
                deviceName = prefs[Keys.DEVICE_NAME] ?: defaultDeviceName(),
                serverPort = prefs[Keys.SERVER_PORT] ?: DEFAULT_PORT,
            )
        }

    suspend fun ensureInitialized() {
        context.settingsDataStore.edit { prefs ->
            if (prefs[Keys.DEVICE_ID].isNullOrBlank()) {
                prefs[Keys.DEVICE_ID] = UUID.randomUUID().toString()
            }
            if (prefs[Keys.DEVICE_NAME].isNullOrBlank()) {
                prefs[Keys.DEVICE_NAME] = defaultDeviceName()
            }
            val port = prefs[Keys.SERVER_PORT] ?: DEFAULT_PORT
            if (port !in 1024..65535) prefs[Keys.SERVER_PORT] = DEFAULT_PORT
        }
    }

    suspend fun current(): AppSettings {
        ensureInitialized()
        return settings.first()
    }

    suspend fun updateDeviceName(name: String) {
        val sanitized = name.trim().replace(Regex("\\s+"), " ").take(48)
        require(sanitized.isNotBlank()) { "O nome do dispositivo não pode ficar vazio." }
        context.settingsDataStore.edit { it[Keys.DEVICE_NAME] = sanitized }
    }

    suspend fun updateServerPort(port: Int) {
        require(port in 1024..65535) { "A porta deve estar entre 1024 e 65535." }
        context.settingsDataStore.edit { it[Keys.SERVER_PORT] = port }
    }

    private fun defaultDeviceName(): String {
        val model = Build.MODEL?.trim().orEmpty()
        if (model.isBlank() || model.equals("unknown", ignoreCase = true)) return "Dispositivo Android"
        return model.take(48)
    }

    companion object {
        const val DEFAULT_PORT = 8765
    }
}
