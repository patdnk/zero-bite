//
//  ScanFlowTests.swift
//  ZeroBiteTests
//

import Foundation
import Testing
import UIKit
@testable import ZeroBite

private let now = TestSupport.date(2026, 9, 7)

private func labelResult(barcode: String? = "5000000000000") -> ScanResult {
    LabelScanner.analyse(lines: ["SEMI SKIMMED MILK", "USE BY 12 SEP 2026", "PACKED 01/09/2026"], barcode: barcode, now: now)
}

private let milk = ProductInfo(barcode: "5000000000000", name: "Organic Semi Skimmed Milk", brand: "Yeo", category: .dairy)

/// Applies events in order and returns the final state and the effects of the last one.
private func run(_ events: [ScanFlow.Event], from state: ScanFlow.State = .init()) -> (ScanFlow.State, [ScanFlow.Effect]) {
    var state = state
    var effects: [ScanFlow.Effect] = []
    for event in events { effects = ScanFlow.reduce(&state, event) }
    return (state, effects)
}

// MARK: - Reducer

struct ScanFlowReducerTests {

    @Test func choosingAPhotoStartsReadingItOnce() {
        let data = Data([1, 2, 3])
        let (state, effects) = run([.photoChosen(data)])
        #expect(state.status == .processing)
        #expect(effects == [.readLabel(data)])

        let (_, again) = run([.photoChosen(data)], from: state)
        #expect(again.isEmpty)
    }

    @Test func aReadLabelOpensReviewAndLooksUpTheBarcode() {
        let result = labelResult()
        let (state, effects) = run([.photoChosen(Data()), .labelRead(result)])
        #expect(state.status == .idle)
        #expect(state.path == [.review])
        #expect(state.review?.name == "Semi Skimmed Milk")
        #expect(state.review?.isLookingUp == true)
        #expect(effects == [.lookUpProduct(barcode: "5000000000000")])
    }

    @Test func noBarcodeMeansNoLookup() {
        let (state, effects) = run([.photoChosen(Data()), .labelRead(labelResult(barcode: nil))])
        #expect(state.review?.isLookingUp == false)
        #expect(effects.isEmpty)
    }

    @Test func anEmptyReadIsAFailureTheUserCanDismiss() {
        let (state, _) = run([.photoChosen(Data()), .labelRead(ScanResult())])
        #expect(state.status == .failed(.nothingFound))
        #expect(state.path.isEmpty)

        let (dismissed, _) = run([.errorDismissed], from: state)
        #expect(dismissed.status == .idle)
    }

    @Test func aResultThatArrivesWhenNotReadingIsIgnored() {
        let (state, effects) = run([.labelRead(labelResult())])
        #expect(state == ScanFlow.State())
        #expect(effects.isEmpty)
    }

    @Test func liveScanPushesReviewOnTopOfTheScanner() {
        let (state, _) = run([.liveScanTapped, .liveScanFinished(labelResult())])
        #expect(state.path == [.live, .review])
    }

    @Test func barcodeLookupFillsNameAndCategoryWithoutOverwritingUserEdits() {
        let (filled, _) = run([.liveScanTapped, .liveScanFinished(labelResult()), .productLookedUp(milk)])
        #expect(filled.review?.name == "Organic Semi Skimmed Milk")
        #expect(filled.review?.category == .dairy)
        #expect(filled.review?.isLookingUp == false)

        let (edited, _) = run([.liveScanTapped, .liveScanFinished(labelResult()), .nameChanged("My milk"), .productLookedUp(milk)])
        #expect(edited.review?.name == "My milk")
        #expect(edited.review?.product == milk)
    }

    @Test func goingBackFromReviewCancelsTheLookup() {
        let (reviewing, _) = run([.liveScanTapped, .liveScanFinished(labelResult())])
        let (back, effects) = run([.pathChanged([.live])], from: reviewing)
        #expect(back.path == [.live])
        #expect(back.review?.isLookingUp == false)
        #expect(effects == [.cancelLookUp])

        // Continue can't be sent from a screen that isn't review.
        let (stillBack, _) = run([.continueTapped(now: now)], from: back)
        #expect(stillBack.path == [.live])

        // A lookup that finishes afterwards changes nothing.
        let (late, _) = run([.productLookedUp(milk)], from: back)
        #expect(late == back)
    }

