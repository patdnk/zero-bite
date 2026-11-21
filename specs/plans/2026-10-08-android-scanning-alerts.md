# Zero Bite Android — Plan 4: Label Scanning and Food Alerts

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the scan placeholder with the real add-food flow and add FSA food alerts, matching the iOS app:
- Add food: choose a source (camera, photo or typing), read the label on the device, review what was read (with an Open Food Facts lookup), then fill in the item form.
- Food alerts: recall and allergy alerts from the Food Standards Agency, with matches against the fridge shown as a banner, card flags and item-detail cards, recall notifications, a Food alerts screen, and the Settings "Food alerts" row.

**Architecture:**
- Builds on `rename-zero-bite` after Plans 1–3.
- **Parsing** stays in `:core`: `LabelScanner.analyse`, `RecallMatcher` and the FSA and Open Food Facts clients. `:core` gains `LiveScanSession`, which accumulates what the camera has read.
- **Scan sheet:** a `ScanCoordinator` owns a Navigation 3 back stack (choose → live → review → form). It is held by the sheet's `ScanFlowViewModel`, so the stack survives rotation and is thrown away when the sheet closes.
- **Image reading:** ML Kit (bundled models) sits behind a `LabelReading` interface, so every view model and screen test runs on the JVM without it.
- **Recalls:** a `RecallStore` exposes a `StateFlow<RecallState>`. It caches the fetched alerts in a JSON file, keeps the last-checked time and the dismissed and notified sets in a `recalls` DataStore, and refreshes when the cache is more than six hours old.
- **Recall sync:** an app-scoped `RecallSync`, the counterpart of `ReminderSync`, posts one notification per new match on a separate `recalls` channel.
- **Settings tab:** gains a back stack for the Food alerts list and the alert detail screen.

**Tech Stack:** As Plan 3, plus:
- CameraX 1.6.2 (`camera-camera2`, `camera-lifecycle`, `camera-view`)
- ML Kit Text Recognition v2 Latin, bundled (`com.google.mlkit:text-recognition:16.0.1`)
- ML Kit Barcode Scanning, bundled (`com.google.mlkit:barcode-scanning:17.3.0`)
- Ktor OkHttp engine 3.6.0
- Activity Result `PickVisualMedia` (the system photo picker)
- Material 3 `PullToRefreshBox`

**Spec:** [`specs/2026-10-08-android-app-design.md`](../2026-10-08-android-app-design.md), sections 5 and 6 (Scanning, Network, Storage → Recalls, Reminders → recall channel). Builds on [`2026-10-08-android-features.md`](2026-10-08-android-features.md).

## Global Constraints

- **Earlier plans still apply:**
  - package `com.mazesys.zerobite`, `minSdk` 26, `targetSdk` 36, `compileSdk` 37;
  - the Robolectric JVM flags, the `androidx.compose.ui.test.junit4.v2` rule, and `@Config(qualifiers = "w411dp-h891dp-xxhdpi")` for screen tests;
  - theme tokens only (colours, `Radius`, `Spacing`, `ZeroBiteTheme.type`), with no visible Material defaults;
  - `AppClock` for every "now" or "today";
  - test view models built `by lazy` (Plan 3 ruling);
  - `hasText(...) and isHeading()` for headers that repeat on chips;
  - Gradle run from `android/` with `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- **Platforms:** iOS app code is not changed. `:core` stays free of `android.`, `java.` and `javax.` imports (`./gradlew :core:checkPortable`).
- **Copy:** text is taken verbatim from these iOS files:
  - `ios/ZeroBite/Screens/Scan/*`
  - `ios/ZeroBite/Screens/Alerts/*`
  - `ios/ZeroBite/UI/RecallViews.swift`
  - `ios/ZeroBite/Services/Alerts/RecallStore.swift`
  - `ios/ZeroBite/Services/AppDependencies.swift`
  - `ios/ZeroBite/Services/Notifications/ExpiryNotificationService.swift` (`postRecallNotice`)
  - `ios/ZeroBite/Screens/Settings/SettingsView.swift`
  
  Adaptations are listed in the task that makes them.
- **Scanning:** bundled ML Kit models only (no Play Services model download). Barcode formats are EAN-13, EAN-8, UPC-A, UPC-E, Code 128 and QR. ML Kit clients are created lazily, on first use: `AppLaunchTest` and `DeepLinkTest` build the live `AppDependencies` under Robolectric, where ML Kit cannot start.
- **Camera:** the `CAMERA` permission is requested only when the live scanner opens. The rationale copy is iOS's `NSCameraUsageDescription`, "Zero Bite uses the camera to read best-before dates and barcodes on food labels." The camera is `required="false"`: the photo and typing paths always work.
- **Network:** network calls never throw out of `appScope` or `viewModelScope`; failures degrade quietly (spec §5). `CancellationException` is always rethrown, never swallowed.
- **FSA:**
  - Alerts are fetched for the last 120 days (`min-created`), refreshed when older than 6 hours, and cached in `filesDir/fsa-food-alerts.json`.
  - Marks use the iOS key names: `recalls.lastChecked`, `recalls.dismissedMatches`, `recalls.notifiedMatches`.
  - At most 3 recall notifications are posted per sync.
  - Recall notifications go on channel `recalls` ("Food recalls"); the tag is `zerobite.recall.<matchId>`.
- **Out of scope here:** launcher icons, splash mark, signing and Firebase (Plan 5), and background recall checks (spec "Later").

## Review Focus

1. **The fridge or the alerts change after a match was notified, or the app restarts:** each match is notified once, ever. A match the user dismissed with "Not my batch" never comes back in the banner, the flags or the detail screen.
   - Pinned by `each match is notified once, even across restarts` (Task 6) and `dismissed matches disappear everywhere` (Task 7).
2. **No network, or the FSA returns an error:** the cached alerts stay, a "Couldn't reach the Food Standards Agency…" line shows, and the six-hour throttle is not reset, so the next foreground tries again.
   - Pinned by `a failed refresh keeps the cache and reports it` (Task 5).
3. **Recall checks turned off in Settings:** no banner, no flags, no detail cards, no notifications. Turning them back on fetches immediately.
   - Pinned by `recall checks off hides every match` (Task 7) and `turning checks back on refreshes now` (Task 6).
4. **A photo with no readable text, a non-image file, or a barcode lookup that fails:**
   - nothing found → "No text or barcode was found…";
   - unreadable → "That file doesn't look like a photo.";
   - a failed lookup keeps the OCR name and the review screen usable.
   
   Pinned by `empty photo explains what to try` and `unreadable photo says so` (Task 2), and `a failed lookup keeps the scanned name` (Task 2).
5. **Camera permission denied:** the live scanner explains why it needs the camera, links to app settings, and offers the photo path. It never shows a black preview with no way out.
   - Pinned by `denied camera offers settings and a photo` (Task 4).

---

## File Structure

```
android/
├── gradle/libs.versions.toml                + camerax, mlkit, ktor-client-okhttp
├── core/src/main/kotlin/…/core/scanning/LiveScanSession.kt     accumulates live camera reads
└── app/src/main/
    ├── AndroidManifest.xml                  + INTERNET, CAMERA, camera feature (optional)
    └── kotlin/com/mazesys/zerobite/
        ├── AppDependencies.kt               + labels, productLookup, recalls, recallNotifier, recallSync
        ├── ZeroBiteApplication.kt           starts RecallSync
        ├── MainActivity.kt                  refreshes recalls on resume
        ├── scanning/                        RecognizedText, TextLines, LabelReading, MlKitLabelReader
        ├── recalls/                         RecallState, RecallStore, RecallPersistence (file + DataStore),
        │                                    RecallSync, RecallNotifier
        ├── reminders/Notifications.kt       + `recalls` channel, per-channel canPost
        ├── coordination/                    + ScanCoordinator; SettingsCoordinator gains a back stack
        ├── ui/components/                   + LoadingOverlay, RecallViews, FlowTags, LinkRow
        ├── ui/format/DateText.kt            + dayMonth, relative
        └── screens/
            ├── scan/                        ScanFlowViewModel/Screen, ScanReviewViewModel/Screen,
            │                                LiveScanViewModel, LiveScannerScreen, ScanSheet
            ├── alerts/                      FoodAlertsViewModel/Screen, FoodAlertDetailScreen
            ├── settings/SettingsTab.kt      Settings back stack (root → alerts → alert)
            ├── inventory/                   recall banner + flags (Fridge), recall cards (detail)
            └── root/                        SheetHost uses ScanSheet; ScanPlaceholder deleted
```

---

### Task 1: Reading labels: live-scan accumulation, line order and ML Kit

**Files:**
- Create: `android/core/src/main/kotlin/com/mazesys/zerobite/core/scanning/LiveScanSession.kt`
- Test: `android/core/src/test/kotlin/com/mazesys/zerobite/core/scanning/LiveScanSessionTest.kt`
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/scanning/LabelReading.kt`
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/scanning/MlKitLabelReader.kt`
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/scanning/TextLinesTest.kt`
- Modify: `android/gradle/libs.versions.toml`, `android/app/build.gradle.kts`, `android/app/src/main/AndroidManifest.xml`, `android/app/src/main/kotlin/com/mazesys/zerobite/AppDependencies.kt`, `android/app/src/test/kotlin/com/mazesys/zerobite/testing/Fakes.kt`

**Interfaces:**
- Consumes: `LabelScanner.analyse(lines, barcode, today): ScanResult` and `ScanResult` from `:core`.
- Produces:
  - `data class LiveScanSession(val lines: List<String> = emptyList(), val barcode: String? = null)` with `fun adding(newLines: List<String>, barcodes: List<String>): LiveScanSession` and `fun result(today: LocalDate): ScanResult`
  - `data class RecognizedText(val lines: List<String>, val barcodes: List<String>)`
  - `data class PositionedLine(val text: String, val centerY: Int?)`, `object TextLines { fun ordered(lines: List<PositionedLine>): List<String> }`
  - `fun interface PhotoScanner { suspend fun scan(photo: Uri, today: LocalDate): ScanResult }`
  - `class UnreadablePhoto : Exception`
  - `interface LabelReading : PhotoScanner { val supportsLiveScanning: Boolean; fun analyzer(onFrame: (RecognizedText) -> Unit): ImageAnalysis.Analyzer }`
  - `class MlKitLabelReader(context: Context) : LabelReading`
  - `AppDependencies.labels: LabelReading`
  - `FakeLabels` in Fakes
  - `testDependencies(labels = …)`

- [ ] **Step 1: Write the failing core test**

`android/core/src/test/kotlin/com/mazesys/zerobite/core/scanning/LiveScanSessionTest.kt`:

```kotlin
package com.mazesys.zerobite.core.scanning

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class LiveScanSessionTest {

    private val today = LocalDate(2026, 9, 7)

    @Test
    fun `keeps each distinct line once, in the order first seen`() {
        val session = LiveScanSession()
            .adding(listOf("TESCO", "USE BY 12 SEP 2026"), emptyList())
            .adding(listOf("USE BY 12 SEP 2026", "SEMI SKIMMED MILK"), emptyList())
        assertEquals(listOf("TESCO", "USE BY 12 SEP 2026", "SEMI SKIMMED MILK"), session.lines)
    }

    @Test
    fun `trims lines and drops ones shorter than two characters`() {
        val session = LiveScanSession().adding(listOf("  MILK  ", "A", " ", ""), emptyList())
        assertEquals(listOf("MILK"), session.lines)
    }

    @Test
    fun `the first barcode seen wins`() {
        val session = LiveScanSession()
            .adding(emptyList(), emptyList())
            .adding(emptyList(), listOf("5000119000000", "123"))
            .adding(emptyList(), listOf("999"))
        assertEquals("5000119000000", session.barcode)
    }

    @Test
    fun `adding nothing new returns an equal session`() {
        val session = LiveScanSession().adding(listOf("MILK"), listOf("5000119000000"))
        assertEquals(session, session.adding(listOf("MILK"), listOf("111")))
    }

    @Test
    fun `result parses dates like the photo path`() {
        val result = LiveScanSession().adding(listOf("SEMI SKIMMED MILK", "USE BY 12 SEP 2026"), emptyList()).result(today)
        assertEquals(LocalDate(2026, 9, 12), result.bestCandidate?.date)
        assertNull(result.barcode)
    }

    @Test
    fun `an empty session has an empty result`() {
        assertSame(true, LiveScanSession().result(today).isEmpty)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :core:test --tests '*LiveScanSessionTest*'`
Expected: compilation FAILS with "Unresolved reference 'LiveScanSession'".

- [ ] **Step 3: Implement `LiveScanSession`**

`android/core/src/main/kotlin/com/mazesys/zerobite/core/scanning/LiveScanSession.kt`:

```kotlin
package com.mazesys.zerobite.core.scanning

import com.mazesys.zerobite.core.model.ScanResult
import kotlinx.datetime.LocalDate

/**
 * What the live camera has read so far: every distinct text line (first-seen order) and the
 * first barcode. Immutable, so a frame callback can fold into it atomically. Mirrors the
 * accumulation in iOS LiveScannerView.
 */
