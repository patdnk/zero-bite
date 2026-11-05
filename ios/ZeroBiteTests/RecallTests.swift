//
//  RecallTests.swift
//  ZeroBiteTests
//
//  FSA alert decoding (against a real API response), batch date parsing, recall matching
//  the recall store, the GIS client and the combined sources.
//

import Foundation
import Testing
@testable import ZeroBite

private final class FixtureBundleToken {}

enum AlertFixtures {
    static func load() throws -> [FoodAlert] {
        let url = try #require(Bundle(for: FixtureBundleToken.self).url(forResource: "fsa-alerts", withExtension: "json"))
        return try JSONDecoder().decode(FSAAlertResponse.self, from: Data(contentsOf: url)).items
    }

    static func alert(_ id: String) throws -> FoodAlert {
        try #require(try load().first { $0.id == id })
    }
}

struct FoodAlertDecodingTests {

    @Test func decodesRealResponseIncludingLooseFields() throws {
        let alerts = try AlertFixtures.load()
        #expect(alerts.count == 7)

        let brie = try AlertFixtures.alert("FSA-PRIN-28-2026")
        #expect(brie.kind == .recall)
        #expect(brie.business == "Traditional Cheese Company")
        #expect(brie.url?.absoluteString == "https://alerts.food.gov.uk/news-alerts/alert/fsa-prin-28-2026")
        #expect(brie.products.map(\.name).contains("Traditional Cheese Company French Brie"))
        #expect(brie.products.first?.batches.first?.useBy == "10 July 2026, 16 July 2026, and 17 July 2026")

        let potNoodle = try AlertFixtures.alert("FSA-AA-43-2026")
        #expect(potNoodle.kind == .allergy)
        #expect(potNoodle.allergens == ["Milk"])
    }

    @Test func flagsNoticesCoveringEveryBatch() throws {
        let falooda = try AlertFixtures.alert("FSA-AA-27-2026")
        #expect(falooda.products.first?.batches.first?.coversAllBatches == true)
        let brie = try AlertFixtures.alert("FSA-PRIN-28-2026")
        #expect(brie.products.first?.batches.first?.coversAllBatches == false)
    }

    @Test func cachedModelRoundTrips() throws {
        let alerts = try AlertFixtures.load()
        let data = try JSONEncoder().encode(alerts)
        #expect(try JSONDecoder().decode([FoodAlert].self, from: data) == alerts)
    }
}

struct AlertDateParserTests {

    @Test(arguments: [
        ("16 July 2026", [AlertDateSpec(year: 2026, month: 7, day: 16)]),
        ("10, 16 and 17 July 2026", [16, 10, 17].map { AlertDateSpec(year: 2026, month: 7, day: $0) }),
        ("end of 28 April 2026", [AlertDateSpec(year: 2026, month: 4, day: 28)]),
        ("May 2027", [AlertDateSpec(year: 2027, month: 5, day: nil)]),
        ("31 January 2027 02 May 2027", [AlertDateSpec(year: 2027, month: 1, day: 31), AlertDateSpec(year: 2027, month: 5, day: 2)]),
        ("12/08/2026", [AlertDateSpec(year: 2026, month: 8, day: 12)]),
        ("All best before dates", []),
    ])
    func parsesNoticeDates(text: String, expected: [AlertDateSpec]) {
        #expect(Set(AlertDateParser.dates(in: text)) == Set(expected))
    }
}

@MainActor
struct RecallMatcherTests {

    private let now = TestSupport.date(2026, 7, 12)
    private var matcher: RecallMatcher { RecallMatcher(now: now, calendar: TestSupport.calendar) }

    private func item(_ name: String, expires: Date, brand: String? = nil, barcode: String? = nil) -> FoodItem {
        FoodItem(name: name, category: .dairy, addedAt: TestSupport.date(2026, 7, 5), expiresAt: expires, barcode: barcode, brand: brand)
    }

