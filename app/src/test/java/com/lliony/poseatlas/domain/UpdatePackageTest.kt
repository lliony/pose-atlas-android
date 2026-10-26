package com.lliony.poseatlas.domain

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.Signature
import androidx.test.core.app.ApplicationProvider
import com.lliony.poseatlas.data.update.UpdateRelease
import com.lliony.poseatlas.data.update.UpdateRepository
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@Suppress("DEPRECATION")
class UpdatePackageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = UpdateRepository(context)
    private val file = File(context.cacheDir, "candidate.apk")
    private val release = UpdateRelease("1.0.0-beta.3", "", "", 1, "")
    private fun candidate(code: Int = 3, name: String = context.packageName, signer: String = "aabb") {
        fun info(version: Int, pkg: String, cert: String) = PackageInfo().apply {
            packageName = pkg; versionCode = version; versionName = "1.0.0-beta.$version"
            signatures = arrayOf(Signature(cert))
            signingInfo = android.content.pm.SigningInfo().also { shadowOf(it).setSignatures(signatures) }
        }
        shadowOf(context.packageManager).apply {
            installPackage(info(2, context.packageName, "aabb"))
            setPackageArchiveInfo(file.absolutePath, info(code, name, signer))
        }
    }
    @Test fun acceptsNewerPackageWithSameSignature() {
        candidate()
        repository.verifyApk(file, release)
    }
    @Test fun rejectsForeignPackageAndDifferentSignature() {
        candidate(name = "com.other.app")
        assertThrows(IllegalArgumentException::class.java) { repository.verifyApk(file, release) }
        candidate(signer = "ccdd")
        assertThrows(IllegalArgumentException::class.java) { repository.verifyApk(file, release) }
    }
    @Test fun rejectsDowngradeAndMismatchedVersion() {
        candidate(code = 2)
        assertThrows(IllegalArgumentException::class.java) { repository.verifyApk(file, release) }
        candidate(code = 4)
        assertThrows(IllegalArgumentException::class.java) { repository.verifyApk(file, release) }
    }
}