data class LiveScanSession(
    val lines: List<String> = emptyList(),
    val barcode: String? = null,
) {
    fun adding(newLines: List<String>, barcodes: List<String>): LiveScanSession {
        val seen = lines.toMutableSet()
        val merged = lines.toMutableList()
        for (raw in newLines) {
            val line = raw.trim()
            if (line.length >= 2 && seen.add(line)) merged += line
        }
        return LiveScanSession(merged, barcode ?: barcodes.firstOrNull())
    }

    fun result(today: LocalDate): ScanResult = LabelScanner.analyse(lines, barcode, today)
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `cd android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :core:test --tests '*LiveScanSessionTest*' :core:checkPortable`
Expected: PASS (6 tests); checkPortable passes.

- [ ] **Step 5: Write the failing line-order test**

`android/app/src/test/kotlin/com/mazesys/zerobite/scanning/TextLinesTest.kt`:

```kotlin
package com.mazesys.zerobite.scanning

import org.junit.Test
import kotlin.test.assertEquals

class TextLinesTest {

    @Test
    fun `lines read top to bottom, like the iOS Vision sort`() {
        val ordered = TextLines.ordered(
            listOf(PositionedLine("USE BY 12 SEP", 300), PositionedLine("TESCO", 40), PositionedLine("MILK", 120)),
        )
        assertEquals(listOf("TESCO", "MILK", "USE BY 12 SEP"), ordered)
    }

    @Test
    fun `lines without a position go last, in reading order`() {
        val ordered = TextLines.ordered(
            listOf(PositionedLine("B", null), PositionedLine("TOP", 10), PositionedLine("C", null)),
        )
        assertEquals(listOf("TOP", "B", "C"), ordered)
    }

    @Test
    fun `blank lines are dropped and the rest trimmed`() {
        assertEquals(listOf("MILK"), TextLines.ordered(listOf(PositionedLine("  MILK ", 5), PositionedLine("   ", 6))))
    }
}
```

- [ ] **Step 6: Run it to verify it fails**

Run: `cd android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests '*TextLinesTest*'`
Expected: compilation FAILS with "Unresolved reference 'TextLines'".

- [ ] **Step 7: Add the dependencies and manifest entries**

In `android/gradle/libs.versions.toml`, add under `[versions]`:

```toml
camerax = "1.6.2"
mlkit-text = "16.0.1"
mlkit-barcode = "17.3.0"
```

and under `[libraries]`:

```toml
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
camera-camera2 = { module = "androidx.camera:camera-camera2", version.ref = "camerax" }
camera-lifecycle = { module = "androidx.camera:camera-lifecycle", version.ref = "camerax" }
camera-view = { module = "androidx.camera:camera-view", version.ref = "camerax" }
mlkit-text-recognition = { module = "com.google.mlkit:text-recognition", version.ref = "mlkit-text" }
mlkit-barcode-scanning = { module = "com.google.mlkit:barcode-scanning", version.ref = "mlkit-barcode" }
```

In `android/app/build.gradle.kts` `dependencies { … }`, after `implementation(libs.kotlinx.serialization.json)`:

```kotlin
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.ktor.client.okhttp)
```

(`ktor-client-okhttp` is used in Task 2. It is added here so the dependency change lands in one commit.)

In `android/app/src/main/AndroidManifest.xml`, after the `RECEIVE_BOOT_COMPLETED` line:

```xml
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.CAMERA" />
    <!-- Live scanning is optional: photos and typing work without a camera. -->
    <uses-feature android:name="android.hardware.camera.any" android:required="false" />
```

- [ ] **Step 8: Implement `LabelReading` and `TextLines`**

`android/app/src/main/kotlin/com/mazesys/zerobite/scanning/LabelReading.kt`:

```kotlin
package com.mazesys.zerobite.scanning

import android.net.Uri
import androidx.camera.core.ImageAnalysis
import com.mazesys.zerobite.core.model.ScanResult
import kotlinx.datetime.LocalDate

/** Text lines (top to bottom) and barcode payloads read from one image or camera frame. */
data class RecognizedText(val lines: List<String>, val barcodes: List<String>)

/** One recognised line and the vertical centre of its box, if the recogniser gave one. */
data class PositionedLine(val text: String, val centerY: Int?)

object TextLines {
    /** Top-to-bottom reading order, as the iOS Vision path sorts by box midpoint. */
    fun ordered(lines: List<PositionedLine>): List<String> =
        lines.withIndex()
            .sortedWith(compareBy<IndexedValue<PositionedLine>>({ it.value.centerY == null }, { it.value.centerY ?: 0 }, { it.index }))
            .map { it.value.text.trim() }
            .filter { it.isNotEmpty() }
}

/** The chosen file couldn't be decoded as an image. */
class UnreadablePhoto : Exception("That file doesn't look like a photo.")

/** Reads a still photo of a label into a ScanResult (text + barcode + parsed dates). */
fun interface PhotoScanner {
    suspend fun scan(photo: Uri, today: LocalDate): ScanResult
}

/** Label reading for both the photo path and the live camera. */
interface LabelReading : PhotoScanner {
    /** False on devices without a camera; the scan sheet then hides the live option. */
    val supportsLiveScanning: Boolean

    /** A CameraX analyzer that reports what each frame contains. */
    fun analyzer(onFrame: (RecognizedText) -> Unit): ImageAnalysis.Analyzer
}
```

- [ ] **Step 9: Implement `MlKitLabelReader`**

`android/app/src/main/kotlin/com/mazesys/zerobite/scanning/MlKitLabelReader.kt`:

```kotlin
package com.mazesys.zerobite.scanning

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mazesys.zerobite.core.model.ScanResult
import com.mazesys.zerobite.core.scanning.LabelScanner
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.LocalDate
import java.io.IOException
import kotlin.coroutines.resume

/**
 * On-device OCR and barcode reading with ML Kit's bundled models (no download, works offline).
 * Clients are created on first use: building AppDependencies must not start ML Kit, because the
 * Robolectric launch tests build the live dependencies.
 */
class MlKitLabelReader(private val context: Context) : LabelReading {

    private val text: TextRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private val barcodes: BarcodeScanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                // iOS reads EAN-13 (which covers UPC-A), EAN-8, UPC-E, Code 128 and QR.
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E, Barcode.FORMAT_CODE_128, Barcode.FORMAT_QR_CODE,
                )
                .build(),
        )
    }

    override val supportsLiveScanning: Boolean
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    /** Reads text and barcodes; a failed barcode pass just means no barcode. Null if OCR failed. */
    private fun read(image: InputImage, onResult: (RecognizedText?) -> Unit) {
        val textTask = text.process(image)
        val codeTask = barcodes.process(image)
        Tasks.whenAllComplete(textTask, codeTask).addOnCompleteListener {
            if (!textTask.isSuccessful) {
                onResult(null)
                return@addOnCompleteListener
            }
            val lines = textTask.result.textBlocks.flatMap { block ->
                block.lines.map { PositionedLine(it.text, it.boundingBox?.centerY()) }
            }
            val codes = if (codeTask.isSuccessful) codeTask.result.mapNotNull { it.rawValue } else emptyList()
            onResult(RecognizedText(TextLines.ordered(lines), codes))
        }
    }

    override suspend fun scan(photo: Uri, today: LocalDate): ScanResult {
        val image = try {
            InputImage.fromFilePath(context, photo)
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw UnreadablePhoto()
        }
        val recognized = suspendCancellableCoroutine { continuation -> read(image) { continuation.resume(it) } }
            ?: throw IOException("Couldn't read the text in that photo.")
        return LabelScanner.analyse(recognized.lines, recognized.barcodes.firstOrNull(), today)
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyzer(onFrame: (RecognizedText) -> Unit) = ImageAnalysis.Analyzer { proxy ->
        val media = proxy.image
        if (media == null) {
            proxy.close()
            return@Analyzer
        }
        // The frame stays open until ML Kit is done, so CameraX drops frames instead of queueing them.
        read(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)) { result ->
            if (result != null) onFrame(result)
            proxy.close()
        }
    }
}
```

- [ ] **Step 10: Wire `labels` into `AppDependencies` and the test fakes**

In `AppDependencies.kt`:
- Add the import `com.mazesys.zerobite.scanning.LabelReading` and `com.mazesys.zerobite.scanning.MlKitLabelReader`.
- Add the constructor parameter `val labels: LabelReading,` after `val notifier: Notifier,`.
- In `live(context)`, add `labels = MlKitLabelReader(app),` after `notifier = AndroidNotifier(app),`.

In `android/app/src/test/kotlin/com/mazesys/zerobite/testing/Fakes.kt`:
- Add the imports `android.net.Uri`, `androidx.camera.core.ImageAnalysis`, `com.mazesys.zerobite.core.model.ScanResult`, `com.mazesys.zerobite.scanning.LabelReading` and `com.mazesys.zerobite.scanning.RecognizedText`.
- Append:

```kotlin
/** Returns `result` (or throws `failure`) for every photo; counts calls. */
class FakeLabels(
    var result: ScanResult = ScanResult(),
    var failure: Exception? = null,
    override val supportsLiveScanning: Boolean = true,
) : LabelReading {
    var scans = 0
    override suspend fun scan(photo: Uri, today: kotlinx.datetime.LocalDate): ScanResult {
        scans++
        failure?.let { throw it }
        return result
    }
    override fun analyzer(onFrame: (RecognizedText) -> Unit) = ImageAnalysis.Analyzer { it.close() }
}
```

and replace `testDependencies` with:

```kotlin
fun testDependencies(
    inventory: FakeInventoryRepository = FakeInventoryRepository(),
    settings: FakeSettings = FakeSettings(SettingsSnapshot(hasCompletedWelcome = true, dismissedNotificationTip = true)),
    recipes: FakeRecipes = FakeRecipes(),
    clock: AppClock = TestClock.fixed(),
    ids: IdGenerator = SequentialIds(),
    access: FakeAccess = FakeAccess(),
    scheduler: FakeScheduler = FakeScheduler(),
    notifier: FakeNotifier = FakeNotifier(),
    labels: FakeLabels = FakeLabels(),
) = AppDependencies(
    clock = clock, settings = settings, inventory = inventory, recipes = recipes, ids = ids,
    appScope = CoroutineScope(Dispatchers.Unconfined), notifications = access, scheduler = scheduler,
    notifier = notifier, labels = labels,
)
```

- [ ] **Step 11: Run the tests to verify they pass**

Run: `cd android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; `TextLinesTest` passes (3 tests) and every earlier test still passes, including `AppLaunchTest` (proves the lazy ML Kit clients).

- [ ] **Step 12: Commit**

```bash
git add android/gradle/libs.versions.toml android/app/build.gradle.kts android/app/src/main/AndroidManifest.xml \
  android/core/src/main/kotlin/com/mazesys/zerobite/core/scanning/LiveScanSession.kt \
  android/core/src/test/kotlin/com/mazesys/zerobite/core/scanning/LiveScanSessionTest.kt \
  android/app/src/main/kotlin/com/mazesys/zerobite/scanning android/app/src/test/kotlin/com/mazesys/zerobite/scanning \
  android/app/src/main/kotlin/com/mazesys/zerobite/AppDependencies.kt android/app/src/test/kotlin/com/mazesys/zerobite/testing/Fakes.kt
git commit -m "Read labels on the device with bundled ML Kit, and accumulate live camera reads"
```

---

### Task 2: Scan flow state: coordinator, photo processing, review and barcode lookup

**Files:**
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/coordination/ScanCoordinator.kt`
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/screens/scan/ScanFlowViewModel.kt`
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/screens/scan/ScanReviewViewModel.kt`
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/screens/scan/ScanFlowViewModelTest.kt`
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/screens/scan/ScanReviewViewModelTest.kt`
- Modify: `AppDependencies.kt` (+ `productLookup`, the shared `HttpClient`), `testing/Fakes.kt` (+ `FakeProductLookup`)

**Interfaces:**
- Consumes: `PhotoScanner`, `UnreadablePhoto`, `FakeLabels` (Task 1); `ProductLookup`, `ProductInfo`, `OpenFoodFactsClient(http)`, `ScanResult`, `DateCandidate`, `ItemDraft.new(today)`, `FoodCategory.defaultShelfLifeDays` (`:core`).
- Produces:
  - `sealed interface ScanRoute : NavKey { Choose; Live; Review(result: ScanResult); Form(draft: ItemDraft) }`
  - `class ScanCoordinator(onFinished: () -> Unit)` with `backStack: SnapshotStateList<ScanRoute>`, `showLiveScanner()`, `showReview(result)`, `showForm(draft)`, `pop()`, `finish()`
  - `sealed interface ScanPhase { Idle; Processing; Failed(message) }`
  - `class ScanFlowViewModel(coordinator, supportsLiveScanning, scanner: PhotoScanner, clock)` with `phase: StateFlow<ScanPhase>`, `startLiveScan()`, `enterManually()`, `liveScanFinished(result)`, `process(photo: Uri)`, `dismissError()`, `cancel()`
  - `data class ScanReviewUiState(…)`
  - `class ScanReviewViewModel(result, productLookup, clock, onContinue: (ItemDraft) -> Unit)` with `state`, `setName`, `setCategory`, `select(index)`, `clearSelection()`, `toggleLines()`, `makeDraft()`, `continueToForm()`
  - `AppDependencies.productLookup: ProductLookup`, `AppDependencies.http` (live only, shared with Task 5)
  - `FakeProductLookup`

- [ ] **Step 1: Write the failing flow test**

`android/app/src/test/kotlin/com/mazesys/zerobite/screens/scan/ScanFlowViewModelTest.kt`:

```kotlin
package com.mazesys.zerobite.screens.scan

import android.net.Uri
import com.mazesys.zerobite.coordination.ScanCoordinator
import com.mazesys.zerobite.coordination.ScanRoute
import com.mazesys.zerobite.core.model.ScanResult
import com.mazesys.zerobite.scanning.UnreadablePhoto
import com.mazesys.zerobite.testing.FakeLabels
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs

@RunWith(RobolectricTestRunner::class)
class ScanFlowViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private var finished = 0
    private val coordinator = ScanCoordinator(onFinished = { finished++ })
    private val labels = FakeLabels()
    private val vm by lazy { ScanFlowViewModel(coordinator, supportsLiveScanning = true, scanner = labels, clock = TestClock.fixed()) }
    private val photo = Uri.parse("content://media/picker/0/1")
    private val milk = ScanResult(lines = listOf("SEMI SKIMMED MILK"), barcode = "5000119000000")

    @Test
    fun `a readable photo goes to review`() {
        labels.result = milk
        vm.process(photo)
        assertEquals(ScanPhase.Idle, vm.phase.value)
        assertEquals(ScanRoute.Review(milk), coordinator.backStack.last())
    }

    @Test
    fun `empty photo explains what to try`() {
        vm.process(photo)
        assertEquals(ScanPhase.Failed("No text or barcode was found. Try a sharper, closer photo of the label."), vm.phase.value)
        assertEquals(listOf<ScanRoute>(ScanRoute.Choose), coordinator.backStack.toList())
    }

    @Test
    fun `unreadable photo says so`() {
        labels.failure = UnreadablePhoto()
        vm.process(photo)
        assertEquals(ScanPhase.Failed("That file doesn't look like a photo."), vm.phase.value)
    }

    @Test
    fun `a photo that can't be opened says so`() {
        labels.failure = IOException("boom")
        vm.process(photo)
        assertEquals(ScanPhase.Failed("Couldn't load that photo."), vm.phase.value)
        vm.dismissError()
        assertEquals(ScanPhase.Idle, vm.phase.value)
    }

    @Test
    fun `typing it in opens a fresh form, live scan opens the scanner`() {
        vm.enterManually()
        val form = assertIs<ScanRoute.Form>(coordinator.backStack.last())
        assertEquals(TestClock.today.plus(7, DateTimeUnit.DAY), form.draft.expiresOn)
        coordinator.pop()
        vm.startLiveScan()
        assertEquals(ScanRoute.Live, coordinator.backStack.last())
        vm.liveScanFinished(milk)
        assertEquals(ScanRoute.Review(milk), coordinator.backStack.last())
    }

    @Test
    fun `pop stops at the first screen, cancel closes the sheet`() {
        coordinator.pop()
        assertEquals(1, coordinator.backStack.size)
        vm.cancel()
        assertEquals(1, finished)
    }
}
```

- [ ] **Step 2: Write the failing review test**

`android/app/src/test/kotlin/com/mazesys/zerobite/screens/scan/ScanReviewViewModelTest.kt`:

```kotlin
package com.mazesys.zerobite.screens.scan

import com.mazesys.zerobite.core.model.FoodCategory
import com.mazesys.zerobite.core.model.ItemDraft
import com.mazesys.zerobite.core.model.ProductInfo
import com.mazesys.zerobite.core.scanning.LabelScanner
import com.mazesys.zerobite.testing.FakeProductLookup
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScanReviewViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val label = LabelScanner.analyse(
        lines = listOf("TESCO", "SEMI SKIMMED MILK", "USE BY 12 SEP 2026", "PACKED 01/09/2026"),
        barcode = "5000119000000",
        today = TestClock.today,
    )
    private val lookup = FakeProductLookup()
    private var continued: ItemDraft? = null

    private fun review(result: com.mazesys.zerobite.core.model.ScanResult = label) =
        ScanReviewViewModel(result, lookup, TestClock.fixed(), onContinue = { continued = it })

    @Test
    fun `starts from the guess and the most likely date`() {
        val vm = review()
        assertEquals(label.suggestedName ?: "", vm.state.value.name)
        assertEquals(0, vm.state.value.selectedIndex)
        assertEquals(LocalDate(2026, 9, 12), vm.state.value.selectedCandidate?.date)
    }

    @Test
    fun `a catalogue name replaces the guess and sets the category`() {
        lookup.info = ProductInfo("5000119000000", "Semi Skimmed Milk", "Tesco", FoodCategory.DAIRY)
        val vm = review()
        assertEquals("Semi Skimmed Milk", vm.state.value.name)
        assertEquals(FoodCategory.DAIRY, vm.state.value.category)
        assertEquals("Tesco", vm.state.value.product?.brand)
        assertFalse(vm.state.value.isLookingUp)
        assertEquals(listOf("5000119000000"), lookup.asked)
    }

    @Test
    fun `a typed name survives the lookup`() {
        lookup.hold = true
        val vm = review()
        vm.setName("My milk")
        assertTrue(vm.state.value.isLookingUp)
        lookup.info = ProductInfo("5000119000000", "Semi Skimmed Milk", "Tesco", FoodCategory.DAIRY)
        lookup.release()
        assertEquals("My milk", vm.state.value.name)
        assertEquals(FoodCategory.DAIRY, vm.state.value.category)
    }

    @Test
    fun `a failed lookup keeps the scanned name`() {
        lookup.failure = java.io.IOException("offline")
        val vm = review()
        assertEquals(label.suggestedName ?: "", vm.state.value.name)
        assertNull(vm.state.value.product)
        assertFalse(vm.state.value.isLookingUp)
    }

    @Test
    fun `no barcode means no lookup`() {
        review(label.copy(barcode = null))
        assertTrue(lookup.asked.isEmpty())
    }

    @Test
    fun `draft uses the chosen date, or the category's shelf life with none`() {
        val vm = review()
        vm.setName("  Milk  ")
        vm.setCategory(FoodCategory.DAIRY)
        val chosen = vm.makeDraft()
        assertEquals("Milk", chosen.name)
        assertEquals(LocalDate(2026, 9, 12), chosen.expiresOn)
        assertEquals("5000119000000", chosen.barcode)

        vm.clearSelection()
        assertEquals(TestClock.today.plus(FoodCategory.DAIRY.defaultShelfLifeDays, DateTimeUnit.DAY), vm.makeDraft().expiresOn)
    }

    @Test
    fun `continue needs a name`() {
        val vm = review()
        vm.setName("   ")
        assertFalse(vm.state.value.canContinue)
        vm.continueToForm()
        assertNull(continued)
        vm.setName("Milk")
        vm.continueToForm()
        assertEquals("Milk", continued?.name)
    }
}
```

- [ ] **Step 3: Add `FakeProductLookup` to Fakes**

Append to `testing/Fakes.kt` (add the imports `com.mazesys.zerobite.core.model.ProductInfo`, `com.mazesys.zerobite.core.products.ProductLookup` and `kotlinx.coroutines.CompletableDeferred`):

```kotlin
/** Answers `info` (or throws `failure`); with `hold`, waits until `release()`. */
class FakeProductLookup(var info: ProductInfo? = null, var failure: Exception? = null) : ProductLookup {
    val asked = mutableListOf<String>()
    var hold = false
    private val gate = CompletableDeferred<Unit>()
    fun release() = gate.complete(Unit)
    override suspend fun lookup(barcode: String): ProductInfo? {
        asked += barcode
        if (hold) gate.await()
        failure?.let { throw it }
        return info
    }
}
```

- [ ] **Step 4: Run them to verify they fail**

Run: `cd android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests '*ScanFlowViewModelTest*' --tests '*ScanReviewViewModelTest*'`
Expected: compilation FAILS with unresolved `ScanCoordinator`, `ScanRoute`, `ScanFlowViewModel`, `ScanPhase`, `ScanReviewViewModel`.

- [ ] **Step 5: Implement `ScanCoordinator`**

`android/app/src/main/kotlin/com/mazesys/zerobite/coordination/ScanCoordinator.kt`:

```kotlin
package com.mazesys.zerobite.coordination

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey
import com.mazesys.zerobite.core.model.ItemDraft
import com.mazesys.zerobite.core.model.ScanResult

sealed interface ScanRoute : NavKey {
    /** Pick a source: camera, photo or typing. */
    data object Choose : ScanRoute
    data object Live : ScanRoute
    data class Review(val result: ScanResult) : ScanRoute
    data class Form(val draft: ItemDraft) : ScanRoute
}

/**
 * Drives the add-food sheet: choose source → (live scan) → review → form → done. One per sheet
 * presentation; its view model holds it, so the stack survives rotation and dies with the sheet.
 */
class ScanCoordinator(private val onFinished: () -> Unit) {

    val backStack: SnapshotStateList<ScanRoute> = mutableStateListOf(ScanRoute.Choose)

    fun showLiveScanner() {
        backStack.add(ScanRoute.Live)
    }

    fun showReview(result: ScanResult) {
        backStack.add(ScanRoute.Review(result))
    }

    fun showForm(draft: ItemDraft) {
        backStack.add(ScanRoute.Form(draft))
    }

    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    /** Saved or cancelled: close the sheet. */
    fun finish() = onFinished()
}
```

- [ ] **Step 6: Implement `ScanFlowViewModel`**

`android/app/src/main/kotlin/com/mazesys/zerobite/screens/scan/ScanFlowViewModel.kt`:

```kotlin
package com.mazesys.zerobite.screens.scan

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazesys.zerobite.coordination.ScanCoordinator
import com.mazesys.zerobite.core.model.ItemDraft
import com.mazesys.zerobite.core.model.ScanResult
import com.mazesys.zerobite.core.time.AppClock
import com.mazesys.zerobite.scanning.PhotoScanner
import com.mazesys.zerobite.scanning.UnreadablePhoto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface ScanPhase {
    data object Idle : ScanPhase
    data object Processing : ScanPhase
    data class Failed(val message: String) : ScanPhase
}

