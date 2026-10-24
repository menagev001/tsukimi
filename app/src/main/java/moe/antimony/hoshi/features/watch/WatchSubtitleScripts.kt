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
    "window.hoshiWatch.configure({ fontSizePx: $fontSizePx });"

internal fun watchShowCuesScript(lines: List<String>): String {
    val array = lines.joinToString(prefix = "[", postfix = "]") { readerJavaScriptStringLiteral(it) }
    return "window.hoshiWatch && window.hoshiWatch.showCues($array);"
}

internal fun watchBottomInsetScript(px: Float): String =
    "window.hoshiWatch && window.hoshiWatch.setBottomInset($px);"

/** What a tap on the overlay hit, from the [watchSelectTextScript] result. */
internal sealed interface WatchTapResult {
    /** A word was selected; the popup opens through the selection bridge. */
    data object Selected : WatchTapResult

    /** Neither a subtitle line nor the line list. */
    data object Nothing : WatchTapResult

    /** Somewhere in the line list that is not a word or a timestamp. */
    data object List : WatchTapResult

    data class SeekToCue(val cueIndex: Int) : WatchTapResult

    companion object {
        fun fromWebViewResult(result: String?): WatchTapResult {
            val value = result?.trim()
            if (value == null || value == "null" || value == "undefined") return Nothing
            val token = value.removeSurrounding("\"")
            return when {
                token == "image" || token == "link" -> Nothing
                token == "list" -> List
                token.startsWith("seek:") -> token.removePrefix("seek:").toIntOrNull()?.let(::SeekToCue) ?: List
                else -> Selected
            }
        }
    }
}

internal fun watchSetCueListScript(cues: List<WatchSubtitleCue>): String {
    val array = cues.joinToString(prefix = "[", postfix = "]") { cue ->
        "{start:${cue.startMs},text:${readerJavaScriptStringLiteral(cue.text)}}"
    }
    return "window.hoshiWatch && window.hoshiWatch.setCueList($array);"
}

internal fun watchSetActiveCueScript(index: Int?): String =
    "window.hoshiWatch && window.hoshiWatch.setActiveCue(${index ?: -1});"

internal fun watchShowListScript(visible: Boolean, widthPx: Float): String =
    "window.hoshiWatch && (window.hoshiWatch.setListWidth($widthPx), window.hoshiWatch.showList($visible));"

internal fun watchScrollListScript(dy: Float): String =
    "window.hoshiWatch && window.hoshiWatch.scrollList($dy);"

internal fun watchFlingListScript(dy: Float): String =
    "window.hoshiWatch && window.hoshiWatch.flingList($dy);"

internal fun watchSelectTextScript(x: Float, y: Float, maxLength: Int, sentenceOnly: Boolean): String =
    "window.hoshiWatch ? window.hoshiWatch.selectTextAt($x, $y, $maxLength, $sentenceOnly) : null"
