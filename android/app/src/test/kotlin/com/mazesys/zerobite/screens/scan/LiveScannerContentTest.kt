package com.mazesys.zerobite.screens.scan

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mazesys.zerobite.R
import com.mazesys.zerobite.core.scanning.LiveScanSession
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.ui.text.UiText
import com.mazesys.zerobite.ui.text.uiText
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class LiveScannerContentTest {

    @get:Rule
    val compose = createComposeRule()

    private var settings = 0
    private var photo = 0
    private var finished = 0
    private var dismissed = 0

    private fun show(access: CameraAccess, session: LiveScanSession = LiveScanSession(), error: UiText? = null) {
        val ui = LiveScanUiState(session, session.result(TestClock.today))
        compose.setContent {
            ZeroBiteTheme {
                LiveScannerContent(
                    access = access, ui = ui, cameraError = error,
                    onFinish = { finished++ }, onOpenSettings = { settings++ }, onUsePhoto = { photo++ },
                    onBack = {}, onDismissError = { dismissed++ },
                ) { Text("Camera preview") }
            }
        }
    }

    @Test
    fun `denied camera offers settings and a photo`() {
        show(CameraAccess.DENIED)
        compose.onNodeWithText("Camera access is off").assertExists()
        compose.onNodeWithText("Zero Bite uses the camera to read best-before dates and barcodes on food labels.").assertExists()
        compose.onNodeWithText("Camera preview").assertDoesNotExist()
        compose.onNodeWithText("Open settings").performClick()
        compose.onNodeWithText("Choose a photo instead").performClick()
        assertEquals(1, settings)
        assertEquals(1, photo)
    }

    @Test
    fun `granted shows the preview and what was found`() {
        show(CameraAccess.GRANTED, LiveScanSession().adding(listOf("SEMI SKIMMED MILK", "USE BY 12 SEP 2026"), listOf("5000119000000")))
        compose.onNodeWithText("Camera preview").assertExists()
        compose.onNodeWithText("12 September 2026").assertExists()
        compose.onNodeWithText("5000119000000").assertExists()
        compose.onNodeWithText("2 lines read").assertExists()
        compose.onNodeWithText("Use what I've found").assertIsEnabled().performClick()
        assertEquals(1, finished)
    }

    @Test
    fun `a single line reads as one line`() {
        show(CameraAccess.GRANTED, LiveScanSession().adding(listOf("SEMI SKIMMED MILK"), emptyList()))
        compose.onNodeWithText("1 line read").assertExists()
    }

    @Test
    fun `nothing yet asks for a date and can't finish`() {
        show(CameraAccess.GRANTED)
        compose.onNodeWithText("Looking for a best-before date…").assertExists()
        compose.onNodeWithText("0 lines read").assertExists()
        compose.onNodeWithText("Use what I've found").assertIsNotEnabled()
    }

    @Test
    fun `a camera error is shown and can be dismissed`() {
        show(CameraAccess.GRANTED, error = uiText(R.string.scan_camera_unavailable_message))
        compose.onNodeWithText("Camera unavailable").assertExists()
        compose.onNodeWithText("The camera couldn't start. Close any other app that is using it and try again.").assertExists()
        compose.onNodeWithText("OK").performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun `resuming picks up a permission granted in settings`() {
        assertEquals(CameraAccess.GRANTED, CameraAccess.DENIED.afterResume(granted = true))
        assertEquals(CameraAccess.GRANTED, CameraAccess.ASKING.afterResume(granted = true))
        assertEquals(CameraAccess.DENIED, CameraAccess.DENIED.afterResume(granted = false))
        // The system prompt pauses the activity: not granted yet must not read as denied.
        assertEquals(CameraAccess.ASKING, CameraAccess.ASKING.afterResume(granted = false))
    }
}
