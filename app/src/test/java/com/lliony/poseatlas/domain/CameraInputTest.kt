package com.lliony.poseatlas.domain

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.lliony.poseatlas.MainActivity
import com.lliony.poseatlas.PoseAtlasApplication
import com.lliony.poseatlas.ui.AtlasViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = PoseAtlasApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraInputTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun resetProviderPaths() {
        // Robolectric 每个测试使用不同缓存目录，FileProvider 的静态路径缓存不会随之更新。
        val cache = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
        (cache.get(null) as MutableMap<*, *>).clear()
    }
    private fun ready(): AtlasViewModel {
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("新建卡片").fetchSemanticsNodes().isNotEmpty() }
        return androidx.lifecycle.ViewModelProvider(compose.activity)[AtlasViewModel::class.java]
    }
    private fun capture(success: Boolean, recreate: Boolean = false): Uri {
        compose.onNodeWithText("从相册选择").assertIsDisplayed()
        compose.onNodeWithText("拍摄照片").performClick()
        compose.waitForIdle()
        val request = shadowOf(compose.activity).nextStartedActivityForResult
        check(request != null) { "Camera did not launch" }
        assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, request.intent.action)
        val uri = androidx.core.content.IntentCompat.getParcelableExtra(request.intent, MediaStore.EXTRA_OUTPUT, Uri::class.java)!!
        if (success) {
            val bitmap = Bitmap.createBitmap(80, 120, Bitmap.Config.ARGB_8888)
            compose.activity.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
        }
        if (recreate) {
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
        }
        compose.runOnIdle {
            compose.activity.activityResultRegistry.dispatchResult(request.requestCode, if (success) Activity.RESULT_OK else Activity.RESULT_CANCELED, null)
        }
        return uri
    }
    @Test fun editorImportsCameraPhotoAndCancellationKeepsIt() {
        val vm = ready()
        compose.onNodeWithContentDescription("新建卡片").performClick()
        compose.onNodeWithText("＋ 添加照片 (0/20)").performClick()
        val uri = capture(true, recreate = true)
        compose.waitUntil(10_000) { compose.runOnIdle { vm.editor.value?.card?.images?.size == 1 && !vm.busy.value } }
        val photo = vm.editor.value!!.card.images.single()
        assertEquals(80, photo.width)
        assertEquals(120, photo.height)
        assertFalse(java.io.File(compose.activity.cacheDir, "camera/" + uri.lastPathSegment).exists())
        compose.onNodeWithText("＋ 添加照片 (1/20)").performClick()
        capture(false)
        compose.runOnIdle { assertEquals(listOf(photo), vm.editor.value!!.card.images) }
    }
    @Test fun recommendationImportsCameraPhotoAndCancellationKeepsIt() {
        val vm = ready()
        compose.onNodeWithContentDescription("打开模型推荐").performClick()
        compose.onNodeWithText("＋ 选择一张参考图").performScrollTo().performClick()
        capture(true)
        compose.waitUntil(10_000) { compose.runOnIdle { vm.recommendationSession.photo.value != null && !vm.busy.value } }
        val photo = vm.recommendationSession.photo.value!!
        assertEquals(80, photo.width)
        compose.onNodeWithText("＋ 选择一张参考图").performScrollTo().performClick()
        capture(false)
        compose.runOnIdle { assertEquals(photo, vm.recommendationSession.photo.value) }
    }
}
