//
//  GISAlertParserTests.swift
//  ZeroBiteTests
//
//  The GIS (Polish Chief Sanitary Inspectorate) page parser, run against snapshots of real
//  gov.pl pages and the golden expectations in testdata/gis-alerts.json, which the Android
//  port reads too.
//

import Foundation
import Testing
@testable import ZeroBite

private final class GISBundleToken {}

private struct GISGolden: Decodable {
    struct Entry: Decodable, Equatable {
        let id: String
        let url: String
        let published: String
    }
    struct List: Decodable {
        let file: String
        let entries: [Entry]
    }
    struct Batch: Decodable {
        let batchCode: String?
        let bestBefore: String?
        let useBy: String?
        let lotNumber: String?
    }
    struct Product: Decodable {
        let name: String
        let packSize: String?
        let productCode: String?
        let batches: [Batch]
    }
    struct Expect: Decodable {
        let id: String
        let title: String
        let shortTitle: String?
        let kind: String
        let created: String
        let url: String
        let business: String?
        let otherBusinesses: [String]
        let products: [Product]
        let allergens: [String]
        let risk: String?
        let consumerAdvice: String?
    }
    struct Case: Decodable {
        let file: String
        let url: String
        let expect: Expect
    }
    let list: List
    let alerts: [Case]
}

private enum GISFixtures {
    static func html(_ file: String) throws -> String {
        let name = (file as NSString).deletingPathExtension
        let url = try #require(Bundle(for: GISBundleToken.self).url(forResource: name, withExtension: "html"))
        return try String(contentsOf: url, encoding: .utf8)
    }

    static func golden() throws -> GISGolden { try Golden.load("gis-alerts", as: GISGolden.self) }

    /// The Warsaw calendar day of a date, as "YYYY-MM-DD".
    static func warsawDay(_ date: Date) -> String {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/Warsaw")!
        let c = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year!, c.month!, c.day!)
    }
}

struct GISAlertParserTests {

    @Test func listMatchesGolden() throws {
        let golden = try GISFixtures.golden()
        let entries = GISAlertParser.listEntries(html: try GISFixtures.html(golden.list.file))
        let actual = entries.map { GISGolden.Entry(id: $0.id, url: $0.url.absoluteString, published: GISFixtures.warsawDay($0.published)) }
        #expect(actual == golden.list.entries)
        #expect(!actual.isEmpty)
    }

    @Test func listSkipsFoodContactItems() throws {
        let html = try GISFixtures.html("gis-list.html")
        // The snapshot really does contain food-contact items for this to prove anything.
        #expect(html.contains("wyrobu-do-kontaktu"))
        let entries = GISAlertParser.listEntries(html: html)
        #expect(!entries.contains { $0.id.contains("wyrobu-do-kontaktu") })
        #expect(entries.allSatisfy { $0.id.hasPrefix("GIS-") && $0.url.host == "www.gov.pl" })
    }

    @Test func alertsMatchGolden() throws {
        let golden = try GISFixtures.golden()
        #expect(golden.alerts.count == 6)
        for c in golden.alerts {
            let expected = c.expect
            let url = try #require(URL(string: c.url))
            let alert = try #require(GISAlertParser.alert(html: try GISFixtures.html(c.file), url: url), "\(c.file)")

            #expect(alert.id == expected.id, "\(c.file)")
            #expect(alert.title == expected.title, "\(c.file)")
            #expect(alert.shortTitle == expected.shortTitle, "\(c.file)")
            #expect(alert.kind.rawValue == expected.kind, "\(c.file)")
            #expect(GISFixtures.warsawDay(alert.created) == expected.created, "\(c.file)")
            #expect(alert.url?.absoluteString == expected.url, "\(c.file)")
            #expect(alert.business == expected.business, "\(c.file)")
            #expect(alert.otherBusinesses == expected.otherBusinesses, "\(c.file)")
            #expect(alert.allergens == expected.allergens, "\(c.file)")
            #expect(alert.risk == expected.risk, "\(c.file)")
            #expect(alert.consumerAdvice == expected.consumerAdvice, "\(c.file)")
            #expect(alert.source == .gis, "\(c.file)")

            #expect(alert.products.count == expected.products.count, "\(c.file)")
            for (product, want) in zip(alert.products, expected.products) {
                #expect(product.name == want.name, "\(c.file)")
                #expect(product.packSize == want.packSize, "\(c.file)")
                #expect(product.productCode == want.productCode, "\(c.file)")
                #expect(product.batches.count == want.batches.count, "\(c.file) \(want.name)")
                for (batch, wantBatch) in zip(product.batches, want.batches) {
                    #expect(batch.batchCode == wantBatch.batchCode, "\(c.file) \(want.name)")
                    #expect(batch.bestBefore == wantBatch.bestBefore, "\(c.file) \(want.name)")
                    #expect(batch.useBy == wantBatch.useBy, "\(c.file) \(want.name)")
                    #expect(batch.lotNumber == wantBatch.lotNumber, "\(c.file) \(want.name)")
                }
            }
        }
    }

