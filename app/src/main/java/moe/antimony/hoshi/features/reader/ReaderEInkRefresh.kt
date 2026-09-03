package moe.antimony.hoshi.features.reader

import android.webkit.WebView
import android.util.Log

/**
 * Wraps the Onyx (Boox) EPD SDK (com.onyx.android.sdk:onyxsdk-device). That SDK targets Onyx's
 * own hardware; behavior on other manufacturers' e-ink panels or on regular LCD/OLED screens is
 * unverified here, so every call is reflection-gated and swallows failures rather than crashing
 * the reader.
 *
 * - [preventWebViewA2Mode] should run once when the reader WebView is created: Onyx devices tend
 *   to auto-switch a WebView with fast-changing content (a popup opening/closing) into A2 mode,
 *   a fast but lossy refresh mode that is the main source of the ghosting this exists to avoid.
 * - [forceFullRefresh] should run right after a lookup popup is fully dismissed, to force one
 *   full-quality (GC16) repaint that clears out whatever A2-mode ghosting slipped through.
 */
internal object ReaderEInkRefresh {
    private const val TAG = "ReaderEInkRefresh"
    private const val EPD_CONTROLLER_CLASS = "com.onyx.android.sdk.api.device.epd.EpdController"
    private const val UPDATE_MODE_CLASS = "com.onyx.android.sdk.api.device.epd.UpdateMode"

    private val epdControllerClass by lazy { runCatching { Class.forName(EPD_CONTROLLER_CLASS) }.getOrNull() }
    private val updateModeGc by lazy {
        runCatching {
            val updateModeClass = Class.forName(UPDATE_MODE_CLASS)
            updateModeClass.getField("GC").get(null)
        }.getOrNull()
    }

    fun preventWebViewA2Mode(webView: WebView) {
        val controller = epdControllerClass ?: return
        runCatching {
            val method = controller.getMethod(
                "setWebViewContrastOptimize",
                WebView::class.java,
                Boolean::class.javaPrimitiveType,
            )
            method.invoke(null, webView, true)
        }.onFailure { error ->
            Log.w(TAG, "setWebViewContrastOptimize failed", error)
        }
    }

    fun forceFullRefresh(webView: WebView) {
        val controller = epdControllerClass ?: return
        val mode = updateModeGc ?: return
        runCatching {
            val method = controller.getMethod("invalidate", android.view.View::class.java, mode.javaClass)
            method.invoke(null, webView, mode)
        }.onFailure { error ->
            Log.w(TAG, "EpdController.invalidate failed", error)
        }
    }
}
