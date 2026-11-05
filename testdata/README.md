# Golden fixtures

Shared by the iOS tests (`ios/ZeroBiteTests`) and, later, the Android `:core` tests.

- `fsa-alerts.json`: a real response from the Food Standards Agency alerts API, Open Government Licence v3.0.
- `gis-*.html`: pages from gov.pl, Główny Inspektorat Sanitarny, CC BY-SA 4.0 (text only; photos are not included).
- `gis-alerts.json`: what the GIS parser must produce from those pages. `list.entries` is the food entries of `gis-list.html`, newest first. `alerts[].expect` is every `FoodAlert` field for one article, with `created` and `published` as `YYYY-MM-DD` (the Europe/Warsaw day).