    @Test func continueUsesTheSelectedDateOrFallsBackToShelfLife() throws {
        let (state, _) = run([.liveScanTapped, .liveScanFinished(labelResult()), .continueTapped(now: now)])
        guard case .form(let draft) = state.path.last else { Issue.record("no form on the path"); return }
        let day = TestSupport.calendar.dateComponents([.year, .month, .day], from: draft.expiresAt)
        #expect([day.year, day.month, day.day] == [2026, 9, 12])
        #expect(draft.barcode == "5000000000000")

        let review = try #require(run([.liveScanTapped, .liveScanFinished(labelResult()), .candidateSelected(nil), .categoryChanged(.dairy)]).0.review)
        let fallback = review.makeDraft(now: now, calendar: TestSupport.calendar)
        let days = TestSupport.calendar.dateComponents([.day], from: TestSupport.calendar.startOfDay(for: now), to: TestSupport.calendar.startOfDay(for: fallback.expiresAt)).day
        #expect(days == FoodCategory.dairy.defaultShelfLifeDays)
    }

    @Test func continueNeedsAName() {
        let (state, _) = run([.liveScanTapped, .liveScanFinished(labelResult()), .nameChanged("  "), .continueTapped(now: now)])
        #expect(state.path == [.live, .review])
    }

    @Test func closingCancelsAllWorkAndIgnoresEverythingAfter() {
        let (state, effects) = run([.photoChosen(Data()), .closeTapped])
        #expect(state.isFinished)
        #expect(effects == [.cancelReadLabel, .cancelLookUp, .close])

        let (after, more) = run([.labelRead(labelResult()), .liveScanTapped], from: state)
        #expect(after == state)
        #expect(more.isEmpty)
    }

    @Test func swipingTheSheetAwayCancelsWorkWithoutClosingAgain() {
        let (_, effects) = run([.photoChosen(Data()), .dismissed])
        #expect(effects == [.cancelReadLabel, .cancelLookUp])
    }
}

// MARK: - Coordinator (effects)

@MainActor
struct ScanCoordinatorTests {

    private let photo = UIGraphicsImageRenderer(size: CGSize(width: 4, height: 4)).jpegData(withCompressionQuality: 1) { context in
        UIColor.white.setFill()
        context.fill(CGRect(x: 0, y: 0, width: 4, height: 4))
    }

    @Test func aPhotoIsReadThenTheBarcodeIsLookedUp() async {
        let result = labelResult()
        let coordinator = ScanCoordinator(
            supportsLiveScanning: false,
            productLookup: StubProductLookup(info: milk),
            readLabel: { _ in result },
            onFinished: {}
        )
        await coordinator.send(.photoChosen(photo)).value
        #expect(coordinator.state.path == [.review])
        #expect(coordinator.state.review?.name == "Organic Semi Skimmed Milk")
        #expect(coordinator.state.review?.isLookingUp == false)
    }

    @Test func somethingThatIsNotAPhotoFails() async {
        let coordinator = ScanCoordinator(supportsLiveScanning: false, productLookup: StubProductLookup(info: nil), readLabel: { _ in labelResult() }, onFinished: {})
        await coordinator.send(.photoChosen(Data([0, 1, 2]))).value
        #expect(coordinator.state.status == .failed(.notAPhoto))
    }

    @Test func closingWhileReadingStopsTheReadAndClosesOnce() async {
        let closed = CallCount()
        let readCancelled = CallCount()
        let coordinator = ScanCoordinator(
            supportsLiveScanning: false,
            productLookup: StubProductLookup(info: nil),
            readLabel: { _ in
                do {
                    try await Task.sleep(for: .seconds(60))
                } catch {
                    await readCancelled.hit()
                    throw error
                }
                return labelResult()
            },
            onFinished: { closed.hit() }
        )
        let reading = coordinator.send(.photoChosen(photo))
        coordinator.send(.closeTapped)
        coordinator.send(.dismissed)
        await reading.value
        #expect(closed.value == 1)
        #expect(readCancelled.value == 1)
        #expect(coordinator.state.review == nil)
    }
}
