package com.lliony.poseatlas.data.ai

import com.lliony.poseatlas.domain.model.atlasJson
import kotlinx.serialization.json.*

/** Messages 使用独立的 system 字段和图片 source，不能直接套用 Chat Completions 消息。 */
internal fun anthropicBody(model: String, system: String, text: String, imageData: String? = null): JsonObject = buildJsonObject {
    put("model", model); put("max_tokens", 4096); put("system", system)
    putJsonArray("messages") { add(buildJsonObject {
        put("role", "user")
        putJsonArray("content") {
            add(buildJsonObject { put("type", "text"); put("text", text) })
            if (imageData != null) {
                require(imageData.startsWith("data:image/jpeg;base64,")) { "图片格式无效" }
                add(buildJsonObject {
                    put("type", "image")
                    putJsonObject("source") {
                        put("type", "base64"); put("media_type", "image/jpeg")
                        put("data", imageData.substringAfter(','))
                    }
                })
            }
        }
    }) }
}

internal fun anthropicText(raw: String): String {
    val response = atlasJson.parseToJsonElement(raw).jsonObject
    require(response["stop_reason"]?.jsonPrimitive?.content != "max_tokens") { "模型响应被截断，请重试或缩小输入范围" }
    val text = response["content"]?.jsonArray.orEmpty().filter { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }
        .joinToString("\n") { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }
    require(text.isNotBlank()) { "服务返回格式不正确，确认兼容 Anthropic Messages" }
    return text
}
