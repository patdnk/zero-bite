//
//  ScanFactory.swift
//  ZeroBite
//
//  Factory for the add-food flow (scan → review → form).
//

import SwiftUI

@MainActor
enum ScanFactory {

    static func makeFlow(dependencies: AppDependencies, onFinished: @escaping @MainActor () -> Void) -> some View {
        ViewModelHost(ScanCoordinator(
            supportsLiveScanning: LiveScannerView.isSupported,
            productLookup: dependencies.productLookup,
            onFinished: onFinished
        )) { coordinator in
            ScanFlowView(coordinator: coordinator, dependencies: dependencies)
        }
    }
}
