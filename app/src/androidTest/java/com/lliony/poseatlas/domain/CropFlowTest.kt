package com.lliony.poseatlas.domain

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lliony.poseatlas.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CropFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun circularRecommendationAndManualCropKeepOriginalUntilConfirmed() {
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("新建卡片").fetchSemanticsNodes().isNotEmpty() }
        val fab = compose.onNodeWithContentDescription("打开模型推荐").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertEquals(fab.width, fab.height, 1f)
        compose.onNodeWithText("模型推荐").assertDoesNotExist()
        compose.onNodeWithText("往前走，拍一个背影").performClick()
        compose.onNodeWithText("编辑").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("AI 填充").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("AI 填充").assertIsDisplayed()
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[com.lliony.poseatlas.ui.AtlasViewModel::class.java]
        val original = vm.editor.value!!.card.images.first()
        compose.onNodeWithText("裁切当前图片").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("确认裁切").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("取消裁切").performClick()
        org.junit.Assert.assertEquals(original, vm.editor.value!!.card.images.first())
        compose.onNodeWithText("AI 填充").assertIsDisplayed()
        compose.onNodeWithText("裁切当前图片").performScrollTo().performClick()
        compose.onNodeWithText("1:1").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("crop-canvas").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("裁切缩放").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(2f) }
        compose.onNodeWithTag("crop-canvas").performTouchInput { swipe(center, androidx.compose.ui.geometry.Offset(center.x + 80f, center.y), 300) }
        compose.onNodeWithText("确认裁切").assertIsEnabled().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("crop-screen").fetchSemanticsNodes().isEmpty() || vm.cropError.value != null }
        org.junit.Assert.assertNull(vm.cropError.value, vm.cropError.value)
        val cropped = vm.editor.value!!.card.images.first()
        org.junit.Assert.assertEquals(cropped.width, cropped.height)
        org.junit.Assert.assertEquals(original.id, cropped.id)
    }

}
