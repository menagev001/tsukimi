package moe.antimony.hoshi.manga

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.antimony.hoshi.epub.EpubArchiveExtractor
import moe.antimony.hoshi.importing.ImportFileType
import moe.antimony.hoshi.importing.validateImportFile

/**
 * Owns copying a manga volume (CBZ or folder of page images, plus a `.mokuro` sidecar) into app
 * storage, mirroring `moe.antimony.hoshi.epub.BookImportDataSource`'s scope: file copy + naming
 * only, no profile/language detection (that belongs a layer up, same as EPUB import).
 *
 * Pages are extracted once at import time and kept extracted -- unlike EPUB, which keeps its
 * packed archive for iOS-backup-compatibility reasons that don't apply here, repeatedly
 * decompressing a CBZ while flipping pages would hurt e-ink/low-end read performance. The raw
 * `.mokuro` JSON is kept as a verbatim byte copy, never re-serialized.
 *
 * The common real-world case (confirmed against real mokuro-processed CBZ downloads) is a single
 * self-contained `.cbz` that already bundles its own `.mokuro` file as a root-level zip entry
 * alongside the page subfolder and a cover image -- [importCbz] (single-`Uri` overload) handles
 * this and is the primary single-file import path. The two-`Uri` overload and the folder-scan
 * path (`importCandidate`) exist for the less common case where the sidecar is a separate file.
 */
