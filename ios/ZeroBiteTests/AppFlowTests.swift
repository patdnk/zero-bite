//
//  AppFlowTests.swift
//  ZeroBiteTests
//

import Foundation
import Testing
@testable import ZeroBite

struct AppFlowReducerTests {

    private func reduce(_ state: AppFlowPhase, _ event: AppFlow.Event) -> (AppFlowPhase, [AppFlow.Effect]) {
        var state = state
        let effects = AppFlow.reduce(&state, event)
        return (state, effects)
    }

    @Test(arguments: [(false, AppFlowPhase.welcome), (true, .main)])
    func launchGoesToWelcomeUntilItHasBeenCompleted(hasCompletedWelcome: Bool, expected: AppFlowPhase) {
        let (state, effects) = reduce(.launch, .launchFinished(hasCompletedWelcome: hasCompletedWelcome))
        #expect(state == expected)
        #expect(effects.isEmpty)
    }

    @Test func welcomeFinishedGoesToMainAndRemembersIt() {
        let (state, effects) = reduce(.welcome, .welcomeFinished)
        #expect(state == .main)
        #expect(effects == [.rememberWelcomeCompleted])
    }

    @Test func eventsThatDoNotApplyLeaveThePhaseUnchanged() {
        let cases: [(AppFlowPhase, AppFlow.Event)] = [
            (.welcome, .launchFinished(hasCompletedWelcome: true)),
            (.main, .launchFinished(hasCompletedWelcome: false)),
            (.launch, .welcomeFinished),
            (.main, .welcomeFinished),
        ]
        for (phase, event) in cases {
            let (state, effects) = reduce(phase, event)
            #expect(state == phase)
            #expect(effects.isEmpty)
        }
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
