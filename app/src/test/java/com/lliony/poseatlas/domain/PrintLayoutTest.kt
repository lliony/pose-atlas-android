package com.lliony.poseatlas.domain

import com.google.common.truth.Truth.assertThat
import com.lliony.poseatlas.domain.logic.*
import org.junit.Test

class PrintLayoutTest {
    @Test fun a4LandscapeThreeFourFitsEight() {
        val layout = printLayout("a4", true, 3.0 / 4)
        assertThat(layout.cols).isEqualTo(4)
        assertThat(layout.rows).isEqualTo(2)
        assertThat(layout.capacity).isEqualTo(8)
    }
    @Test fun duplexPermutationIsAnInvolution() {
        for (cols in 1..6) for (rows in 1..6) for (short in listOf(true, false)) {
            val indices = (0 until cols * rows).map { backIndex(it, cols, rows, short) }
            assertThat(indices.toSet()).hasSize(cols * rows)
            indices.forEachIndexed { i, j -> assertThat(backIndex(j, cols, rows, short)).isEqualTo(i) }
        }
        assertThat(backIndex(0, 4, 2, true)).isEqualTo(3)
        assertThat(backIndex(0, 4, 2, false)).isEqualTo(4)
    }
    @Test fun lastPageUsesCompactCapacity() {
        val layout = printLayout("a4", true, 3.0 / 4, 2)
        assertThat(layout.capacity).isEqualTo(2)
        assertThat(layout.cardWidth).isAtLeast(65.0)
        assertThat(layout.cardHeight).isAtLeast(84.0)
    }
}
