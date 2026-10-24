package moe.antimony.hoshi.features.reader

import android.content.Context
import android.net.Uri
import android.view.MotionEvent
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.antimony.hoshi.LocalHoshiUiDependencies
import moe.antimony.hoshi.content.ContentLanguageProfile
import moe.antimony.hoshi.dictionary.DictionaryRepository
import moe.antimony.hoshi.features.ai.AiLookupOverlay
import moe.antimony.hoshi.features.ai.AiLookupViewModel
import moe.antimony.hoshi.features.anki.AnkiViewModel
import moe.antimony.hoshi.features.audio.AudioRequestHandler
import moe.antimony.hoshi.features.audio.AudioSettings
import moe.antimony.hoshi.features.audio.LocalAudioRepository
import moe.antimony.hoshi.features.audio.WordAudioPlayer
import moe.antimony.hoshi.features.dictionary.DictionaryImageRequestHandler
import moe.antimony.hoshi.features.dictionary.DictionarySettings
import moe.antimony.hoshi.features.dictionary.LookupPopupAssets
import moe.antimony.hoshi.features.dictionary.LookupPopupHtml
import moe.antimony.hoshi.features.dictionary.LookupPopupItem
import moe.antimony.hoshi.features.dictionary.LookupPopupOptions
import moe.antimony.hoshi.features.dictionary.clearPopupSelectionHighlights
import moe.antimony.hoshi.features.dictionary.closeChildPopupsAndClearSelection
import moe.antimony.hoshi.features.dictionary.closeChildPopupsForScrolledParent
import moe.antimony.hoshi.features.dictionary.createLookupPopupItem
import moe.antimony.hoshi.features.dictionary.dismissPopupAt
import moe.antimony.hoshi.features.dictionary.openPopupExternalLink
import moe.antimony.hoshi.features.dictionary.withLookupPopupVisualOptions
import moe.antimony.hoshi.features.export.WordExport
import moe.antimony.hoshi.features.export.shareWordExport
import moe.antimony.hoshi.features.furigana.KnownWordsRepository

/**
 * Hooks for the AI buttons of the popup. The defaults send the popup's own sentence; Watch mode
 * replaces them to add the neighbouring subtitle lines as context.
 */
internal interface LookupPopupAiRequests {
    fun askWordNuance(ai: AiLookupViewModel, word: String, selection: ReaderSelectionData) {
        ai.askWordNuance(word = word, sentence = selection.sentence)
    }

    fun requestSentenceBreakdown(ai: AiLookupViewModel, selection: ReaderSelectionData) {
        ai.requestSentenceBreakdown(selection.sentence)
    }

    companion object Default : LookupPopupAiRequests
}

/**
 * The dictionary lookup popup stack for a screen that renders its own text in a WebView and
 * loads `reader-popup-host.js` into it (manga OCR text, Watch subtitles). It owns the popup
 * stack state, the popup iframe document and resources, and the replies to the popup's bridge
 * messages (child lookups, Anki, word audio, AI, export, mark known).
 *
 * The screen installs [installInto] on its WebView, routes popup resource requests to
 * [interceptRequest], forwards text selections to [handleTextSelected], and draws
 * [LookupPopupHostLayers] above the WebView.
 */
