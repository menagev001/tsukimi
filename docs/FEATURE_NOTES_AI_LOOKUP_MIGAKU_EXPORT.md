# Feature notes: AI lookup + Migaku JSON export

Personal-fork feature notes, saved at the user's explicit request per
`AGENTS.md`'s exception for design/plan docs. Not a source-of-truth doc like
`docs/ARCHITECTURE.md`; update or delete freely as the feature evolves.

## Goal

Two additions on top of upstream Hoshi Reader Android (HuangAntimony fork),
for personal use:

1. **AI lookup**: an "Ask AI" action in the existing dictionary popup
   (single tap, reuses existing word+context), plus a new double-tap gesture
   on reader text that sends full-sentence context to the same AI action
   instead of a single word.
2. **Migaku JSON export**: export local highlights (word + surrounding
   sentence context + book/timestamp/note) as JSON for import into Migaku,
   since the user doesn't use Anki.

## Environment constraints

- Arch Linux. Nothing gets installed outside pacman/AUR tracking.
- Android Studio / SDK: AUR only (`yay -S android-studio`), no manual tarball
  installs to system paths.
- Build tool: repo's `./gradlew` wrapper only, no global `gradle`.
- JDK: isolated version manager (e.g. `jenv`) or AUR package if a specific
  version is needed, never overwrite the system default JDK.
- Flag before any system-wide install and explain what it's for.

## Working conventions

- Match existing architecture: Compose + Material 3, Hilt DI, StateFlow
  ViewModels, Repository layer, DataStore-backed settings.
- Reader JS/CSS logic belongs in `app/src/main/assets/hoshi-web`; Kotlin stays
  a thin typed bridge (small commands, escaping, asset loading, bridge
  invocation) — no large Kotlin string-based JS scripts.
- Conventional Commits.
- Feature work happens on a `codex/`-prefixed branch (this repo currently on
  `codex/ai-lookup-migaku-export`), per `AGENTS.md`'s branch convention for
  larger/cross-module changes.
- Validate against `docs/VALIDATION.md` before calling any step done
  (`./gradlew test`, `./gradlew assembleDebug`, `node --test
  app/src/test/js/*.test.mjs` for reader web asset changes, `./gradlew lint`
  for resource/UI changes).
- Never run a connected Android instrumentation task that clears/reinstalls
  app data without asking first.
- VN (visual novel) reader mode has its own text-range system
  (`reader-vn-content-stream.js`, `reader-vn-range-map.js`,
  `reader-vn-selection-projection.js`) and must not be touched by either
  feature; both features only need to work in paginated/continuous modes.

## Step 1 findings: how the existing systems work

### Tap-to-lookup flow, end to end

- **Native tap detection** (not JS): `SwipePageTouchListener.kt` (used by
  Paginated and VisualNovel view modes) and a separate
  `ContinuousScrollTouchListener` (used by Continuous mode) each capture
  `MotionEvent`s and produce a `Result.Tap(x, y)` immediately on
  `ACTION_UP` — there is currently **no double-tap detection anywhere in the
  reader text surfaces** (only `ReaderFullscreenImageViewer.kt` has
  `onDoubleTap`, for pinch-zoom on fullscreen images, unrelated to reader
  text). `ReaderChapterWebView.kt:301-390` wires `onTap` to a local
  `selectAt(x, y)` closure that evaluates
  `window.hoshiSelection.selectText(x, y, maxLength)` in the WebView via
  `ReaderSelectionCommand.SelectText` (`ReaderSelectionCommand.kt:14`).
- **JS word-boundary + sentence scan**: `hoshiSelection.selectText`
  (`app/src/main/assets/hoshi-web/shared/selection.js:559`) finds the tapped
  character, expands to a matchable-text span using the language policy
  (`shared/selection-ja.js`), and separately computes real sentence context
  via `getSentenceContext` (`selection.js:441`) — this already does a
  terminator scan using `sentenceDelimiters = '。！？.!?\n\r'` (all CJK
  terminators, not just `。`) plus bracket-balance cleanup, and already skips
  `<rt>`/`<rp>` furigana text via `isFurigana`/`createWalker`'s
  `NodeFilter.FILTER_REJECT`. It posts `{ text, sentence, rect,
  normalizedOffset, sentenceOffset }` back to native via
  `postTextSelected` → `window.webkit.messageHandlers.textSelected` (or the
  `android-reader` bridge variant).
- **Bridge to Kotlin popup pipeline**: popup UI itself runs in an iframe
  (`popup/iframe.html` + `popup.js`) hosted inside the reader WebView by
  `reader-popup-host.js`, talking to native through
  `ReaderLookupPopupWebBridge` (`ReaderLookupPopupBridge.kt:451`), a
  `WebMessageListener` on channel `HoshiReaderPopup`. Messages are typed via
  a sealed class `ReaderLookupPopupBridgeMessage`
  (`ReaderLookupPopupBridge.kt:307-386`, variants incl. `TextSelected`,
  `MineEntry`, `PlayWordAudio`, `LookupRedirect`, `DuplicateCheck`,
  `ShowNotes`). `ReaderWebView.kt` (a large `@Composable`, not a ViewModel)
  is the single dispatch point via `handleReaderPopupBridgeMessage`
  (`ReaderWebView.kt:723-857`), calling `dictionaryRepository.lookup(...)` →
  `DictionaryLookupQueryService.lookup` → native JNI lookup in
  `third_party/hoshidicts-kotlin-bridge`. Replies go back to JS via
  `replyReaderPopupMessage(popupId, messageId, bodyJson)`
  (`ReaderWebView.kt:699-707`), resolving a pending JS Promise in
  `reader-popup-host.js`.
