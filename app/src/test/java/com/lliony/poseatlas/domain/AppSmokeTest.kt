package com.lliony.poseatlas.domain

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.lliony.poseatlas.MainActivity
import com.lliony.poseatlas.PoseAtlasApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = PoseAtlasApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun aiFillActionsStayVisibleWhileScrollingAndUndoRestoresSnapshot() {
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("新建卡片").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("往前走，拍一个背影").performClick()
        compose.onNodeWithText("编辑").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("AI 填充").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("私人备注（不打印、不分享）").performScrollTo()
        compose.onNodeWithText("AI 填充").assertIsDisplayed()
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[com.lliony.poseatlas.ui.AtlasViewModel::class.java]
        val original = vm.editor.value!!.card
        compose.runOnIdle { vm.busy.value = true }
        compose.onNodeWithText("处理中…").assertIsNotEnabled()
        compose.runOnIdle { vm.busy.value = false; vm.editorUndo.value = original; vm.edit(original.copy(title = "填充后的标题")) }
        compose.onNodeWithText("撤销填充").assertIsDisplayed().performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(original, vm.editor.value!!.card) }
    }
    @Test fun failedCropKeepsOriginalAndCanRetry() {
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("新建卡片").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("往前走，拍一个背影").performClick()
        compose.onNodeWithText("编辑").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("AI 填充").fetchSemanticsNodes().isNotEmpty() }
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[com.lliony.poseatlas.ui.AtlasViewModel::class.java]
        val bitmap = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888)
        val bytes = java.io.ByteArrayOutputStream().apply { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, this) }.toByteArray()
        bitmap.recycle()
        val photo = vm.repository.images.import(bytes.inputStream())
        val file = vm.repository.images.file(photo.src)!!
        val originalBytes = file.readBytes()
        compose.runOnIdle { vm.edit(vm.editor.value!!.card.copy(images = listOf(photo))); vm.beginCrop(photo) }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("crop-canvas").fetchSemanticsNodes().isNotEmpty() }
        file.delete()
        compose.onNodeWithText("确认裁切").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("裁切保存失败，原图未更改。请检查存储空间后重试。").fetchSemanticsNodes().isNotEmpty() }
        org.junit.Assert.assertEquals(photo, vm.editor.value!!.card.images.first())
        compose.onNodeWithTag("crop-screen").assertExists()
        file.writeBytes(originalBytes)
        compose.onNodeWithText("确认裁切").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("crop-screen").fetchSemanticsNodes().isEmpty() }
        org.junit.Assert.assertNotEquals(photo.src, vm.editor.value!!.card.images.first().src)
        org.junit.Assert.assertArrayEquals(originalBytes, file.readBytes())
    }
    @Test fun cardFrontHeightDoesNotDependOnCodeWrapping() {
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("新建卡片").fetchSemanticsNodes().isNotEmpty() }
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[com.lliony.poseatlas.ui.AtlasViewModel::class.java]
        val first = vm.state.value.cards[0]
        val second = vm.state.value.cards[1]
        kotlinx.coroutines.runBlocking { vm.repository.mutate { saveCard(first.copy(code = "01\n02\n03\n04\n05")) } }
        val firstHeight = compose.onNodeWithTag("pose-card-${first.id}").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
        compose.onNodeWithTag("library-grid").performScrollToNode(hasTestTag("pose-card-${second.id}"))
        val secondHeight = compose.onNodeWithTag("pose-card-${second.id}").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
        org.junit.Assert.assertEquals(firstHeight, secondHeight, 1f)
    }
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
        compose.onNodeWithText("确认裁切").assertIsEnabled().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("crop-screen").fetchSemanticsNodes().isEmpty() || vm.cropError.value != null }
        org.junit.Assert.assertNull(vm.cropError.value, vm.cropError.value)
        val cropped = vm.editor.value!!.card.images.first()
        org.junit.Assert.assertEquals(cropped.width, cropped.height)
        org.junit.Assert.assertEquals(original.id, cropped.id)
    }
    @Test fun mobileNavigationPlacesSettingsLastAndOpensRecommendationOverlay() {
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("新建卡片").fetchSemanticsNodes().isNotEmpty() }
        val albums = compose.onNodeWithText("卡片册").fetchSemanticsNode().boundsInRoot
        val settings = compose.onNodeWithText("设置").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue("设置应位于卡片册右侧", settings.left > albums.left)
        compose.onNodeWithText("图鉴").assertIsSelected()
        val firstCard = compose.onNodeWithTag("pose-card-card-01-001").fetchSemanticsNode().boundsInRoot
        val grid = compose.onNodeWithTag("library-grid").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue("手机卡片应占据整行", firstCard.width > grid.width * 0.8f)
        compose.onNodeWithContentDescription("打开模型推荐").performClick()
        compose.onNodeWithText("AI 姿势推荐").assertExists()
        compose.onNodeWithText("描述场景、光线或想要的姿势").performTextInput("咖啡馆窗边")
        compose.onNodeWithContentDescription("收起模型推荐").performClick()
        compose.onNodeWithContentDescription("打开模型推荐").performClick()
        compose.onNodeWithText("咖啡馆窗边").assertExists()
    }
    @Test fun printOptionsCannotChangeDuringGeneration() {
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("新建卡片").fetchSemanticsNodes().isNotEmpty() }
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[com.lliony.poseatlas.ui.AtlasViewModel::class.java]
        compose.onNodeWithText("卡片册").performClick()
        compose.onNodeWithText(vm.state.value.albums.first().name).performClick()
        compose.onNodeWithText("打印 / PDF").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("a4").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { vm.busy.value = true }
        compose.onNodeWithText("a5").assertIsNotEnabled()
    }
    @Test fun emptyReplacementKeepsCategoryRecoveryAvailable() {
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("新建卡片").fetchSemanticsNodes().isNotEmpty() }
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[com.lliony.poseatlas.ui.AtlasViewModel::class.java]
        kotlinx.coroutines.runBlocking { vm.repository.mutate { replace(com.lliony.poseatlas.domain.model.State.Empty) } }
        compose.onNodeWithContentDescription("管理分类").assertExists()
        compose.onNodeWithContentDescription("管理分类").performClick()
        compose.onNodeWithText("新增").assertExists()
    }
    @Test fun launchSearchAndRestoreNewCardDraft() {
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("新建卡片").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("搜索标题、编号、标签").performTextInput("REF-001")
        compose.onNodeWithText("迎着阳光，轻轻走来").assertExists()
        compose.onNodeWithContentDescription("新建卡片").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("标题 *").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("标题 *").performTextInput("保留的新卡草稿")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("保留草稿").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("新建卡片").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("新建卡片").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("保留的新卡草稿").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("已恢复上次未保存的修改").assertExists()
    }
}
