package moe.antimony.hoshi.features.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import moe.antimony.hoshi.di.FilesDir
import moe.antimony.hoshi.di.IoDispatcher
import moe.antimony.hoshi.features.ai.aiSettingsDataStore
import moe.antimony.hoshi.features.anki.ankiDataStore
import moe.antimony.hoshi.features.audio.audioSettingsDataStore
import moe.antimony.hoshi.features.bookshelf.bookshelfDataStore
import moe.antimony.hoshi.features.dictionary.dictionarySettingsDataStore
import moe.antimony.hoshi.features.furigana.KnownWordsRepository
import moe.antimony.hoshi.features.furigana.knownWordsSettingsDataStore
import moe.antimony.hoshi.features.manga.mangaCropSettingsDataStore
import moe.antimony.hoshi.features.reader.readerSettingsDataStore
import moe.antimony.hoshi.features.sasayaki.sasayakiSettingsDataStore
import moe.antimony.hoshi.features.statistics.statisticsSettingsDataStore
import moe.antimony.hoshi.features.wallpaper.bookCoverWallpaperSettingsDataStore
import moe.antimony.hoshi.features.watch.watchPreferencesDataStore
import moe.antimony.hoshi.profiles.ProfileRepository

/** One settings store in the backup; [privateKeys] never leave the device and are kept on restore. */
internal class BackedUpSettingsStore(
    val name: String,
    val dataStore: DataStore<Preferences>,
    val privateKeys: Set<String> = emptySet(),
)

/**
 * The Settings backup: every settings store, the profiles' JSON files, the fonts and the known
 * word lists. Secrets (the AI API key) and device options (e-ink, volume keys) stay on the
 * device: they are left out of the backup and keep their current value on restore. Logins
 * (Jellyfin, Google Drive) are in stores that are not backed up at all.
 */
