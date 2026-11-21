//
//  RecipeStore.swift
//  ZeroBite
//
//  Loads the bundled recipe catalogue (recipes.json) and, for a translated UI, applies the
//  language's overlay (recipes.<language>.json) and tag names (recipe-tags.json).
//

import Foundation

enum RecipeStore {

    /// Languages with a recipe overlay; anything else shows the English source.
    static let translatedLanguages: Set<String> = ["pl"]

    /// The catalogue language for the app's current localization: "pl" or "en".
    static func language(bundle: Bundle = .main) -> String {
        let preferred = bundle.preferredLocalizations.first ?? "en"
        return translatedLanguages.contains(preferred) ? preferred : "en"
    }

    static func loadBundled(bundle: Bundle = .main, language: String? = nil) -> [Recipe] {
        let language = language ?? self.language(bundle: bundle)
        guard let recipes = decode([Recipe].self, resource: "recipes", bundle: bundle) else { return [] }
        let tagNames = decode([String: [String: String]].self, resource: "recipe-tags", bundle: bundle)?[language] ?? [:]
        let overlay = translatedLanguages.contains(language)
            ? decode([String: RecipeTranslation].self, resource: "recipes.\(language)", bundle: bundle) ?? [:]
            : [:]
        return recipes.map { localize($0, translation: overlay[$0.id], tagNames: tagNames) }
    }

    /// Applies a translation (title, summary, steps, ingredient labels and amounts) and tag display
    /// names. Anything the translation leaves out keeps its English text.
    static func localize(_ recipe: Recipe, translation: RecipeTranslation?, tagNames: [String: String]) -> Recipe {
        let ingredients = recipe.ingredients.map { ingredient -> Recipe.Ingredient in
            guard let text = translation?.ingredients[ingredient.name] else { return ingredient }
            return Recipe.Ingredient(
                name: ingredient.name,
                amount: text.amount,
                optional: ingredient.optional,
                label: text.name,
                missingLabel: text.missing
            )
        }
        let steps = translation.map(\.steps).flatMap { $0.count == recipe.steps.count ? $0 : nil } ?? recipe.steps
        var localized = Recipe(
            id: recipe.id,
            title: translation?.title ?? recipe.title,
            summary: translation?.summary ?? recipe.summary,
            ingredients: ingredients,
            steps: steps,
            prepMinutes: recipe.prepMinutes,
            servings: recipe.servings,
            tags: recipe.tags
        )
        localized.tagLabels = tagNames.isEmpty ? nil : recipe.tags.map { tagNames[$0] ?? $0 }
        localized.canonicalTitle = recipe.canonicalTitle ?? recipe.title
        return localized
    }

    private static func decode<T: Decodable>(_ type: T.Type, resource: String, bundle: Bundle) -> T? {
        guard let url = bundle.url(forResource: resource, withExtension: "json"),
              let data = try? Data(contentsOf: url)
        else { return nil }
        return try? JSONDecoder().decode(type, from: data)
    }
}

/// One recipe's entry in recipes.<language>.json, keyed there by recipe id.
struct RecipeTranslation: Decodable, Sendable {
    struct Ingredient: Decodable, Sendable {
        /// Label shown after the amount, agreeing with it ("3 jajka").
        let name: String
        let amount: String
        /// Genitive form for "missing" lists ("jajek").
        let missing: String
    }

    let title: String
    let summary: String
    let steps: [String]
    /// Keyed by the English ingredient `name` from recipes.json.
    let ingredients: [String: Ingredient]
}