/** State machine for the add-food sheet: idle → processing → (review | failed). */
class ScanFlowViewModel(
    val coordinator: ScanCoordinator,
    val supportsLiveScanning: Boolean,
    private val scanner: PhotoScanner,
    private val clock: AppClock,
) : ViewModel() {

    private val _phase = MutableStateFlow<ScanPhase>(ScanPhase.Idle)
    val phase: StateFlow<ScanPhase> = _phase.asStateFlow()

    fun startLiveScan() = coordinator.showLiveScanner()

    fun enterManually() = coordinator.showForm(ItemDraft.new(clock.today()))

    fun liveScanFinished(result: ScanResult) = coordinator.showReview(result)

    /** Runs OCR + barcode detection on a chosen photo and moves to the review screen. */
    fun process(photo: Uri) {
        if (_phase.value == ScanPhase.Processing) return
        _phase.value = ScanPhase.Processing
        viewModelScope.launch {
            _phase.value = try {
                val result = scanner.scan(photo, clock.today())
                if (result.isEmpty) {
                    ScanPhase.Failed(NOTHING_FOUND)
                } else {
                    coordinator.showReview(result)
                    ScanPhase.Idle
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: UnreadablePhoto) {
                ScanPhase.Failed(e.message!!)
            } catch (e: IOException) {
                ScanPhase.Failed(LOAD_FAILED)
            } catch (e: Exception) {
                ScanPhase.Failed(e.message ?: LOAD_FAILED)
            }
        }
    }

    fun dismissError() {
        _phase.value = ScanPhase.Idle
    }

    fun cancel() = coordinator.finish()

    private companion object {
        const val NOTHING_FOUND = "No text or barcode was found. Try a sharper, closer photo of the label."
        const val LOAD_FAILED = "Couldn't load that photo."
    }
}
```

- [ ] **Step 7: Implement `ScanReviewViewModel`**

`android/app/src/main/kotlin/com/mazesys/zerobite/screens/scan/ScanReviewViewModel.kt`:

```kotlin
package com.mazesys.zerobite.screens.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazesys.zerobite.core.model.DateCandidate
import com.mazesys.zerobite.core.model.FoodCategory
import com.mazesys.zerobite.core.model.ItemDraft
import com.mazesys.zerobite.core.model.ProductInfo
import com.mazesys.zerobite.core.model.ScanResult
import com.mazesys.zerobite.core.products.ProductLookup
import com.mazesys.zerobite.core.time.AppClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus

data class ScanReviewUiState(
    val result: ScanResult,
    val name: String,
    val category: FoodCategory = FoodCategory.OTHER,
    /** Index into `candidates`; null for "None of these". */
    val selectedIndex: Int? = null,
    val product: ProductInfo? = null,
    val isLookingUp: Boolean = false,
    val showsLines: Boolean = false,
) {
    val candidates: List<DateCandidate> get() = result.candidates
    val lines: List<String> get() = result.lines
    val barcode: String? get() = result.barcode
    val selectedCandidate: DateCandidate? get() = selectedIndex?.let { candidates.getOrNull(it) }
    val canContinue: Boolean get() = name.isNotBlank()
}

/** Confirm what was read: product name (barcode lookup when possible) and which date is the expiry. */
class ScanReviewViewModel(
    result: ScanResult,
    private val productLookup: ProductLookup,
    private val clock: AppClock,
    private val onContinue: (ItemDraft) -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ScanReviewUiState(result, name = result.suggestedName ?: "", selectedIndex = if (result.candidates.isEmpty()) null else 0),
    )
    val state: StateFlow<ScanReviewUiState> = _state.asStateFlow()

    init {
        loadProduct()
    }

    private fun loadProduct() {
        val result = _state.value.result
        val barcode = result.barcode ?: return
        _state.update { it.copy(isLookingUp = true) }
        viewModelScope.launch {
            val info = try {
                productLookup.lookup(barcode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null // Offline or the catalogue failed: keep what OCR found.
            }
            _state.update { s ->
                if (info == null) {
                    s.copy(isLookingUp = false)
                } else {
                    s.copy(
                        isLookingUp = false,
                        product = info,
                        // A catalogue name beats an OCR guess; keep anything the user has already typed.
                        name = if (s.name.isEmpty() || s.name == result.suggestedName) info.name else s.name,
                        category = info.category ?: s.category,
                    )
                }
            }
        }
    }

    fun setName(name: String) = _state.update { it.copy(name = name) }

    fun setCategory(category: FoodCategory) = _state.update { it.copy(category = category) }

    fun select(index: Int) = _state.update { it.copy(selectedIndex = index) }

    fun clearSelection() = _state.update { it.copy(selectedIndex = null) }

    fun toggleLines() = _state.update { it.copy(showsLines = !it.showsLines) }

    fun makeDraft(): ItemDraft {
        val s = _state.value
        val today = clock.today()
        return ItemDraft.new(today).copy(
            name = s.name.trim(),
            category = s.category,
            barcode = s.barcode,
            brand = s.product?.brand,
            expiresOn = s.selectedCandidate?.date ?: today.plus(s.category.defaultShelfLifeDays, DateTimeUnit.DAY),
        )
    }

    fun continueToForm() {
        if (!_state.value.canContinue) return
        onContinue(makeDraft())
    }
}
```

- [ ] **Step 8: Wire `productLookup` into `AppDependencies`**

In `AppDependencies.kt`:
- Add the imports `com.mazesys.zerobite.core.products.OpenFoodFactsClient`, `com.mazesys.zerobite.core.products.ProductLookup`, `io.ktor.client.HttpClient`, `io.ktor.client.engine.okhttp.OkHttp` and `io.ktor.client.plugins.HttpTimeout`.
- Add the constructor parameter `val productLookup: ProductLookup,` after `val labels: LabelReading,`.
- Add the companion member and update `live` to:

```kotlin
        /** One HTTP client for the whole app (Open Food Facts, FSA). 20 s, like iOS. */
        private fun httpClient() = HttpClient(OkHttp) {
            install(HttpTimeout) { requestTimeoutMillis = 20_000 }
            expectSuccess = false
        }

        fun live(context: Context): AppDependencies {
            val app = context.applicationContext
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val prefs = app.getSharedPreferences("reminders", Context.MODE_PRIVATE)
            val http = httpClient()
            return AppDependencies(
                clock = SystemAppClock,
                settings = DataStoreSettings(app.settingsDataStore, scope),
                inventory = RoomInventoryRepository(ZeroBiteDatabase.open(app).foodItems()),
                recipes = BundledRecipeRepository(app.assets),
                ids = IdGenerator.Random,
                appScope = scope,
                notifications = AndroidNotificationAccess(app, prefs),
                scheduler = AlarmReminderScheduler(app, prefs),
                notifier = AndroidNotifier(app),
                labels = MlKitLabelReader(app),
                productLookup = OpenFoodFactsClient(http),
            )
        }
```

In Fakes, add `productLookup: FakeProductLookup = FakeProductLookup(),` to `testDependencies` after `labels` and pass `productLookup = productLookup` in the `AppDependencies(...)` call.

- [ ] **Step 9: Run the tests to verify they pass**

Run: `cd android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests '*ScanFlowViewModelTest*' --tests '*ScanReviewViewModelTest*'`
Expected: PASS (6 + 7 tests).

- [ ] **Step 10: Run the whole app suite and commit**

Run: `cd android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, no failures.

```bash
git add android/app/src/main/kotlin/com/mazesys/zerobite/coordination/ScanCoordinator.kt \
  android/app/src/main/kotlin/com/mazesys/zerobite/screens/scan android/app/src/test/kotlin/com/mazesys/zerobite/screens/scan \
  android/app/src/main/kotlin/com/mazesys/zerobite/AppDependencies.kt android/app/src/test/kotlin/com/mazesys/zerobite/testing/Fakes.kt
git commit -m "Add the scan flow state machine, scan review and Open Food Facts lookup"
```

---

### Task 3: The add-food sheet: choose a source, pick a photo, review, then the form

**Files:**
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/ui/components/LoadingOverlay.kt`
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/screens/scan/ScanFlowScreen.kt`
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/screens/scan/ScanReviewScreen.kt`
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/screens/scan/ScanSheet.kt`
- Delete: `android/app/src/main/kotlin/com/mazesys/zerobite/screens/root/ScanPlaceholder.kt`
- Modify: `android/app/src/main/kotlin/com/mazesys/zerobite/screens/root/SheetHost.kt`
- Modify: `android/app/src/test/kotlin/com/mazesys/zerobite/screens/root/RootScreenTest.kt:119`
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/screens/scan/ScanSheetTest.kt`
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/screens/scan/ScanReviewScreenTest.kt`

**Interfaces:**
- Consumes: Task 2's view models and coordinator; `ItemFormViewModel(ItemFormMode.Add, draft, inventory, clock, ids, onSaved)`, `ItemFormScreen(vm, onCancel)` (Plan 2); `AppAlert`, `AlertConfig`, `AlertButton` (Plan 2); `DetailTitle`, `CardSection`, `InfoRow`, `ActionRow`, `LabeledInput`, `ChipFilter`, `Overline`, `AppCard`, `AppButton`, `TopBar`; `DateText.long`.
- Produces:
  - `LoadingOverlay(visible: Boolean, message: String)`
  - `ScanFlowScreen(viewModel: ScanFlowViewModel)`
  - `ScanReviewScreen(viewModel: ScanReviewViewModel, onBack: () -> Unit)`
  - `ScanSheet(dependencies: AppDependencies, onFinished: () -> Unit, live: @Composable (ScanFlowViewModel) -> Unit)`. The `live` slot is filled with a placeholder here and with the real scanner in Task 4.
  - Test tags: `scan.live`, `scan.photo`, `scan.manual`, `review.name`, `review.continue`.

**Copy adaptations:** none. The iOS "Choose a photo of the label" option opens the Android system photo picker.

- [ ] **Step 1: Write the failing sheet test**

`android/app/src/test/kotlin/com/mazesys/zerobite/screens/scan/ScanSheetTest.kt`:

```kotlin
package com.mazesys.zerobite.screens.scan

import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.mazesys.zerobite.core.model.ScanResult
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeLabels
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.testDependencies
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ScanSheetTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val inventory = FakeInventoryRepository()
    private val labels = FakeLabels()
    private var finished = 0
    private var flow: ScanFlowViewModel? = null

    private fun show(supportsLive: Boolean = true) {
        val deps = testDependencies(inventory = inventory, labels = FakeLabels(supportsLiveScanning = supportsLive).also {
            it.result = labels.result
            it.failure = labels.failure
        })
        val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        compose.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                ZeroBiteTheme {
                    ScanSheet(deps, onFinished = { finished++ }) { vm -> flow = vm; Text("Live scanner") }
                }
            }
        }
    }

    @Test
    fun `offers the three sources`() {
        show()
        compose.onNode(hasText("Add food") and isHeading()).assertIsDisplayed()
        compose.onNodeWithTag("scan.live").assertIsDisplayed()
        compose.onNodeWithTag("scan.photo").assertIsDisplayed()
        compose.onNodeWithTag("scan.manual").assertIsDisplayed()
        compose.onNodeWithText("Point at the best-before or use-by date. A barcode helps fill in the product name.").assertExists()
    }

    @Test
    fun `no camera hides the live option`() {
        show(supportsLive = false)
        compose.onNodeWithTag("scan.live").assertDoesNotExist()
    }

    @Test
    fun `typing it in saves through the form and closes the sheet`() {
        show()
        compose.onNodeWithTag("scan.manual").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("itemForm.name").performTextInput("Yoghurt")
        compose.onNodeWithTag("itemForm.save").performClick()
        compose.waitUntil(3_000) { finished == 1 }
        assertEquals(listOf("Yoghurt"), inventory.current.map { it.name })
    }

    @Test
    fun `a scanned photo lands on review, and back returns to the sources`() {
        labels.result = ScanResult(lines = listOf("SEMI SKIMMED MILK"))
        show()
        compose.onNodeWithTag("scan.live").performClick()
        compose.onNodeWithText("Live scanner").assertIsDisplayed()
        compose.runOnUiThread { flow!!.coordinator.pop() }
        compose.runOnUiThread { flow!!.process(Uri.parse("content://media/picker/0/1")) }
        compose.waitForIdle()
        compose.onNode(hasText("Check the details") and isHeading()).assertIsDisplayed()
    }

    @Test
    fun `a failed photo shows the error`() {
        show()
        compose.onNodeWithTag("scan.live").performClick()
        compose.runOnUiThread { flow!!.coordinator.pop() }
        compose.runOnUiThread { flow!!.process(Uri.parse("content://media/picker/0/1")) }
        compose.onNodeWithText("Couldn't read that").assertIsDisplayed()
        compose.onNodeWithText("No text or barcode was found. Try a sharper, closer photo of the label.").assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Couldn't read that").assertDoesNotExist()
    }
}
```

(The tests open and pop the live route only to capture the `ScanFlowViewModel` that the `live` slot receives; the photo picker itself can't run under Robolectric, so `process(uri)` is called directly.)

- [ ] **Step 2: Write the failing review screen test**

`android/app/src/test/kotlin/com/mazesys/zerobite/screens/scan/ScanReviewScreenTest.kt`:

```kotlin
package com.mazesys.zerobite.screens.scan

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import com.mazesys.zerobite.core.model.ItemDraft
import com.mazesys.zerobite.core.scanning.LabelScanner
import com.mazesys.zerobite.testing.FakeProductLookup
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ScanReviewScreenTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private var continued: ItemDraft? = null

    private fun show(lines: List<String>, barcode: String? = "5000119000000") {
        val result = LabelScanner.analyse(lines, barcode, TestClock.today)
        val vm = ScanReviewViewModel(result, FakeProductLookup(), TestClock.fixed(), onContinue = { continued = it })
        compose.setContent { ZeroBiteTheme { ScanReviewScreen(vm, onBack = {}) } }
    }

    @Test
    fun `lists the dates found and continues with the chosen one`() {
        show(listOf("TESCO", "SEMI SKIMMED MILK", "USE BY 12 SEP 2026", "PACKED 01/09/2026"))
        compose.onNodeWithText("12 September 2026").assertExists()
        compose.onNodeWithText("Pick the best-before or use-by date. Most likely first.").assertExists()
        compose.onNodeWithText("Barcode").assertExists()
        compose.onNodeWithTag("review.continue").performClick()
        assertEquals(LocalDate(2026, 9, 12), continued?.expiresOn)
    }

    @Test
    fun `no date says so`() {
        show(listOf("SEMI SKIMMED MILK"), barcode = null)
        compose.onNodeWithText("No date was found on the label. You can set one on the next screen.").assertExists()
    }

    @Test
    fun `continue is disabled without a name`() {
        show(listOf("SEMI SKIMMED MILK", "USE BY 12 SEP 2026"))
        compose.onNodeWithTag("review.name").performTextClearance()
        compose.onNodeWithTag("review.continue").assertIsNotEnabled()
    }

    @Test
    fun `the read text can be shown`() {
        show(listOf("SEMI SKIMMED MILK", "USE BY 12 SEP 2026"))
        compose.onNodeWithText("Show 2 lines").performScrollTo().performClick()
        compose.onNodeWithText("Hide 2 lines").assertExists()
        compose.onNodeWithTag("review.continue").assertIsEnabled()
    }
}
```

- [ ] **Step 3: Run them to verify they fail**

Run: `cd android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests '*ScanSheetTest*' --tests '*ScanReviewScreenTest*'`
Expected: compilation FAILS with unresolved `ScanSheet`, `ScanReviewScreen`.

- [ ] **Step 4: Implement `LoadingOverlay`**

`android/app/src/main/kotlin/com/mazesys/zerobite/ui/components/LoadingOverlay.kt`:

```kotlin
package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme

/** Dims the screen and blocks touches while something slow runs (iOS `.loadingOverlay`). */
@Composable
fun LoadingOverlay(visible: Boolean, message: String) {
    if (!visible) return
    val colors = ZeroBiteTheme.colors
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.background(colors.surface, RoundedCornerShape(Radius.card)).padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            CircularProgressIndicator(color = colors.brandOrange, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            Text(message, style = ZeroBiteTheme.type.subheadline, color = colors.textPrimary)
        }
    }
}
```

- [ ] **Step 5: Implement `ScanFlowScreen`**

`android/app/src/main/kotlin/com/mazesys/zerobite/screens/scan/ScanFlowScreen.kt`:

```kotlin
package com.mazesys.zerobite.screens.scan

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazesys.zerobite.coordination.AlertButton
import com.mazesys.zerobite.coordination.AlertConfig
import com.mazesys.zerobite.screens.root.AppAlert
import com.mazesys.zerobite.ui.components.AppCard
import com.mazesys.zerobite.ui.components.DetailTitle
import com.mazesys.zerobite.ui.components.LoadingOverlay
import com.mazesys.zerobite.ui.components.TopBar
import com.mazesys.zerobite.ui.components.TopBarLeading
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.medium

/** First screen of the add-food sheet: camera, photo or typing. */
@Composable
fun ScanFlowScreen(viewModel: ScanFlowViewModel) {
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val colors = ZeroBiteTheme.colors
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.process(uri)
    }

    Box(Modifier.fillMaxSize().background(colors.backgroundPrimary)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(TopBarLeading.Close(viewModel::cancel))
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                DetailTitle(title = "Add food", overline = "Scan or type")
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    if (viewModel.supportsLiveScanning) {
                        ScanOptionRow(Icons.Rounded.QrCodeScanner, "Scan with the camera", "Reads dates and barcodes live", isPrimary = true,
                            onClick = viewModel::startLiveScan, modifier = Modifier.testTag("scan.live"))
                    }
                    ScanOptionRow(Icons.Rounded.PhotoLibrary, "Choose a photo of the label", "Pick a picture of the packaging",
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.testTag("scan.photo"))
                    ScanOptionRow(Icons.Rounded.EditNote, "Type it in", "No label to read",
                        onClick = viewModel::enterManually, modifier = Modifier.testTag("scan.manual"))
                }
                Text(
                    "Point at the best-before or use-by date. A barcode helps fill in the product name.",
                    style = ZeroBiteTheme.type.caption,
                    color = colors.textSecondary,
                )
            }
        }
        LoadingOverlay(phase == ScanPhase.Processing, "Reading the label…")
    }

    (phase as? ScanPhase.Failed)?.let { failed ->
        AppAlert(
            AlertConfig(title = "Couldn't read that", message = failed.message, primary = AlertButton("OK")),
            onDismiss = viewModel::dismissError,
        )
    }
}

@Composable
private fun ScanOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
) {
    val colors = ZeroBiteTheme.colors
    AppCard(modifier.clickable(role = Role.Button, onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Box(
                Modifier.size(44.dp).background(
                    if (isPrimary) colors.brandOrange else colors.brandOrange.copy(alpha = 0.12f),
                    RoundedCornerShape(Radius.control),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = if (isPrimary) Color.White else colors.brandOrange, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = ZeroBiteTheme.type.subheadline.medium(), color = colors.textPrimary)
                Text(subtitle, style = ZeroBiteTheme.type.caption, color = colors.textSecondary)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(12.dp))
        }
    }
}
```


- [ ] **Step 6: Implement `ScanReviewScreen`**

`android/app/src/main/kotlin/com/mazesys/zerobite/screens/scan/ScanReviewScreen.kt`:

```kotlin
package com.mazesys.zerobite.screens.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazesys.zerobite.core.model.DateCandidate
import com.mazesys.zerobite.core.model.FoodCategory
import com.mazesys.zerobite.ui.components.ActionRow
import com.mazesys.zerobite.ui.components.AppButton
import com.mazesys.zerobite.ui.components.CardSection
import com.mazesys.zerobite.ui.components.ChipFilter
import com.mazesys.zerobite.ui.components.DetailTitle
import com.mazesys.zerobite.ui.components.InfoRow
import com.mazesys.zerobite.ui.components.LabeledInput
import com.mazesys.zerobite.ui.components.Overline
import com.mazesys.zerobite.ui.components.TopBar
import com.mazesys.zerobite.ui.components.TopBarLeading
import com.mazesys.zerobite.ui.format.DateText
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.semibold

/** Check what was read: name, category, product and which date is the expiry. */
@Composable
fun ScanReviewScreen(viewModel: ScanReviewViewModel, onBack: () -> Unit) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val colors = ZeroBiteTheme.colors

    Column(Modifier.fillMaxSize().background(colors.backgroundPrimary)) {
        TopBar(TopBarLeading.Back(onBack))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            DetailTitle(title = "Check the details", overline = "Scanned label")

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                LabeledInput(label = "Name", value = ui.name, onValueChange = viewModel::setName, placeholder = "Product name", testTag = "review.name")
                if (ui.isLookingUp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        CircularProgressIndicator(color = colors.textSecondary, strokeWidth = 1.5.dp, modifier = Modifier.size(12.dp))
                        Text("Looking up barcode…", style = ZeroBiteTheme.type.caption, color = colors.textSecondary)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Overline("Category")
                ChipFilter(FoodCategory.entries, ui.category, viewModel::setCategory, label = { it.label }, contentPadding = androidx.compose.foundation.layout.PaddingValues())
            }

            if (ui.product != null || ui.barcode != null) {
                CardSection(
                    title = "Product",
                    rows = buildList<@Composable () -> Unit> {
                        ui.product?.let { product ->
                            add { InfoRow("Found", listOfNotNull(product.brand, product.name).joinToString(" · "), icon = Icons.Rounded.Verified) }
                        }
                        ui.barcode?.let { code -> add { InfoRow("Barcode", code, icon = Icons.Rounded.QrCode2) } }
                    },
                )
            }

            if (ui.candidates.isEmpty()) {
                CardSection(title = "Expiry date", rows = listOf<@Composable () -> Unit>({
                    Text("No date was found on the label. You can set one on the next screen.",
                        style = ZeroBiteTheme.type.subheadline, color = colors.textSecondary)
                }))
            } else {
                CardSection(
                    title = "Expiry date",
                    footer = "Pick the best-before or use-by date. Most likely first.",
                    rows = buildList<@Composable () -> Unit> {
                        ui.candidates.forEachIndexed { index, candidate ->
                            add { CandidateRow(candidate, index == ui.selectedIndex) { viewModel.select(index) } }
                        }
                        add { ActionRow("None of these", viewModel::clearSelection, icon = Icons.Rounded.Close, tint = colors.textSecondary) }
                    },
                )
            }

            if (ui.lines.isNotEmpty()) {
                CardSection(
                    title = "Text read from the label",
                    rows = buildList<@Composable () -> Unit> {
                        add {
                            ActionRow(
                                if (ui.showsLines) "Hide ${ui.lines.size} lines" else "Show ${ui.lines.size} lines",
                                viewModel::toggleLines,
                                icon = if (ui.showsLines) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                tint = colors.textSecondary,
                            )
                        }
                        if (ui.showsLines) add {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                ui.lines.forEach { line ->
                                    Text(line, style = ZeroBiteTheme.type.footnote.copy(fontFamily = FontFamily.Monospace), color = colors.textSecondary)
                                }
                            }
                        }
                    },
                )
            }
        }
        AppButton(
            "Continue",
            onClick = viewModel::continueToForm,
            enabled = ui.canContinue,
            modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm).testTag("review.continue"),
        )
    }
}

@Composable
private fun CandidateRow(candidate: DateCandidate, isSelected: Boolean, onClick: () -> Unit) {
    val colors = ZeroBiteTheme.colors
    Row(
        Modifier.clickable(role = Role.Checkbox, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(DateText.long(candidate.date), style = ZeroBiteTheme.type.subheadline.semibold(), color = colors.textPrimary)
            Text("“${candidate.sourceLine}”", style = ZeroBiteTheme.type.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ConfidenceDots(candidate.confidence)
        Icon(
            if (isSelected) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
            contentDescription = null,
            tint = if (isSelected) colors.brandOrange else colors.textMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ConfidenceDots(confidence: Double) {
    val colors = ZeroBiteTheme.colors
    Row(
        Modifier.semantics { contentDescription = "Confidence ${(confidence * 100).toInt()} percent" },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(3) { index ->
            androidx.compose.foundation.layout.Box(
                Modifier.size(width = 4.dp, height = 10.dp)
                    .background(if (index / 3.0 < confidence) colors.brandOrange else colors.chipFill),
            )
        }
    }
}
```


- [ ] **Step 7: Implement `ScanSheet` and use it from `SheetHost`**

`android/app/src/main/kotlin/com/mazesys/zerobite/screens/scan/ScanSheet.kt`:

```kotlin
package com.mazesys.zerobite.screens.scan

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.mazesys.zerobite.AppDependencies
import com.mazesys.zerobite.coordination.ScanCoordinator
import com.mazesys.zerobite.coordination.ScanRoute
import com.mazesys.zerobite.screens.inventory.ItemFormMode
import com.mazesys.zerobite.screens.inventory.ItemFormScreen
import com.mazesys.zerobite.screens.inventory.ItemFormViewModel

/**
 * The add-food sheet. Its ScanFlowViewModel (in the sheet's store) holds the ScanCoordinator, so
 * the stack survives rotation. `live` renders the camera screen (a slot so tests can stub it).
 */
@Composable
fun ScanSheet(
    dependencies: AppDependencies,
    onFinished: () -> Unit,
    live: @Composable (ScanFlowViewModel) -> Unit,
) {
    val flow = viewModel {
        ScanFlowViewModel(ScanCoordinator(onFinished), dependencies.labels.supportsLiveScanning, dependencies.labels, dependencies.clock)
    }
    val scan = flow.coordinator
    NavDisplay(
        backStack = scan.backStack,
        onBack = { scan.pop() },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<ScanRoute.Choose> { ScanFlowScreen(flow) }
            entry<ScanRoute.Live> { live(flow) }
            entry<ScanRoute.Review> { route ->
                val review = viewModel {
                    ScanReviewViewModel(route.result, dependencies.productLookup, dependencies.clock, onContinue = scan::showForm)
                }
                ScanReviewScreen(review, onBack = scan::pop)
            }
            entry<ScanRoute.Form> { route ->
                val form = viewModel {
                    ItemFormViewModel(ItemFormMode.Add, route.draft, dependencies.inventory, dependencies.clock, dependencies.ids, onSaved = { scan.finish() })
                }
                ItemFormScreen(form, onCancel = scan::finish)
            }
        },
    )
}
```

The Navigation 3 imports above must match the ones in `screens/inventory/InventoryTab.kt`; copy them from there if any differ.

In `SheetHost.kt`, replace the `SheetDestination.Scan ->` line with:

```kotlin
                    SheetDestination.Scan -> ScanSheet(dependencies, onFinished = coordinator::dismissSheet) { flow ->
                        androidx.compose.material3.Text("Live scanning arrives in the next task.")
                    }
```

(Task 4 replaces this slot with `LiveScannerScreen`.) Add the import `com.mazesys.zerobite.screens.scan.ScanSheet`, delete `screens/root/ScanPlaceholder.kt`, and in `RootScreenTest.kt:119` change `waitFor("Scan a label")` to `waitFor("Choose a photo of the label")`.

- [ ] **Step 8: Run the tests to verify they pass**

Run: `cd android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests '*ScanSheetTest*' --tests '*ScanReviewScreenTest*' --tests '*RootScreenTest*'`
Expected: PASS (5 + 4 tests + RootScreenTest).

- [ ] **Step 9: Run the whole suite and commit**

Run: `cd android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, no failures.

```bash
git add -A android/app/src/main/kotlin/com/mazesys/zerobite/screens android/app/src/main/kotlin/com/mazesys/zerobite/ui/components/LoadingOverlay.kt \
  android/app/src/test/kotlin/com/mazesys/zerobite/screens
git commit -m "Add the add-food sheet: sources, photo picker, scan review and the item form"
```

---

### Task 4: Live camera scanning

**Files:**
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/screens/scan/LiveScanViewModel.kt`
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/screens/scan/LiveScannerScreen.kt`
- Modify: `android/app/src/main/kotlin/com/mazesys/zerobite/screens/root/SheetHost.kt` (the `live` slot)
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/screens/scan/LiveScanViewModelTest.kt`
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/screens/scan/LiveScannerContentTest.kt`

**Interfaces:**
- Consumes: `LiveScanSession` and `LabelReading.analyzer` (Task 1); `ScanFlowViewModel.liveScanFinished` and `ScanCoordinator.pop` (Task 2); `AppAlert`; `AppButton`; `DateText.long`.
- Produces:
  - `data class LiveScanUiState(session, result)` with `bestDateText`, `lineCountText`, `canFinish`
  - `class LiveScanViewModel(clock, onFinish: (ScanResult) -> Unit)` with `state`, `onFrame(text: RecognizedText)`, `finish()`
  - `enum class CameraAccess { ASKING, GRANTED, DENIED }`
  - `LiveScannerContent(access, ui, cameraError, onFinish, onOpenSettings, onUsePhoto, onBack, onDismissError, preview)`
  - `LiveScannerScreen(viewModel, labels, onBack, onUsePhoto)`

**Copy adaptations:**
- iOS shows the system camera prompt with `NSCameraUsageDescription`. Android shows that same sentence on the denied panel, with "Open settings" and "Choose a photo instead".
- iOS's "Camera unavailable" alert is kept for CameraX bind errors.

- [ ] **Step 1: Write the failing view model test**

`android/app/src/test/kotlin/com/mazesys/zerobite/screens/scan/LiveScanViewModelTest.kt`:

```kotlin
package com.mazesys.zerobite.screens.scan

import com.mazesys.zerobite.core.model.ScanResult
import com.mazesys.zerobite.scanning.RecognizedText
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveScanViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private var finished: ScanResult? = null
    private val vm by lazy { LiveScanViewModel(TestClock.fixed(), onFinish = { finished = it }) }

    @Test
    fun `starts empty and can't finish`() {
        assertNull(vm.state.value.bestDateText)
        assertEquals("0 lines read", vm.state.value.lineCountText)
        assertFalse(vm.state.value.canFinish)
        vm.finish()
        assertNull(finished)
    }

    @Test
    fun `frames build up the date, barcode and name`() {
        vm.onFrame(RecognizedText(listOf("SEMI SKIMMED MILK"), emptyList()))
        vm.onFrame(RecognizedText(listOf("SEMI SKIMMED MILK", "USE BY 12 SEP 2026"), listOf("5000119000000")))
        val ui = vm.state.value
        assertEquals("12 September 2026", ui.bestDateText)
        assertEquals("5000119000000", ui.result.barcode)
        assertEquals("2 lines read", ui.lineCountText)
        assertTrue(ui.canFinish)
        vm.finish()
        assertEquals(ui.result, finished)
    }
}
```

- [ ] **Step 2: Write the failing content test**

`android/app/src/test/kotlin/com/mazesys/zerobite/screens/scan/LiveScannerContentTest.kt`:

```kotlin
package com.mazesys.zerobite.screens.scan

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mazesys.zerobite.core.scanning.LiveScanSession
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class LiveScannerContentTest {

    @get:Rule
    val compose = createComposeRule()

    private var settings = 0
    private var photo = 0
    private var finished = 0

    private fun show(access: CameraAccess, session: LiveScanSession = LiveScanSession(), error: String? = null) {
        val ui = LiveScanUiState(session, session.result(TestClock.today))
        compose.setContent {
            ZeroBiteTheme {
                LiveScannerContent(
                    access = access, ui = ui, cameraError = error,
                    onFinish = { finished++ }, onOpenSettings = { settings++ }, onUsePhoto = { photo++ },
                    onBack = {}, onDismissError = {},
                ) { Text("Camera preview") }
            }
        }
    }

    @Test
    fun `denied camera offers settings and a photo`() {
        show(CameraAccess.DENIED)
        compose.onNodeWithText("Zero Bite uses the camera to read best-before dates and barcodes on food labels.").assertExists()
        compose.onNodeWithText("Camera preview").assertDoesNotExist()
        compose.onNodeWithText("Open settings").performClick()
        compose.onNodeWithText("Choose a photo instead").performClick()
        assertEquals(1, settings)
        assertEquals(1, photo)
    }

    @Test
    fun `granted shows the preview and what was found`() {
        show(CameraAccess.GRANTED, LiveScanSession().adding(listOf("SEMI SKIMMED MILK", "USE BY 12 SEP 2026"), listOf("5000119000000")))
        compose.onNodeWithText("Camera preview").assertExists()
        compose.onNodeWithText("12 September 2026").assertExists()
        compose.onNodeWithText("5000119000000").assertExists()
        compose.onNodeWithText("2 lines read").assertExists()
        compose.onNodeWithText("Use what I've found").assertIsEnabled().performClick()
        assertEquals(1, finished)
    }

    @Test
    fun `nothing yet asks for a date and can't finish`() {
        show(CameraAccess.GRANTED)
        compose.onNodeWithText("Looking for a best-before date…").assertExists()
        compose.onNodeWithText("Use what I've found").assertIsNotEnabled()
    }

    @Test
    fun `a camera error is shown`() {
        show(CameraAccess.GRANTED, error = "The camera is in use by another app.")
        compose.onNodeWithText("Camera unavailable").assertExists()
        compose.onNodeWithText("The camera is in use by another app.").assertExists()
    }
}
```

- [ ] **Step 3: Run them to verify they fail**

Run: `cd android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests '*LiveScanViewModelTest*' --tests '*LiveScannerContentTest*'`
Expected: compilation FAILS with unresolved `LiveScanViewModel`, `LiveScanUiState`, `LiveScannerContent`, `CameraAccess`.

- [ ] **Step 4: Implement `LiveScanViewModel`**

`android/app/src/main/kotlin/com/mazesys/zerobite/screens/scan/LiveScanViewModel.kt`:

```kotlin
package com.mazesys.zerobite.screens.scan

import androidx.lifecycle.ViewModel
import com.mazesys.zerobite.core.model.ScanResult
import com.mazesys.zerobite.core.scanning.LiveScanSession
import com.mazesys.zerobite.core.time.AppClock
import com.mazesys.zerobite.scanning.RecognizedText
import com.mazesys.zerobite.ui.format.DateText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LiveScanUiState(
    val session: LiveScanSession = LiveScanSession(),
    val result: ScanResult = ScanResult(),
) {
    val bestDateText: String? get() = result.bestCandidate?.let { DateText.long(it.date) }
    val lineCountText: String get() = "${session.lines.size} lines read"
    val canFinish: Boolean get() = !result.isEmpty
}

/** Folds camera frames into what's been read so far (iOS LiveScannerView's state). */
class LiveScanViewModel(
    private val clock: AppClock,
    private val onFinish: (ScanResult) -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(LiveScanUiState())
    val state: StateFlow<LiveScanUiState> = _state.asStateFlow()

    fun onFrame(text: RecognizedText) = _state.update { current ->
        val session = current.session.adding(text.lines, text.barcodes)
        // Most frames add nothing new; skip the re-parse then.
        if (session == current.session) current else LiveScanUiState(session, session.result(clock.today()))
    }

    fun finish() {
        val result = _state.value.result
        if (!result.isEmpty) onFinish(result)
    }
}
```

- [ ] **Step 5: Implement `LiveScannerScreen` and `LiveScannerContent`**

`android/app/src/main/kotlin/com/mazesys/zerobite/screens/scan/LiveScannerScreen.kt`:

```kotlin
package com.mazesys.zerobite.screens.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazesys.zerobite.coordination.AlertButton
import com.mazesys.zerobite.coordination.AlertConfig
import com.mazesys.zerobite.scanning.LabelReading
import com.mazesys.zerobite.screens.root.AppAlert
import com.mazesys.zerobite.ui.components.AppButton
import com.mazesys.zerobite.ui.components.ButtonKind
import com.mazesys.zerobite.ui.components.EmptyStateView
import com.mazesys.zerobite.ui.components.TopBar
import com.mazesys.zerobite.ui.components.TopBarLeading
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.semibold

enum class CameraAccess { ASKING, GRANTED, DENIED }

/** Live label scanning: asks for the camera, previews it and reads every frame. */
@Composable
fun LiveScannerScreen(viewModel: LiveScanViewModel, labels: LabelReading, onBack: () -> Unit, onUsePhoto: () -> Unit) {
    val context = LocalContext.current
    val ui by viewModel.state.collectAsStateWithLifecycle()
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var access by rememberSaveable { mutableStateOf(if (granted()) CameraAccess.GRANTED else CameraAccess.ASKING) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        access = if (ok) CameraAccess.GRANTED else CameraAccess.DENIED
    }
    LaunchedEffect(Unit) {
        if (access == CameraAccess.ASKING) request.launch(Manifest.permission.CAMERA)
    }
    val analyzer = remember(labels) { labels.analyzer(viewModel::onFrame) }

    LiveScannerContent(
        access = access,
        ui = ui,
        cameraError = cameraError,
        onFinish = viewModel::finish,
        onOpenSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
        onUsePhoto = onUsePhoto,
        onBack = onBack,
        onDismissError = { cameraError = null },
    ) {
        CameraPreview(analyzer, onError = { cameraError = it })
    }
}

/** Stateless body, so the permission states and the summary card are testable without a camera. */
@Composable
fun LiveScannerContent(
    access: CameraAccess,
    ui: LiveScanUiState,
    cameraError: String?,
    onFinish: () -> Unit,
    onOpenSettings: () -> Unit,
    onUsePhoto: () -> Unit,
    onBack: () -> Unit,
    onDismissError: () -> Unit,
    preview: @Composable () -> Unit,
) {
    val colors = ZeroBiteTheme.colors
    Box(Modifier.fillMaxSize().background(colors.backgroundPrimary)) {
        when (access) {
            CameraAccess.GRANTED -> {
                preview()
                SummaryCard(ui, onFinish, Modifier.align(Alignment.BottomCenter))
            }
            CameraAccess.DENIED -> Column(
                Modifier.align(Alignment.Center).padding(Spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                EmptyStateView(
                    title = "Camera access is off",
                    message = "Zero Bite uses the camera to read best-before dates and barcodes on food labels.",
                    icon = Icons.Rounded.PhotoCamera,
                )
                AppButton("Open settings", onOpenSettings)
                AppButton("Choose a photo instead", onUsePhoto, kind = ButtonKind.TONAL)
            }
            CameraAccess.ASKING -> Unit
        }
        TopBar(TopBarLeading.Back(onBack))
    }
    if (cameraError != null) {
        AppAlert(AlertConfig(title = "Camera unavailable", message = cameraError, primary = AlertButton("OK")), onDismiss = onDismissError)
    }
}

@Composable
private fun SummaryCard(ui: LiveScanUiState, onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ZeroBiteTheme.colors
    Column(
        modifier.padding(Spacing.lg).fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.94f), RoundedCornerShape(Radius.card))
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (ui.bestDateText != null) {
            Found(Icons.Rounded.CalendarMonth, ui.bestDateText!!, emphasised = true)
        } else {
            Found(Icons.Rounded.CalendarMonth, "Looking for a best-before date…", muted = true)
        }
        ui.result.barcode?.let { Found(Icons.Rounded.QrCode2, it) }
        ui.result.suggestedName?.let { Found(Icons.AutoMirrored.Rounded.Label, it) }
        Text(ui.lineCountText, style = ZeroBiteTheme.type.caption, color = colors.textSecondary)
        AppButton("Use what I've found", onFinish, enabled = ui.canFinish)
    }
}

