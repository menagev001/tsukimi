package moe.antimony.hoshi.features.watch

import android.content.Context
import moe.antimony.hoshi.content.ContentLanguageProfile
import moe.antimony.hoshi.features.reader.readerJavaScriptStringLiteral

/** The shared selection engine plus the Watch overlay assets under `hoshi-web/watch/`. */
internal data class WatchSubtitleWebAssets(
    val languageJapaneseJs: String,
    val selectionJapaneseJs: String,
    val selectionJs: String,
    val watchSubtitlesJs: String,
    val watchSubtitlesCss: String,
) {
    companion object {
        @Volatile
        private var cached: WatchSubtitleWebAssets? = null

        fun load(context: Context): WatchSubtitleWebAssets =
            cached ?: synchronized(this) {
                cached ?: read(context.applicationContext).also { cached = it }
            }

        private fun read(context: Context): WatchSubtitleWebAssets =
            WatchSubtitleWebAssets(
                languageJapaneseJs = context.readAsset("hoshi-web/shared/language-ja.js"),
                selectionJapaneseJs = context.readAsset("hoshi-web/shared/selection-ja.js"),
                selectionJs = context.readAsset("hoshi-web/shared/selection.js"),
                watchSubtitlesJs = context.readAsset("hoshi-web/watch/watch-subtitles.js"),
                watchSubtitlesCss = context.readAsset("hoshi-web/watch/watch-subtitles.css"),
            )

        private fun Context.readAsset(path: String): String =
            assets.open(path).bufferedReader().use { it.readText() }
    }
}

internal const val WatchSubtitleBaseUrl = "https://appassets.androidplatform.net/watch/"

internal fun watchSubtitleSetupScript(
    assets: WatchSubtitleWebAssets,
    fontSizePx: Int,
): String {
    val language = ContentLanguageProfile.Japanese
    val htmlLang = readerJavaScriptStringLiteral(language.htmlLang)
    val selectionLanguageId = readerJavaScriptStringLiteral(language.dictionaryLanguageId)
    val css = readerJavaScriptStringLiteral(assets.watchSubtitlesCss)
    return """
        (function() {
          document.documentElement.lang = $htmlLang;
          document.documentElement.dataset.hoshiContentLanguage = $htmlLang;
          var style = document.createElement('style');
          style.textContent = $css;
          document.head.appendChild(style);
          ${assets.languageJapaneseJs}
          ${assets.selectionJapaneseJs}
          ${assets.selectionJs}
          window.hoshiSelection.configure({
            bridge: 'android-reader',
            language: $selectionLanguageId,
            linkTapResult: 'link',
            imageTapResult: 'image',
            rubyAwareRects: true,
            scaleRects: false
          });
          ${assets.watchSubtitlesJs}
          ${watchSubtitleConfigureCall(fontSizePx)}
          if (!document.getElementById('hoshi-reader-popup-host-script')) {
            var popupHostScript = document.createElement('script');
            popupHostScript.id = 'hoshi-reader-popup-host-script';
            popupHostScript.src = 'https://appassets.androidplatform.net/popup/reader-popup-host.js';
            document.head.appendChild(popupHostScript);
          }
        })();
    """.trimIndent()
}

internal fun watchSubtitleConfigureScript(fontSizePx: Int): String =
    "window.hoshiWatch && ${watchSubtitleConfigureCall(fontSizePx)}"

private fun watchSubtitleConfigureCall(fontSizePx: Int): String =
    "window.hoshiWatch.configure({ fontSizePx: $fontSizePx, secondaryFontSizePx: ${secondaryFontSizePx(fontSizePx)} });"

private fun secondaryFontSizePx(fontSizePx: Int): Int = (fontSizePx * 0.7f).toInt()

internal fun watchShowCuesScript(primary: List<String>, secondary: List<String>): String {
    fun array(lines: List<String>) = lines.joinToString(prefix = "[", postfix = "]") { readerJavaScriptStringLiteral(it) }
    return "window.hoshiWatch && window.hoshiWatch.showCues(${array(primary)}, ${array(secondary)});"
}

internal fun watchBottomInsetScript(px: Float): String =
    "window.hoshiWatch && window.hoshiWatch.setBottomInset($px);"

/** True when a [watchSelectTextScript] result means the tap selected no text. */
internal fun watchSelectedNothing(result: String?): Boolean {
    val value = result?.trim()
    return value == null || value == "null" || value == "undefined" || value == "\"image\"" || value == "\"link\""
}

internal fun watchSelectTextScript(x: Float, y: Float, maxLength: Int, sentenceOnly: Boolean): String =
    "window.hoshiWatch ? window.hoshiWatch.selectTextAt($x, $y, $maxLength, $sentenceOnly) : null"
