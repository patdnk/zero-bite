//
//  GISAlertParser.swift
//  ZeroBite
//
//  Reads Poland's Chief Sanitary Inspectorate (GIS) food warnings from gov.pl HTML into the
//  same `FoodAlert` the FSA feed produces. gov.pl has no API, so this works on two kinds of
//  page: the paged list of warnings (`/web/gis/ostrzezenia?page=N`) and one article per
//  warning. Articles are loosely formatted ("Label: value" lines under headings), so the
//  parser flattens the HTML to lines and reads fields by label, never by markup.
//
//  Text from gov.pl, Główny Inspektorat Sanitarny, CC BY-SA 4.0. Photos are CC BY-NC-ND and
//  are never read or fetched.
//

import Foundation

enum GISAlertParser {

    static let base = URL(string: "https://www.gov.pl")!

    /// One row of the list page: enough to decide whether the article is new and worth fetching.
    struct ListEntry: Hashable, Sendable {
        let id: String
        let url: URL
        let published: Date
    }

    // MARK: - List page

    private static let listRowMarker = "<span class=\"date\">"

    /// How many rows the list page has, food or not. An empty page is one with no rows at all;
    /// a page of only food-contact items is still a list, so paging must go on.
    static func listRowCount(html: String) -> Int {
        html.components(separatedBy: listRowMarker).count - 1
    }

