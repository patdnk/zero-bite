# Zero Bite Android — Plan 1: Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure the repo (iOS into `ios/`), write golden test fixtures proven against the Swift app, and build the Android Gradle project with a fully tested, Android-free `:core` module containing all of Zero Bite's pure logic and network clients.

**Architecture:** `android/core` is a plain Kotlin/JVM library with no `java.*`/`android.*` imports (enforced by a Gradle check) so it can become a Kotlin Multiplatform module later. Behaviour is pinned by JSON fixtures in `/testdata` that both the existing Swift tests and the new Kotlin tests run, with Swift as the reference implementation. The Android UI app (`:app`) is Plan 2.

**Tech Stack:** Kotlin 2.4.20, Gradle 9.8.1, kotlinx-datetime 0.8.0, kotlinx-serialization 1.11.0, kotlinx-coroutines 1.11.0, Ktor client 3.6.0, JUnit 6.1.3 (Jupiter); XcodeGen + Swift Testing for the iOS fixture runner.

**Spec:** [`specs/2026-10-08-android-app-design.md`](../2026-10-08-android-app-design.md)

**Plan series:** 1 Foundation (this) → 2 App shell (theme, BrandMark, navigation, Room, Fridge screens) → 3 Features (scan, reminders, recipes, alerts, settings, onboarding) → 4 Release (icons, signing, Firebase App Distribution). Each later plan is written when the previous one lands.

## Global Constraints

- iOS **app code** is not changed. Allowed iOS-side edits: moving files into `ios/`, `ios/project.yml` resource paths, and adding `ios/ZeroBiteTests/GoldenFixtureTests.swift`.
- `:core` main sources must not import `java.*`, `javax.*` or `android.*` (Gradle task `checkPortable`, wired into `check`). Tests may use `java.io.File`.
- Package root: `com.mazesys.zerobite.core`. Application ID (later): `com.mazesys.zerobite`.
- Expiry dates are `kotlinx.datetime.LocalDate`; timestamps are `kotlin.time.Instant`; a `TimeZone` or `today: LocalDate` is always passed in — `:core` never reads the system clock except in `SystemAppClock`.
- Bytecode target JVM 17 (Android-compatible); build with the installed JDK 21.
- User-facing strings (notification titles/bodies, labels) are copied verbatim from the Swift sources, including the em dash in "Plan a meal around it — Zero Bite has recipe ideas."
- `/testdata/*.json` is the parity contract. **Swift is the reference implementation**: if the Swift runner (Task 2) fails a fixture case, the fixture is wrong — fix the fixture, never the Swift app; if you believe Swift has a real bug, stop and tell Pat.
- kotlinx-datetime 0.8 names: `LocalDate(year, month, day)`, `date.day`, `date.month.number`, `plus(n, DateTimeUnit.DAY)`, `daysUntil`. If a member name differs in the resolved version, use its documented equivalent — do not change behaviour.
- Run Gradle from `android/` as `./gradlew …`.

## Review Focus

1. **Mixed-case and unusual label text** (`use by 12 sep 2026`, `Use by: 3rd Oct 2026`, leap days) must parse exactly like iOS — pinned by extra cases in `expiry-dates.json` (Task 2) and run in Task 5.
2. **Reminders across the clocks change** (late October in the UK) must still fire at the chosen local time — pinned by `fire times follow local time across the clocks change` in Task 8.
3. **Accented item names** ("Crème fraîche") must still match recipe ingredients without `java.text.Normalizer` — pinned by a `recipe-matching.json` name case (Task 2) and `TextFoldingTest` (Task 7).
4. **Malformed FSA records** (missing notation, unknown type, blank product name, single object where an array is expected) must be skipped or tolerated, never crash the whole feed — pinned by `skips malformed records and tolerates loose shapes` in Task 9.
5. **Messy barcodes** (spaces, letters, empty) must be cleaned before Open Food Facts lookup, and an empty one must not hit the network — pinned by `strips non-digits` and `empty barcode makes no request` in Task 11.

---

## File Structure

```
zero-waste/
├── ios/                                   (moved, Task 1)
│   ├── project.yml                        resource paths → ../shared-assets, ../testdata
│   ├── ZeroBite/ …                        unchanged code
│   └── ZeroBiteTests/GoldenFixtureTests.swift   (Task 2) runs /testdata against Swift
├── shared-assets/recipes.json             (moved, Task 1)
├── testdata/                              parity fixtures
│   ├── fsa-alerts.json                    (moved, Task 1) real FSA API response
│   ├── expiry-dates.json                  (Task 2)
│   ├── product-names.json                 (Task 2)
│   ├── recipe-matching.json               (Task 2)
│   ├── notification-plans.json            (Task 2)
│   └── recall-matching.json               (Task 2)
├── android/
│   ├── settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml, gradlew…
│   └── core/
│       ├── build.gradle.kts
│       └── src/main/kotlin/com/mazesys/zerobite/core/
│           ├── time/AppClock.kt                      clock + time-zone abstraction
│           ├── text/TextFolding.kt                   diacritic folding (portable)
│           ├── model/FoodItem.kt                     FoodCategory, StorageLocation, ExpiryStatus, FoodItem
│           ├── model/ItemDraft.kt
│           ├── model/Recipe.kt                       Recipe + RecipeCatalogue.decode
│           ├── model/ScanResult.kt                   DateCandidate, ProductInfo, ScanResult
│           ├── model/Allergen.kt
│           ├── model/FoodAlert.kt
│           ├── inventory/InventoryRepository.kt      interface
│           ├── recipes/RecipeRepository.kt           interface
│           ├── recipes/RecipeMatcher.kt              MatchedIngredient, RecipeMatch, RecipeMatcher
│           ├── scanning/ExpiryDateParser.kt
│           ├── scanning/ProductNameGuesser.kt
│           ├── scanning/LabelScanner.kt
│           ├── notifications/ExpiryNotificationPlanner.kt   + NotificationPreferences, PlannedNotification
│           ├── alerts/FsaAlertDecoder.kt
│           ├── alerts/AlertDateParser.kt
│           ├── alerts/RecallMatcher.kt
│           ├── alerts/FsaAlertsClient.kt
│           ├── products/OpenFoodFactsClient.kt
│           └── network/ZeroBiteHttp.kt               user agent
│       └── src/test/kotlin/com/mazesys/zerobite/core/  one test file per unit + Fixtures.kt
└── .github/workflows/android.yml          (Task 12)
```

---

### Task 1: Phase 0 — repo housekeeping

**Files:**
- Move: `ZeroBite/` → `ios/ZeroBite/`, `ZeroBiteTests/` → `ios/ZeroBiteTests/`, `project.yml` → `ios/project.yml`
- Move: `ios/ZeroBite/Resources/recipes.json` → `shared-assets/recipes.json`
- Move: `ios/ZeroBiteTests/Fixtures/fsa-alerts.json` → `testdata/fsa-alerts.json`
- Modify: `ios/project.yml`, `scripts/fetch-fonts.sh`, `scripts/render-assets.sh`, `.gitignore`, `README.md`, `appstore/README.md`
- Delete (local, gitignored): `ZeroWaste.xcodeproj`, `ZeroBite.xcodeproj`, `build/`

**Interfaces:**
- Produces: `shared-assets/recipes.json` and `testdata/fsa-alerts.json` at the repo root; `ios/` builds and tests exactly as before.

- [ ] **Step 1: Make sure nothing unrelated is uncommitted**

Run: `git status --short`
If anything is listed, STOP and ask Pat to commit or stash it (it is his in-progress App Store work — do not commit it yourself). Continue only on a clean tree.

- [ ] **Step 2: Create the working branch**

```bash
git switch -c android-foundation
```

- [ ] **Step 3: Delete stale local build products**

Confirm they are untracked, then delete:

```bash
git ls-files ZeroWaste.xcodeproj ZeroBite.xcodeproj build   # must print nothing
rm -rf ZeroWaste.xcodeproj ZeroBite.xcodeproj build
```

- [ ] **Step 4: Move the iOS project (plain `mv`, so the gitignored font travels too)**

```bash
mkdir -p ios shared-assets testdata
mv ZeroBite ZeroBiteTests project.yml ios/
mv ios/ZeroBite/Resources/recipes.json shared-assets/recipes.json
mv ios/ZeroBiteTests/Fixtures/fsa-alerts.json testdata/fsa-alerts.json
rmdir ios/ZeroBiteTests/Fixtures
git add -A ios ZeroBite ZeroBiteTests project.yml shared-assets testdata
git status --short | head    # expect renames (R), no deletions without matching adds
```

- [ ] **Step 5: Point `ios/project.yml` at the shared resources**

In the `ZeroBite` target, replace

```yaml
    sources:
      - path: ZeroBite
```

with

```yaml
    sources:
      - path: ZeroBite
      - path: ../shared-assets/recipes.json
        buildPhase: resources
```

In the `ZeroBiteTests` target, replace

```yaml
    sources:
      - path: ZeroBiteTests
```

with

```yaml
    sources:
      - path: ZeroBiteTests
      - path: ../testdata
        buildPhase: resources
```

- [ ] **Step 6: Update script paths**

`scripts/fetch-fonts.sh`:
- `DEST="$ROOT/ZeroBite/Resources/Fonts"` → `DEST="$ROOT/ios/ZeroBite/Resources/Fonts"`
- `echo "Installed $(basename "$FONT") → ZeroBite/Resources/Fonts/Mona-Sans.ttf"` → `echo "Installed $(basename "$FONT") → ios/ZeroBite/Resources/Fonts/Mona-Sans.ttf"`
- `(cd "$ROOT" && xcodegen generate)` → `(cd "$ROOT/ios" && xcodegen generate)`
- `run 'xcodegen generate' in $ROOT` → `run 'xcodegen generate' in $ROOT/ios`

`scripts/render-assets.sh`:
- `"$ROOT/ZeroBite/UI/BrandMark.swift"` → `"$ROOT/ios/ZeroBite/UI/BrandMark.swift"`
- `"$ROOT/ZeroBite/Assets.xcassets/AppIcon.appiconset"` → `"$ROOT/ios/ZeroBite/Assets.xcassets/AppIcon.appiconset"`

`.gitignore`: `ZeroBite/Resources/Fonts/*.ttf` → `ios/ZeroBite/Resources/Fonts/*.ttf`

`appstore/README.md`: `ZeroBite/UI/BrandMark.swift` → `ios/ZeroBite/UI/BrandMark.swift`

- [ ] **Step 7: Update the README's getting-started and add a layout section**

In `README.md`, replace the block

```bash
xcodegen generate
open ZeroBite.xcodeproj
```

with

```bash
cd ios
xcodegen generate
open ZeroBite.xcodeproj
```

replace `xcodebuild test -project ZeroBite.xcodeproj` with `cd ios && xcodebuild test -project ZeroBite.xcodeproj`, and insert after the "Features" section:

```markdown
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
```

- [ ] **Step 8: Regenerate and run the full iOS suite**

```bash
cd ios && xcodegen generate && xcodebuild test -project ZeroBite.xcodeproj -scheme ZeroBite -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -quiet; cd ..
```

Expected: `** TEST SUCCEEDED **`. `RecipeMatcherTests.bundledCatalogueLoadsAndIsWellFormed` proves `recipes.json` is still bundled; `FoodAlertDecodingTests` proves `fsa-alerts.json` is still in the test bundle.

- [ ] **Step 9: Check the font script still resolves its paths** (no download needed)

Run: `bash -n scripts/fetch-fonts.sh scripts/render-assets.sh && ls ios/ZeroBite/Resources/Fonts/`
Expected: no syntax errors; `Mona-Sans.ttf` listed (it moved with the folder).

- [ ] **Step 10: Commit**

