package moe.antimony.hoshi.features.furigana

/**
 * [html] in Anki's furigana syntax, e.g. `パンを<b> 食[た]べる</b>`: every dictionary word with
 * kanji gets its reading, with a space before each reading's base as Anki's `furigana:` filter
 * expects. Markup such as the target word's `<b>` is kept, and text on each side of a tag is
 * segmented on its own, so a word never spans a tag.
 */
internal fun ankiFuriganaHtml(html: String, segment: (String) -> List<FuriganaWordSpan>): String =
    AnkiMarkupRegex.split(html).zip(AnkiMarkupRegex.findAll(html).map { it.value }.toList() + "")
        .joinToString("") { (text, tag) -> ankiFuriganaText(text, segment) + tag }

private fun ankiFuriganaText(text: String, segment: (String) -> List<FuriganaWordSpan>): String =
    segment(text).joinToString("") { span ->
        span.furiganaParts()?.joinToString("") { part ->
            when (part) {
                is FuriganaPart.Ruby -> " ${part.kanjiText}[${part.reading}]"
                is FuriganaPart.Plain -> part.text
            }
        } ?: span.text
    }

private val AnkiMarkupRegex = Regex("<[^>]*>")