    /// Anchors for the golden file itself, so a wrong golden can't pass quietly.
    @Test func goldenHasTheKnownShapes() throws {
        let alerts = try GISFixtures.golden().alerts.map(\.expect)
        let bigos = try #require(alerts.first { $0.id.contains("bigosu") })
        #expect(bigos.products.count == 1)
        #expect(bigos.products[0].batches.count == 6)
        #expect(bigos.allergens == ["gluten"])
        let cheeses = try #require(alerts.first { $0.id.contains("serow-rozne") })
        #expect(cheeses.products.count == 8)
        let ean = try #require(alerts.first { $0.id.contains("sera-podpuszczkowego") })
        #expect(ean.products.first?.productCode == "5905784304458")
    }

    @Test func parsesNothingFromUnrelatedHTML() throws {
        let html = "<html><body><p>Błąd</p></body></html>"
        let url = try #require(URL(string: "https://www.gov.pl/web/gis/x"))
        #expect(GISAlertParser.alert(html: html, url: url) == nil)
        #expect(GISAlertParser.listEntries(html: html).isEmpty)
    }

    @Test func allergenStemsStartWords() throws {
        let html = """
        <article class="article-area__article " id="main-content">
        <h2>Ostrzeżenie publiczne dotyczące żywności: niezadeklarowany alergen w chlebie</h2>
        <p class="event-date">01.08.2026</p>
        <div class="editor-content"><div><h3>Zagrożenie:</h3>
        <p>Produkt należy wycofać w trybie pilnym. Wykryto gluten.</p>
        <h3>Szczegóły dotyczące produktu:</h3>
        <p>Nazwa produktu: <strong>Chleb</strong></p>
        <p>Numer partii: <strong>1</strong></p>
        <h3>Działania podjęte przez organy:</h3>
        <p>Producent: <strong>nie ma znaczenia</strong></p>
        <h3>Zalecenia dla konsumentów:</h3><p>Nie jeść.</p></div></div>
        </article>
        """
        let url = try #require(URL(string: "https://www.gov.pl/web/gis/chleb"))
        let alert = try #require(GISAlertParser.alert(html: html, url: url))
        #expect(alert.kind == .allergy)
        #expect(alert.allergens == ["gluten"])
        // Text after the details section, like "Producent" in running text, is not read as a field.
        #expect(alert.business == nil)
    }

    @Test func decodesEntities() {
        #expect(GISAlertParser.lines(ofHTML: "<p>Szczeg&oacute;ły&nbsp;&bdquo;x&rdquo; &#322;&amp;</p>") == ["Szczegóły „x” ł&"])
        #expect(GISAlertParser.lines(ofHTML: "<p>&#x142;&unknown;&amp;lt;</p>") == ["ł&unknown;&lt;"])
    }

    // MARK: Dates