    @Test func barcodeOnTheNoticeIsALikelyMatch() throws {
        let brie = try AlertFixtures.alert("FSA-PRIN-28-2026")
        let match = try #require(matcher.match(alert: brie, product: brie.products[0],
                                               item: item("Cheese", expires: TestSupport.date(2026, 8, 1), barcode: "5099874213772")))
        #expect(match.confidence == .likely)
        #expect(match.reasons.contains(.barcode))
    }

    @Test func headNounWithMatchingDateIsPossible() throws {
        let brie = try AlertFixtures.alert("FSA-PRIN-28-2026")
        // The Dunnes Stores brie lists use-by dates of 10, 16 and 17 July.
        let match = try #require(matcher.match(alert: brie, product: brie.products[0],
                                               item: item("Brie", expires: TestSupport.date(2026, 7, 16))))
        #expect(match.confidence == .possible)
        #expect(match.reasons == [.name, .date])
    }

    @Test func nameBrandAndDateIsLikely() throws {
        let brie = try AlertFixtures.alert("FSA-PRIN-28-2026")
        let match = try #require(matcher.match(alert: brie, product: brie.products[1],
                                               item: item("French brie", expires: TestSupport.date(2026, 7, 17), brand: "Traditional Cheese Company")))
        #expect(match.confidence == .likely)
        #expect(match.reasons.isSuperset(of: [.name, .brand, .date]))
    }

    @Test func nameOnlyWithADifferentBatchDateIsIgnored() throws {
        let brie = try AlertFixtures.alert("FSA-PRIN-28-2026")
        #expect(matcher.match(alert: brie, product: brie.products[1], item: item("Brie", expires: TestSupport.date(2026, 9, 3))) == nil)
    }

    @Test func singleWordMustBeTheProductsHeadNoun() throws {
        let chocolate = try AlertFixtures.alert("FSA-PRIN-47-2026-update-1")
        for product in chocolate.products {
            #expect(matcher.match(alert: chocolate, product: product, item: item("Milk", expires: TestSupport.date(2026, 7, 20))) == nil)
        }
        #expect(RecallMatcher.nameMatches(itemWords: ["ham"], productWords: RecallMatcher.words("Brady Family Crumb Irish Ham")))
        #expect(RecallMatcher.nameMatches(itemWords: RecallMatcher.words("Greek yoghurts"), productWords: RecallMatcher.words("Greek Style Yogurt 500g")))
    }

    @Test func dismissedConsumedAndOldAlertsAreSkipped() throws {
        let alerts = try AlertFixtures.load()
        let brie = item("Brie", expires: TestSupport.date(2026, 7, 16))
        let found = matcher.matches(alerts: alerts, items: [brie])
        let match = try #require(found.first)
        #expect(match.alert.id == "FSA-PRIN-28-2026")
        #expect(matcher.matches(alerts: alerts, items: [brie], dismissed: [match.id]).isEmpty)

        brie.consumedAt = now
        #expect(matcher.matches(alerts: alerts, items: [brie]).isEmpty)

        let muchLater = RecallMatcher(now: TestSupport.date(2027, 3, 1), calendar: TestSupport.calendar)
        #expect(muchLater.matches(alerts: alerts, items: [item("Brie", expires: TestSupport.date(2026, 7, 16))]).isEmpty)
    }
}

@MainActor
struct RecallStoreTests {

    private struct StubClient: FoodAlertsFetching {
        let result: Result<[FoodAlert], Error>
        func fetchAlerts(since: Date, known: [FoodAlert]) async throws -> [FoodAlert] { try result.get() }
    }

    private func makeStore(_ result: Result<[FoodAlert], Error>, cacheURL: URL? = nil) -> RecallStore {
        RecallStore(client: StubClient(result: result), cacheURL: cacheURL, defaults: AppSettings.ephemeral().userDefaults)
    }

    @Test func refreshCachesAlertsAndThrottles() async throws {
        let cache = FileManager.default.temporaryDirectory.appendingPathComponent("alerts-\(UUID().uuidString).json")
        let store = makeStore(.success(try AlertFixtures.load()), cacheURL: cache)
        #expect(store.needsRefresh)
        #expect(await store.refresh())
        #expect(store.alerts.count == 7)
        #expect(!store.needsRefresh)

        let reloaded = RecallStore(client: StubClient(result: .success([])), cacheURL: cache, defaults: AppSettings.ephemeral().userDefaults)
        #expect(reloaded.alerts.count == 7)
    }

    @Test func failedRefreshKeepsCachedAlerts() async throws {
        let store = makeStore(.failure(URLError(.notConnectedToInternet)))
        #expect(await store.refresh() == false)
        #expect(store.lastErrorMessage != nil)
        #expect(store.alerts.isEmpty)
    }

