//
//  RecallMatcher.swift
//  ZeroBite
//
//  Decides which food alerts might concern items in the fridge. FSA notices carry product
//  names, businesses, pack sizes and free-text batch dates but rarely a barcode, so matching
//  is deliberately cautious: results are "likely" or "possible", never certain, and the UI
//  always asks the user to check the pack.
//

import Foundation

struct RecallMatch: Identifiable, Hashable, Sendable {

    enum Confidence: Int, Comparable, Sendable {
        case possible
        case likely

        static func < (lhs: Confidence, rhs: Confidence) -> Bool { lhs.rawValue < rhs.rawValue }
    }

    enum Reason: String, Hashable, Sendable {
        /// The item's barcode appears on the notice.
        case barcode
        /// The item's name matches a recalled product.
        case name
        /// The item's brand (or a brand word in its name) matches the business or product.
        case brand
        /// The item's date matches a recalled batch date.
        case date
        /// The notice covers every batch of the product.
        case allBatches
    }

    let alert: FoodAlert
    let product: FoodAlert.Product
    let itemID: UUID
    let itemName: String
    let confidence: Confidence
    let reasons: Set<Reason>

    var id: String { Self.id(alertID: alert.id, itemID: itemID) }

    static func id(alertID: String, itemID: UUID) -> String { "\(alertID)|\(itemID.uuidString)" }
}

struct RecallMatcher: Sendable {

    /// The language of a notice's text. FSA notices are English, GIS notices Polish.
    enum Language: Sendable { case english, polish }

    var now: Date = .now
    var calendar: Calendar = .current
    /// Older notices are ignored: products from them have long left the shelves.
    var lookbackDays = 120

    func matches(alerts: [FoodAlert], items: [FoodItem], dismissed: Set<String> = []) -> [RecallMatch] {
        let cutoff = calendar.date(byAdding: .day, value: -lookbackDays, to: now) ?? now
        let recent = alerts.filter { $0.created >= cutoff }
        var results: [RecallMatch] = []
        for item in items where !item.isConsumed {
            // Keep only the best-matching product per alert for each item.
            var best: [String: RecallMatch] = [:]
            for alert in recent {
                for product in alert.products {
                    guard let match = match(alert: alert, product: product, item: item),
                          !dismissed.contains(match.id)
                    else { continue }
                    if let existing = best[alert.id], existing.confidence >= match.confidence { continue }
                    best[alert.id] = match
                }
            }
            results.append(contentsOf: best.values)
        }
        return results.sorted {
            if $0.confidence != $1.confidence { return $0.confidence > $1.confidence }
            return $0.alert.created > $1.alert.created
        }
    }

    func match(alert: FoodAlert, product: FoodAlert.Product, item: FoodItem) -> RecallMatch? {
        var reasons = Set<RecallMatch.Reason>()
        let language: Language = alert.source == .gis ? .polish : .english

        if let barcode = item.barcode?.filter(\.isNumber), barcode.count >= 8, Self.barcodes(on: product).contains(barcode) {
            reasons.insert(.barcode)
        }

        // Item, brand and notice are all folded the same way, so "Bonitki" on both stems alike.
        let productFolded = Self.foldedWords(product.name, language: language)
        let productWords = Self.words(product.name, language: language)
        var businessWords = Set(alert.businesses.flatMap { Self.words($0, language: language) }).subtracting(Self.genericBusinessWords)
        // Polish producers are named for what they make ("Mlecz Ser Górskie"), so a word the
        // product itself carries says nothing about the brand and must not hide the item's name.
        if language == .polish { businessWords.subtract(productWords) }
        let brandWords = Set(Self.words(item.brand ?? "", language: language))
        let itemWords = Self.words(item.name, language: language).filter { !businessWords.contains($0) && !brandWords.contains($0) }

        if Self.nameMatches(itemWords: itemWords, productWords: productWords, language: language, productFoldedWords: productFolded) {
            reasons.insert(.name)
        }

        let allItemWords = Set(Self.words(item.name, language: language)).union(brandWords)
        if !businessWords.isDisjoint(with: allItemWords) || (!brandWords.isEmpty && !brandWords.isDisjoint(with: productWords)) {
            reasons.insert(.brand)
        }

        let specs = product.batches.flatMap { AlertDateParser.dates(in: [$0.useBy, $0.bestBefore].compactMap { $0 }.joined(separator: " ")) }
        if product.batches.contains(where: \.coversAllBatches) {
            reasons.insert(.allBatches)
        } else if specs.contains(where: { $0.matches(item.expiresAt, calendar: calendar) }) {
            reasons.insert(.date)
        }
        let dateContradicts = !specs.isEmpty && !reasons.contains(.date) && !reasons.contains(.allBatches)

        let confidence: RecallMatch.Confidence
        if reasons.contains(.barcode) {
            confidence = .likely
        } else if reasons.contains(.name) && reasons.contains(.brand) {
            confidence = (reasons.contains(.date) || reasons.contains(.allBatches)) ? .likely : .possible
        } else if reasons.contains(.name) && !dateContradicts {
            confidence = .possible
        } else {
            return nil
        }

        return RecallMatch(alert: alert, product: product, itemID: item.id, itemName: item.name,
                           confidence: confidence, reasons: reasons)
    }

