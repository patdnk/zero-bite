//
//  ZeroBiteApp.swift
//  ZeroBite
//

import SwiftUI
import UserNotifications

@main
struct ZeroBiteApp: App {
    @Environment(\.scenePhase) private var scenePhase

    @State private var coordinator: AppCoordinator
    private let notificationDelegate = NotificationDelegate()

    init() {
        AppFont.registerBundledFonts()
        AppFont.configureAppearance()
        let dependencies = AppDependencies.live()
        // Firebase is configured here only if the user has already agreed to share usage data.
        dependencies.applyUsageDataChoice()
        let coordinator = AppCoordinator(dependencies: dependencies)
        _coordinator = State(initialValue: coordinator)

        notificationDelegate.onOpenItem = { id in
            Task { @MainActor in coordinator.eventLoop(event: .didOpenItemNotification(id)) }
        }
        UNUserNotificationCenter.current().delegate = notificationDelegate

        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("--sample-data"), dependencies.inventory.items.isEmpty {
            dependencies.inventory.loadSampleData()
        }
        #endif
    }

    var body: some Scene {
        WindowGroup {
            RootView(coordinator: coordinator)
        }
        .onChange(of: scenePhase) { _, phase in
            guard phase == .active else { return }
            coordinator.eventLoop(event: .didBecomeActive)
        }
    }
}
