package moe.antimony.hoshi.features.watch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.antimony.hoshi.R
import moe.antimony.hoshi.features.bookshelf.MainShellLayoutSpec
import moe.antimony.hoshi.features.settings.SettingsDetailScaffold

private val ThumbCardWidth = 260.dp
private val PosterCellMinWidth = 140.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WatchHomeView(
    layoutSpec: MainShellLayoutSpec,
    onOpenLibrary: (WatchLibrary) -> Unit,
    onOpenItem: (WatchItem) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: WatchHomeViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.main_tab_watch),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    if (uiState.isSignedIn == true) {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_refresh))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        when (uiState.isSignedIn) {
            null -> Unit
            false -> WatchCenteredMessage(
                message = stringResource(R.string.watch_not_signed_in_message),
                primaryAction = stringResource(R.string.watch_open_settings) to onOpenSettings,
                modifier = contentModifier,
            )
            true -> when (val home = uiState.home) {
                WatchLoadState.Loading -> WatchLoading(contentModifier)
                is WatchLoadState.Failed -> WatchLoadFailureMessage(
                    failure = home.failure,
                    onRetry = viewModel::refresh,
                    onOpenSettings = onOpenSettings,
                    modifier = contentModifier,
                )
                is WatchLoadState.Ready -> WatchHomeContent(
                    home = home.value,
                    layoutSpec = layoutSpec,
                    onOpenLibrary = onOpenLibrary,
                    onOpenItem = onOpenItem,
                    modifier = contentModifier,
                )
            }
        }
    }
}

@Composable
private fun WatchHomeContent(
    home: WatchHome,
    layoutSpec: MainShellLayoutSpec,
    onOpenLibrary: (WatchLibrary) -> Unit,
    onOpenItem: (WatchItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val horizontalPadding = layoutSpec.pageHorizontalPaddingDp.dp
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        homeRow(R.string.watch_continue_watching, home.continueWatching, horizontalPadding) { item ->
            WatchThumbCard(item = item, onClick = { onOpenItem(item) }, modifier = Modifier.width(ThumbCardWidth))
        }
        homeRow(R.string.watch_next_up, home.nextUp, horizontalPadding) { item ->
            WatchThumbCard(item = item, onClick = { onOpenItem(item) }, modifier = Modifier.width(ThumbCardWidth))
        }
        homeRow(R.string.watch_libraries, home.libraries, horizontalPadding) { library ->
            WatchLibraryCard(library = library, onClick = { onOpenLibrary(library) }, modifier = Modifier.width(ThumbCardWidth))
        }
    }
}

private fun <T> LazyListScope.homeRow(
    titleRes: Int,
    entries: List<T>,
    horizontalPadding: Dp,
    card: @Composable (T) -> Unit,
) {
    if (entries.isEmpty()) return
    item {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(entries) { card(it) }
        }
    }
}

@Composable
internal fun WatchLibraryView(
    libraryId: String,
    title: String,
    kind: WatchLibraryKind,
    onOpenItem: (WatchItem) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<WatchLibraryViewModel, WatchLibraryViewModel.Factory>(
        creationCallback = { factory -> factory.create(libraryId, kind) },
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsDetailScaffold(title = title, onClose = onClose, modifier = modifier) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        when (val items = uiState.items) {
            WatchLoadState.Loading -> WatchLoading(contentModifier)
            is WatchLoadState.Failed -> WatchLoadFailureMessage(
                failure = items.failure,
                onRetry = viewModel::reload,
                onOpenSettings = null,
                modifier = contentModifier,
            )
            is WatchLoadState.Ready -> if (items.value.isEmpty()) {
                WatchCenteredMessage(message = stringResource(R.string.watch_empty_library), modifier = contentModifier)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(PosterCellMinWidth),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = contentModifier.fillMaxSize(),
                ) {
                    items(items.value, key = WatchItem::id) { item ->
                        WatchPosterCard(item = item, onClick = { onOpenItem(item) })
                    }
                }
            }
        }
    }
}

@Composable
internal fun WatchSeriesView(
    seriesId: String,
    title: String,
    onOpenItem: (WatchItem) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<WatchSeriesViewModel, WatchSeriesViewModel.Factory>(
        creationCallback = { factory -> factory.create(seriesId) },
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsDetailScaffold(title = title, onClose = onClose, modifier = modifier) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        when (val series = uiState.series) {
            WatchLoadState.Loading -> WatchLoading(contentModifier)
            is WatchLoadState.Failed -> WatchLoadFailureMessage(
                failure = series.failure,
                onRetry = viewModel::reload,
                onOpenSettings = null,
                modifier = contentModifier,
            )
            is WatchLoadState.Ready -> LazyColumn(
                modifier = contentModifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                series.value.series.overview?.takeIf(String::isNotBlank)?.let { overview ->
                    item {
                        Text(
                            text = overview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                }
                if (series.value.seasons.size > 1) {
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 8.dp),
                        ) {
                            items(series.value.seasons, key = WatchItem::id) { season ->
                                FilterChip(
                                    selected = season.id == uiState.selectedSeasonId,
                                    onClick = { viewModel.selectSeason(season.id) },
                                    label = { Text(season.name) },
                                )
                            }
                        }
                    }
                }
                when (val episodes = uiState.episodes) {
                    WatchLoadState.Loading -> item { WatchLoading(Modifier.padding(32.dp)) }
                    is WatchLoadState.Failed -> item {
                        WatchLoadFailureMessage(
                            failure = episodes.failure,
                            onRetry = viewModel::reload,
                            onOpenSettings = null,
                        )
                    }
                    is WatchLoadState.Ready -> if (episodes.value.isEmpty()) {
                        item { WatchCenteredMessage(message = stringResource(R.string.watch_no_episodes)) }
                    } else {
                        items(episodes.value, key = WatchItem::id) { episode ->
                            WatchEpisodeRow(episode = episode, onClick = { onOpenItem(episode) })
                        }
                    }
                }
            }
        }
    }
}
