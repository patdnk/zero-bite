# Zero Bite for Android — Design

- **Date:** 2026-10-08
- **Status:** Draft, awaiting review
- **Author:** Pat Dynek (with Claude)

## 1. Goals

Bring Zero Bite to Android with full feature parity with the shipped iOS app, so friends on
Android can use it and so both platforms can grow together long term.

**Why:** (B) equal platforms for the long haul — avoid writing every feature twice forever;
(C) personal — friends use Android, and it's a chance to learn Compose and Kotlin Multiplatform.

**Success criteria**

1. An Android app (`com.mazesys.zerobite`) with every iOS feature: label OCR + barcode
   scanning, Open Food Facts lookup, inventory, expiry reminders, recipe matching with
   "I cooked this", FSA recall/allergy alerts, onboarding, the bespoke UI and brand mark.
2. Pure logic lives in a `:core` module with no Android dependencies, so it can later become
   a Kotlin Multiplatform module that iOS consumes (Phase 2) without rework.
3. Shared golden test fixtures prove Android and iOS behave identically on the subtle logic
   (date parsing, name guessing, recipe matching, reminder planning, recall matching).
4. Friends can install it via Firebase App Distribution; the path to a Google Play release is
   prepared.

**Non-goals (this spec):** converting `:core` to KMP, changing iOS app code, a backend,
accounts or sync, tablets/foldables beyond basic support, Wear OS, widgets, non-English
locales. Portrait phone, UK-first — same as iOS.

## 2. Phases

| Phase | What | Status |
|---|---|---|
| 0 | Repo housekeeping: move iOS into `ios/`, shared assets out, delete stale local files | This spec |
| 1 | Native Android app (`android/:core` + `android/:app`), Firebase App Distribution | This spec |
| 2 | Convert `:core` to KMP; iOS swaps Swift services for it, one at a time | Outlined in §10, separate spec |
| Later | Shared recipe database, background recall checks, Google Play production | §10 |

## 3. Phase 0 — Repo housekeeping

Housekeeping only — no Swift code is refactored.

Target layout:

```
zero-waste/
├── ios/            ZeroBite/, ZeroBiteTests/, project.yml   (git mv — history kept)
├── android/        Gradle project (§4)
├── shared-assets/  recipes.json (single source for both apps)
├── testdata/       golden JSON fixtures (§8)
├── design/         render-assets tool, Marketing.swift, BrandMark.svg, AppIcon.svg
├── appstore/       iOS store listing (unchanged)
├── playstore/      Play listing (added when the Play account exists)
├── docs/           website — stays at root, served by GitHub Pages
├── specs/          design specs (not under docs/, which is public)
└── scripts/        fetch-fonts.sh, render-assets.sh, distribute-android.sh
```

Steps:

1. Commit or set aside the in-progress App Store changes on `rename-zero-bite` first, so the
   move is a clean, separate commit.
2. Delete stale local, gitignored leftovers: `ZeroWaste.xcodeproj` (pre-rename) and the root
   `build/` (DerivedData).
3. `git mv ZeroBite ZeroBiteTests project.yml ios/`.
4. Move `ZeroBite/Resources/recipes.json` → `shared-assets/recipes.json`; reference it from
   `project.yml` as a resource so the iOS bundle is unchanged at runtime.
5. Update paths in `project.yml`, `scripts/fetch-fonts.sh` (install Mona Sans into both
   `ios/ZeroBite/Resources/Fonts/` and `android/app/src/main/res/font/mona_sans.ttf` — Android resource names must be lowercase; gitignored like the iOS copy),
   `scripts/render-assets.sh`, `.gitignore`, `README.md`, and the project memory notes.
6. Verify: `cd ios && xcodegen generate`, full iOS test suite green on iPhone 17 Pro, and a
   device build succeeds.

## 4. Android project structure

```
android/
├── settings.gradle.kts
├── gradle/libs.versions.toml          version catalog
├── core/                              Kotlin/JVM library — no android.* allowed
│   └── com.mazesys.zerobite.core
│       ├── model/          FoodItem, FoodCategory, Recipe, FoodAlert, Allergen, ItemDraft, ScanResult, ProductInfo
│       ├── scanning/       ExpiryDateParser, ProductNameGuesser, LabelScanner.analyse(lines, barcode, now)
│       ├── recipes/        RecipeMatcher, RecipeRepository (interface)
│       ├── notifications/  ExpiryNotificationPlanner, NotificationPreferences, PlannedNotification
│       ├── alerts/         RecallMatcher, FsaAlertsClient
│       ├── products/       OpenFoodFactsClient (ProductLookup interface)
│       ├── inventory/      InventoryRepository (interface)
│       └── time/           Clock abstraction (kotlinx-datetime)
└── app/                               Android application
    └── com.mazesys.zerobite
        ├── ZeroBiteApplication, MainActivity
        ├── data/           Room DB + InventoryRepository impl, BundledRecipeRepository, RecallCache
        ├── platform/       scanning (CameraX + ML Kit), reminders (AlarmManager), settings (DataStore)
        ├── coordination/   AppFlowController, AppCoordinator, feature coordinators, routes
        ├── screens/        splash, welcome, inventory, scan, recipes, alerts, settings
        └── ui/             theme, components, BrandMark
```

