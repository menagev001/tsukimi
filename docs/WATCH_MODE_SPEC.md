# Watch Mode Spec

Date: 2026-10-24
Status: draft, agreed in outline

Watch mode adds a Jellyfin video player to Tsukimi with tappable Japanese
subtitles, the existing dictionary popup and AI lookups, and fully automatic
Anki cards with screenshot and sentence audio. The same automatic card flow
then replaces the current book mining pipeline (share JSON → Google Drive →
n8n → "Sentence Mining" sheet → cards made by hand in Migaku).

Target device: Android tablet first (phone works the same). Desktop is out of
scope.

## Goals

- Watch anything in the Jellyfin libraries, at home and away, from Tsukimi.
- Tap any word in the Japanese subtitle and get the same popup as in the reader:
  Yomitan dictionaries, AI word nuance, AI sentence breakdown.
- One tap makes a complete card in Anki, Migaku style, without editing.
- Combine two or more subtitle lines into one card.
- Books get the same automatic card, so there is one pipeline for everything.
- Move the existing Migaku deck to Anki and drop the Migaku subscription.

## Non-goals

- Changing the official Jellyfin apps or the Jellyfin server.
- Desktop player.
- Picture subtitles (PGS, VobSub). Bazarr is set to fetch `.srt` instead.
- Plain machine translation of subtitles.

## System Overview

```
Seerr → Sonarr/Radarr → library on kura
Bazarr → <video>.ja.srt / <video>.en.srt next to the video
         (subtitle-translator fills JA for western films)

Tsukimi (tablet)
  Watch tab ── jellyfin.feathercomet.com (Cloudflare tunnel) ── Jellyfin on kura
     │ video + audio: Media3 ExoPlayer
     │ subtitle text: Jellyfin subtitle stream API, drawn by Tsukimi
     ▼
  tap word → popup (hoshi-web, native dictionary) → AI (Claude API)
     │
     ▼
  "Add card" → card builder → AnkiDroid → AnkiWeb sync → Anki on the desktop
```

Nothing new runs on the NAS unless the audio clip test in Phase 3 fails (see
Sentence Audio).

## Phase 1: Jellyfin Player

- New top-level **Watch** tab next to Books, Dictionary, Statistics and Settings,
  with its own Nav3 back stack (same pattern as the other tabs).
- Server setup in Settings: server URL (default suggestion
  `https://jellyfin.feathercomet.com`), username and password. Tsukimi logs in with
  the Jellyfin Kotlin SDK (`org.jellyfin.sdk`) and stores the access token in
  the existing DataStore pattern, never the password.
- Browse: libraries → series → seasons → episodes, or films. "Continue watching"
  and "Next up" rows from Jellyfin at the top.
- Playback with Media3 ExoPlayer (already a dependency, version 1.10.1).
  The stream URL comes from Jellyfin `PlaybackInfo`: direct play when the tablet
  can decode the file, otherwise Jellyfin transcodes (QuickSync on kura). A
  quality setting for away from home (original / 8 / 4 / 2 Mbit/s).
- Progress reporting to Jellyfin (playback start, progress every ~10 s, stop),
  so watched state and "Continue watching" match the official apps.
- Audio track picker (Japanese by default when present).
- Player controls: play/pause, seek, ±10 s, previous/next subtitle line,
  playback speed.

## Phase 2: Tappable Subtitles

### Getting the subtitle text

- Jellyfin 12.2 exposes text subtitles (embedded text tracks and external
  `.srt` files) through
  `GET /Videos/{itemId}/{mediaSourceId}/Subtitles/{index}/Stream.{format}`.
  Tsukimi downloads the whole Japanese track once per episode as `srt` and parses
  it into cues (start, end, text). ExoPlayer's own subtitle rendering stays off.
- Track choice: the stream tagged `jpn`, then an untagged text track whose
  content detects as Japanese (kana check). The user can pick another track in
  the player.
- The English track (if any) is downloaded too. It is used for the optional
  second subtitle line and for the card's translation field.
- Subtitle offset setting (±0.1 s steps), saved per episode, for subtitles that
  are slightly out of sync.

