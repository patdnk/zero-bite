//
//  AppCoordinatorTests.swift
//  ZeroBiteTests
//

import Foundation
import Testing
@testable import ZeroBite

struct AppStateMachineTests {

    private typealias State = AppCoordinator.State

    private func run(_ events: [AppCoordinator.Event], from state: State = .init()) -> (State, [AppCoordinator.Command]) {
        var state = state
        var commands: [AppCoordinator.Command] = []
        for event in events { commands = AppCoordinator.stateMachine(&state, event) }
        return (state, commands)
    }

    @Test func firstLaunchGoesToWelcome() {
        let (state, commands) = run([.didFinishLaunch(hasCompletedWelcome: false)])
        #expect(state.phase == .welcome)
        #expect(commands.isEmpty)
    }

    @Test func laterLaunchesStartMain() {
        let (state, commands) = run([.didFinishLaunch(hasCompletedWelcome: true)])
        #expect(state.phase == .main)
        #expect(commands == [.willStartMain])
    }

    @Test func finishingWelcomeRemembersItAndStartsMain() {
        let (state, commands) = run([.didFinishLaunch(hasCompletedWelcome: false), .didFinishWelcome])
        #expect(state.phase == .main)
        #expect(commands == [.willRememberWelcomeCompleted, .willStartMain])
    }

    @Test func eventsThatDoNotFitThePhaseAreIgnored() {
        let cases: [(AppCoordinator.Phase, AppCoordinator.Event)] = [
            (.welcome, .didFinishLaunch(hasCompletedWelcome: true)),
            (.main, .didFinishLaunch(hasCompletedWelcome: false)),
            (.launch, .didFinishWelcome),
            (.main, .didFinishWelcome),
        ]
        for (phase, event) in cases {
            let (state, commands) = run([event], from: State(phase: phase))
            #expect(state.phase == phase)
            #expect(commands.isEmpty)
        }
    }

    @Test func aNotificationTappedBeforeMainOpensOnceMainStarts() {
        let id = UUID()
        let (waiting, none) = run([.didOpenItemNotification(id)])
        #expect(waiting.pendingItemID == id)
        #expect(none.isEmpty)

        let (state, commands) = run([.didFinishLaunch(hasCompletedWelcome: true)], from: waiting)
        #expect(state.pendingItemID == nil)
        #expect(commands == [.willStartMain, .willShowItem(id)])
    }

    @Test func aNotificationTappedInMainOpensAtOnce() {
        let id = UUID()
        let (_, commands) = run([.didOpenItemNotification(id)], from: State(phase: .main))
        #expect(commands == [.willShowItem(id)])
    }

    @Test func becomingActiveRefreshesInEveryPhase() {
        for phase in [AppCoordinator.Phase.launch, .welcome, .main] {
            #expect(run([.didBecomeActive], from: State(phase: phase)).1 == [.willRefreshServices])
        }
    }
}

@MainActor
struct AppCoordinatorTests {

    @Test func firstLaunchGoesThroughWelcomeThenRemembersIt() {
        let dependencies = AppDependencies.preview(withSampleData: false)
        let app = AppCoordinator(dependencies: dependencies)
        app.finishLaunch()
        #expect(app.state.phase == .welcome)
        #expect(app.main == nil)

        app.eventLoop(event: .didFinishWelcome)
        #expect(app.state.phase == .main)
        #expect(app.main != nil)
        #expect(dependencies.settings.hasCompletedWelcome)

        let relaunch = AppCoordinator(dependencies: dependencies)
        relaunch.finishLaunch()
        #expect(relaunch.state.phase == .main)
    }

    @Test func aNotificationTappedOnTheSplashOpensTheItemOnTheFridgeTab() throws {
        let dependencies = AppDependencies.preview(withSampleData: false)
        dependencies.settings.hasCompletedWelcome = true
        let app = AppCoordinator(dependencies: dependencies)
        let id = UUID()
        app.eventLoop(event: .didOpenItemNotification(id))
        app.finishLaunch()

        let main = try #require(app.main)
        #expect(main.state.selectedTab == .fridge)
        #expect(main.inventory.state.path == [.itemDetail(id)])
    }

    @Test func anIgnoredEventRunsNothingAndIsLoggedAsIgnored() {
        let dependencies = AppDependencies.preview(withSampleData: false)
        let app = AppCoordinator(dependencies: dependencies)
        app.eventLoop(event: .didFinishWelcome)  // still on the splash
        #expect(app.state.phase == .launch)
        #expect(!dependencies.settings.hasCompletedWelcome)
        #expect(app.loop.history.last?.wasIgnored == true)
    }
}
