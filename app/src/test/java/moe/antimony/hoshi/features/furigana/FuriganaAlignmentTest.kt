package moe.antimony.hoshi.features.furigana

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuriganaAlignmentTest {
    private fun span(text: String, expression: String, reading: String, isKnownWord: Boolean = false) =
        FuriganaWordSpan(text, expression, reading, isKnownWord)

    @Test
    fun stripsMatchingOkuriganaFromBothTextAndReading() {
        // reading is the lemma's own reading (たべる, matching expression 食べる), not a reading of
        // the conjugated text on the page -- the dictionary never returns "たべた" for this match.
        val parts = span("食べた", "食べる", "たべる").furiganaParts()

        assertEquals(
            listOf(FuriganaPart.Ruby("食", "た"), FuriganaPart.Plain("べた")),
            parts,
        )
    }

    @Test
    fun handlesConjugationsWhereTheDictionaryReadingDoesNotContainTheOnPageEnding() {
        // Real lookups against the app's own dictionaries (godan te-iru, godan -zu negative,
        // i-adjective adverbial): reading is always the lemma's reading only, sharing no literal
        // characters with most of the on-page ending -- these must resolve by comparing expression
        // against reading first, not text's tail against reading.
        assertEquals(
            listOf(FuriganaPart.Ruby("鳴", "な"), FuriganaPart.Plain("らしている")),
            span("鳴らしている", "鳴らす", "ならす").furiganaParts(),
        )
        assertEquals(
            listOf(FuriganaPart.Ruby("使", "つか"), FuriganaPart.Plain("わず")),
            span("使わず", "使う", "つかう").furiganaParts(),
        )
        assertEquals(
            listOf(FuriganaPart.Ruby("素早", "すばや"), FuriganaPart.Plain("く")),
            span("素早く", "素早い", "すばやい").furiganaParts(),
        )
    }

    @Test
    fun leavesKnownWordsUnannotated() {
        assertNull(span("食べた", "食べる", "たべる", isKnownWord = true).furiganaParts())
    }

    @Test
    fun correctlyAnnotatesAPhraseLevelMatchByAligningEachKanjiBlockSeparately() {
        // Using the exact values a real lookup against the app's dictionaries returned: the
        // dictionary matched the whole phrase "指示を出している" against the set phrase entry
        // "指示を出す" (reading "しじをだす"). Aligning 指示を出す against しじをだす segment by
        // segment (指示->しじ, を is a literal anchor, 出->だ) recovers the correct per-kanji
        // reading even though the match spans more than one word -- the conjugated ending
        // (している) is whatever's left after the last kanji block, shown as plain text.
        val parts = span("指示を出している", "指示を出す", "しじをだす").furiganaParts()

        assertEquals(
            listOf(
                FuriganaPart.Ruby("指示", "しじ"),
                FuriganaPart.Plain("を"),
                FuriganaPart.Ruby("出", "だ"),
                FuriganaPart.Plain("している"),
            ),
            parts,
        )
    }

    @Test
    fun annotatesEachKanjiBlockOfACompoundVerbWithKanaBetweenThem() {
        // 突っ込んだ: 突 and 込 are two separate kanji blocks with っ (part of the word's own
        // spelling, not conjugation) between them.
        val parts = span("突っ込んだ", "突っ込む", "つっこむ").furiganaParts()

        assertEquals(
            listOf(
                FuriganaPart.Ruby("突", "つ"),
                FuriganaPart.Plain("っ"),
                FuriganaPart.Ruby("込", "こ"),
                FuriganaPart.Plain("んだ"),
            ),
            parts,
        )
    }

    @Test
    fun annotatesAWordWithALeadingKanaPrefix() {
        // お願い: the honorific prefix お is plain kana before the kanji, not part of a kanji
        // core -- it should stay as plain text while 願 still gets its own reading (ねが).
        val parts = span("お願い", "お願い", "おねがい").furiganaParts()

        assertEquals(
            listOf(FuriganaPart.Plain("お"), FuriganaPart.Ruby("願", "ねが"), FuriganaPart.Plain("い")),
            parts,
        )
    }

    @Test
    fun leavesKanaOnlyWordsUnannotated() {
        assertNull(span("とても", "とても", "とても").furiganaParts())
    }

    @Test
    fun leavesAPureKanjiCompoundWithNoOkuriganaAsOneRubySpan() {
        assertEquals(
            listOf(FuriganaPart.Ruby("先生", "せんせい")),
            span("先生", "先生", "せんせい").furiganaParts(),
        )
    }
}
