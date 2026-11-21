package com.mazesys.zerobite.screens.recipes

import com.mazesys.zerobite.core.model.Recipe
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeRecipes
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.recipeFridge
import com.mazesys.zerobite.testing.testRecipes
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecipeDetailViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private var shownItem: String? = null
    private var cooked: String? = null
    private val stock = Recipe("stock", "Stock", "", listOf(Recipe.Ingredient("salt"), Recipe.Ingredient("water")), listOf("Boil"), 5, 1, emptyList())

    private fun vm(id: String) = RecipeDetailViewModel(
        id, FakeInventoryRepository(recipeFridge()), FakeSettings(), FakeRecipes(testRecipes() + stock), TestClock.fixed(),
        onShowItem = { shownItem = it }, onCooked = { cooked = it },
    )

    @Test
    fun `matches the recipe against the fridge`() {
        val state = vm("omelette").state.value
        assertEquals("Omelette", state.recipe?.title)
        assertTrue(state.canMarkCooked)
        val eggs = state.recipe!!.ingredients.first { it.name == "eggs" }
        assertEquals("eggs", state.matched(eggs)?.item?.id)
        assertTrue(state.matched(state.recipe.ingredients.first { it.name == "salt" })!!.isStaple)
    }

    @Test
    fun `nothing to mark keeps the button disabled`() {
        assertFalse(vm("stock").state.value.canMarkCooked)
    }

    @Test
    fun `unknown recipe is not found`() {
        val state = vm("nope").state.value
        assertFalse(state.isLoading)
        assertNull(state.recipe)
    }

    @Test
    fun `taps go to the item and the cooked sheet`() {
        val model = vm("omelette")
        model.showItem(model.state.value.match!!.usedItems.first())
        model.cooked()
        assertEquals("eggs", shownItem)
        assertEquals("omelette", cooked)
    }
}
