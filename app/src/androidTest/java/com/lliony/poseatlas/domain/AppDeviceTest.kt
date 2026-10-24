package com.lliony.poseatlas.domain

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.lliony.poseatlas.MainActivity
import com.lliony.poseatlas.PoseAtlasApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4



@RunWith(AndroidJUnit4::class)


class AppDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
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