class MangaImportDataSource(
    private val filesDir: File,
    private val fileDataSource: MangaFileDataSource,
    private val archiveExtractor: EpubArchiveExtractor = EpubArchiveExtractor(),
    private val clock: MangaClock = SystemMangaClock,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /** Self-contained CBZ: its `.mokuro` sidecar is bundled inside the archive itself. */
    suspend fun importCbz(contentResolver: ContentResolver, cbzUri: Uri): File = withContext(ioDispatcher) {
        val cbzDisplayName = contentResolver.validateImportFile(cbzUri, ImportFileType.MangaCbz)
        val importRoot = File(filesDir, "ImportTemp/${UUID.randomUUID()}").canonicalFile
        val archiveFile = importRoot.resolve("source.cbz").canonicalFile
        val extractedRoot = importRoot.resolve("extracted").canonicalFile
        try {
            importRoot.mkdirs()
            contentResolver.copyUriTo(cbzUri, archiveFile)
            archiveExtractor.extract(archiveFile, extractedRoot)
            val mokuroFile = findBundledMokuroFile(extractedRoot)
                ?: throw IllegalArgumentException(
                    "This .cbz does not contain a .mokuro file. Use folder import instead if it's a separate file.",
                )
            val volume = parseOrThrow(mokuroFile)
            importVolume(
                fallbackTitle = cbzDisplayName.substringBeforeLast('.', cbzDisplayName),
                volume = volume,
                mokuroFile = mokuroFile,
                resolvePage = { imgPath, destination -> copyExtractedPage(extractedRoot, imgPath, destination) },
                coverSource = findBundledCoverImage(extractedRoot, setOfNotNull(mokuroFile.nameWithoutExtension, volume.volume)),
            )
        } finally {
            importRoot.deleteRecursively()
        }
    }

    /** CBZ paired with a separate `.mokuro` sidecar file, picked (or discovered) independently. */
    suspend fun importCbz(contentResolver: ContentResolver, cbzUri: Uri, mokuroUri: Uri): File =
        withContext(ioDispatcher) {
            val cbzDisplayName = contentResolver.validateImportFile(cbzUri, ImportFileType.MangaCbz)
            contentResolver.validateImportFile(mokuroUri, ImportFileType.MokuroSidecar)

            val importRoot = File(filesDir, "ImportTemp/${UUID.randomUUID()}").canonicalFile
            val mokuroFile = importRoot.resolve("source.mokuro").canonicalFile
            val archiveFile = importRoot.resolve("source.cbz").canonicalFile
            val extractedRoot = importRoot.resolve("extracted").canonicalFile
            try {
                importRoot.mkdirs()
                contentResolver.copyUriTo(mokuroUri, mokuroFile)
                contentResolver.copyUriTo(cbzUri, archiveFile)
                archiveExtractor.extract(archiveFile, extractedRoot)

                val volume = parseOrThrow(mokuroFile)
                importVolume(
                    fallbackTitle = cbzDisplayName.substringBeforeLast('.', cbzDisplayName),
                    volume = volume,
                    mokuroFile = mokuroFile,
                    resolvePage = { imgPath, destination -> copyExtractedPage(extractedRoot, imgPath, destination) },
                    coverSource = findBundledCoverImage(extractedRoot, setOfNotNull(volume.volume)),
                )
            } finally {
                importRoot.deleteRecursively()
            }
        }

    internal suspend fun importCandidate(
        contentResolver: ContentResolver,
        candidate: MangaImportCandidate<Uri>,
    ): File = when (candidate) {
        is MangaImportCandidate.SelfContainedCbz -> importCbz(contentResolver, candidate.cbzKey)
        is MangaImportCandidate.CbzWithExternalMokuro ->
            importCbz(contentResolver, candidate.cbzKey, candidate.mokuroKey)
        is MangaImportCandidate.LooseMokuroWithImages -> importLooseMokuroWithImages(contentResolver, candidate)
    }

    private suspend fun importLooseMokuroWithImages(
        contentResolver: ContentResolver,
        candidate: MangaImportCandidate.LooseMokuroWithImages<Uri>,
    ): File = withContext(ioDispatcher) {
        val importRoot = File(filesDir, "ImportTemp/${UUID.randomUUID()}").canonicalFile
        val mokuroFile = importRoot.resolve("source.mokuro").canonicalFile
        try {
            importRoot.mkdirs()
            contentResolver.copyUriTo(candidate.mokuroKey, mokuroFile)
            val volume = parseOrThrow(mokuroFile)
            val fallbackTitle = candidate.mokuroDisplayName
                .substringAfterLast('/')
                .substringBeforeLast('.', candidate.mokuroDisplayName)
            importVolume(
                fallbackTitle = fallbackTitle,
                volume = volume,
                mokuroFile = mokuroFile,
                resolvePage = { imgPath, destination ->
                    copyLoosePage(contentResolver, candidate.loosePageFiles, imgPath, destination)
                },
            )
        } finally {
            importRoot.deleteRecursively()
        }
    }

    private fun parseOrThrow(mokuroFile: File): MokuroVolume =
        runCatching { MokuroParser.parse(mokuroFile) }.getOrElse {
            throw IllegalArgumentException("Unable to parse .mokuro sidecar", it)
        }

    /**
     * Whether [existingRoot] (a previously-imported volume whose folder collided with the one
     * being imported now) is the *same* volume, not just a different volume sharing a title.
     * When either side lacks a `volume_uuid` (mokuro doesn't always emit one), this can't be
     * confirmed -- treated as "not the same volume" rather than risk silently dropping a
     * genuinely different import, since that's the far more common and far more confusing
     * failure of the two.
     */
    private fun isSameVolumeAlreadyImported(existingRoot: File, volumeUuid: String?): Boolean {
        if (volumeUuid == null) return false
        val existingMokuro = existingRoot.resolve(MOKURO_FILE_NAME).takeIf { it.isFile } ?: return false
        val existingVolumeUuid = runCatching { MokuroParser.parse(existingMokuro) }.getOrNull()?.volumeUuid
        return existingVolumeUuid != null && existingVolumeUuid == volumeUuid
    }

    internal suspend fun importVolume(
        fallbackTitle: String,
        volume: MokuroVolume,
        mokuroFile: File,
        resolvePage: (imgPath: String, destination: File) -> Boolean,
        coverSource: File? = null,
    ): File {
        // Real mokuro output sets `title` to the *series* name, shared verbatim across every
        // volume of that series (confirmed against a real sample: "All You Need Is Kill" /
        // "オール・ユー・ニード・イズ・キル 第01巻") -- using it alone would give every volume of
        // the same series an identical displayed title, and identical sanitized folder names.
        val seriesTitle = volume.title?.takeIf { it.isNotBlank() }
        val volumeLabel = volume.volume?.takeIf { it.isNotBlank() }
        val title = when {
            seriesTitle != null && volumeLabel != null && volumeLabel != seriesTitle -> "$seriesTitle ($volumeLabel)"
            seriesTitle != null -> seriesTitle
            volumeLabel != null -> volumeLabel
            else -> fallbackTitle
        }

        var targetRoot = fileDataSource.createVolumeDirectoryForImportedTitle(title)
        var suffix = 2
        while (targetRoot.listFiles()?.isNotEmpty() == true) {
            if (isSameVolumeAlreadyImported(targetRoot, volume.volumeUuid)) {
                // Genuinely a re-import of the same volume (matched by its stable volume_uuid,
                // when mokuro provided one) -- leave the existing copy as-is.
                return targetRoot
            }
            // A different volume landed on the same sanitized title -- most commonly another
            // volume of the same series -- so claim the next available folder instead of
            // colliding with (and silently no-oping on top of) what's already there.
            targetRoot = fileDataSource.createVolumeDirectoryForImportedTitle("$title (${suffix++})")
        }

        targetRoot.mkdirs()
        val pagesRoot = targetRoot.resolve(PAGES_DIRECTORY_NAME).canonicalFile
        pagesRoot.mkdirs()
        var firstPageRelativePath: String? = null
        for (page in volume.pages) {
            val destination = resolveSafePageDestination(pagesRoot, page.imgPath) ?: continue
            destination.parentFile?.mkdirs()
            if (!resolvePage(page.imgPath, destination)) continue
            if (firstPageRelativePath == null) {
                firstPageRelativePath = destination.toRelativeString(pagesRoot)
            }
        }

        mokuroFile.copyTo(targetRoot.resolve(MOKURO_FILE_NAME))
        val coverRelativePath = coverSource?.let { source ->
            val name = "$COVER_FILE_STEM.${source.extension.lowercase()}"
            source.copyTo(targetRoot.resolve(name), overwrite = true)
            name
        } ?: firstPageRelativePath?.let { "$PAGES_DIRECTORY_NAME/$it" }

        val metadata = MangaVolumeMetadata(
            id = UUID.randomUUID().toString(),
            title = title,
            cover = coverRelativePath,
            folder = targetRoot.name,
            lastAccess = clock.currentAppleReferenceDateSeconds(),
            mokuro = MOKURO_FILE_NAME,
            pageCount = volume.pages.size,
            seriesTitle = seriesTitle,
        )
        MangaSidecarDataSource(ioDispatcher).saveMetadata(targetRoot, metadata)
        return targetRoot
    }

    private fun copyExtractedPage(extractedRoot: File, imgPath: String, destination: File): Boolean {
        val source = resolveExtractedPageSource(extractedRoot, imgPath) ?: return false
        source.copyTo(destination, overwrite = true)
        return true
    }

    private fun copyLoosePage(
        contentResolver: ContentResolver,
        loosePageFiles: Map<String, Uri>,
        imgPath: String,
        destination: File,
    ): Boolean {
        val basename = imgPath.substringAfterLast('/')
        val uri = loosePageFiles[imgPath]
            ?: loosePageFiles.entries.firstOrNull { it.key.substringAfterLast('/') == basename }?.value
            ?: return false
        contentResolver.copyUriTo(uri, destination)
        return true
    }

    private fun ContentResolver.copyUriTo(uri: Uri, destination: File) {
        openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected file" }
            destination.outputStream().use { output -> input.copyTo(output) }
        }
    }
}

