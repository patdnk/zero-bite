package com.mazesys.zerobite.screens.inventory

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.data.SettingsSnapshot
import com.mazesys.zerobite.testing.FakeAccess
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.SequentialIds
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.ui.text.Strings
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Fridge in Polish: resources, plurals, dates and the sample food all follow the app language. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pl-w411dp-h891dp-xxhdpi")
class InventoryScreenPolishTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `my food reads in polish`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = AppCoordinator { TestClock.today }
        val vm = InventoryViewModel(
            FakeInventoryRepository(), FakeSettings(SettingsSnapshot(dismissedNotificationTip = true)), TestClock.fixed(),
            SequentialIds(), app.inventory, FakeAccess(), Strings { context.getString(it) },
        )
        compose.setContent { ZeroBiteTheme { InventoryScreen(vm) } }

        compose.onNodeWithText("Nie ma tu jeszcze produktów").assertIsDisplayed()
        compose.onNodeWithText("Wypróbuj na przykładowych produktach").performClick()

        compose.onNode(hasText("Moje produkty") and isHeading()).assertIsDisplayed()
        compose.onNodeWithText("pon. 7 wrz").assertIsDisplayed()
        // The sample yogurt went off yesterday and leads the "Use first" card.
        compose.onNodeWithText("Zużyj najpierw").assertIsDisplayed()
        compose.onNodeWithText("Jogurt grecki").assertIsDisplayed()
        compose.onNodeWithText("500 g · Lodówka").assertIsDisplayed()
        compose.onNodeWithText("dzień po terminie").assertIsDisplayed()
    }
}
