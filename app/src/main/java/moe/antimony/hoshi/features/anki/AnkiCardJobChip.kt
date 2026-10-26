package moe.antimony.hoshi.features.anki

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import moe.antimony.hoshi.R
import moe.antimony.hoshi.features.ai.AiRepository
import moe.antimony.hoshi.features.ai.AiSettings

/**
 * A card from a popup's Anki button before it starts. [build] makes the request once it is known
 * whether an AI example sentence replaces the sentence; [onCancel] releases what was prepared.
 */
internal class AnkiPendingCard(
    val sentenceLength: Int,
    val build: (useAiExample: Boolean) -> AnkiCardRequest,
    val onCancel: () -> Unit = {},
)

@HiltViewModel
internal class AnkiCardJobsViewModel @Inject constructor(
    private val runner: AnkiCardJobRunner,
    aiRepository: AiRepository,
) : ViewModel() {
    val jobs: StateFlow<List<AnkiCardJob>> = runner.jobs

    /** A card over the long sentence limit, waiting for [confirmLongCard]. */
    private val _pendingLongCard = MutableStateFlow<AnkiPendingCard?>(null)
    val pendingLongCard: StateFlow<AnkiPendingCard?> = _pendingLongCard.asStateFlow()

    private val longSentenceLimit = aiRepository.settings
        .map { it.longSentenceLimit }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AiSettings.DEFAULT_LONG_SENTENCE_LIMIT)

    /** Starts [card] now, or asks first when its sentence is longer than the long sentence limit. */
    fun requestCard(card: AnkiPendingCard) {
        if (card.sentenceLength > longSentenceLimit.value) {
            _pendingLongCard.value = card
        } else {
            runner.start(card.build(false))
        }
    }

    /** The long sentence dialog's answer: keep the original, use an AI example, or cancel (null). */
    fun confirmLongCard(useAiExample: Boolean?) {
        val card = _pendingLongCard.value ?: return
        _pendingLongCard.value = null
        if (useAiExample == null) card.onCancel() else runner.start(card.build(useAiExample))
    }

    fun cancel(id: Long) = runner.cancel(id)

    fun clearFinished(id: Long) = runner.clearFinished(id)
}

/** Asks how to make a card whose sentence is longer than the long sentence limit. */
@Composable
internal fun AnkiLongSentenceDialog(viewModel: AnkiCardJobsViewModel) {
    val pending by viewModel.pendingLongCard.collectAsStateWithLifecycle()
    val card = pending ?: return
    AlertDialog(
        onDismissRequest = { viewModel.confirmLongCard(null) },
        title = { Text(stringResource(R.string.anki_long_sentence_title)) },
        text = { Text(pluralStringResource(R.plurals.anki_long_sentence_message, card.sentenceLength, card.sentenceLength)) },
        confirmButton = {
            TextButton(onClick = { viewModel.confirmLongCard(true) }) {
                Text(stringResource(R.string.anki_long_sentence_use_ai_example))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { viewModel.confirmLongCard(null) }) { Text(stringResource(R.string.action_cancel)) }
                TextButton(onClick = { viewModel.confirmLongCard(false) }) {
                    Text(stringResource(R.string.anki_long_sentence_keep_original))
                }
            }
        },
    )
}

private const val FinishedChipMs = 3_000L

/** Shows the newest card job: progress with Cancel, then its result for a few seconds. */
@Composable
internal fun AnkiCardJobChip(
    modifier: Modifier = Modifier,
    viewModel: AnkiCardJobsViewModel = hiltViewModel(),
) {
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val job = jobs.lastOrNull()
    LaunchedEffect(job?.id, job?.status) {
        if (job != null && job.status != AnkiCardJobStatus.Working) {
            delay(FinishedChipMs)
            viewModel.clearFinished(job.id)
        }
    }
    AnimatedVisibility(visible = job != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        if (job == null) return@AnimatedVisibility
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            tonalElevation = 2.dp,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 8.dp).widthIn(max = 480.dp),
            ) {
                if (job.status == AnkiCardJobStatus.Working) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = when (val status = job.status) {
                        AnkiCardJobStatus.Working -> stringResource(R.string.anki_card_job_working_format, job.label)
                        is AnkiCardJobStatus.Added -> if (status.duplicate) {
                            stringResource(R.string.anki_card_job_added_duplicate_format, job.label)
                        } else {
                            stringResource(R.string.anki_card_job_added_format, job.label)
                        }
                        AnkiCardJobStatus.Failed -> stringResource(R.string.anki_card_job_failed_format, job.label)
                        AnkiCardJobStatus.Cancelled -> stringResource(R.string.anki_card_job_cancelled)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 12.dp).weight(1f, fill = false),
                )
                if (job.status == AnkiCardJobStatus.Working) {
                    TextButton(onClick = { viewModel.cancel(job.id) }) {
                        Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.inversePrimary)
                    }
                }
            }
        }
    }
}
