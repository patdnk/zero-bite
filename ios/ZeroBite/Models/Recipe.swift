//
//  Recipe.swift
//  ZeroBite
//
//  Bundled recipe catalogue model (Resources/recipes.json). Display text may come from a
//  translation overlay (see `RecipeStore`); `Ingredient.name`, `tags` and `id` always stay the
//  English canonical keys used by matching.
//

import Foundation

struct Recipe: Codable, Identifiable, Hashable, Sendable {

    struct Ingredient: Codable, Hashable, Identifiable, Sendable {
        /// English canonical name, used for matching against fridge items.
        var name: String
        var amount: String
        var optional: Bool
        /// Shown after the amount in the ingredient list ("3 jajka"). Defaults to `name`.
        var label: String
        /// Used in lists of missing ingredients ("Brakuje: jajek, sera."). Defaults to `name`.
        var missingLabel: String

        var id: String { "\(name)|\(amount)" }

        init(name: String, amount: String = "", optional: Bool = false, label: String? = nil, missingLabel: String? = nil) {
            self.name = name
            self.amount = amount
            self.optional = optional
            self.label = label ?? name
            self.missingLabel = missingLabel ?? name
        }

        private enum CodingKeys: String, CodingKey { case name, amount, optional }

        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            name = try container.decode(String.self, forKey: .name)
            amount = try container.decodeIfPresent(String.self, forKey: .amount) ?? ""
            optional = try container.decodeIfPresent(Bool.self, forKey: .optional) ?? false
            label = name
            missingLabel = name
        }
    }

    let id: String
    let title: String
    let summary: String
    let ingredients: [Ingredient]
    let steps: [String]
    let prepMinutes: Int
    let servings: Int
    /// Canonical tag keys from recipes.json ("no-cook").
    let tags: [String]
    /// Display names for `tags`, same order. `nil` shows the keys themselves.
    var tagLabels: [String]? = nil
    /// The English title when `title` is a translation; drives the placeholder artwork.
    var canonicalTitle: String? = nil

    private enum CodingKeys: String, CodingKey {
        case id, title, summary, ingredients, steps, prepMinutes, servings, tags
    }

    var requiredIngredients: [Ingredient] { ingredients.filter { !$0.optional } }

    var displayTags: [String] {
        guard let tagLabels, tagLabels.count == tags.count else { return tags }
        return tagLabels
    }
}
