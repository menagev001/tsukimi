package moe.antimony.hoshi.manga

import java.nio.file.Files
import moe.antimony.hoshi.importing.ImportFileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaImportDataSourceTest {
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
