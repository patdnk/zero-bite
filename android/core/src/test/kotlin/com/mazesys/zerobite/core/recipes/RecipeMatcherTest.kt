package com.mazesys.zerobite.core.recipes

import com.mazesys.zerobite.core.Fixtures
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.Recipe
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@Serializable
private data class RecipeFixture(
    val today: LocalDate,
    val soonThresholdDays: Int,
    val nameMatches: List<NameMatch>,
    val staples: List<Staple>,
    val recipes: List<Recipe>,
    val cases: List<Case>,
) {
    @Serializable data class NameMatch(val ingredient: String, val item: String, val matches: Boolean)
    @Serializable data class Staple(val name: String, val isStaple: Boolean)
    @Serializable data class Item(val id: String, val name: String, val expiresInDays: Int, val consumed: Boolean = false)
    @Serializable data class Expect(
        val canMake: Boolean? = null,
        val missing: List<String>? = null,
        val missingOptional: List<String>? = null,
        val staples: List<String>? = null,
        val usedItemIds: List<String>? = null,
        val expiringItemIds: List<String>? = null,
        val ingredientItems: Map<String, String>? = null,
    )
    @Serializable data class Case(
        val name: String,
        val inventory: List<Item>,
        val recipes: List<String>,
        val ranking: List<String>? = null,
        val matches: Map<String, Expect> = emptyMap(),
    )
}

class RecipeMatcherTest {

    private val fixture = Fixtures.load<RecipeFixture>("recipe-matching.json")
    private val addedAt = Instant.parse("2026-09-01T09:00:00Z")

    @TestFactory
    fun `golden name matches`() = fixture.nameMatches.map { m ->
        DynamicTest.dynamicTest("${m.ingredient} ~ ${m.item}") {
            assertEquals(m.matches, RecipeMatcher.matches(m.ingredient, m.item))
        }
    }

    @TestFactory
    fun `golden staples`() = fixture.staples.map { s ->
        DynamicTest.dynamicTest(s.name) { assertEquals(s.isStaple, RecipeMatcher.isStaple(s.name)) }
    }

    @TestFactory
    fun `golden matching cases`() = fixture.cases.map { case ->
        DynamicTest.dynamicTest(case.name) {
            val inventory = case.inventory.map {
                FoodItem(
                    id = it.id,
                    name = it.name,
                    addedAt = addedAt,
                    expiresOn = fixture.today.plus(it.expiresInDays, DateTimeUnit.DAY),
                    consumedAt = if (it.consumed) addedAt else null,
                )
            }
            val recipes = case.recipes.map { id -> fixture.recipes.first { it.id == id } }
            val ranked = RecipeMatcher(fixture.today, fixture.soonThresholdDays).match(recipes, inventory)

            case.ranking?.let { assertEquals(it, ranked.map { m -> m.recipe.id }, "ranking") }
            for ((recipeId, expect) in case.matches) {
                val match = ranked.first { it.recipe.id == recipeId }
                expect.canMake?.let { assertEquals(it, match.canMake, "canMake") }
                expect.missing?.let { assertEquals(it, match.missing.map { i -> i.name }, "missing") }
                expect.missingOptional?.let { assertEquals(it, match.missingOptional.map { i -> i.name }, "missingOptional") }
                expect.staples?.let { assertEquals(it, match.matched.filter { m -> m.isStaple }.map { m -> m.ingredient.name }, "staples") }
                expect.usedItemIds?.let { assertEquals(it, match.usedItems.map { i -> i.id }, "usedItems") }
                expect.expiringItemIds?.let { assertEquals(it, match.expiringItems.map { i -> i.id }, "expiringItems") }
                expect.ingredientItems?.forEach { (ingredient, itemId) ->
                    assertEquals(itemId, match.matched.first { it.ingredient.name == ingredient }.item?.id, ingredient)
                }
            }
        }
    }

    @Test
    fun `normalize folds, strips punctuation and singularises`() {
        assertEquals("creme fraiche", RecipeMatcher.normalize("Crème-fraîche"))
        assertEquals("cherry tomato", RecipeMatcher.normalize("Cherry tomatoes"))
        assertEquals("berry", RecipeMatcher.normalize("Berries"))
        assertEquals("hummus", RecipeMatcher.normalize("Hummus"))
        assertEquals("peach", RecipeMatcher.normalize("peaches"))
    }

    @Test
    fun `recipe with only staples in the fridge ranks last`() {
        val stock = Recipe("stock", "Stock", "", listOf(Recipe.Ingredient("salt"), Recipe.Ingredient("water")), listOf("Boil"), 5, 1, emptyList())
        val match = RecipeMatcher(fixture.today).match(stock, emptyList())
        assertEquals(true, match.canMake)
        assertEquals(20.0, match.score) // 100 coverage + 20 can-make − 100 nothing from the fridge
    }
}
