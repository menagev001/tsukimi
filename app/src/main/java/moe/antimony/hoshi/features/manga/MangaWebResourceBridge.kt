package moe.antimony.hoshi.features.manga

import android.webkit.WebResourceResponse
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder

/**
 * Serves manga page images to the WebView under the same `appassets.androidplatform.net`
 * synthetic origin the reader already uses, mirroring `features.reader.ReaderWebResourceBridge`.
 * Page files were already sanitized against path traversal at import time
 * (`resolveSafePageDestination`), but this re-validates at serve time too, since the requested
 * path comes from a URL the WebView constructs, not a value this class controls directly.
 */
internal class MangaWebResourceBridge(
    private val pageFile: (relativeImgPath: String) -> File?,
) {
    fun resourceForUrl(url: String): WebResourceResponse? {
        val uri = runCatching { URI(url).normalize() }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host != "appassets.androidplatform.net") return null
        val path = uri.path.orEmpty()
        if (!path.startsWith("/manga/pages/")) return null
        val relativePath = runCatching {
            URLDecoder.decode(path.removePrefix("/manga/pages/"), "UTF-8")
        }.getOrNull() ?: return null
        val file = pageFile(relativePath) ?: return notFound()
        return WebResourceResponse(
            file.mediaType(),
            null,
            HttpURLConnection.HTTP_OK,
            "OK",
            emptyMap(),
            file.inputStream(),
        )
    }

    private fun notFound(): WebResourceResponse =
        WebResourceResponse(
            "text/plain",
            "UTF-8",
            HttpURLConnection.HTTP_NOT_FOUND,
            "Not Found",
            emptyMap(),
            ByteArray(0).inputStream(),
        )
}

private fun File.mediaType(): String = when (extension.lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    "bmp" -> "image/bmp"
    else -> "application/octet-stream"
}
