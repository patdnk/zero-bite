//
//  ScanFlow.swift
//  ZeroBite
//
//  The add-food sheet's finite state machine: choose a source → (live scan | read a photo)
//  → review what was read (with a barcode lookup) → item form → closed.
//  Pure: ScanCoordinator runs the effects (OCR, barcode lookup, closing the sheet).
//

import Foundation

enum ScanRoute: Hashable {
    case live
    case review
    case form(ItemDraft)
}

enum ScanFlow: FlowMachine {

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

    enum Event: Equatable, Sendable {
        // Choosing a source
        case liveScanTapped
        case manualEntryTapped(now: Date)
        case photoChosen(Data)
        case photoLoadFailed
        case labelRead(ScanResult)
        case labelReadFailed(Failure)
        case errorDismissed
        case liveScanFinished(ScanResult)

        // Reviewing what was read
        case nameChanged(String)
        case categoryChanged(FoodCategory)
        /// nil means "None of these".
        case candidateSelected(DateCandidate.ID?)
        case productLookedUp(ProductInfo?)
        case continueTapped(now: Date)

        // Navigation and lifecycle
        /// The navigation stack changed its own path (back button, swipe back).
        case pathChanged([ScanRoute])
        case itemSaved
        case closeTapped
        /// The sheet went away without closeTapped (swiped down).
        case dismissed
    }

    enum Effect: Equatable {
        case readLabel(Data)
        case cancelReadLabel
        case lookUpProduct(barcode: String)
        case cancelLookUp
        /// Ask the parent to close the sheet.
        case close
    }

    static func reduce(_ state: inout State, _ event: Event) -> [Effect] {
        guard !state.isFinished else { return [] }

        switch event {
        case .liveScanTapped:
            guard state.status != .processing else { return [] }
            state.path.append(.live)
            return []

        case .manualEntryTapped(let now):
            guard state.status != .processing else { return [] }
            state.path.append(.form(ItemDraft(now: now)))
            return []

        case .photoChosen(let data):
            guard state.status != .processing else { return [] }
            state.status = .processing
            return [.readLabel(data)]

        case .photoLoadFailed:
            state.status = .failed(.photoNotLoaded)
            return []

        case .labelRead(let result):
            guard state.status == .processing else { return [] }  // stale result
            guard !result.isEmpty else {
                state.status = .failed(.nothingFound)
                return []
            }
            state.status = .idle
            return startReview(result, in: &state)

        case .labelReadFailed(let failure):
            guard state.status == .processing else { return [] }
            state.status = .failed(failure)
            return []

        case .errorDismissed:
            if case .failed = state.status { state.status = .idle }
            return []

        case .liveScanFinished(let result):
            guard state.path.last == .live, !result.isEmpty else { return [] }
            return startReview(result, in: &state)

        case .nameChanged(let name):
            state.review?.name = name
            return []

        case .categoryChanged(let category):
            state.review?.category = category
            return []

        case .candidateSelected(let id):
            state.review?.selectedCandidateID = id
            return []

        case .productLookedUp(let info):
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

        case .continueTapped(let now):
            guard let review = state.review, review.canContinue, state.path.last == .review else { return [] }
            state.path.append(.form(review.makeDraft(now: now)))
            return []

        case .pathChanged(let path):
            state.path = path
            // Left review mid-lookup: stop it. A late result is then ignored (isLookingUp is false).
            guard !path.contains(.review), state.review?.isLookingUp == true else { return [] }
            state.review?.isLookingUp = false
            return [.cancelLookUp]

        case .itemSaved, .closeTapped:
            state.isFinished = true
            return [.cancelReadLabel, .cancelLookUp, .close]

        case .dismissed:
            state.isFinished = true
            return [.cancelReadLabel, .cancelLookUp]
        }
    }

    private static func startReview(_ result: ScanResult, in state: inout State) -> [Effect] {
        let review = Review(result: result)
        state.review = review
        state.path.append(.review)
        if let barcode = result.barcode {
            return [.lookUpProduct(barcode: barcode)]
        }
        return []
    }
}
