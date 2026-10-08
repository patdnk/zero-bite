# Zero Bite Android — Plan 2: App Shell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A runnable Android app with Zero Bite's bespoke look: theme, Mona Sans, the BrandMark, MVVM-C navigation, a Room-backed fridge, and the Fridge list, item detail and add/edit screens. It installs on the emulator and passes an on-device smoke test.

**Architecture:** A new `:app` module on top of Plan 1's `:core`. View models expose one `StateFlow` of UI state and never navigate; they call coordinators. Coordinators own Navigation 3 back stacks (plain snapshot lists, the Android counterpart of iOS's `NavigationPath`). `AppDependencies` is a hand-wired composition root. Every visible component is custom (Material 3 sits underneath only for dialogs and the date picker). Scanning, reminders, recipes, alerts, settings and onboarding are Plan 3. Their entry points exist here as clearly marked placeholders.

**Tech Stack:** AGP 9.4.1 (built-in Kotlin), Kotlin 2.4.20, KSP 2.3.12, Compose BOM 2026.09.00, Navigation 3 1.2.0, Lifecycle 2.11.0, Room 2.8.5, DataStore 1.2.1, material-icons-extended 1.7.8, Robolectric 4.17, Roborazzi 1.76.0.

**Spec:** [`specs/2026-10-08-android-app-design.md`](../2026-10-08-android-app-design.md). Plan 1 is [`2026-10-08-android-foundation.md`](2026-10-08-android-foundation.md).

## Global Constraints

- iOS **app code** is not changed. The only edits outside `android/` are `design/render-assets.swift` (a new `mark` command), `scripts/render-assets.sh`, `scripts/fetch-fonts.sh`, `.gitignore`, `README.md`, `.github/workflows/android.yml` and one new fixture image `testdata/brandmark-256.png`.
- Package/application ID `com.mazesys.zerobite`; `minSdk 26`, `targetSdk 36`, `compileSdk 37`. These are pinned by a toolchain spike on 2026-10-08: Compose BOM 2026.09.00 requires compileSdk ≥ 37, and AGP downloads platform 37 on first build.
- Robolectric on JDK 21 needs `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED` (set once in `app/build.gradle.kts`). Compose tests import `createComposeRule`/`createAndroidComposeRule` from `androidx.compose.ui.test.junit4.v2`.
- Visuals: colours only from `ZeroBiteTheme.colors`, corners only `Radius.card` (4dp) / `Radius.control` (3dp), spacing from `Spacing`. No Material component may show its default look.
- User-facing strings are copied verbatim from the iOS screens (`ios/ZeroBite/Screens/Inventory/*`, `ios/ZeroBite/UI/*`).
- `:core` stays portable. Its only change in this plan is making `formatQuantity` public (Task 12).
- Time comes from `AppClock` (`dependencies.clock`); tests use `FixedClock` at **Mon 7 Sep 2026 09:00 Europe/London** (`TestClock`).
- Run Gradle from `android/` with `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` (write `./gradlew` below; prefix that env var).
- Screenshot tests (Roborazzi) record into `android/app/src/test/screenshots/`. `./gradlew :app:recordRoborazziDebug` records and `verifyRoborazziDebug` verifies. Plain `testDebugUnitTest` runs them without comparing, so CI never depends on the gitignored font.
- **Not in this plan (Plan 3):** label scanning, reminders and the "turn on reminders" tip, recall banner/cards/flags, the Recipes and Settings tabs, onboarding, and the "Recipes using…" section on item detail.

## Review Focus

1. **System back:**
   - With a sheet open, back closes the sheet.
   - With item detail open, back returns to the list.
   - At the Fridge root, back leaves the app.

   Pinned by `back closes the sheet, then pops detail` in Task 13.
2. **Configuration change while the add/edit form is open** (dark-mode toggle, rotation): the half-typed draft must survive, and the form's view model must be freed once the sheet is dismissed. Pinned by `SheetViewModelStoresTest` in Task 13.
3. **Large text (system font scale 2.0):** item names and the header stay visible on the Fridge screen. Pinned by `large font scale keeps names visible` in Task 10.
4. **An item deleted while its detail screen is open:** the screen shows "Item removed" rather than crashing. Pinned by `missing item shows removed state` in Task 11.
5. **Malformed quantity input** (`abc`, `0`, `1,5`): it can't be saved as garbage, and the comma works as a decimal separator. Pinned by `quantity text is parsed leniently and validated` in Task 12.

---

## File Structure

```
android/
├── settings.gradle.kts                    + include(":app")
├── build.gradle.kts                       + android/compose/ksp/roborazzi plugins (apply false)
├── gradle/libs.versions.toml              + app libraries
└── app/
    ├── build.gradle.kts                   module config, shared-assets copy task, Robolectric flags
    ├── schemas/                           exported Room schema (generated, committed)
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── assets/fonts/mona_sans.ttf (gitignored; scripts/fetch-fonts.sh)
        │   ├── res/values{,-night}/       colors.xml, themes.xml, strings.xml
        │   └── kotlin/com/mazesys/zerobite/
        │       ├── ZeroBiteApplication.kt, MainActivity.kt, AppViewModel.kt
        │       ├── AppDependencies.kt      composition root + IdGenerator
        │       ├── data/                   FoodItemEntity, FoodItemDao, ZeroBiteDatabase,
        │       │                           RoomInventoryRepository, SampleData,
        │       │                           SettingsRepository (+ DataStore impl), BundledRecipeRepository
        │       ├── coordination/           AppFlowController, AppCoordinator, InventoryCoordinator,
        │       │                           SheetViewModelStores
        │       ├── ui/theme/               Color, Dimens, Type, AppFont, Theme, StatusStyle, AppIcons
        │       ├── ui/brand/               BrandMark
        │       ├── ui/components/          Surfaces, Headings, Buttons, Layout, ChipFilter, Inputs,
        │       │                           CardSection, EmptyState, TopBar, AppTabBar, FoodViews
        │       ├── ui/format/              DateText
        │       └── screens/
        │           ├── root/               RootScreen, LaunchScreen, WelcomePlaceholder, MainScreen,
        │           │                       SheetHost, PlaceholderTab, ScanPlaceholder, AppAlert
        │           └── inventory/          InventoryViewModel, InventoryScreen, ItemDetailViewModel,
        │                                   ItemDetailScreen, ItemFormViewModel, ItemFormScreen, InventoryTab
        ├── test/kotlin/com/mazesys/zerobite/…   unit, Robolectric and Roborazzi tests (+ testing/Fakes.kt)
        ├── test/resources/robolectric.properties
        ├── test/screenshots/               Roborazzi goldens
        └── androidTest/kotlin/com/mazesys/zerobite/SmokeTest.kt
```

---

### Task 1: `:app` module skeleton

**Files:**
- Modify: `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle/libs.versions.toml`, `scripts/fetch-fonts.sh`, `.gitignore`
- Create: `android/app/build.gradle.kts`, `android/app/src/main/AndroidManifest.xml`, `android/app/src/main/res/values/{strings,colors,themes}.xml`, `android/app/src/main/res/values-night/colors.xml`, `android/app/src/main/kotlin/com/mazesys/zerobite/{ZeroBiteApplication,MainActivity}.kt`, `android/app/src/test/resources/robolectric.properties`, `android/local.properties` (gitignored)
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/AppLaunchTest.kt`

**Interfaces:**
- Consumes: `:core` (`RecipeCatalogue.decode`).
- Produces: an installable `com.mazesys.zerobite` debug APK; `recipes.json` packaged as an asset from `/shared-assets`; Mona Sans at `assets/fonts/mona_sans.ttf` when fetched; test system property `zerobite.testdata`.

- [ ] **Step 1: Add the libraries to `android/gradle/libs.versions.toml`**

Append to `[versions]`:

```toml
agp = "9.4.1"
ksp = "2.3.12"
compose-bom = "2026.09.00"
activity = "1.13.0"
lifecycle = "2.11.0"
navigation3 = "1.2.0"
room = "2.8.5"
datastore = "1.2.1"
core-splashscreen = "1.2.0"
material-icons = "1.7.8"
robolectric = "4.17"
roborazzi = "1.76.0"
androidx-test-core = "1.7.0"
androidx-test-junit = "1.3.0"
androidx-test-runner = "1.7.0"
junit4 = "4.13.2"
```

Append to `[libraries]`:

```toml
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-graphics = { module = "androidx.compose.ui:ui-graphics" }
compose-foundation = { module = "androidx.compose.foundation:foundation" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
material-icons-extended = { module = "androidx.compose.material:material-icons-extended", version.ref = "material-icons" }
activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activity" }
lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-viewmodel-navigation3 = { module = "androidx.lifecycle:lifecycle-viewmodel-navigation3", version.ref = "lifecycle" }
navigation3-runtime = { module = "androidx.navigation3:navigation3-runtime", version.ref = "navigation3" }
navigation3-ui = { module = "androidx.navigation3:navigation3-ui", version.ref = "navigation3" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
core-splashscreen = { module = "androidx.core:core-splashscreen", version.ref = "core-splashscreen" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "kotlinx-coroutines" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
roborazzi = { module = "io.github.takahirom.roborazzi:roborazzi", version.ref = "roborazzi" }
roborazzi-compose = { module = "io.github.takahirom.roborazzi:roborazzi-compose", version.ref = "roborazzi" }
roborazzi-junit-rule = { module = "io.github.takahirom.roborazzi:roborazzi-junit-rule", version.ref = "roborazzi" }
androidx-test-core = { module = "androidx.test:core", version.ref = "androidx-test-core" }
androidx-test-junit = { module = "androidx.test.ext:junit", version.ref = "androidx-test-junit" }
androidx-test-runner = { module = "androidx.test:runner", version.ref = "androidx-test-runner" }
junit4 = { module = "junit:junit", version.ref = "junit4" }
```

Append to `[plugins]`:

```toml
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
roborazzi = { id = "io.github.takahirom.roborazzi", version.ref = "roborazzi" }
```

- [ ] **Step 2: Register the module and plugins**

`android/settings.gradle.kts`: replace `include(":core")` with `include(":core", ":app")`.

`android/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.roborazzi) apply false
}
```

Create `android/local.properties` (already gitignored by `android/.gitignore`):

```properties
sdk.dir=/Users/patdynek/Library/Android/sdk
```

- [ ] **Step 3: Write `android/app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.mazesys.zerobite"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.mazesys.zerobite"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                // Robolectric reaches into JDK internals that JDK 17+ hides.
                test.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
                test.systemProperty("zerobite.testdata", rootDir.resolve("../testdata").canonicalPath)
            }
        }
    }
}

kotlin {
    compilerOptions {
        optIn.addAll("kotlin.time.ExperimentalTime", "kotlin.uuid.ExperimentalUuidApi")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

/** Copies the recipe catalogue from /shared-assets so iOS and Android bundle the same file. */
abstract class CopySharedAssets : DefaultTask() {
    @get:InputFile abstract val recipes: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile
        out.mkdirs()
        recipes.get().asFile.copyTo(out.resolve("recipes.json"), overwrite = true)
    }
}

val copySharedAssets = tasks.register<CopySharedAssets>("copySharedAssets") {
    recipes.set(rootProject.layout.projectDirectory.file("../shared-assets/recipes.json"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copySharedAssets, CopySharedAssets::outputDir)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.core.splashscreen)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
```

- [ ] **Step 4: Manifest and resources**

`android/app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:name=".ZeroBiteApplication"
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.ZeroBite.Starting">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="portrait"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">Zero Bite</string>
</resources>
```

`res/values/colors.xml`:

```xml
<resources>
    <color name="background">#FFFBF7</color>
</resources>
```

`res/values-night/colors.xml`:

```xml
<resources>
    <color name="background">#201816</color>
</resources>
```

`res/values/themes.xml` (the launcher icon and splash mark arrive in Plan 4):

```xml
<resources>
    <style name="Theme.ZeroBite" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:windowBackground">@color/background</item>
    </style>

    <style name="Theme.ZeroBite.Starting" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">@color/background</item>
        <item name="postSplashScreenTheme">@style/Theme.ZeroBite</item>
    </style>
</resources>
```

`android/app/src/test/resources/robolectric.properties`:

```properties
sdk=36
```

- [ ] **Step 5: Write the failing launch test**

`android/app/src/test/kotlin/com/mazesys/zerobite/AppLaunchTest.kt`:

```kotlin
package com.mazesys.zerobite

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.mazesys.zerobite.core.model.RecipeCatalogue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AppLaunchTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `launches and shows the app name`() {
        compose.onNodeWithText("Zero Bite").assertExists()
    }

    @Test
    fun `bundles the shared recipe catalogue`() {
        val text = compose.activity.assets.open("recipes.json").bufferedReader().use { it.readText() }
        assertTrue(RecipeCatalogue.decode(text).size >= 30)
    }
}
```

- [ ] **Step 6: Run it to see it fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest; cd ..`
Expected: compilation FAILS: `Unresolved reference 'MainActivity'`. The first run also downloads platform 37.

- [ ] **Step 7: Add the application and activity**

`ZeroBiteApplication.kt`:

```kotlin
package com.mazesys.zerobite

import android.app.Application

class ZeroBiteApplication : Application()
```

`MainActivity.kt`:

```kotlin
package com.mazesys.zerobite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { Text("Zero Bite") }
    }
}
```

- [ ] **Step 8: Run the tests**

Run: `cd android && ./gradlew :app:testDebugUnitTest; cd ..`
Expected: `BUILD SUCCESSFUL`, 2 tests passed.

- [ ] **Step 9: Ship Mona Sans to Android too**

In `scripts/fetch-fonts.sh`, after `cp "$FONT" "$DEST/Mona-Sans.ttf"`, add:

```bash
ANDROID_DEST="$ROOT/android/app/src/main/assets/fonts"
mkdir -p "$ANDROID_DEST"
cp "$FONT" "$ANDROID_DEST/mona_sans.ttf"
echo "Installed $(basename "$FONT") → android/app/src/main/assets/fonts/mona_sans.ttf"
```

Add to the repo-root `.gitignore`, under the existing fonts line:

```
android/app/src/main/assets/fonts/*.ttf
```

Copy the already-downloaded font, so no network is needed:

```bash
mkdir -p android/app/src/main/assets/fonts
cp ios/ZeroBite/Resources/Fonts/Mona-Sans.ttf android/app/src/main/assets/fonts/mona_sans.ttf
git check-ignore -q android/app/src/main/assets/fonts/mona_sans.ttf && echo ignored
```

Expected: `ignored`.

- [ ] **Step 10: Build the APK and commit**

Run: `cd android && ./gradlew :app:assembleDebug; cd ..`
Expected: `BUILD SUCCESSFUL`; `android/app/build/outputs/apk/debug/app-debug.apk` exists.

```bash
git add android scripts/fetch-fonts.sh .gitignore
git commit -m "Add the Android app module

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Theme: colours, type, spacing, Mona Sans

**Files:**
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/ui/theme/{Color,Dimens,Type,AppFont,Theme,StatusStyle}.kt`
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/ui/theme/ThemeTest.kt`

**Interfaces:**
- Produces:
  - `data class ZeroBiteColors(backgroundPrimary, backgroundSecondary, cardTint, surface, hairline, chipFill, textPrimary, textSecondary, textMuted, onInk, brand, brandOrange, brandYellow, statusExpired, statusToday, statusSoon, statusFresh: Color)`, `val LightColors`, `val DarkColors`
  - `object Radius { card = 4.dp; control = 3.dp }`, `object Spacing { xs 4, sm 8, md 12, lg 16, xl 24, gutter 16 }`
  - `data class ZeroBiteTypography(largeTitle, title, title2, title3, headline, body, callout, subheadline, footnote, caption, caption2: TextStyle)` with `companion fun of(family: FontFamily)`; `TextStyle.medium()`, `.semibold()`, `.bold()`
  - `object AppFont { fun family(assets: AssetManager): FontFamily; fun resolve(bundledFonts: List<String>, load: () -> FontFamily): FontFamily }`
  - `@Composable fun ZeroBiteTheme(darkTheme: Boolean = isSystemInDarkTheme(), content)` and `object ZeroBiteTheme { val colors; val type }`
  - `fun ExpiryStatus.tint(colors: ZeroBiteColors): Color`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.mazesys.zerobite.ui.theme

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mazesys.zerobite.core.model.ExpiryStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertSame

@RunWith(RobolectricTestRunner::class)
class ThemeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `dark theme swaps the palette`() {
        var light: ZeroBiteColors? = null
        var dark: ZeroBiteColors? = null
        compose.setContent {
            ZeroBiteTheme(darkTheme = false) { light = ZeroBiteTheme.colors }
            ZeroBiteTheme(darkTheme = true) { dark = ZeroBiteTheme.colors }
        }
        assertEquals(LightColors, light)
        assertEquals(DarkColors, dark)
    }

    @Test
    fun `falls back to the system font when Mona Sans was not fetched`() {
        val loaded = FontFamily.Serif
        assertSame(FontFamily.Default, AppFont.resolve(emptyList()) { loaded })
        assertSame(loaded, AppFont.resolve(listOf("mona_sans.ttf")) { loaded })
    }

    @Test
    fun `type scale mirrors iOS Dynamic Type defaults`() {
        val type = ZeroBiteTypography.of(FontFamily.Default)
        assertEquals(34.sp, type.largeTitle.fontSize)
        assertEquals(15.sp, type.subheadline.fontSize)
        assertEquals(FontWeight.SemiBold, type.headline.fontWeight)
        assertEquals(FontWeight.SemiBold, type.caption.semibold().fontWeight)
    }

    @Test
    fun `status tints follow the warning ramp`() {
        assertEquals(LightColors.statusExpired, ExpiryStatus.EXPIRED.tint(LightColors))
        assertEquals(LightColors.statusToday, ExpiryStatus.TODAY.tint(LightColors))
        assertEquals(LightColors.statusSoon, ExpiryStatus.SOON.tint(LightColors))
        assertEquals(DarkColors.statusFresh, ExpiryStatus.FRESH.tint(DarkColors))
    }
}
```

- [ ] **Step 2: Run to see it fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests '*ThemeTest'; cd ..`
Expected: compilation FAILS: unresolved `ZeroBiteTheme`, `LightColors`, `AppFont`.

- [ ] **Step 3: Implement the theme files**

`Color.kt`:

```kotlin
package com.mazesys.zerobite.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Colour tokens: the iOS `Constants` palette (warm, darkened red/orange/yellow) in light and dark. */
@Immutable
data class ZeroBiteColors(
    val backgroundPrimary: Color,
    val backgroundSecondary: Color,
    val cardTint: Color,
    /** Card and input fill. */
    val surface: Color,
    /** 1dp card, input and tab bar borders. */
    val hairline: Color,
    /** Unselected chips, secondary buttons, empty progress tracks. */
    val chipFill: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    /** Text on an ink fill (selected chips, primary buttons). */
    val onInk: Color,
    val brand: Color,
    val brandOrange: Color,
    val brandYellow: Color,
    val statusExpired: Color,
    val statusToday: Color,
    val statusSoon: Color,
    /** Muted olive, so "fine" stays distinguishable from the warning ramp. */
    val statusFresh: Color,
)

val LightColors = ZeroBiteColors(
    backgroundPrimary = Color(0xFFFFFBF7),
    backgroundSecondary = Color(0xFFF6EEE6),
    cardTint = Color(0xFFF3E1D2),
    surface = Color(0xFFFFFFFF),
    hairline = Color(0xFFEEE1D5),
    chipFill = Color(0xFFF6EEE6),
    textPrimary = Color(0xFF2B1A12),
    textSecondary = Color(0xFF7A5C4E),
    textMuted = Color(0xFFA08779),
    onInk = Color(0xFFFFFBF7),
    brand = Color(0xFFB23A22),
    brandOrange = Color(0xFFC2611A),
    brandYellow = Color(0xFFC2941A),
    statusExpired = Color(0xFF9B1C1C),
    statusToday = Color(0xFFC2611A),
    statusSoon = Color(0xFFC2941A),
    statusFresh = Color(0xFF6E7F1E),
)

val DarkColors = ZeroBiteColors(
    backgroundPrimary = Color(0xFF201816),
    backgroundSecondary = Color(0xFF140F0D),
    cardTint = Color(0xFF2C201A),
    surface = Color(0xFF2A201C),
    hairline = Color(0xFF3A2C25),
    chipFill = Color(0xFF352821),
    textPrimary = Color(0xFFF7F0EA),
    textSecondary = Color(0xFFC3B2A8),
    textMuted = Color(0xFF8F7D73),
    onInk = Color(0xFF201816),
    brand = Color(0xFFD9573B),
    brandOrange = Color(0xFFE07B2E),
    brandYellow = Color(0xFFE0B33A),
    statusExpired = Color(0xFFE24B4B),
    statusToday = Color(0xFFE07B2E),
    statusSoon = Color(0xFFE0B33A),
    statusFresh = Color(0xFF9DB33A),
)
```

`Dimens.kt`:

```kotlin
package com.mazesys.zerobite.ui.theme

import androidx.compose.ui.unit.dp

/** Deliberately tight corners, so the app reads as bespoke rather than stock Material. */
object Radius {
    /** Cards, panels, image placeholders. */
    val card = 4.dp
    /** Buttons, chips, inputs, icon tiles. */
    val control = 3.dp
}

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    /** Horizontal page gutter. */
    val gutter = 16.dp
}
```

`Type.kt`:

```kotlin
package com.mazesys.zerobite.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** iOS Dynamic Type text styles at the default size; `sp` keeps the user's font scale. */
@Immutable
data class ZeroBiteTypography(
    val largeTitle: TextStyle,
    val title: TextStyle,
    val title2: TextStyle,
    val title3: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val callout: TextStyle,
    val subheadline: TextStyle,
    val footnote: TextStyle,
    val caption: TextStyle,
    val caption2: TextStyle,
) {
    companion object {
        fun of(family: FontFamily): ZeroBiteTypography {
            fun style(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal) =
                TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = lineHeight.sp, fontWeight = weight)
            return ZeroBiteTypography(
                largeTitle = style(34, 41),
                title = style(28, 34),
                title2 = style(22, 28),
                title3 = style(20, 25),
                headline = style(17, 22, FontWeight.SemiBold),
                body = style(17, 22),
                callout = style(16, 21),
                subheadline = style(15, 20),
                footnote = style(13, 18),
                caption = style(12, 16),
                caption2 = style(11, 13),
            )
        }
    }
}

fun TextStyle.medium(): TextStyle = copy(fontWeight = FontWeight.Medium)
fun TextStyle.semibold(): TextStyle = copy(fontWeight = FontWeight.SemiBold)
fun TextStyle.bold(): TextStyle = copy(fontWeight = FontWeight.Bold)
```

`AppFont.kt`:

```kotlin
package com.mazesys.zerobite.ui.theme

import android.content.res.AssetManager
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/**
 * Mona Sans when scripts/fetch-fonts.sh has put the variable font into assets, else the system
 * font, so the app always builds and runs (same rule as iOS).
 */
object AppFont {
    private const val FILE = "mona_sans.ttf"
    private val WEIGHTS = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

    fun family(assets: AssetManager): FontFamily =
        resolve(assets.list("fonts")?.toList().orEmpty()) { monaSans(assets) }

    fun resolve(bundledFonts: List<String>, load: () -> FontFamily): FontFamily =
        if (FILE in bundledFonts) load() else FontFamily.Default

    private fun monaSans(assets: AssetManager): FontFamily = FontFamily(
        WEIGHTS.map { weight ->
            Font(
                path = "fonts/$FILE",
                assetManager = assets,
                weight = weight,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
            )
        },
    )
}
```

`StatusStyle.kt`:

```kotlin
package com.mazesys.zerobite.ui.theme

import androidx.compose.ui.graphics.Color
import com.mazesys.zerobite.core.model.ExpiryStatus

