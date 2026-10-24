package moe.antimony.hoshi.features.watch

import android.annotation.SuppressLint
import android.net.Uri
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import moe.antimony.hoshi.features.reader.LookupPopupHost
import moe.antimony.hoshi.features.reader.ReaderTapDispatcher
import moe.antimony.hoshi.features.reader.androidPixelsToCssPixels
import moe.antimony.hoshi.features.reader.readerSelectionMaxLength
import moe.antimony.hoshi.webview.applyHoshiWebViewSecurityDefaults

/**
 * Transparent WebView over the video that draws the current subtitle lines and hosts the lookup
 * popup. It receives every touch on the player:
 * - touches on an open popup go to the WebView itself,
 * - a tap on a word (in a line or in the line list) opens the popup, a double tap on a line asks
 *   for its sentence breakdown,
 * - a tap on a timestamp in the line list calls [onSeekToCue]; drags in the list scroll it,
 * - a double tap elsewhere seeks back (left half) or forward (right half),
 * - any other tap closes an open popup, or else (outside the list) calls [onBlankTap].
 */
/**
 * Longer than the reader's 180 ms: two taps on one side of a video come a little slower than on
 * a page, and a slightly later single tap matters less here.
 */
private const val WatchDoubleTapTimeoutMs = 250L

/** Seconds of fling velocity turned into list scroll distance. */
private const val ListFlingDistancePerVelocity = 0.25f

