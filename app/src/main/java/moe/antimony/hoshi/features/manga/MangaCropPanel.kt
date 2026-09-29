package moe.antimony.hoshi.features.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import moe.antimony.hoshi.R

/**
 * Page-crop controls, docked to the bottom of the reader rather than shown as a modal sheet so the
 * page stays visible above it and every change can be judged live. Manual values move in 1% steps
 * via buttons instead of a slider: dragging a slider repaints continuously, which e-ink panels
 * render as a smeared, ghosting mess.
 */
@Composable
internal fun MangaCropPanel(
    state: MangaCropUiState,
    onModeChange: (MangaCropMode) -> Unit,
    onAdjust: (MangaCropEdge, Int) -> Unit,
    onResetToDetected: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.manga_crop_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close))
                }
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                MangaCropMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.mode == mode,
                        onClick = { onModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, MangaCropMode.entries.size),
                        icon = {},
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(mode.labelRes))
                    }
                }
            }
            Text(
                text = stringResource(
                    when {
                        state.mode == MangaCropMode.Off -> R.string.manga_crop_off_description
                        state.detecting -> R.string.manga_crop_auto_detecting
                        state.mode == MangaCropMode.Auto -> R.string.manga_crop_auto_description
                        else -> R.string.manga_crop_manual_description
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.mode == MangaCropMode.Manual) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CropStepper(MangaCropEdge.Top, state, onAdjust, Modifier.weight(1f))
                    CropStepper(MangaCropEdge.Bottom, state, onAdjust, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CropStepper(MangaCropEdge.Left, state, onAdjust, Modifier.weight(1f))
                    CropStepper(MangaCropEdge.Right, state, onAdjust, Modifier.weight(1f))
                }
                TextButton(onClick = onResetToDetected, enabled = state.auto != null) {
                    Text(stringResource(R.string.manga_crop_reset))
                }
            }
            Text(
                text = stringResource(R.string.manga_crop_series_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CropStepper(
    edge: MangaCropEdge,
    state: MangaCropUiState,
    onAdjust: (MangaCropEdge, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val edgeLabel = stringResource(edge.labelRes)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text = edgeLabel, modifier = Modifier.weight(1f))
        IconButton(onClick = { onAdjust(edge, -1) }) {
            Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.manga_crop_decrease, edgeLabel))
        }
        Text(
            text = stringResource(R.string.manga_crop_percent, state.percent(edge)),
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 40.dp),
        )
        IconButton(onClick = { onAdjust(edge, 1) }) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.manga_crop_increase, edgeLabel))
        }
    }
}

private val MangaCropMode.labelRes: Int
    get() = when (this) {
        MangaCropMode.Off -> R.string.manga_crop_mode_off
        MangaCropMode.Auto -> R.string.manga_crop_mode_auto
        MangaCropMode.Manual -> R.string.manga_crop_mode_manual
    }

private val MangaCropEdge.labelRes: Int
    get() = when (this) {
        MangaCropEdge.Top -> R.string.manga_crop_edge_top
        MangaCropEdge.Bottom -> R.string.manga_crop_edge_bottom
        MangaCropEdge.Left -> R.string.manga_crop_edge_left
        MangaCropEdge.Right -> R.string.manga_crop_edge_right
    }