@Composable
private fun Found(icon: ImageVector, text: String, emphasised: Boolean = false, muted: Boolean = false) {
    val colors = ZeroBiteTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Icon(icon, contentDescription = null, tint = if (muted) colors.textSecondary else colors.textPrimary, modifier = Modifier.size(18.dp))
        Text(
            text,
            style = if (emphasised) ZeroBiteTheme.type.headline.semibold() else ZeroBiteTheme.type.subheadline,
            color = if (muted) colors.textSecondary else colors.textPrimary,
        )
    }
}

/** CameraX preview + analysis bound to this screen's lifecycle; unbound when it leaves. */
@Composable
private fun CameraPreview(analyzer: ImageAnalysis.Analyzer, onError: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            try {
                val cameras = future.get().also { provider = it }
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(executor, analyzer) }
                cameras.unbindAll()
                cameras.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                onError(e.message ?: "The camera couldn't start.")
            }
        }, executor)
        onDispose { provider?.unbindAll() }
    }
    AndroidView({ previewView }, Modifier.fillMaxSize())
}
```

Check that `EmptyStateView`'s parameters (`ui/components/EmptyState.kt:18`) are `title`, `message` and `icon`, with optional primary-button parameters defaulted. If they aren't, pass what it requires and ledger the change.

- [ ] **Step 6: Fill the live slot in `SheetHost`**

In `SheetHost.kt`, replace the Task 3 `SheetDestination.Scan ->` slot with:

```kotlin
                    SheetDestination.Scan -> ScanSheet(dependencies, onFinished = coordinator::dismissSheet) { flow ->
                        val live = viewModel(key = "live") { LiveScanViewModel(dependencies.clock, onFinish = flow::liveScanFinished) }
                        LiveScannerScreen(live, dependencies.labels, onBack = flow.coordinator::pop, onUsePhoto = flow.coordinator::pop)
                    }
```

Add the imports `com.mazesys.zerobite.screens.scan.LiveScanViewModel` and `com.mazesys.zerobite.screens.scan.LiveScannerScreen`. The `viewModel` here resolves against the `Live` entry's own store, which comes from `rememberViewModelStoreNavEntryDecorator`, so leaving the scanner starts the next one fresh.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `cd android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; `LiveScanViewModelTest` (2) and `LiveScannerContentTest` (4) pass, nothing else fails.

- [ ] **Step 8: Commit**

```bash
git add android/app/src/main/kotlin/com/mazesys/zerobite/screens android/app/src/test/kotlin/com/mazesys/zerobite/screens/scan
git commit -m "Scan labels live with CameraX, with a way out when the camera is denied"
```

---
### Task 5: Recall store: fetch, cache, throttle, dismissals and relative dates

**Files:**
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/recalls/RecallStore.kt`
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/recalls/LocalRecallPersistence.kt`
- Modify: `android/app/src/main/kotlin/com/mazesys/zerobite/ui/format/DateText.kt`
- Modify: `AppDependencies.kt` (+ `recalls`), `testing/Fakes.kt` (+ `MutableClock`, `FakeAlerts`, `FakeRecallPersistence`, `recalledMilk()`, `milkAllergyAlert()`, `recallStoreWith(...)`)
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/recalls/RecallStoreTest.kt`
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/recalls/LocalRecallPersistenceTest.kt`
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/ui/format/DateTextTest.kt`

**Interfaces:**
- Consumes: `FoodAlertsFetching.fetchAlerts(since: LocalDate, known: List<FoodAlert>)`, `FsaAlertsClient(http)`, `FoodAlert` (`@Serializable`), `RecallMatcher(today).matches(alerts, items, dismissed)`, `RecallMatch.id` (`:core`); `AppDependencies.live`'s `http` (Task 2).
- Note: `:core` already has `GisAlertsClient` (Poland's GIS warnings) and `CombinedFoodAlerts`, which merges the FSA and GIS clients behind the same `FoodAlertsFetching`. This task's store takes whichever `FoodAlertsFetching` it is given and passes its cached alerts as `known`; Task 7 of `specs/plans/2026-11-04-poland-recalls.md` wires the combined client and the Settings feed choice.
- Produces:
  - `data class RecallMarks(lastChecked: Instant? = null, dismissed: Set<String> = emptySet(), notified: Set<String> = emptySet())`
  - `interface RecallPersistence { suspend fun loadAlerts(); suspend fun saveAlerts(alerts); suspend fun loadMarks(); suspend fun saveMarks(marks) }`
  - `data class RecallState(alerts, marks, isLoaded, isRefreshing, errorMessage)` with `lastChecked` and `fun matches(items: List<FoodItem>, today: LocalDate): List<RecallMatch>` (active items only)
  - `class RecallStore(client, persistence, clock)` with `state: StateFlow<RecallState>`, `suspend load()`, `needsRefresh`, `suspend refresh(force = false): Boolean`, `suspend dismiss(match)`, `suspend claimUnnotified(matches): List<RecallMatch>`, and companion `HISTORY_DAYS = 120`, `REFRESH_INTERVAL = 6.hours`, `UNREACHABLE`
  - `class LocalRecallPersistence(cacheFile: File, store: DataStore<Preferences>)`
  - `DateText.dayMonth(date)` ("7 Sep") and `DateText.relative(then: Instant, now: Instant)` ("now", "5 minutes ago", "3 hours ago", "yesterday", "3 days ago", "last week", "2 weeks ago")
  - `AppDependencies.recalls: RecallStore`
  - `testDependencies(alerts = FakeAlerts(), recallPersistence = FakeRecallPersistence())`

- [ ] **Step 1: Add the recall fakes and fixtures**

Append to `testing/Fakes.kt` (imports: `com.mazesys.zerobite.core.alerts.FoodAlertsFetching`, `com.mazesys.zerobite.core.model.FoodAlert`, `com.mazesys.zerobite.recalls.RecallMarks`, `com.mazesys.zerobite.recalls.RecallPersistence`, `com.mazesys.zerobite.recalls.RecallStore`, `kotlinx.datetime.minus`):

```kotlin
class MutableClock(var instant: Instant = TestClock.now, override val timeZone: TimeZone = TestClock.zone) : AppClock {
    override fun now(): Instant = instant
}

/** Alerts client double: returns `alerts` or throws `failure`, and records each `since` and `known` it was asked for. */
class FakeAlerts(var alerts: List<FoodAlert> = emptyList(), var failure: Exception? = null) : FoodAlertsFetching {
    val requests = mutableListOf<LocalDate>()
    val knownPerCall = mutableListOf<List<FoodAlert>>()
    override suspend fun fetchAlerts(since: LocalDate, known: List<FoodAlert>): List<FoodAlert> {
        requests += since
        knownPerCall += known
        failure?.let { throw it }
        return alerts
    }
}

/** In-memory cache + marks; shared between two RecallStores it simulates an app restart. */
class FakeRecallPersistence(var alerts: List<FoodAlert> = emptyList(), var marks: RecallMarks = RecallMarks()) : RecallPersistence {
    override suspend fun loadAlerts() = alerts
    override suspend fun saveAlerts(alerts: List<FoodAlert>) { this.alerts = alerts }
    override suspend fun loadMarks() = marks
    override suspend fun saveMarks(marks: RecallMarks) { this.marks = marks }
}

/** "Milk" in the fridge, and an FSA recall listing its barcode: a "likely" match. */
fun recalledMilk(): Pair<FoodItem, FoodAlert> {
    val item = FoodItem(
        id = "milk", name = "Milk", addedAt = TestClock.now,
        expiresOn = TestClock.today.plus(3, DateTimeUnit.DAY), barcode = "5000119000000",
    )
    val alert = FoodAlert(
        id = "FSA-PRIN-01-2026",
        title = "Dairy Co recalls Semi Skimmed Milk because it may contain pieces of plastic",
        shortTitle = "Dairy Co recalls Semi Skimmed Milk",
        kind = FoodAlert.Kind.RECALL,
        created = TestClock.today,
        url = "https://www.food.gov.uk/news-alerts/alert/fsa-prin-01-2026",
        business = "Dairy Co",
        products = listOf(
            FoodAlert.Product(
                name = "Semi Skimmed Milk", packSize = "2 pints", productCode = "5000119000000",
                batches = listOf(FoodAlert.Batch(batchCode = "L6232")),
            ),
        ),
        risk = "The product may contain small pieces of plastic.",
        consumerAdvice = "Do not drink this product. Return it to the store for a full refund.",
    )
    return item to alert
}

