package moe.antimony.hoshi.features.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface WatchLoadState<out T> {
    data object Loading : WatchLoadState<Nothing>
    data class Ready<T>(val value: T) : WatchLoadState<T>
    data class Failed(val failure: WatchLoadFailure) : WatchLoadState<Nothing>
}

internal fun <T> Result<T>.toWatchLoadState(): WatchLoadState<T> = fold(
    onSuccess = { WatchLoadState.Ready(it) },
    onFailure = { WatchLoadState.Failed(watchLoadFailure(it)) },
)

data class WatchHomeUiState(
    /** Null until the stored session has been read. */
    val isSignedIn: Boolean? = null,
    val home: WatchLoadState<WatchHome> = WatchLoadState.Loading,
)

@HiltViewModel
internal class WatchHomeViewModel @Inject constructor(
    jellyfinRepository: JellyfinRepository,
    private val browseRepository: WatchBrowseRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WatchHomeUiState())
    val uiState: StateFlow<WatchHomeUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            jellyfinRepository.settings
                .map { settings -> settings.takeIf { it.isLoggedIn }?.let { it.serverUrl to it.accessToken } }
                .distinctUntilChanged()
                .collect { session ->
                    loadJob?.cancel()
                    _uiState.update { it.copy(isSignedIn = session != null, home = WatchLoadState.Loading) }
                    refresh()
                }
        }
    }

    /** Reloads the rows; already loaded content stays visible until the new data arrives. */
    fun refresh() {
        if (_uiState.value.isSignedIn != true || loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            val result = browseRepository.home().toWatchLoadState()
            _uiState.update { current ->
                if (result is WatchLoadState.Failed && current.home is WatchLoadState.Ready) current else current.copy(home = result)
            }
        }
    }
}

data class WatchLibraryUiState(
    val items: WatchLoadState<List<WatchItem>> = WatchLoadState.Loading,
)

@HiltViewModel(assistedFactory = WatchLibraryViewModel.Factory::class)
internal class WatchLibraryViewModel @AssistedInject constructor(
    @Assisted private val libraryId: String,
    @Assisted private val kind: WatchLibraryKind,
    private val browseRepository: WatchBrowseRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WatchLibraryUiState())
    val uiState: StateFlow<WatchLibraryUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        _uiState.value = WatchLibraryUiState()
        viewModelScope.launch {
            _uiState.value = WatchLibraryUiState(browseRepository.libraryItems(libraryId, kind).toWatchLoadState())
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(libraryId: String, kind: WatchLibraryKind): WatchLibraryViewModel
    }
}

data class WatchSeriesUiState(
    val series: WatchLoadState<WatchSeries> = WatchLoadState.Loading,
    val selectedSeasonId: String? = null,
    val episodes: WatchLoadState<List<WatchItem>> = WatchLoadState.Loading,
)

@HiltViewModel(assistedFactory = WatchSeriesViewModel.Factory::class)
internal class WatchSeriesViewModel @AssistedInject constructor(
    @Assisted private val seriesId: String,
    private val browseRepository: WatchBrowseRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WatchSeriesUiState())
    val uiState: StateFlow<WatchSeriesUiState> = _uiState.asStateFlow()
    private var episodesJob: Job? = null

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            val previousSeasonId = _uiState.value.selectedSeasonId
            _uiState.value = WatchSeriesUiState()
            val series = browseRepository.series(seriesId).toWatchLoadState()
            val seasons = (series as? WatchLoadState.Ready)?.value?.seasons.orEmpty()
            val seasonId = seasons.firstOrNull { it.id == previousSeasonId }?.id ?: defaultSeason(seasons)?.id
            _uiState.value = WatchSeriesUiState(series = series, selectedSeasonId = seasonId)
            if (seasonId != null) loadEpisodes(seasonId)
        }
    }

    fun selectSeason(seasonId: String) {
        if (seasonId == _uiState.value.selectedSeasonId) return
        _uiState.update { it.copy(selectedSeasonId = seasonId) }
        loadEpisodes(seasonId)
    }

    private fun loadEpisodes(seasonId: String) {
        episodesJob?.cancel()
        _uiState.update { it.copy(episodes = WatchLoadState.Loading) }
        episodesJob = viewModelScope.launch {
            val episodes = browseRepository.episodes(seriesId, seasonId).toWatchLoadState()
            _uiState.update { if (it.selectedSeasonId == seasonId) it.copy(episodes = episodes) else it }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(seriesId: String): WatchSeriesViewModel
    }
}

/** The first season that still has unwatched episodes, ignoring specials, else the first season. */
internal fun defaultSeason(seasons: List<WatchItem>): WatchItem? =
    seasons.firstOrNull { (it.seasonNumber ?: 0) > 0 && (it.unplayedCount ?: 0) > 0 }
        ?: seasons.firstOrNull { (it.seasonNumber ?: 0) > 0 }
        ?: seasons.firstOrNull()
