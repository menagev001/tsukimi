<div align="center">

# 月見 Tsukimi

![Platform](https://img.shields.io/badge/platform-Android-lightgrey)
![License](https://img.shields.io/badge/license-GPLv3-blue)
[![Download](https://img.shields.io/github/downloads/menagev001/tsukimi/total?label=download)](https://github.com/menagev001/tsukimi/releases)
[![Latest download](https://img.shields.io/github/downloads/menagev001/tsukimi/latest/total?label=latest%20download)](https://github.com/menagev001/tsukimi/releases/latest)

**English** | [简体中文](README.zh-CN.md)

Tsukimi is a Japanese immersion app for Android: read EPUB books and manga with Yomitan dictionary lookup, ask an AI to explain words and sentences, and turn what you read into Anki cards. Video watching with tappable subtitles is planned.

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

### Bookshelf

- Import EPUBs individually, in batches, or recursively from folders, and keep reading progress visible from the bookshelf.
- Organize books with custom shelves.
- Export EPUBs or pull remote synced books back into the local library.

### Reading

- Read Japanese books in vertical or horizontal text, with paginated or continuous scrolling.
- Customize themes, fonts, paragraph spacing, and reader controls, including custom reader themes.
- Use immersive focus mode, volume-key page turning, and e-ink display options.
- Open reader images in fullscreen with zoom, copy, save, and share actions.

### Lookup

- Import, download, update, and manage Yomitan dictionaries.
- Tap text in the reader, search from the Dictionary tab, or look up selected text from other Android apps.
- Tap unknown words inside definitions for recursive lookup.
- Inject custom CSS styles.
- Use online or local word audio.

### Highlights And Statistics

- Add five-color highlights while reading and jump to them at any time.
- Track reading statistics, including characters read, time spent, and reading speed, with live display while reading.

### AI Lookups

- Ask an AI (Claude) to explain a looked-up word in its sentence, or double-tap text for a sentence breakdown. Both prompts are editable in the AI settings, and overloaded requests are retried automatically.
- Export highlights with their sentence context as JSON for Migaku.

### Manga

- Read CBZ volumes with [mokuro](https://github.com/kha-white/mokuro) OCR: tap a speech bubble to reveal its text, then tap a word to look it up. Double-tap a bubble for a sentence breakdown.
- Group volumes into series, crop scanned page margins automatically or by hand, and track manga reading statistics.

### Anki Card Mining

- Create cards through AnkiDroid or AnkiConnect.
- Use [Lapis](https://github.com/donkuri/lapis)-compatible fields, duplicate checks, and media export.

### Audiobook Read-Along

- Match audiobook subtitle files to book text to highlight the current sentence.
- Follow highlights with automatic page turning.
- Control playback speed, skip actions, and Android media controls.

### Data Sync And Migration

- Sync reading progress, statistics, and books through Google Drive, compatible with ッツ Reader.
- Import or export ッツ bookdata, and back up or restore books and dictionaries with `.hoshi` archives compatible with Hoshi Reader iOS.

## Download

Download the latest Tsukimi APK from [GitHub Releases](https://github.com/menagev001/tsukimi/releases/latest).

Tsukimi requires Android 8.0 or later.

## Development Status

Tsukimi is developed for personal use on an e-ink Android tablet and shared as is. See [docs/CHANGELOG.md](docs/CHANGELOG.md) for shipped user-visible changes.

Bug reports and feature requests are welcome as [issues](https://github.com/menagev001/tsukimi/issues).

## Privacy And Data

Tsukimi stores imported books, dictionaries, fonts, audiobook data, reading progress, highlights, statistics, and settings locally in app storage.

Google Drive sync uses a user-configured Google Cloud OAuth device-code flow. Anki card mining talks to AnkiDroid or the configured AnkiConnect endpoint. Update checks read GitHub release metadata.

## Based On

Tsukimi is a modified version of [Hoshi Reader Android](https://github.com/HuangAntimony/Hoshi-Reader-Android) by HuangAntimony, which is a native Android recreation of [Hoshi Reader](https://github.com/Manhhao/Hoshi-Reader) by Manhhao. Both are licensed under GPL-3.0, and so is Tsukimi. The code was forked shortly after Hoshi Reader Android v1.3.3 and is no longer synced with upstream.

Added in Tsukimi: AI word and sentence lookups, Migaku JSON export, manga support (CBZ and mokuro), and the Tsukimi name and release channel.

It builds on this ecosystem:

- [hoshidicts](https://github.com/Manhhao/hoshidicts) and [hoshidicts-kotlin-bridge](https://github.com/HuangAntimony/hoshidicts-kotlin-bridge) for Yomitan dictionary support.
- [Yomitan](https://github.com/yomidevs/yomitan) for dictionary format and lookup inspiration.
- [AnkiDroid](https://github.com/ankidroid/Anki-Android) for Android card creation integration.
- [Ankiconnect Android](https://github.com/KamWithK/AnkiconnectAndroid) for local audio behavior and AnkiDroid duplicate scope/checksum query references.
- [ッツ Ebook Reader](https://github.com/ttu-ttu/ebook-reader) for reader, statistics, and sync compatibility references.
- [mokuro](https://github.com/kha-white/mokuro) for manga OCR data.

## License

Distributed under the GNU General Public License v3.0. See [LICENSE](LICENSE) for details.
