package moe.antimony.hoshi.features.export

import org.junit.Assert.assertTrue
import org.junit.Test

class WordExportTest {
    @Test
    fun jsonExportContainsOnlyWordAndSentenceFields() {
        val export = WordExport(word = "猫", sentence = "猫が座る。")

        val json = WordExportFormat.toJson(export)

        assertTrue(json.contains("\"word\""))
        assertTrue(json.contains("\"sentence\""))
        assertTrue(json.contains("猫"))
        assertTrue(json.contains("猫が座る。"))
    }
}
