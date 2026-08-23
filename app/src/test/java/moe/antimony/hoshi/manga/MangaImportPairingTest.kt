package moe.antimony.hoshi.manga

import moe.antimony.hoshi.importing.ImportDirectoryFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaImportPairingTest {
    @Test
    fun pairsMokuroWithSameDirectorySameStemCbz() {
        val mokuro = ImportDirectoryFile(key = "m1", displayName = "Series/Volume 1.mokuro")
        val cbz = ImportDirectoryFile(key = "c1", displayName = "Series/Volume 1.cbz")
        val unrelatedCbz = ImportDirectoryFile(key = "c2", displayName = "Series/Volume 2.cbz")

        val candidates = pairMangaImportCandidates(
            mokuroFiles = listOf(mokuro),
            cbzFiles = listOf(unrelatedCbz, cbz),
            imageFiles = emptyList(),
        )

        assertEquals(1, candidates.size)
        assertEquals("c1", candidates.single().cbzKey)
        assertTrue(candidates.single().loosePageFiles.isEmpty())
    }

    @Test
    fun cbzMatchIsCaseInsensitiveOnStem() {
        val mokuro = ImportDirectoryFile(key = "m1", displayName = "Volume 1.MOKURO")
        val cbz = ImportDirectoryFile(key = "c1", displayName = "volume 1.cbz")

        val candidates = pairMangaImportCandidates(
            mokuroFiles = listOf(mokuro),
            cbzFiles = listOf(cbz),
            imageFiles = emptyList(),
        )

        assertEquals("c1", candidates.single().cbzKey)
    }

    @Test
    fun doesNotMatchCbzInADifferentDirectory() {
        val mokuro = ImportDirectoryFile(key = "m1", displayName = "Series/Volume 1.mokuro")
        val cbzElsewhere = ImportDirectoryFile(key = "c1", displayName = "Other/Volume 1.cbz")

        val candidates = pairMangaImportCandidates(
            mokuroFiles = listOf(mokuro),
            cbzFiles = listOf(cbzElsewhere),
            imageFiles = emptyList(),
        )

        assertNull(candidates.single().cbzKey)
    }

    @Test
    fun fallsBackToLooseImagesUnderTheMokuroDirectorySubtreeWhenNoCbzMatches() {
        val mokuro = ImportDirectoryFile(key = "m1", displayName = "Series/Volume 1.mokuro")
        val siblingImage = ImportDirectoryFile(key = "i1", displayName = "Series/001.jpg")
        val nestedImage = ImportDirectoryFile(key = "i2", displayName = "Series/Volume 1/002.jpg")
        val unrelatedImage = ImportDirectoryFile(key = "i3", displayName = "Other/003.jpg")

        val candidates = pairMangaImportCandidates(
            mokuroFiles = listOf(mokuro),
            cbzFiles = emptyList(),
            imageFiles = listOf(siblingImage, nestedImage, unrelatedImage),
        )

        val candidate = candidates.single()
        assertNull(candidate.cbzKey)
        assertEquals(
            mapOf("001.jpg" to "i1", "Volume 1/002.jpg" to "i2"),
            candidate.loosePageFiles,
        )
    }

    @Test
    fun loosePagesAtScanRootAreKeyedByTheirDisplayName() {
        val mokuro = ImportDirectoryFile(key = "m1", displayName = "Volume 1.mokuro")
        val image = ImportDirectoryFile(key = "i1", displayName = "001.jpg")

        val candidates = pairMangaImportCandidates(
            mokuroFiles = listOf(mokuro),
            cbzFiles = emptyList(),
            imageFiles = listOf(image),
        )

        assertEquals(mapOf("001.jpg" to "i1"), candidates.single().loosePageFiles)
    }

    @Test
    fun pairsMultipleMokuroFilesIndependently() {
        val mokuroOne = ImportDirectoryFile(key = "m1", displayName = "Volume 1.mokuro")
        val mokuroTwo = ImportDirectoryFile(key = "m2", displayName = "Volume 2.mokuro")
        val cbzOne = ImportDirectoryFile(key = "c1", displayName = "Volume 1.cbz")
        val cbzTwo = ImportDirectoryFile(key = "c2", displayName = "Volume 2.cbz")

        val candidates = pairMangaImportCandidates(
            mokuroFiles = listOf(mokuroOne, mokuroTwo),
            cbzFiles = listOf(cbzOne, cbzTwo),
            imageFiles = emptyList(),
        )

        assertEquals(
            mapOf("m1" to "c1", "m2" to "c2"),
            candidates.associate { it.mokuroKey to it.cbzKey },
        )
    }
}
