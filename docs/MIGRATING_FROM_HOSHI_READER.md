# Moving from Hoshi Reader to Tsukimi

Tsukimi has its own application ID, so it installs next to Hoshi Reader
instead of updating it. Nothing moves over by itself; this page lists what to
bring along and how. Keep Hoshi Reader installed until Tsukimi works.

## What moves how

| What | How |
|---|---|
| Books, with reading progress and bookmarks | Books backup |
| Dictionaries | Dictionaries backup |
| Manga volumes | Import the CBZ files again |
| Reader settings (font, size, margins, e-ink options) | Set again by hand |
| AI API key, Anki setup | Set again by hand |
| Known words | Import the word list again; Anki words come back by themselves |
| Anki cards | Stay in AnkiDroid; nothing to do |

Take a screenshot of Hoshi Reader's Appearance and Behavior settings before
you start, so you can copy them.

## 1. Back up in Hoshi Reader

Settings → Advanced → Backup:

- **Books** → Export: saves a `.hoshi` file.
- **Dictionaries** → Export: saves a second `.hoshi` file.

## 2. Install Tsukimi

Download `Tsukimi-v<version>-arm64-v8a.apk` from the GitHub release and open
it; Android asks once to allow installs from your browser or file manager.
Later updates install from Settings → About.

## 3. Restore in Tsukimi

Settings → Advanced → Backup:

- **Books** → Import: pick the books `.hoshi` file.
- **Dictionaries** → Import: pick the dictionaries `.hoshi` file.

## 4. Set up once

- **Settings → AI**: paste the API key.
- **Settings → Anki**: tap **Fetch** and allow access to AnkiDroid. Fetch maps
  every field of the Lapis note type (see [ANKI_LAPIS_SETUP.md](ANKI_LAPIS_SETUP.md)).
  Under **Decks by Source**, pick a deck for books, manga and Watch, or
  **New Deck…** for one that does not exist yet.
- **Settings → Known Words**: **Import Word List** if you have one, and keep
  **Add Mature Anki Words** on.
- **Manga tab**: **+** to import CBZ files, or the folder button for a whole
  folder.
- **Settings → Appearance / Behavior**: copy your old settings from the
  screenshots (E-ink Mode for e-ink readers).
- **Settings → Watch** (only where you watch video): sign in to Jellyfin.

When everything is there, uninstall Hoshi Reader.

## Several devices

Tsukimi keeps its library on each device. Anki is what ties devices
together: with AnkiDroid signed in to the same AnkiWeb account on every
device, cards mined anywhere reach all of them, and each device's Known Words
follow its own AnkiDroid. Turn on **Settings → Anki → Sync AnkiDroid after
adding card** so new cards go to AnkiWeb right away.

Books can also follow you between devices through Google Drive sync
(Settings → Advanced → Sync); manga volumes and words marked known by hand
stay on the device where they are.
