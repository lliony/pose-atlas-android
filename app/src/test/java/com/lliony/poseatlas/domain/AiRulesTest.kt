package com.lliony.poseatlas.domain

import com.google.common.truth.Truth.assertThat
import com.lliony.poseatlas.domain.logic.*
import com.lliony.poseatlas.domain.model.*
import org.junit.Assert.assertThrows
import org.junit.Test

class AiRulesTest {
    @Test fun baseUrlPreservesPrefixAndRejectsCredentials() {
        assertThat(normalizeAiUrl("https://example.com/v1/chat/completions/")).isEqualTo("https://example.com/v1/")
        for (bad in listOf("http://example.com", "https://user:pass@example.com", "https://example.com?key=x", "https://example.com#x")) {
            assertThrows(IllegalArgumentException::class.java) { normalizeAiUrl(bad) }
        }
    }
    @Test fun recommendationsFilterUnknownIdsAndDuplicates() {
        val response = """{"sceneSummary":"街道","recommendations":[{"cardId":"a","reason":"合适","adjustment":"稍侧身"},{"cardId":"a"},{"cardId":"outside"}]}"""
        val result = parseRecommendations(response, setOf("a", "b"))
        assertThat(result.recommendations.map { it.cardId }).containsExactly("a")
    }
    @Test fun emptyRecommendationsAreDifferentFromAllInvalid() {
        assertThat(parseRecommendations("""{"sceneSummary":"","recommendations":[]}""", setOf("a")).recommendations).isEmpty()
        assertThrows(IllegalArgumentException::class.java) {
            parseRecommendations("""{"sceneSummary":"","recommendations":[{"cardId":"unknown"}]}""", setOf("a"))
        }
    }
    @Test fun generatedContentOnlyFillsBlankFields() {
        val card = Card("card", "category", "01-001", "已写标题", tags = listOf("保留"), notes = Notes(pose = "已写动作"))
        val generated = GeneratedCard(title = "生成标题", tags = listOf("生成"), gender = "女性", styles = listOf("自然"), notes = Notes(pose = "覆盖", camera = "新机位"))
        val result = fillBlankFields(card, generated)
        assertThat(result.title).isEqualTo("已写标题")
        assertThat(result.tags).containsExactly("保留")
        assertThat(result.notes.pose).isEqualTo("已写动作")
        assertThat(result.notes.camera).isEqualTo("新机位")
    }
    @Test fun parseGeneratedDecodesBoundaryJson() {
        val generated = parseGenerated("""{"title":"生成标题","tags":["生成"],"gender":"女性","styles":["自然"],"notes":{"pose":"覆盖"}}""")
        assertThat(generated.title).isEqualTo("生成标题")
        assertThat(generated.styles).containsExactly("自然")
        assertThat(generated.notes.pose).isEqualTo("覆盖")
    }
}
