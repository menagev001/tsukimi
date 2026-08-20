package moe.antimony.hoshi.features.ai

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.antimony.hoshi.R
import moe.antimony.hoshi.ui.asString

@Composable
fun AiLookupOverlay(
    state: AiLookupState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state is AiLookupState.Hidden) return
    BackHandler(onBack = onDismiss)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .systemBarsPadding(),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.ai_lookup_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close))
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                val sentence = when (state) {
                    is AiLookupState.Loading -> state.sentence
                    is AiLookupState.Success -> state.sentence
                    is AiLookupState.Error -> state.sentence
                    AiLookupState.Hidden -> ""
                }
                Text(
                    text = sentence,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                when (state) {
                    is AiLookupState.Loading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                            Text(stringResource(R.string.ai_lookup_loading))
                        }
                    }
                    is AiLookupState.Success -> {
                        Text(
                            text = state.response,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .height(280.dp),
                        )
                    }
                    is AiLookupState.Error -> {
                        Column {
                            Text(
                                text = state.message.asString(),
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                TextButton(onClick = onRetry) {
                                    Text(stringResource(R.string.ai_lookup_retry))
                                }
                            }
                        }
                    }
                    AiLookupState.Hidden -> Unit
                }
            }
        }
    }
}
