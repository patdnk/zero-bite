# App Store Connect: App Privacy answers

**Data collection: "Yes, we collect data from this app."** (from version 1.1)

Since 1.1 the app can send usage statistics and crash reports through Google Firebase Analytics
(the `FirebaseAnalyticsCore` package, without IDFA support) and Firebase Crashlytics. Collection
is off until the user chooses **Share usage data** in onboarding or turns on Settings → Privacy.
Apple's "optional disclosure" exemption doesn't apply (the collection isn't infrequent), so it
has to be declared even though it's opt-in.

Answer each data type below with **Not linked to the user's identity** (there are no accounts or
user IDs) and **Not used for tracking**.

| Data type (App Store Connect) | Source | Purposes |
|---|---|---|
| Identifiers → Device ID | Firebase app-instance ID and Crashlytics installation ID | Analytics |
| Usage Data → Product Interaction | Screen views, sessions, app opens | Analytics |
| Location → Coarse Location | Approximate region Google derives from a masked IP | Analytics |
| Diagnostics → Crash Data | Crashlytics stack traces and app state | App Functionality, Analytics |
| Diagnostics → Other Diagnostic Data | Device model, OS version, app version, language | App Functionality, Analytics |

Everything else stays on the device or isn't collected:

| What happens | Leaves the device? | Collected? |
|---|---|---|
| Food list, dates, notes, settings, allergies | No (on-device SwiftData / UserDefaults) | No |
| Label photos (camera or photo picker) | No (Vision runs on device, images aren't saved) | No |
| Barcode lookup | The barcode number only, to Open Food Facts, to answer that request | No: not linked to the user, not stored by us |
| Food recall alerts | Download of the public alert lists of the UK FSA and/or Poland's GIS (gov.pl), as chosen in Settings; nothing about the user is sent | No |
| Notifications | Local only | No |

There are no accounts, ads or tracking. The IDFA isn't used (AdSupport and Google's identity
support aren't linked, ad personalisation is off), so **App Tracking Transparency isn't needed**.

Re-check this page if you add custom analytics events, user IDs, Crashlytics custom keys or logs,
iCloud sync, accounts or any server of your own.

## URLs

The pages are published from the Astro site in `~/Repositories/non-work/mazesys.com` (GitHub Pages, custom domain).
`docs/` in this repo holds the original drafts.

| Field | URL | Required |
|---|---|---|
| Support URL | https://mazesys.com/zerobite/support.html | Yes |
| Privacy Policy URL | https://mazesys.com/zerobite/privacy-policy.html | Yes |
| Marketing URL | https://mazesys.com/zerobite/ | No |