### Showing and tapping

- The current cue is drawn in a transparent WebView overlay at the bottom of
  the video, using the same hoshi-web text selection code as the reader
  (`hoshiSelection`), so word boundaries, scanning and the popup behave the same.
  Plain white text with a soft outline; size setting. No ASS styling.
- Tapping a word pauses playback and opens the existing lookup popup (popup
  iframe + `ReaderLookupPopupWebBridge`). Closing the popup resumes playback
  (setting: resume automatically or stay paused).
- No English line on screen (decided 2026-10-24); the English track is still
  downloaded for the card's translation field.
- Optional: subtitle list panel (side sheet on the tablet) with all cues; tap a
  cue to jump to it. Looking up words works there too.

### AI with context

Watch mode sends the AI lookups the subtitle that contains the word, plus the
**2 subtitle lines before and 2 after** as context. The user message becomes:

```
Word: <word>
Context before:
<line −2>
<line −1>
Sentence: <the line, or the merged lines>
Context after:
<line +1>
<line +2>
```

The sentence breakdown gets the same block without the `Word:` line. The default
prompts `word_nuance.md` and `sentence_breakdown.md` get one added rule: context
lines are only for understanding, analyse only the `Sentence`. The prompts stay
editable in AI Settings as today. Book lookups keep sending the single sentence.

## Phase 3: Automatic Cards

### Creating a card

- An **Add card** button in the popup (same place as the current Anki button).
  The card is created immediately, in the background, while the video continues.
  A small "Card being made…" chip shows progress with **Cancel**. When it is done
  the chip shows "Card added" with **Undo** for a few seconds.
- The AnkiDroid API cannot delete notes. Undo therefore tags the note
  `hoshi::undo` and suspends it; a note on the AnkiDroid side lists these for
  deletion. Cancel before the note is added just stops the job.
- Duplicate check with `findDuplicateNotes` on the expression; the chip warns
  but still allows adding.

### Combining lines

- Before adding, the popup shows the line used for the card with
  **+ previous line** and **+ next line** buttons (can be pressed more than
  once). The merged text becomes the sentence. The audio clip and the context
  lines cover the merged range; the screenshot is taken at the original line.

### Card fields

The card uses the existing Anki field mapping system (`AnkiFieldTemplates`,
handlebars like `{expression}`). New handlebars:

| Handlebar | Content |
|---|---|
| `{ai-word-nuance}` | AI word nuance result (the popup result is reused when it was already asked) |
| `{ai-sentence-breakdown}` | AI sentence breakdown, including its English translation |
| `{ai-example-sentence}` | AI example sentence, see below (empty when not needed) |
| `{sentence-translation}` | Matching English subtitle line(s) by time overlap, empty for books |
| `{screenshot}` | Video frame of the line |
| `{sentence-audio}` | Audio clip of the line (or merged lines) |
| `{source}` | Film or series title, `S01E03`, timestamp `12:34` |

Existing handlebars keep working: `{expression}`, `{reading}`,
`{furigana-plain}`, `{audio}` (word audio), `{glossary-first}`, `{glossary}`,
`{sentence}`, pitch accent and frequency. `{sasayaki-audio}` keeps meaning the
audiobook clip in books; Lapis' `SentenceAudio` maps to `{sentence-audio}` in
Watch mode and `{sasayaki-audio}` in books. `Picture` maps to `{screenshot}` in
Watch mode and `{book-cover}` in books.

Lapis has no fields for the AI results. The note type gets extra fields
(`AINuance`, `AIBreakdown`, `ExampleSentence`, `SentenceTranslation`, `Source`)
added once in Anki on the desktop, plus a back-template block that shows them.
The Phase 3 setup step lists exactly what to add.

Card tags: `hoshi`, `hoshi::watch` or `hoshi::book`, and the series/book title.

### Example sentence

