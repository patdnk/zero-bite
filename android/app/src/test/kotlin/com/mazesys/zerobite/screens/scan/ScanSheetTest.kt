package com.mazesys.zerobite.screens.scan

import android.net.Uri
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
import com.mazesys.zerobite.core.model.ScanResult
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeLabels
import com.mazesys.zerobite.testing.MainDispatcherRule
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
class ScanSheetTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val inventory = FakeInventoryRepository()
    private val labels = FakeLabels()
    private var finished = 0
    private var flow: ScanFlowViewModel? = null

    private fun show(supportsLive: Boolean = true) {
        val deps = testDependencies(inventory = inventory, labels = FakeLabels(supportsLiveScanning = supportsLive).also {
            it.result = labels.result
            it.failure = labels.failure
        })
        val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        compose.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                ZeroBiteTheme {
                    ScanSheet(deps, onFinished = { finished++ }) { vm -> flow = vm; Text("Live scanner") }
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
        compose.waitUntil(3_000) { finished == 1 }
        assertEquals(listOf("Yoghurt"), inventory.current.map { it.name })
    }

    @Test
    fun `a scanned photo lands on review, and back returns to the sources`() {
        labels.result = ScanResult(lines = listOf("SEMI SKIMMED MILK"))
        show()
        compose.onNodeWithTag("scan.live").performClick()
        compose.onNodeWithText("Live scanner").assertIsDisplayed()
        compose.runOnUiThread { flow!!.coordinator.pop() }
        compose.runOnUiThread { flow!!.process(Uri.parse("content://media/picker/0/1")) }
        compose.waitForIdle()
        compose.onNode(hasText("Check the details") and isHeading()).assertIsDisplayed()

        compose.runOnUiThread { flow!!.coordinator.pop() }
        compose.waitForIdle()
        compose.onNodeWithTag("scan.photo").assertIsDisplayed()
        compose.onNode(hasText("Check the details") and isHeading()).assertDoesNotExist()
    }

    @Test
    fun `a failed photo shows the error`() {
        show()
        compose.onNodeWithTag("scan.live").performClick()
        compose.onNodeWithText("Live scanner").assertIsDisplayed()
        compose.runOnUiThread { flow!!.coordinator.pop() }
        compose.runOnUiThread { flow!!.process(Uri.parse("content://media/picker/0/1")) }
        compose.onNodeWithText("Couldn't read that").assertIsDisplayed()
        compose.onNodeWithText("No text or barcode was found. Try a sharper, closer photo of the label.").assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Couldn't read that").assertDoesNotExist()
    }
}
