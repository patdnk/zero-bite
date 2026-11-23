package com.mazesys.zerobite.screens.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.mazesys.zerobite.reminders.LocalPermissionRequester
import com.mazesys.zerobite.reminders.NotificationPermission
import com.mazesys.zerobite.reminders.PermissionRequester
import com.mazesys.zerobite.testing.FakeAccess
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.sampleFridge
import com.mazesys.zerobite.testing.testDependencies
import com.mazesys.zerobite.testing.testMain
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsScreenTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val access = FakeAccess(NotificationPermission.NOT_ASKED)

    private val inventory = FakeInventoryRepository(sampleFridge())

    private fun show(dark: Boolean = false) {
        val dependencies = testDependencies(inventory = inventory, settings = FakeSettings(), access = access)
        val vm = SettingsViewModel(dependencies, testMain(dependencies).settings, "1.0 (1)")
        val grant = PermissionRequester { access.current = NotificationPermission.GRANTED; true }
        compose.setContent {
            ZeroBiteTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalPermissionRequester provides grant) {
                    SettingsScreen(vm)
                    SettingsAlertHost(vm)
                }
            }
        }
    }

    @Test
    fun `reminders toggle asks and turns on`() {
        show()
        compose.onNode(hasText("Settings") and isHeading()).assertExists()
        compose.onNodeWithTag("settings.notificationsToggle").assertIsOff().performClick()
        compose.onNodeWithTag("settings.notificationsToggle").assertIsOn()
        compose.onNodeWithText("Remind me 3 days before").assertExists()
    }

    @Test
    fun `sections from iOS are all there`() {
        show()
        for (text in listOf("Food safety", "Your allergies", "“Expiring soon” means within 3 days", "In My food", "How Zero Bite works")) {
            compose.onNodeWithText(text).performScrollTo().assertExists()
        }
    }

    @Test
    fun `usage data sharing is off until switched on`() {
        show()
        compose.onNodeWithText("Privacy").performScrollTo().assertExists()
        compose.onNodeWithTag("settings.shareUsageToggle").performScrollTo().assertIsOff().performClick()
        compose.onNodeWithTag("settings.shareUsageToggle").assertIsOn()
    }

    @Test
    fun `delete everything asks first, and cancel keeps the food`() {
        show()
        compose.onNodeWithText("Delete everything").performScrollTo().performClick()
        compose.onNodeWithText("Delete everything?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Delete everything?").assertDoesNotExist()
        assertTrue(inventory.current.isNotEmpty())

        compose.onNodeWithText("Delete everything").performScrollTo().performClick()
        compose.onNodeWithText("Delete all").performClick()
        compose.waitForIdle()
        assertTrue(inventory.current.isEmpty())
    }

    @Test
    fun `settings light`() {
        show()
        compose.onRoot().captureRoboImage("src/test/screenshots/settings_light.png")
    }

    @Test
    fun `settings privacy light`() {
        show()
        compose.onNodeWithText("In My food").performScrollTo() // the Data row below, so the whole Privacy section shows
        compose.onRoot().captureRoboImage("src/test/screenshots/settings_privacy_light.png")
    }

    @Test
    fun `settings dark`() {
        show(dark = true)
        compose.onRoot().captureRoboImage("src/test/screenshots/settings_dark.png")
    }
}
