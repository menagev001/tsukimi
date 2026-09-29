package moe.antimony.hoshi.features.ai

import android.util.Log
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.serialization.SerializationException
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
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: run {
                    Log.e("AiClient", "AI request HTTP $responseCode with no error body")
                    throw IOException("AI request HTTP $responseCode")
                }
            }
            stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

class AiRequestException(message: String, val retryable: Boolean = false) : RuntimeException(message)

class AiClient @Inject constructor(
    private val transport: AiTransport,
) {
    private val timeoutMillis: Int = 30_000

    fun ask(baseUrl: String, model: String, apiKey: String, systemPrompt: String, userMessage: String): String {
        val endpoint = baseUrl.trimEnd('/') + "/v1/messages"
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", MaxOutputTokens)
            put("system", systemPrompt)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", userMessage)
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
        val response = try {
            json.parseToJsonElement(responseBody).jsonObject
        } catch (e: SerializationException) {
            Log.e(Tag, "AI response was not valid JSON. Raw body: $responseBody", e)
            throw AiRequestException(
                "AI response was not valid JSON (${responseBody.take(120)})",
                retryable = true,
            )
        }
        val error = response["error"]?.takeUnless { it is JsonNull }
        if (error != null) {
            val errorObject = error.jsonObject
            val errorType = errorObject["type"]?.jsonPrimitive?.contentOrNull
            val message = errorObject["message"]?.jsonPrimitive?.contentOrNull ?: "AI request failed"
            Log.w(Tag, "AI API returned an error (type=$errorType): $message")
            throw AiRequestException(message, retryable = errorType in RetryableErrorTypes)
        }
        val contentBlocks = response["content"]?.jsonArray.orEmpty()
        val text = contentBlocks
            .mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
            .joinToString("\n")
            .takeIf(String::isNotBlank)
        val stopReason = response["stop_reason"]?.jsonPrimitive?.contentOrNull
        if (text == null) {
            val blockTypes = contentBlocks.mapNotNull { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull }
            Log.w(Tag, "Empty AI response (stop_reason=$stopReason, blocks=$blockTypes). Raw body: $responseBody")
            throw AiRequestException(
                "Empty AI response (stop_reason=${stopReason ?: "unknown"})",
                retryable = true,
            )
        }
        if (stopReason == "max_tokens") {
            Log.w(Tag, "AI response was truncated at the max_tokens limit ($MaxOutputTokens)")
            return "$text\n\n[Response was cut off - it hit the output length limit. Try a shorter sentence.]"
        }
        return text
    }

    private companion object {
        const val Tag = "AiClient"
        const val MaxOutputTokens = 4096
        val RetryableErrorTypes = setOf("overloaded_error", "rate_limit_error", "api_error")
        val json = Json { ignoreUnknownKeys = true }
    }
}
