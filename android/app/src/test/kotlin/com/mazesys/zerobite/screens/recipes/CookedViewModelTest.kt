package com.mazesys.zerobite.screens.recipes

import com.mazesys.zerobite.testing.english
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeRecipes
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.recipeFridge
import com.mazesys.zerobite.testing.testRecipes
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class CookedViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val inventory = FakeInventoryRepository(recipeFridge())
    private var done = 0
    private fun vm() = CookedViewModel("omelette", inventory, FakeSettings(), FakeRecipes(testRecipes()), TestClock.fixed(), onDone = { done++ })

    @Test
    fun `every used item starts ticked`() {
        val state = vm().state.value
        assertEquals("Omelette", state.recipeTitle)
        assertEquals(listOf("eggs", "butter"), state.usableItems.map { it.id })
        assertEquals(setOf("eggs", "butter"), state.selectedIds)
        assertEquals("Update fridge", english(state.confirmTitle))
    }

    @Test
    fun `confirm marks the ticked items used`() {
        val model = vm()
        model.toggle(model.state.value.usableItems.first { it.id == "butter" })
        model.confirm()
        assertEquals(TestClock.now, inventory.current.first { it.id == "eggs" }.consumedAt)
        assertNull(inventory.current.first { it.id == "butter" }.consumedAt)
        assertEquals(1, done)
    }

    @Test
    fun `nothing ticked just closes`() {
        val model = vm()
        model.state.value.usableItems.forEach(model::toggle)
        assertEquals("Done", english(model.state.value.confirmTitle))
        model.confirm()
        assertEquals(emptyList(), inventory.current.filter { it.isConsumed })
        assertEquals(1, done)
    }

    @Test
    fun `items used up meanwhile are skipped`() = runBlocking {
        val model = vm()
        val earlier = Instant.parse("2026-09-06T10:00:00Z")
        inventory.markConsumed(listOf("eggs"), earlier) // e.g. "Used it" on the Fridge tab meanwhile
        model.confirm()
        assertEquals(earlier, inventory.current.first { it.id == "eggs" }.consumedAt)
        assertEquals(TestClock.now, inventory.current.first { it.id == "butter" }.consumedAt)
        assertEquals(1, done)
    }
}
