package moe.antimony.hoshi.manga

import java.io.File
import kotlinx.serialization.json.Json

/**
 * Sole parse boundary for `.mokuro` sidecar JSON, mirroring how `EpubBookParser` is the sole
 * boundary to the Rust EPUB parser. The on-disk copy under a volume's `mokuro.json` is kept as a
 * verbatim byte copy of the imported source file and only parsed here, lazily.
 */
object MokuroParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(file: File): MokuroVolume =
        json.decodeFromString(MokuroVolume.serializer(), file.readText())

    fun parse(text: String): MokuroVolume =
        json.decodeFromString(MokuroVolume.serializer(), text)
}
