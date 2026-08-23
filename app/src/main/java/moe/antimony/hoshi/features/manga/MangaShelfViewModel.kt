package moe.antimony.hoshi.features.manga

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.antimony.hoshi.R
import moe.antimony.hoshi.manga.MangaSortOption
import moe.antimony.hoshi.manga.MangaVolumeEntry
import moe.antimony.hoshi.ui.UiText

@HiltViewModel
internal class MangaShelfViewModel private constructor(
    private val repository: MangaLibraryRepository,
    private val injectedScope: CoroutineScope?,
    @Suppress("UNUSED_PARAMETER") marker: Unit,
) : ViewModel() {
    @Inject
    constructor(repository: MangaLibraryRepository) : this(repository, injectedScope = null, marker = Unit)

    internal constructor(repository: MangaLibraryRepository, coroutineScope: CoroutineScope) :
        this(repository, injectedScope = coroutineScope, marker = Unit)

    private val workScope: CoroutineScope
        get() = injectedScope ?: viewModelScope

    private val _uiState = MutableStateFlow(MangaShelfUiState())
    val uiState: StateFlow<MangaShelfUiState> = _uiState

    private var openVolumeInFlight = false

    fun reloadVolumes(sortOption: MangaSortOption = _uiState.value.sortOption) {
        _uiState.update { it.copy(isLoading = !it.hasLoadedVolumes) }
        workScope.launch {
            val volumes = repository.loadVolumes(sortOption)
            val coverSourcesById = repository.coverSourcesById(volumes)
            val progressById = repository.progressById(volumes)
            _uiState.update {
                it.copy(
                    volumes = volumes,
                    coverSourcesById = coverSourcesById,
                    progressById = progressById,
                    sortOption = sortOption,
                    hasLoadedVolumes = true,
                    isLoading = false,
                )
            }
        }
    }

    fun openVolume(entry: MangaVolumeEntry) {
        if (openVolumeInFlight) return
        openVolumeInFlight = true
        workScope.launch {
            try {
                val volumeId = repository.openVolume(entry)
                _uiState.update { it.copy(openReaderVolumeId = volumeId) }
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        errorMessage = error.localizedMessage?.let(UiText::Literal)
                            ?: UiText.Resource(R.string.manga_shelf_open_failed),
                    )
                }
            } finally {
                openVolumeInFlight = false
            }
        }
    }

    fun reportSelectionError() {
        _uiState.update { it.copy(errorMessage = UiText.Resource(R.string.manga_shelf_import_selection_invalid)) }
    }

    fun importCbz(cbzUri: Uri, mokuroUri: Uri) {
        _uiState.update { it.copy(isImporting = true) }
        workScope.launch {
            try {
                repository.importCbz(cbzUri, mokuroUri)
                reloadVolumesSync()
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        errorMessage = error.localizedMessage?.let(UiText::Literal)
                            ?: UiText.Resource(R.string.manga_shelf_import_failed),
                    )
                }
            } finally {
                _uiState.update { it.copy(isImporting = false) }
            }
        }
    }

    fun importFolder(treeUri: Uri) {
        _uiState.update { it.copy(isImporting = true) }
        workScope.launch {
            try {
                val importedIds = repository.importFolder(treeUri)
                if (importedIds.isEmpty()) {
                    _uiState.update {
                        it.copy(errorMessage = UiText.Resource(R.string.manga_shelf_import_folder_empty))
                    }
                }
                reloadVolumesSync()
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        errorMessage = error.localizedMessage?.let(UiText::Literal)
                            ?: UiText.Resource(R.string.manga_shelf_import_failed),
                    )
                }
            } finally {
                _uiState.update { it.copy(isImporting = false) }
            }
        }
    }

    fun deleteVolume(entry: MangaVolumeEntry) {
        workScope.launch {
            repository.deleteVolume(entry)
            reloadVolumesSync()
        }
    }

    fun renameVolume(entry: MangaVolumeEntry, title: String) {
        workScope.launch {
            repository.renameVolume(entry, title.takeIf { it.isNotBlank() })
            reloadVolumesSync()
        }
    }

    fun consumeOpenReaderEvent() {
        _uiState.update { it.copy(openReaderVolumeId = null) }
    }

    fun consumeStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun consumeErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private suspend fun reloadVolumesSync(sortOption: MangaSortOption = _uiState.value.sortOption) {
        val volumes = repository.loadVolumes(sortOption)
        val coverSourcesById = repository.coverSourcesById(volumes)
        val progressById = repository.progressById(volumes)
        _uiState.update {
            it.copy(
                volumes = volumes,
                coverSourcesById = coverSourcesById,
                progressById = progressById,
                sortOption = sortOption,
                hasLoadedVolumes = true,
            )
        }
    }
}
