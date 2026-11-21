# Polish Food Recalls (GIS) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Zero Bite reads the Polish Chief Sanitary Inspectorate's (GIS) public food warnings on the phone and matches them against the fridge alongside the UK FSA alerts. The feed follows the device region and can be changed in Settings.

**Architecture:** A GIS parser turns gov.pl HTML (the list pages and warning articles) into the existing `FoodAlert` model. A GIS client pages the list and fetches only the articles it hasn't seen. A `CombinedFoodAlerts` client fans out to the enabled sources, so `RecallStore` keeps one alert list. `RecallMatcher` gains Polish word rules, chosen per alert by `FoodAlert.source`. Swift is the reference implementation. Kotlin `:core` ports it, and shared golden fixtures in `testdata/` keep the two in step.

**Tech Stack:** Swift 6, SwiftUI, Swift Testing, Foundation regex (`NSRegularExpression`); Kotlin, Ktor, kotlinx.datetime, JUnit (Android `:core`). No new dependencies.

**Spec:** `specs/2026-11-04-poland-recalls.md`. Read it first: the source facts, behaviour and exact copy are there.

## Global Constraints

- No new third-party dependencies on either platform, and no HTML parsing library. Use regex and string scanning only.
- No server of our own. Every request goes from the phone to `www.gov.pl` (GIS) or `data.food.gov.uk` (FSA).
- Never download, cache, show or bundle GIS photos (CC BY-NC-ND). Use text only.
- GIS alert ids are `GIS-<slug>`. `FoodAlert.source` is derived from the id prefix, which keeps existing caches valid.
- Refresh interval stays `6 * 60 * 60` seconds and history stays 120 days, for both sources.
- GIS requests send `User-Agent: ZeroBite-iOS/1.0` (iOS) or `ZeroBiteHttp.USER_AGENT` (Android), with at most 3 article requests in flight at once.
- User-facing copy comes verbatim from the spec's Copy table, with a Polish translation for every new or changed string.
- FSA behaviour and every existing golden fixture stay unchanged. `testdata/recall-matching.json` must still pass as-is.
- Polish folding maps `ł`/`Ł` to `l` explicitly before diacritic folding, so iOS and Android agree.
- Swift is the reference implementation, and Kotlin matches its output on every shared fixture.

## Review Focus

1. **gov.pl changes its markup or returns an error page.** The parser returns no alerts rather than garbage alerts. Cached alerts survive, and the error banner shows. Covered in Task 1 (`parsesNothingFromUnrelatedHTML`) and Task 2 (`keepsCachedGISAlertsWhenGISFails`).
2. **The user switches feeds while offline.** Alerts from the feed they turned off disappear at once, and nothing is fetched for it. Covered in Task 2 (`dropsDisabledSourceWithoutNetwork`).
3. **A warning with many batch lines for one product** (bigos: six "Data minimalnej trwałości/ Numer partii" lines) becomes one product with six batches, not six products. Covered in Task 1.
4. **Adjective-first and inflected Polish names.** "Pesto" matches "Zielone pesto"; "Mleko" does not match "Czekolada mleczna"; "Sery" matches "Ser regionalny Gałka". Covered in Task 3's `nameMatches` fixture rows.
5. **Food-contact warnings and updates in the list.** Food-contact items are skipped. An "Aktualizacja" (update) entry is its own alert with its own id. Covered in Task 1 (`listSkipsFoodContactItems`).

---

## File Structure

| File | Responsibility |
|---|---|
| `testdata/gis-list.html`, `testdata/gis-alert-*.html` | gov.pl snapshots (full pages) |
| `testdata/gis-alerts.json` | Expected parse of each snapshot (golden) |
| `testdata/recall-matching-pl.json` | Polish matching and date golden cases |
| `testdata/README.md` | Add the gov.pl CC BY-SA 4.0 attribution for the snapshots |
| `ios/ZeroBite/Models/FoodAlert.swift` | Add `Source` and the derived `source` |
| `ios/ZeroBite/Services/Alerts/GISAlertParser.swift` | New: HTML to text lines, list entries, article to `FoodAlert` |
| `ios/ZeroBite/Services/Alerts/GISFoodAlertsClient.swift` | New: pages the list and fetches unseen articles |
| `ios/ZeroBite/Services/Alerts/CombinedFoodAlerts.swift` | New: fan-out to enabled sources, partial failure |
| `ios/ZeroBite/Services/Alerts/RecallStore.swift` | New fetch signature, partial-failure handling, generic error copy |
| `ios/ZeroBite/Services/Alerts/RecallMatcher.swift` | `Language`, Polish words and head rule, month-only dates |
| `ios/ZeroBite/Services/AppSettings.swift`, `AppDependencies.swift` | `recallSources` setting and wiring |
| `ios/ZeroBite/Screens/Settings/*`, `Screens/Alerts/*`, `UI/RecallViews.swift` | Picker, per-source attribution and copy |
| `ios/ZeroBite/Resources/Localizable.xcstrings` | English and Polish strings |
| `android/core/src/main/kotlin/com/mazesys/zerobite/core/alerts/Gis*.kt`, `CombinedFoodAlerts.kt` | Kotlin ports |
| `android/core/.../alerts/RecallMatcher.kt`, `AlertDateParser.kt`, `core/model/FoodAlert.kt` | Kotlin ports |
| `docs/privacy.html`, `appstore/app-privacy.md` | Name gov.pl |

