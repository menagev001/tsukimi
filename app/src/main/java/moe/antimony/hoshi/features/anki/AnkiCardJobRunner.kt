package moe.antimony.hoshi.features.anki

import android.util.Log
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.antimony.hoshi.di.ApplicationScope
import moe.antimony.hoshi.features.ai.AiRepository
import moe.antimony.hoshi.features.ai.AiSentenceContext

/** One card from a popup's Anki button, built in the background. */
data class AnkiCardRequest(
    val label: String,
    val formatId: String?,
    val payloadJson: String,
    val context: AnkiMiningContext,
    /** Adds slow parts such as media to the context inside the job; cancelling the job stops it. */
    val prepareContext: (suspend (AnkiMiningContext) -> AnkiMiningContext)? = null,
    /** Runs when the job ends in any way, e.g. to delete temporary media files. */
    val cleanUp: () -> Unit = {},
    /** Lines around the sentence for the AI fields, e.g. neighbouring subtitle lines. */
    val aiContext: AiSentenceContext? = null,
    /** Replace the sentence by an AI example sentence; the original goes to `{original-sentence}`. */
    val useAiExampleSentence: Boolean = false,
)

data class AnkiCardJob(
    val id: Long,
    val label: String,
    val status: AnkiCardJobStatus,
)

sealed interface AnkiCardJobStatus {
    data object Working : AnkiCardJobStatus

    /** [duplicate]: a note for the same expression was already in the deck; it was added anyway. */
    data class Added(val duplicate: Boolean) : AnkiCardJobStatus

    data object Failed : AnkiCardJobStatus

    data object Cancelled : AnkiCardJobStatus
}

/**
 * Builds cards in the application scope, so a card finishes even when the screen that started it
 * closes. [jobs] holds the jobs that are running or finished recently, newest last.
 */
@Singleton
class AnkiCardJobRunner @Inject internal constructor(
    private val repository: AnkiRepository,
    private val aiRepository: AiRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val nextId = AtomicLong()
    private val running = mutableMapOf<Long, Job>()
    private val _jobs = MutableStateFlow<List<AnkiCardJob>>(emptyList())
    val jobs: StateFlow<List<AnkiCardJob>> = _jobs.asStateFlow()

    fun start(request: AnkiCardRequest): Long {
        val id = nextId.incrementAndGet()
        _jobs.update { (it + AnkiCardJob(id, request.label, AnkiCardJobStatus.Working)).takeLast(MaxKeptJobs) }
        running[id] = scope.launch {
            val status = try {
                val payload = runCatching { AnkiMiningPayload.fromJson(request.payloadJson) }.getOrNull()
                val expression = payload?.expression.orEmpty()
                val duplicate = expression.isNotBlank() &&
                    runCatching { repository.isDuplicate(expression, emptyList(), emptyList()) }.getOrDefault(false)
                val prepared = request.prepareContext?.invoke(request.context) ?: request.context
                val context = withAiFields(request, prepared, expression, payload?.matched.orEmpty().ifBlank { expression })
                val added = repository.mineEntry(
                    rawPayload = request.payloadJson,
                    context = context,
                    decks = emptyList(),
                    noteTypes = emptyList(),
                    formatId = request.formatId,
                    allowDuplicates = true,
                )
                if (added) AnkiCardJobStatus.Added(duplicate) else AnkiCardJobStatus.Failed
            } catch (error: CancellationException) {
                AnkiCardJobStatus.Cancelled
            } catch (error: Exception) {
                Log.w(Tag, "Card job failed: ${error.message}")
                AnkiCardJobStatus.Failed
            } finally {
                runCatching(request.cleanUp)
            }
            finish(id, status)
        }
        return id
    }

    /** Adds the AI example sentence and AI fields the card format uses; failures tag the card. */
    private suspend fun withAiFields(
        request: AnkiCardRequest,
        context: AnkiMiningContext,
        expression: String,
        word: String,
    ): AnkiMiningContext {
        var result = context
        var aiFailed = false
        if (request.useAiExampleSentence && expression.isNotBlank()) {
            aiRepository.askExampleSentence(expression)
                .onSuccess { example ->
                    // The original's translation (e.g. its English subtitle) does not fit the example.
                    val translation = aiRepository.askTranslation(example).onFailure { aiFailed = true }.getOrNull()
                    result = result.copy(
                        sentence = example,
                        sentenceOffset = null,
                        originalSentence = context.sentence,
                        sentenceTranslation = translation?.let(::ankiTextFieldHtml),
                    )
                }
                .onFailure { aiFailed = true }
        }
        // Context lines belong to the original line, not to an example sentence.
        val aiContext = request.aiContext.takeIf { result.originalSentence == null }
        val usesNuance = word.isNotBlank() && repository.formatUsesHandlebar(request.formatId, "{ai-word-nuance}")
        val usesBreakdown = repository.formatUsesHandlebar(request.formatId, "{ai-sentence-breakdown}")
        coroutineScope {
            val nuance = async { if (usesNuance) aiRepository.askWordNuance(word, result.sentence, aiContext) else null }
            val breakdown = async { if (usesBreakdown) aiRepository.askSentenceBreakdown(result.sentence, aiContext) else null }
            val nuanceResult = nuance.await()
            val breakdownResult = breakdown.await()
            if (nuanceResult?.isFailure == true || breakdownResult?.isFailure == true) aiFailed = true
            result = result.copy(
                aiWordNuance = nuanceResult?.getOrNull()?.let(::ankiTextFieldHtml),
                aiSentenceBreakdown = breakdownResult?.getOrNull()?.let(::ankiTextFieldHtml),
            )
        }
        return if (aiFailed) result.copy(extraTags = result.extraTags + AiMissingTag) else result
    }

    /** Stops a job that has not added its note yet. */
    fun cancel(id: Long) {
        running[id]?.cancel()
    }

    /** Removes finished jobs from [jobs], e.g. after their result was shown. */
    fun clearFinished(id: Long) {
        _jobs.update { jobs -> jobs.filterNot { it.id == id && it.status != AnkiCardJobStatus.Working } }
    }

    private fun finish(id: Long, status: AnkiCardJobStatus) {
        running.remove(id)
        _jobs.update { jobs -> jobs.map { if (it.id == id) it.copy(status = status) else it } }
    }

    private companion object {
        const val Tag = "AnkiCardJobRunner"
        const val MaxKeptJobs = 5
        const val AiMissingTag = "tsukimi::ai-missing"
    }
}

/** Plain AI text as Anki field HTML: markup characters escaped, line breaks kept. */
internal fun ankiTextFieldHtml(text: String): String =
    text.trim()
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\r\n", "\n")
        .replace("\n", "<br>")