fun ExpiryStatus.tint(colors: ZeroBiteColors): Color = when (this) {
    ExpiryStatus.EXPIRED -> colors.statusExpired
    ExpiryStatus.TODAY -> colors.statusToday
    ExpiryStatus.SOON -> colors.statusSoon
    ExpiryStatus.FRESH -> colors.statusFresh
}
```

`Theme.kt`:

```kotlin
package com.mazesys.zerobite.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily

private val LocalColors = staticCompositionLocalOf { LightColors }
private val LocalTypography = staticCompositionLocalOf { ZeroBiteTypography.of(FontFamily.Default) }

@Composable
fun ZeroBiteTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val family = remember(context) { AppFont.family(context.assets) }
    val typography = remember(family) { ZeroBiteTypography.of(family) }
    val colors = if (darkTheme) DarkColors else LightColors

    // Material 3 is only underneath for dialogs and the date picker; give it our palette and corners.
    MaterialTheme(
        colorScheme = colors.materialScheme(darkTheme),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(Radius.control),
            small = RoundedCornerShape(Radius.control),
            medium = RoundedCornerShape(Radius.card),
            large = RoundedCornerShape(Radius.card),
            extraLarge = RoundedCornerShape(Radius.card),
        ),
        typography = Typography(
            bodyLarge = typography.body,
            bodyMedium = typography.subheadline,
            bodySmall = typography.caption,
            titleLarge = typography.title3,
            titleMedium = typography.headline,
            labelLarge = typography.subheadline.semibold(),
            headlineSmall = typography.title2,
        ),
    ) {
        CompositionLocalProvider(
            LocalColors provides colors,
            LocalTypography provides typography,
            LocalContentColor provides colors.textPrimary,
            LocalTextStyle provides typography.body,
            content = content,
        )
    }
}

object ZeroBiteTheme {
    val colors: ZeroBiteColors
        @Composable @ReadOnlyComposable get() = LocalColors.current

    val type: ZeroBiteTypography
        @Composable @ReadOnlyComposable get() = LocalTypography.current
}

private fun ZeroBiteColors.materialScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = textPrimary,
        onPrimary = onInk,
        secondary = brandOrange,
        onSecondary = Color.White,
        background = backgroundPrimary,
        onBackground = textPrimary,
        surface = backgroundPrimary,
        onSurface = textPrimary,
        surfaceVariant = chipFill,
        onSurfaceVariant = textSecondary,
        surfaceContainerHigh = backgroundPrimary,
        surfaceContainerHighest = surface,
        outline = hairline,
        outlineVariant = hairline,
        error = statusExpired,
    )
}
```

- [ ] **Step 4: Run the tests**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests '*ThemeTest'; cd ..`
Expected: 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app
git commit -m "Port the Zero Bite palette, type scale and Mona Sans to Android

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: BrandMark

**Files:**
- Modify: `design/render-assets.swift`, `scripts/render-assets.sh`
- Create: `testdata/brandmark-256.png` (rendered from the iOS `BrandMark.swift`)
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/ui/brand/BrandMark.kt`
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/ui/brand/BrandMarkTest.kt`

**Interfaces:**
- Produces: `@Composable fun BrandMark(modifier: Modifier = Modifier, style: BrandMarkStyle = BrandMarkStyle.COLOR)`, `enum class BrandMarkStyle { COLOR, TINTED }`, `object BrandMarkColors { red, cream, ink }`, `fun DrawScope.drawBrandMark(disc: Color, ink: Color)` (Plan 4 renders the launcher icon from it).

- [ ] **Step 1: Teach the render tool to output a reference image**

In `design/render-assets.swift`, extend the usage line to `"usage: render-assets icons <appiconset-dir> | marketing <raw-screens-dir> <out-dir> | mark <out.png>"` and add a case before `default:`:

```swift
        case "mark":
            // Reference for the Android BrandMark test: the mark on white, 256 px square.
            let out = URL(fileURLWithPath: args.dropFirst().first ?? "brandmark-256.png")
            try write(ZStack { Color.white; BrandMark() }, size: 256, opaque: true, to: out)
```

In `scripts/render-assets.sh`, add a case after `marketing)`:

```bash
  mark)
    "$BIN" mark "$ROOT/testdata/brandmark-256.png"
    ;;
```

and change the header comment's usage block to include `#   scripts/render-assets.sh mark        # testdata/brandmark-256.png for the Android BrandMark test`.

Run: `scripts/render-assets.sh mark && file testdata/brandmark-256.png`
Expected: `wrote …/testdata/brandmark-256.png (256×256)` and `PNG image data, 256 x 256`.

- [ ] **Step 2: Write the failing test**

```kotlin
package com.mazesys.zerobite.ui.brand

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "mdpi")
class BrandMarkTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(style: BrandMarkStyle = BrandMarkStyle.COLOR): android.graphics.Bitmap {
        compose.setContent {
            Box(Modifier.size(256.dp).background(Color.White).testTag("mark")) {
                BrandMark(Modifier.fillMaxSize(), style)
            }
        }
        return compose.onNodeWithTag("mark").captureToImage().asAndroidBitmap()
    }

    private fun channelDiff(a: Int, b: Int): Int =
        listOf(16, 8, 0).maxOf { shift -> abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF)) }

    @Test
    fun `matches the iOS rendering of the mark`() {
        val actual = render()
        val reference = File(System.getProperty("zerobite.testdata"), "brandmark-256.png")
        val expected = BitmapFactory.decodeFile(reference.path)
        assertEquals(256, actual.width)
        var differing = 0
        for (y in 0 until 256) for (x in 0 until 256) {
            if (channelDiff(actual.getPixel(x, y), expected.getPixel(x, y)) > 48) differing++
        }
        // Only anti-aliased edges may differ between the two renderers.
        assertTrue(differing < 256 * 256 / 100, "$differing of 65536 pixels differ from the iOS mark")
    }

    @Test
    fun `tinted style is a grey disc`() {
        val pixel = render(BrandMarkStyle.TINTED).getPixel(128, 66) // inside the disc, clear of the Z
        assertTrue(channelDiff(pixel, 0xFF6B6B6B.toInt()) <= 2, Integer.toHexString(pixel))
    }

    @Test
    fun `is labelled for accessibility`() {
        compose.setContent { BrandMark(Modifier.size(64.dp)) }
        compose.onNodeWithContentDescription("Zero Bite").assertExists()
    }
}
```

- [ ] **Step 3: Run to see it fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests '*BrandMarkTest'; cd ..`
Expected: compilation FAILS: `Unresolved reference 'BrandMark'`.

- [ ] **Step 4: Implement the mark** (geometry copied from `ios/ZeroBite/UI/BrandMark.swift`; do not redesign it)

```kotlin
package com.mazesys.zerobite.ui.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.min

enum class BrandMarkStyle {
    /** Red disc, cream ring, band and Z. */
    COLOR,
    /** Greyscale, for monochrome/themed icons. */
    TINTED,
}

object BrandMarkColors {
    val red = Color(0xFFB23A22)
    val cream = Color(0xFFFFFBF7)
    val ink = Color(0xFF2B1A12)
}

/**
 * The Zero Bite mark: a red disc with a cream ring, a Z whose diagonal runs on as a band (like
 * the UK national speed limit sign), and an apple-style bite on the east side.
 */
@Composable
fun BrandMark(modifier: Modifier = Modifier, style: BrandMarkStyle = BrandMarkStyle.COLOR) {
    val disc = if (style == BrandMarkStyle.COLOR) BrandMarkColors.red else Color(0xFF6B6B6B)
    val ink = if (style == BrandMarkStyle.COLOR) BrandMarkColors.cream else Color.White
    Canvas(
        modifier
            .aspectRatio(1f)
            // Offscreen so the bite can punch through to whatever is behind the mark.
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .semantics { contentDescription = "Zero Bite" },
    ) {
        drawBrandMark(disc, ink)
    }
}

// Geometry in a 1024 × 1024 design space (see design/BrandMark.svg).
private val CENTER = Offset(512f, 512f)
private const val RING_OUTER_RADIUS = 376f
private const val DISC_RADIUS = 300f
/** The Z is drawn in its own 1024 space and scaled to sit inside the ring. */
private const val LETTER_SCALE = 0.909f
private const val BAND_ANGLE_DEGREES = -40f
private val BITE_CENTER = Offset(885f, 464f)
private const val BITE_RADIUS = 175f

fun DrawScope.drawBrandMark(disc: Color, ink: Color) {
    val side = min(size.width, size.height)
    translate(left = (size.width - side) / 2, top = (size.height - side) / 2) {
        scale(side / 1024f, pivot = Offset.Zero) {
            drawCircle(ink, RING_OUTER_RADIUS, CENTER)
            drawCircle(disc, DISC_RADIUS, CENTER)

            scale(LETTER_SCALE, pivot = CENTER) {
                drawRect(ink, Offset(372f, 352f), Size(280f, 84f))
                drawRect(ink, Offset(372f, 588f), Size(280f, 84f))
                // The diagonal runs right through to the outer edge so it fuses with the ring.
                val ring = Path().apply { addOval(Rect(CENTER, RING_OUTER_RADIUS / LETTER_SCALE)) }
                clipPath(ring) {
                    rotate(BAND_ANGLE_DEGREES, pivot = CENTER) {
                        drawRect(ink, Offset(-100f, 470f), Size(1224f, 84f))
                    }
                }
            }

            drawCircle(Color.Black, BITE_RADIUS, BITE_CENTER, blendMode = BlendMode.DstOut)
        }
    }
}
```

- [ ] **Step 5: Run the tests**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests '*BrandMarkTest'; cd ..`
Expected: 3 tests PASS. If the iOS comparison fails, compare the failing count against the image. Write the actual bitmap to `/tmp/android-mark.png` with `actual.compress(PNG, …)` and open it next to `testdata/brandmark-256.png`. Fix the geometry; never raise the tolerance to make it pass.

- [ ] **Step 6: Commit**

```bash
git add design/render-assets.swift scripts/render-assets.sh testdata/brandmark-256.png android/app
git commit -m "Draw the BrandMark in Compose, checked against the iOS rendering

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Design-system primitives: surfaces, headings, buttons, icons

**Files:**
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/ui/theme/AppIcons.kt`
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/ui/components/{Surfaces,Headings,Buttons}.kt`
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/ui/components/PrimitivesTest.kt`

**Interfaces:**
- Consumes: theme (Task 2).
- Produces:
  - Icons: `val FoodCategory.icon`, `val StorageLocation.icon`, `val ExpiryStatus.icon: ImageVector`
  - `AppCard(modifier, accent: Color? = null, padding: Dp = Spacing.md, content: ColumnScope.() -> Unit)`
  - `IconTile(icon, modifier, tint, size = 32.dp)`
  - `HeaderIconButton(icon, contentDescription, onClick, modifier, bordered = true)`
  - `ShelfLifeBar(progress: Float, tint, modifier)`
  - `Overline(text, modifier, tint)`
  - `ScreenHeader(title, modifier, overline = null, count = null, trailing: RowScope.() -> Unit = {})`
  - `SectionHeading(title, modifier, detail = null, tint, icon = null)`
  - `DetailTitle(title, modifier, overline = null, overlineTint)`
  - `Tag(text, modifier, tint = null)`
  - `enum class ButtonKind { INK, BRAND, TONAL, PLAIN }`
  - `AppButton(text, onClick, modifier, kind = INK, compact = false, icon = null, enabled = true, loading = false)`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.mazesys.zerobite.core.model.ExpiryStatus
import com.mazesys.zerobite.core.model.FoodCategory
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.icon
import com.mazesys.zerobite.ui.theme.tint
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PrimitivesTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `buttons call back only when enabled`() {
        var taps = 0
        compose.setContent {
            ZeroBiteTheme {
                Column {
                    AppButton("Used it", onClick = { taps++ })
                    AppButton("Disabled", onClick = { taps += 100 }, enabled = false)
                }
            }
        }
        compose.onNodeWithText("Used it").performClick()
        compose.onNodeWithText("Disabled").assertIsNotEnabled().performClick()
        assertEquals(1, taps)
    }

    @Test
    fun `loading button hides its title and ignores taps`() {
        var taps = 0
        compose.setContent { ZeroBiteTheme { AppButton("Saving", onClick = { taps++ }, loading = true) } }
        compose.onNodeWithText("Saving").assertDoesNotExist()
        assertEquals(0, taps)
    }

    @Test
    fun `screen header shows the count tag only when given`() {
        compose.setContent {
            ZeroBiteTheme {
                Column {
                    ScreenHeader(title = "Fridge", overline = "Mon 7 Sep", count = 14)
                    ScreenHeader(title = "Recipes")
                }
            }
        }
        compose.onNodeWithContentDescription("14 items").assertExists()
        compose.onNodeWithText("Mon 7 Sep").assertExists()
    }

    @Test
    fun `header icon button is labelled and clickable`() {
        var tapped = false
        compose.setContent { ZeroBiteTheme { HeaderIconButton(Icons.Rounded.Add, "Add food", onClick = { tapped = true }) } }
        compose.onNodeWithContentDescription("Add food").performClick()
        assertEquals(true, tapped)
    }

    @Test
    fun `primitives gallery light`() = gallery(dark = false)

    @Test
    fun `primitives gallery dark`() = gallery(dark = true)

    private fun gallery(dark: Boolean) {
        compose.setContent {
            ZeroBiteTheme(darkTheme = dark) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScreenHeader(title = "Fridge", overline = "Mon 7 Sep", count = 14) {
                        HeaderIconButton(Icons.Rounded.Add, "Add food", onClick = {})
                    }
                    SectionHeading(title = "Expired", detail = "1 item", tint = ExpiryStatus.EXPIRED.tint(ZeroBiteTheme.colors), icon = ExpiryStatus.EXPIRED.icon)
                    AppCard(accent = ZeroBiteTheme.colors.statusSoon) {
                        IconTile(FoodCategory.DAIRY.icon, tint = ZeroBiteTheme.colors.statusSoon)
                        DetailTitle(title = "Greek yogurt", overline = "Dairy & eggs")
                        ShelfLifeBar(progress = 0.7f, tint = ZeroBiteTheme.colors.statusSoon)
                    }
                    Tag("Spinach", tint = ZeroBiteTheme.colors.brandOrange)
                    AppButton("Mark as used up", onClick = {})
                    AppButton("Scan a label", onClick = {}, kind = ButtonKind.BRAND)
                    AppButton("Edit", onClick = {}, kind = ButtonKind.TONAL)
                    AppButton("Not now", onClick = {}, kind = ButtonKind.PLAIN, compact = true)
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/primitives_${if (dark) "dark" else "light"}.png")
    }
}
```

- [ ] **Step 2: Run to see it fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests '*PrimitivesTest'; cd ..`
Expected: compilation FAILS: unresolved `AppButton`, `ScreenHeader`, `icon`.

- [ ] **Step 3: Implement the icons** (SF Symbol counterparts; names verified against material-icons-extended 1.7.8)

`ui/theme/AppIcons.kt`:

```kotlin
package com.mazesys.zerobite.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.BakeryDining
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Countertops
import androidx.compose.material.icons.rounded.Dangerous
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.Liquor
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.RiceBowl
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SetMeal
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.TakeoutDining
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector
import com.mazesys.zerobite.core.model.ExpiryStatus
import com.mazesys.zerobite.core.model.FoodCategory
import com.mazesys.zerobite.core.model.StorageLocation

val FoodCategory.icon: ImageVector
    get() = when (this) {
        FoodCategory.DAIRY -> Icons.Rounded.WaterDrop
        FoodCategory.MEAT -> Icons.Rounded.Restaurant
        FoodCategory.FISH -> Icons.Rounded.SetMeal
        FoodCategory.VEGETABLES -> Icons.Rounded.Eco
        FoodCategory.FRUIT -> Icons.Rounded.Spa
        FoodCategory.BAKERY -> Icons.Rounded.BakeryDining
        FoodCategory.GRAINS -> Icons.Rounded.RiceBowl
        FoodCategory.CONDIMENTS -> Icons.Rounded.Liquor
        FoodCategory.DRINKS -> Icons.Rounded.LocalBar
        FoodCategory.LEFTOVERS -> Icons.Rounded.TakeoutDining
        FoodCategory.OTHER -> Icons.Rounded.Inventory2
    }

val StorageLocation.icon: ImageVector
    get() = when (this) {
        StorageLocation.FRIDGE -> Icons.Rounded.Kitchen
        StorageLocation.FREEZER -> Icons.Rounded.AcUnit
        StorageLocation.PANTRY -> Icons.Rounded.Countertops
    }

val ExpiryStatus.icon: ImageVector
    get() = when (this) {
        ExpiryStatus.EXPIRED -> Icons.Rounded.Dangerous
        ExpiryStatus.TODAY -> Icons.Rounded.Warning
        ExpiryStatus.SOON -> Icons.Rounded.Schedule
        ExpiryStatus.FRESH -> Icons.Rounded.CheckCircle
    }
```

- [ ] **Step 4: Implement the surfaces**

`ui/components/Surfaces.kt`:

```kotlin
package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme

/** Card with a hairline border. `accent` draws a 3dp status edge down the leading side. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    padding: Dp = Spacing.md,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = ZeroBiteTheme.colors
    val shape = RoundedCornerShape(Radius.card)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.hairline, shape)
            .drawWithContent {
                drawContent()
                if (accent != null) drawRect(accent, size = Size(3.dp.toPx(), size.height))
            }
            .padding(padding),
        content = content,
    )
}

/** Tinted square tile holding an icon (category icons, empty states). */
@Composable
fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = ZeroBiteTheme.colors.textPrimary,
    size: Dp = 32.dp,
) {
    Box(
        modifier.size(size).background(tint.copy(alpha = 0.12f), RoundedCornerShape(Radius.control)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.56f))
    }
}

/** Square header button (add, back, more). Bordered on pages; borderless and tonal for closing sheets. */
@Composable
fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bordered: Boolean = true,
) {
    val colors = ZeroBiteTheme.colors
    val shape = RoundedCornerShape(Radius.control)
    Box(
        modifier
            .size(38.dp)
            .clip(shape)
            .background(if (bordered) colors.surface else colors.chipFill)
            .then(if (bordered) Modifier.border(1.dp, colors.hairline, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
    }
}

/** Thin progress bar with square ends. `progress`: 0 = just bought, 1 = at or past the date. */
@Composable
fun ShelfLifeBar(progress: Float, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(ZeroBiteTheme.colors.chipFill)
            .clearAndSetSemantics {},
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(tint))
    }
}
```

- [ ] **Step 5: Implement the headings**

`ui/components/Headings.kt`:

```kotlin
package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.medium
import com.mazesys.zerobite.ui.theme.semibold

/** Small tracked label above titles and as section headings inside cards. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, tint: Color = ZeroBiteTheme.colors.textSecondary) {
    Text(text, modifier, color = tint, style = ZeroBiteTheme.type.caption.semibold(), letterSpacing = 0.6.sp)
}

/** Editorial screen title: orange overline, tightly tracked title, optional count tag, trailing buttons. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    count: Int? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = ZeroBiteTheme.colors
    val type = ZeroBiteTheme.type
    Row(modifier.fillMaxWidth().padding(top = Spacing.sm), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (overline != null) Overline(overline, tint = colors.brandOrange)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Text(
                    title,
                    style = type.largeTitle.semibold(),
                    letterSpacing = (-1).sp,
                    color = colors.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                if (count != null) {
                    Text(
                        "$count",
                        style = type.caption.semibold(),
                        color = colors.textSecondary,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .background(colors.chipFill, RoundedCornerShape(Radius.control))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .semantics { contentDescription = "$count items" },
                    )
                }
            }
        }
        Spacer(Modifier.width(Spacing.md))
        Row(
            Modifier.padding(top = if (overline == null) 0.dp else 4.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            content = trailing,
        )
    }
}

/** Heading between groups of cards: label on the left, optional detail on the right. */
@Composable
fun SectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    tint: Color = ZeroBiteTheme.colors.textPrimary,
    icon: ImageVector? = null,
) {
    Row(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text(title, style = ZeroBiteTheme.type.subheadline.semibold(), color = tint, modifier = Modifier.weight(1f))
        if (detail != null) Text(detail, style = ZeroBiteTheme.type.caption, color = ZeroBiteTheme.colors.textSecondary)
    }
}

/** Inline header on pushed screens and sheets: overline plus a title. */
@Composable
fun DetailTitle(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    overlineTint: Color = ZeroBiteTheme.colors.brandOrange,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (overline != null) Overline(overline, tint = overlineTint)
        Text(
            title,
            style = ZeroBiteTheme.type.title.semibold(),
            letterSpacing = (-0.8).sp,
            color = ZeroBiteTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/** Small static tag (ingredient names, recipe tags). */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, tint: Color? = null) {
    val colors = ZeroBiteTheme.colors
    Text(
        text,
        modifier
            .background(tint?.copy(alpha = 0.12f) ?: colors.chipFill, RoundedCornerShape(Radius.control))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        style = ZeroBiteTheme.type.caption.medium(),
        color = tint ?: colors.textSecondary,
    )
}
```

- [ ] **Step 6: Implement the buttons**

`ui/components/Buttons.kt`:

```kotlin
package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.semibold

enum class ButtonKind {
    /** Dark ink fill: the main action on a screen. */
    INK,
    /** Burnt orange fill: scanning and other "do it now" actions. */
    BRAND,
    /** Light chip fill: secondary actions next to a primary one. */
    TONAL,
    /** Text only. */
    PLAIN,
}

/** Flat, tight-cornered, full-width button shared by every action in the app. */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.INK,
    compact: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = ZeroBiteTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val (background, foreground) = when (kind) {
        ButtonKind.INK -> colors.textPrimary to colors.onInk
        ButtonKind.BRAND -> colors.brandOrange to Color.White
        ButtonKind.TONAL -> colors.chipFill to colors.textPrimary
        ButtonKind.PLAIN -> Color.Transparent to colors.textSecondary
    }
    val active = enabled && !loading
    val style = (if (compact) ZeroBiteTheme.type.subheadline else ZeroBiteTheme.type.headline).semibold()

    Row(
        modifier
            .fillMaxWidth()
            .alpha(if (!active) 0.45f else if (pressed) 0.75f else 1f)
            .clip(RoundedCornerShape(Radius.control))
            .background(background)
            .clickable(interactionSource = interaction, indication = null, enabled = active, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (compact) 10.dp else 16.dp, vertical = if (compact) 8.dp else 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), color = foreground, strokeWidth = 2.dp)
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = style, color = foreground)
        }
    }
}
```

- [ ] **Step 7: Run the tests and record the gallery**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests '*PrimitivesTest' && ./gradlew :app:recordRoborazziDebug --tests '*PrimitivesTest'; cd ..
```

Expected: tests PASS, and `android/app/src/test/screenshots/primitives_light.png` and `primitives_dark.png` exist. Open both: corners are tight, nothing is Material-purple, and dark text is readable.

- [ ] **Step 8: Commit**

```bash
git add android/app
git commit -m "Add the Android design-system primitives

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Controls: chips, search, inputs, card sections, empty state, top bar, tab bar

**Files:**
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/ui/components/{Layout,ChipFilter,Inputs,CardSection,EmptyState,TopBar,AppTabBar}.kt`
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/ui/components/ControlsTest.kt`

