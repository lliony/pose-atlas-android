package com.lliony.poseatlas.domain

import com.google.common.truth.Truth.assertThat
import com.lliony.poseatlas.domain.model.MediaRef
import org.junit.Test

class MediaRefTest {
    @Test fun `isImported 与 isSeed 互斥判别`() {
        assertThat(MediaRef.isImported("/api/media/abc-1_2")).isTrue()
        assertThat(MediaRef.isImported("/images/street.png")).isFalse()
        assertThat(MediaRef.isSeed("/images/street.png")).isTrue()
        assertThat(MediaRef.isSeed("/api/media/abc")).isFalse()
    }

    @Test fun `isValid 只认两套合法地址`() {
        assertThat(MediaRef.isValid("/api/media/abc")).isTrue()
        assertThat(MediaRef.isValid("/images/pose-reference-styles.png")).isTrue()
        assertThat(MediaRef.isValid("/api/media/../secret")).isFalse()
        assertThat(MediaRef.isValid("/images/other.png")).isFalse()
    }

    @Test fun `mediaFileName 只取导入 uuid 落盘名`() {
        assertThat(MediaRef.mediaFileName("/api/media/abc-1_2")).isEqualTo("abc-1_2")
        assertThat(MediaRef.mediaFileName("/images/street.png")).isNull()
    }

    @Test fun `newId 落在导入前缀且可被回读`() {
        val src = MediaRef.newId()
        assertThat(src).startsWith("/api/media/")
        assertThat(MediaRef.mediaFileName(src)).isNotNull()
        assertThat(MediaRef.isValid(src)).isTrue()
    }
}