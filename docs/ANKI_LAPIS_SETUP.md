# Lapis Setup for Tsukimi Cards

Tsukimi fills a few fields that the Lapis note type does not have, and it
switches Lapis' card type per card. This page lists the one-time changes in
Anki (AnkiDroid or desktop Anki). Desktop changes reach AnkiDroid through
AnkiWeb sync and the other way round.

In AnkiDroid all three parts are under **Manage note types → Lapis**:
**Fields**, and **Cards** (front template, back template, styling).

## 1. Fields

Add these fields to Lapis (names exactly as written):

- `Source`
- `SentenceTranslation`
- `OriginalSentence`
- `AINuance`
- `AIBreakdown`

Then in Tsukimi: **Settings → Anki → Fetch**. Fetch maps every field Tsukimi
knows, including `SentenceAudio`, `Picture`, `IsAudioCard` and
`IsSentenceCard`.

## 2. Front template: audio card with only a play button

Lapis' audio card shows the sentence with the word blanked out next to the
audio. To keep only the audio, find this block:

```html
    {{#IsAudioCard}}
    <div id="audio">
        <div lang="ja" class="front-sentence">{{kanji:Sentence}}</div>
        <div>
```

and delete the third line, so it becomes:

```html
    {{#IsAudioCard}}
    <div id="audio">
        <div>
```

## 3. Back template

### Translation under the sentence

The back template shows the sentence in two places (`class="sentence"` and
`class="sentence-alt"`, Lapis picks one with its sentence position setting).
In **both**, find this line:

```html
            {{^SentenceFurigana}} {{furigana:Sentence}} {{/SentenceFurigana}}
```

and add this line directly below it:

```html
            {{#SentenceTranslation}}<div class="tsukimi-translation">{{SentenceTranslation}}</div>{{/SentenceTranslation}}
```

### AI fields, original sentence and source

Find this line (near the end of `<main>`):

```html
        {{#MiscInfo}}
```

and paste this block directly above it:

```html
        {{#AINuance}}
        <details open>
            <summary>AI word nuance</summary>
            <div class="tsukimi-ai">{{AINuance}}</div>
        </details>
        {{/AINuance}}
        {{#AIBreakdown}}
        <details>
            <summary>AI sentence breakdown</summary>
            <div class="tsukimi-ai">{{AIBreakdown}}</div>
        </details>
        {{/AIBreakdown}}
        {{#OriginalSentence}}
        <details>
            <summary>Original sentence</summary>
            <div class="tsukimi-original" lang="ja">{{OriginalSentence}}</div>
        </details>
        {{/OriginalSentence}}
        {{#Source}}
        <div class="tsukimi-source">{{Source}}</div>
        {{/Source}}
```

## 4. Styling

Add at the end of the styling:

```css
.tsukimi-translation {
    margin-top: 0.4em;
    font-size: 0.8em;
    opacity: 0.75;
}

.tsukimi-ai,
.tsukimi-original {
    margin: 0.4em 0 0.8em;
    font-size: 0.85em;
    line-height: 1.5;
    text-align: left;
}

.tsukimi-source {
    margin-top: 0.8em;
    font-size: 0.75em;
    opacity: 0.6;
}
```

## Card types

Tsukimi sets one of Lapis' card type switches per card:

- **Audio card** (`IsAudioCard`): Watch cards that keep their original line,
  so the card has the line's audio.
- **Sentence card** (`IsSentenceCard`): books, manga, and Watch cards whose
  sentence is an AI example sentence.

To change a card's type later, edit those two fields (an `x` switches a type
on, an empty field switches it off).
