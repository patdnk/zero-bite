package com.mazesys.zerobite.screens.scan

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import com.mazesys.zerobite.core.model.ItemDraft
import com.mazesys.zerobite.core.scanning.LabelScanner
import com.mazesys.zerobite.testing.FakeProductLookup
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ScanReviewScreenTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private var continued: ItemDraft? = null

    private fun show(lines: List<String>, barcode: String? = "5000119000000") {
        val result = LabelScanner.analyse(lines, barcode, TestClock.today)
        val vm = ScanReviewViewModel(result, FakeProductLookup(), TestClock.fixed(), onContinue = { continued = it })
        compose.setContent { ZeroBiteTheme { ScanReviewScreen(vm, onBack = {}) } }
    }

    @Test
    fun `lists the dates found and continues with the chosen one`() {
        show(listOf("TESCO", "SEMI SKIMMED MILK", "USE BY 12 SEP 2026", "PACKED 01/09/2026"))
        compose.onNodeWithText("12 September 2026").assertExists()
        compose.onNodeWithText("Pick the best-before or use-by date. Most likely first.").assertExists()
        compose.onNodeWithText("Barcode").assertExists()
        compose.onNodeWithTag("review.continue").performClick()
        assertEquals(LocalDate(2026, 9, 12), continued?.expiresOn)
    }

    @Test
    fun `no date says so`() {
        show(listOf("SEMI SKIMMED MILK"), barcode = null)
        compose.onNodeWithText("No date was found on the label. You can set one on the next screen.").assertExists()
    }

    @Test
    fun `continue is disabled without a name`() {
        show(listOf("SEMI SKIMMED MILK", "USE BY 12 SEP 2026"))
        compose.onNodeWithTag("review.name").performTextClearance()
        compose.onNodeWithTag("review.continue").assertIsNotEnabled()
    }

    @Test
    fun `the read text can be shown`() {
        show(listOf("SEMI SKIMMED MILK", "USE BY 12 SEP 2026"))
        compose.onNodeWithText("Show 2 lines").performScrollTo().performClick()
        compose.onNodeWithText("Hide 2 lines").assertExists()
        compose.onNodeWithTag("review.continue").assertIsEnabled()
    }
}
