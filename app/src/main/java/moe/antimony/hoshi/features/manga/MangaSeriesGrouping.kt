package moe.antimony.hoshi.features.manga

import moe.antimony.hoshi.manga.MangaVolumeEntry

/**
 * One shelf grid entry: either a single ungrouped volume ([seriesKey] null, matching today's
 * behavior exactly) or 2+ volumes sharing a mokuro series title, rendered as one stacked card
 * that drills into its own volume list.
 */
internal data class MangaSeriesGroup(
    val seriesKey: String?,
    val volumes: List<MangaVolumeEntry>,
)

/**
 * Groups volumes by `metadata.seriesTitle` (the raw mokuro series name), preserving the input
 * list's own ordering (already sorted by [moe.antimony.hoshi.manga.MangaSortOption]) at the group
 * level -- a group's position is wherever its first member first appears. A series only "exists"
 * as a group once it has 2+ volumes; a lone volume (including every volume imported before
 * `seriesTitle` existed, which is always null) renders exactly as before, so this is additive
 * only for the multi-volume case it's meant for.
 */
internal fun groupMangaVolumesBySeries(volumes: List<MangaVolumeEntry>): List<MangaSeriesGroup> {
    val byKey = linkedMapOf<String, MutableList<MangaVolumeEntry>>()
    for (entry in volumes) {
        // Volumes lacking a series title (not yet re-imported since this field was added, or a
        // genuine one-shot) key off their own id so they never accidentally group with anything.
        val key = entry.metadata.seriesTitle?.takeIf { it.isNotBlank() } ?: "single:${entry.metadata.id}"
        byKey.getOrPut(key) { mutableListOf() }.add(entry)
    }
    return byKey.map { (key, members) ->
        MangaSeriesGroup(seriesKey = if (members.size > 1) key else null, volumes = members)
    }
}