/** An allergy alert for undeclared milk that matches nothing in a fridge. */
fun milkAllergyAlert() = FoodAlert(
    id = "FSA-AA-02-2026",
    title = "Choc Co does not declare milk in its Dark Chocolate",
    kind = FoodAlert.Kind.ALLERGY,
    created = TestClock.today.minus(2, DateTimeUnit.DAY),
    business = "Choc Co",
    products = listOf(FoodAlert.Product(name = "Dark Chocolate", packSize = "100g")),
    allergens = listOf("Milk"),
)

/** A store whose cache already holds `alerts` (call `load()` or let a sync load it). */
fun recallStoreWith(
    alerts: List<FoodAlert> = emptyList(),
    client: FakeAlerts = FakeAlerts(alerts),
    persistence: FakeRecallPersistence = FakeRecallPersistence(alerts),
    clock: AppClock = TestClock.fixed(),
) = RecallStore(client, persistence, clock)
```

If `FoodAlert`'s constructor has no `risk` parameter, or its `Product` has no `packSize` parameter, check `core/model/FoodAlert.kt:13-75` and use the real names.

- [ ] **Step 2: Write the failing store test**

`android/app/src/test/kotlin/com/mazesys/zerobite/recalls/RecallStoreTest.kt`:

```kotlin
package com.mazesys.zerobite.recalls

import com.mazesys.zerobite.testing.FakeAlerts
import com.mazesys.zerobite.testing.FakeRecallPersistence
import com.mazesys.zerobite.testing.MutableClock
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.milkAllergyAlert
import com.mazesys.zerobite.testing.recalledMilk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class RecallStoreTest {

    private val milk = recalledMilk()
    private val clock = MutableClock()
    private val client = FakeAlerts()
    private val persistence = FakeRecallPersistence()
    private val store = RecallStore(client, persistence, clock)

    @Test
    fun `loads the cache and marks once`() = runBlocking {
        persistence.alerts = listOf(milk.second)
        persistence.marks = RecallMarks(lastChecked = TestClock.now, dismissed = setOf("x"))
        store.load()
        persistence.alerts = emptyList()
        store.load()
        assertEquals(listOf(milk.second), store.state.value.alerts)
        assertEquals(setOf("x"), store.state.value.marks.dismissed)
        assertTrue(store.state.value.isLoaded)
    }

    @Test
    fun `refresh fetches 120 days, newest first, and saves`() = runBlocking {
        client.alerts = listOf(milkAllergyAlert(), milk.second)
        assertTrue(store.refresh())
        assertEquals(listOf(TestClock.today.minus(120, DateTimeUnit.DAY)), client.requests)
        assertEquals(listOf(milk.second.id, milkAllergyAlert().id), store.state.value.alerts.map { it.id })
        assertEquals(store.state.value.alerts, persistence.alerts)
        assertEquals(TestClock.now, store.state.value.lastChecked)
        assertEquals(TestClock.now, persistence.marks.lastChecked)
        assertFalse(store.state.value.isRefreshing)
    }

    @Test
    fun `refresh is throttled for six hours unless forced`() = runBlocking {
        store.refresh()
        store.refresh()
        assertEquals(1, client.requests.size)
        clock.instant = TestClock.now + 6.hours + 1.minutes
        store.refresh()
        assertEquals(2, client.requests.size)
        store.refresh(force = true)
        assertEquals(3, client.requests.size)
    }

    @Test
    fun `a failed refresh keeps the cache and reports it`() = runBlocking {
        val checked = TestClock.now - 7.hours
        persistence.alerts = listOf(milk.second)
        persistence.marks = RecallMarks(lastChecked = checked)
        client.failure = java.io.IOException("offline")

        assertFalse(store.refresh())
        assertEquals(listOf(milk.second), store.state.value.alerts)
        assertEquals(RecallStore.UNREACHABLE, store.state.value.errorMessage)
        assertEquals(checked, store.state.value.lastChecked)
        assertTrue(store.needsRefresh, "the next foreground tries again")

        client.failure = null
        assertTrue(store.refresh())
        assertNull(store.state.value.errorMessage)
    }

    @Test
    fun `dismissed and notified marks persist`() = runBlocking {
        persistence.alerts = listOf(milk.second)
        store.load()
        val match = store.state.value.matches(listOf(milk.first), TestClock.today).single()

        assertEquals(listOf(match), store.claimUnnotified(listOf(match)))
        assertEquals(emptyList(), store.claimUnnotified(listOf(match)))
        assertEquals(setOf(match.id), persistence.marks.notified)

        store.dismiss(match)
        assertEquals(setOf(match.id), persistence.marks.dismissed)
        assertTrue(store.state.value.matches(listOf(milk.first), TestClock.today).isEmpty())
    }

    @Test
    fun `used-up items never match`() = runBlocking {
        persistence.alerts = listOf(milk.second)
        store.load()
        val used = milk.first.copy(consumedAt = TestClock.now)
        assertTrue(store.state.value.matches(listOf(used), TestClock.today).isEmpty())
    }
}
```

- [ ] **Step 3: Write the failing persistence test**

`android/app/src/test/kotlin/com/mazesys/zerobite/recalls/LocalRecallPersistenceTest.kt`:

```kotlin
package com.mazesys.zerobite.recalls

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.milkAllergyAlert
import com.mazesys.zerobite.testing.recalledMilk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals

class LocalRecallPersistenceTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun open(job: Job) = LocalRecallPersistence(
        File(folder.root, "fsa-food-alerts.json"),
        PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { File(folder.root, "recalls.preferences_pb") },
    )

    @Test
    fun `alerts and marks survive a restart`() = runBlocking {
        val alerts = listOf(recalledMilk().second, milkAllergyAlert())
        val marks = RecallMarks(lastChecked = TestClock.now, dismissed = setOf("a|b"), notified = setOf("c|d"))
        val first = Job()
        open(first).apply {
            saveAlerts(alerts)
            saveMarks(marks)
        }
        first.cancelAndJoin()

        val second = Job()
        val reopened = open(second)
        assertEquals(alerts, reopened.loadAlerts())
        assertEquals(marks, reopened.loadMarks())
        second.cancelAndJoin()
    }

    @Test
    fun `a missing or corrupt cache reads as empty`() = runBlocking {
        val job = Job()
        val persistence = open(job)
        assertEquals(emptyList(), persistence.loadAlerts())
        File(folder.root, "fsa-food-alerts.json").writeText("{not json")
        assertEquals(emptyList(), persistence.loadAlerts())
        assertEquals(RecallMarks(), persistence.loadMarks())
        job.cancelAndJoin()
    }
}
```

- [ ] **Step 4: Write the failing date text test**

`android/app/src/test/kotlin/com/mazesys/zerobite/ui/format/DateTextTest.kt`:

```kotlin
package com.mazesys.zerobite.ui.format

import com.mazesys.zerobite.testing.TestClock
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DateTextTest {

    private fun ago(duration: Duration) = DateText.relative(TestClock.now - duration, TestClock.now)

    @Test
    fun `day and short month`() {
        assertEquals("7 Sep", DateText.dayMonth(TestClock.today))
    }

    @Test
    fun `relative times read like iOS named presentation`() {
        assertEquals("now", ago(20.seconds))
        assertEquals("1 minute ago", ago(1.minutes))
        assertEquals("5 minutes ago", ago(5.minutes))
        assertEquals("1 hour ago", ago(61.minutes))
        assertEquals("3 hours ago", ago(3.hours))
        assertEquals("yesterday", ago(26.hours))
        assertEquals("3 days ago", ago(3.days))
        assertEquals("last week", ago(8.days))
        assertEquals("2 weeks ago", ago(15.days))
    }

    @Test
    fun `a time in the future reads as now`() {
        assertEquals("now", DateText.relative(TestClock.now + 5.minutes, TestClock.now))
    }
}
```

- [ ] **Step 5: Run them to verify they fail**

Run: `cd android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests '*RecallStoreTest*' --tests '*LocalRecallPersistenceTest*' --tests '*DateTextTest*'`
Expected: compilation FAILS with unresolved `RecallStore`, `RecallMarks`, `RecallPersistence`, `LocalRecallPersistence`, `DateText.relative`, `DateText.dayMonth`.

- [ ] **Step 6: Implement `RecallStore`**

`android/app/src/main/kotlin/com/mazesys/zerobite/recalls/RecallStore.kt`:

```kotlin
package com.mazesys.zerobite.recalls

import com.mazesys.zerobite.core.alerts.FoodAlertsFetching
import com.mazesys.zerobite.core.alerts.RecallMatch
import com.mazesys.zerobite.core.alerts.RecallMatcher
import com.mazesys.zerobite.core.model.FoodAlert
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.time.AppClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** When the alerts were last fetched, "Not my batch" dismissals, and matches already notified. */
data class RecallMarks(
    val lastChecked: Instant? = null,
    val dismissed: Set<String> = emptySet(),
    val notified: Set<String> = emptySet(),
)

/** Where the alert cache and the marks live between launches. */
interface RecallPersistence {
    suspend fun loadAlerts(): List<FoodAlert>
    suspend fun saveAlerts(alerts: List<FoodAlert>)
    suspend fun loadMarks(): RecallMarks
    suspend fun saveMarks(marks: RecallMarks)
}

data class RecallState(
    /** Newest first. */
    val alerts: List<FoodAlert> = emptyList(),
    val marks: RecallMarks = RecallMarks(),
    val isLoaded: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
) {
    val lastChecked: Instant? get() = marks.lastChecked

    /** Alerts that may concern items still in the fridge, minus the ones the user dismissed. */
    fun matches(items: List<FoodItem>, today: LocalDate): List<RecallMatch> =
        RecallMatcher(today).matches(alerts, items.filterNot { it.isConsumed }, marks.dismissed)
}

/**
 * Recent UK food alerts from the Food Standards Agency, cached on disk, with the user's
 * dismissals. Matching runs on demand so it always reflects the current fridge. Port of iOS
 * RecallStore.
 */
class RecallStore(
    private val client: FoodAlertsFetching,
    private val persistence: RecallPersistence,
    private val clock: AppClock,
) {
    private val _state = MutableStateFlow(RecallState())
    val state: StateFlow<RecallState> = _state.asStateFlow()

    private val loading = Mutex()
    private val refreshing = Mutex()
    private val marking = Mutex()

    /** Reads the cache and marks; later calls do nothing. */
    suspend fun load() = loading.withLock {
        if (_state.value.isLoaded) return@withLock
        val alerts = persistence.loadAlerts()
        val marks = persistence.loadMarks()
        _state.update { it.copy(alerts = alerts, marks = marks, isLoaded = true) }
    }

    val needsRefresh: Boolean
        get() {
            val last = _state.value.lastChecked ?: return true
            return clock.now() - last > REFRESH_INTERVAL
        }

    /**
     * Fetches the last [HISTORY_DAYS] of alerts unless the cache is fresh (or `force`). Returns
     * false if the request failed; the cached alerts stay and `errorMessage` says so.
     */
    suspend fun refresh(force: Boolean = false): Boolean {
        load()
        if (!force && !needsRefresh) return true
        if (!refreshing.tryLock()) return true
        try {
            _state.update { it.copy(isRefreshing = true) }
            val fetched = try {
                client.fetchAlerts(clock.today().minus(HISTORY_DAYS, DateTimeUnit.DAY), _state.value.alerts)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = UNREACHABLE) }
                return false
            }
            val sorted = fetched.sortedByDescending { it.created }
            persistence.saveAlerts(sorted)
            _state.update { it.copy(alerts = sorted, errorMessage = null) }
            updateMarks { it.copy(lastChecked = clock.now()) }
            return true
        } finally {
            _state.update { it.copy(isRefreshing = false) }
            refreshing.unlock()
        }
    }

    /** "Not my batch": hides this alert for this item for good. */
    suspend fun dismiss(match: RecallMatch) {
        load()
        updateMarks { it.copy(dismissed = it.dismissed + match.id) }
    }

    /** The matches not notified before, now recorded as notified, so each one alerts only once. */
    suspend fun claimUnnotified(matches: List<RecallMatch>): List<RecallMatch> {
        load()
        var fresh = emptyList<RecallMatch>()
        updateMarks { marks ->
            fresh = matches.filter { it.id !in marks.notified }
            if (fresh.isEmpty()) marks else marks.copy(notified = marks.notified + fresh.map { it.id })
        }
        return fresh
    }

    private suspend fun updateMarks(transform: (RecallMarks) -> RecallMarks) = marking.withLock {
        val old = _state.value.marks
        val new = transform(old)
        if (new == old) return@withLock
        _state.update { it.copy(marks = new) }
        persistence.saveMarks(new)
    }

    companion object {
        /** How far back to fetch; RecallMatcher ignores older notices anyway. */
        const val HISTORY_DAYS = 120

        /** Don't hit the API more often than this unless the user asks. */
        val REFRESH_INTERVAL = 6.hours

        const val UNREACHABLE = "Couldn't reach the Food Standards Agency. Showing the last alerts we downloaded."
    }
}
```

Check that `RecallMatch` and `RecallMatcher` are in package `com.mazesys.zerobite.core.alerts` (`core/alerts/RecallMatcher.kt`), and fix the imports if not.

- [ ] **Step 7: Implement `LocalRecallPersistence`**

`android/app/src/main/kotlin/com/mazesys/zerobite/recalls/LocalRecallPersistence.kt`:

```kotlin
package com.mazesys.zerobite.recalls

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.mazesys.zerobite.core.model.FoodAlert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.time.Instant

/** Alert cache as a JSON file; marks in the `recalls` DataStore under the iOS key names. */
class LocalRecallPersistence(
    private val cacheFile: File,
    private val store: DataStore<Preferences>,
) : RecallPersistence {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun loadAlerts(): List<FoodAlert> = withContext(Dispatchers.IO) {
        if (!cacheFile.exists()) return@withContext emptyList()
        // A cache we can't read is just an empty cache: the next refresh rewrites it.
        runCatching { json.decodeFromString<List<FoodAlert>>(cacheFile.readText()) }.getOrDefault(emptyList())
    }

    override suspend fun saveAlerts(alerts: List<FoodAlert>) = withContext(Dispatchers.IO) {
        runCatching {
            val temp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
            temp.writeText(json.encodeToString(alerts))
            temp.renameTo(cacheFile)
        }
        Unit
    }

    override suspend fun loadMarks(): RecallMarks {
        val prefs = store.data.first()
        return RecallMarks(
            lastChecked = prefs[LAST_CHECKED]?.let(Instant::fromEpochMilliseconds),
            dismissed = prefs[DISMISSED].orEmpty(),
            notified = prefs[NOTIFIED].orEmpty(),
        )
    }

    override suspend fun saveMarks(marks: RecallMarks) {
        store.edit { prefs ->
            val checked = marks.lastChecked
            if (checked == null) prefs.remove(LAST_CHECKED) else prefs[LAST_CHECKED] = checked.toEpochMilliseconds()
            prefs[DISMISSED] = marks.dismissed
            prefs[NOTIFIED] = marks.notified
        }
    }

    private companion object {
        val LAST_CHECKED = longPreferencesKey("recalls.lastChecked")
        val DISMISSED = stringSetPreferencesKey("recalls.dismissedMatches")
        val NOTIFIED = stringSetPreferencesKey("recalls.notifiedMatches")
    }
}
```

The test's `lastChecked` round-trips through milliseconds, so `TestClock.now` must have no sub-millisecond part. It doesn't (`2026-09-07T08:00:00Z`).

- [ ] **Step 8: Add `dayMonth` and `relative` to `DateText`**

In `ui/format/DateText.kt`, add the import `kotlin.time.Instant` and these members:

```kotlin
    /** "7 Sep": alert rows. */
    fun dayMonth(date: LocalDate): String = "${date.day} ${MONTHS_SHORT[date.month.number - 1]}"

    /** "5 minutes ago", "yesterday", "last week": iOS `.relative(presentation: .named)` in English. */
    fun relative(then: Instant, now: Instant): String {
        val minutes = (now - then).inWholeMinutes
        if (minutes < 1) return "now"
        if (minutes < 60) return plural(minutes, "minute")
        val hours = minutes / 60
        if (hours < 24) return plural(hours, "hour")
        val days = hours / 24
        if (days == 1L) return "yesterday"
        if (days < 7) return plural(days, "day")
        val weeks = days / 7
        return if (weeks == 1L) "last week" else plural(weeks, "week")
    }

    private fun plural(n: Long, unit: String) = if (n == 1L) "1 $unit ago" else "$n ${unit}s ago"
```

- [ ] **Step 9: Wire `recalls` into `AppDependencies` and the fakes**

In `AppDependencies.kt`:
- Add the imports `com.mazesys.zerobite.core.alerts.FsaAlertsClient`, `com.mazesys.zerobite.recalls.LocalRecallPersistence`, `com.mazesys.zerobite.recalls.RecallStore` and `java.io.File`.
- Below the `settingsDataStore` line, add:

```kotlin
/** Recall marks (last checked, dismissed, notified), kept apart from user settings. */
private val Context.recallsDataStore: DataStore<Preferences> by preferencesDataStore(name = "recalls")
```

- Add the constructor parameter `val recalls: RecallStore,` after `val productLookup: ProductLookup,`.
- In `live`, after `productLookup = OpenFoodFactsClient(http),`, add:

```kotlin
                recalls = RecallStore(
                    FsaAlertsClient(http),
                    LocalRecallPersistence(File(app.filesDir, "fsa-food-alerts.json"), app.recallsDataStore),
                    SystemAppClock,
                ),
```

In Fakes, add `alerts: FakeAlerts = FakeAlerts(),` and `recallPersistence: FakeRecallPersistence = FakeRecallPersistence(),` to `testDependencies` after `productLookup`, and pass `recalls = RecallStore(alerts, recallPersistence, clock)`.

- [ ] **Step 10: Run the tests to verify they pass**

Run: `cd android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. `RecallStoreTest` (6), `LocalRecallPersistenceTest` (2) and `DateTextTest` (3) pass; nothing else fails.

- [ ] **Step 11: Commit**

```bash
git add android/app/src/main/kotlin/com/mazesys/zerobite/recalls android/app/src/test/kotlin/com/mazesys/zerobite/recalls \
  android/app/src/main/kotlin/com/mazesys/zerobite/ui/format/DateText.kt android/app/src/test/kotlin/com/mazesys/zerobite/ui/format \
  android/app/src/main/kotlin/com/mazesys/zerobite/AppDependencies.kt android/app/src/test/kotlin/com/mazesys/zerobite/testing/Fakes.kt
git commit -m "Fetch, cache and throttle FSA food alerts, with dismissals that persist"
```

---

### Task 6: Recall sync and recall notifications

**Files:**
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/recalls/RecallNotifier.kt`
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/recalls/RecallSync.kt`
- Modify: `reminders/Notifications.kt`, `AppDependencies.kt`, `ZeroBiteApplication.kt`, `MainActivity.kt`, `screens/settings/SettingsViewModel.kt`, `testing/Fakes.kt`
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/recalls/RecallSyncTest.kt`
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/recalls/RecallNotificationsTest.kt`
- Test: append to `android/app/src/test/kotlin/com/mazesys/zerobite/screens/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `RecallStore` (Task 5); `Notifications.post`/`canPost` (Plan 3); `InventoryRepository.items`; `SettingsRepository.current()`/`state`.
- Produces:
  - `Notifications.CHANNEL_RECALLS = "recalls"`
  - `Notifications.canPost(context, channel = CHANNEL_EXPIRY)` and `Notifications.channelBlocked(context, channel)`
  - `Notifications.post(…, channel = CHANNEL_EXPIRY)`
  - `interface RecallNotifier { fun post(match: RecallMatch) }`
  - `object RecallNotice { fun tag(m); fun title(m); fun body(m) }`
  - `class AndroidRecallNotifier(context)`
  - `class RecallSync(recalls, inventory, settings, clock, notifier)` with `start(scope)` and `suspend refresh(force = false)`
  - `AppDependencies.recallNotifier` and `AppDependencies.recallSync`
  - `FakeRecallNotifier`
  - `testDependencies(recallNotifier = …)`

**Copy:**
- The notification title and body come from iOS `postRecallNotice`.
- Android adaptation: channel name "Food recalls", description "When something in your fridge may be affected by a UK food recall."

- [ ] **Step 1: Add the notifier fake**

Append to Fakes (import `com.mazesys.zerobite.core.alerts.RecallMatch`, `com.mazesys.zerobite.recalls.RecallNotifier`):

```kotlin
class FakeRecallNotifier : RecallNotifier {
    val posted = mutableListOf<RecallMatch>()
    override fun post(match: RecallMatch) {
        posted += match
    }
}
```

Add `recallNotifier: FakeRecallNotifier = FakeRecallNotifier(),` to `testDependencies` after `recallPersistence` and pass `recallNotifier = recallNotifier`.

- [ ] **Step 2: Write the failing sync test**

`android/app/src/test/kotlin/com/mazesys/zerobite/recalls/RecallSyncTest.kt`:

```kotlin
package com.mazesys.zerobite.recalls

