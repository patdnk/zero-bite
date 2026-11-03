//
//  UsageDataCollection.swift
//  ZeroBite
//
//  Turns Firebase Analytics and Crashlytics collection on or off to match the user's
//  "share usage data" choice. Firebase isn't even configured until the user agrees, because
//  configuring it registers an installation with Google straight away.
//

import FirebaseAnalytics
import FirebaseCore
import FirebaseCrashlytics
import Foundation

@MainActor
protocol UsageDataCollection {
    func setEnabled(_ enabled: Bool)
}

struct FirebaseUsageDataCollection: UsageDataCollection {

    func setEnabled(_ enabled: Bool) {
        #if !DEBUG
        // Release builds only. GoogleService-Info.plist is gitignored (the repo is public), so a
        // clone without it simply runs without Firebase.
        if FirebaseApp.app() == nil {
            guard enabled, Self.hasConfigFile else { return }
            FirebaseApp.configure()
        }
        Analytics.setAnalyticsCollectionEnabled(enabled)
        let crashlytics = Crashlytics.crashlytics()
        crashlytics.setCrashlyticsCollectionEnabled(enabled)
        if !enabled {
            crashlytics.deleteUnsentReports()
        }
        #endif
    }

    #if !DEBUG
    private static var hasConfigFile: Bool {
        Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil
    }
    #endif
}
