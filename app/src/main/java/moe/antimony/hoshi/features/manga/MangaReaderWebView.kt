package moe.antimony.hoshi.features.manga

import android.view.MotionEvent
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
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
import moe.antimony.hoshi.manga.MangaVolumeEntry
import moe.antimony.hoshi.manga.MokuroPage
import moe.antimony.hoshi.manga.MokuroVolume
import moe.antimony.hoshi.webview.applyHoshiWebViewSecurityDefaults
import moe.antimony.hoshi.webview.disableNativeOverscrollStretch

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
    contentLanguageProfile: ContentLanguageProfile,
    onSaveBookmark: (pageIndex: Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContainer = LocalHoshiUiDependencies.current
    val dictionaryRepository = appContainer.dictionaryRepository
    val fontManager = appContainer.readerFontManager
    val systemDarkTheme = isSystemInDarkTheme()
    val eInkMode = readerSettings.eInkMode
    val darkInterface = readerSettings.usesDarkInterface(systemDarkTheme)

    var webView by remember { mutableStateOf<WebView?>(null) }
    // A plain remembered box, not Compose state: zoom is purely native-touch-driven UI, and must
    // survive `update` re-running the AndroidView block (which happens on every recomposition,
    // e.g. a settings change) without resetting mid-gesture or after the user has zoomed in.
    val zoomScale = remember(volumeId) { floatArrayOf(1f) }
    var currentPageIndex by remember(volumeId) {
        mutableIntStateOf(initialPageIndex.coerceIn(0, (mokuroVolume.pages.size - 1).coerceAtLeast(0)))
    }
    var lookupPopups by remember(volumeId) { mutableStateOf<List<LookupPopupItem>>(emptyList()) }
    var readerPopupHistories by remember(volumeId) { mutableStateOf<Map<String, ReaderPopupHistoryCounts>>(emptyMap()) }
    var rootSelectionHighlight by remember(volumeId) { mutableStateOf<ReaderRootSelectionHighlight?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val ankiViewModel: AnkiViewModel = hiltViewModel()
    val ankiUiState by ankiViewModel.uiState.collectAsStateWithLifecycle()
    val aiLookupViewModel: AiLookupViewModel = hiltViewModel()
    val aiLookupState by aiLookupViewModel.state.collectAsStateWithLifecycle()

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
            eInkLineColorCss = eInkLineColorCss,
            assets = MangaReaderWebAssets.load(context),
        )
    }

    fun showCurrentPage(view: WebView) {
        val page = mokuroVolume.pages.getOrNull(currentPageIndex) ?: return
        val pageJson = Json.encodeToString(MokuroPage.serializer(), page)
        val imageUrl = "https://appassets.androidplatform.net/manga/pages/" +
            page.imgPath.split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8") }
        view.evaluateJavascript(mangaShowPageScript(currentPageIndex, pageJson, imageUrl), null)
    }

    fun goToPage(index: Int) {
        val clamped = index.coerceIn(0, mokuroVolume.pages.lastIndex.coerceAtLeast(0))
        if (clamped == currentPageIndex) return
        currentPageIndex = clamped
        dismissRootLookupPopup()
        webView?.let(::showCurrentPage)
        onSaveBookmark(clamped)
    }

    fun selectAt(x: Float, y: Float, onBlankTap: () -> Unit) {
        val view = webView ?: return
        val density = view.resources.displayMetrics.density
        view.evaluateJavascript(
            ReaderSelectionCommand.SelectText(
                x = androidPixelsToCssPixels(x, density),
                y = androidPixelsToCssPixels(y, density),
                maxLength = readerSelectionMaxLength(dictionarySettings),
            ).source,
        ) { result ->
            val selectionResult = moe.antimony.hoshi.features.reader.ReaderSelectionResult.fromWebViewResult(result)
            when {
                selectionResult.isImageTap || selectionResult.isLinkTap -> Unit
                selectionResult.selectedNothing -> onBlankTap()
            }
        }
    }

    val currentShouldBlockOnPopup = rememberUpdatedState<(MotionEvent) -> Boolean> { event ->
        val view = webView ?: return@rememberUpdatedState false
        val density = view.resources.displayMetrics.density
        val payloads = readerLookupPopupFramePayloads(
            popups = themedLookupPopups,
            histories = readerPopupHistories,
            viewport = ReaderLookupPopupViewport(width = 0.0, height = 0.0),
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
        modifier = modifier.background(Color(readerSettings.backgroundColor(systemDarkTheme))),
        factory = { ctx ->
            WebView(ctx).apply {
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
                    onPageFinished = { view -> showCurrentPage(view) },
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
        update = { view ->
            view.evaluateJavascript(bootstrapScript, null)
            view.setOnTouchListener(
                object : MangaPageTouchListener(view.context, swipeDistance = 60f) {
                    override fun isZoomed(): Boolean = zoomScale[0] > 1.001f

                    override fun onZoom(scaleFactor: Float, focalX: Float, focalY: Float, settling: Boolean) {
                        val density = view.resources.displayMetrics.density
                        zoomScale[0] = (zoomScale[0] * scaleFactor).coerceIn(1f, 6f)
                        view.evaluateJavascript(
                            "window.hoshiManga && window.hoshiManga.setZoom(" +
                                "${zoomScale[0]}, ${androidPixelsToCssPixels(focalX, density)}, " +
                                "${androidPixelsToCssPixels(focalY, density)}, $settling);",
                            null,
                        )
                    }

                    override fun onZoomGestureEnd() {
                        if (zoomScale[0] <= 1.05f) {
                            zoomScale[0] = 1f
                            view.evaluateJavascript("window.hoshiManga && window.hoshiManga.resetZoom(true);", null)
                        }
                    }

                    override fun onPan(dx: Float, dy: Float, settling: Boolean) {
                        val density = view.resources.displayMetrics.density
                        view.evaluateJavascript(
                            "window.hoshiManga && window.hoshiManga.panBy(" +
                                "${androidPixelsToCssPixels(dx, density)}, ${androidPixelsToCssPixels(dy, density)}, $settling);",
                            null,
                        )
                    }

                    override fun shouldIgnoreReaderGesture(event: MotionEvent): Boolean =
                        super.shouldIgnoreReaderGesture(event) || currentShouldBlockOnPopup.value(event)

                    override fun onTap(x: Float, y: Float) {
                        selectAt(x, y) {
                            val rtl = mangaReadingDirectionRtlDefault()
                            val third = view.width / 3f
                            when {
                                x < third -> goToPage(if (rtl) currentPageIndex + 1 else currentPageIndex - 1)
                                x > third * 2 -> goToPage(if (rtl) currentPageIndex - 1 else currentPageIndex + 1)
                                else -> Unit
                            }
                        }
                    }

                    override fun onDoubleTap(x: Float, y: Float) {
                        val view2 = webView ?: return
                        val density = view2.resources.displayMetrics.density
                        view2.evaluateJavascript(
                            ReaderSelectionCommand.SelectText(
                                x = androidPixelsToCssPixels(x, density),
                                y = androidPixelsToCssPixels(y, density),
                                maxLength = readerSelectionMaxLength(dictionarySettings),
                                sentenceOnly = true,
                            ).source,
                            null,
                        )
                    }

                    override fun onLeftSwipe() {
                        val rtl = mangaReadingDirectionRtlDefault()
                        goToPage(if (rtl) currentPageIndex - 1 else currentPageIndex + 1)
                    }

                    override fun onRightSwipe() {
                        val rtl = mangaReadingDirectionRtlDefault()
                        goToPage(if (rtl) currentPageIndex + 1 else currentPageIndex - 1)
                    }
                },
            )
        },
        onRelease = { view ->
            if (webView == view) webView = null
        },
    )

    AiLookupOverlay(
        state = aiLookupState,
        onConfirmSentenceBreakdown = aiLookupViewModel::confirmSentenceBreakdown,
        onDismiss = aiLookupViewModel::dismiss,
        onRetry = aiLookupViewModel::retry,
        modifier = Modifier.fillMaxSize(),
    )

    val popupPayloads = remember(themedLookupPopups, readerPopupHistories, readerPopupIframeUrl) {
        readerLookupPopupFramePayloads(
            popups = themedLookupPopups,
            histories = readerPopupHistories,
            viewport = ReaderLookupPopupViewport(width = 0.0, height = 0.0),
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
