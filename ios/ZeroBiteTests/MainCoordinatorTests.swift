//
//  MainCoordinatorTests.swift
//  ZeroBiteTests
//
//  The tabs and their children: Main's state machine, and the events children send up to it.
//

import Foundation
import Testing
@testable import ZeroBite

struct MainStateMachineTests {

    private typealias State = MainCoordinator.State

    private func run(_ events: [MainCoordinator.Event], from state: State = .init()) -> (State, [MainCoordinator.Command]) {
        let replay = MainCoordinator.replay(events, from: state)
        return (replay.state, replay.commands.last ?? [])
    }

    @Test func theScanSheetStartsAScanAndClosingItStopsIt() {
        let (open, start) = run([.didRequestSheet(.scan)])
        #expect(open.sheet == .scan)
        #expect(start == [.willStartScan])

        let (closed, stop) = run([.didDismissSheet], from: open)
        #expect(closed.sheet == nil)
        #expect(stop == [.willStopScan])
    }

    @Test func otherSheetsNeedNoChild() {
        let (state, commands) = run([.didRequestSheet(.editItem(UUID()))])
        #expect(state.sheet != nil)
        #expect(commands.isEmpty)
        #expect(run([.didDismissSheet], from: state).1.isEmpty)
    }

    @Test func onlyOneSheetAtATime() {
        let id = UUID()
        let (state, commands) = run([.didRequestSheet(.editItem(id)), .didRequestSheet(.scan)])
        #expect(state.sheet == .editItem(id))
        #expect(commands.isEmpty)
    }

    @Test func aFinishedScanOnlyClosesTheScanSheet() {
        let id = UUID()
        let (state, _) = run([.didRequestSheet(.editItem(id)), .didFinishScan])
        #expect(state.sheet == .editItem(id))
    }

    @Test func aRequestedItemClosesWhateverIsOpenAndShowsTheFridge() {
        let id = UUID()
        let (state, commands) = run([.didSelectTab(.settings), .didRequestSheet(.scan), .didRequestItem(id)])
        #expect(state.selectedTab == .fridge)
        #expect(state.sheet == nil)
        #expect(commands == [.willStopScan, .willShowItem(id)])
    }

    @Test func howItWorksWaitsForTheSheetToClose() {
        let (blocked, _) = run([.didRequestSheet(.scan), .didRequestHowItWorks])
        #expect(!blocked.isShowingHowItWorks)

        let (shown, _) = run([.didRequestHowItWorks])
        #expect(shown.isShowingHowItWorks)
        #expect(!run([.didFinishHowItWorks], from: shown).0.isShowingHowItWorks)
    }
}

struct TabStateMachineTests {

    @Test func inventoryPushesPagesAndAsksMainForSheets() {
        let item = UUID()
        let now = TestSupport.date(2026, 9, 7)
        let replay = InventoryCoordinator.replay(
            [.didSelectItem(item), .didSelectRecipe("pancakes"), .didDeleteItem, .didTapAddItem(now: now)],
            from: .init()
        )
        #expect(replay.state.path == [.itemDetail(item)])
        #expect(replay.commands.last == [.willPresent(.addItem(ItemDraft(now: now)))])
    }

    @Test func aRequestedItemReplacesTheFridgeStack() {
        let item = UUID()
        let replay = InventoryCoordinator.replay([.didSelectRecipe("pancakes"), .didRequestItem(item)], from: .init())
        #expect(replay.state.path == [.itemDetail(item)])
    }

    @Test func recipesAsksMainForTheCookedSheet() {
        let replay = RecipesCoordinator.replay([.didSelectRecipe("pancakes"), .didTapCooked(recipeID: "pancakes")], from: .init())
        #expect(replay.state.path == [.recipe("pancakes")])
        #expect(replay.commands.last == [.willPresent(.cooked(recipeID: "pancakes"))])
    }