@SuppressLint("ClickableViewAccessibility")
@Composable
internal fun WatchSubtitleOverlay(
    popupHost: LookupPopupHost,
    primaryLines: List<String>,
    cues: List<WatchSubtitleCue>,
    activeCueIndex: Int?,
    listVisible: Boolean,
    listWidth: Dp,
    fontSizePx: Int,
    bottomInset: Dp,
    onBlankTap: () -> Unit,
    onSideDoubleTap: (forward: Boolean) -> Unit,
    onSeekToCue: (cueIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pageReady by remember { mutableStateOf(false) }
    val assets = remember(context) { WatchSubtitleWebAssets.load(context) }
    val currentFontSizePx by rememberUpdatedState(fontSizePx)
    val currentOnBlankTap by rememberUpdatedState(onBlankTap)
    val currentOnSideDoubleTap by rememberUpdatedState(onSideDoubleTap)
    val currentOnSeekToCue by rememberUpdatedState(onSeekToCue)
    val currentListVisible by rememberUpdatedState(listVisible)
    val currentListWidth by rememberUpdatedState(listWidth)
    val webView = popupHost.webView

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                val view = this
                applyHoshiWebViewSecurityDefaults()
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                popupHost.installInto(this)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript(watchSubtitleSetupScript(assets, currentFontSizePx), null)
                        pageReady = true
                    }

                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        request.url?.let { uri: Uri -> popupHost.interceptRequest(uri) }
                }

                fun inList(x: Float): Boolean =
                    currentListVisible && x >= width - currentListWidth.value * resources.displayMetrics.density

                fun selectAt(x: Float, y: Float, sentenceOnly: Boolean, onNothingSelected: () -> Unit) {
                    val density = resources.displayMetrics.density
                    evaluateJavascript(
                        watchSelectTextScript(
                            x = androidPixelsToCssPixels(x, density),
                            y = androidPixelsToCssPixels(y, density),
                            maxLength = readerSelectionMaxLength(popupHost.dictionarySettings),
                            sentenceOnly = sentenceOnly,
                        ),
                    ) { result ->
                        when (val tap = WatchTapResult.fromWebViewResult(result)) {
                            WatchTapResult.Selected -> Unit
                            WatchTapResult.Nothing -> onNothingSelected()
                            WatchTapResult.List -> popupHost.dismiss()
                            is WatchTapResult.SeekToCue -> {
                                popupHost.dismiss()
                                currentOnSeekToCue(tap.cueIndex)
                            }
                        }
                    }
                }

                val taps = ReaderTapDispatcher(
                    doubleTapTimeoutMs = WatchDoubleTapTimeoutMs,
                    onTap = { x, y ->
                        selectAt(x, y, sentenceOnly = false) {
                            if (popupHost.isOpen) popupHost.dismiss() else currentOnBlankTap()
                        }
                    },
                    onDoubleTap = { x, y ->
                        selectAt(x, y, sentenceOnly = true) {
                            popupHost.dismiss()
                            if (!inList(x)) currentOnSideDoubleTap(x >= view.width / 2f)
                        }
                    },
                )
                val gestures = GestureDetector(
                    ctx,
                    object : GestureDetector.SimpleOnGestureListener() {
                        override fun onDown(event: MotionEvent): Boolean = true

                        override fun onSingleTapUp(event: MotionEvent): Boolean {
                            taps.dispatchTap(
                                x = event.x,
                                y = event.y,
                                eventTimeMs = event.eventTime,
                                postDelayed = { runnable, delayMs -> view.postDelayed(runnable, delayMs) },
                                removeCallback = { runnable -> view.removeCallbacks(runnable) },
                            )
                            return true
                        }

                        override fun onScroll(start: MotionEvent?, event: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                            if (start == null || !inList(start.x)) return false
                            popupHost.dismiss()
                            val density = resources.displayMetrics.density
                            evaluateJavascript(watchScrollListScript(androidPixelsToCssPixels(distanceY, density)), null)
                            return true
                        }

                        override fun onFling(start: MotionEvent?, event: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                            if (start == null || !inList(start.x)) return false
                            val density = resources.displayMetrics.density
                            val distance = -velocityY * ListFlingDistancePerVelocity
                            evaluateJavascript(watchFlingListScript(androidPixelsToCssPixels(distance, density)), null)
                            return true
                        }
                    },
                ).apply {
                    // Every tap must reach ReaderTapDispatcher; GestureDetector's own double-tap
                    // detection would swallow the second tap of a double tap.
                    setOnDoubleTapListener(null)
                }
                var gestureOnPopup = false
                setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        val density = resources.displayMetrics.density
                        gestureOnPopup = popupHost.blocksGesture(
                            event,
                            viewportWidthDp = width / density.toDouble(),
                            viewportHeightDp = height / density.toDouble(),
                        )
                        if (gestureOnPopup) taps.cancelPending { view.removeCallbacks(it) }
                    }
                    // Scrolling and buttons inside the popup need the WebView's own touch handling.
                    if (gestureOnPopup) return@setOnTouchListener false
                    gestures.onTouchEvent(event)
                    true
                }
                popupHost.webView = this
                loadDataWithBaseURL(
                    WatchSubtitleBaseUrl,
                    "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"></head><body></body></html>",
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
        onRelease = { view ->
            if (popupHost.webView == view) popupHost.webView = null
            view.destroy()
        },
    )

    LaunchedEffect(webView, pageReady, fontSizePx) {
        if (pageReady) webView?.evaluateJavascript(watchSubtitleConfigureScript(fontSizePx), null)
    }
    LaunchedEffect(webView, pageReady, bottomInset) {
        if (pageReady) webView?.evaluateJavascript(watchBottomInsetScript(bottomInset.value), null)
    }
    LaunchedEffect(webView, pageReady, primaryLines) {
        if (pageReady) webView?.evaluateJavascript(watchShowCuesScript(primaryLines), null)
    }
    LaunchedEffect(webView, pageReady, cues) {
        if (pageReady) webView?.evaluateJavascript(watchSetCueListScript(cues), null)
    }
    LaunchedEffect(webView, pageReady, cues, activeCueIndex) {
        if (pageReady) webView?.evaluateJavascript(watchSetActiveCueScript(activeCueIndex), null)
    }
    LaunchedEffect(webView, pageReady, listVisible, listWidth) {
        if (pageReady) webView?.evaluateJavascript(watchShowListScript(listVisible, listWidth.value), null)
    }
}
