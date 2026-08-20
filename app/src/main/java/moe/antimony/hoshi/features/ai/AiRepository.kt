package moe.antimony.hoshi.features.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.antimony.hoshi.di.IoDispatcher

class AiRepository @Inject constructor(
    private val settingsRepository: AiSettingsRepository,
    private val client: AiClient,
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val promptCache = ConcurrentHashMap<String, String>()

    val settings: Flow<AiSettings> = settingsRepository.settings

    suspend fun updateBaseUrl(value: String) = settingsRepository.updateBaseUrl(value)
    suspend fun updateModel(value: String) = settingsRepository.updateModel(value)
    suspend fun updateApiKey(value: String) = settingsRepository.updateApiKey(value)

    suspend fun askWordNuance(word: String, sentence: String): Result<String> =
        ask(promptAsset = WordNuancePromptAsset, userMessage = "Word: $word\nSentence: $sentence")

    suspend fun askSentenceBreakdown(sentence: String): Result<String> =
        ask(promptAsset = SentenceBreakdownPromptAsset, userMessage = sentence)

    private suspend fun ask(promptAsset: String, userMessage: String): Result<String> = withContext(ioDispatcher) {
        runCatching {
            val systemPrompt = promptCache.getOrPut(promptAsset) {
                context.assets.open(promptAsset).bufferedReader().use { it.readText() }
            }
            val currentSettings = settingsRepository.settings.first()
            if (currentSettings.apiKey.isBlank()) {
                throw AiRequestException("No API key configured")
            }
            client.ask(
                baseUrl = currentSettings.baseUrl,
                model = currentSettings.model,
                apiKey = currentSettings.apiKey,
                systemPrompt = systemPrompt,
                userMessage = userMessage,
            )
        }
    }

    private companion object {
        const val WordNuancePromptAsset = "ai-prompts/word_nuance.md"
        const val SentenceBreakdownPromptAsset = "ai-prompts/sentence_breakdown.md"
    }
}