- `:core` is the future KMP module. It is a plain `kotlin("jvm")` module so Gradle enforces
  "no Android imports". Its dependencies are all KMP-ready: kotlinx-datetime,
  kotlinx-serialization, kotlinx-coroutines, Ktor client.
- `RecipeRepository` and `InventoryRepository` are interfaces in `:core`; implementations live
  in `:app`. The bundled-JSON recipe repository is the first implementation of many.
- `recipes.json` is copied from `shared-assets/` into `app/src/main/assets/` by a Gradle
  `Copy` task wired before `mergeAssets` — never committed twice.
- Toolchain: latest stable AGP and Kotlin (K2) at implementation time, JDK 17, `compileSdk`
  and `targetSdk` = latest stable API, `minSdk` = 26 (Android 8.0).

## 5. App architecture (MVVM-C, mirroring iOS)

| iOS | Android |
|---|---|
| `AppFlowController` (`launch → welcome → main`) | `AppFlowController` with `StateFlow<AppFlowPhase>`; `RootScreen` switches on it |
| `AppCoordinator` — tabs, sheets, alerts | `AppCoordinator` — `selectedTab`, `presentedSheet`, `alert` as state; owns feature coordinators |
| Feature coordinators + route enums + `NavigationPath` | `InventoryCoordinator`, `RecipesCoordinator`, `SettingsCoordinator`, `ScanCoordinator`, each owning a back stack of `@Serializable` routes; Navigation Compose typed routes, one `NavHost` per tab |
| `AppDependencies.live()` / `.preview()` | `AppDependencies` built in `ZeroBiteApplication`; `AppDependencies.fake()` for tests/previews. Manual DI, no framework |
| `inventory.onChange` → resync notifications + recall matches | `InventoryRepository.changes: Flow<Unit>` collected in `AppDependencies` on an application `CoroutineScope` |
| Factories + `ViewModelHost` | `InventoryFactory`, `RecipesFactory`, … create `ViewModel`s via `viewModel { }` with explicit dependencies |
| `@MainActor @Observable` VMs with explicit state enums | `androidx.lifecycle.ViewModel` exposing one `StateFlow<UiState>` (sealed types mirroring e.g. `ScanFlowViewModel.Phase`, `ItemDetailViewModel.State`) plus event methods |

Rules:

- View models never navigate; they call their coordinator (or injected callbacks), as on iOS.
- Sheets — scan, add item, edit item, cooked, how-it-works — are driven by
  `AppCoordinator.presentedSheet` and shown full-screen or as bottom sheets.
- Notification taps deep-link through `AppCoordinator.handle(deepLink)` (open item, open alerts).
- System back and predictive back call the active coordinator's `pop()`.

Error handling:

- Async screen state is explicit: `Loading / Loaded / Empty / Error`.
- Network failures degrade quietly: barcode lookup falls back to `ProductNameGuesser`; alerts
  show the cached list with a "couldn't refresh" banner.
- A database that fails to open at startup crashes loudly (matches iOS `fatalError`) rather
  than silently showing an empty fridge.

## 6. Platform services

### Scanning
- Live scan: CameraX `Preview` + `ImageAnalysis`, frames fed to ML Kit Text Recognition v2
  (Latin) and ML Kit Barcode Scanning. Recognised lines + barcode → `LabelScanner.analyse`
  in `:core`, so parsing is identical to iOS.
- Photo fallback: system Photo Picker → same ML Kit calls on a still image (emulator path,
  mirrors iOS simulator path).
- Use the **bundled** ML Kit models (not the Play Services–downloaded variants) so scanning
  works offline and on first launch, at a cost of roughly 4–6 MB of APK size.
- `CAMERA` permission requested on entering the scan screen, with the iOS usage copy as the
  rationale; a denied state links to app settings and offers the photo fallback.

### Reminders
- `ExpiryNotificationPlanner` (pure, `:core`) produces `PlannedNotification`s with the iOS ID
  scheme (`zerobite.item.<id>…`) from items + `NotificationPreferences` (enabled, leadDays,
  hour, minute) + now.
- `AlarmReminderScheduler` (`:app`) maps them to `AlarmManager.setAndAllowWhileIdle`
  (inexact — no exact-alarm permission needed); a `BroadcastReceiver` posts the notification
  on an `expiry` channel. New recall matches (same trigger as iOS `notifyNewRecallMatches`) post
  immediately on a separate `recalls` channel, so users can mute either independently.
