package com.mazesys.zerobite.data

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.mazesys.zerobite.R
import com.mazesys.zerobite.core.model.Recipe
import com.mazesys.zerobite.core.recipes.RecipeMatcher
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.ui.text.Strings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** "Try it with sample food" in Polish finds the same recipes as in English. */
@RunWith(RobolectricTestRunner::class)
class SampleDataMatchingTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun inLanguage(tag: String): Context =
        context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })

    private val sampleNames: List<Int> = R.string::class.java.fields.filter { it.name.startsWith("sample_") }.map { it.getInt(null) }

    @Test
    fun `polish sample food matches the ingredients its english name does`() = runBlocking {
        val repository = BundledRecipeRepository(context.assets, MutableStateFlow("en"))
        val ingredients = repository.recipes().flatMap(Recipe::ingredients).map { it.name }.distinct()
        val aliases = repository.ingredientAliases()
        val english = inLanguage("en-GB")
        val polish = inLanguage("pl")
        assertTrue(sampleNames.size >= 20)
        val differences = sampleNames.mapNotNull { id ->
            val en = english.getString(id)
            val pl = polish.getString(id)
            val enMatches = ingredients.filter { RecipeMatcher.matches(it, en, aliases) && it !in ENGLISH_ONLY[en].orEmpty() }
            val plMatches = ingredients.filter { RecipeMatcher.matches(it, pl, aliases) }
            "$en $enMatches / $pl $plMatches".takeIf { enMatches != plMatches }
        }
        assertEquals(emptyList(), differences)
    }

    @Test
    fun `polish sample fridge ranks recipes like the english one`() = runBlocking {
        val repository = BundledRecipeRepository(context.assets, MutableStateFlow("en"))
        val recipes = repository.recipes()
        val matcher = RecipeMatcher(TestClock.today, aliases = repository.ingredientAliases())
        fun summary(tag: String): Map<String, String> {
            val strings = inLanguage(tag)
            var id = 0
            val fridge = SampleData.items(TestClock.today, TestClock.now, { "item-${id++}" }, Strings { strings.getString(it) })
            return matcher.match(recipes, fridge).associate { it.id to "${it.score}|${it.missing.map { i -> i.name }}" }
        }
        assertEquals(summary("en-GB"), summary("pl"))
    }

    private companion object {
        /**
         * English-only matches where the item name sits inside a longer ingredient name ("Onion"
         * in "spring onion"). Aliases only match one way, so "Cebula" rightly does not count as
         * dymka. The same exception as the iOS test.
         */
        val ENGLISH_ONLY = mapOf("Onion" to setOf("spring onion"))
    }
}
