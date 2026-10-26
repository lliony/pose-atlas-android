package com.lliony.poseatlas.domain.logic

import com.lliony.poseatlas.domain.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import java.net.URI

fun normalizeAiUrl(input: String): String {
    val uri = runCatching { URI(input.trim()) }.getOrElse { throw IllegalArgumentException("模型地址无效") }
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) { "请填写不含凭据、查询参数或片段的 HTTPS 地址" }
    return input.trim().trimEnd('/').removeSuffix("/chat/completions").removeSuffix("/messages") + "/"
}
@Serializable data class Recommendation(val cardId: String, val reason: String = "", val adjustment: String = "")
@Serializable data class Recommendations(val sceneSummary: String = "", val recommendations: List<Recommendation>)
private fun jsonContent(raw: String): String {
    require(raw.toByteArray().size <= 1024 * 1024) { "响应超过大小限制" }
    return raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
}
fun parseRecommendations(raw: String, candidates: Set<String>): Recommendations {
    val parsed = atlasJson.decodeFromString<Recommendations>(jsonContent(raw))
    require(parsed.sceneSummary.length <= 2000) { "场景描述过长" }
    val valid = parsed.recommendations.filter { it.cardId in candidates }.distinctBy { it.cardId }.take(5)
    require(parsed.recommendations.isEmpty() || valid.isNotEmpty()) { "响应异常：推荐卡片不在候选中" }
    return parsed.copy(recommendations = valid)
}
@Serializable private data class GeneratedCard(val title: String = "", val tags: List<String> = emptyList(), val gender: String? = null, val styles: List<String> = emptyList(), val notes: Notes = Notes())
fun fillBlankFields(card: Card, raw: String): Card {
    val draft = atlasJson.decodeFromString<GeneratedCard>(jsonContent(raw))
    return card.copy(title = card.title.ifBlank { draft.title.take(28) }, tags = card.tags.ifEmpty { draft.tags.map { it.take(12) }.filter { it.isNotBlank() }.distinct().take(4) },
        gender = card.gender?.takeIf { it.isNotBlank() } ?: draft.gender?.takeIf { it in genders },
        styles = card.styles?.takeIf { it.isNotEmpty() } ?: draft.styles.filter { it in styles }.distinct().take(3),
        notes = Notes.from(card.notes.values().zip(draft.notes.values()) { old, new -> old.ifBlank { new.take(80) } }))
}
