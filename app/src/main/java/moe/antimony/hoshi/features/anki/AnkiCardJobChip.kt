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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import moe.antimony.hoshi.R

@HiltViewModel
internal class AnkiCardJobsViewModel @Inject constructor(
    private val runner: AnkiCardJobRunner,
) : ViewModel() {
    val jobs: StateFlow<List<AnkiCardJob>> = runner.jobs

    fun start(request: AnkiCardRequest): Long = runner.start(request)

    fun cancel(id: Long) = runner.cancel(id)

    fun clearFinished(id: Long) = runner.clearFinished(id)
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
