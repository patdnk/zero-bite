//
//  CountUnit.swift
//  ZeroBite
//
//  Some layouts draw a count large and its unit small ("3" + "days"). The unit's form depends
//  on the count (Polish: 1 dzień, 2 dni, 5 porcji), so the catalog holds the whole phrase with
//  plural variations and this strips the number back out to leave the unit.
//

import Foundation

enum CountUnit {
    /// The unit part of a localized "<count> <unit>" phrase, e.g. "days" from "3 days".
    static func unit(in phrase: String, count: Int) -> String {
        guard let range = phrase.range(of: "\(count)") else { return phrase }
        var unit = phrase
        unit.removeSubrange(range)
        return unit.trimmingCharacters(in: .whitespaces)
    }
}
