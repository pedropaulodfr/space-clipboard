package com.spaceclipboard.app

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test

class DpadNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun dpadFocusesAControl() {
        composeRule.waitUntil(20_000) { composeRule.activity.hasWindowFocus() }
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.pressDPadDown()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(isFocused()).assertIsFocused()
    }
}
