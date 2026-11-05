package com.toimg.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class AppLaunchTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreenIsVisibleOnLaunch() {
        composeRule
            .onNodeWithText("图片整理，从这里开始")
            .assertIsDisplayed()
    }
}
