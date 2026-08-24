package moe.antimony.hoshi.manga

import moe.antimony.hoshi.importing.ImportDirectoryFile

/**
 * One discovered manga volume candidate from a folder-scan import. The real-world common case
 * (confirmed against actual mokuro-processed CBZ downloads) is [SelfContainedCbz]: the `.mokuro`
 * JSON is already bundled as a root-level entry inside the CBZ alongside the page subfolder and
 * a cover image -- this is also the single-file picker's only case (see
 * `MangaImportDataSource.importCbz(contentResolver, cbzUri)`). [CbzWithExternalMokuro] and
 * [LooseMokuroWithImages] exist for less common layouts where the sidecar sits next to the
 * archive/images as a separate file instead.
 */
internal sealed interface MangaImportCandidate<T> {
    data class SelfContainedCbz<T>(
        val cbzKey: T,
        val cbzDisplayName: String,
    ) : MangaImportCandidate<T>

    data class CbzWithExternalMokuro<T>(
        val cbzKey: T,
        val mokuroKey: T,
        val mokuroDisplayName: String,
    ) : MangaImportCandidate<T>

    data class LooseMokuroWithImages<T>(
        val mokuroKey: T,
        val mokuroDisplayName: String,
        val loosePageFiles: Map<String, T>,
    ) : MangaImportCandidate<T>
}

internal fun <T> pairMangaImportCandidates(
    mokuroFiles: List<ImportDirectoryFile<T>>,
    cbzFiles: List<ImportDirectoryFile<T>>,
    imageFiles: List<ImportDirectoryFile<T>>,
): List<MangaImportCandidate<T>> {
    val consumedMokuroKeys = mutableSetOf<T>()

    val cbzCandidates = cbzFiles.map { cbz ->
        val externalMokuro = mokuroFiles.firstOrNull { it.sameDirectoryAndStemAs(cbz) }
        if (externalMokuro != null) {
            consumedMokuroKeys += externalMokuro.key
            MangaImportCandidate.CbzWithExternalMokuro(
                cbzKey = cbz.key,
                mokuroKey = externalMokuro.key,
                mokuroDisplayName = externalMokuro.displayName,
            )
        } else {
            // No same-directory same-stem .mokuro sibling -- treat as a candidate whose
            // .mokuro is bundled inside the archive itself; probed for real at import time.
            MangaImportCandidate.SelfContainedCbz(cbzKey = cbz.key, cbzDisplayName = cbz.displayName)
        }
    }

    val looseMokuroCandidates = mokuroFiles
        .filter { it.key !in consumedMokuroKeys }
        .map { mokuro ->
            val mokuroDir = mokuro.displayName.substringBeforeLast('/', "")
            val loosePages = imageFiles
                .filter { it.isWithinDirectorySubtree(mokuroDir) }
                .associate { it.relativeTo(mokuroDir) to it.key }
            MangaImportCandidate.LooseMokuroWithImages(
                mokuroKey = mokuro.key,
                mokuroDisplayName = mokuro.displayName,
                loosePageFiles = loosePages,
            )
        }

    return cbzCandidates + looseMokuroCandidates
}

private fun <T> ImportDirectoryFile<T>.sameDirectoryAndStemAs(other: ImportDirectoryFile<T>): Boolean {
    val thisDir = displayName.substringBeforeLast('/', "")
    val otherDir = other.displayName.substringBeforeLast('/', "")
    if (thisDir != otherDir) return false
    val thisStem = displayName.substringAfterLast('/').substringBeforeLast('.', "")
    val otherStem = other.displayName.substringAfterLast('/').substringBeforeLast('.', "")
    return thisStem.isNotBlank() && thisStem.equals(otherStem, ignoreCase = true)
}

private fun <T> ImportDirectoryFile<T>.isWithinDirectorySubtree(directory: String): Boolean =
    if (directory.isBlank()) {
        true
    } else {
        displayName == directory || displayName.startsWith("$directory/")
    }

private fun <T> ImportDirectoryFile<T>.relativeTo(directory: String): String =
    if (directory.isBlank()) displayName else displayName.removePrefix("$directory/")
