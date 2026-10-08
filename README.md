# Zero Bite

An iOS app that catalogues what's in your fridge, freezer and cupboard, reminds you before
food expires, and ranks recipes by what you can cook right now — expiring items first.

## Features

- **Scan labels** — live camera scanning (VisionKit) reads best-before / use-by dates and
  barcodes; on the simulator, pick a photo of the label instead (Vision OCR).
- **Barcode lookup** — product name and category from Open Food Facts (no API key).
- **Expiry parsing** — UK/EU date formats (`12/09/26`, `12 SEP 2026`, `BEST BEFORE END OCT 2026`,
  `EXP 120926`…), keyword-aware so "use by" beats "packed on".
- **Reminders** — local notifications a few days before, on the day, and after expiry.
  Re-planned automatically whenever the fridge changes.
- **Recipes** — 35 bundled recipes matched against your inventory with synonym-aware
  ingredient matching (cheddar ≈ cheese, red pepper ≈ bell pepper) and store-cupboard staples.
  "I cooked this" marks the used items as consumed.

## Repository layout

| Folder | What |
|---|---|
| `ios/` | The SwiftUI app (`project.yml`, `ZeroBite/`, `ZeroBiteTests/`) |
| `android/` | The Android app (Gradle: `:core` pure logic, `:app` Compose UI) |
| `shared-assets/` | Data both apps bundle (`recipes.json`) |
| `testdata/` | Golden fixtures run by both test suites — the cross-platform behaviour contract |
| `design/`, `scripts/` | Brand mark, icon and screenshot rendering, font fetching |
| `appstore/` | App Store listing |
| `docs/` | Website (GitHub Pages) |
| `specs/` | Design specs and implementation plans |

## Architecture

MVVM-C, mirroring `drivvy-ios`:

| Layer | Where | Notes |
|---|---|---|
| Flow state machine | `Coordination/AppFlowController` | `launch → welcome → main` |
| App coordinator | `Coordination/AppCoordinator` | tabs, sheets, alerts, deep links |
| Feature coordinators | `InventoryCoordinator`, `RecipesCoordinator`, `ScanCoordinator`, `SettingsCoordinator` | own `NavigationPath` + route enums |
| Factories | `Screens/*/…Factory.swift` | build view model + view; `ViewModelHost` keeps VMs alive |
| View models | `@MainActor @Observable`, dependencies injected | explicit state enums (`ScanFlowViewModel.Phase`, `ItemDetailViewModel.State`…) |
| Services | `Services/` | `InventoryStore` (SwiftData), `ExpiryDateParser`, `RecipeMatcher`, `ExpiryNotificationPlanner`, `OpenFoodFactsLookup` |

Everything that matters is pure and unit-tested: date parsing, name guessing, recipe
matching, notification planning, the store, and the view models (Swift Testing).

## Getting started

Requires Xcode 26 and [XcodeGen](https://github.com/yonaskolb/XcodeGen).

```bash
cd ios
xcodegen generate
open ZeroBite.xcodeproj
```

Run the `ZeroBite` scheme on an iPhone simulator. Launch argument `--sample-data` fills an
empty fridge. Run tests with ⌘U or:

```bash
cd ios && xcodebuild test -project ZeroBite.xcodeproj -scheme ZeroBite -destination 'platform=iOS Simulator,name=iPhone 17 Pro'
```

### Typeface

The app is set in [Mona Sans](https://github.com/github/mona-sans) (SIL OFL). The font file
isn't committed; fetch it once and the project picks it up (falls back to the system font
when it's missing):

```bash
scripts/fetch-fonts.sh
```

## Android

The Android app lives in `android/` (in progress — see `specs/`). Its `:core` module holds all
of Zero Bite's pure logic — date parsing, name guessing, recipe matching, reminder planning,
recall matching and the Open Food Facts / FSA clients — with no Android dependencies, so it
can later become a Kotlin Multiplatform module shared with iOS.

```bash
cd android
./gradlew :core:check
```

Requires JDK 17+ (Android Studio's bundled JDK works).

### Keeping iOS and Android in step

`testdata/` holds golden fixtures (label text → expected dates, fridge → recipe ranking,
items → reminders, FSA notices → recall matches). Both test suites run them:
`ios/ZeroBiteTests/GoldenFixtureTests.swift` and the `:core` tests. Swift is the reference
implementation: change behaviour in both, and update the fixture in the same commit.