import com.mazesys.zerobite.data.SettingsSnapshot
import com.mazesys.zerobite.testing.FakeAlerts
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeRecallNotifier
import com.mazesys.zerobite.testing.FakeRecallPersistence
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.recalledMilk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecallSyncTest {

    private val milk = recalledMilk()
    private val client = FakeAlerts(listOf(milk.second))
    private val persistence = FakeRecallPersistence()
    private val inventory = FakeInventoryRepository(listOf(milk.first))
    private val settings = FakeSettings()
    private val notifier = FakeRecallNotifier()
    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())

    private fun sync(store: RecallStore = RecallStore(client, persistence, TestClock.fixed())) =
        RecallSync(store, inventory, settings, TestClock.fixed(), notifier).also { it.start(scope) }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `new matches are notified once`() = runBlocking {
        val sync = sync()
        sync.refresh(force = true)
        assertEquals(listOf(milk.first.id), notifier.posted.map { it.itemId })

        sync.refresh(force = true)
        inventory.add(milk.first.copy(id = "other", name = "Bread", barcode = null))
        assertEquals(1, notifier.posted.size)
    }

    @Test
    fun `each match is notified once, even across restarts`() = runBlocking {
        sync().refresh(force = true)
        assertEquals(1, notifier.posted.size)

        // A fresh process: same persisted cache and marks, new store and sync.
        sync(RecallStore(client, persistence, TestClock.fixed()))
        assertEquals(1, notifier.posted.size)
    }

    @Test
    fun `a match found by adding food is notified`() = runBlocking {
        inventory.delete(milk.first.id)
        val sync = sync()
        sync.refresh(force = true)
        assertTrue(notifier.posted.isEmpty())
        inventory.add(milk.first)
        assertEquals(1, notifier.posted.size)
    }

    @Test
    fun `at most three notices per sync, the rest count as told`() = runBlocking {
        (1..5).forEach { inventory.add(milk.first.copy(id = "milk-$it")) }
        sync().refresh(force = true)
        assertEquals(3, notifier.posted.size)
        assertEquals(6, persistence.marks.notified.size)
    }

    @Test
    fun `recall checks off means no fetch and no notices`() = runBlocking {
        settings.update { it.copy(recallChecksEnabled = false) }
        persistence.alerts = listOf(milk.second)
        val sync = sync()
        sync.refresh(force = true)
        assertTrue(client.requests.isEmpty())
        assertTrue(notifier.posted.isEmpty())
    }
}
```

- [ ] **Step 3: Write the failing notification test**

`android/app/src/test/kotlin/com/mazesys/zerobite/recalls/RecallNotificationsTest.kt`:

```kotlin
package com.mazesys.zerobite.recalls

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.mazesys.zerobite.core.alerts.RecallMatcher
import com.mazesys.zerobite.reminders.Notifications
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.recalledMilk
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RecallNotificationsTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val milk = recalledMilk()
    private val match = RecallMatcher(TestClock.today).matches(listOf(milk.second), listOf(milk.first)).single()

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifications.createChannels(app)
    }

    @Test
    fun `copy matches iOS`() {
        assertEquals("Recall: check your Milk", RecallNotice.title(match))
        assertEquals("Dairy Co recalls Semi Skimmed Milk. Check the pack before eating it.", RecallNotice.body(match))
        assertEquals("zerobite.recall.${match.id}", RecallNotice.tag(match))
        assertEquals("Possible recall: Milk", RecallNotice.title(match.copy(confidence = com.mazesys.zerobite.core.alerts.RecallMatch.Confidence.POSSIBLE)))
    }

    @Test
    fun `posts on the recalls channel and opens the item`() {
        AndroidRecallNotifier(app).post(match)
        val posted = shadowOf(manager).allNotifications.single()
        assertEquals(Notifications.CHANNEL_RECALLS, posted.channelId)
        assertEquals("Recall: check your Milk", posted.extras.getString(NotificationCompat.EXTRA_TITLE))
        val tap = shadowOf(posted.contentIntent).savedIntent
        assertEquals(milk.first.id, tap.getStringExtra(Notifications.EXTRA_ITEM_ID))
    }

    @Test
    fun `blocking recalls leaves reminders on`() {
        manager.deleteNotificationChannel(Notifications.CHANNEL_RECALLS)
        manager.createNotificationChannel(NotificationChannel(Notifications.CHANNEL_RECALLS, "Food recalls", NotificationManager.IMPORTANCE_NONE))
        AndroidRecallNotifier(app).post(match)
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
        assertFalse(Notifications.canPost(app, Notifications.CHANNEL_RECALLS))
        assertTrue(Notifications.canPost(app))
    }
}
```

(`shadowOf(contentIntent).savedIntent` is the form Plan 3's `ReminderReceiverTest` uses.)

- [ ] **Step 4: Add the Settings test**

Append inside `SettingsViewModelTest` (it already builds `vm` from `testDependencies(...)`). Add a `FakeAlerts` field and pass it as `alerts = alerts` in that `testDependencies` call:

```kotlin
    @Test
    fun `turning checks back on refreshes now`() = runBlocking {
        vm.setRecallChecksEnabled(false)
        assertTrue(alerts.requests.isEmpty())
        vm.setRecallChecksEnabled(true)
        assertEquals(1, alerts.requests.size)
    }
```

with the field `private val alerts = com.mazesys.zerobite.testing.FakeAlerts()` declared beside `notifier`.

- [ ] **Step 5: Run them to verify they fail**

Run: `cd android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests '*RecallSyncTest*' --tests '*RecallNotificationsTest*' --tests '*SettingsViewModelTest*'`
Expected: compilation FAILS with unresolved `RecallSync`, `RecallNotice`, `AndroidRecallNotifier`, `CHANNEL_RECALLS`.

- [ ] **Step 6: Give `Notifications` a recalls channel and per-channel checks**

In `reminders/Notifications.kt`:

```kotlin
    const val CHANNEL_RECALLS = "recalls"

    fun createChannels(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_EXPIRY, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Expiry reminders")
                .setDescription("A heads-up before food expires, on the day, and if it slips past.")
                .build(),
        )
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_RECALLS, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName("Food recalls")
                .setDescription("When something in your fridge may be affected by a UK food recall.")
                .build(),
        )
    }

    /** True when the app may post on `channel` (runtime permission on Android 13+, app and channel not blocked). */
    fun canPost(context: Context, channel: String = CHANNEL_EXPIRY): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled() && !channelBlocked(context, channel)
    }

    /** The user can block one channel while leaving the app's other notifications on. */
    fun channelBlocked(context: Context, channel: String): Boolean =
        NotificationManagerCompat.from(context).getNotificationChannel(channel)?.importance == NotificationManagerCompat.IMPORTANCE_NONE

    fun reminderChannelBlocked(context: Context): Boolean = channelBlocked(context, CHANNEL_EXPIRY)
```

Change `post`'s signature to `fun post(context: Context, tag: String, title: String, body: String, itemId: String?, channel: String = CHANNEL_EXPIRY)`. Inside it, use `if (!canPost(context, channel)) return` and `NotificationCompat.Builder(context, channel)`.

- [ ] **Step 7: Implement `RecallNotifier`**

`android/app/src/main/kotlin/com/mazesys/zerobite/recalls/RecallNotifier.kt`:

```kotlin
package com.mazesys.zerobite.recalls

import android.content.Context
import com.mazesys.zerobite.core.alerts.RecallMatch
import com.mazesys.zerobite.reminders.Notifications

interface RecallNotifier {
    fun post(match: RecallMatch)
}

/** Copy from iOS ExpiryNotificationService.postRecallNotice. */
object RecallNotice {
    fun tag(match: RecallMatch) = "zerobite.recall.${match.id}"

    fun title(match: RecallMatch) =
        if (match.confidence == RecallMatch.Confidence.LIKELY) "Recall: check your ${match.itemName}" else "Possible recall: ${match.itemName}"

    fun body(match: RecallMatch) = "${match.alert.displayTitle}. Check the pack before eating it."
}

/** Immediate notice that something in the fridge may be recalled; tapping it opens the item. */
class AndroidRecallNotifier(private val context: Context) : RecallNotifier {
    override fun post(match: RecallMatch) = Notifications.post(
        context,
        tag = RecallNotice.tag(match),
        title = RecallNotice.title(match),
        body = RecallNotice.body(match),
        itemId = match.itemId,
        channel = Notifications.CHANNEL_RECALLS,
    )
}
```

- [ ] **Step 8: Implement `RecallSync`**

`android/app/src/main/kotlin/com/mazesys/zerobite/recalls/RecallSync.kt`:

```kotlin
package com.mazesys.zerobite.recalls

import com.mazesys.zerobite.core.inventory.InventoryRepository
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.time.AppClock
import com.mazesys.zerobite.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps recall notices current: whenever the fridge, the alerts or the dismissals change, posts
 * one notice per match the user hasn't heard about (at most three at a time, like iOS
 * notifyNewRecallMatches). Fetching happens on foreground via [refresh].
 */
class RecallSync(
    private val recalls: RecallStore,
    private val inventory: InventoryRepository,
    private val settings: SettingsRepository,
    private val clock: AppClock,
    private val notifier: RecallNotifier,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            recalls.load()
            combine(
                inventory.items,
                recalls.state.map { it.alerts to it.marks.dismissed }.distinctUntilChanged(),
                settings.state.map { it.recallChecksEnabled }.distinctUntilChanged(),
            ) { items, _, _ -> items }
                .collectLatest { items -> notifyNew(items) }
        }
    }

    /** Fetches new alerts (throttled unless `force`) when recall checks are on. */
    suspend fun refresh(force: Boolean = false) {
        if (!settings.current().recallChecksEnabled) return
        recalls.refresh(force)
    }

    private suspend fun notifyNew(items: List<FoodItem>) {
        // Read the stored setting, not the flow's not-yet-loaded default.
        if (!settings.current().recallChecksEnabled) return
        val fresh = recalls.claimUnnotified(recalls.state.value.matches(items, clock.today()))
        fresh.take(MAX_NOTICES).forEach(notifier::post)
    }

    private companion object {
        const val MAX_NOTICES = 3
    }
}
```

- [ ] **Step 9: Wire it up**

In `AppDependencies.kt`:
- Add the imports `com.mazesys.zerobite.recalls.AndroidRecallNotifier`, `com.mazesys.zerobite.recalls.RecallNotifier` and `com.mazesys.zerobite.recalls.RecallSync`.
- Add the constructor parameter `val recallNotifier: RecallNotifier,` after `val recalls: RecallStore,`.
- Below the `val reminders = …` line, add:

```kotlin
    val recallSync = RecallSync(recalls, inventory, settings, clock, recallNotifier)
```

- In `live`, add `recallNotifier = AndroidRecallNotifier(app),` after the `recalls = …` argument.

In `ZeroBiteApplication.onCreate`, after `dependencies.reminders.start(dependencies.appScope)`:

```kotlin
        dependencies.recallSync.start(dependencies.appScope)
```

In `MainActivity.onResume`, after the reminders line:

```kotlin
        // Throttled to every six hours; runs on the app scope so leaving the screen doesn't cancel it.
        dependencies.appScope.launch { dependencies.recallSync.refresh() }
```

In `SettingsViewModel`, replace `setRecallChecksEnabled` with:

```kotlin
    fun setRecallChecksEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.update { it.copy(recallChecksEnabled = enabled) }
            if (enabled) dependencies.appScope.launch { dependencies.recallSync.refresh(force = true) }
        }
    }
```

- [ ] **Step 10: Run the tests to verify they pass**

Run: `cd android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. `RecallSyncTest` (5), `RecallNotificationsTest` (3) and the new Settings test pass, and Plan 3's notification tests still pass.

- [ ] **Step 11: Commit**

```bash
git add android/app/src/main/kotlin/com/mazesys/zerobite android/app/src/test/kotlin/com/mazesys/zerobite
git commit -m "Notify once per new recall match on a separate Food recalls channel"
```

---

### Task 7: Recalls in the fridge: banner, card flags and item-detail cards

**Files:**
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/ui/components/RecallViews.kt`
- Modify: `screens/inventory/InventoryViewModel.kt`, `screens/inventory/InventoryScreen.kt`, `screens/inventory/ItemDetailViewModel.kt`, `screens/inventory/ItemDetailScreen.kt`, `screens/shared/DetailEntries.kt`, `screens/root/MainScreen.kt`
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/ui/components/RecallViewsTest.kt`
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/screens/inventory/RecallViewModelsTest.kt`
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/screens/inventory/RecallScreensTest.kt`

**Interfaces:**
- Consumes: `RecallStore`, `RecallState.matches` (Task 5); `RecallMatch`, `FoodAlert`, `FoodAlert.Kind.label`, `FoodAlert.Batch.summary` (`:core`); `AppCard`, `IconTile`, `Overline`, `Tag`, `AppButton`; `DateText.abbreviated`, `DateText.dayMonth`; `FoodItemCard(isRecallFlagged = …)` (Plan 2).
- Produces:
  - `val RecallMatch.headline`, `val RecallMatch.explanation`
  - `fun FoodAlert.Kind.tint(colors): Color`, `val FoodAlert.Kind.icon: ImageVector`
  - `RecallBanner(matches, onOpen, modifier)`
  - `RecallCard(match, onNotMine, onThrowAway, modifier)`
  - `AlertProductDetails(product)`
  - `FoodAlertRow(alert, highlight, modifier)`
  - `FlowTags(tags, tint)`
  - `InventoryUiState.recallMatches`, `InventoryUiState.recalledItemIds`, `InventoryViewModel(…, recalls: RecallStore? = null)`, `InventoryViewModel.showRecalledItem(match)`
  - `ItemDetailUiState.recallMatches`, `ItemDetailViewModel(…, recalls: RecallStore? = null)`, `ItemDetailViewModel.dismissRecall(match)`
  - Test tags: `inventory.recallBanner`, `item.recallCard`, `recall.notMine`.

- [ ] **Step 1: Write the failing component test**

`android/app/src/test/kotlin/com/mazesys/zerobite/ui/components/RecallViewsTest.kt`:

```kotlin
package com.mazesys.zerobite.ui.components

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mazesys.zerobite.core.alerts.RecallMatch
import com.mazesys.zerobite.core.alerts.RecallMatch.Reason
import com.mazesys.zerobite.core.alerts.RecallMatcher
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.recalledMilk
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class RecallViewsTest {

    @get:Rule
    val compose = createComposeRule()

    private val milk = recalledMilk()
    private val match = RecallMatcher(TestClock.today).matches(listOf(milk.second), listOf(milk.first)).single()

    @Test
    fun `headline and explanation read like iOS`() {
        assertEquals("Your milk may be recalled", match.headline)
        assertEquals("The barcode is on the notice.", match.explanation)
        val possible = match.copy(confidence = RecallMatch.Confidence.POSSIBLE, reasons = setOf(Reason.NAME, Reason.BRAND, Reason.DATE))
        assertEquals("Possible recall: Milk", possible.headline)
        assertEquals("The brand matches, the product name matches, your date matches a recalled batch.", possible.explanation)
        assertEquals("Every batch is affected.", match.copy(reasons = setOf(Reason.ALL_BATCHES)).explanation)
        assertEquals("", match.copy(reasons = emptySet()).explanation)
    }

    @Test
    fun `banner counts matches and opens the first`() {
        var opened: RecallMatch? = null
        val second = match.copy(itemId = "milk-2")
        compose.setContent { ZeroBiteTheme { RecallBanner(listOf(match, second), onOpen = { opened = it }) } }
        compose.onNodeWithText("2 possible recalls").assertExists()
        compose.onNodeWithText("Your milk may be recalled").assertExists()
        compose.onNodeWithText("Check the pack").assertExists()
        compose.onNodeWithTag("inventory.recallBanner").performClick()
        assertEquals(match, opened)
    }

    @Test
    fun `card shows the batch and both actions`() {
        var notMine = 0
        var remove = 0
        compose.setContent { ZeroBiteTheme { RecallCard(match, onNotMine = { notMine++ }, onThrowAway = { remove++ }) } }
        compose.onNodeWithText("Product recall · 7 Sep 2026").assertExists()
        compose.onNodeWithText("The barcode is on the notice. Compare the details below with your pack.").assertExists()
        compose.onNodeWithText("Batch L6232").assertExists()
        compose.onNodeWithText("Do not drink this product. Return it to the store for a full refund.").assertExists()
        compose.onNodeWithText("FSA notice").assertExists()
        compose.onNodeWithTag("recall.notMine").performClick()
        compose.onNodeWithText("It's affected: remove it").performClick()
        assertEquals(1, notMine)
        assertEquals(1, remove)
    }
}
```

- [ ] **Step 2: Write the failing view model test**

`android/app/src/test/kotlin/com/mazesys/zerobite/screens/inventory/RecallViewModelsTest.kt`:

```kotlin
package com.mazesys.zerobite.screens.inventory

import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.coordination.InventoryRoute
import com.mazesys.zerobite.data.SettingsSnapshot
import com.mazesys.zerobite.testing.FakeAccess
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeRecipes
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.SequentialIds
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.recallStoreWith
import com.mazesys.zerobite.testing.recalledMilk
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecallViewModelsTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val milk = recalledMilk()
    private val app = AppCoordinator { TestClock.today }
    private val inventory = FakeInventoryRepository(listOf(milk.first))
    private val settings = FakeSettings(SettingsSnapshot(dismissedNotificationTip = true))
    private val store = recallStoreWith(listOf(milk.second)).also { runBlocking { it.load() } }

    private val list by lazy {
        InventoryViewModel(inventory, settings, TestClock.fixed(), SequentialIds(), app.inventory, FakeAccess(), recalls = store)
    }
    private val detail by lazy {
        ItemDetailViewModel(milk.first.id, inventory, settings, FakeRecipes(), TestClock.fixed(), onEdit = {}, onShowRecipe = {}, onDeleted = {}, recalls = store)
    }

    @Test
    fun `recall matches show and flag the item`() {
        val ui = list.state.value
        assertEquals(listOf(milk.first.id), ui.recallMatches.map { it.itemId })
        assertEquals(setOf(milk.first.id), ui.recalledItemIds)
        assertEquals(1, detail.state.value.recallMatches.size)
        list.showRecalledItem(ui.recallMatches.single())
        assertEquals(InventoryRoute.ItemDetail(milk.first.id), app.inventory.backStack.last())
    }

    @Test
    fun `recall checks off hides every match`() = runBlocking {
        settings.update { it.copy(recallChecksEnabled = false) }
        assertTrue(list.state.value.recallMatches.isEmpty())
        assertTrue(detail.state.value.recallMatches.isEmpty())
    }

    @Test
    fun `dismissed matches disappear everywhere`() {
        detail.dismissRecall(detail.state.value.recallMatches.single())
        assertTrue(detail.state.value.recallMatches.isEmpty())
        assertTrue(list.state.value.recallMatches.isEmpty())
    }

    @Test
    fun `used-up items have no recall card`() = runBlocking {
        inventory.markConsumed(listOf(milk.first.id), TestClock.now)
        assertTrue(detail.state.value.recallMatches.isEmpty())
    }
}
```

- [ ] **Step 3: Write the failing screen test**

`android/app/src/test/kotlin/com/mazesys/zerobite/screens/inventory/RecallScreensTest.kt`:

