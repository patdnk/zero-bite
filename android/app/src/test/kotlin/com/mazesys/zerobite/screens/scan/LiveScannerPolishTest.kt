package com.mazesys.zerobite.screens.scan

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.material3.Text
import com.mazesys.zerobite.core.scanning.LiveScanSession
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The camera screen in Polish: copy, the date and the plural line count follow the app language. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pl-w411dp-h891dp-xxhdpi")
class LiveScannerPolishTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(access: CameraAccess, session: LiveScanSession) {
        val ui = LiveScanUiState(session, session.result(TestClock.today))
        compose.setContent {
            ZeroBiteTheme {
                LiveScannerContent(access, ui, cameraError = null, onFinish = {}, onOpenSettings = {}, onUsePhoto = {}, onBack = {}, onDismissError = {}) {
                    Text("Podgląd")
                }
            }
        }
    }

    @Test
    fun `the denied panel reads in polish`() {
        show(CameraAccess.DENIED, LiveScanSession())
        compose.onNodeWithText("Dostęp do aparatu jest wyłączony").assertExists()
        compose.onNodeWithText("Otwórz ustawienia").assertExists()
        compose.onNodeWithText("Wybierz zdjęcie").assertExists()
    }

    @Test
    fun `what was found reads in polish`() {
        show(CameraAccess.GRANTED, LiveScanSession().adding(listOf("MLEKO", "USE BY 12 SEP 2026"), emptyList()))
        compose.onNodeWithText("12 września 2026").assertExists()
        compose.onNodeWithText("Odczytano 2 wiersze").assertExists()
        compose.onNodeWithText("Użyj znalezionych danych").assertExists()
    }

    @Test
    fun `the plural covers the other polish forms`() {
        show(CameraAccess.GRANTED, LiveScanSession().adding(listOf("AA", "BB", "CC", "DD", "EE"), emptyList()))
        compose.onNodeWithText("Odczytano 5 wierszy").assertExists()
    }
}
