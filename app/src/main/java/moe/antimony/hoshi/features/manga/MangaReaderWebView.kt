package moe.antimony.hoshi.features.manga

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import moe.antimony.hoshi.LocalHoshiUiDependencies
import moe.antimony.hoshi.content.ContentLanguageProfile
import moe.antimony.hoshi.features.ai.AiLookupOverlay
import moe.antimony.hoshi.features.ai.AiLookupViewModel
import moe.antimony.hoshi.features.anki.AnkiViewModel
import moe.antimony.hoshi.features.audio.AudioRequestHandler
import moe.antimony.hoshi.features.audio.AudioSettings
import moe.antimony.hoshi.features.audio.LocalAudioRepository
import moe.antimony.hoshi.features.dictionary.DictionaryImageRequestHandler
import moe.antimony.hoshi.features.dictionary.DictionarySettings
import moe.antimony.hoshi.features.dictionary.LookupPopupAssets
import moe.antimony.hoshi.features.dictionary.LookupPopupHtml
import moe.antimony.hoshi.features.dictionary.LookupPopupItem
import moe.antimony.hoshi.features.dictionary.LookupPopupOptions
import moe.antimony.hoshi.features.dictionary.closeChildPopupsAndClearSelection
import moe.antimony.hoshi.features.dictionary.closeChildPopupsForScrolledParent
import moe.antimony.hoshi.features.dictionary.clearPopupSelectionHighlights
import moe.antimony.hoshi.features.dictionary.createLookupPopupItem
import moe.antimony.hoshi.features.dictionary.dismissPopupAt
import moe.antimony.hoshi.features.dictionary.withLookupPopupVisualOptions
import moe.antimony.hoshi.features.export.WordExport
import moe.antimony.hoshi.features.export.shareWordExport
import moe.antimony.hoshi.features.reader.ReaderLookupPopupBridgeCallbackHolder
import moe.antimony.hoshi.features.reader.ReaderLookupPopupBridgeCallbacks
import moe.antimony.hoshi.features.reader.ReaderLookupPopupBridgeMessage
import moe.antimony.hoshi.features.reader.ReaderLookupPopupResourceHandler
import moe.antimony.hoshi.features.reader.ReaderLookupPopupRootHighlightPayload
import moe.antimony.hoshi.features.reader.ReaderLookupPopupViewport
import moe.antimony.hoshi.features.reader.ReaderLookupPopupWebBridge
import moe.antimony.hoshi.features.reader.ReaderPopupHistoryCounts
import moe.antimony.hoshi.features.reader.ReaderRootSelectionHighlight
import moe.antimony.hoshi.features.reader.ReaderSelectionBridge
import moe.antimony.hoshi.features.reader.ReaderSelectionBridgePayload
import moe.antimony.hoshi.features.reader.ReaderSelectionCommand
import moe.antimony.hoshi.features.reader.ReaderSelectionData
import moe.antimony.hoshi.features.reader.ReaderSelectionRect
import moe.antimony.hoshi.features.reader.ReaderSettings
import moe.antimony.hoshi.features.reader.androidPixelsToCssPixels
import moe.antimony.hoshi.features.audio.WordAudioPlayer
import moe.antimony.hoshi.features.dictionary.openPopupExternalLink
import moe.antimony.hoshi.features.reader.readerSelectionMaxLength
import moe.antimony.hoshi.features.reader.usesDarkInterface
import moe.antimony.hoshi.features.reader.readerJavaScriptStringLiteral
import moe.antimony.hoshi.features.reader.readerLookupPopupFramePayloads
import moe.antimony.hoshi.features.reader.readerLookupPopupIframeUrl
import moe.antimony.hoshi.features.reader.readerLookupPopupTouchBlocksReaderGesture
import moe.antimony.hoshi.features.reader.ReaderLookupPopupIframeSync
import moe.antimony.hoshi.features.reader.ReaderAppearanceSheet
import moe.antimony.hoshi.features.reader.ReaderHardwareKeyAction
import moe.antimony.hoshi.features.reader.ReaderNavigationDirection
import moe.antimony.hoshi.features.reader.readerHardwareKeyEventForKeyEvent
import moe.antimony.hoshi.features.sasayaki.SasayakiSettings
import moe.antimony.hoshi.manga.MangaPageCrop
import moe.antimony.hoshi.manga.MangaVolumeEntry
import moe.antimony.hoshi.manga.MokuroPage
import moe.antimony.hoshi.manga.MokuroVolume
import moe.antimony.hoshi.webview.applyHoshiWebViewSecurityDefaults
import moe.antimony.hoshi.webview.disableNativeOverscrollStretch
import moe.antimony.hoshi.R

/**
 * Manga's WebView orchestration, mirroring the essential (non-EPUB-specific) slice of
 * `features.reader.ReaderWebView`: dictionary popup stack, AI lookup, and export are reused
 * unmodified since they operate purely on `{word, sentence}` strings with zero DOM coupling.
 * No auto-sync, wallpaper, Sasayaki, or statistics -- and no custom restore-token/epoch WebView
 * lifecycle machinery (v1 scope decision): standard Android view recreation only.
 */
