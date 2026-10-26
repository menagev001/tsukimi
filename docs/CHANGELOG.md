# Changelog

All notable user-visible changes to Tsukimi are documented here.
The format follows a Keep a Changelog style, and release sections use Semantic Versioning.
Historical release notes before v1.3.0 live in [CHANGELOG_ARCHIVE.md](CHANGELOG_ARCHIVE.md).

## [Unreleased]

### Added

- Add a Watch tab for a Jellyfin server (version 12 or newer): sign in under
  Settings → Watch, then browse Continue Watching, Next Up and your film and
  series libraries, with seasons and episodes per series, and play films and
  episodes full screen with ±10 s, a seek bar and playback speed. Files the
  tablet can decode play directly; anything else (for example 10-bit H.264) is
  transcoded by Jellyfin. Playback resumes where you stopped, and progress
  and watched state are saved to Jellyfin, so they match the other Jellyfin apps.
  Japanese audio is chosen by default (Settings → Watch → Audio Language), an
  audio track button switches tracks during playback, and a lower streaming
  quality can be set for mobile data and phone hotspots. Japanese subtitles
  are shown by Tsukimi itself (white text with a soft outline, four sizes),
  with a subtitle track menu, per-episode timing correction in 0.1 s steps,
  and buttons to jump to the previous or next line. Tap a word in a subtitle
  to pause and open the dictionary popup (Anki, Explain Word, Export and Mark
  Known included); playback resumes when it closes (Settings → Watch → Resume
  After Lookup). Double-tap a subtitle for its Sentence Breakdown, or
  double-tap the left or right side of the video to go back or forward 10
  seconds. Explain Word and Sentence Breakdown from a subtitle also send the
  two lines before and after it as context. A line list panel shows the whole
  track next to the video, follows playback, jumps to a line when you tap its
  time, and looks up words like the subtitles do. Open it from the player or
  by swiping in from the right edge; swipe it to the right to close it. The
  popup's Anki button in Watch adds the card in the background while the video
  continues, with a progress chip (Cancel, duplicate warning), the tags
  `tsukimi`, `tsukimi::watch` and the title, and two new Anki field values:
  `{source}` (series, episode and time) and `{sentence-translation}` (the
  English subtitle lines at that time). While the popup is open, a bar shows
  the sentence the card will use, with + Previous line / + Next line to join a
  sentence that is spread over several subtitles; the card, its translation and
  Explain Word / Sentence Breakdown then use the joined sentence. A new Anki
  field value `{screenshot}` adds the video frame of the line (without
  subtitles, at most 1280 px wide), and `{sentence-audio}` the audio of the
  line(s), cut from the original file with 0.25 s padding; when that fails
  the card is still added, tagged `tsukimi::audio-missing`.
  `{card-type-audio}` / `{card-type-sentence}` switch Lapis between an audio
  card (the card has its sentence audio) and a sentence card. The default
  Lapis mapping now fills `SentenceAudio`, `Picture`, the card type and the
  `Source` / `SentenceTranslation` fields for books, manga and video alike.
  Cards can also carry AI answers: `{ai-word-nuance}` and
  `{ai-sentence-breakdown}` are asked in the background when the card format
  uses them (a failed request adds the card anyway, tagged
  `tsukimi::ai-missing`). When a Watch card's sentence is longer than the long
  sentence limit (AI Settings, default 40 characters), a dialog offers to keep
  it or to let AI write a short example sentence for the word, which then
  becomes the card's sentence while `{original-sentence}` keeps the original;
  `{sentence-translation}` then holds an AI translation of the example.
  The example sentence prompt is editable in AI Settings. Tsukimi keeps only
  the session token, never the password, and Sign out ends the session on the
  server.

### Changed

