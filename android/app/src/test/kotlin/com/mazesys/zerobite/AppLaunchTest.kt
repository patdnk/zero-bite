package com.mazesys.zerobite

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.mazesys.zerobite.core.model.RecipeCatalogue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AppLaunchTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `launches and shows the app name`() {
        compose.onNodeWithText("Zero Bite").assertExists()
    }

    @Test
    fun `bundles the shared recipe catalogue`() {
        val text = compose.activity.assets.open("recipes.json").bufferedReader().use { it.readText() }
        assertTrue(RecipeCatalogue.decode(text).size >= 30)
    }
}