- **Anki mining is the template for "Ask AI"**: JS creates a button slot
  (`popup.js:1585-1615`), posts `webkit.messageHandlers.mineEntry` with a
  request/response id, Kotlin's `ReaderWebView.kt:753-763` handles
  `MineEntry` by calling `ankiViewModel.mineEntryAsync(...)` and replying
  through the same `replyReaderPopupMessage`. A new "Ask AI" action should
  mirror this exact shape: new bridge-message variant, new
  `window.webkit.messageHandlers.*` shim entry, new JS button, new Kotlin
  case invoking a new AI service and replying the same way.

### Dictionary popup UI

Renders entirely inside the reader WebView as an iframe stack — **not**
native Compose UI. `reader-popup-host.js` builds a `#hoshi-reader-popup-layer`
containing `<iframe>`s of `popup/iframe.html` running `popup.js`
(styled by `popup/popup.css`, icons under `popup/icons/*.svg`). The only
native Compose piece is hosting the single reader `WebView` itself
(`AndroidView` in `ReaderChapterWebView.kt:225-230`) plus resource-handler
wiring that serves `/popup/*` assets. Per-entry action buttons (audio, Anki
mine, notes) are `button-slot`s created in `popup.js:1585`; the host action
bar (back/forward/close, Sasayaki) is separate host-rendered DOM in
`reader-popup-host.js:95-118`. AGENTS.md explicitly forbids reintroducing a
native overlay popup fallback for these flows — a new "Ask AI" affordance
must stay inside this same JS/iframe system.

### Settings screens

Compose screens live under `app/src/main/java/moe/antimony/hoshi/features/*`
(not one flat "settings" package — e.g. Anki's screen is under
`features/anki/AnkiConnectView.kt`). Routes are Nav3 typed keys in
`navigation/AppRoute.kt`, mapped in `AppShell.kt`'s `entryProvider`. Closest
concrete pattern for a new "AI provider" settings screen: `AnkiConnectView.kt`
— a `@HiltViewModel`-backed screen using `SettingsDetailScaffold`,
`OutlinedTextField` for URL and `OutlinedSecureTextField` for the API key,
`AlertDialog`-based inline editors, and a DataStore-backed repository
(`AnkiSettingsRepository.kt`, `preferencesDataStore(name = "anki_settings")`)
reached through a `@HiltViewModel` (`AnkiViewModel.kt`). A simpler
pure-DataStore example with no profile-JSON mirroring is
`features/update/UpdateSettings.kt` (`UpdateSettingsRepository`). The new AI
settings screen (base URL, model string, API key) should follow the
`AnkiConnectView` + DataStore-repository shape without the profile-JSON
mirroring Anki also does.

### Highlights storage (for Migaku export)

`ReaderHighlight` (`app/src/main/java/moe/antimony/hoshi/epub/ReaderHighlight.kt:44-52`):
`id`, `character` (chapter char offset), `offset`, `text` (the highlighted
span only), `color`, `createdAt` (epoch double). **No stored sentence
context, book title, or note field** — book identity is implicit from the
sidecar file location, not stored per-row. Persisted as a JSON array at
`<bookRoot>/highlights.json`, read/written by
`BookSidecarDataSource.loadHighlights`/`saveHighlights`
(`BookRepository.kt:638-643`), exposed via `BookRepository`/`BookStorage`
facades. The suggested export shape's `sentence` and `note` fields don't
exist on the model today — export will need to (a) re-derive sentence
context from chapter text using the same terminator-scan approach as
Feature 1's double-tap, keyed by `character`/`offset`, and (b) either add a
`note` field to `ReaderHighlight` or omit it / leave it blank, and pull
`book_title` from the book's own metadata sidecar rather than the highlight
row.

## Design decision: double-tap timing (resolved 2026-08-20)

Native tap dispatch currently fires **immediately** on `ACTION_UP` with no
double-tap disambiguation delay (`ReaderSwipeGestureTracker` in
`SwipePageTouchListener.kt`, and the separate `ContinuousScrollTouchListener`).
Decision: introduce a short wait (standard Android double-tap timeout,
~300-400ms, e.g. `ViewConfiguration.getDoubleTapTimeout()`) before dispatching
a *single* tap, so a fast second tap instead dispatches double-tap. Single
tap keeps its existing zero-latency *behavior* (same popup, same content)
but gains a small delay before it fires; double tap fires exactly once with
no intermediate word-popup flash.

Also: Paginated and VisualNovel currently **share** `SwipePageTouchListener`
in `ReaderChapterWebView.kt`'s `when (readerSettings.viewMode)` branch
(`ReaderViewMode.Paginated, ReaderViewMode.VisualNovel -> { ... }`), so
double-tap wiring must be added in a way that's opted out for VN specifically,
not just inherited by both from a shared base-class change. Continuous mode's
`ContinuousScrollTouchListener` is a separate listener entirely and needs its
own double-tap wiring.
