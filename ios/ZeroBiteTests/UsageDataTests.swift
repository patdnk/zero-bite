//
//  UsageDataTests.swift
//  ZeroBiteTests
//
//  The "share usage data" setting: off by default, persisted, and passed on to the collector.
//

import Foundation
import Testing
@testable import ZeroBite

@MainActor
struct UsageDataTests {

    @Test func shareUsageDataIsOffByDefaultAndPersists() {
        let suiteName = "zerobite.tests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defer { defaults.removePersistentDomain(forName: suiteName) }

        let settings = AppSettings(defaults: defaults)
        #expect(!settings.shareUsageData)

        settings.shareUsageData = true
        #expect(AppSettings(defaults: defaults).shareUsageData)
    }

    @Test func launchWithSharingOffNeverTurnsCollectionOn() {
        let usageData = RecordingUsageDataCollection()
        let dependencies = AppDependencies.preview(withSampleData: false, usageData: usageData)

        dependencies.applyUsageDataChoice()

        #expect(!usageData.calls.contains(true))
    }

    @Test func launchAppliesTheSavedChoice() {
        let usageData = RecordingUsageDataCollection()
        let dependencies = AppDependencies.preview(withSampleData: false, usageData: usageData)

        dependencies.applyUsageDataChoice()
        dependencies.settings.shareUsageData = true
        dependencies.applyUsageDataChoice()

        #expect(usageData.calls == [false, true])
    }

    @Test func settingsToggleTellsTheCollectorStraightAway() {
        let usageData = RecordingUsageDataCollection()
        let dependencies = AppDependencies.preview(withSampleData: false, usageData: usageData)
        let viewModel = SettingsViewModel(dependencies: dependencies, coordinator: SettingsCoordinator(inventory: dependencies.inventory))

        #expect(!viewModel.shareUsageData)
        viewModel.shareUsageData = true
        #expect(dependencies.settings.shareUsageData)
        viewModel.shareUsageData = false
        #expect(!dependencies.settings.shareUsageData)
        #expect(usageData.calls == [true, false])
    }
}
