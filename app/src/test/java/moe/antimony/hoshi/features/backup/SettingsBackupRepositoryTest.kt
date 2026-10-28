package moe.antimony.hoshi.features.backup

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsBackupRepositoryTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    @After
    fun tearDown() = scope.cancel()

    private inner class Device {
        val filesDir: File = temp.newFolder()
        val reader = PreferenceDataStoreFactory.create(scope = scope) { temp.newFile("reader-${System.nanoTime()}.preferences_pb") }
        val ai = PreferenceDataStoreFactory.create(scope = scope) { temp.newFile("ai-${System.nanoTime()}.preferences_pb") }
        var restored = 0
        val repository = SettingsBackupRepository(
            stores = listOf(
                BackedUpSettingsStore("reader-settings", reader, setOf("eInkMode")),
                BackedUpSettingsStore("ai-settings", ai, setOf("apiKey")),
            ),
            filesDir = filesDir,
            ioDispatcher = Dispatchers.IO,
            afterRestore = { restored++ },
        )
    }

    @Test
    fun settingsMoveToAnotherDeviceWithoutSecretsOrDeviceOptions() = runBlocking {
        val boox = Device()
        boox.reader.edit {
            it[intPreferencesKey("fontSize")] = 22
            it[booleanPreferencesKey("eInkMode")] = true
            it[stringSetPreferencesKey("tags")] = setOf("a", "b")
        }
        boox.ai.edit {
            it[stringPreferencesKey("model")] = "claude"
            it[stringPreferencesKey("apiKey")] = "secret"
        }
        boox.filesDir.resolve("Profiles/default-ja").mkdirs()
        boox.filesDir.resolve("Profiles/default-ja/reader_settings.json").writeText("""{"fontSize":22,"eInkMode":true}""")
        boox.filesDir.resolve("KnownWords").mkdirs()
        boox.filesDir.resolve("KnownWords/marked.json").writeText("""[{"word":"猫","reading":"ねこ"}]""")
        boox.filesDir.resolve("Fonts").mkdirs()
        boox.filesDir.resolve("Fonts/Klee.ttf").writeBytes(byteArrayOf(1, 2, 3))

        val backup = ByteArrayOutputStream().also { boox.repository.export(it) }.toByteArray()

        val tablet = Device()
        tablet.reader.edit {
            it[intPreferencesKey("fontSize")] = 30
            it[booleanPreferencesKey("eInkMode")] = false
            it[intPreferencesKey("onlyHere")] = 1
        }
        tablet.ai.edit { it[stringPreferencesKey("apiKey")] = "tablet-key" }
        tablet.filesDir.resolve("Profiles/default-ja").mkdirs()
        tablet.filesDir.resolve("Profiles/default-ja/reader_settings.json").writeText("""{"fontSize":30,"eInkMode":false}""")
        tablet.filesDir.resolve("KnownWords").mkdirs()
        tablet.filesDir.resolve("KnownWords/marked.json").writeText("""[{"word":"犬","reading":"いぬ"}]""")

        tablet.repository.restore(ByteArrayInputStream(backup))

        val reader = tablet.reader.data.first()
        assertEquals(22, reader[intPreferencesKey("fontSize")])
        assertEquals(false, reader[booleanPreferencesKey("eInkMode")])
        assertEquals(null, reader[intPreferencesKey("onlyHere")])
        assertEquals(setOf("a", "b"), reader[stringSetPreferencesKey("tags")])
        val ai = tablet.ai.data.first()
        assertEquals("claude", ai[stringPreferencesKey("model")])
        assertEquals("tablet-key", ai[stringPreferencesKey("apiKey")])
        val profile = tablet.filesDir.resolve("Profiles/default-ja/reader_settings.json").readText()
        assertTrue(profile, "\"fontSize\": 22" in profile && "\"eInkMode\": false" in profile)
        val words = tablet.filesDir.resolve("KnownWords/marked.json").readText()
        assertTrue(words, "猫" in words && "犬" in words)
        assertEquals(listOf<Byte>(1, 2, 3), tablet.filesDir.resolve("Fonts/Klee.ttf").readBytes().toList())
        assertEquals(1, tablet.restored)
        assertTrue("secret" !in backup.decodeToString())
    }

    @Test(expected = UnsupportedSettingsBackupException::class)
    fun aBooksBackupIsNotRestoredAsSettings() = runBlocking {
        val books = ByteArrayOutputStream().also { output ->
            java.util.zip.ZipOutputStream(output).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("book/metadata.json"))
                zip.write("{}".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        Device().repository.restore(ByteArrayInputStream(books))
    }
}
