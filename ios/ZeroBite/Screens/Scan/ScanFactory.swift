//
//  ScanFactory.swift
//  ZeroBite
//
//  Factory for the add-food flow (scan → review → form). MainCoordinator starts the
//  ScanCoordinator when the sheet opens and stops it when the sheet goes away.
//

import SwiftUI

@MainActor
enum ScanFactory {

    static func makeCoordinator(parent: MainCoordinator?, dependencies: AppDependencies) -> ScanCoordinator {
        ScanCoordinator(
            parent: parent,
            supportsLiveScanning: LiveScannerView.isSupported,
            productLookup: dependencies.productLookup
        )
    }

    static func makeFlow(coordinator: ScanCoordinator, dependencies: AppDependencies) -> some View {
        ScanFlowView(coordinator: coordinator, dependencies: dependencies)
    }
}
