package moe.antimony.hoshi.features.ai

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

fun interface AiTransport {
    fun post(url: String, headers: Map<String, String>, body: String, timeoutMillis: Int): String
}

class HttpAiTransport : AiTransport {
    override fun post(url: String, headers: Map<String, String>, body: String, timeoutMillis: Int): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            doOutput = true
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        return try {
            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: throw IOException("AI request HTTP ${connection.responseCode}")
            }
            stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

class AiRequestException(message: String) : RuntimeException(message)

class AiClient @Inject constructor() {
    private val transport: AiTransport = HttpAiTransport()
    private val timeoutMillis: Int = 30_000

    fun ask(baseUrl: String, model: String, apiKey: String, prompt: String): String {
        val endpoint = baseUrl.trimEnd('/') + "/v1/messages"
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", 1024)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", prompt)
                        },
                    )
                },
            )
        }
        val headers = mapOf(
            "content-type" to "application/json",
            "x-api-key" to apiKey,
            "anthropic-version" to "2023-06-01",
        )
        val responseBody = transport.post(endpoint, headers, body.toString(), timeoutMillis)
        val response = json.parseToJsonElement(responseBody).jsonObject
        val error = response["error"]?.takeUnless { it is JsonNull }
        if (error != null) {
            val message = error.jsonObject["message"]?.jsonPrimitive?.contentOrNull ?: "AI request failed"
            throw AiRequestException(message)
        }
        val text = response["content"]?.jsonArray
            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
            ?.joinToString("\n")
            ?.takeIf(String::isNotBlank)
        return text ?: throw AiRequestException("Empty AI response")
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
