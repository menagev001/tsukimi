package moe.antimony.hoshi.content

/**
 * The minimal shape [moe.antimony.hoshi.profiles.ProfileRepository]/[moe.antimony.hoshi.profiles.ProfileActivationService]
 * need to resolve the effective dictionary/reader profile for a piece of content -- implemented
 * by both `epub.BookMetadata` and `manga.MangaVolumeMetadata` so profile activation stays
 * content-type-agnostic rather than EPUB-specific.
 */
interface ProfileActivationSubject {
    val profileId: String?
    val bookLanguage: String?
}
