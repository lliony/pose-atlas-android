package com.lliony.poseatlas.domain

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.lliony.poseatlas.data.local.WorkspaceEntryPoint
import dagger.hilt.android.EntryPointAccessors
import com.lliony.poseatlas.MainActivity
import com.lliony.poseatlas.PoseAtlasApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4



@RunWith(AndroidJUnit4::class)


class AppDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun repo() = EntryPointAccessors.fromApplication(compose.activity.applicationContext, WorkspaceEntryPoint::class.java).repository()
    @Test fun detailPhotosSwipeAndKeepOnlyCurrentTeachingOnDevice() {
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("新建卡片").fetchSemanticsNodes().isNotEmpty() }
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[com.lliony.poseatlas.ui.AtlasViewModel::class.java]
        val original = vm.state.value.cards.first()
        val photos = vm.state.value.cards.flatMap { it.images }.take(3).mapIndexed { index, photo ->
            photo.copy(id = "detail-device-$index", notes = com.lliony.poseatlas.domain.model.Notes(pose = "第${index + 1}张独立教学"))
        }
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        fun screenshot(name: String) {
            compose.waitForIdle()
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            java.io.File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        try {
            kotlinx.coroutines.runBlocking { repo().mutate { saveCard(original.copy(images = photos)) } }
            compose.waitUntil(10_000) { vm.state.value.cards.first { it.id == original.id }.images.first().id == photos.first().id }
            compose.onNodeWithContentDescription("查看详情：${original.title}").performClick()
            compose.onNodeWithTag("detail-photo-pager").assertIsDisplayed()
            compose.onNodeWithText("1 / 3").assertExists()
            compose.onNodeWithContentDescription("上一张图片").assertIsNotEnabled()
            screenshot("detail-page-1.png")
            for (page in 2..3) {
                compose.onNodeWithTag("detail-photo-pager").performScrollTo().performTouchInput { swipeLeft() }
                compose.waitUntil(5_000) { compose.onAllNodesWithText("$page / 3").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("第${page}张独立教学").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("第${page - 1}张独立教学").assertDoesNotExist()
                compose.onAllNodesWithText("分享此图的卡片正反面").assertCountEquals(1)
                compose.onNodeWithTag("detail-photo-pager").performScrollTo()
                screenshot("detail-page-$page.png")
            }
            compose.onNodeWithContentDescription("下一张图片").assertIsNotEnabled()
            compose.onNodeWithTag("detail-photo-pager").performTouchInput { swipeRight() }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("2 / 3").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("第2张独立教学").performScrollTo().assertIsDisplayed()
            kotlinx.coroutines.runBlocking { repo().mutate { saveCard(original.copy(images = photos.take(1))) } }
            compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("下一张图片").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("第1张独立教学").performScrollTo().assertIsDisplayed()
        } finally {
            kotlinx.coroutines.runBlocking { repo().mutate { saveCard(original) } }
        }
    }

    @Test fun launchSearchAndRestoreNewCardDraft() {
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("新建卡片").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("搜索标题、编号、标签").performTextInput("REF-001")
        compose.onNodeWithText("迎着阳光，轻轻走来").assertExists()
        compose.onNodeWithContentDescription("新建卡片").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("标题 *").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("标题 *").performTextReplacement("保留的新卡草稿")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("保留草稿").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("新建卡片").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("新建卡片").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("保留的新卡草稿").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("已恢复上次未保存的修改").assertExists()
    }
    @Test fun cardFrameRotatesInBothDirections() {
        val id = "card-01-001"
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("card-photo-$id", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        val card = compose.onNodeWithTag("pose-card-$id")
        val frontWidth = card.fetchSemanticsNode().boundsInRoot.width
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val originalScale = android.provider.Settings.Global.getString(instrumentation.targetContext.contentResolver, "animator_duration_scale")
        fun animationScale(value: String?) {
            val command = if (value == null) "settings delete global animator_duration_scale" else "settings put global animator_duration_scale $value"
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        }
        // 显式启用测试环境的动画，结束后恢复原设置。
        try {
            animationScale("1")
            compose.waitUntil(5_000) { android.animation.ValueAnimator.areAnimatorsEnabled() }
            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag("card-photo-$id", useUnmergedTree = true)
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(160)
            org.junit.Assert.assertTrue(card.fetchSemanticsNode().boundsInRoot.width < frontWidth * 0.8f)
            compose.mainClock.advanceTimeBy(600)
            compose.onNodeWithTag("card-back-$id", useUnmergedTree = true).assertExists()
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(160)
            org.junit.Assert.assertTrue(card.fetchSemanticsNode().boundsInRoot.width < frontWidth * 0.8f)
            compose.mainClock.advanceTimeBy(600)
            compose.onNodeWithTag("card-photo-$id", useUnmergedTree = true).assertExists()
            org.junit.Assert.assertEquals(frontWidth, card.fetchSemanticsNode().boundsInRoot.width, 1f)
        } finally {
            compose.mainClock.autoAdvance = true
            animationScale(originalScale)
        }
    }
    @Test fun cardImageAndTopIconsRemainSeparateOnDevice() {
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("card-photo-card-01-001", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("card-photo-card-01-001", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("card-back-card-01-001", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("card-back-card-01-001", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("card-back-card-01-001", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("card-photo-card-01-001", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("选择卡片：往前走，拍一个背影").performClick().assertIsOn()
        compose.onNodeWithTag("card-photo-card-01-001", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("查看详情：往前走，拍一个背影").performClick()
        compose.onNodeWithText("编辑").assertExists()
    }
    @Test fun originalRatiosAndPagingFillCardOnDevice() {
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("新建卡片").fetchSemanticsNodes().isNotEmpty() }
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[com.lliony.poseatlas.ui.AtlasViewModel::class.java]
        val original = vm.state.value.cards.first()
        val ratios = listOf(90 to 160, 100 to 100, 120 to 160, 160 to 90, 100 to 150, 160 to 120)
        val photos = ratios.map { (w, h) ->
            val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.rgb(244, 224, 199))
            val paint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(195, 72, 47); strokeWidth = 4f; style = android.graphics.Paint.Style.STROKE }
            canvas.drawRect(2f, 2f, w - 2f, h - 2f, paint)
            canvas.drawLine(0f, 0f, w.toFloat(), h.toFloat(), paint)
            val bytes = java.io.ByteArrayOutputStream().apply { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, this) }.toByteArray()
            bitmap.recycle()
            vm.images.import(bytes.inputStream())
        }
        try {
            kotlinx.coroutines.runBlocking { repo().mutate { saveCard(original.copy(images = photos)) } }
            compose.waitUntil(10_000) { vm.state.value.cards.first { it.id == original.id }.images.size == photos.size }
            ratios.forEachIndexed { index, (w, h) ->
                compose.onNodeWithTag("library-grid").performScrollToIndex(0)
                val image = compose.onNodeWithTag("card-photo-${original.id}", useUnmergedTree = true).getUnclippedBoundsInRoot()
                val card = compose.onNodeWithTag("pose-card-${original.id}").getUnclippedBoundsInRoot()
                org.junit.Assert.assertEquals(w.toFloat() / h, (image.right - image.left).value / (image.bottom - image.top).value, 0.02f)
                org.junit.Assert.assertEquals(0f, (image.left - card.left).value, 0.6f)
                org.junit.Assert.assertEquals(0f, (card.right - image.right).value, 0.6f)
                val controls = compose.onNodeWithTag("card-controls-${original.id}", useUnmergedTree = true).getUnclippedBoundsInRoot()
                org.junit.Assert.assertTrue(controls.top >= image.top && controls.bottom <= image.bottom)
                org.junit.Assert.assertTrue(controls.left >= image.left && controls.right <= image.right)
                val next = compose.onNode(hasContentDescription("下一张图片") and hasAnyAncestor(hasTestTag("pose-card-${original.id}")))
                next.performScrollTo().assertIsDisplayed()
                val bottom = next.getUnclippedBoundsInRoot().bottom
                val scrolledCard = compose.onNodeWithTag("pose-card-${original.id}").getUnclippedBoundsInRoot()
                // Material 图标按钮的语义边界可能小于其布局触控区，底部只要求至少 12dp。
                org.junit.Assert.assertTrue((scrolledCard.bottom - bottom).value >= 11.5f)
                // 比例矩阵直接触发分页语义，避免悬浮入口覆盖测试点击位置。
                if (index < ratios.lastIndex) next.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
            }
        } finally {
            kotlinx.coroutines.runBlocking { repo().mutate { saveCard(original) } }
        }
    }
}

