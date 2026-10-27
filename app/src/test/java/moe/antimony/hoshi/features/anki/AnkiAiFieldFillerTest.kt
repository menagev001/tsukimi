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
import kotlinx.coroutines.runBlocking
import moe.antimony.hoshi.features.ai.AiClient
import moe.antimony.hoshi.features.ai.AiRepository
import moe.antimony.hoshi.features.ai.AiSettingsRepository
import moe.antimony.hoshi.features.audio.LocalAudioRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AnkiAiFieldFillerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val noteType = AnkiNoteType(
        2L,
        "Lapis",
        listOf("Expression", "Sentence", "AINuance", "AIBreakdown", "OriginalSentence"),
    )
    private val mappings = mapOf(
        "Expression" to "{expression}",
        "Sentence" to "{sentence}",
        "AINuance" to "{ai-word-nuance}",
        "AIBreakdown" to "{ai-sentence-breakdown}",
        "OriginalSentence" to "{original-sentence}",
    )
    private val note = AnkiNoteRecord(
        id = 7L,
        fields = listOf("食べる", "パンを<b>食べる</b>。", "", "Already there", ""),
        tags = setOf("tsukimi", AnkiAiMissingTag),
    )

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun fillsOnlyTheEmptyAiFieldsAndRemovesTheTag() = runBlocking {
        val backend = UpdateRecordingBackend()
        val filler = filler(backend, apiKey = "key")

        assertTrue(filler.fillNote(AnkiFormatNote(noteType, mappings, note)))

        val updated = backend.updated!!
        assertEquals(listOf("食べる", "パンを<b>食べる</b>。", "Eating, plainly.", "Already there", ""), updated.fields)
        assertEquals(setOf("tsukimi"), updated.tags)
    }

    @Test
    fun keepsTheNoteUntouchedWhenTheAiRequestFails() = runBlocking {
        val backend = UpdateRecordingBackend()
        val filler = filler(backend, apiKey = "")

        assertFalse(filler.fillNote(AnkiFormatNote(noteType, mappings, note)))

        assertNull(backend.updated)
    }

    private suspend fun filler(backend: UpdateRecordingBackend, apiKey: String): AnkiAiFieldFiller {
        val ankiRepository = AnkiRepository(
            context = ContextWrapper(null),
            backend = backend,
            settingsRepository = object : AnkiSettingsRepository {
                private val state = MutableStateFlow(AnkiSettings())
                override val settings: Flow<AnkiSettings> = state
                override suspend fun update(transform: (AnkiSettings) -> AnkiSettings) {
                    state.value = transform(state.value)
                }
            },
            localAudioRepository = LocalAudioRepository(Files.createTempDirectory("anki-audio").toFile()),
        )
        val aiSettings = AiSettingsRepository(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { tempFolder.newFile("ai.preferences_pb") }),
        ).apply {
            updateApiKey(apiKey)
            // Prompt assets cannot be read in unit tests.
            updateWordNuancePromptOverride("Explain the word")
        }
        val aiRepository = AiRepository(
            settingsRepository = aiSettings,
            client = AiClient { _, _, _, _ -> """{"content":[{"type":"text","text":"Eating, plainly."}]}""" },
            context = ContextWrapper(null),
            ioDispatcher = Dispatchers.IO,
        )
        return AnkiAiFieldFiller(ankiRepository, aiRepository, scope)
    }

    private class UpdateRecordingBackend : AnkiBackend {
        var updated: AnkiNoteRecord? = null

        override fun isAvailable(): Boolean = true
        override fun fetchDecks(): List<AnkiDeck> = emptyList()
        override fun fetchNoteTypes(): List<AnkiNoteType> = emptyList()
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
        ): Boolean = false

        override fun addMediaFromUri(uriString: String, preferredName: String, mimeType: String): String? = null

        override fun updateNote(noteType: AnkiNoteType, note: AnkiNoteRecord): Boolean {
            updated = note
            return true
        }
    }
}