```kotlin
package com.mazesys.zerobite.screens.inventory

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.coordination.InventoryRoute
import com.mazesys.zerobite.data.SettingsSnapshot
import com.mazesys.zerobite.testing.FakeAccess
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeRecipes
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.SequentialIds
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.recallStoreWith
import com.mazesys.zerobite.testing.recalledMilk
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class RecallScreensTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val milk = recalledMilk()
    private val app = AppCoordinator { TestClock.today }
    private val inventory = FakeInventoryRepository(listOf(milk.first))
    private val settings = FakeSettings(SettingsSnapshot(dismissedNotificationTip = true))
    private val store = recallStoreWith(listOf(milk.second)).also { runBlocking { it.load() } }

    @Test
    fun `the recall banner opens the item`() {
        val vm = InventoryViewModel(inventory, settings, TestClock.fixed(), SequentialIds(), app.inventory, FakeAccess(), recalls = store)
        compose.setContent { ZeroBiteTheme { InventoryScreen(vm) } }
        compose.onNodeWithText("Food recall").assertExists()
        compose.onNodeWithTag("inventory.recallBanner").performClick()
        assertEquals(InventoryRoute.ItemDetail(milk.first.id), app.inventory.backStack.last())
    }

    @Test
    fun `not my batch hides the recall card`() {
        val vm = ItemDetailViewModel(milk.first.id, inventory, settings, FakeRecipes(), TestClock.fixed(), onEdit = {}, onShowRecipe = {}, onDeleted = {}, recalls = store)
        compose.setContent { ZeroBiteTheme { ItemDetailScreen(vm, onBack = {}) } }
        compose.onNodeWithTag("item.recallCard").assertExists()
        compose.onNodeWithTag("recall.notMine").performClick()
        compose.onNodeWithTag("item.recallCard").assertDoesNotExist()
    }

    @Test
    fun `it's affected asks before removing`() {
        var deleted = 0
        val vm = ItemDetailViewModel(milk.first.id, inventory, settings, FakeRecipes(), TestClock.fixed(), onEdit = {}, onShowRecipe = {}, onDeleted = { deleted++ }, recalls = store)
        compose.setContent { ZeroBiteTheme { ItemDetailScreen(vm, onBack = {}) } }
        compose.onNodeWithText("It's affected: remove it").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.waitUntil(3_000) { deleted == 1 }
    }
}
```

(The confirm button of Plan 2's "Delete this item?" dialog is "Delete"; the overflow menu's "Delete" item isn't composed while the menu is closed.)

- [ ] **Step 4: Run them to verify they fail**

Run: `cd android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests '*RecallViewsTest*' --tests '*RecallViewModelsTest*' --tests '*RecallScreensTest*'`
Expected: compilation FAILS with unresolved `headline`, `RecallBanner`, `RecallCard`, `recalls =`, `recallMatches`.

- [ ] **Step 5: Implement `RecallViews`**

`android/app/src/main/kotlin/com/mazesys/zerobite/ui/components/RecallViews.kt`:

```kotlin
package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.NoFood
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Scale
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mazesys.zerobite.core.alerts.RecallMatch
import com.mazesys.zerobite.core.model.FoodAlert
import com.mazesys.zerobite.ui.format.DateText
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteColors
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.semibold

val RecallMatch.headline: String
    get() = if (confidence == RecallMatch.Confidence.LIKELY) "Your ${itemName.lowercase()} may be recalled" else "Possible recall: $itemName"

/** Why we think it matches, in plain words. */
val RecallMatch.explanation: String
    get() {
        val parts = buildList {
            if (RecallMatch.Reason.BARCODE in reasons) add("the barcode is on the notice")
            if (RecallMatch.Reason.BRAND in reasons) add("the brand matches")
            if (RecallMatch.Reason.NAME in reasons && RecallMatch.Reason.BARCODE !in reasons) add("the product name matches")
            if (RecallMatch.Reason.DATE in reasons) add("your date matches a recalled batch")
            if (RecallMatch.Reason.ALL_BATCHES in reasons) add("every batch is affected")
        }
        if (parts.isEmpty()) return ""
        return parts.joinToString(", ").replaceFirstChar { it.uppercase() } + "."
    }

fun FoodAlert.Kind.tint(colors: ZeroBiteColors): Color =
    if (this == FoodAlert.Kind.ALLERGY) colors.brandOrange else colors.statusExpired

val FoodAlert.Kind.icon: ImageVector
    get() = if (this == FoodAlert.Kind.ALLERGY) Icons.Rounded.NoFood else Icons.Rounded.Warning

/** Fridge banner: the first match, with a count when there are more. */
@Composable
fun RecallBanner(matches: List<RecallMatch>, onOpen: (RecallMatch) -> Unit, modifier: Modifier = Modifier) {
    val first = matches.firstOrNull() ?: return
    val colors = ZeroBiteTheme.colors
    AppCard(
        modifier.testTag("inventory.recallBanner").clickable(role = Role.Button) { onOpen(first) },
        accent = colors.statusExpired,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            IconTile(Icons.Rounded.Warning, tint = colors.statusExpired, size = 36.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Overline(if (matches.size == 1) "Food recall" else "${matches.size} possible recalls", tint = colors.statusExpired)
                Text(first.headline, style = ZeroBiteTheme.type.subheadline.semibold(), color = colors.textPrimary)
                Text(first.alert.displayTitle, style = ZeroBiteTheme.type.caption, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("Check the pack", style = ZeroBiteTheme.type.caption.semibold(), color = colors.statusExpired, modifier = Modifier.padding(top = 2.dp))
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, contentDescription = null, tint = colors.textMuted,
                modifier = Modifier.padding(top = 10.dp).size(12.dp))
        }
    }
}

/** Item detail: one alert that may concern this item, with what to do about it. */
@Composable
fun RecallCard(match: RecallMatch, onNotMine: () -> Unit, onThrowAway: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ZeroBiteTheme.colors
    val alert = match.alert
    val tint = alert.kind.tint(colors)
    val uriHandler = LocalUriHandler.current
    AppCard(modifier.testTag("item.recallCard"), accent = tint, padding = Spacing.lg) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                IconTile(alert.kind.icon, tint = tint, size = 40.dp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Overline("${alert.kind.label} · ${DateText.abbreviated(alert.created)}", tint = tint)
                    Text(alert.displayTitle, style = ZeroBiteTheme.type.subheadline.semibold(), color = colors.textPrimary)
                }
            }
            Text("${match.explanation} Compare the details below with your pack.", style = ZeroBiteTheme.type.caption, color = colors.textSecondary)
            AlertProductDetails(match.product)
            alert.consumerAdvice?.let { advice ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Overline("What to do")
                    Text(advice, style = ZeroBiteTheme.type.subheadline, color = colors.textPrimary)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppButton("It's affected: remove it", onThrowAway, compact = true, icon = Icons.Rounded.Delete)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppButton("Not my batch", onNotMine, Modifier.weight(1f).testTag("recall.notMine"), kind = ButtonKind.TONAL, compact = true)
                    alert.url?.let { url ->
                        AppButton("FSA notice", { uriHandler.openUri(url) }, Modifier.weight(1f), kind = ButtonKind.TONAL, compact = true,
                            icon = Icons.AutoMirrored.Rounded.OpenInNew)
                    }
                }
            }
        }
    }
}

/** A recalled product: name, pack size and each affected batch. */
@Composable
fun AlertProductDetails(product: FoodAlert.Product, modifier: Modifier = Modifier) {
    val colors = ZeroBiteTheme.colors
    Column(
        modifier.fillMaxWidth().background(colors.chipFill, RoundedCornerShape(Radius.control)).padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(product.name, style = ZeroBiteTheme.type.subheadline.semibold(), color = colors.textPrimary)
        product.packSize?.let { Detail(Icons.Rounded.Scale, it) }
        product.batches.map { it.summary }.filter { it.isNotEmpty() }.forEach { Detail(Icons.Rounded.QrCode2, it) }
    }
}

@Composable
private fun Detail(icon: ImageVector, text: String) {
    val colors = ZeroBiteTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.width(16.dp).size(13.dp))
        Text(text, style = ZeroBiteTheme.type.caption, color = colors.textSecondary)
    }
}

