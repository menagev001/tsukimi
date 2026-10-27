package moe.antimony.hoshi.features.anki

object AnkiFieldTemplates {
    private val templates = mapOf(
        // One format for books, manga and video: values that do not apply to a source are empty.
        "Lapis" to mapOf(
            "Expression" to "{expression}",
            "ExpressionFurigana" to "{furigana-plain}",
            "ExpressionReading" to "{reading}",
            "ExpressionAudio" to "{audio}",
            "SelectionText" to "{popup-selection-text}",
            "MainDefinition" to "{glossary-first}",
            "Sentence" to "{sentence}",
            "SentenceFurigana" to "{sentence-furigana}",
            "SentenceAudio" to "{sentence-audio}{sasayaki-audio}",
            "Picture" to "{screenshot}{book-cover}",
            "IsAudioCard" to "{card-type-audio}",
            "IsSentenceCard" to "{card-type-sentence}",
            "Source" to "{source}",
            "SentenceTranslation" to "{sentence-translation}",
            "OriginalSentence" to "{original-sentence}",
            "AINuance" to "{ai-word-nuance}",
            "AIBreakdown" to "{ai-sentence-breakdown}",
            "Glossary" to "{glossary}",
            "PitchPosition" to "{pitch-accent-positions}",
            "PitchCategories" to "{pitch-accent-categories}",
            "Frequency" to "{frequencies}",
            "FreqSort" to "{frequency-harmonic-rank}",
            "MiscInfo" to "{document-title}",
        ),
        "Kiku" to mapOf(
            "Expression" to "{expression}",
            "ExpressionFurigana" to "{furigana-plain}",
            "ExpressionReading" to "{reading}",
            "ExpressionAudio" to "{audio}",
            "SelectionText" to "{popup-selection-text}",
            "MainDefinition" to "{glossary-first}",
            "Sentence" to "{sentence}",
            "SentenceAudio" to "{sasayaki-audio}",
            "Picture" to "{book-cover}",
            "Glossary" to "{glossary}",
            "PitchPosition" to "{pitch-accent-positions}",
            "PitchCategories" to "{pitch-accent-categories}",
            "Frequency" to "{frequencies}",
            "FreqSort" to "{frequency-harmonic-rank}",
            "MiscInfo" to "{document-title}",
        ),
        "Senren" to mapOf(
            "word" to "{expression}",
            "reading" to "{reading}",
            "sentence" to "<span class=\"group\">{cloze-prefix}<span class=\"highlight\">{cloze-body}</span>{cloze-suffix}</span>",
            "selectionText" to "{popup-selection-text}",
            "definition" to "{glossary-first}",
            "wordAudio" to "{audio}",
            "sentenceAudio" to "{sasayaki-audio}",
            "picture" to "{book-cover}",
            "glossary" to "{glossary}",
            "pitchPositions" to "{pitch-accent-positions}",
            "pitchCategories" to "{pitch-accent-categories}",
            "frequencies" to "{frequencies}",
            "freqSort" to "{frequency-harmonic-rank}",
            "miscInfo" to "{document-title}",
        ),
    )

    fun matches(noteType: AnkiNoteType): Boolean =
        templates.containsKey(noteType.name)

    fun defaultMappings(noteType: AnkiNoteType): Map<String, String> {
        val defaults = templates[noteType.name].orEmpty()
        return noteType.fields.mapNotNull { field ->
            defaults[field]?.let { field to it }
        }.toMap()
    }

    fun applyDefaultsIfUnmapped(
        noteType: AnkiNoteType,
        currentMappings: Map<String, String>,
    ): Map<String, String> {
        if (!matches(noteType)) return currentMappings
        if (noteType.fields.any { field -> currentMappings[field] != null }) return currentMappings
        return defaultMappings(noteType)
    }
}