    @Test func keepsCachedGISAlertsWhenGISFails() async throws {
        let fsaOld = try AlertFixtures.alert("FSA-PRIN-28-2026")
        let fsaNew = try AlertFixtures.alert("FSA-AA-43-2026")
        let gis = try GISTestData.alert("gis-alert-boczek.html")
        let cache = FileManager.default.temporaryDirectory.appendingPathComponent("alerts-\(UUID().uuidString).json")
        try JSONEncoder().encode([fsaOld, gis]).write(to: cache)

        let combined = CombinedFoodAlerts(
            clients: [.fsa: StubSource(result: .success([fsaNew])), .gis: StubSource(result: .failure(URLError(.timedOut)))],
            enabled: { [.fsa, .gis] }
        )
        let store = RecallStore(client: combined, cacheURL: cache, defaults: AppSettings.ephemeral().userDefaults)
        #expect(store.alerts.count == 2)

        #expect(await store.refresh(force: true) == false)
        #expect(store.alerts.map(\.id) == [gis.id, fsaNew.id])
        #expect(store.lastErrorMessage == "Couldn't check for new food alerts. Showing the last ones we downloaded.")
        #expect(store.lastChecked == nil)
        // The merged list is what the next launch starts from.
        let reloaded = RecallStore(client: combined, cacheURL: cache, defaults: AppSettings.ephemeral().userDefaults)
        #expect(reloaded.alerts.map(\.id) == [gis.id, fsaNew.id])
    }

    @Test func gisErrorPageKeepsCachedGISAlerts() async throws {
        let gis = try GISTestData.alert("gis-alert-boczek.html")
        let fsaNew = try AlertFixtures.alert("FSA-AA-43-2026")
        let cache = FileManager.default.temporaryDirectory.appendingPathComponent("alerts-\(UUID().uuidString).json")
        try JSONEncoder().encode([gis]).write(to: cache)
        // gov.pl answers 200 with an error page instead of the list.
        let network = StubNetwork { _ in .init(body: Data("<html><body>Błąd</body></html>".utf8)) }
        let combined = CombinedFoodAlerts(
            clients: [.fsa: StubSource(result: .success([fsaNew])), .gis: GISFoodAlertsClient(session: network.session)],
            enabled: { [.fsa, .gis] }
        )
        let store = RecallStore(client: combined, cacheURL: cache, defaults: AppSettings.ephemeral().userDefaults)

        #expect(await store.refresh(force: true) == false)
        #expect(Set(store.alerts.map(\.id)) == [gis.id, fsaNew.id])
        #expect(store.lastErrorMessage == "Couldn't check for new food alerts. Showing the last ones we downloaded.")
    }

    @Test func dismissAndNotifiedArePersisted() async throws {
        let settings = AppSettings.ephemeral()
        let store = RecallStore(client: StubClient(result: .success(try AlertFixtures.load())), cacheURL: nil, defaults: settings.userDefaults)
        await store.refresh()
        let brie = FoodItem(name: "Brie", expiresAt: TestSupport.date(2026, 7, 16))
        let match = try #require(store.matches(for: [brie], now: TestSupport.date(2026, 7, 12)).first)

        store.markNotified([match])
        store.dismiss(match)
        let again = RecallStore(client: StubClient(result: .success([])), cacheURL: nil, defaults: settings.userDefaults)
        #expect(again.dismissedMatchIDs.contains(match.id))
        #expect(again.notifiedMatchIDs.contains(match.id))
    }
}

// MARK: - GIS client and combined sources

private final class GISTestBundleToken {}

enum GISTestData {
    static func html(_ file: String) throws -> String {
        let name = (file as NSString).deletingPathExtension
        let url = try #require(Bundle(for: GISTestBundleToken.self).url(forResource: name, withExtension: "html"))
        return try String(contentsOf: url, encoding: .utf8)
    }

    /// The article snapshot parsed at the URL the list page links to.
    static func alert(_ file: String) throws -> FoodAlert {
        let slug = try #require(slugs[file])
        return try #require(GISAlertParser.alert(html: html(file), url: articleURL(slug)))
    }

    static func articleURL(_ slug: String) -> URL { URL(string: "https://www.gov.pl/web/gis/" + slug)! }

    /// Snapshots whose slug is also in `gis-list.html`.
    static let slugs = [
        "gis-alert-sery-rozne.html": "ostrzezenie-publiczne-dotyczace-zywnosci-wykrycie-obecnosci-bakterii-listeria-monocytogenes-w-jednej-partii-serow-rozne-rodzaje",
        "gis-alert-boczek.html": "ostrzezenie-publiczne-dotyczace-zywnosci-wykrycie-obecnosci-bakterii-listeria-monocytogenes-w-jednej-partii-boczku-wedzonego",
    ]
}

