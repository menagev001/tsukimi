package moe.antimony.hoshi.features.export

import android.content.Context
import android.content.Intent
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.antimony.hoshi.R

@Serializable
data class WordExport(
    val word: String,
    val sentence: String,
)

object WordExportFormat {
    private val json = Json {
        prettyPrint = true
    }

    fun toJson(export: WordExport): String =
        json.encodeToString(export)
}

fun Context.shareWordExport(export: WordExport) {
    val sendIntent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, WordExportFormat.toJson(export))
    startActivity(Intent.createChooser(sendIntent, resources.getString(R.string.action_export)))
}
