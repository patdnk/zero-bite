package com.mazesys.zerobite.screens.recipes

import com.mazesys.zerobite.testing.english
import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.coordination.RecipesRoute
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
import kotlin.test.assertTrue

class RecipesViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val app = AppCoordinator { TestClock.today }
    private val vm by lazy { RecipesViewModel(FakeInventoryRepository(recipeFridge()), FakeSettings(), FakeRecipes(testRecipes()), TestClock.fixed(), app.recipes) }
    private fun ids() = vm.state.value.displayed.map { it.recipe.id }

    @Test
    fun `ready shows only what can be made now`() {
        assertEquals(listOf("omelette"), ids())
        assertEquals("You can make these now", english(vm.state.value.scope.sectionTitle))
    }

    @Test
    fun `nearly shows recipes missing one or two things`() {
        vm.setScope(RecipeScope.NEARLY)
        assertEquals(setOf("stirfry", "pancakes"), ids().toSet())
    }

    @Test
    fun `all shows the whole catalogue ranked`() {
        vm.setScope(RecipeScope.ALL)
        assertEquals(4, ids().size)
        assertEquals("omelette", ids().first())
    }

    @Test
    fun `search matches titles and ingredients and hides the strip`() {
        assertTrue(vm.state.value.showsExpiringStrip)
        assertEquals(listOf("Eggs"), vm.state.value.expiringItems.map { it.name })
        vm.setScope(RecipeScope.ALL)
        vm.setSearchText("pepper")
        assertEquals(listOf("stirfry"), ids())
        assertFalse(vm.state.value.showsExpiringStrip)
        vm.setSearchText("PANCAKE")
        assertEquals(listOf("pancakes"), ids())
    }

    @Test
    fun `empty states explain themselves`() {
        vm.setSearchText("caviar")
        assertEquals(emptyList(), ids())
        assertEquals("Nothing to cook yet", english(vm.state.value.emptyTitle))
        assertEquals("Try a different search.", english(vm.state.value.emptyMessage))
        val empty = RecipesViewModel(FakeInventoryRepository(), FakeSettings(), FakeRecipes(), TestClock.fixed(), app.recipes)
        empty.setScope(RecipeScope.NEARLY)
        assertEquals("No near-misses", english(empty.state.value.emptyTitle))
        assertEquals(
            "Everything is either ready to cook or needs more than a couple of ingredients.",
            english(empty.state.value.emptyMessage),
        )
    }

    @Test
    fun `taps route through the coordinator`() {
        vm.showRecipe(vm.state.value.displayed.first())
        assertEquals(RecipesRoute.RecipeDetail("omelette"), app.recipes.backStack.last())
        vm.showItem(vm.state.value.expiringItems.first())
        assertEquals(RecipesRoute.ItemDetail("eggs"), app.recipes.backStack.last())
        app.recipes.popToRoot()
        assertEquals(listOf<RecipesRoute>(RecipesRoute.Catalogue), app.recipes.backStack.toList())
    }
}
