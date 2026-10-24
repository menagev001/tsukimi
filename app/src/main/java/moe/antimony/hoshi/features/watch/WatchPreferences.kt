package moe.antimony.hoshi.features.watch

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import moe.antimony.hoshi.R

enum class WatchStreamingQuality(
    @param:StringRes val labelRes: Int,
    val maxBitrate: Int,
) {
    Original(R.string.watch_quality_original, OriginalQualityBitrate),
    Mbps8(R.string.watch_quality_8_mbps, 8_000_000),
    Mbps4(R.string.watch_quality_4_mbps, 4_000_000),
    Mbps2(R.string.watch_quality_2_mbps, 2_000_000),
}

/** Audio language picked when a video starts; codes are ISO 639-2 (Jellyfin) and ISO 639-1 (Media3). */
enum class WatchAudioLanguage(
    @param:StringRes val labelRes: Int,
    val jellyfinCode: String?,
    val playerCode: String?,
) {
    Japanese(R.string.profile_language_japanese, "jpn", "ja"),
    English(R.string.profile_language_english, "eng", "en"),
    ServerDefault(R.string.watch_audio_language_server_default, null, null),
}

data class WatchPreferences(
    /** Used on metered connections (mobile data, phone hotspots), i.e. away from home. */
    val meteredQuality: WatchStreamingQuality = WatchStreamingQuality.Original,
    val audioLanguage: WatchAudioLanguage = WatchAudioLanguage.Japanese,
)

internal const val OriginalQualityBitrate = 120_000_000

private val Context.watchPreferencesDataStore by preferencesDataStore(name = "watch-preferences")

fun Context.watchPreferencesRepository(): WatchPreferencesRepository =
    WatchPreferencesRepository(watchPreferencesDataStore)

class WatchPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) {
    val preferences: Flow<WatchPreferences> = dataStore.data.map { preferences ->
        WatchPreferences(
            meteredQuality = enumOrDefault(preferences[KEY_METERED_QUALITY], WatchStreamingQuality.Original),
            audioLanguage = enumOrDefault(preferences[KEY_AUDIO_LANGUAGE], WatchAudioLanguage.Japanese),
        )
    }

    suspend fun updateMeteredQuality(value: WatchStreamingQuality) {
        dataStore.edit { it[KEY_METERED_QUALITY] = value.name }
    }

    suspend fun updateAudioLanguage(value: WatchAudioLanguage) {
        dataStore.edit { it[KEY_AUDIO_LANGUAGE] = value.name }
    }

    private companion object {
        val KEY_METERED_QUALITY = stringPreferencesKey("meteredQuality")
        val KEY_AUDIO_LANGUAGE = stringPreferencesKey("audioLanguage")
    }
}

private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
