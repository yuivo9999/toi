package com.toimg.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class AppFlowNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun placeholderFlowCanReachResultAndReturnHome() {
        composeRule.onNodeWithText("开始图片流程").performClick()
        composeRule.onNodeWithText("选择需要处理的图片").assertIsDisplayed()

        composeRule.onNodeWithText("进入工作区预览").performClick()
        composeRule.onNodeWithText("图片移动工作区").assertIsDisplayed()

        composeRule.onNodeWithText("预览完成页面").performClick()
        composeRule.onNodeWithText("流程页面已连通").assertIsDisplayed()

        composeRule.onNodeWithText("返回首页").performClick()
        composeRule.onNodeWithText("图片整理，从这里开始").assertIsDisplayed()
    }
}