/**
 * Finds the `.mokuro` entry bundled inside an extracted CBZ. Real mokuro-processed CBZ downloads
 * keep it at the archive root (alongside a page subfolder and cover image); a recursive search is
 * used to stay lenient about layout variance rather than assuming root-only placement.
 */
internal fun findBundledMokuroFile(extractedRoot: File): File? =
    extractedRoot.walkTopDown()
        .filter { it.isFile && it.extension.equals("mokuro", ignoreCase = true) }
        .minByOrNull { it.toRelativeString(extractedRoot).count { c -> c == File.separatorChar } }

/**
 * Finds the volume thumbnail mokuro bundles next to its page folder: an image named after the
 * volume (`<volume>.webp` beside `<volume>/` and `<volume>.mokuro`). Preferred over the first page
 * as the shelf cover because the first page is often a two-page spread, which the portrait cover
 * slot crops down to its middle.
 */
internal fun findBundledCoverImage(extractedRoot: File, volumeNames: Set<String>): File? {
    if (volumeNames.isEmpty()) return null
    return extractedRoot.walkTopDown()
        .filter { file ->
            file.isFile &&
                ImportFileType.MangaPageImage.matchesDisplayName(file.name) &&
                volumeNames.any { it.equals(file.nameWithoutExtension, ignoreCase = true) }
        }
        .minByOrNull { it.toRelativeString(extractedRoot).count { c -> c == File.separatorChar } }
}

/**
 * Guards a page destination path derived from a `.mokuro` file's untrusted `img_path` value.
 * Unlike zip entry names (already validated by `EpubArchiveExtractor.extract`), `img_path` comes
 * from external JSON and must be checked independently before it's used to build a file path.
 */
internal fun resolveSafePageDestination(pagesRoot: File, imgPath: String): File? {
    val root = pagesRoot.canonicalFile
    val destination = root.resolve(imgPath).canonicalFile
    return if (destination.path == root.path || destination.path.startsWith(root.path + File.separator)) {
        destination
    } else {
        null
    }
}

/**
 * Resolves a page's source file inside an extracted CBZ, preferring an exact relative-path match
 * and falling back to a basename search -- real-world CBZ layouts don't always match `img_path`'s
 * directory prefix exactly (e.g. a nested "Volume 1/" wrapper folder inside the archive).
 */
internal fun resolveExtractedPageSource(extractedRoot: File, imgPath: String): File? {
    val root = extractedRoot.canonicalFile
    val direct = root.resolve(imgPath).canonicalFile
    if ((direct.path == root.path || direct.path.startsWith(root.path + File.separator)) && direct.isFile) {
        return direct
    }
    val basename = imgPath.substringAfterLast('/')
    return root.walkTopDown().firstOrNull { it.isFile && it.name == basename }
}