    @Test func settingsConfirmsBeforeDeleting() {
        let asked = SettingsCoordinator.replay([.didTapDeleteEverything], from: .init())
        #expect(asked.state.alert == .confirmDeleteEverything)
        #expect(asked.commands == [[]])

        let confirmed = SettingsCoordinator.replay([.didConfirmAlert(.confirmDeleteEverything)], from: asked.state)
        #expect(confirmed.state.alert == nil)
        #expect(confirmed.commands == [[.willDeleteEverything]])

        // SwiftUI may clear the presentation before running the button's action.
        let clearedFirst = SettingsCoordinator.replay([.didDismissAlert, .didConfirmAlert(.confirmDeleteEverything)], from: asked.state)
        #expect(clearedFirst.commands == [[], [.willDeleteEverything]])

        let cancelled = SettingsCoordinator.replay([.didDismissAlert, .didDismissAlert], from: asked.state)
        #expect(cancelled.commands == [[], []])
    }

    @Test func clearingRemovesExactlyTheItemsTheMessageCounted() {
        let ids = [UUID(), UUID()]
        let replay = SettingsCoordinator.replay([.didTapClearUsedHistory(itemIDs: ids), .didConfirmAlert(.confirmClearUsedHistory(itemIDs: ids))], from: .init())
        #expect(replay.commands.last == [.willClearUsedHistory(ids)])
    }

    @Test func nothingToClearAsksNothing() {
        let replay = SettingsCoordinator.replay([.didTapClearUsedHistory(itemIDs: [])], from: .init())
        #expect(replay.state.alert == nil)
    }

    @Test func aFoodAlertPushesItsDetailPage() {
        let alert = FoodAlert(id: "FSA-1", title: "Recall", kind: .recall, created: TestSupport.date(2026, 9, 7), otherBusinesses: [],
                              products: [], allergens: [])
        let replay = SettingsCoordinator.replay([.didChangePath([.foodAlerts]), .didSelectFoodAlert(alert)], from: .init())
        #expect(replay.state.path == [.foodAlerts, .foodAlert(alert)])
    }

    @Test func anInfoMessageConfirmsNothing() {
        let replay = SettingsCoordinator.replay([.didFindNotificationsDenied, .didConfirmAlert(.notificationsOff)], from: .init())
        #expect(replay.state.alert == nil)
        #expect(replay.commands == [[], []])
    }
}

/// Children and parent wired together, as the app runs them.
@MainActor
struct CoordinatorTreeTests {

    private func makeMain(withSampleData: Bool = false) -> MainCoordinator {
        MainCoordinator(dependencies: .preview(withSampleData: withSampleData))
    }

    @Test func aTabsSheetRequestOpensTheSheetOnMain() {
        let main = makeMain()
        let id = UUID()
        main.recipes.eventLoop(event: .didTapEditItem(id))
        #expect(main.state.sheet == .editItem(id))
    }

    @Test func scanningStartsAFreshScanEachTimeAndClosesTheSheetWhenItFinishes() throws {
        let main = makeMain()
        main.inventory.eventLoop(event: .didTapScan)
        let first = try #require(main.scan)
        #expect(first.parent === main)

        first.eventLoop(event: .didTapClose)
        #expect(main.state.sheet == nil)

        main.inventory.eventLoop(event: .didTapScan)
        let second = try #require(main.scan)
        #expect(second !== first)
        #expect(!second.state.isFinished)
    }

    @Test func swipingTheScanSheetAwayStopsTheScan() throws {
        let main = makeMain()
        main.eventLoop(event: .didRequestSheet(.scan))
        let scan = try #require(main.scan)
        main.eventLoop(event: .didDismissSheet)
        #expect(scan.state.isFinished)
    }

    @Test func confirmingDeleteEverythingEmptiesTheFridge() {
        let main = makeMain(withSampleData: true)
        #expect(!main.dependencies.inventory.items.isEmpty)
        main.settings.eventLoop(event: .didTapDeleteEverything)
        #expect(!main.dependencies.inventory.items.isEmpty)
        main.settings.eventLoop(event: .didConfirmAlert(.confirmDeleteEverything))
        #expect(main.dependencies.inventory.items.isEmpty)
    }

    @Test func settingsAsksMainToReplayHowItWorks() {
        let main = makeMain()
        main.settings.eventLoop(event: .didTapHowItWorks)
        #expect(main.state.isShowingHowItWorks)
    }
}
