package com.mazesys.zerobite.screens.scan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.mazesys.zerobite.coordination.ScanCoordinator
import com.mazesys.zerobite.core.scanning.LabelScanner
import com.mazesys.zerobite.testing.FakeLabels
import com.mazesys.zerobite.testing.FakeProductLookup
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The add-food screens in Polish: copy, plural line counts, dates and the quoted source line follow the app language. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pl-w411dp-h891dp-xxhdpi")
class ScanScreensPolishTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the sources read in polish`() {
        val vm = ScanFlowViewModel(ScanCoordinator {}, supportsLiveScanning = true, scanner = FakeLabels(), clock = TestClock.fixed())
        compose.setContent { ZeroBiteTheme { ScanFlowScreen(vm) } }

        compose.onNode(hasText("Dodaj produkt") and isHeading()).assertIsDisplayed()
        compose.onNodeWithText("Skanuj aparatem").assertIsDisplayed()
        compose.onNodeWithText("Wybierz zdjęcie etykiety").assertIsDisplayed()
        compose.onNodeWithText("Wpisz ręcznie").assertIsDisplayed()
    }

    @Test
    fun `the review reads in polish`() {
        val result = LabelScanner.analyse(listOf("SEMI SKIMMED MILK", "USE BY 12 SEP 2026"), null, TestClock.today)
        val vm = ScanReviewViewModel(result, FakeProductLookup(), TestClock.fixed(), onContinue = {})
        compose.setContent { ZeroBiteTheme { ScanReviewScreen(vm, onBack = {}) } }

        compose.onNode(hasText("Sprawdź dane") and isHeading()).assertIsDisplayed()
        compose.onNodeWithText("12 września 2026").assertExists()
        compose.onNodeWithText("„USE BY 12 SEP 2026”").assertExists()
        compose.onNodeWithText("Pokaż 2 wiersze").performScrollTo().performClick()
        compose.onNodeWithText("Ukryj 2 wiersze").assertExists()
    }
}
