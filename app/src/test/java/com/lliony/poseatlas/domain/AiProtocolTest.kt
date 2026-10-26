package com.lliony.poseatlas.domain

import com.lliony.poseatlas.data.ai.*
import com.lliony.poseatlas.domain.logic.normalizeAiUrl
import com.lliony.poseatlas.domain.model.atlasJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AiProtocolTest {
    @Test fun oldProfilesDefaultToOpenAiAndAnthropicChoiceSurvivesStorage() {
        val old = atlasJson.decodeFromString<AiProfile>("""{"id":"a","name":"旧配置","baseUrl":"https://example.com/v1/","apiKey":"test","model":"test"}""")
        assertEquals(AiFormat.OPENAI, old.format)
        val updated = old.copy(format = AiFormat.ANTHROPIC)
        assertEquals(updated, atlasJson.decodeFromString<AiProfile>(atlasJson.encodeToString(updated)))
    }
    @Test fun messagesEndpointPreservesGatewayPrefix() {
        assertEquals("https://example.com/gateway/v1/", normalizeAiUrl("https://example.com/gateway/v1/messages/"))
        assertEquals("https://api.anthropic.com/v1/", normalizeAiUrl("https://api.anthropic.com/v1/"))
    }
    @Test fun anthropicSeparatesSystemAndEncodesImageSource() {
        val body = anthropicBody("model", "system instructions", "user input", "data:image/jpeg;base64,YWJj")
        assertEquals("system instructions", body["system"]!!.jsonPrimitive.content)
        assertTrue(body["max_tokens"]!!.jsonPrimitive.int > 0)
        val message = body["messages"]!!.jsonArray.single().jsonObject
        assertEquals("user", message["role"]!!.jsonPrimitive.content)
        val blocks = message["content"]!!.jsonArray
        assertEquals("user input", blocks[0].jsonObject["text"]!!.jsonPrimitive.content)
        val image = blocks[1].jsonObject["source"]!!.jsonObject
        assertEquals("base64", image["type"]!!.jsonPrimitive.content)
        assertEquals("image/jpeg", image["media_type"]!!.jsonPrimitive.content)
        assertEquals("YWJj", image["data"]!!.jsonPrimitive.content)
        assertEquals(1, anthropicBody("model", "system", "text")["messages"]!!.jsonArray.single().jsonObject["content"]!!.jsonArray.size)
    }
    @Test fun readsTextBlocksAndRejectsTruncatedOrNonTextResponses() {
        assertEquals("hello\nworld", anthropicText("""{"content":[{"type":"thinking","thinking":"private"},{"type":"text","text":"hello"},{"type":"text","text":"world"}],"stop_reason":"end_turn"}"""))
        assertThrows(IllegalArgumentException::class.java) { anthropicText("""{"content":[{"type":"text","text":"partial"}],"stop_reason":"max_tokens"}""") }
        assertThrows(IllegalArgumentException::class.java) { anthropicText("""{"content":[]}""") }
    }
}
