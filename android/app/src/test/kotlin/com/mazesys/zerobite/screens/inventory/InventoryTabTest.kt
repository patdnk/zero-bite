package com.mazesys.zerobite.screens.inventory

import com.mazesys.zerobite.testing.FakeAccess
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.sampleFridge
import com.mazesys.zerobite.testing.testDependencies
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class InventoryTabTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `opens detail from the grid and comes back`() {
        val dependencies = testDependencies(inventory = FakeInventoryRepository(sampleFridge()))
        val app = AppCoordinator { TestClock.today }
        val list = InventoryViewModel(dependencies.inventory, dependencies.settings, dependencies.clock, dependencies.ids, app.inventory, FakeAccess())
        compose.setContent { ZeroBiteTheme { InventoryTab(app.inventory, list, dependencies) } }

        compose.onNodeWithText("Spinach").performClick()
        compose.onNodeWithText("Details").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNode(hasText("Fridge") and isHeading()).assertIsDisplayed()
        assertEquals(1, app.inventory.backStack.size)
    }
}
