# App Store assets

| Path | What it is |
|---|---|
| `app-icon-1024.png` | Marketing icon (opaque, no alpha), same as the app's default icon |
| `screenshots/6.9in/` | 1320 × 2868 panels → **iPhone 6.9" Display** slot |
| `screenshots/6.3in/` | 1206 × 2622 panels → **iPhone 6.1"/6.3" Display** slot |
| `screenshots/6.5in/` | 1284 × 2778 panels → **iPhone 6.5" Display** slot |
| `header/product-page-header-3840x1646.png` | Product page header (Creative Assets); key content stays in the centre, the phones at the sides may be cropped |
| `screenshots/overview.png` | All 6.9" panels side by side, for review only (don't upload) |

Each slot in App Store Connect only accepts its own pixel size, and file names start with the size (`6.3in-01-…`) so the sets can't be mixed up.
| `metadata/en-GB/` | Name, subtitle, keywords, description, promo text, release notes and URLs (fastlane `deliver` layout) |
| `metadata/pl/` | The same files in Polish for the Polish App Store localisation (`pl`); URLs unchanged |
| `app-privacy.md` | Answers for App Store Connect's App Privacy questionnaire, and where the URLs come from |
| `../docs/` | Marketing, support and privacy pages to host for the URLs |
| `raw/` | Unframed simulator captures the panels are built from |

## Regenerating

Icons and panels are rendered from `ios/ZeroBite/UI/BrandMark.swift`, the same view the app draws,
so the logo can't drift between the app and the store.

```bash
scripts/fetch-fonts.sh              # Mona Sans, needed for the app and the panel headlines
scripts/render-assets.sh icons      # AppIcon light / dark / tinted → Assets.xcassets
scripts/render-assets.sh marketing  # panels → appstore/screenshots, header → appstore/header
```

Panel copy, colours and pop-out crops are in `design/Marketing.swift`.

## Updating the raw captures

1. Boot the **iPhone 17 Pro Max** simulator, delete the app, install a fresh build, and set the status bar:
   `xcrun simctl status_bar booted override --time 9:41 --batteryState discharging --batteryLevel 100 --wifiBars 3 --cellularBars 4`
2. Skip onboarding, load **Try it with sample food**, dismiss the reminders tip and mark the Greek yogurt as used,
   so Spinach (due today) is the "Use first" item.
3. Save full-resolution captures with `xcrun simctl io booted screenshot appstore/raw/<name>.png`:
   `fridge`, `item-detail` (Spinach), `recipes`, `recipe-detail` (top recipe), `add-food` (scan sheet),
   `onboarding-scan` (Settings → How Zero Bite works → page 2).
4. If a screen's layout changed, update the `popOut` crop rectangles in `design/Marketing.swift`.

The live camera option doesn't exist on the simulator, so the Add food capture shows only the photo and
manual options; on a device "Scan with the camera" appears first.
