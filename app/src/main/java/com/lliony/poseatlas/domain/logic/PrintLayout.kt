package com.lliony.poseatlas.domain.logic

import kotlin.math.*

data class PrintLayout(val paperWidth: Double, val paperHeight: Double, val cardWidth: Double, val cardHeight: Double, val cols: Int, val rows: Int) { val capacity get() = cols * rows }
fun backIndex(index: Int, cols: Int, rows: Int, short: Boolean): Int = if (short) index / cols * cols + cols - 1 - index % cols else (rows - 1 - index / cols) * cols + index % cols
fun printLayout(paper: String, landscape: Boolean, aspect: Double, count: Int? = null): PrintLayout {
    require(aspect.isFinite() && aspect > 0)
    val size = mapOf("a5" to (148.0 to 210.0), "a4" to (210.0 to 297.0), "a3" to (297.0 to 420.0), "letter" to (215.9 to 279.4)).getValue(paper)
    val pw = if (landscape) size.second else size.first
    val ph = if (landscape) size.first else size.second
    val uw = pw - 17; val uh = ph - 10
    val candidates = (1..6).flatMap { cols -> (1..6).mapNotNull { rows ->
        var w = min(uw / cols, uh / rows * aspect); var h = w / aspect
        val short = min(w, h)
        if (short > 94.5) { w *= 94.5 / short; h *= 94.5 / short }
        if (min(w, h) < 65 || h < 84 || (count != null && cols * rows < count)) null else PrintLayout(pw, ph, w, h, cols, rows)
    } }
    val best = if (count == null) candidates.maxByOrNull { it.capacity * it.cardWidth * it.cardHeight / (uw * uh) }
        else candidates.sortedWith(compareBy<PrintLayout> { it.capacity }.thenByDescending { it.cols * it.cardWidth / uw }.thenByDescending { it.cardWidth * it.cardHeight }).firstOrNull()
    val layout = best ?: if (count != null) printLayout(paper, landscape, aspect) else PrintLayout(pw, ph, min(uw, uh * aspect), min(uw, uh * aspect) / aspect, 1, 1)
    return layout.copy(cardWidth = round(layout.cardWidth * 10) / 10, cardHeight = round(layout.cardHeight * 10) / 10)
}
