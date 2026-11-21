//
//  ScanReviewView.swift
//  ZeroBite
//

import SwiftUI

struct ScanReviewView: View {
    let coordinator: ScanCoordinator

    @State private var showsLines = false

    var body: some View {
        if let review = coordinator.state.review {
            content(review)
        }
    }

    private func content(_ review: ScanFlow.Review) -> some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.xl) {
                    DetailTitle(overline: String(localized: "Scanned label"), title: String(localized: "Check the details"))

                    VStack(alignment: .leading, spacing: Spacing.sm) {
                        LabeledInput(label: String(localized: "Name"), placeholder: String(localized: "Product name"), text: Binding(get: { coordinator.state.review?.name ?? "" }, set: { coordinator.send(.nameChanged($0)) }), accessibilityID: "review.name")
                        if review.isLookingUp {
                            HStack(spacing: Spacing.sm) {
                                ProgressView().controlSize(.small)
                                Text("Looking up barcode…")
                            }
                            .font(.app(.caption))
                            .foregroundStyle(Constants.TextSecondary)
                        }
                    }

                    VStack(alignment: .leading, spacing: Spacing.sm) {
                        Overline(String(localized: "Category"))
                        ChipFilter(options: FoodCategory.allCases, selection: Binding(get: { coordinator.state.review?.category ?? .other }, set: { coordinator.send(.categoryChanged($0)) })) { $0.label }
                    }

                    if review.product != nil || review.result.barcode != nil {
                        CardSection(String(localized: "Product")) {
                            if let product = review.product {
                                InfoRow(String(localized: "Found"), symbol: "checkmark.seal", value: [product.brand, product.name].compactMap { $0 }.joined(separator: " · "))
                            }
                            if let barcode = review.result.barcode {
                                InfoRow(String(localized: "Barcode"), symbol: "barcode", value: barcode)
                            }
                        }
                    }

                    expiry(review)

                    if !review.result.lines.isEmpty {
                        CardSection(String(localized: "Text read from the label")) {
                            ActionRow(title: showsLines ? String(localized: "Hide \(review.result.lines.count) lines") : String(localized: "Show \(review.result.lines.count) lines"),
                                      symbol: showsLines ? "chevron.up" : "chevron.down",
                                      tint: Constants.TextSecondary) {
                                withAnimation(.snappy) { showsLines.toggle() }
                            }
                            if showsLines {
                                VStack(alignment: .leading, spacing: 4) {
                                    ForEach(review.result.lines.indices, id: \.self) { index in
                                        Text(review.result.lines[index])
                                            .font(.app(.footnote).monospaced())
                                            .foregroundStyle(Constants.TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, Spacing.gutter)
                .padding(.bottom, Spacing.xl)
            }
            .scrollDismissesKeyboard(.interactively)

            PrimaryButton(String(localized: "Continue"), isEnabled: review.canContinue) { coordinator.send(.continueTapped(now: .now)) }
                .accessibilityIdentifier("review.continue")
                .padding(.horizontal, Spacing.gutter)
                .padding(.vertical, Spacing.sm)
                .background(Constants.BackgroundPrimary)
        }
        .pageBackground()
        .topBar(.back)
    }

    @ViewBuilder
    private func expiry(_ review: ScanFlow.Review) -> some View {
        if review.result.candidates.isEmpty {
            CardSection(String(localized: "Expiry date")) {
                Text("No date was found on the label. You can set one on the next screen.")
                    .font(.app(.subheadline))
                    .foregroundStyle(Constants.TextSecondary)
            }
        } else {
            CardSection(String(localized: "Expiry date"), footer: String(localized: "Pick the best-before or use-by date. Most likely first.")) {
                ForEach(review.result.candidates) { candidate in
                    Button { coordinator.send(.candidateSelected(candidate.id)) } label: {
                        candidateRow(candidate, isSelected: review.selectedCandidateID == candidate.id)
                    }
                    .buttonStyle(.plain)
                }
                ActionRow(title: String(localized: "None of these"), symbol: "xmark", tint: Constants.TextSecondary) {
                    coordinator.send(.candidateSelected(nil))
                }
            }
        }
    }

    private func candidateRow(_ candidate: DateCandidate, isSelected: Bool) -> some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(candidate.date.formatted(date: .long, time: .omitted))
                    .font(.app(.subheadline, weight: .semibold))
                    .foregroundStyle(Constants.TextPrimary)
                Text("“\(candidate.sourceLine)”", comment: "A line of label text in quotation marks")
                    .font(.app(.caption))
                    .foregroundStyle(Constants.TextSecondary)
                    .lineLimit(1)
            }
            Spacer()
            ConfidenceDots(confidence: candidate.confidence)
            Image(systemName: isSelected ? "checkmark.square.fill" : "square")
                .font(.system(size: 18))
                .foregroundStyle(isSelected ? Constants.BrandOrange : Constants.TextMuted)
        }
        .contentShape(Rectangle())
    }
}

private struct ConfidenceDots: View {
    let confidence: Double

    var body: some View {
        HStack(spacing: 3) {
            ForEach(0..<3, id: \.self) { index in
                Rectangle()
                    .fill(Double(index) / 3 < confidence ? Constants.BrandOrange : Constants.ChipFill)
                    .frame(width: 4, height: 10)
            }
        }
        .accessibilityLabel("Confidence \(Int(confidence * 100)) percent")
    }
}

#Preview {
    let now = Date.now
    let result = LabelScanner.analyse(
        lines: ["TESCO", "SEMI SKIMMED MILK", "USE BY 12 SEP 2026", "2 PINTS 1.136L", "PACKED 01/09/2026"],
        barcode: "5000119000000",
        now: now
    )
    let coordinator = ScanCoordinator(supportsLiveScanning: true, productLookup: AppDependencies.preview().productLookup, onFinished: {})
    coordinator.send(.liveScanTapped)
    coordinator.send(.liveScanFinished(result))
    return NavigationStack {
        ScanReviewView(coordinator: coordinator)
    }
}
