package moe.antimony.hoshi.navigation

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import moe.antimony.hoshi.LocalHoshiUiDependencies
import moe.antimony.hoshi.content.ContentLanguageProfile
import moe.antimony.hoshi.features.manga.MangaReaderWebView
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
    onReaderSettingsChange: (ReaderSettings) -> Unit,
    onReaderKeyEventHandlerChange: (((KeyEvent) -> Boolean)?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appContainer = LocalHoshiUiDependencies.current
    var reloadKey by remember(volumeId) { mutableIntStateOf(0) }
    val systemDarkTheme = isSystemInDarkTheme()
    val loadingBackground = Modifier.background(Color(readerSettings.backgroundColor(systemDarkTheme)))
    val scope = rememberCoroutineScope()

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
            val readyState = state.loadState
            val lifecycleOwner = LocalLifecycleOwner.current
            // Tracks active reading time only (no chars/speed, per what was actually asked for),
            // pausing while the app is backgrounded rather than counting idle time. Flushed via
            // appContainer.appScope rather than the composable-scoped `scope` above: a save
            // launched from onDispose on a scope that's being torn down in that same step risks
            // losing the race against its own cancellation, which appScope (process-lifetime)
            // doesn't have.
            DisposableEffect(volumeId, lifecycleOwner) {
                var segmentStartMillis: Long? = System.currentTimeMillis()
                fun flushSegment() {
                    val start = segmentStartMillis ?: return
                    segmentStartMillis = null
                    val deltaSeconds = (System.currentTimeMillis() - start) / 1000.0
                    appContainer.appScope.launch { stateHolder.addReadingSeconds(readyState.entry, deltaSeconds) }
                }
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_PAUSE -> flushSegment()
                        Lifecycle.Event.ON_RESUME -> segmentStartMillis = System.currentTimeMillis()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    flushSegment()
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }
            MangaReaderWebView(
                volumeId = volumeId,
                volumeEntry = readyState.entry,
                mokuroVolume = readyState.mokuroVolume,
                initialPageIndex = readyState.bookmark?.pageIndex ?: 0,
                readerSettings = readerSettings,
                onReaderSettingsChange = onReaderSettingsChange,
                onReaderKeyEventHandlerChange = onReaderKeyEventHandlerChange,
                contentLanguageProfile = state.contentLanguageProfile,
                onSaveBookmark = { pageIndex ->
                    scope.launch { stateHolder.saveBookmark(readyState.entry, pageIndex) }
                },
                onClose = onClose,
                modifier = modifier.fillMaxSize(),
            )
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
