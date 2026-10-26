package moe.antimony.hoshi.features.anki

import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import moe.antimony.hoshi.features.ai.AiClient
import moe.antimony.hoshi.features.ai.AiRepository
import moe.antimony.hoshi.features.ai.AiSettingsRepository
import moe.antimony.hoshi.features.audio.LocalAudioRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AnkiCardJobRunnerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun aiExampleSentenceReplacesTheSentenceAndKeepsTheOriginal() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        try {
            val deck = AnkiDeck(1L, "Mining")
            val noteType = AnkiNoteType(
                2L,
                "Lapis",
                listOf("Expression", "Sentence", "OriginalSentence", "SentenceTranslation", "IsSentenceCard"),
            )
            val backend = FieldRecordingBackend(deck, noteType)
            val ankiRepository = AnkiRepository(
                context = ContextWrapper(null),
                backend = backend,
                settingsRepository = FixedAnkiSettings(
                    AnkiSettings(
                        selectedDeckId = deck.id,
                        selectedDeckName = deck.name,
                        selectedNoteTypeId = noteType.id,
                        selectedNoteTypeName = noteType.name,
                        availableDecks = listOf(deck),
                        availableNoteTypes = listOf(noteType),
                        fieldMappings = mapOf(
                            "Expression" to "{expression}",
                            "Sentence" to "{sentence}",
                            "OriginalSentence" to "{original-sentence}",
                            "SentenceTranslation" to "{sentence-translation}",
                            "IsSentenceCard" to "{card-type-sentence}",
                        ),
                    ),
                ),
                localAudioRepository = LocalAudioRepository(Files.createTempDirectory("anki-audio").toFile()),
            )
            val aiSettings = AiSettingsRepository(
                PreferenceDataStoreFactory.create(scope = scope, produceFile = { tempFolder.newFile("ai.preferences_pb") }),
            ).apply {
                updateApiKey("key")
                updateExampleSentencePromptOverride("Example for [word]")
            }
            // The translation prompt is an app asset, which unit tests cannot read; only the example is asked here.
            val answers = ArrayDeque(listOf("パンを食べる。", "I eat bread."))
            val aiRepository = AiRepository(
                settingsRepository = aiSettings,
                client = AiClient { _, _, _, _ ->
                    """{"content":[{"type":"text","text":"${answers.removeFirst()}"}]}"""
                },
                context = ContextWrapper(null),
                ioDispatcher = Dispatchers.IO,
            )
            val runner = AnkiCardJobRunner(ankiRepository, aiRepository, scope)
            val original = "昨日は朝から忙しくて、夜になってやっとパンを食べることができた。"

            val id = runner.start(
                AnkiCardRequest(
                    label = "食べる",
                    formatId = null,
                    payloadJson = """{"expression":"食べる","matched":"食べる"}""",
                    context = AnkiMiningContext(sentence = original, sentenceTranslation = "Subtitle of the original."),
                    useAiExampleSentence = true,
                ),
            )
            val status = withTimeout(10_000) {
                runner.jobs.first { jobs -> jobs.single { it.id == id }.status != AnkiCardJobStatus.Working }
            }.single { it.id == id }.status

            assertTrue(status is AnkiCardJobStatus.Added)
            assertEquals("パンを<b>食べる</b>。", backend.fields["Sentence"])
            assertEquals(original, backend.fields["OriginalSentence"])
            assertEquals("x", backend.fields["IsSentenceCard"])
        } finally {
            scope.cancel()
        }
    }

    private class FixedAnkiSettings(settings: AnkiSettings) : AnkiSettingsRepository {
        private val state = MutableStateFlow(settings)
        override val settings: Flow<AnkiSettings> = state

        override suspend fun update(transform: (AnkiSettings) -> AnkiSettings) {
            state.value = transform(state.value)
        }
    }

    private class FieldRecordingBackend(
        private val deck: AnkiDeck,
        private val noteType: AnkiNoteType,
    ) : AnkiBackend {
        var fields: Map<String, String> = emptyMap()

        override fun isAvailable(): Boolean = true
        override fun fetchDecks(): List<AnkiDeck> = listOf(deck)
        override fun fetchNoteTypes(): List<AnkiNoteType> = listOf(noteType)
        override fun isDuplicate(
            deck: AnkiDeck,
            noteType: AnkiNoteType,
            key: String,
            duplicateScope: AnkiDuplicateScope,
            checkDuplicatesAcrossAllModels: Boolean,
        ): Boolean = false

        override fun addNote(
            deck: AnkiDeck,
            noteType: AnkiNoteType,
            fieldsByName: Map<String, String>,
            tags: Set<String>,
            allowDupes: Boolean,
            duplicateScope: AnkiDuplicateScope,
            checkDuplicatesAcrossAllModels: Boolean,
        ): Boolean {
            fields = fieldsByName
            return true
        }

        override fun addMediaFromUri(uriString: String, preferredName: String, mimeType: String): String? = null
    }
}
