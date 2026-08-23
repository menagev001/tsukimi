package moe.antimony.hoshi.navigation

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import moe.antimony.hoshi.manga.MangaBookmark
import moe.antimony.hoshi.manga.MangaRepository
import moe.antimony.hoshi.manga.MangaVolumeMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaReaderRouteStateHolderTest {
    @Test
    fun loadReadyParsesMokuroVolumeAndRestoresBookmark() = runBlocking {
        val repository = MangaRepository(Files.createTempDirectory("hoshi-manga-route").toFile())
        val root = repository.createVolumeDirectoryForImportedTitle("volume-a")
        repository.saveMetadata(
            root,
            MangaVolumeMetadata(id = "volume-a", title = "Volume A", cover = null, folder = "volume-a", lastAccess = 1.0),
        )
        root.resolve("mokuro.json").writeText(
            """{"pages": [{"img_width": 100, "img_height": 200, "img_path": "001.jpg"}]}""",
        )
        val bookmark = MangaBookmark(pageIndex = 3, lastModified = 10.0)
        repository.saveBookmark(root, bookmark)
        val stateHolder = MangaReaderRouteStateHolder(repository)

        val state = stateHolder.load("volume-a")

        assertTrue(state is MangaReaderRouteLoadState.Ready)
        state as MangaReaderRouteLoadState.Ready
        assertEquals(1, state.mokuroVolume.pages.size)
        assertEquals(bookmark, state.bookmark)
        assertEquals("volume-a", state.entry.metadata.id)
    }

    @Test
    fun loadErrorReportsMissingVolume() = runBlocking {
        val repository = MangaRepository(Files.createTempDirectory("hoshi-manga-route-missing").toFile())
        val stateHolder = MangaReaderRouteStateHolder(repository)

        val state = stateHolder.load("missing")

        assertEquals(MangaReaderRouteLoadState.Error("Manga volume not found."), state)
    }

    @Test
    fun loadErrorReportsMissingMokuroSidecar() = runBlocking {
        val repository = MangaRepository(Files.createTempDirectory("hoshi-manga-route-no-mokuro").toFile())
        val root = repository.createVolumeDirectoryForImportedTitle("volume-a")
        repository.saveMetadata(
            root,
            MangaVolumeMetadata(id = "volume-a", title = "Volume A", cover = null, folder = "volume-a", lastAccess = 1.0),
        )
        val stateHolder = MangaReaderRouteStateHolder(repository)

        val state = stateHolder.load("volume-a")

        assertEquals(MangaReaderRouteLoadState.Error("Unable to read this volume's mokuro data."), state)
    }

    @Test
    fun saveBookmarkPersistsPageIndex() = runBlocking {
        val repository = MangaRepository(Files.createTempDirectory("hoshi-manga-route-save").toFile())
        val root = repository.createVolumeDirectoryForImportedTitle("volume-a")
        repository.saveMetadata(
            root,
            MangaVolumeMetadata(id = "volume-a", title = "Volume A", cover = null, folder = "volume-a", lastAccess = 1.0),
        )
        val stateHolder = MangaReaderRouteStateHolder(repository)
        val entry = requireNotNull(repository.loadVolumeEntry("volume-a"))

        stateHolder.saveBookmark(entry, pageIndex = 7)

        assertEquals(7, repository.loadBookmark(root)?.pageIndex)
    }
}
