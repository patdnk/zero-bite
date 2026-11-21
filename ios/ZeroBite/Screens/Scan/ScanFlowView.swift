//
//  ScanFlowView.swift
//  ZeroBite
//

import PhotosUI
import SwiftUI

struct ScanFlowView: View {
    let coordinator: ScanCoordinator
    let dependencies: AppDependencies

    @State private var pickerItem: PhotosPickerItem?

    var body: some View {
        NavigationStack(path: path) {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.lg) {
                    DetailTitle(overline: String(localized: "Scan or type"), title: String(localized: "Add food"))

                    VStack(spacing: Spacing.sm) {
                        if coordinator.supportsLiveScanning {
                            Button { coordinator.send(.liveScanTapped) } label: {
                                ScanOptionRow(symbol: "barcode.viewfinder", title: String(localized: "Scan with the camera"), subtitle: String(localized: "Reads dates and barcodes live"), isPrimary: true)
                            }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("scan.live")
                        }
                        PhotosPicker(selection: $pickerItem, matching: .images) {
                            ScanOptionRow(symbol: "photo.on.rectangle", title: String(localized: "Choose a photo of the label"), subtitle: String(localized: "Pick a picture of the packaging"))
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("scan.photo")
                        Button { coordinator.send(.manualEntryTapped(now: .now)) } label: {
                            ScanOptionRow(symbol: "square.and.pencil", title: String(localized: "Type it in"), subtitle: String(localized: "No label to read"))
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("scan.manual")
                    }

                    Text("Point at the best-before or use-by date. A barcode helps fill in the product name.")
                        .font(.app(.caption))
                        .foregroundStyle(Constants.TextSecondary)
                }
                .padding(.horizontal, Spacing.gutter)
                .padding(.bottom, Spacing.xl)
            }
            .pageBackground()
            .topBar(.close { coordinator.send(.closeTapped) })
            .navigationDestination(for: ScanRoute.self) { route in
                destination(for: route)
            }
        }
        // The sheet was swiped away: stop any OCR or barcode lookup still running.
        .onDisappear { coordinator.send(.dismissed) }
        .loadingOverlay(coordinator.state.status == .processing, message: String(localized: "Reading the label…"))
        .onChange(of: pickerItem) { _, item in
            guard let item else { return }
            pickerItem = nil
            Task {
                if let data = try? await item.loadTransferable(type: Data.self) {
                    coordinator.send(.photoChosen(data))
                } else {
                    coordinator.send(.photoLoadFailed)
                }
            }
        }
        .alert(
            "Couldn't read that",
            isPresented: Binding(
                get: { failure != nil },
                set: { if !$0 { coordinator.send(.errorDismissed) } }
            )
        ) {
            Button("OK") { coordinator.send(.errorDismissed) }
        } message: {
            Text(failure?.message ?? "")
        }
    }

    /// The stack's path, owned by the state machine. A back button or swipe becomes an event.
    private var path: Binding<[ScanRoute]> {
        Binding(get: { coordinator.state.path }, set: { coordinator.send(.pathChanged($0)) })
    }

    private var failure: ScanFlow.Failure? {
        if case .failed(let failure) = coordinator.state.status { return failure }
        return nil
    }

    @ViewBuilder
    private func destination(for route: ScanRoute) -> some View {
        switch route {
        case .live:
            LiveScannerView(onFinish: { coordinator.send(.liveScanFinished($0)) })
        case .review:
            ScanReviewView(coordinator: coordinator)
        case .form(let draft):
            InventoryFactory.makeForm(
                mode: .add,
                draft: draft,
                dependencies: dependencies,
                onCancel: { coordinator.send(.closeTapped) },
                onSaved: { _ in coordinator.send(.itemSaved) }
            )
        }
    }
}

private struct ScanOptionRow: View {
    let symbol: String
    let title: String
    let subtitle: String
    var isPrimary = false

    var body: some View {
        HStack(spacing: Spacing.md) {
            Image(systemName: symbol)
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(isPrimary ? .white : Constants.BrandOrange)
                .frame(width: 44, height: 44)
                .background(isPrimary ? Constants.BrandOrange : Constants.BrandOrange.opacity(0.12),
                            in: RoundedRectangle(cornerRadius: Radius.control))
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.app(.subheadline, weight: .semibold)).foregroundStyle(Constants.TextPrimary)
                Text(subtitle).font(.app(.caption)).foregroundStyle(Constants.TextSecondary)
            }
            Spacer()
            Image(systemName: "chevron.right")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Constants.TextMuted)
        }
        .cardStyle()
        .contentShape(Rectangle())
    }
}

#Preview {
    ScanFactory.makeFlow(dependencies: .preview(), onFinished: {})
}
