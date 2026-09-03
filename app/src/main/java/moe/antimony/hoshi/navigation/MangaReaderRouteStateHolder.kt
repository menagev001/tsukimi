package moe.antimony.hoshi.navigation

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.antimony.hoshi.manga.MangaBookmark
import moe.antimony.hoshi.manga.MangaReadingStats
import moe.antimony.hoshi.manga.MangaRepository
import moe.antimony.hoshi.manga.MangaVolumeEntry
import moe.antimony.hoshi.manga.MokuroVolume

/**
 * Leaner analog of [ReaderRouteStateHolder] for manga: no Drive/TTU auto-sync, wallpaper
 * publication, or Sasayaki cues -- just load the volume entry, lazily parse its mokuro sidecar,
 * and load/save the page-index bookmark.
 */
internal class MangaReaderRouteStateHolder(
    private val repository: MangaRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun load(volumeId: String): MangaReaderRouteLoadState = withContext(ioDispatcher) {
        runCatching {
            val entry = repository.loadVolumeEntry(volumeId) ?: error("Manga volume not found.")
            val mokuroVolume = repository.loadMokuroVolume(entry.root)
                ?: error("Unable to read this volume's mokuro data.")
            val metadata = entry.metadata.copy(
                lastAccess = repository.currentAppleReferenceDateSeconds(),
            )
            repository.saveMetadata(entry.root, metadata)
            val displayEntry = entry.copy(metadata = metadata)
            val bookmark = repository.loadBookmark(entry.root)
            MangaReaderRouteLoadState.Ready(
                entry = displayEntry,
                mokuroVolume = mokuroVolume,
                bookmark = bookmark,
            )
        }.getOrElse { error ->
            MangaReaderRouteLoadState.Error(error.localizedMessage ?: "Failed to open manga volume.")
        }
    }

    suspend fun saveBookmark(entry: MangaVolumeEntry, pageIndex: Int) {
        withContext(ioDispatcher) {
            repository.saveBookmark(
                entry.root,
                MangaBookmark(
                    pageIndex = pageIndex,
                    lastModified = repository.currentAppleReferenceDateSeconds(),
                ),
            )
        }
    }

    /**
     * Adds [deltaSeconds] of active reading time to this volume's running total. Called on a
     * long-lived, dispose-safe scope (see [moe.antimony.hoshi.navigation.MangaReaderRouteDestination])
     * rather than a composable-scoped one, since the save needs to actually complete as the reader
     * screen is being torn down (navigating away), not race its own cancellation.
     */
    suspend fun addReadingSeconds(entry: MangaVolumeEntry, deltaSeconds: Double) {
        if (deltaSeconds <= 0.0) return
        withContext(ioDispatcher) {
            val current = repository.loadReadingStats(entry.root)
            repository.saveReadingStats(entry.root, current.copy(totalSeconds = current.totalSeconds + deltaSeconds))
        }
    }
}

internal sealed interface MangaReaderRouteLoadState {
    data object Loading : MangaReaderRouteLoadState

    data class Ready(
        val entry: MangaVolumeEntry,
        val mokuroVolume: MokuroVolume,
        val bookmark: MangaBookmark?,
    ) : MangaReaderRouteLoadState

    data class Error(
        val message: String,
    ) : MangaReaderRouteLoadState
}
