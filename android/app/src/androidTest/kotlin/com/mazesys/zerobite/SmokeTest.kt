package com.mazesys.zerobite

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Launch → (welcome) → add an item by hand → find it in the fridge, on a real device. */
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    private fun existsText(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun addAnItemByHand() {
        compose.waitUntil(10_000) { exists("welcome.start") || exists("inventory.addButton") }
        if (exists("welcome.start")) compose.onNodeWithTag("welcome.start").performClick()

        compose.waitUntil(5_000) { exists("inventory.addButton") }
        compose.onNodeWithTag("inventory.addButton").performClick()
        compose.onNodeWithText("Add manually").performClick()

        val name = "Smoke test ${System.currentTimeMillis() % 100_000}"
        compose.waitUntil(5_000) { exists("itemForm.name") }
        compose.onNodeWithTag("itemForm.name").performTextInput(name)
        compose.onNodeWithTag("itemForm.save").performClick()

        compose.waitUntil(5_000) { exists("inventory.search") }
        compose.onNodeWithTag("inventory.search").performTextInput(name)
        compose.waitUntil(5_000) { existsText(name) }
    }
}
