//
//  AppFlowTests.swift
//  ZeroBiteTests
//

import Foundation
import Testing
@testable import ZeroBite

struct AppFlowPhaseReducerTests {

    @Test(arguments: [(false, AppFlowPhase.welcome), (true, .main)])
    func launchGoesToWelcomeUntilItHasBeenCompleted(hasCompletedWelcome: Bool, expected: AppFlowPhase) {
        #expect(AppFlowPhase.reduce(.launch, on: .launchFinished(hasCompletedWelcome: hasCompletedWelcome)) == expected)
    }

    @Test func welcomeFinishedGoesToMain() {
        #expect(AppFlowPhase.reduce(.welcome, on: .welcomeFinished) == .main)
    }

    @Test func eventsThatDoNotApplyLeaveThePhaseUnchanged() {
        #expect(AppFlowPhase.reduce(.welcome, on: .launchFinished(hasCompletedWelcome: true)) == .welcome)
        #expect(AppFlowPhase.reduce(.main, on: .launchFinished(hasCompletedWelcome: false)) == .main)
        #expect(AppFlowPhase.reduce(.launch, on: .welcomeFinished) == .launch)
        #expect(AppFlowPhase.reduce(.main, on: .welcomeFinished) == .main)
    }
}

@MainActor
struct AppFlowControllerTests {

    @Test func firstLaunchGoesToWelcomeThenRemembersCompletion() {
        let settings = AppSettings.ephemeral()
        let flow = AppFlowController(settings: settings)
        #expect(flow.phase == .launch)
        flow.finishLaunch()
        #expect(flow.phase == .welcome)
        flow.send(.welcomeFinished)
        #expect(flow.phase == .main)
        #expect(settings.hasCompletedWelcome)

        let relaunch = AppFlowController(settings: settings)
        relaunch.finishLaunch()
        #expect(relaunch.phase == .main)
    }

    @Test func ignoredEventRunsNoEffects() {
        let settings = AppSettings.ephemeral()
        let flow = AppFlowController(settings: settings)
        flow.send(.welcomeFinished)  // still on the splash: not a valid transition
        #expect(flow.phase == .launch)
        #expect(!settings.hasCompletedWelcome)
    }
}