- Full replan on: inventory change, preference change, `BOOT_COMPLETED`,
  `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`. Scheduled alarm IDs are persisted so
  stale ones are cancelled on replan.
- `POST_NOTIFICATIONS` (Android 13+) requested from the welcome flow's "enable reminders"
  step, as on iOS.

### Storage
- Inventory: Room in `:app` implementing `InventoryRepository` (`Flow<List<FoodItem>>`,
  insert/update/delete/markConsumed). Type converters for kotlinx-datetime types. Schema
  exported from v1 so migrations are testable.
- Settings: Jetpack DataStore (Preferences) behind `AppSettings`, using the iOS key names
  (`hasCompletedWelcome`, `notificationsEnabled`, `notificationLeadDays`, `notifyHour`,
  `notifyMinute`, `soonThresholdDays`, `dismissedNotificationTip`, `recallChecksEnabled`,
  `allergens`).
- Recalls: alert cache as a JSON file in `filesDir`; last-checked, dismissed and notified sets
  in DataStore — same semantics as iOS `RecallStore` (120 days of history, refresh if older
  than 6 hours).

### Network
- Ktor client (OkHttp engine on Android) + kotlinx-serialization, in `:core`.
- `OpenFoodFactsClient` and `FsaAlertsClient` are ported from iOS; the FSA date filter is
  `min-created=YYYY-MM-DD` (`since` returns 400) and is covered by a test.
- Recall refresh happens on app foreground when stale — no background job (parity with iOS).

## 7. UI and design system

Jetpack Compose throughout. Chosen because its declarative model maps directly onto SwiftUI
and the MVVM-C structure, bespoke components are cheap to build, and it is the one UI route
that keeps Compose Multiplatform open later. iOS stays SwiftUI.

**Theme (`ui/theme/`)**
- `Radius` (card 4dp, control 3dp) and `Spacing` (xs 4, sm 8, md 12, lg 16, xl 24,
  gutter 16) ported 1:1 from `Theme.swift`.
- Colours from the iOS asset catalog: darkened red / orange / yellow, muted olive "fresh",
  with light and dark variants.
- Exposed via `ZeroBiteTheme.colors / typography / spacing` composition locals. Material 3 is
  underneath only so system components (dialogs, date pickers) inherit the palette; no
  visible component uses Material defaults.
- Mona Sans variable font via `FontVariation` (weight + optical size); a text-style scale
  mirroring the iOS Dynamic Type styles, honouring system font scale; system-font fallback if
  the font file is missing.

**Components (`ui/components/`)** — one-to-one with iOS `UI/`: `TopBar`, `ScreenHeader`,
`AppTabBar` (custom, not Material `NavigationBar`), `BottomSearchBar` (pinned, `imePadding`),
`ChipFilter`, `FoodItemRow`, `RecipeRow`, `ExpiryBadge`, `RecallViews`, `EmptyStateView`,
`LoadingOverlay`, `PrimaryButton`, form components. SF Symbols are replaced with Material
Symbols Rounded chosen for matching weight.

**BrandMark** — redrawn as a Compose `Canvas` from the geometry in `BrandMark.swift` (red disc,
white ring, speed-limit-style Z, bite on the east side). Not to be redesigned. A screenshot
test compares it to `design/BrandMark.svg`. `render-assets.sh` gains an `android` target that
generates the adaptive launcher icon (foreground + background) and the Android 13 monochrome
themed icon. Android 12+ SplashScreen API hands off to the same splash → welcome flow.

**Deliberately Android-native:** edge-to-edge with correct insets, system and predictive back,
Android share sheet, `HapticFeedback`.

## 8. Testing

**Golden fixtures (`testdata/`)** — the parity contract between platforms:

| File | Input → expected |
|---|---|
| `expiry-dates.json` | label lines + now → date + kind (use-by / best-before / none) |
| `product-names.json` | OCR lines → guessed name |
| `recipe-matching.json` | inventory + recipes → ranking, matched and missing ingredients (incl. synonyms) |
| `notification-plans.json` | items + preferences + now → planned reminders |
| `recall-matching.json` | FSA alerts + inventory + allergens → matches |

Seeded from the existing Swift test cases. Kotlin `:core` tests run every fixture. One Swift
test file is added to `ZeroBiteTests` that runs the same fixtures against the Swift services
(the only iOS change in Phase 1 — a test, no app code). Both must pass when a fixture changes.

**`:core`** — JUnit 5 + kotlinx-coroutines-test: fixtures above; Ktor clients against
`MockEngine` with recorded Open Food Facts / FSA responses, including error and timeout paths.