/// Counts calls and replies with a fixed result.
private final class StubSource: FoodAlertsFetching, @unchecked Sendable {
    let result: Result<[FoodAlert], Error>
    private let lock = NSLock()
    private var received: [[FoodAlert]] = []

    init(result: Result<[FoodAlert], Error>) { self.result = result }

    /// The `known` argument of each call.
    var knownPerCall: [[FoodAlert]] { lock.withLock { received } }

    func fetchAlerts(since: Date, known: [FoodAlert]) async throws -> [FoodAlert] {
        lock.withLock { received.append(known) }
        return try result.get()
    }
}

struct GISFoodAlertsClientTests {

    private let boczekSlug = GISTestData.slugs["gis-alert-boczek.html"]!
    private let seryRozneSlug = GISTestData.slugs["gis-alert-sery-rozne.html"]!
    /// Between the list's 6 October entry and its 8 October one.
    private let since = TestSupport.date(2026, 10, 7)

    /// Serves the list snapshot as page 1 and the two article snapshots that match list
    /// entries. Every other article is a 404; `override` can answer anything first.
    private func network(override: (@Sendable (URL) -> StubNetwork.Reply?)? = nil) throws -> StubNetwork {
        let list = try GISTestData.html("gis-list.html")
        let articles = try GISTestData.slugs.reduce(into: [String: String]()) { $0[$1.value] = try GISTestData.html($1.key) }
        return StubNetwork { url in
            if let reply = override?(url) { return reply }
            if url.path == "/web/gis/ostrzezenia" {
                let page = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first { $0.name == "page" }?.value
                return page == "1" ? .init(body: Data(list.utf8)) : .init(body: Data("<html></html>".utf8))
            }
            if let html = articles[url.lastPathComponent] { return .init(body: Data(html.utf8)) }
            return .init(status: 404)
        }
    }

    @Test func stopsPagingAtCutoff() async throws {
        let network = try network()
        let client = GISFoodAlertsClient(session: network.session)

        let alerts = try await client.fetchAlerts(since: since, known: [])

        let requests = network.requests.map(\.absoluteString)
        #expect(requests.filter { $0.contains("ostrzezenia") } == ["https://www.gov.pl/web/gis/ostrzezenia?page=1"])
        // The list has 5 food entries newer than `since`; the older ones are never opened.
        let articles = requests.filter { !$0.contains("ostrzezenia?") }
        #expect(articles.count == 5)
        #expect(Set(articles) == Set(GISAlertParser.listEntries(html: try GISTestData.html("gis-list.html")).prefix(5).map(\.url.absoluteString)))
        #expect(!requests.contains { $0.contains("/photo/") })
        // Two of the five articles have a snapshot; newest first.
        #expect(alerts.map(\.id) == ["GIS-" + seryRozneSlug, "GIS-" + boczekSlug])
        #expect(alerts.allSatisfy { $0.source == .gis })
    }

    @Test func stopsAtMaxPages() async throws {
        // Every page is the same list, which never reaches back past `since`.
        let list = try GISTestData.html("gis-list.html")
        let network = StubNetwork { _ in .init(body: Data(list.utf8)) }
        let client = GISFoodAlertsClient(session: network.session, maxPages: 2)

        _ = try await client.fetchAlerts(since: TestSupport.date(2026, 1, 1), known: [])

        let pages = network.requests.map(\.absoluteString).filter { $0.contains("ostrzezenia?") }
        #expect(pages == ["https://www.gov.pl/web/gis/ostrzezenia?page=1", "https://www.gov.pl/web/gis/ostrzezenia?page=2"])
    }

    @Test func requestsCarryUserAgentAndTimeout() async throws {
        let network = try network()
        let client = GISFoodAlertsClient(session: network.session)

        _ = try await client.fetchAlerts(since: since, known: [])

        let sent = network.receivedRequests
        #expect(sent.count == 6)
        #expect(sent.allSatisfy { $0.value(forHTTPHeaderField: "User-Agent") == "ZeroBite-iOS/1.0" })
        #expect(sent.allSatisfy { $0.timeoutInterval == 20 })
    }

