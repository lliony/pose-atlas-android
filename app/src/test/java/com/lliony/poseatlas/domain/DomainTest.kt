package com.lliony.poseatlas.domain

import com.google.common.truth.Truth.assertThat
import com.lliony.poseatlas.domain.model.*
import com.lliony.poseatlas.domain.logic.*
import org.junit.Assert.assertThrows
import org.junit.Test

class DomainTest {
    private val photo = Photo("photo", "/images/street.png", notes = Notes(pose = "  单图动作  ", camera = " "))
    private val card = Card("card", "category", "01-009", "街边", notes = Notes(pose = "通用动作", camera = "低机位"), images = listOf(photo))
    private val state = State(listOf(Category("category", "街道", "01")), listOf(card), emptyList())

    @Test fun imageNotesOverrideOnlyNonblankFields() {
        val notes = resolveNotes(card, photo)
        assertThat(notes.pose).isEqualTo("单图动作")
        assertThat(notes.camera).isEqualTo("低机位")
    }

    @Test fun nextCodeUsesMaximumIncludingDeletedCards() {
        val cards = listOf(card, card.copy(id = "deleted", code = "01-027", deleted = true))
        assertThat(nextCardCode(state.categories.first(), cards)).isEqualTo("01-028")
    }

    @Test fun duplicateIdsAcrossKindsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            validateState(state.copy(cards = listOf(card.copy(id = "category"))))
        }
    }

    @Test fun missingCategoryIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { validateState(state.copy(categories = emptyList())) }
    }

    @Test fun invalidPanelAndTraversalSourceAreRejected() {
        for (p in listOf(photo.copy(panel = 3), photo.copy(src = "/api/media/../secret"))) {
            assertThrows(IllegalArgumentException::class.java) { validateState(state.copy(cards = listOf(card.copy(images = listOf(p))))) }
        }
    }

    @Test fun referencePanelFiveIsValid() {
        validateState(state.copy(cards = listOf(card.copy(images = listOf(photo.copy(src = "/images/pose-reference-styles.png", panel = 5))))))
    }

    @Test fun emptyImagesAndOversizeNotesAreRejected() {
        for (c in listOf(card.copy(images = emptyList()), card.copy(notes = Notes(pose = "长".repeat(2001))))) {
            assertThrows(IllegalArgumentException::class.java) { validateState(state.copy(cards = listOf(c))) }
        }
    }

    @Test fun filtersCombineAndExcludeDeletedCards() {
        val c = card.copy(gender = "女性", styles = listOf("自然"), tags = listOf("WALK"))
        assertThat(filterCards(listOf(c, c.copy(id = "trash", deleted = true)), CardFilter(query = "walk", gender = "女性", style = "自然"))).containsExactly(c)
        assertThat(filterCards(listOf(c), CardFilter(gender = "男性"))).isEmpty()
    }
}
