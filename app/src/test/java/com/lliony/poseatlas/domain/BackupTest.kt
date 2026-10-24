package com.lliony.poseatlas.domain

import com.google.common.truth.Truth.assertThat
import com.lliony.poseatlas.data.backup.BackupCodec
import com.lliony.poseatlas.domain.model.*
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupTest {
    @Test fun readsActualWebPackBackupFixture() {
        val bytes = javaClass.getResourceAsStream("/web-backup.zip")!!.use { it.readBytes() }
        val parsed = BackupCodec.read(bytes)
        assertThat(parsed.state.cards.single().title).isEqualTo("Web 兼容样例")
        assertThat(parsed.state.cards.single().privateNote).isEqualTo("保留私人备注")
        assertThat(parsed.images.keys).containsExactly("/api/media/fixture-image")
    }
    private val state = State(listOf(Category("cat", "分类", "01")), listOf(Card("card", "cat", "01-001", "卡片", privateNote = "私人内容", images = listOf(Photo("photo", "/api/media/test")))), listOf(Album("album", "册子", cardIds = listOf("card"))))
    @Test fun zipRoundTripPreservesStateAndImageKeys() {
        val bytes = byteArrayOf(1, 2, 3)
        val output = ByteArrayOutputStream()
        BackupCodec.write(output, state, mapOf("/api/media/test" to bytes))
        val result = BackupCodec.read(output.toByteArray())
        assertThat(result.state).isEqualTo(state)
        assertThat(result.images["/api/media/test"]).isEqualTo(bytes)
    }
    @Test fun unknownFieldsAreNotRetainedInExport() {
        val text = """{"format":"pose-atlas-backup","version":1,"state":{"categories":[],"cards":[],"albums":[{"id":"album","name":"册子","description":"","cardIds":[],"shareId":"secret","apiKey":"hidden"}]},"images":{},"token":"hidden"}"""
        val parsed = BackupCodec.read(text.toByteArray())
        val output = ByteArrayOutputStream()
        BackupCodec.write(output, parsed.state, emptyMap())
        val again = BackupCodec.read(output.toByteArray())
        assertThat(again.state.albums.single().name).isEqualTo("册子")
        assertThat(BackupCodec.metadata(parsed.state, emptyMap())).doesNotContain("secret")
        assertThat(BackupCodec.metadata(parsed.state, emptyMap())).doesNotContain("apiKey")
    }
    @Test fun zipTraversalAndUnsupportedVersionAreRejected() {
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use { it.putNextEntry(ZipEntry("../escape")); it.write(byteArrayOf(1)); it.closeEntry() }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.read(zip.toByteArray()) }
        val bad = """{"format":"pose-atlas-backup","version":2,"state":{"categories":[],"cards":[],"albums":[]}}"""
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.read(bad.toByteArray()) }
    }
    @Test fun missingFormatOrVersionIsNotAcceptedAsBackup() {
        for (text in listOf("""{"state":{"categories":[],"cards":[],"albums":[]}}""", """{"format":"pose-atlas-backup","state":{"categories":[],"cards":[],"albums":[]}}""")) {
            assertThrows(IllegalArgumentException::class.java) { BackupCodec.read(text.toByteArray()) }
        }
    }
}
