package moe.antimony.hoshi.features.migaku

import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import moe.antimony.hoshi.di.IoDispatcher
import moe.antimony.hoshi.epub.BookRepository
import moe.antimony.hoshi.epub.BookSortOption

@Singleton
class MigakuExportRepository @Inject constructor(
    private val bookRepository: BookRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun buildExport(): List<MigakuHighlightExport> = withContext(ioDispatcher) {
        bookRepository.loadBookEntries(BookSortOption.Title).flatMap { entry ->
            bookRepository.loadHighlights(entry.root).map { highlight ->
                highlight.toMigakuExport(bookTitle = entry.displayTitle)
            }
        }
    }

    suspend fun writeExport(output: OutputStream): Int = withContext(ioDispatcher) {
        val entries = buildExport()
        output.write(MigakuExportFormat.toJson(entries).toByteArray(Charsets.UTF_8))
        entries.size
    }
}
