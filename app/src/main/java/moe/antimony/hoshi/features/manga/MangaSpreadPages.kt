package moe.antimony.hoshi.features.manga

/**
 * Groups page indices into 1- or 2-page "spread" groups for double-page display. The first page
 * always shows alone (the conventional cover/title-page treatment used by other manga readers'
 * double-page modes), then pages pair up two at a time; a trailing single page (when the page
 * count works out even after the leading singleton) shows alone too rather than padding with a
 * blank. Passing a [pageCount] of 1 per group (i.e. calling with every page already singleton)
 * is how single-page mode is expressed -- callers don't need a separate code path.
 */
internal fun mangaSpreadGroups(pageCount: Int): List<List<Int>> {
    if (pageCount <= 0) return emptyList()
    val groups = mutableListOf<List<Int>>()
    groups += listOf(0)
    var index = 1
    while (index < pageCount) {
        if (index + 1 < pageCount) {
            groups += listOf(index, index + 1)
            index += 2
        } else {
            groups += listOf(index)
            index += 1
        }
    }
    return groups
}

/** Index into [groups] of whichever group contains [page]; falls back to the first group. */
internal fun mangaSpreadGroupIndexForPage(groups: List<List<Int>>, page: Int): Int {
    val found = groups.indexOfFirst { page in it }
    return if (found >= 0) found else 0
}
