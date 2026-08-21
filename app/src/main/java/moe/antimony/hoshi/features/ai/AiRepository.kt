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
    suspend fun updateWordNuancePromptOverride(value: String) =
        settingsRepository.updateWordNuancePromptOverride(value)
    suspend fun updateSentenceBreakdownPromptOverride(value: String) =
        settingsRepository.updateSentenceBreakdownPromptOverride(value)

    suspend fun defaultWordNuancePrompt(): String = withContext(ioDispatcher) {
        loadPromptAsset(WordNuancePromptAsset)
    }

    suspend fun defaultSentenceBreakdownPrompt(): String = withContext(ioDispatcher) {
        loadPromptAsset(SentenceBreakdownPromptAsset)
    }

    suspend fun askWordNuance(word: String, sentence: String): Result<String> =
        ask(
            promptAsset = WordNuancePromptAsset,
            promptOverride = { it.wordNuancePromptOverride },
            userMessage = "Word: $word\nSentence: $sentence",
        )

    suspend fun askSentenceBreakdown(sentence: String): Result<String> =
        ask(
            promptAsset = SentenceBreakdownPromptAsset,
            promptOverride = { it.sentenceBreakdownPromptOverride },
            userMessage = sentence,
        )

    private suspend fun ask(
        promptAsset: String,
        promptOverride: (AiSettings) -> String,
        userMessage: String,
    ): Result<String> = withContext(ioDispatcher) {
        runCatching {
            val currentSettings = settingsRepository.settings.first()
            if (currentSettings.apiKey.isBlank()) {
                throw AiRequestException("No API key configured")
            }
            val systemPrompt = promptOverride(currentSettings).takeIf(String::isNotBlank)
                ?: loadPromptAsset(promptAsset)
            client.ask(
                baseUrl = currentSettings.baseUrl,
                model = currentSettings.model,
                apiKey = currentSettings.apiKey,
                systemPrompt = systemPrompt,
                userMessage = userMessage,
            )
        }
    }

    private fun loadPromptAsset(promptAsset: String): String =
        promptCache.getOrPut(promptAsset) {
            context.assets.open(promptAsset).bufferedReader().use { it.readText() }
        }

    private companion object {
        const val WordNuancePromptAsset = "ai-prompts/word_nuance.md"
        const val SentenceBreakdownPromptAsset = "ai-prompts/sentence_breakdown.md"
    }
}
