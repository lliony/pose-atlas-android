package com.lliony.poseatlas.domain

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.lliony.poseatlas.MainActivity
import com.lliony.poseatlas.PoseAtlasApplication
import com.lliony.poseatlas.data.local.WorkspaceEntryPoint
import dagger.hilt.android.EntryPointAccessors
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
    private fun repo() = EntryPointAccessors.fromApplication(compose.activity.applicationContext, WorkspaceEntryPoint::class.java).repository()
    @Test fun systemBarIconsFollowTheInAppTheme() {
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("新建卡片").fetchSemanticsNodes().isNotEmpty() }
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[com.lliony.poseatlas.ui.AtlasViewModel::class.java]
        val controller = androidx.core.view.WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView)
        compose.runOnIdle { vm.preferences(vm.preferences.value.copy(theme = "dark")) }
        compose.waitUntil(10_000) { compose.runOnIdle { vm.preferences.value.theme == "dark" } }
        compose.waitForIdle()
        org.junit.Assert.assertFalse(controller.isAppearanceLightStatusBars)
        org.junit.Assert.assertFalse(controller.isAppearanceLightNavigationBars)
        compose.runOnIdle { vm.preferences(vm.preferences.value.copy(theme = "paper")) }
        compose.waitUntil(10_000) { compose.runOnIdle { vm.preferences.value.theme == "paper" } }
        compose.waitForIdle()
        org.junit.Assert.assertTrue(controller.isAppearanceLightStatusBars)
        org.junit.Assert.assertTrue(controller.isAppearanceLightNavigationBars)
    }
    @Test fun newCardPlacesAiFillBelowAddPhotos() {
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("新建卡片").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("新建卡片").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("ai-fill-bar").fetchSemanticsNodes().isNotEmpty() }
        val addPhoto = compose.onNodeWithText("＋ 添加照片 (0/20)").getUnclippedBoundsInRoot()
        val ai = compose.onNodeWithTag("ai-fill-bar").getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue(ai.top >= addPhoto.bottom)
        compose.onNodeWithText("私人备注（不打印、不分享）").performScrollTo()
        compose.onNodeWithText("AI 填充").assertIsNotDisplayed()
        compose.onNodeWithText("AI 填充").performScrollTo().assertIsDisplayed()
    }
    @Test fun aiFillActionsStayVisibleWhileScrollingAndUndoRestoresSnapshot() {
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("新建卡片").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("往前走，拍一个背影").performScrollTo().performClick()
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
        compose.onNodeWithText("往前走，拍一个背影").performScrollTo().performClick()
        compose.onNodeWithText("编辑").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("AI 填充").fetchSemanticsNodes().isNotEmpty() }
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[com.lliony.poseatlas.ui.AtlasViewModel::class.java]
        val bitmap = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888)
        val bytes = java.io.ByteArrayOutputStream().apply { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, this) }.toByteArray()
        bitmap.recycle()
        val photo = vm.images.import(bytes.inputStream())
        val file = vm.images.file(photo.src)!!
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
    private fun showRatioPhoto(width: Int, height: Int): String {
        compose.waitUntil(20_000) {
            compose.onAllNodesWithContentDescription("新建卡片").fetchSemanticsNodes().isNotEmpty()
        }
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[com.lliony.poseatlas.ui.AtlasViewModel::class.java]
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val bytes = java.io.ByteArrayOutputStream().apply {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, this)
        }.toByteArray()
        bitmap.recycle()
        val photo = vm.images.import(bytes.inputStream())
        val card = vm.state.value.cards.first()
        kotlinx.coroutines.runBlocking {
            repo().mutate { saveCard(card.copy(images = listOf(photo))) }
        }
        compose.waitUntil(10_000) {
            compose.runOnIdle { vm.state.value.cards.first { it.id == card.id }.images.single().id == photo.id }
        }
        compose.waitForIdle()
        return card.id
    }

    @Test fun detailPhotosSwipeInOneAreaAndNotesFollowTheCurrentPhoto() {
        val id = showRatioPhoto(160, 90)
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[com.lliony.poseatlas.ui.AtlasViewModel::class.java]
        val card = vm.state.value.cards.first { it.id == id }
        val photos = listOf(160 to 90, 90 to 160, 100 to 100).mapIndexed { index, (w, h) ->
            card.images.single().copy(id = "detail-photo-$index", width = w, height = h,
                notes = com.lliony.poseatlas.domain.model.Notes(pose = "第${index + 1}张独立教学"))
        }
        kotlinx.coroutines.runBlocking { repo().mutate { saveCard(card.copy(images = photos)) } }
        compose.waitUntil(10_000) { vm.state.value.cards.first { it.id == id }.images.size == 3 }
        compose.onNodeWithContentDescription("查看详情：${card.title}").performClick()
        compose.onNodeWithTag("detail-photo-pager").assertIsDisplayed()
        compose.onNodeWithText("1 / 3").assertExists()
        compose.onNodeWithText("第1张独立教学").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("第2张独立教学").assertDoesNotExist()
        compose.onNodeWithTag("detail-photo-pager").performScrollTo().performTouchInput { swipeLeft() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("2 / 3").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("第2张独立教学").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("第1张独立教学").assertDoesNotExist()
        compose.onNodeWithTag("detail-photo-pager").performScrollTo().performTouchInput { swipeLeft() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("3 / 3").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("detail-photo-pager").performTouchInput { swipeRight() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("2 / 3").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("分享此图的卡片正反面").assertCountEquals(1)
        compose.onNodeWithContentDescription("下一张图片").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("3 / 3").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("下一张图片").assertIsNotEnabled()
        kotlinx.coroutines.runBlocking { repo().mutate { saveCard(card.copy(images = photos.take(1))) } }
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("下一张图片").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("第1张独立教学").performScrollTo().assertIsDisplayed()
    }

    @Test fun cardPhotoUsesItsOwnAspectAndHasRealInsets() {
        listOf(90 to 160, 100 to 100, 120 to 160, 160 to 90, 100 to 150, 160 to 120).forEach { (w, h) ->
            val id = showRatioPhoto(w, h)
            compose.onNodeWithTag("library-grid").performScrollToIndex(0)
            val photo = compose.onNodeWithTag("card-photo-$id", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val card = compose.onNodeWithTag("pose-card-$id").getUnclippedBoundsInRoot()
            org.junit.Assert.assertEquals(w.toFloat() / h, (photo.right - photo.left).value / (photo.bottom - photo.top).value, 0.02f)
            org.junit.Assert.assertTrue(photo.left.value - card.left.value >= 11.5f)
            org.junit.Assert.assertTrue(card.right.value - photo.right.value >= 11.5f)
            org.junit.Assert.assertTrue(photo.top.value - card.top.value >= 11.5f)
        }
    }
    @Test fun longCodeDoesNotChangeTheSamePhotoCardHeight() {
        val id = showRatioPhoto(120, 160)
        val before = compose.onNodeWithTag("pose-card-$id").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[com.lliony.poseatlas.ui.AtlasViewModel::class.java]
        val card = vm.state.value.cards.first { it.id == id }
        kotlinx.coroutines.runBlocking {
            repo().mutate { saveCard(card.copy(code = "01\n02\n03\n04\n05")) }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("01\n02\n03\n04\n05").fetchSemanticsNodes().isNotEmpty()
        }
        val after = compose.onNodeWithTag("pose-card-$id").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
        org.junit.Assert.assertEquals(before, after, 1f)
    }
    @Test fun photoFlipsWhileTopIconsKeepTheirOwnActions() {
        val id = showRatioPhoto(120, 160)
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[com.lliony.poseatlas.ui.AtlasViewModel::class.java]
        val title = vm.state.value.cards.first { it.id == id }.title
        val photoBounds = compose.onNodeWithTag("card-photo-$id", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val controls = compose.onNodeWithTag("card-controls-$id", useUnmergedTree = true).getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue(controls.top >= photoBounds.top && controls.bottom <= photoBounds.bottom)
        org.junit.Assert.assertTrue(controls.left >= photoBounds.left && controls.right <= photoBounds.right)
        compose.onNodeWithTag("card-photo-$id", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("card-back-$id", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("card-back-$id", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("编辑", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("card-back-$id", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("card-photo-$id", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("card-photo-$id", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("选择卡片：$title").performClick().assertIsOn()
        compose.onNodeWithText("已选 1").assertExists()
        compose.onNodeWithTag("card-back-$id", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("选择卡片：$title").performClick().assertIsOff()
        compose.onNodeWithContentDescription("查看详情：$title").performClick()
        compose.onNodeWithText("编辑").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("card-photo-$id", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("card-photo-$id", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("翻面看教学").assertDoesNotExist()
    }
    @Test fun circularRecommendationAndManualCropKeepOriginalUntilConfirmed() {
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("新建卡片").fetchSemanticsNodes().isNotEmpty() }
        val fab = compose.onNodeWithContentDescription("打开模型推荐").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertEquals(fab.width, fab.height, 1f)
        compose.onNodeWithText("模型推荐").assertDoesNotExist()
        compose.onNodeWithText("往前走，拍一个背影").performScrollTo().performClick()
        compose.onNodeWithText("编辑").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("AI 填充").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("AI 填充").assertIsDisplayed()
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[com.lliony.poseatlas.ui.AtlasViewModel::class.java]
        val original = vm.editor.value!!.card.images.first()
        compose.onNodeWithText("裁切当前图片").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("确认裁切").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("取消裁切").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("crop-screen").fetchSemanticsNodes().isEmpty() }
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
        kotlinx.coroutines.runBlocking { repo().mutate { replace(com.lliony.poseatlas.domain.model.State.Empty) } }
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
