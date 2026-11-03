# Google Play Console: Data safety answers

From version 1.1 the Android app can send usage statistics and crash reports through Google
Firebase Analytics and Crashlytics. Collection is off until the user chooses **Share usage data**
in onboarding or turns on Settings → Privacy. The advertising ID permission and the Privacy
Sandbox ad APIs are removed from the manifest, and ad personalisation is off.

## Overview questions

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | Yes |
| Is all of the user data collected by your app encrypted in transit? | Yes (Firebase uses TLS) |
| Do you provide a way for users to request that their data is deleted? | Yes: email support@mazesys.com (the privacy policy says so) |

## Data types

For every type below: **Collected: yes. Shared: no** (Google processes it for us as a service
provider, which Play doesn't count as sharing). **Processed ephemerally: no. Required or
optional: optional** (users can choose).

| Category → type | Source | Purposes |
|---|---|---|
| Location → Approximate location | Region Google derives from a masked IP | Analytics |
| App activity → App interactions | Screen views, sessions, app opens | Analytics |
| App info and performance → Crash logs | Crashlytics stack traces | App functionality, Analytics |
| App info and performance → Diagnostics | Device model, OS version, app version, language | App functionality, Analytics |
| Device or other IDs → Device or other IDs | Firebase app-instance ID and Crashlytics installation ID | Analytics |

Not collected: personal info, financial info, health and fitness, messages, photos and videos,
audio, files and docs, calendar, contacts, web browsing, precise location. The food list, notes,
allergies and settings stay on the device. Barcode lookups (Open Food Facts) send only the barcode
number and aren't stored by us; recall alerts are a download of the FSA's public feed.

Re-check this page if you add custom analytics events, user IDs, Crashlytics custom keys or logs,
accounts or any server of your own.

**Privacy policy URL:** https://mazesys.com/zerobite/privacy-policy.html
