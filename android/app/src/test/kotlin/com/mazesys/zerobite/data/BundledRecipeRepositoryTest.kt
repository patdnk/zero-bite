package com.mazesys.zerobite.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mazesys.zerobite.core.recipes.RecipeMatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class BundledRecipeRepositoryTest {

    private val language = MutableStateFlow("en")
    private val repository = BundledRecipeRepository(ApplicationProvider.getApplicationContext<Context>().assets, language)

    @Test
    fun `loads the bundled catalogue once`() = runBlocking {
        val first = repository.recipes()
        assertTrue(first.size >= 30)
        assertSame(first, repository.recipes())
    }

    @Test
    fun `english catalogue is shown as bundled`() = runBlocking {
        val omelette = repository.recipes().first { it.id == "classic-omelette" }
        assertEquals("Classic cheese omelette", omelette.title)
        assertEquals(listOf("breakfast", "quick", "vegetarian"), omelette.tagLabels)
        assertEquals("eggs", omelette.ingredients.first().label)
    }

    @Test
    fun `polish app language shows the polish overlay`() = runBlocking {
        language.value = "pl"
        val omelette = repository.recipes().first { it.id == "classic-omelette" }
        assertEquals("Klasyczny omlet z serem", omelette.title)
        assertEquals("Classic cheese omelette", omelette.englishTitle)
        assertEquals(listOf("śniadanie", "szybkie", "wegetariańskie"), omelette.tagLabels)
        val eggs = omelette.ingredients.first()
        assertEquals(listOf("eggs", "3", "jajka", "jajek"), listOf(eggs.name, eggs.amount, eggs.label, eggs.missingLabel))
    }

    @Test
    fun `catalogue follows app language changes`() = runBlocking {
        assertEquals("Classic cheese omelette", repository.catalogue.first().first { it.id == "classic-omelette" }.title)
        language.value = "pl"
        assertEquals("Klasyczny omlet z serem", repository.catalogue.first().first { it.id == "classic-omelette" }.title)
        language.value = "de" // no overlay: English
        assertEquals("Classic cheese omelette", repository.catalogue.first().first { it.id == "classic-omelette" }.title)
    }

    @Test
    fun `polish ingredient aliases are bundled and always on`() = runBlocking {
        val aliases = repository.ingredientAliases()
        assertSame(aliases, repository.ingredientAliases())
        assertTrue(RecipeMatcher.matches("eggs", "Jajka", aliases))
        assertTrue(RecipeMatcher.matches("chicken", "Pierś z kurczaka", aliases))
    }
}
