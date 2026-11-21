# Polish food recalls (GIS) — design

Date: 2026-11-04. Status: agreed with Pat (parsing on the phone; feed follows the device region, changeable in Settings).

## Why

Zero Bite checks the fridge against UK Food Standards Agency (FSA) alerts. It now has Polish users and built-in Polish recipes, but no Polish recalls. Poland's equivalent is the Chief Sanitary Inspectorate (Główny Inspektorat Sanitarny, GIS), which publishes "Ostrzeżenie publiczne dotyczące żywności" (public food warnings).

## Source facts (checked 2026-11-04)

- List: `https://www.gov.pl/web/gis/ostrzezenia?page=N` (N from 1), 10 entries a page, newest first. `size` is ignored. There is no API, RSS feed or dane.gov.pl dataset.
- About 7 food warnings a month, updates included. In each `<li>`, `<span class="date">DD.MM.YYYY</span>` is followed by `<div class="title"><a href="/web/gis/<slug>">`.
- Food entries have slugs starting `ostrzezenie-publiczne-dotyczace-zywnosci` or `aktualizacja-ostrzezenia-publicznego` and containing `zywnosci`. Food-contact items (`...dotyczace-wyrobu-do-kontaktu-z-zywnoscia...`) are mixed into the list and are not food.
- Article: `<article class="article-area__article " id="main-content">`, with the title in `<h2>`, the date as the first `DD.MM.YYYY`, and sections headed by `<h3>` or `<p>` text ending in `:`. The sections are "Zagrożenie:" (hazard), "Szczegóły dotyczące produktu/produktów:" (product details), "Działania podjęte…" (actions taken) and "Zalecenia dla konsumentów:" (advice for consumers). The fields are `Label: <strong>value</strong>`, separated by `<br />` or `<p>`.
- Field labels seen, with counts out of 36 warnings:
  - Product start: `Nazwa produktu` (product name, 37) and `Produkt` (product, 10).
  - Batch: `Numer partii` (batch number, 43).
  - Use by: `Termin przydatności do spożycia` (27).
  - Best before: `Data minimalnej trwałości` (17) and `Najlepiej spożyć przed[ końcem]` (best before [the end of]).
  - Combined batch and date: `Data minimalnej trwałości/ Numer partii` and `Numer partii i data minimalnej trwałości`.
  - Barcode: `Kod kreskowy` (3).
  - Brand: `Marka`.
  - Businesses: `Producent` (producer), `Dystrybutor` (distributor), `Wyprodukowano dla` (made for), `Importer`, `Sprzedawca` (seller), `Sprzedaż detaliczna` (retail), `Dostawca` (supplier).
- Dates come as `DD.MM.YYYY`, `DD/MM/YYYY`, `DD/MM/YY` and month-only `MM.YYYY`, sometimes followed by `r.` and sometimes several joined with `oraz` (and).
- Only 3 of 36 warnings carry an EAN. As with FSA, name, brand and date do most of the matching.
- Licence: gov.pl text is CC BY-SA 4.0 and photos are CC BY-NC-ND 4.0. We use text only and attribute it ("Główny Inspektorat Sanitarny, gov.pl, CC BY-SA 4.0"). We never download, show or bundle the photos.

## Behaviour

1. **Fetch on the phone, as with FSA.** The app reads list pages until an entry is older than the 120-day window, or until page 8. It fetches only the articles not already in the cache, since articles don't change and an update is a new entry. That makes 1–2 list requests a refresh at most, plus the new articles, with no server of our own. Refresh stays throttled to 6 hours.
2. **Map each warning to the existing `FoodAlert`.**
   - `id`: `GIS-<slug>`.
   - `kind`: `.allergy` when the title or the hazard text mentions an allergen (`alergen`), otherwise `.recall`.
   - `allergens`: English `Allergen` raw values found in the title and hazard text, filled only for allergy alerts.
   - `business`: the first producer. `otherBusinesses`: the other business labels, plus `Marka`.
   - `risk`: the hazard text. `consumerAdvice`: the advice-for-consumers text.
   - Products and batches come from the product-details section.
3. **Choose feeds by region.**
   - Default: device region `PL` gets GIS, every other region gets FSA.
   - Settings → Food safety → "Recalls from" offers UK (FSA), Poland (GIS) or Both.
   - Changing it refreshes immediately. Alerts from a feed that was switched off disappear even when offline.
4. **One source failing doesn't hide the other.** Its cached alerts stay, the other source updates, and the error banner shows.
5. **Polish-aware matching for GIS alerts.** FSA matching is unchanged. For GIS alerts:
   - Fold diacritics, mapping `ł` to `l` explicitly so iOS and Android agree.
   - Drop Polish stop words, strip inflection endings and match on the stems.
   - A one-word item must equal the product's first word, or its second word when the first looks like an adjective ("Zielone **pesto**").
   - Month-only dates (`03.2028`) match any day of that month.
6. **Attribution and copy.** Each alert shows its own source and licence. The privacy page names gov.pl as a third party the app contacts.

## Copy (exact)

| Where | English | Polish |
|---|---|---|
| Recalls toggle (replaces "Check UK food recalls") | Check food recalls | Sprawdzaj wycofania żywności |
| Feed picker label | Recalls from | Źródło komunikatów |
| Picker options | UK (FSA) · Poland (GIS) · Both | Wielka Brytania (FSA) · Polska (GIS) · Oba |
| Food safety footer | Checks official recall notices and tells you if any of your food might be affected. | Sprawdza oficjalne komunikaty o wycofaniach i daje znać, jeśli mogą dotyczyć twoich produktów. |
| Alerts list footer, GIS line (FSA line unchanged; show the line for each enabled feed) | Warnings from Poland's Chief Sanitary Inspectorate (GIS), gov.pl, licensed under CC BY-SA 4.0. | Ostrzeżenia Głównego Inspektoratu Sanitarnego (GIS) z gov.pl, na licencji CC BY-SA 4.0. |
| Alert detail source, GIS | Source: Chief Sanitary Inspectorate (GIS), gov.pl. CC BY-SA 4.0. | Źródło: Główny Inspektorat Sanitarny (GIS), gov.pl. CC BY-SA 4.0. |
| Notice link, GIS (FSA keeps "FSA notice") | GIS notice | Komunikat GIS |
| Fetch error (replaces the FSA-specific one) | Couldn't check for new food alerts. Showing the last ones we downloaded. | Nie udało się sprawdzić nowych alertów. Pokazujemy ostatnio pobrane. |
| Empty-list hint (replaces the FSA-specific one) | Pull down to check for new alerts. | Pociągnij w dół, by znów sprawdzić alerty. |
| About footer, last sentence | Food alerts come from the Food Standards Agency (Open Government Licence v3.0) and Poland's Chief Sanitary Inspectorate (gov.pl, CC BY-SA 4.0). | Alerty żywnościowe pochodzą z brytyjskiej Food Standards Agency (Open Government Licence v3.0) i polskiego Głównego Inspektoratu Sanitarnego (gov.pl, CC BY-SA 4.0). |

## Out of scope

- Retailers' own withdrawal notices (Biedronka, Lidl and so on).
- Regional sanepid (local sanitary inspectorate) pages.
- Food Standards Scotland's Scotland-only alerts.
- Translating GIS text into English.
- Matching English item names against Polish notices. A barcode still matches either way.
