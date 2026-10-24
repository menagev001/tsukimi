package moe.antimony.hoshi.features.watch

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class JellyfinSettings(
    val serverUrl: String = DEFAULT_SERVER_URL,
    val userId: String = "",
    val userName: String = "",
    val accessToken: String = "",
) {
    val isLoggedIn: Boolean
        get() = userId.isNotBlank() && accessToken.isNotBlank()

    companion object {
        const val DEFAULT_SERVER_URL = "https://jellyfin.feathercomet.com"
    }
}

private val Context.jellyfinSettingsDataStore by preferencesDataStore(name = "jellyfin-settings")

fun Context.jellyfinSettingsRepository(): JellyfinSettingsRepository =
    JellyfinSettingsRepository(jellyfinSettingsDataStore)

class JellyfinSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<JellyfinSettings> = dataStore.data.map { preferences ->
        JellyfinSettings(
            serverUrl = preferences[KEY_SERVER_URL]?.takeIf(String::isNotBlank)
                ?: JellyfinSettings.DEFAULT_SERVER_URL,
            userId = preferences[KEY_USER_ID] ?: "",
            userName = preferences[KEY_USER_NAME] ?: "",
            accessToken = preferences[KEY_ACCESS_TOKEN] ?: "",
        )
    }

    suspend fun saveSession(
        serverUrl: String,
        userId: String,
        userName: String,
        accessToken: String,
    ) {
        dataStore.edit {
            it[KEY_SERVER_URL] = serverUrl
            it[KEY_USER_ID] = userId
            it[KEY_USER_NAME] = userName
            it[KEY_ACCESS_TOKEN] = accessToken
        }
    }

    suspend fun clearSession() {
        dataStore.edit {
            it.remove(KEY_USER_ID)
            it.remove(KEY_USER_NAME)
            it.remove(KEY_ACCESS_TOKEN)
        }
    }

    private companion object {
        val KEY_SERVER_URL = stringPreferencesKey("serverUrl")
        val KEY_USER_ID = stringPreferencesKey("userId")
        val KEY_USER_NAME = stringPreferencesKey("userName")
        val KEY_ACCESS_TOKEN = stringPreferencesKey("accessToken")
    }
}
