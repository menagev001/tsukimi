package moe.antimony.hoshi.features.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeAiTransport(private val response: String) : AiTransport {
    var lastUrl: String? = null
    var lastHeaders: Map<String, String>? = null
    var lastBody: String? = null

    override fun post(url: String, headers: Map<String, String>, body: String, timeoutMillis: Int): String {
        lastUrl = url
        lastHeaders = headers
        lastBody = body
        return response
    }
}

class AiClientTest {
    @Test
    fun askSendsSystemPromptAndUserMessageSeparately() {
        val transport = FakeAiTransport(
            """{"content":[{"type":"text","text":"response text"}]}""",
        )
        val client = AiClient(transport)

        val result = client.ask(
            baseUrl = "https://api.anthropic.com",
            model = "claude-sonnet-5",
            apiKey = "test-key",
            systemPrompt = "You are a helpful assistant.",
            userMessage = "Word: 猫\nSentence: 猫が座る。",
        )

        assertEquals("response text", result)
        assertEquals("https://api.anthropic.com/v1/messages", transport.lastUrl)
        assertEquals("test-key", transport.lastHeaders?.get("x-api-key"))
        val body = transport.lastBody.orEmpty()
        assertTrue(body.contains("\"system\":\"You are a helpful assistant.\""))
        assertTrue(body.contains("猫が座る。"))
    }

    @Test
    fun askThrowsOnApiError() {
        val transport = FakeAiTransport(
            """{"type":"error","error":{"type":"invalid_request_error","message":"bad request"}}""",
        )
        val client = AiClient(transport)

        val error = runCatching {
            client.ask(
                baseUrl = "https://api.anthropic.com",
                model = "claude-sonnet-5",
                apiKey = "test-key",
                systemPrompt = "system",
                userMessage = "user",
            )
        }.exceptionOrNull()

        assertTrue(error is AiRequestException)
        assertEquals("bad request", error?.message)
    }
}