    /// The calendar day of a date in a time zone, as "YYYY-MM-DD".
    private func day(_ date: Date, in zone: String) -> String {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: zone)!
        let c = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year!, c.month!, c.day!)
    }

    @Test func datesKeepTheirCalendarDayInTheUK() throws {
        let golden = try GISFixtures.golden()
        // 12 October 2026 is in British summer time, an hour behind Warsaw: midnight in Warsaw
        // would be 11 October there.
        let seryRozne = try #require(golden.alerts.first { $0.expect.id.contains("serow-rozne") })
        let seryRozneURL = try #require(URL(string: seryRozne.url))
        let alert = try #require(GISAlertParser.alert(html: try GISFixtures.html(seryRozne.file), url: seryRozneURL))
        #expect(day(alert.created, in: "Europe/Warsaw") == "2026-10-12")
        #expect(day(alert.created, in: "Europe/London") == "2026-10-12")

        let entries = GISAlertParser.listEntries(html: try GISFixtures.html(golden.list.file))
        #expect(!entries.isEmpty)
        for (entry, want) in zip(entries, golden.list.entries) {
            #expect(day(entry.published, in: "Europe/London") == want.published, "\(want.id)")
        }
    }

    @Test func rejectsImpossibleDates() throws {
        let row = #"<span class="date">31.02.2026</span><div class="title"><a href="/web/gis/ostrzezenie-publiczne-dotyczace-zywnosci-x">X</a></div>"#
        #expect(GISAlertParser.listEntries(html: row).isEmpty)
        let valid = row.replacingOccurrences(of: "31.02.2026", with: "28.02.2026")
        #expect(GISAlertParser.listEntries(html: valid).count == 1)

        let url = try #require(URL(string: "https://www.gov.pl/web/gis/x"))
        #expect(GISAlertParser.alert(html: articleHTML(date: "31.02.2026"), url: url) == nil)
        #expect(GISAlertParser.alert(html: articleHTML(date: "28.02.2026"), url: url) != nil)
    }

    /// The smallest page the parser accepts as an alert.
    private func articleHTML(date: String) -> String {
        """
        <article id="main-content"><h2>Ostrzeżenie publiczne dotyczące żywności: x</h2>
        <p class="event-date">\(date)</p>
        <h3>Szczegóły dotyczące produktu:</h3><p>Nazwa produktu: <strong>Chleb</strong></p>
        <h3>Zalecenia dla konsumentów:</h3><p>Nie jeść.</p></article>
        """
    }

    // MARK: Links

    private func listRow(date: String = "01.10.2026", href: String) -> String {
        #"<span class="date">\#(date)</span><div class="title"><a href="\#(href)">X</a></div>"#
    }

    @Test func listIgnoresLinksOutsideGovPl() {
        let slug = "ostrzezenie-publiczne-dotyczace-zywnosci-x"
        let html = [
            listRow(href: "https://example.com/web/gis/" + slug),
            listRow(href: "https://www.gov.pl.evil.example/web/gis/" + slug),
            listRow(href: "https://www.gov.pl@evil.example/web/gis/" + slug),
            listRow(href: "//evil.example/web/gis/" + slug),
            listRow(href: "https://www.gov.pl/web/gis/" + slug + "-ok"),
            listRow(href: "/web/gis/" + slug + "-relative"),
        ].joined()
        let entries = GISAlertParser.listEntries(html: html)
        #expect(entries.map(\.id) == ["GIS-" + slug + "-ok", "GIS-" + slug + "-relative"])
        #expect(entries.allSatisfy { $0.url.host == "www.gov.pl" })
    }

    @Test func alertIdMatchesListIdForEscapedSlugs() throws {
        let slug = "ostrzezenie-publiczne-dotyczace-zywnosci-%C5%BCurek-%28test%29"
        let entries = GISAlertParser.listEntries(html: listRow(href: "/web/gis/" + slug + "?page=2"))
        let entry = try #require(entries.first)
        #expect(entry.id == "GIS-" + slug)
        let alert = try #require(GISAlertParser.alert(html: articleHTML(date: "01.10.2026"), url: entry.url))
        #expect(alert.id == entry.id)
    }

    @Test func listRowCountCountsEveryRow() throws {
        let html = listRow(href: "/web/gis/ostrzezenie-publiczne-dotyczace-zywnosci-x")
            + listRow(href: "/web/gis/ostrzezenie-publiczne-dotyczace-wyrobu-do-kontaktu-z-zywnoscia-y")
        #expect(GISAlertParser.listRowCount(html: html) == 2)
        #expect(GISAlertParser.listEntries(html: html).count == 1)
        #expect(GISAlertParser.listRowCount(html: "<html></html>") == 0)
        let snapshot = try GISFixtures.html("gis-list.html")
        #expect(GISAlertParser.listRowCount(html: snapshot) > GISAlertParser.listEntries(html: snapshot).count)
    }

    // MARK: Business names

    @Test func businessNamesStopBeforeTheAddress() throws {
        func business(_ value: String) throws -> String? {
            let html = """
            <article id="main-content"><h2>Ostrzeżenie publiczne dotyczące żywności: x</h2>
            <p class="event-date">01.10.2026</p>
            <h3>Szczegóły dotyczące produktu:</h3><p>Nazwa produktu: <strong>Chleb</strong></p>
            <p>Producent: <strong>\(value)</strong></p>
            <h3>Zalecenia dla konsumentów:</h3><p>Nie jeść.</p></article>
            """
            let url = try #require(URL(string: "https://www.gov.pl/web/gis/x"))
            return GISAlertParser.alert(html: html, url: url)?.business
        }
        #expect(try business("Mlecz Ser Górskie Sp. z o. o. ul. ks. Juliana Łyska 7A") == "Mlecz Ser Górskie Sp. z o. o.")
        #expect(try business("Piekarnia Kowalski al. Jana Pawła 3, 00-001 Warszawa") == "Piekarnia Kowalski")
        #expect(try business("Mleczarnia Wieś Sp. z o.o. os. Słoneczne 1") == "Mleczarnia Wieś Sp. z o.o.")
        #expect(try business("Hurtownia pl. Wolności 2") == "Hurtownia")
        #expect(try business("Zakład Mięsny 34-600 Limanowa") == "Zakład Mięsny")
        #expect(try business("Zakład Mięsny Sp. z o.o., ul. Długa 1") == "Zakład Mięsny Sp. z o.o.")
        #expect(try business("Firma Nr 12-34 Sp. j.") == "Firma Nr 12-34 Sp. j.")
    }

    @Test func sourceFollowsIdPrefix() throws {
        let gis = FoodAlert(id: "GIS-x", title: "t", kind: .recall, created: .now, otherBusinesses: [],
                            products: [], allergens: [])
        #expect(gis.source == .gis)
        #expect(try AlertFixtures.load().allSatisfy { $0.source == .fsa })
    }
}
