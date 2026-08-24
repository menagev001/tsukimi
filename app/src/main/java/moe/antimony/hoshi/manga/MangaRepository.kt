package moe.antimony.hoshi.manga

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import moe.antimony.hoshi.di.FilesDir
import moe.antimony.hoshi.di.IoDispatcher

/**
 * Low-level manga domain boundary, mirroring `moe.antimony.hoshi.epub.BookRepository`'s
 * file/sidecar split. Manga volumes live under their own `Manga/` storage root, entirely
 * separate from `Books/`, rather than a type flag on `BookMetadata`.
 */
@Singleton
class MangaRepository private constructor(
    private val filesDir: File,
    private val ioDispatcher: CoroutineDispatcher,
    private val fileDataSource: MangaFileDataSource,
    private val sidecarDataSource: MangaSidecarDataSource,
    private val clock: MangaClock,
) {
    @Inject
    constructor(
        @FilesDir filesDir: File,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(
        filesDir = filesDir,
        ioDispatcher = ioDispatcher,
        fileDataSource = MangaFileDataSource(filesDir, ioDispatcher),
        sidecarDataSource = MangaSidecarDataSource(ioDispatcher),
        clock = SystemMangaClock,
    )

    constructor(filesDir: File) : this(filesDir, Dispatchers.IO)

    private val importDataSource = MangaImportDataSource(
        filesDir = filesDir,
        fileDataSource = fileDataSource,
        clock = clock,
        ioDispatcher = ioDispatcher,
    )

    val mangaDirectory: File get() = fileDataSource.mangaDirectory

    /** Self-contained CBZ: its `.mokuro` sidecar is bundled inside the archive itself. */
    suspend fun importCbz(contentResolver: ContentResolver, cbzUri: Uri): File =
        importDataSource.importCbz(contentResolver, cbzUri)

    /** CBZ paired with a separate `.mokuro` sidecar file. */
    suspend fun importCbz(contentResolver: ContentResolver, cbzUri: Uri, mokuroUri: Uri): File =
        importDataSource.importCbz(contentResolver, cbzUri, mokuroUri)

    internal suspend fun importCandidate(contentResolver: ContentResolver, candidate: MangaImportCandidate<Uri>): File =
        importDataSource.importCandidate(contentResolver, candidate)

    suspend fun loadAllVolumes(): List<File> = fileDataSource.loadAllVolumes()

    suspend fun loadVolumeEntries(sortOption: MangaSortOption = MangaSortOption.Recent): List<MangaVolumeEntry> {
        val entries = loadAllVolumes()
            .mapNotNull { root -> loadMetadata(root)?.let { MangaVolumeEntry(root, it) } }
        return when (sortOption) {
            MangaSortOption.Recent -> entries.sortedByDescending { it.metadata.lastAccess }
            MangaSortOption.Title -> entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayTitle })
        }
    }

    suspend fun loadVolumeEntry(volumeId: String): MangaVolumeEntry? {
        for (root in loadAllVolumes()) {
            val metadata = loadMetadata(root) ?: continue
            if (metadata.id == volumeId) return MangaVolumeEntry(root, metadata)
        }
        return null
    }

    suspend fun createVolumeDirectoryForImportedTitle(title: String): File =
        fileDataSource.createVolumeDirectoryForImportedTitle(title)

    suspend fun loadMetadata(volumeRoot: File): MangaVolumeMetadata? =
        sidecarDataSource.loadMetadata(volumeRoot)

    suspend fun saveMetadata(volumeRoot: File, metadata: MangaVolumeMetadata) {
        sidecarDataSource.saveMetadata(volumeRoot, metadata)
    }

    suspend fun coverFile(entry: MangaVolumeEntry): File? = fileDataSource.coverFile(entry)

    suspend fun pageFile(volumeRoot: File, relativeImgPath: String): File? =
        fileDataSource.pageFile(volumeRoot, relativeImgPath)

    suspend fun loadBookmark(volumeRoot: File): MangaBookmark? =
        sidecarDataSource.loadBookmark(volumeRoot)

    suspend fun saveBookmark(volumeRoot: File, bookmark: MangaBookmark) {
        sidecarDataSource.saveBookmark(volumeRoot, bookmark)
    }

    suspend fun loadHighlights(volumeRoot: File): List<MangaHighlight> =
        sidecarDataSource.loadHighlights(volumeRoot).orEmpty()

    suspend fun saveHighlights(volumeRoot: File, highlights: List<MangaHighlight>) {
        sidecarDataSource.saveHighlights(volumeRoot, highlights)
    }

    suspend fun loadMokuroVolume(volumeRoot: File): MokuroVolume? = withContext(ioDispatcher) {
        val file = fileDataSource.mokuroFile(volumeRoot) ?: return@withContext null
        runCatching { MokuroParser.parse(file) }.getOrNull()
    }

    suspend fun deleteVolume(volumeRoot: File) {
        fileDataSource.deleteVolume(volumeRoot)
    }

    fun currentAppleReferenceDateSeconds(): Double = clock.currentAppleReferenceDateSeconds()
}

enum class MangaSortOption {
    Recent,
    Title,
}