---

### Task 1: GIS page parser (iOS) and fixtures

**Files:**
- Create: `testdata/gis-list.html`, `testdata/gis-alert-bigos-update.html`, `testdata/gis-alert-kokos-sulphites.html`, `testdata/gis-alert-herbatniki.html`, `testdata/gis-alert-ser-galka-ean.html`, `testdata/gis-alert-sery-rozne.html`, `testdata/gis-alert-boczek.html`
- Create: `testdata/gis-alerts.json`
- Create: `ios/ZeroBite/Services/Alerts/GISAlertParser.swift`
- Modify: `ios/ZeroBite/Models/FoodAlert.swift` (add `Source`)
- Modify: `testdata/README.md` (create it if it's missing)
- Test: `ios/ZeroBiteTests/GISAlertParserTests.swift`

**Interfaces:**
- Produces:
  - `enum FoodAlert.Source: String, Codable, Sendable, CaseIterable { case fsa, gis }`
  - `var FoodAlert.source: Source { id.hasPrefix("GIS-") ? .gis : .fsa }`
  - `enum GISAlertParser` with:
    - `struct ListEntry: Hashable, Sendable { let id: String; let url: URL; let published: Date }`
    - `static func listEntries(html: String) -> [ListEntry]`
    - `static func alert(html: String, url: URL) -> FoodAlert?`
    - `static func lines(ofHTML html: String) -> [String]`
  - `static let GISAlertParser.base = URL(string: "https://www.gov.pl")!`

- [ ] **Step 1: Capture the snapshots.** `curl -sL -A "ZeroBite-iOS/1.0"` each page into `testdata/`:
  - `https://www.gov.pl/web/gis/ostrzezenia?page=1` → `gis-list.html`
  - `/web/gis/aktualizacja-ostrzezenia-publicznego-z-dnia-03072026-dotyczacego-zywnosci-stwierdzenie-niezadeklarowanej-obecnosci-skladnika-alergennego--glutenu-w-partiach-bigosu-z-miesem-i-kielbasa` → `gis-alert-bigos-update.html`
  - `/web/gis/ostrzezenie-publiczne-dotyczace-zywnosci-niewlasciwa-informacja-dotyczaca-alergenu--brak-wyroznienia-obecnosci-siarczynow-na-etykiecie-produktu` → `gis-alert-kokos-sulphites.html`
  - `/web/gis/ostrzezenie-publiczne-dotyczace-zywnosci-podwyzszony-poziom-akryloamidu-w-okreslonej-partii-herbatnikow` → `gis-alert-herbatniki.html`
  - `/web/gis/ostrzezenie-publiczne-dotyczace-zywnosci-wykrycie-obecnosci-bakterii-listeria-monocytogenes-w-jednej-partii-sera-podpuszczkowego` → `gis-alert-ser-galka-ean.html`
  - `/web/gis/ostrzezenie-publiczne-dotyczace-zywnosci-wykrycie-obecnosci-bakterii-listeria-monocytogenes-w-jednej-partii-serow-rozne-rodzaje` → `gis-alert-sery-rozne.html`
  - `/web/gis/ostrzezenie-publiczne-dotyczace-zywnosci-wykrycie-obecnosci-bakterii-listeria-monocytogenes-w-jednej-partii-boczku-wedzonego` → `gis-alert-boczek.html`

  Add to `testdata/README.md`: "`gis-*.html`: pages from gov.pl, Główny Inspektorat Sanitarny, CC BY-SA 4.0 (text only; photos are not included)."

- [ ] **Step 2: Write `testdata/gis-alerts.json`.** Its shape is `{ "list": { "file", "entries": [{ "id", "url", "published" }] }, "alerts": [{ "file", "url", "expect": <FoodAlert fields> }] }`. Fill it by reading the snapshots and applying these rules:
  - `list.entries`: every food entry on the page, newest first, with `published` as `YYYY-MM-DD`. Food-contact entries are absent; the snapshot should contain at least one, and if it doesn't, capture page 2 as well.
  - `title`: the `<h2>` text. `shortTitle`: the text after the first `": "`, with its first letter capitalised and `"Aktualizacja: "` in front for update titles.
  - `created`: the first `DD.MM.YYYY` in the article, read in the `Europe/Warsaw` timezone.
  - `kind` is `"AA"` when the title or the "Zagrożenie:" text contains `alergen`, else `"PRIN"`.
  - `allergens` are English raw values: bigos → `["gluten"]`, kokos → `["sulphites"]`, all others `[]`.
  - Bigos: one product, `Bigos z mięsem i kiełbasą, 500 g`, with 6 batches. Each batch has `batchCode` and `bestBefore` both set to the full value (e.g. `"10.2027 10A"`).
  - Herbatniki: product `Herbatniki Petit Beurre, 400 g`, one batch with `batchCode` = `bestBefore` = `"6104037121 01-10 - 13/04/2027"`. `otherBusinesses` contains `"Bonitki"` (from `Marka`) and `"YARYCH IMPORT EKSPORT SP. Z O.O."`.
  - Ser gałka: `productCode` `"5905784304458"`, `useBy` `"25.09.2026"`, `batchCode` `"07286"`. `business` is `"PPHU Balser Marian Górz Wróblówka"`, the text before the first comma. `otherBusinesses` is `["CARREFOUR POLSKA Sp. z o. o. ul. Targowa 72"]`, also cut at the first comma.
  - Sery różne: 8 products, each with batch `"261.2026/M"` and its own `useBy`. `business` starts `"Mlecz Ser Górskie Sp. z o. o. ul. ks. Juliana Łyska 7A"`, again cut at the first comma.
  - `risk` is the "Zagrożenie:" lines joined with spaces. `consumerAdvice` is the "Zalecenia dla konsumentów:" lines joined with spaces, stopping before "Zdjęcia" (photos).

- [ ] **Step 3: Write the failing tests** in `GISAlertParserTests.swift`, each loading the snapshots from the test bundle:
  - `listMatchesGolden`: `GISAlertParser.listEntries` equals `gis-alerts.json` `list.entries`.
  - `listSkipsFoodContactItems`: no entry id contains `wyrobu-do-kontaktu`.
  - `alertsMatchGolden`: for each `alerts[i]`, `GISAlertParser.alert(html:url:)` equals `expect` field by field, with the batch count asserted.
  - `parsesNothingFromUnrelatedHTML`: `alert(html: "<html><body><p>Błąd</p></body></html>", url:)` is `nil`, and `listEntries` of the same string is `[]`.
  - `allergenStemsStartWords`: an allergy alert whose hazard text says "w trybie pilnym" and "gluten" has `allergens == ["gluten"]`, with no fish.
  - `decodesEntities`: `lines(ofHTML: "<p>Szczeg&oacute;ły&nbsp;&bdquo;x&rdquo; &#322;&amp;</p>")` == `["Szczegóły „x” ł&"]`.
  - `sourceFollowsIdPrefix`: `GIS-x` gives `.gis`, and an existing FSA fixture alert gives `.fsa`.

- [ ] **Step 4: Run them and confirm they fail.** Run `cd ios && xcodegen generate && xcodebuild test -scheme ZeroBite -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -only-testing:ZeroBiteTests/GISAlertParserTests`. Expected: FAIL because `GISAlertParser` is not found.

- [ ] **Step 5: Implement `GISAlertParser` and `FoodAlert.Source`.**

  `lines(ofHTML:)`:
  - Turn `<br>`, `</p>`, `</h2>`, `</h3>`, `</li>` and `</div>` into newlines, then strip all other tags.
  - Decode entities: the named ones `nbsp amp quot lt gt oacute Oacute eacute ouml szlig ndash mdash bdquo rdquo ldquo` plus numeric `&#N;` and `&#xH;`. Leave unknown entities as they are.
  - Collapse whitespace and drop empty lines.

  `alert(html:url:)`:
  - Work only inside `<article … id="main-content"> … </article>`.
  - Read product and business fields only between the line starting `Szczegóły dotyczące produkt` and the next line starting `Działania` or `Zalecenia`. The actions section repeats words like "Producent" in running text.
  - A line `Label: value` whose label is a known field starts or extends a product. A product starts at `Nazwa produktu` or `Produkt`.
  - Each batch-type label (batch number, use by, best before, the combined labels, any label containing both `partii` and `trwałości`) fills the current batch. When that field is already set, it starts a new batch.
  - Business labels (any label beginning `Producent`, plus `Dystrybutor*`, `Wyprodukowano dla`, `Wyprodukowany w UE dla`, `Importer`, `Sprzedawca`, `Sprzedaż detaliczna`, `Dostawca`, `Marka`) are cut at the first comma. The first `Producent*` becomes `business`; the rest go to `otherBusinesses` in page order.
  - Allergens for allergy alerts: search the folded title and hazard text for these stems. A stem must start a word, so `ryb` must not match "trybie":

    | Stem | Allergen |
    |---|---|
    | `gluten` | gluten |
    | `siarczyn`, `dwutlenk` + `siark` | sulphites |
    | `mlek`, `mleka`, `laktoz` | milk |
    | `jaj` | egg |
    | `orzeszk` + `ziemn` | peanuts |
    | `orzech`, `pistacj`, `migdal` | nuts |
    | `sezam` | sesame |
    | `soj` | soya |
    | `seler` | celery |
    | `gorczyc` | mustard |
    | `lubin` | lupin |
    | `ryb` | fish |
    | `skorupiak` | crustaceans |
    | `mieczak` | molluscs |

  - Return `nil` when there is no `<h2>`, no date or no product.

- [ ] **Step 6: Run the tests and confirm they pass.** Use the same command as Step 4. Expected: PASS. Then run the full `ZeroBiteTests` and expect PASS, because the FSA fixtures are untouched.

- [ ] **Step 7: Commit.** `git add testdata/gis-* testdata/README.md ios/ZeroBite/Services/Alerts/GISAlertParser.swift ios/ZeroBite/Models/FoodAlert.swift ios/ZeroBiteTests/GISAlertParserTests.swift` then `git commit -m "Parse GIS food warnings from gov.pl pages into food alerts"`

---

### Task 2: GIS client, combined sources and RecallStore (iOS)

**Files:**
- Create: `ios/ZeroBite/Services/Alerts/GISFoodAlertsClient.swift`, `ios/ZeroBite/Services/Alerts/CombinedFoodAlerts.swift`
- Modify: `ios/ZeroBite/Services/Alerts/RecallStore.swift` (protocol, refresh, error copy), `ios/ZeroBite/Services/AppDependencies.swift:66-74`, `PreviewFoodAlerts`
- Test: `ios/ZeroBiteTests/RecallTests.swift` (add to the existing store tests)

**Interfaces:**
- Consumes: `GISAlertParser.listEntries`, `GISAlertParser.alert`, `FoodAlert.source` (Task 1).
- Produces:
  - `protocol FoodAlertsFetching: Sendable { func fetchAlerts(since: Date, known: [FoodAlert]) async throws -> [FoodAlert] }`, which replaces the one-argument form. `FSAFoodAlertsClient` ignores `known`.
  - `struct GISFoodAlertsClient: FoodAlertsFetching { var session: URLSession = .shared; var maxPages = 8 }`.
  - `struct CombinedFoodAlerts: FoodAlertsFetching { init(clients: [FoodAlert.Source: any FoodAlertsFetching], enabled: @escaping @Sendable () -> Set<FoodAlert.Source>) }`.
  - `struct FoodAlertsPartialFailure: Error { let alerts: [FoodAlert]; let failed: Set<FoodAlert.Source> }`.

- [ ] **Step 1: Write the failing tests** (add a `URLProtocol` stub to `TestSupport.swift` if none exists):
  - `gisClientStopsPagingAtCutoff`:
    - Setup: page 1 is `gis-list.html`, and `since` is set later than the oldest list entry.
    - Expect: exactly one list request, then one article request per entry newer than `since`, with alerts ordered by `created` descending.
  - `gisClientReusesKnownArticles`: when `known` holds an alert with entry 1's id, entry 1's article is never requested.
  - `gisClientSkipsBrokenArticle`: an article that returns 500 is left out and the others are returned. A list page that returns 500 makes the call throw.
  - `combinedFetchesOnlyEnabledSources`: with `enabled = [.gis]`, the FSA stub is never called.
  - `dropsDisabledSourceWithoutNetwork`:
    - Setup: `known` holds FSA and GIS alerts, `enabled = [.fsa]`, and every client stub throws.
    - Expect: the call throws `FoodAlertsPartialFailure` whose `alerts` holds only the known FSA alerts.
  - `keepsCachedGISAlertsWhenGISFails`:
    - Setup: a store with cached FSA and GIS alerts, where the FSA client succeeds and the GIS client throws.
    - Expect: after `refresh(force: true)`, `alerts` holds the new FSA alerts plus the cached GIS ones, `lastErrorMessage` == "Couldn't check for new food alerts. Showing the last ones we downloaded.", and `refresh` returns `false`.

- [ ] **Step 2: Run them and confirm they fail.** Run `-only-testing:ZeroBiteTests/RecallStoreTests`, or whichever suite holds the store tests. Expected: compile FAIL on the new signature.

- [ ] **Step 3: Implement.**
  - GIS client:
    - Read pages `?page=1…maxPages`, stopping after the first page that contains an entry with `published < since`.
    - Fetch the unknown articles with at most 3 in flight (a `withThrowingTaskGroup` gated to 3). Each request has a 20 s timeout and the User-Agent header.
    - Return the known alerts for entries still on the list (or with `created >= since`), plus the newly parsed ones.
  - `CombinedFoodAlerts`:
    - Call each enabled client with the `known` alerts of its own source.
    - On a client error, keep that source's known alerts.
    - Throw `FoodAlertsPartialFailure` if any enabled source failed, otherwise return the merged list.
  - `RecallStore.refresh`: pass `alerts` as `known`. On `FoodAlertsPartialFailure`, save `alerts`, cache them, set the error and return `false`. Don't update `lastChecked`, so the next launch retries.
  - Use the new error copy from the spec.
  - Wire `CombinedFoodAlerts(clients: [.fsa: FSAFoodAlertsClient(), .gis: GISFoodAlertsClient()], enabled: { settings.recallSources })` in `AppDependencies`. Until Task 4 lands, `recallSources` can be a constant `[.fsa]`.

- [ ] **Step 4: Run the tests and confirm they pass.** Run the full `ZeroBiteTests`. Expected: PASS.

- [ ] **Step 5: Commit.** Message: `"Fetch GIS warnings on the phone and combine alert sources"`.

---

### Task 3: Polish recall matching (iOS)

**Files:**
- Modify: `ios/ZeroBite/Services/Alerts/RecallMatcher.swift`
- Create: `testdata/recall-matching-pl.json`
- Test: `ios/ZeroBiteTests/GoldenFixtureTests.swift` (new `@Test func polishRecallMatching()`, loading alerts by parsing the Task 1 snapshots)

**Interfaces:**
- Consumes: parsed GIS fixture alerts (Task 1).
- Produces:
  - `enum RecallMatcher.Language: Sendable { case english, polish }`
  - `static func words(_ text: String, language: Language = .english) -> [String]`
  - `static func nameMatches(itemWords: [String], productWords: [String], language: Language = .english) -> Bool`
  - `match(alert:product:item:)` uses `.polish` when `alert.source == .gis`.
  - `AlertDateParser.dates(in:)` also returns month-only specs for `MM.YYYY` and `MM/YYYY`.

- [ ] **Step 1: Write `testdata/recall-matching-pl.json`.** It has the same keys as `recall-matching.json`; `alertId` refers to the `GIS-…` ids from `gis-alerts.json`. Required rows:
  - `nameMatches` (`language` is implied Polish):

    | Item | Product | Matches |
    |---|---|---|
    | Ser | SER GAŁKA Z ZAKOPANEGO 280g | true |
    | Sery | Ser regionalny Gałka Naturalna 250 g | true |
    | Boczek | Boczek wędzony | true |
    | Pesto | Zielone pesto | true |
    | Mleko | Czekolada mleczna | false |
    | Gałka wędzona | Ser regionalny Gałka Wędzona 250 g | true |
    | Galka wedzona | Ser regionalny Gałka Wędzona 250 g | true |
    | Herbatniki | Herbatniki Petit Beurre, 400 g | true |
    | Kiełbasa | Bigos z mięsem i kiełbasą, 500 g | false |

  - `alertDates`:
    - `"03.2028 19A"` → `[{2028,3,nil}]`
    - `"17.07.2026 r. oraz 11.07.2026 r."` → two day specs
    - `"6104037121 01-10 - 13/04/2027"` → `[{2027,4,13}]`
    - `"261.2026/M"` → `[]`
  - `matchCases`:
    - Barcode `5905784304458` on the ser gałka alert → `likely`, `["barcode"]`.
    - "Ser" expiring 2026-09-25 on ser gałka → `possible`, `["name","date"]`.
    - "Bigos" with brand none, expiring 2027-10-31 → `possible`, reasons include `name` and `date` (month-only date).
    - "Bigos" expiring 2027-05-15 → `null`, because the date contradicts every batch.
    - "Herbatniki" with brand "Bonitki", expiring 2027-04-13 → `likely`.
  - `concerns`: the kokos alert with profile `["sulphites","milk"]` → `["sulphites"]`.

- [ ] **Step 2: Write the failing `polishRecallMatching` test.** It mirrors `recallMatching()`, passes `language: .polish` to `words` and `nameMatches`, and gets its alerts from `GISAlertParser.alert` over the snapshots. Also re-assert that `recallMatching()` (English) still passes.

- [ ] **Step 3: Run it and confirm it fails.** Run `-only-testing:ZeroBiteTests/GoldenFixtureTests`. Expected: FAIL on `language:` not existing.

- [ ] **Step 4: Implement.**
  - Polish `words`:
    - Map `ł→l`, then fold case and diacritics. Split on non-alphanumerics and drop tokens with digits.
    - Drop stop words: `z ze i w we na do od bez oraz o po dla lub typu smak smaku marki partia partii g kg ml l szt op`.
    - Strip the longest matching suffix, keeping at least 3 letters. The suffixes are `ami ach ego emu ymi ich ych owi om ow ie ia iu ej a e i o u y`.
  - Polish `nameMatches`:
    - Several item words: every item word must be in the product.
    - A single item word must equal product word 0, or word 1 when word 0 looks like an adjective. Word 0 looks like an adjective when its folded, unstemmed form ends in `ny na ne owy owa owe ony ona one ski ska skie cki cka ckie`.
    - The adjective check needs the unstemmed words, so keep a parallel raw list in `match` for this one check.
  - Every `words` call in `match` (product, business, item name and brand) uses the alert's language, so "Bonitki" on the item and on the notice stem alike.
  - Month-only dates: add `(?<![\d./])(\d{1,2})[./](\d{4})(?!\d)` to `AlertDateParser`. Months outside 1…12 are rejected.

- [ ] **Step 5: Run the tests and confirm they pass.** Run the full `ZeroBiteTests`. Expected: PASS, the English fixture included.

- [ ] **Step 6: Commit.** Message: `"Match Polish item names against GIS warnings"`.

---

### Task 4: Recall feed setting, copy and privacy text (iOS)

**Files:**
- Modify: `ios/ZeroBite/Services/AppSettings.swift`, `ios/ZeroBite/Services/AppDependencies.swift`
- Modify: `ios/ZeroBite/Screens/Settings/SettingsView.swift:51-56,129`, `SettingsViewModel.swift`
- Modify: `ios/ZeroBite/Screens/Alerts/FoodAlertsView.swift:60,137`, `FoodAlertsViewModel.swift:96`, `ios/ZeroBite/UI/RecallViews.swift:135`
- Modify: `ios/ZeroBite/Resources/Localizable.xcstrings`
- Modify: `docs/privacy.html:38-39,54`, `appstore/app-privacy.md:29`
- Test: `ios/ZeroBiteTests/RecallTests.swift`, `LocalizationTests.swift`

**Interfaces:**
- Consumes: `FoodAlert.Source` (Task 1), `CombinedFoodAlerts` (Task 2).
- Produces:
  - `var AppSettings.recallSources: Set<FoodAlert.Source>`, stored under key `"recallSources"` as `[String]`.
  - `static func AppSettings.defaultRecallSources(region: String?) -> Set<FoodAlert.Source>`: `"PL"` gives `[.gis]`; anything else, including nil, gives `[.fsa]`.

- [ ] **Step 1: Write the failing tests.**
  - `defaultSourcesFollowRegion`: PL gives `[.gis]`, GB gives `[.fsa]`, DE gives `[.fsa]`, nil gives `[.fsa]`.
  - `recallSourcesPersist`: set `[.fsa, .gis]` on an ephemeral settings object, then a fresh object over the same defaults reads `[.fsa, .gis]`.
  - `polishRecallCopy`: in the `pl` bundle, "Check food recalls" → "Sprawdzaj wycofania żywności", "GIS notice" → "Komunikat GIS" and "Both" → "Oba".

- [ ] **Step 2: Run them and confirm they fail.** Expected: FAIL.

- [ ] **Step 3: Implement.**
  - The settings default reads `Locale.current.region?.identifier`.
  - In the Food safety card, under the toggle, add a "Recalls from" menu picker with three options. They map to `[.fsa]`, `[.gis]` and `[.fsa, .gis]`, and the picker is disabled while the toggle is off. Use accessibility identifier `settings.recallSources`.
  - Changing the picker calls `recalls.refresh(force: true)`.
  - Per-alert copy:
    - The detail source line and the notice-link label depend on `alert.source`.
    - The list footer shows one line for each enabled source.
    - Replace the toggle, the footer, the error, the empty hint and the About sentence with the spec's copy, adding the Polish for each.
  - Privacy page:
    - Retitle the third-party block "Food recall alerts (Food Standards Agency, gov.pl)".
    - The block's first sentence becomes: "If recall checks are on, Zero Bite downloads the public alert lists of the UK Food Standards Agency and/or Poland's Chief Sanitary Inspectorate (gov.pl), as chosen in Settings → Food safety." The rest of the paragraph stays as it is.
    - In the line-54 sentence, the list of services becomes "Open Food Facts, the Food Standards Agency or gov.pl".
  - Update the matching row in `app-privacy.md`.

- [ ] **Step 4: Run the tests and confirm they pass.** Run the full `ZeroBiteTests`. Expected: PASS.

- [ ] **Step 5: Run on the simulator.**
  - Launch on iPhone 17 Pro with the region set to Poland (`-AppleLocale pl_PL` and `-AppleLanguages "(pl)"` launch arguments).
  - Add "Ser", then open Food alerts and confirm GIS warnings are listed with the GIS attribution.
  - Switch to "Wielka Brytania (FSA)" and confirm the GIS alerts disappear.
  - Check screenshots with `xcrun simctl io <udid> screenshot`, since the MCP screenshot lags one action behind.

- [ ] **Step 6: Commit.** Message: `"Choose recall feeds by region, with a Settings override and per-source attribution"`.

---

### Task 5: Kotlin port of the GIS parser, client and combined sources

**Files:**
- Create: `android/core/src/main/kotlin/com/mazesys/zerobite/core/alerts/GisAlertParser.kt`, `GisAlertsClient.kt`, `CombinedFoodAlerts.kt`
- Modify: `android/core/src/main/kotlin/com/mazesys/zerobite/core/model/FoodAlert.kt` (add `enum class Source { FSA, GIS }` and `val source get() = if (id.startsWith("GIS-")) GIS else FSA`)
- Modify: `FsaAlertsClient.kt` (`FoodAlertsFetching.fetchAlerts(since: LocalDate, known: List<FoodAlert>)`)
- Test: `android/core/src/test/kotlin/com/mazesys/zerobite/core/alerts/GisAlertParserTest.kt`, `GisAlertsClientTest.kt`, `CombinedFoodAlertsTest.kt`

**Interfaces:**
- Consumes: `testdata/gis-*.html` and `gis-alerts.json` (Task 1), with the behaviour defined in Task 2.
- Produces:
  - `object GisAlertParser { data class ListEntry(val id: String, val url: String, val published: LocalDate); fun listEntries(html: String): List<ListEntry>; fun alert(html: String, url: String): FoodAlert?; fun lines(html: String): List<String> }`
  - `class GisAlertsClient(http: HttpClient, maxPages: Int = 8) : FoodAlertsFetching`
  - `class CombinedFoodAlerts(clients: Map<FoodAlert.Source, FoodAlertsFetching>, enabled: () -> Set<FoodAlert.Source>) : FoodAlertsFetching`
  - `class FoodAlertsPartialFailure(val alerts: List<FoodAlert>, val failed: Set<FoodAlert.Source>) : Exception()`

- [ ] **Step 1: Write the failing tests.** Port each Task 1 and Task 2 test by name, reading fixtures with `Fixtures.text("gis-list.html")` and so on. The HTTP tests use Ktor `MockEngine`, the same way `FsaAlertsClientTest` does.
- [ ] **Step 2: Run them and confirm they fail.** Run `cd android && ./gradlew :core:test --tests '*Gis*' --tests '*CombinedFoodAlerts*'`. Expected: FAIL (unresolved references).
- [ ] **Step 3: Implement.** Apply the same algorithm and entity table as Task 1. Limit concurrency to 3 with a `Semaphore(3)`. Update the existing FSA client and its test to take the new `known` argument.
- [ ] **Step 4: Run the tests and confirm they pass.** Run `./gradlew :core:check`. Expected: PASS.
- [ ] **Step 5: Commit.** Message: `"Port the GIS parser, client and combined alert sources to Android core"`.

---

### Task 6: Kotlin port of Polish matching

**Files:**
- Modify: `android/core/src/main/kotlin/com/mazesys/zerobite/core/alerts/RecallMatcher.kt`, `AlertDateParser.kt`
- Test: `android/core/src/test/kotlin/com/mazesys/zerobite/core/alerts/RecallMatcherTest.kt` (add `polishGoldenFixture()`)

**Interfaces:**
- Consumes: `testdata/recall-matching-pl.json` (Task 3), `GisAlertParser` (Task 5).
- Produces: `enum class Language { ENGLISH, POLISH }`, `fun words(text: String, language: Language = Language.ENGLISH)` and `fun nameMatches(itemWords, productWords, language = Language.ENGLISH)`. The language is chosen from `alert.source`.

- [ ] **Step 1: Write the failing test.** `polishGoldenFixture()` runs every row of `recall-matching-pl.json`, mirroring the existing English golden test.
- [ ] **Step 2: Run it and confirm it fails.** Run `./gradlew :core:test --tests '*RecallMatcherTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement.** Use the rules from Task 3 Step 4. Map `ł→l` before `Normalizer` NFD folding.
- [ ] **Step 4: Run the tests and confirm they pass.** Run `./gradlew :core:check`. Expected: PASS, the English golden test included.
- [ ] **Step 5: Commit.** Message: `"Port Polish recall matching to Android core"`.

---

### Task 7: Android app — recall feed setting and copy

**Prerequisite:** Tasks 5–8 of `specs/plans/2026-10-08-android-scanning-alerts.md` must be merged first. They cover the recall store, sync, recalls in the fridge and the alerts screens. Rebase this task onto them.

**Files:**
- Modify: `android/app/src/main/kotlin/com/mazesys/zerobite/data/Settings.kt`, `AppDependencies.kt`, `screens/settings/SettingsScreen.kt`, `SettingsViewModel.kt`
- Modify: the alert screens and recall card composables from that plan (attribution, notice link)
- Modify: `android/app/src/main/res/values/strings.xml`, `values-pl/strings.xml`
- Test: `android/app/src/test/...` settings and view-model tests, following that plan's test locations

**Interfaces:**
- Consumes: `CombinedFoodAlerts`, `GisAlertsClient`, `FoodAlert.Source` (Task 5).
- Produces: a `recallSources: Set<FoodAlert.Source>` setting stored as a string set under key `recallSources`, defaulting from `Locale.getDefault().country` with the same rule as Task 4.

- [ ] **Step 1: Write the failing tests.** They mirror Task 4's `defaultSourcesFollowRegion` and `recallSourcesPersist`, plus a view-model test showing that changing sources triggers a forced refresh.
- [ ] **Step 2: Run them and confirm they fail.** Run `./gradlew :app:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3: Implement.** Use the same picker, copy and attribution as Task 4, with English and Polish strings verbatim from the spec. Wire `CombinedFoodAlerts` into the recall store in `AppDependencies`.
- [ ] **Step 4: Run the tests and confirm they pass.** Run `./gradlew :core:check :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`. Expected: PASS.
- [ ] **Step 5: Commit.** Message: `"Choose recall feeds on Android, with GIS attribution"`.

---

### Task 8: Live check and whole-branch review

- [ ] **Step 1: Run a live smoke test on iOS.** Run a debug build on the simulator with the Polish region, pull to refresh in Food alerts, and confirm the newest GIS warning on gov.pl/web/gis/ostrzezenia appears with its products. Confirm through the debugger or the cache file that no photo URLs are fetched.
- [ ] **Step 2: Run a live smoke test on Android** (after Task 7). Do the same on the emulator.
- [ ] **Step 3: Get a whole-branch review** against the spec and the Review Focus list.
- [ ] **Step 4: Publish the privacy text.** The live privacy page is built from `~/Repositories/non-work/mazesys.com`. Hand Pat the changed paragraph to apply there. Don't push that repo without asking.
