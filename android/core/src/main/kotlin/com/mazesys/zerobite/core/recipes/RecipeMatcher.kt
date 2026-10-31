package com.mazesys.zerobite.core.recipes

import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.Recipe
import com.mazesys.zerobite.core.text.TextFolding
import kotlinx.datetime.LocalDate

data class MatchedIngredient(
    val ingredient: Recipe.Ingredient,
    /** The fridge item satisfying this ingredient; null means it's a store-cupboard staple. */
    val item: FoodItem?,
) {
    val isStaple: Boolean get() = item == null
}

data class RecipeMatch(
    val recipe: Recipe,
    val matched: List<MatchedIngredient>,
    val missing: List<Recipe.Ingredient>,
    val missingOptional: List<Recipe.Ingredient>,
    val expiringItems: List<FoodItem>,
    val score: Double,
) {
    val id: String get() = recipe.id
    val canMake: Boolean get() = missing.isEmpty()
    val usedItems: List<FoodItem> get() = matched.mapNotNull { it.item }.distinctBy { it.id }
    val requiredCount: Int get() = recipe.requiredIngredients.size
    val requiredMatchedCount: Int get() = matched.count { !it.ingredient.optional }

    fun uses(itemId: String): Boolean = matched.any { it.item?.id == itemId }
}

/**
 * Scores recipes against what's in the fridge, favouring recipes that use up food about to
 * expire. Port of the iOS RecipeMatcher — keep them in step via testdata/recipe-matching.json
 * and testdata/recipe-matching-pl.json.
 */
