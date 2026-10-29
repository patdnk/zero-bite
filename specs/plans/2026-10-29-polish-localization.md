# Polish localization — implementation plan

Goal: Zero Bite ships in English (source, en-GB copy) and Polish on iOS and Android. The app follows the
device / per-app language. Scope agreed with Pat on 2026-10-29: all UI strings, notifications and permission
prompts, the 35 bundled recipes (with Polish ingredient matching), and a Polish App Store listing.

Out of scope (follow-ups): Polish best-before phrases in the label OCR parser (`ExpiryDateParser`), Polish
product names from Open Food Facts, Polish App Store screenshot panels, translating FSA alert content (it is
UK government data and stays English).

## Global constraints

- English copy must not change. Every existing English string, test and golden fixture keeps passing as is.
- Polish is natural, concise, informal-friendly app Polish (address the user with "ty" forms, e.g. "Dodaj",
  "Zeskanuj"), matching the tone of the English copy. Use correct diacritics (ą ć ę ł ń ó ś ź ż).
  Polish typography: „cudzysłów” quotes, en dash with spaces for asides, decimal comma.
- Plurals use real plural rules, never "dzień/dni" hacks: iOS String Catalog plural variations
  (Polish categories one / few / many / other), Android `<plurals>` (one / few / many / other).
- Dates come from the platform formatter for the current locale (Polish genitive months: "7 września 2026").
  No hardcoded month or weekday names.
- Brand name "Zero Bite" is never translated.
- No string-concatenated sentences in Polish; use whole-sentence format strings with placeholders.
- iOS: SwiftUI string literals in views are already `LocalizedStringKey`; strings built in view models or
  models use `String(localized:)` / `LocalizedStringResource`. One catalog `ios/ZeroBite/Resources/Localizable.xcstrings`
  (sourceLanguage `en`) for UI, `Notifications.xcstrings` table for notification copy, `InfoPlist.xcstrings`
  for `CFBundleDisplayName` and `NSCameraUsageDescription`. Every key has a `pl` translation with
  `"state": "translated"`. project.yml gains `options.developmentLanguage: en` and Polish in known regions
  (via the catalogs being resources; verify `knownRegions` contains `pl` after `xcodegen generate`).
- Android: every user-visible string lives in `res/values/strings.xml` with a Polish twin in
  `res/values-pl/strings.xml`; Compose uses `stringResource` / `pluralStringResource`. Per-app language:
  `androidResources { generateLocaleConfig = true }` plus `res/resources.properties` with
  `unqualifiedResLocale=en-GB`.
- Ingredient matching stays language-agnostic: Polish aliases are always active, whatever the UI language,
  because people may type Polish item names with an English UI.
- Shared data lives in `shared-assets/`, golden fixtures in `testdata/`; Swift is the reference
  implementation and both suites run the same fixtures.
- Concurrency rule for this plan: several implementers share one checkout. Each task touches only the paths
  it lists, stages only its own paths (`git add <paths>`) and commits with `git commit -m … -- <paths>`.
  If `.git/index.lock` exists, wait a few seconds and retry. Never `git add -A`, never `git stash`,
  never reset or checkout other paths.

## Polish notification copy (exact; both platforms and the fixture use it verbatim)

| Case | English (unchanged) | Polish |
|---|---|---|
| soon title, leadDays = 1 | `{name} expires tomorrow` | `{name}: termin mija jutro` |
| soon title, leadDays = n ≥ 2 | `{name} expires in {n} days` | `{name}: termin mija za {n} dni` |
| soon body | `Plan a meal around it — Zero Bite has recipe ideas.` | `Zaplanuj z tym posiłek – Zero Bite podpowie przepisy.` |
| today title | `{name} expires today` | `{name}: termin mija dziś` |
| today body | `Use it today or freeze it.` | `Zużyj to dziś albo zamroź.` |
| expired title | `{name} has expired` | `{name}: termin minął` |
| expired body | `Check it and update your fridge.` | `Sprawdź produkt i zaktualizuj lodówkę.` |

(Polish "dni" is correct for every n ≥ 2, so the soon title needs no plural variants, but iOS should still
model it as a plural-capable format string.)

## Shared data formats (Task 3 produces, Tasks 4 and 5 consume)

`shared-assets/recipes.pl.json` — Polish overlay keyed by recipe id. Every recipe id in `recipes.json` has an
entry; every ingredient (keyed by its English `name` exactly as in recipes.json) has an entry; `steps` has the
same count as the English recipe:

```json
{
  "classic-omelette": {
    "title": "Klasyczny omlet z serem",
    "summary": "…",
    "steps": ["…", "…"],
    "ingredients": { "eggs": { "name": "jajka", "amount": "3" }, "milk": { "name": "mleko", "amount": "odrobina" } }
  }
}
```