- The Anki button in the book reader and the manga reader now adds the card
  in the background with a progress chip, like in Watch: the AI fields, the
  long-sentence dialog with its AI example sentence, and the tags `tsukimi`,
  `tsukimi::book` / `tsukimi::manga` and the title. Manga cards get the
  tapped speech bubble, cut out of the page, as `{screenshot}`.
- Fetch in Settings → Anki now keeps each card format's deck and note type
  when they still exist, prefers a note type Tsukimi knows (Lapis, Kiku,
  Senren) over the first one, and resets the field mapping to Tsukimi's
  defaults, so a single Fetch maps every known Lapis field.
- Rename the app to 月見 Tsukimi. It now has its own application ID
  (`com.feathercomet.tsukimi`), so it installs next to Hoshi Reader instead of
  updating it: move books and dictionaries with a `.hoshi` backup from
  Settings; manga volumes and settings such as the AI key and Anki mappings
  are not part of the backup and need to be imported and set up again. Update
  checks, the About page and issue reports now use the Tsukimi repository on
  GitHub, and dictionary deep links use `tsukimi://search?text=...` instead of
  `hoshi://`.

## [v1.5.0] - 2026-09-29

### Added

- Tap a speech bubble in the manga reader to reveal its recognised text over
  the artwork, outlined with a thin dotted border, then tap the word you want to
  look up in that text, so a lookup always lands on the character you aimed at.
  Tapping another bubble moves the reveal there; tapping clear of every bubble
  hides it again. Double-tapping a bubble still opens its sentence breakdown.
- Add page-margin cropping to the manga reader (crop button in the toolbar),
  on Auto by default: the white border around scanned pages is detected once
  per volume and removed consistently on every page, so the art and text fill
  more of the screen. Manual mode sets the top, bottom, left and right crop in
  1% steps, starting from the detected values, and Off shows pages as scanned.
  The choice applies to every volume in a series.

### Changed

- Make "Explain Word" and "Sentence Breakdown" answers shorter and more
  focused: grammar notes only where something needs explaining, the dictionary
  form of hard-to-reverse conjugations, and closing notes only when warranted.

### Fixed

- Fix manga reader taps landing "just off" the printed text, and the end of a
  line being untappable entirely. Each line's text is now sized so its character
  count spans exactly the extent the OCR recorded for it, instead of being
  derived from the OCR's own font-size estimate; taps also snap to the nearest
  character in the OCR region they land in or beside, and the end of a long line
  is no longer clipped out of the tappable area.
- Fix the manga reader's selection box staying put when the page is zoomed or
  panned, leaving it stranded beside the popup instead of on its word.
- Show the manga reader's toolbar briefly when a volume is opened. It is
  otherwise revealed only by tapping the middle third of the page, which is
  easily mistaken for the page-turn zones on either side.
- Fix a manga reader tap that missed the text -- or a second tap on an
  already-open word -- zooming the page back out or turning the page. Only a tap
  clear of every OCR region now does that.
- Use the volume thumbnail bundled in a mokuro CBZ as the manga shelf cover,
  instead of the first page, which is often a two-page spread cropped down to
  its middle. Volumes without a bundled thumbnail keep the first page.
- Retry an "Explain Word" or "Sentence Breakdown" request automatically when
  the AI provider is briefly overloaded, and keep long AI answers scrollable
  instead of pushing the popup's buttons off screen.
- Clear the e-ink ghosting left on screen after the lookup popup closes when
  the reduce-ghosting setting is on, using a brief full-screen flash.

## [v1.4.0] - 2026-09-03

### Added

- Add an optional "Add Furigana to Unknown Words" reader setting that
  generates furigana for kanji words not in a known-word list, baked directly
  into the chapter so it never blocks the page from loading; processing is
  centered on wherever you currently are in the book (not always the start)
  with a live "X / Y characters scanned" progress indicator, and a
  "Recalculate Furigana" action to force a fresh pass on demand. The
  known-word list is built either by tapping a "Mark as known" button in the
  reader's lookup popup, or by importing a Migaku known-words export from
  Reader Appearance settings; both merge additively and never overwrite each
  other.
