//
//  WelcomeViewModelTests.swift
//  ZeroBiteTests
//

import Foundation
import Testing
@testable import ZeroBite

@MainActor
struct WelcomeViewModelTests {

    private func makeViewModel(
        mode: WelcomeViewModel.Mode = .firstRun,
        settings: AppSettings = .ephemeral(),
        usageData: any UsageDataCollection = RecordingUsageDataCollection(),
        onComplete: @escaping () -> Void = {}
    ) -> WelcomeViewModel {
        WelcomeViewModel(mode: mode, settings: settings, notifications: ExpiryNotificationService(), usageData: usageData, onComplete: onComplete)
    }

    @Test func firstRunPagesThroughToTheRemindersStep() async {
        var completed = false
        let viewModel = makeViewModel(onComplete: { completed = true })

        #expect(viewModel.pages.last == .reminders)
        for _ in 0..<(viewModel.pages.count - 1) {
            await viewModel.primaryAction()
        }
        #expect(viewModel.currentPage == .reminders)
        #expect(viewModel.isLastPage)
        #expect(!completed)
    }

    @Test func shareQuestionComesBetweenCookAndReminders() {
        let pages = makeViewModel().pages
        let share = pages.firstIndex(of: .share)
        #expect(share == pages.firstIndex(of: .cook).map { $0 + 1 })
        #expect(share == pages.firstIndex(of: .reminders).map { $0 - 1 })
    }

    @Test func skipJumpsToTheShareQuestionOnFirstRun() {
        let viewModel = makeViewModel()
        viewModel.skipIntro()
        #expect(viewModel.currentPage == .share)
        #expect(!viewModel.showsSkip)
    }

    @Test func sharingTurnsCollectionOnAndMovesToReminders() async {
        let settings = AppSettings.ephemeral()
        let usageData = RecordingUsageDataCollection()
        let viewModel = makeViewModel(settings: settings, usageData: usageData)
        viewModel.skipIntro()

        await viewModel.primaryAction()

        #expect(settings.shareUsageData)
        #expect(usageData.calls == [true])
        #expect(viewModel.currentPage == .reminders)
    }

    @Test func notSharingKeepsCollectionOffAndMovesToReminders() {
        let settings = AppSettings.ephemeral()
        let usageData = RecordingUsageDataCollection()
        let viewModel = makeViewModel(settings: settings, usageData: usageData)
        viewModel.skipIntro()

        viewModel.shareUsageData(false)

        #expect(!settings.shareUsageData)
        #expect(usageData.calls == [false])
        #expect(viewModel.currentPage == .reminders)
    }

    @Test func replayHasNoQuestionsAndFinishesOnLastPage() async {
        var completed = false
        let usageData = RecordingUsageDataCollection()
        let viewModel = makeViewModel(mode: .replay, usageData: usageData, onComplete: { completed = true })

        #expect(!viewModel.pages.contains(.share))
        #expect(!viewModel.pages.contains(.reminders))
        for _ in 0..<viewModel.pages.count {
            await viewModel.primaryAction()
        }
        #expect(completed)
        #expect(usageData.calls.isEmpty)
    }
}
