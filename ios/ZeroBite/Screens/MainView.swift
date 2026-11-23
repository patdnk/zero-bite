//
//  MainView.swift
//  ZeroBite
//
//  The tabs, bound to MainCoordinator, plus the sheet and the "how it works" replay over them.
//

import SwiftUI

struct MainView: View {
    let coordinator: MainCoordinator

    private var dependencies: AppDependencies { coordinator.dependencies }

    @State private var isKeyboardVisible = false

    var body: some View {
        VStack(spacing: 0) {
            // Every tab stays alive in the ZStack so scroll position and search text survive switching.
            ZStack {
                ForEach(MainCoordinator.Tab.allCases, id: \.self) { tab in
                    let isSelected = coordinator.state.selectedTab == tab
                    tabContent(for: tab)
                        .opacity(isSelected ? 1 : 0)
                        .allowsHitTesting(isSelected)
                        .accessibilityHidden(!isSelected)
                }
            }
            // Root screens hide the navigation bar, so cover the status bar ourselves.
            .overlay(alignment: .top) {
                GeometryReader { geo in
                    Constants.BackgroundPrimary
                        .frame(height: geo.safeAreaInsets.top)
                        .ignoresSafeArea(edges: .top)
                }
                .allowsHitTesting(false)
            }

            // Hidden while typing so the bottom search field sits directly on the keyboard.
            if !isKeyboardVisible {
                AppTabBar(selection: Binding(
                    get: { coordinator.state.selectedTab },
                    set: { coordinator.eventLoop(event: .didSelectTab($0)) }
                ))
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)) { _ in
            isKeyboardVisible = true
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { _ in
            isKeyboardVisible = false
        }
        .tint(Constants.BrandOrange)
        .fullScreenCover(isPresented: Binding(
            get: { coordinator.state.isShowingHowItWorks },
            set: { if !$0 { coordinator.eventLoop(event: .didFinishHowItWorks) } }
        )) {
            WelcomeFactory.make(mode: .replay, dependencies: dependencies) {
                coordinator.eventLoop(event: .didFinishHowItWorks)
            }
        }
        .sheet(item: Binding(
            get: { coordinator.state.sheet },
            set: { if $0 == nil { coordinator.eventLoop(event: .didDismissSheet) } }
        )) { sheet in
            sheetContent(for: sheet)
        }
    }

    @ViewBuilder
    private func tabContent(for tab: MainCoordinator.Tab) -> some View {
        switch tab {
        case .fridge:
            InventoryFactory.makeList(coordinator: coordinator.inventory, dependencies: dependencies)
        case .recipes:
            RecipesFactory.makeList(coordinator: coordinator.recipes, dependencies: dependencies)
        case .settings:
            SettingsFactory.make(coordinator: coordinator.settings, dependencies: dependencies)
        }
    }

    @ViewBuilder
    private func sheetContent(for sheet: MainCoordinator.Sheet) -> some View {
        switch sheet {
        case .scan:
            if let scan = coordinator.scan {
                ScanFactory.makeFlow(coordinator: scan, dependencies: dependencies)
            }
        case .addItem(let draft):
            NavigationStack {
                InventoryFactory.makeForm(mode: .add, draft: draft, dependencies: dependencies,
                                          onCancel: { coordinator.eventLoop(event: .didDismissSheet) },
                                          onSaved: { _ in coordinator.eventLoop(event: .didDismissSheet) })
            }
        case .editItem(let id):
            NavigationStack {
                if let item = dependencies.inventory.item(id: id) {
                    InventoryFactory.makeForm(mode: .edit(id), draft: ItemDraft(item: item), dependencies: dependencies,
                                              onCancel: { coordinator.eventLoop(event: .didDismissSheet) },
                                              onSaved: { _ in coordinator.eventLoop(event: .didDismissSheet) })
                } else {
                    ContentUnavailableView("Item not found", systemImage: "questionmark.circle")
                }
            }
        case .cooked(let recipeID):
            RecipesFactory.makeCooked(recipeID: recipeID, dependencies: dependencies, onDone: { coordinator.eventLoop(event: .didDismissSheet) })
                .presentationDetents([.medium, .large])
        }
    }
}

#Preview {
    MainView(coordinator: MainCoordinator(dependencies: .preview()))
}
