package moe.antimony.hoshi.features.ai

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
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
    suspend fun updateExampleSentencePromptOverride(value: String) =
        settingsRepository.updateExampleSentencePromptOverride(value)
    suspend fun updateLongSentenceLimit(value: Int) = settingsRepository.updateLongSentenceLimit(value)

    suspend fun defaultWordNuancePrompt(): String = withContext(ioDispatcher) {
        loadPromptAsset(WordNuancePromptAsset)
    }

    suspend fun defaultSentenceBreakdownPrompt(): String = withContext(ioDispatcher) {
        loadPromptAsset(SentenceBreakdownPromptAsset)
    }

    suspend fun defaultExampleSentencePrompt(): String = withContext(ioDispatcher) {
        loadPromptAsset(ExampleSentencePromptAsset)
    }

    suspend fun askWordNuance(word: String, sentence: String, context: AiSentenceContext? = null): Result<String> =
        ask(
            promptAsset = WordNuancePromptAsset,
            promptOverride = { it.wordNuancePromptOverride },
            userMessage = aiUserMessage(word = word, sentence = sentence, context = context),
        )

    suspend fun askSentenceBreakdown(sentence: String, context: AiSentenceContext? = null): Result<String> =
        ask(
            promptAsset = SentenceBreakdownPromptAsset,
            promptOverride = { it.sentenceBreakdownPromptOverride },
            userMessage = aiUserMessage(word = null, sentence = sentence, context = context),
        )

    /** One example sentence for [word]; the prompt's `[word]` is replaced by it. */
    suspend fun askExampleSentence(word: String): Result<String> =
        ask(
            promptAsset = ExampleSentencePromptAsset,
            promptOverride = { it.exampleSentencePromptOverride },
            userMessage = word,
            systemPromptTransform = { it.replace(WordPlaceholder, word) },
        ).map { it.trim() }

    private suspend fun ask(
        promptAsset: String,
        promptOverride: (AiSettings) -> String,
        userMessage: String,
        systemPromptTransform: (String) -> String = { it },
    ): Result<String> = withContext(ioDispatcher) {
        var result: Result<String>
        var attempt = 1
        while (true) {
            result = runCatching {
                val currentSettings = settingsRepository.settings.first()
                if (currentSettings.apiKey.isBlank()) {
                    throw AiRequestException("No API key configured")
                }
                val systemPrompt = systemPromptTransform(
                    promptOverride(currentSettings).takeIf(String::isNotBlank) ?: loadPromptAsset(promptAsset),
                )
                client.ask(
                    baseUrl = currentSettings.baseUrl,
                    model = currentSettings.model,
                    apiKey = currentSettings.apiKey,
                    systemPrompt = systemPrompt,
                    userMessage = userMessage,
                )
            }
            val error = result.exceptionOrNull() ?: break
            if (attempt >= MaxAttempts || !isRetryable(error)) break
            Log.w(Tag, "AI request failed on attempt $attempt/$MaxAttempts, retrying: ${error.message}")
            delay(attempt * RetryBackoffMillis)
            attempt++
        }
        result
    }

    private fun isRetryable(error: Throwable): Boolean =
        error is IOException || (error as? AiRequestException)?.retryable == true

    private fun loadPromptAsset(promptAsset: String): String =
        promptCache.getOrPut(promptAsset) {
            context.assets.open(promptAsset).bufferedReader().use { it.readText() }
        }

    private companion object {
        const val Tag = "AiRepository"
        const val MaxAttempts = 3
        const val RetryBackoffMillis = 750L
        const val WordNuancePromptAsset = "ai-prompts/word_nuance.md"
        const val SentenceBreakdownPromptAsset = "ai-prompts/sentence_breakdown.md"
        const val ExampleSentencePromptAsset = "ai-prompts/example_sentence.md"
        const val WordPlaceholder = "[word]"
    }
}
