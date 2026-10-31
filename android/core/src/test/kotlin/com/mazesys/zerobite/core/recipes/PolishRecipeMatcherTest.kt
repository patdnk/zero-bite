package com.mazesys.zerobite.core.recipes

import com.mazesys.zerobite.core.Fixtures
import com.mazesys.zerobite.core.model.FoodItem
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@Serializable
private data class PolishMatchFixture(val nameMatches: List<NameMatch>) {
    @Serializable data class NameMatch(val ingredient: String, val item: String, val matches: Boolean)
}

/** Polish item names matching English ingredients through shared-assets/ingredient-aliases.pl.json. */
class PolishRecipeMatcherTest {

    private val fixture = Fixtures.load<PolishMatchFixture>("recipe-matching-pl.json")
    private val aliases = IngredientAliases.decode(Fixtures.sharedAsset("ingredient-aliases.pl.json"))

    @TestFactory
    fun `golden polish name matches`() = fixture.nameMatches.map { m ->
        DynamicTest.dynamicTest("${m.ingredient} ~ ${m.item}") {
            assertEquals(m.matches, RecipeMatcher.matches(m.ingredient, m.item, aliases))
        }
    }

    @Test
    fun `aliases only apply when passed in`() {
        assertFalse(RecipeMatcher.matches("eggs", "Jajka"))
        assertTrue(RecipeMatcher.matches("eggs", "Jajka", aliases))
    }

    @Test
    fun `the matcher picks fridge items through its aliases`() {
        val today = LocalDate(2026, 9, 7)
        val eggs = FoodItem(id = "eggs", name = "Jajka z wolnego wybiegu", addedAt = Instant.parse("2026-09-01T09:00:00Z"), expiresOn = today)
        assertEquals(eggs, RecipeMatcher(today, aliases = aliases).bestItem("eggs", listOf(eggs)))
        assertNull(RecipeMatcher(today).bestItem("eggs", listOf(eggs)))
    }
}
