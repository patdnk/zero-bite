package com.mazesys.zerobite.screens.scan

import android.net.Uri
import com.mazesys.zerobite.scanning.UnreadablePhoto
import java.io.IOException
import com.mazesys.zerobite.coordination.MainCoordinator
import com.mazesys.zerobite.coordination.ScanCoordinator
import com.mazesys.zerobite.coordination.SheetDestination
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.mazesys.zerobite.core.model.DateCandidate
import com.mazesys.zerobite.core.model.FoodCategory
import com.mazesys.zerobite.core.model.ProductInfo
import com.mazesys.zerobite.core.model.ScanResult
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeLabels
import com.mazesys.zerobite.testing.FakeProductLookup
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.testDependencies
import com.mazesys.zerobite.testing.testMain
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.datetime.LocalDate
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ScanSheetTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val inventory = FakeInventoryRepository()
    private val labels = FakeLabels()
    private val productLookup = FakeProductLookup()
    private lateinit var tabs: MainCoordinator
    private val scan: ScanCoordinator get() = tabs.scan!!
    private val finished: Boolean get() = tabs.state.sheet == null
    private val photo = Uri.parse("content://media/picker/0/1")

    private fun show(supportsLive: Boolean = true) {
        val deps = testDependencies(inventory = inventory, productLookup = productLookup, labels = FakeLabels(supportsLiveScanning = supportsLive).also {
            it.result = labels.result
            it.failure = labels.failure
        })
        tabs = testMain(deps)
        tabs.eventLoop(MainCoordinator.Event.DidRequestSheet(SheetDestination.Scan))
        val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        compose.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                ZeroBiteTheme {
                    ScanSheet(scan, deps) { Text("Live scanner") }
                }
            }
        }
    }

    @Test
    fun `offers the three sources`() {
        show()
        compose.onNode(hasText("Add food") and isHeading()).assertIsDisplayed()
        compose.onNodeWithTag("scan.live").assertIsDisplayed()
        compose.onNodeWithTag("scan.photo").assertIsDisplayed()
        compose.onNodeWithTag("scan.manual").assertIsDisplayed()
        compose.onNodeWithText("Point at the best-before or use-by date. A barcode helps fill in the product name.").assertExists()
    }

    @Test
    fun `no camera hides the live option`() {
        show(supportsLive = false)
        compose.onNodeWithTag("scan.live").assertDoesNotExist()
    }

    @Test
    fun `typing it in saves through the form and closes the sheet`() {
        show()
        compose.onNodeWithTag("scan.manual").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("itemForm.name").performTextInput("Yoghurt")
        compose.onNodeWithTag("itemForm.save").performClick()
        compose.waitUntil(3_000) { finished }
        assertEquals(listOf("Yoghurt"), inventory.current.map { it.name })
    }

    @Test
    fun `a scanned photo lands on review, and back returns to the sources`() {
        labels.result = ScanResult(lines = listOf("SEMI SKIMMED MILK"))
        show()
        compose.onNodeWithTag("scan.live").performClick()
        compose.onNodeWithText("Live scanner").assertIsDisplayed()
        compose.runOnUiThread { scan.eventLoop(ScanCoordinator.Event.DidTapBack) }
        compose.runOnUiThread { scan.eventLoop(ScanCoordinator.Event.DidChoosePhoto(photo, TestClock.today)) }
        compose.waitForIdle()
        compose.onNode(hasText("Check the details") and isHeading()).assertIsDisplayed()

        compose.runOnUiThread { scan.eventLoop(ScanCoordinator.Event.DidTapBack) }
        compose.waitForIdle()
        compose.onNodeWithTag("scan.photo").assertIsDisplayed()
        compose.onNode(hasText("Check the details") and isHeading()).assertDoesNotExist()
    }

    @Test
    fun `an unreadable photo says it isn't a photo`() = showsPhotoError(UnreadablePhoto(), "That file doesn't look like a photo.")

    @Test
    fun `a photo that won't load says so`() = showsPhotoError(IOException("boom"), "Couldn't load that photo.")

    private fun showsPhotoError(failure: Exception, message: String) {
        labels.failure = failure
        show()
        compose.runOnUiThread { scan.eventLoop(ScanCoordinator.Event.DidChoosePhoto(photo, TestClock.today)) }
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText(message).assertDoesNotExist()
    }

    @Test
    fun `a failed photo shows the error`() {
        show()
        compose.onNodeWithTag("scan.live").performClick()
        compose.onNodeWithText("Live scanner").assertIsDisplayed()
        compose.runOnUiThread { scan.eventLoop(ScanCoordinator.Event.DidTapBack) }
        compose.runOnUiThread { scan.eventLoop(ScanCoordinator.Event.DidChoosePhoto(photo, TestClock.today)) }
        compose.onNodeWithText("Couldn't read that").assertIsDisplayed()
        compose.onNodeWithText("No text or barcode was found. Try a sharper, closer photo of the label.").assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Couldn't read that").assertDoesNotExist()
    }

    @Test
    fun `a scanned photo carries the barcode, brand, name and chosen expiry into the saved item`() {
        labels.result = ScanResult(
            lines = listOf("GREEK YOGHURT", "USE BY 14 SEP 26", "BEST BEFORE 20 SEP 26"),
            barcode = "5000119000000",
            candidates = listOf(
                DateCandidate(LocalDate(2026, 9, 14), "14 SEP 26", "USE BY 14 SEP 26", 0.9),
                DateCandidate(LocalDate(2026, 9, 20), "20 SEP 26", "BEST BEFORE 20 SEP 26", 0.6),
            ),
            suggestedName = "Greek yoghurt",
        )
        productLookup.info = ProductInfo("5000119000000", "Natural Greek Yoghurt", "Acme Dairy", FoodCategory.DAIRY)
        show()
        compose.onNodeWithTag("scan.live").performClick()
        compose.onNodeWithText("Live scanner").assertIsDisplayed()
        compose.runOnUiThread { scan.eventLoop(ScanCoordinator.Event.DidTapBack) }
        compose.runOnUiThread { scan.eventLoop(ScanCoordinator.Event.DidChoosePhoto(photo, TestClock.today)) }
        compose.waitForIdle()
        compose.onNode(hasText("Check the details") and isHeading()).assertIsDisplayed()
        assertEquals(listOf("5000119000000"), productLookup.asked)

        compose.onNodeWithText("20 September 2026").performClick()
        compose.onNodeWithTag("review.continue").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("itemForm.save").performClick()
        compose.waitUntil(3_000) { finished }

        val saved = inventory.current.single()
        assertEquals("Natural Greek Yoghurt", saved.name)
        assertEquals("Acme Dairy", saved.brand)
        assertEquals("5000119000000", saved.barcode)
        assertEquals(FoodCategory.DAIRY, saved.category)
        assertEquals(LocalDate(2026, 9, 20), saved.expiresOn)
    }
}
