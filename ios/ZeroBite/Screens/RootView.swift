//
//  RootView.swift
//  ZeroBite
//
//  Root view driven by AppCoordinator's phase: Launch → Welcome → Main.
//

import SwiftUI

struct RootView: View {
    let coordinator: AppCoordinator

    var body: some View {
        Group {
            switch coordinator.state.phase {
            case .launch:
                SplashView(onFinish: { coordinator.finishLaunch() })
            case .welcome:
                WelcomeFactory.make(dependencies: coordinator.dependencies) {
                    coordinator.eventLoop(event: .didFinishWelcome)
                }
            case .main:
                if let main = coordinator.main {
                    MainView(coordinator: main)
                }
            }
        }
        .font(.app(.body))
        .animation(.easeInOut(duration: 0.3), value: coordinator.state.phase)
    }
}

#Preview {
    RootView(coordinator: AppCoordinator(dependencies: .preview()))
}
