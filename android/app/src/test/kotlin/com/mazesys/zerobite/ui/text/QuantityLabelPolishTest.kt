package com.mazesys.zerobite.ui.text

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.testing.TestClock
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** Quantities in Polish: the unit agrees with the number, and decimals use a comma. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pl")
class QuantityLabelPolishTest {

    @get:Rule
    val compose = createComposeRule()

    private fun item(quantity: Double, unit: String) =
        FoodItem(id = "$quantity$unit", name = "Ser", quantity = quantity, unit = unit, addedAt = TestClock.now, expiresOn = TestClock.today)

    @Test
    fun `slices agree with the number`() {
        compose.setContent {
            Column {
                listOf(1.0, 2.0, 5.0, 1.5, 22.0).forEach { Text(item(it, "slices").quantityLabel()) }
                Text(item(3.0, "pcs").quantityLabel())
            }
        }
        compose.onNodeWithText("1 plaster").assertIsDisplayed()
        compose.onNodeWithText("2 plastry").assertIsDisplayed()
        compose.onNodeWithText("5 plastrów").assertIsDisplayed()
        compose.onNodeWithText("1,5 plastra").assertIsDisplayed()
        compose.onNodeWithText("22 plastry").assertIsDisplayed()
        compose.onNodeWithText("3 szt.").assertIsDisplayed()
    }

    @Test
    fun `other count nouns agree with the number too`() {
        val expected = mapOf(
            "pints" to listOf("1 pinta", "2 pinty", "5 pint", "1,5 pinty"),
            "loaf" to listOf("1 bochenek", "2 bochenki", "5 bochenków", "1,5 bochenka"),
            "bulb" to listOf("1 główka", "2 główki", "5 główek", "1,5 główki"),
            "bag" to listOf("1 opak.", "2 opak.", "5 opak.", "1,5 opak."),
        )
        val quantities = listOf(1.0, 2.0, 5.0, 1.5)
        compose.setContent {
            Column { expected.keys.forEach { unit -> quantities.forEach { Text(item(it, unit).quantityLabel()) } } }
        }
        expected.values.flatten().forEach { compose.onNodeWithText(it).assertExists() }
    }

    @Test
    fun `typed unit labels map back to their codes`() {
        lateinit var codes: Map<String, String>
        compose.setContent { codes = unitCodes() }
        compose.waitForIdle()
        assertEquals("pcs", codes["szt."])
        assertEquals("pack", codes["opak."])
        assertEquals("slices", codes["plastry"])
        assertEquals("g", codes["g"])
    }
}
