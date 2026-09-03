package moe.antimony.hoshi.manga

import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.runBlocking
import moe.antimony.hoshi.epub.HighlightColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaRepositoryTest {
    @Test
    fun savesAndLoadsMetadata() = runBlocking {
        val repository = MangaRepository(Files.createTempDirectory("hoshi-manga-metadata").toFile())
        val root = repository.createVolumeDirectoryForImportedTitle("volume-a")
        val id = UUID.randomUUID().toString()
        val metadata = MangaVolumeMetadata(
            id = id,
            title = "Example Volume",
            cover = "pages/001.jpg",
            folder = "volume-a",
            lastAccess = 10.0,
            mokuro = "mokuro.json",
            pageCount = 42,
        )

        repository.saveMetadata(root, metadata)

        assertEquals(metadata, repository.loadMetadata(root))
    }

    @Test
    fun loadVolumeEntriesSortsByLastAccessDescendingByDefault() = runBlocking {
        val repository = MangaRepository(Files.createTempDirectory("hoshi-manga-sort-recent").toFile())
        val olderRoot = repository.createVolumeDirectoryForImportedTitle("older")
        val newerRoot = repository.createVolumeDirectoryForImportedTitle("newer")
        repository.saveMetadata(
            olderRoot,
            MangaVolumeMetadata(id = "older-id", title = "Older", cover = null, folder = "older", lastAccess = 10.0),
        )
        repository.saveMetadata(
            newerRoot,
            MangaVolumeMetadata(id = "newer-id", title = "Newer", cover = null, folder = "newer", lastAccess = 20.0),
        )

        val entries = repository.loadVolumeEntries()

        assertEquals(listOf("newer-id", "older-id"), entries.map { it.metadata.id })
    }

    @Test
    fun loadVolumeEntriesCanSortByTitle() = runBlocking {
        val repository = MangaRepository(Files.createTempDirectory("hoshi-manga-sort-title").toFile())
        val zRoot = repository.createVolumeDirectoryForImportedTitle("z")
        val aRoot = repository.createVolumeDirectoryForImportedTitle("a")
        repository.saveMetadata(
            zRoot,
            MangaVolumeMetadata(id = "z-id", title = "Zeta", cover = null, folder = "z", lastAccess = 20.0),
        )
        repository.saveMetadata(
            aRoot,
            MangaVolumeMetadata(id = "a-id", title = "Alpha", cover = null, folder = "a", lastAccess = 10.0),
        )

        val entries = repository.loadVolumeEntries(MangaSortOption.Title)

        assertEquals(listOf("Alpha", "Zeta"), entries.map { it.metadata.title })
    }

    @Test
    fun loadVolumeEntryFindsVolumeByStableMetadataId() = runBlocking {
        val repository = MangaRepository(Files.createTempDirectory("hoshi-manga-find-id").toFile())
        val root = repository.createVolumeDirectoryForImportedTitle("volume")
        val id = UUID.randomUUID().toString()
        repository.saveMetadata(
            root,
            MangaVolumeMetadata(id = id, title = "Volume", cover = null, folder = "volume", lastAccess = 1.0),
        )

        val entry = repository.loadVolumeEntry(id)

        assertEquals(root.canonicalFile, entry?.root?.canonicalFile)
    }

    @Test
    fun savesAndLoadsBookmark() = runBlocking {
        val repository = MangaRepository(Files.createTempDirectory("hoshi-manga-bookmark").toFile())
        val root = repository.createVolumeDirectoryForImportedTitle("volume")

        repository.saveBookmark(root, MangaBookmark(pageIndex = 7, lastModified = 5.0))

        assertEquals(MangaBookmark(pageIndex = 7, lastModified = 5.0), repository.loadBookmark(root))
    }

    @Test
    fun loadReadingStatsDefaultsToZeroWhenNeverSaved() = runBlocking {
        val repository = MangaRepository(Files.createTempDirectory("hoshi-manga-stats-default").toFile())
        val root = repository.createVolumeDirectoryForImportedTitle("volume")

        assertEquals(MangaReadingStats(totalSeconds = 0.0), repository.loadReadingStats(root))
    }

    @Test
    fun savesAndLoadsReadingStats() = runBlocking {
        val repository = MangaRepository(Files.createTempDirectory("hoshi-manga-stats").toFile())
        val root = repository.createVolumeDirectoryForImportedTitle("volume")

        repository.saveReadingStats(root, MangaReadingStats(totalSeconds = 125.5))

        assertEquals(MangaReadingStats(totalSeconds = 125.5), repository.loadReadingStats(root))
    }

    @Test
    fun savesAndLoadsHighlights() = runBlocking {
        val repository = MangaRepository(Files.createTempDirectory("hoshi-manga-highlights").toFile())
        val root = repository.createVolumeDirectoryForImportedTitle("volume")
        val highlights = listOf(
            MangaHighlight(
                id = UUID.randomUUID().toString(),
                pageIndex = 3,
                blockIndex = 1,
                lineIndex = 0,
                text = "食べる",
                color = HighlightColor.Blue,
                createdAt = 12.5,
            ),
        )

        repository.saveHighlights(root, highlights)

        assertEquals(highlights, repository.loadHighlights(root))
    }

    @Test
    fun loadMokuroVolumeParsesRawSidecarCopy() = runBlocking {
        val repository = MangaRepository(Files.createTempDirectory("hoshi-manga-mokuro").toFile())
        val root = repository.createVolumeDirectoryForImportedTitle("volume")
        root.resolve(MOKURO_FILE_NAME).writeText(
            """{"pages": [{"img_width": 10, "img_height": 20, "img_path": "001.jpg"}]}""",
        )

        val volume = repository.loadMokuroVolume(root)

        assertEquals(1, volume?.pages?.size)
    }

    @Test
    fun loadMokuroVolumeReturnsNullWhenSidecarMissing() = runBlocking {
        val repository = MangaRepository(Files.createTempDirectory("hoshi-manga-mokuro-missing").toFile())
        val root = repository.createVolumeDirectoryForImportedTitle("volume")

        assertNull(repository.loadMokuroVolume(root))
    }

    @Test
    fun pageFileRejectsPathTraversalOutsidePagesDirectory() = runBlocking {
        val repository = MangaRepository(Files.createTempDirectory("hoshi-manga-traversal").toFile())
        val root = repository.createVolumeDirectoryForImportedTitle("volume")
        root.resolve("pages").mkdirs()
        root.resolve("pages/001.jpg").writeBytes(byteArrayOf(1))
        root.resolve("secret.txt").writeText("do not serve me")

        assertTrue(repository.pageFile(root, "001.jpg") != null)
        assertNull(repository.pageFile(root, "../secret.txt"))
        assertNull(repository.pageFile(root, "../../secret.txt"))
    }

    @Test
    fun importedVolumeDirectoryNameSanitizesAndDedupes() = runBlocking {
        val repository = MangaRepository(Files.createTempDirectory("hoshi-manga-dedupe").toFile())

        val first = repository.createVolumeDirectoryForImportedTitle("巻/一:話")
        first.resolve("metadata.json").writeText("{}")
        val second = repository.createVolumeDirectoryForImportedTitle("巻/一:話")

        assertEquals("巻_一_話", first.name)
        assertEquals(first.canonicalFile, second.canonicalFile)
    }

    @Test
    fun deleteVolumeRemovesVolumeDirectory() = runBlocking {
        val repository = MangaRepository(Files.createTempDirectory("hoshi-manga-delete").toFile())
        val root = repository.createVolumeDirectoryForImportedTitle("delete-me")
        root.resolve("metadata.json").writeText("{}")

        repository.deleteVolume(root)

        assertFalse(root.exists())
        assertEquals(emptyList<MangaVolumeEntry>(), repository.loadVolumeEntries())
    }
}