```bash
git add -A ios shared-assets testdata scripts .gitignore README.md appstore/README.md
git commit -m "Move the iOS app into ios/ and share recipes and fixtures

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 11: Update the project memory**

In `~/.claude/projects/-Users-patdynek-Repositories-non-work-zero-waste/memory/zero-waste-project.md`, change paths to the new layout: `xcodegen generate` runs in `ios/`; `UI/Theme.swift`, `UI/BrandMark.swift` live under `ios/ZeroBite/`; add one line: "Repo is a monorepo: `ios/`, `android/`, `shared-assets/recipes.json`, `testdata/` golden fixtures shared by both test suites."

---

### Task 2: Golden fixtures, proven against Swift

**Files:**
- Create: `testdata/expiry-dates.json`, `testdata/product-names.json`, `testdata/recipe-matching.json`, `testdata/notification-plans.json`, `testdata/recall-matching.json`
- Create: `ios/ZeroBiteTests/GoldenFixtureTests.swift`

**Interfaces:**
- Produces: the five fixture files with exactly the schemas below. Kotlin tests in Tasks 5–10 decode them with matching `@Serializable` classes. Dates are ISO `YYYY-MM-DD`; local date-times are `YYYY-MM-DDTHH:mm` in the fixture's time zone; Swift reads day-only dates as 09:00 Europe/London (the `TestSupport.date` convention).

- [ ] **Step 1: Write `testdata/expiry-dates.json`**

```json
{
  "today": "2026-09-07",
  "cases": [
    { "name": "UK numeric date", "lines": ["BEST BEFORE 12/09/2026"], "best": "2026-09-12" },
    { "name": "month name with two-digit year", "lines": ["USE BY 15 SEP 26"], "best": "2026-09-15" },
    { "name": "dotted two-digit year", "lines": ["BB 03.10.26"], "best": "2026-10-03" },
    { "name": "ISO date", "lines": ["2026-11-02"], "best": "2026-11-02" },
    { "name": "obviously month-first numeric date is swapped", "lines": ["09/14/2026"], "best": "2026-09-14" },
    { "name": "month and year means end of month", "lines": ["BEST BEFORE END OCT 2026"], "best": "2026-10-31" },
    { "name": "missing year assumes the upcoming occurrence", "lines": ["USE BY 05 JAN"], "best": "2027-01-05" },
    { "name": "keyword line outranks packing date", "lines": ["PACKED 01/09/2026", "USE BY 10/09/2026"], "best": "2026-09-10", "count": 2 },
    { "name": "keyword on previous line boosts the date", "lines": ["BEST BEFORE", "20 SEP 2026", "LOT 4471 12/08/2026"], "best": "2026-09-20" },
    { "name": "batch code is not a date", "lines": ["LOT 123456"], "best": null, "count": 0 },
    { "name": "batch code with a space is not a date", "lines": ["L2026091 4"], "best": null, "count": 0 },
    { "name": "impossible date is rejected", "lines": ["31/02/2026"], "best": null, "count": 0 },
    { "name": "volume is not a date", "lines": ["1.136L"], "best": null, "count": 0 },
    { "name": "compact digits without a keyword are ignored", "lines": ["120926"], "best": null, "count": 0 },
    { "name": "compact digits with a keyword are read", "lines": ["EXP 120926"], "best": "2026-09-12" },
    { "name": "year far in the past is rejected", "lines": ["USE BY 12/09/1999"], "best": null, "count": 0 },
    { "name": "year far in the future is rejected", "lines": ["USE BY 12/09/2099"], "best": null, "count": 0 },
    { "name": "same day is de-duplicated keeping the higher confidence", "lines": ["12/09/2026", "USE BY 12 SEP 2026"], "best": "2026-09-12", "count": 1, "bestSourceLine": "USE BY 12 SEP 2026" },
    { "name": "ordinal day with month name", "lines": ["Use by: 3rd Oct 2026"], "best": "2026-10-03" },
    { "name": "dotted four-digit year", "lines": ["EXP 12.09.2026"], "best": "2026-09-12" },
    { "name": "day and month only uses the current year", "lines": ["DISPLAY UNTIL 14 SEP"], "best": "2026-09-14" },
    { "name": "numeric month and year means end of month", "lines": ["BEST BEFORE END 09/2026"], "best": "2026-09-30" },
    { "name": "lowercase label", "lines": ["use by 12 sep 2026"], "best": "2026-09-12" },
    { "name": "leap day in a leap year", "lines": ["USE BY 29/02/2028"], "best": "2028-02-29" },
    { "name": "leap day in a common year is impossible", "lines": ["USE BY 29/02/2027"], "best": null, "count": 0 }
  ]
}
```

- [ ] **Step 2: Write `testdata/product-names.json`**

```json
{
  "cases": [
    { "name": "picks the product line, not the retailer or date", "lines": ["TESCO", "SEMI SKIMMED MILK", "USE BY 12 SEP", "2 PINTS 1.136L", "Keep refrigerated"], "expected": "Semi Skimmed Milk" },
    { "name": "nothing looks like a name", "lines": ["12/09/2026", "500g", "www.example.com"], "expected": null },
    { "name": "shouted pack front wins", "lines": ["GREEK YOGURT", "USE BY 14 SEP 2026"], "expected": "Greek Yogurt" },
    { "name": "uppercase product beats mixed-case brand", "lines": ["Ingredients: milk", "Cathedral City", "MATURE CHEDDAR"], "expected": "Mature Cheddar" }
  ]
}
```

- [ ] **Step 3: Write `testdata/recipe-matching.json`**

```json
{
  "today": "2026-09-07",
  "soonThresholdDays": 3,
  "nameMatches": [
    { "ingredient": "eggs", "item": "Egg", "matches": true },
    { "ingredient": "tomatoes", "item": "Cherry tomatoes", "matches": true },
    { "ingredient": "cheese", "item": "Cheddar cheese", "matches": true },
    { "ingredient": "cheese", "item": "Parmesan", "matches": true },
    { "ingredient": "bell pepper", "item": "Red pepper", "matches": true },
    { "ingredient": "chicken", "item": "Chicken breast", "matches": true },
    { "ingredient": "eggs", "item": "Eggplant", "matches": false },
    { "ingredient": "milk", "item": "Bread", "matches": false },
    { "ingredient": "cream", "item": "Crème fraîche", "matches": true },
    { "ingredient": "spring onions", "item": "Scallions", "matches": true }
  ],
  "staples": [
    { "name": "salt", "isStaple": true },
    { "name": "Olive oil", "isStaple": true },
    { "name": "soy sauce", "isStaple": true },
    { "name": "Sea salt", "isStaple": true },
    { "name": "chicken", "isStaple": false }
  ],
  "recipes": [
    {
      "id": "omelette", "title": "Omelette", "summary": "", "prepMinutes": 10, "servings": 1, "tags": [], "steps": [],
      "ingredients": [
        { "name": "eggs", "amount": "3" },
        { "name": "cheese", "amount": "30g", "optional": true },
        { "name": "butter", "amount": "1 tbsp" },
        { "name": "salt", "amount": "pinch" }
      ]
    },
    {
      "id": "stirfry", "title": "Stir-fry", "summary": "", "prepMinutes": 20, "servings": 2, "tags": [], "steps": [],
      "ingredients": [
        { "name": "chicken", "amount": "2" },
        { "name": "bell pepper", "amount": "1" },
        { "name": "soy sauce", "amount": "2 tbsp" }
      ]
    }
  ],
  "cases": [
    {
      "name": "can make when required ingredients are present",
      "inventory": [
        { "id": "eggs", "name": "Eggs", "expiresInDays": 10 },
        { "id": "butter", "name": "Butter", "expiresInDays": 30 }
      ],
      "recipes": ["omelette"],
      "matches": {
        "omelette": { "canMake": true, "missing": [], "missingOptional": ["cheese"], "staples": ["salt"], "usedItemIds": ["eggs", "butter"] }
      }
    },
    {
      "name": "missing required ingredient blocks can-make",
      "inventory": [{ "id": "chicken", "name": "Chicken breast", "expiresInDays": 1 }],
      "recipes": ["stirfry"],
      "matches": { "stirfry": { "canMake": false, "missing": ["bell pepper"] } }
    },
    {
      "name": "expiring items boost ranking and the soonest item is used",
      "inventory": [
        { "id": "fresh-eggs", "name": "Eggs", "expiresInDays": 20 },
        { "id": "old-eggs", "name": "Eggs", "expiresInDays": 1 },
        { "id": "butter", "name": "Butter", "expiresInDays": 30 },
        { "id": "chicken", "name": "Chicken breast", "expiresInDays": 20 },
        { "id": "pepper", "name": "Red pepper", "expiresInDays": 20 }
      ],
      "recipes": ["stirfry", "omelette"],
      "ranking": ["omelette", "stirfry"],
      "matches": { "omelette": { "expiringItemIds": ["old-eggs"], "ingredientItems": { "eggs": "old-eggs" } } }
    },
    {
      "name": "consumed items are ignored",
      "inventory": [
        { "id": "eggs", "name": "Eggs", "expiresInDays": 10, "consumed": true },
        { "id": "butter", "name": "Butter", "expiresInDays": 30 }
      ],
      "recipes": ["omelette"],
      "matches": { "omelette": { "canMake": false, "missing": ["eggs"] } }
    }
  ]
}
```

- [ ] **Step 4: Write `testdata/notification-plans.json`**

```json
{
  "timeZone": "Europe/London",
  "now": "2026-09-07T08:00",
  "cases": [
    {
      "name": "lead, today and expired reminders",
      "preferences": { "enabled": true, "leadDays": 3, "hour": 9, "minute": 30 },
      "items": [{ "id": "milk", "name": "Milk", "expiresOn": "2026-09-12" }],
      "expected": [
        { "itemId": "milk", "suffix": "soon", "title": "Milk expires in 3 days", "body": "Plan a meal around it — Zero Bite has recipe ideas.", "fireAt": "2026-09-09T09:30" },
        { "itemId": "milk", "suffix": "today", "title": "Milk expires today", "body": "Use it today or freeze it.", "fireAt": "2026-09-12T09:30" },
        { "itemId": "milk", "suffix": "expired", "title": "Milk has expired", "body": "Check it and update your fridge.", "fireAt": "2026-09-13T09:30" }
      ]
    },
    {
      "name": "reminders already in the past are dropped",
      "preferences": { "enabled": true, "leadDays": 3, "hour": 9, "minute": 30 },
      "items": [{ "id": "yogurt", "name": "Yogurt", "expiresOn": "2026-09-07" }],
      "expected": [
        { "itemId": "yogurt", "suffix": "today", "title": "Yogurt expires today", "body": "Use it today or freeze it.", "fireAt": "2026-09-07T09:30" },
        { "itemId": "yogurt", "suffix": "expired", "title": "Yogurt has expired", "body": "Check it and update your fridge.", "fireAt": "2026-09-08T09:30" }
      ]
    },
    {
      "name": "long-expired item plans nothing",
      "preferences": { "enabled": true, "leadDays": 3, "hour": 9, "minute": 30 },
      "items": [{ "id": "old", "name": "Old", "expiresOn": "2026-08-01" }],
      "expected": []
    },
    {
      "name": "single-day lead says tomorrow",
      "preferences": { "enabled": true, "leadDays": 1, "hour": 9, "minute": 0 },
      "items": [{ "id": "bread", "name": "Bread", "expiresOn": "2026-09-12" }],
      "expected": [
        { "itemId": "bread", "suffix": "soon", "title": "Bread expires tomorrow", "body": "Plan a meal around it — Zero Bite has recipe ideas.", "fireAt": "2026-09-11T09:00" },
        { "itemId": "bread", "suffix": "today", "title": "Bread expires today", "body": "Use it today or freeze it.", "fireAt": "2026-09-12T09:00" },
        { "itemId": "bread", "suffix": "expired", "title": "Bread has expired", "body": "Check it and update your fridge.", "fireAt": "2026-09-13T09:00" }
      ]
    },
    {
      "name": "disabled preferences plan nothing",
      "preferences": { "enabled": false, "leadDays": 3, "hour": 9, "minute": 0 },
      "items": [{ "id": "milk", "name": "Milk", "expiresOn": "2026-09-12" }],
      "expected": []
    },
    {
      "name": "reminders from several items are sorted by fire time",
      "preferences": { "enabled": true, "leadDays": 3, "hour": 9, "minute": 30 },
      "items": [
        { "id": "cheese", "name": "Cheese", "expiresOn": "2026-09-20" },
        { "id": "ham", "name": "Ham", "expiresOn": "2026-09-10" }
      ],
      "expected": [
        { "itemId": "ham", "suffix": "soon", "title": "Ham expires in 3 days", "body": "Plan a meal around it — Zero Bite has recipe ideas.", "fireAt": "2026-09-07T09:30" },
        { "itemId": "ham", "suffix": "today", "title": "Ham expires today", "body": "Use it today or freeze it.", "fireAt": "2026-09-10T09:30" },
        { "itemId": "ham", "suffix": "expired", "title": "Ham has expired", "body": "Check it and update your fridge.", "fireAt": "2026-09-11T09:30" },
        { "itemId": "cheese", "suffix": "soon", "title": "Cheese expires in 3 days", "body": "Plan a meal around it — Zero Bite has recipe ideas.", "fireAt": "2026-09-17T09:30" },
        { "itemId": "cheese", "suffix": "today", "title": "Cheese expires today", "body": "Use it today or freeze it.", "fireAt": "2026-09-20T09:30" },
        { "itemId": "cheese", "suffix": "expired", "title": "Cheese has expired", "body": "Check it and update your fridge.", "fireAt": "2026-09-21T09:30" }
      ]
    }
  ]
}
```

- [ ] **Step 5: Write `testdata/recall-matching.json`** (alerts come from `fsa-alerts.json`)

```json
{
  "matchCases": [
    {
      "name": "barcode on the notice is a likely match",
      "today": "2026-07-12", "alertId": "FSA-PRIN-28-2026", "productIndex": 0,
      "item": { "name": "Cheese", "expiresOn": "2026-08-01", "barcode": "5099874213772" },
      "expect": { "confidence": "likely", "reasonsInclude": ["barcode"] }
    },
    {
      "name": "head noun with a matching date is possible",
      "today": "2026-07-12", "alertId": "FSA-PRIN-28-2026", "productIndex": 0,
      "item": { "name": "Brie", "expiresOn": "2026-07-16" },
      "expect": { "confidence": "possible", "reasons": ["name", "date"] }
    },
    {
      "name": "name, brand and date is likely",
      "today": "2026-07-12", "alertId": "FSA-PRIN-28-2026", "productIndex": 1,
      "item": { "name": "French brie", "expiresOn": "2026-07-17", "brand": "Traditional Cheese Company" },
      "expect": { "confidence": "likely", "reasonsInclude": ["name", "brand", "date"] }
    },
    {
      "name": "name only with a different batch date is ignored",
      "today": "2026-07-12", "alertId": "FSA-PRIN-28-2026", "productIndex": 1,
      "item": { "name": "Brie", "expiresOn": "2026-09-03" },
      "expect": null
    },
    {
      "name": "a single word must be the product's head noun",
      "today": "2026-07-12", "alertId": "FSA-PRIN-47-2026-update-1", "productIndex": null,
      "item": { "name": "Milk", "expiresOn": "2026-07-20" },
      "expect": null
    }
  ],
  "listCases": [
    {
      "name": "finds the brie recall",
      "today": "2026-07-12",
      "items": [{ "id": "brie", "name": "Brie", "expiresOn": "2026-07-16" }],
      "firstAlertId": "FSA-PRIN-28-2026"
    },
    {
      "name": "dismissed match is skipped",
      "today": "2026-07-12",
      "items": [{ "id": "brie", "name": "Brie", "expiresOn": "2026-07-16" }],
      "dismissed": [{ "alertId": "FSA-PRIN-28-2026", "itemId": "brie" }],
      "firstAlertId": null
    },
    {
      "name": "consumed items are skipped",
      "today": "2026-07-12",
      "items": [{ "id": "brie", "name": "Brie", "expiresOn": "2026-07-16", "consumed": true }],
      "firstAlertId": null
    },
    {
      "name": "alerts older than the lookback are ignored",
      "today": "2027-03-01",
      "items": [{ "id": "brie", "name": "Brie", "expiresOn": "2026-07-16" }],
      "firstAlertId": null
    }
  ],
  "alertDates": [
    { "text": "16 July 2026", "expected": [{ "year": 2026, "month": 7, "day": 16 }] },
    { "text": "10, 16 and 17 July 2026", "expected": [{ "year": 2026, "month": 7, "day": 10 }, { "year": 2026, "month": 7, "day": 16 }, { "year": 2026, "month": 7, "day": 17 }] },
    { "text": "end of 28 April 2026", "expected": [{ "year": 2026, "month": 4, "day": 28 }] },
    { "text": "May 2027", "expected": [{ "year": 2027, "month": 5, "day": null }] },
    { "text": "31 January 2027 02 May 2027", "expected": [{ "year": 2027, "month": 1, "day": 31 }, { "year": 2027, "month": 5, "day": 2 }] },
    { "text": "12/08/2026", "expected": [{ "year": 2026, "month": 8, "day": 12 }] },
    { "text": "All best before dates", "expected": [] }
  ],
  "nameMatches": [
    { "item": "ham", "product": "Brady Family Crumb Irish Ham", "matches": true },
    { "item": "Greek yoghurts", "product": "Greek Style Yogurt 500g", "matches": true },
    { "item": "Milk", "product": "Pistachio Milk Chocolate", "matches": false }
  ],
  "allergens": [
    { "allergen": "gluten", "label": "Wheat", "matches": true },
    { "allergen": "gluten", "label": "Barley", "matches": true },
    { "allergen": "nuts", "label": "Hazelnut", "matches": true },
    { "allergen": "nuts", "label": "Peanuts", "matches": false },
    { "allergen": "milk", "label": "Milk", "matches": true },
    { "allergen": "milk", "label": "Egg", "matches": false }
  ],
  "concerns": [
    { "alertId": "FSA-AA-43-2026", "allergens": ["milk", "egg"], "expected": ["milk"] },
    { "alertId": "FSA-PRIN-28-2026", "allergens": ["milk"], "expected": [] }
  ]
}
```

- [ ] **Step 6: Write the Swift runner `ios/ZeroBiteTests/GoldenFixtureTests.swift`**

```swift
//
//  GoldenFixtureTests.swift
//  ZeroBiteTests
//
//  Runs the golden fixtures in /testdata — the same files the Android `:core` tests read —
//  against the Swift services, so both platforms are held to one set of expectations.
//

import Foundation
import Testing
@testable import ZeroBite

private final class GoldenBundleToken {}

enum Golden {
    static func load<T: Decodable>(_ name: String, as type: T.Type) throws -> T {
        let url = try #require(Bundle(for: GoldenBundleToken.self).url(forResource: name, withExtension: "json"))
        return try JSONDecoder().decode(T.self, from: Data(contentsOf: url))
    }

    /// "2026-09-07" → 09:00 London on that day (the TestSupport convention).
    static func day(_ iso: String) -> Date {
        let parts = iso.split(separator: "-").compactMap { Int($0) }
        return TestSupport.date(parts[0], parts[1], parts[2])
    }

    static func iso(_ date: Date) -> String {
        let c = TestSupport.calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year!, c.month!, c.day!)
    }

    /// "2026-09-07T08:00" in London.
    static func localDateTime(_ text: String) -> Date {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_GB_POSIX")
        formatter.timeZone = TestSupport.calendar.timeZone
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm"
        return formatter.date(from: text)!
    }
}

struct GoldenExpiryDateTests {
    struct Fixture: Decodable {
        struct Case: Decodable {
            let name: String
            let lines: [String]
            let best: String?
            let count: Int?
            let bestSourceLine: String?
        }
        let today: String
        let cases: [Case]
    }

