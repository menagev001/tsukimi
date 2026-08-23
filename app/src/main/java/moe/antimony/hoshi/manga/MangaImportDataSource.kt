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
 */
class MangaImportDataSource(
    private val filesDir: File,
    private val fileDataSource: MangaFileDataSource,
    private val archiveExtractor: EpubArchiveExtractor = EpubArchiveExtractor(),
    private val clock: MangaClock = SystemMangaClock,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
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
                )
            } finally {
                importRoot.deleteRecursively()
            }
        }

    internal suspend fun importFolderCandidate(
        contentResolver: ContentResolver,
        candidate: MangaImportCandidate<Uri>,
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

            val cbzKey = candidate.cbzKey
            if (cbzKey != null) {
                val archiveFile = importRoot.resolve("source.cbz").canonicalFile
                val extractedRoot = importRoot.resolve("extracted").canonicalFile
                contentResolver.copyUriTo(cbzKey, archiveFile)
                archiveExtractor.extract(archiveFile, extractedRoot)
                importVolume(
                    fallbackTitle = fallbackTitle,
                    volume = volume,
                    mokuroFile = mokuroFile,
                    resolvePage = { imgPath, destination -> copyExtractedPage(extractedRoot, imgPath, destination) },
                )
            } else {
                importVolume(
                    fallbackTitle = fallbackTitle,
                    volume = volume,
                    mokuroFile = mokuroFile,
                    resolvePage = { imgPath, destination ->
                        copyLoosePage(contentResolver, candidate.loosePageFiles, imgPath, destination)
                    },
                )
            }
        } finally {
            importRoot.deleteRecursively()
        }
    }

    private fun parseOrThrow(mokuroFile: File): MokuroVolume =
        runCatching { MokuroParser.parse(mokuroFile) }.getOrElse {
            throw IllegalArgumentException("Unable to parse .mokuro sidecar", it)
        }

    private suspend fun importVolume(
        fallbackTitle: String,
        volume: MokuroVolume,
        mokuroFile: File,
        resolvePage: (imgPath: String, destination: File) -> Boolean,
    ): File {
        val title = volume.title?.takeIf { it.isNotBlank() }
            ?: volume.volume?.takeIf { it.isNotBlank() }
            ?: fallbackTitle
        val targetRoot = fileDataSource.createVolumeDirectoryForImportedTitle(title)
        if (targetRoot.listFiles()?.isNotEmpty() == true) {
            // Already imported (concurrent/duplicate import attempt) -- leave it as-is.
            return targetRoot
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

        val metadata = MangaVolumeMetadata(
            id = UUID.randomUUID().toString(),
            title = title,
            cover = firstPageRelativePath?.let { "$PAGES_DIRECTORY_NAME/$it" },
            folder = targetRoot.name,
            lastAccess = clock.currentAppleReferenceDateSeconds(),
            mokuro = MOKURO_FILE_NAME,
            pageCount = volume.pages.size,
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
