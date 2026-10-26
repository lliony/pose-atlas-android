package com.lliony.poseatlas.domain

import com.lliony.poseatlas.data.ai.AiConfiguration
import com.lliony.poseatlas.data.ai.AiProfile
import com.lliony.poseatlas.domain.logic.AiProfileRules as Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class AiProfileRulesTest {
    private val base = "https://api.example.com/v1/chat/completions"
    private fun profile(id: String = "p1", name: String = "主模型", model: String = "gpt-4o", key: String = "secret", remember: Boolean = true) =
        AiProfile(id, name, base, key, model, remember)

    @Test fun `normalize - 规范 baseUrl、保留其余字段`() {
        val p = profile()
        val result = Rules.normalize(p)
        assertEquals("https://api.example.com/v1/", result.baseUrl)
        assertEquals(p.id, result.id); assertEquals(p.name, result.name); assertEquals(p.model, result.model); assertEquals(p.apiKey, result.apiKey)
    }

    @Test fun `normalize - 任一名 或 模型 或 key 为空抛`() {
        listOf(profile(name = "  "), profile(model = ""), profile(key = " ")).forEach {
            try { Rules.normalize(it); fail("应当抛出 IllegalArgumentException") } catch (e: IllegalArgumentException) { /* 符合预期 */ }
        }
    }

    @Test fun `upsert - 新档案去重并入并默认推荐与填卡`() {
        val config = AiConfiguration(profiles = listOf(profile("a"), profile("b")))
        val result = Rules.upsert(config, profile("a", name = "改名后"))
        assertEquals(2, result.profiles.size)
        assertEquals("改名后", result.profiles.first { it.id == "a" }.name)
        assertEquals("a", result.recommendId); assertEquals("a", result.draftId)
    }

    @Test fun `upsert - 已有推荐与填卡时不覆盖`() {
        val config = AiConfiguration(profiles = listOf(profile("a"), profile("b")), recommendId = "b", draftId = "b")
        val result = Rules.upsert(config, profile("a"))
        assertEquals("b", result.recommendId); assertEquals("b", result.draftId)
    }

    @Test fun `delete - 移除档案`() {
        val config = AiConfiguration(profiles = listOf(profile("a"), profile("b")))
        val result = Rules.delete(config, "a")
        assertEquals(listOf("b"), result.profiles.map { it.id })
    }

    @Test fun `delete - 清掉指向它的推荐与填卡，不误伤其他`() {
        val config = AiConfiguration(profiles = listOf(profile("a"), profile("b")), recommendId = "a", draftId = "b")
        val result = Rules.delete(config, "a")
        assertNull(result.recommendId)
        assertEquals("b", result.draftId)
    }
}