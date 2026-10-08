package com.mazesys.zerobite.screens.inventory

import com.mazesys.zerobite.core.model.FoodCategory
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.ItemDraft
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.SequentialIds
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.sampleFridge
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ItemFormViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val inventory = FakeInventoryRepository(sampleFridge())
    private val saved = mutableListOf<FoodItem>()

    private fun add() = ItemFormViewModel(ItemFormMode.Add, ItemDraft.new(TestClock.today), inventory, TestClock.fixed(), SequentialIds("new"), saved::add)
    private fun edit(id: String) = ItemFormViewModel(ItemFormMode.Edit(id), null, inventory, TestClock.fixed(), SequentialIds("new"), saved::add)

    @Test
    fun `a new item needs a name`() {
        val vm = add()
        assertFalse(vm.state.value.canSave)
        assertEquals("New item", vm.state.value.title)
        vm.updateDraft { it.copy(name = "  ") }
        assertFalse(vm.state.value.canSave)
        vm.updateDraft { it.copy(name = "Oat milk") }
        assertTrue(vm.state.value.canSave)
    }

    @Test
    fun `adding saves a trimmed item`() {
        val vm = add()
        vm.updateDraft { it.copy(name = "  Oat milk ", category = FoodCategory.DRINKS) }
        vm.save()
        val item = saved.single()
        assertEquals("new-1", item.id)
        assertEquals("Oat milk", item.name)
        assertEquals(TestClock.now, item.addedAt)
        assertEquals(LocalDate(2026, 9, 14), item.expiresOn)
        assertEquals(item, inventory.current.first { it.id == "new-1" })
    }

    @Test
    fun `editing loads the item and keeps its identity`() {
        val milk = inventory.current.first { it.name == "Semi-skimmed milk" }
        val vm = edit(milk.id)
        assertTrue(vm.state.value.isLoaded)
        assertEquals("Edit item", vm.state.value.title)
        assertEquals("Semi-skimmed milk", vm.state.value.draft.name)
        assertEquals("2", vm.state.value.quantityText)
        vm.updateDraft { it.copy(name = "Whole milk") }
        vm.save()
        val updated = saved.single()
        assertEquals(milk.id, updated.id)
        assertEquals(milk.addedAt, updated.addedAt)
        assertEquals("Whole milk", inventory.current.first { it.id == milk.id }.name)
    }

    @Test
    fun `editing an item that no longer exists explains why`() = runBlocking {
        assertEquals("This item no longer exists.", edit("gone").state.value.errorMessage)

        val milk = inventory.current.first { it.name == "Semi-skimmed milk" }
        val vm = edit(milk.id)
        inventory.delete(milk.id)
        vm.save()
        assertEquals("This item no longer exists.", vm.state.value.errorMessage)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `typical shelf life comes from the category`() {
        val vm = add()
        vm.updateDraft { it.copy(category = FoodCategory.FRUIT) }
        vm.applyDefaultShelfLife()
        assertEquals(LocalDate(2026, 9, 13), vm.state.value.draft.expiresOn)
        assertEquals(6, vm.state.value.defaultShelfLifeDays)
    }

    @Test
    fun `quantity text is parsed leniently and validated`() {
        val vm = add()
        vm.updateDraft { it.copy(name = "Rice") }
        vm.setQuantityText("abc")
        assertFalse(vm.state.value.canSave)
        vm.setQuantityText("0")
        assertFalse(vm.state.value.canSave)
        vm.setQuantityText("1,5")
        assertEquals(1.5, vm.state.value.draft.quantity)
        assertTrue(vm.state.value.canSave)
        assertEquals("1,5", vm.state.value.quantityText, "keeps what was typed")
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun `float syntax, tiny and huge quantities are rejected`() {
        val vm = add()
        vm.updateDraft { it.copy(name = "Rice") }
        for (bad in listOf("Infinity", "1e30", "1.5f", "0.001", "0.00", "1,000", "1.2.3", "10000000")) {
            vm.setQuantityText(bad)
            assertFalse(vm.state.value.canSave, "\"$bad\" must not be saveable")
        }
        for ((good, value) in listOf("2" to 2.0, "0.25" to 0.25, "1,5" to 1.5, " 500 " to 500.0)) {
            vm.setQuantityText(good)
            assertTrue(vm.state.value.canSave, "\"$good\" should be saveable")
            assertEquals(value, vm.state.value.draft.quantity)
        }
    }
}
