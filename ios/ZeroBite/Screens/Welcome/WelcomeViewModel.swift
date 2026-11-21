//
//  WelcomeViewModel.swift
//  ZeroBite
//
//  Onboarding: a short paged explainer of how the app works, ending (on first run) with the
//  usage data question and the notification opt-in. Can be replayed from Settings without them.
//

import Foundation
import Observation

@MainActor
@Observable
final class WelcomeViewModel {

    enum Mode: Equatable {
        /// First launch: explainer pages, then ask about usage data and notification permission.
        case firstRun
        /// Opened from Settings: explainer pages only.
        case replay
    }

    enum Page: Hashable, CaseIterable {
        case welcome, scan, remind, cook, share, reminders

        /// Pages that ask the user something; first run only.
        var isQuestion: Bool { self == .share || self == .reminders }
    }

    enum Step: Equatable {
        case intro
        case requesting
        case denied
    }

    let mode: Mode
    let pages: [Page]
    var currentPage: Page
    private(set) var step: Step = .intro

    private let settings: AppSettings
    private let notifications: ExpiryNotificationService
    private let usageData: any UsageDataCollection
    private let onComplete: () -> Void

    init(
        mode: Mode = .firstRun,
        settings: AppSettings,
        notifications: ExpiryNotificationService,
        usageData: any UsageDataCollection,
        onComplete: @escaping () -> Void
    ) {
        self.mode = mode
        self.settings = settings
        self.notifications = notifications
        self.usageData = usageData
        self.onComplete = onComplete
        pages = mode == .firstRun ? Page.allCases : Page.allCases.filter { !$0.isQuestion }
        currentPage = pages[0]
    }

    // MARK: - Paging

    var pageIndex: Int { pages.firstIndex(of: currentPage) ?? 0 }
    var isLastPage: Bool { currentPage == pages.last }
    var isRequesting: Bool { step == .requesting }
    /// Skip (or Close) is offered on the explainer pages only.
    var showsSkip: Bool { !isLastPage && !currentPage.isQuestion }

    var primaryTitle: String {
        switch currentPage {
        case .reminders: step == .denied ? String(localized: "Continue") : String(localized: "Turn on reminders")
        case .share: String(localized: "Share usage data")
        case .cook: mode == .replay ? String(localized: "Done") : String(localized: "Next")
        case .welcome: String(localized: "Show me how")
        default: String(localized: "Next")
        }
    }

    /// Advances, or finishes on the last page (asking for permission on first run).
    func primaryAction() async {
        switch currentPage {
        case .reminders:
            if step == .denied {
                continueAfterDenied()
            } else {
                await enableReminders()
            }
        case .share:
            shareUsageData(true)
        default:
            if isLastPage {
                finish()
            } else {
                currentPage = pages[pageIndex + 1]
            }
        }
    }

    /// Jumps past the explainer: to the first question on first run, or closes when replaying.
    func skipIntro() {
        if mode == .firstRun, pages.contains(.share) {
            currentPage = .share
        } else {
            finish()
        }
    }

    // MARK: - Usage data

    /// Records the answer on the share page and moves on to the reminders question.
    func shareUsageData(_ share: Bool) {
        settings.shareUsageData = share
        usageData.setEnabled(share)
        currentPage = .reminders
    }

    // MARK: - Notifications

    func enableReminders() async {
        step = .requesting
        let granted = await notifications.requestAuthorization()
        settings.notificationsEnabled = granted
        if granted {
            onComplete()
        } else {
            step = .denied
        }
    }

    func skip() {
        settings.notificationsEnabled = false
        onComplete()
    }

    func continueAfterDenied() {
        onComplete()
    }

    func finish() {
        onComplete()
    }
}