    @Test func expiryDates() throws {
        let fixture = try Golden.load("expiry-dates", as: Fixture.self)
        let parser = ExpiryDateParser(calendar: TestSupport.calendar, referenceDate: Golden.day(fixture.today))
        for c in fixture.cases {
            let candidates = parser.parse(lines: c.lines)
            #expect(candidates.first.map { Golden.iso($0.date) } == c.best, "\(c.name)")
            if let count = c.count { #expect(candidates.count == count, "\(c.name)") }
            if let line = c.bestSourceLine { #expect(candidates.first?.sourceLine == line, "\(c.name)") }
        }
    }

    struct NameFixture: Decodable {
        struct Case: Decodable { let name: String; let lines: [String]; let expected: String? }
        let cases: [Case]
    }

    @Test func productNames() throws {
        for c in try Golden.load("product-names", as: NameFixture.self).cases {
            #expect(ProductNameGuesser.guess(from: c.lines) == c.expected, "\(c.name)")
        }
    }
}

@MainActor
struct GoldenRecipeTests {
    struct Fixture: Decodable {
        struct NameMatch: Decodable { let ingredient: String; let item: String; let matches: Bool }
        struct Staple: Decodable { let name: String; let isStaple: Bool }
        struct Item: Decodable { let id: String; let name: String; let expiresInDays: Int; let consumed: Bool? }
        struct Expect: Decodable {
            let canMake: Bool?
            let missing: [String]?
            let missingOptional: [String]?
            let staples: [String]?
            let usedItemIds: [String]?
            let expiringItemIds: [String]?
            let ingredientItems: [String: String]?
        }
        struct Case: Decodable {
            let name: String
            let inventory: [Item]
            let recipes: [String]
            let ranking: [String]?
            let matches: [String: Expect]?
        }
        let today: String
        let soonThresholdDays: Int
        let nameMatches: [NameMatch]
        let staples: [Staple]
        let recipes: [Recipe]
        let cases: [Case]
    }

    @Test func recipeMatching() throws {
        let fixture = try Golden.load("recipe-matching", as: Fixture.self)
        for m in fixture.nameMatches {
            #expect(RecipeMatcher.matches(ingredient: m.ingredient, itemName: m.item) == m.matches, "\(m.ingredient) ~ \(m.item)")
        }
        for s in fixture.staples {
            #expect(RecipeMatcher.isStaple(s.name) == s.isStaple, "staple \(s.name)")
        }

        let now = Golden.day(fixture.today)
        let matcher = RecipeMatcher(soonThresholdDays: fixture.soonThresholdDays, now: now)
        for c in fixture.cases {
            var fixtureID: [UUID: String] = [:]
            let inventory = c.inventory.map { spec -> FoodItem in
                let item = FoodItem(
                    name: spec.name,
                    expiresAt: TestSupport.calendar.date(byAdding: .day, value: spec.expiresInDays, to: now)!,
                    consumedAt: spec.consumed == true ? now : nil
                )
                fixtureID[item.id] = spec.id
                return item
            }
            let recipes = c.recipes.compactMap { id in fixture.recipes.first { $0.id == id } }
            let ranked = matcher.match(recipes: recipes, inventory: inventory)
            if let ranking = c.ranking { #expect(ranked.map(\.recipe.id) == ranking, "\(c.name)") }

            for (recipeID, expect) in c.matches ?? [:] {
                let match = try #require(ranked.first { $0.recipe.id == recipeID }, "\(c.name)")
                let ids = { (items: [FoodItem]) in items.map { fixtureID[$0.id] ?? "?" } }
                if let v = expect.canMake { #expect(match.canMake == v, "\(c.name)") }
                if let v = expect.missing { #expect(match.missing.map(\.name) == v, "\(c.name)") }
                if let v = expect.missingOptional { #expect(match.missingOptional.map(\.name) == v, "\(c.name)") }
                if let v = expect.staples { #expect(match.matched.filter(\.isStaple).map(\.ingredient.name) == v, "\(c.name)") }
                if let v = expect.usedItemIds { #expect(ids(match.usedItems) == v, "\(c.name)") }
                if let v = expect.expiringItemIds { #expect(ids(match.expiringItems) == v, "\(c.name)") }
                for (ingredient, itemID) in expect.ingredientItems ?? [:] {
                    let item = match.matched.first { $0.ingredient.name == ingredient }?.item
                    #expect(item.flatMap { fixtureID[$0.id] } == itemID, "\(c.name): \(ingredient)")
                }
            }
        }
    }
}

struct GoldenNotificationTests {
    struct Fixture: Decodable {
        struct Prefs: Decodable { let enabled: Bool; let leadDays: Int; let hour: Int; let minute: Int }
        struct Item: Decodable { let id: String; let name: String; let expiresOn: String }
        struct Expected: Decodable { let itemId: String; let suffix: String; let title: String; let body: String; let fireAt: String }
        struct Case: Decodable { let name: String; let preferences: Prefs; let items: [Item]; let expected: [Expected] }
        let timeZone: String
        let now: String
        let cases: [Case]
    }

    @Test func notificationPlans() throws {
        let fixture = try Golden.load("notification-plans", as: Fixture.self)
        #expect(fixture.timeZone == TestSupport.calendar.timeZone.identifier)
        let planner = ExpiryNotificationPlanner(calendar: TestSupport.calendar)
        let now = Golden.localDateTime(fixture.now)
        for c in fixture.cases {
            let uuids = Dictionary(uniqueKeysWithValues: c.items.map { ($0.id, UUID()) })
            let items = c.items.map { ExpiringItemSummary(id: uuids[$0.id]!, name: $0.name, expiresAt: Golden.day($0.expiresOn)) }
            let p = c.preferences
            let planned = planner.plan(
                items: items,
                preferences: NotificationPreferences(enabled: p.enabled, leadDays: p.leadDays, hour: p.hour, minute: p.minute),
                now: now
            )
            #expect(planned.count == c.expected.count, "\(c.name)")
            for (actual, expected) in zip(planned, c.expected) {
                let uuid = uuids[expected.itemId]!
                #expect(actual.id == "\(ExpiryNotificationPlanner.identifierPrefix)\(uuid.uuidString).\(expected.suffix)", "\(c.name)")
                #expect(actual.itemID == uuid, "\(c.name)")
                #expect(actual.title == expected.title, "\(c.name)")
                #expect(actual.body == expected.body, "\(c.name)")
                #expect(actual.fireDate == Golden.localDateTime(expected.fireAt), "\(c.name)")
            }
        }
    }
}

@MainActor
struct GoldenRecallTests {
    struct Fixture: Decodable {
        struct Item: Decodable {
            let id: String?
            let name: String
            let expiresOn: String
            let brand: String?
            let barcode: String?
            let consumed: Bool?
        }
        struct Expect: Decodable { let confidence: String; let reasons: [String]?; let reasonsInclude: [String]? }
        struct MatchCase: Decodable {
            let name: String
            let today: String
            let alertId: String
            let productIndex: Int?
            let item: Item
            let expect: Expect?
        }
        struct Dismissed: Decodable { let alertId: String; let itemId: String }
        struct ListCase: Decodable {
            let name: String
            let today: String
            let items: [Item]
            let dismissed: [Dismissed]?
            let firstAlertId: String?
        }
        struct Spec: Decodable { let year: Int; let month: Int; let day: Int? }
        struct AlertDateCase: Decodable { let text: String; let expected: [Spec] }
        struct NameMatch: Decodable { let item: String; let product: String; let matches: Bool }
        struct AllergenCase: Decodable { let allergen: String; let label: String; let matches: Bool }
        struct Concern: Decodable { let alertId: String; let allergens: [String]; let expected: [String] }

        let matchCases: [MatchCase]
        let listCases: [ListCase]
        let alertDates: [AlertDateCase]
        let nameMatches: [NameMatch]
        let allergens: [AllergenCase]
        let concerns: [Concern]
    }

    private func makeItem(_ spec: Fixture.Item, id: UUID = UUID(), now: Date) -> FoodItem {
        FoodItem(
            id: id, name: spec.name, category: .dairy, addedAt: now,
            expiresAt: Golden.day(spec.expiresOn), barcode: spec.barcode, brand: spec.brand,
            consumedAt: spec.consumed == true ? now : nil
        )
    }

    private func confidenceName(_ confidence: RecallMatch.Confidence) -> String {
        confidence == .likely ? "likely" : "possible"
    }

    @Test func recallMatching() throws {
        let fixture = try Golden.load("recall-matching", as: Fixture.self)
        let alerts = try AlertFixtures.load()

        for c in fixture.matchCases {
            let now = Golden.day(c.today)
            let matcher = RecallMatcher(now: now, calendar: TestSupport.calendar)
            let alert = try #require(alerts.first { $0.id == c.alertId }, "\(c.name)")
            let products = c.productIndex.map { [alert.products[$0]] } ?? alert.products
            let results = products.compactMap { matcher.match(alert: alert, product: $0, item: makeItem(c.item, now: now)) }
            guard let expect = c.expect else {
                #expect(results.isEmpty, "\(c.name)")
                continue
            }
            let match = try #require(results.first, "\(c.name)")
            #expect(confidenceName(match.confidence) == expect.confidence, "\(c.name)")
            let reasons = Set(match.reasons.map(\.rawValue))
            if let exact = expect.reasons { #expect(reasons == Set(exact), "\(c.name)") }
            if let subset = expect.reasonsInclude { #expect(reasons.isSuperset(of: subset), "\(c.name)") }
        }

        for c in fixture.listCases {
            let now = Golden.day(c.today)
            let uuids = Dictionary(uniqueKeysWithValues: c.items.map { ($0.id ?? "item", UUID()) })
            let items = c.items.map { makeItem($0, id: uuids[$0.id ?? "item"]!, now: now) }
            let dismissed = Set((c.dismissed ?? []).map { RecallMatch.id(alertID: $0.alertId, itemID: uuids[$0.itemId]!) })
            let found = RecallMatcher(now: now, calendar: TestSupport.calendar).matches(alerts: alerts, items: items, dismissed: dismissed)
            #expect(found.first?.alert.id == c.firstAlertId, "\(c.name)")
            if c.firstAlertId == nil { #expect(found.isEmpty, "\(c.name)") }
        }

        for c in fixture.alertDates {
            let expected = Set(c.expected.map { AlertDateSpec(year: $0.year, month: $0.month, day: $0.day) })
            #expect(Set(AlertDateParser.dates(in: c.text)) == expected, "\(c.text)")
        }

        for c in fixture.nameMatches {
            let result = RecallMatcher.nameMatches(itemWords: RecallMatcher.words(c.item), productWords: RecallMatcher.words(c.product))
            #expect(result == c.matches, "\(c.item) ~ \(c.product)")
        }

        for c in fixture.allergens {
            let allergen = try #require(Allergen(rawValue: c.allergen))
            #expect(allergen.matches(alertLabel: c.label) == c.matches, "\(c.allergen) ~ \(c.label)")
        }

        for c in fixture.concerns {
            let alert = try #require(alerts.first { $0.id == c.alertId })
            let profile = c.allergens.compactMap(Allergen.init(rawValue:))
            #expect(alert.concerns(profile).map(\.rawValue) == c.expected, "\(c.alertId)")
        }
    }
}
```

- [ ] **Step 7: Run the Swift runner against the reference implementation**

```bash
cd ios && xcodegen generate && xcodebuild test -project ZeroBite.xcodeproj -scheme ZeroBite -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -only-testing:ZeroBiteTests/GoldenExpiryDateTests -only-testing:ZeroBiteTests/GoldenRecipeTests -only-testing:ZeroBiteTests/GoldenNotificationTests -only-testing:ZeroBiteTests/GoldenRecallTests -quiet; cd ..
```

Expected: `** TEST SUCCEEDED **`. If a case fails, the failure message names it. Cases ported from the existing Swift tests must pass unchanged; for the cases added in this plan (ordinal day, dotted four-digit year, day-month only, numeric month-year, lowercase, leap days, "Mature Cheddar", "Crème fraîche", "Scallions", "Sea salt", "Pistachio Milk Chocolate", the sorted-items reminder case), correct the fixture's expected value to what Swift returns and note the change in the commit message.

- [ ] **Step 8: Run the full iOS suite and commit**

```bash
cd ios && xcodebuild test -project ZeroBite.xcodeproj -scheme ZeroBite -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -quiet; cd ..
git add testdata ios/ZeroBiteTests/GoldenFixtureTests.swift
git commit -m "Add golden fixtures shared by iOS and Android tests

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Gradle project and `:core` skeleton

**Files:**
- Create: `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle.properties`, `android/gradle/libs.versions.toml`, `android/.gitignore`
- Create: `android/core/build.gradle.kts`
- Create: `android/core/src/main/kotlin/com/mazesys/zerobite/core/time/AppClock.kt`
- Create: `android/core/src/test/kotlin/com/mazesys/zerobite/core/Fixtures.kt`
- Test: `android/core/src/test/kotlin/com/mazesys/zerobite/core/time/AppClockTest.kt`
- Generated: Gradle wrapper (`android/gradlew`, `android/gradlew.bat`, `android/gradle/wrapper/*`)

**Interfaces:**
- Produces:
  - `interface AppClock { val timeZone: TimeZone; fun now(): Instant; fun today(): LocalDate }`, `object SystemAppClock : AppClock`, `class FixedClock(instant: Instant, timeZone: TimeZone) : AppClock`
  - Test helper `object Fixtures { val json: Json; fun text(name: String): String; fun sharedAsset(name: String): String; inline fun <reified T> load(name: String): T }` reading `/testdata` and `/shared-assets` via system properties `zerobite.testdata` / `zerobite.sharedAssets`.
  - Gradle task `:core:checkPortable` (part of `:core:check`).

- [ ] **Step 1: Install Gradle once (only to generate the wrapper)**

Run: `brew install gradle && gradle --version`
Expected: Gradle 9.x prints. (Everything after this step uses `./gradlew`.)

- [ ] **Step 2: Write the build files**

`android/settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ZeroBite"
include(":core")
```

`android/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
```

`android/gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
org.gradle.caching=true
org.gradle.configuration-cache=true
kotlin.code.style=official
```

`android/gradle/libs.versions.toml`:

```toml
[versions]
kotlin = "2.4.20"
kotlinx-coroutines = "1.11.0"
kotlinx-datetime = "0.8.0"
kotlinx-serialization = "1.11.0"
ktor = "3.6.0"
junit = "6.1.3"

[libraries]
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinx-datetime" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }
junit-bom = { module = "org.junit:junit-bom", version.ref = "junit" }
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter" }
junit-platform-launcher = { module = "org.junit.platform:junit-platform-launcher" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

`android/.gitignore`:

```
.gradle/
build/
local.properties
.idea/
*.iml
.kotlin/
keystore.properties
```

`android/core/build.gradle.kts`:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    api(libs.kotlinx.datetime)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)

    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}

tasks.test {
    useJUnitPlatform()
    // Golden fixtures and shared assets live at the repo root, shared with the iOS tests.
    systemProperty("zerobite.testdata", rootDir.resolve("../testdata").canonicalPath)
    systemProperty("zerobite.sharedAssets", rootDir.resolve("../shared-assets").canonicalPath)
}

// :core becomes a Kotlin Multiplatform module later, so keep JVM- and Android-only APIs out.
val checkPortable by tasks.registering {
    val sources = fileTree("src/main/kotlin")
    inputs.files(sources)
    doLast {
        val banned = listOf("import java.", "import javax.", "import android.")
        val offenders = sources.files.filter { file -> file.readLines().any { line -> banned.any(line::startsWith) } }
        check(offenders.isEmpty()) { "JVM/Android-only imports in :core: ${offenders.joinToString { it.name }}" }
    }
}
tasks.named("check") { dependsOn(checkPortable) }
```

- [ ] **Step 3: Generate the wrapper**

```bash
cd android && gradle wrapper --gradle-version 9.8.1 && ./gradlew --version; cd ..
```

Expected: `Gradle 9.8.1`.

- [ ] **Step 4: Write the failing clock test**

`android/core/src/test/kotlin/com/mazesys/zerobite/core/time/AppClockTest.kt`:

```kotlin
package com.mazesys.zerobite.core.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class AppClockTest {

    @Test
    fun `today is the calendar day in the clock's time zone`() {
        // 23:30 UTC on 6 Sep is 00:30 BST on 7 Sep.
        val clock = FixedClock(Instant.parse("2026-09-06T23:30:00Z"), TimeZone.of("Europe/London"))
        assertEquals(LocalDate(2026, 9, 7), clock.today())
        assertEquals(LocalDate(2026, 9, 6), FixedClock(clock.now(), TimeZone.UTC).today())
    }
}
```

- [ ] **Step 5: Run it to see it fail**

Run: `cd android && ./gradlew :core:test; cd ..`
Expected: compilation FAILS with `Unresolved reference 'FixedClock'`.

- [ ] **Step 6: Implement the clock**

`android/core/src/main/kotlin/com/mazesys/zerobite/core/time/AppClock.kt`:

```kotlin
package com.mazesys.zerobite.core.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/** The current instant and the user's time zone. Injected everywhere so tests can pin "now". */
interface AppClock {
    val timeZone: TimeZone
    fun now(): Instant
    fun today(): LocalDate = now().toLocalDateTime(timeZone).date
}

object SystemAppClock : AppClock {
    override val timeZone: TimeZone get() = TimeZone.currentSystemDefault()
    override fun now(): Instant = Clock.System.now()
}

class FixedClock(private val instant: Instant, override val timeZone: TimeZone) : AppClock {
    override fun now(): Instant = instant
}
```

- [ ] **Step 7: Add the fixture helper**

`android/core/src/test/kotlin/com/mazesys/zerobite/core/Fixtures.kt`:

```kotlin
package com.mazesys.zerobite.core

import kotlinx.serialization.json.Json
import java.io.File

/** Reads the golden fixtures in /testdata and data in /shared-assets (paths set by Gradle). */
object Fixtures {
    val json = Json { ignoreUnknownKeys = true }

    private fun dir(property: String): File =
        File(System.getProperty(property) ?: error("$property is not set — run tests through Gradle"))

    fun text(name: String): String = File(dir("zerobite.testdata"), name).readText()

    fun sharedAsset(name: String): String = File(dir("zerobite.sharedAssets"), name).readText()

    inline fun <reified T> load(name: String): T = json.decodeFromString(text(name))
}
```

- [ ] **Step 8: Run the tests and the portability check**

Run: `cd android && ./gradlew :core:check; cd ..`
Expected: `BUILD SUCCESSFUL`, 1 test passed.

- [ ] **Step 9: Prove the portability guard bites**

Temporarily add `import java.io.File` as the first import of `AppClock.kt`, run `cd android && ./gradlew :core:checkPortable; cd ..`, expect FAILURE naming `AppClock.kt`, then remove the line and re-run to see it pass.

- [ ] **Step 10: Commit**

```bash
git add android
git commit -m "Add the Android Gradle project with a portable :core module

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Core models and repository interfaces

**Files:**
- Create: `android/core/src/main/kotlin/com/mazesys/zerobite/core/model/FoodItem.kt`
- Create: `android/core/src/main/kotlin/com/mazesys/zerobite/core/model/ItemDraft.kt`
- Create: `android/core/src/main/kotlin/com/mazesys/zerobite/core/model/Recipe.kt`
- Create: `android/core/src/main/kotlin/com/mazesys/zerobite/core/model/ScanResult.kt`
- Create: `android/core/src/main/kotlin/com/mazesys/zerobite/core/inventory/InventoryRepository.kt`
- Create: `android/core/src/main/kotlin/com/mazesys/zerobite/core/recipes/RecipeRepository.kt`
- Test: `android/core/src/test/kotlin/com/mazesys/zerobite/core/model/FoodItemTest.kt`, `ItemDraftTest.kt`, `RecipeCatalogueTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `enum class FoodCategory(val label: String, val defaultShelfLifeDays: Int)` — `DAIRY, MEAT, FISH, VEGETABLES, FRUIT, BAKERY, GRAINS, CONDIMENTS, DRINKS, LEFTOVERS, OTHER`; `val raw: String`; `companion fun fromRaw(raw: String): FoodCategory` (unknown → `OTHER`)
  - `enum class StorageLocation(val label: String)` — `FRIDGE, FREEZER, PANTRY`; `raw`, `fromRaw` (unknown → `FRIDGE`)
  - `enum class ExpiryStatus(val sectionTitle: String)` — `EXPIRED, TODAY, SOON, FRESH` (declaration order = urgency); `val needsAttention: Boolean`
  - `data class FoodItem(id: String, name: String, quantity: Double = 1.0, unit: String = "pcs", category: FoodCategory = OTHER, storage: StorageLocation = FRIDGE, addedAt: Instant, expiresOn: LocalDate, barcode: String? = null, brand: String? = null, notes: String = "", consumedAt: Instant? = null)` with `isConsumed`, `quantityText`, `daysUntilExpiry(today)`, `status(today, soonThresholdDays = 3)`, `expiryDescription(today)`, `shortExpiryLabel(today)`
  - `data class ItemDraft(...)` with `trimmedName`, `isValid`, `makeItem(id: String, now: Instant)`, `applyTo(item: FoodItem): FoodItem`, `companion: unitSuggestions, new(today), from(item)`
  - `@Serializable data class Recipe(id, title, summary, ingredients: List<Recipe.Ingredient>, steps, prepMinutes, servings, tags)` with nested `Ingredient(name, amount = "", optional = false)` and `requiredIngredients`; `object RecipeCatalogue { fun decode(json: String): List<Recipe> }`
  - `data class DateCandidate(date: LocalDate, matchedText: String, sourceLine: String, confidence: Double)`, `data class ProductInfo(barcode: String, name: String, brand: String?, category: FoodCategory?)`, `data class ScanResult(lines, barcode, candidates, suggestedName)` with `bestCandidate`, `isEmpty`
  - `interface InventoryRepository` and `interface RecipeRepository` exactly as below.

- [ ] **Step 1: Write the failing model tests**

`android/core/src/test/kotlin/com/mazesys/zerobite/core/model/FoodItemTest.kt`:

```kotlin
package com.mazesys.zerobite.core.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class FoodItemTest {

    private val today = LocalDate(2026, 9, 7)

    private fun item(days: Int, quantity: Double = 1.0, unit: String = "pcs") = FoodItem(
        id = "id-$days",
        name = "Milk",
        quantity = quantity,
        unit = unit,
        addedAt = Instant.parse("2026-09-01T09:00:00Z"),
        expiresOn = today.plus(days, DateTimeUnit.DAY),
    )

    @Test
    fun `status buckets use calendar days`() {
        assertEquals(ExpiryStatus.EXPIRED, item(-1).status(today))
        assertEquals(ExpiryStatus.TODAY, item(0).status(today))
        assertEquals(ExpiryStatus.SOON, item(3).status(today))
        assertEquals(ExpiryStatus.FRESH, item(4).status(today))
        assertEquals(ExpiryStatus.SOON, item(5).status(today, soonThresholdDays = 5))
    }

    @Test
    fun `statuses are ordered most urgent first`() {
        assertEquals(listOf(ExpiryStatus.EXPIRED, ExpiryStatus.TODAY, ExpiryStatus.SOON, ExpiryStatus.FRESH), ExpiryStatus.entries.sorted())
        assertTrue(ExpiryStatus.SOON.needsAttention)
        assertFalse(ExpiryStatus.FRESH.needsAttention)
    }

    @Test
    fun `expiry descriptions read naturally`() {
        assertEquals("Expired 3 days ago", item(-3).expiryDescription(today))
        assertEquals("Expired yesterday", item(-1).expiryDescription(today))
        assertEquals("Expires today", item(0).expiryDescription(today))
        assertEquals("Expires tomorrow", item(1).expiryDescription(today))
        assertEquals("Expires in 5 days", item(5).expiryDescription(today))
    }

    @Test
    fun `short labels switch to a date after two weeks`() {
        assertEquals("Expired", item(-2).shortExpiryLabel(today))
        assertEquals("Today", item(0).shortExpiryLabel(today))
        assertEquals("Tomorrow", item(1).shortExpiryLabel(today))
        assertEquals("4d", item(4).shortExpiryLabel(today))
        assertEquals("14d", item(14).shortExpiryLabel(today))
        assertEquals("22 Sep", item(15).shortExpiryLabel(today))
    }

    @Test
    fun `quantity text drops trailing zeros`() {
        assertEquals("1 pcs", item(0).quantityText)
        assertEquals("0.5 kg", item(0, 0.5, "kg").quantityText)
        assertEquals("1.25 l", item(0, 1.25, "l").quantityText)
        assertEquals("2 pack", item(0, 2.0, "pack").quantityText)
    }

    @Test
    fun `raw values round trip and unknown values fall back`() {
        assertEquals(FoodCategory.DAIRY, FoodCategory.fromRaw("dairy"))
        assertEquals(FoodCategory.OTHER, FoodCategory.fromRaw("spaceship"))
        assertEquals("leftovers", FoodCategory.LEFTOVERS.raw)
        assertEquals(StorageLocation.PANTRY, StorageLocation.fromRaw("pantry"))
        assertEquals(StorageLocation.FRIDGE, StorageLocation.fromRaw(""))
        assertEquals("Cupboard", StorageLocation.PANTRY.label)
    }
}
```

`android/core/src/test/kotlin/com/mazesys/zerobite/core/model/ItemDraftTest.kt`:

```kotlin
package com.mazesys.zerobite.core.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class ItemDraftTest {

    private val today = LocalDate(2026, 9, 7)
    private val now = Instant.parse("2026-09-07T08:00:00Z")

    @Test
    fun `a new draft expires in a week`() {
        assertEquals(LocalDate(2026, 9, 14), ItemDraft.new(today).expiresOn)
    }

    @Test
    fun `needs a name and a positive quantity`() {
        val draft = ItemDraft.new(today)
        assertFalse(draft.isValid)
        assertFalse(draft.copy(name = "   ").isValid)
        assertFalse(draft.copy(name = "Milk", quantity = 0.0).isValid)
        assertTrue(draft.copy(name = "Milk").isValid)
    }

    @Test
    fun `making an item trims the name and defaults a blank unit`() {
        val item = ItemDraft.new(today).copy(name = "  Milk ", unit = " ", category = FoodCategory.DAIRY).makeItem("abc", now)
        assertEquals("abc", item.id)
        assertEquals("Milk", item.name)
        assertEquals("pcs", item.unit)
        assertEquals(FoodCategory.DAIRY, item.category)
        assertEquals(now, item.addedAt)
        assertEquals(LocalDate(2026, 9, 14), item.expiresOn)
    }

    @Test
    fun `applying keeps identity and history`() {
        val consumed = Instant.parse("2026-09-08T10:00:00Z")
        val original = ItemDraft.new(today).copy(name = "Milk").makeItem("abc", now).copy(consumedAt = consumed)
        val edited = ItemDraft.from(original).copy(name = "Whole milk", quantity = 2.0).applyTo(original)
        assertEquals("abc", edited.id)
        assertEquals(now, edited.addedAt)
        assertEquals(consumed, edited.consumedAt)
        assertEquals("Whole milk", edited.name)
        assertEquals(2.0, edited.quantity)
    }
}
```

`android/core/src/test/kotlin/com/mazesys/zerobite/core/model/RecipeCatalogueTest.kt`:

```kotlin
package com.mazesys.zerobite.core.model

import com.mazesys.zerobite.core.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecipeCatalogueTest {

    @Test
    fun `bundled catalogue loads and is well formed`() {
        val recipes = RecipeCatalogue.decode(Fixtures.sharedAsset("recipes.json"))
        assertTrue(recipes.size >= 30)
        assertEquals(recipes.size, recipes.map { it.id }.toSet().size)
        for (recipe in recipes) {
            assertTrue(recipe.requiredIngredients.isNotEmpty(), "${recipe.id} has no required ingredients")
            assertTrue(recipe.steps.isNotEmpty(), "${recipe.id} has no steps")
        }
    }

    @Test
    fun `ingredient amount and optional default when absent`() {
        val json = """[{"id":"x","title":"X","summary":"","ingredients":[{"name":"eggs"}],"steps":["Cook"],"prepMinutes":5,"servings":1,"tags":[],"futureField":1}]"""
        val ingredient = RecipeCatalogue.decode(json).single().ingredients.single()
        assertEquals(Recipe.Ingredient(name = "eggs", amount = "", optional = false), ingredient)
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `cd android && ./gradlew :core:test; cd ..`
Expected: compilation FAILS (unresolved `FoodItem`, `ItemDraft`, `RecipeCatalogue`).

- [ ] **Step 3: Implement `FoodItem.kt`**

```kotlin
package com.mazesys.zerobite.core.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
import kotlin.math.round
import kotlin.time.Instant

enum class FoodCategory(val label: String, val defaultShelfLifeDays: Int) {
    DAIRY("Dairy & eggs", 7),
    MEAT("Meat", 3),
    FISH("Fish & seafood", 2),
    VEGETABLES("Vegetables", 7),
    FRUIT("Fruit", 6),
    BAKERY("Bakery", 4),
    GRAINS("Grains & pasta", 180),
    CONDIMENTS("Condiments", 90),
    DRINKS("Drinks", 30),
    LEFTOVERS("Leftovers", 3),
    OTHER("Other", 14);

    /** Stored value, identical to the iOS raw value. */
    val raw: String get() = name.lowercase()

    companion object {
        fun fromRaw(raw: String): FoodCategory = entries.firstOrNull { it.raw == raw } ?: OTHER
    }
}

enum class StorageLocation(val label: String) {
    FRIDGE("Fridge"),
    FREEZER("Freezer"),
    PANTRY("Cupboard");

    val raw: String get() = name.lowercase()

    companion object {
        fun fromRaw(raw: String): StorageLocation = entries.firstOrNull { it.raw == raw } ?: FRIDGE
    }
}

/** Lifecycle of an item relative to its expiry date, declared from most to least urgent. */
enum class ExpiryStatus(val sectionTitle: String) {
    EXPIRED("Expired"),
    TODAY("Expires today"),
    SOON("Expiring soon"),
    FRESH("Fresh");

    /** Expired, expiring today, or within the "soon" window. */
    val needsAttention: Boolean get() = this != FRESH
}

/** A single thing in the fridge, freezer or cupboard. */
data class FoodItem(
    val id: String,
    val name: String,
    val quantity: Double = 1.0,
    val unit: String = "pcs",
    val category: FoodCategory = FoodCategory.OTHER,
    val storage: StorageLocation = StorageLocation.FRIDGE,
    val addedAt: Instant,
    val expiresOn: LocalDate,
    val barcode: String? = null,
    /** Brand from the barcode lookup; helps match product recalls. */
    val brand: String? = null,
    val notes: String = "",
    val consumedAt: Instant? = null,
) {
    val isConsumed: Boolean get() = consumedAt != null

    val quantityText: String get() = "${formatQuantity(quantity)} $unit"

    /** Whole calendar days until expiry. Negative when already expired. */
    fun daysUntilExpiry(today: LocalDate): Int = today.daysUntil(expiresOn)

    fun status(today: LocalDate, soonThresholdDays: Int = 3): ExpiryStatus {
        val days = daysUntilExpiry(today)
        return when {
            days < 0 -> ExpiryStatus.EXPIRED
            days == 0 -> ExpiryStatus.TODAY
            days <= soonThresholdDays -> ExpiryStatus.SOON
            else -> ExpiryStatus.FRESH
        }
    }

    /** "Expires in 3 days", "Expired yesterday", ... */
    fun expiryDescription(today: LocalDate): String {
        val days = daysUntilExpiry(today)
        return when {
            days < -1 -> "Expired ${-days} days ago"
            days == -1 -> "Expired yesterday"
            days == 0 -> "Expires today"
            days == 1 -> "Expires tomorrow"
            else -> "Expires in $days days"
        }
    }

    /** Compact label for badges: "Expired", "Today", "2d", "12 Sep". */
    fun shortExpiryLabel(today: LocalDate): String {
        val days = daysUntilExpiry(today)
        return when {
            days < 0 -> "Expired"
            days == 0 -> "Today"
            days == 1 -> "Tomorrow"
            days <= 14 -> "${days}d"
            else -> "${expiresOn.day} ${MONTH_ABBREVIATIONS[expiresOn.month.number - 1]}"
        }
    }
}

internal val MONTH_ABBREVIATIONS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** Up to two decimal places, no trailing zeros: 1 → "1", 0.5 → "0.5", 1.25 → "1.25". */
internal fun formatQuantity(quantity: Double): String {
    val hundredths = round(quantity * 100).toLong()
    val whole = hundredths / 100
    val fraction = hundredths % 100
    return when {
        fraction == 0L -> "$whole"
        fraction % 10 == 0L -> "$whole.${fraction / 10}"
        else -> "$whole.${fraction.toString().padStart(2, '0')}"
    }
}
```

- [ ] **Step 4: Implement `ItemDraft.kt`**

```kotlin
package com.mazesys.zerobite.core.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.time.Instant

/** Editing buffer for the add/edit item form; nothing is saved until the user commits. */
data class ItemDraft(
    val name: String = "",
    val category: FoodCategory = FoodCategory.OTHER,
    val storage: StorageLocation = StorageLocation.FRIDGE,
    val quantity: Double = 1.0,
    val unit: String = "pcs",
    val expiresOn: LocalDate,
    val notes: String = "",
    val barcode: String? = null,
    val brand: String? = null,
) {
    val trimmedName: String get() = name.trim()
    val isValid: Boolean get() = trimmedName.isNotEmpty() && quantity > 0

    private val resolvedUnit: String get() = if (unit.trim().isEmpty()) "pcs" else unit

    fun makeItem(id: String, now: Instant): FoodItem = FoodItem(
        id = id,
        name = trimmedName,
        quantity = quantity,
        unit = resolvedUnit,
        category = category,
        storage = storage,
        addedAt = now,
        expiresOn = expiresOn,
        barcode = barcode,
        brand = brand,
        notes = notes,
    )

    /** The edited item: same id, added date and consumed state. */
    fun applyTo(item: FoodItem): FoodItem = item.copy(
        name = trimmedName,
        quantity = quantity,
        unit = resolvedUnit,
        category = category,
        storage = storage,
        expiresOn = expiresOn,
        notes = notes,
        barcode = barcode,
        brand = brand,
    )

    companion object {
        val unitSuggestions = listOf("pcs", "g", "kg", "ml", "l", "pack", "slices")

        fun new(today: LocalDate): ItemDraft = ItemDraft(expiresOn = today.plus(7, DateTimeUnit.DAY))

        fun from(item: FoodItem): ItemDraft = ItemDraft(
            name = item.name,
            category = item.category,
            storage = item.storage,
            quantity = item.quantity,
            unit = item.unit,
            expiresOn = item.expiresOn,
            notes = item.notes,
            barcode = item.barcode,
            brand = item.brand,
        )
    }
}
```

- [ ] **Step 5: Implement `Recipe.kt` and `ScanResult.kt`**

`Recipe.kt`:

```kotlin
package com.mazesys.zerobite.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A recipe from the bundled catalogue (shared-assets/recipes.json). */
@Serializable
data class Recipe(
    val id: String,
    val title: String,
    val summary: String,
    val ingredients: List<Ingredient>,
    val steps: List<String>,
    val prepMinutes: Int,
    val servings: Int,
    val tags: List<String>,
) {
    @Serializable
    data class Ingredient(
        val name: String,
        val amount: String = "",
        val optional: Boolean = false,
    )

    val requiredIngredients: List<Ingredient> get() = ingredients.filterNot { it.optional }
}

object RecipeCatalogue {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(text: String): List<Recipe> = json.decodeFromString(text)
}
```

`ScanResult.kt`:

```kotlin
package com.mazesys.zerobite.core.model

import kotlinx.datetime.LocalDate

/** A date found on a label, with how confident the parser is that it's the expiry. */
data class DateCandidate(
    val date: LocalDate,
    val matchedText: String,
    val sourceLine: String,
    val confidence: Double,
)

/** Product details resolved from a barcode (Open Food Facts). */
data class ProductInfo(
    val barcode: String,
    val name: String,
    val brand: String?,
    val category: FoodCategory?,
)

/** Output of reading a label: raw text, barcode, and the expiry-date candidates found. */
data class ScanResult(
    val lines: List<String> = emptyList(),
    val barcode: String? = null,
    val candidates: List<DateCandidate> = emptyList(),
    val suggestedName: String? = null,
) {
    val bestCandidate: DateCandidate? get() = candidates.firstOrNull()
    val isEmpty: Boolean get() = lines.isEmpty() && barcode == null
}
```

- [ ] **Step 6: Add the repository interfaces**

`inventory/InventoryRepository.kt`:

```kotlin
package com.mazesys.zerobite.core.inventory

import com.mazesys.zerobite.core.model.FoodItem
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/** The user's food. Implemented with Room on Android; SwiftData stays on iOS. */
interface InventoryRepository {
    /** Every item, consumed or not, soonest expiry first. Emits again after every change. */
    val items: Flow<List<FoodItem>>

    suspend fun item(id: String): FoodItem?
    suspend fun add(item: FoodItem)
    suspend fun update(item: FoodItem)
    suspend fun markConsumed(ids: List<String>, at: Instant)
    suspend fun restore(id: String)
    suspend fun delete(id: String)
    suspend fun deleteAll()
}
```

`recipes/RecipeRepository.kt`:

```kotlin
package com.mazesys.zerobite.core.recipes

import com.mazesys.zerobite.core.model.Recipe

/** Source of recipes: the bundled catalogue today, a shared recipe database later. */
interface RecipeRepository {
    suspend fun recipes(): List<Recipe>
}
```

- [ ] **Step 7: Run the tests**

Run: `cd android && ./gradlew :core:check; cd ..`
Expected: `BUILD SUCCESSFUL`; FoodItemTest, ItemDraftTest, RecipeCatalogueTest pass.

- [ ] **Step 8: Commit**

```bash
git add android/core
git commit -m "Port the food, draft, recipe and scan models to :core

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Expiry date parser

**Files:**
- Create: `android/core/src/main/kotlin/com/mazesys/zerobite/core/scanning/ExpiryDateParser.kt`
- Test: `android/core/src/test/kotlin/com/mazesys/zerobite/core/scanning/ExpiryDateParserTest.kt`

**Interfaces:**
- Consumes: `DateCandidate` (Task 4); fixture `testdata/expiry-dates.json` (Task 2).
- Produces: `class ExpiryDateParser(today: LocalDate)` with `fun parse(text: String): List<DateCandidate>`, `fun parse(lines: List<String>): List<DateCandidate>`, `fun bestDate(lines: List<String>): LocalDate?`; `companion fun containsKeyword(line: String): Boolean`.

- [ ] **Step 1: Write the failing fixture test**

```kotlin
package com.mazesys.zerobite.core.scanning

import com.mazesys.zerobite.core.Fixtures
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Serializable
private data class ExpiryDateFixture(val today: LocalDate, val cases: List<Case>) {
    @Serializable
    data class Case(
        val name: String,
        val lines: List<String>,
        val best: LocalDate? = null,
        val count: Int? = null,
        val bestSourceLine: String? = null,
    )
}

class ExpiryDateParserTest {

    private val fixture = Fixtures.load<ExpiryDateFixture>("expiry-dates.json")

    @TestFactory
    fun `golden expiry dates`() = fixture.cases.map { case ->
        DynamicTest.dynamicTest(case.name) {
            val candidates = ExpiryDateParser(fixture.today).parse(case.lines)
            assertEquals(case.best, candidates.firstOrNull()?.date, "best date")
            case.count?.let { assertEquals(it, candidates.size, "candidate count") }
            case.bestSourceLine?.let { assertEquals(it, candidates.first().sourceLine, "source line") }
        }
    }

    @Test
    fun `text is split into lines`() {
        val parser = ExpiryDateParser(fixture.today)
        assertEquals(LocalDate(2026, 9, 10), parser.parse("PACKED 01/09/2026\nUSE BY 10/09/2026").first().date)
        assertEquals(LocalDate(2026, 9, 10), parser.bestDate(listOf("USE BY 10/09/2026")))
    }

    @Test
    fun `keywords are whole words`() {
        assertTrue(ExpiryDateParser.containsKeyword("Best before end"))
        assertTrue(ExpiryDateParser.containsKeyword("EXP 12/09"))
        assertFalse(ExpiryDateParser.containsKeyword("Express delivery"))
    }
}
```

- [ ] **Step 2: Run to see it fail**

Run: `cd android && ./gradlew :core:test --tests '*ExpiryDateParserTest'; cd ..`
Expected: compilation FAILS — `Unresolved reference 'ExpiryDateParser'`.

- [ ] **Step 3: Implement the parser** (a line-for-line port of `ExpiryDateParser.swift`)

```kotlin
package com.mazesys.zerobite.core.scanning

import com.mazesys.zerobite.core.model.DateCandidate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Finds best-before / use-by dates in OCR'd label text. UK/EU day-month-year ordering is
 * assumed for ambiguous numerics. Port of the iOS ExpiryDateParser — keep them in step via
 * testdata/expiry-dates.json.
 */
class ExpiryDateParser(private val today: LocalDate) {

    private enum class Kind { YMD, DMY, D_MON_Y, MON_D_Y, D_MON, MON_Y, M_Y, COMPACT8, COMPACT6 }

    private class Pattern(val kind: Kind, val regex: Regex, val baseConfidence: Double)

    fun parse(text: String): List<DateCandidate> = parse(text.lines())

    /** Candidates sorted by confidence (desc), then date (asc), one per day. */
    fun parse(lines: List<String>): List<DateCandidate> {
        val cleaned = lines.map { it.trim() }.filter { it.isNotEmpty() }
        val found = mutableListOf<DateCandidate>()

        cleaned.forEachIndexed { index, line ->
            val hasKeyword = containsKeyword(line)
            val previousHasKeyword = index > 0 && containsKeyword(cleaned[index - 1])
            val looksLikeManufacture = MANUFACTURE.containsMatchIn(line)
            val consumed = mutableListOf<IntRange>()

            for (pattern in PATTERNS) {
                // Bare digit runs are only worth considering when the line says "use by" etc.
                if ((pattern.kind == Kind.COMPACT6 || pattern.kind == Kind.COMPACT8) && !hasKeyword) continue

                for (match in pattern.regex.findAll(line)) {
                    if (consumed.any { it.overlaps(match.range) }) continue
                    consumed += match.range
                    val date = makeDate(pattern.kind, match) ?: continue

                    var confidence = pattern.baseConfidence
                    if (hasKeyword) confidence += 0.2 else if (previousHasKeyword) confidence += 0.15
                    if (looksLikeManufacture) confidence -= 0.3

                    val days = today.daysUntil(date)
                    if (days < -365) confidence -= 0.3 else if (days < -30) confidence -= 0.1
                    if (days > 365 * 5) confidence -= 0.15

                    found += DateCandidate(date, match.value, line, confidence.coerceIn(0.0, 1.0))
                }
            }
        }

        val bestPerDay = LinkedHashMap<LocalDate, DateCandidate>()
        for (candidate in found) {
            val existing = bestPerDay[candidate.date]
            if (existing != null && existing.confidence >= candidate.confidence) continue
            bestPerDay[candidate.date] = candidate
        }
        return bestPerDay.values.sortedWith(
            compareByDescending<DateCandidate> { it.confidence }.thenBy { it.date }
        )
    }

    fun bestDate(lines: List<String>): LocalDate? = parse(lines).firstOrNull()?.date

    private fun makeDate(kind: Kind, match: MatchResult): LocalDate? {
        fun group(i: Int): String = match.groups[i]?.value.orEmpty()
        fun int(i: Int): Int? = group(i).toIntOrNull()
        fun monthName(i: Int): Int? = MONTH_NAMES.indexOf(group(i).lowercase().take(3)).takeIf { it >= 0 }?.plus(1)
        fun year(i: Int): Int? = int(i)?.let { if (it < 100) 2000 + it else it }

        var day: Int? = null
        var month: Int? = null
        var yearValue: Int? = null
        var endOfMonth = false
        var yearInferred = false

        when (kind) {
            Kind.YMD -> { yearValue = year(1); month = int(2); day = int(3) }
            Kind.DMY -> {
                day = int(1); month = int(2); yearValue = year(3)
                // "09/14/2026" can only be month-first; "12/09/2026" is read day-first (UK).
                val d = day
                val m = month
                if (d != null && m != null && d <= 12 && m > 12) { day = m; month = d }
            }
            Kind.D_MON_Y -> { day = int(1); month = monthName(2); yearValue = year(3) }
            Kind.MON_D_Y -> { month = monthName(1); day = int(2); yearValue = year(3) }
            Kind.D_MON -> { day = int(1); month = monthName(2); yearInferred = true }
            Kind.MON_Y -> { month = monthName(1); yearValue = year(2); endOfMonth = true }
            Kind.M_Y -> { month = int(1); yearValue = year(2); endOfMonth = true }
            Kind.COMPACT8, Kind.COMPACT6 -> { day = int(1); month = int(2); yearValue = year(3) }
        }

        val m = month?.takeIf { it in 1..12 } ?: return null
        if (yearInferred) yearValue = today.year
        val y = yearValue?.takeIf { it in (today.year - 2)..(today.year + 15) } ?: return null

        var date = if (endOfMonth) {
            LocalDate(y, m, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
        } else {
            val d = day?.takeIf { it in 1..31 } ?: return null
            // Rejects impossible dates such as 31 Feb.
            runCatching { LocalDate(y, m, d) }.getOrNull() ?: return null
        }

        if (yearInferred && date < today.minus(60, DateTimeUnit.DAY)) {
            date = date.plus(1, DateTimeUnit.YEAR)
        }
        return date
    }

    companion object {
        private val KEYWORD = Regex(
            """\b(best before end|best before|best by|bbe|bb|use by|use before|expiry|expires|expire|exp|display until|sell by|consume by|eat by)(?![a-z])""",
            RegexOption.IGNORE_CASE,
        )
        private val MANUFACTURE = Regex(
            """\b(packed on|packed|manufactured|mfg|mfd|prod\.?|production|batch|lot)(?![a-z])""",
            RegexOption.IGNORE_CASE,
        )
        private val MONTH_NAMES = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

        private const val MONTH = """(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.?"""
        private const val NOT_DIGIT_BEFORE = """(?<!\d)"""
        private const val NOT_DIGIT_AFTER = """(?!\d)"""

        private fun rx(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

        private val PATTERNS = listOf(
            Pattern(Kind.YMD, rx(NOT_DIGIT_BEFORE + """(\d{4})[./-](\d{1,2})[./-](\d{1,2})""" + NOT_DIGIT_AFTER), 0.8),
            Pattern(Kind.D_MON_Y, rx(NOT_DIGIT_BEFORE + """(\d{1,2})(?:st|nd|rd|th)?\s*[-./]?\s*""" + MONTH + """\s*[-./,']?\s*(\d{2,4})""" + NOT_DIGIT_AFTER), 0.85),
            Pattern(Kind.MON_D_Y, rx("""\b""" + MONTH + """\s*(\d{1,2})(?:st|nd|rd|th)?,?\s+(\d{2,4})""" + NOT_DIGIT_AFTER), 0.8),
            Pattern(Kind.DMY, rx(NOT_DIGIT_BEFORE + """(\d{1,2})[./-](\d{1,2})[./-](\d{2,4})""" + NOT_DIGIT_AFTER), 0.75),
            Pattern(Kind.MON_Y, rx("""\b""" + MONTH + """\s*[-./,']?\s*(\d{4})""" + NOT_DIGIT_AFTER), 0.6),
            Pattern(Kind.D_MON, rx(NOT_DIGIT_BEFORE + """(\d{1,2})(?:st|nd|rd|th)?\s*[-./]?\s*""" + MONTH + """\b"""), 0.5),
            Pattern(Kind.M_Y, rx("""(?<![\d./-])(\d{1,2})[./-](\d{4})""" + NOT_DIGIT_AFTER), 0.55),
            Pattern(Kind.COMPACT8, rx(NOT_DIGIT_BEFORE + """(\d{2})(\d{2})(\d{4})""" + NOT_DIGIT_AFTER), 0.3),
            Pattern(Kind.COMPACT6, rx(NOT_DIGIT_BEFORE + """(\d{2})(\d{2})(\d{2})""" + NOT_DIGIT_AFTER), 0.25),
        )

        /** True when the line is about the expiry date ("use by", "best before", "exp"…). */
        fun containsKeyword(line: String): Boolean = KEYWORD.containsMatchIn(line)

        private fun IntRange.overlaps(other: IntRange) = first <= other.last && other.first <= last
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `cd android && ./gradlew :core:test --tests '*ExpiryDateParserTest'; cd ..`
Expected: all 25 golden cases and 2 unit tests PASS. A failing golden case means the port differs from Swift — compare the pattern order, group indexes and confidence arithmetic with `ios/ZeroBite/Services/Scanning/ExpiryDateParser.swift`.

- [ ] **Step 5: Commit**

```bash
git add android/core
git commit -m "Port the expiry date parser to :core

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Product name guesser and label scanner

**Files:**
- Create: `android/core/src/main/kotlin/com/mazesys/zerobite/core/scanning/ProductNameGuesser.kt`
- Create: `android/core/src/main/kotlin/com/mazesys/zerobite/core/scanning/LabelScanner.kt`
- Test: `android/core/src/test/kotlin/com/mazesys/zerobite/core/scanning/ProductNameGuesserTest.kt`

**Interfaces:**
- Consumes: `ExpiryDateParser` (Task 5), `ScanResult` (Task 4), fixture `product-names.json`.
- Produces: `object ProductNameGuesser { val BLOCKLIST: List<String>; fun guess(lines: List<String>): String?; fun prettify(text: String): String }`, `object LabelScanner { fun analyse(lines: List<String>, barcode: String?, today: LocalDate): ScanResult }` — Plan 3's scanner feeds ML Kit output into `analyse`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.mazesys.zerobite.core.scanning

import com.mazesys.zerobite.core.Fixtures
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@Serializable
private data class ProductNameFixture(val cases: List<Case>) {
    @Serializable
    data class Case(val name: String, val lines: List<String>, val expected: String? = null)
}

class ProductNameGuesserTest {

    @TestFactory
    fun `golden product names`() = Fixtures.load<ProductNameFixture>("product-names.json").cases.map { case ->
        DynamicTest.dynamicTest(case.name) {
            assertEquals(case.expected, ProductNameGuesser.guess(case.lines))
        }
    }

    @Test
    fun `prettify title-cases each word`() {
        assertEquals("Semi Skimmed Milk", ProductNameGuesser.prettify("SEMI  SKIMMED milk"))
    }

    @Test
    fun `analyse combines text, barcode and dates`() {
        val result = LabelScanner.analyse(
            lines = listOf("GREEK YOGURT", "USE BY 14 SEP 2026"),
            barcode = "5000000000000",
            today = LocalDate(2026, 9, 7),
        )
        assertEquals("5000000000000", result.barcode)
        assertEquals("Greek Yogurt", result.suggestedName)
        assertEquals(LocalDate(2026, 9, 14), assertNotNull(result.bestCandidate).date)
        assertFalse(result.isEmpty)
    }
}
```

- [ ] **Step 2: Run to see it fail**

Run: `cd android && ./gradlew :core:test --tests '*ProductNameGuesserTest'; cd ..`
Expected: compilation FAILS — `Unresolved reference 'ProductNameGuesser'`.

- [ ] **Step 3: Implement**

`ProductNameGuesser.kt`:

```kotlin
package com.mazesys.zerobite.core.scanning

/** Picks the line of OCR'd label text most likely to be the product name. */
object ProductNameGuesser {

    /** Lines containing any of these are label boilerplate, not a product name. */
    val BLOCKLIST: List<String> = listOf(
        "ingredients", "nutrition", "storage", "keep refrigerated", "keep chilled", "refrigerate",
        "www", "http", "kcal", "kj", "per 100", "typical values", "allergen", "contains", "recycle",
        "barcode", "batch", "lot ", "net weight", "energy", "protein", "carbohydrate", "fat ",
        "salt", "sugars", "fibre", "serving", "once opened", "made in", "produced", "packed",
        "ltd", "plc", "gmbh", "inc.", "©", "®", "tesco", "sainsbury", "asda", "aldi", "lidl",
        "morrisons", "waitrose", "co-op", "m&s", "marks & spencer", "ocado", "iceland",
    )

    fun guess(lines: List<String>): String? {
        var bestScore = 0.0
        var bestText: String? = null

        for (raw in lines) {
            val text = raw.trim()
            if (text.length !in 3..40) continue
            val lower = text.lowercase()
            if (BLOCKLIST.any { lower.contains(it) }) continue
            if (ExpiryDateParser.containsKeyword(text)) continue

            val letters = text.count { it.isLetter() }
            val digits = text.count { it.isDigit() }
            if (letters < 3 || digits > 2 || letters.toDouble() / text.length < 0.6) continue

            val words = text.split(' ').count { it.isNotEmpty() }
            var score = letters + minOf(words, 4) * 2.0
            if (text == text.uppercase()) score += 3 // pack fronts shout the product name

            if (bestText == null || score > bestScore) {
                bestScore = score
                bestText = text
            }
        }
        return bestText?.let(::prettify)
    }

    fun prettify(text: String): String =
        text.lowercase()
            .split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }
}
```

`LabelScanner.kt`:

```kotlin
package com.mazesys.zerobite.core.scanning

import com.mazesys.zerobite.core.model.ScanResult
import kotlinx.datetime.LocalDate

/** Turns text and a barcode already read from a label into a ScanResult. */
object LabelScanner {
    fun analyse(lines: List<String>, barcode: String?, today: LocalDate): ScanResult = ScanResult(
        lines = lines,
        barcode = barcode,
        candidates = ExpiryDateParser(today).parse(lines),
        suggestedName = ProductNameGuesser.guess(lines),
    )
}
```

- [ ] **Step 4: Run the tests**

Run: `cd android && ./gradlew :core:check; cd ..`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add android/core
git commit -m "Port the product name guesser and label scanner to :core

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Recipe matcher

**Files:**
- Create: `android/core/src/main/kotlin/com/mazesys/zerobite/core/text/TextFolding.kt`
- Create: `android/core/src/main/kotlin/com/mazesys/zerobite/core/recipes/RecipeMatcher.kt`
- Test: `android/core/src/test/kotlin/com/mazesys/zerobite/core/text/TextFoldingTest.kt`, `android/core/src/test/kotlin/com/mazesys/zerobite/core/recipes/RecipeMatcherTest.kt`

**Interfaces:**
- Consumes: `FoodItem`, `Recipe`, `ExpiryStatus` (Task 4); fixture `recipe-matching.json`.
- Produces:
  - `object TextFolding { fun foldDiacritics(text: String): String }` (also used by Task 10)
  - `data class MatchedIngredient(ingredient: Recipe.Ingredient, item: FoodItem?)` with `isStaple`
  - `data class RecipeMatch(recipe, matched, missing, missingOptional, expiringItems, score)` with `id`, `canMake`, `usedItems`, `requiredCount`, `requiredMatchedCount`, `uses(itemId: String)`
  - `class RecipeMatcher(today: LocalDate, soonThresholdDays: Int = 3)` with `match(recipes, inventory): List<RecipeMatch>`, `match(recipe, inventory): RecipeMatch`, `bestItem(ingredient, items): FoodItem?`; companion `matches(ingredient, itemName)`, `isStaple(name)`, `normalize(text)`, `singularize(word)`, `containsWords(needle, haystack)`

- [ ] **Step 1: Write the failing tests**

`TextFoldingTest.kt`:

```kotlin
package com.mazesys.zerobite.core.text

import kotlin.test.Test
import kotlin.test.assertEquals

class TextFoldingTest {
    @Test
    fun `folds accented Latin letters`() {
        assertEquals("Creme fraiche", TextFolding.foldDiacritics("Crème fraîche"))
        assertEquals("Gruyere, jalapeno, Emmental", TextFolding.foldDiacritics("Gruyère, jalapeño, Emmental"))
        assertEquals("O’Brien Fine Foods", TextFolding.foldDiacritics("O’Brien Fine Foods"))
    }
}
```

`RecipeMatcherTest.kt`:

```kotlin
package com.mazesys.zerobite.core.recipes

import com.mazesys.zerobite.core.Fixtures
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.Recipe
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@Serializable
private data class RecipeFixture(
    val today: LocalDate,
    val soonThresholdDays: Int,
    val nameMatches: List<NameMatch>,
    val staples: List<Staple>,
    val recipes: List<Recipe>,
    val cases: List<Case>,
) {
    @Serializable data class NameMatch(val ingredient: String, val item: String, val matches: Boolean)
    @Serializable data class Staple(val name: String, val isStaple: Boolean)
    @Serializable data class Item(val id: String, val name: String, val expiresInDays: Int, val consumed: Boolean = false)
    @Serializable data class Expect(
        val canMake: Boolean? = null,
        val missing: List<String>? = null,
        val missingOptional: List<String>? = null,
        val staples: List<String>? = null,
        val usedItemIds: List<String>? = null,
        val expiringItemIds: List<String>? = null,
        val ingredientItems: Map<String, String>? = null,
    )
    @Serializable data class Case(
        val name: String,
        val inventory: List<Item>,
        val recipes: List<String>,
        val ranking: List<String>? = null,
        val matches: Map<String, Expect> = emptyMap(),
    )
}

class RecipeMatcherTest {

    private val fixture = Fixtures.load<RecipeFixture>("recipe-matching.json")
    private val addedAt = Instant.parse("2026-09-01T09:00:00Z")

    @TestFactory
    fun `golden name matches`() = fixture.nameMatches.map { m ->
        DynamicTest.dynamicTest("${m.ingredient} ~ ${m.item}") {
            assertEquals(m.matches, RecipeMatcher.matches(m.ingredient, m.item))
        }
    }

    @TestFactory
    fun `golden staples`() = fixture.staples.map { s ->
        DynamicTest.dynamicTest(s.name) { assertEquals(s.isStaple, RecipeMatcher.isStaple(s.name)) }
    }

    @TestFactory
    fun `golden matching cases`() = fixture.cases.map { case ->
        DynamicTest.dynamicTest(case.name) {
            val inventory = case.inventory.map {
                FoodItem(
                    id = it.id,
                    name = it.name,
                    addedAt = addedAt,
                    expiresOn = fixture.today.plus(it.expiresInDays, DateTimeUnit.DAY),
                    consumedAt = if (it.consumed) addedAt else null,
                )
            }
            val recipes = case.recipes.map { id -> fixture.recipes.first { it.id == id } }
            val ranked = RecipeMatcher(fixture.today, fixture.soonThresholdDays).match(recipes, inventory)

            case.ranking?.let { assertEquals(it, ranked.map { m -> m.recipe.id }, "ranking") }
            for ((recipeId, expect) in case.matches) {
                val match = ranked.first { it.recipe.id == recipeId }
                expect.canMake?.let { assertEquals(it, match.canMake, "canMake") }
                expect.missing?.let { assertEquals(it, match.missing.map { i -> i.name }, "missing") }
                expect.missingOptional?.let { assertEquals(it, match.missingOptional.map { i -> i.name }, "missingOptional") }
                expect.staples?.let { assertEquals(it, match.matched.filter { m -> m.isStaple }.map { m -> m.ingredient.name }, "staples") }
                expect.usedItemIds?.let { assertEquals(it, match.usedItems.map { i -> i.id }, "usedItems") }
                expect.expiringItemIds?.let { assertEquals(it, match.expiringItems.map { i -> i.id }, "expiringItems") }
                expect.ingredientItems?.forEach { (ingredient, itemId) ->
                    assertEquals(itemId, match.matched.first { it.ingredient.name == ingredient }.item?.id, ingredient)
                }
            }
        }
    }

    @Test
    fun `normalize folds, strips punctuation and singularises`() {
        assertEquals("creme fraiche", RecipeMatcher.normalize("Crème-fraîche"))
        assertEquals("cherry tomato", RecipeMatcher.normalize("Cherry tomatoes"))
        assertEquals("berry", RecipeMatcher.normalize("Berries"))
        assertEquals("hummus", RecipeMatcher.normalize("Hummus"))
        assertEquals("peach", RecipeMatcher.normalize("peaches"))
    }

    @Test
    fun `recipe with only staples in the fridge ranks last`() {
        val stock = Recipe("stock", "Stock", "", listOf(Recipe.Ingredient("salt"), Recipe.Ingredient("water")), listOf("Boil"), 5, 1, emptyList())
        val match = RecipeMatcher(fixture.today).match(stock, emptyList())
        assertEquals(true, match.canMake)
        assertEquals(20.0, match.score) // 100 coverage + 20 can-make − 100 nothing from the fridge
    }
}
```

- [ ] **Step 2: Run to see them fail**

Run: `cd android && ./gradlew :core:test --tests '*RecipeMatcherTest' --tests '*TextFoldingTest'; cd ..`
Expected: compilation FAILS — unresolved `TextFolding`, `RecipeMatcher`.

- [ ] **Step 3: Implement `TextFolding.kt`**

```kotlin
package com.mazesys.zerobite.core.text

/**
 * Strips accents from Latin letters ("Crème" → "Creme"). A lookup table rather than
 * java.text.Normalizer so :core stays portable to Kotlin Multiplatform.
 */
object TextFolding {

    private val FOLDS: Map<Char, Char> = buildMap {
        fun add(base: Char, accented: String) = accented.forEach { put(it, base) }
        add('a', "àáâãäåāăą"); add('A', "ÀÁÂÃÄÅĀĂĄ")
        add('c', "çćĉċč"); add('C', "ÇĆĈĊČ")
        add('d', "ďđ"); add('D', "ĎĐ")
        add('e', "èéêëēĕėęě"); add('E', "ÈÉÊËĒĔĖĘĚ")
        add('g', "ĝğġģ"); add('G', "ĜĞĠĢ")
        add('i', "ìíîïĩīĭįı"); add('I', "ÌÍÎÏĨĪĬĮİ")
        add('l', "ĺļľŀł"); add('L', "ĹĻĽĿŁ")
        add('n', "ñńņň"); add('N', "ÑŃŅŇ")
        add('o', "òóôõöøōŏő"); add('O', "ÒÓÔÕÖØŌŎŐ")
        add('r', "ŕŗř"); add('R', "ŔŖŘ")
        add('s', "śŝşš"); add('S', "ŚŜŞŠ")
        add('t', "ţťŧ"); add('T', "ŢŤŦ")
        add('u', "ùúûüũūŭůűų"); add('U', "ÙÚÛÜŨŪŬŮŰŲ")
        add('y', "ýÿŷ"); add('Y', "ÝŸŶ")
        add('z', "źżž"); add('Z', "ŹŻŽ")
    }

    fun foldDiacritics(text: String): String = buildString(text.length) {
        for (c in text) append(FOLDS[c] ?: c)
    }
}
```

- [ ] **Step 4: Implement `RecipeMatcher.kt`**

```kotlin
package com.mazesys.zerobite.core.recipes

import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.Recipe
import com.mazesys.zerobite.core.text.TextFolding
import kotlinx.datetime.LocalDate

data class MatchedIngredient(
    val ingredient: Recipe.Ingredient,
    /** The fridge item satisfying this ingredient; null means it's a store-cupboard staple. */
    val item: FoodItem?,
) {
    val isStaple: Boolean get() = item == null
}

data class RecipeMatch(
    val recipe: Recipe,
    val matched: List<MatchedIngredient>,
    val missing: List<Recipe.Ingredient>,
    val missingOptional: List<Recipe.Ingredient>,
    val expiringItems: List<FoodItem>,
    val score: Double,
) {
    val id: String get() = recipe.id
    val canMake: Boolean get() = missing.isEmpty()
    val usedItems: List<FoodItem> get() = matched.mapNotNull { it.item }.distinctBy { it.id }
    val requiredCount: Int get() = recipe.requiredIngredients.size
    val requiredMatchedCount: Int get() = matched.count { !it.ingredient.optional }

    fun uses(itemId: String): Boolean = matched.any { it.item?.id == itemId }
}

/**
 * Scores recipes against what's in the fridge, favouring recipes that use up food about to
 * expire. Port of the iOS RecipeMatcher — keep them in step via testdata/recipe-matching.json.
 */
class RecipeMatcher(private val today: LocalDate, private val soonThresholdDays: Int = 3) {

    fun match(recipes: List<Recipe>, inventory: List<FoodItem>): List<RecipeMatch> {
        val available = inventory.filterNot { it.isConsumed }
        return recipes
            .map { match(it, available) }
            .sortedWith(compareByDescending<RecipeMatch> { it.score }.thenBy { it.recipe.title })
    }

    fun match(recipe: Recipe, inventory: List<FoodItem>): RecipeMatch {
        val available = inventory.filterNot { it.isConsumed }
        val matched = mutableListOf<MatchedIngredient>()
        val missing = mutableListOf<Recipe.Ingredient>()
        val missingOptional = mutableListOf<Recipe.Ingredient>()

        for (ingredient in recipe.ingredients) {
            val item = bestItem(ingredient.name, available)
            when {
                item != null -> matched += MatchedIngredient(ingredient, item)
                isStaple(ingredient.name) -> matched += MatchedIngredient(ingredient, null)
                ingredient.optional -> missingOptional += ingredient
                else -> missing += ingredient
            }
        }

        val required = recipe.requiredIngredients.size
        val requiredMatched = matched.count { !it.ingredient.optional }
        val coverage = if (required == 0) 1.0 else requiredMatched.toDouble() / required

        val expiring = matched
            .mapNotNull { it.item }
            .filter { it.status(today, soonThresholdDays).needsAttention }
            .distinctBy { it.id }

        val realMatches = matched.count { it.item != null }
        var score = coverage * 100
        score += expiring.size * 15
        score -= missing.size * 5
        if (missing.isEmpty()) score += 20
        if (realMatches == 0) score -= 100 // only staples: not really "makeable from the fridge"

        return RecipeMatch(recipe, matched, missing, missingOptional, expiring, score)
    }

    /** Prefers the item that expires soonest when several satisfy the ingredient. */
    fun bestItem(ingredient: String, items: List<FoodItem>): FoodItem? =
        items.filter { matches(ingredient, it.name) }.minByOrNull { it.expiresOn }

    companion object {

        /** Assumed to always be in the cupboard; never counted as "missing". */
        val STAPLES: Set<String> = listOf(
            "salt", "black pepper", "olive oil", "vegetable oil", "oil", "water", "sugar", "flour",
            "plain flour", "vinegar", "soy sauce", "stock", "stock cube", "chilli flakes", "dried herbs",
        ).map(::normalize).toSet()

        /** Names treated as interchangeable when matching. */
        val SYNONYM_GROUPS: List<List<String>> = listOf(
            listOf("cheese", "cheddar", "mozzarella", "parmesan", "feta", "gouda", "brie", "halloumi", "gruyere", "emmental"),
            listOf("chicken", "chicken breast", "chicken thigh"),
            listOf("yogurt", "yoghurt", "greek yogurt", "natural yogurt", "skyr"),
            listOf("courgette", "zucchini"),
            listOf("aubergine", "eggplant"),
            listOf("coriander", "cilantro"),
            listOf("spring onion", "scallion", "green onion"),
            listOf("rocket", "arugula"),
            listOf("mince", "minced beef", "beef mince", "ground beef", "beef"),
            listOf("pasta", "spaghetti", "penne", "fusilli", "tagliatelle", "linguine", "macaroni", "rigatoni"),
            listOf("rice", "basmati", "jasmine rice", "long grain rice"),
            listOf("bread", "sourdough", "baguette", "ciabatta", "roll", "bap", "pitta", "pita", "loaf"),
            listOf("cream", "double cream", "single cream", "creme fraiche"),
            listOf("bacon", "pancetta", "lardons"),
            listOf("bell pepper", "red pepper", "green pepper", "yellow pepper", "capsicum"),
            listOf("tortilla", "wrap", "flatbread"),
            listOf("noodle", "egg noodle", "rice noodle", "ramen"),
            listOf("berry", "strawberry", "blueberry", "raspberry", "blackberry", "mixed berries"),
            listOf("prawn", "shrimp"),
            listOf("lettuce", "salad leaves", "mixed leaves", "romaine", "iceberg", "little gem"),
            listOf("tuna", "tinned tuna"),
            listOf("banana"),
        ).map { group -> group.map(::normalize) }

        fun matches(ingredient: String, itemName: String): Boolean {
            val a = normalize(ingredient)
            val b = normalize(itemName)
            if (a.isEmpty() || b.isEmpty()) return false
            if (a == b) return true
            if (containsWords(a, b) || containsWords(b, a)) return true
            val ga = synonymGroup(a)
            val gb = synonymGroup(b)
            return ga != null && ga == gb
        }

        fun isStaple(name: String): Boolean {
            val normalized = normalize(name)
            return normalized in STAPLES || STAPLES.any { containsWords(it, normalized) }
        }

        /** Lowercase, strip accents and punctuation, singularise each word. */
        fun normalize(text: String): String {
            val folded = TextFolding.foldDiacritics(text).lowercase()
            val cleaned = buildString(folded.length) {
                for (c in folded) append(if (c.isLetter() || c == ' ') c else ' ')
            }
            return cleaned.split(' ').filter { it.isNotEmpty() }.joinToString(" ") { singularize(it) }
        }

        fun singularize(word: String): String {
            if (word.length <= 3) return word
            if (word.endsWith("ies")) return word.dropLast(3) + "y"
            if (word.endsWith("oes")) return word.dropLast(2)
            if (word.endsWith("ss") || word.endsWith("us") || word.endsWith("is")) return word
            if (word.endsWith("ches") || word.endsWith("shes") || word.endsWith("xes") || word.endsWith("ses")) {
                return word.dropLast(2)
            }
            if (word.endsWith("s")) return word.dropLast(1)
            return word
        }

        /** Whole-word phrase containment: "onion" is in "spring onion" but not in "onionring". */
        fun containsWords(needle: String, haystack: String): Boolean =
            needle.length >= 3 && " $haystack ".contains(" $needle ")

        private fun synonymGroup(normalizedName: String): Int? =
            SYNONYM_GROUPS.indexOfFirst { group ->
                group.any { it == normalizedName || containsWords(it, normalizedName) }
            }.takeIf { it >= 0 }
    }
}
```

- [ ] **Step 5: Run the tests**

Run: `cd android && ./gradlew :core:check; cd ..`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add android/core
git commit -m "Port the recipe matcher to :core with portable accent folding

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Expiry notification planner

**Files:**
- Create: `android/core/src/main/kotlin/com/mazesys/zerobite/core/notifications/ExpiryNotificationPlanner.kt`
- Test: `android/core/src/test/kotlin/com/mazesys/zerobite/core/notifications/ExpiryNotificationPlannerTest.kt`

**Interfaces:**
- Consumes: fixture `notification-plans.json`.
- Produces: `data class ExpiringItemSummary(id: String, name: String, expiresOn: LocalDate)`, `data class PlannedNotification(id: String, itemId: String, title: String, body: String, fireAt: Instant)`, `data class NotificationPreferences(enabled: Boolean, leadDays: Int, hour: Int, minute: Int)` with `companion val DEFAULT`, `class ExpiryNotificationPlanner(timeZone: TimeZone, limit: Int = 60)` with `plan(items, preferences, now): List<PlannedNotification>` and `plan(item, preferences, now)`; `companion const val ID_PREFIX = "zerobite.item."`. Plan 3's `AlarmReminderScheduler` consumes these.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.mazesys.zerobite.core.notifications

import com.mazesys.zerobite.core.Fixtures
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@Serializable
private data class NotificationFixture(val timeZone: String, val now: LocalDateTime, val cases: List<Case>) {
    @Serializable data class Prefs(val enabled: Boolean, val leadDays: Int, val hour: Int, val minute: Int)
    @Serializable data class Item(val id: String, val name: String, val expiresOn: LocalDate)
    @Serializable data class Expected(val itemId: String, val suffix: String, val title: String, val body: String, val fireAt: LocalDateTime)
    @Serializable data class Case(val name: String, val preferences: Prefs, val items: List<Item>, val expected: List<Expected>)
}

class ExpiryNotificationPlannerTest {

    private val fixture = Fixtures.load<NotificationFixture>("notification-plans.json")
    private val zone = TimeZone.of(fixture.timeZone)
    private val planner = ExpiryNotificationPlanner(zone)

    @TestFactory
    fun `golden notification plans`() = fixture.cases.map { case ->
        DynamicTest.dynamicTest(case.name) {
            val p = case.preferences
            val planned = planner.plan(
                items = case.items.map { ExpiringItemSummary(it.id, it.name, it.expiresOn) },
                preferences = NotificationPreferences(p.enabled, p.leadDays, p.hour, p.minute),
                now = fixture.now.toInstant(zone),
            )
            val expected = case.expected.map {
                PlannedNotification(
                    id = "${ExpiryNotificationPlanner.ID_PREFIX}${it.itemId}.${it.suffix}",
                    itemId = it.itemId,
                    title = it.title,
                    body = it.body,
                    fireAt = it.fireAt.toInstant(zone),
                )
            }
            assertEquals(expected, planned)
        }
    }

    @Test
    fun `fire times follow local time across the clocks change`() {
        // UK clocks go back on 25 Oct 2026: 09:30 is 08:30Z before and 09:30Z after.
        val planned = planner.plan(
            item = ExpiringItemSummary("ham", "Ham", LocalDate(2026, 10, 26)),
            preferences = NotificationPreferences(enabled = true, leadDays = 3, hour = 9, minute = 30),
            now = Instant.parse("2026-10-01T00:00:00Z"),
        )
        assertEquals(
            listOf("2026-10-23T08:30:00Z", "2026-10-26T09:30:00Z", "2026-10-27T09:30:00Z").map(Instant::parse),
            planned.map { it.fireAt },
        )
    }

    @Test
    fun `limit keeps the soonest reminders`() {
        val items = (0 until 40).map { ExpiringItemSummary("item-$it", "Item $it", LocalDate(2026, 10, 1 + it % 28)) }
        val planned = planner.plan(items, NotificationPreferences(true, 3, 9, 30), fixture.now.toInstant(zone))
        assertEquals(60, planned.size)
        assertEquals(planned.sortedBy { it.fireAt }, planned)
        assertTrue(NotificationPreferences.DEFAULT.let { !it.enabled && it.leadDays == 3 && it.hour == 9 && it.minute == 0 })
    }
}
```

- [ ] **Step 2: Run to see it fail**

Run: `cd android && ./gradlew :core:test --tests '*ExpiryNotificationPlannerTest'; cd ..`
Expected: compilation FAILS — unresolved `ExpiryNotificationPlanner`.

- [ ] **Step 3: Implement**

```kotlin
package com.mazesys.zerobite.core.notifications

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Instant

data class ExpiringItemSummary(val id: String, val name: String, val expiresOn: LocalDate)

data class PlannedNotification(
    val id: String,
    val itemId: String,
    val title: String,
    val body: String,
    val fireAt: Instant,
)

data class NotificationPreferences(
    val enabled: Boolean,
    /** Days before expiry for the first heads-up. */
    val leadDays: Int,
    val hour: Int,
    val minute: Int,
) {
    companion object {
        val DEFAULT = NotificationPreferences(enabled = false, leadDays = 3, hour = 9, minute = 0)
    }
}

/**
 * Pure planning of which reminders each item needs: a heads-up `leadDays` before, one on the
 * day, one the day after. Port of the iOS ExpiryNotificationPlanner — keep them in step via
 * testdata/notification-plans.json.
 */
class ExpiryNotificationPlanner(
    private val timeZone: TimeZone,
    /** Same cap as iOS (which allows 64 pending notifications) so both platforms behave alike. */
    private val limit: Int = 60,
) {

    fun plan(items: List<ExpiringItemSummary>, preferences: NotificationPreferences, now: Instant): List<PlannedNotification> {
        if (!preferences.enabled) return emptyList()
        return items
            .flatMap { plan(it, preferences, now) }
            .sortedBy { it.fireAt }
            .take(limit)
    }

    fun plan(item: ExpiringItemSummary, preferences: NotificationPreferences, now: Instant): List<PlannedNotification> {
        fun fireAt(daysFromExpiry: Int): Instant =
            LocalDateTime(item.expiresOn.plus(daysFromExpiry, DateTimeUnit.DAY), LocalTime(preferences.hour, preferences.minute))
                .toInstant(timeZone)

        val leadPhrase = if (preferences.leadDays == 1) "tomorrow" else "in ${preferences.leadDays} days"
        val candidates = listOf(
            Candidate("soon", fireAt(-preferences.leadDays), "${item.name} expires $leadPhrase", "Plan a meal around it — Zero Bite has recipe ideas."),
            Candidate("today", fireAt(0), "${item.name} expires today", "Use it today or freeze it."),
            Candidate("expired", fireAt(1), "${item.name} has expired", "Check it and update your fridge."),
        )

        return candidates
            .filter { it.fireAt > now }
            .map { PlannedNotification("$ID_PREFIX${item.id}.${it.suffix}", item.id, it.title, it.body, it.fireAt) }
    }

    private class Candidate(val suffix: String, val fireAt: Instant, val title: String, val body: String)

    companion object {
        const val ID_PREFIX = "zerobite.item."
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `cd android && ./gradlew :core:check; cd ..`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add android/core
git commit -m "Port the expiry notification planner to :core

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Food alerts, allergens and the FSA decoder

**Files:**
- Create: `android/core/src/main/kotlin/com/mazesys/zerobite/core/model/Allergen.kt`
- Create: `android/core/src/main/kotlin/com/mazesys/zerobite/core/model/FoodAlert.kt`
- Create: `android/core/src/main/kotlin/com/mazesys/zerobite/core/alerts/FsaAlertDecoder.kt`
- Test: `android/core/src/test/kotlin/com/mazesys/zerobite/core/alerts/FsaAlertDecoderTest.kt`

**Interfaces:**
- Consumes: `testdata/fsa-alerts.json` (Task 1).
- Produces:
  - `enum class Allergen(val label: String)` — `CELERY, GLUTEN, CRUSTACEANS, EGG, FISH, LUPIN, MILK, MOLLUSCS, MUSTARD, NUTS, PEANUTS, SESAME, SOYA, SULPHITES`; `raw`, `fromRaw(raw): Allergen?`, `matches(alertLabel: String): Boolean`
  - `@Serializable data class FoodAlert(id, title, shortTitle, kind: FoodAlert.Kind, created: LocalDate, url: String?, business: String?, otherBusinesses, products: List<FoodAlert.Product>, allergens: List<String>, risk, consumerAdvice)` with nested `Kind(code, label)` {`ALLERGY`, `RECALL`, `ACTION_REQUIRED`}, `Batch(batchCode, bestBefore, useBy, lotNumber)` with `summary`, `coversAllBatches`, `Product(name, packSize, productCode, batches)`; `displayTitle`, `businesses`, `concerns(allergens: List<Allergen>): List<Allergen>`
  - `object FsaAlertDecoder { fun decode(body: String): List<FoodAlert> }`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.mazesys.zerobite.core.alerts

import com.mazesys.zerobite.core.Fixtures
import com.mazesys.zerobite.core.model.Allergen
import com.mazesys.zerobite.core.model.FoodAlert
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FsaAlertDecoderTest {

    private val alerts = FsaAlertDecoder.decode(Fixtures.text("fsa-alerts.json"))
    private fun alert(id: String) = alerts.first { it.id == id }

    @Test
    fun `decodes the real response including loose fields`() {
        assertEquals(7, alerts.size)

        val brie = alert("FSA-PRIN-28-2026")
        assertEquals(FoodAlert.Kind.RECALL, brie.kind)
        assertEquals(LocalDate(2026, 6, 19), brie.created)
        assertEquals("Traditional Cheese Company", brie.business)
        assertEquals("https://alerts.food.gov.uk/news-alerts/alert/fsa-prin-28-2026", brie.url)
        assertTrue(brie.products.any { it.name == "Traditional Cheese Company French Brie" })
        assertEquals("10 July 2026, 16 July 2026, and 17 July 2026", brie.products.first().batches.first().useBy)

        val potNoodle = alert("FSA-AA-43-2026")
        assertEquals(FoodAlert.Kind.ALLERGY, potNoodle.kind)
        assertEquals(listOf("Milk"), potNoodle.allergens)
    }

    @Test
    fun `flags notices covering every batch`() {
        assertTrue(alert("FSA-AA-27-2026").products.first().batches.first().coversAllBatches)
        assertFalse(alert("FSA-PRIN-28-2026").products.first().batches.first().coversAllBatches)
    }

    @Test
    fun `cached model round trips`() {
        val json = Json { ignoreUnknownKeys = true }
        val encoded = json.encodeToString(alerts)
        assertEquals(alerts, json.decodeFromString<List<FoodAlert>>(encoded))
    }

    @Test
    fun `batch summary reads naturally`() {
        assertEquals("Batch L123 · Best before May 2027", FoodAlert.Batch(batchCode = "L123", bestBefore = "May 2027").summary)
        assertEquals("All batches · All use by dates", FoodAlert.Batch(batchCode = "All batches", useBy = "All use by dates").summary)
    }

    @Test
    fun `only allergy alerts concern allergies`() {
        assertEquals(listOf(Allergen.MILK), alert("FSA-AA-43-2026").concerns(listOf(Allergen.MILK, Allergen.EGG)))
        assertEquals(emptyList(), alert("FSA-PRIN-28-2026").concerns(listOf(Allergen.MILK)))
    }

    @Test
    fun `allergen profile covers FSA labels`() {
        assertTrue(Allergen.GLUTEN.matches("Wheat"))
        assertTrue(Allergen.NUTS.matches("Hazelnut"))
        assertFalse(Allergen.NUTS.matches("Peanuts"))
        assertEquals(Allergen.SULPHITES, Allergen.fromRaw("sulphites"))
        assertNull(Allergen.fromRaw("unicorn"))
        assertEquals("Tree nuts", Allergen.NUTS.label)
        assertEquals("Crustaceans", Allergen.CRUSTACEANS.label)
    }

    @Test
    fun `skips malformed records and tolerates loose shapes`() {
        val body = """
        {"items": [
          {"title": "No notation", "created": "2026-07-01", "type": ["http://x/def/PRIN"]},
          {"notation": "A", "title": "Unknown type", "created": "2026-07-01", "type": ["http://x/def/Alert"]},
          {"notation": "B", "title": "Bad date", "created": "July", "type": ["http://x/def/PRIN"]},
          {"notation": "C", "title": "Loose", "created": "2026-07-01",
           "type": [{"@id": "http://x/def/AA"}],
           "otherBusiness": {"commonName": "Shop"},
           "problem": [{"allergen": {"label": ["Sesame"]}, "riskStatement": "Contains sesame"}],
           "productDetails": [
             {"productName": "  ", "batchDescription": {"batchCode": "X"}},
             {"productName": "Bagel", "batchDescription": {"batchCode": "L1"}}
           ]}
        ]}
        """.trimIndent()
        val decoded = FsaAlertDecoder.decode(body)
        assertEquals(listOf("C"), decoded.map { it.id })
        val c = decoded.single()
        assertEquals(FoodAlert.Kind.ALLERGY, c.kind)
        assertEquals(listOf("Shop"), c.otherBusinesses)
        assertEquals(listOf("Sesame"), c.allergens)
        assertEquals("Contains sesame", c.risk)
        assertEquals(listOf("Bagel"), c.products.map { it.name })
        assertEquals("L1", c.products.single().batches.single().batchCode)
        assertEquals(emptyList(), FsaAlertDecoder.decode("""{"meta": {}}"""))
    }
}
```

- [ ] **Step 2: Run to see it fail**

Run: `cd android && ./gradlew :core:test --tests '*FsaAlertDecoderTest'; cd ..`
Expected: compilation FAILS — unresolved `FsaAlertDecoder`, `Allergen`, `FoodAlert`.

- [ ] **Step 3: Implement `Allergen.kt`**

```kotlin
package com.mazesys.zerobite.core.model

/**
 * The 14 allergens UK law requires on labels, and how they map to the more specific labels
 * used in FSA allergy alerts ("Gluten" covers "Wheat" and "Barley").
 */
enum class Allergen(val label: String, private val alertTerms: List<String>) {
    CELERY("Celery", listOf("celery", "celeriac")),
    GLUTEN("Gluten", listOf("gluten", "wheat", "barley", "rye", "oat", "spelt", "kamut")),
    CRUSTACEANS("Crustaceans", listOf("crustacean", "crab", "lobster", "prawn", "shrimp", "langoustine")),
    EGG("Egg", listOf("egg")),
    FISH("Fish", listOf("fish")),
    LUPIN("Lupin", listOf("lupin")),
    MILK("Milk", listOf("milk", "lactose", "whey", "casein")),
    MOLLUSCS("Molluscs", listOf("mollusc", "mussel", "oyster", "squid", "clam", "scallop")),
    MUSTARD("Mustard", listOf("mustard")),
    NUTS("Tree nuts", listOf("nut", "almond", "hazelnut", "walnut", "cashew", "pecan", "brazil", "pistachio", "macadamia")),
    PEANUTS("Peanuts", listOf("peanut")),
    SESAME("Sesame", listOf("sesame")),
    SOYA("Soya", listOf("soy")),
    SULPHITES("Sulphites", listOf("sulphite", "sulfite", "sulphur dioxide"));

    /** Stored value, identical to the iOS raw value. */
    val raw: String get() = name.lowercase()

    fun matches(alertLabel: String): Boolean {
        val label = alertLabel.lowercase()
        // "Peanuts" is its own allergen, not a tree nut.
        if (this == NUTS && label.startsWith("peanut")) return false
        return alertTerms.any { label.startsWith(it) || label.contains(" $it") }
    }

    companion object {
        fun fromRaw(raw: String): Allergen? = entries.firstOrNull { it.raw == raw }
    }
}
```

- [ ] **Step 4: Implement `FoodAlert.kt`**

```kotlin
package com.mazesys.zerobite.core.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * A UK Food Standards Agency food alert (product recall or allergy alert), reduced to what the
 * app needs. Serializable so the fetched list can be cached on disk.
 *
 * Contains public sector information licensed under the Open Government Licence v3.0.
 */
@Serializable
data class FoodAlert(
    val id: String,
    val title: String,
    val shortTitle: String? = null,
    val kind: Kind,
    val created: LocalDate,
    val url: String? = null,
    val business: String? = null,
    val otherBusinesses: List<String> = emptyList(),
    val products: List<Product> = emptyList(),
    val allergens: List<String> = emptyList(),
    val risk: String? = null,
    val consumerAdvice: String? = null,
) {
    enum class Kind(val code: String, val label: String) {
        /** An allergen is missing from or wrong on the label. */
        ALLERGY("AA", "Allergy alert"),
        /** Product recall information notice. */
        RECALL("PRIN", "Product recall"),
        /** A recall that also needs local authority action. */
        ACTION_REQUIRED("FAFA", "Product recall");

        companion object {
            fun fromCode(code: String): Kind? = entries.firstOrNull { it.code == code }
        }
    }

    @Serializable
    data class Batch(
        val batchCode: String? = null,
        val bestBefore: String? = null,
        val useBy: String? = null,
        val lotNumber: String? = null,
    ) {
        /** e.g. "Batch L6232 · Best before May 2027". */
        val summary: String
            get() {
                // "All batches" / "All use by dates" read on their own; don't prefix them.
                fun label(prefix: String, value: String?): String? {
                    val trimmed = value?.trim()
                    if (trimmed.isNullOrEmpty()) return null
                    return if (trimmed.lowercase().startsWith("all")) trimmed else "$prefix $trimmed"
                }
                return listOfNotNull(
                    label("Batch", batchCode),
                    label("Lot", lotNumber),
                    label("Use by", useBy),
                    label("Best before", bestBefore),
                ).joinToString(" · ")
            }

        /** True when the notice covers every batch ("All batches", "All best before dates"). */
        val coversAllBatches: Boolean
            get() = listOfNotNull(batchCode, bestBefore, useBy).any { it.lowercase().contains("all ") }
    }

    @Serializable
    data class Product(
        val name: String,
        val packSize: String? = null,
        val productCode: String? = null,
        val batches: List<Batch> = emptyList(),
    )

    val displayTitle: String get() = shortTitle ?: title

    /** Businesses named on the notice: manufacturer first, then retailers. */
    val businesses: List<String> get() = listOfNotNull(business) + otherBusinesses

    /** The user's allergens this allergy alert concerns. */
    fun concerns(profile: List<Allergen>): List<Allergen> =
        if (kind != Kind.ALLERGY) emptyList() else profile.filter { a -> allergens.any { a.matches(it) } }
}
```

- [ ] **Step 5: Implement `FsaAlertDecoder.kt`**

```kotlin
package com.mazesys.zerobite.core.alerts

import com.mazesys.zerobite.core.model.FoodAlert
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Decodes `GET https://data.food.gov.uk/food-alerts/id.json?_view=full…`. The API's JSON-LD is
 * loose about types — a field can be a string, an object with "@id"/"label", or an array — so
 * it is walked by hand. Records missing an id, title, date or known type are skipped.
 */
object FsaAlertDecoder {

    private val json = Json { ignoreUnknownKeys = true }

    fun decode(body: String): List<FoodAlert> {
        val items = (json.parseToJsonElement(body).jsonObject["items"] as? JsonArray) ?: return emptyList()
        return items.mapNotNull { (it as? JsonObject)?.let(::alert) }
    }

    private fun alert(raw: JsonObject): FoodAlert? {
        val notation = raw.string("notation") ?: return null
        val title = raw.string("title") ?: return null
        val created = raw.string("created")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val kind = raw.many("type")
            .firstNotNullOfOrNull { looseValue(it)?.substringAfterLast('/')?.let(FoodAlert.Kind::fromCode) }
            ?: return null

        val problems = raw.many("problem").filterIsInstance<JsonObject>()
        return FoodAlert(
            id = notation,
            title = title,
            shortTitle = raw.string("shortTitle"),
            kind = kind,
            created = created,
            url = raw["alertURL"]?.let(::looseValue),
            business = (raw["reportingBusiness"] as? JsonObject)?.string("commonName"),
            otherBusinesses = raw.many("otherBusiness").filterIsInstance<JsonObject>().mapNotNull { it.string("commonName") },
            products = raw.many("productDetails").filterIsInstance<JsonObject>().mapNotNull(::product),
            allergens = problems.flatMap { p -> p.many("allergen").mapNotNull(::looseLabel) }.toSet().sorted(),
            risk = problems.firstNotNullOfOrNull { it.string("riskStatement") },
            consumerAdvice = raw.string("consumerAdvice"),
        )
    }

    private fun product(raw: JsonObject): FoodAlert.Product? {
        val name = raw.string("productName")?.trim()
        if (name.isNullOrEmpty()) return null
        return FoodAlert.Product(
            name = name,
            packSize = raw.string("packSizeDescription"),
            productCode = raw.string("productCode"),
            batches = raw.many("batchDescription").filterIsInstance<JsonObject>().map {
                FoodAlert.Batch(
                    batchCode = it.string("batchCode"),
                    bestBefore = it.string("bestBeforeDescription"),
                    useBy = it.string("useByDescription"),
                    lotNumber = it.string("lotNumber"),
                )
            },
        )
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** A field that is sometimes a single value and sometimes an array of them. */
    private fun JsonObject.many(key: String): List<JsonElement> = when (val value = this[key]) {
        null, JsonNull -> emptyList()
        is JsonArray -> value.jsonArray
        else -> listOf(value)
    }

    /** The string form: a plain string, else "@id", else "label". */
    private fun looseValue(element: JsonElement): String? = when (element) {
        is JsonPrimitive -> element.takeIf { it.isString }?.content
        is JsonObject -> element.string("@id") ?: looseLabel(element)
        else -> null
    }

    /** A plain string, or an object's "label" (itself a string or an array of strings). */
    private fun looseLabel(element: JsonElement): String? = when (element) {
        is JsonPrimitive -> element.takeIf { it.isString }?.content
        is JsonObject -> when (val label = element["label"]) {
            is JsonPrimitive -> label.takeIf { it.isString }?.content
            is JsonArray -> (label.firstOrNull() as? JsonPrimitive)?.takeIf { it.isString }?.content
            else -> null
        }
        else -> null
    }
}
```

- [ ] **Step 6: Run the tests**

Run: `cd android && ./gradlew :core:check; cd ..`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add android/core
git commit -m "Port FSA food alerts, allergens and the alert decoder to :core

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: Alert date parser and recall matcher

**Files:**
- Create: `android/core/src/main/kotlin/com/mazesys/zerobite/core/alerts/AlertDateParser.kt`
- Create: `android/core/src/main/kotlin/com/mazesys/zerobite/core/alerts/RecallMatcher.kt`
- Test: `android/core/src/test/kotlin/com/mazesys/zerobite/core/alerts/RecallMatcherTest.kt`

**Interfaces:**
- Consumes: `FoodAlert`, `Allergen`, `FsaAlertDecoder` (Task 9), `FoodItem` (Task 4), `TextFolding` (Task 7); fixtures `recall-matching.json`, `fsa-alerts.json`.
- Produces: `data class AlertDateSpec(year: Int, month: Int, day: Int?)` with `matches(date: LocalDate)`; `object AlertDateParser { fun dates(text: String): List<AlertDateSpec> }`; `data class RecallMatch(alert, product, itemId: String, itemName: String, confidence: RecallMatch.Confidence, reasons: Set<RecallMatch.Reason>)` with `id`, nested `Confidence { POSSIBLE, LIKELY }`, `Reason { BARCODE, NAME, BRAND, DATE, ALL_BATCHES }`, `companion fun id(alertId, itemId)`; `class RecallMatcher(today: LocalDate, lookbackDays: Int = 120)` with `matches(alerts, items, dismissed = emptySet())`, `match(alert, product, item)`; companion `nameMatches`, `words`, `barcodes`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.mazesys.zerobite.core.alerts

import com.mazesys.zerobite.core.Fixtures
import com.mazesys.zerobite.core.model.Allergen
import com.mazesys.zerobite.core.model.FoodCategory
import com.mazesys.zerobite.core.model.FoodItem
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@Serializable
private data class RecallFixture(
    val matchCases: List<MatchCase>,
    val listCases: List<ListCase>,
    val alertDates: List<AlertDateCase>,
    val nameMatches: List<NameMatch>,
    val allergens: List<AllergenCase>,
    val concerns: List<Concern>,
) {
    @Serializable data class Item(
        val id: String = "item",
        val name: String,
        val expiresOn: LocalDate,
        val brand: String? = null,
        val barcode: String? = null,
        val consumed: Boolean = false,
    )
    @Serializable data class Expect(val confidence: String, val reasons: List<String>? = null, val reasonsInclude: List<String>? = null)
    @Serializable data class MatchCase(val name: String, val today: LocalDate, val alertId: String, val productIndex: Int? = null, val item: Item, val expect: Expect? = null)
    @Serializable data class Dismissed(val alertId: String, val itemId: String)
    @Serializable data class ListCase(val name: String, val today: LocalDate, val items: List<Item>, val dismissed: List<Dismissed> = emptyList(), val firstAlertId: String? = null)
    @Serializable data class Spec(val year: Int, val month: Int, val day: Int? = null)
    @Serializable data class AlertDateCase(val text: String, val expected: List<Spec>)
    @Serializable data class NameMatch(val item: String, val product: String, val matches: Boolean)
    @Serializable data class AllergenCase(val allergen: String, val label: String, val matches: Boolean)
    @Serializable data class Concern(val alertId: String, val allergens: List<String>, val expected: List<String>)
}

class RecallMatcherTest {

    private val fixture = Fixtures.load<RecallFixture>("recall-matching.json")
    private val alerts = FsaAlertDecoder.decode(Fixtures.text("fsa-alerts.json"))
    private val stamp = Instant.parse("2026-07-05T09:00:00Z")

    private fun item(spec: RecallFixture.Item) = FoodItem(
        id = spec.id,
        name = spec.name,
        category = FoodCategory.DAIRY,
        addedAt = stamp,
        expiresOn = spec.expiresOn,
        barcode = spec.barcode,
        brand = spec.brand,
        consumedAt = if (spec.consumed) stamp else null,
    )

    /** Fixture spelling of a reason (matches the iOS raw values). */
    private fun RecallMatch.Reason.fixtureName() = when (this) {
        RecallMatch.Reason.BARCODE -> "barcode"
        RecallMatch.Reason.NAME -> "name"
        RecallMatch.Reason.BRAND -> "brand"
        RecallMatch.Reason.DATE -> "date"
        RecallMatch.Reason.ALL_BATCHES -> "allBatches"
    }

    @TestFactory
    fun `golden single matches`() = fixture.matchCases.map { case ->
        DynamicTest.dynamicTest(case.name) {
            val alert = alerts.first { it.id == case.alertId }
            val products = case.productIndex?.let { listOf(alert.products[it]) } ?: alert.products
            val matcher = RecallMatcher(case.today)
            val results = products.mapNotNull { matcher.match(alert, it, item(case.item)) }
            val expect = case.expect
            if (expect == null) {
                assertEquals(emptyList(), results)
                return@dynamicTest
            }
            val match = assertNotNull(results.firstOrNull())
            assertEquals(expect.confidence, match.confidence.name.lowercase())
            val reasons = match.reasons.map { it.fixtureName() }.toSet()
            expect.reasons?.let { assertEquals(it.toSet(), reasons) }
            expect.reasonsInclude?.let { assertTrue(reasons.containsAll(it), "reasons $reasons") }
        }
    }

    @TestFactory
    fun `golden match lists`() = fixture.listCases.map { case ->
        DynamicTest.dynamicTest(case.name) {
            val dismissed = case.dismissed.map { RecallMatch.id(it.alertId, it.itemId) }.toSet()
            val found = RecallMatcher(case.today).matches(alerts, case.items.map(::item), dismissed)
            assertEquals(case.firstAlertId, found.firstOrNull()?.alert?.id)
            if (case.firstAlertId == null) assertEquals(emptyList(), found)
        }
    }

    @TestFactory
    fun `golden alert dates`() = fixture.alertDates.map { case ->
        DynamicTest.dynamicTest(case.text) {
            assertEquals(case.expected.map { AlertDateSpec(it.year, it.month, it.day) }.toSet(), AlertDateParser.dates(case.text).toSet())
        }
    }

    @TestFactory
    fun `golden name matches`() = fixture.nameMatches.map { case ->
        DynamicTest.dynamicTest("${case.item} ~ ${case.product}") {
            assertEquals(case.matches, RecallMatcher.nameMatches(RecallMatcher.words(case.item), RecallMatcher.words(case.product)))
        }
    }

    @TestFactory
    fun `golden allergens and concerns`() =
        fixture.allergens.map { case ->
            DynamicTest.dynamicTest("${case.allergen} ~ ${case.label}") {
                assertEquals(case.matches, Allergen.fromRaw(case.allergen)!!.matches(case.label))
            }
        } + fixture.concerns.map { case ->
            DynamicTest.dynamicTest("concerns ${case.alertId}") {
                val profile = case.allergens.mapNotNull(Allergen::fromRaw)
                assertEquals(case.expected, alerts.first { it.id == case.alertId }.concerns(profile).map { it.raw })
            }
        }

    @Test
    fun `words fold accents, drop stop words and digits, and singularise`() {
        assertEquals(listOf("greek", "yogurt"), RecallMatcher.words("Greek Style Yoghurts 500g"))
        assertEquals(listOf("o", "brien", "fine", "food"), RecallMatcher.words("O’Brien Fine Foods"))
        assertEquals(listOf("creme", "brulee"), RecallMatcher.words("Crème Brûlée"))
    }

    @Test
    fun `barcodes are long digit runs from product and batch codes`() {
        val product = com.mazesys.zerobite.core.model.FoodAlert.Product(
            name = "Brie",
            productCode = "EAN 5099874213772",
            batches = listOf(com.mazesys.zerobite.core.model.FoodAlert.Batch(batchCode = "L123 / 12345678")),
        )
        assertEquals(setOf("5099874213772", "12345678"), RecallMatcher.barcodes(product))
    }
}
```

- [ ] **Step 2: Run to see it fail**

Run: `cd android && ./gradlew :core:test --tests '*RecallMatcherTest'; cd ..`
Expected: compilation FAILS — unresolved `RecallMatcher`, `AlertDateParser`.

- [ ] **Step 3: Implement `AlertDateParser.kt`**

```kotlin
package com.mazesys.zerobite.core.alerts

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/** A date from a notice at day or month precision ("16 July 2026", "May 2027"). */
data class AlertDateSpec(val year: Int, val month: Int, val day: Int?) {
    fun matches(date: LocalDate): Boolean =
        date.year == year && date.month.number == month && (day == null || date.day == day)
}

/**
 * Pulls dates out of FSA batch text such as "10, 16 and 17 July 2026", "end of 28 April 2026",
 * "31 January 2027 02 May 2027" or "May 2027".
 */
object AlertDateParser {

    private const val MONTH =
        "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"

    private val WRITTEN = Regex(
        """((?:\b\d{1,2}(?:st|nd|rd|th)?\b[\s,&]*(?:and\s+)?)*)\b""" + MONTH + """\.?,?\s+(\d{4})""",
        RegexOption.IGNORE_CASE,
    )

    private val NUMERIC = Regex("""\b(\d{1,2})[/.](\d{1,2})[/.](\d{2,4})\b""")

    private val NON_DIGITS = Regex("""\D+""")

    private val MONTH_NAMES = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    fun dates(text: String): List<AlertDateSpec> {
        val specs = mutableListOf<AlertDateSpec>()

        for (match in WRITTEN.findAll(text)) {
            val year = match.groupValues[3].toIntOrNull() ?: continue
            val month = monthIndex(match.groupValues[2].lowercase()) ?: continue
            val days = match.groupValues[1].split(NON_DIGITS).mapNotNull { it.toIntOrNull() }.filter { it in 1..31 }
            if (days.isEmpty()) {
                specs += AlertDateSpec(year, month, null)
            } else {
                days.mapTo(specs) { AlertDateSpec(year, month, it) }
            }
        }

        for (match in NUMERIC.findAll(text)) {
            val day = match.groupValues[1].toIntOrNull() ?: continue
            val month = match.groupValues[2].toIntOrNull() ?: continue
            var year = match.groupValues[3].toIntOrNull() ?: continue
            if (day !in 1..31 || month !in 1..12) continue
            if (year < 100) year += 2000
            specs += AlertDateSpec(year, month, day)
        }
        return specs
    }

    private fun monthIndex(text: String): Int? =
        MONTH_NAMES.indexOfFirst { text.startsWith(it) }.takeIf { it >= 0 }?.plus(1)
}
```

- [ ] **Step 4: Implement `RecallMatcher.kt`**

```kotlin
package com.mazesys.zerobite.core.alerts

import com.mazesys.zerobite.core.model.FoodAlert
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.text.TextFolding
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

data class RecallMatch(
    val alert: FoodAlert,
    val product: FoodAlert.Product,
    val itemId: String,
    val itemName: String,
    val confidence: Confidence,
    val reasons: Set<Reason>,
) {
    /** Declared weakest first so `>` means "more confident". */
    enum class Confidence { POSSIBLE, LIKELY }

    enum class Reason {
        /** The item's barcode appears on the notice. */
        BARCODE,
        /** The item's name matches a recalled product. */
        NAME,
        /** The item's brand (or a brand word in its name) matches the business or product. */
        BRAND,
        /** The item's date matches a recalled batch date. */
        DATE,
        /** The notice covers every batch of the product. */
        ALL_BATCHES,
    }

    val id: String get() = id(alert.id, itemId)

    companion object {
        fun id(alertId: String, itemId: String): String = "$alertId|$itemId"
    }
}

/**
 * Decides which food alerts might concern items in the fridge. Notices rarely carry a barcode,
 * so matching is cautious: results are "likely" or "possible", never certain. Port of the iOS
 * RecallMatcher — keep them in step via testdata/recall-matching.json.
 */
class RecallMatcher(
    private val today: LocalDate,
    /** Older notices are ignored: products from them have long left the shelves. */
    private val lookbackDays: Int = 120,
) {

    fun matches(alerts: List<FoodAlert>, items: List<FoodItem>, dismissed: Set<String> = emptySet()): List<RecallMatch> {
        val cutoff = today.minus(lookbackDays, DateTimeUnit.DAY)
        val recent = alerts.filter { it.created >= cutoff }
        val results = mutableListOf<RecallMatch>()
        for (item in items) {
            if (item.isConsumed) continue
            // Keep only the best-matching product per alert for each item.
            val best = LinkedHashMap<String, RecallMatch>()
            for (alert in recent) {
                for (product in alert.products) {
                    val match = match(alert, product, item) ?: continue
                    if (match.id in dismissed) continue
                    val existing = best[alert.id]
                    if (existing != null && existing.confidence >= match.confidence) continue
                    best[alert.id] = match
                }
            }
            results += best.values
        }
        return results.sortedWith(
            compareByDescending<RecallMatch> { it.confidence }.thenByDescending { it.alert.created }
        )
    }

    fun match(alert: FoodAlert, product: FoodAlert.Product, item: FoodItem): RecallMatch? {
        val reasons = mutableSetOf<RecallMatch.Reason>()

        val barcode = item.barcode?.filter { it.isDigit() }
        if (barcode != null && barcode.length >= 8 && barcode in barcodes(product)) {
            reasons += RecallMatch.Reason.BARCODE
        }

        val productWords = words(product.name)
        val businessWords = alert.businesses.flatMap(::words).toSet() - GENERIC_BUSINESS_WORDS
        val brandWords = words(item.brand.orEmpty()).toSet()
        val itemWords = words(item.name).filter { it !in businessWords && it !in brandWords }

        if (nameMatches(itemWords, productWords)) reasons += RecallMatch.Reason.NAME

        val allItemWords = words(item.name).toSet() + brandWords
        if (businessWords.any { it in allItemWords } || (brandWords.isNotEmpty() && brandWords.any { it in productWords })) {
            reasons += RecallMatch.Reason.BRAND
        }

        val specs = product.batches.flatMap { AlertDateParser.dates(listOfNotNull(it.useBy, it.bestBefore).joinToString(" ")) }
        if (product.batches.any { it.coversAllBatches }) {
            reasons += RecallMatch.Reason.ALL_BATCHES
        } else if (specs.any { it.matches(item.expiresOn) }) {
            reasons += RecallMatch.Reason.DATE
        }
        val dateContradicts = specs.isNotEmpty() && RecallMatch.Reason.DATE !in reasons && RecallMatch.Reason.ALL_BATCHES !in reasons

        val name = RecallMatch.Reason.NAME in reasons
        val confidence = when {
            RecallMatch.Reason.BARCODE in reasons -> RecallMatch.Confidence.LIKELY
            name && RecallMatch.Reason.BRAND in reasons ->
                if (RecallMatch.Reason.DATE in reasons || RecallMatch.Reason.ALL_BATCHES in reasons) RecallMatch.Confidence.LIKELY
                else RecallMatch.Confidence.POSSIBLE
            name && !dateContradicts -> RecallMatch.Confidence.POSSIBLE
            else -> return null
        }
        return RecallMatch(alert, product, item.id, item.name, confidence, reasons)
    }

    companion object {

        /**
         * A one-word item ("Brie", "Ham") must be the product's head noun, so "Milk" doesn't match
         * "Pistachio Milk Chocolate". Longer names need every word to appear in the product name.
         */
        fun nameMatches(itemWords: List<String>, productWords: List<String>): Boolean {
            val head = productWords.lastOrNull() ?: return false
            if (itemWords.isEmpty()) return false
            if (itemWords.size == 1) return itemWords[0] == head
            val product = productWords.toSet()
            return itemWords.all { it in product }
        }

        /** Lowercased, accent-folded, singular content words, in order. */
        fun words(text: String): List<String> =
            splitAlphanumeric(TextFolding.foldDiacritics(text).lowercase())
                .filter { it !in STOP_WORDS && it.none(Char::isDigit) }
                .map(::singular)
                .map { SYNONYMS[it] ?: it }

        /** Long digit runs on the notice (product codes and barcode-style batch codes). */
        fun barcodes(product: FoodAlert.Product): Set<String> {
            val text = (listOf(product.productCode) + product.batches.map { it.batchCode }).filterNotNull().joinToString(" ")
            return text.split(Regex("""\D+""")).filter { it.length >= 8 }.toSet()
        }

        private fun splitAlphanumeric(text: String): List<String> {
            val words = mutableListOf<String>()
            val current = StringBuilder()
            for (c in text) {
                if (c.isLetterOrDigit()) {
                    current.append(c)
                } else if (current.isNotEmpty()) {
                    words += current.toString()
                    current.clear()
                }
            }
            if (current.isNotEmpty()) words += current.toString()
            return words
        }

        private fun singular(word: String): String {
            if (word.length <= 3) return word
            if (word.endsWith("ies")) return word.dropLast(3) + "y"
            if (word.endsWith("oes")) return word.dropLast(2)
            if (word.endsWith("s") && !word.endsWith("ss")) return word.dropLast(1)
            return word
        }

        val STOP_WORDS: Set<String> = setOf(
            "the", "and", "with", "of", "in", "a", "an", "for", "style", "flavour", "flavor", "flavoured",
            "pack", "x", "g", "kg", "ml", "l", "pcs", "organic", "mini", "deluxe", "family", "range", "fresh",
            "finest", "classic", "original", "selection", "everyday", "essential", "taste", "own", "brand",
        )

        val GENERIC_BUSINESS_WORDS: Set<String> = setOf(
            "ltd", "limited", "plc", "uk", "co", "company", "foods", "food", "store", "group", "international",
            "trading", "brand", "products", "inc", "llp", "fine", "traditional",
        )

        val SYNONYMS: Map<String, String> = mapOf(
            "yoghurt" to "yogurt", "yoghurts" to "yogurt",
            "courgette" to "zucchini", "aubergine" to "eggplant", "coriander" to "cilantro",
        )
    }
}
```

- [ ] **Step 5: Run the tests**

Run: `cd android && ./gradlew :core:check; cd ..`
Expected: `BUILD SUCCESSFUL`. If `words("O’Brien Fine Foods")` differs from the expected list, check `splitAlphanumeric` against Swift's `components(separatedBy: CharacterSet.alphanumerics.inverted)` — the curly apostrophe is a separator in both.

- [ ] **Step 6: Commit**

```bash
git add android/core
git commit -m "Port the alert date parser and recall matcher to :core

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: Open Food Facts and FSA network clients

**Files:**
- Create: `android/core/src/main/kotlin/com/mazesys/zerobite/core/network/ZeroBiteHttp.kt`
- Create: `android/core/src/main/kotlin/com/mazesys/zerobite/core/products/OpenFoodFactsClient.kt`
- Create: `android/core/src/main/kotlin/com/mazesys/zerobite/core/alerts/FsaAlertsClient.kt`
- Test: `android/core/src/test/kotlin/com/mazesys/zerobite/core/products/OpenFoodFactsClientTest.kt`, `android/core/src/test/kotlin/com/mazesys/zerobite/core/alerts/FsaAlertsClientTest.kt`

**Interfaces:**
- Consumes: `ProductInfo`, `FoodCategory` (Task 4), `FsaAlertDecoder` (Task 9).
- Produces: `object ZeroBiteHttp { const val USER_AGENT = "ZeroBite-Android/1.0" }`; `interface ProductLookup { suspend fun lookup(barcode: String): ProductInfo? }`; `class OpenFoodFactsClient(http: HttpClient) : ProductLookup` with `companion fun parse(code, body): ProductInfo?`, `fun category(tags: List<String>): FoodCategory?`; `interface FoodAlertsFetching { suspend fun fetchAlerts(since: LocalDate): List<FoodAlert> }`; `class FsaAlertsClient(http: HttpClient) : FoodAlertsFetching`; `class FoodAlertsUnavailable(status: Int) : Exception`. Plan 2 builds the `HttpClient` (OkHttp engine, timeouts) in `:app` and passes it in.

- [ ] **Step 1: Write the failing tests**

`OpenFoodFactsClientTest.kt`:

```kotlin
package com.mazesys.zerobite.core.products

import com.mazesys.zerobite.core.model.FoodCategory
import com.mazesys.zerobite.core.model.ProductInfo
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

class OpenFoodFactsClientTest {

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private fun client(handler: MockRequestHandler) = OpenFoodFactsClient(HttpClient(MockEngine(handler)))

    @Test
    fun `resolves name, first brand and category`() = runTest {
        var requested = ""
        val lookup = client { request ->
            requested = request.url.toString()
            assertEquals("ZeroBite-Android/1.0", request.headers[HttpHeaders.UserAgent])
            respond(
                """{"code":"5000","status":1,"product":{"product_name":" Semi Skimmed Milk ","brands":"Tesco, Tesco Finest","categories_tags":["en:dairies","en:milks"]}}""",
                HttpStatusCode.OK, json,
            )
        }
        assertEquals(
            ProductInfo("5000112637922", "Semi Skimmed Milk", "Tesco", FoodCategory.DAIRY),
            lookup.lookup("5000112637922"),
        )
        assertEquals("https://world.openfoodfacts.org/api/v2/product/5000112637922?fields=product_name%2Cbrands%2Ccategories_tags", requested)
    }

    @Test
    fun `strips non-digits from the barcode`() = runTest {
        var path = ""
        client { request ->
            path = request.url.encodedPath
            respond("""{"status":0}""", HttpStatusCode.OK, json)
        }.lookup(" 50 0011-2637922x ")
        assertEquals("/api/v2/product/5000112637922", path)
    }

    @Test
    fun `empty barcode makes no request`() = runTest {
        assertNull(client { fail("no request expected") }.lookup(" abc "))
    }

    @Test
    fun `unknown product or HTTP error is null`() = runTest {
        assertNull(client { respond("""{"status":0,"status_verbose":"product not found"}""", HttpStatusCode.OK, json) }.lookup("123"))
        assertNull(client { respond("""{"status":1,"product":{"product_name":"  "}}""", HttpStatusCode.OK, json) }.lookup("123"))
        assertNull(client { respond("", HttpStatusCode.NotFound) }.lookup("123"))
    }

    @Test
    fun `category comes from the most specific matching tag`() {
        assertEquals(FoodCategory.DAIRY, OpenFoodFactsClient.category(listOf("en:beverages", "en:milks")))
        assertEquals(FoodCategory.FRUIT, OpenFoodFactsClient.category(listOf("en:plant-based-foods", "en:fruits")))
        assertEquals(FoodCategory.GRAINS, OpenFoodFactsClient.category(listOf("en:pastas")))
        assertNull(OpenFoodFactsClient.category(emptyList()))
        assertNull(OpenFoodFactsClient.category(listOf("en:snacks")))
    }
}
```

`FsaAlertsClientTest.kt`:

```kotlin
package com.mazesys.zerobite.core.alerts

import com.mazesys.zerobite.core.Fixtures
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FsaAlertsClientTest {

    private fun client(handler: MockRequestHandler) = FsaAlertsClient(HttpClient(MockEngine(handler)))

    @Test
    fun `fetches with min-created and decodes the feed`() = runTest {
        val alerts = client { request ->
            val params = request.url.parameters
            assertEquals("/food-alerts/id.json", request.url.encodedPath)
            assertEquals("2026-03-14", params["min-created"])
            assertNull(params["since"]) // the FSA API rejects "since" with HTTP 400
            assertEquals("full", params["_view"])
            assertEquals("-created", params["_sort"])
            assertEquals("200", params["_limit"])
            assertEquals("application/json", request.headers[HttpHeaders.Accept])
            respond(Fixtures.text("fsa-alerts.json"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }.fetchAlerts(since = LocalDate(2026, 3, 14))
        assertEquals(7, alerts.size)
    }

    @Test
    fun `non-200 responses throw`() = runTest {
        val error = assertFailsWith<FoodAlertsUnavailable> {
            client { respond("", HttpStatusCode.BadRequest) }.fetchAlerts(LocalDate(2026, 3, 14))
        }
        assertEquals(400, error.status)
    }

    @Test
    fun `connection failures propagate to the caller`() = runTest {
        assertFailsWith<IllegalStateException> {
            client { error("offline") }.fetchAlerts(LocalDate(2026, 3, 14))
        }
    }
}
```

- [ ] **Step 2: Run to see them fail**

Run: `cd android && ./gradlew :core:test --tests '*ClientTest'; cd ..`
Expected: compilation FAILS — unresolved `OpenFoodFactsClient`, `FsaAlertsClient`.

- [ ] **Step 3: Implement**

`network/ZeroBiteHttp.kt`:

```kotlin
package com.mazesys.zerobite.core.network

object ZeroBiteHttp {
    const val USER_AGENT = "ZeroBite-Android/1.0"
}
```

`products/OpenFoodFactsClient.kt`:

```kotlin
package com.mazesys.zerobite.core.products

import com.mazesys.zerobite.core.model.FoodCategory
import com.mazesys.zerobite.core.model.ProductInfo
import com.mazesys.zerobite.core.network.ZeroBiteHttp
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Resolves a barcode to a product name and category. */
interface ProductLookup {
    suspend fun lookup(barcode: String): ProductInfo?
}

/** The free, keyless Open Food Facts API. Timeouts are configured on the injected client. */
class OpenFoodFactsClient(private val http: HttpClient) : ProductLookup {

    override suspend fun lookup(barcode: String): ProductInfo? {
        val code = barcode.filter { it.isDigit() }
        if (code.isEmpty()) return null
        val response = http.get("https://world.openfoodfacts.org/api/v2/product/$code") {
            parameter("fields", "product_name,brands,categories_tags")
            header(HttpHeaders.UserAgent, ZeroBiteHttp.USER_AGENT)
        }
        if (response.status != HttpStatusCode.OK) return null
        return parse(code, response.bodyAsText())
    }

    @Serializable
    private data class Response(val status: Int = 0, val product: Product? = null) {
        @Serializable
        data class Product(
            @SerialName("product_name") val productName: String? = null,
            val brands: String? = null,
            @SerialName("categories_tags") val categoriesTags: List<String> = emptyList(),
        )
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(code: String, body: String): ProductInfo? {
            val decoded = json.decodeFromString<Response>(body)
            val product = decoded.product ?: return null
            val name = product.productName?.trim()
            if (decoded.status != 1 || name.isNullOrEmpty()) return null
            return ProductInfo(
                barcode = code,
                name = name,
                brand = product.brands?.split(',')?.firstOrNull()?.trim(),
                category = category(product.categoriesTags),
            )
        }

        private val RULES: List<Pair<FoodCategory, List<String>>> = listOf(
            FoodCategory.DAIRY to listOf("dairy", "dairies", "milk", "cheese", "yogurt", "yoghurt", "egg", "butter", "cream"),
            FoodCategory.MEAT to listOf("meat", "poultry", "chicken", "beef", "pork", "lamb", "sausage", "bacon", "ham"),
            FoodCategory.FISH to listOf("fish", "seafood", "salmon", "tuna", "prawn", "shrimp"),
            FoodCategory.BAKERY to listOf("bread", "bakery", "pastry", "pastries", "cake", "biscuit"),
            FoodCategory.GRAINS to listOf("pasta", "rice", "cereal", "grain", "flour", "noodle"),
            FoodCategory.CONDIMENTS to listOf("sauce", "condiment", "spread", "dressing", "mayonnaise", "ketchup", "mustard"),
            FoodCategory.DRINKS to listOf("beverage", "drink", "juice", "water", "soda"),
            FoodCategory.FRUIT to listOf("fruit"),
            FoodCategory.VEGETABLES to listOf("vegetable", "salad", "legume"),
        )

        /** Maps Open Food Facts category tags (general → specific) to our coarse categories. */
        fun category(tags: List<String>): FoodCategory? {
            for (tag in tags.asReversed()) {
                val lower = tag.lowercase()
                for ((category, needles) in RULES) {
                    if (needles.any { lower.contains(it) }) return category
                }
            }
            return null
        }
    }
}
```

`alerts/FsaAlertsClient.kt`:

```kotlin
package com.mazesys.zerobite.core.alerts

import com.mazesys.zerobite.core.model.FoodAlert
import com.mazesys.zerobite.core.network.ZeroBiteHttp
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.LocalDate

interface FoodAlertsFetching {
    suspend fun fetchAlerts(since: LocalDate): List<FoodAlert>
}

class FoodAlertsUnavailable(val status: Int) : Exception("FSA food alerts returned HTTP $status")

/**
 * The free, keyless FSA Food Alerts API (Open Government Licence v3.0).
 * https://data.food.gov.uk/food-alerts/ui/reference — note the date filter is `min-created`;
 * `since` returns 400.
 */
class FsaAlertsClient(private val http: HttpClient) : FoodAlertsFetching {

    override suspend fun fetchAlerts(since: LocalDate): List<FoodAlert> {
        val response = http.get("https://data.food.gov.uk/food-alerts/id.json") {
            parameter("_view", "full")
            parameter("_sort", "-created")
            parameter("_limit", "200")
            parameter("min-created", since.toString())
            header(HttpHeaders.Accept, "application/json")
            header(HttpHeaders.UserAgent, ZeroBiteHttp.USER_AGENT)
        }
        if (response.status != HttpStatusCode.OK) throw FoodAlertsUnavailable(response.status.value)
        return FsaAlertDecoder.decode(response.bodyAsText())
    }
}
```

- [ ] **Step 4: Run the whole `:core` suite**

Run: `cd android && ./gradlew :core:check; cd ..`
Expected: `BUILD SUCCESSFUL`, every test from Tasks 3–11 passing.

- [ ] **Step 5: Commit**

```bash
git add android/core
git commit -m "Add Open Food Facts and FSA alert clients to :core

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 12: CI and documentation

**Files:**
- Create: `.github/workflows/android.yml`
- Modify: `README.md`

**Interfaces:**
- Consumes: `./gradlew :core:check` (Tasks 3–11).
- Produces: a GitHub Actions check named `Android / core` on PRs touching Android, fixtures or shared assets.

- [ ] **Step 1: Write the workflow**

```yaml
name: Android

on:
  push:
    branches: [main]
    paths: ['android/**', 'testdata/**', 'shared-assets/**', '.github/workflows/android.yml']
  pull_request:
    paths: ['android/**', 'testdata/**', 'shared-assets/**', '.github/workflows/android.yml']

jobs:
  core:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: android
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew :core:check
```

- [ ] **Step 2: Validate it locally**

Run: `ruby -ryaml -e 'YAML.load_file(".github/workflows/android.yml"); puts "ok"'`
Expected: `ok`.

- [ ] **Step 3: Document the Android side in the README**

Append to `README.md`:

````markdown
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
````

- [ ] **Step 4: Run everything one last time**

```bash
cd android && ./gradlew :core:check; cd ..
cd ios && xcodebuild test -project ZeroBite.xcodeproj -scheme ZeroBite -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -quiet; cd ..
```

Expected: both succeed.

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/android.yml README.md
git commit -m "Run Android core checks in CI and document the shared fixtures

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## Spec coverage (Plan 1 scope)

| Spec section | Covered by |
|---|---|
| §3 Phase 0 housekeeping | Task 1 |
| §4 `:core` structure, KMP-ready deps, portability | Tasks 3–11 (`checkPortable` in Task 3) |
| §4 `RecipeRepository`/`InventoryRepository` interfaces | Task 4 |
| §6 Scanning logic (`LabelScanner.analyse`) | Tasks 5–6 |
| §6 Reminder planning | Task 8 |
| §6 Network (Ktor, `min-created`) | Task 11 |
| §8 Golden fixtures + Swift runner | Task 2 |
| §8 `:core` tests incl. MockEngine | Tasks 3–11 |
| §8 CI | Task 12 (`:core` only; `:app` lint/APK added in Plan 2) |
| §3 step 5 Mona Sans into `android/app` (no `:app` yet), §4 recipes.json copy into `:app` assets, §5, §6 platform services, §7 UI, §9 release | Plans 2–4 |