    @Test func reusesKnownArticles() async throws {
        let known = try GISTestData.alert("gis-alert-boczek.html")
        var marked = known
        marked.shortTitle = "from the cache"
        let network = try network()
        let client = GISFoodAlertsClient(session: network.session)

        let alerts = try await client.fetchAlerts(since: since, known: [marked])

        #expect(!network.requests.contains { $0.lastPathComponent == boczekSlug })
        #expect(alerts.first { $0.id == known.id }?.shortTitle == "from the cache")
        #expect(alerts.count == 2)
    }

    @Test func dropsKnownAlertsOlderThanTheWindowAndOffTheList() async throws {
        let boczek = try GISTestData.alert("gis-alert-boczek.html")
        let stale = FoodAlert(
            id: "GIS-gone-from-list", title: boczek.title, shortTitle: nil, kind: boczek.kind,
            created: TestSupport.date(2026, 3, 1), url: nil, business: nil, otherBusinesses: [],
            products: boczek.products, allergens: [], risk: nil, consumerAdvice: nil
        )
        let client = GISFoodAlertsClient(session: try network().session)

        let alerts = try await client.fetchAlerts(since: since, known: [stale])

        #expect(!alerts.contains { $0.id == stale.id })
    }

    @Test func skipsBrokenArticle() async throws {
        let seryRozne = seryRozneSlug
        let network = try network(override: { url in
            url.lastPathComponent == seryRozne ? .init(status: 500) : nil
        })
        let client = GISFoodAlertsClient(session: network.session)

        let alerts = try await client.fetchAlerts(since: since, known: [])

        #expect(alerts.map(\.id) == ["GIS-" + boczekSlug])
    }

    @Test func failingListPageThrows() async throws {
        let network = try network(override: { url in
            url.path == "/web/gis/ostrzezenia" ? .init(status: 500) : nil
        })
        let client = GISFoodAlertsClient(session: network.session)

        await #expect(throws: URLError.self) {
            _ = try await client.fetchAlerts(since: since, known: [])
        }
    }

    @Test func emptyFirstPageThrows() async throws {
        let network = StubNetwork { _ in .init(body: Data("<html><body>Błąd</body></html>".utf8)) }
        let client = GISFoodAlertsClient(session: network.session)

        await #expect(throws: URLError.self) {
            _ = try await client.fetchAlerts(since: since, known: [])
        }
        #expect(network.requests.count == 1)
    }

    @Test func emptyLaterPageEndsTheList() async throws {
        let list = try GISTestData.html("gis-list.html")
        let network = StubNetwork { url in
            url.query == "page=1" ? .init(body: Data(list.utf8)) : .init(body: Data("<html></html>".utf8))
        }
        let client = GISFoodAlertsClient(session: network.session)

        // Every entry on page 1 is newer than `since`, so only the empty page 2 ends paging.
        _ = try await client.fetchAlerts(since: TestSupport.date(2026, 1, 1), known: [])

        let pages = network.requests.map(\.absoluteString).filter { $0.contains("ostrzezenia?") }
        #expect(pages == ["https://www.gov.pl/web/gis/ostrzezenia?page=1", "https://www.gov.pl/web/gis/ostrzezenia?page=2"])
    }

    @Test func downloadsAtMostThreeArticlesAtOnce() async throws {
        let list = try GISTestData.html("gis-list.html")
        let network = StubNetwork { url in
            url.path == "/web/gis/ostrzezenia" ? .init(body: Data(list.utf8)) : .init(status: 404, delay: 0.2)
        }
        let client = GISFoodAlertsClient(session: network.session, maxPages: 1)

        _ = try await client.fetchAlerts(since: TestSupport.date(2026, 1, 1), known: [])

        #expect(network.requests.count == 1 + 8)
        #expect(network.peakConcurrency == 3)
    }
}

struct CombinedFoodAlertsTests {

    @Test func fetchesOnlyEnabledSources() async throws {
        let fsa = StubSource(result: .success([try AlertFixtures.alert("FSA-AA-43-2026")]))
        let gis = StubSource(result: .success([try GISTestData.alert("gis-alert-boczek.html")]))
        let combined = CombinedFoodAlerts(clients: [.fsa: fsa, .gis: gis], enabled: { [.gis] })

        let alerts = try await combined.fetchAlerts(since: .distantPast, known: [])

        #expect(fsa.knownPerCall.isEmpty)
        #expect(gis.knownPerCall.count == 1)
        #expect(alerts.map(\.source) == [.gis])
    }

