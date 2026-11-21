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
| `ios/` | The SwiftUI app (`project.yml`, `ZeroBite/`, `ZeroBiteTests/`, `ZeroBiteUITests/`) |
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

That runs the unit tests (`ZeroBiteTests`) and the UI smoke test (`ZeroBiteUITests`); add
`-only-testing:ZeroBiteTests` for the unit tests alone. Lint with
[SwiftLint](https://github.com/realm/SwiftLint) from `ios/` (config in `ios/.swiftlint.yml`):
`swiftlint lint --strict`.

### Typeface

The app is set in [Mona Sans](https://github.com/github/mona-sans) (SIL OFL). The font file
isn't committed; fetch it once and the project picks it up (falls back to the system font
when it's missing):

```bash
scripts/fetch-fonts.sh
```

## Android

The Android app lives in `android/`: `:core` holds all of Zero Bite's pure logic (no Android
dependencies, ready to become a Kotlin Multiplatform module), and `:app` is the Jetpack
Compose app with the same bespoke look as iOS. Label scanning and UK food alerts arrive in the next plan (see `specs/plans/`);
everything else matches iOS.

Open `android/` in Android Studio, or from the command line (JDK 17+; Android Studio's
bundled JDK works):

```bash
cd android
./gradlew :app:installDebug          # build and install on a running emulator/device
./gradlew :core:check :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest   # on-device smoke test
```

`scripts/fetch-fonts.sh` also installs Mona Sans into the Android app (it falls back to the
system font without it). Screenshot tests use Roborazzi: `./gradlew :app:recordRoborazziDebug`
records `android/app/src/test/screenshots/`, `verifyRoborazziDebug` compares against them.

### Continuous integration

`.github/workflows/ios.yml` and `android.yml` run in the same stages: **lint** (SwiftLint /
Android Lint, plus `scripts/check-polish-assets.py`) and **unit tests** in parallel, then
**e2e** (the smoke test on a simulator / emulator) once the unit tests pass. A **CI summary**
job fails if any stage did, and a nightly run opens an issue when it fails.

### Keeping iOS and Android in step

`testdata/` holds golden fixtures (label text → expected dates, fridge → recipe ranking,
items → reminders, FSA notices → recall matches). Both test suites run them:
`ios/ZeroBiteTests/GoldenFixtureTests.swift` and the `:core` tests. Swift is the reference
implementation: change behaviour in both, and update the fixture in the same commit.