**`:app`**
- View models: JVM tests with fake repositories and a fixed clock (scan phases, item detail,
  cooked flow, alerts, settings), mirroring the Swift VM tests.
- Room: DAO tests on in-memory DB (Robolectric) and a migration test harness from v1.
- `AlarmReminderScheduler`: Robolectric shadow `AlarmManager` — alarms set and cancelled.
- Screenshots: Roborazzi for key screens in light and dark, plus the BrandMark check.
- One instrumented smoke test: launch → welcome → add item manually → visible in fridge.

**CI** — GitHub Actions on changes under `android/**`, `testdata/**`, `shared-assets/**`:
`:core` + `:app` unit tests, lint, debug APK build.

## 9. Release and distribution

**Signing:** release keystore generated once, stored outside the repo (`~/.zerobite/`, backed
up in a password manager), referenced from a gitignored `android/keystore.properties`. It
becomes the Play upload key later (Play App Signing holds the app key). R8 + resource
shrinking on release, with keep rules for Room and kotlinx-serialization.

**Versioning:** `versionName` follows the iOS marketing version (1.0); `versionCode` is a
monotonically increasing build number.

**Now — Firebase App Distribution**
- Pat (one-time): create a Firebase project and register Android app `com.mazesys.zerobite`;
  invite testers into a `friends` group.
- `scripts/distribute-android.sh`: builds the signed release APK and uploads it with the
  Firebase CLI, with release notes and the `friends` group.
- ~~No Firebase SDK or Google Services plugin in the app.~~ Changed for 1.1 (3 Nov 2026): the
  app ships Firebase Analytics and Crashlytics, off until the user opts in. See
  `2026-11-03-usage-data-consent.md`, `playstore/data-safety.md` and `appstore/app-privacy.md`.

**Later — Google Play** (when Pat has a Play Console account)
- `playstore/` beside `appstore/`: listing text adapted from the iOS metadata, feature
  graphic and phone screenshots (`render-assets.sh` extended for Play sizes), Data safety
  answers in `playstore/data-safety.md` (opt-in Firebase usage and crash data).
- New personal accounts need a closed test with 12+ testers for 14 days before production:
  move the Firebase testers into Play closed testing, then promote.
- Optional: Gradle Play Publisher in CI to upload signed AABs to the internal track.

## 10. Phase 2 and future work (not in this spec)

**Phase 2 — KMP transition** (own spec when Android is stable):
1. Convert `:core` to KMP (`commonMain`, `androidMain`, `iosMain`); Ktor engine per platform
   (OkHttp / Darwin). No Android behaviour change.
2. Export an XCFramework with SKIE so sealed classes, `Flow` and `suspend` are idiomatic in
   Swift.
3. Swap iOS services one per PR, lowest risk first: `ExpiryDateParser` → `ProductNameGuesser`
   → `RecipeMatcher` → `ExpiryNotificationPlanner` → `RecallMatcher` + FSA client → Open Food
   Facts client. Golden fixtures gate each swap; the Swift original is deleted after.
4. SwiftData persistence and SwiftUI screens stay native — no iOS user data migration.

**Future, must not be blocked by this design**
- Shared recipe database: a remote-backed `RecipeRepository` (with local cache) replacing the
  bundled-JSON implementation on both platforms.
- Background recall checks (WorkManager / BGTaskScheduler) on both platforms together.
- Moving persistence into `:core` (Room KMP) if sync is ever added.

## 11. Decisions log

| Decision | Choice | Rejected alternatives |
|---|---|---|
| Code sharing | Native Android now, `:core` boundary for KMP later | KMP from day one; cross-platform rewrite (Flutter/RN) |
| Shared scope (Phase 2) | Logic only; persistence and VMs native | Logic + persistence; logic + persistence + VMs |
| v1 scope | Full parity incl. FSA alerts | Core first, alerts in v1.1 |
| UI toolkit | Jetpack Compose, bespoke theme | XML Views; Compose Multiplatform for both |
| DI | Manual `AppDependencies` | Hilt, Koin |
| Inventory storage | Room (in `:app`) | SQLDelight; Room KMP in `:core` |
| Reminders | Inexact `AlarmManager` driven by shared planner | Exact alarms; WorkManager daily sweep |
| Distribution | Firebase App Distribution now, Play closed testing later | Sideloaded APK |
| Analytics and crash reports (1.1) | Firebase Analytics + Crashlytics, opt-in, no advertising ID | None (the original choice); on by default with an opt-out |
| Repo | Monorepo; iOS moved to `ios/` | Separate Android repo |

## 12. Pat's manual steps

- Commit or set aside the in-progress App Store changes before Phase 0.
- Create the Firebase project and register the Android app; invite friends.
- Back up the release keystore and its passwords.
- Later: create the Google Play Console account ($25) and recruit 12+ closed testers.
