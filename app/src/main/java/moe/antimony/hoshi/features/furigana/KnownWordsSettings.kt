package moe.antimony.hoshi.features.furigana

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class KnownWordsSettings(
    /** Add the words of mature Anki cards to the known words. */
    val ankiSyncEnabled: Boolean = true,
    /** When the last Anki sync finished; null before the first one. */
    val lastAnkiSyncMillis: Long? = null,
)

private val Context.knownWordsSettingsDataStore by preferencesDataStore(name = "known-words-settings")

fun Context.knownWordsSettingsRepository(): KnownWordsSettingsRepository =
    KnownWordsSettingsRepository(knownWordsSettingsDataStore)

class KnownWordsSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<KnownWordsSettings> = dataStore.data.map { preferences ->
        KnownWordsSettings(
            ankiSyncEnabled = preferences[KEY_ANKI_SYNC_ENABLED] ?: true,
            lastAnkiSyncMillis = preferences[KEY_LAST_ANKI_SYNC],
        )
    }

    suspend fun updateAnkiSyncEnabled(value: Boolean) {
        dataStore.edit { it[KEY_ANKI_SYNC_ENABLED] = value }
    }

    suspend fun updateLastAnkiSync(millis: Long) {
        dataStore.edit { it[KEY_LAST_ANKI_SYNC] = millis }
    }

    private companion object {
        val KEY_ANKI_SYNC_ENABLED = booleanPreferencesKey("anki_sync_enabled")
        val KEY_LAST_ANKI_SYNC = longPreferencesKey("last_anki_sync")
    }
}
