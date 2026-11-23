//
//  ScanCoordinator.swift
//  ZeroBite
//
//  The add-food sheet: choose a source → (live scan | read a photo) → review what was read
//  (with a barcode lookup) → item form → finished. Started by MainCoordinator for each
//  presentation; reports back with parent?.eventLoop(event: .didFinishScan).
//

import Foundation
import Observation
import UIKit

enum ScanRoute: Hashable {
    case live
    case review
    case form(ItemDraft)
}

@MainActor
@Observable
final class ScanCoordinator: EventDrivenCoordinator {

    typealias ReadLabel = @Sendable (Data) async throws -> ScanResult

    private enum TaskID: Hashable {
        case readLabel
        case lookUp
    }

    weak var parent: MainCoordinator?
    let supportsLiveScanning: Bool
    let loop = EventLoop<State, Event, Command>(State())

    @ObservationIgnored private let productLookup: any ProductLookup
    @ObservationIgnored private let readLabel: ReadLabel

    init(
        parent: MainCoordinator? = nil,
        supportsLiveScanning: Bool,
        productLookup: any ProductLookup,
        readLabel: @escaping ReadLabel = { try await LabelScanner.scan(imageData: $0) }
    ) {
        self.parent = parent
        self.supportsLiveScanning = supportsLiveScanning
        self.productLookup = productLookup
        self.readLabel = readLabel
    }

    func execute(_ command: Command) -> CommandTask<Event> {
        switch command {
        case .willReadLabel(let data):
            let readLabel = readLabel
            return .run(id: TaskID.readLabel) {
                guard let normalized = UIImage(data: data)?.normalizedJPEGData() else {
                    return .didFailToReadLabel(.notAPhoto)
                }
                do {
                    return .didReadLabel(try await readLabel(normalized))
                } catch {
                    return .didFailToReadLabel(.scanner(error.localizedDescription))
                }
            }

        case .willCancelReadLabel:
            return .cancel(id: TaskID.readLabel)

        case .willLookUpProduct(let barcode):
            let productLookup = productLookup
            return .run(id: TaskID.lookUp) {
                .didLookUpProduct(try? await productLookup.lookup(barcode: barcode))
            }

        case .willCancelLookUp:
            return .cancel(id: TaskID.lookUp)

        case .willFinish:
            return .perform { [weak self] in self?.parent?.eventLoop(event: .didFinishScan) }
        }
    }
}

// MARK: - State

extension ScanCoordinator {

    struct State: Equatable {
        var path: [ScanRoute] = []
        var status: Status = .idle
        /// What was read, and the user's edits to it. Kept after the review screen is popped
        /// so it can finish animating out; the next read replaces it.
        var review: Review?
        /// Saved, cancelled or dismissed. Every later event is ignored.
        var isFinished = false
    }

    /// Reading a chosen photo. Processing and an error can't both be true.
    enum Status: Equatable {
        case idle
        case processing
        case failed(Failure)
    }

    enum Failure: Equatable, Sendable {
        case photoNotLoaded
        case notAPhoto
        case nothingFound
        case scanner(String)

        var message: String {
            switch self {
            case .photoNotLoaded: String(localized: "Couldn't load that photo.")
            case .notAPhoto: String(localized: "That file doesn't look like a photo.")
            case .nothingFound: String(localized: "No text or barcode was found. Try a sharper, closer photo of the label.")
            case .scanner(let message): message
            }
        }
    }

    struct Review: Equatable {
        let result: ScanResult
        var name: String
        var category: FoodCategory = .other
        var selectedCandidateID: DateCandidate.ID?
        var product: ProductInfo?
        var isLookingUp: Bool

        init(result: ScanResult) {
            self.result = result
            name = result.suggestedName ?? ""
            selectedCandidateID = result.bestCandidate?.id
            isLookingUp = result.barcode != nil
        }

        var selectedCandidate: DateCandidate? {
            result.candidates.first { $0.id == selectedCandidateID }
        }

        var canContinue: Bool {
            !name.trimmingCharacters(in: .whitespaces).isEmpty
        }

        func makeDraft(now: Date, calendar: Calendar = .current) -> ItemDraft {
            var draft = ItemDraft(now: now)
            draft.name = name.trimmingCharacters(in: .whitespaces)
            draft.category = category
            draft.barcode = result.barcode
            draft.brand = product?.brand
            if let date = selectedCandidate?.date {
                draft.expiresAt = date
            } else {
                draft.expiresAt = calendar.date(byAdding: .day, value: category.defaultShelfLifeDays, to: now) ?? now
            }
            return draft
        }
    }
}

