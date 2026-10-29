//
//  ItemFormView.swift
//  ZeroBite
//

import SwiftUI

struct ItemFormView: View {
    @Bindable var viewModel: ItemFormViewModel
    let onCancel: () -> Void

    @FocusState private var quantityFocused: Bool
    @FocusState private var unitFocused: Bool

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.xl) {
                    DetailTitle(overline: viewModel.isEditing ? String(localized: "Edit") : String(localized: "Add to your fridge"), title: viewModel.title)

                    LabeledInput(
                        label: String(localized: "Name"),
                        placeholder: String(localized: "Greek yogurt"),
                        text: $viewModel.draft.name,
                        accessibilityID: "itemForm.name",
                        autoFocus: viewModel.draft.name.isEmpty
                    )

                    LabeledInput(
                        label: String(localized: "Brand (optional, helps spot recalls)"),
                        placeholder: String(localized: "Yeo Valley", comment: "Placeholder: an example food brand"),
                        text: Binding(
                            get: { viewModel.draft.brand ?? "" },
                            set: { viewModel.draft.brand = $0.trimmingCharacters(in: .whitespaces).isEmpty ? nil : $0 }
                        ),
                        accessibilityID: "itemForm.brand"
                    )

                    VStack(alignment: .leading, spacing: Spacing.sm) {
                        Overline(String(localized: "Category"))
                        ChipFilter(options: FoodCategory.allCases, selection: $viewModel.draft.category) { $0.label }
                    }

                    VStack(alignment: .leading, spacing: Spacing.sm) {
                        Overline(String(localized: "Stored in"))
                        ChipFilter(options: StorageLocation.allCases, selection: $viewModel.draft.storage) { $0.label }
                    }

                    quantity

                    CardSection(String(localized: "Expiry"), footer: String(localized: "No date on the label? Use the typical shelf life for \(viewModel.draft.category.label.lowercased()).")) {
                        InfoRow(String(localized: "Expires on"), symbol: "calendar") {
                            DatePicker("Expires on", selection: $viewModel.draft.expiresAt, displayedComponents: .date)
                                .labelsHidden()
                        }
                        ActionRow(title: String(localized: "Use typical shelf life (\(viewModel.defaultShelfLifeDays) days)"), symbol: "wand.and.stars", tint: Constants.BrandOrange) {
                            viewModel.applyDefaultShelfLife()
                        }
                    }

                    LabeledInput(label: String(localized: "Notes"), placeholder: String(localized: "Opened on Monday, half left"), text: $viewModel.draft.notes, axis: .vertical)

                    if let barcode = viewModel.draft.barcode {
                        CardSection {
                            InfoRow(String(localized: "Barcode"), symbol: "barcode", value: barcode)
                        }
                    }

                    if let error = viewModel.errorMessage {
                        Text(error)
                            .font(.app(.subheadline, weight: .medium))
                            .foregroundStyle(Constants.StatusExpired)
                    }
                }
                .padding(.horizontal, Spacing.gutter)
                .padding(.bottom, Spacing.xl)
            }
            .scrollDismissesKeyboard(.interactively)

            PrimaryButton(viewModel.isEditing ? String(localized: "Save changes") : String(localized: "Add to fridge"), isEnabled: viewModel.canSave) {
                viewModel.save()
            }
            .accessibilityIdentifier("itemForm.save")
            .padding(.horizontal, Spacing.gutter)
            .padding(.vertical, Spacing.sm)
            .background(Constants.BackgroundPrimary)
        }
        .pageBackground()
        .topBar(.close(onCancel))
    }

    private var quantity: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Overline(String(localized: "Quantity"))
            HStack(spacing: Spacing.sm) {
                TextField("Amount", value: $viewModel.draft.quantity, format: .number)
                    .keyboardType(.decimalPad)
                    .font(.app(.body))
                    .focused($quantityFocused)
                    .inputChrome(isFocused: quantityFocused)
                TextField("Unit", text: Binding(
                    get: { ItemDraft.unitLabel(viewModel.draft.unit) },
                    set: { viewModel.draft.unit = ItemDraft.unitCode(forLabel: $0) }
                ))
                    .font(.app(.body))
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .focused($unitFocused)
                    .inputChrome(isFocused: unitFocused)
                    .frame(width: 110)
            }
            ChipFilter(options: ItemDraft.unitSuggestions, selection: $viewModel.draft.unit) { ItemDraft.unitLabel($0) }
        }
    }
}

#Preview {
    NavigationStack {
        InventoryFactory.makeForm(mode: .add, draft: ItemDraft(), dependencies: .preview(), onCancel: {}, onSaved: { _ in })
    }
}
