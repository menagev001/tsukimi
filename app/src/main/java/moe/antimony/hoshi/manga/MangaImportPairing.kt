package moe.antimony.hoshi.manga

import moe.antimony.hoshi.importing.ImportDirectoryFile

/**
 * One discovered `.mokuro` file paired with its likely page-image source, from a folder-scan
 * import. `cbzKey` is a same-directory, same-stem `.cbz`/`.zip` file, if present -- the common
 * batch-processing layout (`Volume 1.mokuro` + `Volume 1.cbz` side by side). When no such archive
 * exists, `loosePageFiles` holds every image file found anywhere under the `.mokuro` file's own
 * directory subtree, keyed by path relative to that directory, as a candidate pool for the loose-
 * image import path.
 *
 * This is a heuristic, not a guarantee: `.mokuro` files don't declare a page-image root, so the
 * actual `pages[].imgPath` values (only known once the file is parsed) must still be resolved
 * against this pool leniently at import time -- falling back to a basename match when the
 * relative path doesn't resolve directly, since real-world folder layouts vary.
 */
internal data class MangaImportCandidate<T>(
    val mokuroKey: T,
    val mokuroDisplayName: String,
    val cbzKey: T?,
    val loosePageFiles: Map<String, T>,
)

internal fun <T> pairMangaImportCandidates(
    mokuroFiles: List<ImportDirectoryFile<T>>,
    cbzFiles: List<ImportDirectoryFile<T>>,
    imageFiles: List<ImportDirectoryFile<T>>,
): List<MangaImportCandidate<T>> =
    mokuroFiles.map { mokuro ->
        val mokuroDir = mokuro.displayName.substringBeforeLast('/', "")
        val cbz = cbzFiles.firstOrNull { it.sameDirectoryAndStemAs(mokuro, mokuroDir) }
        val loosePages = if (cbz != null) {
            emptyMap()
        } else {
            imageFiles
                .filter { it.isWithinDirectorySubtree(mokuroDir) }
                .associate { it.relativeTo(mokuroDir) to it.key }
        }
        MangaImportCandidate(
            mokuroKey = mokuro.key,
            mokuroDisplayName = mokuro.displayName,
            cbzKey = cbz?.key,
            loosePageFiles = loosePages,
        )
    }

private fun <T> ImportDirectoryFile<T>.sameDirectoryAndStemAs(
    mokuro: ImportDirectoryFile<T>,
    mokuroDir: String,
): Boolean {
    val candidateDir = displayName.substringBeforeLast('/', "")
    if (candidateDir != mokuroDir) return false
    val candidateStem = displayName.substringAfterLast('/').substringBeforeLast('.', "")
    val mokuroStem = mokuro.displayName.substringAfterLast('/').substringBeforeLast('.', "")
    return candidateStem.isNotBlank() && candidateStem.equals(mokuroStem, ignoreCase = true)
}

private fun <T> ImportDirectoryFile<T>.isWithinDirectorySubtree(directory: String): Boolean =
    if (directory.isBlank()) {
        true
    } else {
        displayName == directory || displayName.startsWith("$directory/")
    }

private fun <T> ImportDirectoryFile<T>.relativeTo(directory: String): String =
    if (directory.isBlank()) displayName else displayName.removePrefix("$directory/")
