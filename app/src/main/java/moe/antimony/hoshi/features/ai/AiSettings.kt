package moe.antimony.hoshi.features.ai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AiSettings(
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_MODEL,
    val apiKey: String = "",
    val wordNuancePromptOverride: String = "",
    val sentenceBreakdownPromptOverride: String = "",
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://api.anthropic.com"
        const val DEFAULT_MODEL = "claude-sonnet-5"
    }
}

private val Context.aiSettingsDataStore by preferencesDataStore(name = "ai-settings")

fun Context.aiSettingsRepository(): AiSettingsRepository =
    AiSettingsRepository(aiSettingsDataStore)

class AiSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<AiSettings> = dataStore.data.map { preferences ->
        AiSettings(
            baseUrl = preferences[KEY_BASE_URL]?.takeIf(String::isNotBlank) ?: AiSettings.DEFAULT_BASE_URL,
            model = preferences[KEY_MODEL]?.takeIf(String::isNotBlank) ?: AiSettings.DEFAULT_MODEL,
            apiKey = preferences[KEY_API_KEY] ?: "",
            wordNuancePromptOverride = preferences[KEY_WORD_NUANCE_PROMPT] ?: "",
            sentenceBreakdownPromptOverride = preferences[KEY_SENTENCE_BREAKDOWN_PROMPT] ?: "",
        )
    }

    suspend fun updateBaseUrl(value: String) {
        dataStore.edit { it[KEY_BASE_URL] = value }
    }

    suspend fun updateModel(value: String) {
        dataStore.edit { it[KEY_MODEL] = value }
    }

    suspend fun updateApiKey(value: String) {
        dataStore.edit { it[KEY_API_KEY] = value }
    }

    suspend fun updateWordNuancePromptOverride(value: String) {
        dataStore.edit { it[KEY_WORD_NUANCE_PROMPT] = value }
    }

    suspend fun updateSentenceBreakdownPromptOverride(value: String) {
        dataStore.edit { it[KEY_SENTENCE_BREAKDOWN_PROMPT] = value }
    }

    private companion object {
        val KEY_BASE_URL = stringPreferencesKey("baseUrl")
        val KEY_MODEL = stringPreferencesKey("model")
        val KEY_API_KEY = stringPreferencesKey("apiKey")
        val KEY_WORD_NUANCE_PROMPT = stringPreferencesKey("wordNuancePromptOverride")
        val KEY_SENTENCE_BREAKDOWN_PROMPT = stringPreferencesKey("sentenceBreakdownPromptOverride")
    }
}
