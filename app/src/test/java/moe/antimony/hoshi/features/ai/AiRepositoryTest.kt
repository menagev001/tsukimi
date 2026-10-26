package moe.antimony.hoshi.features.ai

import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AiRepositoryTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun exampleSentenceFillsTheWordIntoThePromptAndTrimsTheAnswer() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        try {
            val settings = AiSettingsRepository(
                PreferenceDataStoreFactory.create(scope = scope, produceFile = { tempFolder.newFile("ai.preferences_pb") }),
            )
            settings.updateApiKey("key")
            settings.updateExampleSentencePromptOverride("One sentence for [word], please.")
            var body = ""
            val repository = AiRepository(
                settingsRepository = settings,
                client = AiClient { _, _, requestBody, _ ->
                    body = requestBody
                    """{"content":[{"type":"text","text":"  パンを食べる。\n"}]}"""
                },
                context = ContextWrapper(null),
                ioDispatcher = Dispatchers.IO,
            )

            assertEquals("パンを食べる。", repository.askExampleSentence("食べる").getOrThrow())
            assertTrue(body, body.contains("One sentence for 食べる, please."))
            assertEquals(AiSettings.DEFAULT_LONG_SENTENCE_LIMIT, settings.settings.first().longSentenceLimit)
        } finally {
            scope.cancel()
        }
    }
}
