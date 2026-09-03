package moe.antimony.hoshi.features.furigana

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuriganaHtmlInjectorTest {
    private fun injectorFor(vararg entries: Pair<String, FuriganaWordSpan>): FuriganaHtmlInjector {
        val byText = entries.toMap()
        return FuriganaHtmlInjector { text -> listOf(byText[text] ?: FuriganaWordSpan(text, "", "", true)) }
    }

    @Test
    fun wrapsAnUnknownWordSplittingOffTheOkuriganaTail() {
        val injector = injectorFor(
            "私は食べた。" to FuriganaWordSpan("食べた", "食べる", "たべる", isKnownWord = false),
        )

        val result = injector.inject("<html><body><p>私は食べた。</p></body></html>")

        assertTrue(result.contains("<ruby>食<rt>た</rt></ruby>べた"))
    }

    @Test
    fun leavesKnownWordsAsPlainText() {
        val injector = injectorFor(
            "食べた" to FuriganaWordSpan("食べた", "食べる", "たべる", isKnownWord = true),
        )

        val result = injector.inject("<html><body><p>食べた</p></body></html>")

        assertTrue(!result.contains("<ruby>"))
        assertTrue(result.contains("食べた"))
    }

    @Test
    fun leavesKanaOnlyWordsUnwrappedEvenWhenUnknown() {
        val injector = injectorFor(
            "とても" to FuriganaWordSpan("とても", "とても", "とても", isKnownWord = false),
        )

        val result = injector.inject("<html><body><p>とても</p></body></html>")

        assertTrue(!result.contains("<ruby>"))
    }

    @Test
    fun doesNotReprocessTextAlreadyInsideRubyRtOrScript() {
        val injector = FuriganaHtmlInjector { text ->
            error("segment should not be called for skipped elements, got: $text")
        }

        val html = """
            <html><body>
              <ruby>先生<rt>せんせい</rt></ruby>
              <script>var x = "食べた";</script>
              <style>.a::after { content: "食べた"; }</style>
            </body></html>
        """.trimIndent()

        val result = injector.inject(html)

        assertTrue(result.contains("せんせい"))
    }

    @Test
    fun stopsProcessingOnceTheCharacterBudgetIsExhausted() {
        var callCount = 0
        val injector = FuriganaHtmlInjector(
            segment = { text ->
                callCount++
                listOf(FuriganaWordSpan(text, "先生", "せんせい", isKnownWord = false))
            },
            maxProcessedCharacters = 15,
        )
        val html = "<html><body>" +
            (1..5).joinToString("") { "<p>${"先".repeat(10)}</p>" } +
            "</body></html>"

        injector.inject(html)

        // Budget is checked between text nodes, not mid-node, so the node that crosses the
        // budget still gets processed in full -- only the *next* one is skipped.
        assertEquals(2, callCount)
    }

    @Test
    fun startFractionSkipsAheadToRoughlyThatPositionInTheChapter() {
        val injector = FuriganaHtmlInjector(
            backBufferCharacters = 0,
            segment = { text -> listOf(FuriganaWordSpan(text, "先生", "せんせい", isKnownWord = false)) },
        )
        val html = "<html><body>" + (1..4).joinToString("") { "<p>先生</p>" } + "</body></html>"

        // 8 characters total; halfway is 4, which lands exactly on the boundary between the
        // second and third 2-character paragraph -- so the first two are skipped untouched.
        val result = injector.inject(html, startFraction = 0.5)

        assertEquals(2, Regex("<ruby>").findAll(result).count())
    }

    @Test
    fun backBufferCoversSomeTextBeforeTheTargetPosition() {
        val injector = FuriganaHtmlInjector(
            backBufferCharacters = 2,
            segment = { text -> listOf(FuriganaWordSpan(text, "先生", "せんせい", isKnownWord = false)) },
        )
        val html = "<html><body>" + (1..4).joinToString("") { "<p>先生</p>" } + "</body></html>"

        // Same halfway point as above, but backed off by 2 characters -- one fewer paragraph
        // gets skipped, covering a bit of what the reader has likely just read.
        val result = injector.inject(html, startFraction = 0.5)

        assertEquals(3, Regex("<ruby>").findAll(result).count())
    }

    @Test
    fun reportsProgressStartingAtZeroAndEndingAtTheTarget() {
        val updates = mutableListOf<Pair<Int, Int>>()
        val injector = FuriganaHtmlInjector(
            onProgress = { processed, total -> updates += processed to total },
            segment = { text -> listOf(FuriganaWordSpan(text, "先生", "せんせい", isKnownWord = false)) },
        )
        val html = "<html><body>" + (1..4).joinToString("") { "<p>先生</p>" } + "</body></html>"

        injector.inject(html)

        // 4 paragraphs of 2 characters each: an initial (0, 8), one update per paragraph, ending
        // at (8, 8) -- so a caller can show "processed / total" while the chapter is loading.
        assertEquals(0 to 8, updates.first())
        assertEquals(8 to 8, updates.last())
        assertEquals(6, updates.size)
    }

    @Test
    fun malformedXhtmlFallsBackToTheOriginalUnchanged() {
        val injector = injectorFor()
        val malformed = "<html><body><p>unterminated"

        val result = injector.inject(malformed)

        assertEquals(malformed, result)
    }

    @Test
    fun preservesElementAttributesAndSurroundingStructure() {
        val injector = injectorFor(
            "先生" to FuriganaWordSpan("先生", "先生", "せんせい", isKnownWord = false),
        )

        val result = injector.inject("""<html><body><p class="a" id="p1">先生</p></body></html>""")

        assertTrue(result.contains("""class="a""""))
        assertTrue(result.contains("""id="p1""""))
    }
}