- New AI prompt **Example sentence**, editable in AI Settings like the other two
  (`ai-prompts/example_sentence.md` + override in `AiSettings`). Default text:

  > Write one natural Japanese example sentence for the word [word] that makes
  > its meaning unambiguous through context. Let the surrounding words do the
  > explaining (situation, contrast, or irony) rather than just illustrating the
  > word in isolation.
  >
  > Rules:
  > - One sentence only, short to medium length, natural and memorable. Shorter is better.
  > - Use the word in its natural collocation if it rarely appears alone.
  > - Output only the Japanese sentence. No reading, translation, explanation, label, or any other text.

  `[word]` is filled in by Tsukimi.
- Asked automatically when the sentence is longer than a set number of
  characters (setting, default 40), for books and video. The original sentence
  stays on the card as context; the example sentence goes in `ExampleSentence`.
  A button in the popup asks for one by hand.

### Screenshot

- Taken on the tablet from the player surface with `PixelCopy` at the moment of
  the line (the frame you see, without the subtitle overlay), scaled to max
  1280 px wide, saved as WebP/JPEG, added with `addMediaFromUri`.

### Sentence audio

- First choice, on the tablet: Media3 Transformer clips the line's time range
  from the same stream URL, audio only, AAC. Tsukimi already does exactly this for
  audiobook cues in `SasayakiCueAudioExporter`. Padding setting (default
  0.25 s before and after). Jellyfin is asked for an AAC audio stream so the
  tablet never has to decode DTS/TrueHD.
- Phase 3 starts with a test of this on a direct-play film and a transcoded
  episode, through the tunnel. Only if it is too slow or unreliable: a small
  `clipper` container on kura (ffmpeg, reads the file from `media`, returns
  JPEG + MP3 for a time range), exposed through the tunnel with a token.

### Failure handling

- No AI key or AI error: the card is still added without the AI fields and
  tagged `hoshi::ai-missing`. A "Fill AI fields" action later fills them with
  `updateNoteFields`.
- No network to Jellyfin while clipping: card added without audio, tagged
  `hoshi::audio-missing`.

## Phase 4: Books on the Same Pipeline

- The existing Anki button in the reader gets the same background job: AI
  nuance, AI breakdown and (for long sentences) the example sentence are added
  automatically, using the field mapping above.
- One-time import of the open rows (`card_created` = FALSE) of the "Sentence
  Mining" sheet: export as CSV, import in Tsukimi (Settings → Anki → Import word
  list), each row runs the normal card builder with dictionary lookup. Rows
  with type `Claude` always get an example sentence.
- After that the share-to-Drive step, the n8n workflow "Process Sentence Mining"
  and the sheet are retired (n8n workflow deactivated, not deleted).

## Phase 5: Migaku Migration

- Migaku has no official export. The community userscript Migaku-Exporter
  (github.com/wa-ra-ki/Migaku-Exporter, fork of SirOlaf/migaku-anki-exporter)
  exports Migaku Memory decks as `.apkg` with media, card intervals and review
  history. Test on one small deck first, check intervals in Anki, then export
  the rest.
- Map the exported fields onto the Lapis note type in Anki on the desktop.
- Known words: Tsukimi already imports Migaku known-word exports
  (`KnownWordsRepository`); do a final export before the subscription ends.
- Keep Migaku until the Anki deck has been checked for a week of reviews.

## Settings Summary

- Watch: server URL, login, quality away from home, default audio language,
  subtitle size, resume after lookup, subtitle offset.
- Anki: Watch-mode field mapping, audio padding, example-sentence length limit.
- AI: Example sentence prompt (next to the two existing prompts).

## Open Points

- Exact Lapis field names to add, and the back-template block (Phase 3 setup).
- Which Jellyfin server Tsukimi uses: `jellyfin-public` (jellyfin.feathercomet.com)
  or `jellyfin-private` (private.feathercomet.com). The setting allows either.
- Whether undo should hard-delete through AnkiConnect when the desktop is on.
- Jellyfin Kotlin SDK version: the newest release that is at least two weeks old
  when Phase 1 starts.

## Validation

Each phase follows `docs/VALIDATION.md`. UI strings go in `values/strings.xml`
and `values-zh-rCN/strings.xml`. Branch per phase: `codex/watch-player`,
`codex/watch-subtitles`, `codex/watch-cards`, `codex/book-auto-cards`.
