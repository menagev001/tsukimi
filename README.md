<div align="center">

# 月見 Tsukimi

![Platform](https://img.shields.io/badge/platform-Android-lightgrey)
![License](https://img.shields.io/badge/license-GPLv3-blue)
[![Download](https://img.shields.io/github/downloads/menagev001/tsukimi/total?label=download)](https://github.com/menagev001/tsukimi/releases)
[![Latest download](https://img.shields.io/github/downloads/menagev001/tsukimi/latest/total?label=latest%20download)](https://github.com/menagev001/tsukimi/releases/latest)

**English** | [简体中文](README.zh-CN.md)

Tsukimi is a Japanese immersion app for Android: read EPUB books and manga, watch your Jellyfin videos with tappable Japanese subtitles, look words up in Yomitan dictionaries, ask an AI to explain words and sentences, and turn everything you read and watch into Anki cards in the background.

Tsukimi is based on [Hoshi Reader Android](https://github.com/HuangAntimony/Hoshi-Reader-Android) by HuangAntimony, a native Android recreation of [Hoshi Reader](https://github.com/Manhhao/Hoshi-Reader) by Manhhao. See [Based On](#based-on) for what is inherited and what is new.

<table>
  <tr>
    <td><img src="docs/images/readme/bookshelf.jpg" alt="Bookshelf" width="100%"></td>
    <td><img src="docs/images/readme/reader-lookup-popup.jpg" alt="Reader lookup popup" width="100%"></td>
    <td><img src="docs/images/readme/reader-dark-theme.jpg" alt="Dark reader" width="100%"></td>
    <td><img src="docs/images/readme/reader-eink-mode.jpg" alt="E-ink reader" width="100%"></td>
  </tr>
  <tr>
    <td><img src="docs/images/readme/sasayaki-audiobook.jpg" alt="Sasayaki audiobook" width="100%"></td>
    <td><img src="docs/images/readme/reader-statistics.jpg" alt="Reader statistics" width="100%"></td>
    <td><img src="docs/images/readme/reader-highlights.jpg" alt="Reader highlights" width="100%"></td>
    <td><img src="docs/images/readme/dictionary-recursive-lookup.jpg" alt="Dictionary recursive lookup" width="100%"></td>
  </tr>
  <tr>
    <td><img src="docs/images/readme/reader-appearance-settings.jpg" alt="Reader appearance settings" width="100%"></td>
    <td><img src="docs/images/readme/dictionary-management.jpg" alt="Dictionary management" width="100%"></td>
    <td><img src="docs/images/readme/anki-card-settings.jpg" alt="Anki card settings" width="100%"></td>
    <td><img src="docs/images/readme/sync-settings.jpg" alt="Sync settings" width="100%"></td>
  </tr>
</table>

</div>

## Features

Sections marked **(Tsukimi)** are new in Tsukimi; the rest is inherited from Hoshi Reader Android.

### Watch (Tsukimi)

- Sign in to your own [Jellyfin](https://jellyfin.org) server (version 12 or newer) and browse Continue Watching, Next Up and your film and series libraries.
- Play films and episodes full screen. Files the device can decode play directly; anything else is transcoded by Jellyfin. Playback resumes where you stopped, and watched state and progress are saved to Jellyfin.
- Japanese audio is chosen by default, and a lower streaming quality can be set for mobile data.
- Japanese subtitles are drawn by Tsukimi in four sizes. Tap a word to pause and look it up, double-tap a line for its sentence breakdown, and double-tap the left or right side of the video to go back or forward 10 seconds.
- A line list panel (swipe in from the right edge) shows the whole track, follows playback, jumps to a line when you tap its time, and looks up words like the subtitles do.
- Fix subtitle timing per episode: step it in 0.1 s steps (hold to go faster), or long-press a line in the list and sync it to the current video time.
- Mine Anki cards while the video keeps playing, with a screenshot, the line's audio, the English subtitle as translation, and the episode and time as source. Join a sentence spread over several subtitles with + Previous line / + Next line.

### Bookshelf

- Import EPUBs individually, in batches, or recursively from folders, and keep reading progress visible from the bookshelf.
- Organize books with custom shelves.
- Export EPUBs or pull remote synced books back into the local library.

### Reading

- Read Japanese books in vertical or horizontal text, with paginated or continuous scrolling.
- Customize themes, fonts, paragraph spacing, and reader controls, including custom reader themes.
- Use immersive focus mode, volume-key page turning, and e-ink display options.
- Open reader images in fullscreen with zoom, copy, save, and share actions.
- **(Tsukimi)** Add furigana to the words you do not know yet, generated from your dictionaries and baked into the chapter, and reduce e-ink ghosting after the lookup popup closes on Onyx Boox devices.

### Manga (Tsukimi)

- Read CBZ volumes with [mokuro](https://github.com/kha-white/mokuro) OCR in their own Manga tab: tap a speech bubble to reveal its text, then tap a word to look it up. Double-tap a bubble for a sentence breakdown.
- Group volumes into series, crop scanned page margins automatically or by hand, and track manga reading statistics.
- Manga cards get the scene around the bubble as their picture, with the mined sentence marked in yellow.

### Lookup

- Import, download, update, and manage Yomitan dictionaries.
- Tap text in the reader, search from the Dictionary tab, or look up selected text from other Android apps.
- Tap unknown words inside definitions for recursive lookup.
- Inject custom CSS styles.
- Use online or local word audio.
- **(Tsukimi)** Use the volume keys to step through the terms of a lookup popup, open lookups from `tsukimi://search?text=...` links, and export a word with its sentence as JSON.

### AI Lookups (Tsukimi)

- Ask an AI (Claude, with your own API key) to explain a looked-up word in its sentence, or double-tap text for a sentence breakdown. In Watch, the lines around the subtitle are sent as context.
- All prompts are editable in the AI settings, and overloaded requests are retried automatically.

### Known Words (Tsukimi)

- Keep a list of the words you know: import a word list (JSON or CSV), mark words known from the lookup popup, and add the words of your mature Anki cards (interval of 21 days or more) automatically each time Tsukimi opens.
- The list decides which words get furigana in books.

### Highlights And Statistics

- Add five-color highlights while reading and jump to them at any time.
- Track reading statistics, including characters read, time spent, and reading speed, with live display while reading.

### Anki Card Mining

- Create cards through AnkiDroid or AnkiConnect.
- Use [Lapis](https://github.com/donkuri/lapis)-compatible fields, duplicate checks, and media export.
- **(Tsukimi)** Cards from books, manga and video are added in the background with a progress chip, so reading and playback continue. One card format serves all three, with new fields for the source, the sentence translation, the screenshot, the sentence audio, sentence furigana and AI answers; one Fetch maps every Lapis field. See [docs/ANKI_LAPIS_SETUP.md](docs/ANKI_LAPIS_SETUP.md) for the matching Lapis template changes.
- **(Tsukimi)** Send cards from books, manga and video to their own decks, and tag them by source and title.
- **(Tsukimi)** A long sentence offers to keep it or to let AI write a short example sentence for the word (long-press the Anki button to get the choice for any sentence). Cards whose AI fields failed can be filled in later from the Anki settings.

### Audiobook Read-Along

- Match audiobook subtitle files to book text to highlight the current sentence.
- Follow highlights with automatic page turning.
- Control playback speed, skip actions, and Android media controls.

### Data Sync And Migration

- Sync reading progress, statistics, and books through Google Drive, compatible with ッツ Reader.
- Import or export ッツ bookdata, and back up or restore books and dictionaries with `.hoshi` archives compatible with Hoshi Reader iOS.
- **(Tsukimi)** Also back up manga, and all settings with profiles, fonts and known words, to move Tsukimi to another device. Coming from Hoshi Reader? See [docs/MIGRATING_FROM_HOSHI_READER.md](docs/MIGRATING_FROM_HOSHI_READER.md).

## Download

Download the latest Tsukimi APK from [GitHub Releases](https://github.com/menagev001/tsukimi/releases/latest).

Tsukimi requires Android 8.0 or later.

## Development Status

Tsukimi is developed for personal use on an Onyx Boox e-ink reader and an Android tablet, and shared as is. See [docs/CHANGELOG.md](docs/CHANGELOG.md) for shipped user-visible changes.

Bug reports and feature requests are welcome as [issues](https://github.com/menagev001/tsukimi/issues).

## Privacy And Data

Tsukimi stores imported books, manga, dictionaries, fonts, audiobook data, reading progress, highlights, statistics, known words, and settings locally in app storage.

Google Drive sync uses a user-configured Google Cloud OAuth device-code flow. Anki card mining talks to AnkiDroid or the configured AnkiConnect endpoint. Watch talks only to the Jellyfin server you sign in to and keeps a session token, never your password. AI lookups send the word, its sentence and nearby lines to the Anthropic API with your own key, only when you ask. Update checks read GitHub release metadata.

## Based On

Tsukimi is a modified version of [Hoshi Reader Android](https://github.com/HuangAntimony/Hoshi-Reader-Android) by HuangAntimony, which is a native Android recreation of [Hoshi Reader](https://github.com/Manhhao/Hoshi-Reader) by Manhhao. Both are licensed under GPL-3.0, and so is Tsukimi. The code was forked shortly after Hoshi Reader Android v1.3.3 and is no longer synced with upstream.

Added in Tsukimi: video watching through Jellyfin with tappable subtitles, manga support (CBZ and mokuro), AI word and sentence lookups, background Anki card mining for books, manga and video, known words with automatic furigana, more backups, and the Tsukimi name and release channel.

It builds on this ecosystem:

- [hoshidicts](https://github.com/Manhhao/hoshidicts) and [hoshidicts-kotlin-bridge](https://github.com/HuangAntimony/hoshidicts-kotlin-bridge) for Yomitan dictionary support.
- [Yomitan](https://github.com/yomidevs/yomitan) for dictionary format and lookup inspiration.
- [AnkiDroid](https://github.com/ankidroid/Anki-Android) for Android card creation integration.
- [Ankiconnect Android](https://github.com/KamWithK/AnkiconnectAndroid) for local audio behavior and AnkiDroid duplicate scope/checksum query references.
- [ッツ Ebook Reader](https://github.com/ttu-ttu/ebook-reader) for reader, statistics, and sync compatibility references.
- [mokuro](https://github.com/kha-white/mokuro) for manga OCR data.
- [Jellyfin](https://jellyfin.org) and its [Kotlin SDK](https://github.com/jellyfin/jellyfin-sdk-kotlin) for video libraries and streaming, played with Android Media3.
- [Zen Antique](https://fonts.google.com/specimen/Zen+Antique) (SIL Open Font License) for the 月見 of the app icon.

## License

Distributed under the GNU General Public License v3.0. See [LICENSE](LICENSE) for details.