- Add a Reader Appearance setting (shown when E-ink Mode is on) that reduces
  ghosting after the lookup popup closes on Onyx/Boox e-ink devices.
- Add an optional Reader Behavior setting that uses the volume keys to jump
  between terms in the topmost lookup popup.
- Add `hoshi://search?text=...` deep links for opening lookup results in the
  existing popup overlay, with `mode=app` support for opening the Dictionary
  tab instead.
- Add an "Explain Word" action to the reader lookup popup that sends the
  looked-up word and its sentence to a configurable AI provider (Settings >
  AI Lookup, with optional custom prompt overrides) and shows a word-nuance
  explanation, with the word bolded in the sentence, in a scrollable result
  view.
- Add a double-tap gesture on reader text (paginated and continuous modes)
  that offers a "Sentence Breakdown" of the surrounding sentence, skipping
  the normal dictionary popup; the AI request only sends once that option is
  tapped.
- Add an "Export" action to the reader lookup popup, Dictionary tab, and
  Process Text popup that shares the looked-up word and its sentence as JSON
  through Android's share sheet, so the destination (Drive, email, clipboard,
  any app) is picked per export.

### Fixed

- Make the double-tap "Sentence Breakdown" gesture work inside nested lookup
  popups (tapping a word from within an already-open popup), not just the
  main reader text.
- Keep oversized lookup popup frames fully inside the visible screen so their
  bottom border and all scrollable content remain reachable.
- Prefer Arial throughout lookup popups before Android's Japanese font fallback
  so pitch-accent markers stay aligned with their reading, and keep pitch
  dictionary labels intact when compact entries wrap.

## [v1.3.3] - 2026-08-13

### Added

- Automatically center the current chapter when opening the Reader Contents or
  Sasayaki chapter list.
- Add per-source enable controls for imported local audio databases while
  preserving each source's configured priority.
- Add Ogg Opus audiobook import with embedded title, artist, cover, and chapter
  metadata in Sasayaki, load its artist without the platform-reader delay, and
  show MP3, M4B, and Opus audiobook duration before playback starts.
- Add a Reader Appearance swipe-threshold control for paginated and VN modes;
  setting it to zero disables swipe page turns while preserving hardware page
  keys.
- Add up to three named Anki card formats with independent icons, decks, note
  types, field mappings, tags, and duplicate states.
- Add confirmation before deleting Anki formats and an edit-screen action that
  duplicates a format and returns to the format list.
- Add Anki duplicate-note search buttons that appear only for matching notes,
  plus grouped per-format mining and search actions placed before audio.
- Add precise cloze-part handlebars, numeric pitch accent graph handlebars, and
  advanced glossary mapping options for Anki cards.

### Changed

- Increase the default lookup popup size to 500 × 500 dp and allow its
  height to be adjusted up to 1000 dp.
- Align the built-in Lapis, Kiku, and Senren field presets with iOS by no longer
  setting sentence-card marker fields.

### Fixed

- Allow importing supported files whose display names contain `#` or `?`,
  including EPUB volume numbers such as `Book #01.epub`.
- Base reduced-motion popup scrolling on the portion of the popup that is
  actually visible when its configured height exceeds the screen.
- Show Reader Contents, fragment jumps, and chapter labels for EPUBs whose
  navigation document is stored in a subdirectory.
- Prevent staggered multi-touch taps in paginated and VN Reader modes from
  being mistaken for a page-turn swipe.
- Keep Google Drive sync and TTU backup restore compatible with progress files
  whose book data IDs exceed Android's 32-bit integer range.
- Keep dictionary lookup available when tapping Sasayaki-highlighted text in VN
  Reader mode.

## [v1.3.2] - 2026-08-04

### Added

