package com.lliony.poseatlas.domain

import com.lliony.poseatlas.domain.logic.*
import org.junit.Assert.*
import org.junit.Test

class CropGeometryTest {
    @Test fun squareFrameOnLandscapeCanReachEitherEdge() {
        val left = cropWindow(2f, 1f, 1f, 0f, .5f)
        assertEquals(0f, left.left, .0001f)
        assertEquals(.5f, left.right, .0001f)
        val right = cropWindow(2f, 1f, 1f, 1f, .5f)
        assertEquals(.5f, right.left, .0001f)
        assertEquals(1f, right.right, .0001f)
    }
    @Test fun allRatiosAndZoomLevelsStayInsideImage() {
        for (aspect in listOf(.01f, .75f, 1f, 2f, 100f)) {
            for (ratio in listOf(.5625f, .666667f, .75f, 1f, 1.33333f, 1.5f, 1.77778f)) {
                for (zoom in listOf(1f, 2f, 8f)) {
                    val rect = cropWindow(aspect, ratio, zoom, -20f, 20f)
                    assertTrue(rect.left >= 0 && rect.top >= 0 && rect.right <= 1 && rect.bottom <= 1)
                    assertEquals(ratio, rect.width * aspect / rect.height, .001f)
                }
            }
        }
    }
    @Test fun ratioChangeKeepsCenterWhenItFits() {
        val rect = cropWindow(1f, 4f / 3, 2f, .6f, .4f)
        assertEquals(.6f, rect.centerX, .0001f)
        assertEquals(.4f, rect.centerY, .0001f)
    }
    @Test fun invalidCoordinatesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { NormalizedCrop(-.1f, 0f, 1f, 1f) }
        assertThrows(IllegalArgumentException::class.java) { NormalizedCrop(0f, 0f, Float.NaN, 1f) }
        assertThrows(IllegalArgumentException::class.java) { cropWindow(0f, 1f, 1f, .5f, .5f) }
    }
}