class MangaFileDataSource(
    filesDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    val mangaDirectory: File = File(filesDir, "Manga")

    suspend fun loadAllVolumes(): List<File> = withContext(ioDispatcher) {
        mangaDirectory
            .listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }

    suspend fun createVolumeDirectory(folder: String): File = withContext(ioDispatcher) {
        mangaDirectory.mkdirs()
        val root = mangaDirectory.resolve(folder).canonicalFile
        val mangaRoot = mangaDirectory.canonicalFile
        require(root.path == mangaRoot.path || root.path.startsWith(mangaRoot.path + File.separator)) {
            "Unsafe manga folder: $folder"
        }
        root.mkdirs()
        root
    }

    suspend fun createVolumeDirectoryForImportedTitle(title: String): File {
        val safeTitle = title.sanitizeImportedVolumeTitle()
        require(safeTitle.isNotBlank()) { "Manga volume title is empty" }
        return createVolumeDirectory(safeTitle)
    }

    suspend fun coverFile(entry: MangaVolumeEntry): File? = withContext(ioDispatcher) {
        val cover = entry.metadata.cover?.takeIf { it.isNotBlank() } ?: return@withContext null
        resolveStoredRelativeFile(entry.root, cover)
    }

    suspend fun pageFile(volumeRoot: File, relativeImgPath: String): File? = withContext(ioDispatcher) {
        val root = volumeRoot.canonicalFile
        val pagesRoot = root.resolve(PAGES_DIRECTORY_NAME).canonicalFile
        val target = pagesRoot.resolve(relativeImgPath).canonicalFile
        if ((target.path == pagesRoot.path || target.path.startsWith(pagesRoot.path + File.separator)) && target.isFile) {
            target
        } else {
            null
        }
    }

    suspend fun mokuroFile(volumeRoot: File): File? = withContext(ioDispatcher) {
        val file = volumeRoot.resolve(MOKURO_FILE_NAME)
        file.takeIf { it.isFile }
    }

    private fun resolveStoredRelativeFile(volumeRoot: File, relativePath: String): File? {
        val root = volumeRoot.canonicalFile
        val resolved = root.resolve(relativePath).canonicalFile
        return if ((resolved.path == root.path || resolved.path.startsWith(root.path + File.separator)) && resolved.isFile) {
            resolved
        } else {
            null
        }
    }

    suspend fun deleteVolume(volumeRoot: File) = withContext(ioDispatcher) {
        val root = volumeRoot.canonicalFile
        val mangaRoot = mangaDirectory.canonicalFile
        require(root.path != mangaRoot.path && root.path.startsWith(mangaRoot.path + File.separator)) {
            "Unsafe manga directory: ${volumeRoot.path}"
        }
        if (root.exists()) {
            root.deleteRecursively()
        }
    }
}

class MangaSidecarDataSource(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "    "
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend fun loadMetadata(volumeRoot: File): MangaVolumeMetadata? =
        loadJson(MangaVolumeMetadata.serializer(), volumeRoot.resolve(METADATA_FILE_NAME))

    suspend fun saveMetadata(volumeRoot: File, metadata: MangaVolumeMetadata) {
        saveJson(volumeRoot, METADATA_FILE_NAME, MangaVolumeMetadata.serializer(), metadata)
    }

    suspend fun loadBookmark(volumeRoot: File): MangaBookmark? =
        loadJson(MangaBookmark.serializer(), volumeRoot.resolve(BOOKMARK_FILE_NAME))

    suspend fun saveBookmark(volumeRoot: File, bookmark: MangaBookmark) {
        saveJson(volumeRoot, BOOKMARK_FILE_NAME, MangaBookmark.serializer(), bookmark)
    }

    suspend fun loadHighlights(volumeRoot: File): List<MangaHighlight>? =
        loadJson(ListSerializer(MangaHighlight.serializer()), volumeRoot.resolve(HIGHLIGHTS_FILE_NAME))

    suspend fun saveHighlights(volumeRoot: File, highlights: List<MangaHighlight>) {
        saveJson(volumeRoot, HIGHLIGHTS_FILE_NAME, ListSerializer(MangaHighlight.serializer()), highlights)
    }

    private suspend fun <T> loadJson(serializer: KSerializer<T>, file: File): T? = withContext(ioDispatcher) {
        if (!file.isFile) return@withContext null
        runCatching { json.decodeFromString(serializer, file.readText()) }.getOrNull()
    }

    private suspend fun <T> saveJson(volumeRoot: File, fileName: String, serializer: KSerializer<T>, value: T) =
        withContext(ioDispatcher) {
            volumeRoot.mkdirs()
            volumeRoot.resolve(fileName).writeText(json.encodeToString(serializer, value))
        }
}

interface MangaClock {
    fun currentAppleReferenceDateSeconds(): Double
}

object SystemMangaClock : MangaClock {
    override fun currentAppleReferenceDateSeconds(): Double {
        val now = Instant.now()
        return now.epochSecond.toDouble() + (now.nano.toDouble() / 1_000_000_000.0) - APPLE_REFERENCE_EPOCH_SECONDS
    }
}

internal const val MOKURO_FILE_NAME = "mokuro.json"
internal const val PAGES_DIRECTORY_NAME = "pages"
private const val METADATA_FILE_NAME = "metadata.json"
private const val BOOKMARK_FILE_NAME = "bookmark.json"
private const val HIGHLIGHTS_FILE_NAME = "highlights.json"
private const val APPLE_REFERENCE_EPOCH_SECONDS = 978_307_200.0

private fun String.sanitizeImportedVolumeTitle(): String =
    split(Regex("[\\\\/:*?\"<>|\\n\\r\\u0000-\\u001F]"))
        .joinToString("_")
        .trim()
