package moe.antimony.hoshi.features.migaku

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.antimony.hoshi.epub.ReaderHighlight

@Serializable
data class MigakuHighlightExport(
    val word: String,
    val sentence: String,
    @SerialName("book_title") val bookTitle: String,
    val timestamp: String,
    val note: String = "",
)

object MigakuExportFormat {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun toJson(entries: List<MigakuHighlightExport>): String =
        json.encodeToString(entries)
}

private const val AppleReferenceEpochSeconds = 978_307_200L

internal fun Double.toAppleReferenceInstant(): Instant =
    Instant.ofEpochMilli(((this + AppleReferenceEpochSeconds) * 1000).toLong())

internal fun ReaderHighlight.toMigakuExport(bookTitle: String): MigakuHighlightExport =
    MigakuHighlightExport(
        word = text,
        sentence = sentence?.takeIf(String::isNotBlank) ?: text,
        bookTitle = bookTitle,
        timestamp = createdAt.toAppleReferenceInstant().toString(),
    )