    /// The food warnings on a list page, in page order (newest first). Food-contact items,
    /// which GIS mixes into the same list, are skipped, and so is any link that leaves gov.pl.
    static func listEntries(html: String) -> [ListEntry] {
        // Each entry is `<span class="date">DD.MM.YYYY</span>` then `<div class="title"><a href=…>`.
        html.components(separatedBy: listRowMarker).dropFirst().compactMap { chunk in
            guard let day = captures(#"^\s*(\d{2})\.(\d{2})\.(\d{4})\s*</span>"#, in: chunk),
                  let published = date(day: day[0], month: day[1], year: day[2]),
                  let link = captures(#"<div class="title">\s*<a href="([^"]+)""#, in: chunk),
                  let slug = pathSlug(link[0]),
                  isFood(slug: slug),
                  let url = URL(string: link[0], relativeTo: base)?.absoluteURL,
                  url.host == base.host
            else { return nil }
            return ListEntry(id: "GIS-" + slug, url: url, published: published)
        }
    }

    /// The last path segment of a link as written, without query or fragment and without
    /// decoding percent escapes. List entries and articles both take their id from this.
    private static func pathSlug(_ link: String) -> String? {
        link.prefix { $0 != "?" && $0 != "#" }.split(separator: "/").last.map(String.init)
    }

    private static func isFood(slug: String) -> Bool {
        (slug.hasPrefix("ostrzezenie-publiczne-dotyczace-zywnosci") || slug.hasPrefix("aktualizacja-ostrzezenia-publicznego"))
            && slug.contains("zywnosci")
            // "…wyrobu-do-kontaktu-z-zywnoscia" also contains "zywnosci".
            && !slug.contains("kontaktu")
    }

    // MARK: - Article page

    /// Parses one warning page. Returns nil when the page isn't a warning with a title, a date
    /// and at least one product (an error page, a layout change).
    static func alert(html: String, url: URL) -> FoodAlert? {
        guard let article = captures(#"<article[^>]*id="main-content"[^>]*>(.*?)</article>"#, in: html),
              let parts = captures(#"<h2[^>]*>(.*?)</h2>(.*)"#, in: article[0]),
              case let title = lines(ofHTML: parts[0]).joined(separator: " "), !title.isEmpty
        else { return nil }

        // The article's date is the first one after the title: titles of updates quote the
        // date of the original warning ("z dnia 03.07.2026"), which is not this one.
        let body = lines(ofHTML: parts[1])
        guard let created = body.lazy.compactMap({ line in
            captures(#"\b(\d{2})\.(\d{2})\.(\d{4})\b"#, in: line).flatMap { date(day: $0[0], month: $0[1], year: $0[2]) }
        }).first
        else { return nil }

        let hazard = section("Zagrożenie", in: body)
        let details = section("Szczegóły dotyczące produkt", in: body)
        let risk = hazard.isEmpty ? nil : hazard.joined(separator: " ")
        let advice = section("Zalecenia", in: body)

        let fields = productFields(details)
        guard !fields.products.isEmpty else { return nil }

        let isAllergy = fold(title + " " + (risk ?? "")).contains("alergen")
        return FoodAlert(
            id: "GIS-" + (pathSlug(url.absoluteString) ?? url.lastPathComponent),
            title: title,
            shortTitle: shortTitle(of: title),
            kind: isAllergy ? .allergy : .recall,
            created: created,
            url: url,
            business: fields.business,
            otherBusinesses: fields.otherBusinesses,
            products: fields.products,
            allergens: isAllergy ? allergens(in: title + " " + (risk ?? "")) : [],
            risk: risk,
            consumerAdvice: advice.isEmpty ? nil : advice.joined(separator: " ")
        )
    }

    /// "Wykrycie …" for "Ostrzeżenie publiczne dotyczące żywności: wykrycie …", with
    /// "Aktualizacja: " in front for updates.
    private static func shortTitle(of title: String) -> String? {
        guard let colon = title.range(of: ": ") else { return nil }
        let rest = title[colon.upperBound...]
        guard let first = rest.first else { return nil }
        let short = first.uppercased() + rest.dropFirst()
        return title.hasPrefix("Aktualizacja") ? "Aktualizacja: " + short : short
    }

    // MARK: Sections

    private static let headings = ["Zagrożenie", "Szczegóły dotyczące produkt", "Działania", "Zalecenia", "Zdjęcia"]

    /// The lines under the heading that starts with `prefix`, up to the next heading. Text on
    /// the heading's own line after the colon counts too.
    private static func section(_ prefix: String, in lines: [String]) -> [String] {
        guard let start = lines.firstIndex(where: { $0.hasPrefix(prefix) }) else { return [] }
        var result: [String] = []
        if let colon = lines[start].firstIndex(of: ":") {
            let inline = lines[start][lines[start].index(after: colon)...].trimmingCharacters(in: .whitespaces)
            if !inline.isEmpty { result.append(inline) }
        }
        for line in lines[(start + 1)...] {
            if headings.contains(where: line.hasPrefix) { break }
            result.append(line)
        }
        return result
    }

    // MARK: Product details

    private static let businessLabels = [
        "producent", "dystrybu", "wyprodukowano dla", "wyprodukowany w ue dla", "importer",
        "sprzedawca", "sprzedaż detaliczna", "dostawca", "marka",
    ]

    /// Reads the "Label: value" lines of the product details section. A product starts at
    /// "Nazwa produktu" or "Produkt"; each batch label fills the current batch, and starts a
    /// new one when it would overwrite a field already set.
    private static func productFields(_ lines: [String])
        -> (products: [FoodAlert.Product], business: String?, otherBusinesses: [String])
    {
        var products: [FoodAlert.Product] = []
        var business: String?
        var others: [String] = []

        for line in lines {
            guard let colon = line.firstIndex(of: ":") else { continue }
            let label = line[..<colon].trimmingCharacters(in: .whitespaces).lowercased()
            let value = line[line.index(after: colon)...].trimmingCharacters(in: .whitespaces)
            guard !value.isEmpty else { continue }

            if label == "nazwa produktu" || label == "produkt" {
                products.append(FoodAlert.Product(name: value, packSize: nil, productCode: nil, batches: []))
            } else if label == "kod kreskowy" {
                if !products.isEmpty { products[products.count - 1].productCode = value }
            } else if businessLabels.contains(where: label.hasPrefix) {
                let name = businessName(value)
                if name.isEmpty { continue }
                if label.hasPrefix("producent"), business == nil {
                    business = name
                } else {
                    others.append(name)
                }
            } else if !products.isEmpty {
                addBatchField(label: label, value: value, to: &products[products.count - 1])
            }
        }
        return (products, business, others)
    }

    /// Where a company's address starts: " ul. ", " al. ", " os. " or " pl. " (street, avenue,
    /// estate, square) or a postal code.
    private static let addressStart = try! NSRegularExpression(pattern: #" (?:ul|al|os|pl)\. |\b\d{2}-\d{3}\b"#, options: [.caseInsensitive])

    /// The company name of a "Producent: …" value. Addresses and registration numbers follow
    /// the first comma, or a street or postal code.
    private static func businessName(_ value: String) -> String {
        var name = value.split(separator: ",", maxSplits: 1).first.map(String.init) ?? value
        if let match = addressStart.firstMatch(in: name, range: NSRange(name.startIndex..., in: name)),
           let range = Range(match.range, in: name) {
            name = String(name[..<range.lowerBound])
        }
        return name.trimmingCharacters(in: .whitespaces)
    }

    private enum BatchField { case batchCode, bestBefore, useBy }

    private static func addBatchField(label: String, value: String, to product: inout FoodAlert.Product) {
        var fields: [BatchField] = []
        if label.contains("partii") { fields.append(.batchCode) }
        if label.contains("trwałości") || label.hasPrefix("najlepiej spożyć przed") { fields.append(.bestBefore) }
        if label.contains("przydatności") { fields.append(.useBy) }
        guard !fields.isEmpty else { return }

        func isSet(_ field: BatchField, in batch: FoodAlert.Batch) -> Bool {
            switch field {
            case .batchCode: batch.batchCode != nil
            case .bestBefore: batch.bestBefore != nil
            case .useBy: batch.useBy != nil
            }
        }
        if product.batches.isEmpty || fields.contains(where: { isSet($0, in: product.batches[product.batches.count - 1]) }) {
            product.batches.append(FoodAlert.Batch())
        }
        let last = product.batches.count - 1
        for field in fields {
            switch field {
            case .batchCode: product.batches[last].batchCode = value
            case .bestBefore: product.batches[last].bestBefore = value
            case .useBy: product.batches[last].useBy = value
            }
        }
    }

    // MARK: Allergens

    /// Polish word stems for each allergen, matched on folded text. Every stem in a group
    /// must be present, and each must start a word ("ryb" is in "ryba" but not in "trybie").
    private static let allergenStems: [(allergen: String, groups: [[String]])] = [
        ("gluten", [["gluten"]]),
        ("sulphites", [["siarczyn"], ["dwutlenk", "siark"]]),
        ("milk", [["mlek"], ["laktoz"]]),
        ("egg", [["jaj"]]),
        ("peanuts", [["orzeszk", "ziemn"]]),
        ("nuts", [["orzech"], ["pistacj"], ["migdal"]]),
        ("sesame", [["sezam"]]),
        ("soya", [["soj"]]),
        ("celery", [["seler"]]),
        ("mustard", [["gorczyc"]]),
        ("lupin", [["lubin"]]),
        ("fish", [["ryb"]]),
        ("crustaceans", [["skorupiak"]]),
        ("molluscs", [["mieczak"]]),
    ]

    /// English `Allergen` raw values named in the text, sorted.
    private static func allergens(in text: String) -> [String] {
        let folded = fold(text)
        return allergenStems
            .filter { entry in entry.groups.contains { $0.allSatisfy { startsAWord($0, in: folded) } } }
            .map(\.allergen)
            .sorted()
    }

    /// Lowercase with diacritics removed. "ł" has no decomposition, so it is mapped explicitly.
    private static func fold(_ text: String) -> String {
        text.lowercased()
            .replacingOccurrences(of: "ł", with: "l")
            .folding(options: .diacriticInsensitive, locale: nil)
    }

    private static func startsAWord(_ stem: String, in text: String) -> Bool {
        var searchStart = text.startIndex
        while let range = text.range(of: stem, range: searchStart..<text.endIndex) {
            if range.lowerBound == text.startIndex { return true }
            let before = text[text.index(before: range.lowerBound)]
            if !(before.isLetter || before.isNumber) { return true }
            searchStart = range.upperBound
        }
        return false
    }

    // MARK: - HTML to lines

    /// Flattens HTML to trimmed, non-empty text lines: block ends become line breaks, other
    /// tags are dropped and entities decoded.
    static func lines(ofHTML html: String) -> [String] {
        let flat = html
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
            .replacingOccurrences(of: #"(?i)<\s*(?:br\s*/?|/p|/h2|/h3|/li|/div)\s*>"#, with: "\n", options: .regularExpression)
            .replacingOccurrences(of: #"<[^>]*>"#, with: "", options: .regularExpression)
        return decodeEntities(flat)
            .components(separatedBy: "\n")
            .map { $0.replacingOccurrences(of: #"[\s\x{00A0}]+"#, with: " ", options: .regularExpression)
                .trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }

    private static let namedEntities: [String: String] = [
        "nbsp": "\u{00A0}", "amp": "&", "quot": "\"", "lt": "<", "gt": ">",
        "oacute": "ó", "Oacute": "Ó", "eacute": "é", "ouml": "ö", "szlig": "ß",
        "ndash": "–", "mdash": "—", "bdquo": "„", "rdquo": "”", "ldquo": "“",
    ]

    /// Decodes in one pass, so "&amp;lt;" becomes "&lt;". Unknown entities stay as written.
    private static func decodeEntities(_ text: String) -> String {
        var result = ""
        var rest = text[...]
        while let amp = rest.firstIndex(of: "&") {
            result += rest[..<amp]
            let tail = rest[amp...]
            if let semicolon = tail.prefix(12).firstIndex(of: ";"),
               let decoded = entity(String(tail[tail.index(after: amp)..<semicolon])) {
                result += decoded
                rest = tail[tail.index(after: semicolon)...]
            } else {
                result.append("&")
                rest = tail.dropFirst()
            }
        }
        return result + rest
    }

    private static func entity(_ name: String) -> String? {
        if name.hasPrefix("#") {
            let digits = name.dropFirst()
            let code = digits.first == "x" || digits.first == "X" ? UInt32(digits.dropFirst(), radix: 16) : UInt32(digits)
            return code.flatMap(Unicode.Scalar.init).map { String($0) }
        }
        return namedEntities[name]
    }

    // MARK: - Helpers

    /// The capture groups of the first match, or nil. `.` matches line breaks.
    private static func captures(_ pattern: String, in text: String) -> [String]? {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.dotMatchesLineSeparators, .caseInsensitive]),
              let match = regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text))
        else { return nil }
        return (1..<match.numberOfRanges).map { index in
            Range(match.range(at: index), in: text).map { String(text[$0]) } ?? ""
        }
    }

    /// The calendar Polish warnings are dated in.
    private static let warsaw: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/Warsaw")!
        return calendar
    }()

    /// Noon on a `DD.MM.YYYY` day in Poland, where the warnings are dated. Noon rather than
    /// midnight, so the day is the same on a phone in the UK, an hour behind. Nil for numbers
    /// that aren't a real date (31.02.2026).
    private static func date(day: String, month: String, year: String) -> Date? {
        guard let day = Int(day), let month = Int(month), let year = Int(year),
              (1...31).contains(day), (1...12).contains(month),
              let date = warsaw.date(from: DateComponents(year: year, month: month, day: day, hour: 12))
        else { return nil }
        let parts = warsaw.dateComponents([.year, .month, .day], from: date)
        return parts.year == year && parts.month == month && parts.day == day ? date : nil
    }
}
