//
//  SettingsView.swift
//  ZeroBite
//

import SwiftUI

struct SettingsView: View {
    @Bindable var viewModel: SettingsViewModel
    let dependencies: AppDependencies

    private enum Route: Hashable { case alerts }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.xl) {
                    ScreenHeader(overline: "Zero Bite \(viewModel.appVersion)", title: String(localized: "Settings"))

                    CardSection(String(localized: "Reminders"), footer: String(localized: "You'll get a heads-up before the date, one on the day, and one if it slips past.")) {
                        Toggle(isOn: Binding(
                            get: { viewModel.notificationsEnabled },
                            set: { enabled in Task { await viewModel.setNotificationsEnabled(enabled) } }
                        )) {
                            Text("Expiry reminders").font(.app(.subheadline, weight: .medium))
                        }
                        .tint(Constants.BrandOrange)
                        .disabled(viewModel.isUpdatingNotifications)
                        .accessibilityIdentifier("settings.notificationsToggle")

                        if viewModel.notificationState == .denied {
                            ActionRow(title: String(localized: "Allow in Settings app"), symbol: "gear", tint: Constants.BrandOrange) {
                                viewModel.openSystemSettings()
                            }
                        }

                        Stepper(value: $viewModel.leadDays, in: 1...14) {
                            Text("Remind me \(viewModel.leadDays) days before").font(.app(.subheadline))
                        }
                        InfoRow(String(localized: "At"), symbol: "clock") {
                            DatePicker("At", selection: $viewModel.notifyTime, displayedComponents: .hourAndMinute)
                                .labelsHidden()
                        }
                        ActionRow(title: String(localized: "Send a test notification"), symbol: "bell", tint: Constants.BrandOrange) {
                            Task { await viewModel.sendTestNotification() }
                        }
                        .disabled(!viewModel.notificationsEnabled)
                        .opacity(viewModel.notificationsEnabled ? 1 : 0.45)
                    }

                    CardSection(String(localized: "Food safety"), footer: String(localized: "Checks the Food Standards Agency for UK recalls and tells you if any of your food might be affected.")) {
                        Toggle(isOn: $viewModel.recallChecksEnabled) {
                            Text("Check UK food recalls").font(.app(.subheadline, weight: .medium))
                        }
                        .tint(Constants.BrandOrange)
                        .accessibilityIdentifier("settings.recallsToggle")

                        NavigationLink(value: Route.alerts) {
                            HStack(spacing: Spacing.md) {
                                Image(systemName: "exclamationmark.triangle")
                                    .font(.system(size: 14, weight: .semibold))
                                    .frame(width: 20)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Food alerts").font(.app(.subheadline, weight: .medium))
                                    Text(viewModel.alertsSummary)
                                        .font(.app(.caption))
                                        .foregroundStyle(Constants.TextSecondary)
                                }
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(Constants.TextMuted)
                            }
                            .foregroundStyle(Constants.TextPrimary)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("settings.foodAlerts")
                    }

                    VStack(alignment: .leading, spacing: Spacing.sm) {
                        Overline(String(localized: "Your allergies"))
                        FlowLayout(spacing: 6) {
                            ForEach(Allergen.allCases) { allergen in
                                let isOn = viewModel.isSelected(allergen)
                                Button { viewModel.toggle(allergen) } label: {
                                    Text(allergen.label)
                                        .font(.app(.subheadline, weight: isOn ? .semibold : .regular))
                                        .foregroundStyle(isOn ? Constants.OnInk : Constants.TextSecondary)
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 7)
                                        .background(isOn ? Constants.TextPrimary : Constants.ChipFill,
                                                    in: RoundedRectangle(cornerRadius: Radius.control))
                                }
                                .buttonStyle(.plain)
                                .accessibilityAddTraits(isOn ? .isSelected : [])
                            }
                        }
                        Text("Allergy alerts for these show under Food alerts → For you.")
                            .font(.app(.caption))
                            .foregroundStyle(Constants.TextSecondary)
                    }

                    CardSection(String(localized: "My food"), footer: String(localized: "Recipes that use food inside this window are ranked higher.")) {
                        Stepper(value: $viewModel.soonThresholdDays, in: 1...14) {
                            Text("“Expiring soon” means within \(viewModel.soonThresholdDays) days").font(.app(.subheadline))
                        }
                    }

                    CardSection(String(localized: "Data")) {
                        InfoRow(String(localized: "In My food"), symbol: "refrigerator", value: "\(viewModel.activeItemCount)")
                        InfoRow(String(localized: "Used up"), symbol: "checkmark", value: "\(viewModel.usedItemCount)")
                        ActionRow(title: String(localized: "Add sample food"), symbol: "sparkles") { viewModel.loadSampleData() }
                        ActionRow(title: String(localized: "Clear used-up history"), symbol: "clock.arrow.circlepath") { viewModel.clearUsedHistory() }
                            .disabled(viewModel.usedItemCount == 0)
                            .opacity(viewModel.usedItemCount == 0 ? 0.45 : 1)
                        ActionRow(title: String(localized: "Delete everything"), symbol: "trash", tint: Constants.StatusExpired) { viewModel.deleteEverything() }
                            .disabled(viewModel.activeItemCount == 0 && viewModel.usedItemCount == 0)
                    }

                    CardSection(String(localized: "About"), footer: String(localized: "Dates and barcodes are read on your device with Apple Vision. Product names come from Open Food Facts. Food alerts come from the Food Standards Agency under the Open Government Licence v3.0.")) {
                        ActionRow(title: String(localized: "How Zero Bite works"), symbol: "book.pages", tint: Constants.BrandOrange) {
                            viewModel.showHowItWorks()
                        }
                        .accessibilityIdentifier("settings.howItWorks")
                        InfoRow(String(localized: "Version"), value: viewModel.appVersion)
                        InfoRow(String(localized: "Recipes"), value: "\(viewModel.recipeCount)")
                    }
                }
                .padding(.horizontal, Spacing.gutter)
                .padding(.bottom, Spacing.xl)
            }
            .pageBackground()
            .toolbar(.hidden, for: .navigationBar)
            .task { await viewModel.refresh() }
            .navigationDestination(for: Route.self) { route in
                switch route {
                case .alerts: FoodAlertsFactory.makeList(dependencies: dependencies)
                }
            }
        }
    }
}

#Preview {
    let dependencies = AppDependencies.preview()
    let coordinator = AppCoordinator(dependencies: dependencies)
    SettingsFactory.make(coordinator: coordinator.settingsCoordinator, dependencies: dependencies)
}