`shared-assets/recipe-tags.json` — display names for every tag used in recipes.json:
`{ "en": { "breakfast": "Breakfast", … }, "pl": { "breakfast": "Śniadanie", … } }`. (English display names
must equal what the apps show today.)

`shared-assets/ingredient-aliases.pl.json` — Polish names that match an English canonical ingredient term:
`{ "egg": ["jajko", "jajka", "jaja", "jajek"], "cheese": ["ser", "sera", "serek"], … }`. Keys are English terms as
they appear in recipe ingredient names or the matchers' synonym groups (singular form). Values list the
common inflected forms people write on a shopping list or a label (nominative singular/plural, genitive), and
Polish names of the synonym-group members (e.g. "cukinia" for courgette, "bakłażan" for aubergine,
"mielone" / "mięso mielone" for mince). Cover every non-staple ingredient used by recipes.json.

Matching semantics (Tasks 4 and 5): `matches(ingredient, itemName)` keeps its current rules and additionally
returns true when the normalized item name contains, as whole words (same `containsWords` rule), any
normalized Polish alias of the ingredient, or of any term in the ingredient's synonym group. Normalization is
the existing one on each platform applied to both sides. A fixture must prove "Jajka" matches "eggs" and
"Bakłażan" does not match "eggs".

`testdata/recipe-matching-pl.json` — `{ "nameMatches": [ { "ingredient": "eggs", "item": "Jajka", "matches": true }, … ] }`
with at least 20 cases including inflections, multi-word items ("Pierś z kurczaka", "Ser żółty gouda",
"Mleko 2%"), synonym-group members and at least 5 negatives.

`testdata/notification-plans-pl.json` — same shape as `notification-plans.json` (timeZone, now, cases) with
Polish expected titles/bodies from the table above; at least the lead/today/expired case, the single-day
"jutro" case and a leadDays = 5 case.

## Tasks

### Task 1: iOS UI strings → String Catalog (en + pl)

Files: `ios/ZeroBite/**` except `Services/Notifications/ExpiryNotificationPlanner.swift`,
`Services/Recipes/*`, `Models/Recipe.swift`; `ios/project.yml`; new `ios/ZeroBite/Resources/Localizable.xcstrings`,
`ios/ZeroBite/Resources/InfoPlist.xcstrings`; `ios/ZeroBiteTests/**` only if a test must adapt.

- Find every user-visible English string in views, view models, models (Allergen names, FoodAlert kind labels,
  status/expiry badges like "2 days left", units, empty states, onboarding, settings, scanner, alerts, tab bar,
  accessibility labels), and sample data item names (`SampleData.swift` inserts names in the current language).
- Move strings built in code to `String(localized:)` with plural-aware keys where a count is involved.
- Write the catalog JSON by hand: every key, `en` source, `pl` translation, plural variations for Polish.
- `InfoPlist.xcstrings`: `CFBundleDisplayName` = "Zero Bite" in both; `NSCameraUsageDescription` Polish:
  "Zero Bite używa aparatu, by odczytać daty ważności i kody kreskowe z etykiet."
- Dates: keep `.formatted` (locale-aware). Remove any hardcoded English date pieces.
- Recipe titles/ingredients/tags are NOT in this task (Task 4) — leave recipe data display untouched.
- Verify: `cd ios && xcodegen generate`; build + full `ZeroBite` test scheme on iPhone 17 Pro passes; launch
  the app on the simulator with `-AppleLanguages "(pl)" -AppleLocale pl_PL` and screenshot Fridge, Add food,
  Settings, onboarding to confirm Polish, no clipped text, no untranslated strings (save screenshots to the
  report directory).

### Task 2: Android UI strings → resources (en + pl)

Files: `android/app/src/main/**` except `data/BundledRecipeRepository.kt` and recipe ingredient/tag display
(Task 5); `android/app/build.gradle.kts`;
new `android/app/src/main/res/values-pl/strings.xml`, `android/app/src/main/res/resources.properties`;
`android/app/src/test/**` only if a test must adapt.

- Move every hardcoded user-visible string in Compose screens, components, view models (use string resource
  ids / a small `UiText` wrapper where a view model produces text), notification channel names/descriptions,
  permission rationale, alerts, sample data names, into `strings.xml` (+ `<plurals>`), with Polish twins.
- Replace `ui/format/DateText.kt`'s hardcoded English names with locale-aware `java.time` formatting that
  still produces exactly the current en-GB output ("Mon 7 Sep", "7 September 2026", "7 Sep 2026") in English.
