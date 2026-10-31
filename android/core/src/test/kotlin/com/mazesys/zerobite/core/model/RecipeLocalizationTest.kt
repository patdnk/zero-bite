package com.mazesys.zerobite.core.model

import com.mazesys.zerobite.core.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** shared-assets/recipes.pl.json and recipe-tags.json applied to the English catalogue. */
class RecipeLocalizationTest {

    private val english = RecipeCatalogue.decode(Fixtures.sharedAsset("recipes.json"))
    private val polish = RecipeCatalogue.decodeTranslations(Fixtures.sharedAsset("recipes.pl.json"))
    private val tags = RecipeCatalogue.decodeTagNames(Fixtures.sharedAsset("recipe-tags.json"))

    @Test
    fun `english display fields default to the catalogue text`() {
        val omelette = english.first { it.id == "classic-omelette" }
        assertEquals(omelette.title, omelette.englishTitle)
        assertEquals(omelette.tags, omelette.tagLabels)
        val eggs = omelette.ingredients.first { it.name == "eggs" }
        assertEquals("eggs", eggs.label)
        assertEquals("eggs", eggs.missingLabel)
        // English tag names are what the apps showed before they existed.
        assertEquals(english, RecipeCatalogue.localize(english, emptyMap(), tags.getValue("en")))
    }

    @Test
    fun `polish overlay translates every recipe and keeps english keys`() {
        val localized = RecipeCatalogue.localize(english, polish, tags.getValue("pl"))
        for ((en, pl) in english.zip(localized)) {
            val t = polish.getValue(en.id)
            assertEquals(en.id, pl.id)
            assertEquals(t.title, pl.title)
            assertEquals(en.title, pl.englishTitle)
            assertEquals(t.summary, pl.summary)
            assertEquals(t.steps, pl.steps)
            assertEquals(en.tags, pl.tags)
            assertEquals(en.tags.map { tags.getValue("pl").getValue(it) }, pl.tagLabels)
            assertEquals(en.ingredients.map { it.name }, pl.ingredients.map { it.name }, en.id)
            assertEquals(en.ingredients.map { it.optional }, pl.ingredients.map { it.optional }, en.id)
            for (ingredient in pl.ingredients) {
                val i = t.ingredients.getValue(ingredient.name)
                assertEquals(i.name, ingredient.label)
                assertEquals(i.amount, ingredient.amount)
                assertEquals(i.missing, ingredient.missingLabel)
            }
        }
    }

    @Test
    fun `the omelette reads in polish`() {
        val omelette = RecipeCatalogue.localize(english, polish, tags.getValue("pl")).first { it.id == "classic-omelette" }
        val eggs = omelette.ingredients.first { it.name == "eggs" }
        assertEquals("3 jajka", "${eggs.amount} ${eggs.label}")
        assertEquals("jajek", eggs.missingLabel)
        assertTrue(omelette.tagLabels.all { it in tags.getValue("pl").values })
    }

    @Test
    fun `missing translations fall back to english`() {
        val recipe = english.first()
        val partial = RecipeTranslation(
            title = "Tytuł", summary = "Opis", steps = listOf("za mało kroków") + recipe.steps, ingredients = emptyMap(),
        )
        val localized = RecipeCatalogue.localize(listOf(recipe), mapOf(recipe.id to partial), emptyMap()).single()
        assertEquals("Tytuł", localized.title)
        assertEquals(recipe.steps, localized.steps)
        assertEquals(recipe.ingredients, localized.ingredients)
        assertEquals(recipe.tags, localized.tagLabels)
    }
}
