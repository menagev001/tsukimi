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
import moe.antimony.hoshi.features.reader.READER_DOUBLE_TAP_TIMEOUT_MS
import moe.antimony.hoshi.features.reader.ReaderTapDispatcher
import moe.antimony.hoshi.features.reader.androidPixelsToCssPixels
import moe.antimony.hoshi.features.reader.readerSelectionMaxLength
import moe.antimony.hoshi.webview.applyHoshiWebViewSecurityDefaults

/**
 * Transparent WebView over the video that draws the current subtitle lines and hosts the lookup
 * popup. It receives every touch on the player:
 * - touches on an open popup go to the WebView itself,
 * - a tap on a word opens the popup, a double tap on a line asks for its sentence breakdown,
 * - a double tap elsewhere seeks back (left half) or forward (right half),
 * - any other tap closes an open popup, or else calls [onBlankTap].
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
internal fun WatchSubtitleOverlay(
    popupHost: LookupPopupHost,
    primaryLines: List<String>,
    fontSizePx: Int,
    bottomInset: Dp,
    onBlankTap: () -> Unit,
    onSideDoubleTap: (forward: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pageReady by remember { mutableStateOf(false) }
    val assets = remember(context) { WatchSubtitleWebAssets.load(context) }
    val currentFontSizePx by rememberUpdatedState(fontSizePx)
    val currentOnBlankTap by rememberUpdatedState(onBlankTap)
    val currentOnSideDoubleTap by rememberUpdatedState(onSideDoubleTap)
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

                fun selectAt(x: Float, y: Float, sentenceOnly: Boolean, onNothingSelected: () -> Unit) {
                    val density = resources.displayMetrics.density
                    evaluateJavascript(
                        watchSelectTextScript(
                            x = androidPixelsToCssPixels(x, density),
                            y = androidPixelsToCssPixels(y, density),
                            maxLength = readerSelectionMaxLength(popupHost.dictionarySettings),
                            sentenceOnly = sentenceOnly,
                        ),
                    ) { result -> if (watchSelectedNothing(result)) onNothingSelected() }
                }

                val taps = ReaderTapDispatcher(
                    doubleTapTimeoutMs = READER_DOUBLE_TAP_TIMEOUT_MS,
                    onTap = { x, y ->
                        selectAt(x, y, sentenceOnly = false) {
                            if (popupHost.isOpen) popupHost.dismiss() else currentOnBlankTap()
                        }
                    },
                    onDoubleTap = { x, y ->
                        selectAt(x, y, sentenceOnly = true) {
                            popupHost.dismiss()
                            currentOnSideDoubleTap(x >= view.width / 2f)
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
                    },
                )
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
        if (pageReady) webView?.evaluateJavascript(watchShowCuesScript(primaryLines, emptyList()), null)
    }
}
