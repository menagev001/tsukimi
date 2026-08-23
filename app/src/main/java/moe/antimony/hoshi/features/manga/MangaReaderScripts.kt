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
 * Injects one page's OCR data + page image URL. `pageJson` is the raw mokuro page JSON object
 * text (one `MokuroPage`, not the whole volume) so a several-hundred-page volume never puts more
 * than the current page's OCR data in the WebView's JS heap at once -- mirrors how EPUB injects
 * only the current chapter, not the whole book.
 */
internal fun mangaShowPageScript(pageIndex: Int, pageJson: String, imageUrl: String): String {
    val imageUrlLiteral = readerJavaScriptStringLiteral(imageUrl)
    return """
        (function() {
          window.hoshiManga && window.hoshiManga.showPage($pageIndex, $pageJson, $imageUrlLiteral);
        })();
    """.trimIndent()
}
