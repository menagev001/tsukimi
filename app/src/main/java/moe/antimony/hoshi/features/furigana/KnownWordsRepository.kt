package moe.antimony.hoshi.features.furigana

import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import moe.antimony.hoshi.di.FilesDir
import moe.antimony.hoshi.di.IoDispatcher

/**
 * A word the reader is treated as already knowing, keyed by (word, reading) so that
 * homographs with different readings (e.g. 今日/きょう vs 今日/こんにち) don't collide.
 */
@Serializable
data class KnownWordEntry(
    val word: String,
    val reading: String,
)

@Serializable
private data class MigakuKnownWordEntry(
    val word: String = "",
    val reading: String = "",
    val language: String = "",
    val status: String = "",
)

@Serializable
private data class MigakuKnownWordExport(
    val words: List<MigakuKnownWordEntry> = emptyList(),
)

data class KnownWordsImportSummary(
    val exportedKnownCount: Int,
    val newlyAddedCount: Int,
    val totalKnownCount: Int,
)

/**
 * Two files by design, not one: [importedFile] holds the bulk Migaku export (rewritten only on
 * reimport, which is rare, so a full rewrite at 10k+ rows is fine) and [markedFile] holds words
 * marked known one at a time from the reader (rewritten on every mark, but stays small for a
 * long time, so a full rewrite there is cheap). Both are unioned into one in-memory set.
 */
class KnownWordsRepository @Inject constructor(
    @FilesDir filesDir: File,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val knownWordsDir = File(filesDir, "KnownWords")
    private val importedFile = File(knownWordsDir, "imported.json")
    private val markedFile = File(knownWordsDir, "marked.json")
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    private val mutex = Mutex()

    @Volatile
    private var cachedKnownWords: Set<KnownWordEntry>? = null

    /**
     * A synchronous, possibly-stale snapshot for callers that can't suspend (e.g. WebView
     * resource serving, which runs on a non-UI thread but must return synchronously). Empty
     * until something calls [knownWords] at least once -- callers relying on this should warm
     * the cache with a [knownWords] call first. Never wrong in a way that hides furigana it
     * shouldn't: an unwarmed cache just means every word is treated as unknown.
     */
    val knownWordsSnapshot: Set<KnownWordEntry> get() = cachedKnownWords ?: emptySet()

    suspend fun knownWords(): Set<KnownWordEntry> = withContext(ioDispatcher) {
        mutex.withLock { knownWordsLocked() }
    }

    suspend fun isKnown(word: String, reading: String): Boolean =
        KnownWordEntry(word, reading) in knownWords()

    suspend fun markKnown(word: String, reading: String) = withContext(ioDispatcher) {
        mutex.withLock {
            val entry = KnownWordEntry(word, reading)
            val existing = loadEntries(markedFile)
            if (entry in existing) return@withLock
            saveEntries(markedFile, existing + entry)
            // Refreshed immediately, not just invalidated: knownWordsSnapshot is read
            // synchronously elsewhere (the furigana injector) and would otherwise go empty --
            // not just missing this one word, *every* known word -- until something else
            // happens to call knownWords() again and repopulate it.
            refreshCacheLocked()
        }
    }

    suspend fun importMigakuExport(exportJson: String): KnownWordsImportSummary = withContext(ioDispatcher) {
        mutex.withLock {
            val parsed = runCatching {
                json.decodeFromString(MigakuKnownWordExport.serializer(), exportJson)
            }.getOrNull()
            val fromExport = parsed?.words
                .orEmpty()
                .asSequence()
                .filter { it.language == "ja" && it.status == "KNOWN" && it.word.isNotEmpty() }
                .map { KnownWordEntry(it.word, it.reading) }
                .toSet()

            val existingImported = loadEntries(importedFile).toSet()
            val mergedImported = existingImported + fromExport
            if (mergedImported.size != existingImported.size) {
                saveEntries(importedFile, mergedImported.toList())
                refreshCacheLocked()
            }

            KnownWordsImportSummary(
                exportedKnownCount = fromExport.size,
                newlyAddedCount = mergedImported.size - existingImported.size,
                totalKnownCount = (mergedImported + loadEntries(markedFile)).size,
            )
        }
    }

    private fun knownWordsLocked(): Set<KnownWordEntry> = cachedKnownWords ?: refreshCacheLocked()

    private fun refreshCacheLocked(): Set<KnownWordEntry> =
        (loadEntries(importedFile) + loadEntries(markedFile)).toSet().also { cachedKnownWords = it }

    private fun loadEntries(file: File): List<KnownWordEntry> {
        if (!file.isFile) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(KnownWordEntry.serializer()), file.readText())
        }.getOrDefault(emptyList())
    }

    private fun saveEntries(file: File, entries: List<KnownWordEntry>) {
        knownWordsDir.mkdirs()
        file.writeText(json.encodeToString(ListSerializer(KnownWordEntry.serializer()), entries))
    }
}