@Singleton
class SettingsBackupRepository internal constructor(
    private val stores: List<BackedUpSettingsStore>,
    private val filesDir: File,
    private val ioDispatcher: CoroutineDispatcher,
    private val afterRestore: suspend () -> Unit,
) {
    @Inject
    internal constructor(
        @ApplicationContext context: Context,
        @FilesDir filesDir: File,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        profileRepository: ProfileRepository,
        knownWordsRepository: KnownWordsRepository,
    ) : this(
        stores = listOf(
            BackedUpSettingsStore("reader-settings", context.readerSettingsDataStore, DeviceReaderKeys),
            BackedUpSettingsStore("dictionary-settings", context.dictionarySettingsDataStore),
            BackedUpSettingsStore("anki_settings", context.ankiDataStore),
            BackedUpSettingsStore("ai-settings", context.aiSettingsDataStore, setOf("apiKey")),
            BackedUpSettingsStore("bookshelf-settings", context.bookshelfDataStore),
            BackedUpSettingsStore("sasayaki-settings", context.sasayakiSettingsDataStore),
            BackedUpSettingsStore("statistics-settings", context.statisticsSettingsDataStore),
            BackedUpSettingsStore("manga-crop-settings", context.mangaCropSettingsDataStore),
            BackedUpSettingsStore("watch-preferences", context.watchPreferencesDataStore),
            BackedUpSettingsStore("known-words-settings", context.knownWordsSettingsDataStore, setOf("last_anki_sync")),
            BackedUpSettingsStore("audio-settings", context.audioSettingsDataStore, setOf("localAudioDatabaseUri")),
            BackedUpSettingsStore("book-cover-wallpaper-settings", context.bookCoverWallpaperSettingsDataStore),
        ),
        filesDir = filesDir,
        ioDispatcher = ioDispatcher,
        afterRestore = {
            profileRepository.reloadProfilesFromDisk()
            knownWordsRepository.reloadFromDisk()
        },
    )

    suspend fun export(contentResolver: ContentResolver, uri: Uri) = withContext(ioDispatcher) {
        contentResolver.openOutputStream(uri)?.use { export(it) } ?: error("Unable to open backup destination.")
    }

    suspend fun export(output: OutputStream) = withContext(ioDispatcher) {
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putText(MarkerEntry, """{"version":1}""")
            stores.forEach { store -> zip.putText("$StoresFolder/${store.name}.json", storeJson(store).toString()) }
            filesUnder(ProfilesFolder).forEach { (path, file) ->
                zip.putText(path, withoutKeys(file.readText(), DeviceReaderKeys))
            }
            KnownWordsFiles.map { "$KnownWordsFolder/$it" }.map { it to filesDir.resolve(it) }.filter { it.second.isFile }
                .forEach { (path, file) -> zip.putFile(path, file) }
            filesUnder(FontsFolder).forEach { (path, file) -> zip.putFile(path, file) }
        }
    }

    suspend fun restore(contentResolver: ContentResolver, uri: Uri) = withContext(ioDispatcher) {
        contentResolver.openInputStream(uri)?.use { restore(it) } ?: error("Unable to open backup file.")
    }

    suspend fun restore(input: InputStream) = withContext(ioDispatcher) {
        val entries = readEntries(input)
        if (MarkerEntry !in entries) throw UnsupportedSettingsBackupException()
        stores.forEach { store ->
            entries["$StoresFolder/${store.name}.json"]?.let { restoreStore(store, it.decodeToString()) }
        }
        entries.filterKeys { it.startsWith("$ProfilesFolder/") && it.endsWith(".json") }.forEach { (path, bytes) ->
            val target = safeChild(filesDir, path)
            val incoming = bytes.decodeToString()
            target.parentFile?.mkdirs()
            target.writeText(if (target.isFile) withLocalKeys(incoming, target.readText(), DeviceReaderKeys) else incoming)
        }
        // Known words are only ever added, so a restore merges instead of dropping words marked since.
        KnownWordsFiles.forEach { name ->
            val path = "$KnownWordsFolder/$name"
            val incoming = entries[path] ?: return@forEach
            val target = filesDir.resolve(path)
            target.parentFile?.mkdirs()
            target.writeText(mergedArrays(target.takeIf { it.isFile }?.readText(), incoming.decodeToString()))
        }
        entries.filterKeys { it.startsWith("$FontsFolder/") }.forEach { (path, bytes) ->
            safeChild(filesDir, path).apply { parentFile?.mkdirs() }.writeBytes(bytes)
        }
        afterRestore()
    }

    private suspend fun storeJson(store: BackedUpSettingsStore): JsonObject {
        val values = store.dataStore.data.first().asMap()
            .filterKeys { it.name !in store.privateKeys }
            .toSortedMap(compareBy { it.name })
        return buildJsonObject {
            values.forEach { (key, value) ->
                val typed: Pair<String, JsonElement>? = when (value) {
                    is Boolean -> "b" to JsonPrimitive(value)
                    is Int -> "i" to JsonPrimitive(value)
                    is Long -> "l" to JsonPrimitive(value)
                    is Float -> "f" to JsonPrimitive(value)
                    is Double -> "d" to JsonPrimitive(value)
                    is String -> "s" to JsonPrimitive(value)
                    is Set<*> -> "ss" to JsonArray(value.map { JsonPrimitive(it.toString()) })
                    else -> null
                }
                if (typed != null) {
                    put(key.name, buildJsonObject {
                        put("t", typed.first)
                        put("v", typed.second)
                    })
                }
            }
        }
    }

    private suspend fun restoreStore(store: BackedUpSettingsStore, text: String) {
        val incoming = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        store.dataStore.edit { preferences ->
            preferences.asMap().keys.filter { it.name !in store.privateKeys }.forEach { preferences.remove(it) }
            incoming.forEach { (key, value) ->
                if (key in store.privateKeys) return@forEach
                val entry = value as? JsonObject ?: return@forEach
                val raw = entry["v"] ?: return@forEach
                when (entry["t"]?.jsonPrimitive?.contentOrNull) {
                    "b" -> raw.jsonPrimitive.booleanOrNull?.let { preferences[booleanPreferencesKey(key)] = it }
                    "i" -> raw.jsonPrimitive.intOrNull?.let { preferences[intPreferencesKey(key)] = it }
                    "l" -> raw.jsonPrimitive.longOrNull?.let { preferences[longPreferencesKey(key)] = it }
                    "f" -> raw.jsonPrimitive.floatOrNull?.let { preferences[floatPreferencesKey(key)] = it }
                    "d" -> raw.jsonPrimitive.doubleOrNull?.let { preferences[doublePreferencesKey(key)] = it }
                    "s" -> raw.jsonPrimitive.contentOrNull?.let { preferences[stringPreferencesKey(key)] = it }
                    "ss" -> preferences[stringSetPreferencesKey(key)] =
                        raw.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }.toSet()
                }
            }
        }
    }

    private fun filesUnder(folder: String): List<Pair<String, File>> {
        val root = filesDir.resolve(folder)
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown().filter { it.isFile && !it.name.startsWith(".") }
            .map { "$folder/${it.relativeTo(root).invariantSeparatorsPath}" to it }
            .sortedBy { it.first }
            .toList()
    }

    private fun readEntries(input: InputStream): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
            }
        }
        return entries
    }

    private companion object {
        const val MarkerEntry = "tsukimi-settings.json"
        const val StoresFolder = "settings"
        const val ProfilesFolder = "Profiles"
        const val KnownWordsFolder = "KnownWords"
        const val FontsFolder = "Fonts"
        val KnownWordsFiles = listOf("imported.json", "marked.json")

        /** Reader options that belong to the device: e-ink panels and volume keys. */
        val DeviceReaderKeys = setOf(
            "eInkMode",
            "eInkReduceGhosting",
            "volumeKeysTurnPages",
            "volumeKeysNavigatePopupTerms",
            "volumeKeysSeekSasayaki",
        )
    }
}