@Composable
internal fun MangaReaderWebView(
    volumeId: String,
    volumeEntry: MangaVolumeEntry,
    mokuroVolume: MokuroVolume,
    initialPageIndex: Int,
    readerSettings: ReaderSettings,
    onReaderSettingsChange: (ReaderSettings) -> Unit,
    onReaderKeyEventHandlerChange: (((KeyEvent) -> Boolean)?) -> Unit,
    contentLanguageProfile: ContentLanguageProfile,
    onSaveBookmark: (pageIndex: Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val composeView = LocalView.current
    // Manga pages are full-bleed images, unlike EPUB text which just reflows around a top
    // inset -- leaving the status bar visible crops real page content underneath it. Hidden
    // unconditionally for the reader's lifetime (not tied to any opt-in "focus mode" the way
    // EPUB's is), with swipe-to-reveal-transiently still available for checking notifications.
    DisposableEffect(context, composeView) {
        val activity = context.findActivity()
        val controller = activity?.window?.let { window -> WindowCompat.getInsetsController(window, composeView) }
        val previousBehavior = controller?.systemBarsBehavior
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            if (previousBehavior != null) controller.systemBarsBehavior = previousBehavior
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    val appContainer = LocalHoshiUiDependencies.current
    val dictionaryRepository = appContainer.dictionaryRepository
    val fontManager = appContainer.readerFontManager
    val systemDarkTheme = isSystemInDarkTheme()
    val eInkMode = readerSettings.eInkMode
    val darkInterface = readerSettings.usesDarkInterface(systemDarkTheme)
    // ReaderAppearanceSheet is shared with EPUB and always renders a Sasayaki section regardless
    // of caller -- harmless/inert here (manga has no Sasayaki concept), reusing it as-is rather
    // than forking a manga-only settings screen, per the request for "the same" settings screen.
    val sasayakiSettings by appContainer.sasayakiSettingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = SasayakiSettings(),
    )
    var showAppearance by remember(volumeId) { mutableStateOf(false) }
    // Chrome (back and settings buttons) is toggled by tapping the blank center
    // third of the page (the existing dead zone between the left/right page-turn tap targets),
    // matching the common manga-reader convention of tapping the middle of the screen to reveal
    // reading UI.
    //
    // It starts *visible* and auto-hides, rather than starting hidden: that centre third is only a
    // third of the screen wide and is often covered by speech bubbles (which take the tap for a
    // lookup instead), so a reader who does not already know the gesture has no way to discover
    // that the toolbar -- and therefore the reader's settings at all -- exists.
    var showChrome by remember(volumeId) { mutableStateOf(true) }
    LaunchedEffect(volumeId) {
        delay(ChromeAutoHideDelayMs)
        showChrome = false
    }
    val scope = rememberCoroutineScope()

    // Landscape auto-switches to a 2-page spread (matching how most manga readers handle this --
    // e.g. Tachiyomi/Mihon), portrait shows one page at a time. Using device orientation directly
    // rather than comparing measured viewport dimensions keeps this available at composable-body
    // scope (no need to be inside the BoxWithConstraints below, which several functions here --
    // goToPage, showCurrentPage -- are defined before entering).
    val isSpreadMode = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val spreadGroups = remember(volumeId, isSpreadMode, mokuroVolume.pages.size) {
        if (isSpreadMode) {
            mangaSpreadGroups(mokuroVolume.pages.size)
        } else {
            mokuroVolume.pages.indices.map { listOf(it) }
        }
    }

    var webView by remember { mutableStateOf<WebView?>(null) }
    // A plain remembered box, not Compose state: zoom is purely native-touch-driven UI, and must
    // survive `update` re-running the AndroidView block (which happens on every recomposition,
    // e.g. a settings change) without resetting mid-gesture or after the user has zoomed in.
    val zoomScale = remember(volumeId) { floatArrayOf(1f) }
    // Bumped on every page turn while eInkMode is on to trigger a brief full-black flash (see the
    // overlay below) -- the same "simulate a display refresh on page turn" cue mokuro's own
    // reader and other Boox-focused manga readers use to clear e-ink ghosting between pages.
    var eInkFlashTrigger by remember(volumeId) { mutableIntStateOf(0) }
    // Always a group *start* index (the earlier-read page of a spread pair, or the page itself
    // when alone) -- see spreadGroups above and the goToPage/goToNextGroup/goToPreviousGroup
    // functions below, which are the only things allowed to change this.
    var currentPageIndex by remember(volumeId) {
        val clampedInitial = initialPageIndex.coerceIn(0, (mokuroVolume.pages.size - 1).coerceAtLeast(0))
        val groupIndex = mangaSpreadGroupIndexForPage(spreadGroups, clampedInitial)
        mutableIntStateOf(spreadGroups.getOrNull(groupIndex)?.firstOrNull() ?: clampedInitial)
    }
    var lookupPopups by remember(volumeId) { mutableStateOf<List<LookupPopupItem>>(emptyList()) }
    var readerPopupHistories by remember(volumeId) { mutableStateOf<Map<String, ReaderPopupHistoryCounts>>(emptyMap()) }
    var rootSelectionHighlight by remember(volumeId) { mutableStateOf<ReaderRootSelectionHighlight?>(null) }
    // The character count behind the current root highlight. Kept so the highlight can be
    // re-measured after a zoom/pan -- see refreshRootSelectionHighlight below.
    var rootSelectionHighlightCount by remember(volumeId) { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    val ankiViewModel: AnkiViewModel = hiltViewModel()
    val ankiUiState by ankiViewModel.uiState.collectAsStateWithLifecycle()
    val aiLookupViewModel: AiLookupViewModel = hiltViewModel()
    val aiLookupState by aiLookupViewModel.state.collectAsStateWithLifecycle()
    val cropViewModel: MangaCropViewModel = hiltViewModel()
    LaunchedEffect(volumeId) {
        cropViewModel.bind(volumeEntry.root, mangaSeriesKey(volumeEntry.metadata), mokuroVolume)
    }
    val cropState by cropViewModel.uiState.collectAsStateWithLifecycle()
    var showCropPanel by remember(volumeId) { mutableStateOf(false) }

    var dictionaryStyles by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(dictionaryRepository) {
        dictionaryStyles = withContext(Dispatchers.IO) {
            runCatching {
                dictionaryRepository.ensureLookupQueryReady()
                dictionaryRepository.dictionaryStyles()
            }.getOrDefault(emptyMap())
        }
    }
    val dictionarySettings by appContainer.dictionarySettingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = DictionarySettings(),
    )
    val audioSettings by appContainer.audioSettingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = AudioSettings(),
    )

    fun popupIndex(popupId: String): Int = lookupPopups.indexOfFirst { it.id == popupId }
    fun popupById(popupId: String): LookupPopupItem? = lookupPopups.firstOrNull { it.id == popupId }

    fun setLookupPopups(next: List<LookupPopupItem>) {
        val activeIds = next.mapTo(mutableSetOf()) { it.id }
        readerPopupHistories = readerPopupHistories.filterKeys(activeIds::contains)
        rootSelectionHighlight = rootSelectionHighlight?.takeIf { it.popupId == null || it.popupId in activeIds }
        lookupPopups = next
    }

    fun clearReaderSelection(onCleared: () -> Unit = {}) {
        val current = webView
        if (current == null) {
            onCleared()
        } else {
            current.evaluateJavascript(ReaderSelectionCommand.ClearSelection.source) { onCleared() }
        }
    }

    fun dismissRootLookupPopup() {
        rootSelectionHighlight = null
        setLookupPopups(clearPopupSelectionHighlights(lookupPopups))
        clearReaderSelection()
        setLookupPopups(emptyList())
    }

    fun replyReaderPopupMessage(popupId: String, messageId: String, bodyJson: String) {
        webView?.evaluateJavascript(
            """
                window.hoshiReaderPopupHost &&
                window.hoshiReaderPopupHost.resolveMessage(${readerJavaScriptStringLiteral(popupId)}, ${readerJavaScriptStringLiteral(messageId)}, $bodyJson)
            """.trimIndent(),
            null,
        )
    }

    fun highlightReaderPopupSelection(popupId: String, highlightCount: Int) {
        webView?.evaluateJavascript(
            """
                window.hoshiReaderPopupHost &&
                window.hoshiReaderPopupHost.highlightSelection(${readerJavaScriptStringLiteral(popupId)}, $highlightCount)
            """.trimIndent(),
            null,
        )
    }

    fun popupOptions(isVertical: Boolean): LookupPopupOptions =
        LookupPopupOptions(
            isVertical = isVertical,
            isFullWidth = readerSettings.popupFullWidth,
            width = readerSettings.popupWidth,
            height = readerSettings.popupHeight,
            swipeToDismiss = readerSettings.popupSwipeToDismiss,
            swipeThreshold = readerSettings.popupSwipeThreshold,
            reducedMotionScrolling = readerSettings.popupReducedMotionScrolling,
            reducedMotionScrollPercent = readerSettings.popupReducedMotionScrollPercent,
            reducedMotionSwipeThreshold = readerSettings.popupReducedMotionSwipeThreshold,
            popupScale = readerSettings.popupScale,
            popupActionBar = readerSettings.popupActionBar,
            dictionarySettings = dictionarySettings,
            darkMode = darkInterface,
            eInkMode = eInkMode,
            audioSettings = audioSettings,
            documentTitle = volumeEntry.displayTitle,
            coverPath = null,
            contentLanguageProfile = contentLanguageProfile,
        )

    fun lookupRootPopup(selection: ReaderSelectionData): Pair<LookupPopupItem, Int>? =
        createLookupPopupItem(
            selection = selection,
            dictionaryStyles = dictionaryStyles,
            lookup = dictionaryRepository::lookup,
            options = popupOptions(isVertical = true),
        )

    fun lookupChildPopup(selection: ReaderSelectionData): Pair<LookupPopupItem, Int>? =
        createLookupPopupItem(
            selection = selection,
            dictionaryStyles = dictionaryStyles,
            lookup = dictionaryRepository::lookup,
            options = popupOptions(isVertical = false),
        )

    fun handleTextSelected(
        selection: ReaderSelectionData,
        selectionRects: (Int, (List<ReaderSelectionRect>) -> Unit) -> Unit,
    ) {
        rootSelectionHighlight = null
        setLookupPopups(emptyList())
        if (selection.isSentenceOnly) {
            aiLookupViewModel.requestSentenceBreakdown(selection.sentence)
            return
        }
        val lookup = lookupRootPopup(selection) ?: return
        val (popup, highlightCount) = lookup
        rootSelectionHighlight = ReaderRootSelectionHighlight(popupId = popup.id, rects = null)
        rootSelectionHighlightCount = highlightCount
        setLookupPopups(listOf(popup))
        selectionRects(highlightCount) { rects ->
            if (lookupPopups.none { it.id == popup.id }) return@selectionRects
            val displayRects = rects.ifEmpty { listOf(popup.state.selection.rect) }
            val anchor = displayRects.firstOrNull()
            if (anchor != null) {
                setLookupPopups(
                    lookupPopups.map { existing ->
                        if (existing.id == popup.id) {
                            existing.copy(state = existing.state.copy(selection = existing.state.selection.copy(rect = anchor)))
                        } else {
                            existing
                        }
                    },
                )
            }
            rootSelectionHighlight = ReaderRootSelectionHighlight(popupId = popup.id, rects = displayRects)
        }
    }

    fun handleReaderPopupBridgeMessage(message: ReaderLookupPopupBridgeMessage) {
        when (message) {
            is ReaderLookupPopupBridgeMessage.OpenLink -> context.openPopupExternalLink(message.url)
            is ReaderLookupPopupBridgeMessage.TapOutside -> {
                val index = popupIndex(message.popupId).takeIf { it >= 0 } ?: return
                setLookupPopups(closeChildPopupsAndClearSelection(lookupPopups, index))
            }
            is ReaderLookupPopupBridgeMessage.SwipeDismiss -> {
                val index = popupIndex(message.popupId).takeIf { it >= 0 } ?: return
                if (index == 0) dismissRootLookupPopup() else setLookupPopups(dismissPopupAt(lookupPopups, index))
            }
            is ReaderLookupPopupBridgeMessage.TextSelected -> {
                val index = popupIndex(message.popupId).takeIf { it >= 0 } ?: return
                if (message.selection.isSentenceOnly) {
                    setLookupPopups(emptyList())
                    aiLookupViewModel.requestSentenceBreakdown(message.selection.sentence)
                    return
                }
                val nextPopups = lookupPopups.take(index + 1)
                val lookup = lookupChildPopup(message.selection)
                if (lookup == null) {
                    highlightReaderPopupSelection(message.popupId, 0)
                    return
                }
                val (childPopup, highlightCount) = lookup
                setLookupPopups(nextPopups + childPopup)
                highlightReaderPopupSelection(message.popupId, highlightCount)
            }
            is ReaderLookupPopupBridgeMessage.PlayWordAudio -> {
                WordAudioPlayer.get(context).play(message.url, message.mode)
            }
            is ReaderLookupPopupBridgeMessage.MineEntry -> {
                val popup = popupById(message.popupId) ?: return
                val messageId = message.messageId ?: return
                ankiViewModel.mineEntryAsync(message.formatId, message.payloadJson, popup.state.ankiContext) { mined ->
                    replyReaderPopupMessage(message.popupId, messageId, mined.toString())
                }
            }
            is ReaderLookupPopupBridgeMessage.DuplicateCheck -> {
                ankiViewModel.duplicateStatesAsync(message.valuesByHandlebar) { states ->
                    val messageId = message.messageId ?: return@duplicateStatesAsync
                    replyReaderPopupMessage(message.popupId, messageId, moe.antimony.hoshi.features.reader.readerPopupBooleanMapJson(states))
                }
            }
            is ReaderLookupPopupBridgeMessage.ShowNotes -> {
                ankiViewModel.showNotesAsync(message.formatId, message.valuesByHandlebar) { shown ->
                    val messageId = message.messageId ?: return@showNotesAsync
                    replyReaderPopupMessage(message.popupId, messageId, shown.toString())
                }
            }
            is ReaderLookupPopupBridgeMessage.LookupRedirect -> {
                val popup = popupById(message.popupId) ?: return
                val results = dictionaryRepository.lookup(
                    message.query,
                    popup.state.dictionarySettings.maxResults,
                    popup.state.dictionarySettings.scanLength,
                )
                if (results.isNotEmpty()) {
                    setLookupPopups(
                        lookupPopups.map { existing ->
                            if (existing.id == message.popupId) existing.copy(state = existing.state.copy(results = results)) else existing
                        },
                    )
                    val current = readerPopupHistories[message.popupId] ?: ReaderPopupHistoryCounts()
                    readerPopupHistories = readerPopupHistories + (message.popupId to current.copy(backCount = current.backCount + 1, forwardCount = 0))
                }
                replyReaderPopupMessage(message.popupId, message.messageId ?: return, results.size.toString())
            }
            is ReaderLookupPopupBridgeMessage.GetEntry -> {
                val entry = popupById(message.popupId)?.state?.results?.getOrNull(message.index)
                val body = entry?.let(LookupPopupHtml::entryJsonString) ?: "null"
                replyReaderPopupMessage(message.popupId, message.messageId ?: return, body)
            }
            is ReaderLookupPopupBridgeMessage.PopupScrolled -> {
                val index = popupIndex(message.popupId).takeIf { it >= 0 } ?: return
                setLookupPopups(closeChildPopupsForScrolledParent(lookupPopups, index))
            }
            is ReaderLookupPopupBridgeMessage.ScrollState -> Unit
            is ReaderLookupPopupBridgeMessage.NavigateBack -> {
                val current = readerPopupHistories[message.popupId] ?: return
                if (current.backCount > 0) {
                    readerPopupHistories = readerPopupHistories + (message.popupId to current.copy(backCount = current.backCount - 1, forwardCount = current.forwardCount + 1))
                }
            }
            is ReaderLookupPopupBridgeMessage.NavigateForward -> {
                val current = readerPopupHistories[message.popupId] ?: return
                if (current.forwardCount > 0) {
                    readerPopupHistories = readerPopupHistories + (message.popupId to current.copy(backCount = current.backCount + 1, forwardCount = current.forwardCount - 1))
                }
            }
            is ReaderLookupPopupBridgeMessage.ContentReady -> Unit
            is ReaderLookupPopupBridgeMessage.SasayakiReplayCue,
            is ReaderLookupPopupBridgeMessage.SasayakiTogglePlayback,
            is ReaderLookupPopupBridgeMessage.SasayakiPlayForward,
            -> Unit
            is ReaderLookupPopupBridgeMessage.AskAi -> {
                val selection = popupById(message.popupId)?.state?.selection ?: return
                aiLookupViewModel.askWordNuance(word = message.word, sentence = selection.sentence)
            }
            is ReaderLookupPopupBridgeMessage.ExportWord -> {
                val selection = popupById(message.popupId)?.state?.selection ?: return
                context.shareWordExport(WordExport(word = message.word, sentence = selection.sentence))
            }
            is ReaderLookupPopupBridgeMessage.MarkKnown -> {
                if (message.expression.isEmpty()) return
                scope.launch { appContainer.knownWordsRepository.markKnown(message.expression, message.reading) }
            }
        }
    }

    val readerPopupBridgeHolder = remember { ReaderLookupPopupBridgeCallbackHolder() }
    val currentHandleReaderPopupBridgeMessage = rememberUpdatedState(::handleReaderPopupBridgeMessage)
    LaunchedEffect(readerPopupBridgeHolder) {
        readerPopupBridgeHolder.callbacks = ReaderLookupPopupBridgeCallbacks { message ->
            currentHandleReaderPopupBridgeMessage.value(message)
        }
    }

    val popupAssets = remember(context) { LookupPopupAssets.load(context) }
    val readerPopupIframeDocument = remember(
        dictionaryStyles, dictionarySettings, readerSettings, darkInterface, eInkMode, audioSettings, contentLanguageProfile,
    ) {
        LookupPopupHtml.renderIframeDocument(
            assets = null,
            dictionaryStyles = dictionaryStyles,
            settings = dictionarySettings,
            swipeToDismiss = readerSettings.popupSwipeToDismiss,
            swipeThreshold = readerSettings.popupSwipeThreshold,
            reducedMotionScrolling = readerSettings.popupReducedMotionScrolling,
            reducedMotionScrollPercent = readerSettings.popupReducedMotionScrollPercent,
            reducedMotionSwipeThreshold = readerSettings.popupReducedMotionSwipeThreshold,
            darkMode = darkInterface,
            eInkMode = eInkMode,
            audioSettings = audioSettings,
            ankiSettings = ankiUiState.popupSettings,
            fontFaceCss = fontManager.popupFontFaceCss(),
            popupScale = readerSettings.popupScale,
            contentLanguageProfile = contentLanguageProfile,
            aiLookupEnabled = true,
            markKnownEnabled = true,
        )
    }
    val currentReaderPopupIframeDocument = rememberUpdatedState(readerPopupIframeDocument)
    val readerPopupIframeUrl = remember(readerPopupIframeDocument) {
        readerLookupPopupIframeUrl(readerPopupIframeDocument.hashCode())
    }
    LaunchedEffect(webView, readerPopupIframeUrl) {
        webView?.evaluateJavascript(
            """
                (function() {
                  var iframeUrl = ${readerJavaScriptStringLiteral(readerPopupIframeUrl)};
                  window.__hoshiReaderPopupIframeUrl = iframeUrl;
                  if (window.hoshiReaderPopupHost) {
                    window.hoshiReaderPopupHost.preloadIdleRootFrame(iframeUrl);
                  }
                })();
            """.trimIndent(),
            null,
        )
    }
    val readerPopupResourceHandler = remember(context, popupAssets, fontManager, dictionaryRepository) {
        ReaderLookupPopupResourceHandler(
            context = context.applicationContext,
            assets = popupAssets,
            fontManager = fontManager,
            audioRequestHandler = AudioRequestHandler(LocalAudioRepository.fromContext(context.applicationContext)),
            imageRequestHandler = DictionaryImageRequestHandler(dictionaryRepository::dictionaryMedia),
            iframeDocument = { currentReaderPopupIframeDocument.value },
        )
    }
    val themedLookupPopups = remember(lookupPopups, darkInterface, eInkMode, audioSettings, readerSettings.popupScale) {
        lookupPopups.withLookupPopupVisualOptions(
            darkMode = darkInterface,
            eInkMode = eInkMode,
            audioSettings = audioSettings,
            popupScale = readerSettings.popupScale,
        )
    }

    val mangaResourceBridge = remember(volumeEntry) {
        MangaWebResourceBridge { relativePath ->
            volumeEntry.root.resolve("pages").resolve(relativePath).takeIf { it.isFile }
        }
    }

    val eInkLineColorCss = if (darkInterface) "#fff" else "#000"
    val bootstrapScript = remember(contentLanguageProfile, eInkMode, mangaReadingDirectionRtlDefault(), readerSettings, darkInterface) {
        mangaReaderSetupScript(
            contentLanguageProfile = contentLanguageProfile,
            eInkMode = eInkMode,
            rtl = mangaReadingDirectionRtlDefault(),
            backgroundColorCss = readerJavaScriptColorCss(readerSettings.backgroundColor(systemDarkTheme)),
            textColorCss = readerSettings.textColorCss(systemDarkTheme),
            eInkLineColorCss = eInkLineColorCss,
            assets = MangaReaderWebAssets.load(context),
        )
    }
    val currentBootstrapScript = rememberUpdatedState(bootstrapScript)

    // The crop each page of the shown group was last laid out with, so a crop-state change that
    // doesn't affect the visible page (e.g. detection finishing while cropping is Off) doesn't
    // needlessly re-render it -- a plain holder like zoomScale, not Compose state.
    val lastShownCrops = remember(volumeId) { arrayOfNulls<Pair<Int, List<MangaPageCrop>>>(1) }

    // `group` is in reading order (ascending page index). Kotlin resolves RTL into final
    // left-to-right *visual* order here, once, so reader-manga.js's showPage stays a plain
    // "lay these out left to right" renderer with no reading-direction concept of its own --
    // for RTL, the earlier-read page displays on the right, i.e. visual order is reversed.
    fun currentGroupVisualOrder(): List<Int>? {
        val groupIndex = mangaSpreadGroupIndexForPage(spreadGroups, currentPageIndex)
        val group = spreadGroups.getOrNull(groupIndex) ?: return null
        return if (mangaReadingDirectionRtlDefault()) group.reversed() else group
    }

    fun currentGroupCrops(visualOrder: List<Int>): List<MangaPageCrop> = visualOrder.map { index ->
        val page = mokuroVolume.pages[index]
        cropState.cropForPage(index, isWide = page.imgWidth > page.imgHeight)
    }

    fun showCurrentPage(view: WebView) {
        val visualOrder = currentGroupVisualOrder() ?: return
        val pages = visualOrder.map { mokuroVolume.pages[it] }
        val pagesJson = pages.map { Json.encodeToString(MokuroPage.serializer(), it) }
        val imageUrls = pages.map { page ->
            "https://appassets.androidplatform.net/manga/pages/" +
                page.imgPath.split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8") }
        }
        val crops = currentGroupCrops(visualOrder)
        lastShownCrops[0] = currentPageIndex to crops
        view.evaluateJavascript(mangaShowPageScript(currentPageIndex, pagesJson, imageUrls, crops), null)
    }

    /**
     * The root selection highlight is drawn by the popup host layer, which sits *outside*
     * `.hoshi-manga-page` and therefore does not receive the page's zoom/pan CSS transform. Its
     * rects are viewport coordinates measured once, at selection time, so any pan or zoom leaves
     * the box behind -- it appears pinned near the popup while the word it belongs to slides away.
     *
     * Rather than teach the popup layer about the page transform, the rects are simply re-measured
     * from the live DOM once the gesture settles. `hide` is used during the gesture itself: an
     * empty rect list draws nothing, whereas a null list would mark the highlight *pending*, which
     * the host treats as "still loading" and uses to block the popup from being revealed.
     */
    fun refreshRootSelectionHighlight(hide: Boolean) {
        val highlight = rootSelectionHighlight ?: return
        val popupId = highlight.popupId
        if (hide) {
            rootSelectionHighlight = ReaderRootSelectionHighlight(popupId = popupId, rects = emptyList())
            return
        }
        val view = webView ?: return
        val count = rootSelectionHighlightCount
        if (count <= 0) return
        // Deferred past the settle transition in reader-manga.css: measuring the instant the
        // gesture ends reads the page's pre-animation position, putting the box back in the wrong
        // place for a second time.
        view.postDelayed(
            {
                view.evaluateJavascript(ReaderSelectionCommand.SelectionRects(count).source) { result ->
                    if (lookupPopups.none { it.id == popupId }) return@evaluateJavascript
                    val rects = ReaderSelectionBridgePayload.rectsFromJavascriptResult(result)
                    if (rects.isEmpty()) return@evaluateJavascript
                    rootSelectionHighlight = ReaderRootSelectionHighlight(popupId = popupId, rects = rects)
                }
            },
            PageSettleTransitionMs,
        )
    }

    fun resetZoomToFit(view: WebView) {
        zoomScale[0] = 1f
        view.evaluateJavascript("window.hoshiManga && window.hoshiManga.resetZoom(true);", null)
    }

    fun goToPage(index: Int) {
        val clampedIndex = index.coerceIn(0, mokuroVolume.pages.lastIndex.coerceAtLeast(0))
        // Always snap to the containing group's start -- the position that actually matters is
        // "which spread is showing", and every caller (tap zones, swipes, key nav) already thinks
        // in terms of group starts via goToNextGroup/goToPreviousGroup below; this just makes
        // goToPage robust if ever called with a page in the middle of a pair.
        val groupIndex = mangaSpreadGroupIndexForPage(spreadGroups, clampedIndex)
        val clamped = spreadGroups.getOrNull(groupIndex)?.firstOrNull() ?: clampedIndex
        if (clamped == currentPageIndex) return
        currentPageIndex = clamped
        // showCurrentPage/showPage always resets the JS-side zoom for the new page -- keep this
        // in sync so isZoomed() doesn't keep reporting zoomed (and routing touches to pan instead
        // of normal tap/swipe) on a page that's actually back at 1x.
        zoomScale[0] = 1f
        if (eInkMode) eInkFlashTrigger++
        dismissRootLookupPopup()
        webView?.let(::showCurrentPage)
        onSaveBookmark(clamped)
    }

    fun goToNextGroup() {
        val groupIndex = mangaSpreadGroupIndexForPage(spreadGroups, currentPageIndex)
        val nextStart = spreadGroups.getOrNull(groupIndex + 1)?.firstOrNull() ?: return
        goToPage(nextStart)
    }

    fun goToPreviousGroup() {
        val groupIndex = mangaSpreadGroupIndexForPage(spreadGroups, currentPageIndex)
        val previousStart = spreadGroups.getOrNull(groupIndex - 1)?.firstOrNull() ?: return
        goToPage(previousStart)
    }

    // Rotating the device changes spreadGroups (single pages <-> paired spreads) but doesn't by
    // itself re-render the WebView -- without this, the display would keep showing the old
    // grouping until the next explicit page turn. Also re-snaps currentPageIndex to its new
    // group's start (a page that was a spread's second half in landscape, say, becomes its own
    // group start after rotating to portrait).
    LaunchedEffect(spreadGroups) {
        val groupIndex = mangaSpreadGroupIndexForPage(spreadGroups, currentPageIndex)
        val snapped = spreadGroups.getOrNull(groupIndex)?.firstOrNull() ?: currentPageIndex
        if (snapped != currentPageIndex) currentPageIndex = snapped
        webView?.let(::showCurrentPage)
    }

    // Re-lays out the visible page in place when its crop changes: a mode switch, a manual step,
    // or Auto detection finishing after the page was first shown uncropped.
    LaunchedEffect(cropState) {
        val view = webView ?: return@LaunchedEffect
        val visualOrder = currentGroupVisualOrder() ?: return@LaunchedEffect
        if (lastShownCrops[0] == (currentPageIndex to currentGroupCrops(visualOrder))) return@LaunchedEffect
        zoomScale[0] = 1f
        // The overlay is rebuilt, so an open lookup's highlight would point at stale elements.
        dismissRootLookupPopup()
        showCurrentPage(view)
    }

    // Reuses EPUB's exact hardware-key logic (readerHardwareKeyEventForKeyEvent) rather than
    // reimplementing volume-key/page-key handling: physical page-turn buttons on devices like the
    // Boox Go 7 report as KEYCODE_VOLUME_UP/DOWN at the Android input level, and without consuming
    // that event here first (via Activity.dispatchKeyEvent, wired through
    // onReaderKeyEventHandlerChange) the system's default volume-adjustment handling runs instead.
    // Forward/Backward always mean "next/previous group" -- RTL only changes which screen side a
    // swipe or tap-zone maps to those, not the navigation direction itself (see onTap below).
    val currentReaderKeyHandler = rememberUpdatedState<(KeyEvent) -> Boolean> { event ->
        val result = readerHardwareKeyEventForKeyEvent(
            keyCode = event.keyCode,
            action = event.action,
            repeatCount = event.repeatCount,
            settings = readerSettings,
            sasayakiEnabled = false,
            hasSasayakiAudio = false,
        )
        if (!result.consumed) return@rememberUpdatedState false
        (result.action as? ReaderHardwareKeyAction.ReaderNavigation)?.let { navigation ->
            when (navigation.direction) {
                ReaderNavigationDirection.Forward -> goToNextGroup()
                ReaderNavigationDirection.Backward -> goToPreviousGroup()
            }
        }
        true
    }
    DisposableEffect(onReaderKeyEventHandlerChange) {
        onReaderKeyEventHandlerChange { event -> currentReaderKeyHandler.value(event) }
        onDispose { onReaderKeyEventHandlerChange(null) }
    }

    /**
     * `onBlankTap` fires only for a tap that was genuinely nowhere near OCR text while no block was
     * revealed. A tap on or within snapping distance of an OCR block -- which reveals it, or looks
     * up text in the already-revealed one -- and a tap that hides the revealed block are handled
     * entirely inside the WebView (see `selectTextAt` in `reader-manga.js`) and must not fall
     * through to the blank-tap behaviour, or they would also reset the zoom or turn the page.
     */
    fun selectAt(x: Float, y: Float, sentenceOnly: Boolean = false, onBlankTap: () -> Unit) {
        val view = webView ?: return
        val density = view.resources.displayMetrics.density
        view.evaluateJavascript(
            mangaSelectTextScript(
                x = androidPixelsToCssPixels(x, density),
                y = androidPixelsToCssPixels(y, density),
                maxLength = readerSelectionMaxLength(dictionarySettings),
                sentenceOnly = sentenceOnly,
            ),
        ) { result ->
            val selectionResult = MangaSelectionResult.fromWebViewResult(result)
            when {
                // The popup belonged to the block that was just revealed over or hidden.
                selectionResult.blockToggled -> dismissRootLookupPopup()
                selectionResult.isImageTap || selectionResult.isLinkTap || selectionResult.nearText -> Unit
                selectionResult.selectedNothing -> onBlankTap()
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    // The popup shell's own size/position math (LookupPopupLayout, Kotlin-side) needs the real
    // viewport in dp -- a hardcoded ReaderLookupPopupViewport(0.0, 0.0) placeholder here silently
    // collapsed every popup to ~0 height (visible in the DOM, on-screen invisible), which is what
    // produced "opens nothing" for every tap despite the underlying selection/lookup succeeding.
    val popupViewportWidthDp = maxWidth.value.toDouble()
    val popupViewportHeightDp = maxHeight.value.toDouble()

    // Must share the same real viewport as popupPayloads below -- this separate zero-sized
    // viewport previously made every popup frame's computed bounds collapse to ~0, so a tap
    // anywhere (including on a popup's close button, or a swipe meant to dismiss it) never read
    // as "inside the popup" and fell through to the reader's own tap/swipe handling underneath.
    val currentShouldBlockOnPopup = rememberUpdatedState<(MotionEvent) -> Boolean> { event ->
        val view = webView ?: return@rememberUpdatedState false
        val density = view.resources.displayMetrics.density
        val payloads = readerLookupPopupFramePayloads(
            popups = themedLookupPopups,
            histories = readerPopupHistories,
            viewport = ReaderLookupPopupViewport(width = popupViewportWidthDp, height = popupViewportHeightDp),
            sasayakiWasPaused = false,
            sasayakiIsPlaying = false,
            iframeUrl = readerPopupIframeUrl,
            rootSelectionHighlight = null,
        )
        readerLookupPopupTouchBlocksReaderGesture(
            popups = payloads,
            x = androidPixelsToCssPixels(event.x, density).toDouble(),
            y = androidPixelsToCssPixels(event.y, density).toDouble(),
        )
    }

    AndroidView(
        modifier = Modifier.fillMaxSize().background(Color(readerSettings.backgroundColor(systemDarkTheme))),
        factory = { ctx ->
            WebView(ctx).apply {
                val webViewInstance = this
                applyHoshiWebViewSecurityDefaults()
                disableNativeOverscrollStretch()
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                addJavascriptInterface(
                    ReaderSelectionBridge(this) { selection, selectionRects -> handleTextSelected(selection, selectionRects) },
                    "HoshiTextSelection",
                )
                ReaderLookupPopupWebBridge.install(this, readerPopupBridgeHolder)
                webViewClient = MangaWebViewClient(
                    mangaResourceBridge = mangaResourceBridge,
                    popupResourceHandler = { readerPopupResourceHandler },
                    onPageFinished = { view ->
                        // Must run in this order: the bootstrap script defines window.hoshiManga
                        // (and hoshiSelection/CSS); showCurrentPage's evaluateJavascript call
                        // depends on it already existing on the loaded document. Evaluating the
                        // bootstrap from AndroidView's `update` block instead (as originally
                        // written) raced the async page load and silently lost the script against
                        // a not-yet-loaded document -- window.hoshiManga was never defined, so
                        // showPage's `window.hoshiManga && ...` guard no-opped with zero errors,
                        // producing a black screen.
                        view.evaluateJavascript(currentBootstrapScript.value, null)
                        showCurrentPage(view)
                    },
                )
                // Built once here rather than in AndroidView's `update` block: `update` re-runs on
                // every recomposition (e.g. any dictionary/AI state change), and setOnTouchListener
                // with a freshly-constructed listener object discards that object's in-flight
                // gesture state -- mid-pinch `pinching`/`panning` flags reset (read as a stray
                // single-finger drag, sometimes crossing the swipe threshold and firing an
                // unintended page turn) and the pending-tap half of a double-tap is silently
                // dropped (its ReaderTapDispatcher never sees the second tap that would have
                // paired with it), so double-tap never fired at all. All values read below are
                // State-backed (remember/collectAsState/rememberUpdatedState), so this single
                // long-lived closure still always observes their current values at touch time.
                setOnTouchListener(
                    object : MangaPageTouchListener(ctx, swipeDistance = 60f) {
                        override fun isZoomed(): Boolean = zoomScale[0] > 1.001f

                        override fun onZoom(scaleFactor: Float, focalX: Float, focalY: Float, panDx: Float, panDy: Float, settling: Boolean) {
                            zoomScale[0] = (zoomScale[0] * scaleFactor).coerceIn(1f, 6f)
                            refreshRootSelectionHighlight(hide = true)
                            val density = webViewInstance.resources.displayMetrics.density
                            webViewInstance.evaluateJavascript(
                                "window.hoshiManga && window.hoshiManga.setZoom(" +
                                    "${zoomScale[0]}, ${androidPixelsToCssPixels(focalX, density)}, " +
                                    "${androidPixelsToCssPixels(focalY, density)}, " +
                                    "${androidPixelsToCssPixels(panDx, density)}, ${androidPixelsToCssPixels(panDy, density)}, $settling);",
                                null,
                            )
                        }

                        override fun onZoomGestureEnd() {
                            if (zoomScale[0] <= 1.05f) resetZoomToFit(webViewInstance)
                            refreshRootSelectionHighlight(hide = false)
                        }

                        override fun onPan(dx: Float, dy: Float, settling: Boolean) {
                            refreshRootSelectionHighlight(hide = !settling)
                            val density = webViewInstance.resources.displayMetrics.density
                            webViewInstance.evaluateJavascript(
                                "window.hoshiManga && window.hoshiManga.panBy(" +
                                    "${androidPixelsToCssPixels(dx, density)}, ${androidPixelsToCssPixels(dy, density)}, $settling);",
                                null,
                            )
                        }

                        override fun shouldIgnoreReaderGesture(event: MotionEvent): Boolean =
                            super.shouldIgnoreReaderGesture(event) || currentShouldBlockOnPopup.value(event)

                        override fun shouldSuppressPan(event: MotionEvent): Boolean =
                            currentShouldBlockOnPopup.value(event)

                        override fun onTap(x: Float, y: Float) {
                            selectAt(x, y) {
                                if (isZoomed()) {
                                    // Page turns while zoomed are double-tap only now (see
                                    // onDoubleTap) -- a single blank tap instead resets back to
                                    // fit-page, since panning/zooming otherwise had no "get back
                                    // to normal" gesture of its own.
                                    resetZoomToFit(webViewInstance)
                                    return@selectAt
                                }
                                val rtl = mangaReadingDirectionRtlDefault()
                                val third = webViewInstance.width / 3f
                                when {
                                    x < third -> if (rtl) goToNextGroup() else goToPreviousGroup()
                                    x > third * 2 -> if (rtl) goToPreviousGroup() else goToNextGroup()
                                    else -> showChrome = !showChrome
                                }
                            }
                        }

                        override fun onDoubleTap(x: Float, y: Float) {
                            // Double-tap on a sentence opens its breakdown; double-tap on blank
                            // space (most useful while zoomed in, where a one-finger drag pans
                            // instead of swiping) turns the page instead -- left half = next,
                            // right half = back, matching the existing RTL-aware tap-zone
                            // direction used for single-tap page turns.
                            selectAt(x, y, sentenceOnly = true) {
                                val rtl = mangaReadingDirectionRtlDefault()
                                val half = webViewInstance.width / 2f
                                if (x < half) {
                                    if (rtl) goToNextGroup() else goToPreviousGroup()
                                } else {
                                    if (rtl) goToPreviousGroup() else goToNextGroup()
                                }
                            }
                        }

                        override fun onLeftSwipe() {
                            val rtl = mangaReadingDirectionRtlDefault()
                            if (rtl) goToPreviousGroup() else goToNextGroup()
                        }

                        override fun onRightSwipe() {
                            val rtl = mangaReadingDirectionRtlDefault()
                            if (rtl) goToNextGroup() else goToPreviousGroup()
                        }
                    },
                )
                webView = this
                loadDataWithBaseURL(
                    "https://appassets.androidplatform.net/manga/",
                    "<!DOCTYPE html><html><head></head><body></body></html>",
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
        onRelease = { view ->
            if (webView == view) webView = null
        },
    )

    if (showChrome) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.35f), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = Color.White,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { showCropPanel = true },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.35f), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Crop,
                        contentDescription = stringResource(R.string.manga_crop_action),
                        tint = Color.White,
                    )
                }
                IconButton(
                    onClick = { showAppearance = true },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.35f), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = stringResource(R.string.reader_menu),
                        tint = Color.White,
                    )
                }
            }
        }
    }

    if (showCropPanel) {
        MangaCropPanel(
            state = cropState,
            onModeChange = cropViewModel::setMode,
            onAdjust = cropViewModel::adjust,
            onResetToDetected = cropViewModel::resetManualToDetected,
            onDismiss = { showCropPanel = false },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (eInkMode) {
        var eInkFlashVisible by remember(volumeId) { mutableStateOf(false) }
        LaunchedEffect(eInkFlashTrigger) {
            if (eInkFlashTrigger == 0) return@LaunchedEffect
            // A hard on/off cut, not an eased fade -- matches the "no animations" e-ink principle
            // already used for page transitions elsewhere in this reader (a smooth cross-fade
            // would itself be an animation an e-ink panel can't render smoothly).
            eInkFlashVisible = true
            delay(EInkFlashDurationMs)
            eInkFlashVisible = false
        }
        if (eInkFlashVisible) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        }
    }

    AiLookupOverlay(
        state = aiLookupState,
        onConfirmSentenceBreakdown = aiLookupViewModel::confirmSentenceBreakdown,
        onDismiss = aiLookupViewModel::dismiss,
        onRetry = aiLookupViewModel::retry,
        modifier = Modifier.fillMaxSize(),
    )

    val popupPayloads = remember(themedLookupPopups, readerPopupHistories, readerPopupIframeUrl, popupViewportWidthDp, popupViewportHeightDp) {
        readerLookupPopupFramePayloads(
            popups = themedLookupPopups,
            histories = readerPopupHistories,
            viewport = ReaderLookupPopupViewport(width = popupViewportWidthDp, height = popupViewportHeightDp),
            sasayakiWasPaused = false,
            sasayakiIsPlaying = false,
            iframeUrl = readerPopupIframeUrl,
            rootSelectionHighlight = rootSelectionHighlight,
        )
    }
    ReaderLookupPopupIframeSync(
        webView = webView,
        payloads = popupPayloads,
        rootHighlight = rootSelectionHighlight?.let { highlight ->
            ReaderLookupPopupRootHighlightPayload.fromReaderRects(
                popupId = highlight.popupId,
                rects = highlight.rects,
                darkMode = darkInterface,
                eInkMode = eInkMode,
                verticalWriting = true,
            )
        },
    )

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.fillMaxSize(),
    )

    if (showAppearance) {
        ReaderAppearanceSheet(
            settings = readerSettings,
            onSettingsChange = onReaderSettingsChange,
            sasayakiSettings = sasayakiSettings,
            onSasayakiSettingsChange = { newSettings ->
                scope.launch { appContainer.sasayakiSettingsRepository.update { newSettings } }
            },
            fontManager = fontManager,
            onRecalculateFurigana = null,
            onImportKnownWords = null,
            onDismiss = { showAppearance = false },
        )
    }
    }
}

private fun readerJavaScriptColorCss(argb: Long): String {
    val alpha = (argb shr 24) and 0xFF
    val red = (argb shr 16) and 0xFF
    val green = (argb shr 8) and 0xFF
    val blue = argb and 0xFF
    return "rgba($red, $green, $blue, ${alpha / 255.0})"
}

// Right-to-left is the standard convention for Japanese manga page order. Replaced by a real
// MangaReaderSettings-backed value in a later settings pass; hardcoded here since that
// repository doesn't exist yet.
private fun mangaReadingDirectionRtlDefault(): Boolean = true

private const val EInkFlashDurationMs = 120L

// How long the reader chrome stays up when a volume is opened, before auto-hiding.
private const val ChromeAutoHideDelayMs = 3500L

// Matches the `.hoshi-manga-page-settling` transform transition in reader-manga.css, plus a frame.
private const val PageSettleTransitionMs = 160L

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
