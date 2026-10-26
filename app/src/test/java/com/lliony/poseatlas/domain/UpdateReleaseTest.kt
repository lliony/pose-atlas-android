package com.lliony.poseatlas.domain

import com.lliony.poseatlas.data.update.*
import org.junit.Assert.*
import org.junit.Test

class UpdateReleaseTest {
    private fun release(version: String, draft: Boolean = false, digest: String = "sha256:" + "a".repeat(64)) = """{"tag_name":"v$version","draft":$draft,"prerelease":true,"assets":[{"name":"pose-atlas-$version-debug.apk","browser_download_url":"https://github.com/lliony/pose-atlas-android/releases/download/v$version/pose-atlas-$version-debug.apk","size":100,"digest":"$digest"}]}"""
    @Test fun comparesBetaNumbersAndStableVersions() {
        assertTrue(compareVersions("1.0.0-beta.10", "1.0.0-beta.9") > 0)
        assertTrue(compareVersions("1.0.0", "1.0.0-beta.10") > 0)
        assertTrue(compareVersions("1.10.0", "1.9.9") > 0)
        assertEquals(0, compareVersions("v1.0.0+build", "1.0.0"))
        assertThrows(IllegalArgumentException::class.java) { compareVersions("unknown", "1.0.0") }
    }
    @Test fun choosesNewestPublishedBetaAndIgnoresDraftsAndOlderBuilds() {
        val json = "[${release("1.0.0-beta.9")},${release("1.0.0-beta.11", true)},${release("1.0.0-beta.10")}]"
        assertEquals("1.0.0-beta.10", selectUpdate(json, "1.0.0-beta.3")!!.version)
        assertNull(selectUpdate(json, "1.0.0-beta.10"))
        assertNull(selectUpdate(json, "1.0.0"))
    }
    @Test fun rejectsInvalidResponseHashAndForeignDownload() {
        assertThrows(Exception::class.java) { selectUpdate("{}", "1.0.0-beta.2") }
        assertThrows(IllegalArgumentException::class.java) { selectUpdate("[${release("1.0.0-beta.3", digest = "bad")}]", "1.0.0-beta.2") }
        assertThrows(IllegalArgumentException::class.java) { selectUpdate("[${release("1.0.0-beta.3").replace("github.com/lliony", "evil.example/lliony")}]", "1.0.0-beta.2") }
    }
    @Test fun redirectsRequireHttpsAndExactTrustedHosts() {
        assertTrue(trustedUpdateUrl("https://release-assets.githubusercontent.com/a?token=test"))
        listOf("http://github.com/a", "https://github.com.evil.com/a", "https://user@github.com/a", "https://github.com:444/a").forEach { assertFalse(trustedUpdateUrl(it)) }
    }
}
