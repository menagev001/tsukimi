package moe.antimony.hoshi.features.ai

import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.antimony.hoshi.di.IoDispatcher

class AiRepository @Inject constructor(
    private val settingsRepository: AiSettingsRepository,
    private val client: AiClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    val settings: Flow<AiSettings> = settingsRepository.settings

    suspend fun updateBaseUrl(value: String) = settingsRepository.updateBaseUrl(value)
    suspend fun updateModel(value: String) = settingsRepository.updateModel(value)
    suspend fun updateApiKey(value: String) = settingsRepository.updateApiKey(value)

    suspend fun ask(word: String?, sentence: String): Result<String> = withContext(ioDispatcher) {
        runCatching {
            val currentSettings = settingsRepository.settings.first()
            if (currentSettings.apiKey.isBlank()) {
                throw AiRequestException("No API key configured")
            }
            client.ask(
                baseUrl = currentSettings.baseUrl,
                model = currentSettings.model,
                apiKey = currentSettings.apiKey,
                prompt = aiLookupPrompt(word, sentence),
            )
        }
    }
}

internal fun aiLookupPrompt(word: String?, sentence: String): String =
    if (word != null) {
        "You are helping a Japanese learner reading an ebook. " +
            "Explain the word \"$word\" as used in this sentence: explain its meaning, reading, and " +
            "grammatical role here specifically. Sentence: $sentence"
    } else {
        "You are helping a Japanese learner reading an ebook. " +
            "Explain and translate this sentence, breaking down any notable grammar or vocabulary: $sentence"
    }
