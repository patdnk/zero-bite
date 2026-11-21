package com.mazesys.zerobite.screens.scan

import com.mazesys.zerobite.core.model.FoodCategory
import com.mazesys.zerobite.core.model.ItemDraft
import com.mazesys.zerobite.core.model.ProductInfo
import com.mazesys.zerobite.core.model.ScanResult
import com.mazesys.zerobite.core.scanning.LabelScanner
import com.mazesys.zerobite.testing.FakeProductLookup
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScanReviewViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val label = LabelScanner.analyse(
        lines = listOf("TESCO", "SEMI SKIMMED MILK", "USE BY 12 SEP 2026", "PACKED 01/09/2026"),
        barcode = "5000119000000",
        today = TestClock.today,
    )
    private val lookup = FakeProductLookup()
    private var continued: ItemDraft? = null

    private fun review(result: ScanResult = label) =
        ScanReviewViewModel(result, lookup, TestClock.fixed(), onContinue = { continued = it })

    @Test
    fun `starts from the guess and the most likely date`() {
        val vm = review()
        assertEquals(label.suggestedName ?: "", vm.state.value.name)
        assertEquals(0, vm.state.value.selectedIndex)
        assertEquals(LocalDate(2026, 9, 12), vm.state.value.selectedCandidate?.date)
    }

    @Test
    fun `a catalogue name replaces the guess and sets the category`() {
        lookup.info = ProductInfo("5000119000000", "Semi Skimmed Milk", "Tesco", FoodCategory.DAIRY)
        val vm = review()
        assertEquals("Semi Skimmed Milk", vm.state.value.name)
        assertEquals(FoodCategory.DAIRY, vm.state.value.category)
        assertEquals("Tesco", vm.state.value.product?.brand)
        assertFalse(vm.state.value.isLookingUp)
        assertEquals(listOf("5000119000000"), lookup.asked)
    }

    @Test
    fun `a typed name survives the lookup`() {
        lookup.hold = true
        val vm = review()
        vm.setName("My milk")
        assertTrue(vm.state.value.isLookingUp)
        lookup.info = ProductInfo("5000119000000", "Semi Skimmed Milk", "Tesco", FoodCategory.DAIRY)
        lookup.release()
        assertEquals("My milk", vm.state.value.name)
        assertEquals(FoodCategory.DAIRY, vm.state.value.category)
    }

    @Test
    fun `a failed lookup keeps the scanned name`() {
        lookup.failure = IOException("offline")
        val vm = review()
        assertEquals(label.suggestedName ?: "", vm.state.value.name)
        assertNull(vm.state.value.product)
        assertFalse(vm.state.value.isLookingUp)
    }

    @Test
    fun `a lookup that finds nothing keeps the scanned name`() {
        val vm = review()
        assertEquals(label.suggestedName ?: "", vm.state.value.name)
        assertNull(vm.state.value.product)
        assertFalse(vm.state.value.isLookingUp)
    }

    @Test
    fun `no barcode means no lookup`() {
        review(label.copy(barcode = null))
        assertTrue(lookup.asked.isEmpty())
    }

    @Test
    fun `draft uses the chosen date, or the category's shelf life with none`() {
        val vm = review()
        vm.setName("  Milk  ")
        vm.setCategory(FoodCategory.DAIRY)
        val chosen = vm.makeDraft()
        assertEquals("Milk", chosen.name)
        assertEquals(LocalDate(2026, 9, 12), chosen.expiresOn)
        assertEquals("5000119000000", chosen.barcode)

        vm.clearSelection()
        assertEquals(TestClock.today.plus(FoodCategory.DAIRY.defaultShelfLifeDays, DateTimeUnit.DAY), vm.makeDraft().expiresOn)
    }

    @Test
    fun `continue needs a name`() {
        val vm = review()
        vm.setName("   ")
        assertFalse(vm.state.value.canContinue)
        vm.continueToForm()
        assertNull(continued)
        vm.setName("Milk")
        vm.continueToForm()
        assertEquals("Milk", continued?.name)
    }
}
