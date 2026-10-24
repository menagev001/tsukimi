package moe.antimony.hoshi.features.watch

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
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

enum class WatchSubtitleSize(
    @param:StringRes val labelRes: Int,
    /** CSS pixels of the Japanese line; the second line is drawn smaller. */
    val fontSizePx: Int,
) {
    Small(R.string.watch_subtitle_size_small, 24),
    Medium(R.string.watch_subtitle_size_medium, 32),
    Large(R.string.watch_subtitle_size_large, 40),
    ExtraLarge(R.string.watch_subtitle_size_extra_large, 50),
}

data class WatchPreferences(
    /** Used on metered connections (mobile data, phone hotspots), i.e. away from home. */
    val meteredQuality: WatchStreamingQuality = WatchStreamingQuality.Original,
    val audioLanguage: WatchAudioLanguage = WatchAudioLanguage.Japanese,
    val subtitleSize: WatchSubtitleSize = WatchSubtitleSize.Medium,
    /** Continue playback when the lookup popup closes; otherwise stay paused. */
    val resumeAfterLookup: Boolean = true,
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
            subtitleSize = enumOrDefault(preferences[KEY_SUBTITLE_SIZE], WatchSubtitleSize.Medium),
            resumeAfterLookup = preferences[KEY_RESUME_AFTER_LOOKUP] ?: true,
        )
    }

    /** Subtitle timing correction for one video; positive values show lines later. */
    fun subtitleOffsetMs(itemId: String): Flow<Long> =
        dataStore.data.map { it[subtitleOffsetKey(itemId)] ?: 0L }

    suspend fun updateMeteredQuality(value: WatchStreamingQuality) {
        dataStore.edit { it[KEY_METERED_QUALITY] = value.name }
    }

    suspend fun updateAudioLanguage(value: WatchAudioLanguage) {
        dataStore.edit { it[KEY_AUDIO_LANGUAGE] = value.name }
    }

    suspend fun updateSubtitleSize(value: WatchSubtitleSize) {
        dataStore.edit { it[KEY_SUBTITLE_SIZE] = value.name }
    }

    suspend fun updateResumeAfterLookup(value: Boolean) {
        dataStore.edit { it[KEY_RESUME_AFTER_LOOKUP] = value }
    }

    suspend fun updateSubtitleOffsetMs(itemId: String, offsetMs: Long) {
        dataStore.edit {
            if (offsetMs == 0L) it.remove(subtitleOffsetKey(itemId)) else it[subtitleOffsetKey(itemId)] = offsetMs
        }
    }

    private companion object {
        val KEY_METERED_QUALITY = stringPreferencesKey("meteredQuality")
        val KEY_AUDIO_LANGUAGE = stringPreferencesKey("audioLanguage")
        val KEY_SUBTITLE_SIZE = stringPreferencesKey("subtitleSize")
        val KEY_RESUME_AFTER_LOOKUP = booleanPreferencesKey("resumeAfterLookup")

        fun subtitleOffsetKey(itemId: String) = longPreferencesKey("subtitleOffset.$itemId")
    }
}

private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
