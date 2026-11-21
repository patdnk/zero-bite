# Usage data consent (iOS and Android)

Zero Bite 1.1 adds Firebase Analytics and Crashlytics. Nothing is sent until the user says yes,
which keeps the privacy policy's promise ("we'll ask for your permission first") and suits EU
(Polish) users. Both platforms behave identically.

## Behaviour

- New setting **shareUsageData**, default `false`. Existing installs also start at `false`; they
  can turn it on in Settings (they won't see onboarding again).
- **Onboarding (first run only):** a new page **SHARE** between COOK and REMINDERS.
  - Primary "Share usage data" → `shareUsageData = true`, go to REMINDERS.
  - Secondary "Don't share" (styled like the reminders page's "Not now") → `false`, go to REMINDERS.
  - "Skip" on the explainer pages now jumps to SHARE (the first question), not REMINDERS.
  - Replay from Settings has no SHARE page (as it has no REMINDERS page).
- **Settings:** a new **Privacy** section, just before **Data**, with the toggle "Share usage and
  crash data" and the footer below. Changing it takes effect straight away.
- **Collection:** Firebase isn't started (no `FirebaseApp.configure()` / no `FirebaseInitProvider`)
  until `shareUsageData` is true, so nothing (not even an installation ID) reaches Google before
  consent. Analytics and Crashlytics collection also default to off in the manifest / Info.plist.
  At launch, and whenever the setting changes, the app sets both to `shareUsageData`. Turning it
  off also deletes unsent crash reports. Debug builds never collect, whatever the setting.
  Builds without the Firebase config file (gitignored) must no-op safely.
- No custom analytics events. Food, notes, allergies and photos are never sent.

## Copy

| Key idea | en-GB | pl |
|---|---|---|
| Page title | Help us improve Zero Bite | Pomóż nam ulepszać Zero Bite |
| Page body | Share usage statistics and crash reports with us, sent through Google Firebase. They never include your food, notes, allergies or photos. You can change this at any time in Settings. | Udostępniaj nam statystyki użycia i raporty o awariach, wysyłane przez Google Firebase. Nigdy nie zawierają twojego jedzenia, notatek, alergii ani zdjęć. Możesz to zmienić w każdej chwili w Ustawieniach. |
| Primary button | Share usage data | Udostępniaj dane |
| Secondary button | Don't share | Nie udostępniaj |
| Settings section | Privacy | Prywatność |
| Toggle | Share usage and crash data | Udostępniaj statystyki i raporty o awariach |
| Footer | Usage statistics and crash reports help us fix problems and improve the app. They're sent through Google Firebase and never include your food, notes, allergies or photos. | Statystyki użycia i raporty o awariach pomagają nam naprawiać błędy i ulepszać aplikację. Są wysyłane przez Google Firebase i nigdy nie zawierają twojego jedzenia, notatek, alergii ani zdjęć. |

Match the existing Polish terminology (e.g. what the app calls Settings); if a term differs,
change it on both platforms so they stay identical.
