package moe.antimony.hoshi.features.anki

import android.util.Log
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.antimony.hoshi.di.ApplicationScope

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
                val expression = runCatching { AnkiMiningPayload.fromJson(request.payloadJson).expression }.getOrDefault("")
                val duplicate = expression.isNotBlank() &&
                    runCatching { repository.isDuplicate(expression, emptyList(), emptyList()) }.getOrDefault(false)
                val context = request.prepareContext?.invoke(request.context) ?: request.context
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
    }
}