**Interfaces:**
- Consumes: Task 4 primitives.
- Produces:
  - `fun Modifier.bleed(horizontal: Dp): Modifier` widens a child past its parent's padding (edge-to-edge chip rows)
  - `fun <T> ChipFilter(options: List<T>, selected: T, onSelect: (T) -> Unit, label: (T) -> String, modifier, contentPadding = PaddingValues(horizontal = Spacing.gutter))`
  - `data class SearchAccessory(icon, label, onClick)` and `BottomSearchBar(text, onTextChange, placeholder, modifier, accessory = null, testTag = "search.field")`
  - `AppTextField(value, onValueChange, placeholder, modifier, singleLine = true, autoFocus = false, keyboardOptions)` and `LabeledInput(label, value, onValueChange, placeholder, modifier, singleLine = true, testTag = label, autoFocus = false, keyboardOptions)`
  - `CardSection(modifier, title = null, footer = null, rows: List<@Composable () -> Unit>)`, `InfoRow(label, value, modifier, icon)`, `InfoRow(label, modifier, icon, trailing)`, `ActionRow(title, onClick, modifier, icon, tint)`
  - `EmptyStateView(title, message, icon, modifier, primaryTitle, primaryIcon, onPrimary, secondaryTitle, onSecondary)`
  - `sealed interface TopBarLeading { Back(onClick), Close(onClick), None }` and `TopBar(leading, modifier, trailing)`
  - `data class TabItem<T>(key, title, icon, selectedIcon)` and `fun <T> AppTabBar(tabs, selected, onSelect, modifier)`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.mazesys.zerobite.core.model.StorageLocation
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ControlsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `chip filter selects an option`() {
        compose.setContent {
            var selected by remember { mutableStateOf(StorageLocation.FRIDGE) }
            ZeroBiteTheme { ChipFilter(StorageLocation.entries, selected, { selected = it }, { it.label }) }
        }
        compose.onNodeWithText("Cupboard").performClick()
        compose.onNodeWithText("Cupboard").assertIsSelected()
    }

    @Test
    fun `search bar types, clears and runs its accessory`() {
        var scans = 0
        var latest = ""
        compose.setContent {
            var text by remember { mutableStateOf("") }
            ZeroBiteTheme {
                BottomSearchBar(
                    text = text,
                    onTextChange = { text = it; latest = it },
                    placeholder = "Search your food",
                    accessory = SearchAccessory(Icons.Rounded.QrCodeScanner, "Scan label or barcode") { scans++ },
                )
            }
        }
        compose.onNodeWithText("Search your food").assertExists()
        compose.onNodeWithContentDescription("Scan label or barcode").performClick()
        assertEquals(1, scans)
        compose.onNodeWithTag("search.field").performTextInput("milk")
        assertEquals("milk", latest)
        compose.onNodeWithText("Cancel").assertExists() // focused: Cancel replaces the accessory
        compose.onNodeWithContentDescription("Clear search").performClick()
        assertEquals("", latest)
    }

    @Test
    fun `tab bar selects tabs`() {
        val tabs = listOf(
            TabItem("fridge", "Fridge", Icons.Outlined.Kitchen, Icons.Rounded.Kitchen),
            TabItem("settings", "Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
        )
        compose.setContent {
            var selected by remember { mutableStateOf("fridge") }
            ZeroBiteTheme { AppTabBar(tabs, selected, { selected = it }) }
        }
        compose.onNodeWithTag("tab.settings").performClick()
        compose.onNodeWithTag("tab.settings").assertIsSelected()
    }

    @Test
    fun `top bar back calls back`() {
        var backs = 0
        compose.setContent { ZeroBiteTheme { TopBar(TopBarLeading.Back { backs++ }) } }
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `labeled input shows its placeholder until typed into`() {
        compose.setContent {
            var name by remember { mutableStateOf("") }
            ZeroBiteTheme { LabeledInput("Name", name, { name = it }, placeholder = "Greek yogurt", testTag = "name") }
        }
        compose.onNodeWithText("Greek yogurt").assertExists()
        compose.onNodeWithTag("name").performTextInput("Milk")
        compose.onNodeWithText("Greek yogurt").assertDoesNotExist()
    }

    @Test
    fun `empty state actions fire`() {
        var primary = false
        var secondary = false
        compose.setContent {
            ZeroBiteTheme {
                EmptyStateView(
                    title = "Your fridge is empty",
                    message = "Scan a label or add something by hand.",
                    icon = Icons.Rounded.Kitchen,
                    primaryTitle = "Scan a label", onPrimary = { primary = true },
                    secondaryTitle = "Try it with sample food", onSecondary = { secondary = true },
                )
            }
        }
        compose.onNodeWithText("Scan a label").performClick()
        compose.onNodeWithText("Try it with sample food").performClick()
        assertTrue(primary && secondary)
    }

    @Test
    fun `controls gallery light`() = gallery(dark = false)

    @Test
    fun `controls gallery dark`() = gallery(dark = true)

    private fun gallery(dark: Boolean) {
        compose.setContent {
            ZeroBiteTheme(darkTheme = dark) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TopBar(TopBarLeading.Close {})
                    ChipFilter(StorageLocation.entries, StorageLocation.FRIDGE, {}, { it.label })
                    CardSection(
                        title = "Details",
                        footer = "No date on the label? Use the typical shelf life.",
                        rows = listOf<@Composable () -> Unit>(
                            { InfoRow("Stored in", "Fridge") },
                            { InfoRow("Quantity", "500 g") },
                            { ActionRow("Use typical shelf life (7 days)", onClick = {}) },
                        ),
                    )
                    LabeledInput("Name", "", {}, placeholder = "Greek yogurt")
                    BottomSearchBar("", {}, "Search your food", accessory = SearchAccessory(Icons.Rounded.QrCodeScanner, "Scan") {})
                    AppTabBar(
                        listOf(
                            TabItem("fridge", "Fridge", Icons.Outlined.Kitchen, Icons.Rounded.Kitchen),
                            TabItem("settings", "Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
                        ),
                        "fridge", {},
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/controls_${if (dark) "dark" else "light"}.png")
    }
}
```

- [ ] **Step 2: Run to see it fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests '*ControlsTest'; cd ..`
Expected: compilation FAILS: unresolved `ChipFilter`, `BottomSearchBar`, `AppTabBar` and others.

- [ ] **Step 3: Implement layout helper and chips**

`ui/components/Layout.kt`:

```kotlin
package com.mazesys.zerobite.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp

/** Lets a child (e.g. a chip row) run edge to edge past its parent's horizontal padding. */
fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val inset = horizontal.roundToPx()
    val width = constraints.maxWidth + inset * 2
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-inset, 0) }
}
```

`ui/components/ChipFilter.kt`:

```kotlin
package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.semibold

/** Horizontally scrolling single-select chips (replaces a segmented control). */
@Composable
fun <T> ChipFilter(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.gutter),
) {
    val colors = ZeroBiteTheme.colors
    val style = ZeroBiteTheme.type.subheadline
    LazyRow(modifier, contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(options) { option ->
            val isSelected = option == selected
            Text(
                label(option),
                style = if (isSelected) style.semibold() else style,
                color = if (isSelected) colors.onInk else colors.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.control))
                    .background(if (isSelected) colors.textPrimary else colors.chipFill)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(option) })
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}
```

- [ ] **Step 4: Implement the inputs and search bar**

`ui/components/Inputs.kt`:

```kotlin
package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.medium

/** Bordered text input; the border darkens while focused. */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    autoFocus: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val colors = ZeroBiteTheme.colors
    val type = ZeroBiteTheme.type
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val shape = RoundedCornerShape(Radius.control)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.focusRequester(focusRequester).onFocusChanged { focused = it.isFocused },
        textStyle = type.body.copy(color = colors.textPrimary),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        maxLines = if (singleLine) 1 else 4,
        keyboardOptions = keyboardOptions,
        cursorBrush = SolidColor(colors.textPrimary),
        decorationBox = { inner ->
            Box(
                Modifier
                    .background(colors.surface, shape)
                    .border(1.dp, if (focused) colors.textSecondary else colors.hairline, shape)
                    .padding(horizontal = 10.dp, vertical = 11.dp),
            ) {
                if (value.isEmpty()) Text(placeholder, style = type.body, color = colors.textMuted)
                inner()
            }
        },
    )
    if (autoFocus) LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

/** Text input with a small label above it. */
@Composable
fun LabeledInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    testTag: String = label,
    autoFocus: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = ZeroBiteTheme.type.caption.medium(), color = ZeroBiteTheme.colors.textSecondary)
        AppTextField(
            value, onValueChange, placeholder,
            Modifier.fillMaxWidth().testTag(testTag),
            singleLine = singleLine, autoFocus = autoFocus, keyboardOptions = keyboardOptions,
        )
    }
}

data class SearchAccessory(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/** Search field pinned above the tab bar, with an optional square action (Scan). */
@Composable
fun BottomSearchBar(
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    accessory: SearchAccessory? = null,
    testTag: String = "search.field",
) {
    val colors = ZeroBiteTheme.colors
    val type = ZeroBiteTheme.type
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Radius.control)

    Row(
        modifier.fillMaxWidth().background(colors.backgroundPrimary).padding(horizontal = Spacing.gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(42.dp)
                .clip(shape)
                .background(colors.surface)
                .border(1.dp, if (focused) colors.textSecondary else colors.hairline, shape)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) Text(placeholder, style = type.subheadline, color = colors.textMuted)
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.testTag(testTag),
                    singleLine = true,
                    textStyle = type.subheadline.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.textPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                )
            }
            if (text.isNotEmpty()) {
                Icon(
                    Icons.Rounded.Cancel,
                    contentDescription = "Clear search",
                    tint = colors.textMuted,
                    modifier = Modifier.size(18.dp).clickable(role = Role.Button) { onTextChange("") },
                )
            }
        }
        if (focused) {
            Text(
                "Cancel",
                style = type.subheadline.medium(),
                color = colors.textPrimary,
                modifier = Modifier.clickable(role = Role.Button) {
                    onTextChange("")
                    focusManager.clearFocus()
                },
            )
        } else if (accessory != null) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(shape)
                    .background(colors.brandOrange)
                    .clickable(role = Role.Button, onClick = accessory.onClick)
                    .semantics { contentDescription = accessory.label },
                contentAlignment = Alignment.Center,
            ) {
                Icon(accessory.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}
```

- [ ] **Step 5: Implement card sections and the empty state**

`ui/components/CardSection.kt`:

```kotlin
package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.medium

/** A titled group of rows inside one card, divided by hairlines, with optional footer text. */
@Composable
fun CardSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    footer: String? = null,
    rows: List<@Composable () -> Unit>,
) {
    val colors = ZeroBiteTheme.colors
    val shape = RoundedCornerShape(Radius.card)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (title != null) Overline(title, Modifier.semantics { heading() })
        Column(Modifier.fillMaxWidth().clip(shape).background(colors.surface).border(1.dp, colors.hairline, shape)) {
            rows.forEachIndexed { index, row ->
                if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
                Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = 11.dp)) { row() }
            }
        }
        if (footer != null) {
            Text(footer, style = ZeroBiteTheme.type.caption, color = colors.textSecondary, modifier = Modifier.padding(horizontal = 2.dp))
        }
    }
}

/** Label on the left, value on the right. */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    InfoRow(label, modifier, icon) {
        Text(value, style = ZeroBiteTheme.type.subheadline.medium(), color = ZeroBiteTheme.colors.textPrimary)
    }
}

/** Label on the left, any control on the right. */
@Composable
fun InfoRow(label: String, modifier: Modifier = Modifier, icon: ImageVector? = null, trailing: @Composable () -> Unit) {
    val colors = ZeroBiteTheme.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
        Text(label, style = ZeroBiteTheme.type.subheadline, color = colors.textSecondary, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Row-shaped button inside a CardSection. */
@Composable
fun ActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = ZeroBiteTheme.colors.textPrimary,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 24.dp).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(title, style = ZeroBiteTheme.type.subheadline.medium(), color = tint)
    }
}
```

`ui/components/EmptyState.kt`:

```kotlin
package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.semibold

@Composable
fun EmptyStateView(
    title: String,
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    primaryTitle: String? = null,
    primaryIcon: ImageVector? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryTitle: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val colors = ZeroBiteTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        IconTile(icon, tint = colors.brandOrange, size = 56.dp)
        Text(
            title,
            style = ZeroBiteTheme.type.title2.semibold(),
            letterSpacing = (-0.5).sp,
            color = colors.textPrimary,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        Text(message, style = ZeroBiteTheme.type.body, color = colors.textSecondary)
        if (primaryTitle != null && onPrimary != null) {
            AppButton(primaryTitle, onPrimary, Modifier.padding(top = Spacing.md), kind = ButtonKind.BRAND, icon = primaryIcon)
        }
        if (secondaryTitle != null && onSecondary != null) {
            AppButton(secondaryTitle, onSecondary, kind = ButtonKind.TONAL)
        }
    }
}
```

- [ ] **Step 6: Implement the top bar and tab bar**

`ui/components/TopBar.kt`:

```kotlin
package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme

sealed interface TopBarLeading {
    /** Pops the current screen. */
    data class Back(val onClick: () -> Unit) : TopBarLeading
    /** Closes a sheet or flow. */
    data class Close(val onClick: () -> Unit) : TopBarLeading
    data object None : TopBarLeading
}

/** Replaces a system app bar on pushed screens and sheets with square header buttons. */
@Composable
fun TopBar(leading: TopBarLeading, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier
            .fillMaxWidth()
            .background(ZeroBiteTheme.colors.backgroundPrimary)
            .padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.xs, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        when (leading) {
            is TopBarLeading.Back -> HeaderIconButton(Icons.Rounded.ChevronLeft, "Back", leading.onClick, Modifier.testTag("topBar.back"))
            is TopBarLeading.Close -> HeaderIconButton(Icons.Rounded.Close, "Close", leading.onClick, Modifier.testTag("topBar.close"), bordered = false)
            TopBarLeading.None -> Unit
        }
        Spacer(Modifier.weight(1f))
        trailing()
    }
}
```

`ui/components/AppTabBar.kt`:

```kotlin
package com.mazesys.zerobite.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.medium
import com.mazesys.zerobite.ui.theme.semibold

data class TabItem<T>(val key: T, val title: String, val icon: ImageVector, val selectedIcon: ImageVector)

/** Flat full-width tab bar: hairline on top, icon + label, and an indicator that slides along the top. */
@Composable
fun <T> AppTabBar(tabs: List<TabItem<T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val colors = ZeroBiteTheme.colors
    val index = tabs.indexOfFirst { it.key == selected }.coerceAtLeast(0)
    BoxWithConstraints(modifier.fillMaxWidth().background(colors.backgroundPrimary)) {
        val tabWidth = maxWidth / tabs.size
        val indicatorX by animateDpAsState(
            targetValue = tabWidth * index + (tabWidth - 44.dp) / 2,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            label = "tabIndicator",
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        Row(Modifier.fillMaxWidth()) {
            tabs.forEach { tab ->
                val isSelected = tab.key == selected
                val tint = if (isSelected) colors.textPrimary else colors.textMuted
                Column(
                    Modifier
                        .weight(1f)
                        .testTag("tab.${tab.title.lowercase()}")
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab.key) })
                        .padding(top = 10.dp, bottom = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(if (isSelected) tab.selectedIcon else tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                    Text(
                        tab.title,
                        style = ZeroBiteTheme.type.caption2.let { if (isSelected) it.semibold() else it.medium() },
                        color = tint,
                    )
                }
            }
        }
        Box(Modifier.offset(x = indicatorX).size(width = 44.dp, height = 3.dp).background(colors.brandOrange))
    }
}
```

- [ ] **Step 7: Run the tests and record the gallery**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests '*ControlsTest' && ./gradlew :app:recordRoborazziDebug --tests '*ControlsTest'; cd ..
```

Expected: 8 tests PASS; `controls_light.png` and `controls_dark.png` recorded.

- [ ] **Step 8: Commit**

```bash
git add android/app
git commit -m "Add chips, search, inputs, card sections, top bar and tab bar

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Food presentations: badge, card, row, "use first" hero

**Files:**
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/ui/components/FoodViews.kt`
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/ui/components/FoodViewsTest.kt`

**Interfaces:**
- Consumes: `FoodItem`, `ExpiryStatus` (`:core`); Tasks 2, 4 and 5.
- Produces:
  - `fun FoodItem.shelfLifeProgress(now: Instant, timeZone: TimeZone): Float`
  - `ExpiryBadge(item, today, soonThresholdDays, modifier)`
  - `FoodItemCard(item, today, soonThresholdDays, onClick, onLongClick, modifier, isRecallFlagged = false)`
  - `FoodItemRow(item, today, soonThresholdDays, modifier)`
  - `UseFirstCard(item, today, progress: Float, soonThresholdDays, othersNeedingAttention, onOpen, onUsed, onFreeze: (() -> Unit)?, onRecipes, modifier)`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.mazesys.zerobite.core.model.FoodCategory
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.StorageLocation
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class FoodViewsTest {

    @get:Rule
    val compose = createComposeRule()

    private val zone = TimeZone.of("Europe/London")
    private val today = LocalDate(2026, 9, 7)
    private val now = Instant.parse("2026-09-07T08:00:00Z")

    private fun item(name: String, days: Int, category: FoodCategory = FoodCategory.DAIRY, storage: StorageLocation = StorageLocation.FRIDGE) =
        FoodItem(
            id = name, name = name, quantity = 500.0, unit = "g", category = category, storage = storage,
            addedAt = Instant.parse("2026-09-01T09:00:00Z"), expiresOn = today.plus(days, DateTimeUnit.DAY),
        )

    @Test
    fun `shelf life progress runs from added to the end of the expiry day`() {
        val milk = FoodItem(
            id = "m", name = "Milk",
            addedAt = Instant.parse("2026-09-05T23:00:00Z"), // 00:00 BST on 6 Sep
            expiresOn = LocalDate(2026, 9, 7),               // ends 00:00 BST on 8 Sep, 48 h later
        )
        assertEquals(0.5f, milk.shelfLifeProgress(Instant.parse("2026-09-06T23:00:00Z"), zone), 0.001f)
        assertEquals(1.5f, milk.shelfLifeProgress(Instant.parse("2026-09-08T23:00:00Z"), zone), 0.001f)
        val sameInstant = milk.copy(addedAt = Instant.parse("2026-09-07T23:00:00Z"))
        assertEquals(1f, sameInstant.shelfLifeProgress(now, zone))
    }

    @Test
    fun `card shows quantity and label and reports taps`() {
        var taps = 0
        var longPresses = 0
        compose.setContent {
            ZeroBiteTheme { FoodItemCard(item("Greek yogurt", 2), today, 3, onClick = { taps++ }, onLongClick = { longPresses++ }) }
        }
        compose.onNodeWithText("500 g").assertExists()
        compose.onNodeWithText("2d").assertExists()
        compose.onNodeWithText("Greek yogurt").performClick()
        compose.onNodeWithText("Greek yogurt").performTouchInput { longClick() }
        assertEquals(1, taps)
        assertEquals(1, longPresses)
    }

    @Test
    fun `use first card counts down and hides freeze when not allowed`() {
        compose.setContent {
            ZeroBiteTheme {
                UseFirstCard(
                    item = item("Spinach", -3, FoodCategory.VEGETABLES), today = today, progress = 1f,
                    soonThresholdDays = 3, othersNeedingAttention = 4,
                    onOpen = {}, onUsed = {}, onFreeze = null, onRecipes = {},
                )
            }
        }
        compose.onNodeWithText("3").assertExists()
        compose.onNodeWithText("days over").assertExists()
        compose.onNodeWithText("4 more need attention").assertExists()
        compose.onNodeWithText("Freeze").assertDoesNotExist()
        compose.onNodeWithText("Used it").assertExists()
    }

    @Test
    fun `row shows quantity and storage`() {
        compose.setContent { ZeroBiteTheme { FoodItemRow(item("Salmon fillets", 60, FoodCategory.FISH, StorageLocation.FREEZER), today, 3) } }
        compose.onNodeWithText("500 g · Freezer").assertExists()
        compose.onNodeWithText("6 Nov").assertExists()
    }

    @Test
    fun `food views gallery light`() = gallery(false)

    @Test
    fun `food views gallery dark`() = gallery(true)

    private fun gallery(dark: Boolean) {
        compose.setContent {
            ZeroBiteTheme(darkTheme = dark) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    UseFirstCard(item("Greek yogurt", -1), today, 1f, 3, 5, {}, {}, null, {})
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FoodItemCard(item("Spinach", 0, FoodCategory.VEGETABLES), today, 3, {}, {}, Modifier.weight(1f))
                        FoodItemCard(item("Semi-skimmed milk", 2), today, 3, {}, {}, Modifier.weight(1f), isRecallFlagged = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FoodItemCard(item("Bacon", 5, FoodCategory.MEAT), today, 3, {}, {}, Modifier.weight(1f))
                        FoodItemCard(item("Basmati rice", 300, FoodCategory.GRAINS, StorageLocation.PANTRY), today, 3, {}, {}, Modifier.weight(1f))
                    }
                    FoodItemRow(item("Salmon fillets", 60, FoodCategory.FISH, StorageLocation.FREEZER), today, 3)
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/food_views_${if (dark) "dark" else "light"}.png")
    }
}
```

- [ ] **Step 2: Run to see it fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests '*FoodViewsTest'; cd ..`
Expected: compilation FAILS: unresolved `FoodItemCard`, `UseFirstCard`, `shelfLifeProgress`.

- [ ] **Step 3: Implement**

`ui/components/FoodViews.kt`:

```kotlin
package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.icon
import com.mazesys.zerobite.ui.theme.semibold
import com.mazesys.zerobite.ui.theme.tint
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlin.time.Duration
import kotlin.time.Instant

/** Fraction of the shelf life already used: from when it was added to the end of its expiry day. */
fun FoodItem.shelfLifeProgress(now: Instant, timeZone: TimeZone): Float {
    val end = expiresOn.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
    val total = end - addedAt
    if (total <= Duration.ZERO) return 1f
    return ((now - addedAt) / total).toFloat()
}

/** Compact tag showing how long an item has left, tinted by urgency. */
@Composable
fun ExpiryBadge(item: FoodItem, today: LocalDate, soonThresholdDays: Int, modifier: Modifier = Modifier) {
    val tint = item.status(today, soonThresholdDays).tint(ZeroBiteTheme.colors)
    Text(
        item.shortExpiryLabel(today),
        modifier
            .background(tint.copy(alpha = 0.12f), RoundedCornerShape(Radius.control))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        style = ZeroBiteTheme.type.caption.semibold(),
        color = tint,
    )
}

/** Two-up grid card for the Fridge screen. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FoodItemCard(
    item: FoodItem,
    today: LocalDate,
    soonThresholdDays: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isRecallFlagged: Boolean = false,
) {
    val colors = ZeroBiteTheme.colors
    val type = ZeroBiteTheme.type
    val status = item.status(today, soonThresholdDays)
    val tint = status.tint(colors)
    AppCard(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.card))
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick),
        accent = when {
            isRecallFlagged -> colors.statusExpired
            status.needsAttention -> tint
            else -> null
        },
    ) {
        Row(verticalAlignment = Alignment.Top) {
            IconTile(item.category.icon, tint = tint)
            Spacer(Modifier.weight(1f))
            if (isRecallFlagged) RecallFlag()
        }
        Text(
            item.name,
            style = type.subheadline.semibold(),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(item.quantityText, style = type.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(4.dp))
            Text(item.shortExpiryLabel(today), style = type.caption.semibold(), color = tint, maxLines = 1)
        }
    }
}

@Composable
private fun RecallFlag() {
    Row(
        Modifier.background(ZeroBiteTheme.colors.statusExpired, RoundedCornerShape(Radius.control)).padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(Icons.Rounded.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
        Text("Recall?", style = ZeroBiteTheme.type.caption2.semibold(), color = Color.White)
    }
}

/** Full-width row, where a list reads better than a grid. */
@Composable
fun FoodItemRow(item: FoodItem, today: LocalDate, soonThresholdDays: Int, modifier: Modifier = Modifier) {
    val colors = ZeroBiteTheme.colors
    val tint = item.status(today, soonThresholdDays).tint(colors)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        IconTile(item.category.icon, tint = tint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.name, style = ZeroBiteTheme.type.subheadline.semibold(), color = colors.textPrimary)
            Text("${item.quantityText} · ${item.storage.label}", style = ZeroBiteTheme.type.caption, color = colors.textSecondary)
        }
        ExpiryBadge(item, today, soonThresholdDays)
    }
}

/** Hero card for the single most urgent item, with quick actions. */
@Composable
fun UseFirstCard(
    item: FoodItem,
    today: LocalDate,
    progress: Float,
    soonThresholdDays: Int,
    othersNeedingAttention: Int,
    onOpen: () -> Unit,
    onUsed: () -> Unit,
    onFreeze: (() -> Unit)?,
    onRecipes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZeroBiteTheme.colors
    val type = ZeroBiteTheme.type
    val status = item.status(today, soonThresholdDays)
    val tint = status.tint(colors)
    AppCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Overline("Use first", Modifier.weight(1f))
            if (othersNeedingAttention > 0) {
                Text("$othersNeedingAttention more need attention", style = type.caption, color = colors.textSecondary)
            }
        }
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen).padding(vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.name, style = type.title3.semibold(), color = colors.textPrimary)
                Text("${item.quantityText} · ${item.storage.label}", style = type.caption, color = colors.textSecondary)
            }
            Countdown(item.daysUntilExpiry(today), tint)
        }
        ShelfLifeBar(progress, tint)
        Row(Modifier.fillMaxWidth().padding(top = Spacing.md), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppButton("Used it", onUsed, Modifier.weight(1f), kind = ButtonKind.INK, compact = true)
            if (onFreeze != null) AppButton("Freeze", onFreeze, Modifier.weight(1f), kind = ButtonKind.TONAL, compact = true)
            AppButton("Recipes", onRecipes, Modifier.weight(1f), kind = ButtonKind.TONAL, compact = true)
        }
    }
}

@Composable
private fun Countdown(days: Int, tint: Color) {
    val big = ZeroBiteTheme.type.largeTitle.semibold().copy(fontSize = 40.sp, lineHeight = 44.sp)
    val small = ZeroBiteTheme.type.subheadline
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        when {
            days < 0 -> {
                Text("${-days}", style = big, letterSpacing = (-1.5).sp, color = tint, modifier = Modifier.alignByBaseline())
                Text(if (-days == 1) "day over" else "days over", style = small, color = tint, modifier = Modifier.alignByBaseline())
            }
            days == 0 -> Text("Today", style = ZeroBiteTheme.type.title.semibold(), letterSpacing = (-1).sp, color = tint)
            else -> {
                Text("$days", style = big, letterSpacing = (-1.5).sp, color = tint, modifier = Modifier.alignByBaseline())
                Text(if (days == 1) "day" else "days", style = small, color = tint, modifier = Modifier.alignByBaseline())
            }
        }
    }
}

```

- [ ] **Step 4: Run the tests and record**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests '*FoodViewsTest' && ./gradlew :app:recordRoborazziDebug --tests '*FoodViewsTest'; cd ..
```

Expected: 6 tests PASS; `food_views_light.png` and `food_views_dark.png` recorded. Compare them by eye with the iOS Fridge screenshots in `appstore/screenshots/6.9in/`.

- [ ] **Step 5: Commit**

```bash
git add android/app
git commit -m "Add the Android food item card, row, badge and use-first hero

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Room inventory and sample data

**Files:**
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/data/{FoodItemEntity,FoodItemDao,ZeroBiteDatabase,RoomInventoryRepository,SampleData}.kt`
- Generated (commit it): `android/app/schemas/com.mazesys.zerobite.data.ZeroBiteDatabase/1.json`
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/data/RoomInventoryRepositoryTest.kt`

**Interfaces:**
- Consumes: `InventoryRepository`, `FoodItem`, `FoodCategory`, `StorageLocation` (`:core`).
- Produces:
  - `class RoomInventoryRepository(dao: FoodItemDao) : InventoryRepository`, whose items are ordered by expiry date, then name case-insensitively (as iOS)
  - `ZeroBiteDatabase.open(context)` and `ZeroBiteDatabase.inMemory(context)`
  - `object SampleData { fun items(today: LocalDate, now: Instant, newId: () -> String): List<FoodItem> }` (the 21-item iOS sample fridge)

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.mazesys.zerobite.data

import androidx.test.core.app.ApplicationProvider
import com.mazesys.zerobite.core.model.FoodCategory
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.StorageLocation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class RoomInventoryRepositoryTest {

    private val db = ZeroBiteDatabase.inMemory(ApplicationProvider.getApplicationContext())
    private val repository = RoomInventoryRepository(db.foodItems())
    private val added = Instant.parse("2026-09-01T09:00:00Z")

    @After
    fun close() = db.close()

    private fun item(id: String, name: String, expires: LocalDate) = FoodItem(id = id, name = name, addedAt = added, expiresOn = expires)

    @Test
    fun `round trips every field`() = runBlocking {
        val full = FoodItem(
            id = "a", name = "Crème fraîche", quantity = 1.25, unit = "tub", category = FoodCategory.DAIRY,
            storage = StorageLocation.PANTRY, addedAt = added, expiresOn = LocalDate(2026, 9, 12),
            barcode = "5000112637922", brand = "Yeo Valley", notes = "Opened Monday",
            consumedAt = Instant.parse("2026-09-03T10:15:30Z"),
        )
        repository.add(full)
        assertEquals(full, repository.item("a"))
        assertEquals(listOf(full), repository.items.first())
    }

    @Test
    fun `items are ordered by expiry then name ignoring case`() = runBlocking {
        repository.add(item("1", "banana", LocalDate(2026, 9, 10)))
        repository.add(item("2", "Apple", LocalDate(2026, 9, 10)))
        repository.add(item("3", "Zucchini", LocalDate(2026, 9, 8)))
        assertEquals(listOf("Zucchini", "Apple", "banana"), repository.items.first().map { it.name })
    }

    @Test
    fun `update, consume, restore, delete and clear`() = runBlocking {
        repository.add(item("1", "Milk", LocalDate(2026, 9, 10)))
        repository.add(item("2", "Eggs", LocalDate(2026, 9, 20)))

        repository.update(repository.item("1")!!.copy(name = "Whole milk"))
        assertEquals("Whole milk", repository.item("1")!!.name)

        val at = Instant.parse("2026-09-07T08:00:00Z")
        repository.markConsumed(listOf("1", "2"), at)
        assertEquals(listOf(at, at), repository.items.first().map { it.consumedAt })

        repository.restore("2")
        assertNull(repository.item("2")!!.consumedAt)

        repository.delete("1")
        assertEquals(listOf("2"), repository.items.first().map { it.id })

        repository.deleteAll()
        assertEquals(emptyList(), repository.items.first())
    }

    @Test
    fun `sample fridge matches iOS`() {
        var n = 0
        val today = LocalDate(2026, 9, 7)
        val items = SampleData.items(today, added) { "id-${++n}" }
        assertEquals(21, items.size)
        assertEquals(21, items.map { it.id }.toSet().size)
        val yogurt = items.first { it.name == "Greek yogurt" }
        assertEquals(LocalDate(2026, 9, 6), yogurt.expiresOn)
        assertEquals("500 g", yogurt.quantityText)
        assertEquals(StorageLocation.FREEZER, items.first { it.name == "Salmon fillets" }.storage)
        assertEquals(StorageLocation.PANTRY, items.first { it.name == "Spaghetti" }.storage)
    }
}
```

- [ ] **Step 2: Run to see it fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests '*RoomInventoryRepositoryTest'; cd ..`
Expected: compilation FAILS: unresolved `ZeroBiteDatabase`, `RoomInventoryRepository`, `SampleData`.

- [ ] **Step 3: Implement the database**

`data/FoodItemEntity.kt`:

```kotlin
package com.mazesys.zerobite.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mazesys.zerobite.core.model.FoodCategory
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.StorageLocation
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * Row for one item. Dates are stored as ISO text (sorts correctly) and instants as epoch
 * millis, so no type converters are needed. Category and storage use the iOS raw values.
 */
@Entity(tableName = "food_items", indices = [Index("expiresOn")])
data class FoodItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val quantity: Double,
    val unit: String,
    val category: String,
    val storage: String,
    val addedAt: Long,
    val expiresOn: String,
    val barcode: String?,
    val brand: String?,
    val notes: String,
    val consumedAt: Long?,
) {
    fun toModel(): FoodItem = FoodItem(
        id = id,
        name = name,
        quantity = quantity,
        unit = unit,
        category = FoodCategory.fromRaw(category),
        storage = StorageLocation.fromRaw(storage),
        addedAt = Instant.fromEpochMilliseconds(addedAt),
        expiresOn = LocalDate.parse(expiresOn),
        barcode = barcode,
        brand = brand,
        notes = notes,
        consumedAt = consumedAt?.let(Instant::fromEpochMilliseconds),
    )

    companion object {
        fun from(item: FoodItem) = FoodItemEntity(
            id = item.id,
            name = item.name,
            quantity = item.quantity,
            unit = item.unit,
            category = item.category.raw,
            storage = item.storage.raw,
            addedAt = item.addedAt.toEpochMilliseconds(),
            expiresOn = item.expiresOn.toString(),
            barcode = item.barcode,
            brand = item.brand,
            notes = item.notes,
            consumedAt = item.consumedAt?.toEpochMilliseconds(),
        )
    }
}
```

`data/FoodItemDao.kt`:

```kotlin
package com.mazesys.zerobite.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodItemDao {
    @Query("SELECT * FROM food_items ORDER BY expiresOn, name COLLATE NOCASE")
    fun observeAll(): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_items WHERE id = :id")
    suspend fun get(id: String): FoodItemEntity?

    @Insert
    suspend fun insert(item: FoodItemEntity)

    @Update
    suspend fun update(item: FoodItemEntity)

    @Query("UPDATE food_items SET consumedAt = :at WHERE id IN (:ids)")
    suspend fun markConsumed(ids: List<String>, at: Long)

    @Query("UPDATE food_items SET consumedAt = NULL WHERE id = :id")
    suspend fun restore(id: String)

    @Query("DELETE FROM food_items WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM food_items")
    suspend fun deleteAll()
}
```

`data/ZeroBiteDatabase.kt`:

```kotlin
package com.mazesys.zerobite.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FoodItemEntity::class], version = 1, exportSchema = true)
abstract class ZeroBiteDatabase : RoomDatabase() {
    abstract fun foodItems(): FoodItemDao

    companion object {
        fun open(context: Context): ZeroBiteDatabase =
            Room.databaseBuilder(context, ZeroBiteDatabase::class.java, "zerobite.db").build()

        /** Throwaway database for tests and previews. */
        fun inMemory(context: Context): ZeroBiteDatabase =
            Room.inMemoryDatabaseBuilder(context, ZeroBiteDatabase::class.java).allowMainThreadQueries().build()
    }
}
```

`data/RoomInventoryRepository.kt`:

```kotlin
package com.mazesys.zerobite.data

import com.mazesys.zerobite.core.inventory.InventoryRepository
import com.mazesys.zerobite.core.model.FoodItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

class RoomInventoryRepository(private val dao: FoodItemDao) : InventoryRepository {

    override val items: Flow<List<FoodItem>> = dao.observeAll().map { rows -> rows.map(FoodItemEntity::toModel) }

    override suspend fun item(id: String): FoodItem? = dao.get(id)?.toModel()

    override suspend fun add(item: FoodItem) = dao.insert(FoodItemEntity.from(item))

    override suspend fun update(item: FoodItem) = dao.update(FoodItemEntity.from(item))

    override suspend fun markConsumed(ids: List<String>, at: Instant) = dao.markConsumed(ids, at.toEpochMilliseconds())

    override suspend fun restore(id: String) = dao.restore(id)

    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun deleteAll() = dao.deleteAll()
}
```

- [ ] **Step 4: Port the sample fridge** (values copied from `ios/ZeroBite/Services/Inventory/SampleData.swift`)

`data/SampleData.kt`:

```kotlin
package com.mazesys.zerobite.data

import com.mazesys.zerobite.core.model.FoodCategory
import com.mazesys.zerobite.core.model.FoodCategory.BAKERY
import com.mazesys.zerobite.core.model.FoodCategory.DAIRY
import com.mazesys.zerobite.core.model.FoodCategory.FISH
import com.mazesys.zerobite.core.model.FoodCategory.FRUIT
import com.mazesys.zerobite.core.model.FoodCategory.GRAINS
import com.mazesys.zerobite.core.model.FoodCategory.MEAT
import com.mazesys.zerobite.core.model.FoodCategory.VEGETABLES
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.StorageLocation
import com.mazesys.zerobite.core.model.StorageLocation.FREEZER
import com.mazesys.zerobite.core.model.StorageLocation.FRIDGE
import com.mazesys.zerobite.core.model.StorageLocation.PANTRY
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.time.Instant

/** A realistic fridge for "Try it with sample food", previews and screenshots. */
object SampleData {

    fun items(today: LocalDate, now: Instant, newId: () -> String): List<FoodItem> {
        fun item(name: String, quantity: Double, unit: String, category: FoodCategory, days: Int, storage: StorageLocation = FRIDGE) =
            FoodItem(
                id = newId(), name = name, quantity = quantity, unit = unit, category = category,
                storage = storage, addedAt = now, expiresOn = today.plus(days, DateTimeUnit.DAY),
            )
        return listOf(
            item("Greek yogurt", 500.0, "g", DAIRY, -1),
            item("Spinach", 1.0, "bag", VEGETABLES, 0),
            item("Chicken breast", 2.0, "pcs", MEAT, 1),
            item("Semi-skimmed milk", 2.0, "pints", DAIRY, 2),
            item("Mushrooms", 250.0, "g", VEGETABLES, 2),
            item("Sourdough bread", 1.0, "loaf", BAKERY, 2),
            item("Cherry tomatoes", 1.0, "punnet", VEGETABLES, 3),
            item("Red pepper", 2.0, "pcs", VEGETABLES, 4),
            item("Bacon", 1.0, "pack", MEAT, 5),
            item("Lemon", 3.0, "pcs", FRUIT, 8),
            item("Bananas", 5.0, "pcs", FRUIT, 4),
            item("Carrots", 6.0, "pcs", VEGETABLES, 10),
            item("Onion", 3.0, "pcs", VEGETABLES, 12),
            item("Eggs", 6.0, "pcs", DAIRY, 12),
            item("Cheddar cheese", 350.0, "g", DAIRY, 14),
            item("Potatoes", 1.5, "kg", VEGETABLES, 14, PANTRY),
            item("Garlic", 1.0, "bulb", VEGETABLES, 30, PANTRY),
            item("Butter", 250.0, "g", DAIRY, 30),
            item("Salmon fillets", 2.0, "pcs", FISH, 60, FREEZER),
            item("Basmati rice", 1.0, "kg", GRAINS, 300, PANTRY),
            item("Spaghetti", 500.0, "g", GRAINS, 400, PANTRY),
        )
    }
}
```

- [ ] **Step 5: Run the tests**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests '*RoomInventoryRepositoryTest'; cd ..`
Expected: 4 tests PASS. `android/app/schemas/com.mazesys.zerobite.data.ZeroBiteDatabase/1.json` now exists.

- [ ] **Step 6: Commit**

```bash
git add android/app
git commit -m "Store the Android fridge in Room

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Settings, recipes and the composition root

**Files:**
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/data/{Settings,BundledRecipeRepository}.kt`
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/AppDependencies.kt`
- Modify: `android/app/src/main/kotlin/com/mazesys/zerobite/ZeroBiteApplication.kt`
- Create (test support): `android/app/src/test/kotlin/com/mazesys/zerobite/testing/Fakes.kt`
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/data/SettingsTest.kt`, `BundledRecipeRepositoryTest.kt`

**Interfaces:**
- Consumes: `RecipeRepository`, `RecipeCatalogue`, `NotificationPreferences`, `AppClock`, `SystemAppClock` (`:core`); Task 7.
- Produces:
  - `data class SettingsSnapshot(hasCompletedWelcome, notificationsEnabled, leadDays, notifyHour, notifyMinute, soonThresholdDays = 3, dismissedNotificationTip, recallChecksEnabled = true, allergens: Set<String>)` with `notificationPreferences`
  - `interface SettingsRepository { val state: StateFlow<SettingsSnapshot>; suspend fun current(): SettingsSnapshot; suspend fun update(transform: (SettingsSnapshot) -> SettingsSnapshot) }`; `class DataStoreSettings(store: DataStore<Preferences>, scope: CoroutineScope) : SettingsRepository` (iOS key names)
  - `class BundledRecipeRepository(assets: AssetManager) : RecipeRepository`
  - `fun interface IdGenerator { fun next(): String }` with `IdGenerator.Random`
  - `class AppDependencies(clock, settings, inventory, recipes, ids, appScope)` and `AppDependencies.live(context)`
  - `ZeroBiteApplication.dependencies`
  - Test fakes: `FakeInventoryRepository`, `FakeSettings`, `FakeRecipes`, `SequentialIds`, `TestClock`, `MainDispatcherRule`, `testDependencies(...)`

- [ ] **Step 1: Write the test fakes** (shared by every later task's tests)

`android/app/src/test/kotlin/com/mazesys/zerobite/testing/Fakes.kt`:

```kotlin
package com.mazesys.zerobite.testing

import com.mazesys.zerobite.AppDependencies
import com.mazesys.zerobite.IdGenerator
import com.mazesys.zerobite.core.inventory.InventoryRepository
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.Recipe
import com.mazesys.zerobite.core.recipes.RecipeRepository
import com.mazesys.zerobite.core.time.AppClock
import com.mazesys.zerobite.core.time.FixedClock
import com.mazesys.zerobite.data.SampleData
import com.mazesys.zerobite.data.SettingsRepository
import com.mazesys.zerobite.data.SettingsSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import kotlin.time.Instant

/** Mon 7 Sep 2026, 09:00 in London. */
object TestClock {
    val zone: TimeZone = TimeZone.of("Europe/London")
    val now: Instant = Instant.parse("2026-09-07T08:00:00Z")
    val today: LocalDate = LocalDate(2026, 9, 7)
    fun fixed(): AppClock = FixedClock(now, zone)
}

class SequentialIds(private val prefix: String = "id") : IdGenerator {
    private var n = 0
    override fun next(): String = "$prefix-${++n}"
}

fun sampleFridge(): List<FoodItem> = SampleData.items(TestClock.today, TestClock.now, SequentialIds("sample")::next)

class FakeInventoryRepository(initial: List<FoodItem> = emptyList()) : InventoryRepository {
    private val state = MutableStateFlow(sorted(initial))
    val current: List<FoodItem> get() = state.value
    override val items: Flow<List<FoodItem>> = state

    override suspend fun item(id: String) = state.value.firstOrNull { it.id == id }
    override suspend fun add(item: FoodItem) { state.value = sorted(state.value + item) }
    override suspend fun update(item: FoodItem) { state.value = sorted(state.value.map { if (it.id == item.id) item else it }) }
    override suspend fun markConsumed(ids: List<String>, at: Instant) {
        state.value = state.value.map { if (it.id in ids) it.copy(consumedAt = at) else it }
    }
    override suspend fun restore(id: String) { state.value = state.value.map { if (it.id == id) it.copy(consumedAt = null) else it } }
    override suspend fun delete(id: String) { state.value = state.value.filterNot { it.id == id } }
    override suspend fun deleteAll() { state.value = emptyList() }

    private fun sorted(items: List<FoodItem>) = items.sortedWith(compareBy<FoodItem> { it.expiresOn }.thenBy { it.name.lowercase() })
}

class FakeSettings(initial: SettingsSnapshot = SettingsSnapshot()) : SettingsRepository {
    private val flow = MutableStateFlow(initial)
    override val state: StateFlow<SettingsSnapshot> = flow
    override suspend fun current() = flow.value
    override suspend fun update(transform: (SettingsSnapshot) -> SettingsSnapshot) { flow.value = transform(flow.value) }
}

class FakeRecipes(private val recipes: List<Recipe> = emptyList()) : RecipeRepository {
    override suspend fun recipes() = recipes
}

fun testDependencies(
    inventory: FakeInventoryRepository = FakeInventoryRepository(),
    settings: FakeSettings = FakeSettings(SettingsSnapshot(hasCompletedWelcome = true)),
    recipes: FakeRecipes = FakeRecipes(),
    clock: AppClock = TestClock.fixed(),
    ids: IdGenerator = SequentialIds(),
) = AppDependencies(clock, settings, inventory, recipes, ids, CoroutineScope(Dispatchers.Unconfined))

/** Runs `viewModelScope` work immediately on the test thread. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
```

- [ ] **Step 2: Write the failing tests**

`data/SettingsTest.kt`:

```kotlin
package com.mazesys.zerobite.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class SettingsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun open(file: File): Pair<DataStoreSettings, Job> {
        val job = Job()
        val scope = CoroutineScope(Dispatchers.IO + job)
        return DataStoreSettings(PreferenceDataStoreFactory.create(scope = scope) { file }, scope) to job
    }

    @Test
    fun `defaults match iOS`() = runBlocking {
        val (settings, job) = open(File(folder.root, "settings.preferences_pb"))
        val snapshot = settings.current()
        assertEquals(false, snapshot.hasCompletedWelcome)
        assertEquals(3, snapshot.soonThresholdDays)
        assertEquals(true, snapshot.recallChecksEnabled)
        assertEquals(com.mazesys.zerobite.core.notifications.NotificationPreferences.DEFAULT, snapshot.notificationPreferences)
        job.cancelAndJoin()
    }

    @Test
    fun `updates persist and reach the state flow`() = runBlocking {
        val file = File(folder.root, "settings.preferences_pb")
        val (settings, job) = open(file)
        settings.update { it.copy(hasCompletedWelcome = true, soonThresholdDays = 5, allergens = setOf("milk", "peanuts")) }
        assertEquals(5, settings.state.first { it.soonThresholdDays == 5 }.soonThresholdDays)
        job.cancelAndJoin()

        val (reopened, job2) = open(file)
        val snapshot = reopened.current()
        assertEquals(true, snapshot.hasCompletedWelcome)
        assertEquals(setOf("milk", "peanuts"), snapshot.allergens)
        job2.cancelAndJoin()
    }
}
```

`data/BundledRecipeRepositoryTest.kt`:

```kotlin
package com.mazesys.zerobite.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class BundledRecipeRepositoryTest {
    @Test
    fun `loads the bundled catalogue once`() = runBlocking {
        val repository = BundledRecipeRepository(ApplicationProvider.getApplicationContext<android.content.Context>().assets)
        val first = repository.recipes()
        assertTrue(first.size >= 30)
        assertSame(first, repository.recipes())
    }
}
```

- [ ] **Step 3: Run to see it fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests '*SettingsTest' --tests '*BundledRecipeRepositoryTest'; cd ..`
Expected: compilation FAILS: unresolved `DataStoreSettings`, `BundledRecipeRepository`, `AppDependencies`, `IdGenerator`.

- [ ] **Step 4: Implement settings**

`data/Settings.kt`:

```kotlin
package com.mazesys.zerobite.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.mazesys.zerobite.core.notifications.NotificationPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** User preferences, with the iOS defaults. */
data class SettingsSnapshot(
    /** First-run welcome (intro + notification opt-in) has been seen. */
    val hasCompletedWelcome: Boolean = false,
    val notificationsEnabled: Boolean = false,
    /** Days before expiry for the first reminder. */
    val leadDays: Int = NotificationPreferences.DEFAULT.leadDays,
    val notifyHour: Int = NotificationPreferences.DEFAULT.hour,
    val notifyMinute: Int = NotificationPreferences.DEFAULT.minute,
    /** Items expiring within this many days count as "expiring soon". */
    val soonThresholdDays: Int = 3,
    /** The Fridge "turn on reminders" tip was answered. */
    val dismissedNotificationTip: Boolean = false,
    /** Check the Food Standards Agency for recalls and allergy alerts. */
    val recallChecksEnabled: Boolean = true,
    /** Allergen raw values the user wants allergy alerts for. */
    val allergens: Set<String> = emptySet(),
) {
    val notificationPreferences: NotificationPreferences
        get() = NotificationPreferences(notificationsEnabled, leadDays, notifyHour, notifyMinute)
}

interface SettingsRepository {
    /** Live settings. Before the first read it holds the defaults, so use [current] for decisions. */
    val state: StateFlow<SettingsSnapshot>
    suspend fun current(): SettingsSnapshot
    suspend fun update(transform: (SettingsSnapshot) -> SettingsSnapshot)
}

class DataStoreSettings(private val store: DataStore<Preferences>, scope: CoroutineScope) : SettingsRepository {

    override val state: StateFlow<SettingsSnapshot> =
        store.data.map(::read).stateIn(scope, SharingStarted.Eagerly, SettingsSnapshot())

    override suspend fun current(): SettingsSnapshot = read(store.data.first())

    override suspend fun update(transform: (SettingsSnapshot) -> SettingsSnapshot) {
        store.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    /** Same key names as iOS `AppSettings`. */
    private object Keys {
        val hasCompletedWelcome = booleanPreferencesKey("hasCompletedWelcome")
        val notificationsEnabled = booleanPreferencesKey("notificationsEnabled")
        val leadDays = intPreferencesKey("notificationLeadDays")
        val notifyHour = intPreferencesKey("notifyHour")
        val notifyMinute = intPreferencesKey("notifyMinute")
        val soonThresholdDays = intPreferencesKey("soonThresholdDays")
        val dismissedNotificationTip = booleanPreferencesKey("dismissedNotificationTip")
        val recallChecksEnabled = booleanPreferencesKey("recallChecksEnabled")
        val allergens = stringSetPreferencesKey("allergens")
    }

    private fun read(prefs: Preferences): SettingsSnapshot {
        val defaults = SettingsSnapshot()
        return SettingsSnapshot(
            hasCompletedWelcome = prefs[Keys.hasCompletedWelcome] ?: defaults.hasCompletedWelcome,
            notificationsEnabled = prefs[Keys.notificationsEnabled] ?: defaults.notificationsEnabled,
            leadDays = prefs[Keys.leadDays] ?: defaults.leadDays,
            notifyHour = prefs[Keys.notifyHour] ?: defaults.notifyHour,
            notifyMinute = prefs[Keys.notifyMinute] ?: defaults.notifyMinute,
            soonThresholdDays = prefs[Keys.soonThresholdDays] ?: defaults.soonThresholdDays,
            dismissedNotificationTip = prefs[Keys.dismissedNotificationTip] ?: defaults.dismissedNotificationTip,
            recallChecksEnabled = prefs[Keys.recallChecksEnabled] ?: defaults.recallChecksEnabled,
            allergens = prefs[Keys.allergens] ?: defaults.allergens,
        )
    }

    private fun write(prefs: MutablePreferences, s: SettingsSnapshot) {
        prefs[Keys.hasCompletedWelcome] = s.hasCompletedWelcome
        prefs[Keys.notificationsEnabled] = s.notificationsEnabled
        prefs[Keys.leadDays] = s.leadDays
        prefs[Keys.notifyHour] = s.notifyHour
        prefs[Keys.notifyMinute] = s.notifyMinute
        prefs[Keys.soonThresholdDays] = s.soonThresholdDays
        prefs[Keys.dismissedNotificationTip] = s.dismissedNotificationTip
        prefs[Keys.recallChecksEnabled] = s.recallChecksEnabled
        prefs[Keys.allergens] = s.allergens
    }
}
```

- [ ] **Step 5: Implement the recipe repository and composition root**

`data/BundledRecipeRepository.kt`:

```kotlin
package com.mazesys.zerobite.data

import android.content.res.AssetManager
import com.mazesys.zerobite.core.model.Recipe
import com.mazesys.zerobite.core.model.RecipeCatalogue
import com.mazesys.zerobite.core.recipes.RecipeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The catalogue bundled from /shared-assets/recipes.json; read once, then cached. */
class BundledRecipeRepository(private val assets: AssetManager) : RecipeRepository {
    private val mutex = Mutex()
    private var cached: List<Recipe>? = null

    override suspend fun recipes(): List<Recipe> = mutex.withLock {
        cached ?: withContext(Dispatchers.IO) {
            RecipeCatalogue.decode(assets.open("recipes.json").bufferedReader().use { it.readText() })
        }.also { cached = it }
    }
}
```

`AppDependencies.kt`:

```kotlin
package com.mazesys.zerobite

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.mazesys.zerobite.core.inventory.InventoryRepository
import com.mazesys.zerobite.core.recipes.RecipeRepository
import com.mazesys.zerobite.core.time.AppClock
import com.mazesys.zerobite.core.time.SystemAppClock
import com.mazesys.zerobite.data.BundledRecipeRepository
import com.mazesys.zerobite.data.DataStoreSettings
import com.mazesys.zerobite.data.RoomInventoryRepository
import com.mazesys.zerobite.data.SettingsRepository
import com.mazesys.zerobite.data.ZeroBiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.uuid.Uuid

fun interface IdGenerator {
    fun next(): String

    companion object {
        val Random = IdGenerator { Uuid.random().toString() }
    }
}

/** One DataStore per process, as DataStore requires. */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Composition root: the long-lived services handed to coordinators, factories and view models. */
class AppDependencies(
    val clock: AppClock,
    val settings: SettingsRepository,
    val inventory: InventoryRepository,
    val recipes: RecipeRepository,
    val ids: IdGenerator,
    /** Outlives screens: background syncs (reminders, recalls) run here in Plan 3. */
    val appScope: CoroutineScope,
) {
    companion object {
        fun live(context: Context): AppDependencies {
            val app = context.applicationContext
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            return AppDependencies(
                clock = SystemAppClock,
                settings = DataStoreSettings(app.settingsDataStore, scope),
                inventory = RoomInventoryRepository(ZeroBiteDatabase.open(app).foodItems()),
                recipes = BundledRecipeRepository(app.assets),
                ids = IdGenerator.Random,
                appScope = scope,
            )
        }
    }
}
```

`ZeroBiteApplication.kt`:

```kotlin
package com.mazesys.zerobite

import android.app.Application

class ZeroBiteApplication : Application() {
    val dependencies: AppDependencies by lazy { AppDependencies.live(this) }
}
```

- [ ] **Step 6: Run the tests**

Run: `cd android && ./gradlew :app:testDebugUnitTest; cd ..`
Expected: every `:app` test so far PASSES, including SettingsTest (2) and BundledRecipeRepositoryTest (1).

- [ ] **Step 7: Commit**

```bash
git add android/app
git commit -m "Add Android settings, recipe catalogue and composition root

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Coordination: app flow, tabs, sheets, Fridge navigation

**Files:**
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/coordination/{AppFlowController,AppCoordinator,InventoryCoordinator}.kt`
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/coordination/CoordinationTest.kt`

**Interfaces:**
- Consumes: `SettingsRepository` (Task 8), `ItemDraft` (`:core`).
- Produces:
  - `enum class AppFlowPhase { LAUNCH, WELCOME, MAIN }`; `class AppFlowController(settings)` with `phase: StateFlow<AppFlowPhase>`, `suspend transitionFromLaunch()`, `suspend transitionToMain()`, `transitionToWelcome()`
  - `enum class AppTab(val title: String) { FRIDGE, RECIPES, SETTINGS }`
  - `sealed interface SheetDestination { Scan; AddItem(draft); EditItem(itemId) }`; `data class PresentedSheet(id: Long, destination)`
  - `data class AlertConfig(title, message, primary: AlertButton, secondary: AlertButton?)`; `data class AlertButton(title, isDestructive = false, action = {})`
  - `class AppCoordinator(today: () -> LocalDate)` with `selectedTab`, `presentedSheet`, `alert` (Compose state), `inventory: InventoryCoordinator`, `selectTab`, `presentSheet`, `dismissSheet`, `showAlert`, `dismissAlert`, `showItem(id)` (deep link)
  - `@Serializable sealed interface InventoryRoute : NavKey { Fridge; ItemDetail(itemId) }`; `class InventoryCoordinator(parent)` with `backStack: SnapshotStateList<InventoryRoute>`, `showItem`, `showScan`, `showAddItem`, `showEditItem`, `pop`, `popToRoot`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.mazesys.zerobite.coordination

import com.mazesys.zerobite.data.SettingsSnapshot
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.TestClock
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoordinationTest {

    @Test
    fun `launch goes to welcome until it has been completed`() = runBlocking {
        val settings = FakeSettings()
        val flow = AppFlowController(settings)
        assertEquals(AppFlowPhase.LAUNCH, flow.phase.value)
        flow.transitionFromLaunch()
        assertEquals(AppFlowPhase.WELCOME, flow.phase.value)
        flow.transitionToMain()
        assertEquals(AppFlowPhase.MAIN, flow.phase.value)
        assertTrue(settings.current().hasCompletedWelcome)

        val returning = AppFlowController(FakeSettings(SettingsSnapshot(hasCompletedWelcome = true)))
        returning.transitionFromLaunch()
        assertEquals(AppFlowPhase.MAIN, returning.phase.value)
    }

    @Test
    fun `add item presents a sheet with a week-long default date`() {
        val app = AppCoordinator { TestClock.today }
        app.inventory.showAddItem()
        val sheet = app.presentedSheet!!
        val destination = assertIs<SheetDestination.AddItem>(sheet.destination)
        assertEquals(LocalDate(2026, 9, 14), destination.draft.expiresOn)

        app.inventory.showAddItem()
        assertTrue(app.presentedSheet!!.id != sheet.id, "each presentation gets its own id")
        app.dismissSheet()
        assertNull(app.presentedSheet)
    }

    @Test
    fun `deep link to an item resets to the fridge and opens it`() {
        val app = AppCoordinator { TestClock.today }
        app.selectTab(AppTab.SETTINGS)
        app.inventory.showItem("old")
        app.presentSheet(SheetDestination.Scan)

        app.showItem("milk")

        assertNull(app.presentedSheet)
        assertEquals(AppTab.FRIDGE, app.selectedTab)
        assertEquals(listOf(InventoryRoute.Fridge, InventoryRoute.ItemDetail("milk")), app.inventory.backStack.toList())
    }

    @Test
    fun `popping never removes the fridge root`() {
        val app = AppCoordinator { TestClock.today }
        app.inventory.pop()
        assertEquals(listOf<InventoryRoute>(InventoryRoute.Fridge), app.inventory.backStack.toList())
        app.inventory.showItem("a")
        app.inventory.pop()
        assertEquals(listOf<InventoryRoute>(InventoryRoute.Fridge), app.inventory.backStack.toList())
    }

    @Test
    fun `alerts show and dismiss`() {
        val app = AppCoordinator { TestClock.today }
        app.showAlert(AlertConfig("Delete this item?", primary = AlertButton("Delete", isDestructive = true)))
        assertEquals("Delete this item?", app.alert?.title)
        app.dismissAlert()
        assertNull(app.alert)
    }
}
```

- [ ] **Step 2: Run to see it fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests '*CoordinationTest'; cd ..`
Expected: compilation FAILS: unresolved `AppFlowController`, `AppCoordinator`.

- [ ] **Step 3: Implement**

`coordination/AppFlowController.kt`:

```kotlin
package com.mazesys.zerobite.coordination

import com.mazesys.zerobite.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which root screen is shown: the single source of truth for app-level flow. */
enum class AppFlowPhase { LAUNCH, WELCOME, MAIN }

class AppFlowController(private val settings: SettingsRepository) {

    private val _phase = MutableStateFlow(AppFlowPhase.LAUNCH)
    val phase: StateFlow<AppFlowPhase> = _phase.asStateFlow()

    /** Call when the splash is done. Skips the welcome flow once it has been completed. */
    suspend fun transitionFromLaunch() {
        _phase.value = if (settings.current().hasCompletedWelcome) AppFlowPhase.MAIN else AppFlowPhase.WELCOME
    }

    /** Call when the welcome flow finishes. */
    suspend fun transitionToMain() {
        settings.update { it.copy(hasCompletedWelcome = true) }
        _phase.value = AppFlowPhase.MAIN
    }

    /** Re-run the welcome flow (from Settings, in Plan 3). */
    fun transitionToWelcome() {
        _phase.value = AppFlowPhase.WELCOME
    }
}
```

`coordination/AppCoordinator.kt`:

```kotlin
package com.mazesys.zerobite.coordination

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mazesys.zerobite.core.model.ItemDraft
import kotlinx.datetime.LocalDate

enum class AppTab(val title: String) {
    FRIDGE("Fridge"),
    RECIPES("Recipes"),
    SETTINGS("Settings"),
}

sealed interface SheetDestination {
    data object Scan : SheetDestination
    data class AddItem(val draft: ItemDraft) : SheetDestination
    data class EditItem(val itemId: String) : SheetDestination
}

/** One presentation of a sheet; `id` is unique per presentation so its view models are fresh. */
data class PresentedSheet(val id: Long, val destination: SheetDestination)

data class AlertButton(val title: String, val isDestructive: Boolean = false, val action: () -> Unit = {})

data class AlertConfig(
    val title: String,
    val message: String? = null,
    val primary: AlertButton,
    val secondary: AlertButton? = null,
)

/** Main-content coordinator: tabs, sheets, alerts and the feature coordinators. */
class AppCoordinator(val today: () -> LocalDate) {

    var selectedTab by mutableStateOf(AppTab.FRIDGE)
        private set
    var presentedSheet by mutableStateOf<PresentedSheet?>(null)
        private set
    var alert by mutableStateOf<AlertConfig?>(null)
        private set

    private var sheetCount = 0L

    val inventory = InventoryCoordinator(this)

    fun selectTab(tab: AppTab) {
        selectedTab = tab
    }

    fun presentSheet(destination: SheetDestination) {
        presentedSheet = PresentedSheet(++sheetCount, destination)
    }

    fun dismissSheet() {
        presentedSheet = null
    }

    fun showAlert(config: AlertConfig) {
        alert = config
    }

    fun dismissAlert() {
        alert = null
    }

    /** Deep link (e.g. a notification tap): jump to the Fridge tab and open the item. */
    fun showItem(id: String) {
        presentedSheet = null
        selectedTab = AppTab.FRIDGE
        inventory.popToRoot()
        inventory.showItem(id)
    }
}
```

`coordination/InventoryCoordinator.kt`:

```kotlin
package com.mazesys.zerobite.coordination

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey
import com.mazesys.zerobite.core.model.ItemDraft
import kotlinx.serialization.Serializable

@Serializable
sealed interface InventoryRoute : NavKey {
    @Serializable
    data object Fridge : InventoryRoute

    @Serializable
    data class ItemDetail(val itemId: String) : InventoryRoute
}

/** Navigation within the Fridge tab. The back stack is the Android counterpart of iOS's NavigationPath. */
class InventoryCoordinator(private val parent: AppCoordinator) {

    val backStack: SnapshotStateList<InventoryRoute> = mutableStateListOf(InventoryRoute.Fridge)

    fun showItem(id: String) {
        backStack.add(InventoryRoute.ItemDetail(id))
    }

    fun showScan() = parent.presentSheet(SheetDestination.Scan)

    fun showAddItem() = parent.presentSheet(SheetDestination.AddItem(ItemDraft.new(parent.today())))

    fun showEditItem(id: String) = parent.presentSheet(SheetDestination.EditItem(id))

    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun popToRoot() {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests '*CoordinationTest'; cd ..`
Expected: 5 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app
git commit -m "Add Android app flow, tab, sheet and Fridge coordinators

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: Fridge list

**Files:**
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/ui/format/DateText.kt`
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/ui/components/Menus.kt`
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/screens/inventory/{InventoryViewModel,InventoryScreen}.kt`
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/screens/inventory/{InventoryViewModelTest,InventoryScreenTest}.kt`

**Interfaces:**
- Consumes: Tasks 4–9; `SampleData`; `shelfLifeProgress`.
- Produces:
  - `object DateText { header(date) = "Mon 7 Sep"; long(date) = "7 September 2026"; abbreviated(date) = "7 Sep 2026" }`
  - `AppDropdownMenu(expanded, onDismiss, modifier, content)` and `AppMenuItem(title, icon, onClick, destructive = false)`
  - `enum class StorageFilter(title, location) { ALL, FRIDGE, FREEZER, PANTRY }`, `data class InventorySection(status, items)`
  - `data class InventoryUiState(...)` with `isEmpty`, `othersNeedingAttention`, `canFreeze(item)`, `emptyGridMessage`; `InventoryUiState.build(active, searchText, filter, soonThresholdDays, today)`
  - `class InventoryViewModel(inventory, settings, clock, ids, coordinator: InventoryCoordinator)` with `state`, `setSearchText`, `setStorageFilter`, `markUsed`, `freeze`, `delete`, `showDetails`, `showScan`, `showAddItem`, `showEditItem`, `loadSampleData`
  - `@Composable fun InventoryScreen(viewModel: InventoryViewModel, modifier: Modifier = Modifier)`

- [ ] **Step 1: Write the failing view-model tests** (ported from `ios/ZeroBiteTests/InventoryTests.swift`)

```kotlin
package com.mazesys.zerobite.screens.inventory

import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.coordination.InventoryRoute
import com.mazesys.zerobite.coordination.SheetDestination
import com.mazesys.zerobite.core.model.StorageLocation
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.SequentialIds
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.sampleFridge
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InventoryViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val inventory = FakeInventoryRepository(sampleFridge())
    private val app = AppCoordinator { TestClock.today }

    private fun viewModel(repository: FakeInventoryRepository = inventory) =
        InventoryViewModel(repository, FakeSettings(), TestClock.fixed(), SequentialIds(), app.inventory)

    private fun InventoryUiState.gridNames() = gridSections.flatMap { it.items }.map { it.name }

    @Test
    fun `sections are ordered by urgency and the hero leaves the grid`() {
        val state = viewModel().state.value
        assertEquals("Greek yogurt", state.useFirstItem?.name)
        val statuses = state.gridSections.map { it.status }
        assertEquals(statuses.sorted(), statuses)
        assertFalse("Greek yogurt" in state.gridNames())
        assertEquals(state.attentionCount - 1, state.othersNeedingAttention)
        assertEquals("Mon 7 Sep", state.headerDate)
    }

    @Test
    fun `search and storage filter narrow the list`() {
        val vm = viewModel()
        vm.setSearchText("milk")
        assertEquals(listOf("Semi-skimmed milk"), vm.state.value.gridNames())
        assertNull(vm.state.value.useFirstItem, "no hero while searching")

        vm.setSearchText("")
        vm.setStorageFilter(StorageFilter.FREEZER)
        assertEquals(listOf("Salmon fillets"), vm.state.value.gridNames())
    }

    @Test
    fun `an empty search result explains itself`() {
        val vm = viewModel()
        vm.setSearchText("caviar")
        assertEquals(emptyList(), vm.state.value.gridNames())
        assertEquals("Nothing matches “caviar”.", vm.state.value.emptyGridMessage)
    }

    @Test
    fun `freezing moves to the freezer for at least three months`() {
        val vm = viewModel()
        val spinach = inventory.current.first { it.name == "Spinach" }
        vm.freeze(spinach)
        val frozen = inventory.current.first { it.id == spinach.id }
        assertEquals(StorageLocation.FREEZER, frozen.storage)
        assertEquals(LocalDate(2026, 12, 7), frozen.expiresOn)
        assertFalse(vm.state.value.canFreeze(frozen))

        val yogurt = inventory.current.first { it.name == "Greek yogurt" }
        assertFalse(vm.state.value.canFreeze(yogurt), "expired food can't be frozen")
        vm.freeze(yogurt)
        assertEquals(StorageLocation.FRIDGE, inventory.current.first { it.id == yogurt.id }.storage)
    }

    @Test
    fun `marking used removes it from the active list`() {
        val vm = viewModel()
        val before = vm.state.value.activeItems.size
        val spinach = inventory.current.first { it.name == "Spinach" }
        vm.markUsed(spinach)
        assertEquals(before - 1, vm.state.value.activeItems.size)
        assertEquals(TestClock.now, inventory.current.first { it.id == spinach.id }.consumedAt)
    }

    @Test
    fun `an empty fridge can load the sample food`() {
        val vm = viewModel(FakeInventoryRepository())
        assertTrue(vm.state.value.isEmpty)
        vm.loadSampleData()
        assertEquals(21, vm.state.value.activeItems.size)
    }

    @Test
    fun `actions route through the coordinator`() {
        val vm = viewModel()
        val spinach = inventory.current.first { it.name == "Spinach" }
        vm.showDetails(spinach)
        assertEquals(InventoryRoute.ItemDetail(spinach.id), app.inventory.backStack.last())
        vm.showAddItem()
        assertIs<SheetDestination.AddItem>(app.presentedSheet?.destination)
        vm.showEditItem(spinach)
        assertEquals(SheetDestination.EditItem(spinach.id), app.presentedSheet?.destination)
        vm.showScan()
        assertEquals(SheetDestination.Scan, app.presentedSheet?.destination)
    }
}
```

- [ ] **Step 2: Write the failing screen tests**

```kotlin
package com.mazesys.zerobite.screens.inventory

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.coordination.InventoryRoute
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.SequentialIds
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.sampleFridge
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class InventoryScreenTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val app = AppCoordinator { TestClock.today }

    private fun show(inventory: FakeInventoryRepository, dark: Boolean = false, fontScale: Float? = null) {
        val vm = InventoryViewModel(inventory, FakeSettings(), TestClock.fixed(), SequentialIds(), app.inventory)
        compose.setContent {
            ZeroBiteTheme(darkTheme = dark) {
                if (fontScale == null) {
                    InventoryScreen(vm)
                } else {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) { InventoryScreen(vm) }
                }
            }
        }
    }

    @Test
    fun `empty fridge offers sample food`() {
        show(FakeInventoryRepository())
        compose.onNodeWithText("Your fridge is empty").assertIsDisplayed()
        compose.onNodeWithText("Try it with sample food").performClick()
        compose.onNodeWithText("Greek yogurt").assertIsDisplayed()
    }

    @Test
    fun `tapping a card opens its detail`() {
        val inventory = FakeInventoryRepository(sampleFridge())
        show(inventory)
        compose.onNodeWithText("Spinach").performClick()
        val spinach = inventory.current.first { it.name == "Spinach" }
        assertEquals(InventoryRoute.ItemDetail(spinach.id), app.inventory.backStack.last())
    }

    @Test
    fun `search narrows the grid`() {
        show(FakeInventoryRepository(sampleFridge()))
        compose.onNodeWithTag("inventory.search").performTextInput("milk")
        compose.onNodeWithText("Semi-skimmed milk").assertIsDisplayed()
        compose.onNodeWithText("Spinach").assertDoesNotExist()
    }

    @Test
    fun `large font scale keeps names visible`() {
        show(FakeInventoryRepository(sampleFridge()), fontScale = 2f)
        compose.onNodeWithText("Fridge").assertIsDisplayed()
        compose.onNodeWithText("Greek yogurt").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/fridge_font2.png")
    }

    @Test
    fun `fridge light`() {
        show(FakeInventoryRepository(sampleFridge()))
        compose.onRoot().captureRoboImage("src/test/screenshots/fridge_light.png")
    }

    @Test
    fun `fridge dark`() {
        show(FakeInventoryRepository(sampleFridge()), dark = true)
        compose.onRoot().captureRoboImage("src/test/screenshots/fridge_dark.png")
    }

    @Test
    fun `empty fridge light`() {
        show(FakeInventoryRepository())
        compose.onRoot().captureRoboImage("src/test/screenshots/fridge_empty_light.png")
    }
}
```

- [ ] **Step 3: Run to see them fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests '*Inventory*Test'; cd ..`
Expected: compilation FAILS: unresolved `InventoryViewModel`, `StorageFilter`, `InventoryScreen`.

- [ ] **Step 4: Implement date text and menus**

`ui/format/DateText.kt`:

```kotlin
package com.mazesys.zerobite.ui.format

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/** en-GB date strings matching the iOS app. */
object DateText {
    private val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val MONTHS_SHORT = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val MONTHS_LONG = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )

    /** "Mon 7 Sep": the Fridge header overline. */
    fun header(date: LocalDate): String = "${DAYS[date.dayOfWeek.ordinal]} ${date.day} ${MONTHS_SHORT[date.month.number - 1]}"

    /** "7 September 2026". */
    fun long(date: LocalDate): String = "${date.day} ${MONTHS_LONG[date.month.number - 1]} ${date.year}"

    /** "7 Sep 2026". */
    fun abbreviated(date: LocalDate): String = "${date.day} ${MONTHS_SHORT[date.month.number - 1]} ${date.year}"
}
```

`ui/components/Menus.kt`:

```kotlin
package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme

/** Material dropdown restyled: card surface, hairline border, tight corners. */
@Composable
fun AppDropdownMenu(expanded: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = ZeroBiteTheme.colors
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = RoundedCornerShape(Radius.card),
        containerColor = colors.surface,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, colors.hairline),
        content = content,
    )
}

@Composable
fun AppMenuItem(title: String, icon: ImageVector, onClick: () -> Unit, destructive: Boolean = false) {
    val colors = ZeroBiteTheme.colors
    val tint = if (destructive) colors.statusExpired else colors.textPrimary
    DropdownMenuItem(
        text = { Text(title, style = ZeroBiteTheme.type.subheadline, color = tint) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) },
        onClick = onClick,
    )
}
```

- [ ] **Step 5: Implement the view model**

`screens/inventory/InventoryViewModel.kt`:

```kotlin
package com.mazesys.zerobite.screens.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazesys.zerobite.IdGenerator
import com.mazesys.zerobite.coordination.InventoryCoordinator
import com.mazesys.zerobite.core.inventory.InventoryRepository
import com.mazesys.zerobite.core.model.ExpiryStatus
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.StorageLocation
import com.mazesys.zerobite.core.time.AppClock
import com.mazesys.zerobite.data.SampleData
import com.mazesys.zerobite.data.SettingsRepository
import com.mazesys.zerobite.ui.components.shelfLifeProgress
import com.mazesys.zerobite.ui.format.DateText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

enum class StorageFilter(val title: String, val location: StorageLocation?) {
    ALL("All", null),
    FRIDGE("Fridge", StorageLocation.FRIDGE),
    FREEZER("Freezer", StorageLocation.FREEZER),
    PANTRY("Cupboard", StorageLocation.PANTRY),
}

data class InventorySection(val status: ExpiryStatus, val items: List<FoodItem>)

data class InventoryUiState(
    val today: LocalDate,
    val isLoading: Boolean = true,
    val headerDate: String = DateText.header(today),
    /** Items not yet used up, soonest expiry first. */
    val activeItems: List<FoodItem> = emptyList(),
    val searchText: String = "",
    val storageFilter: StorageFilter = StorageFilter.ALL,
    val soonThresholdDays: Int = 3,
    /** The single most urgent item, highlighted above the grid while browsing (not searching). */
    val useFirstItem: FoodItem? = null,
    val useFirstProgress: Float = 0f,
    val attentionCount: Int = 0,
    /** Sections below the hero card, which already shows its item. */
    val gridSections: List<InventorySection> = emptyList(),
) {
    val isEmpty: Boolean get() = !isLoading && activeItems.isEmpty()

    /** Items needing attention other than the highlighted one. */
    val othersNeedingAttention: Int get() = maxOf(attentionCount - if (useFirstItem == null) 0 else 1, 0)

    val emptyGridMessage: String
        get() = if (searchText.isBlank()) "Nothing stored here." else "Nothing matches “$searchText”."

    /** Freezing only makes sense for food that hasn't gone off and isn't frozen already. */
    fun canFreeze(item: FoodItem): Boolean =
        item.storage != StorageLocation.FREEZER && item.status(today, soonThresholdDays) != ExpiryStatus.EXPIRED

    companion object {
        fun build(
            active: List<FoodItem>,
            searchText: String,
            filter: StorageFilter,
            soonThresholdDays: Int,
            today: LocalDate,
        ): InventoryUiState {
            val query = searchText.trim().lowercase()
            val filtered = active.filter { item ->
                (filter.location == null || item.storage == filter.location) &&
                    (query.isEmpty() || item.name.lowercase().contains(query))
            }
            fun needsAttention(item: FoodItem) = item.status(today, soonThresholdDays).needsAttention
            val hero = if (query.isNotEmpty()) null else filtered.filter(::needsAttention).minByOrNull { it.expiresOn }
            val grid = filtered.filter { it.id != hero?.id }
            val sections = ExpiryStatus.entries.mapNotNull { status ->
                grid.filter { it.status(today, soonThresholdDays) == status }
                    .takeIf { it.isNotEmpty() }
                    ?.let { InventorySection(status, it) }
            }
            return InventoryUiState(
                today = today,
                isLoading = false,
                activeItems = active,
                searchText = searchText,
                storageFilter = filter,
                soonThresholdDays = soonThresholdDays,
                useFirstItem = hero,
                attentionCount = active.count(::needsAttention),
                gridSections = sections,
            )
        }
    }
}

/** The Fridge list: filtering, grouping by urgency, and item actions. */
class InventoryViewModel(
    private val inventory: InventoryRepository,
    settings: SettingsRepository,
    private val clock: AppClock,
    private val ids: IdGenerator,
    private val coordinator: InventoryCoordinator,
) : ViewModel() {

    private val searchText = MutableStateFlow("")
    private val storageFilter = MutableStateFlow(StorageFilter.ALL)

    val state: StateFlow<InventoryUiState> =
        combine(inventory.items, settings.state, searchText, storageFilter) { items, s, query, filter ->
            val built = InventoryUiState.build(items.filterNot { it.isConsumed }, query, filter, s.soonThresholdDays, clock.today())
            built.copy(useFirstProgress = built.useFirstItem?.shelfLifeProgress(clock.now(), clock.timeZone) ?: 0f)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, InventoryUiState(today = clock.today()))

    fun setSearchText(text: String) {
        searchText.value = text
    }

    fun setStorageFilter(filter: StorageFilter) {
        storageFilter.value = filter
    }

    fun markUsed(item: FoodItem) {
        viewModelScope.launch { inventory.markConsumed(listOf(item.id), clock.now()) }
    }

    /** Moves an item to the freezer with a typical frozen shelf life of three months. */
    fun freeze(item: FoodItem) {
        if (!state.value.canFreeze(item)) return
        val frozenUntil = clock.today().plus(3, DateTimeUnit.MONTH)
        viewModelScope.launch {
            inventory.update(item.copy(storage = StorageLocation.FREEZER, expiresOn = maxOf(item.expiresOn, frozenUntil)))
        }
    }

    fun delete(item: FoodItem) {
        viewModelScope.launch { inventory.delete(item.id) }
    }

    fun showDetails(item: FoodItem) = coordinator.showItem(item.id)

    fun showScan() = coordinator.showScan()

    fun showAddItem() = coordinator.showAddItem()

    fun showEditItem(item: FoodItem) = coordinator.showEditItem(item.id)

    fun loadSampleData() {
        viewModelScope.launch {
            SampleData.items(clock.today(), clock.now(), ids::next).forEach { inventory.add(it) }
        }
    }
}
```

- [ ] **Step 6: Implement the screen** (layout and copy follow `ios/ZeroBite/Screens/Inventory/InventoryView.swift`; the recall banner and reminder tip are Plan 3)

`screens/inventory/InventoryScreen.kt`:

```kotlin
package com.mazesys.zerobite.screens.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazesys.zerobite.core.model.ExpiryStatus
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.ui.components.AppDropdownMenu
import com.mazesys.zerobite.ui.components.AppMenuItem
import com.mazesys.zerobite.ui.components.BottomSearchBar
import com.mazesys.zerobite.ui.components.ChipFilter
import com.mazesys.zerobite.ui.components.EmptyStateView
import com.mazesys.zerobite.ui.components.FoodItemCard
import com.mazesys.zerobite.ui.components.HeaderIconButton
import com.mazesys.zerobite.ui.components.ScreenHeader
import com.mazesys.zerobite.ui.components.SearchAccessory
import com.mazesys.zerobite.ui.components.SectionHeading
import com.mazesys.zerobite.ui.components.UseFirstCard
import com.mazesys.zerobite.ui.components.bleed
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.tint

@Composable
fun InventoryScreen(viewModel: InventoryViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize().background(ZeroBiteTheme.colors.backgroundPrimary)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f).testTag("inventory.grid"),
            contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            fullWidth("header") {
                ScreenHeader(
                    title = "Fridge",
                    overline = state.headerDate,
                    count = if (state.isLoading || state.isEmpty) null else state.activeItems.size,
                ) {
                    AddMenu(onScan = viewModel::showScan, onAddManually = viewModel::showAddItem)
                }
            }
            when {
                state.isLoading -> Unit
                state.isEmpty -> fullWidth("empty") {
                    EmptyStateView(
                        title = "Your fridge is empty",
                        message = "Scan a label or add something by hand. We'll remind you before it goes off and suggest what to cook.",
                        icon = Icons.Rounded.Kitchen,
                        modifier = Modifier.padding(top = 40.dp),
                        primaryTitle = "Scan a label",
                        primaryIcon = Icons.Rounded.QrCodeScanner,
                        onPrimary = viewModel::showScan,
                        secondaryTitle = "Try it with sample food",
                        onSecondary = viewModel::loadSampleData,
                    )
                }
                else -> fridgeContent(state, viewModel)
            }
        }
        if (!state.isLoading && !state.isEmpty) {
            BottomSearchBar(
                text = state.searchText,
                onTextChange = viewModel::setSearchText,
                placeholder = "Search your food",
                accessory = SearchAccessory(Icons.Rounded.QrCodeScanner, "Scan label or barcode", viewModel::showScan),
                testTag = "inventory.search",
            )
        }
    }
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

