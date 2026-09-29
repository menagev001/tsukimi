package moe.antimony.hoshi.features.ai

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import moe.antimony.hoshi.R
import moe.antimony.hoshi.ui.asString

@Composable
fun AiLookupOverlay(
    state: AiLookupState,
    onConfirmSentenceBreakdown: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state is AiLookupState.Hidden) return
    BackHandler(onBack = onDismiss)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        val maxDialogHeight = maxHeight * 0.85f
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .heightIn(max = maxDialogHeight)
                .systemBarsPadding(),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(state.titleRes()),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close))
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    text = sentenceAnnotatedString(state.sentence(), state.highlightWord()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                when (state) {
                    is AiLookupState.Confirm -> {
                        Button(onClick = onConfirmSentenceBreakdown, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.sentence_breakdown_title))
                        }
                    }
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
                        ScrollableResponseText(
                            text = state.response,
                            modifier = Modifier.weight(1f, fill = false),
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

@Composable
private fun ScrollableResponseText(text: String, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    Row(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState),
        )
        if (scrollState.maxValue > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            ScrollbarTrack(scrollState = scrollState, modifier = Modifier.fillMaxHeight())
        }
    }
}

@Composable
private fun ScrollbarTrack(scrollState: ScrollState, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier
            .width(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)),
    ) {
        val trackHeight = maxHeight
        val viewportFraction = if (scrollState.maxValue > 0) {
            (scrollState.viewportSize.toFloat() / (scrollState.maxValue + scrollState.viewportSize)).coerceIn(0.08f, 1f)
        } else {
            1f
        }
        val scrollFraction = if (scrollState.maxValue > 0) {
            scrollState.value.toFloat() / scrollState.maxValue
        } else {
            0f
        }
        val thumbHeight = trackHeight * viewportFraction
        val thumbOffset = (trackHeight - thumbHeight) * scrollFraction
        Box(
            modifier = Modifier
                .padding(top = thumbOffset)
                .width(4.dp)
                .height(thumbHeight)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)),
        )
    }
}

private fun sentenceAnnotatedString(sentence: String, highlightWord: String?): AnnotatedString =
    buildAnnotatedString {
        if (highlightWord.isNullOrEmpty()) {
            append(sentence)
            return@buildAnnotatedString
        }
        var index = 0
        while (index < sentence.length) {
            val found = sentence.indexOf(highlightWord, index)
            if (found < 0) {
                append(sentence.substring(index))
                break
            }
            append(sentence.substring(index, found))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(sentence.substring(found, found + highlightWord.length))
            }
            index = found + highlightWord.length
        }
    }

private fun AiLookupState.sentence(): String = when (this) {
    is AiLookupState.Confirm -> request.sentence
    is AiLookupState.Loading -> request.sentence
    is AiLookupState.Success -> request.sentence
    is AiLookupState.Error -> request.sentence
    AiLookupState.Hidden -> ""
}

private fun AiLookupState.highlightWord(): String? = when (this) {
    is AiLookupState.Confirm -> null
    is AiLookupState.Loading -> (request as? AiLookupRequest.WordNuance)?.word
    is AiLookupState.Success -> (request as? AiLookupRequest.WordNuance)?.word
    is AiLookupState.Error -> (request as? AiLookupRequest.WordNuance)?.word
    AiLookupState.Hidden -> null
}

private fun AiLookupState.titleRes(): Int = when (this) {
    is AiLookupState.Confirm -> R.string.sentence_breakdown_title
    is AiLookupState.Loading -> request.titleRes()
    is AiLookupState.Success -> request.titleRes()
    is AiLookupState.Error -> request.titleRes()
    AiLookupState.Hidden -> R.string.explain_word_title
}

private fun AiLookupRequest.titleRes(): Int = when (this) {
    is AiLookupRequest.WordNuance -> R.string.explain_word_title
    is AiLookupRequest.SentenceBreakdown -> R.string.sentence_breakdown_title
}
