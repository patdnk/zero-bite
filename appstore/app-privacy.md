# App Store Connect: App Privacy answers

**Data collection: "No, we do not collect data from this app."**

Why that's accurate under Apple's definition (data is "collected" when it's sent off the device in a way
you or your partners can access for longer than needed to service the request):

| What happens | Leaves the device? | Collected? |
|---|---|---|
| Food list, dates, notes, settings, allergies | No (on-device SwiftData / UserDefaults) | No |
| Label photos (camera or photo picker) | No (Vision runs on device, images aren't saved) | No |
| Barcode lookup | The barcode number only, to Open Food Facts, to answer that request | No: not linked to the user, not stored by us |
| Food recall alerts | Download of the FSA's public feed; nothing about the user is sent | No |
| Notifications | Local only | No |

There are no accounts, analytics, crash reporting SDKs, ads or tracking, so **App Tracking Transparency isn't needed**.

Re-check this page if you add analytics, crash reporting, iCloud sync, accounts or any server of your own.

## URLs

The pages are published from the Astro site in `~/Repositories/non-work/mazesys.com` (GitHub Pages, custom domain).
`docs/` in this repo holds the original drafts.

| Field | URL | Required |
|---|---|---|
| Support URL | https://mazesys.com/zerobite/support.html | Yes |
| Privacy Policy URL | https://mazesys.com/zerobite/privacy-policy.html | Yes |
| Marketing URL | https://mazesys.com/zerobite/ | No |