    // MARK: - Name matching

    /// A one-word item ("Brie", "Ham") must be the product's head noun, so "Milk" doesn't match
    /// "Pistachio Milk Chocolate". Longer names need every word to appear in the product name.
    ///
    /// Polish puts the noun first ("Ser regionalny Gałka"), so a one-word item must be the
    /// product's first word, or its second when the first is an adjective ("Zielone pesto").
    /// That adjective check needs the unstemmed words (`productFoldedWords`, from `foldedWords`).
    static func nameMatches(itemWords: [String], productWords: [String], language: Language = .english, productFoldedWords: [String] = []) -> Bool {
        guard !itemWords.isEmpty else { return false }
        if itemWords.count > 1 {
            let product = Set(productWords)
            return itemWords.allSatisfy(product.contains)
        }
        switch language {
        case .english:
            return itemWords[0] == productWords.last
        case .polish:
            guard let first = productWords.first else { return false }
            if itemWords[0] == first { return true }
            let startsWithAdjective = productFoldedWords.first.map { word in polishAdjectiveEndings.contains { word.hasSuffix($0) } } ?? false
            return startsWithAdjective && productWords.count > 1 && itemWords[0] == productWords[1]
        }
    }

    /// Lowercased, accent-folded, stemmed content words, in order: singular for English,
    /// suffix-stripped for Polish.
    static func words(_ text: String, language: Language = .english) -> [String] {
        let folded = foldedWords(text, language: language)
        switch language {
        case .english: return folded.map(singular).map { synonyms[$0] ?? $0 }
        case .polish: return folded.map(polishStem)
        }
    }

    /// The content words before stemming: lowercased, accent-folded, without stop words or
    /// numbers. `words` is these, stemmed, at the same positions.
    static func foldedWords(_ text: String, language: Language = .english) -> [String] {
        let lowered: String
        let stops: Set<String>
        switch language {
        case .english:
            lowered = text
            stops = stopWords
        case .polish:
            // ł has no accent to fold, so map it by hand first.
            lowered = text.lowercased().replacingOccurrences(of: "ł", with: "l")
            stops = polishStopWords
        }
        let folded = lowered.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: Locale(identifier: "en_GB"))
        return folded
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty && !stops.contains($0) && $0.rangeOfCharacter(from: .decimalDigits) == nil }
    }

    private static func singular(_ word: String) -> String {
        guard word.count > 3 else { return word }
        if word.hasSuffix("ies") { return String(word.dropLast(3)) + "y" }
        if word.hasSuffix("oes") { return String(word.dropLast(2)) }
        if word.hasSuffix("s") && !word.hasSuffix("ss") { return String(word.dropLast()) }
        return word
    }

    /// Drops the longest case or plural ending that still leaves three letters, so "sery" and
    /// "ser", or "Bonitki" on a pack and on a notice, meet at the same stem.
    private static func polishStem(_ word: String) -> String {
        guard let suffix = polishSuffixes.first(where: { word.hasSuffix($0) && word.count - $0.count >= 3 }) else { return word }
        return String(word.dropLast(suffix.count))
    }

    /// Long digit runs on the notice (product codes and barcode-style batch codes).
    static func barcodes(on product: FoodAlert.Product) -> Set<String> {
        let text = ([product.productCode] + product.batches.map(\.batchCode)).compactMap { $0 }.joined(separator: " ")
        return Set(text.components(separatedBy: CharacterSet.decimalDigits.inverted).filter { $0.count >= 8 })
    }

    static let stopWords: Set<String> = [
        "the", "and", "with", "of", "in", "a", "an", "for", "style", "flavour", "flavor", "flavoured",
        "pack", "x", "g", "kg", "ml", "l", "pcs", "organic", "mini", "deluxe", "family", "range", "fresh",
        "finest", "classic", "original", "selection", "everyday", "essential", "taste", "own", "brand",
    ]

    static let polishStopWords: Set<String> = [
        "z", "ze", "i", "w", "we", "na", "do", "od", "bez", "oraz", "o", "po", "dla", "lub", "typu",
        "smak", "smaku", "marki", "partia", "partii", "g", "kg", "ml", "l", "szt", "op",
    ]

    /// Longest first, so the first match is the longest.
    private static let polishSuffixes = [
        "ami", "ach", "ego", "emu", "ymi", "ich", "ych", "owi", "om", "ow", "ie", "ia", "iu", "ej",
        "a", "e", "i", "o", "u", "y",
    ]

    /// Endings of unstemmed adjectives ("zielone", "wędzony", "regionalna", "górski").
    private static let polishAdjectiveEndings = ["ny", "na", "ne", "owy", "owa", "owe", "ony", "ona", "one", "ski", "ska", "skie", "cki", "cka", "ckie"]

    static let genericBusinessWords: Set<String> = [
        "ltd", "limited", "plc", "uk", "co", "company", "foods", "food", "store", "group", "international",
        "trading", "brand", "products", "inc", "llp", "fine", "traditional",
    ]

    static let synonyms: [String: String] = [
        "yoghurt": "yogurt", "yoghurts": "yogurt",
        "courgette": "zucchini", "aubergine": "eggplant", "coriander": "cilantro",
    ]
}

