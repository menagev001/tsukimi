package moe.antimony.hoshi.features.manga

import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import moe.antimony.hoshi.features.reader.ReaderLookupPopupResourceHandler

/**
 * Mirrors `features.reader.EpubWebViewClient`'s two-branch `shouldInterceptRequest` (popup
 * resources first, then content resources) but serves manga page images instead of EPUB
 * resources.
 */
internal class MangaWebViewClient(
    private val mangaResourceBridge: MangaWebResourceBridge,
    private val popupResourceHandler: () -> ReaderLookupPopupResourceHandler?,
    private val onPageFinished: (WebView) -> Unit,
) : WebViewClient() {
    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        onPageFinished(view)
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val uri = request.url ?: return null
        return popupResourceHandler()?.handle(uri)
            ?: mangaResourceBridge.resourceForUrl(uri.toString())
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        view.destroy()
        return true
    }
}
