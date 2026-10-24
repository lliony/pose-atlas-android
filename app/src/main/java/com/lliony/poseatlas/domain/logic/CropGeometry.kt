package com.lliony.poseatlas.domain.logic

import kotlin.math.min

/** Coordinates relative to the oriented image (or the selected panel). */
data class NormalizedCrop(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(listOf(left, top, right, bottom).all { it.isFinite() } &&
            left >= 0 && top >= 0 && right <= 1 && bottom <= 1 && left < right && top < bottom) { "裁切范围无效" }
    }
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2
    val centerY get() = (top + bottom) / 2
}

fun cropWindow(imageAspect: Float, frameAspect: Float, zoom: Float, centerX: Float, centerY: Float): NormalizedCrop {
    require(listOf(imageAspect, frameAspect, zoom, centerX, centerY).all { it.isFinite() }) { "裁切参数无效" }
    require(imageAspect > 0 && frameAspect > 0 && zoom >= 1) { "裁切参数无效" }
    val width = min(1f, frameAspect / imageAspect) / zoom
    val height = min(1f, imageAspect / frameAspect) / zoom
    val left = (centerX - width / 2).coerceIn(0f, 1f - width)
    val top = (centerY - height / 2).coerceIn(0f, 1f - height)
    return NormalizedCrop(left, top, (left + width).coerceAtMost(1f), (top + height).coerceAtMost(1f))
}
