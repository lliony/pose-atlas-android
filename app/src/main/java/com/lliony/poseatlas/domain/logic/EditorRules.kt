package com.lliony.poseatlas.domain.logic

import com.lliony.poseatlas.domain.model.Card
import com.lliony.poseatlas.domain.model.Category
import com.lliony.poseatlas.domain.model.Photo

/** 编辑器会话的纯规则：打开目标裁决与裁切身份比对，零状态零 IO，可单测。 */
object EditorRules {

    /** 打开编辑器时的目标：库中已有→直接编辑；否则需一个分类以新建。 */
    sealed interface OpenTarget {
        data class Existing(val card: Card) : OpenTarget
        data class Create(val category: Category, val code: String) : OpenTarget
    }

    /** 裁定打开目标。库中无此卡且无分类时抛「请先添加分类」。 */
    fun openTarget(id: String, cards: List<Card>, categories: List<Category>): OpenTarget {
        cards.find { it.id == id }?.let { return OpenTarget.Existing(it) }
        val category = categories.firstOrNull() ?: throw IllegalArgumentException("请先添加分类")
        return OpenTarget.Create(category, nextCardCode(category, cards))
    }

    /** 裁切身份比对：当前编辑卡片仍是目标卡，且目标图片仍在卡片图上（防返回后误裁/孤儿）。 */
    fun sameCardAndImage(current: Card?, cardId: String, photo: Photo): Boolean =
        current != null && current.id == cardId && current.images.any {
            it.id == photo.id && it.src == photo.src && it.panel == photo.panel
        }
}