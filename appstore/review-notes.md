# App Review reply (Guideline 2.1 – Information Needed)

Paste the block below as the reply in App Store Connect **and** into
App Review Information → Notes (the Notes field takes 4,000 characters max; this block fits).
Attach the screen recording to the reply.

---

Hello, and thank you for reviewing Zero Bite. Here is the information you asked for.

1. SCREEN RECORDING
Attached: a recording made on an iPhone 13 Pro running iOS [VERSION]. It starts from launching the app on the Home Screen and shows onboarding, adding food by scanning a label and barcode, the fridge list, an expiry reminder, recipes, food recall alerts and Settings.
Zero Bite has no account registration, login, user-generated content shared with others, or paid content or In-App Purchases, so none of those flows exist in the app.

2. PURPOSE AND AUDIENCE
Zero Bite helps households use the food they've already bought before it goes off. It's for anyone who shops and cooks at home, and is aimed in particular at people in the UK.
Problem: food gets forgotten at the back of the fridge and thrown away after its date. Zero Bite keeps a list of the food you have, sorts it by how soon it needs eating, reminds you before the date, suggests recipes that use what's about to expire, and warns you if something you've stored is the subject of a UK food recall or allergy alert.

3. SETUP AND MAIN FEATURES
No login, account or sample file is needed. Everything works offline except the barcode lookup and recall alerts.
- Launch the app and go through the short onboarding. Choose whether to share usage statistics and crash reports (optional, off unless you agree), then allow notifications to get expiry reminders (optional).
- Fridge tab: tap "Try it with sample food" to fill the list instantly, or add your own food. Sample food can also be added from Settings → Add sample food.
- Add food: tap +, then "Scan with the camera" and point it at a best-before/use-by date and the barcode on any packaged food. The date is read on the device and the barcode fills in the product name. Choosing a photo or entering an item by hand also work.
- The item to use first has Used it, Freeze and Recipes buttons. Tap any item for its details.
- Recipes tab: recipes ranked by what's in the fridge, with expiring food counted first.
- Food alerts (Settings → Check UK food recalls, then Food alerts): recent Food Standards Agency recalls and allergy alerts, with any that may match your stored food highlighted. Add allergies in Settings to see relevant allergy alerts.
- Settings → Send a test notification shows what a reminder looks like without waiting for a date.
- Settings → Privacy turns sharing of usage statistics and crash reports on or off.
- Settings → Delete everything removes all data stored by the app.

4. EXTERNAL SERVICES
- Open Food Facts (world.openfoodfacts.org) public API: only the scanned barcode number is sent, to look up the product name and brand. No key or account.
- UK Food Standards Agency Food Alerts API (data.food.gov.uk): the app downloads the public list of recent alerts. Nothing about the user is sent. Data is used under the Open Government Licence v3.0.
- Google Firebase Analytics and Crashlytics: usage statistics and crash reports, only if the user agrees in onboarding or in Settings → Privacy. Nothing is sent before that. No IDFA, no tracking, and no food, notes, allergies or photos are included.
- On-device Apple frameworks: Vision/VisionKit (labels, barcodes), local notifications, SwiftData.
No server of our own, authentication, payments, ads or AI services. The user's food data stays on the device.

5. REGIONAL DIFFERENCES
The app works the same in every region. The one UK-specific feature is food recall alerts: they come from the UK Food Standards Agency, so they cover products sold in the UK and are labelled "UK food recalls" in the app. Users outside the UK can switch the check off in Settings; all other features are unaffected. Barcode lookup works for products worldwide, with best coverage where Open Food Facts has data. The app is in English.

6. REGULATED INDUSTRY / THIRD-PARTY MATERIAL
Zero Bite doesn't operate in a regulated industry and gives no medical or food-safety advice of its own. Recall and allergy information is reproduced from the Food Standards Agency's public data under the Open Government Licence v3.0, which permits reuse with attribution. The app attributes it, says it isn't affiliated with the FSA, and links to the official notice. Product names come from Open Food Facts (Open Database Licence). The recipes bundled with the app are our own.

Thank you,
Pat Dynek

---

## Screen recording shot list

Record on the iPhone with Control Center → Screen Recording (turn the mic off). Update iOS first
(Apple asks for the latest OS). Aim for 2–3 minutes. Before you start: delete the app and install the
build you submitted (TestFlight or the same archive), and have a packaged food with a clear date and barcode to hand.

1. Start on the Home Screen and tap the Zero Bite icon (the recording must begin with the launch).
2. Go through onboarding; show the usage data choice, then allow notifications when asked.
3. Fridge tab, empty state → tap **+** → **Scan with the camera**, scan the date and barcode on the real product,
   show the name filling in on the review screen, save.
4. Tap **Try it with sample food** (or Settings → Add sample food) so the list has several items; show the "use first" item and the sorting.
5. On the "use first" card tap **Freeze**, then **Used it** on the next one; open an item to show its details.
6. **Recipes** tab → open the top recipe and show what you have / what's missing.
7. **Settings** → turn on **Check UK food recalls** → open **Food alerts**, open one alert. Add an allergy.
8. Settings → **Send a test notification**, lock or go Home briefly so the notification appears, tap it to return.
9. Settings → show the **Privacy** toggle, then **Delete everything** to show the user can remove their data.
