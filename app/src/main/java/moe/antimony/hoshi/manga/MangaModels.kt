package moe.antimony.hoshi.manga

import java.io.File
import kotlinx.serialization.Serializable
import moe.antimony.hoshi.epub.HighlightColor

@Serializable
data class MangaVolumeMetadata(
    val id: String,
    val title: String?,
    // Path relative to the volume's own root directory, e.g. "pages/<firstImgPath>".
    val cover: String?,
    val folder: String?,
    // Apple-reference-epoch seconds, same convention as BookMetadata.lastAccess.
    val lastAccess: Double,
    val renamedTitle: String? = null,
    // Sidecar file name for the raw mokuro JSON copy, defaults to "mokuro.json".
    val mokuro: String? = null,
    // Cached from mokuro.pages.size at import time so the shelf grid never has to parse the
    // full mokuro.json just to render page-count/progress UI.
    val pageCount: Int = 0,
    val profileId: String? = null,
    val bookLanguage: String? = null,
) {
    val displayTitle: String
        get() = renamedTitle?.takeIf { it.isNotBlank() } ?: title.orEmpty()
}

@Serializable
data class MangaBookmark(
    val pageIndex: Int,
    val lastModified: Double? = null,
)

@Serializable
data class MangaHighlight(
    val id: String,
    val pageIndex: Int,
    val blockIndex: Int,
    // Null means the whole block is highlighted, not a single line within it.
    val lineIndex: Int? = null,
    val text: String,
    val color: HighlightColor,
    val createdAt: Double,
)

data class MangaVolumeEntry(
    val root: File,
    val metadata: MangaVolumeMetadata,
) {
    val displayTitle: String
        get() = metadata.displayTitle.ifBlank { root.name }
}
