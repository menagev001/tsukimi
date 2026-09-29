package moe.antimony.hoshi.features.manga

import moe.antimony.hoshi.content.ContentLanguageProfile
import moe.antimony.hoshi.features.reader.readerJavaScriptStringLiteral

/**
 * Builds the manga reader bootstrap script, mirroring the shape of
 * `features.reader.readerSetupScript` (private in that file) but far smaller: no chapter
 * pagination/restore-token machinery, since manga pages are shown one at a time via
 * `window.hoshiManga.showPage(...)` called separately per page turn.
 */
internal fun mangaReaderSetupScript(
    contentLanguageProfile: ContentLanguageProfile,
    eInkMode: Boolean,
    rtl: Boolean,
    backgroundColorCss: String,
    textColorCss: String,
    eInkLineColorCss: String,
    assets: MangaReaderWebAssets,
): String {
    val eInkModeLiteral = readerJavaScriptStringLiteral(if (eInkMode) "true" else "false")
    val rtlLiteral = readerJavaScriptStringLiteral(if (rtl) "true" else "false")
    val contentLanguageTag = readerJavaScriptStringLiteral(contentLanguageProfile.htmlLang)
    val selectionLanguageId = readerJavaScriptStringLiteral(contentLanguageProfile.dictionaryLanguageId)
    val css = readerJavaScriptStringLiteral(
        assets.readerMangaCss
            .replace("__HOSHI_MANGA_BACKGROUND_COLOR__", backgroundColorCss)
            .replace("__HOSHI_MANGA_TEXT_COLOR__", textColorCss)
            .replace("__HOSHI_MANGA_EINK_LINE_COLOR__", eInkLineColorCss),
    )
    val selectionSupportScript = assets.selectionSupportJs(contentLanguageProfile)
    val selectionScript = assets.selectionJs
    val mangaScript = assets.readerMangaJs
    return """
        (function() {
          document.documentElement.dataset.hoshiReaderEinkMode = $eInkModeLiteral;
          document.documentElement.lang = $contentLanguageTag;
          document.documentElement.dataset.hoshiContentLanguage = $contentLanguageTag;
          var style = document.createElement('style');
          style.textContent = $css;
          document.head.appendChild(style);
          $selectionSupportScript
          $selectionScript
          window.hoshiSelection.configure({
            bridge: 'android-reader',
            language: $selectionLanguageId,
            linkTapResult: 'link',
            imageTapResult: 'image',
            rubyAwareRects: true,
            scaleRects: false
          });
          $mangaScript
          window.hoshiManga.configure({ rtl: $rtlLiteral, eInkMode: $eInkModeLiteral === 'true' });
          if (!document.getElementById('hoshi-reader-popup-host-script')) {
            var popupHostScript = document.createElement('script');
            popupHostScript.id = 'hoshi-reader-popup-host-script';
            popupHostScript.src = 'https://appassets.androidplatform.net/popup/reader-popup-host.js';
            document.head.appendChild(popupHostScript);
          }
        })();
    """.trimIndent()
}

/**
 * Injects one page's (or, in spread mode, one pair's) OCR data + page image URL(s). `pagesJson`
 * is the raw mokuro page JSON object text for each entry (one or two `MokuroPage`s, never the
 * whole volume) so a several-hundred-page volume never puts more than the currently-displayed
 * page(s)' OCR data in the WebView's JS heap at once -- mirrors how EPUB injects only the current
 * chapter, not the whole book. `pagesJson`/`imageUrls` must already be in final left-to-right
 * *visual* order (the caller resolves RTL reading order into display order before calling this).
 */
internal fun mangaShowPageScript(pageIndex: Int, pagesJson: List<String>, imageUrls: List<String>): String {
    val pagesArrayLiteral = pagesJson.joinToString(prefix = "[", postfix = "]")
    val urlsArrayLiteral = imageUrls.joinToString(prefix = "[", postfix = "]") { readerJavaScriptStringLiteral(it) }
    return """
        (function() {
          window.hoshiManga && window.hoshiManga.showPage($pageIndex, $pagesArrayLiteral, $urlsArrayLiteral);
        })();
    """.trimIndent()
}

/**
 * Tap entry point for the manga reader. Unlike the EPUB reader's direct `hoshiSelection.selectText`
 * call, a first tap on an OCR block only reveals it as readable text, and lookups on the revealed
 * block snap a near-miss to its closest character -- see `selectTextAt` in `reader-manga.js` for
 * the return contract consumed by [MangaSelectionResult].
 */
internal fun mangaSelectTextScript(x: Float, y: Float, maxLength: Int, sentenceOnly: Boolean): String =
    "window.hoshiManga ? window.hoshiManga.selectTextAt($x, $y, $maxLength, $sentenceOnly) : null"

/**
 * Result of [mangaSelectTextScript].
 *
 * [nearText] and [blockToggled] are the distinctions the EPUB-side `ReaderSelectionResult` has no
 * need for. [nearText]: a tap on the revealed OCR block that produced no new selection (a
 * deliberate tap-again-to-dismiss, or glyphs that could not be resolved). [blockToggled]: a tap
 * that only revealed an OCR block as readable text, or hid the revealed one again. Treating either
 * as "tapped blank artwork" would snap the page out of zoom or turn it.
 */
internal data class MangaSelectionResult(
    val selectedNothing: Boolean,
    val nearText: Boolean = false,
    val blockToggled: Boolean = false,
    val isImageTap: Boolean = false,
    val isLinkTap: Boolean = false,
) {
    companion object {
        const val NEAR_TEXT_TOKEN: String = "near-text"
        const val BLOCK_TOGGLED_TOKEN: String = "block-toggled"

        fun fromWebViewResult(result: String?): MangaSelectionResult {
            val value = result?.trim()
            val isNull = value == null || value == "null" || value == "undefined"
            val token = value?.removeSurrounding("\"")
            return MangaSelectionResult(
                selectedNothing = isNull,
                nearText = token == NEAR_TEXT_TOKEN,
                blockToggled = token == BLOCK_TOGGLED_TOKEN,
                isImageTap = token == "image",
                isLinkTap = token == "link",
            )
        }
    }
}
