package moe.antimony.hoshi.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import moe.antimony.hoshi.LocalHoshiUiDependencies
import moe.antimony.hoshi.content.ContentLanguageProfile
import moe.antimony.hoshi.features.reader.ReaderLoadingPage
import moe.antimony.hoshi.features.reader.ReaderSettings

/**
 * Leaner analog of [ReaderRouteDestination] for manga: no auto-sync, wallpaper publication, or
 * Sasayaki cues -- load state, activate the volume's profile (so dictionary lookup resolves the
 * right language/profile, same as EPUB), and render the manga reader.
 */
@Composable
internal fun MangaReaderRouteDestination(
    volumeId: String,
    stateHolder: MangaReaderRouteStateHolder,
    readerSettings: ReaderSettings,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appContainer = LocalHoshiUiDependencies.current
    var reloadKey by remember(volumeId) { mutableIntStateOf(0) }
    val systemDarkTheme = isSystemInDarkTheme()
    val loadingBackground = Modifier.background(Color(readerSettings.backgroundColor(systemDarkTheme)))

    val routeState by produceState<MangaReaderRouteRenderState>(
        MangaReaderRouteRenderState.Loading,
        volumeId,
        stateHolder,
        reloadKey,
    ) {
        val loaded = stateHolder.load(volumeId)
        value = when (loaded) {
            is MangaReaderRouteLoadState.Ready -> {
                val profile = appContainer.profileActivationService.activateForBook(loaded.entry.metadata)
                val contentLanguageProfile = ContentLanguageProfile.fromDictionaryLanguageId(profile.dictionaryLanguageId)
                    ?: ContentLanguageProfile.Default
                MangaReaderRouteRenderState.Ready(loaded, contentLanguageProfile)
            }
            is MangaReaderRouteLoadState.Error -> {
                appContainer.profileActivationService.clearLoadedProfile()
                MangaReaderRouteRenderState.Error(loaded.message)
            }
            MangaReaderRouteLoadState.Loading -> MangaReaderRouteRenderState.Loading
        }
    }

    when (val state = routeState) {
        MangaReaderRouteRenderState.Loading -> ReaderLoadingPage(
            backgroundColor = Color(readerSettings.backgroundColor(systemDarkTheme)),
            modifier = modifier.fillMaxSize(),
        )
        is MangaReaderRouteRenderState.Error -> Box(
            modifier = modifier.fillMaxSize().then(loadingBackground),
            contentAlignment = Alignment.Center,
        ) {
            Text(state.message)
        }
        is MangaReaderRouteRenderState.Ready -> {
            // Placeholder body: validates routing/back-stack/profile-activation in isolation
            // before the reader-manga.js overlay shell + MangaReaderWebView are wired in.
            Box(
                modifier = modifier.fillMaxSize().then(loadingBackground),
                contentAlignment = Alignment.Center,
            ) {
                Text(state.loadState.entry.displayTitle)
            }
        }
    }
}

internal sealed interface MangaReaderRouteRenderState {
    data object Loading : MangaReaderRouteRenderState

    data class Ready(
        val loadState: MangaReaderRouteLoadState.Ready,
        val contentLanguageProfile: ContentLanguageProfile,
    ) : MangaReaderRouteRenderState

    data class Error(
        val message: String,
    ) : MangaReaderRouteRenderState
}
