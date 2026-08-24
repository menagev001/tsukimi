package moe.antimony.hoshi.features.manga

import android.content.ContentResolver
import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import moe.antimony.hoshi.di.IoDispatcher
import moe.antimony.hoshi.features.bookshelf.BookCoverSource
import moe.antimony.hoshi.features.bookshelf.toBookCoverSource
import moe.antimony.hoshi.importing.ImportFileType
import moe.antimony.hoshi.importing.SafImportDirectoryScanner
import moe.antimony.hoshi.manga.MangaImportCandidate
import moe.antimony.hoshi.manga.MangaSortOption
import moe.antimony.hoshi.manga.MangaVolumeEntry
import moe.antimony.hoshi.manga.MangaRepository
import moe.antimony.hoshi.manga.pairMangaImportCandidates

/**
 * Shelf-facing façade over [MangaRepository], mirroring how `BookshelfRepository` sits above
 * `epub.BookRepository`. UI/ViewModel code goes through this rather than reaching into
 * `MangaRepository` directly.
 */
internal interface MangaLibraryRepository {
    suspend fun loadVolumes(sortOption: MangaSortOption = MangaSortOption.Recent): List<MangaVolumeEntry>
    suspend fun coverSourcesById(entries: List<MangaVolumeEntry>): Map<String, BookCoverSource>
    suspend fun progressById(entries: List<MangaVolumeEntry>): Map<String, Double>
    suspend fun openVolume(entry: MangaVolumeEntry): String
    /** Self-contained CBZ (the common case): its `.mokuro` sidecar is bundled inside. */
    suspend fun importCbz(cbzUri: Uri): String
    /** CBZ paired with a separate `.mokuro` sidecar file. */
    suspend fun importCbz(cbzUri: Uri, mokuroUri: Uri): String
    suspend fun importFolder(treeUri: Uri): List<String>
    suspend fun deleteVolume(entry: MangaVolumeEntry)
    suspend fun renameVolume(entry: MangaVolumeEntry, title: String?)
}

@Singleton
internal class AndroidMangaLibraryRepository @Inject constructor(
    private val contentResolver: ContentResolver,
    private val mangaRepository: MangaRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : MangaLibraryRepository {
    override suspend fun loadVolumes(sortOption: MangaSortOption): List<MangaVolumeEntry> =
        mangaRepository.loadVolumeEntries(sortOption)

    override suspend fun coverSourcesById(entries: List<MangaVolumeEntry>): Map<String, BookCoverSource> =
        withContext(ioDispatcher) {
            entries.mapNotNull { entry ->
                mangaRepository.coverFile(entry)?.toBookCoverSource()?.let { source ->
                    entry.metadata.id to source
                }
            }.toMap()
        }

    override suspend fun progressById(entries: List<MangaVolumeEntry>): Map<String, Double> =
        withContext(ioDispatcher) {
            entries.associate { entry ->
                val pageCount = entry.metadata.pageCount
                val pageIndex = mangaRepository.loadBookmark(entry.root)?.pageIndex ?: 0
                val progress = if (pageCount > 0) {
                    (pageIndex.toDouble() / pageCount.toDouble()).coerceIn(0.0, 1.0)
                } else {
                    0.0
                }
                entry.metadata.id to progress
            }
        }

    override suspend fun openVolume(entry: MangaVolumeEntry): String = withContext(ioDispatcher) {
        val metadata = mangaRepository.loadMetadata(entry.root) ?: entry.metadata
        mangaRepository.saveMetadata(
            entry.root,
            metadata.copy(lastAccess = mangaRepository.currentAppleReferenceDateSeconds()),
        )
        metadata.id
    }

    override suspend fun importCbz(cbzUri: Uri): String = withContext(ioDispatcher) {
        val root = mangaRepository.importCbz(contentResolver, cbzUri)
        requireNotNull(mangaRepository.loadMetadata(root)).id
    }

    override suspend fun importCbz(cbzUri: Uri, mokuroUri: Uri): String = withContext(ioDispatcher) {
        val root = mangaRepository.importCbz(contentResolver, cbzUri, mokuroUri)
        requireNotNull(mangaRepository.loadMetadata(root)).id
    }

    override suspend fun importFolder(treeUri: Uri): List<String> = withContext(ioDispatcher) {
        val scanner = SafImportDirectoryScanner(contentResolver)
        val candidates: List<MangaImportCandidate<Uri>> = pairMangaImportCandidates(
            mokuroFiles = scanner.scan(treeUri, ImportFileType.MokuroSidecar),
            cbzFiles = scanner.scan(treeUri, ImportFileType.MangaCbz),
            imageFiles = scanner.scan(treeUri, ImportFileType.MangaPageImage),
        )
        candidates.map { candidate ->
            val root = mangaRepository.importCandidate(contentResolver, candidate)
            requireNotNull(mangaRepository.loadMetadata(root)).id
        }
    }

    override suspend fun deleteVolume(entry: MangaVolumeEntry) {
        mangaRepository.deleteVolume(entry.root)
    }

    override suspend fun renameVolume(entry: MangaVolumeEntry, title: String?) = withContext(ioDispatcher) {
        val metadata = mangaRepository.loadMetadata(entry.root) ?: entry.metadata
        mangaRepository.saveMetadata(entry.root, metadata.copy(renamedTitle = title))
    }
}
