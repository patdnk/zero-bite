package com.mazesys.zerobite.core.recipes

import com.mazesys.zerobite.core.Fixtures
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.RecipeCatalogue
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Ranking the catalogue compares every ingredient with every item, so its helpers must stay cheap. */
class RecipeMatcherCostTest {

    private val today = LocalDate(2026, 9, 7)

    /** The Polish sample food, the fridge a new user tries the app with. */
    private val fridge = listOf(
        "Jogurt grecki", "Szpinak", "Pierś z kurczaka", "Mleko 2%", "Pieczarki", "Chleb na zakwasie", "Pomidorki koktajlowe",
        "Czerwona papryka", "Boczek", "Cytryna", "Banany", "Marchew", "Cebula", "Jajka", "Ser cheddar", "Ziemniaki", "Czosnek",
        "Masło", "Filety z łososia", "Ryż basmati", "Spaghetti",
    ).mapIndexed { i, name ->
        FoodItem(id = "$i", name = name, addedAt = Instant.parse("2026-09-07T08:00:00Z"), expiresOn = today.plus(i, DateTimeUnit.DAY))
    }

    @Test
    fun `ranking the catalogue against a full fridge stays under 25 MB of allocations`() {
        val recipes = RecipeCatalogue.decode(Fixtures.sharedAsset("recipes.json"))
        val aliases = IngredientAliases.decode(Fixtures.sharedAsset("ingredient-aliases.pl.json"))
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

        val before = threads.currentThreadAllocatedBytes
        val matches = RecipeMatcher(today, aliases = aliases).match(recipes, fridge)
        val megabytes = (threads.currentThreadAllocatedBytes - before) / (1024 * 1024)

        assertEquals(recipes.size, matches.size)
        assertTrue(megabytes < 25, "ranking allocated $megabytes MB")
    }

    @Test
    fun `containsWords agrees with padding both strings in spaces`() {
        val names = listOf(
            "onion", "onions", "red onion", "onion ring", "spring onion soup", "onionring", "ring onion", "onion onionring", "onionring onion",
            "egg", "eggs", "egg noodle", "eggy egg", "noodle egg", "a", "ab", "abc", "abc abc", "xabc abc", "abcx abc", "abcx xabc", "",
            "abc  abc", "  abc", "abc  ", "   ", "abc\uD83C\uDF73 abc", "\uD83C\uDF73abc", "abc \uD83C\uDF73", "\uD83C\uDF73\uD83C\uDF73",
        )
        for (needle in names) for (haystack in names) {
            val expected = needle.length >= 3 && " $haystack ".contains(" $needle ")
            assertEquals(expected, RecipeMatcher.containsWords(needle, haystack), "'$needle' in '$haystack'")
        }
    }
}