// MARK: - Dates in notices

/// A date from a notice, at day or month precision ("16 July 2026", "May 2027").
struct AlertDateSpec: Hashable, Sendable {
    let year: Int
    let month: Int
    let day: Int?

    func matches(_ date: Date, calendar: Calendar) -> Bool {
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        guard parts.year == year, parts.month == month else { return false }
        return day == nil || parts.day == day
    }
}

/// Pulls dates out of FSA batch text such as "10, 16 and 17 July 2026", "end of 28 April 2026",
/// "31 January 2027 02 May 2027" or "May 2027", and GIS batch text such as "17.07.2026 r." or
/// "03.2028 19A".
enum AlertDateParser {

    private static let monthPattern = "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"

    private static let written = try! NSRegularExpression(
        pattern: "((?:\\b\\d{1,2}(?:st|nd|rd|th)?\\b[\\s,&]*(?:and\\s+)?)*)\\b\(monthPattern)\\.?,?\\s+(\\d{4})",
        options: [.caseInsensitive]
    )

    private static let numeric = try! NSRegularExpression(pattern: "\\b(\\d{1,2})[/.](\\d{1,2})[/.](\\d{2,4})\\b")

    /// "03.2028" or "03/2028": a month with no day. Not the tail of a full date ("13/04/2027").
    private static let monthOnly = try! NSRegularExpression(pattern: "(?<![\\d./])(\\d{1,2})[./](\\d{4})(?!\\d)")

    static func dates(in text: String) -> [AlertDateSpec] {
        let ns = text as NSString
        var specs: [AlertDateSpec] = []

        for match in written.matches(in: text, range: NSRange(location: 0, length: ns.length)) {
            let daysText = ns.substring(with: match.range(at: 1))
            let monthText = ns.substring(with: match.range(at: 2)).lowercased()
            guard let year = Int(ns.substring(with: match.range(at: 3))),
                  let month = monthIndex(monthText)
            else { continue }
            let days = daysText.components(separatedBy: CharacterSet.decimalDigits.inverted).compactMap(Int.init).filter { (1...31).contains($0) }
            if days.isEmpty {
                specs.append(AlertDateSpec(year: year, month: month, day: nil))
            } else {
                specs.append(contentsOf: days.map { AlertDateSpec(year: year, month: month, day: $0) })
            }
        }

        for match in numeric.matches(in: text, range: NSRange(location: 0, length: ns.length)) {
            guard let day = Int(ns.substring(with: match.range(at: 1))),
                  let month = Int(ns.substring(with: match.range(at: 2))),
                  var year = Int(ns.substring(with: match.range(at: 3))),
                  (1...31).contains(day), (1...12).contains(month)
            else { continue }
            if year < 100 { year += 2000 }
            specs.append(AlertDateSpec(year: year, month: month, day: day))
        }

        for match in monthOnly.matches(in: text, range: NSRange(location: 0, length: ns.length)) {
            guard let month = Int(ns.substring(with: match.range(at: 1))),
                  let year = Int(ns.substring(with: match.range(at: 2))),
                  (1...12).contains(month)
            else { continue }
            specs.append(AlertDateSpec(year: year, month: month, day: nil))
        }
        return specs
    }

    private static func monthIndex(_ text: String) -> Int? {
        let names = ["jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"]
        return names.firstIndex { text.hasPrefix($0) }.map { $0 + 1 }
    }
}
