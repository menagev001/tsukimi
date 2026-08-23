package moe.antimony.hoshi.manga

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MokuroVolume(
    val version: String? = null,
    val title: String? = null,
    @SerialName("title_uuid") val titleUuid: String? = null,
    val volume: String? = null,
    @SerialName("volume_uuid") val volumeUuid: String? = null,
    val pages: List<MokuroPage> = emptyList(),
)

@Serializable
data class MokuroPage(
    @SerialName("img_width") val imgWidth: Int,
    @SerialName("img_height") val imgHeight: Int,
    @SerialName("img_path") val imgPath: String,
    val blocks: List<MokuroBlock> = emptyList(),
)

@Serializable
data class MokuroBlock(
    // [x1, y1, x2, y2] in the page's own pixel space. Not a fixed-size tuple in
    // kotlinx.serialization, so size is validated defensively via `rect` before use --
    // community-produced .mokuro files can be malformed.
    val box: List<Double> = emptyList(),
    val vertical: Boolean = false,
    @SerialName("font_size") val fontSize: Double? = null,
    // Per-line point polygons, index-aligned with `lines`. Length mismatches between
    // linesCoords and lines are possible in malformed input and must be handled by callers.
    @SerialName("lines_coords") val linesCoords: List<List<List<Double>>> = emptyList(),
    val lines: List<String> = emptyList(),
) {
    val rect: MokuroRect?
        get() = box.takeIf { it.size == 4 }
            ?.let { (x1, y1, x2, y2) -> MokuroRect(x1, y1, x2, y2) }
}

data class MokuroRect(val x1: Double, val y1: Double, val x2: Double, val y2: Double) {
    val width: Double get() = x2 - x1
    val height: Double get() = y2 - y1
}

/**
 * Axis-aligned bounding box of a line's point polygon (`lines_coords[i]`), used instead of the
 * raw polygon for overlay positioning -- highlight/selection rect handling elsewhere in this
 * codebase is rectangle-based, and a true polygon buys nothing against skewed OCR output.
 */
fun List<List<Double>>.toMokuroRect(): MokuroRect? {
    if (isEmpty()) return null
    var minX = Double.POSITIVE_INFINITY
    var minY = Double.POSITIVE_INFINITY
    var maxX = Double.NEGATIVE_INFINITY
    var maxY = Double.NEGATIVE_INFINITY
    for (point in this) {
        if (point.size < 2) continue
        val (x, y) = point
        if (x < minX) minX = x
        if (y < minY) minY = y
        if (x > maxX) maxX = x
        if (y > maxY) maxY = y
    }
    if (minX > maxX || minY > maxY) return null
    return MokuroRect(minX, minY, maxX, maxY)
}
