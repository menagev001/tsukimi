package moe.antimony.hoshi.features.anki

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.antimony.hoshi.di.ApplicationScope
import moe.antimony.hoshi.features.ai.AiRepository

sealed interface AnkiAiFillStatus {
    data object Idle : AnkiAiFillStatus

    data class Running(val done: Int, val total: Int) : AnkiAiFillStatus

    /** [failed] cards keep the tag, e.g. because the AI request failed again. */
    data class Done(val filled: Int, val failed: Int) : AnkiAiFillStatus

    data class Failed(val failure: AnkiFetchFailure) : AnkiAiFillStatus
}

/**
 * Fills the empty AI fields of cards tagged [AnkiAiMissingTag] and removes the tag once every
 * AI field the card's format maps is filled. Runs in the application scope, so leaving the
 * settings screen does not stop it.
 */
@Singleton
internal class AnkiAiFieldFiller @Inject constructor(
    private val repository: AnkiRepository,
    private val aiRepository: AiRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val _status = MutableStateFlow<AnkiAiFillStatus>(AnkiAiFillStatus.Idle)
    val status: StateFlow<AnkiAiFillStatus> = _status.asStateFlow()
    private var job: Job? = null

    /** Cards that still carry the tag; null when Anki cannot be read. */
    suspend fun missingCount(): Int? =
        runCatching { repository.findFormatNotes(TagSearch).size }.getOrNull()

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { fill() }
    }

    private suspend fun fill() {
        val notes = try {
            repository.findFormatNotes(TagSearch)
        } catch (error: AnkiFetchException) {
            _status.value = AnkiAiFillStatus.Failed(error.failure)
            return
        }
        var filled = 0
        notes.forEachIndexed { index, note ->
            _status.value = AnkiAiFillStatus.Running(index, notes.size)
            val ok = try {
                fillNote(note)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(Tag, "Filling AI fields failed for note ${note.note.id}", error)
                false
            }
            if (ok) filled++
        }
        _status.value = AnkiAiFillStatus.Done(filled, notes.size - filled)
    }

    /** Asks for the empty AI fields and writes the note back; false when anything failed. */
    internal suspend fun fillNote(formatNote: AnkiFormatNote): Boolean {
        val fields = formatNote.note.fields.toMutableList()
        val word = formatNote.fieldFor("{expression}")?.let(::ankiPlainText).orEmpty()
        val sentence = formatNote.fieldFor("{sentence}")?.let(::ankiPlainText).orEmpty()
        val hasExample = !formatNote.fieldFor("{original-sentence}").isNullOrBlank()
        var ok = true
        suspend fun fillEmpty(handlebar: String, ask: suspend () -> Result<String>) {
            val index = formatNote.fieldIndex(handlebar) ?: return
            if (fields.getOrNull(index)?.isNotBlank() != false) return
            ask().onSuccess { fields[index] = ankiTextFieldHtml(it) }.onFailure { ok = false }
        }
        if (sentence.isNotEmpty()) {
            if (word.isNotEmpty()) fillEmpty("{ai-word-nuance}") { aiRepository.askWordNuance(word, sentence) }
            fillEmpty("{ai-sentence-breakdown}") { aiRepository.askSentenceBreakdown(sentence) }
            // An AI example sentence needs its own translation; a read sentence keeps its source's.
            if (hasExample) fillEmpty("{sentence-translation}") { aiRepository.askTranslation(sentence) }
        }
        if (!ok) return false
        return repository.updateNote(
            formatNote.noteType,
            formatNote.note.copy(fields = fields, tags = formatNote.note.tags - AnkiAiMissingTag),
        )
    }

    private companion object {
        const val Tag = "AnkiAiFieldFiller"
        const val TagSearch = "tag:$AnkiAiMissingTag"
    }
}

data class AnkiAiFillUiState(
    val missingCount: Int? = null,
    val status: AnkiAiFillStatus = AnkiAiFillStatus.Idle,
)

@HiltViewModel
internal class AnkiAiFillViewModel @Inject constructor(
    private val filler: AnkiAiFieldFiller,
) : ViewModel() {
    private val missingCount = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<AnkiAiFillUiState> = combine(missingCount, filler.status, ::AnkiAiFillUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnkiAiFillUiState())

    init {
        refreshCount()
        viewModelScope.launch {
            // Count again when a run ends, so the row shows what is still missing.
            filler.status.collect { if (it is AnkiAiFillStatus.Done) refreshCount() }
        }
    }

    fun fill() = filler.start()

    fun refreshCount() {
        viewModelScope.launch { missingCount.value = filler.missingCount() }
    }
}