    @Test func handsEachClientOnlyItsOwnKnownAlerts() async throws {
        let knownFSA = try AlertFixtures.alert("FSA-AA-43-2026")
        let knownGIS = try GISTestData.alert("gis-alert-boczek.html")
        let fsa = StubSource(result: .success([]))
        let gis = StubSource(result: .success([]))
        let combined = CombinedFoodAlerts(clients: [.fsa: fsa, .gis: gis], enabled: { [.fsa, .gis] })

        _ = try await combined.fetchAlerts(since: .distantPast, known: [knownFSA, knownGIS])

        #expect(fsa.knownPerCall == [[knownFSA]])
        #expect(gis.knownPerCall == [[knownGIS]])
    }

    @Test func mergesSourcesNewestFirst() async throws {
        let fsaAlert = try AlertFixtures.alert("FSA-AA-43-2026")
        let gisAlert = try GISTestData.alert("gis-alert-boczek.html")
        let combined = CombinedFoodAlerts(
            clients: [.fsa: StubSource(result: .success([fsaAlert])), .gis: StubSource(result: .success([gisAlert]))],
            enabled: { [.fsa, .gis] }
        )

        let alerts = try await combined.fetchAlerts(since: .distantPast, known: [])

        #expect(alerts == [fsaAlert, gisAlert].sorted { $0.created > $1.created })
    }

    @Test func dropsDisabledSourceWithoutNetwork() async throws {
        let knownFSA = try AlertFixtures.alert("FSA-AA-43-2026")
        let knownGIS = try GISTestData.alert("gis-alert-boczek.html")
        let failing = StubSource(result: .failure(URLError(.notConnectedToInternet)))
        let combined = CombinedFoodAlerts(clients: [.fsa: failing, .gis: failing], enabled: { [.fsa] })

        do {
            _ = try await combined.fetchAlerts(since: .distantPast, known: [knownFSA, knownGIS])
            Issue.record("Expected a partial failure")
        } catch let partial as FoodAlertsPartialFailure {
            #expect(partial.alerts == [knownFSA])
            #expect(partial.failed == [.fsa])
        }
        #expect(failing.knownPerCall.count == 1)
    }
}

struct AllergenTests {

    @Test func profileCoversFSAAllergenLabels() {
        #expect(Allergen.gluten.matches(alertLabel: "Wheat"))
        #expect(Allergen.gluten.matches(alertLabel: "Barley"))
        #expect(Allergen.nuts.matches(alertLabel: "Hazelnut"))
        #expect(!Allergen.nuts.matches(alertLabel: "Peanuts"))
        #expect(Allergen.milk.matches(alertLabel: "Milk"))
        #expect(!Allergen.milk.matches(alertLabel: "Egg"))
    }

    @Test func onlyAllergyAlertsConcernAllergies() throws {
        let potNoodle = try AlertFixtures.alert("FSA-AA-43-2026")
        #expect(potNoodle.concerns([.milk, .egg]) == [.milk])
        let brie = try AlertFixtures.alert("FSA-PRIN-28-2026")
        #expect(brie.concerns([.milk]).isEmpty)
    }
}

@MainActor
struct FoodAlertsViewModelTests {

    @Test func forYouShowsFridgeMatchesAndAllergyAlerts() async {
        let dependencies = AppDependencies.preview(alerts: PreviewFoodAlerts())
        await dependencies.recalls.refresh(force: true)
        let viewModel = FoodAlertsViewModel(recalls: dependencies.recalls, inventory: dependencies.inventory, settings: dependencies.settings)

        // The sample fridge has semi-skimmed milk, so the milk recall is "for you".
        #expect(viewModel.rows.map(\.alert.id) == ["FSA-PRIN-PREVIEW"])
        #expect(viewModel.rows.first?.highlight == "You have: Semi-skimmed milk")

        dependencies.settings.allergenProfile = [.milk]
        #expect(viewModel.rows.map(\.alert.id) == ["FSA-PRIN-PREVIEW", "FSA-AA-PREVIEW"])

        viewModel.filter = .allergy
        #expect(viewModel.rows.map(\.alert.id) == ["FSA-AA-PREVIEW"])
    }
}

struct FoodAlertBatchTests {
    @Test func summaryReadsNaturally() {
        #expect(FoodAlert.Batch(batchCode: "L123", bestBefore: "May 2027").summary == "Batch L123 · Best before May 2027")
        #expect(FoodAlert.Batch(batchCode: "All batches", useBy: "All use by dates").summary == "All batches · All use by dates")
    }
}