/** The picked file is a .hoshi backup, but not a Settings backup. */
class UnsupportedSettingsBackupException : IllegalArgumentException("Not a Tsukimi settings backup.")

private val prettyJson = Json { prettyPrint = true }

/** [text]'s top-level object without [keys]; other JSON is left alone. */
internal fun withoutKeys(text: String, keys: Set<String>): String {
    val element = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return text
    return prettyJson.encodeToString(JsonElement.serializer(), JsonObject(element.filterKeys { it !in keys }))
}

/** [incoming], with [keys] taken from [local] where it has them. */
internal fun withLocalKeys(incoming: String, local: String, keys: Set<String>): String {
    val incomingObject = runCatching { Json.parseToJsonElement(incoming) }.getOrNull() as? JsonObject ?: return incoming
    val localObject = runCatching { Json.parseToJsonElement(local) }.getOrNull() as? JsonObject ?: return incoming
    return prettyJson.encodeToString(JsonElement.serializer(), JsonObject(incomingObject + localObject.filterKeys { it in keys }))
}

/** Two JSON arrays as one, without duplicates; the local entries come first. */
internal fun mergedArrays(local: String?, incoming: String): String {
    fun entries(text: String?) = text?.let { runCatching { Json.parseToJsonElement(it).jsonArray }.getOrNull() }.orEmpty()
    return prettyJson.encodeToString(JsonElement.serializer(), JsonArray((entries(local) + entries(incoming)).distinctBy { it.toString() }))
}

private fun safeChild(root: File, relative: String): File {
    val child = root.resolve(relative).canonicalFile
    require(child.path.startsWith(root.canonicalFile.path + File.separator)) { "Path leaves its folder: $relative" }
    return child
}

private fun ZipOutputStream.putText(path: String, text: String) {
    putNextEntry(ZipEntry(path))
    write(text.toByteArray())
    closeEntry()
}

private fun ZipOutputStream.putFile(path: String, file: File) {
    putNextEntry(ZipEntry(path))
    file.inputStream().use { it.copyTo(this) }
    closeEntry()
}
