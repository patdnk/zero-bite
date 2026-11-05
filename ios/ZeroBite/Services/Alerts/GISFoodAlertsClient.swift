//
//  GISFoodAlertsClient.swift
//  ZeroBite
//
//  Fetches Poland's Chief Sanitary Inspectorate (GIS) food warnings straight from gov.pl on
//  the phone, so there is no server to run. Reads the paged list of warnings newest first,
//  stops once a page reaches back past the cutoff, and downloads only the articles it doesn't
//  already have. `GISAlertParser` does the parsing. Photos are never requested.
//
//  Text from gov.pl, Główny Inspektorat Sanitarny, CC BY-SA 4.0.
//

import Foundation

struct GISFoodAlertsClient: FoodAlertsFetching {

    var session: URLSession = .shared
    /// Upper bound on list pages per refresh (10 entries each), so a layout change can't loop.
    var maxPages = 8

    /// Articles downloaded at once; gov.pl is a public site, so be gentle with it.
    private static let maxConcurrentArticles = 3

    func fetchAlerts(since: Date, known: [FoodAlert]) async throws -> [FoodAlert] {
        let entries = try await listEntries(since: since)
        let knownIDs = Set(known.map(\.id))
        let fresh = entries.filter { $0.published >= since && !knownIDs.contains($0.id) }
        let downloaded = await articles(for: fresh)
        try Task.checkCancellation()
        // New warnings were listed and none could be read: more likely an article layout change
        // than a quiet day, and returning the old alerts as a success would hide it.
        if !fresh.isEmpty && downloaded.isEmpty { throw URLError(.cannotParseResponse) }

        // Known alerts stay while they are on the list or inside the window; older ones that
        // dropped off the list are let go.
        let listed = Set(entries.map(\.id))
        let kept = known.filter { listed.contains($0.id) || $0.created >= since }
        return (kept + downloaded).sorted { ($0.created, $1.id) > ($1.created, $0.id) }
    }

    // MARK: - List

    /// Reads list pages until one contains an entry older than `since`. A failing list page
    /// throws, since the result would silently miss warnings. So does a first page with no rows:
    /// a 200 with no list is an error page or a layout change, and treating it as "no warnings"
    /// would wipe the alerts already held. A later page with no rows is the end of the list; one
    /// with rows but no food warnings (only food-contact items) is not, and paging goes on.
    private func listEntries(since: Date) async throws -> [GISAlertParser.ListEntry] {
        var entries: [GISAlertParser.ListEntry] = []
        var seen = Set<String>()
        for page in stride(from: 1, through: maxPages, by: 1) {
            var components = URLComponents(url: GISAlertParser.base.appendingPathComponent("web/gis/ostrzezenia"), resolvingAgainstBaseURL: false)!
            components.queryItems = [URLQueryItem(name: "page", value: String(page))]
            let body = try await html(at: components.url!)
            if GISAlertParser.listRowCount(html: body) == 0 {
                if page == 1 { throw URLError(.cannotParseResponse) }
                break
            }
            let pageEntries = GISAlertParser.listEntries(html: body)
            // A new warning can shift entries onto the next page between two requests.
            entries += pageEntries.filter { seen.insert($0.id).inserted }
            if pageEntries.contains(where: { $0.published < since }) { break }
        }
        return entries
    }

    // MARK: - Articles

    /// Downloads and parses the articles, a few at a time. One that fails to load or parse is
    /// left out so a single bad page doesn't hide the rest.
    private func articles(for entries: [GISAlertParser.ListEntry]) async -> [FoodAlert] {
        await withTaskGroup(of: FoodAlert?.self) { group in
            var pending = entries.makeIterator()
            var alerts: [FoodAlert] = []
            for _ in 0..<Self.maxConcurrentArticles {
                guard let entry = pending.next() else { break }
                group.addTask { await article(for: entry) }
            }
            for await alert in group {
                if let alert { alerts.append(alert) }
                if let entry = pending.next() {
                    group.addTask { await article(for: entry) }
                }
            }
            return alerts
        }
    }

    private func article(for entry: GISAlertParser.ListEntry) async -> FoodAlert? {
        guard let page = try? await html(at: entry.url) else { return nil }
        return GISAlertParser.alert(html: page, url: entry.url)
    }

    // MARK: - Networking

    private func html(at url: URL) async throws -> String {
        var request = URLRequest(url: url)
        request.timeoutInterval = 20
        request.setValue("text/html", forHTTPHeaderField: "Accept")
        request.setValue("ZeroBite-iOS/1.0", forHTTPHeaderField: "User-Agent")

        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200,
              let html = String(data: data, encoding: .utf8)
        else { throw URLError(.badServerResponse) }
        return html
    }
}
