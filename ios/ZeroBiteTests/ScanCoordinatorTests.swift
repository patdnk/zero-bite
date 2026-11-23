//
//  ScanCoordinatorTests.swift
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

/// Applies events in order and returns the final state and the commands of the last one.
private func run(_ events: [ScanCoordinator.Event], from state: ScanCoordinator.State = .init()) -> (ScanCoordinator.State, [ScanCoordinator.Command]) {
    var state = state
    var commands: [ScanCoordinator.Command] = []
    for event in events { commands = ScanCoordinator.stateMachine(&state, event) }
    return (state, commands)
}

// MARK: - State machine

struct ScanStateMachineTests {

    @Test func choosingAPhotoStartsReadingItOnce() {
        let data = Data([1, 2, 3])
        let (state, commands) = run([.didChoosePhoto(data)])
        #expect(state.status == .processing)
        #expect(commands == [.willReadLabel(data)])

        let (_, again) = run([.didChoosePhoto(data)], from: state)
        #expect(again.isEmpty)
    }

    @Test func aReadLabelOpensReviewAndLooksUpTheBarcode() {
        let result = labelResult()
        let (state, commands) = run([.didChoosePhoto(Data()), .didReadLabel(result)])
        #expect(state.status == .idle)
        #expect(state.path == [.review])
        #expect(state.review?.name == "Semi Skimmed Milk")
        #expect(state.review?.isLookingUp == true)
        #expect(commands == [.willLookUpProduct(barcode: "5000000000000")])
    }

    @Test func noBarcodeMeansNoLookup() {
        let (state, commands) = run([.didChoosePhoto(Data()), .didReadLabel(labelResult(barcode: nil))])
        #expect(state.review?.isLookingUp == false)
        #expect(commands.isEmpty)
    }

    @Test func anEmptyReadIsAFailureTheUserCanDismiss() {
        let (state, _) = run([.didChoosePhoto(Data()), .didReadLabel(ScanResult())])
        #expect(state.status == .failed(.nothingFound))
        #expect(state.path.isEmpty)

        let (dismissed, _) = run([.didDismissError], from: state)
        #expect(dismissed.status == .idle)
    }

    @Test func aResultThatArrivesWhenNotReadingIsIgnored() {
        let (state, commands) = run([.didReadLabel(labelResult())])
        #expect(state == ScanCoordinator.State())
        #expect(commands.isEmpty)
    }

    @Test func liveScanPushesReviewOnTopOfTheScanner() {
        let (state, _) = run([.didTapLiveScan, .didFinishLiveScan(labelResult())])
        #expect(state.path == [.live, .review])
    }

    @Test func barcodeLookupFillsNameAndCategoryWithoutOverwritingUserEdits() {
        let (filled, _) = run([.didTapLiveScan, .didFinishLiveScan(labelResult()), .didLookUpProduct(milk)])
        #expect(filled.review?.name == "Organic Semi Skimmed Milk")
        #expect(filled.review?.category == .dairy)
        #expect(filled.review?.isLookingUp == false)

        let (edited, _) = run([.didTapLiveScan, .didFinishLiveScan(labelResult()), .didChangeName("My milk"), .didLookUpProduct(milk)])
        #expect(edited.review?.name == "My milk")
        #expect(edited.review?.product == milk)
    }

    @Test func goingBackFromReviewCancelsTheLookup() {
        let (reviewing, _) = run([.didTapLiveScan, .didFinishLiveScan(labelResult())])
        let (back, commands) = run([.didChangePath([.live])], from: reviewing)
        #expect(back.path == [.live])
        #expect(back.review?.isLookingUp == false)
        #expect(commands == [.willCancelLookUp])

        // Continue can't be sent from a screen that isn't review.
        let (stillBack, _) = run([.didTapContinue(now: now)], from: back)
        #expect(stillBack.path == [.live])

        // A lookup that finishes afterwards changes nothing.
        let (late, _) = run([.didLookUpProduct(milk)], from: back)
        #expect(late == back)
    }

    @Test func continueUsesTheSelectedDateOrFallsBackToShelfLife() throws {
        let (state, _) = run([.didTapLiveScan, .didFinishLiveScan(labelResult()), .didTapContinue(now: now)])
        guard case .form(let draft) = state.path.last else { Issue.record("no form on the path"); return }
        let day = TestSupport.calendar.dateComponents([.year, .month, .day], from: draft.expiresAt)
        #expect([day.year, day.month, day.day] == [2026, 9, 12])
        #expect(draft.barcode == "5000000000000")

        let review = try #require(run([.didTapLiveScan, .didFinishLiveScan(labelResult()), .didSelectCandidate(nil), .didChangeCategory(.dairy)]).0.review)
        let fallback = review.makeDraft(now: now, calendar: TestSupport.calendar)
        let days = TestSupport.calendar.dateComponents([.day], from: TestSupport.calendar.startOfDay(for: now), to: TestSupport.calendar.startOfDay(for: fallback.expiresAt)).day
        #expect(days == FoodCategory.dairy.defaultShelfLifeDays)
    }

    @Test func continueNeedsAName() {
        let (state, _) = run([.didTapLiveScan, .didFinishLiveScan(labelResult()), .didChangeName("  "), .didTapContinue(now: now)])
        #expect(state.path == [.live, .review])
    }

    @Test func closingCancelsAllWorkAndIgnoresEverythingAfter() {
        let (state, commands) = run([.didChoosePhoto(Data()), .didTapClose])
        #expect(state.isFinished)
        #expect(commands == [.willCancelReadLabel, .willCancelLookUp, .willFinish])

        let (after, more) = run([.didReadLabel(labelResult()), .didTapLiveScan], from: state)
        #expect(after == state)
        #expect(more.isEmpty)
    }

    @Test func swipingTheSheetAwayCancelsWorkWithoutClosingAgain() {
        let (_, commands) = run([.didChoosePhoto(Data()), .didDismiss])
        #expect(commands == [.willCancelReadLabel, .willCancelLookUp])
    }
}

// MARK: - Coordinator (commands)

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
            readLabel: { _ in result }
        )
        await coordinator.eventLoop(event: .didChoosePhoto(photo)).value
        #expect(coordinator.state.path == [.review])
        #expect(coordinator.state.review?.name == "Organic Semi Skimmed Milk")
        #expect(coordinator.state.review?.isLookingUp == false)
    }

    @Test func somethingThatIsNotAPhotoFails() async {
        let coordinator = ScanCoordinator(supportsLiveScanning: false, productLookup: StubProductLookup(info: nil), readLabel: { _ in labelResult() })
        await coordinator.eventLoop(event: .didChoosePhoto(Data([0, 1, 2]))).value
        #expect(coordinator.state.status == .failed(.notAPhoto))
    }

    @Test func closingWhileReadingStopsTheReadAndClosesTheSheet() async {
        let readCancelled = CallCount()
        let main = MainCoordinator(dependencies: .preview(withSampleData: false))
        main.eventLoop(event: .didRequestSheet(.scan))
        let coordinator = ScanCoordinator(
            parent: main,
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
            }
        )
        let reading = coordinator.eventLoop(event: .didChoosePhoto(photo))
        coordinator.eventLoop(event: .didTapClose)
        coordinator.eventLoop(event: .didDismiss)
        await reading.value
        #expect(main.state.sheet == nil)
        #expect(readCancelled.value == 1)
        #expect(coordinator.state.review == nil)
    }
}
