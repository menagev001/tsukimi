package moe.antimony.hoshi.manga

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import moe.antimony.hoshi.importing.ImportFileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaImportDataSourceTest {
    @Test
    fun importVolumeCombinesSeriesTitleAndVolumeLabelWhenBothPresentAndDistinct() = runBlocking {
        val importDataSource = newImportDataSource()

        val root = importDataSource.importVolume(
            fallbackTitle = "fallback",
            volume = MokuroVolume(title = "All You Need Is Kill", volume = "Volume 01", pages = emptyList()),
            mokuroFile = tempMokuroFile(),
            resolvePage = { _, _ -> false },
        )

        val metadata = MangaSidecarDataSource(Dispatchers.IO).loadMetadata(root)
        assertEquals("All You Need Is Kill (Volume 01)", metadata?.title)
    }

    @Test
    fun importVolumeGivesDistinctVolumesOfTheSameSeriesSeparateFolders() = runBlocking {
        // Real mokuro output sets `title` to the series name, identical across every volume of
        // that series -- importing a second, genuinely different volume must not collide with
        // (and silently no-op on top of) the first one just because their titles match.
        val importDataSource = newImportDataSource()

        val firstRoot = importDataSource.importVolume(
            fallbackTitle = "fallback",
            volume = MokuroVolume(
                title = "20th Century Boys",
                volume = "Volume 01",
                volumeUuid = "uuid-vol-1",
                pages = listOf(MokuroPage(imgWidth = 10, imgHeight = 10, imgPath = "001.jpg", blocks = emptyList())),
            ),
            mokuroFile = tempMokuroFile(),
            resolvePage = { _, destination -> destination.writeBytes(byteArrayOf(1)); true },
        )
        val secondRoot = importDataSource.importVolume(
            fallbackTitle = "fallback",
            volume = MokuroVolume(
                title = "20th Century Boys",
                volume = "Volume 02",
                volumeUuid = "uuid-vol-2",
                pages = listOf(MokuroPage(imgWidth = 10, imgHeight = 10, imgPath = "001.jpg", blocks = emptyList())),
            ),
            mokuroFile = tempMokuroFile(),
            resolvePage = { _, destination -> destination.writeBytes(byteArrayOf(2)); true },
        )

        assertNotEquals(firstRoot.canonicalFile, secondRoot.canonicalFile)
        val sidecar = MangaSidecarDataSource(Dispatchers.IO)
        assertEquals("20th Century Boys (Volume 01)", sidecar.loadMetadata(firstRoot)?.title)
        assertEquals("20th Century Boys (Volume 02)", sidecar.loadMetadata(secondRoot)?.title)
    }

    @Test
    fun importVolumeReimportingTheSameVolumeUuidReusesTheExistingFolder() = runBlocking {
        val importDataSource = newImportDataSource()
        val volume = MokuroVolume(
            title = "20th Century Boys",
            volume = "Volume 01",
            volumeUuid = "uuid-vol-1",
            pages = listOf(MokuroPage(imgWidth = 10, imgHeight = 10, imgPath = "001.jpg", blocks = emptyList())),
        )

        val firstRoot = importDataSource.importVolume(
            fallbackTitle = "fallback",
            volume = volume,
            mokuroFile = tempMokuroFile(volume),
            resolvePage = { _, destination -> destination.writeBytes(byteArrayOf(1)); true },
        )
        val secondRoot = importDataSource.importVolume(
            fallbackTitle = "fallback",
            volume = volume,
            mokuroFile = tempMokuroFile(volume),
            resolvePage = { _, destination -> destination.writeBytes(byteArrayOf(1)); true },
        )

        assertEquals(firstRoot.canonicalFile, secondRoot.canonicalFile)
    }

    @Test
    fun importVolumeUsesBundledCoverImageInsteadOfFirstPage() = runBlocking {
        val importDataSource = newImportDataSource()
        val coverSource = Files.createTempFile("hoshi-manga-cover", ".webp").toFile().apply {
            writeBytes(byteArrayOf(7, 7))
        }

        val root = importDataSource.importVolume(
            fallbackTitle = "fallback",
            volume = MokuroVolume(
                title = "Adolf ni Tsugu",
                volume = "adolf_ni_tsugu_1",
                pages = listOf(MokuroPage(imgWidth = 20, imgHeight = 10, imgPath = "001.webp", blocks = emptyList())),
            ),
            mokuroFile = tempMokuroFile(),
            resolvePage = { _, destination -> destination.writeBytes(byteArrayOf(1)); true },
            coverSource = coverSource,
        )

        val metadata = MangaSidecarDataSource(Dispatchers.IO).loadMetadata(root)
        assertEquals("cover.webp", metadata?.cover)
        assertTrue(root.resolve("cover.webp").readBytes().contentEquals(byteArrayOf(7, 7)))
    }

    @Test
    fun importVolumeFallsBackToFirstPageCoverWithoutBundledCover() = runBlocking {
        val importDataSource = newImportDataSource()

        val root = importDataSource.importVolume(
            fallbackTitle = "fallback",
            volume = MokuroVolume(
                title = "20th Century Boys",
                pages = listOf(
                    MokuroPage(imgWidth = 10, imgHeight = 10, imgPath = "vol/001.webp", blocks = emptyList()),
                    MokuroPage(imgWidth = 10, imgHeight = 10, imgPath = "vol/002.webp", blocks = emptyList()),
                ),
            ),
            mokuroFile = tempMokuroFile(),
            resolvePage = { _, destination -> destination.writeBytes(byteArrayOf(1)); true },
        )

        assertEquals("pages/vol/001.webp", MangaSidecarDataSource(Dispatchers.IO).loadMetadata(root)?.cover)
    }

    private fun newImportDataSource(): MangaImportDataSource {
        val filesDir = Files.createTempDirectory("hoshi-manga-import-volume").toFile()
        return MangaImportDataSource(
            filesDir = filesDir,
            fileDataSource = MangaFileDataSource(filesDir),
        )
    }

    // importVolume copies the mokuroFile bytes verbatim rather than re-serializing `volume`, so
    // for a same-volume-uuid comparison to see anything at all, the file's actual content must
    // match the volume being imported -- an empty placeholder file (fine when a test doesn't
    // round-trip through the copy) would otherwise silently carry no volume_uuid at all.
    private fun tempMokuroFile(volume: MokuroVolume? = null): File =
        Files.createTempFile("hoshi-manga-import-volume-mokuro", ".mokuro").toFile().apply {
            writeText(if (volume != null) Json.encodeToString(MokuroVolume.serializer(), volume) else "{}")
        }

    @Test
    fun resolveSafePageDestinationAllowsSimpleAndNestedRelativePaths() {
        val pagesRoot = Files.createTempDirectory("hoshi-manga-safe-dest").toFile()

        assertEquals(
            pagesRoot.resolve("001.jpg").canonicalFile,
            resolveSafePageDestination(pagesRoot, "001.jpg"),
        )
        assertEquals(
            pagesRoot.resolve("Volume 1/001.jpg").canonicalFile,
            resolveSafePageDestination(pagesRoot, "Volume 1/001.jpg"),
        )
    }

    @Test
    fun resolveSafePageDestinationRejectsPathTraversal() {
        val pagesRoot = Files.createTempDirectory("hoshi-manga-safe-dest-traversal").toFile()

        assertNull(resolveSafePageDestination(pagesRoot, "../secret.jpg"))
        assertNull(resolveSafePageDestination(pagesRoot, "../../etc/passwd"))
        assertNull(resolveSafePageDestination(pagesRoot, "a/../../secret.jpg"))
    }

    @Test
    fun resolveSafePageDestinationRejectsAbsolutePathEscape() {
        val pagesRoot = Files.createTempDirectory("hoshi-manga-safe-dest-absolute").toFile()
        val outside = Files.createTempDirectory("hoshi-manga-outside").toFile()

        assertNull(resolveSafePageDestination(pagesRoot, outside.resolve("secret.jpg").absolutePath))
    }

    @Test
    fun resolveExtractedPageSourcePrefersExactRelativePath() {
        val root = Files.createTempDirectory("hoshi-manga-extract-exact").toFile()
        root.resolve("Volume 1").mkdirs()
        root.resolve("Volume 1/001.jpg").writeBytes(byteArrayOf(1))
        root.resolve("001.jpg").writeBytes(byteArrayOf(2))

        val resolved = resolveExtractedPageSource(root, "Volume 1/001.jpg")

        assertEquals(root.resolve("Volume 1/001.jpg").canonicalFile, resolved)
    }

    @Test
    fun resolveExtractedPageSourceFallsBackToBasenameSearch() {
        val root = Files.createTempDirectory("hoshi-manga-extract-fallback").toFile()
        root.resolve("Nested Wrapper").mkdirs()
        root.resolve("Nested Wrapper/001.jpg").writeBytes(byteArrayOf(1))

        val resolved = resolveExtractedPageSource(root, "Volume 1/001.jpg")

        assertEquals(root.resolve("Nested Wrapper/001.jpg").canonicalFile, resolved)
    }

    @Test
    fun resolveExtractedPageSourceReturnsNullWhenNothingMatches() {
        val root = Files.createTempDirectory("hoshi-manga-extract-missing").toFile()

        assertNull(resolveExtractedPageSource(root, "missing.jpg"))
    }

    @Test
    fun resolveExtractedPageSourceDoesNotEscapeExtractedRootViaTraversal() {
        val root = Files.createTempDirectory("hoshi-manga-extract-traversal").toFile()
        val outside = requireNotNull(root.parentFile).resolve("outside-secret.jpg")
        outside.writeBytes(byteArrayOf(9))

        // Even though a same-named file exists outside the root, the direct traversal attempt
        // itself must not resolve to it -- only the basename-search fallback (scoped to `root`'s
        // own subtree via walkTopDown) may find same-named files, and it won't find this one.
        val resolved = resolveExtractedPageSource(root, "../outside-secret.jpg")

        assertNull(resolved)
        outside.delete()
    }

    @Test
    fun findBundledMokuroFilePrefersRootLevelEntryOverANestedOne() {
        // Matches the real layout confirmed against actual mokuro-processed CBZ downloads: the
        // .mokuro file sits at the archive root alongside a page subfolder and a cover image.
        val root = Files.createTempDirectory("hoshi-manga-bundled-mokuro").toFile()
        root.resolve("Volume 1").mkdirs()
        root.resolve("Volume 1/nested.mokuro").writeText("{}")
        root.resolve("Volume 1.mokuro").writeText("{}")

        val found = findBundledMokuroFile(root)

        assertEquals(root.resolve("Volume 1.mokuro").canonicalFile, found)
    }

    @Test
    fun findBundledMokuroFileIsCaseInsensitiveOnExtension() {
        val root = Files.createTempDirectory("hoshi-manga-bundled-mokuro-case").toFile()
        root.resolve("Volume 1.MOKURO").writeText("{}")

        assertEquals(root.resolve("Volume 1.MOKURO").canonicalFile, findBundledMokuroFile(root))
    }

    @Test
    fun findBundledMokuroFileReturnsNullWhenNoneExists() {
        val root = Files.createTempDirectory("hoshi-manga-bundled-mokuro-missing").toFile()
        root.resolve("001.jpg").writeText("not a mokuro file")

        assertNull(findBundledMokuroFile(root))
    }

    @Test
    fun findBundledCoverImageFindsVolumeNamedImageBesidePageFolder() {
        // Real mokuro CBZ layout: "<volume>/" pages, "<volume>.mokuro" and a "<volume>.webp" thumbnail.
        val root = Files.createTempDirectory("hoshi-manga-bundled-cover").toFile()
        root.resolve("adolf_ni_tsugu_1").mkdirs()
        root.resolve("adolf_ni_tsugu_1/001.webp").writeBytes(byteArrayOf(1))
        root.resolve("adolf_ni_tsugu_1.mokuro").writeText("{}")
        root.resolve("adolf_ni_tsugu_1.webp").writeBytes(byteArrayOf(2))
        root.resolve("series.json").writeText("{}")

        assertEquals(
            root.resolve("adolf_ni_tsugu_1.webp").canonicalFile,
            findBundledCoverImage(root, setOf("adolf_ni_tsugu_1"))?.canonicalFile,
        )
    }

    @Test
    fun findBundledCoverImageIgnoresPagesAndNonImages() {
        val root = Files.createTempDirectory("hoshi-manga-bundled-cover-missing").toFile()
        root.resolve("Volume 1").mkdirs()
        root.resolve("Volume 1/001.jpg").writeBytes(byteArrayOf(1))
        root.resolve("Volume 1.mokuro").writeText("{}")

        assertNull(findBundledCoverImage(root, setOf("Volume 1")))
        assertNull(findBundledCoverImage(root, emptySet()))
    }

    @Test
    fun mangaCbzImportFileTypeMatchesCbzAndZipExtensions() {
        assertTrue(ImportFileType.MangaCbz.matchesDisplayName("Volume 1.cbz"))
        assertTrue(ImportFileType.MangaCbz.matchesDisplayName("Volume 1.zip"))
        assertEquals(false, ImportFileType.MangaCbz.matchesDisplayName("Volume 1.mokuro"))
    }

    @Test
    fun mokuroSidecarImportFileTypeMatchesOnlyMokuroExtension() {
        assertTrue(ImportFileType.MokuroSidecar.matchesDisplayName("Volume 1.mokuro"))
        assertEquals(false, ImportFileType.MokuroSidecar.matchesDisplayName("Volume 1.json"))
    }
}
