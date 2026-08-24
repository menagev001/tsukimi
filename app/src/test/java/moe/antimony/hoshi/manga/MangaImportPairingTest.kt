package moe.antimony.hoshi.manga

import moe.antimony.hoshi.importing.ImportDirectoryFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaImportPairingTest {
    @Test
    fun cbzWithNoSameStemMokuroSiblingIsTreatedAsSelfContained() {
        // The common real-world case: a mokuro-processed CBZ download bundles its own .mokuro
        // file as a root-level zip entry, so no separate sidecar file exists on disk to pair
        // against -- confirmed against real sample volumes (see MokuroParserTest's fixture).
        val cbz = ImportDirectoryFile(key = "c1", displayName = "Series/Volume 1.cbz")

        val candidates = pairMangaImportCandidates(
            mokuroFiles = emptyList(),
            cbzFiles = listOf(cbz),
            imageFiles = emptyList(),
        )

        val candidate = candidates.single()
        assertTrue(candidate is MangaImportCandidate.SelfContainedCbz)
        assertEquals("c1", (candidate as MangaImportCandidate.SelfContainedCbz).cbzKey)
    }

    @Test
    fun pairsCbzWithSameDirectorySameStemExternalMokuro() {
        val mokuro = ImportDirectoryFile(key = "m1", displayName = "Series/Volume 1.mokuro")
        val cbz = ImportDirectoryFile(key = "c1", displayName = "Series/Volume 1.cbz")
        val unrelatedMokuro = ImportDirectoryFile(key = "m2", displayName = "Series/Volume 2.mokuro")

        val candidates = pairMangaImportCandidates(
            mokuroFiles = listOf(mokuro, unrelatedMokuro),
            cbzFiles = listOf(cbz),
            imageFiles = emptyList(),
        )

        val cbzCandidate = candidates.filterIsInstance<MangaImportCandidate.CbzWithExternalMokuro<String>>().single()
        assertEquals("c1", cbzCandidate.cbzKey)
        assertEquals("m1", cbzCandidate.mokuroKey)
        // The unrelated mokuro (different stem) isn't consumed by the pairing -- it surfaces as
        // its own loose-mokuro candidate instead.
        val looseCandidate = candidates.filterIsInstance<MangaImportCandidate.LooseMokuroWithImages<String>>().single()
        assertEquals("m2", looseCandidate.mokuroKey)
    }

    @Test
    fun externalMokuroMatchIsCaseInsensitiveOnStem() {
        val mokuro = ImportDirectoryFile(key = "m1", displayName = "Volume 1.MOKURO")
        val cbz = ImportDirectoryFile(key = "c1", displayName = "volume 1.cbz")

        val candidates = pairMangaImportCandidates(
            mokuroFiles = listOf(mokuro),
            cbzFiles = listOf(cbz),
            imageFiles = emptyList(),
        )

        val cbzCandidate = candidates.single() as MangaImportCandidate.CbzWithExternalMokuro<String>
        assertEquals("m1", cbzCandidate.mokuroKey)
    }

    @Test
    fun doesNotPairMokuroInADifferentDirectoryWithACbz() {
        val mokuro = ImportDirectoryFile(key = "m1", displayName = "Other/Volume 1.mokuro")
        val cbz = ImportDirectoryFile(key = "c1", displayName = "Series/Volume 1.cbz")

        val candidates = pairMangaImportCandidates(
            mokuroFiles = listOf(mokuro),
            cbzFiles = listOf(cbz),
            imageFiles = emptyList(),
        )

        assertTrue(candidates.any { it is MangaImportCandidate.SelfContainedCbz })
        assertTrue(candidates.any { it is MangaImportCandidate.LooseMokuroWithImages })
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

        val candidate = candidates.single() as MangaImportCandidate.LooseMokuroWithImages<String>
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

        val candidate = candidates.single() as MangaImportCandidate.LooseMokuroWithImages<String>
        assertEquals(mapOf("001.jpg" to "i1"), candidate.loosePageFiles)
    }

    @Test
    fun pairsMultipleCbzFilesIndependently() {
        val cbzOne = ImportDirectoryFile(key = "c1", displayName = "Volume 1.cbz")
        val cbzTwo = ImportDirectoryFile(key = "c2", displayName = "Volume 2.cbz")
        val mokuroTwo = ImportDirectoryFile(key = "m2", displayName = "Volume 2.mokuro")

        val candidates = pairMangaImportCandidates(
            mokuroFiles = listOf(mokuroTwo),
            cbzFiles = listOf(cbzOne, cbzTwo),
            imageFiles = emptyList(),
        )

        assertEquals(2, candidates.size)
        assertTrue(candidates.any { it is MangaImportCandidate.SelfContainedCbz && it.cbzKey == "c1" })
        assertTrue(
            candidates.any {
                it is MangaImportCandidate.CbzWithExternalMokuro && it.cbzKey == "c2" && it.mokuroKey == "m2"
            },
        )
    }
}
