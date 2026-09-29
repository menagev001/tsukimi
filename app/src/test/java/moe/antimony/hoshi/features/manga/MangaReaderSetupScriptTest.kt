package moe.antimony.hoshi.features.manga

import moe.antimony.hoshi.content.ContentLanguageProfile
import org.junit.Assert.assertFalse
import org.junit.Test

class MangaReaderSetupScriptTest {

    @Test
    fun `setup script fills every colour placeholder in the shipped stylesheet`() {
        val script = mangaReaderSetupScript(
            contentLanguageProfile = ContentLanguageProfile.Japanese,
            eInkMode = false,
            rtl = true,
            backgroundColorCss = "rgba(255, 255, 255, 1.0)",
            textColorCss = "rgba(0, 0, 0, 1.0)",
            eInkLineColorCss = "#000",
            // Real CSS, stub JS: the placeholder assertions below are about the CSS substitution
            // actually performed on the shipped stylesheet, not about a fixture that happens to
            // contain the placeholders.
            assets = MangaReaderWebAssets(
                languageJapaneseJs = "",
                selectionJapaneseJs = "",
                selectionEnglishJs = "",
                selectionJs = "",
                readerMangaJs = "",
                readerMangaCss = java.io.File("src/main/assets/hoshi-web/manga/reader-manga.css").readText(),
            ),
        )

        // An unsubstituted placeholder reaches the WebView as a literal CSS value, which is an
        // invalid declaration the browser drops silently -- a revealed OCR block then renders with
        // no colour at all rather than failing loudly.
        assertFalse(script.contains("__HOSHI_MANGA_TEXT_COLOR__"))
        assertFalse(script.contains("__HOSHI_MANGA_BACKGROUND_COLOR__"))
        assertFalse(script.contains("__HOSHI_MANGA_EINK_LINE_COLOR__"))
    }
}
