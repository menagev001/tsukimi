package moe.antimony.hoshi.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import moe.antimony.hoshi.features.watch.WatchLibraryKind

@Serializable
sealed interface AppRoute : NavKey {
    @Serializable
    data object MainRoute : AppRoute

    @Serializable
    data object BooksRoute : AppRoute

    @Serializable
    data object MangaRoute : AppRoute

    @Serializable
    data object WatchRoute : AppRoute

    @Serializable
    data class WatchLibraryRoute(
        val libraryId: String,
        val title: String,
        val kind: WatchLibraryKind,
    ) : AppRoute

    @Serializable
    data class WatchSeriesRoute(
        val seriesId: String,
        val title: String,
    ) : AppRoute

    @Serializable
    data class WatchPlayerRoute(
        val itemId: String,
        val title: String,
    ) : AppRoute

    @Serializable
    data object DictionaryRoute : AppRoute

    @Serializable
    data object StatisticsRoute : AppRoute

    @Serializable
    data object SettingsRoute : AppRoute

    @Serializable
    data class SettingsDetailRoute(
        val section: SettingsDetailSection,
    ) : AppRoute

    @Serializable
    data class AnkiCardFormatRoute(
        val formatId: String,
    ) : AppRoute

    @Serializable
    data object AnkiAdvancedRoute : AppRoute

    @Serializable
    data class ReaderRoute(
        val bookId: String,
    ) : AppRoute

    @Serializable
    data class MangaReaderRoute(
        val volumeId: String,
    ) : AppRoute
}

@Serializable
enum class SettingsDetailSection {
    Dictionaries,
    Anki,
    Ai,
    KnownWords,
    Watch,
    Profiles,
    Appearance,
    Behavior,
    Advanced,
    Diagnostics,
    About,
}