@Stable
internal class LookupPopupHost(
    private val context: Context,
    private val scope: CoroutineScope,
    private val inputs: State<LookupPopupHostInputs>,
    val ankiViewModel: AnkiViewModel,
    val aiLookupViewModel: AiLookupViewModel,
    private val dictionaryStylesState: State<Map<String, String>>,
    private val dictionarySettingsState: State<DictionarySettings>,
    private val audioSettingsState: State<AudioSettings>,
) {
    var webView: WebView? by mutableStateOf(null)

    var lookupPopups: List<LookupPopupItem> by mutableStateOf(emptyList())
        private set
    var popupHistories: Map<String, ReaderPopupHistoryCounts> by mutableStateOf(emptyMap())
        private set

    /** The root selection highlight; screens with zoom or pan re-measure its rects. */
    var rootSelectionHighlight: ReaderRootSelectionHighlight? by mutableStateOf(null)

    /** Character count behind [rootSelectionHighlight], for re-measuring its rects. */
    var rootSelectionHighlightCount: Int by mutableIntStateOf(0)
        private set

    val isOpen: Boolean
        get() = lookupPopups.isNotEmpty()

    val dictionarySettings: DictionarySettings
        get() = dictionarySettingsState.value

    internal val bridgeHolder = ReaderLookupPopupBridgeCallbackHolder().also { holder ->
        holder.callbacks = ReaderLookupPopupBridgeCallbacks { message -> handleBridgeMessage(message) }
    }

    internal var resourceHandler: ReaderLookupPopupResourceHandler? = null
    internal var iframeUrl: String = ""

    private val dictionaryRepository get() = inputs.value.dictionaryRepository

    fun installInto(view: WebView) {
        view.addJavascriptInterface(
            ReaderSelectionBridge(view) { selection, selectionRects -> handleTextSelected(selection, selectionRects) },
            "HoshiTextSelection",
        )
        ReaderLookupPopupWebBridge.install(view, bridgeHolder)
    }

    /** Popup iframe, script and dictionary media requests; null for everything else. */
    fun interceptRequest(uri: Uri): WebResourceResponse? = resourceHandler?.handle(uri)

    fun dismiss() {
        rootSelectionHighlight = null
        updatePopups(clearPopupSelectionHighlights(lookupPopups))
        clearSelection()
        updatePopups(emptyList())
    }

    /** True when [event] lands on an open popup, so the screen must not treat it as its own gesture. */
    fun blocksGesture(event: MotionEvent, viewportWidthDp: Double, viewportHeightDp: Double): Boolean {
        val view = webView ?: return false
        val density = view.resources.displayMetrics.density
        val payloads = readerLookupPopupFramePayloads(
            popups = themedPopups(),
            histories = popupHistories,
            viewport = ReaderLookupPopupViewport(width = viewportWidthDp, height = viewportHeightDp),
            sasayakiWasPaused = false,
            sasayakiIsPlaying = false,
            iframeUrl = iframeUrl,
            rootSelectionHighlight = null,
        )
        return readerLookupPopupTouchBlocksReaderGesture(
            popups = payloads,
            x = androidPixelsToCssPixels(event.x, density).toDouble(),
            y = androidPixelsToCssPixels(event.y, density).toDouble(),
        )
    }

    internal fun themedPopups(): List<LookupPopupItem> {
        val current = inputs.value
        return lookupPopups.withLookupPopupVisualOptions(
            darkMode = current.darkInterface,
            eInkMode = current.eInkMode,
            audioSettings = audioSettingsState.value,
            popupScale = current.readerSettings.popupScale,
        )
    }

    fun handleTextSelected(
        selection: ReaderSelectionData,
        selectionRects: (Int, (List<ReaderSelectionRect>) -> Unit) -> Unit,
    ) {
        rootSelectionHighlight = null
        updatePopups(emptyList())
        if (selection.isSentenceOnly) {
            inputs.value.aiRequests.requestSentenceBreakdown(aiLookupViewModel, selection)
            return
        }
        val (popup, highlightCount) = lookupPopup(selection, isVertical = inputs.value.rootPopupVertical) ?: return
        rootSelectionHighlight = ReaderRootSelectionHighlight(popupId = popup.id, rects = null)
        rootSelectionHighlightCount = highlightCount
        updatePopups(listOf(popup))
        selectionRects(highlightCount) { rects ->
            if (lookupPopups.none { it.id == popup.id }) return@selectionRects
            val displayRects = rects.ifEmpty { listOf(popup.state.selection.rect) }
            val anchor = displayRects.firstOrNull()
            if (anchor != null) {
                updatePopups(
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

    private fun popupIndex(popupId: String): Int = lookupPopups.indexOfFirst { it.id == popupId }

    private fun popupById(popupId: String): LookupPopupItem? = lookupPopups.firstOrNull { it.id == popupId }

    private fun updatePopups(next: List<LookupPopupItem>) {
        val activeIds = next.mapTo(mutableSetOf()) { it.id }
        popupHistories = popupHistories.filterKeys(activeIds::contains)
        rootSelectionHighlight = rootSelectionHighlight?.takeIf { it.popupId == null || it.popupId in activeIds }
        lookupPopups = next
    }

    private fun clearSelection() {
        webView?.evaluateJavascript(ReaderSelectionCommand.ClearSelection.source, null)
    }

    private fun replyMessage(popupId: String, messageId: String, bodyJson: String) {
        webView?.evaluateJavascript(
            """
                window.hoshiReaderPopupHost &&
                window.hoshiReaderPopupHost.resolveMessage(${readerJavaScriptStringLiteral(popupId)}, ${readerJavaScriptStringLiteral(messageId)}, $bodyJson)
            """.trimIndent(),
            null,
        )
    }

    private fun highlightPopupSelection(popupId: String, highlightCount: Int) {
        webView?.evaluateJavascript(
            """
                window.hoshiReaderPopupHost &&
                window.hoshiReaderPopupHost.highlightSelection(${readerJavaScriptStringLiteral(popupId)}, $highlightCount)
            """.trimIndent(),
            null,
        )
    }

    private fun popupOptions(isVertical: Boolean): LookupPopupOptions {
        val current = inputs.value
        val readerSettings = current.readerSettings
        return LookupPopupOptions(
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
            dictionarySettings = dictionarySettingsState.value,
            darkMode = current.darkInterface,
            eInkMode = current.eInkMode,
            audioSettings = audioSettingsState.value,
            documentTitle = current.documentTitle,
            coverPath = null,
            contentLanguageProfile = current.contentLanguageProfile,
        )
    }

    private fun lookupPopup(selection: ReaderSelectionData, isVertical: Boolean): Pair<LookupPopupItem, Int>? =
        createLookupPopupItem(
            selection = selection,
            dictionaryStyles = dictionaryStylesState.value,
            lookup = dictionaryRepository::lookup,
            options = popupOptions(isVertical = isVertical),
        )

    private fun handleBridgeMessage(message: ReaderLookupPopupBridgeMessage) {
        when (message) {
            is ReaderLookupPopupBridgeMessage.OpenLink -> context.openPopupExternalLink(message.url)
            is ReaderLookupPopupBridgeMessage.TapOutside -> {
                val index = popupIndex(message.popupId).takeIf { it >= 0 } ?: return
                updatePopups(closeChildPopupsAndClearSelection(lookupPopups, index))
            }
            is ReaderLookupPopupBridgeMessage.SwipeDismiss -> {
                val index = popupIndex(message.popupId).takeIf { it >= 0 } ?: return
                if (index == 0) dismiss() else updatePopups(dismissPopupAt(lookupPopups, index))
            }
            is ReaderLookupPopupBridgeMessage.TextSelected -> {
                val index = popupIndex(message.popupId).takeIf { it >= 0 } ?: return
                if (message.selection.isSentenceOnly) {
                    updatePopups(emptyList())
                    inputs.value.aiRequests.requestSentenceBreakdown(aiLookupViewModel, message.selection)
                    return
                }
                val nextPopups = lookupPopups.take(index + 1)
                val lookup = lookupPopup(message.selection, isVertical = false)
                if (lookup == null) {
                    highlightPopupSelection(message.popupId, 0)
                    return
                }
                val (childPopup, highlightCount) = lookup
                updatePopups(nextPopups + childPopup)
                highlightPopupSelection(message.popupId, highlightCount)
            }
            is ReaderLookupPopupBridgeMessage.PlayWordAudio -> {
                WordAudioPlayer.get(context).play(message.url, message.mode)
            }
            is ReaderLookupPopupBridgeMessage.MineEntry -> {
                val popup = popupById(message.popupId) ?: return
                val messageId = message.messageId ?: return
                ankiViewModel.mineEntryAsync(message.formatId, message.payloadJson, popup.state.ankiContext) { mined ->
                    replyMessage(message.popupId, messageId, mined.toString())
                }
            }
            is ReaderLookupPopupBridgeMessage.DuplicateCheck -> {
                ankiViewModel.duplicateStatesAsync(message.valuesByHandlebar) { states ->
                    val messageId = message.messageId ?: return@duplicateStatesAsync
                    replyMessage(message.popupId, messageId, readerPopupBooleanMapJson(states))
                }
            }
            is ReaderLookupPopupBridgeMessage.ShowNotes -> {
                ankiViewModel.showNotesAsync(message.formatId, message.valuesByHandlebar) { shown ->
                    val messageId = message.messageId ?: return@showNotesAsync
                    replyMessage(message.popupId, messageId, shown.toString())
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
                    updatePopups(
                        lookupPopups.map { existing ->
                            if (existing.id == message.popupId) existing.copy(state = existing.state.copy(results = results)) else existing
                        },
                    )
                    val current = popupHistories[message.popupId] ?: ReaderPopupHistoryCounts()
                    popupHistories = popupHistories + (message.popupId to current.copy(backCount = current.backCount + 1, forwardCount = 0))
                }
                replyMessage(message.popupId, message.messageId ?: return, results.size.toString())
            }
            is ReaderLookupPopupBridgeMessage.GetEntry -> {
                val entry = popupById(message.popupId)?.state?.results?.getOrNull(message.index)
                val body = entry?.let(LookupPopupHtml::entryJsonString) ?: "null"
                replyMessage(message.popupId, message.messageId ?: return, body)
            }
            is ReaderLookupPopupBridgeMessage.PopupScrolled -> {
                val index = popupIndex(message.popupId).takeIf { it >= 0 } ?: return
                updatePopups(closeChildPopupsForScrolledParent(lookupPopups, index))
            }
            is ReaderLookupPopupBridgeMessage.ScrollState -> Unit
            is ReaderLookupPopupBridgeMessage.NavigateBack -> {
                val current = popupHistories[message.popupId] ?: return
                if (current.backCount > 0) {
                    popupHistories = popupHistories +
                        (message.popupId to current.copy(backCount = current.backCount - 1, forwardCount = current.forwardCount + 1))
                }
            }
            is ReaderLookupPopupBridgeMessage.NavigateForward -> {
                val current = popupHistories[message.popupId] ?: return
                if (current.forwardCount > 0) {
                    popupHistories = popupHistories +
                        (message.popupId to current.copy(backCount = current.backCount + 1, forwardCount = current.forwardCount - 1))
                }
            }
            is ReaderLookupPopupBridgeMessage.ContentReady -> Unit
            is ReaderLookupPopupBridgeMessage.SasayakiReplayCue,
            is ReaderLookupPopupBridgeMessage.SasayakiTogglePlayback,
            is ReaderLookupPopupBridgeMessage.SasayakiPlayForward,
            -> Unit
            is ReaderLookupPopupBridgeMessage.AskAi -> {
                val selection = popupById(message.popupId)?.state?.selection ?: return
                inputs.value.aiRequests.askWordNuance(aiLookupViewModel, message.word, selection)
            }
            is ReaderLookupPopupBridgeMessage.ExportWord -> {
                val selection = popupById(message.popupId)?.state?.selection ?: return
                context.shareWordExport(WordExport(word = message.word, sentence = selection.sentence))
            }
            is ReaderLookupPopupBridgeMessage.MarkKnown -> {
                if (message.expression.isEmpty()) return
                val knownWords = inputs.value.knownWordsRepository
                scope.launch { knownWords.markKnown(message.expression, message.reading) }
            }
        }
    }
}

/** Values the host reads at the time a popup is created or a message arrives. */
internal data class LookupPopupHostInputs(
    val readerSettings: ReaderSettings,
    val darkInterface: Boolean,
    val eInkMode: Boolean,
    val contentLanguageProfile: ContentLanguageProfile,
    val documentTitle: String,
    /** Manga OCR text is vertical; subtitles are horizontal. Child popups are always horizontal. */
    val rootPopupVertical: Boolean,
    val aiRequests: LookupPopupAiRequests,
    val dictionaryRepository: DictionaryRepository,
    val knownWordsRepository: KnownWordsRepository,
)

@Composable
internal fun rememberLookupPopupHost(
    readerSettings: ReaderSettings,
    darkInterface: Boolean,
    eInkMode: Boolean,
    contentLanguageProfile: ContentLanguageProfile,
    documentTitle: String,
    rootPopupVertical: Boolean,
    aiRequests: LookupPopupAiRequests = LookupPopupAiRequests,
): LookupPopupHost {
    val context = LocalContext.current
    val appContainer = LocalHoshiUiDependencies.current
    val dictionaryRepository = appContainer.dictionaryRepository
    val fontManager = appContainer.readerFontManager
    val scope = rememberCoroutineScope()
    val ankiViewModel: AnkiViewModel = hiltViewModel()
    val ankiUiState by ankiViewModel.uiState.collectAsStateWithLifecycle()
    val aiLookupViewModel: AiLookupViewModel = hiltViewModel()
    val dictionaryStyles = remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(dictionaryRepository) {
        dictionaryStyles.value = withContext(Dispatchers.IO) {
            runCatching {
                dictionaryRepository.ensureLookupQueryReady()
                dictionaryRepository.dictionaryStyles()
            }.getOrDefault(emptyMap())
        }
    }
    val dictionarySettings = appContainer.dictionarySettingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = DictionarySettings(),
    )
    val audioSettings = appContainer.audioSettingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = AudioSettings(),
    )
    val inputs = rememberUpdatedState(
        LookupPopupHostInputs(
            readerSettings = readerSettings,
            darkInterface = darkInterface,
            eInkMode = eInkMode,
            contentLanguageProfile = contentLanguageProfile,
            documentTitle = documentTitle,
            rootPopupVertical = rootPopupVertical,
            aiRequests = aiRequests,
            dictionaryRepository = dictionaryRepository,
            knownWordsRepository = appContainer.knownWordsRepository,
        ),
    )
    val host = remember(ankiViewModel, aiLookupViewModel) {
        LookupPopupHost(
            context = context,
            scope = scope,
            inputs = inputs,
            ankiViewModel = ankiViewModel,
            aiLookupViewModel = aiLookupViewModel,
            dictionaryStylesState = dictionaryStyles,
            dictionarySettingsState = dictionarySettings,
            audioSettingsState = audioSettings,
        )
    }

    val popupAssets = remember(context) { LookupPopupAssets.load(context) }
    val iframeDocument = remember(
        dictionaryStyles.value, dictionarySettings.value, readerSettings, darkInterface, eInkMode, audioSettings.value,
        contentLanguageProfile, ankiUiState.popupSettings,
    ) {
        LookupPopupHtml.renderIframeDocument(
            assets = null,
            dictionaryStyles = dictionaryStyles.value,
            settings = dictionarySettings.value,
            swipeToDismiss = readerSettings.popupSwipeToDismiss,
            swipeThreshold = readerSettings.popupSwipeThreshold,
            reducedMotionScrolling = readerSettings.popupReducedMotionScrolling,
            reducedMotionScrollPercent = readerSettings.popupReducedMotionScrollPercent,
            reducedMotionSwipeThreshold = readerSettings.popupReducedMotionSwipeThreshold,
            darkMode = darkInterface,
            eInkMode = eInkMode,
            audioSettings = audioSettings.value,
            ankiSettings = ankiUiState.popupSettings,
            fontFaceCss = fontManager.popupFontFaceCss(),
            popupScale = readerSettings.popupScale,
            contentLanguageProfile = contentLanguageProfile,
            aiLookupEnabled = true,
            markKnownEnabled = true,
        )
    }
    val currentIframeDocument = rememberUpdatedState(iframeDocument)
    host.iframeUrl = remember(iframeDocument) { readerLookupPopupIframeUrl(iframeDocument.hashCode()) }
    host.resourceHandler = remember(context, popupAssets, fontManager, dictionaryRepository) {
        ReaderLookupPopupResourceHandler(
            context = context.applicationContext,
            assets = popupAssets,
            fontManager = fontManager,
            audioRequestHandler = AudioRequestHandler(LocalAudioRepository.fromContext(context.applicationContext)),
            imageRequestHandler = DictionaryImageRequestHandler(dictionaryRepository::dictionaryMedia),
            iframeDocument = { currentIframeDocument.value },
        )
    }
    LaunchedEffect(host.webView, host.iframeUrl) {
        host.webView?.evaluateJavascript(
            """
                (function() {
                  var iframeUrl = ${readerJavaScriptStringLiteral(host.iframeUrl)};
                  window.__hoshiReaderPopupIframeUrl = iframeUrl;
                  if (window.hoshiReaderPopupHost) {
                    window.hoshiReaderPopupHost.preloadIdleRootFrame(iframeUrl);
                  }
                })();
            """.trimIndent(),
            null,
        )
    }
    return host
}

/** The AI result overlay and the popup iframes; draw above the screen's WebView. */
@Composable
internal fun LookupPopupHostLayers(
    host: LookupPopupHost,
    viewportWidthDp: Double,
    viewportHeightDp: Double,
    darkInterface: Boolean,
    eInkMode: Boolean,
    verticalWriting: Boolean,
) {
    val aiLookupState by host.aiLookupViewModel.state.collectAsStateWithLifecycle()
    AiLookupOverlay(
        state = aiLookupState,
        onConfirmSentenceBreakdown = host.aiLookupViewModel::confirmSentenceBreakdown,
        onDismiss = host.aiLookupViewModel::dismiss,
        onRetry = host.aiLookupViewModel::retry,
        modifier = Modifier.fillMaxSize(),
    )
    val payloads = remember(
        host.lookupPopups, host.popupHistories, host.iframeUrl, viewportWidthDp, viewportHeightDp,
        host.rootSelectionHighlight, darkInterface, eInkMode,
    ) {
        readerLookupPopupFramePayloads(
            popups = host.themedPopups(),
            histories = host.popupHistories,
            viewport = ReaderLookupPopupViewport(width = viewportWidthDp, height = viewportHeightDp),
            sasayakiWasPaused = false,
            sasayakiIsPlaying = false,
            iframeUrl = host.iframeUrl,
            rootSelectionHighlight = host.rootSelectionHighlight,
        )
    }
    ReaderLookupPopupIframeSync(
        webView = host.webView,
        payloads = payloads,
        rootHighlight = host.rootSelectionHighlight?.let { highlight ->
            ReaderLookupPopupRootHighlightPayload.fromReaderRects(
                popupId = highlight.popupId,
                rects = highlight.rects,
                darkMode = darkInterface,
                eInkMode = eInkMode,
                verticalWriting = verticalWriting,
            )
        },
    )
}
