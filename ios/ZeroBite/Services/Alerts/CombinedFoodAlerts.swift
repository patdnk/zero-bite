//
//  CombinedFoodAlerts.swift
//  ZeroBite
//
//  Merges the food alert sources (UK FSA, Poland GIS) into one list. Only the sources the
//  user has switched on are queried. One source failing doesn't hide the others: its known
//  alerts stay, and the failure is reported alongside the merged list.
//

import Foundation

/// Thrown when some enabled sources failed. `alerts` is still the best list available: fresh
/// alerts from the sources that worked, plus the known alerts of the ones that didn't.
struct FoodAlertsPartialFailure: Error {
    let alerts: [FoodAlert]
    let failed: Set<FoodAlert.Source>
}

struct CombinedFoodAlerts: FoodAlertsFetching {

    let clients: [FoodAlert.Source: any FoodAlertsFetching]
    /// Read on every fetch, so changing the setting applies to the next refresh.
    let enabled: @Sendable () -> Set<FoodAlert.Source>

    init(clients: [FoodAlert.Source: any FoodAlertsFetching], enabled: @escaping @Sendable () -> Set<FoodAlert.Source>) {
        self.clients = clients
        self.enabled = enabled
    }

    func fetchAlerts(since: Date, known: [FoodAlert]) async throws -> [FoodAlert] {
        // Alerts of a disabled source are dropped, so turning one off clears it from the list.
        let sources = enabled().filter { clients[$0] != nil }
        let outcomes = await withTaskGroup(of: (FoodAlert.Source, Result<[FoodAlert], Error>).self) { group in
            for source in sources {
                let client = clients[source]!
                let own = known.filter { $0.source == source }
                group.addTask {
                    do {
                        return (source, .success(try await client.fetchAlerts(since: since, known: own)))
                    } catch {
                        return (source, .failure(error))
                    }
                }
            }
            var outcomes: [FoodAlert.Source: Result<[FoodAlert], Error>] = [:]
            for await (source, outcome) in group { outcomes[source] = outcome }
            return outcomes
        }
        try Task.checkCancellation()

        var merged: [FoodAlert] = []
        var failed: Set<FoodAlert.Source> = []
        for (source, outcome) in outcomes {
            switch outcome {
            case .success(let alerts): merged += alerts
            case .failure:
                failed.insert(source)
                merged += known.filter { $0.source == source }
            }
        }
        merged.sort { ($0.created, $1.id) > ($1.created, $0.id) }
        if !failed.isEmpty { throw FoodAlertsPartialFailure(alerts: merged, failed: failed) }
        return merged
    }
}
