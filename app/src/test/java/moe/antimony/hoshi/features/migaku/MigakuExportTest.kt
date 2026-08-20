package moe.antimony.hoshi.features.migaku

import moe.antimony.hoshi.epub.HighlightColor
import moe.antimony.hoshi.epub.ReaderHighlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MigakuExportTest {
    @Test
    fun highlightWithSentenceMapsFieldsDirectly() {
        val highlight = ReaderHighlight(
            id = "1",
            character = 100,
            offset = 5,
            text = "猫",
            color = HighlightColor.Yellow,
            createdAt = 0.0,
            sentence = "猫が座る。",
        )

        val export = highlight.toMigakuExport(bookTitle = "テスト小説")

        assertEquals("猫", export.word)
        assertEquals("猫が座る。", export.sentence)
        assertEquals("テスト小説", export.bookTitle)
        assertEquals("", export.note)
    }

    @Test
    fun highlightWithoutSentenceFallsBackToHighlightedText() {
        val highlight = ReaderHighlight(
            id = "1",
            character = 100,
            offset = 5,
            text = "猫",
            color = HighlightColor.Yellow,
            createdAt = 0.0,
            sentence = null,
        )

        val export = highlight.toMigakuExport(bookTitle = "テスト小説")

        assertEquals("猫", export.sentence)
    }

    @Test
    fun highlightWithBlankSentenceFallsBackToHighlightedText() {
        val highlight = ReaderHighlight(
            id = "1",
            character = 100,
            offset = 5,
            text = "猫",
            color = HighlightColor.Yellow,
            createdAt = 0.0,
            sentence = "   ",
        )

        val export = highlight.toMigakuExport(bookTitle = "テスト小説")

        assertEquals("猫", export.sentence)
    }

    @Test
    fun appleReferenceCreatedAtConvertsToExpectedIsoTimestamp() {
        // 0.0 Apple reference seconds is exactly the 2001-01-01T00:00:00Z epoch.
        assertEquals("2001-01-01T00:00:00Z", 0.0.toAppleReferenceInstant().toString())
    }

    @Test
    fun jsonExportUsesSnakeCaseBookTitleKey() {
        val entries = listOf(
            MigakuHighlightExport(
                word = "猫",
                sentence = "猫が座る。",
                bookTitle = "テスト小説",
                timestamp = "2001-01-01T00:00:00Z",
            ),
        )

        val json = MigakuExportFormat.toJson(entries)

        assertTrue(json.contains("\"book_title\""))
        assertTrue(json.contains("\"word\""))
        assertTrue(json.contains("\"sentence\""))
        assertTrue(json.contains("\"timestamp\""))
        assertTrue(json.contains("\"note\""))
    }
}