private fun LazyGridScope.fridgeContent(state: InventoryUiState, viewModel: InventoryViewModel) {
    state.useFirstItem?.let { hero ->
        fullWidth("useFirst") {
            UseFirstCard(
                item = hero,
                today = state.today,
                progress = state.useFirstProgress,
                soonThresholdDays = state.soonThresholdDays,
                othersNeedingAttention = state.othersNeedingAttention,
                onOpen = { viewModel.showDetails(hero) },
                onUsed = { viewModel.markUsed(hero) },
                onFreeze = if (state.canFreeze(hero)) ({ viewModel.freeze(hero) }) else null,
                // Recipes for an item live on its detail screen (Plan 3 adds them there).
                onRecipes = { viewModel.showDetails(hero) },
                modifier = Modifier.testTag("inventory.useFirst").padding(top = Spacing.sm),
            )
        }
    }
    fullWidth("filter") {
        ChipFilter(
            options = StorageFilter.entries,
            selected = state.storageFilter,
            onSelect = viewModel::setStorageFilter,
            label = { it.title },
            modifier = Modifier.bleed(Spacing.gutter).padding(vertical = Spacing.xs),
        )
    }
    if (state.gridSections.isEmpty()) {
        fullWidth("nothing") {
            Text(
                state.emptyGridMessage,
                style = ZeroBiteTheme.type.subheadline,
                color = ZeroBiteTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
            )
        }
    }
    state.gridSections.forEach { section ->
        fullWidth("heading-${section.status}") {
            val count = section.items.size
            SectionHeading(
                title = section.status.sectionTitle,
                detail = if (count == 1) "1 item" else "$count items",
                tint = if (section.status == ExpiryStatus.FRESH) ZeroBiteTheme.colors.textPrimary else section.status.tint(ZeroBiteTheme.colors),
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
        items(section.items, key = { it.id }) { item -> CardWithMenu(item, state, viewModel) }
    }
}

@Composable
private fun CardWithMenu(item: FoodItem, state: InventoryUiState, viewModel: InventoryViewModel) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        FoodItemCard(
            item = item,
            today = state.today,
            soonThresholdDays = state.soonThresholdDays,
            onClick = { viewModel.showDetails(item) },
            onLongClick = { menuOpen = true },
        )
        AppDropdownMenu(expanded = menuOpen, onDismiss = { menuOpen = false }) {
            AppMenuItem("Used it", Icons.Rounded.Check, onClick = { menuOpen = false; viewModel.markUsed(item) })
            if (state.canFreeze(item)) {
                AppMenuItem("Move to freezer", Icons.Rounded.AcUnit, onClick = { menuOpen = false; viewModel.freeze(item) })
            }
            AppMenuItem("Edit", Icons.Rounded.Edit, onClick = { menuOpen = false; viewModel.showEditItem(item) })
            AppMenuItem("Delete", Icons.Rounded.Delete, onClick = { menuOpen = false; viewModel.delete(item) }, destructive = true)
        }
    }
}

