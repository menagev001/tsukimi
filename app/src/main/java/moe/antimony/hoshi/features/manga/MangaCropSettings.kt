package moe.antimony.hoshi.features.manga

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import moe.antimony.hoshi.manga.MangaPageCrop

@Serializable
enum class MangaCropMode {
    Off,
    Auto,
    Manual,
}

/**
 * One series' page-crop choice. [manual] is only meaningful in [MangaCropMode.Manual], but is kept
 * across mode switches so going back to Manual restores what the reader had set.
 */
@Serializable
data class MangaCropSetting(
    val mode: MangaCropMode = MangaCropMode.Auto,
    val manual: MangaPageCrop? = null,
)

private val Context.mangaCropSettingsDataStore by preferencesDataStore(name = "manga-crop-settings")

fun Context.mangaCropSettingsRepository(): MangaCropSettingsRepository =
    MangaCropSettingsRepository(mangaCropSettingsDataStore)

/**
 * Per-series crop settings. Scans differ between series far more than between volumes of one
 * series, so a single choice covers every volume of a series; a series never touched uses the
 * default ([MangaCropMode.Auto]).
 */
class MangaCropSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun setting(seriesKey: String): Flow<MangaCropSetting> = dataStore.data
        .map { preferences ->
            preferences[key(seriesKey)]
                ?.let { runCatching { json.decodeFromString(MangaCropSetting.serializer(), it) }.getOrNull() }
                ?: MangaCropSetting()
        }
        .distinctUntilChanged()

    suspend fun update(seriesKey: String, transform: (MangaCropSetting) -> MangaCropSetting) {
        dataStore.edit { preferences ->
            val current = preferences[key(seriesKey)]
                ?.let { runCatching { json.decodeFromString(MangaCropSetting.serializer(), it) }.getOrNull() }
                ?: MangaCropSetting()
            preferences[key(seriesKey)] = json.encodeToString(MangaCropSetting.serializer(), transform(current))
        }
    }

    private fun key(seriesKey: String) = stringPreferencesKey("series:$seriesKey")
}
