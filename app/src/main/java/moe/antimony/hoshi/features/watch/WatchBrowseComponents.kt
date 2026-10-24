package moe.antimony.hoshi.features.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.antimony.hoshi.R

private val CardShape = RoundedCornerShape(8.dp)

@Composable
internal fun WatchCenteredMessage(
    message: String,
    modifier: Modifier = Modifier,
    primaryAction: Pair<String, () -> Unit>? = null,
    secondaryAction: Pair<String, () -> Unit>? = null,
) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.widthIn(max = 420.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            primaryAction?.let { (label, onClick) -> Button(onClick = onClick) { Text(label) } }
            secondaryAction?.let { (label, onClick) -> OutlinedButton(onClick = onClick) { Text(label) } }
        }
    }
}

@Composable
internal fun WatchLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun WatchLoadFailureMessage(
    failure: WatchLoadFailure,
    onRetry: () -> Unit,
    onOpenSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val needsSignIn = failure == WatchLoadFailure.NotSignedIn || failure == WatchLoadFailure.SessionExpired
    WatchCenteredMessage(
        message = stringResource(failure.messageRes),
        modifier = modifier,
        primaryAction = if (needsSignIn && onOpenSettings != null) {
            stringResource(R.string.watch_open_settings) to onOpenSettings
        } else {
            stringResource(R.string.action_retry) to onRetry
        },
    )
}

@Composable
private fun WatchImage(
    url: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Movie,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp),
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun WatchedBadge(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Rounded.CheckCircle,
        contentDescription = stringResource(R.string.watch_played),
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(6.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .size(22.dp),
    )
}

@Composable
private fun WatchProgressBar(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress },
        drawStopIndicator = {},
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .height(4.dp),
    )
}

/** Landscape card for home rows: episode still or backdrop, progress, and a two-line caption. */
@Composable
internal fun WatchThumbCard(
    item: WatchItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.clip(CardShape).clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            WatchImage(url = item.thumbUrl, modifier = Modifier.fillMaxSize())
            item.progress?.let { WatchProgressBar(it, Modifier.align(Alignment.BottomCenter)) }
            if (item.played) WatchedBadge(Modifier.align(Alignment.TopEnd))
        }
        Text(
            text = item.seriesName ?: item.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        watchItemSubtitle(item)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Portrait card for library grids and season pickers. */
@Composable
internal fun WatchPosterCard(
    item: WatchItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.clip(CardShape).clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            WatchImage(url = item.posterUrl, modifier = Modifier.fillMaxSize())
            item.progress?.let { WatchProgressBar(it, Modifier.align(Alignment.BottomCenter)) }
            if (item.played) WatchedBadge(Modifier.align(Alignment.TopEnd))
        }
        Text(
            text = item.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        item.productionYear?.let {
            Text(
                text = it.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun WatchLibraryCard(
    library: WatchLibrary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.clip(CardShape).clickable(onClick = onClick),
    ) {
        WatchImage(url = library.imageUrl, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Text(
            text = library.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun WatchEpisodeRow(
    episode: WatchItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Box(modifier = Modifier.widthIn(min = 160.dp, max = 220.dp).aspectRatio(16f / 9f)) {
            WatchImage(url = episode.thumbUrl, modifier = Modifier.fillMaxSize())
            episode.progress?.let { WatchProgressBar(it, Modifier.align(Alignment.BottomCenter)) }
            if (episode.played) WatchedBadge(Modifier.align(Alignment.TopEnd))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Text(
                text = episode.episodeNumber?.let { stringResource(R.string.watch_episode_title_format, it, episode.name) }
                    ?: episode.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            episode.runTimeMs?.let {
                Text(
                    text = stringResource(R.string.watch_runtime_minutes_format, watchRuntimeMinutes(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            episode.overview?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** "S1:E3 · Title" for episodes, the year for films. */
@Composable
internal fun watchItemSubtitle(item: WatchItem): String? = when (item.kind) {
    WatchItemKind.Episode -> {
        val code = if (item.seasonNumber != null && item.episodeNumber != null) {
            stringResource(R.string.watch_episode_code_format, item.seasonNumber, item.episodeNumber)
        } else {
            null
        }
        listOfNotNull(code, item.name.takeIf(String::isNotBlank)).joinToString(" · ").ifEmpty { null }
    }
    else -> item.productionYear?.toString()
}

internal fun watchRuntimeMinutes(runTimeMs: Long): Int =
    ((runTimeMs + 30_000) / 60_000).toInt().coerceAtLeast(1)
