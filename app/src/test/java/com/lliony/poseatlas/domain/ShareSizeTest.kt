package com.lliony.poseatlas.domain

import com.google.common.truth.Truth.assertThat
import com.lliony.poseatlas.data.print.shareImageSize
import org.junit.Assert.assertThrows
import org.junit.Test

class ShareSizeTest {
    @Test fun standardCardKeepsThreeHundredDpi() {
        assertThat(shareImageSize(0.75)).isEqualTo(827 to 1102)
    }
    @Test fun extremePanoramaIsRejectedBeforeBitmapAllocation() {
        for (ratio in listOf(2048.0, 1.0 / 2048, Double.NaN, 0.0)) {
            assertThrows(IllegalArgumentException::class.java) { shareImageSize(ratio) }
        }
    }
}
