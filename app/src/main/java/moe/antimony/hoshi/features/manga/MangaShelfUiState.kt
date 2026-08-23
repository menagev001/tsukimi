package moe.antimony.hoshi.features.manga

import moe.antimony.hoshi.features.bookshelf.BookCoverSource
import moe.antimony.hoshi.manga.MangaSortOption
import moe.antimony.hoshi.manga.MangaVolumeEntry
import moe.antimony.hoshi.ui.UiText

internal data class MangaShelfUiState(
    val volumes: List<MangaVolumeEntry> = emptyList(),
    val coverSourcesById: Map<String, BookCoverSource> = emptyMap(),
    val progressById: Map<String, Double> = emptyMap(),
    val sortOption: MangaSortOption = MangaSortOption.Recent,
    val hasLoadedVolumes: Boolean = false,
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
    val openReaderVolumeId: String? = null,
    val statusMessage: UiText? = null,
    val errorMessage: UiText? = null,
)
