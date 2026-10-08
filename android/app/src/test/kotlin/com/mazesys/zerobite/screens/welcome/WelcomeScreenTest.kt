package com.mazesys.zerobite.screens.welcome

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.mazesys.zerobite.reminders.NotificationPermission
import com.mazesys.zerobite.testing.FakeAccess
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class WelcomeScreenTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val vm by lazy { WelcomeViewModel(WelcomeMode.FIRST_RUN, FakeSettings(), FakeAccess(NotificationPermission.NOT_ASKED), onComplete = {}) }

    @Test
    fun `next walks the pages`() {
        compose.setContent { ZeroBiteTheme { WelcomeScreen(vm) } }
        compose.onNodeWithText("Waste less,\ncook more.").assertExists()
        compose.onNodeWithTag("welcome.primary").performClick()
        compose.waitUntil(3_000) { vm.state.value.currentPage == WelcomePage.SCAN }
        compose.onNodeWithText("Scan the label, skip the typing.").assertExists()
        compose.onNodeWithTag("welcome.skip").performClick()
        compose.onNodeWithText("Want a nudge before food goes off?").assertExists()
        compose.onNodeWithTag("welcome.notNow").assertExists()
    }

    @Test
    fun `welcome light`() {
        compose.setContent { ZeroBiteTheme { WelcomeScreen(vm) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/welcome_light.png")
    }

    @Test
    fun `welcome reminders dark`() {
        vm.skipIntro()
        compose.setContent { ZeroBiteTheme(darkTheme = true) { WelcomeScreen(vm) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/welcome_reminders_dark.png")
    }
}