- Enable per-app language (generateLocaleConfig + resources.properties).
- Notification title/body copy is NOT in this task (Task 5) — leave the planner and its call sites' copy alone.
- Recipe titles/ingredients/tags are NOT in this task (Task 5).
- Verify: `./gradlew :core:test :app:testDebugUnitTest` (with nvm Node not needed; use the repo's usual
  Gradle invocation) pass, Roborazzi screenshots unchanged in English, and add one Robolectric test with
  `@Config(qualifiers = "pl")` that renders a screen and asserts a Polish string.

### Task 3: Shared Polish recipe content, aliases and fixtures

Files (new only): `shared-assets/recipes.pl.json`, `shared-assets/recipe-tags.json`,
`shared-assets/ingredient-aliases.pl.json`, `testdata/recipe-matching-pl.json`,
`testdata/notification-plans-pl.json`, and a small validation script `scripts/check-polish-assets.py`
(stdlib only) that checks the overlay covers every recipe id, ingredient name, step count and tag, and that
the alias file covers every non-staple ingredient name (singular form) — run it and include the output.

- Translate all 35 recipes as a Polish cook would write them (metric amounts kept; "1 tbsp" → "1 łyżka",
  "1 tsp" → "1 łyżeczka", "pinch" → "szczypta", "splash" → "odrobina", "handful" → "garść").
- Use the formats and exact notification copy from this plan.

### Task 4: iOS wiring — Polish recipes, ingredient aliases, notification copy

Files: `ios/ZeroBite/Models/Recipe.swift`, `Services/Recipes/*`, recipe-display views/components
(`Screens/Recipes/*`, `UI/RecipeRow.swift`, anything else that displays ingredient names or tags),
`Services/Notifications/*`, `ios/project.yml` (bundle the three shared-assets files), `Localizable.xcstrings` /
new `Notifications.xcstrings`, `ios/ZeroBiteTests/**`.

- `Recipe.Ingredient` gains a display label (defaults to `name`); `name` stays the English canonical key used
  by matching. The store applies `recipes.pl.json` when the app's preferred localization is Polish
  (`Bundle.main.preferredLocalizations.first == "pl"`), injectable for tests.
- Tags display from `recipe-tags.json` for the current language.
- `RecipeMatcher` loads aliases (injectable; default from bundle) and applies the matching semantics above.
  Add a test that runs `testdata/recipe-matching-pl.json`.
- `ExpiryNotificationPlanner` gets an injected copy (title/body templates) whose default comes from the
  `Notifications` table for the current language; a test runs `testdata/notification-plans-pl.json` using the
  Polish copy loaded from the app bundle's `pl.lproj`, and the existing English fixture test is unchanged.
- Verify: full test scheme passes; simulator in Polish shows Polish recipe list, recipe detail, and a Polish
  item name ("Jajka") matching an egg recipe.

### Task 5: Android wiring — Polish recipes, ingredient aliases, notification copy

Files: `android/core/src/**` (model/Recipe.kt, recipes/*, notifications/*), `android/app/build.gradle.kts`
(copy the three shared-assets files into assets), `data/BundledRecipeRepository.kt`, recipe display
screens/components, `reminders/*`, `res/values*/strings.xml` (notification copy), tests.

- Mirror Task 4: ingredient display label, overlay applied when the app locale is Polish, tag display names,
  alias matching in the core `RecipeMatcher` (aliases passed in; core test reads them via `Fixtures.sharedAsset`),
  core test over `recipe-matching-pl.json`.
- `ExpiryNotificationPlanner` takes a `NotificationCopy` (core, pure Kotlin, with an English default);
  the app builds it from string resources. Core test: existing English fixture unchanged. App Robolectric
  test with `@Config(qualifiers = "pl")` builds the copy from resources and runs `notification-plans-pl.json`.
- Verify: `:core:test` and `:app:testDebugUnitTest` pass.

### Task 6: Polish App Store listing

Files (new only): `appstore/metadata/pl/*.txt` mirroring `appstore/metadata/en-GB/` (fastlane `deliver`
layout; URLs copied unchanged). Limits: name ≤ 30, subtitle ≤ 30, keywords ≤ 100 bytes comma-separated with
no spaces after commas and no words already in name/subtitle, promotional text ≤ 170, description ≤ 4000.
Name keeps "Zero Bite". Update `appstore/README.md` table to mention `metadata/pl/`. Report the character
counts.

### Task 7: End-to-end check

Run both full test suites; run iOS in Polish on the simulator through onboarding → sample food → recipes →
item detail → settings; record any untranslated or clipped strings. Android emulator check if one is
available, otherwise Robolectric screenshots in `pl`.

## Order

Tasks 1, 2, 3 and 6 touch disjoint files and run in parallel. Task 4 starts after Tasks 1 and 3; Task 5
after Tasks 2 and 3; Tasks 4 and 5 run in parallel. Task 7 last, then the final whole-branch review.
