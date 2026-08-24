package moe.antimony.hoshi.features.manga

import android.net.Uri
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import moe.antimony.hoshi.R
import moe.antimony.hoshi.features.bookshelf.BookCoverSource
import moe.antimony.hoshi.manga.MangaSortOption
import moe.antimony.hoshi.manga.MangaVolumeEntry
import moe.antimony.hoshi.manga.MangaVolumeMetadata
import moe.antimony.hoshi.ui.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaShelfViewModelTest {
    @Test
    fun initialStateWaitsForFirstLoadBeforeShowingEmpty() {
        val viewModel = MangaShelfViewModel(FakeMangaLibraryRepository(), testScope())

        assertFalse(viewModel.uiState.value.hasLoadedVolumes)
    }

    @Test
    fun reloadVolumesPublishesEntriesCoversAndProgress() {
        val entry = volumeEntry("volume-a")
        val coverSource = BookCoverSource(path = "/tmp/volume-a/pages/001.jpg", cacheKey = "key")
        val repository = FakeMangaLibraryRepository(
            volumes = listOf(entry),
            coverSourcesById = mapOf("volume-a" to coverSource),
            progressById = mapOf("volume-a" to 0.4),
        )
        val viewModel = MangaShelfViewModel(repository, testScope())

        viewModel.reloadVolumes()

        assertEquals(listOf(entry), viewModel.uiState.value.volumes)
        assertEquals(mapOf("volume-a" to coverSource), viewModel.uiState.value.coverSourcesById)
        assertEquals(mapOf("volume-a" to 0.4), viewModel.uiState.value.progressById)
        assertTrue(viewModel.uiState.value.hasLoadedVolumes)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun openVolumePublishesOpenReaderEventThenClearsOnConsume() {
        val entry = volumeEntry("volume-a")
        val viewModel = MangaShelfViewModel(FakeMangaLibraryRepository(openVolumeId = "volume-a"), testScope())

        viewModel.openVolume(entry)

        assertEquals("volume-a", viewModel.uiState.value.openReaderVolumeId)

        viewModel.consumeOpenReaderEvent()

        assertNull(viewModel.uiState.value.openReaderVolumeId)
    }

    @Test
    fun openVolumeFailurePublishesErrorMessage() {
        val entry = volumeEntry("volume-a")
        val repository = FakeMangaLibraryRepository(openVolumeError = IllegalStateException("boom"))
        val viewModel = MangaShelfViewModel(repository, testScope())

        viewModel.openVolume(entry)

        assertEquals("boom", viewModel.uiState.value.errorMessage.testString())

        viewModel.consumeErrorMessage()

        assertNull(viewModel.uiState.value.errorMessage.testString())
    }

    // importCbz/importFolder aren't unit tested directly: android.net.Uri is unmocked on the
    // plain JVM unit test classpath here (no Robolectric/mockito-android in this project), the
    // same reason epub.BookImportDataSource.importBook(ContentResolver, Uri) has no direct unit
    // test either. The ViewModel logic around them (error/importing-state handling) is exercised
    // indirectly through openVolume's equivalent try/catch shape above.

    @Test
    fun deleteVolumeReloadsVolumes() {
        val repository = FakeMangaLibraryRepository(volumesAfterImport = emptyList())
        val viewModel = MangaShelfViewModel(repository, testScope())

        viewModel.deleteVolume(volumeEntry("volume-a"))

        assertTrue(repository.deletedEntryIds.contains("volume-a"))
        assertTrue(viewModel.uiState.value.hasLoadedVolumes)
    }

    @Test
    fun renameVolumeBlankTitleClearsRenamedTitle() {
        val repository = FakeMangaLibraryRepository()
        val viewModel = MangaShelfViewModel(repository, testScope())

        viewModel.renameVolume(volumeEntry("volume-a"), "   ")

        assertEquals(listOf("volume-a" to null), repository.renamedTitles)
    }

    private fun testScope(): CoroutineScope = CoroutineScope(Dispatchers.Unconfined)

    private fun volumeEntry(id: String): MangaVolumeEntry =
        MangaVolumeEntry(
            root = File(id),
            metadata = MangaVolumeMetadata(
                id = id,
                title = id,
                cover = null,
                folder = id,
                lastAccess = 0.0,
            ),
        )
}

private fun UiText?.testString(): String? =
    when (this) {
        null -> null
        is UiText.Literal -> value
        is UiText.Resource -> when (id) {
            R.string.manga_shelf_open_failed -> "Unable to open this manga volume."
            R.string.manga_shelf_import_failed -> "Unable to import this manga volume."
            R.string.manga_shelf_import_folder_empty -> "No .cbz or .mokuro files were found in that folder."
            else -> error("Unmapped string resource id $id in test helper")
        }
        else -> error("Unmapped UiText variant $this in test helper")
    }

private class FakeMangaLibraryRepository(
    private val volumes: List<MangaVolumeEntry> = emptyList(),
    private val coverSourcesById: Map<String, BookCoverSource> = emptyMap(),
    private val progressById: Map<String, Double> = emptyMap(),
    private val openVolumeId: String? = null,
    private val openVolumeError: Throwable? = null,
    private val volumesAfterImport: List<MangaVolumeEntry>? = null,
    private val importError: Throwable? = null,
    private val importFolderResult: List<String>? = null,
) : MangaLibraryRepository {
    val deletedEntryIds = mutableListOf<String>()
    val renamedTitles = mutableListOf<Pair<String, String?>>()
    private var reloadedAfterMutation = false

    override suspend fun loadVolumes(sortOption: MangaSortOption): List<MangaVolumeEntry> =
        if (reloadedAfterMutation) volumesAfterImport ?: volumes else volumes

    override suspend fun coverSourcesById(entries: List<MangaVolumeEntry>): Map<String, BookCoverSource> =
        coverSourcesById

    override suspend fun progressById(entries: List<MangaVolumeEntry>): Map<String, Double> = progressById

    override suspend fun openVolume(entry: MangaVolumeEntry): String {
        openVolumeError?.let { throw it }
        return openVolumeId ?: entry.metadata.id
    }

    override suspend fun importCbz(cbzUri: Uri): String {
        importError?.let { throw it }
        reloadedAfterMutation = true
        return "imported-id"
    }

    override suspend fun importCbz(cbzUri: Uri, mokuroUri: Uri): String {
        importError?.let { throw it }
        reloadedAfterMutation = true
        return "imported-id"
    }

    override suspend fun importFolder(treeUri: Uri): List<String> {
        importError?.let { throw it }
        reloadedAfterMutation = true
        return importFolderResult ?: emptyList()
    }

    override suspend fun deleteVolume(entry: MangaVolumeEntry) {
        deletedEntryIds += entry.metadata.id
        reloadedAfterMutation = true
    }

    override suspend fun renameVolume(entry: MangaVolumeEntry, title: String?) {
        renamedTitles += entry.metadata.id to title
    }
}
