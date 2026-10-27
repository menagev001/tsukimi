package moe.antimony.hoshi.features.furigana

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KnownWordsRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun repository(): KnownWordsRepository =
        KnownWordsRepository(temporaryFolder.newFolder(), Dispatchers.Unconfined)

    @Test
    fun importMigakuExportKeepsOnlyKnownJapaneseWords() = runBlocking {
        val repository = repository()

        val summary = repository.importMigakuExport(
            """
            {
              "words": [
                {"word": "食べる", "reading": "たべる", "language": "ja", "status": "KNOWN"},
                {"word": "見る", "reading": "みる", "language": "ja", "status": "UNKNOWN"},
                {"word": "eat", "reading": "eat", "language": "en", "status": "KNOWN"}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, summary.exportedKnownCount)
        assertEquals(1, summary.newlyAddedCount)
        assertEquals(1, summary.totalKnownCount)
        assertTrue(repository.isKnown("食べる", "たべる"))
        assertTrue(!repository.isKnown("見る", "みる"))
        assertTrue(!repository.isKnown("eat", "eat"))
    }

    @Test
    fun reimportMergesAdditivelyInsteadOfReplacing() = runBlocking {
        val repository = repository()
        repository.importMigakuExport(
            """{"words": [{"word": "食べる", "reading": "たべる", "language": "ja", "status": "KNOWN"}]}""",
        )

        val secondSummary = repository.importMigakuExport(
            """{"words": [{"word": "見る", "reading": "みる", "language": "ja", "status": "KNOWN"}]}""",
        )

        assertEquals(1, secondSummary.newlyAddedCount)
        assertEquals(2, secondSummary.totalKnownCount)
        assertTrue(repository.isKnown("食べる", "たべる"))
        assertTrue(repository.isKnown("見る", "みる"))
    }

    @Test
    fun markKnownIsAdditiveAndSurvivesAReimport() = runBlocking {
        val repository = repository()
        repository.markKnown("読む", "よむ")

        repository.importMigakuExport(
            """{"words": [{"word": "食べる", "reading": "たべる", "language": "ja", "status": "KNOWN"}]}""",
        )

        assertTrue(repository.isKnown("読む", "よむ"))
        assertTrue(repository.isKnown("食べる", "たべる"))
        assertEquals(2, repository.knownWords().size)
    }

    @Test
    fun markingTheSameWordTwiceDoesNotDuplicateIt() = runBlocking {
        val repository = repository()
        repository.markKnown("読む", "よむ")
        repository.markKnown("読む", "よむ")

        assertEquals(1, repository.knownWords().size)
    }

    @Test
    fun markKnownRefreshesTheSynchronousSnapshotImmediately() = runBlocking {
        // knownWordsSnapshot is read from a non-suspend context (the furigana injector) and must
        // reflect a write right away -- not just omit the new word, but never regress to empty,
        // which would happen if the cache were merely invalidated rather than repopulated.
        val repository = repository()
        repository.knownWords() // warm the cache once, as the reader does on startup
        repository.markKnown("お客", "おきゃく")

        val snapshot = repository.knownWordsSnapshot

        assertEquals(setOf(KnownWordEntry("お客", "おきゃく")), snapshot)
    }

    @Test
    fun importMigakuExportRefreshesTheSynchronousSnapshotImmediately() = runBlocking {
        val repository = repository()
        repository.knownWords()

        repository.importMigakuExport(
            """{"words": [{"word": "食べる", "reading": "たべる", "language": "ja", "status": "KNOWN"}]}""",
        )

        assertEquals(setOf(KnownWordEntry("食べる", "たべる")), repository.knownWordsSnapshot)
    }

    @Test
    fun importMigakuExportReadsTheExportersKnownCsv() = runBlocking {
        val repository = repository()

        val summary = repository.importMigakuExport(
            "\uFEFFdictForm,secondary,hasCard\r\n\"食べる\",\"たべる\",1\r\n\"ああ\",\"\",0\r\n\"a,b\",\"x\"\"y\",0\r\n",
        )

        assertEquals(3, summary.exportedKnownCount)
        assertEquals(
            setOf(KnownWordEntry("食べる", "たべる"), KnownWordEntry("ああ", "ああ"), KnownWordEntry("a,b", "x\"y")),
            repository.knownWords(),
        )
    }

    @Test
    fun ankiWordsAreReplacedOnEachSyncAndKeepTheOtherLists() = runBlocking {
        val repository = repository()
        repository.markKnown("お客", "おきゃく")
        repository.replaceAnkiWords(listOf(KnownWordEntry("念願", "ねんがん"), KnownWordEntry("お客", "おきゃく")))

        repository.replaceAnkiWords(listOf(KnownWordEntry("幻滅", "げんめつ")))

        assertEquals(
            setOf(KnownWordEntry("お客", "おきゃく"), KnownWordEntry("幻滅", "げんめつ")),
            repository.knownWordsSnapshot,
        )
        assertEquals(KnownWordsCounts(imported = 0, marked = 1, anki = 1, total = 2), repository.counts())
    }
}