/** Food alerts list row; `highlight` says why it matters to this user. */
@Composable
fun FoodAlertRow(alert: FoodAlert, highlight: String?, modifier: Modifier = Modifier) {
    val colors = ZeroBiteTheme.colors
    val tint = alert.kind.tint(colors)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        IconTile(alert.kind.icon, tint = tint, size = 36.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(alert.displayTitle, style = ZeroBiteTheme.type.subheadline.semibold(), color = colors.textPrimary)
            Text(
                listOfNotNull(alert.kind.label, alert.business, DateText.dayMonth(alert.created)).joinToString(" · "),
                style = ZeroBiteTheme.type.caption,
                color = colors.textSecondary,
            )
            highlight?.let { Tag(it, Modifier.padding(top = 3.dp), tint = tint) }
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, contentDescription = null, tint = colors.textMuted,
            modifier = Modifier.padding(top = 4.dp).size(12.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowTags(tags: List<String>, tint: Color? = null, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.forEach { Tag(it, tint = tint) }
    }
}
```


- [ ] **Step 6: Recalls in the Fridge view model and screen**

In `InventoryViewModel.kt`:
- Add the imports `com.mazesys.zerobite.core.alerts.RecallMatch`, `com.mazesys.zerobite.recalls.RecallState`, `com.mazesys.zerobite.recalls.RecallStore` and `kotlinx.coroutines.flow.MutableStateFlow`, if it isn't already there.
- Add to `InventoryUiState`, after `showsNotificationTip`:

```kotlin
    /** Alerts that may concern food in the fridge (empty when recall checks are off). */
    val recallMatches: List<RecallMatch> = emptyList(),
```

and inside the class body:

```kotlin
    val recalledItemIds: Set<String> get() = recallMatches.mapTo(mutableSetOf()) { it.itemId }
```

- Add a last constructor parameter to `InventoryViewModel`: `recalls: RecallStore? = null,`.
- Replace the `state` declaration with:

```kotlin
    private val recallState = recalls?.state ?: MutableStateFlow(RecallState())

    val state: StateFlow<InventoryUiState> =
        combine(inventory.items, settings.state, searchText, storageFilter, recallState) { items, s, query, filter, recall ->
            val active = items.filterNot { it.isConsumed }
            val built = InventoryUiState.build(active, query, filter, s.soonThresholdDays, clock.today())
            built.copy(
                useFirstProgress = built.useFirstItem?.shelfLifeProgress(clock.now(), clock.timeZone) ?: 0f,
                // Nudge while reminders are off and the user hasn't refused at the system level.
                showsNotificationTip = !s.dismissedNotificationTip && !s.notificationsEnabled &&
                    notifications.permission() != NotificationPermission.DENIED,
                recallMatches = if (s.recallChecksEnabled) recall.matches(active, built.today) else emptyList(),
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, InventoryUiState(today = clock.today()))
```

- Add the method:

```kotlin
    fun showRecalledItem(match: RecallMatch) = coordinator.showItem(match.itemId)
```

In `InventoryScreen.kt`'s `fridgeContent`, add this before the `if (state.showsNotificationTip)` block:

```kotlin
    if (state.recallMatches.isNotEmpty()) {
        fullWidth("recall") {
            RecallBanner(state.recallMatches, onOpen = viewModel::showRecalledItem, modifier = Modifier.padding(top = Spacing.sm))
        }
    }
```

In the grid's `FoodItemCard(` call (around line 174), add `isRecallFlagged = item.id in state.recalledItemIds,`. Import `com.mazesys.zerobite.ui.components.RecallBanner`.

In `MainScreen.kt`, pass `recalls = dependencies.recalls` as the last argument of the `InventoryViewModel(...)` call.

- [ ] **Step 7: Recall cards on item detail**

In `ItemDetailViewModel.kt`:
- Add the imports `com.mazesys.zerobite.core.alerts.RecallMatch`, `com.mazesys.zerobite.recalls.RecallState`, `com.mazesys.zerobite.recalls.RecallStore` and `kotlinx.coroutines.flow.MutableStateFlow`.
- Add `val recallMatches: List<RecallMatch> = emptyList(),` to `ItemDetailUiState` after `recipeMatches`.
- Add the last constructor parameter `private val recalls: RecallStore? = null,`.
- Replace `state` with:

```kotlin
    private val recallState = recalls?.state ?: MutableStateFlow(RecallState())

    val state: StateFlow<ItemDetailUiState> =
        combine(inventory.items, settings.state, catalogue, recallState) { items, s, all, recall ->
            val item = items.firstOrNull { it.id == itemId }
            val today = clock.today()
            val active = items.filterNot { it.isConsumed }
            ItemDetailUiState(
                today = today,
                state = when {
                    item == null -> DetailState.MISSING
                    item.isConsumed -> DetailState.USED
                    else -> DetailState.ACTIVE
                },
                item = item,
                soonThresholdDays = s.soonThresholdDays,
                progress = item?.shelfLifeProgress(clock.now(), clock.timeZone) ?: 0f,
                addedOn = item?.addedAt?.toLocalDateTime(clock.timeZone)?.date,
                consumedOn = item?.consumedAt?.toLocalDateTime(clock.timeZone)?.date,
                recipeMatches = RecipeMatcher(today, s.soonThresholdDays).match(all, active).filter { it.uses(itemId) }.take(5),
                recallMatches = if (item != null && !item.isConsumed && s.recallChecksEnabled) recall.matches(listOf(item), today) else emptyList(),
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ItemDetailUiState(today = clock.today()))

    /** "Not my batch": hides this alert for this item. */
    fun dismissRecall(match: RecallMatch) {
        viewModelScope.launch { recalls?.dismiss(match) }
    }
```

In `DetailEntries.kt`, add `recalls = dependencies.recalls,` after `onDeleted = onDeleted,` in the `ItemDetailViewModel(...)` call.

In `ItemDetailScreen.kt`:
- Change line 89 to `else -> DetailContent(item, ui, viewModel, onRemove = { confirmDelete = true })`.
- Add the parameter `onRemove: () -> Unit` to `DetailContent`.
- Directly after `DetailTitle(title = item.name, overline = item.category.label)`, add:

```kotlin
        ui.recallMatches.forEach { match ->
            RecallCard(match, onNotMine = { viewModel.dismissRecall(match) }, onThrowAway = onRemove)
        }
```

and import `com.mazesys.zerobite.ui.components.RecallCard`.

- [ ] **Step 8: Run the tests to verify they pass**

Run: `cd android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. `RecallViewsTest` (3), `RecallViewModelsTest` (4) and `RecallScreensTest` (3) pass, and the Plan 2/3 inventory tests still pass; their view models default `recalls` to `null`.

- [ ] **Step 9: Commit**

```bash
git add android/app/src/main/kotlin/com/mazesys/zerobite android/app/src/test/kotlin/com/mazesys/zerobite
git commit -m "Show possible recalls in the fridge: banner, card flags and item-detail cards"
```

---

### Task 8: Food alerts screens and the Settings back stack

**Files:**
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/screens/alerts/FoodAlertsViewModel.kt`
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/screens/alerts/FoodAlertsScreen.kt`
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/screens/alerts/FoodAlertDetailScreen.kt`
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/screens/settings/SettingsTab.kt`
- Modify: `coordination/SettingsCoordinator.kt`, `ui/components/CardSection.kt` (+ `LinkRow`), `screens/settings/SettingsViewModel.kt`, `screens/settings/SettingsScreen.kt`, `screens/root/MainScreen.kt`
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/screens/alerts/FoodAlertsViewModelTest.kt`
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/screens/settings/FoodAlertsNavigationTest.kt`

**Interfaces:**
- Consumes: `RecallStore`, `RecallState` (Task 5); `FoodAlertRow`, `AlertProductDetails`, `FlowTags`, `Kind.tint`/`icon` (Task 7); `Allergen.fromRaw`, `FoodAlert.concerns(profile: List<Allergen>)` (`:core`); `DateText.relative`, `DateText.long`; `ChipFilter`; `PullToRefreshBox` (Material 3).
- Produces:
  - `@Serializable sealed interface SettingsRoute : NavKey { Root; FoodAlerts; FoodAlert(alertId) }`
  - `SettingsCoordinator.backStack`, `showFoodAlerts()`, `showFoodAlert(id)`, `pop()`
  - `enum class AlertFilter(title)`
  - `data class AlertRow(alert, highlight)`
  - `data class FoodAlertsUiState(…)`
  - `class FoodAlertsViewModel(recalls, inventory, settings, clock, onOpen: (String) -> Unit)`
  - `FoodAlertsScreen(vm, onBack)`, `FoodAlertDetailScreen(alert, onBack)`
  - `SettingsTab(coordinator, settingsViewModel, dependencies)`
  - `LinkRow(title, subtitle, icon, onClick, modifier)`
  - `SettingsUiState.alertsSummary`, `SettingsViewModel.showFoodAlerts()`
  - Test tags: `settings.foodAlerts`, `alerts.row.<alertId>`.

**Copy adaptations:** iOS's empty-state text "Pull down to check the Food Standards Agency again." is kept, since pull-to-refresh exists here too.

- [ ] **Step 1: Write the failing view model test**

`android/app/src/test/kotlin/com/mazesys/zerobite/screens/alerts/FoodAlertsViewModelTest.kt`:

```kotlin
package com.mazesys.zerobite.screens.alerts

import com.mazesys.zerobite.data.SettingsSnapshot
import com.mazesys.zerobite.recalls.RecallMarks
import com.mazesys.zerobite.testing.FakeAlerts
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeRecallPersistence
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.milkAllergyAlert
import com.mazesys.zerobite.testing.recallStoreWith
import com.mazesys.zerobite.testing.recalledMilk
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.minutes

class FoodAlertsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val milk = recalledMilk()
    private val allergy = milkAllergyAlert()
    private val client = FakeAlerts(listOf(milk.second, allergy))
    private val persistence = FakeRecallPersistence(listOf(milk.second, allergy), RecallMarks(lastChecked = TestClock.now - 5.minutes))
    private val store = recallStoreWith(client = client, persistence = persistence).also { runBlocking { it.load() } }
    private val settings = FakeSettings(SettingsSnapshot(allergens = setOf("milk")))
    private var opened: String? = null
    private val vm by lazy {
        FoodAlertsViewModel(store, FakeInventoryRepository(listOf(milk.first)), settings, TestClock.fixed(), onOpen = { opened = it })
    }

    @Test
    fun `for you shows fridge matches and allergy alerts, with why`() {
        val rows = vm.state.value.rows
        assertEquals(listOf("In your fridge: Milk", "Contains: Milk"), rows.map { it.highlight })
        assertEquals("Checked 5 minutes ago", vm.state.value.lastCheckedText)
    }

    @Test
    fun `filters split recalls from allergy alerts`() {
        vm.setFilter(AlertFilter.RECALLS)
        assertEquals(listOf(milk.second.id), vm.state.value.rows.map { it.alert.id })
        vm.setFilter(AlertFilter.ALLERGY)
        assertEquals(listOf(allergy.id), vm.state.value.rows.map { it.alert.id })
        vm.setFilter(AlertFilter.ALL)
        assertEquals(2, vm.state.value.rows.size)
    }

    @Test
    fun `no allergies suggests adding them`() = runBlocking {
        settings.update { it.copy(allergens = emptySet()) }
        val other = FoodAlertsViewModel(store, FakeInventoryRepository(), settings, TestClock.fixed(), onOpen = {})
        assertEquals(emptyList(), other.state.value.rows)
        assertEquals("Nothing affects you right now", other.state.value.emptyTitle)
        assertEquals(
            "No recall matches anything in your fridge. Add your allergies in Settings to see allergy alerts here too.",
            other.state.value.emptyMessage,
        )
    }

    @Test
    fun `pull to refresh always asks the FSA, and rows open`() {
        vm.refresh()
        assertEquals(1, client.requests.size)
        assertFalse(vm.state.value.isRefreshing)
        vm.open(vm.state.value.rows.first())
        assertEquals(milk.second.id, opened)
    }
}
```

- [ ] **Step 2: Write the failing navigation test**

`android/app/src/test/kotlin/com/mazesys/zerobite/screens/settings/FoodAlertsNavigationTest.kt`:

```kotlin
package com.mazesys.zerobite.screens.settings

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.compose.runtime.CompositionLocalProvider
import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.coordination.SettingsRoute
import com.mazesys.zerobite.recalls.RecallMarks
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeRecallPersistence
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.recalledMilk
import com.mazesys.zerobite.testing.testDependencies
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class FoodAlertsNavigationTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val milk = recalledMilk()
    private val app = AppCoordinator { TestClock.today }

    @Test
    fun `settings row opens the alerts, then an alert, and back returns`() {
        val deps = testDependencies(
            inventory = FakeInventoryRepository(listOf(milk.first)),
            recallPersistence = FakeRecallPersistence(listOf(milk.second), RecallMarks(lastChecked = TestClock.now)),
        )
        runBlocking { deps.recalls.load() }
        val settings = SettingsViewModel(deps, app.settings, appVersion = "1.0 (1)")
        val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        compose.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                ZeroBiteTheme { SettingsTab(app.settings, settings, deps) }
            }
        }

        compose.onNodeWithText("1 in the last 120 days · checked now").performScrollTo()
        compose.onNodeWithTag("settings.foodAlerts").performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Food alerts") and isHeading()).assertExists()
        compose.onNodeWithText("In your fridge: Milk").assertExists()

        compose.onNodeWithTag("alerts.row.${milk.second.id}").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Read the full notice").performScrollTo().assertExists()
        compose.onNodeWithText("Source: Food Standards Agency (${milk.second.id}). Open Government Licence v3.0.").performScrollTo().assertExists()

        compose.runOnUiThread { app.settings.pop() }
        compose.waitForIdle()
        assertEquals(SettingsRoute.FoodAlerts, app.settings.backStack.last())
    }
}
```

- [ ] **Step 3: Run them to verify they fail**

Run: `cd android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests '*FoodAlertsViewModelTest*' --tests '*FoodAlertsNavigationTest*'`
Expected: compilation FAILS with unresolved `FoodAlertsViewModel`, `AlertFilter`, `SettingsTab`, `SettingsRoute`.

- [ ] **Step 4: Give `SettingsCoordinator` a back stack**

Add to `coordination/SettingsCoordinator.kt`. The imports match `InventoryCoordinator.kt`: `mutableStateListOf`, `SnapshotStateList`, `NavKey` and `kotlinx.serialization.Serializable`.

```kotlin
@Serializable
sealed interface SettingsRoute : NavKey {
    @Serializable
    data object Root : SettingsRoute

    @Serializable
    data object FoodAlerts : SettingsRoute

    @Serializable
    data class FoodAlert(val alertId: String) : SettingsRoute
}
```

and, inside the class:

```kotlin
    val backStack: SnapshotStateList<SettingsRoute> = mutableStateListOf(SettingsRoute.Root)

    fun showFoodAlerts() {
        backStack.add(SettingsRoute.FoodAlerts)
    }

    fun showFoodAlert(id: String) {
        backStack.add(SettingsRoute.FoodAlert(id))
    }

    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }
```

Update the class doc comment to: "Settings navigation (root → food alerts → one alert), plus confirmations and the explainer through the parent."

- [ ] **Step 5: Implement `FoodAlertsViewModel`**

`android/app/src/main/kotlin/com/mazesys/zerobite/screens/alerts/FoodAlertsViewModel.kt`:

```kotlin
package com.mazesys.zerobite.screens.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazesys.zerobite.core.inventory.InventoryRepository
import com.mazesys.zerobite.core.model.Allergen
import com.mazesys.zerobite.core.model.FoodAlert
import com.mazesys.zerobite.core.time.AppClock
import com.mazesys.zerobite.data.SettingsRepository
import com.mazesys.zerobite.recalls.RecallStore
import com.mazesys.zerobite.ui.format.DateText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AlertFilter(val title: String) {
    FOR_YOU("For you"),
    RECALLS("Recalls"),
    ALLERGY("Allergy alerts"),
    ALL("All"),
}

/** One alert, and why it's relevant: "In your fridge: Brie" or "Contains: Milk". */
data class AlertRow(val alert: FoodAlert, val highlight: String?)

data class FoodAlertsUiState(
    val filter: AlertFilter = AlertFilter.FOR_YOU,
    val rows: List<AlertRow> = emptyList(),
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val lastCheckedText: String = "Not checked yet",
    val hasAllergies: Boolean = false,
) {
    val emptyTitle: String
        get() = when (filter) {
            AlertFilter.FOR_YOU -> "Nothing affects you right now"
            AlertFilter.RECALLS -> "No recent recalls"
            AlertFilter.ALLERGY -> "No recent allergy alerts"
            AlertFilter.ALL -> "No alerts downloaded yet"
        }

    val emptyMessage: String
        get() = when {
            filter == AlertFilter.FOR_YOU && !hasAllergies ->
                "No recall matches anything in your fridge. Add your allergies in Settings to see allergy alerts here too."
            filter == AlertFilter.FOR_YOU ->
                "No recall matches anything in your fridge, and there are no allergy alerts for your allergies."
            else -> "Pull down to check the Food Standards Agency again."
        }
}

/** Recent FSA alerts, filtered to what matters to this user (iOS FoodAlertsViewModel). */
class FoodAlertsViewModel(
    private val recalls: RecallStore,
    inventory: InventoryRepository,
    settings: SettingsRepository,
    private val clock: AppClock,
    private val onOpen: (String) -> Unit,
) : ViewModel() {

    private val filter = MutableStateFlow(AlertFilter.FOR_YOU)

    val state: StateFlow<FoodAlertsUiState> =
        combine(filter, recalls.state, inventory.items, settings.state) { f, recall, items, s ->
            val matchedNames = recall.matches(items, clock.today()).groupBy({ it.alert.id }, { it.itemName })
            // Profile in the canonical allergen order, like iOS's ordered allergenProfile.
            val allergens = Allergen.entries.filter { it.raw in s.allergens }
            val rows = recall.alerts.mapNotNull { alert ->
                val fridge = matchedNames[alert.id]?.let { "In your fridge: " + it.joinToString(", ") }
                val concerns = alert.concerns(allergens)
                val allergy = if (concerns.isEmpty()) null else "Contains: " + concerns.joinToString(", ") { it.label }
                val highlight = fridge ?: allergy
                val keep = when (f) {
                    AlertFilter.FOR_YOU -> highlight != null
                    AlertFilter.RECALLS -> alert.kind != FoodAlert.Kind.ALLERGY
                    AlertFilter.ALLERGY -> alert.kind == FoodAlert.Kind.ALLERGY
                    AlertFilter.ALL -> true
                }
                if (keep) AlertRow(alert, highlight) else null
            }
            FoodAlertsUiState(
                filter = f,
                rows = rows,
                isRefreshing = recall.isRefreshing,
                errorMessage = recall.errorMessage,
                lastCheckedText = recall.lastChecked?.let { "Checked ${DateText.relative(it, clock.now())}" } ?: "Not checked yet",
                hasAllergies = allergens.isNotEmpty(),
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, FoodAlertsUiState())

    fun setFilter(value: AlertFilter) {
        filter.value = value
    }

    fun refresh() {
        viewModelScope.launch { recalls.refresh(force = true) }
    }

    fun open(row: AlertRow) = onOpen(row.alert.id)
}
```

- [ ] **Step 6: Implement `FoodAlertsScreen` and `FoodAlertDetailScreen`**

`android/app/src/main/kotlin/com/mazesys/zerobite/screens/alerts/FoodAlertsScreen.kt`:

```kotlin
package com.mazesys.zerobite.screens.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazesys.zerobite.ui.components.AppCard
import com.mazesys.zerobite.ui.components.ChipFilter
import com.mazesys.zerobite.ui.components.DetailTitle
import com.mazesys.zerobite.ui.components.FoodAlertRow
import com.mazesys.zerobite.ui.components.TopBar
import com.mazesys.zerobite.ui.components.TopBarLeading
import com.mazesys.zerobite.ui.components.tint
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.semibold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodAlertsScreen(viewModel: FoodAlertsViewModel, onBack: () -> Unit) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val colors = ZeroBiteTheme.colors

    Column(Modifier.fillMaxSize().background(colors.backgroundPrimary)) {
        TopBar(TopBarLeading.Back(onBack)) {
            if (ui.isRefreshing) CircularProgressIndicator(color = colors.textSecondary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        }
        PullToRefreshBox(isRefreshing = ui.isRefreshing, onRefresh = viewModel::refresh, modifier = Modifier.weight(1f)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                DetailTitle(title = "Food alerts", overline = ui.lastCheckedText, modifier = Modifier.padding(horizontal = Spacing.gutter))
                ui.errorMessage?.let {
                    Text(it, style = ZeroBiteTheme.type.caption, color = colors.statusExpired, modifier = Modifier.padding(horizontal = Spacing.gutter))
                }
                ChipFilter(AlertFilter.entries, ui.filter, viewModel::setFilter, label = { it.title }, contentPadding = PaddingValues(horizontal = Spacing.gutter))
                Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    if (ui.rows.isEmpty()) {
                        AppCard(padding = Spacing.lg) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(ui.emptyTitle, style = ZeroBiteTheme.type.headline.semibold(), color = colors.textPrimary)
                                Text(ui.emptyMessage, style = ZeroBiteTheme.type.subheadline, color = colors.textSecondary)
                            }
                        }
                    } else {
                        ui.rows.forEach { row ->
                            AppCard(
                                Modifier.testTag("alerts.row.${row.alert.id}").clickable(role = Role.Button) { viewModel.open(row) },
                                accent = if (row.highlight == null) null else row.alert.kind.tint(colors),
                            ) { FoodAlertRow(row.alert, row.highlight) }
                        }
                    }
                    Text(
                        "Alerts from the Food Standards Agency. Contains public sector information licensed under the Open Government Licence v3.0.",
                        style = ZeroBiteTheme.type.caption2,
                        color = colors.textMuted,
                    )
                }
            }
        }
    }
}
```

`android/app/src/main/kotlin/com/mazesys/zerobite/screens/alerts/FoodAlertDetailScreen.kt`:

```kotlin
package com.mazesys.zerobite.screens.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.mazesys.zerobite.core.model.FoodAlert
import com.mazesys.zerobite.ui.components.AlertProductDetails
import com.mazesys.zerobite.ui.components.AppButton
import com.mazesys.zerobite.ui.components.CardSection
import com.mazesys.zerobite.ui.components.DetailTitle
import com.mazesys.zerobite.ui.components.FlowTags
import com.mazesys.zerobite.ui.components.InfoRow
import com.mazesys.zerobite.ui.components.Overline
import com.mazesys.zerobite.ui.components.TopBar
import com.mazesys.zerobite.ui.components.TopBarLeading
import com.mazesys.zerobite.ui.components.tint
import com.mazesys.zerobite.ui.format.DateText
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme

/** One FSA alert in full (iOS FoodAlertDetailView). */
@Composable
fun FoodAlertDetailScreen(alert: FoodAlert, onBack: () -> Unit) {
    val colors = ZeroBiteTheme.colors
    val tint = alert.kind.tint(colors)
    val uriHandler = LocalUriHandler.current
    fun paragraph(text: String): @Composable () -> Unit = { Text(text, style = ZeroBiteTheme.type.subheadline, color = colors.textPrimary) }

    Column(Modifier.fillMaxSize().background(colors.backgroundPrimary)) {
        TopBar(TopBarLeading.Back(onBack))
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            DetailTitle(title = alert.displayTitle, overline = "${alert.kind.label} · ${DateText.long(alert.created)}", overlineTint = tint)
            if (alert.businesses.isNotEmpty()) {
                CardSection(rows = listOf<@Composable () -> Unit>({ InfoRow("From", alert.businesses.joinToString(", "), icon = Icons.Rounded.Business) }))
            }
            if (alert.allergens.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Overline("Allergens")
                    FlowTags(alert.allergens, tint = tint)
                }
            }
            alert.risk?.let { CardSection(title = "Why", rows = listOf(paragraph(it))) }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Overline(if (alert.products.size == 1) "Product" else "Products")
                alert.products.forEach { AlertProductDetails(it) }
            }
            alert.consumerAdvice?.let { CardSection(title = "What to do", rows = listOf(paragraph(it))) }
            alert.url?.let { url ->
                AppButton("Read the full notice", { uriHandler.openUri(url) }, icon = Icons.AutoMirrored.Rounded.OpenInNew)
            }
            Text(
                "Source: Food Standards Agency (${alert.id}). Open Government Licence v3.0.",
                style = ZeroBiteTheme.type.caption2,
                color = colors.textMuted,
            )
        }
    }
}
```

- [ ] **Step 7: `LinkRow`, the Settings summary and `SettingsTab`**

Append to `ui/components/CardSection.kt` (add any missing imports: `Icons.AutoMirrored.Rounded.ArrowForwardIos`, `Icon`, `Column`, `size`):

```kotlin
/** A row that opens another screen: icon, title, a one-line summary, chevron. */
@Composable
fun LinkRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ZeroBiteTheme.colors
    Row(
        modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(icon, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = ZeroBiteTheme.type.subheadline.medium(), color = colors.textPrimary)
            Text(subtitle, style = ZeroBiteTheme.type.caption, color = colors.textSecondary)
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(12.dp))
    }
}
```

In `SettingsViewModel.kt`:
- Add `val alertsSummary: String = "Not checked yet",` to `SettingsUiState`.
- Import `com.mazesys.zerobite.recalls.RecallStore` and `com.mazesys.zerobite.ui.format.DateText`.
- Replace the `state` declaration with:

```kotlin
    private val settingsAndRecalls = combine(settings.state, dependencies.recalls.state, ::Pair)

    val state: StateFlow<SettingsUiState> =
        combine(settingsAndRecalls, inventory.items, recipeCount, permission, updating) { (s, recall), items, recipes, p, busy ->
            SettingsUiState(
                appVersion = appVersion,
                snapshot = s,
                permission = p,
                activeCount = items.count { !it.isConsumed },
                usedCount = items.count { it.isConsumed },
                recipeCount = recipes,
                isUpdatingNotifications = busy,
                alertsSummary = recall.lastChecked?.let { checked ->
                    "${recall.alerts.size} in the last ${RecallStore.HISTORY_DAYS} days · checked ${DateText.relative(checked, dependencies.clock.now())}"
                } ?: "Not checked yet",
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState(appVersion))

    fun showFoodAlerts() = coordinator.showFoodAlerts()
```

In `SettingsScreen.kt`, replace `{ InfoRow("Food alerts", "Arrives in the next build", icon = Icons.Rounded.Warning) },` with:

```kotlin
                { LinkRow("Food alerts", ui.alertsSummary, Icons.Rounded.Warning, viewModel::showFoodAlerts, Modifier.testTag("settings.foodAlerts")) },
```

and import `com.mazesys.zerobite.ui.components.LinkRow` (and `androidx.compose.ui.platform.testTag` if it isn't imported yet).

`android/app/src/main/kotlin/com/mazesys/zerobite/screens/settings/SettingsTab.kt`:

```kotlin
package com.mazesys.zerobite.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.mazesys.zerobite.AppDependencies
import com.mazesys.zerobite.coordination.SettingsCoordinator
import com.mazesys.zerobite.coordination.SettingsRoute
import com.mazesys.zerobite.screens.alerts.FoodAlertDetailScreen
import com.mazesys.zerobite.screens.alerts.FoodAlertsScreen
import com.mazesys.zerobite.screens.alerts.FoodAlertsViewModel

/** The Settings tab: settings → food alerts → one alert. The root view model is owned by the caller. */
@Composable
fun SettingsTab(
    coordinator: SettingsCoordinator,
    settingsViewModel: SettingsViewModel,
    dependencies: AppDependencies,
    modifier: Modifier = Modifier,
) {
    NavDisplay(
        backStack = coordinator.backStack,
        modifier = modifier,
        onBack = { coordinator.pop() },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<SettingsRoute.Root> { SettingsScreen(settingsViewModel) }
            entry<SettingsRoute.FoodAlerts> {
                val alerts = viewModel {
                    FoodAlertsViewModel(dependencies.recalls, dependencies.inventory, dependencies.settings, dependencies.clock, onOpen = coordinator::showFoodAlert)
                }
                FoodAlertsScreen(alerts, onBack = coordinator::pop)
            }
            entry<SettingsRoute.FoodAlert> { route ->
                val recall by dependencies.recalls.state.collectAsStateWithLifecycle()
                val alert = recall.alerts.firstOrNull { it.id == route.alertId }
                if (alert == null) {
                    // Gone from the cache after a refresh: step back to the list.
                    LaunchedEffect(Unit) { coordinator.pop() }
                } else {
                    FoodAlertDetailScreen(alert, onBack = coordinator::pop)
                }
            }
        },
    )
}
```

In `MainScreen.kt`, change `AppTab.SETTINGS -> SettingsScreen(settingsScreen)` to `AppTab.SETTINGS -> SettingsTab(coordinator.settings, settingsScreen, dependencies)`, and swap the `SettingsScreen` import for `SettingsTab`.

- [ ] **Step 8: Run the tests to verify they pass**

Run: `cd android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. `FoodAlertsViewModelTest` (4) and `FoodAlertsNavigationTest` (1) pass. `SettingsScreenTest` and `RootScreenTest` still pass, including "back on Settings returns to the Fridge tab": with only the root in the Settings stack, NavDisplay doesn't intercept back.

- [ ] **Step 9: Commit**

```bash
git add android/app/src/main/kotlin/com/mazesys/zerobite android/app/src/test/kotlin/com/mazesys/zerobite
git commit -m "Add the Food alerts list and detail screens, reached from Settings"
```

---

### Task 9: On the device, gate and docs

**Files:**
- Modify: `README.md` (Android section)

- [ ] **Step 1: Gate**

Run: `cd android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :core:check :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected: BUILD SUCCESSFUL with no lint errors. Note the size of `app/build/outputs/apk/debug/app-debug.apk` for the ledger; the bundled ML Kit models add roughly 4–6 MB.

- [ ] **Step 2: Smoke test on the emulator**

Boot `Medium_Phone_API_36.0` if it isn't running, then run: `cd android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:connectedDebugAndroidTest`
Expected: `SmokeTest.addAnItemByHand` passes. It uses the Fridge "Add manually" menu, which this plan doesn't change.

- [ ] **Step 3: Scan a photo for real**

```bash
ADB=~/Library/Android/sdk/platform-tools/adb
cd android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:installDebug; cd ..
```

Make a label image in the scratchpad with "SEMI SKIMMED MILK" on one line and "USE BY 12 SEP 2026" on the next, in black on white, at least 800×400:
- With ImageMagick: `magick -size 900x400 xc:white -fill black -pointsize 56 -annotate +40+140 'SEMI SKIMMED MILK' -annotate +40+260 'USE BY 12 SEP 2026' label.png`
- Otherwise, with Python and PIL.
- If neither is available, photograph a real label with the emulator's camera app instead, and ledger that.

Push it and index it:

```bash
$ADB push label.png /sdcard/Pictures/label.png
$ADB shell content call --method scan_volume --uri content://media --arg external_primary
```

On the device: Fridge → scan button → **Choose a photo of the label** → pick `label.png`. Expected on review:
- name "Semi Skimmed Milk", or the name guesser's output;
- an expiry candidate "12 September 2026";
- **Continue** opens the form with that date.

Save it, then check that it appears in the Fridge.

- [ ] **Step 4: Live scanner and camera permission**

```bash
$ADB shell pm revoke com.mazesys.zerobite android.permission.CAMERA
```

On the device:
1. Scan → **Scan with the camera**. The system prompt appears.
2. Deny it. The denied panel shows the camera rationale, **Open settings** and **Choose a photo instead**. The latter returns to the sources.
3. Run `$ADB shell pm grant com.mazesys.zerobite android.permission.CAMERA` and open the scanner again. The emulator's virtual scene preview shows, with "Looking for a best-before date…" and a disabled **Use what I've found**.
4. Leave the scanner. `$ADB shell dumpsys media.camera | grep -i "client"` should show no active Zero Bite client.

- [ ] **Step 5: Food alerts against the real FSA**

On the device: Settings → the **Food alerts** row should read "N in the last 120 days · checked now", with N > 0 when online. Open it: **All** lists alerts and a row opens its detail, and **Read the full notice** opens the browser.

Turn on airplane mode, then pull to refresh. The red "Couldn't reach the Food Standards Agency…" line shows and the list stays. Turn airplane mode off afterwards.

Recall notifications are covered by `RecallSyncTest` and `RecallNotificationsTest`. A live match needs a current recall that happens to match the fridge, so this step doesn't force one.

- [ ] **Step 6: Docs**

In `README.md`, replace "Label scanning and UK food alerts arrive in the next plan (see `specs/plans/`);
everything else matches iOS." with "It matches the iOS app feature for feature: label and barcode scanning
(on-device ML Kit), reminders, recipes and UK food alerts."

- [ ] **Step 7: Commit**

```bash
git add README.md
git commit -m "Note Android feature parity in the README"
```

---

## Spec coverage (Plan 4 scope)

| Spec section | Covered by |
|---|---|
| §6 Scanning: CameraX Preview + ImageAnalysis → ML Kit text + barcode → `LabelScanner.analyse` | Tasks 1, 4 |
| §6 Scanning: Photo Picker fallback on the same ML Kit path | Tasks 1, 3 |
| §6 Scanning: bundled models; `CAMERA` asked on entering the scanner, denied state links to settings and offers photos | Tasks 1, 4 |
| §5 `ScanCoordinator` with its own back stack; scan is a sheet | Tasks 2, 3 |
| §5 Barcode lookup falls back to the name guess when offline | Task 2 |
| §6 Network: Ktor OkHttp engine; FSA `min-created` (client already tested in `:core`) | Tasks 1, 2, 5 |
| §6 Storage → Recalls: JSON cache in `filesDir`, marks in DataStore, 120 days, 6-hour refresh | Task 5 |
| §5 Alerts show the cache with a "couldn't refresh" line on failure | Tasks 5, 8 |
| §6 Reminders: recall matches post on a separate `recalls` channel | Task 6 |
| §6 Network: refresh on foreground when stale, no background job | Task 6 |
| §7 `RecallViews`, `LoadingOverlay` | Tasks 3, 7 |
| iOS parity: Fridge recall banner + flags, item recall cards, Food alerts list + detail, Settings row, allergens feeding "For you" | Tasks 7, 8 |
| §7 Launcher icon, §9 signing + Firebase | Plan 5 |
