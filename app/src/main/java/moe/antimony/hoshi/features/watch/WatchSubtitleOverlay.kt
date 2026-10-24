package moe.antimony.hoshi.features.watch

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.MotionEvent
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
import moe.antimony.hoshi.webview.applyHoshiWebViewSecurityDefaults

/**
 * Transparent WebView over the video that draws the current subtitle lines with the shared
 * reader web stack. It receives every tap on the player; [onBlankTap] gets the ones that do not
 * hit subtitle text.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
internal fun WatchSubtitleOverlay(
    primaryLines: List<String>,
    fontSizePx: Int,
    bottomInset: Dp,
    onBlankTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageReady by remember { mutableStateOf(false) }
    val assets = remember(context) { WatchSubtitleWebAssets.load(context) }
    val currentFontSizePx by rememberUpdatedState(fontSizePx)
    val currentOnBlankTap by rememberUpdatedState(onBlankTap)

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                applyHoshiWebViewSecurityDefaults()
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript(watchSubtitleSetupScript(assets, currentFontSizePx), null)
                        pageReady = true
                    }
                }
                val gestures = GestureDetector(
                    ctx,
                    object : GestureDetector.SimpleOnGestureListener() {
                        override fun onDown(event: MotionEvent): Boolean = true

                        override fun onSingleTapUp(event: MotionEvent): Boolean {
                            currentOnBlankTap()
                            return true
                        }
                    },
                )
                // The overlay never scrolls or selects natively; taps are interpreted here.
                setOnTouchListener { _, event ->
                    gestures.onTouchEvent(event)
                    true
                }
                webView = this
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
            if (webView == view) webView = null
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
