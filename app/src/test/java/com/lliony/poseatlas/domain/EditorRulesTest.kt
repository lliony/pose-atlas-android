package com.lliony.poseatlas.domain

import com.lliony.poseatlas.domain.logic.EditorRules
import com.lliony.poseatlas.domain.logic.nextCardCode
import com.lliony.poseatlas.domain.model.Card
import com.lliony.poseatlas.domain.model.Category
import com.lliony.poseatlas.domain.model.Photo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorRulesTest {
    private val category = Category("cat-1", "官方图鉴", "A")
    private val photo = Photo("p-1", "/api/media/p-1", panel = 1)
    private val existing = Card("card-1", "cat-1", "A01", "名牌", images = listOf(photo))

    @Test fun `openTarget - 库中已有回归 Existing`() {
        val target = EditorRules.openTarget("card-1", listOf(existing), listOf(category))
        assertEquals(existing, (target as EditorRules.OpenTarget.Existing).card)
    }

    @Test fun `openTarget - 缺失时以 nextCardCode 建卡`() {
        val target = EditorRules.openTarget("new", listOf(existing), listOf(category))
        val create = target as EditorRules.OpenTarget.Create
        assertEquals(category, create.category)
        assertEquals(nextCardCode(category, listOf(existing)), create.code)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `openTarget - 无分类不可新建`() {
        EditorRules.openTarget("new", listOf(existing), emptyList())
    }

    @Test fun `sameCardAndImage - 目标图仍在当前编辑卡上`() {
        assertTrue(EditorRules.sameCardAndImage(existing, "card-1", photo))
    }

    @Test fun `sameCardAndImage - 图或卡一换即拒`() {
        assertFalse(EditorRules.sameCardAndImage(null, "card-1", photo))
        assertFalse(EditorRules.sameCardAndImage(existing, "card-9", photo))
        assertFalse(EditorRules.sameCardAndImage(existing, "card-1", Photo("p-9", "/api/media/p-9")))
    }
}