- Add the option to create a new shelf while moving one or more selected books.
- Add a Reader image gallery, true table-of-contents chapter ranges, and optional
  current-chapter progress in Reader chrome and statistics.
- Add a configurable daily statistics reset time and pause reading statistics
  while Reader sheets or fullscreen images are open.
- Add optional current-book cover publishing for the Android lock screen and a
  fixed PNG file used by compatible E-ink sleep-screen tools, plus direct
  integration with iReader’s built-in Book Cover screen saver on compatible
  domestic and Musnap overseas firmware using standard PNG output, with Fit,
  Fill, and Stretch scaling modes.

### Changed

- Expand Sasayaki delay adjustment to -4...4 seconds and playback speed to
  0.5...3x.
- Rename the Reader Go to panel to Contents, order its tabs as Chapters,
  Highlights, Gallery, and Search, and remove overscroll deformation from
  scrolling surfaces throughout the app.

### Fixed

- Keep Reader progress, search, and Sasayaki character offsets stable around
  numeric HTML entities, and keep lookup sentence expansion and recursive
  expression-tag scanning within the selected text boundary.
- Keep manual bookshelf sync from rebuilding the entire shelf, while refreshing
  imported reading progress in place.
- Keep large bookshelves smooth during repeated scrolling by reusing
  size-appropriate persistent cover thumbnails instead of decoding original
  covers again after they leave memory, while recovering from transient
  generation failures or damaged thumbnail-cache entries without hiding valid
  covers.
- Remember the selected Contents and Sasayaki tabs for the current Reader
  session, and keep Sasayaki on the current tab after importing an audiobook.
- Keep VN lookups and mined Anki sentences complete when a word or sentence
  continues onto a later screen.
- Keep Anki audio, book covers, Sasayaki clips, and dictionary media from
  overwriting different exported media by using content-specific filenames.
- Keep dictionary definitions in the configured dictionary order when an
  inflected lookup merges multiple deinflection candidates.
- Keep Sasayaki jumps to cues in the previous chapter from counting the target
  chapter in the current reading session when image holding is enabled.

## [v1.3.1] - 2026-07-11

### Added

- Add a Reader Appearance setting for top safe area height.

### Changed

- Improve dictionary lookup and import behavior by honoring Yomitan term scores
  and normalizing Japanese iteration marks, full-width numbers, and emphatic
  sequences.
- Raise Statistics daily goal limits to 200,000 characters and 12 hours.

### Fixed

- Keep the Statistics tab visible after enabling it and switching away from Settings.
- Refresh Statistics by-book covers when changing calendar ranges.
- Keep Reader lookup highlights from expanding to an entire ruby annotation when
  selecting a shorter word inside it.
- Keep VN vertical text from jumping to a new column immediately after a ruby
  annotation.
- Keep long-pressed Reader volume keys paging or seeking Sasayaki instead of
  falling back to system volume changes after the first press.

## [v1.3.0] - 2026-07-01

### Added

- Add a full-library Statistics tab with habit summaries, calendar range browsing, per-book distribution, daily and weekly goals, and an Advanced Statistics visibility switch.

### Changed

- Open the Reader Go to panel on Chapters by default, order its tabs as Chapters, Highlights, and Search, and focus the search field when Search is selected.

### Fixed

- Prefer exact expression-and-reading local audio matches before falling back to reading-only or expression-only entries.
- Read Sasayaki M4B title, author, and cover metadata from MP4 atoms when Android's platform metadata reader returns empty.
- Improve VN reader media screens, first-highlight display, vertical layout, punctuation wrapping, and lookup and Sasayaki highlight alignment.
- Keep VN and continuous vertical reader content aligned to the configured vertical padding instead of the bottom overlap area.
- Prevent reader lookups from crashing on words that begin with supplementary-plane kanji such as 𠮟.
- Keep Sasayaki image hold active while viewing fullscreen Reader images, and avoid repeated holds once the continuous Reader target image is already visible.
