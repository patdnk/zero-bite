//
//  TestSupport.swift
//  ZeroBiteTests
//

import Foundation
@testable import ZeroBite

enum TestSupport {

    static var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/London")!
        return calendar
    }

    static func date(_ year: Int, _ month: Int, _ day: Int, hour: Int = 9) -> Date {
        calendar.date(from: DateComponents(year: year, month: month, day: day, hour: hour))!
    }

    static func item(_ name: String, daysUntilExpiry: Int, from now: Date, category: FoodCategory = .other, consumed: Bool = false) -> FoodItem {
        FoodItem(
            name: name,
            category: category,
            expiresAt: calendar.date(byAdding: .day, value: daysUntilExpiry, to: now)!,
            consumedAt: consumed ? now : nil
        )
    }
}

struct StubProductLookup: ProductLookup {
    let info: ProductInfo?

    func lookup(barcode: String) async throws -> ProductInfo? { info }
}

/// Records every collection change instead of touching Firebase.
@MainActor
final class RecordingUsageDataCollection: UsageDataCollection {
    private(set) var calls: [Bool] = []

    func setEnabled(_ enabled: Bool) { calls.append(enabled) }
}

/// A fake network for one test. Hand `session` to the client under test; every request it
/// makes goes to `respond` and is recorded, and nothing leaves the process. Each instance has
/// its own id, sent as a header, so tests running in parallel stay apart.
final class StubNetwork: @unchecked Sendable {

    struct Reply {
        var status = 200
        var body = Data()
        /// Holds the response back, so a test can observe requests overlapping.
        var delay: TimeInterval = 0
    }

    private static let header = "X-Stub-Network"
    private static let registry = NSLock()
    nonisolated(unsafe) private static var live: [String: StubNetwork] = [:]

    private let id = UUID().uuidString
    private let respond: @Sendable (URL) throws -> Reply
    private let state = NSLock()
    private var received: [URLRequest] = []
    private var inFlight = 0
    private var peak = 0

    init(respond: @escaping @Sendable (URL) throws -> Reply) {
        self.respond = respond
        Self.registry.withLock { Self.live[id] = self }
    }

    deinit { Self.registry.withLock { _ = Self.live.removeValue(forKey: id) } }

    /// Every request made, in arrival order.
    var receivedRequests: [URLRequest] { state.withLock { received } }
    var requests: [URL] { receivedRequests.compactMap(\.url) }
    /// The most requests that were waiting for a response at the same time.
    var peakConcurrency: Int { state.withLock { peak } }

    var session: URLSession {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [StubURLProtocol.self]
        configuration.httpAdditionalHeaders = [Self.header: id]
        return URLSession(configuration: configuration)
    }

    fileprivate static func network(for request: URLRequest) -> StubNetwork? {
        guard let id = request.value(forHTTPHeaderField: header) else { return nil }
        return registry.withLock { live[id] }
    }

    fileprivate func begin(_ request: URLRequest) throws -> Reply {
        state.withLock {
            received.append(request)
            inFlight += 1
            peak = max(peak, inFlight)
        }
        do {
            return try respond(request.url!)
        } catch {
            end()
            throw error
        }
    }

    fileprivate func end() { state.withLock { inFlight -= 1 } }
}

final class StubURLProtocol: URLProtocol {

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        guard let url = request.url, let network = StubNetwork.network(for: request) else {
            client?.urlProtocol(self, didFailWithError: URLError(.cannotConnectToHost))
            return
        }
        do {
            let reply = try network.begin(request)
            DispatchQueue.global().asyncAfter(deadline: .now() + reply.delay) {
                let response = HTTPURLResponse(url: url, statusCode: reply.status, httpVersion: nil, headerFields: nil)!
                self.client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
                self.client?.urlProtocol(self, didLoad: reply.body)
                self.client?.urlProtocolDidFinishLoading(self)
                network.end()
            }
        } catch {
            client?.urlProtocol(self, didFailWithError: error)
        }
    }

    override func stopLoading() {}
}