@Composable
private fun AddMenu(onScan: () -> Unit, onAddManually: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        HeaderIconButton(Icons.Rounded.Add, "Add food", onClick = { open = true }, modifier = Modifier.testTag("inventory.addButton"))
        AppDropdownMenu(expanded = open, onDismiss = { open = false }) {
            AppMenuItem("Scan label or barcode", Icons.Rounded.QrCodeScanner, onClick = { open = false; onScan() })
            AppMenuItem("Add manually", Icons.Rounded.EditNote, onClick = { open = false; onAddManually() })
        }
    }
}
```

- [ ] **Step 7: Run the tests and record**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests '*Inventory*Test' && ./gradlew :app:recordRoborazziDebug --tests '*InventoryScreenTest'; cd ..
```

Expected: 7 view-model and 7 screen tests PASS; `fridge_light.png`, `fridge_dark.png`, `fridge_font2.png` and `fridge_empty_light.png` recorded. Put `fridge_light.png` next to `appstore/screenshots/6.9in/6.9in-01-waste-less.png`: same hierarchy, same colours, same copy.

- [ ] **Step 8: Commit**

```bash
git add android/app
git commit -m "Add the Android Fridge screen

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: Item detail and the Fridge tab's navigation

**Files:**
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/screens/inventory/{ItemDetailViewModel,ItemDetailScreen,InventoryTab}.kt`
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/screens/inventory/{ItemDetailViewModelTest,ItemDetailScreenTest,InventoryTabTest}.kt`

**Interfaces:**
- Consumes: Tasks 4–10.
- Produces:
  - `enum class DetailState { LOADING, ACTIVE, USED, MISSING }`; `data class ItemDetailUiState(state, item, today, soonThresholdDays, progress, addedOn)` with `status`
  - `class ItemDetailViewModel(itemId, inventory, settings, clock, onEdit: (String) -> Unit, onDeleted: () -> Unit)` with `markUsed`, `restore`, `delete`, `edit`
  - `ItemDetailScreen(viewModel, onBack, modifier)`
  - `InventoryTab(coordinator: InventoryCoordinator, listViewModel: InventoryViewModel, dependencies: AppDependencies, modifier)` is a `NavDisplay` over `coordinator.backStack`

- [ ] **Step 1: Write the failing tests**

`ItemDetailViewModelTest.kt`:

```kotlin
package com.mazesys.zerobite.screens.inventory