// MARK: - Events and commands

extension ScanCoordinator {

    enum Event: Equatable, Sendable {
        // Choosing a source
        case didTapLiveScan
        case didTapManualEntry(now: Date)
        case didChoosePhoto(Data)
        case didFailToLoadPhoto
        case didReadLabel(ScanResult)
        case didFailToReadLabel(Failure)
        case didDismissError
        case didFinishLiveScan(ScanResult)

        // Reviewing what was read
        case didChangeName(String)
        case didChangeCategory(FoodCategory)
        /// nil means "None of these".
        case didSelectCandidate(DateCandidate.ID?)
        case didLookUpProduct(ProductInfo?)
        case didTapContinue(now: Date)

        // Navigation and lifecycle
        /// The navigation stack changed its own path (back button, swipe back).
        case didChangePath([ScanRoute])
        case didSaveItem
        case didTapClose
        /// The sheet went away without didTapClose (swiped down, or the parent closed it).
        case didDismiss
    }

    enum Command: Equatable {
        case willReadLabel(Data)
        case willCancelReadLabel
        case willLookUpProduct(barcode: String)
        case willCancelLookUp
        /// Tell the parent to close the sheet.
        case willFinish
    }
}

// MARK: - State machine

extension ScanCoordinator {

    nonisolated static func stateMachine(_ state: inout State, _ event: Event) -> [Command] {
        guard !state.isFinished else { return [] }

        switch event {
        case .didTapLiveScan:
            guard state.status != .processing else { return [] }
            state.path.append(.live)
            return []

        case .didTapManualEntry(let now):
            guard state.status != .processing else { return [] }
            state.path.append(.form(ItemDraft(now: now)))
            return []

        case .didChoosePhoto(let data):
            guard state.status != .processing else { return [] }
            state.status = .processing
            return [.willReadLabel(data)]

        case .didFailToLoadPhoto:
            state.status = .failed(.photoNotLoaded)
            return []

        case .didReadLabel(let result):
            guard state.status == .processing else { return [] }  // stale result
            guard !result.isEmpty else {
                state.status = .failed(.nothingFound)
                return []
            }
            state.status = .idle
            return startReview(result, in: &state)

        case .didFailToReadLabel(let failure):
            guard state.status == .processing else { return [] }
            state.status = .failed(failure)
            return []

        case .didDismissError:
            if case .failed = state.status { state.status = .idle }
            return []

        case .didFinishLiveScan(let result):
            guard state.path.last == .live, !result.isEmpty else { return [] }
            return startReview(result, in: &state)

        case .didChangeName(let name):
            state.review?.name = name
            return []

        case .didChangeCategory(let category):
            state.review?.category = category
            return []

        case .didSelectCandidate(let id):
            state.review?.selectedCandidateID = id
            return []

        case .didLookUpProduct(let info):
            guard var review = state.review, review.isLookingUp else { return [] }  // left review, or already done
            review.isLookingUp = false
            if let info {
                review.product = info
                // A catalogue name beats an OCR guess; keep anything the user has already typed.
                if review.name.isEmpty || review.name == review.result.suggestedName {
                    review.name = info.name
                }
                if let category = info.category {
                    review.category = category
                }
            }
            state.review = review
            return []

        case .didTapContinue(let now):
            guard let review = state.review, review.canContinue, state.path.last == .review else { return [] }
            state.path.append(.form(review.makeDraft(now: now)))
            return []

        case .didChangePath(let path):
            state.path = path
            // Left review mid-lookup: stop it. A late result is then ignored (isLookingUp is false).
            guard !path.contains(.review), state.review?.isLookingUp == true else { return [] }
            state.review?.isLookingUp = false
            return [.willCancelLookUp]

        case .didSaveItem, .didTapClose:
            state.isFinished = true
            return [.willCancelReadLabel, .willCancelLookUp, .willFinish]

        case .didDismiss:
            state.isFinished = true
            return [.willCancelReadLabel, .willCancelLookUp]
        }
    }

    private nonisolated static func startReview(_ result: ScanResult, in state: inout State) -> [Command] {
        state.review = Review(result: result)
        state.path.append(.review)
        if let barcode = result.barcode {
            return [.willLookUpProduct(barcode: barcode)]
        }
        return []
    }
}
