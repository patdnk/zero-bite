package com.mazesys.zerobite.core.recipes

import kotlinx.serialization.json.Json

/**
 * Names in other languages that stand for an English ingredient term
 * (shared-assets/ingredient-aliases.pl.json): "egg" → "jajko", "jajka", … Always active,
 * whatever the UI language, since people type item names in their own language.
 */
class IngredientAliases(aliases: Map<String, List<String>>) {

    /** Normalized term → its normalized aliases. */
    private val byTerm: Map<String, List<String>> = buildMap<String, MutableList<String>> {
        for ((term, names) in aliases) {
            val key = RecipeMatcher.normalize(term)
            if (key.isEmpty()) continue
            val list = getOrPut(key) { mutableListOf() }
            names.map(RecipeMatcher::normalize).filter { it.isNotEmpty() && it !in list }.forEach(list::add)
        }
    }

    val isEmpty: Boolean get() = byTerm.isEmpty()

    /** The aliases of one normalized term. */
    fun of(normalizedTerm: String): List<String> = byTerm[normalizedTerm].orEmpty()

    companion object {
        val NONE = IngredientAliases(emptyMap())

        private val json = Json { ignoreUnknownKeys = true }

        fun decode(text: String): IngredientAliases = IngredientAliases(json.decodeFromString<Map<String, List<String>>>(text))
    }
}
