package moe.antimony.hoshi.features.manga

import android.content.Context
import moe.antimony.hoshi.content.ContentLanguageProfile

/**
 * Reuses the shared selection engine assets (under `hoshi-web/shared/`) verbatim, same as EPUB
 * -- only `manga/reader-manga.js`/`.css` are new. Mirrors `features.reader.ReaderWebAssets`'s
 * cached-singleton asset-loading pattern.
 */
internal data class MangaReaderWebAssets(
    val languageJapaneseJs: String,
    val selectionJapaneseJs: String,
    val selectionEnglishJs: String,
    val selectionJs: String,
    val readerMangaJs: String,
    val readerMangaCss: String,
) {
    companion object {
        @Volatile
        private var cached: MangaReaderWebAssets? = null

        fun load(context: Context): MangaReaderWebAssets =
            cached ?: synchronized(this) {
                cached ?: read(context.applicationContext).also { cached = it }
            }

        private fun read(context: Context): MangaReaderWebAssets =
            MangaReaderWebAssets(
                languageJapaneseJs = context.readAsset("hoshi-web/shared/language-ja.js"),
                selectionJapaneseJs = context.readAsset("hoshi-web/shared/selection-ja.js"),
                selectionEnglishJs = context.readAsset("hoshi-web/shared/selection-en.js"),
                selectionJs = context.readAsset("hoshi-web/shared/selection.js"),
                readerMangaJs = context.readAsset("hoshi-web/manga/reader-manga.js"),
                readerMangaCss = context.readAsset("hoshi-web/manga/reader-manga.css"),
            )

        private fun Context.readAsset(path: String): String =
            assets.open(path)
                .bufferedReader()
                .use { it.readText() }
    }

    fun selectionSupportJs(contentLanguageProfile: ContentLanguageProfile): String =
        when (contentLanguageProfile.dictionaryLanguageId) {
            ContentLanguageProfile.EnglishLanguageId -> "$languageJapaneseJs\n$selectionEnglishJs"
            else -> "$languageJapaneseJs\n$selectionJapaneseJs"
        }
}