class RecipeMatcher(
    private val today: LocalDate,
    private val soonThresholdDays: Int = 3,
    /** Other-language item names that match an ingredient ("Jajka" for eggs). */
    private val aliases: IngredientAliases = IngredientAliases.NONE,
) {

    fun match(recipes: List<Recipe>, inventory: List<FoodItem>): List<RecipeMatch> {
        val available = inventory.filterNot { it.isConsumed }
        return recipes
            .map { match(it, available) }
            .sortedWith(compareByDescending<RecipeMatch> { it.score }.thenBy { it.recipe.title })
    }

    fun match(recipe: Recipe, inventory: List<FoodItem>): RecipeMatch {
        val available = inventory.filterNot { it.isConsumed }
        val matched = mutableListOf<MatchedIngredient>()
        val missing = mutableListOf<Recipe.Ingredient>()
        val missingOptional = mutableListOf<Recipe.Ingredient>()

        for (ingredient in recipe.ingredients) {
            val item = bestItem(ingredient.name, available)
            when {
                item != null -> matched += MatchedIngredient(ingredient, item)
                isStaple(ingredient.name) -> matched += MatchedIngredient(ingredient, null)
                ingredient.optional -> missingOptional += ingredient
                else -> missing += ingredient
            }
        }

        val required = recipe.requiredIngredients.size
        val requiredMatched = matched.count { !it.ingredient.optional }
        val coverage = if (required == 0) 1.0 else requiredMatched.toDouble() / required

        val expiring = matched
            .mapNotNull { it.item }
            .filter { it.status(today, soonThresholdDays).needsAttention }
            .distinctBy { it.id }

        val realMatches = matched.count { it.item != null }
        var score = coverage * 100
        score += expiring.size * 15
        score -= missing.size * 5
        if (missing.isEmpty()) score += 20
        if (realMatches == 0) score -= 100 // only staples: not really "makeable from the fridge"

        return RecipeMatch(recipe, matched, missing, missingOptional, expiring, score)
    }

    /** Prefers the item that expires soonest when several satisfy the ingredient. */
    fun bestItem(ingredient: String, items: List<FoodItem>): FoodItem? =
        items.filter { matches(ingredient, it.name, aliases) }.minByOrNull { it.expiresOn }

    companion object {

        /** Assumed to always be in the cupboard; never counted as "missing". */
        val STAPLES: Set<String> = listOf(
            "salt", "black pepper", "olive oil", "vegetable oil", "oil", "water", "sugar", "flour",
            "plain flour", "vinegar", "soy sauce", "stock", "stock cube", "chilli flakes", "dried herbs",
        ).map(::normalize).toSet()

        /** Names treated as interchangeable when matching. */
        val SYNONYM_GROUPS: List<List<String>> = listOf(
            listOf("cheese", "cheddar", "mozzarella", "parmesan", "feta", "gouda", "brie", "halloumi", "gruyere", "emmental"),
            listOf("chicken", "chicken breast", "chicken thigh"),
            listOf("yogurt", "yoghurt", "greek yogurt", "natural yogurt", "skyr"),
            listOf("courgette", "zucchini"),
            listOf("aubergine", "eggplant"),
            listOf("coriander", "cilantro"),
            listOf("spring onion", "scallion", "green onion"),
            listOf("rocket", "arugula"),
            listOf("mince", "minced beef", "beef mince", "ground beef", "beef"),
            listOf("pasta", "spaghetti", "penne", "fusilli", "tagliatelle", "linguine", "macaroni", "rigatoni"),
            listOf("rice", "basmati", "jasmine rice", "long grain rice"),
            listOf("bread", "sourdough", "baguette", "ciabatta", "roll", "bap", "pitta", "pita", "loaf"),
            listOf("cream", "double cream", "single cream", "creme fraiche"),
            listOf("bacon", "pancetta", "lardons"),
            listOf("bell pepper", "red pepper", "green pepper", "yellow pepper", "capsicum"),
            listOf("tortilla", "wrap", "flatbread"),
            listOf("noodle", "egg noodle", "rice noodle", "ramen"),
            listOf("berry", "strawberry", "blueberry", "raspberry", "blackberry", "mixed berries"),
            listOf("prawn", "shrimp"),
            listOf("lettuce", "salad leaves", "mixed leaves", "romaine", "iceberg", "little gem"),
            listOf("tuna", "tinned tuna"),
            listOf("banana"),
        ).map { group -> group.map(::normalize) }

        /**
         * True when a fridge item can stand in for a recipe ingredient: the same words, one
         * containing the other, the same synonym group, or the item containing an alias of
         * the ingredient or of any term in its synonym group.
         */
        fun matches(ingredient: String, itemName: String, aliases: IngredientAliases = IngredientAliases.NONE): Boolean {
            val a = normalize(ingredient)
            val b = normalize(itemName)
            if (a.isEmpty() || b.isEmpty()) return false
            if (a == b) return true
            if (containsWords(a, b) || containsWords(b, a)) return true
            val ga = synonymGroup(a)
            val gb = synonymGroup(b)
            if (ga != null && ga == gb) return true
            return aliasMatches(a, b, aliases)
        }

        /**
         * Free-text recipe search: the title, an ingredient's text, or another-language name of an
         * ingredient, so "mleko" finds a recipe whose list says "mleka". [query] is trimmed and
         * lowercased. Port of the iOS `recipe(_:matchesQuery:aliases:)`.
         */
        fun recipeMatchesQuery(recipe: Recipe, query: String, aliases: IngredientAliases = IngredientAliases.NONE): Boolean {
            if (recipe.title.lowercase().contains(query)) return true
            val normalizedQuery = normalize(query)
            return recipe.ingredients.any { ingredient ->
                ingredient.label.lowercase().contains(query) ||
                    ingredient.name.lowercase().contains(query) ||
                    aliasMatches(normalize(ingredient.name), normalizedQuery, aliases)
            }
        }

        /** Whether the item contains, as whole words, an alias of the ingredient or of any term in its synonym group. */
        private fun aliasMatches(normalizedIngredient: String, normalizedItem: String, aliases: IngredientAliases): Boolean {
            if (aliases.isEmpty) return false
            val group = synonymGroup(normalizedIngredient)
            val terms = if (group == null) listOf(normalizedIngredient) else listOf(normalizedIngredient) + SYNONYM_GROUPS[group]
            return terms.any { term -> aliases.of(term).any { containsWords(it, normalizedItem) } }
        }

        fun isStaple(name: String): Boolean {
            val normalized = normalize(name)
            return normalized in STAPLES || STAPLES.any { containsWords(it, normalized) }
        }

        /** Lowercase, strip accents and punctuation, singularise each word. */
        fun normalize(text: String): String {
            val folded = TextFolding.foldDiacritics(text).lowercase()
            val cleaned = buildString(folded.length) {
                for (c in folded) append(if (c.isLetter() || c == ' ') c else ' ')
            }
            return cleaned.split(' ').filter { it.isNotEmpty() }.joinToString(" ") { singularize(it) }
        }

        fun singularize(word: String): String {
            if (word.length <= 3) return word
            if (word.endsWith("ies")) return word.dropLast(3) + "y"
            if (word.endsWith("oes")) return word.dropLast(2)
            if (word.endsWith("ss") || word.endsWith("us") || word.endsWith("is")) return word
            if (word.endsWith("ches") || word.endsWith("shes") || word.endsWith("xes") || word.endsWith("ses")) {
                return word.dropLast(2)
            }
            if (word.endsWith("s")) return word.dropLast(1)
            return word
        }

        /** Whole-word phrase containment: "onion" is in "spring onion" but not in "onionring". */
        fun containsWords(needle: String, haystack: String): Boolean =
            needle.length >= 3 && " $haystack ".contains(" $needle ")

        private fun synonymGroup(normalizedName: String): Int? =
            SYNONYM_GROUPS.indexOfFirst { group ->
                group.any { it == normalizedName || containsWords(it, normalizedName) }
            }.takeIf { it >= 0 }
    }
}
