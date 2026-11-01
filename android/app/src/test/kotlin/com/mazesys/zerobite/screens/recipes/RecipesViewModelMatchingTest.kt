package com.mazesys.zerobite.screens.recipes

import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.core.model.Recipe
import com.mazesys.zerobite.core.recipes.RecipeMatch
import com.mazesys.zerobite.core.recipes.RecipeRepository
import com.mazesys.zerobite.core.time.AppClock
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeRecipes
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.recipeFridge
import com.mazesys.zerobite.testing.testRecipes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Ranking the catalogue against the fridge is far too expensive to redo per keystroke, and has to
 * stay off the main thread. A [RecipeMatch] only gets a new identity when matching runs again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecipesViewModelMatchingTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val app = AppCoordinator { TestClock.today }
    private val inventory = FakeInventoryRepository(recipeFridge())
    private val clock = object : AppClock {
        var instant: Instant = TestClock.now
        override val timeZone: TimeZone = TestClock.zone
        override fun now() = instant
    }

    private fun viewModel(
        recipes: RecipeRepository = FakeRecipes(testRecipes()),
        computation: CoroutineDispatcher = Dispatchers.Unconfined,
    ) = RecipesViewModel(inventory, FakeSettings(), recipes, clock, app.recipes, computation).apply { setScope(RecipeScope.ALL) }

    private fun RecipesViewModel.match(id: String): RecipeMatch = state.value.displayed.first { it.recipe.id == id }

    @Test
    fun `typing and changing scope reuse the matches`() {
        val vm = viewModel()
        val before = vm.match("stirfry")

        for (text in listOf("p", "pe", "pep", "pepper")) vm.setSearchText(text)
        assertSame(before, vm.match("stirfry"))

        vm.setScope(RecipeScope.NEARLY)
        assertSame(before, vm.match("stirfry"))
    }

    @Test
    fun `a fridge change matches again`() = runTest {
        val vm = viewModel()
        val before = vm.match("stirfry")

        inventory.delete("chicken")

        assertNotSame(before, vm.match("stirfry"))
        assertEquals(before.missing.size + 1, vm.match("stirfry").missing.size)
    }

    @Test
    fun `a new day matches again at the next keystroke`() {
        val vm = viewModel()
        val before = vm.match("stirfry")

        clock.instant += 1.days
        assertSame(before, vm.match("stirfry"))
        vm.setSearchText("c")

        assertNotSame(before, vm.match("stirfry"))
        assertEquals(clock.today(), vm.state.value.today)
    }

    @Test
    fun `a language change matches again and the search stays`() {
        val catalogue = MutableStateFlow(testRecipes())
        val vm = viewModel(object : RecipeRepository {
            override suspend fun recipes() = catalogue.value
            override val catalogue: Flow<List<Recipe>> = catalogue
        })
        vm.setSearchText("chicken")
        assertEquals("Chicken stir-fry", vm.match("stirfry").recipe.title)

        catalogue.value = testRecipes().map { if (it.id == "stirfry") it.copy(title = "Chicken po polsku") else it }

        assertEquals("Chicken po polsku", vm.match("stirfry").recipe.title)
        assertEquals("chicken", vm.state.value.searchText)
    }

    @Test
    fun `matching runs on its dispatcher and the search text does not wait for it`() {
        val computation = StandardTestDispatcher()
        val vm = viewModel(computation = computation)
        assertTrue(vm.state.value.isLoading)

        vm.setSearchText("pepper")
        assertEquals("pepper", vm.state.value.searchText)
        assertTrue(vm.state.value.isLoading)

        computation.scheduler.advanceUntilIdle()
        assertFalse(vm.state.value.isLoading)
        assertEquals(listOf("stirfry"), vm.state.value.displayed.map { it.recipe.id })
        assertEquals("pepper", vm.state.value.searchText)
    }
}
