package com.mazesys.zerobite.screens.inventory

import com.mazesys.zerobite.core.model.ExpiryStatus
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeRecipes
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.recipeFridge
import com.mazesys.zerobite.testing.testRecipes
import com.mazesys.zerobite.testing.sampleFridge
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ItemDetailViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val inventory = FakeInventoryRepository(sampleFridge())
    private val milk = inventory.current.first { it.name == "Semi-skimmed milk" }
    private var edited: String? = null
    private var deleted = 0

    private fun viewModel(id: String = milk.id) =
        ItemDetailViewModel(id, inventory, FakeSettings(), FakeRecipes(), TestClock.fixed(), onEdit = { edited = it }, onShowRecipe = {}, onDeleted = { deleted++ })

    @Test
    fun `active item shows its urgency`() {
        val state = viewModel().state.value
        assertEquals(DetailState.ACTIVE, state.state)
        assertEquals(ExpiryStatus.SOON, state.status)
        assertEquals(TestClock.today, state.addedOn)
    }

    @Test
    fun `used up and put back`() {
        val vm = viewModel()
        vm.markUsed()
        assertEquals(DetailState.USED, vm.state.value.state)
        assertEquals(TestClock.now, vm.state.value.item?.consumedAt)
        vm.restore()
        assertEquals(DetailState.ACTIVE, vm.state.value.state)
    }

    @Test
    fun `delete removes the item and leaves the screen`() {
        val vm = viewModel()
        vm.delete()
        assertEquals(DetailState.MISSING, vm.state.value.state)
        assertEquals(1, deleted)
    }

    @Test
    fun `missing item shows removed state`() = runBlocking {
        val vm = viewModel()
        inventory.delete(milk.id) // e.g. deleted from the Fridge grid's menu meanwhile
        assertEquals(DetailState.MISSING, vm.state.value.state)
        assertEquals(DetailState.MISSING, viewModel("no-such-id").state.value.state)
    }

    @Test
    fun `edit asks the coordinator`() {
        viewModel().edit()
        assertEquals(milk.id, edited)
    }

    @Test
    fun `recipes using the item are listed`() {
        val fridge = FakeInventoryRepository(recipeFridge())
        val vm = ItemDetailViewModel(
            "eggs", fridge, FakeSettings(), FakeRecipes(testRecipes()), TestClock.fixed(),
            onEdit = {}, onShowRecipe = {}, onDeleted = {},
        )
        assertEquals(listOf("omelette", "pancakes"), vm.state.value.recipeMatches.map { it.recipe.id })
    }
}