import com.mazesys.zerobite.core.model.ExpiryStatus
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.sampleFridge
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ItemDetailViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val inventory = FakeInventoryRepository(sampleFridge())
    private val milk = inventory.current.first { it.name == "Semi-skimmed milk" }
    private var edited: String? = null
    private var deleted = 0

    private fun viewModel(id: String = milk.id) =
        ItemDetailViewModel(id, inventory, FakeSettings(), TestClock.fixed(), onEdit = { edited = it }, onDeleted = { deleted++ })

    @Test
    fun `active item shows its urgency`() {
        val state = viewModel().state.value
        assertEquals(DetailState.ACTIVE, state.state)
        assertEquals(ExpiryStatus.SOON, state.status)
        assertEquals(TestClock.today, state.addedOn)
    }

    @Test
    fun `used up and put back`() {
        val vm = viewModel()
        vm.markUsed()
        assertEquals(DetailState.USED, vm.state.value.state)
        assertEquals(TestClock.now, vm.state.value.item?.consumedAt)
        vm.restore()
        assertEquals(DetailState.ACTIVE, vm.state.value.state)
    }

    @Test
    fun `delete removes the item and leaves the screen`() {
        val vm = viewModel()
        vm.delete()
        assertEquals(DetailState.MISSING, vm.state.value.state)
        assertEquals(1, deleted)
    }

    @Test
    fun `missing item shows removed state`() = runBlocking {
        val vm = viewModel()
        inventory.delete(milk.id) // e.g. deleted from the Fridge grid's menu meanwhile
        assertEquals(DetailState.MISSING, vm.state.value.state)
        assertEquals(DetailState.MISSING, viewModel("no-such-id").state.value.state)
    }

    @Test
    fun `edit asks the coordinator`() {
        viewModel().edit()
        assertEquals(milk.id, edited)
    }
}
```

`ItemDetailScreenTest.kt`:

```kotlin
package com.mazesys.zerobite.screens.inventory

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.sampleFridge
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ItemDetailScreenTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val inventory = FakeInventoryRepository(sampleFridge())
    private val milk = inventory.current.first { it.name == "Semi-skimmed milk" }
    private var deleted = 0

    private fun show(dark: Boolean = false) {
        val vm = ItemDetailViewModel(milk.id, inventory, FakeSettings(), TestClock.fixed(), onEdit = {}, onDeleted = { deleted++ })
        compose.setContent { ZeroBiteTheme(darkTheme = dark) { ItemDetailScreen(vm, onBack = {}) } }
    }

    @Test
    fun `shows status and details`() {
        show()
        compose.onNodeWithText("Semi-skimmed milk").assertIsDisplayed()
        compose.onNodeWithText("Expires in 2 days").assertIsDisplayed()
        compose.onNodeWithText("9 September 2026").assertExists()
        compose.onNodeWithText("2 pints").assertExists()
    }

    @Test
    fun `mark as used up then put it back`() {
        show()
        compose.onNodeWithText("Mark as used up").performClick()
        compose.onNodeWithText("Used up").assertIsDisplayed()
        compose.onNodeWithText("Put it back in the fridge").performClick()
        compose.onNodeWithText("Mark as used up").assertExists()
    }

    @Test
    fun `delete asks for confirmation`() {
        show()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete this item?").assertIsDisplayed()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(1, deleted)
    }

    @Test
    fun `item removed elsewhere shows the removed state`() = runBlocking {
        show()
        inventory.delete(milk.id)
        compose.onNodeWithText("Item removed").assertIsDisplayed()
    }

    @Test
    fun `detail light`() {
        show()
        compose.onRoot().captureRoboImage("src/test/screenshots/detail_light.png")
    }

    @Test
    fun `detail dark`() {
        show(dark = true)
        compose.onRoot().captureRoboImage("src/test/screenshots/detail_dark.png")
    }
}
```

`InventoryTabTest.kt`:

```kotlin
package com.mazesys.zerobite.screens.inventory

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.sampleFridge
import com.mazesys.zerobite.testing.testDependencies
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class InventoryTabTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `opens detail from the grid and comes back`() {
        val dependencies = testDependencies(inventory = FakeInventoryRepository(sampleFridge()))
        val app = AppCoordinator { TestClock.today }
        val list = InventoryViewModel(dependencies.inventory, dependencies.settings, dependencies.clock, dependencies.ids, app.inventory)
        compose.setContent { ZeroBiteTheme { InventoryTab(app.inventory, list, dependencies) } }

        compose.onNodeWithText("Spinach").performClick()
        compose.onNodeWithText("Details").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Fridge").assertIsDisplayed()
        assertEquals(1, app.inventory.backStack.size)
    }
}
```

- [ ] **Step 2: Run to see them fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests '*ItemDetail*Test' --tests '*InventoryTabTest'; cd ..`
Expected: compilation FAILS: unresolved `ItemDetailViewModel`, `ItemDetailScreen`, `InventoryTab`.

- [ ] **Step 3: Implement the view model**

`screens/inventory/ItemDetailViewModel.kt`:

```kotlin
package com.mazesys.zerobite.screens.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazesys.zerobite.core.inventory.InventoryRepository
import com.mazesys.zerobite.core.model.ExpiryStatus
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.time.AppClock
import com.mazesys.zerobite.data.SettingsRepository
import com.mazesys.zerobite.ui.components.shelfLifeProgress
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime

enum class DetailState { LOADING, ACTIVE, USED, MISSING }

data class ItemDetailUiState(
    val today: LocalDate,
    val state: DetailState = DetailState.LOADING,
    val item: FoodItem? = null,
    val soonThresholdDays: Int = 3,
    val progress: Float = 0f,
    val addedOn: LocalDate? = null,
    val consumedOn: LocalDate? = null,
) {
    val status: ExpiryStatus? get() = item?.status(today, soonThresholdDays)
}

class ItemDetailViewModel(
    val itemId: String,
    private val inventory: InventoryRepository,
    settings: SettingsRepository,
    private val clock: AppClock,
    private val onEdit: (String) -> Unit,
    private val onDeleted: () -> Unit,
) : ViewModel() {

    val state: StateFlow<ItemDetailUiState> =
        combine(inventory.items, settings.state) { items, s ->
            val item = items.firstOrNull { it.id == itemId }
            ItemDetailUiState(
                today = clock.today(),
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
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ItemDetailUiState(today = clock.today()))

    fun markUsed() {
        viewModelScope.launch { inventory.markConsumed(listOf(itemId), clock.now()) }
    }

    fun restore() {
        viewModelScope.launch { inventory.restore(itemId) }
    }

    fun delete() {
        viewModelScope.launch {
            inventory.delete(itemId)
            onDeleted()
        }
    }

    fun edit() = onEdit(itemId)
}
```

- [ ] **Step 4: Implement the screen** (follows `ItemDetailView.swift`; the recall cards and the "Recipes using…" section are Plan 3)

`screens/inventory/ItemDetailScreen.kt`:

```kotlin
package com.mazesys.zerobite.screens.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Scale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.ui.components.AppButton
import com.mazesys.zerobite.ui.components.AppCard
import com.mazesys.zerobite.ui.components.AppDropdownMenu
import com.mazesys.zerobite.ui.components.AppMenuItem
import com.mazesys.zerobite.ui.components.ButtonKind
import com.mazesys.zerobite.ui.components.CardSection
import com.mazesys.zerobite.ui.components.DetailTitle
import com.mazesys.zerobite.ui.components.EmptyStateView
import com.mazesys.zerobite.ui.components.HeaderIconButton
import com.mazesys.zerobite.ui.components.IconTile
import com.mazesys.zerobite.ui.components.InfoRow
import com.mazesys.zerobite.ui.components.ShelfLifeBar
import com.mazesys.zerobite.ui.components.TopBar
import com.mazesys.zerobite.ui.components.TopBarLeading
import com.mazesys.zerobite.ui.format.DateText
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.icon
import com.mazesys.zerobite.ui.theme.semibold
import com.mazesys.zerobite.ui.theme.tint

@Composable
fun ItemDetailScreen(viewModel: ItemDetailViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val colors = ZeroBiteTheme.colors

    Column(modifier.fillMaxSize().background(colors.backgroundPrimary)) {
        TopBar(TopBarLeading.Back(onBack)) {
            if (ui.state == DetailState.ACTIVE || ui.state == DetailState.USED) {
                Box {
                    HeaderIconButton(Icons.Rounded.MoreHoriz, "More", onClick = { menuOpen = true })
                    AppDropdownMenu(expanded = menuOpen, onDismiss = { menuOpen = false }) {
                        AppMenuItem("Edit", Icons.Rounded.Edit, onClick = { menuOpen = false; viewModel.edit() })
                        AppMenuItem("Delete", Icons.Rounded.Delete, onClick = { menuOpen = false; confirmDelete = true }, destructive = true)
                    }
                }
            }
        }
        val item = ui.item
        when {
            ui.state == DetailState.LOADING -> Unit
            item == null -> EmptyStateView(
                title = "Item removed",
                message = "This item is no longer in your fridge.",
                icon = Icons.Rounded.Delete,
                modifier = Modifier.padding(Spacing.gutter),
            )
            else -> DetailContent(item, ui, viewModel)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this item?", style = ZeroBiteTheme.type.headline) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) {
                    Text("Delete", color = colors.statusExpired, style = ZeroBiteTheme.type.subheadline.semibold())
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel", color = colors.textPrimary, style = ZeroBiteTheme.type.subheadline)
                }
            },
            containerColor = colors.backgroundPrimary,
            shape = RoundedCornerShape(Radius.card),
        )
    }
}

@Composable
private fun DetailContent(item: FoodItem, ui: ItemDetailUiState, viewModel: ItemDetailViewModel) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        DetailTitle(title = item.name, overline = item.category.label)
        StatusCard(item, ui)
        CardSection(
            title = "Details",
            rows = buildList<@Composable () -> Unit> {
                add { InfoRow("Stored in", item.storage.label, icon = item.storage.icon) }
                add { InfoRow("Quantity", item.quantityText, icon = Icons.Rounded.Scale) }
                add { InfoRow("Expires", DateText.long(item.expiresOn), icon = Icons.Rounded.Event) }
                ui.addedOn?.let { added -> add { InfoRow("Added", DateText.abbreviated(added), icon = Icons.Rounded.AddCircle) } }
                item.barcode?.takeIf { it.isNotEmpty() }?.let { code -> add { InfoRow("Barcode", code, icon = Icons.Rounded.QrCode) } }
            },
        )
        if (item.notes.isNotEmpty()) {
            CardSection(title = "Notes", rows = listOf<@Composable () -> Unit>({
                Text(item.notes, style = ZeroBiteTheme.type.subheadline, color = ZeroBiteTheme.colors.textPrimary)
            }))
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (ui.state == DetailState.USED) {
                AppButton("Put it back in the fridge", viewModel::restore, kind = ButtonKind.TONAL, icon = Icons.AutoMirrored.Rounded.Undo)
            } else {
                AppButton("Mark as used up", viewModel::markUsed, icon = Icons.Rounded.Check)
                AppButton("Edit", viewModel::edit, kind = ButtonKind.TONAL, icon = Icons.Rounded.Edit)
            }
        }
    }
}

@Composable
private fun StatusCard(item: FoodItem, ui: ItemDetailUiState) {
    val colors = ZeroBiteTheme.colors
    val type = ZeroBiteTheme.type
    val status = ui.status ?: return
    val tint = status.tint(colors)
    val used = ui.state == DetailState.USED
    AppCard(accent = if (!used && status.needsAttention) tint else null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            IconTile(item.category.icon, tint = if (used) colors.textSecondary else tint, size = 44.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (used) {
                    Text("Used up", style = type.headline, color = colors.textPrimary)
                    ui.consumedOn?.let { day ->
                        Text(DateText.abbreviated(day), style = type.caption, color = colors.textSecondary)
                    }
                } else {
                    val description = item.expiryDescription(ui.today)
                    Text(description, style = type.headline, color = tint)
                    // "Expires today" would just repeat the headline, so show what's left instead.
                    Text(
                        if (description == status.sectionTitle) "${item.quantityText} · ${item.storage.label}" else status.sectionTitle,
                        style = type.caption,
                        color = colors.textSecondary,
                    )
                }
            }
        }
        if (!used) ShelfLifeBar(ui.progress, tint, Modifier.padding(top = Spacing.md))
    }
}
```

- [ ] **Step 5: Implement the Fridge tab's navigation**

`screens/inventory/InventoryTab.kt`:

```kotlin
package com.mazesys.zerobite.screens.inventory

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.mazesys.zerobite.AppDependencies
import com.mazesys.zerobite.coordination.InventoryCoordinator
import com.mazesys.zerobite.coordination.InventoryRoute

/**
 * The Fridge tab: list → item detail. The list's view model is owned by the caller so it
 * survives switching tabs; each detail entry gets its own view model, cleared when popped.
 */
@Composable
fun InventoryTab(
    coordinator: InventoryCoordinator,
    listViewModel: InventoryViewModel,
    dependencies: AppDependencies,
    modifier: Modifier = Modifier,
) {
    NavDisplay(
        backStack = coordinator.backStack,
        modifier = modifier,
        onBack = { coordinator.pop() },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<InventoryRoute.Fridge> { InventoryScreen(listViewModel) }
            entry<InventoryRoute.ItemDetail> { route ->
                val detail = viewModel(key = route.itemId) {
                    ItemDetailViewModel(
                        itemId = route.itemId,
                        inventory = dependencies.inventory,
                        settings = dependencies.settings,
                        clock = dependencies.clock,
                        onEdit = coordinator::showEditItem,
                        onDeleted = coordinator::pop,
                    )
                }
                ItemDetailScreen(detail, onBack = coordinator::pop)
            }
        },
    )
}
```

- [ ] **Step 6: Run the tests and record**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests '*ItemDetail*Test' --tests '*InventoryTabTest' && ./gradlew :app:recordRoborazziDebug --tests '*ItemDetailScreenTest'; cd ..
```

Expected: 5 + 6 + 1 tests PASS; `detail_light.png` and `detail_dark.png` recorded.

- [ ] **Step 7: Commit**

```bash
git add android/app
git commit -m "Add the Android item detail screen and Fridge navigation

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 12: Add and edit item form

**Files:**
- Modify: `android/core/src/main/kotlin/com/mazesys/zerobite/core/model/FoodItem.kt` (make `formatQuantity` public)
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/screens/inventory/{ItemFormViewModel,ItemFormScreen}.kt`
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/screens/inventory/{ItemFormViewModelTest,ItemFormScreenTest}.kt`

**Interfaces:**
- Consumes: `ItemDraft`, `formatQuantity` (`:core`); Tasks 4–8.
- Produces:
  - `sealed interface ItemFormMode { Add; Edit(itemId) }`
  - `data class ItemFormUiState(draft, quantityText, isEditing, isLoaded, errorMessage, isSaving)` with `title`, `overline`, `saveTitle`, `canSave`, `defaultShelfLifeDays`
  - `class ItemFormViewModel(mode, initialDraft: ItemDraft?, inventory, clock, ids, onSaved: (FoodItem) -> Unit)` with `updateDraft`, `setQuantityText`, `applyDefaultShelfLife`, `save`
  - `ItemFormScreen(viewModel, onCancel, modifier)`

- [ ] **Step 1: Make `formatQuantity` public in `:core`**

In `android/core/src/main/kotlin/com/mazesys/zerobite/core/model/FoodItem.kt`, change `internal fun formatQuantity(quantity: Double): String` to `fun formatQuantity(quantity: Double): String`. The form shows the same number the card shows.

Run: `cd android && ./gradlew :core:check; cd ..` → `BUILD SUCCESSFUL`.

- [ ] **Step 2: Write the failing tests**

`ItemFormViewModelTest.kt`:

```kotlin
package com.mazesys.zerobite.screens.inventory

import com.mazesys.zerobite.core.model.FoodCategory
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.ItemDraft
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.SequentialIds
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.sampleFridge
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ItemFormViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val inventory = FakeInventoryRepository(sampleFridge())
    private val saved = mutableListOf<FoodItem>()

    private fun add() = ItemFormViewModel(ItemFormMode.Add, ItemDraft.new(TestClock.today), inventory, TestClock.fixed(), SequentialIds("new"), saved::add)
    private fun edit(id: String) = ItemFormViewModel(ItemFormMode.Edit(id), null, inventory, TestClock.fixed(), SequentialIds("new"), saved::add)

    @Test
    fun `a new item needs a name`() {
        val vm = add()
        assertFalse(vm.state.value.canSave)
        assertEquals("New item", vm.state.value.title)
        vm.updateDraft { it.copy(name = "  ") }
        assertFalse(vm.state.value.canSave)
        vm.updateDraft { it.copy(name = "Oat milk") }
        assertTrue(vm.state.value.canSave)
    }

    @Test
    fun `adding saves a trimmed item`() {
        val vm = add()
        vm.updateDraft { it.copy(name = "  Oat milk ", category = FoodCategory.DRINKS) }
        vm.save()
        val item = saved.single()
        assertEquals("new-1", item.id)
        assertEquals("Oat milk", item.name)
        assertEquals(TestClock.now, item.addedAt)
        assertEquals(LocalDate(2026, 9, 14), item.expiresOn)
        assertEquals(item, inventory.current.first { it.id == "new-1" })
    }

    @Test
    fun `editing loads the item and keeps its identity`() {
        val milk = inventory.current.first { it.name == "Semi-skimmed milk" }
        val vm = edit(milk.id)
        assertTrue(vm.state.value.isLoaded)
        assertEquals("Edit item", vm.state.value.title)
        assertEquals("Semi-skimmed milk", vm.state.value.draft.name)
        assertEquals("2", vm.state.value.quantityText)
        vm.updateDraft { it.copy(name = "Whole milk") }
        vm.save()
        val updated = saved.single()
        assertEquals(milk.id, updated.id)
        assertEquals(milk.addedAt, updated.addedAt)
        assertEquals("Whole milk", inventory.current.first { it.id == milk.id }.name)
    }

    @Test
    fun `editing an item that no longer exists explains why`() = runBlocking {
        assertEquals("This item no longer exists.", edit("gone").state.value.errorMessage)

        val milk = inventory.current.first { it.name == "Semi-skimmed milk" }
        val vm = edit(milk.id)
        inventory.delete(milk.id)
        vm.save()
        assertEquals("This item no longer exists.", vm.state.value.errorMessage)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `typical shelf life comes from the category`() {
        val vm = add()
        vm.updateDraft { it.copy(category = FoodCategory.FRUIT) }
        vm.applyDefaultShelfLife()
        assertEquals(LocalDate(2026, 9, 13), vm.state.value.draft.expiresOn)
        assertEquals(6, vm.state.value.defaultShelfLifeDays)
    }

    @Test
    fun `quantity text is parsed leniently and validated`() {
        val vm = add()
        vm.updateDraft { it.copy(name = "Rice") }
        vm.setQuantityText("abc")
        assertFalse(vm.state.value.canSave)
        vm.setQuantityText("0")
        assertFalse(vm.state.value.canSave)
        vm.setQuantityText("1,5")
        assertEquals(1.5, vm.state.value.draft.quantity)
        assertTrue(vm.state.value.canSave)
        assertEquals("1,5", vm.state.value.quantityText, "keeps what was typed")
        assertNull(vm.state.value.errorMessage)
    }
}
```

`ItemFormScreenTest.kt`:

```kotlin
package com.mazesys.zerobite.screens.inventory

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureRoboImage
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.ItemDraft
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.SequentialIds
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ItemFormScreenTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val saved = mutableListOf<FoodItem>()

    private fun show(dark: Boolean = false) {
        val vm = ItemFormViewModel(ItemFormMode.Add, ItemDraft.new(TestClock.today), FakeInventoryRepository(), TestClock.fixed(), SequentialIds(), saved::add)
        compose.setContent { ZeroBiteTheme(darkTheme = dark) { ItemFormScreen(vm, onCancel = {}) } }
    }

    @Test
    fun `fill in a name and add it`() {
        show()
        compose.onNodeWithTag("itemForm.save").assertIsNotEnabled()
        compose.onNodeWithTag("itemForm.name").performTextInput("Oat milk")
        compose.onNodeWithTag("itemForm.save").assertIsEnabled().performClick()
        assertEquals("Oat milk", saved.single().name)
    }

    @Test
    fun `typical shelf life row follows the category`() {
        show()
        compose.onNodeWithText("Meat").performClick() // second chip, always on screen
        compose.onNodeWithText("Use typical shelf life (3 days)").performClick()
        compose.onNodeWithText("10 Sep 2026").assertExists()
    }

    @Test
    fun `form light`() {
        show()
        compose.onRoot().captureRoboImage("src/test/screenshots/form_light.png")
    }

    @Test
    fun `form dark`() {
        show(dark = true)
        compose.onRoot().captureRoboImage("src/test/screenshots/form_dark.png")
    }
}
```

- [ ] **Step 3: Run to see them fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests '*ItemForm*Test'; cd ..`
Expected: compilation FAILS: unresolved `ItemFormViewModel`, `ItemFormMode`, `ItemFormScreen`.

- [ ] **Step 4: Implement the view model**

`screens/inventory/ItemFormViewModel.kt`:

```kotlin
package com.mazesys.zerobite.screens.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazesys.zerobite.IdGenerator
import com.mazesys.zerobite.core.inventory.InventoryRepository
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.ItemDraft
import com.mazesys.zerobite.core.model.formatQuantity
import com.mazesys.zerobite.core.time.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus

sealed interface ItemFormMode {
    data object Add : ItemFormMode
    data class Edit(val itemId: String) : ItemFormMode
}

data class ItemFormUiState(
    val draft: ItemDraft,
    /** What the user typed in the amount field; `draft.quantity` is its parsed value. */
    val quantityText: String,
    val isEditing: Boolean,
    val isLoaded: Boolean = true,
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
) {
    val title: String get() = if (isEditing) "Edit item" else "New item"
    val overline: String get() = if (isEditing) "Edit" else "Add to your fridge"
    val saveTitle: String get() = if (isEditing) "Save changes" else "Add to fridge"
    val canSave: Boolean get() = isLoaded && draft.isValid && !isSaving
    val defaultShelfLifeDays: Int get() = draft.category.defaultShelfLifeDays
}

class ItemFormViewModel(
    private val mode: ItemFormMode,
    initialDraft: ItemDraft?,
    private val inventory: InventoryRepository,
    private val clock: AppClock,
    private val ids: IdGenerator,
    private val onSaved: (FoodItem) -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(
        (initialDraft ?: ItemDraft.new(clock.today())).let { draft ->
            ItemFormUiState(
                draft = draft,
                quantityText = formatQuantity(draft.quantity),
                isEditing = mode is ItemFormMode.Edit,
                isLoaded = initialDraft != null || mode is ItemFormMode.Add,
            )
        },
    )
    val state: StateFlow<ItemFormUiState> = _state.asStateFlow()

    init {
        if (mode is ItemFormMode.Edit && initialDraft == null) {
            viewModelScope.launch {
                val existing = inventory.item(mode.itemId)
                _state.update {
                    if (existing == null) {
                        it.copy(isLoaded = true, errorMessage = MISSING)
                    } else {
                        val draft = ItemDraft.from(existing)
                        it.copy(draft = draft, quantityText = formatQuantity(draft.quantity), isLoaded = true)
                    }
                }
            }
        }
    }

    fun updateDraft(transform: (ItemDraft) -> ItemDraft) {
        _state.update { it.copy(draft = transform(it.draft)) }
    }

    /** Accepts a comma as the decimal separator; anything unparseable counts as zero (not saveable). */
    fun setQuantityText(text: String) {
        val parsed = text.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
        _state.update { it.copy(quantityText = text, draft = it.draft.copy(quantity = parsed)) }
    }

    /** Sets the expiry from the category's typical shelf life (for labels with no readable date). */
    fun applyDefaultShelfLife() {
        updateDraft { it.copy(expiresOn = clock.today().plus(it.category.defaultShelfLifeDays, DateTimeUnit.DAY)) }
    }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (mode) {
                ItemFormMode.Add -> {
                    val item = current.draft.makeItem(ids.next(), clock.now())
                    inventory.add(item)
                    onSaved(item)
                }
                is ItemFormMode.Edit -> {
                    val existing = inventory.item(mode.itemId)
                    if (existing == null) {
                        _state.update { it.copy(isSaving = false, errorMessage = MISSING) }
                        return@launch
                    }
                    val updated = current.draft.applyTo(existing)
                    inventory.update(updated)
                    onSaved(updated)
                }
            }
        }
    }

    private companion object {
        const val MISSING = "This item no longer exists."
    }
}
```

- [ ] **Step 5: Implement the screen** (follows `ItemFormView.swift`)

`screens/inventory/ItemFormScreen.kt`:

```kotlin
package com.mazesys.zerobite.screens.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazesys.zerobite.core.model.FoodCategory
import com.mazesys.zerobite.core.model.ItemDraft
import com.mazesys.zerobite.core.model.StorageLocation
import com.mazesys.zerobite.ui.components.ActionRow
import com.mazesys.zerobite.ui.components.AppButton
import com.mazesys.zerobite.ui.components.AppTextField
import com.mazesys.zerobite.ui.components.CardSection
import com.mazesys.zerobite.ui.components.ChipFilter
import com.mazesys.zerobite.ui.components.DetailTitle
import com.mazesys.zerobite.ui.components.InfoRow
import com.mazesys.zerobite.ui.components.LabeledInput
import com.mazesys.zerobite.ui.components.Overline
import com.mazesys.zerobite.ui.components.TopBar
import com.mazesys.zerobite.ui.components.TopBarLeading
import com.mazesys.zerobite.ui.components.bleed
import com.mazesys.zerobite.ui.format.DateText
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.medium
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@Composable
fun ItemFormScreen(viewModel: ItemFormViewModel, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val colors = ZeroBiteTheme.colors
    var pickingDate by remember { mutableStateOf(false) }
    val draft = ui.draft

    Column(modifier.fillMaxSize().background(colors.backgroundPrimary)) {
        TopBar(TopBarLeading.Close(onCancel))
        if (!ui.isLoaded) return@Column
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            DetailTitle(title = ui.title, overline = ui.overline)
            LabeledInput(
                label = "Name",
                value = draft.name,
                onValueChange = { name -> viewModel.updateDraft { it.copy(name = name) } },
                placeholder = "Greek yogurt",
                testTag = "itemForm.name",
                autoFocus = draft.name.isEmpty(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            LabeledInput(
                label = "Brand (optional, helps spot recalls)",
                value = draft.brand.orEmpty(),
                onValueChange = { brand -> viewModel.updateDraft { it.copy(brand = brand.takeIf { b -> b.isNotBlank() }) } },
                placeholder = "Yeo Valley",
                testTag = "itemForm.brand",
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Overline("Category")
                ChipFilter(FoodCategory.entries, draft.category, { c -> viewModel.updateDraft { it.copy(category = c) } }, { it.label }, Modifier.bleed(Spacing.gutter))
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Overline("Stored in")
                ChipFilter(StorageLocation.entries, draft.storage, { s -> viewModel.updateDraft { it.copy(storage = s) } }, { it.label }, Modifier.bleed(Spacing.gutter))
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Overline("Quantity")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    AppTextField(
                        ui.quantityText, viewModel::setQuantityText, "Amount",
                        Modifier.weight(1f).testTag("itemForm.quantity"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    AppTextField(
                        draft.unit, { unit -> viewModel.updateDraft { it.copy(unit = unit) } }, "Unit",
                        Modifier.width(110.dp).testTag("itemForm.unit"),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    )
                }
                ChipFilter(ItemDraft.unitSuggestions, draft.unit, { u -> viewModel.updateDraft { it.copy(unit = u) } }, { it }, Modifier.bleed(Spacing.gutter))
            }
            CardSection(
                title = "Expiry",
                footer = "No date on the label? Use the typical shelf life for ${draft.category.label.lowercase()}.",
                rows = listOf<@Composable () -> Unit>(
                    {
                        InfoRow("Expires on", icon = Icons.Rounded.Event) {
                            Text(
                                DateText.abbreviated(draft.expiresOn),
                                style = ZeroBiteTheme.type.subheadline.medium(),
                                color = colors.textPrimary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(Radius.control))
                                    .background(colors.chipFill)
                                    .clickable { pickingDate = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    },
                    {
                        ActionRow(
                            "Use typical shelf life (${ui.defaultShelfLifeDays} days)",
                            onClick = viewModel::applyDefaultShelfLife,
                            icon = Icons.Rounded.AutoAwesome,
                            tint = colors.brandOrange,
                        )
                    },
                ),
            )
            LabeledInput(
                label = "Notes",
                value = draft.notes,
                onValueChange = { notes -> viewModel.updateDraft { it.copy(notes = notes) } },
                placeholder = "Opened on Monday, half left",
                singleLine = false,
            )
            draft.barcode?.let { code -> CardSection(rows = listOf<@Composable () -> Unit>({ InfoRow("Barcode", code, icon = Icons.Rounded.QrCode) })) }
            ui.errorMessage?.let { Text(it, style = ZeroBiteTheme.type.subheadline.medium(), color = colors.statusExpired) }
        }
        AppButton(
            text = ui.saveTitle,
            onClick = viewModel::save,
            enabled = ui.canSave,
            loading = ui.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.backgroundPrimary)
                .padding(horizontal = Spacing.gutter, vertical = Spacing.sm)
                .testTag("itemForm.save"),
        )
    }

    if (pickingDate) {
        ExpiryDatePicker(
            initial = draft.expiresOn,
            onPicked = { date -> pickingDate = false; viewModel.updateDraft { it.copy(expiresOn = date) } },
            onDismiss = { pickingDate = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpiryDatePicker(initial: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val colors = ZeroBiteTheme.colors
    // The Material picker works in UTC midnight millis.
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds())
    val pickerColors = DatePickerDefaults.colors(
        containerColor = colors.backgroundPrimary,
        selectedDayContainerColor = colors.textPrimary,
        selectedDayContentColor = colors.onInk,
        todayDateBorderColor = colors.brandOrange,
        todayContentColor = colors.brandOrange,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    onPicked(Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date)
                } ?: onDismiss()
            }) { Text("Done", color = colors.textPrimary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textSecondary) } },
        colors = pickerColors,
    ) {
        DatePicker(state, colors = pickerColors, showModeToggle = false)
    }
}
```

- [ ] **Step 6: Run the tests and record**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests '*ItemForm*Test' && ./gradlew :app:recordRoborazziDebug --tests '*ItemFormScreenTest'; cd ..
```

Expected: 6 + 4 tests PASS; `form_light.png` and `form_dark.png` recorded.

- [ ] **Step 7: Commit**

```bash
git add android/core android/app
git commit -m "Add the Android add and edit item form

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 13: App shell: launch, welcome placeholder, tabs, sheets, back

**Files:**
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/coordination/SheetViewModelStores.kt`
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/AppViewModel.kt`
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/screens/root/{RootScreen,LaunchScreen,WelcomePlaceholder,MainScreen,SheetHost,PlaceholderTab,ScanPlaceholder,AppAlert}.kt`
- Modify: `android/app/src/main/kotlin/com/mazesys/zerobite/MainActivity.kt`
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/coordination/SheetViewModelStoresTest.kt`, `android/app/src/test/kotlin/com/mazesys/zerobite/screens/root/RootScreenTest.kt`

**Interfaces:**
- Consumes: everything above.
- Produces:
  - `class SheetViewModelStores { fun owner(sheetId: Long): ViewModelStoreOwner; fun retainOnly(sheetId: Long?); fun clearAll() }`
  - `class AppViewModel(dependencies) : ViewModel` holding `flow`, `coordinator`, `sheetStores`
  - `RootScreen(flow, coordinator, sheetStores, dependencies)`, `MainScreen(coordinator, sheetStores, dependencies)`, `SheetHost(coordinator, sheetStores, dependencies)`
  - Placeholders that Plan 3 replaces: `WelcomePlaceholder(onDone)`, `PlaceholderTab(overline, title)`, `ScanPlaceholder(onClose, onAddManually)`

- [ ] **Step 1: Write the failing tests**

`coordination/SheetViewModelStoresTest.kt`:

```kotlin
package com.mazesys.zerobite.coordination

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SheetViewModelStoresTest {

    class Probe : ViewModel() {
        var cleared = false
        override fun onCleared() {
            cleared = true
        }
    }

    private val factory = viewModelFactory { initializer { Probe() } }
    private fun SheetViewModelStores.probe(id: Long) = ViewModelProvider(owner(id), factory)[Probe::class.java]

    @Test
    fun `a sheet keeps its view model across recomposition and configuration changes`() {
        val stores = SheetViewModelStores()
        val first = stores.probe(1)
        stores.retainOnly(1) // MainScreen re-runs this after an activity is recreated
        assertSame(first, stores.probe(1))
        assertFalse(first.cleared)
    }

    @Test
    fun `dismissing or replacing a sheet frees its view model`() {
        val stores = SheetViewModelStores()
        val add = stores.probe(1)
        stores.retainOnly(2) // a new sheet replaced it
        assertTrue(add.cleared)
        val edit = stores.probe(2)
        assertNotSame<ViewModel>(add, edit)
        stores.retainOnly(null) // dismissed
        assertTrue(edit.cleared)
    }

    @Test
    fun `clearing everything frees every sheet`() {
        val stores = SheetViewModelStores()
        val probe = stores.probe(7)
        stores.clearAll()
        assertTrue(probe.cleared)
    }
}
```

`screens/root/RootScreenTest.kt`:

```kotlin
package com.mazesys.zerobite.screens.root

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.coordination.AppFlowController
import com.mazesys.zerobite.coordination.SheetViewModelStores
import com.mazesys.zerobite.data.SettingsSnapshot
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.sampleFridge
import com.mazesys.zerobite.testing.testDependencies
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RootScreenTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = AppCoordinator { TestClock.today }

    private fun show(settings: FakeSettings, inventory: FakeInventoryRepository = FakeInventoryRepository(sampleFridge())): AppFlowController {
        val dependencies = testDependencies(inventory = inventory, settings = settings)
        val flow = AppFlowController(settings)
        compose.setContent { ZeroBiteTheme { RootScreen(flow, app, SheetViewModelStores(), dependencies) } }
        return flow
    }

    private fun waitFor(text: String) = compose.waitUntil(3_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    private fun waitForTag(tag: String) = compose.waitUntil(3_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    private fun pressBack() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun `first run goes splash, welcome, fridge`() {
        val settings = FakeSettings()
        show(settings)
        compose.onNodeWithText("Zero Bite").assertExists()
        waitFor("Get started")
        compose.onNodeWithText("Get started").performClick()
        waitForTag("tab.fridge")
        compose.onNodeWithTag("tab.fridge").assertIsSelected()
        assertTrue(runBlocking { settings.current().hasCompletedWelcome })
    }

    @Test
    fun `returning user lands on the fridge`() {
        show(FakeSettings(SettingsSnapshot(hasCompletedWelcome = true)))
        waitForTag("tab.fridge")
        compose.onNodeWithText("Greek yogurt").assertIsDisplayed()
    }

    @Test
    fun `tabs switch between screens`() {
        show(FakeSettings(SettingsSnapshot(hasCompletedWelcome = true)))
        waitForTag("tab.recipes")
        compose.onNodeWithTag("tab.recipes").performClick()
        compose.onNodeWithText("Arrives in the next build.").assertIsDisplayed()
        compose.onNodeWithTag("tab.fridge").performClick()
        compose.onNodeWithText("Greek yogurt").assertIsDisplayed()
    }

    @Test
    fun `adding by hand puts the item in the fridge`() {
        show(FakeSettings(SettingsSnapshot(hasCompletedWelcome = true)), FakeInventoryRepository())
        waitFor("Try it with sample food")
        compose.runOnUiThread { app.inventory.showAddItem() }
        waitForTag("itemForm.name")
        compose.onNodeWithTag("itemForm.name").performTextInput("Oat milk")
        compose.onNodeWithTag("itemForm.save").performClick()
        waitFor("Oat milk")
        assertNull(app.presentedSheet)
    }

    @Test
    fun `back closes the sheet, then pops detail`() {
        show(FakeSettings(SettingsSnapshot(hasCompletedWelcome = true)))
        waitFor("Spinach")
        compose.onNodeWithText("Spinach").performClick()
        waitFor("Details")

        compose.runOnUiThread { app.inventory.showScan() }
        waitFor("Scan a label")
        pressBack()
        compose.waitUntil(3_000) { app.presentedSheet == null }
        compose.onNodeWithText("Details").assertExists()

        pressBack()
        compose.waitUntil(3_000) { app.inventory.backStack.size == 1 }

        pressBack()
        assertTrue(compose.activity.isFinishing, "back at the root leaves the app")
        assertEquals(1, app.inventory.backStack.size)
    }
}
```

- [ ] **Step 2: Run to see them fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests '*SheetViewModelStoresTest' --tests '*RootScreenTest'; cd ..`
Expected: compilation FAILS: unresolved `SheetViewModelStores`, `RootScreen`.

- [ ] **Step 3: Implement the sheet stores and app view model**

`coordination/SheetViewModelStores.kt`:

```kotlin
package com.mazesys.zerobite.coordination

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * One ViewModelStore per presented sheet. It is kept across configuration changes (the owner
 * lives in AppViewModel) and cleared as soon as that sheet is dismissed or replaced, so the
 * add/edit form keeps a half-typed draft through rotation but starts fresh every time.
 */
class SheetViewModelStores {
    private val stores = mutableMapOf<Long, ViewModelStore>()

    fun owner(sheetId: Long): ViewModelStoreOwner {
        val store = stores.getOrPut(sheetId) { ViewModelStore() }
        return object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        }
    }

    /** Frees every sheet's view models except the one still presented (`null`: none is). */
    fun retainOnly(sheetId: Long?) {
        stores.keys.filter { it != sheetId }.forEach { stores.remove(it)?.clear() }
    }

    fun clearAll() {
        stores.values.forEach(ViewModelStore::clear)
        stores.clear()
    }
}
```

`AppViewModel.kt`:

```kotlin
package com.mazesys.zerobite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.coordination.AppFlowController
import com.mazesys.zerobite.coordination.SheetViewModelStores

/** Keeps app-level flow and navigation state alive across configuration changes. */
class AppViewModel(dependencies: AppDependencies) : ViewModel() {
    val flow = AppFlowController(dependencies.settings)
    val coordinator = AppCoordinator(today = dependencies.clock::today)
    val sheetStores = SheetViewModelStores()

    override fun onCleared() = sheetStores.clearAll()

    companion object {
        fun factory(dependencies: AppDependencies): ViewModelProvider.Factory =
            viewModelFactory { initializer { AppViewModel(dependencies) } }
    }
}
```

- [ ] **Step 4: Implement the root screens**

`screens/root/LaunchScreen.kt`:

```kotlin
package com.mazesys.zerobite.screens.root

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazesys.zerobite.ui.brand.BrandMark
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.semibold
import kotlinx.coroutines.delay

/** The mark and name for half a second after the system splash, as on iOS. */
@Composable
fun LaunchScreen(onFinish: () -> Unit) {
    Box(Modifier.fillMaxSize().background(ZeroBiteTheme.colors.backgroundPrimary), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            BrandMark(Modifier.size(112.dp))
            Text("Zero Bite", style = ZeroBiteTheme.type.largeTitle.semibold(), letterSpacing = (-1).sp, color = ZeroBiteTheme.colors.textPrimary)
        }
    }
    LaunchedEffect(Unit) {
        delay(500)
        onFinish()
    }
}
```

`screens/root/WelcomePlaceholder.kt`:

```kotlin
package com.mazesys.zerobite.screens.root

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mazesys.zerobite.ui.brand.BrandMark
import com.mazesys.zerobite.ui.components.AppButton
import com.mazesys.zerobite.ui.components.DetailTitle
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme

/** Placeholder until Plan 3 ports the iOS onboarding (intro pages + reminder opt-in). */
@Composable
fun WelcomePlaceholder(onDone: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(ZeroBiteTheme.colors.backgroundPrimary)
            .systemBarsPadding()
            .padding(horizontal = Spacing.gutter, vertical = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Spacer(Modifier.weight(1f))
        BrandMark(Modifier.size(72.dp))
        DetailTitle(title = "Zero Bite", overline = "Welcome")
        Text(
            "Keep track of what's in your fridge, get a nudge before food goes off, and cook with what you already have.",
            style = ZeroBiteTheme.type.body,
            color = ZeroBiteTheme.colors.textSecondary,
        )
        Spacer(Modifier.weight(1f))
        AppButton("Get started", onDone, Modifier.testTag("welcome.start"))
    }
}
```

`screens/root/PlaceholderTab.kt`:

```kotlin
package com.mazesys.zerobite.screens.root

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mazesys.zerobite.ui.components.ScreenHeader
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme

/** Placeholder for the Recipes and Settings tabs until Plan 3. */
@Composable
fun PlaceholderTab(overline: String, title: String) {
    Column(Modifier.fillMaxSize().background(ZeroBiteTheme.colors.backgroundPrimary).padding(horizontal = Spacing.gutter)) {
        ScreenHeader(title = title, overline = overline)
        Text(
            "Arrives in the next build.",
            style = ZeroBiteTheme.type.body,
            color = ZeroBiteTheme.colors.textSecondary,
            modifier = Modifier.padding(top = Spacing.lg),
        )
    }
}
```

`screens/root/ScanPlaceholder.kt`:

```kotlin
package com.mazesys.zerobite.screens.root

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mazesys.zerobite.ui.components.AppButton
import com.mazesys.zerobite.ui.components.ButtonKind
import com.mazesys.zerobite.ui.components.DetailTitle
import com.mazesys.zerobite.ui.components.TopBar
import com.mazesys.zerobite.ui.components.TopBarLeading
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme

/** Placeholder for the scan flow until Plan 3 adds CameraX + ML Kit. */
@Composable
fun ScanPlaceholder(onClose: () -> Unit, onAddManually: () -> Unit) {
    Column {
        TopBar(TopBarLeading.Close(onClose))
        Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            DetailTitle(title = "Scan a label", overline = "Coming soon")
            Text(
                "Label and barcode scanning arrives in the next build. Add food by hand for now.",
                style = ZeroBiteTheme.type.body,
                color = ZeroBiteTheme.colors.textSecondary,
            )
            AppButton("Add manually", onAddManually, kind = ButtonKind.TONAL, icon = Icons.Rounded.EditNote)
        }
    }
}
```

`screens/root/AppAlert.kt`:

```kotlin
package com.mazesys.zerobite.screens.root

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.mazesys.zerobite.coordination.AlertConfig
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.semibold

/** App-level alerts raised through AppCoordinator.showAlert. */
@Composable
fun AppAlert(config: AlertConfig, onDismiss: () -> Unit) {
    val colors = ZeroBiteTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(config.title, style = ZeroBiteTheme.type.headline) },
        text = config.message?.let { message -> { Text(message, style = ZeroBiteTheme.type.subheadline, color = colors.textSecondary) } },
        confirmButton = {
            TextButton(onClick = { config.primary.action(); onDismiss() }) {
                Text(
                    config.primary.title,
                    style = ZeroBiteTheme.type.subheadline.semibold(),
                    color = if (config.primary.isDestructive) colors.statusExpired else colors.textPrimary,
                )
            }
        },
        dismissButton = config.secondary?.let { secondary ->
            {
                TextButton(onClick = { secondary.action(); onDismiss() }) {
                    Text(secondary.title, style = ZeroBiteTheme.type.subheadline, color = colors.textSecondary)
                }
            }
        },
        containerColor = colors.backgroundPrimary,
        shape = RoundedCornerShape(Radius.card),
    )
}
```

`screens/root/SheetHost.kt`:

```kotlin
package com.mazesys.zerobite.screens.root

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mazesys.zerobite.AppDependencies
import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.coordination.SheetDestination
import com.mazesys.zerobite.coordination.SheetViewModelStores
import com.mazesys.zerobite.core.model.ItemDraft
import com.mazesys.zerobite.screens.inventory.ItemFormMode
import com.mazesys.zerobite.screens.inventory.ItemFormScreen
import com.mazesys.zerobite.screens.inventory.ItemFormViewModel
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme

/** Full-screen sheets that slide up over the tabs (add/edit item, scan). */
@Composable
fun SheetHost(coordinator: AppCoordinator, sheetStores: SheetViewModelStores, dependencies: AppDependencies) {
    AnimatedContent(
        targetState = coordinator.presentedSheet,
        transitionSpec = { slideInVertically { it } togetherWith slideOutVertically { it } },
        contentKey = { it?.id },
        label = "sheet",
    ) { sheet ->
        if (sheet == null) return@AnimatedContent
        BackHandler { coordinator.dismissSheet() }
        CompositionLocalProvider(LocalViewModelStoreOwner provides sheetStores.owner(sheet.id)) {
            Box(Modifier.fillMaxSize().background(ZeroBiteTheme.colors.backgroundPrimary).systemBarsPadding().imePadding()) {
                when (val destination = sheet.destination) {
                    SheetDestination.Scan -> ScanPlaceholder(onClose = coordinator::dismissSheet, onAddManually = coordinator.inventory::showAddItem)
                    is SheetDestination.AddItem -> ItemFormSheet(ItemFormMode.Add, destination.draft, coordinator, dependencies)
                    is SheetDestination.EditItem -> ItemFormSheet(ItemFormMode.Edit(destination.itemId), null, coordinator, dependencies)
                }
            }
        }
    }
}

@Composable
private fun ItemFormSheet(mode: ItemFormMode, draft: ItemDraft?, coordinator: AppCoordinator, dependencies: AppDependencies) {
    val form = viewModel {
        ItemFormViewModel(mode, draft, dependencies.inventory, dependencies.clock, dependencies.ids, onSaved = { coordinator.dismissSheet() })
    }
    ItemFormScreen(form, onCancel = coordinator::dismissSheet)
}
```

`screens/root/MainScreen.kt`:

```kotlin
package com.mazesys.zerobite.screens.root

import androidx.compose.foundation.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SoupKitchen
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SoupKitchen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mazesys.zerobite.AppDependencies
import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.coordination.AppTab
import com.mazesys.zerobite.coordination.SheetViewModelStores
import com.mazesys.zerobite.screens.inventory.InventoryTab
import com.mazesys.zerobite.screens.inventory.InventoryViewModel
import com.mazesys.zerobite.ui.components.AppTabBar
import com.mazesys.zerobite.ui.components.TabItem
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme

private val MAIN_TABS = listOf(
    TabItem(AppTab.FRIDGE, AppTab.FRIDGE.title, Icons.Outlined.Kitchen, Icons.Rounded.Kitchen),
    TabItem(AppTab.RECIPES, AppTab.RECIPES.title, Icons.Outlined.SoupKitchen, Icons.Rounded.SoupKitchen),
    TabItem(AppTab.SETTINGS, AppTab.SETTINGS.title, Icons.Outlined.Settings, Icons.Rounded.Settings),
)

/** Tabs bound to AppCoordinator, plus app-level sheets and alerts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(coordinator: AppCoordinator, sheetStores: SheetViewModelStores, dependencies: AppDependencies) {
    // Tab roots live in the activity's store so they survive switching tabs.
    val inventoryList = viewModel(key = "inventory") {
        InventoryViewModel(dependencies.inventory, dependencies.settings, dependencies.clock, dependencies.ids, coordinator.inventory)
    }
    val tabState = rememberSaveableStateHolder()
    val presentedId = coordinator.presentedSheet?.id
    LaunchedEffect(presentedId) { sheetStores.retainOnly(presentedId) }

    Box(Modifier.fillMaxSize().background(ZeroBiteTheme.colors.backgroundPrimary)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Box(Modifier.weight(1f).statusBarsPadding()) {
                tabState.SaveableStateProvider(coordinator.selectedTab) {
                    when (coordinator.selectedTab) {
                        AppTab.FRIDGE -> InventoryTab(coordinator.inventory, inventoryList, dependencies)
                        AppTab.RECIPES -> PlaceholderTab(overline = "From your fridge", title = "Recipes")
                        AppTab.SETTINGS -> PlaceholderTab(overline = "Zero Bite", title = "Settings")
                    }
                }
            }
            // Hidden while typing so the bottom search field sits directly on the keyboard.
            if (!WindowInsets.isImeVisible) {
                AppTabBar(MAIN_TABS, coordinator.selectedTab, coordinator::selectTab, Modifier.navigationBarsPadding())
            }
        }
        SheetHost(coordinator, sheetStores, dependencies)
        coordinator.alert?.let { AppAlert(it, coordinator::dismissAlert) }
    }
}
```

`screens/root/RootScreen.kt`:

```kotlin
package com.mazesys.zerobite.screens.root

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazesys.zerobite.AppDependencies
import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.coordination.AppFlowController
import com.mazesys.zerobite.coordination.AppFlowPhase
import com.mazesys.zerobite.coordination.SheetViewModelStores
import kotlinx.coroutines.launch

/** Root driven by the app flow phase: launch → welcome → main. */
@Composable
fun RootScreen(flow: AppFlowController, coordinator: AppCoordinator, sheetStores: SheetViewModelStores, dependencies: AppDependencies) {
    val phase by flow.phase.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Crossfade(phase, animationSpec = tween(300), label = "phase") { current ->
        when (current) {
            AppFlowPhase.LAUNCH -> LaunchScreen(onFinish = { scope.launch { flow.transitionFromLaunch() } })
            AppFlowPhase.WELCOME -> WelcomePlaceholder(onDone = { scope.launch { flow.transitionToMain() } })
            AppFlowPhase.MAIN -> MainScreen(coordinator, sheetStores, dependencies)
        }
    }
}
```

- [ ] **Step 5: Wire the activity**

`MainActivity.kt`:

```kotlin
package com.mazesys.zerobite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.mazesys.zerobite.screens.root.RootScreen
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme

class MainActivity : ComponentActivity() {

    private val dependencies: AppDependencies get() = (application as ZeroBiteApplication).dependencies
    private val app: AppViewModel by viewModels { AppViewModel.factory(dependencies) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ZeroBiteTheme {
                RootScreen(app.flow, app.coordinator, app.sheetStores, dependencies)
            }
        }
    }
}
```

- [ ] **Step 6: Run the whole `:app` suite**

Run: `cd android && ./gradlew :app:testDebugUnitTest; cd ..`
Expected: every test PASSES, including `AppLaunchTest` from Task 1. It now shows the launch screen's "Zero Bite" on live dependencies.

- [ ] **Step 7: Commit**

```bash
git add android/app
git commit -m "Wire the Android app shell: launch, tabs, sheets and back handling

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 14: On-device smoke test, CI and docs

**Files:**
- Create: `android/app/src/androidTest/kotlin/com/mazesys/zerobite/SmokeTest.kt`
- Modify: `.github/workflows/android.yml`, `README.md`

**Interfaces:**
- Consumes: the whole app.
- Produces: an instrumented test that passes on the `Medium_Phone_API_36.0` emulator; CI running `:app` unit tests, lint and the debug build.

- [ ] **Step 1: Write the smoke test**

```kotlin
package com.mazesys.zerobite

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Launch → (welcome) → add an item by hand → find it in the fridge, on a real device. */
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    private fun existsText(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun addAnItemByHand() {
        compose.waitUntil(10_000) { exists("welcome.start") || exists("inventory.addButton") }
        if (exists("welcome.start")) compose.onNodeWithTag("welcome.start").performClick()

        compose.waitUntil(5_000) { exists("inventory.addButton") }
        compose.onNodeWithTag("inventory.addButton").performClick()
        compose.onNodeWithText("Add manually").performClick()

        val name = "Smoke test ${System.currentTimeMillis() % 100_000}"
        compose.waitUntil(5_000) { exists("itemForm.name") }
        compose.onNodeWithTag("itemForm.name").performTextInput(name)
        compose.onNodeWithTag("itemForm.save").performClick()

        compose.waitUntil(5_000) { exists("inventory.search") }
        compose.onNodeWithTag("inventory.search").performTextInput(name)
        compose.waitUntil(5_000) { existsText(name) }
    }
}
```

- [ ] **Step 2: Boot the emulator** (run in the background; it takes about a minute)

```bash
~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_36.0 -no-window -no-audio -no-boot-anim -no-snapshot-save
```

Then wait for boot:

```bash
~/Library/Android/sdk/platform-tools/adb wait-for-device shell 'while [ -z "$(getprop sys.boot_completed)" ]; do sleep 2; done'; echo booted
```

Expected: `booted`.

- [ ] **Step 3: Run the smoke test on the device**

Run: `cd android && ./gradlew :app:connectedDebugAndroidTest; cd ..`
Expected: `BUILD SUCCESSFUL`, `SmokeTest.addAnItemByHand` passed (report under `android/app/build/reports/androidTests/`).

- [ ] **Step 4: Look at the real app**

```bash
ADB=~/Library/Android/sdk/platform-tools/adb
$ADB shell pm clear com.mazesys.zerobite
$ADB shell am start -n com.mazesys.zerobite/.MainActivity
```

Then:
1. Tap **Get started**, then **Try it with sample food**.
2. Capture the screen with `$ADB exec-out screencap -p > <scratchpad>/android-fridge.png`.
3. Open an item and capture again. Do the same for **+ → Add manually**.
4. Turn dark mode on with `$ADB shell cmd uimode night yes` and capture the Fridge again.

Compare each image with the iOS screenshots in `appstore/screenshots/6.9in/`. Fix any obvious mismatch in spacing, colour or copy before continuing. Each fix needs its own failing test, or a re-recorded screenshot that you have inspected.

- [ ] **Step 5: Extend CI**

Replace the job in `.github/workflows/android.yml` with:

```yaml
jobs:
  android:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: android
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew :core:check :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Run the same command locally: `cd android && ./gradlew :core:check :app:testDebugUnitTest :app:lintDebug :app:assembleDebug; cd ..`
Expected: `BUILD SUCCESSFUL`. If lint reports errors, fix them. Leave warnings, but list them in your ledger.

- [ ] **Step 6: Document running the Android app**

In `README.md`, replace the `## Android` section's first paragraph and command block with:

````markdown
## Android

The Android app lives in `android/`: `:core` holds all of Zero Bite's pure logic (no Android
dependencies, ready to become a Kotlin Multiplatform module), and `:app` is the Jetpack
Compose app with the same bespoke look as iOS. Scanning, reminders, recipes, alerts and
settings arrive in the next plan (see `specs/plans/`).

Open `android/` in Android Studio, or from the command line (JDK 17+; Android Studio's
bundled JDK works):

```bash
cd android
./gradlew :app:installDebug          # build and install on a running emulator/device
./gradlew :core:check :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest   # on-device smoke test
```

`scripts/fetch-fonts.sh` also installs Mona Sans into the Android app (it falls back to the
system font without it). Screenshot tests use Roborazzi: `./gradlew :app:recordRoborazziDebug`
records `android/app/src/test/screenshots/`, `verifyRoborazziDebug` compares against them.
````

Keep the existing "Keeping iOS and Android in step" subsection.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/androidTest .github/workflows/android.yml README.md
git commit -m "Add an on-device smoke test and build the Android app in CI

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## Spec coverage (Plan 2 scope)

| Spec section | Covered by |
|---|---|
| §4 `:app` structure, recipes.json copied from shared-assets, minSdk 26 | Task 1 |
| §5 MVVM-C: flow controller, app coordinator, feature coordinator, manual DI, VMs with one StateFlow, coordinators own navigation | Tasks 8, 9, 10–13 |
| §5 Deep links via `AppCoordinator.showItem`, back → coordinator `pop()` | Tasks 9, 11, 13 |
| §5 Explicit async states, DB failure crashes loudly (Room open is unguarded) | Tasks 7, 10, 11 |
| §6 Storage: Room for inventory, DataStore with iOS key names | Tasks 7, 8 |
| §7 Theme, palette light/dark, Mona Sans with fallback, Radius/Spacing, bespoke components, BrandMark checked against iOS | Tasks 2–6 |
| §7 Edge-to-edge, system back, Android 12+ SplashScreen API | Tasks 1, 13 |
| §8 VM tests with fakes + fixed clock, Room tests, Roborazzi screenshots light/dark, one instrumented smoke test | Tasks 7–14 |
| §8 CI for `:app` (unit tests, lint, debug APK) | Task 14 |
| §6 Scanning, §6 Reminders, recalls UI, Recipes/Settings tabs, onboarding | Plan 3 |
| §7 Launcher icon (adaptive + monochrome), splash mark, §9 signing and Firebase distribution | Plan 4 |
