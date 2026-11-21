# Zero Bite Android — Plan 3: Recipes, Reminders, Settings, Onboarding

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Plan 2's placeholders with the real Recipes tab (catalogue, recipe detail, "I cooked this"), expiry reminders (AlarmManager + notifications + deep links + the Fridge tip), the Settings tab and the onboarding flow, all matching the iOS app.

**Architecture:** Builds on `android-app-shell` (Plan 2). Recipes follow the Fridge pattern: a `RecipesCoordinator` owns a Navigation 3 back stack, and its list view model lives in the activity store. Reminders are planned by `:core`'s `ExpiryNotificationPlanner`. An app-scoped `ReminderSync` re-plans them whenever the fridge or settings change, and an `AlarmReminderScheduler` turns them into inexact alarms that a `ReminderReceiver` posts as notifications. Runtime permission requests go through a `PermissionRequester` that the activity provides to Compose. Camera scanning and FSA alerts are Plan 4. Icons, signing and Firebase are Plan 5.

**Tech Stack:** As Plan 2, plus `androidx.core:core-ktx` (notifications) and Material 3 `Switch`/`TimePicker` (restyled). Foundation's `HorizontalPager` and `FlowRow`.

**Spec:** [`specs/2026-10-08-android-app-design.md`](../2026-10-08-android-app-design.md). Builds on [`2026-10-08-android-app-shell.md`](2026-10-08-android-app-shell.md).

## Global Constraints

- Everything in Plan 2's Global Constraints still holds: package, SDK levels, Robolectric flags, the `junit4.v2` compose rule, colours/corners/spacing tokens only, iOS copy verbatim, `AppClock` for time, and running `./gradlew` from `android/` with Android Studio's JBR.
- iOS app code is not changed.
- Copy is taken from `ios/ZeroBite/Screens/{Recipes,Settings,Welcome}/*`, `ios/ZeroBite/UI/RecipeRow.swift` and `ios/ZeroBite/Services/Notifications/*`. iOS-only wording is adapted, and every adaptation is listed in the task that makes it (e.g. "Apple Vision" → "on your device").
- Reminders: inexact `AlarmManager.setAndAllowWhileIdle` only (no exact-alarm permission). Notification channel `expiry`. Taps deep-link to the item through `AppCoordinator.showItem`.
- `POST_NOTIFICATIONS` (Android 13+) is requested only from an explicit user action: welcome "Turn on reminders", the Fridge tip's "Turn on", or the Settings toggle.
- Robolectric screen tests use `@Config(qualifiers = "w411dp-h891dp-xxhdpi")` (Plan 2 ruling: the default screen hides lazy-grid items).
- To find a header whose text also appears on a chip or tab, use `hasText(...) and isHeading()` (Plan 2 ruling).
- **Not in this plan:** scanning (CameraX + ML Kit) and FSA food alerts (recall banner, cards, flags, the alerts screen, recall notifications). Those are Plan 4. In Settings, the "Food alerts" row and the allergen and recall toggles are saved now, and Plan 4 makes them do something.

## Review Focus

1. **Reminder permission revoked in system settings** after reminders were turned on: no alarms stay scheduled, the Settings toggle reads off, and the Fridge tip doesn't reappear as if the user had never been asked. Pinned by `revoked permission cancels every reminder` (Task 4) and `notification state follows the system permission` (Task 7).
2. **Reboot, app update or a time-zone change** must keep reminders, since Android drops alarms in those cases. Pinned by `reschedule receiver replans on boot and time changes` (Task 5).
3. **Tapping a reminder while the app is already open** on another tab with a sheet up must land on that item's detail. Pinned by `notification tap opens the item from anywhere` (Task 5).
4. **"I cooked this" when every ingredient is a store-cupboard staple, or when the matched items were used up meanwhile:** the button is disabled, or the sheet lets you finish without crashing. Pinned by `nothing to mark keeps the button disabled` and `items used up meanwhile are skipped` (Task 3).
5. **Steppers at their limits** (lead days and "expiring soon" are clamped to 1–14) and a reminder time of 00:00 or 23:59. Pinned by `steppers clamp to one to fourteen days` and `reminder time accepts the whole day` (Task 7).

---

## File Structure

```
android/app/src/main/
├── AndroidManifest.xml                    + POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED, receivers, singleTop
├── res/drawable/ic_notification.xml       monochrome mark for the status bar
└── kotlin/com/mazesys/zerobite/
    ├── AppDependencies.kt                 + notifications access, scheduler, notifier, ReminderSync
    ├── ZeroBiteApplication.kt             starts ReminderSync, creates channels
    ├── MainActivity.kt                    permission requester, deep-link intents
    ├── reminders/                         NotificationAccess, Notifications (channels/post), Notifier,
    │                                      ReminderScheduler + AlarmReminderScheduler, ReminderReceiver,
    │                                      RescheduleReceiver, ReminderSync, PermissionRequester
    ├── coordination/                      + RecipesCoordinator, SettingsCoordinator; AppCoordinator/
    │                                      InventoryCoordinator gain recipe + cooked + how-it-works routes
    ├── ui/components/                     + RecipeViews (icon, artwork, cards, row, ExpiringChip),
    │                                      Chip, AppStepper, AppSwitch, BrandLockup
    └── screens/
        ├── recipes/                       RecipesViewModel/Screen, RecipeDetailViewModel/Screen,
        │                                  CookedViewModel/Screen, RecipesTab
        ├── shared/DetailEntries.kt        ItemDetailEntry, RecipeDetailEntry (used by both tabs)
        ├── settings/                      SettingsViewModel/Screen
        └── welcome/                       WelcomeViewModel, WelcomeScreen, WelcomeIllustrations
```

---

### Task 1: Recipe presentation components

**Files:**
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/ui/components/RecipeViews.kt`
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/ui/components/RecipeViewsTest.kt`

**Interfaces:**
- Consumes: `Recipe`, `RecipeMatch`, `FoodItem` (`:core`); `AppCard`, `IconTile`, `Tag` and the theme (Plan 2).
- Produces:
  - `val Recipe.icon: ImageVector`
  - `val RecipeMatch.matchPercent: Int`
  - `val RecipeMatch.statusText: String`
  - `fun RecipeMatch.accent(colors: ZeroBiteColors): Color`
  - `RecipeArtwork(recipe, tint, height, modifier)`
  - `FeaturedRecipeCard(match, onClick, modifier)`
  - `RecipeCard(match, onClick, modifier)`
  - `RecipeRow(match, modifier)`
  - `ExpiringChip(item, today, soonThresholdDays, onClick, modifier)`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Egg
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SoupKitchen
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.mazesys.zerobite.core.model.FoodCategory
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.Recipe
import com.mazesys.zerobite.core.recipes.MatchedIngredient
import com.mazesys.zerobite.core.recipes.RecipeMatch
import com.mazesys.zerobite.ui.theme.LightColors
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
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
class RecipeViewsTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate(2026, 9, 7)

    private fun recipe(title: String, vararg ingredients: String) = Recipe(
        id = title.lowercase(), title = title, summary = "", prepMinutes = 10, servings = 2, tags = emptyList(), steps = listOf("Cook"),
        ingredients = ingredients.map { Recipe.Ingredient(it) },
    )

    private fun item(name: String, days: Int) = FoodItem(
        id = name, name = name, category = FoodCategory.DAIRY,
        addedAt = Instant.parse("2026-09-01T09:00:00Z"), expiresOn = today.plus(days, DateTimeUnit.DAY),
    )

    private fun match(recipe: Recipe, matched: Int, missing: List<String> = emptyList(), expiring: List<FoodItem> = emptyList()) = RecipeMatch(
        recipe = recipe,
        matched = recipe.ingredients.take(matched).map { MatchedIngredient(it, item(it.name, 10)) },
        missing = missing.map { Recipe.Ingredient(it) },
        missingOptional = emptyList(),
        expiringItems = expiring,
        score = 0.0,
    )

    @Test
    fun `artwork icon comes from the title`() {
        assertEquals(Icons.Rounded.Egg, recipe("Classic cheese omelette").icon)
        assertEquals(Icons.Rounded.SoupKitchen, recipe("Tomato soup").icon)
        assertEquals(Icons.Rounded.Restaurant, recipe("Mystery dish").icon)
    }

    @Test
    fun `status text and match percent read like iOS`() {
        val omelette = recipe("Omelette", "eggs", "butter", "milk")
        assertEquals("Ready to cook", match(omelette, 3).statusText)
        assertEquals(100, match(omelette, 3).matchPercent)
        assertEquals("Missing milk", match(omelette, 2, listOf("milk")).statusText)
        assertEquals(67, match(omelette, 2, listOf("milk")).matchPercent)
        assertEquals("Missing a, b +2", match(omelette, 0, listOf("a", "b", "c", "d")).statusText)
    }

    @Test
    fun `accent prefers expiring food, then ready, then missing`() {
        val omelette = recipe("Omelette", "eggs")
        assertEquals(LightColors.brandOrange, match(omelette, 1, expiring = listOf(item("eggs", 1))).accent(LightColors))
        assertEquals(LightColors.statusFresh, match(omelette, 1).accent(LightColors))
        assertEquals(LightColors.brandYellow, match(omelette, 0, listOf("eggs")).accent(LightColors))
    }

    @Test
    fun `cards show percent, expiring tags and react to taps`() {
        var opened = 0
        val omelette = recipe("Omelette", "eggs", "butter", "milk")
        compose.setContent {
            ZeroBiteTheme {
                Column {
                    FeaturedRecipeCard(match(omelette, 2, listOf("milk"), expiring = listOf(item("Eggs", 1))), onClick = { opened++ })
                    RecipeRow(match(omelette, 3))
                }
            }
        }
        compose.onNodeWithContentDescription("67 percent of ingredients in your fridge").assertExists()
        compose.onNodeWithText("Eggs").assertExists()
        compose.onNodeWithText("10 min · Ready to cook").assertExists()
        compose.onNodeWithText("Omelette", substring = false).performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `expiring chip opens its item`() {
        var tapped = 0
        compose.setContent { ZeroBiteTheme { ExpiringChip(item("Spinach", 0), today, 3, onClick = { tapped++ }) } }
        compose.onNodeWithText("Today").assertExists()
        compose.onNodeWithText("Spinach").performClick()
        assertEquals(1, tapped)
    }

    @Test
    fun `recipe views light`() = gallery(false)

    @Test
    fun `recipe views dark`() = gallery(true)

    private fun gallery(dark: Boolean) {
        val omelette = recipe("Classic cheese omelette", "eggs", "butter", "milk")
        val soup = recipe("Tomato soup", "tomatoes", "onion", "stock", "cream")
        compose.setContent {
            ZeroBiteTheme(darkTheme = dark) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeaturedRecipeCard(match(omelette, 3, expiring = listOf(item("Eggs", 1), item("Butter", 2))), onClick = {})
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RecipeCard(match(soup, 3, listOf("cream")), onClick = {}, modifier = Modifier.weight(1f))
                        RecipeCard(match(omelette, 3), onClick = {}, modifier = Modifier.weight(1f))
                    }
                    RecipeRow(match(soup, 2, listOf("cream", "onion")))
                    ExpiringChip(item("Spinach", 0), today, 3, onClick = {})
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/recipe_views_${if (dark) "dark" else "light"}.png")
    }
}
```

- [ ] **Step 2: Run to see it fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests '*RecipeViewsTest'; cd ..`
Expected: compilation FAILS: unresolved `icon` on `Recipe`, `FeaturedRecipeCard` and others.

- [ ] **Step 3: Implement** (port of `ios/ZeroBite/UI/RecipeRow.swift`; SF Symbols mapped to Material Rounded icons)

`ui/components/RecipeViews.kt`:

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DinnerDining
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Egg
import androidx.compose.material.icons.rounded.Grass
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SetMeal
import androidx.compose.material.icons.rounded.SoupKitchen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.Recipe
import com.mazesys.zerobite.core.recipes.RecipeMatch
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteColors
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.icon
import com.mazesys.zerobite.ui.theme.semibold
import com.mazesys.zerobite.ui.theme.tint
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

/** Placeholder artwork until recipes carry photos. From the title only: tags say nothing about the look. */
val Recipe.icon: ImageVector
    get() {
        val haystack = title.lowercase()
        val rules: List<Pair<ImageVector, List<String>>> = listOf(
            Icons.Rounded.LocalCafe to listOf("smoothie", "parfait", "oats", "porridge", "granola", "shake"),
            Icons.Rounded.SoupKitchen to listOf("soup", "broth", "stew", "chowder"),
            Icons.Rounded.Eco to listOf("salad", "slaw"),
            Icons.Rounded.SetMeal to listOf("fish", "salmon", "tuna", "prawn", "cod"),
            Icons.Rounded.Cake to listOf("cake", "bake", "muffin", "pudding", "dessert", "crumble", "bread"),
            Icons.Rounded.Egg to listOf("omelette", "frittata", "fried", "fry", "scrambled", "egg", "pancake", "hash"),
            Icons.Rounded.LunchDining to listOf("wrap", "sandwich", "toastie", "burger", "quesadilla"),
            Icons.Rounded.DinnerDining to listOf("pasta", "spaghetti", "carbonara", "risotto", "rice", "noodle"),
            Icons.Rounded.Grass to listOf("vegetable", "curry", "roast", "potato", "carrot"),
        )
        return rules.firstOrNull { (_, needles) -> needles.any { haystack.contains(it) } }?.first ?: Icons.Rounded.Restaurant
    }

/** Share of required ingredients already in the fridge, 0–100. */
val RecipeMatch.matchPercent: Int
    get() = if (requiredCount == 0) 100 else (requiredMatchedCount.toDouble() / requiredCount * 100).roundToInt()

val RecipeMatch.statusText: String
    get() {
        if (canMake) return "Ready to cook"
        val names = missing.take(2).joinToString(", ") { it.name }
        val more = if (missing.size > 2) " +${missing.size - 2}" else ""
        return "Missing $names$more"
    }

fun RecipeMatch.accent(colors: ZeroBiteColors): Color = when {
    expiringItems.isNotEmpty() -> colors.brandOrange
    canMake -> colors.statusFresh
    else -> colors.brandYellow
}

/** Tinted panel standing in for a recipe photo. */
@Composable
fun RecipeArtwork(recipe: Recipe, tint: Color, height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(height).background(tint.copy(alpha = 0.14f)).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Icon(recipe.icon, contentDescription = null, tint = tint, modifier = Modifier.size(height * 0.4f))
    }
}

/** Large card for the best match at the top of the Recipes screen. */
@Composable
fun FeaturedRecipeCard(match: RecipeMatch, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ZeroBiteTheme.colors
    val type = ZeroBiteTheme.type
    val accent = match.accent(colors)
    val recipe = match.recipe
    val meta = buildList {
        add("${recipe.prepMinutes} min")
        add("serves ${recipe.servings}")
        if (match.expiringItems.isNotEmpty()) add("uses ${match.expiringItems.size} expiring")
    }.joinToString(" · ")

    AppCard(modifier.clip(RoundedCornerShape(Radius.card)).clickable(role = Role.Button, onClick = onClick), padding = 0.dp) {
        RecipeArtwork(recipe, accent, 110.dp)
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(recipe.title, style = type.headline, color = colors.textPrimary, modifier = Modifier.weight(1f))
                Text(
                    "${match.matchPercent}%",
                    style = type.headline,
                    color = accent,
                    modifier = Modifier.padding(start = Spacing.sm).semantics {
                        contentDescription = "${match.matchPercent} percent of ingredients in your fridge"
                    },
                )
            }
            Text(meta, style = type.caption, color = colors.textSecondary)
            if (match.expiringItems.isNotEmpty()) {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    match.expiringItems.take(3).forEach { Tag(it.name, tint = colors.brandOrange) }
                    if (match.expiringItems.size > 3) Tag("+${match.expiringItems.size - 3}")
                }
            } else {
                Text(
                    match.statusText,
                    style = type.caption.semibold(),
                    color = if (match.canMake) colors.statusFresh else colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** Half-width grid card. */
@Composable
fun RecipeCard(match: RecipeMatch, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ZeroBiteTheme.colors
    val type = ZeroBiteTheme.type
    AppCard(modifier.clip(RoundedCornerShape(Radius.card)).clickable(role = Role.Button, onClick = onClick), padding = 10.dp) {
        RecipeArtwork(match.recipe, match.accent(colors), 60.dp, Modifier.clip(RoundedCornerShape(Radius.control)))
        Text(
            match.recipe.title,
            style = type.subheadline.semibold(),
            color = colors.textPrimary,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            match.statusText,
            style = type.caption,
            color = if (match.canMake) colors.statusFresh else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            "${match.recipe.prepMinutes} min · ${match.matchPercent}% in fridge",
            style = type.caption2,
            color = colors.textMuted,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** Full-width row for lists inside cards ("Recipes using …" on item detail). */
@Composable
fun RecipeRow(match: RecipeMatch, modifier: Modifier = Modifier) {
    val colors = ZeroBiteTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        IconTile(match.recipe.icon, tint = match.accent(colors), size = 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(match.recipe.title, style = ZeroBiteTheme.type.subheadline.semibold(), color = colors.textPrimary)
            Text(
                "${match.recipe.prepMinutes} min · ${match.statusText}",
                style = ZeroBiteTheme.type.caption,
                color = if (match.canMake) colors.statusFresh else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
    }
}

/** Tappable chip for an expiring item, used in the "Use these first" strip. */
@Composable
fun ExpiringChip(item: FoodItem, today: LocalDate, soonThresholdDays: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ZeroBiteTheme.colors
    val tint = item.status(today, soonThresholdDays).tint(colors)
    val shape = RoundedCornerShape(Radius.control)
    Row(
        modifier
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, tint.copy(alpha = 0.4f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(item.category.icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text(item.name, style = ZeroBiteTheme.type.caption, color = colors.textPrimary, maxLines = 1)
        Text(item.shortExpiryLabel(today), style = ZeroBiteTheme.type.caption.semibold(), color = tint)
    }
}
```

- [ ] **Step 4: Run the tests and record**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests '*RecipeViewsTest' && ./gradlew :app:recordRoborazziDebug --tests '*RecipeViewsTest'; cd ..
```

Expected: 7 tests PASS; `recipe_views_light.png` and `recipe_views_dark.png` recorded. Compare them with the iOS Recipes tab (`appstore/screenshots/6.9in/6.9in-04-cook.png`).

- [ ] **Step 5: Commit**

```bash
git add android/app
git commit -m "Add Android recipe cards, row, artwork and expiring chip

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Recipes tab: catalogue ranked against the fridge

**Files:**
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/coordination/RecipesCoordinator.kt`
- Modify: `android/app/src/main/kotlin/com/mazesys/zerobite/coordination/AppCoordinator.kt` (adds `val recipes`)
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/screens/recipes/{RecipesViewModel,RecipesScreen}.kt`
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/screens/recipes/{RecipesViewModelTest,RecipesScreenTest}.kt`; add `testRecipes()` to `testing/Fakes.kt`

**Interfaces:**
- Consumes: `RecipeMatcher` (`:core`); Task 1 components; Plan 2 components and coordinators.
- Produces:
  - `@Serializable sealed interface RecipesRoute : NavKey { Catalogue; RecipeDetail(recipeId); ItemDetail(itemId) }`
  - `class RecipesCoordinator(parent: AppCoordinator)` with `backStack`, `showRecipe`, `showItem`, `showEditItem`, `pop`, `popToRoot`; `showCooked` is added in Task 3
  - `AppCoordinator.recipes: RecipesCoordinator`
  - `enum class RecipeScope(title, sectionTitle) { READY, NEARLY, ALL }`
  - `data class RecipesUiState(...)` with `showsExpiringStrip`, `emptyTitle`, `emptyMessage`; `RecipesUiState.build(active, recipes, scope, searchText, soonThresholdDays, today)`
  - `class RecipesViewModel(inventory, settings, recipes: RecipeRepository, clock, coordinator: RecipesCoordinator)` with `setScope`, `setSearchText`, `showRecipe(match)`, `showItem(item)`
  - `RecipesScreen(viewModel, modifier)`
  - Test helper `testRecipes(): List<Recipe>` (omelette, stir-fry, pancakes, big salad)

- [ ] **Step 1: Add the recipe fixtures to `testing/Fakes.kt`**

Append:

```kotlin
/** Four recipes whose ranking against [recipeFridge] is easy to reason about. */
fun testRecipes(): List<Recipe> {
    fun recipe(id: String, title: String, vararg ingredients: Pair<String, Boolean>) = Recipe(
        id = id, title = title, summary = "$title summary", prepMinutes = 15, servings = 2,
        tags = listOf("quick"), steps = listOf("Prepare", "Cook", "Serve"),
        ingredients = ingredients.map { (name, optional) -> Recipe.Ingredient(name, amount = "1", optional = optional) },
    )
    return listOf(
        recipe("omelette", "Omelette", "eggs" to false, "butter" to false, "salt" to false, "cheese" to true),
        recipe("stirfry", "Chicken stir-fry", "chicken" to false, "bell pepper" to false, "soy sauce" to false),
        recipe("pancakes", "Pancakes", "flour" to false, "eggs" to false, "milk" to false, "blueberries" to false),
        recipe("salad", "Big salad", "lettuce" to false, "tomatoes" to false, "cucumber" to false, "olive oil" to false),
    )
}

/** Eggs expiring tomorrow, butter and chicken: omelette ready, stir-fry and pancakes nearly, salad far off. */
fun recipeFridge(): List<FoodItem> {
    fun item(id: String, name: String, days: Int) =
        FoodItem(id = id, name = name, addedAt = TestClock.now, expiresOn = TestClock.today.plus(days, DateTimeUnit.DAY))
    return listOf(item("eggs", "Eggs", 1), item("butter", "Butter", 20), item("chicken", "Chicken breast", 5))
}
```

with imports `com.mazesys.zerobite.core.model.Recipe`, `kotlinx.datetime.DateTimeUnit` and `kotlinx.datetime.plus`.

- [ ] **Step 2: Write the failing tests** (ported from the behaviour of `RecipesViewModel.swift`)

`screens/recipes/RecipesViewModelTest.kt`:

```kotlin
package com.mazesys.zerobite.screens.recipes

import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.coordination.RecipesRoute
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeRecipes
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.recipeFridge
import com.mazesys.zerobite.testing.testRecipes
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecipesViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val app = AppCoordinator { TestClock.today }
    private val vm = RecipesViewModel(FakeInventoryRepository(recipeFridge()), FakeSettings(), FakeRecipes(testRecipes()), TestClock.fixed(), app.recipes)
    private fun ids() = vm.state.value.displayed.map { it.recipe.id }

    @Test
    fun `ready shows only what can be made now`() {
        assertEquals(listOf("omelette"), ids())
        assertEquals("You can make these now", vm.state.value.scope.sectionTitle)
    }

    @Test
    fun `nearly shows recipes missing one or two things`() {
        vm.setScope(RecipeScope.NEARLY)
        assertEquals(setOf("stirfry", "pancakes"), ids().toSet())
    }

    @Test
    fun `all shows the whole catalogue ranked`() {
        vm.setScope(RecipeScope.ALL)
        assertEquals(4, ids().size)
        assertEquals("omelette", ids().first())
    }

    @Test
    fun `search matches titles and ingredients and hides the strip`() {
        assertTrue(vm.state.value.showsExpiringStrip)
        assertEquals(listOf("Eggs"), vm.state.value.expiringItems.map { it.name })
        vm.setScope(RecipeScope.ALL)
        vm.setSearchText("pepper")
        assertEquals(listOf("stirfry"), ids())
        assertFalse(vm.state.value.showsExpiringStrip)
        vm.setSearchText("PANCAKE")
        assertEquals(listOf("pancakes"), ids())
    }

    @Test
    fun `empty states explain themselves`() {
        vm.setSearchText("caviar")
        assertEquals(emptyList(), ids())
        assertEquals("Nothing to cook yet", vm.state.value.emptyTitle)
        assertEquals("Try a different search.", vm.state.value.emptyMessage)
        val empty = RecipesViewModel(FakeInventoryRepository(), FakeSettings(), FakeRecipes(), TestClock.fixed(), app.recipes)
        empty.setScope(RecipeScope.NEARLY)
        assertEquals("No near-misses", empty.state.value.emptyTitle)
        assertEquals(
            "Everything is either ready to cook or needs more than a couple of ingredients.",
            empty.state.value.emptyMessage,
        )
    }

    @Test
    fun `taps route through the coordinator`() {
        vm.showRecipe(vm.state.value.displayed.first())
        assertEquals(RecipesRoute.RecipeDetail("omelette"), app.recipes.backStack.last())
        vm.showItem(vm.state.value.expiringItems.first())
        assertEquals(RecipesRoute.ItemDetail("eggs"), app.recipes.backStack.last())
        app.recipes.popToRoot()
        assertEquals(listOf<RecipesRoute>(RecipesRoute.Catalogue), app.recipes.backStack.toList())
    }
}
```

`screens/recipes/RecipesScreenTest.kt`:

```kotlin
package com.mazesys.zerobite.screens.recipes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureRoboImage
import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.coordination.RecipesRoute
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeRecipes
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.recipeFridge
import com.mazesys.zerobite.testing.testRecipes
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
class RecipesScreenTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val app = AppCoordinator { TestClock.today }

    private fun show(dark: Boolean = false) {
        val vm = RecipesViewModel(FakeInventoryRepository(recipeFridge()), FakeSettings(), FakeRecipes(testRecipes()), TestClock.fixed(), app.recipes)
        compose.setContent { ZeroBiteTheme(darkTheme = dark) { RecipesScreen(vm) } }
    }

    @Test
    fun `shows the strip, scopes and the featured recipe`() {
        show()
        compose.onNode(hasText("Recipes") and isHeading()).assertIsDisplayed()
        compose.onNodeWithText("Use these first").assertIsDisplayed()
        compose.onNodeWithText("You can make these now").assertIsDisplayed()
        compose.onNodeWithText("Omelette").performClick()
        assertEquals(RecipesRoute.RecipeDetail("omelette"), app.recipes.backStack.last())
    }

    @Test
    fun `nearly scope and search`() {
        show()
        compose.onNodeWithText("Nearly").performClick()
        compose.onNodeWithText("Missing one or two things").assertIsDisplayed()
        compose.onNodeWithTag("recipes.search").performTextInput("salad")
        compose.onNodeWithText("No near-misses").assertIsDisplayed()
    }

    @Test
    fun `recipes light`() {
        show()
        compose.onRoot().captureRoboImage("src/test/screenshots/recipes_light.png")
    }

    @Test
    fun `recipes dark`() {
        show(dark = true)
        compose.onRoot().captureRoboImage("src/test/screenshots/recipes_dark.png")
    }
}
```

- [ ] **Step 3: Run to see them fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests '*Recipes*Test'; cd ..`
Expected: compilation FAILS: unresolved `RecipesViewModel`, `RecipeScope`, `app.recipes`.

- [ ] **Step 4: Implement the coordinator**

`coordination/RecipesCoordinator.kt`:

```kotlin
package com.mazesys.zerobite.coordination

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface RecipesRoute : NavKey {
    @Serializable
    data object Catalogue : RecipesRoute

    @Serializable
    data class RecipeDetail(val recipeId: String) : RecipesRoute

    @Serializable
    data class ItemDetail(val itemId: String) : RecipesRoute
}

/** Navigation within the Recipes tab. */
class RecipesCoordinator(private val parent: AppCoordinator) {

    val backStack: SnapshotStateList<RecipesRoute> = mutableStateListOf(RecipesRoute.Catalogue)

    fun showRecipe(id: String) {
        backStack.add(RecipesRoute.RecipeDetail(id))
    }

    fun showItem(id: String) {
        backStack.add(RecipesRoute.ItemDetail(id))
    }

    fun showEditItem(id: String) = parent.presentSheet(SheetDestination.EditItem(id))

    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun popToRoot() {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }
}
```

In `coordination/AppCoordinator.kt`, add below `val inventory = InventoryCoordinator(this)`:

```kotlin
    val recipes = RecipesCoordinator(this)
```

- [ ] **Step 5: Implement the view model**

`screens/recipes/RecipesViewModel.kt`:

```kotlin
package com.mazesys.zerobite.screens.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazesys.zerobite.coordination.RecipesCoordinator
import com.mazesys.zerobite.core.inventory.InventoryRepository
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.Recipe
import com.mazesys.zerobite.core.recipes.RecipeMatch
import com.mazesys.zerobite.core.recipes.RecipeMatcher
import com.mazesys.zerobite.core.recipes.RecipeRepository
import com.mazesys.zerobite.core.time.AppClock
import com.mazesys.zerobite.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

enum class RecipeScope(val title: String, val sectionTitle: String) {
    READY("Ready", "You can make these now"),
    NEARLY("Nearly", "Missing one or two things"),
    ALL("All", "All recipes"),
}

data class RecipesUiState(
    val today: LocalDate,
    val isLoading: Boolean = true,
    val scope: RecipeScope = RecipeScope.READY,
    val searchText: String = "",
    val soonThresholdDays: Int = 3,
    /** Items needing attention, for the "Use these first" strip. */
    val expiringItems: List<FoodItem> = emptyList(),
    val displayed: List<RecipeMatch> = emptyList(),
) {
    val showsExpiringStrip: Boolean get() = expiringItems.isNotEmpty() && searchText.isEmpty()

    val emptyTitle: String
        get() = when (scope) {
            RecipeScope.READY -> "Nothing to cook yet"
            RecipeScope.NEARLY -> "No near-misses"
            RecipeScope.ALL -> "No recipes match"
        }

    val emptyMessage: String
        get() = when {
            searchText.isNotEmpty() -> "Try a different search."
            scope == RecipeScope.READY ->
                "Add a few more things to your fridge, or check the “Nearly” tab for recipes that need one more ingredient."
            scope == RecipeScope.NEARLY ->
                "Everything is either ready to cook or needs more than a couple of ingredients."
            else -> "The recipe catalogue is empty."
        }

    companion object {
        fun build(
            active: List<FoodItem>,
            recipes: List<Recipe>,
            scope: RecipeScope,
            searchText: String,
            soonThresholdDays: Int,
            today: LocalDate,
        ): RecipesUiState {
            val matches = RecipeMatcher(today, soonThresholdDays).match(recipes, active)
            val query = searchText.trim().lowercase()
            val displayed = matches.filter { match ->
                val inScope = when (scope) {
                    RecipeScope.READY -> match.canMake
                    RecipeScope.NEARLY -> match.missing.size in 1..2
                    RecipeScope.ALL -> true
                }
                inScope && (
                    query.isEmpty() ||
                        match.recipe.title.lowercase().contains(query) ||
                        match.recipe.ingredients.any { it.name.lowercase().contains(query) }
                    )
            }
            return RecipesUiState(
                today = today,
                isLoading = false,
                scope = scope,
                searchText = searchText,
                soonThresholdDays = soonThresholdDays,
                expiringItems = active.filter { it.status(today, soonThresholdDays).needsAttention },
                displayed = displayed,
            )
        }
    }
}

/** Recipes ranked against the fridge, with a scope filter and search. */
class RecipesViewModel(
    inventory: InventoryRepository,
    settings: SettingsRepository,
    recipes: RecipeRepository,
    private val clock: AppClock,
    private val coordinator: RecipesCoordinator,
) : ViewModel() {

    private val scope = MutableStateFlow(RecipeScope.READY)
    private val searchText = MutableStateFlow("")
    private val catalogue = flow { emit(recipes.recipes()) }

    val state: StateFlow<RecipesUiState> =
        combine(inventory.items, settings.state, catalogue, scope, searchText) { items, s, all, sc, query ->
            RecipesUiState.build(items.filterNot { it.isConsumed }, all, sc, query, s.soonThresholdDays, clock.today())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, RecipesUiState(today = clock.today()))

    fun setScope(value: RecipeScope) {
        scope.value = value
    }

    fun setSearchText(text: String) {
        searchText.value = text
    }

    fun showRecipe(match: RecipeMatch) = coordinator.showRecipe(match.recipe.id)

    fun showItem(item: FoodItem) = coordinator.showItem(item.id)
}
```

- [ ] **Step 6: Implement the screen** (follows `RecipesView.swift`)

`screens/recipes/RecipesScreen.kt`:

```kotlin
package com.mazesys.zerobite.screens.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazesys.zerobite.ui.components.AppCard
import com.mazesys.zerobite.ui.components.BottomSearchBar
import com.mazesys.zerobite.ui.components.ChipFilter
import com.mazesys.zerobite.ui.components.ExpiringChip
import com.mazesys.zerobite.ui.components.FeaturedRecipeCard
import com.mazesys.zerobite.ui.components.RecipeCard
import com.mazesys.zerobite.ui.components.ScreenHeader
import com.mazesys.zerobite.ui.components.SectionHeading
import com.mazesys.zerobite.ui.components.bleed
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme

@Composable
fun RecipesScreen(viewModel: RecipesViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize().background(ZeroBiteTheme.colors.backgroundPrimary)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f).testTag("recipes.grid"),
            contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            fullWidth("header") { ScreenHeader(title = "Recipes", overline = "From your fridge") }
            if (state.showsExpiringStrip) {
                fullWidth("strip") {
                    Column(Modifier.padding(top = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SectionHeading(title = "Use these first", tint = ZeroBiteTheme.colors.brandOrange, icon = Icons.Rounded.Schedule)
                        LazyRow(
                            Modifier.bleed(Spacing.gutter),
                            contentPadding = PaddingValues(horizontal = Spacing.gutter),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(state.expiringItems, key = { it.id }) { item ->
                                ExpiringChip(item, state.today, state.soonThresholdDays, onClick = { viewModel.showItem(item) })
                            }
                        }
                    }
                }
            }
            fullWidth("scopes") {
                ChipFilter(
                    options = RecipeScope.entries,
                    selected = state.scope,
                    onSelect = viewModel::setScope,
                    label = { it.title },
                    modifier = Modifier.bleed(Spacing.gutter).padding(vertical = Spacing.xs),
                )
            }
            if (!state.isLoading) results(state, viewModel)
        }
        BottomSearchBar(
            text = state.searchText,
            onTextChange = viewModel::setSearchText,
            placeholder = "Search recipes or ingredients",
            testTag = "recipes.search",
        )
    }
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

private fun LazyGridScope.results(state: RecipesUiState, viewModel: RecipesViewModel) {
    val matches = state.displayed
    if (matches.isEmpty()) {
        fullWidth("empty") {
            AppCard(padding = Spacing.lg) {
                Text(state.emptyTitle, style = ZeroBiteTheme.type.headline, color = ZeroBiteTheme.colors.textPrimary)
                Text(
                    state.emptyMessage,
                    style = ZeroBiteTheme.type.subheadline,
                    color = ZeroBiteTheme.colors.textSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        return
    }
    fullWidth("section") {
        SectionHeading(
            title = state.scope.sectionTitle,
            detail = if (matches.size == 1) "1 recipe" else "${matches.size} recipes",
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
    fullWidth("featured-${matches.first().recipe.id}") {
        FeaturedRecipeCard(matches.first(), onClick = { viewModel.showRecipe(matches.first()) })
    }
    items(matches.drop(1), key = { it.recipe.id }) { match ->
        RecipeCard(match, onClick = { viewModel.showRecipe(match) })
    }
}
```

- [ ] **Step 7: Run the tests and record**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests '*Recipes*Test' && ./gradlew :app:recordRoborazziDebug --tests '*RecipesScreenTest'; cd ..
```

Expected: 6 + 4 tests PASS; `recipes_light.png` and `recipes_dark.png` recorded.

- [ ] **Step 8: Commit**

```bash
git add android/app
git commit -m "Add the Android Recipes catalogue ranked against the fridge

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Recipe detail, "I cooked this", and recipes on item detail

**Files:**
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/screens/recipes/{RecipeDetailViewModel,RecipeDetailScreen,CookedViewModel,CookedScreen,RecipesTab}.kt`
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/screens/shared/DetailEntries.kt`
- Modify: `coordination/AppCoordinator.kt` (adds `SheetDestination.Cooked`), `coordination/InventoryCoordinator.kt` (adds `RecipeDetail`, `showRecipe`, `showCooked`), `coordination/RecipesCoordinator.kt` (adds `showCooked`)
- Replace: `screens/inventory/ItemDetailViewModel.kt`, `screens/inventory/InventoryTab.kt`
- Modify: `screens/inventory/ItemDetailScreen.kt` (adds the recipes section), `screens/root/MainScreen.kt` (Recipes tab), `screens/root/SheetHost.kt` (cooked sheet)
- Modify tests: `ItemDetailViewModelTest.kt` and `ItemDetailScreenTest.kt` (new constructor)
- Test: `screens/recipes/{RecipeDetailViewModelTest,CookedViewModelTest,RecipeDetailScreenTest,RecipesTabTest}.kt`

**Interfaces:**
- Consumes: Tasks 1–2; Plan 2's `ItemDetailScreen`, `SheetHost` and `MainScreen`.
- Produces:
  - Routes and sheets: `SheetDestination.Cooked(recipeId)`, `InventoryRoute.RecipeDetail(recipeId)`
  - `InventoryCoordinator.showRecipe(id)`, `.showCooked(recipeId)`; `RecipesCoordinator.showCooked(recipeId)`
  - `ItemDetailViewModel(itemId, inventory, settings, recipes, clock, onEdit, onShowRecipe, onDeleted)`, whose state gains `recipeMatches` (up to 5)
  - Recipe detail: `RecipeDetailUiState` with `canMarkCooked` and `matched(ingredient)`; `RecipeDetailViewModel(recipeId, inventory, settings, recipes, clock, onShowItem, onCooked)`; `RecipeDetailScreen(viewModel, onBack, modifier)`
  - Cooked sheet: `CookedUiState`; `CookedViewModel(recipeId, inventory, settings, recipes, clock, onDone)`; `CookedScreen(viewModel, modifier)`
  - Shared entries: `ItemDetailEntry(itemId, dependencies, onEdit, onShowRecipe, onDeleted, onBack)`, `RecipeDetailEntry(recipeId, dependencies, onShowItem, onCooked, onBack)`
  - Tabs: `RecipesTab(coordinator, listViewModel, dependencies, modifier)`; `InventoryTab` keeps its signature

- [ ] **Step 1: Write the failing tests**

`screens/recipes/RecipeDetailViewModelTest.kt`:

```kotlin
package com.mazesys.zerobite.screens.recipes

import com.mazesys.zerobite.core.model.Recipe
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeRecipes
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.recipeFridge
import com.mazesys.zerobite.testing.testRecipes
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecipeDetailViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private var shownItem: String? = null
    private var cooked: String? = null
    private val stock = Recipe("stock", "Stock", "", listOf(Recipe.Ingredient("salt"), Recipe.Ingredient("water")), listOf("Boil"), 5, 1, emptyList())

    private fun vm(id: String) = RecipeDetailViewModel(
        id, FakeInventoryRepository(recipeFridge()), FakeSettings(), FakeRecipes(testRecipes() + stock), TestClock.fixed(),
        onShowItem = { shownItem = it }, onCooked = { cooked = it },
    )

    @Test
    fun `matches the recipe against the fridge`() {
        val state = vm("omelette").state.value
        assertEquals("Omelette", state.recipe?.title)
        assertTrue(state.canMarkCooked)
        val eggs = state.recipe!!.ingredients.first { it.name == "eggs" }
        assertEquals("eggs", state.matched(eggs)?.item?.id)
        assertTrue(state.matched(state.recipe.ingredients.first { it.name == "salt" })!!.isStaple)
    }

    @Test
    fun `nothing to mark keeps the button disabled`() {
        assertFalse(vm("stock").state.value.canMarkCooked)
    }

    @Test
    fun `unknown recipe is not found`() {
        val state = vm("nope").state.value
        assertFalse(state.isLoading)
        assertNull(state.recipe)
    }

    @Test
    fun `taps go to the item and the cooked sheet`() {
        val model = vm("omelette")
        model.showItem(model.state.value.match!!.usedItems.first())
        model.cooked()
        assertEquals("eggs", shownItem)
        assertEquals("omelette", cooked)
    }
}
```

`screens/recipes/CookedViewModelTest.kt`:

```kotlin
package com.mazesys.zerobite.screens.recipes

import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeRecipes
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.recipeFridge
import com.mazesys.zerobite.testing.testRecipes
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class CookedViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val inventory = FakeInventoryRepository(recipeFridge())
    private var done = 0
    private fun vm() = CookedViewModel("omelette", inventory, FakeSettings(), FakeRecipes(testRecipes()), TestClock.fixed(), onDone = { done++ })

    @Test
    fun `every used item starts ticked`() {
        val state = vm().state.value
        assertEquals("Omelette", state.recipeTitle)
        assertEquals(listOf("eggs", "butter"), state.usableItems.map { it.id })
        assertEquals(setOf("eggs", "butter"), state.selectedIds)
        assertEquals("Update fridge", state.confirmTitle)
    }

    @Test
    fun `confirm marks the ticked items used`() {
        val model = vm()
        model.toggle(model.state.value.usableItems.first { it.id == "butter" })
        model.confirm()
        assertEquals(TestClock.now, inventory.current.first { it.id == "eggs" }.consumedAt)
        assertNull(inventory.current.first { it.id == "butter" }.consumedAt)
        assertEquals(1, done)
    }

    @Test
    fun `nothing ticked just closes`() {
        val model = vm()
        model.state.value.usableItems.forEach(model::toggle)
        assertEquals("Done", model.state.value.confirmTitle)
        model.confirm()
        assertEquals(emptyList(), inventory.current.filter { it.isConsumed })
        assertEquals(1, done)
    }

    @Test
    fun `items used up meanwhile are skipped`() = runBlocking {
        val model = vm()
        val earlier = Instant.parse("2026-09-06T10:00:00Z")
        inventory.markConsumed(listOf("eggs"), earlier) // e.g. "Used it" on the Fridge tab meanwhile
        model.confirm()
        assertEquals(earlier, inventory.current.first { it.id == "eggs" }.consumedAt)
        assertEquals(TestClock.now, inventory.current.first { it.id == "butter" }.consumedAt)
        assertEquals(1, done)
    }
}
```

`screens/recipes/RecipeDetailScreenTest.kt`:

```kotlin
package com.mazesys.zerobite.screens.recipes

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeRecipes
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.recipeFridge
import com.mazesys.zerobite.testing.testRecipes
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
class RecipeDetailScreenTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private var shown: String? = null
    private var cooked: String? = null

    private fun show(id: String = "omelette", dark: Boolean = false) {
        val vm = RecipeDetailViewModel(
            id, FakeInventoryRepository(recipeFridge()), FakeSettings(), FakeRecipes(testRecipes()), TestClock.fixed(),
            onShowItem = { shown = it }, onCooked = { cooked = it },
        )
        compose.setContent { ZeroBiteTheme(darkTheme = dark) { RecipeDetailScreen(vm, onBack = {}) } }
    }

    @Test
    fun `ingredient rows explain where each ingredient comes from`() {
        show()
        compose.onNodeWithText("You have Eggs · Tomorrow").assertExists()
        compose.onNodeWithText("Store cupboard").assertExists()
        compose.onNodeWithText("Optional — skip it").assertExists()
        compose.onNodeWithText("You have everything you need.").assertExists()
        compose.onNodeWithText("You have Eggs · Tomorrow").performClick()
        assertEquals("eggs", shown)
    }

    @Test
    fun `missing ingredients are listed`() {
        show("stirfry")
        compose.onNodeWithText("Missing bell pepper.").assertExists()
        compose.onNodeWithText("Not in your fridge").assertExists()
    }

    @Test
    fun `cooked button opens the sheet`() {
        show()
        compose.onNodeWithTag("recipe.cooked").performScrollTo().assertIsEnabled().performClick()
        assertEquals("omelette", cooked)
    }

    @Test
    fun `unknown recipe shows not found`() {
        show("nope")
        compose.onNodeWithText("Recipe not found").assertExists()
    }

    @Test
    fun `recipe detail light`() {
        show()
        compose.onRoot().captureRoboImage("src/test/screenshots/recipe_detail_light.png")
    }

    @Test
    fun `recipe detail dark`() {
        show(dark = true)
        compose.onRoot().captureRoboImage("src/test/screenshots/recipe_detail_dark.png")
    }
}
```

`screens/recipes/RecipesTabTest.kt`:

```kotlin
package com.mazesys.zerobite.screens.recipes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.coordination.RecipesRoute
import com.mazesys.zerobite.coordination.SheetDestination
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeRecipes
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.recipeFridge
import com.mazesys.zerobite.testing.testDependencies
import com.mazesys.zerobite.testing.testRecipes
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class RecipesTabTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `recipe, then its ingredient's item, then back, then cooked`() {
        val dependencies = testDependencies(inventory = FakeInventoryRepository(recipeFridge()), recipes = FakeRecipes(testRecipes()))
        val app = AppCoordinator { TestClock.today }
        val list = RecipesViewModel(dependencies.inventory, dependencies.settings, dependencies.recipes, dependencies.clock, app.recipes)
        compose.setContent { ZeroBiteTheme { RecipesTab(app.recipes, list, dependencies) } }

        compose.onNodeWithText("Omelette").performClick()
        compose.onNodeWithText("Ingredients").assertIsDisplayed()
        compose.onNodeWithText("You have Eggs · Tomorrow").performClick()
        compose.onNodeWithText("Details").assertIsDisplayed()
        assertEquals(RecipesRoute.ItemDetail("eggs"), app.recipes.backStack.last())

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("recipe.cooked").performScrollTo().performClick()
        assertEquals(SheetDestination.Cooked("omelette"), app.presentedSheet?.destination)
    }
}
```

Add to `ItemDetailViewModelTest.kt`:

```kotlin
    @Test
    fun `recipes using the item are listed`() {
        val fridge = FakeInventoryRepository(recipeFridge())
        val vm = ItemDetailViewModel(
            "eggs", fridge, FakeSettings(), FakeRecipes(testRecipes()), TestClock.fixed(),
            onEdit = {}, onShowRecipe = {}, onDeleted = {},
        )
        assertEquals(listOf("omelette", "pancakes"), vm.state.value.recipeMatches.map { it.recipe.id })
    }
```

with imports `com.mazesys.zerobite.testing.FakeRecipes`, `com.mazesys.zerobite.testing.recipeFridge` and `com.mazesys.zerobite.testing.testRecipes`. In the same file, change the existing builder to the new constructor:

```kotlin
    private fun viewModel(id: String = milk.id) =
        ItemDetailViewModel(id, inventory, FakeSettings(), FakeRecipes(), TestClock.fixed(), onEdit = { edited = it }, onShowRecipe = {}, onDeleted = { deleted++ })
```

In `ItemDetailScreenTest.kt`, change the view model line in `show()` to:

```kotlin
        val vm = ItemDetailViewModel(milk.id, inventory, FakeSettings(), FakeRecipes(), TestClock.fixed(), onEdit = {}, onShowRecipe = {}, onDeleted = { deleted++ })
```

and import `com.mazesys.zerobite.testing.FakeRecipes`.

- [ ] **Step 2: Run to see them fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests '*Recipe*Test' --tests '*Cooked*Test' --tests '*ItemDetail*Test'; cd ..`
Expected: compilation FAILS: unresolved `RecipeDetailViewModel`, `CookedViewModel`, `RecipesTab`, `SheetDestination.Cooked`, and the new `ItemDetailViewModel` parameters.

- [ ] **Step 3: Extend the coordinators**

In `coordination/AppCoordinator.kt`, add to `SheetDestination`:

```kotlin
    data class Cooked(val recipeId: String) : SheetDestination
```

In `coordination/InventoryCoordinator.kt`, add to `InventoryRoute`:

```kotlin
    @Serializable
    data class RecipeDetail(val recipeId: String) : InventoryRoute
```

and to `InventoryCoordinator`:

```kotlin
    fun showRecipe(id: String) {
        backStack.add(InventoryRoute.RecipeDetail(id))
    }

    fun showCooked(recipeId: String) = parent.presentSheet(SheetDestination.Cooked(recipeId))
```

In `coordination/RecipesCoordinator.kt`, add:

```kotlin
    fun showCooked(recipeId: String) = parent.presentSheet(SheetDestination.Cooked(recipeId))
```

- [ ] **Step 4: Replace `screens/inventory/ItemDetailViewModel.kt`** (adds recipes using the item, as in `ItemDetailViewModel.swift`)

```kotlin
package com.mazesys.zerobite.screens.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazesys.zerobite.core.inventory.InventoryRepository
import com.mazesys.zerobite.core.model.ExpiryStatus
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.recipes.RecipeMatch
import com.mazesys.zerobite.core.recipes.RecipeMatcher
import com.mazesys.zerobite.core.recipes.RecipeRepository
import com.mazesys.zerobite.core.time.AppClock
import com.mazesys.zerobite.data.SettingsRepository
import com.mazesys.zerobite.ui.components.shelfLifeProgress
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
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
    /** Recipes that would use this item, best first (at most five). */
    val recipeMatches: List<RecipeMatch> = emptyList(),
) {
    val status: ExpiryStatus? get() = item?.status(today, soonThresholdDays)
}

class ItemDetailViewModel(
    val itemId: String,
    private val inventory: InventoryRepository,
    settings: SettingsRepository,
    recipes: RecipeRepository,
    private val clock: AppClock,
    private val onEdit: (String) -> Unit,
    private val onShowRecipe: (String) -> Unit,
    private val onDeleted: () -> Unit,
) : ViewModel() {

    private val catalogue = flow { emit(recipes.recipes()) }

    val state: StateFlow<ItemDetailUiState> =
        combine(inventory.items, settings.state, catalogue) { items, s, all ->
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

    fun showRecipe(match: RecipeMatch) = onShowRecipe(match.recipe.id)
}
```

- [ ] **Step 5: Add the recipes section to `ItemDetailScreen.kt`**

In `DetailContent`, insert before the actions column (the line `        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {` followed by `            if (ui.state == DetailState.USED) {`):

```kotlin
        if (ui.state == DetailState.ACTIVE) RecipesUsing(item, ui, viewModel)
```

and add at the end of the file:

```kotlin
@Composable
private fun RecipesUsing(item: FoodItem, ui: ItemDetailUiState, viewModel: ItemDetailViewModel) {
    val rows: List<@Composable () -> Unit> = if (ui.recipeMatches.isEmpty()) {
        listOf {
            Text("No recipe in the catalogue uses this yet.", style = ZeroBiteTheme.type.subheadline, color = ZeroBiteTheme.colors.textSecondary)
        }
    } else {
        ui.recipeMatches.map { match ->
            { RecipeRow(match, Modifier.clickable(role = Role.Button) { viewModel.showRecipe(match) }) }
        }
    }
    CardSection(title = "Recipes using ${item.name.lowercase()}", rows = rows)
}
```

with imports `androidx.compose.foundation.clickable`, `androidx.compose.ui.semantics.Role` and `com.mazesys.zerobite.ui.components.RecipeRow`.

- [ ] **Step 6: Implement recipe detail** (follows `RecipeDetailView.swift`)

`screens/recipes/RecipeDetailViewModel.kt`:

```kotlin
package com.mazesys.zerobite.screens.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazesys.zerobite.core.inventory.InventoryRepository
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.Recipe
import com.mazesys.zerobite.core.recipes.MatchedIngredient
import com.mazesys.zerobite.core.recipes.RecipeMatch
import com.mazesys.zerobite.core.recipes.RecipeMatcher
import com.mazesys.zerobite.core.recipes.RecipeRepository
import com.mazesys.zerobite.core.time.AppClock
import com.mazesys.zerobite.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

data class RecipeDetailUiState(
    val today: LocalDate,
    val isLoading: Boolean = true,
    val recipe: Recipe? = null,
    val match: RecipeMatch? = null,
    val soonThresholdDays: Int = 3,
) {
    /** Only useful when at least one fridge item would be consumed. */
    val canMarkCooked: Boolean get() = match?.usedItems?.isNotEmpty() == true

    fun matched(ingredient: Recipe.Ingredient): MatchedIngredient? = match?.matched?.firstOrNull { it.ingredient == ingredient }
}

class RecipeDetailViewModel(
    val recipeId: String,
    inventory: InventoryRepository,
    settings: SettingsRepository,
    recipes: RecipeRepository,
    private val clock: AppClock,
    private val onShowItem: (String) -> Unit,
    private val onCooked: (String) -> Unit,
) : ViewModel() {

    private val catalogue = flow { emit(recipes.recipes()) }

    val state: StateFlow<RecipeDetailUiState> =
        combine(inventory.items, settings.state, catalogue) { items, s, all ->
            val today = clock.today()
            val recipe = all.firstOrNull { it.id == recipeId }
            RecipeDetailUiState(
                today = today,
                isLoading = false,
                recipe = recipe,
                match = recipe?.let { RecipeMatcher(today, s.soonThresholdDays).match(it, items.filterNot { item -> item.isConsumed }) },
                soonThresholdDays = s.soonThresholdDays,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, RecipeDetailUiState(today = clock.today()))

    fun showItem(item: FoodItem) = onShowItem(item.id)

    fun cooked() = onCooked(recipeId)
}
```

`screens/recipes/RecipeDetailScreen.kt`:

```kotlin
package com.mazesys.zerobite.screens.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Countertops
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.Recipe
import com.mazesys.zerobite.core.recipes.MatchedIngredient
import com.mazesys.zerobite.core.recipes.RecipeMatch
import com.mazesys.zerobite.ui.components.AppButton
import com.mazesys.zerobite.ui.components.AppCard
import com.mazesys.zerobite.ui.components.CardSection
import com.mazesys.zerobite.ui.components.DetailTitle
import com.mazesys.zerobite.ui.components.EmptyStateView
import com.mazesys.zerobite.ui.components.RecipeArtwork
import com.mazesys.zerobite.ui.components.Tag
import com.mazesys.zerobite.ui.components.TopBar
import com.mazesys.zerobite.ui.components.TopBarLeading
import com.mazesys.zerobite.ui.components.accent
import com.mazesys.zerobite.ui.components.bleed
import com.mazesys.zerobite.ui.components.matchPercent
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.medium
import com.mazesys.zerobite.ui.theme.semibold
import com.mazesys.zerobite.ui.theme.tint
import kotlinx.datetime.LocalDate

@Composable
fun RecipeDetailScreen(viewModel: RecipeDetailViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize().background(ZeroBiteTheme.colors.backgroundPrimary)) {
        TopBar(TopBarLeading.Back(onBack))
        val recipe = ui.recipe
        val match = ui.match
        when {
            ui.isLoading -> Unit
            recipe == null || match == null -> EmptyStateView(
                title = "Recipe not found",
                message = "It may have been removed from the catalogue.",
                icon = Icons.Rounded.QuestionMark,
                modifier = Modifier.padding(Spacing.gutter),
            )
            else -> RecipeContent(recipe, match, ui, viewModel)
        }
    }
}

@Composable
private fun RecipeContent(recipe: Recipe, match: RecipeMatch, ui: RecipeDetailUiState, viewModel: RecipeDetailViewModel) {
    val colors = ZeroBiteTheme.colors
    val accent = match.accent(colors)
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        RecipeArtwork(recipe, accent, 150.dp, Modifier.clip(RoundedCornerShape(Radius.card)))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            DetailTitle(title = recipe.title, overline = "${match.matchPercent}% in your fridge", overlineTint = accent)
            Text(recipe.summary, style = ZeroBiteTheme.type.body, color = colors.textSecondary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Stat("${recipe.prepMinutes}", "min", Icons.Rounded.Schedule, Modifier.weight(1f))
            Stat("${recipe.servings}", if (recipe.servings == 1) "serving" else "servings", Icons.Rounded.People, Modifier.weight(1f))
            Stat(
                "${match.expiringItems.size}", "expiring", Icons.Rounded.Schedule, Modifier.weight(1f),
                tint = if (match.expiringItems.isEmpty()) null else colors.brandOrange,
            )
        }
        StatusBanner(match)
        if (recipe.tags.isNotEmpty()) {
            LazyRow(
                Modifier.bleed(Spacing.gutter),
                contentPadding = PaddingValues(horizontal = Spacing.gutter),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(recipe.tags) { Tag(it) }
            }
        }
        CardSection(
            title = "Ingredients",
            rows = recipe.ingredients.map { ingredient ->
                { IngredientRow(ingredient, ui.matched(ingredient), ui.today, ui.soonThresholdDays, onTapItem = viewModel::showItem) }
            },
        )
        CardSection(
            title = "Method",
            rows = recipe.steps.mapIndexed { index, step ->
                {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Text(
                            (index + 1).toString().padStart(2, '0'),
                            style = ZeroBiteTheme.type.subheadline.semibold(),
                            color = colors.brandOrange,
                            modifier = Modifier.width(24.dp),
                        )
                        Text(step, style = ZeroBiteTheme.type.subheadline, color = colors.textPrimary)
                    }
                }
            },
        )
        AppButton(
            "I cooked this — update my fridge",
            viewModel::cooked,
            Modifier.testTag("recipe.cooked"),
            icon = Icons.Rounded.Verified,
            enabled = ui.canMarkCooked,
        )
    }
}

@Composable
private fun Stat(value: String, unit: String, icon: ImageVector, modifier: Modifier, tint: Color? = null) {
    val colors = ZeroBiteTheme.colors
    AppCard(modifier, padding = 10.dp) {
        Icon(icon, contentDescription = null, tint = tint ?: colors.textSecondary, modifier = Modifier.size(15.dp))
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, style = ZeroBiteTheme.type.title3.semibold(), color = tint ?: colors.textPrimary, modifier = Modifier.alignByBaseline())
            Text(unit, style = ZeroBiteTheme.type.caption, color = colors.textSecondary, modifier = Modifier.alignByBaseline())
        }
    }
}

@Composable
private fun StatusBanner(match: RecipeMatch) {
    val colors = ZeroBiteTheme.colors
    val tint = if (match.canMake) colors.statusFresh else colors.brandYellow
    Row(
        Modifier.fillMaxWidth().background(tint.copy(alpha = 0.12f), RoundedCornerShape(Radius.card)).padding(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(if (match.canMake) Icons.Rounded.CheckCircle else Icons.Rounded.ShoppingCart, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(
            if (match.canMake) "You have everything you need." else "Missing ${match.missing.joinToString(", ") { it.name }}.",
            style = ZeroBiteTheme.type.subheadline.medium(),
            color = tint,
        )
    }
}

@Composable
private fun IngredientRow(
    ingredient: Recipe.Ingredient,
    matched: MatchedIngredient?,
    today: LocalDate,
    soonThresholdDays: Int,
    onTapItem: (FoodItem) -> Unit,
) {
    val colors = ZeroBiteTheme.colors
    val item = matched?.item
    val status = item?.status(today, soonThresholdDays)
    val icon = when {
        status != null -> if (status.needsAttention) Icons.Rounded.Error else Icons.Rounded.CheckCircle
        matched?.isStaple == true -> Icons.Rounded.Countertops
        else -> Icons.Rounded.RadioButtonUnchecked
    }
    val tint = when {
        status != null -> if (status.needsAttention) status.tint(colors) else colors.statusFresh
        else -> colors.textSecondary
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(top = 1.dp).size(18.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (ingredient.amount.isEmpty()) ingredient.name else "${ingredient.amount} ${ingredient.name}",
                style = ZeroBiteTheme.type.subheadline.medium(),
                color = colors.textPrimary,
            )
            val caption = ZeroBiteTheme.type.caption
            when {
                item != null -> Text(
                    "You have ${item.name} · ${item.shortExpiryLabel(today)}",
                    style = caption,
                    color = tint,
                    modifier = Modifier.clickable(role = Role.Button) { onTapItem(item) },
                )
                matched?.isStaple == true -> Text("Store cupboard", style = caption, color = colors.textSecondary)
                ingredient.optional -> Text("Optional — skip it", style = caption, color = colors.textSecondary)
                else -> Text("Not in your fridge", style = caption, color = colors.textSecondary)
            }
        }
    }
}
```

- [ ] **Step 7: Implement the cooked sheet** (follows `CookedView.swift`)

`screens/recipes/CookedViewModel.kt`:

```kotlin
package com.mazesys.zerobite.screens.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazesys.zerobite.core.inventory.InventoryRepository
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.recipes.RecipeMatcher
import com.mazesys.zerobite.core.recipes.RecipeRepository
import com.mazesys.zerobite.core.time.AppClock
import com.mazesys.zerobite.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CookedUiState(
    val isLoaded: Boolean = false,
    val recipeTitle: String? = null,
    val usableItems: List<FoodItem> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
) {
    val confirmTitle: String get() = if (selectedIds.isEmpty()) "Done" else "Update fridge"
}

/** After cooking: choose which matched fridge items were used up. */
class CookedViewModel(
    recipeId: String,
    private val inventory: InventoryRepository,
    settings: SettingsRepository,
    recipes: RecipeRepository,
    private val clock: AppClock,
    private val onDone: () -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(CookedUiState())
    val state: StateFlow<CookedUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val recipe = recipes.recipes().firstOrNull { it.id == recipeId }
            val active = inventory.items.first().filterNot { it.isConsumed }
            val usable = recipe
                ?.let { RecipeMatcher(clock.today(), settings.current().soonThresholdDays).match(it, active).usedItems }
                .orEmpty()
            _state.value = CookedUiState(true, recipe?.title, usable, usable.map { it.id }.toSet())
        }
    }

    fun toggle(item: FoodItem) {
        _state.update { s -> s.copy(selectedIds = if (item.id in s.selectedIds) s.selectedIds - item.id else s.selectedIds + item.id) }
    }

    fun confirm() {
        val selected = _state.value.selectedIds
        viewModelScope.launch {
            // Skip anything used up elsewhere since the sheet opened, so its date isn't overwritten.
            val stillActive = inventory.items.first().filterNot { it.isConsumed }.map { it.id }.toSet()
            val ids = selected.filter { it in stillActive }
            if (ids.isNotEmpty()) inventory.markConsumed(ids, clock.now())
            onDone()
        }
    }

    fun cancel() = onDone()
}
```

`screens/recipes/CookedScreen.kt`:

```kotlin
package com.mazesys.zerobite.screens.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazesys.zerobite.ui.components.AppButton
import com.mazesys.zerobite.ui.components.CardSection
import com.mazesys.zerobite.ui.components.DetailTitle
import com.mazesys.zerobite.ui.components.TopBar
import com.mazesys.zerobite.ui.components.TopBarLeading
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.medium

@Composable
fun CookedScreen(viewModel: CookedViewModel, modifier: Modifier = Modifier) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val colors = ZeroBiteTheme.colors
    Column(modifier.fillMaxSize().background(colors.backgroundPrimary)) {
        TopBar(TopBarLeading.Close(viewModel::cancel))
        if (!ui.isLoaded) return@Column
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            DetailTitle(title = "Nice one! What did you use up?", overline = ui.recipeTitle ?: "Cooked")
            CardSection(
                footer = "Ticked items are marked as used. Anything unticked stays in your fridge — edit its quantity if you only used some.",
                rows = ui.usableItems.map { item ->
                    {
                        val selected = item.id in ui.selectedIds
                        Row(
                            Modifier.fillMaxWidth().toggleable(value = selected, role = Role.Checkbox, onValueChange = { viewModel.toggle(item) }),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            Icon(
                                if (selected) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                                contentDescription = null,
                                tint = if (selected) colors.brandOrange else colors.textMuted,
                                modifier = Modifier.size(22.dp),
                            )
                            Text(item.name, style = ZeroBiteTheme.type.subheadline.medium(), color = colors.textPrimary, modifier = Modifier.weight(1f))
                            Text(item.quantityText, style = ZeroBiteTheme.type.caption, color = colors.textSecondary)
                        }
                    }
                },
            )
        }
        AppButton(
            ui.confirmTitle,
            viewModel::confirm,
            Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm).testTag("cooked.confirm"),
        )
    }
}
```

- [ ] **Step 8: Shared detail entries and both tabs' navigation**

`screens/shared/DetailEntries.kt`:

```kotlin
package com.mazesys.zerobite.screens.shared

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mazesys.zerobite.AppDependencies
import com.mazesys.zerobite.screens.inventory.ItemDetailScreen
import com.mazesys.zerobite.screens.inventory.ItemDetailViewModel
import com.mazesys.zerobite.screens.recipes.RecipeDetailScreen
import com.mazesys.zerobite.screens.recipes.RecipeDetailViewModel

/** Item detail as a navigation entry; used by both the Fridge and Recipes tabs. */
@Composable
fun ItemDetailEntry(
    itemId: String,
    dependencies: AppDependencies,
    onEdit: (String) -> Unit,
    onShowRecipe: (String) -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
) {
    val detail = viewModel(key = "item-$itemId") {
        ItemDetailViewModel(
            itemId, dependencies.inventory, dependencies.settings, dependencies.recipes, dependencies.clock,
            onEdit = onEdit, onShowRecipe = onShowRecipe, onDeleted = onDeleted,
        )
    }
    ItemDetailScreen(detail, onBack = onBack)
}

/** Recipe detail as a navigation entry; used by both tabs. */
@Composable
fun RecipeDetailEntry(
    recipeId: String,
    dependencies: AppDependencies,
    onShowItem: (String) -> Unit,
    onCooked: (String) -> Unit,
    onBack: () -> Unit,
) {
    val detail = viewModel(key = "recipe-$recipeId") {
        RecipeDetailViewModel(
            recipeId, dependencies.inventory, dependencies.settings, dependencies.recipes, dependencies.clock,
            onShowItem = onShowItem, onCooked = onCooked,
        )
    }
    RecipeDetailScreen(detail, onBack = onBack)
}
```

Replace `screens/inventory/InventoryTab.kt`:

```kotlin
package com.mazesys.zerobite.screens.inventory

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.mazesys.zerobite.AppDependencies
import com.mazesys.zerobite.coordination.InventoryCoordinator
import com.mazesys.zerobite.coordination.InventoryRoute
import com.mazesys.zerobite.screens.shared.ItemDetailEntry
import com.mazesys.zerobite.screens.shared.RecipeDetailEntry

/** The Fridge tab: list → item detail → recipe. The list's view model is owned by the caller. */
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
                ItemDetailEntry(
                    route.itemId, dependencies,
                    onEdit = coordinator::showEditItem, onShowRecipe = coordinator::showRecipe,
                    onDeleted = coordinator::pop, onBack = coordinator::pop,
                )
            }
            entry<InventoryRoute.RecipeDetail> { route ->
                RecipeDetailEntry(
                    route.recipeId, dependencies,
                    onShowItem = coordinator::showItem, onCooked = coordinator::showCooked, onBack = coordinator::pop,
                )
            }
        },
    )
}
```

`screens/recipes/RecipesTab.kt`:

```kotlin
package com.mazesys.zerobite.screens.recipes

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.mazesys.zerobite.AppDependencies
import com.mazesys.zerobite.coordination.RecipesCoordinator
import com.mazesys.zerobite.coordination.RecipesRoute
import com.mazesys.zerobite.screens.shared.ItemDetailEntry
import com.mazesys.zerobite.screens.shared.RecipeDetailEntry

/** The Recipes tab: catalogue → recipe → item. */
@Composable
fun RecipesTab(
    coordinator: RecipesCoordinator,
    listViewModel: RecipesViewModel,
    dependencies: AppDependencies,
    modifier: Modifier = Modifier,
) {
    NavDisplay(
        backStack = coordinator.backStack,
        modifier = modifier,
        onBack = { coordinator.pop() },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<RecipesRoute.Catalogue> { RecipesScreen(listViewModel) }
            entry<RecipesRoute.RecipeDetail> { route ->
                RecipeDetailEntry(
                    route.recipeId, dependencies,
                    onShowItem = coordinator::showItem, onCooked = coordinator::showCooked, onBack = coordinator::pop,
                )
            }
            entry<RecipesRoute.ItemDetail> { route ->
                ItemDetailEntry(
                    route.itemId, dependencies,
                    onEdit = coordinator::showEditItem, onShowRecipe = coordinator::showRecipe,
                    onDeleted = coordinator::pop, onBack = coordinator::pop,
                )
            }
        },
    )
}
```

- [ ] **Step 9: Wire the tab and the sheet**

In `screens/root/MainScreen.kt`:
- Below `inventoryList`, add:
  ```kotlin
      val recipesList = viewModel(key = "recipes") {
          RecipesViewModel(dependencies.inventory, dependencies.settings, dependencies.recipes, dependencies.clock, coordinator.recipes)
      }
  ```
- Replace `AppTab.RECIPES -> PlaceholderTab(overline = "From your fridge", title = "Recipes")` with `AppTab.RECIPES -> RecipesTab(coordinator.recipes, recipesList, dependencies)`.
- Import `com.mazesys.zerobite.screens.recipes.RecipesTab` and `com.mazesys.zerobite.screens.recipes.RecipesViewModel`.

In `screens/root/SheetHost.kt`, add a branch to the `when`:

```kotlin
                    is SheetDestination.Cooked -> CookedSheet(destination.recipeId, coordinator, dependencies)
```

and at the end of the file:

```kotlin
@Composable
private fun CookedSheet(recipeId: String, coordinator: AppCoordinator, dependencies: AppDependencies) {
    val cooked = viewModel {
        CookedViewModel(recipeId, dependencies.inventory, dependencies.settings, dependencies.recipes, dependencies.clock, onDone = { coordinator.dismissSheet() })
    }
    CookedScreen(cooked)
}
```

with imports `com.mazesys.zerobite.screens.recipes.CookedScreen` and `com.mazesys.zerobite.screens.recipes.CookedViewModel`.

- [ ] **Step 10: Run the whole suite and record**

```bash
cd android && ./gradlew :app:testDebugUnitTest && ./gradlew :app:recordRoborazziDebug --tests '*RecipeDetailScreenTest'; cd ..
```

Expected: every test PASSES; `recipe_detail_light.png` and `recipe_detail_dark.png` recorded. `RootScreenTest.tabs switch between screens` must first be updated: the Recipes tab is real now, so replace its `compose.onNodeWithText("Arrives in the next build.").assertIsDisplayed()` with `compose.onNodeWithText("Nothing to cook yet").assertIsDisplayed()` (`testDependencies` has an empty catalogue).

- [ ] **Step 11: Commit**

```bash
git add android/app
git commit -m "Add Android recipe detail, the cooked sheet and recipes on item detail

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Reminders: alarms, notifications and the sync loop

**Files:**
- Modify: `android/gradle/libs.versions.toml` and `android/app/build.gradle.kts` (add `androidx.core:core-ktx`)
- Modify: `android/app/src/main/AndroidManifest.xml` (POST_NOTIFICATIONS, ReminderReceiver)
- Create: `android/app/src/main/res/drawable/ic_notification.xml`
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/reminders/{Notifications,NotificationAccess,Notifier,ReminderScheduler,ReminderReceiver,ReminderSync}.kt`
- Modify: `AppDependencies.kt`, `ZeroBiteApplication.kt`, `testing/Fakes.kt`
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/reminders/{AlarmReminderSchedulerTest,ReminderReceiverTest,NotificationAccessTest,ReminderSyncTest}.kt`

**Interfaces:**
- Consumes: `ExpiryNotificationPlanner`, `PlannedNotification`, `ExpiringItemSummary` (`:core`); `SettingsRepository`, `InventoryRepository`, `AppClock`.
- Produces:
  - `object Notifications { CHANNEL_EXPIRY; EXTRA_ITEM_ID; createChannels(context); canPost(context); post(context, tag, title, body, itemId) }`
  - `enum class NotificationPermission { GRANTED, DENIED, NOT_ASKED }`, `interface NotificationAccess { fun permission(): NotificationPermission; fun markAsked() }`, `class AndroidNotificationAccess(context, prefs)`
  - `interface Notifier { fun sendTest() }`, `class AndroidNotifier(context)`
  - `interface ReminderScheduler { fun reschedule(planned: List<PlannedNotification>) }`, `class AlarmReminderScheduler(context, prefs)`
  - `class ReminderReceiver : BroadcastReceiver`
  - `class ReminderSync(inventory, settings, clock, access, scheduler)` with `start(scope): Job` and `suspend resyncNow()`
  - `AppDependencies(clock, settings, inventory, recipes, ids, appScope, notifications, scheduler, notifier)` plus a derived `val reminders: ReminderSync`
  - Test fakes `FakeAccess`, `FakeScheduler`, `FakeNotifier`; `testDependencies(...)` gains `access`, `scheduler` and `notifier` parameters

- [ ] **Step 1: Add core-ktx**

`libs.versions.toml`: under `[versions]` add `core-ktx = "1.19.1"`; under `[libraries]` add `core-ktx = { module = "androidx.core:core-ktx", version.ref = "core-ktx" }`. In `app/build.gradle.kts` add `implementation(libs.core.ktx)` after `implementation(libs.core.splashscreen)`.

- [ ] **Step 2: Write the failing tests**

Append the fakes to `testing/Fakes.kt`:

```kotlin
class FakeAccess(var current: NotificationPermission = NotificationPermission.GRANTED) : NotificationAccess {
    var asked = false
    override fun permission() = current
    override fun markAsked() {
        asked = true
    }
}

class FakeScheduler : ReminderScheduler {
    var last: List<PlannedNotification> = emptyList()
    var calls = 0
    override fun reschedule(planned: List<PlannedNotification>) {
        last = planned
        calls++
    }
}

class FakeNotifier : Notifier {
    var tests = 0
    override fun sendTest() {
        tests++
    }
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
) = AppDependencies(clock, settings, inventory, recipes, ids, CoroutineScope(Dispatchers.Unconfined), access, scheduler, notifier)
```

with imports `com.mazesys.zerobite.core.notifications.PlannedNotification`, `com.mazesys.zerobite.reminders.NotificationAccess`, `com.mazesys.zerobite.reminders.NotificationPermission`, `com.mazesys.zerobite.reminders.Notifier` and `com.mazesys.zerobite.reminders.ReminderScheduler`.

`reminders/AlarmReminderSchedulerTest.kt`:

```kotlin
package com.mazesys.zerobite.reminders

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mazesys.zerobite.core.notifications.PlannedNotification
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class AlarmReminderSchedulerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val scheduler = AlarmReminderScheduler(context, context.getSharedPreferences("reminders-test", Context.MODE_PRIVATE))

    private fun planned(id: String, at: String) =
        PlannedNotification("zerobite.item.$id", id.substringBefore('.'), "Milk expires today", "Use it today or freeze it.", Instant.parse(at))

    @Test
    fun `schedules one inexact alarm per reminder at its time`() {
        scheduler.reschedule(listOf(planned("milk.today", "2026-09-12T08:30:00Z"), planned("milk.expired", "2026-09-13T08:30:00Z")))
        val times = alarms.scheduledAlarms.map { it.triggerAtTime }.sorted()
        assertEquals(listOf(Instant.parse("2026-09-12T08:30:00Z"), Instant.parse("2026-09-13T08:30:00Z")).map { it.toEpochMilliseconds() }, times)
        assertEquals(AlarmManager.RTC_WAKEUP, alarms.scheduledAlarms.first().type)
    }

    @Test
    fun `a new plan replaces the old one`() {
        scheduler.reschedule(listOf(planned("milk.today", "2026-09-12T08:30:00Z"), planned("milk.expired", "2026-09-13T08:30:00Z")))
        scheduler.reschedule(listOf(planned("eggs.soon", "2026-09-10T08:30:00Z")))
        assertEquals(listOf(Instant.parse("2026-09-10T08:30:00Z").toEpochMilliseconds()), alarms.scheduledAlarms.map { it.triggerAtTime })
        scheduler.reschedule(emptyList())
        assertEquals(0, alarms.scheduledAlarms.size)
    }

    @Test
    fun `stale alarms are cancelled even after the process restarts`() {
        scheduler.reschedule(listOf(planned("milk.today", "2026-09-12T08:30:00Z")))
        val restarted = AlarmReminderScheduler(context, context.getSharedPreferences("reminders-test", Context.MODE_PRIVATE))
        restarted.reschedule(emptyList())
        assertEquals(0, alarms.scheduledAlarms.size)
    }
}
```

`reminders/ReminderReceiverTest.kt`:

```kotlin
package com.mazesys.zerobite.reminders

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ReminderReceiverTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val manager = shadowOf(app.getSystemService(NotificationManager::class.java))

    @Before
    fun channels() = Notifications.createChannels(app)

    private fun fire() = ReminderReceiver().onReceive(
        app,
        Intent(app, ReminderReceiver::class.java)
            .setData(Uri.parse("zerobite://reminder/zerobite.item.milk.today"))
            .putExtra(ReminderReceiver.EXTRA_TITLE, "Milk expires today")
            .putExtra(ReminderReceiver.EXTRA_BODY, "Use it today or freeze it.")
            .putExtra(Notifications.EXTRA_ITEM_ID, "milk"),
    )

    @Test
    fun `posts the reminder on the expiry channel`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        fire()
        val posted = manager.allNotifications.single()
        assertEquals("Milk expires today", posted.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Use it today or freeze it.", posted.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(Notifications.CHANNEL_EXPIRY, posted.channelId)
        val tap = shadowOf(posted.contentIntent).savedIntent
        assertEquals("milk", tap.getStringExtra(Notifications.EXTRA_ITEM_ID))
    }

    @Test
    fun `stays silent without notification permission`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        fire()
        assertEquals(0, manager.allNotifications.size)
    }
}
```

`reminders/NotificationAccessTest.kt`:

```kotlin
package com.mazesys.zerobite.reminders

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class NotificationAccessTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val access = AndroidNotificationAccess(app, app.getSharedPreferences("access-test", Context.MODE_PRIVATE))

    @Test
    fun `not asked, then denied, then granted`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertEquals(NotificationPermission.NOT_ASKED, access.permission())
        access.markAsked()
        assertEquals(NotificationPermission.DENIED, access.permission())
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertEquals(NotificationPermission.GRANTED, access.permission())
    }
}
```

`reminders/ReminderSyncTest.kt`:

```kotlin
package com.mazesys.zerobite.reminders

import com.mazesys.zerobite.data.SettingsSnapshot
import com.mazesys.zerobite.testing.FakeAccess
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeScheduler
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.recipeFridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderSyncTest {

    private val inventory = FakeInventoryRepository(recipeFridge())
    private val settings = FakeSettings(SettingsSnapshot(notificationsEnabled = true))
    private val access = FakeAccess(NotificationPermission.GRANTED)
    private val scheduler = FakeScheduler()
    private val sync = ReminderSync(inventory, settings, TestClock.fixed(), access, scheduler)

    @Test
    fun `plans reminders for every active item and follows changes`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        sync.start(scope)
        // Eggs (tomorrow): lead reminder already past, so today + expired; butter and chicken: three each.
        assertTrue(scheduler.last.isNotEmpty())
        assertTrue(scheduler.last.all { it.id.startsWith("zerobite.item.") })
        assertEquals(setOf("eggs", "butter", "chicken"), scheduler.last.map { it.itemId }.toSet())

        inventory.markConsumed(listOf("butter", "chicken"), TestClock.now)
        assertEquals(setOf("eggs"), scheduler.last.map { it.itemId }.toSet())

        settings.update { it.copy(notificationsEnabled = false) }
        assertEquals(emptyList(), scheduler.last)
        scope.cancel()
    }

    @Test
    fun `revoked permission cancels every reminder`() = runBlocking {
        sync.resyncNow()
        assertTrue(scheduler.last.isNotEmpty())
        access.current = NotificationPermission.DENIED // switched off in system settings
        sync.resyncNow()
        assertEquals(emptyList(), scheduler.last)
    }

    @Test
    fun `reminder time and lead days come from settings`() = runBlocking {
        settings.update { it.copy(leadDays = 1, notifyHour = 7, notifyMinute = 15) }
        sync.resyncNow()
        val chickenSoon = scheduler.last.first { it.id == "zerobite.item.chicken.soon" }
        assertEquals("Chicken breast expires tomorrow", chickenSoon.title)
        // Chicken expires Sat 12 Sep; one day before at 07:15 BST.
        assertEquals(kotlin.time.Instant.parse("2026-09-11T06:15:00Z"), chickenSoon.fireAt)
    }
}
```

- [ ] **Step 3: Run to see them fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests 'com.mazesys.zerobite.reminders.*'; cd ..`
Expected: compilation FAILS: unresolved `AlarmReminderScheduler`, `ReminderReceiver`, `Notifications`, `ReminderSync`, `NotificationPermission`.

- [ ] **Step 4: Manifest and status-bar icon**

In `AndroidManifest.xml`, add before `<application`:

```xml
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

and inside `<application>`, after the activity:

```xml
        <receiver
            android:name=".reminders.ReminderReceiver"
            android:exported="false" />
```

`res/drawable/ic_notification.xml`: the mark's ring and Z, white on transparent, as Android status-bar icons require:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="1024"
    android:viewportHeight="1024">
    <path
        android:fillColor="#FFFFFFFF"
        android:fillType="evenOdd"
        android:pathData="M512,136a376,376 0,1 1,0 752a376,376 0,1 1,0 -752z M512,212a300,300 0,1 0,0 600a300,300 0,1 0,0 -600z" />
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M384,366h254v76h-254z M384,582h254v76h-254z M396,617l262,-220l48,57l-262,220z" />
</vector>
```

- [ ] **Step 5: Implement the reminders package**

`reminders/Notifications.kt`:

```kotlin
package com.mazesys.zerobite.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mazesys.zerobite.MainActivity
import com.mazesys.zerobite.R
import com.mazesys.zerobite.ui.brand.BrandMarkColors

/** Channels and posting for every notification the app shows. */
object Notifications {
    const val CHANNEL_EXPIRY = "expiry"
    const val EXTRA_ITEM_ID = "com.mazesys.zerobite.extra.ITEM_ID"

    fun createChannels(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_EXPIRY, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Expiry reminders")
                .setDescription("A heads-up before food expires, on the day, and if it slips past.")
                .build(),
        )
    }

    /** True when the app may show notifications (runtime permission on Android 13+, and not blocked). */
    fun canPost(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Posts (or replaces) the notification identified by `tag`; tapping it opens `itemId` if given. */
    @SuppressLint("MissingPermission") // checked by canPost
    fun post(context: Context, tag: String, title: String, body: String, itemId: String?) {
        if (!canPost(context)) return
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { if (itemId != null) putExtra(EXTRA_ITEM_ID, itemId) }
        val tap = PendingIntent.getActivity(context, tag.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL_EXPIRY)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(BrandMarkColors.red.toArgb())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(tag, 0, notification)
    }
}
```

`reminders/NotificationAccess.kt`:

```kotlin
package com.mazesys.zerobite.reminders

import android.content.Context
import android.content.SharedPreferences

enum class NotificationPermission {
    GRANTED,
    /** Asked before and not granted: only system settings can change it now. */
    DENIED,
    NOT_ASKED,
}

interface NotificationAccess {
    fun permission(): NotificationPermission
    /** Records that the user has been asked, so a refusal reads as DENIED rather than NOT_ASKED. */
    fun markAsked()
}

class AndroidNotificationAccess(private val context: Context, private val prefs: SharedPreferences) : NotificationAccess {
    override fun permission(): NotificationPermission = when {
        Notifications.canPost(context) -> NotificationPermission.GRANTED
        prefs.getBoolean(ASKED, false) -> NotificationPermission.DENIED
        else -> NotificationPermission.NOT_ASKED
    }

    override fun markAsked() {
        prefs.edit().putBoolean(ASKED, true).apply()
    }

    private companion object {
        const val ASKED = "notifications.asked"
    }
}
```

`reminders/Notifier.kt`:

```kotlin
package com.mazesys.zerobite.reminders

import android.content.Context

interface Notifier {
    fun sendTest()
}

class AndroidNotifier(private val context: Context) : Notifier {
    override fun sendTest() = Notifications.post(
        context,
        tag = "zerobite.test",
        title = "Zero Bite reminders are on",
        body = "This is how you'll hear about food that's about to expire.",
        itemId = null,
    )
}
```

`reminders/ReminderScheduler.kt`:

```kotlin
package com.mazesys.zerobite.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import com.mazesys.zerobite.core.notifications.PlannedNotification

interface ReminderScheduler {
    /** Replaces every scheduled reminder with `planned`. */
    fun reschedule(planned: List<PlannedNotification>)
}

/**
 * One inexact alarm per reminder (`setAndAllowWhileIdle`: no exact-alarm permission, fine for a
 * 9am nudge). The ids it scheduled are kept so stale alarms can be cancelled after a restart.
 */
class AlarmReminderScheduler(private val context: Context, private val prefs: SharedPreferences) : ReminderScheduler {

    private val alarms = context.getSystemService(AlarmManager::class.java)

    override fun reschedule(planned: List<PlannedNotification>) {
        prefs.getStringSet(KEY, emptySet()).orEmpty().forEach(::cancel)
        planned.forEach { reminder ->
            val intent = intentFor(reminder.id)
                .putExtra(ReminderReceiver.EXTRA_TITLE, reminder.title)
                .putExtra(ReminderReceiver.EXTRA_BODY, reminder.body)
                .putExtra(Notifications.EXTRA_ITEM_ID, reminder.itemId)
            val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.fireAt.toEpochMilliseconds(), pending)
        }
        prefs.edit().putStringSet(KEY, planned.map { it.id }.toSet()).apply()
    }

    private fun cancel(id: String) {
        val pending = PendingIntent.getBroadcast(context, 0, intentFor(id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
            ?: return
        alarms.cancel(pending)
        pending.cancel()
    }

    /** The data URI makes each reminder's PendingIntent distinct. */
    private fun intentFor(id: String) =
        Intent(context, ReminderReceiver::class.java).setData(Uri.parse("zerobite://reminder/$id"))

    private companion object {
        const val KEY = "reminders.scheduled"
    }
}
```

`reminders/ReminderReceiver.kt`:

```kotlin
package com.mazesys.zerobite.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fired by an alarm: shows the planned reminder. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tag = intent.data?.lastPathSegment ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        Notifications.post(context, tag, title, intent.getStringExtra(EXTRA_BODY).orEmpty(), intent.getStringExtra(Notifications.EXTRA_ITEM_ID))
    }

    companion object {
        const val EXTRA_TITLE = "com.mazesys.zerobite.extra.TITLE"
        const val EXTRA_BODY = "com.mazesys.zerobite.extra.BODY"
    }
}
```

`reminders/ReminderSync.kt`:

```kotlin
package com.mazesys.zerobite.reminders

import com.mazesys.zerobite.core.inventory.InventoryRepository
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.notifications.ExpiringItemSummary
import com.mazesys.zerobite.core.notifications.ExpiryNotificationPlanner
import com.mazesys.zerobite.core.time.AppClock
import com.mazesys.zerobite.data.SettingsRepository
import com.mazesys.zerobite.data.SettingsSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Re-plans every reminder from scratch whenever the fridge or settings change, so the
 * scheduled set always mirrors the fridge (as iOS does on every inventory change).
 */
class ReminderSync(
    private val inventory: InventoryRepository,
    private val settings: SettingsRepository,
    private val clock: AppClock,
    private val access: NotificationAccess,
    private val scheduler: ReminderScheduler,
) {
    fun start(scope: CoroutineScope): Job = scope.launch {
        combine(inventory.items, settings.state) { items, s -> items to s }.collectLatest { (items, s) -> sync(items, s) }
    }

    /** After a reboot, app update, time change, or returning to the app (permission may have changed). */
    suspend fun resyncNow() = sync(inventory.items.first(), settings.current())

    private fun sync(items: List<FoodItem>, settings: SettingsSnapshot) {
        val allowed = settings.notificationsEnabled && access.permission() == NotificationPermission.GRANTED
        val summaries = items.filterNot { it.isConsumed }.map { ExpiringItemSummary(it.id, it.name, it.expiresOn) }
        val preferences = settings.notificationPreferences.copy(enabled = allowed)
        scheduler.reschedule(ExpiryNotificationPlanner(clock.timeZone).plan(summaries, preferences, clock.now()))
    }
}
```

- [ ] **Step 6: Wire it into the composition root and the app**

In `AppDependencies.kt`, extend the class (keep `IdGenerator` and the DataStore delegate as they are):

```kotlin
class AppDependencies(
    val clock: AppClock,
    val settings: SettingsRepository,
    val inventory: InventoryRepository,
    val recipes: RecipeRepository,
    val ids: IdGenerator,
    /** Outlives screens: background syncs (reminders, recalls) run here. */
    val appScope: CoroutineScope,
    val notifications: NotificationAccess,
    val scheduler: ReminderScheduler,
    val notifier: Notifier,
) {
    val reminders = ReminderSync(inventory, settings, clock, notifications, scheduler)

    companion object {
        fun live(context: Context): AppDependencies {
            val app = context.applicationContext
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val prefs = app.getSharedPreferences("reminders", Context.MODE_PRIVATE)
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
            )
        }
    }
}
```

with imports `com.mazesys.zerobite.reminders.AlarmReminderScheduler`, `AndroidNotificationAccess`, `AndroidNotifier`, `NotificationAccess`, `Notifier`, `ReminderScheduler` and `ReminderSync`.

`ZeroBiteApplication.kt`:

```kotlin
package com.mazesys.zerobite

import android.app.Application
import com.mazesys.zerobite.reminders.Notifications

class ZeroBiteApplication : Application() {
    val dependencies: AppDependencies by lazy { AppDependencies.live(this) }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        dependencies.reminders.start(dependencies.appScope)
    }
}
```

- [ ] **Step 7: Run the whole suite**

Run: `cd android && ./gradlew :app:testDebugUnitTest; cd ..`
Expected: every test PASSES, including the 3 + 2 + 1 + 3 new reminder tests.

- [ ] **Step 8: Commit**

```bash
git add android
git commit -m "Schedule Android expiry reminders with AlarmManager and post them as notifications

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Reboot survival, notification taps, permission requests and the Fridge tip

**Files:**
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/reminders/{RescheduleReceiver,PermissionRequester}.kt`
- Modify: `AndroidManifest.xml` (boot permission, reschedule receiver, `launchMode="singleTop"`), `MainActivity.kt`
- Modify: `screens/inventory/InventoryViewModel.kt`, `screens/inventory/InventoryScreen.kt`, `screens/root/MainScreen.kt`
- Modify tests that construct `InventoryViewModel`: `InventoryViewModelTest`, `InventoryScreenTest`, `InventoryTabTest`, and the settings used by `RootScreenTest`
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/reminders/RescheduleReceiverTest.kt`, `android/app/src/test/kotlin/com/mazesys/zerobite/DeepLinkTest.kt`; new cases in `InventoryViewModelTest`

**Interfaces:**
- Consumes: Task 4.
- Produces:
  - `class RescheduleReceiver : BroadcastReceiver` (boot, package replaced, time and time-zone changes)
  - `fun interface PermissionRequester { suspend fun requestNotifications(): Boolean }`; `val LocalPermissionRequester` (default: never granted)
  - `MainActivity.handleIntent(intent)` (public, used for deep links)
  - `InventoryViewModel(inventory, settings, clock, ids, coordinator, notifications: NotificationAccess)`
  - `InventoryUiState.showsNotificationTip`; `InventoryViewModel.enableNotifications(requester)` and `dismissNotificationTip()`

- [ ] **Step 1: Update existing callers and write the failing tests**

In `InventoryViewModelTest`, `InventoryScreenTest` and `InventoryTabTest`, add `FakeAccess()` as the new last argument wherever `InventoryViewModel(...)` is built, and import `com.mazesys.zerobite.testing.FakeAccess`. In `InventoryScreenTest.show(...)`, build the view model with `FakeSettings(SettingsSnapshot(dismissedNotificationTip = true))` instead of `FakeSettings()`, so the grid's layout and screenshots don't change. Do the same in `InventoryViewModelTest`'s `viewModel(...)` builder. In `RootScreenTest`, replace every `SettingsSnapshot(hasCompletedWelcome = true)` with `SettingsSnapshot(hasCompletedWelcome = true, dismissedNotificationTip = true)`.

Add to `InventoryViewModelTest`:

```kotlin
    private fun tipViewModel(access: FakeAccess, settings: FakeSettings = FakeSettings()) =
        InventoryViewModel(inventory, settings, TestClock.fixed(), SequentialIds(), app.inventory, access)

    @Test
    fun `reminder tip shows until answered`() {
        val vm = tipViewModel(FakeAccess(NotificationPermission.NOT_ASKED))
        assertTrue(vm.state.value.showsNotificationTip)
        vm.dismissNotificationTip()
        assertFalse(vm.state.value.showsNotificationTip)
    }

    @Test
    fun `turning reminders on from the tip asks for permission`() {
        val access = FakeAccess(NotificationPermission.NOT_ASKED)
        val settings = FakeSettings()
        val vm = tipViewModel(access, settings)
        vm.enableNotifications { access.current = NotificationPermission.GRANTED; true }
        assertTrue(access.asked)
        assertTrue(settings.state.value.notificationsEnabled)
        assertFalse(vm.state.value.showsNotificationTip)
    }

    @Test
    fun `refusing hides the tip and leaves reminders off`() {
        val access = FakeAccess(NotificationPermission.NOT_ASKED)
        val settings = FakeSettings()
        val vm = tipViewModel(access, settings)
        vm.enableNotifications { access.current = NotificationPermission.DENIED; false }
        assertFalse(settings.state.value.notificationsEnabled)
        assertFalse(vm.state.value.showsNotificationTip)
    }

    @Test
    fun `no tip once permission was refused at the system level`() {
        assertFalse(tipViewModel(FakeAccess(NotificationPermission.DENIED)).state.value.showsNotificationTip)
    }
```

with imports `com.mazesys.zerobite.reminders.NotificationPermission` and `kotlin.test.assertTrue`.

`reminders/RescheduleReceiverTest.kt`:

```kotlin
package com.mazesys.zerobite.reminders

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.mazesys.zerobite.ZeroBiteApplication
import com.mazesys.zerobite.core.model.FoodItem
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertTrue
import kotlin.time.Clock

@RunWith(RobolectricTestRunner::class)
class RescheduleReceiverTest {

    private val app = ApplicationProvider.getApplicationContext<ZeroBiteApplication>()
    private val alarms = shadowOf(app.getSystemService(AlarmManager::class.java))

    @Test
    fun `manifest registers it for boot, updates and time changes`() {
        for (action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) {
            val receivers = app.packageManager.queryBroadcastReceivers(Intent(action).setPackage(app.packageName), 0)
            assertTrue(receivers.any { it.activityInfo.name == RescheduleReceiver::class.java.name }, action)
        }
    }

    @Test
    fun `reschedule receiver replans on boot and time changes`() = runBlocking {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val deps = app.dependencies
        deps.settings.update { it.copy(notificationsEnabled = true) }
        val today = LocalDate(2030, 1, 1)
        deps.inventory.add(FoodItem(id = "milk", name = "Milk", addedAt = Clock.System.now(), expiresOn = today.plus(10, DateTimeUnit.DAY)))
        deps.scheduler.reschedule(emptyList()) // what a reboot does to alarms

        app.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED).setClass(app, RescheduleReceiver::class.java))
        shadowOf(Looper.getMainLooper()).idle()
        val deadline = System.currentTimeMillis() + 5_000
        while (alarms.scheduledAlarms.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertTrue(alarms.scheduledAlarms.isNotEmpty(), "alarms restored after boot")
    }
}
```

`DeepLinkTest.kt`:

```kotlin
package com.mazesys.zerobite

import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.mazesys.zerobite.coordination.AppTab
import com.mazesys.zerobite.coordination.InventoryRoute
import com.mazesys.zerobite.coordination.SheetDestination
import com.mazesys.zerobite.reminders.Notifications
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class DeepLinkTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `notification tap opens the item from anywhere`() {
        val activity = compose.activity
        val app = ViewModelProvider(activity, AppViewModel.factory((activity.application as ZeroBiteApplication).dependencies))[AppViewModel::class.java]
        compose.runOnUiThread {
            app.coordinator.selectTab(AppTab.SETTINGS)
            app.coordinator.presentSheet(SheetDestination.Scan)
            activity.handleIntent(Intent(activity, MainActivity::class.java).putExtra(Notifications.EXTRA_ITEM_ID, "milk"))
        }
        assertEquals(AppTab.FRIDGE, app.coordinator.selectedTab)
        assertNull(app.coordinator.presentedSheet)
        assertEquals(InventoryRoute.ItemDetail("milk"), app.coordinator.inventory.backStack.last())
    }
}
```

- [ ] **Step 2: Run to see them fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests '*InventoryViewModelTest' --tests '*RescheduleReceiverTest' --tests '*DeepLinkTest'; cd ..`
Expected: compilation FAILS: unresolved `RescheduleReceiver`, `handleIntent`, `enableNotifications`, and the sixth `InventoryViewModel` parameter.

- [ ] **Step 3: Implement the receiver and the requester**

`reminders/RescheduleReceiver.kt`:

```kotlin
package com.mazesys.zerobite.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mazesys.zerobite.ZeroBiteApplication
import kotlinx.coroutines.launch

/** Android forgets alarms on reboot and app update, and times shift with the clock: replan. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val dependencies = (context.applicationContext as ZeroBiteApplication).dependencies
        val pending = goAsync()
        dependencies.appScope.launch {
            try {
                dependencies.reminders.resyncNow()
            } finally {
                pending?.finish()
            }
        }
    }
}
```

`reminders/PermissionRequester.kt`:

```kotlin
package com.mazesys.zerobite.reminders

import androidx.compose.runtime.staticCompositionLocalOf

/** Asks the system for notification permission; only ever called from an explicit user action. */
fun interface PermissionRequester {
    suspend fun requestNotifications(): Boolean
}

/** Provided by MainActivity. The default (tests, previews) never grants. */
val LocalPermissionRequester = staticCompositionLocalOf { PermissionRequester { false } }
```

In `AndroidManifest.xml`: add `<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />`, add `android:launchMode="singleTop"` to the activity, and register:

```xml
        <receiver
            android:name=".reminders.RescheduleReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
                <action android:name="android.intent.action.TIME_SET" />
                <action android:name="android.intent.action.TIMEZONE_CHANGED" />
            </intent-filter>
        </receiver>
```

- [ ] **Step 4: Activity: deep links, permission requests, resync on resume**

Replace `MainActivity.kt`:

```kotlin
package com.mazesys.zerobite

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.app.NotificationManagerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.mazesys.zerobite.reminders.LocalPermissionRequester
import com.mazesys.zerobite.reminders.Notifications
import com.mazesys.zerobite.reminders.PermissionRequester
import com.mazesys.zerobite.screens.root.RootScreen
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val dependencies: AppDependencies get() = (application as ZeroBiteApplication).dependencies
    private val app: AppViewModel by viewModels { AppViewModel.factory(dependencies) }

    private var pendingPermission: CompletableDeferred<Boolean>? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingPermission?.complete(granted)
        pendingPermission = null
    }

    private val requester = PermissionRequester {
        if (Build.VERSION.SDK_INT < 33) {
            NotificationManagerCompat.from(this).areNotificationsEnabled()
        } else {
            val result = CompletableDeferred<Boolean>().also { pendingPermission = it }
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            result.await()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            ZeroBiteTheme {
                CompositionLocalProvider(LocalPermissionRequester provides requester) {
                    RootScreen(app.flow, app.coordinator, app.sheetStores, dependencies)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Notification permission may have been changed in system settings meanwhile.
        lifecycleScope.launch { dependencies.reminders.resyncNow() }
    }

    /** A reminder tap: jump to the Fridge tab and open the item, from wherever the app was. */
    fun handleIntent(intent: Intent) {
        val itemId = intent.getStringExtra(Notifications.EXTRA_ITEM_ID) ?: return
        intent.removeExtra(Notifications.EXTRA_ITEM_ID)
        app.coordinator.showItem(itemId)
    }
}
```

- [ ] **Step 5: The Fridge reminder tip** (copy from `InventoryView.swift`'s `notificationTip`)

In `InventoryViewModel.kt`:
- Add `val showsNotificationTip: Boolean = false` to `InventoryUiState`, after `gridSections`.
- Add the constructor parameter `private val notifications: NotificationAccess` (last).
- Make `settings` a property (`private val settings: SettingsRepository`).
- In the `combine` body, copy in the tip:

```kotlin
            built.copy(
                useFirstProgress = built.useFirstItem?.shelfLifeProgress(clock.now(), clock.timeZone) ?: 0f,
                // Nudge while reminders are off and the user hasn't refused at the system level.
                showsNotificationTip = !s.dismissedNotificationTip && !s.notificationsEnabled &&
                    notifications.permission() != NotificationPermission.DENIED,
            )
```

- Add the actions:

```kotlin
    fun enableNotifications(requester: PermissionRequester) {
        viewModelScope.launch {
            val granted = requester.requestNotifications()
            notifications.markAsked()
            settings.update { it.copy(notificationsEnabled = granted, dismissedNotificationTip = true) }
        }
    }

    fun dismissNotificationTip() {
        viewModelScope.launch { settings.update { it.copy(dismissedNotificationTip = true) } }
    }
```

with imports `com.mazesys.zerobite.reminders.NotificationAccess`, `NotificationPermission` and `PermissionRequester`.

In `InventoryScreen.kt`'s `fridgeContent`, insert at the top (before the `useFirstItem` block), so it runs first as on iOS:

```kotlin
    if (state.showsNotificationTip) {
        fullWidth("tip") { NotificationTip(viewModel) }
    }
```

and add:

```kotlin
@Composable
private fun NotificationTip(viewModel: InventoryViewModel) {
    val requester = LocalPermissionRequester.current
    val colors = ZeroBiteTheme.colors
    AppCard(Modifier.padding(top = Spacing.sm).testTag("inventory.notificationTip")) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            IconTile(Icons.Rounded.NotificationsActive, tint = colors.brandOrange)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("Get reminded before food expires", style = ZeroBiteTheme.type.subheadline.semibold(), color = colors.textPrimary)
                Text("We'll nudge you a few days before the date.", style = ZeroBiteTheme.type.caption, color = colors.textSecondary)
                Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppButton("Turn on", { viewModel.enableNotifications(requester) }, Modifier.weight(1f), compact = true)
                    AppButton("Not now", viewModel::dismissNotificationTip, Modifier.weight(1f), kind = ButtonKind.TONAL, compact = true)
                }
            }
        }
    }
}
```

with imports `androidx.compose.foundation.layout.Row`, `androidx.compose.material.icons.rounded.NotificationsActive`, `com.mazesys.zerobite.reminders.LocalPermissionRequester`, `com.mazesys.zerobite.ui.components.AppButton`, `AppCard`, `ButtonKind`, `IconTile` and `com.mazesys.zerobite.ui.theme.semibold`.

In `MainScreen.kt`, pass `dependencies.notifications` as the last `InventoryViewModel` argument.

- [ ] **Step 6: Run the whole suite**

Run: `cd android && ./gradlew :app:testDebugUnitTest; cd ..`
Expected: every test PASSES, including the 4 new tip tests, 2 receiver tests and the deep-link test. Re-record the Fridge screenshots with `./gradlew :app:recordRoborazziDebug --tests '*InventoryScreenTest'`. They should be unchanged, because the tip is dismissed in those tests.

- [ ] **Step 7: Commit**

```bash
git add android
git commit -m "Keep Android reminders across reboots, open items from taps, add the reminder tip

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Onboarding (first run and "How Zero Bite works")

**Files:**
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/ui/components/BrandLockup.kt`
- Create: `android/app/src/main/kotlin/com/mazesys/zerobite/screens/welcome/{WelcomeViewModel,WelcomeScreen,WelcomeIllustrations}.kt`
- Modify: `coordination/AppCoordinator.kt` (adds `SheetDestination.HowItWorks`), `screens/root/RootScreen.kt`, `screens/root/SheetHost.kt`
- Delete: `screens/root/WelcomePlaceholder.kt`
- Modify test: `screens/root/RootScreenTest.kt` (first-run flow)
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/screens/welcome/{WelcomeViewModelTest,WelcomeScreenTest}.kt`

**Interfaces:**
- Consumes: `NotificationAccess`, `PermissionRequester` (Tasks 4–5), `SettingsRepository`, `BrandMark`.
- Produces:
  - `BrandLockup(size: Dp = 28.dp, modifier)`
  - `enum class WelcomeMode { FIRST_RUN, REPLAY }`, `enum class WelcomePage { WELCOME, SCAN, REMIND, COOK, REMINDERS }`, `enum class WelcomeStep { INTRO, REQUESTING, DENIED }`
  - `data class WelcomeUiState(mode, pages, currentPage, step)` with `pageIndex`, `isLastPage`, `primaryTitle`, `showsNotNow`, `skipTitle`
  - `class WelcomeViewModel(mode, settings, access, onComplete: suspend () -> Unit)` with `primaryAction(requester)`, `skipIntro()`, `skip()`, `setPage(page)`
  - `WelcomeScreen(viewModel, modifier)`
  - `SheetDestination.HowItWorks`

- [ ] **Step 1: Write the failing tests** (port of `WelcomeViewModelTests.swift`, plus the permission outcomes)

`screens/welcome/WelcomeViewModelTest.kt`:

```kotlin
package com.mazesys.zerobite.screens.welcome

import com.mazesys.zerobite.reminders.NotificationPermission
import com.mazesys.zerobite.testing.FakeAccess
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WelcomeViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private var completed = 0
    private val settings = FakeSettings()
    private val access = FakeAccess(NotificationPermission.NOT_ASKED)
    private fun vm(mode: WelcomeMode = WelcomeMode.FIRST_RUN) = WelcomeViewModel(mode, settings, access, onComplete = { completed++ })
    private val never = com.mazesys.zerobite.reminders.PermissionRequester { false }

    @Test
    fun `first run pages through to the reminders step`() {
        val model = vm()
        assertEquals(WelcomePage.REMINDERS, model.state.value.pages.last())
        assertEquals("Show me how", model.state.value.primaryTitle)
        repeat(model.state.value.pages.size - 1) { model.primaryAction(never) }
        assertEquals(WelcomePage.REMINDERS, model.state.value.currentPage)
        assertTrue(model.state.value.isLastPage)
        assertEquals("Turn on reminders", model.state.value.primaryTitle)
        assertEquals(0, completed)
    }

    @Test
    fun `skip jumps to reminders on first run`() {
        val model = vm()
        model.skipIntro()
        assertEquals(WelcomePage.REMINDERS, model.state.value.currentPage)
    }

    @Test
    fun `replay has no permission step and finishes on the last page`() {
        val model = vm(WelcomeMode.REPLAY)
        assertFalse(WelcomePage.REMINDERS in model.state.value.pages)
        assertEquals("Close", model.state.value.skipTitle)
        repeat(model.state.value.pages.size) { model.primaryAction(never) }
        assertEquals(1, completed)
    }

    @Test
    fun `granting finishes with reminders on`() {
        val model = vm()
        model.skipIntro()
        model.primaryAction { access.current = NotificationPermission.GRANTED; true }
        assertTrue(settings.state.value.notificationsEnabled)
        assertTrue(access.asked)
        assertEquals(1, completed)
    }

    @Test
    fun `refusing shows the denied step, then continues`() {
        val model = vm()
        model.skipIntro()
        model.primaryAction(never)
        assertEquals(WelcomeStep.DENIED, model.state.value.step)
        assertEquals("Continue", model.state.value.primaryTitle)
        assertFalse(model.state.value.showsNotNow)
        model.primaryAction(never)
        assertEquals(1, completed)
    }

    @Test
    fun `not now finishes with reminders off`() {
        val model = vm()
        model.skipIntro()
        model.skip()
        assertFalse(settings.state.value.notificationsEnabled)
        assertEquals(1, completed)
    }
}
```

`screens/welcome/WelcomeScreenTest.kt`:

```kotlin
package com.mazesys.zerobite.screens.welcome

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.mazesys.zerobite.reminders.NotificationPermission
import com.mazesys.zerobite.testing.FakeAccess
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class WelcomeScreenTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val vm = WelcomeViewModel(WelcomeMode.FIRST_RUN, FakeSettings(), FakeAccess(NotificationPermission.NOT_ASKED), onComplete = {})

    @Test
    fun `next walks the pages`() {
        compose.setContent { ZeroBiteTheme { WelcomeScreen(vm) } }
        compose.onNodeWithText("Waste less,\ncook more.").assertExists()
        compose.onNodeWithTag("welcome.primary").performClick()
        compose.waitUntil(3_000) { vm.state.value.currentPage == WelcomePage.SCAN }
        compose.onNodeWithText("Scan the label, skip the typing.").assertExists()
        compose.onNodeWithTag("welcome.skip").performClick()
        compose.onNodeWithText("Want a nudge before food goes off?").assertExists()
        compose.onNodeWithTag("welcome.notNow").assertExists()
    }

    @Test
    fun `welcome light`() {
        compose.setContent { ZeroBiteTheme { WelcomeScreen(vm) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/welcome_light.png")
    }

    @Test
    fun `welcome reminders dark`() {
        vm.skipIntro()
        compose.setContent { ZeroBiteTheme(darkTheme = true) { WelcomeScreen(vm) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/welcome_reminders_dark.png")
    }
}
```

In `RootScreenTest.kt`, replace the body of `first run goes splash, welcome, fridge` after `show(settings)` with:

```kotlin
        compose.onNodeWithText("Zero Bite").assertExists()
        waitForTag("welcome.skip")
        compose.onNodeWithTag("welcome.skip").performClick()
        waitForTag("welcome.notNow")
        compose.onNodeWithTag("welcome.notNow").performClick()
        waitForTag("tab.fridge")
        compose.onNodeWithTag("tab.fridge").assertIsSelected()
        assertTrue(runBlocking { settings.current().hasCompletedWelcome })
```

- [ ] **Step 2: Run to see them fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests '*Welcome*Test'; cd ..`
Expected: compilation FAILS: unresolved `WelcomeViewModel`, `WelcomeScreen`.

- [ ] **Step 3: Implement the lockup and the view model**

`ui/components/BrandLockup.kt`:

```kotlin
package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazesys.zerobite.ui.brand.BrandMark
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.semibold

/** The mark next to the "Zero Bite" name, in the app typeface. */
@Composable
fun BrandLockup(size: Dp = 28.dp, modifier: Modifier = Modifier) {
    Row(
        modifier.clearAndSetSemantics { contentDescription = "Zero Bite" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(size * 0.32f),
    ) {
        BrandMark(Modifier.size(size))
        Text("Zero Bite", style = ZeroBiteTheme.type.title3.semibold(), letterSpacing = (-0.4).sp, color = ZeroBiteTheme.colors.textPrimary)
    }
}
```

`screens/welcome/WelcomeViewModel.kt`:

```kotlin
package com.mazesys.zerobite.screens.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazesys.zerobite.data.SettingsRepository
import com.mazesys.zerobite.reminders.NotificationAccess
import com.mazesys.zerobite.reminders.PermissionRequester
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WelcomeMode {
    /** First launch: explainer pages, then the notification opt-in. */
    FIRST_RUN,
    /** From Settings: explainer pages only. */
    REPLAY,
}

enum class WelcomePage { WELCOME, SCAN, REMIND, COOK, REMINDERS }

enum class WelcomeStep { INTRO, REQUESTING, DENIED }

data class WelcomeUiState(
    val mode: WelcomeMode,
    val pages: List<WelcomePage>,
    val currentPage: WelcomePage,
    val step: WelcomeStep = WelcomeStep.INTRO,
) {
    val pageIndex: Int get() = pages.indexOf(currentPage).coerceAtLeast(0)
    val isLastPage: Boolean get() = currentPage == pages.last()
    val isRequesting: Boolean get() = step == WelcomeStep.REQUESTING
    val skipTitle: String get() = if (mode == WelcomeMode.REPLAY) "Close" else "Skip"
    val showsNotNow: Boolean get() = currentPage == WelcomePage.REMINDERS && step != WelcomeStep.DENIED

    val primaryTitle: String
        get() = when (currentPage) {
            WelcomePage.REMINDERS -> if (step == WelcomeStep.DENIED) "Continue" else "Turn on reminders"
            WelcomePage.COOK -> if (mode == WelcomeMode.REPLAY) "Done" else "Next"
            WelcomePage.WELCOME -> "Show me how"
            else -> "Next"
        }
}

/** Onboarding: a short paged explainer, ending (on first run) with the reminder opt-in. */
class WelcomeViewModel(
    mode: WelcomeMode,
    private val settings: SettingsRepository,
    private val access: NotificationAccess,
    private val onComplete: suspend () -> Unit,
) : ViewModel() {

    private val pages = if (mode == WelcomeMode.FIRST_RUN) WelcomePage.entries else WelcomePage.entries - WelcomePage.REMINDERS
    private val _state = MutableStateFlow(WelcomeUiState(mode, pages, pages.first()))
    val state: StateFlow<WelcomeUiState> = _state.asStateFlow()
    private var finished = false

    /** Swiping the pager. */
    fun setPage(page: WelcomePage) {
        if (page in pages) _state.update { it.copy(currentPage = page) }
    }

    /** Advances, or finishes on the last page (asking for permission on first run). */
    fun primaryAction(requester: PermissionRequester) {
        val current = _state.value
        when {
            current.currentPage == WelcomePage.REMINDERS && current.step == WelcomeStep.DENIED -> finish()
            current.currentPage == WelcomePage.REMINDERS -> enableReminders(requester)
            current.isLastPage -> finish()
            else -> _state.update { it.copy(currentPage = pages[it.pageIndex + 1]) }
        }
    }

    /** Past the explainer: to the permission page on first run, or closes when replaying. */
    fun skipIntro() {
        if (WelcomePage.REMINDERS in pages) setPage(WelcomePage.REMINDERS) else finish()
    }

    /** "Not now" on the reminders page. */
    fun skip() {
        viewModelScope.launch {
            settings.update { it.copy(notificationsEnabled = false) }
            complete()
        }
    }

    private fun enableReminders(requester: PermissionRequester) {
        _state.update { it.copy(step = WelcomeStep.REQUESTING) }
        viewModelScope.launch {
            val granted = requester.requestNotifications()
            access.markAsked()
            settings.update { it.copy(notificationsEnabled = granted) }
            if (granted) complete() else _state.update { it.copy(step = WelcomeStep.DENIED) }
        }
    }

    private fun finish() {
        viewModelScope.launch { complete() }
    }

    private suspend fun complete() {
        if (finished) return
        finished = true
        onComplete()
    }
}
```

- [ ] **Step 4: Implement the screen and illustrations** (copy from `WelcomeView.swift`)

`screens/welcome/WelcomeScreen.kt`:

```kotlin
package com.mazesys.zerobite.screens.welcome

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazesys.zerobite.reminders.LocalPermissionRequester
import com.mazesys.zerobite.ui.components.AppButton
import com.mazesys.zerobite.ui.components.BrandLockup
import com.mazesys.zerobite.ui.components.ButtonKind
import com.mazesys.zerobite.ui.components.Overline
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.medium
import com.mazesys.zerobite.ui.theme.semibold

@Composable
fun WelcomeScreen(viewModel: WelcomeViewModel, modifier: Modifier = Modifier) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val requester = LocalPermissionRequester.current
    val pager = rememberPagerState { ui.pages.size }

    // The view model owns the page; the pager follows it and reports swipes back.
    LaunchedEffect(ui.pageIndex) { if (pager.currentPage != ui.pageIndex) pager.animateScrollToPage(ui.pageIndex) }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { viewModel.setPage(ui.pages[it]) } }

    Column(modifier.fillMaxSize().background(ZeroBiteTheme.colors.backgroundPrimary).systemBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(38.dp).padding(horizontal = Spacing.xl).padding(top = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageIndicator(ui.pages.size, ui.pageIndex)
            Spacer(Modifier.weight(1f))
            if (!ui.isLastPage) {
                Text(
                    ui.skipTitle,
                    style = ZeroBiteTheme.type.subheadline.medium(),
                    color = ZeroBiteTheme.colors.textSecondary,
                    modifier = Modifier.testTag("welcome.skip").clickable(role = Role.Button) { viewModel.skipIntro() },
                )
            }
        }
        HorizontalPager(pager, Modifier.weight(1f), userScrollEnabled = !ui.isRequesting) { index ->
            OnboardingPage(ui.pages[index], denied = ui.step == WelcomeStep.DENIED)
        }
        Column(Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val onReminders = ui.currentPage == WelcomePage.REMINDERS
            AppButton(
                ui.primaryTitle,
                { viewModel.primaryAction(requester) },
                Modifier.testTag("welcome.primary"),
                kind = if (onReminders) ButtonKind.BRAND else ButtonKind.INK,
                icon = if (onReminders && ui.step != WelcomeStep.DENIED) Icons.Rounded.NotificationsActive else null,
                loading = ui.isRequesting,
            )
            if (ui.showsNotNow) {
                AppButton("Not now", viewModel::skip, Modifier.testTag("welcome.notNow"), kind = ButtonKind.PLAIN)
            }
        }
    }
}

/** Bespoke page dots: short bars, the current one stretched and orange. */
@Composable
private fun PageIndicator(count: Int, index: Int) {
    val colors = ZeroBiteTheme.colors
    Row(
        Modifier.semantics { contentDescription = "Page ${index + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(count) { position ->
            val width by animateDpAsState(if (position == index) 22.dp else 8.dp, label = "dot")
            Box(Modifier.width(width).height(4.dp).background(if (position == index) colors.brandOrange else colors.chipFill))
        }
    }
}

@Composable
private fun OnboardingPage(page: WelcomePage, denied: Boolean) {
    val colors = ZeroBiteTheme.colors
    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.xl)) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) { Illustration(page) }
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (page == WelcomePage.WELCOME) {
                BrandLockup(size = 30.dp, modifier = Modifier.padding(bottom = Spacing.xs))
            } else {
                Overline(page.overline, tint = colors.brandOrange)
            }
            Text(page.title(denied), style = ZeroBiteTheme.type.largeTitle.semibold(), letterSpacing = (-1).sp, color = colors.textPrimary)
            Text(page.message(denied), style = ZeroBiteTheme.type.body, color = colors.textSecondary)
        }
        Spacer(Modifier.size(Spacing.xl))
    }
}

private val WelcomePage.overline: String
    get() = when (this) {
        WelcomePage.WELCOME -> "Zero Bite"
        WelcomePage.SCAN -> "1 · Scan"
        WelcomePage.REMIND -> "2 · Get reminded"
        WelcomePage.COOK -> "3 · Cook"
        WelcomePage.REMINDERS -> "Last step"
    }

private fun WelcomePage.title(denied: Boolean): String = when (this) {
    WelcomePage.WELCOME -> "Waste less,\ncook more."
    WelcomePage.SCAN -> "Scan the label, skip the typing."
    WelcomePage.REMIND -> "Know what to use first."
    WelcomePage.COOK -> "Cook with what you have."
    WelcomePage.REMINDERS -> if (denied) "Reminders are off." else "Want a nudge before food goes off?"
}

private fun WelcomePage.message(denied: Boolean): String = when (this) {
    WelcomePage.WELCOME -> "Keep track of what's in your fridge, freezer and cupboard, and use it up before the date."
    WelcomePage.SCAN -> "Point the camera at a best-before or use-by date. A barcode fills in the product name. Everything is read on your phone."
    WelcomePage.REMIND -> "Your fridge is sorted by urgency. The most urgent item sits at the top, with one-tap Used it and Freeze."
    WelcomePage.COOK -> "Recipes are ranked by what's already in your fridge, with food that's about to expire counted first."
    WelcomePage.REMINDERS ->
        if (denied) "You can turn them on any time in Settings."
        else "We'll remind you a few days before the date, on the day, and if it slips past. You choose when."
}
```

`screens/welcome/WelcomeIllustrations.kt` (mock UI built from the app's own components, as on iOS):

```kotlin
package com.mazesys.zerobite.screens.welcome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BakeryDining
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Egg
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.RiceBowl
import androidx.compose.material.icons.rounded.SetMeal
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazesys.zerobite.ui.brand.BrandMark
import com.mazesys.zerobite.ui.brand.BrandMarkColors
import com.mazesys.zerobite.ui.components.AppCard
import com.mazesys.zerobite.ui.components.IconTile
import com.mazesys.zerobite.ui.components.Overline
import com.mazesys.zerobite.ui.components.ShelfLifeBar
import com.mazesys.zerobite.ui.components.Tag
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.medium
import com.mazesys.zerobite.ui.theme.semibold

@Composable
fun Illustration(page: WelcomePage) {
    Box(Modifier.clearAndSetSemantics {}) {
        when (page) {
            WelcomePage.WELCOME -> WelcomeArt()
            WelcomePage.SCAN -> ScanArt()
            WelcomePage.REMIND -> RemindArt()
            WelcomePage.COOK -> CookArt()
            WelcomePage.REMINDERS -> RemindersArt()
        }
    }
}

@Composable
private fun MockTile(icon: ImageVector, name: String, detail: String, label: String, tint: Color, modifier: Modifier, accent: Boolean = true) {
    val type = ZeroBiteTheme.type
    AppCard(modifier, accent = if (accent) tint else null) {
        IconTile(icon, tint = tint)
        Text(name, style = type.subheadline.semibold(), color = ZeroBiteTheme.colors.textPrimary, maxLines = 1, modifier = Modifier.padding(top = 10.dp))
        Row(Modifier.padding(top = 2.dp)) {
            Text(detail, style = type.caption, color = ZeroBiteTheme.colors.textSecondary, modifier = Modifier.weight(1f))
            Text(label, style = type.caption.semibold(), color = tint)
        }
    }
}

@Composable
private fun WelcomeArt() {
    val c = ZeroBiteTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            MockTile(Icons.Rounded.WaterDrop, "Greek yogurt", "500 g", "Today", c.statusToday, Modifier.weight(1f))
            MockTile(Icons.Rounded.Eco, "Spinach", "1 bag", "2d", c.statusSoon, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            MockTile(Icons.Rounded.SetMeal, "Salmon", "2 pcs", "3 Nov", c.statusFresh, Modifier.weight(1f), accent = false)
            MockTile(Icons.Rounded.RiceBowl, "Spaghetti", "500 g", "Jun", c.statusFresh, Modifier.weight(1f), accent = false)
        }
        Row(Modifier.alpha(0.5f), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            MockTile(Icons.Rounded.BakeryDining, "Sourdough", "1 loaf", "4d", c.statusFresh, Modifier.weight(1f), accent = false)
            MockTile(Icons.Rounded.Spa, "Lemons", "3 pcs", "9d", c.statusFresh, Modifier.weight(1f), accent = false)
        }
    }
}

@Composable
private fun ScanArt() {
    val c = ZeroBiteTheme.colors
    Box(contentAlignment = Alignment.Center) {
        AppCard(Modifier.width(250.dp), padding = Spacing.xl) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("SEMI SKIMMED MILK", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.textSecondary)
                Text("2 PINTS · 1.136 L", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = c.textMuted)
                Text(
                    "USE BY 12 OCT",
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = c.textPrimary,
                    modifier = Modifier.background(c.brandOrange.copy(alpha = 0.15f)).border(2.dp, c.brandOrange).padding(horizontal = 8.dp, vertical = 6.dp),
                )
                Icon(Icons.Rounded.QrCode, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(54.dp))
            }
        }
        Canvas(Modifier.width(300.dp).height(260.dp)) {
            val length = 28.dp.toPx()
            val stroke = 4.dp.toPx()
            for ((corner, dx, dy) in listOf(
                Triple(Offset(0f, 0f), 1f, 1f),
                Triple(Offset(size.width, 0f), -1f, 1f),
                Triple(Offset(0f, size.height), 1f, -1f),
                Triple(Offset(size.width, size.height), -1f, -1f),
            )) {
                drawLine(c.brandOrange, corner, corner + Offset(0f, dy * length), stroke, StrokeCap.Square)
                drawLine(c.brandOrange, corner, corner + Offset(dx * length, 0f), stroke, StrokeCap.Square)
            }
        }
    }
}

/** The app icon in miniature, for mock notifications. */
@Composable
private fun AppIconTile(size: androidx.compose.ui.unit.Dp = 36.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.225f)).background(BrandMarkColors.ink).padding(size * 0.08f),
    ) { BrandMark() }
}

@Composable
private fun RemindArt() {
    val c = ZeroBiteTheme.colors
    val type = ZeroBiteTheme.type
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Overline("Use first", Modifier.weight(1f))
                Text("2 more need attention", style = type.caption, color = c.textSecondary)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Spinach", style = type.title3.semibold(), color = c.textPrimary)
                    Text("1 bag · Fridge", style = type.caption, color = c.textSecondary)
                }
                Text("1", style = type.largeTitle.semibold().copy(fontSize = 40.sp), color = c.statusToday, modifier = Modifier.alignByBaseline())
                Text(" day", style = type.subheadline, color = c.statusToday, modifier = Modifier.alignByBaseline())
            }
            ShelfLifeBar(0.86f, c.statusToday)
            Row(Modifier.padding(top = Spacing.md), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MockButton("Used it", c.textPrimary, c.onInk, Modifier.weight(1f))
                MockButton("Freeze", c.chipFill, c.textPrimary, Modifier.weight(1f))
            }
        }
        AppCard(Modifier.padding(horizontal = Spacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                AppIconTile()
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row {
                        Text("Zero Bite", style = type.caption.semibold(), color = c.textSecondary, modifier = Modifier.weight(1f))
                        Text("now", style = type.caption2, color = c.textSecondary)
                    }
                    Text("Spinach goes off tomorrow", style = type.subheadline.semibold(), color = c.textPrimary)
                    Text("It's in 3 recipes you can make tonight.", style = type.caption, color = c.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun MockButton(text: String, background: Color, foreground: Color, modifier: Modifier) {
    Text(
        text,
        style = ZeroBiteTheme.type.subheadline.semibold(),
        color = foreground,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier.background(background, RoundedCornerShape(Radius.control)).padding(vertical = 8.dp),
    )
}

@Composable
private fun CookArt() {
    val c = ZeroBiteTheme.colors
    val type = ZeroBiteTheme.type
    AppCard(padding = 0.dp) {
        Box(Modifier.fillMaxWidth().height(120.dp).background(c.brandOrange.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Egg, contentDescription = null, tint = c.brandOrange, modifier = Modifier.size(48.dp))
        }
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text("Spinach and egg frittata", style = type.headline, color = c.textPrimary, modifier = Modifier.weight(1f))
                Text("92%", style = type.headline, color = c.brandOrange)
            }
            Text("20 min · serves 2 · uses 2 expiring", style = type.caption, color = c.textSecondary)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag("Spinach", tint = c.brandOrange)
                Tag("Eggs", tint = c.brandOrange)
                Tag("+3")
            }
        }
    }
}

@Composable
private fun RemindersArt() {
    val c = ZeroBiteTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ReminderRow("3 days before", "Greek yogurt is due on Friday", c.statusSoon)
        ReminderRow("On the day", "Use your spinach today", c.statusToday)
        ReminderRow("Day after", "Milk went past its date yesterday", c.statusExpired)
    }
}

@Composable
private fun ReminderRow(whenText: String, text: String, tint: Color) {
    AppCard(accent = tint) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            IconTile(Icons.Rounded.Notifications, tint = tint, size = 36.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Overline(whenText, tint = tint)
                Text(text, style = ZeroBiteTheme.type.subheadline.medium(), color = ZeroBiteTheme.colors.textPrimary)
            }
        }
    }
}
```

- [ ] **Step 5: Use it for first run and for "How Zero Bite works"**

In `coordination/AppCoordinator.kt`, add to `SheetDestination`:

```kotlin
    /** The onboarding explainer, replayed from Settings. */
    data object HowItWorks : SheetDestination
```

In `screens/root/RootScreen.kt`, replace the `AppFlowPhase.WELCOME ->` branch with:

```kotlin
            AppFlowPhase.WELCOME -> {
                val welcome = viewModel(key = "welcome-first-run") {
                    WelcomeViewModel(WelcomeMode.FIRST_RUN, dependencies.settings, dependencies.notifications, onComplete = { flow.transitionToMain() })
                }
                WelcomeScreen(welcome)
            }
```

with imports `androidx.lifecycle.viewmodel.compose.viewModel`, `com.mazesys.zerobite.screens.welcome.WelcomeMode`, `WelcomeScreen` and `WelcomeViewModel`. Delete `screens/root/WelcomePlaceholder.kt`.

In `screens/root/SheetHost.kt`, add the branch:

```kotlin
                    SheetDestination.HowItWorks -> {
                        val replay = viewModel {
                            WelcomeViewModel(WelcomeMode.REPLAY, dependencies.settings, dependencies.notifications, onComplete = { coordinator.dismissSheet() })
                        }
                        WelcomeScreen(replay)
                    }
```

with the same imports. Window insets: SheetHost's Box already applies `systemBarsPadding()`, so `WelcomeScreen` must not pad again. Remove `.systemBarsPadding()` from `WelcomeScreen`'s root `Column` (and its import). In `RootScreen`, call `WelcomeScreen(welcome, Modifier.systemBarsPadding())` with imports `androidx.compose.foundation.layout.systemBarsPadding` and `androidx.compose.ui.Modifier`.

- [ ] **Step 6: Run the whole suite and record**

```bash
cd android && ./gradlew :app:testDebugUnitTest && ./gradlew :app:recordRoborazziDebug --tests '*WelcomeScreenTest'; cd ..
```

Expected: every test PASSES, including the updated `RootScreenTest` first-run flow; `welcome_light.png` and `welcome_reminders_dark.png` recorded.

- [ ] **Step 7: Commit**

```bash
git add android/app
git commit -m "Add the Android onboarding flow and How Zero Bite works

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Settings tab

**Files:**
- Modify: `android/app/build.gradle.kts` (`buildFeatures { buildConfig = true }`)
- Modify: `ui/components/ChipFilter.kt` (extract `Chip`), `ui/components/CardSection.kt` (`ActionRow(enabled)`)
- Create: `ui/components/{AppStepper,AppSwitch}.kt`
- Create: `coordination/SettingsCoordinator.kt`; modify `coordination/AppCoordinator.kt` (adds `val settings`)
- Create: `screens/settings/{SettingsViewModel,SettingsScreen}.kt`; modify `screens/root/MainScreen.kt`; delete `screens/root/PlaceholderTab.kt`
- Test: `android/app/src/test/kotlin/com/mazesys/zerobite/screens/settings/{SettingsViewModelTest,SettingsScreenTest}.kt`

**Interfaces:**
- Consumes: Tasks 4–6; `Allergen` (`:core`).
- Produces:
  - Components: `Chip(text, selected, onClick, modifier)`, `ActionRow(..., enabled = true)`, `AppStepper(label, value, range, onChange, modifier)`, `SwitchRow(label, checked, onCheckedChange, modifier, enabled = true, testTag)`
  - `class SettingsCoordinator(parent)` with `confirm(title, message, actionTitle, isDestructive = true, action)`, `showInfo(title, message)`, `showHowItWorks()`; `AppCoordinator.settings`
  - `enum class ReminderState { ON, OFF, DENIED }`, `data class SettingsUiState(...)`
  - `class SettingsViewModel(dependencies, coordinator, appVersion)` with `refreshPermission`, `setNotificationsEnabled(enabled, requester)`, `setLeadDays`, `setNotifyTime`, `setSoonThresholdDays`, `setRecallChecksEnabled`, `toggleAllergen`, `sendTest`, `loadSampleData`, `clearUsedHistory`, `deleteEverything`, `showHowItWorks`
  - `SettingsScreen(viewModel, modifier)`

- [ ] **Step 1: Write the failing tests**

`screens/settings/SettingsViewModelTest.kt`:

```kotlin
package com.mazesys.zerobite.screens.settings

import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.coordination.SheetDestination
import com.mazesys.zerobite.core.model.Allergen
import com.mazesys.zerobite.data.SettingsSnapshot
import com.mazesys.zerobite.reminders.NotificationPermission
import com.mazesys.zerobite.testing.FakeAccess
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeNotifier
import com.mazesys.zerobite.testing.FakeRecipes
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.sampleFridge
import com.mazesys.zerobite.testing.testDependencies
import com.mazesys.zerobite.testing.testRecipes
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val app = AppCoordinator { TestClock.today }
    private val inventory = FakeInventoryRepository(sampleFridge())
    private val settings = FakeSettings()
    private val access = FakeAccess(NotificationPermission.NOT_ASKED)
    private val notifier = FakeNotifier()
    private val vm = SettingsViewModel(
        testDependencies(inventory = inventory, settings = settings, recipes = FakeRecipes(testRecipes()), access = access, notifier = notifier),
        app.settings,
        appVersion = "1.0 (1)",
    )

    @Test
    fun `notification state follows the system permission`() = runBlocking {
        assertEquals(ReminderState.OFF, vm.state.value.reminderState)
        settings.update { it.copy(notificationsEnabled = true) }
        access.current = NotificationPermission.GRANTED
        vm.refreshPermission()
        assertEquals(ReminderState.ON, vm.state.value.reminderState)
        access.current = NotificationPermission.DENIED // switched off in system settings
        vm.refreshPermission()
        assertEquals(ReminderState.DENIED, vm.state.value.reminderState)
    }

    @Test
    fun `turning reminders on asks, and explains a refusal`() {
        vm.setNotificationsEnabled(true) { access.current = NotificationPermission.GRANTED; true }
        assertTrue(settings.state.value.notificationsEnabled)
        assertEquals(ReminderState.ON, vm.state.value.reminderState)

        vm.setNotificationsEnabled(false) { true }
        assertFalse(settings.state.value.notificationsEnabled)

        access.current = NotificationPermission.DENIED
        vm.setNotificationsEnabled(true) { false }
        assertFalse(settings.state.value.notificationsEnabled)
        assertEquals("Notifications are off", app.alert?.title)
    }

    @Test
    fun `steppers clamp to one to fourteen days`() {
        vm.setLeadDays(0)
        assertEquals(1, settings.state.value.leadDays)
        vm.setLeadDays(99)
        assertEquals(14, settings.state.value.leadDays)
        vm.setSoonThresholdDays(-3)
        assertEquals(1, settings.state.value.soonThresholdDays)
        vm.setSoonThresholdDays(15)
        assertEquals(14, settings.state.value.soonThresholdDays)
    }

    @Test
    fun `reminder time accepts the whole day`() {
        vm.setNotifyTime(0, 0)
        assertEquals("00:00", vm.state.value.notifyTimeText)
        vm.setNotifyTime(23, 59)
        assertEquals("23:59", vm.state.value.notifyTimeText)
    }

    @Test
    fun `allergens and recall checks are saved`() {
        vm.toggleAllergen(Allergen.MILK)
        vm.toggleAllergen(Allergen.PEANUTS)
        vm.toggleAllergen(Allergen.MILK)
        assertEquals(setOf("peanuts"), settings.state.value.allergens)
        vm.setRecallChecksEnabled(false)
        assertFalse(settings.state.value.recallChecksEnabled)
    }

    @Test
    fun `data actions confirm before deleting`() = runBlocking {
        assertEquals(21, vm.state.value.activeCount)
        assertEquals(4, vm.state.value.recipeCount)
        inventory.markConsumed(listOf(inventory.current.first().id), TestClock.now)
        assertEquals(1, vm.state.value.usedCount)

        vm.clearUsedHistory()
        assertEquals("Clear used-up history?", app.alert?.title)
        assertEquals("Removes 1 items you've already marked as used.", app.alert?.message)
        app.alert!!.primary.action()
        assertEquals(0, vm.state.value.usedCount)
        assertEquals(20, vm.state.value.activeCount)

        vm.deleteEverything()
        assertEquals("Delete everything?", app.alert?.title)
        app.alert!!.primary.action()
        assertEquals(0, vm.state.value.activeCount)
    }

    @Test
    fun `test notification, sample food and how it works`() {
        vm.sendTest()
        assertEquals(1, notifier.tests)
        vm.showHowItWorks()
        assertEquals(SheetDestination.HowItWorks, app.presentedSheet?.destination)
        runBlocking { inventory.deleteAll() }
        vm.loadSampleData()
        assertEquals(21, vm.state.value.activeCount)
    }
}
```

`screens/settings/SettingsScreenTest.kt`:

```kotlin
package com.mazesys.zerobite.screens.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.reminders.LocalPermissionRequester
import com.mazesys.zerobite.reminders.NotificationPermission
import com.mazesys.zerobite.reminders.PermissionRequester
import com.mazesys.zerobite.testing.FakeAccess
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.sampleFridge
import com.mazesys.zerobite.testing.testDependencies
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsScreenTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val access = FakeAccess(NotificationPermission.NOT_ASKED)

    private fun show(dark: Boolean = false) {
        val app = AppCoordinator { TestClock.today }
        val vm = SettingsViewModel(
            testDependencies(inventory = FakeInventoryRepository(sampleFridge()), settings = FakeSettings(), access = access),
            app.settings, "1.0 (1)",
        )
        val grant = PermissionRequester { access.current = NotificationPermission.GRANTED; true }
        compose.setContent {
            ZeroBiteTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalPermissionRequester provides grant) { SettingsScreen(vm) }
            }
        }
    }

    @Test
    fun `reminders toggle asks and turns on`() {
        show()
        compose.onNode(hasText("Settings") and isHeading()).assertExists()
        compose.onNodeWithTag("settings.notificationsToggle").assertIsOff().performClick()
        compose.onNodeWithTag("settings.notificationsToggle").assertIsOn()
        compose.onNodeWithText("Remind me 3 days before").assertExists()
    }

    @Test
    fun `sections from iOS are all there`() {
        show()
        for (text in listOf("Food safety", "Your allergies", "“Expiring soon” means within 3 days", "In your fridge", "How Zero Bite works")) {
            compose.onNodeWithText(text).performScrollTo().assertExists()
        }
    }

    @Test
    fun `settings light`() {
        show()
        compose.onRoot().captureRoboImage("src/test/screenshots/settings_light.png")
    }

    @Test
    fun `settings dark`() {
        show(dark = true)
        compose.onRoot().captureRoboImage("src/test/screenshots/settings_dark.png")
    }
}
```

- [ ] **Step 2: Run to see them fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests '*Settings*Test'; cd ..`
Expected: compilation FAILS: unresolved `SettingsViewModel`, `ReminderState`, `app.settings`.

- [ ] **Step 3: Small component additions**

`ChipFilter.kt`: extract the chip so the allergen picker can reuse it. Add:

```kotlin
/** One single-select chip (ink when selected). */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ZeroBiteTheme.colors
    val style = ZeroBiteTheme.type.subheadline
    Text(
        text,
        style = if (selected) style.semibold() else style,
        color = if (selected) colors.onInk else colors.textSecondary,
        modifier = modifier
            .clip(RoundedCornerShape(Radius.control))
            .background(if (selected) colors.textPrimary else colors.chipFill)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}
```

and make `ChipFilter`'s item body `Chip(label(option), option == selected, onClick = { onSelect(option) })`.

`CardSection.kt`: give `ActionRow` an `enabled: Boolean = true` parameter (last). Apply `.alpha(if (enabled) 1f else 0.45f)` and pass `enabled = enabled` to its `clickable`. Import `androidx.compose.ui.draw.alpha`.

`ui/components/AppStepper.kt`:

```kotlin
package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme

/** Label with − / + buttons, clamped to `range` (iOS Stepper). */
@Composable
fun AppStepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(label, style = ZeroBiteTheme.type.subheadline, color = ZeroBiteTheme.colors.textPrimary, modifier = Modifier.weight(1f))
        StepButton(Icons.Rounded.Remove, "Decrease", enabled = value > range.first) { onChange(value - 1) }
        StepButton(Icons.Rounded.Add, "Increase", enabled = value < range.last) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(Radius.control))
            .background(ZeroBiteTheme.colors.chipFill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = ZeroBiteTheme.colors.textPrimary, modifier = Modifier.size(18.dp))
    }
}
```

`ui/components/AppSwitch.kt`:

```kotlin
package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.medium

/** Label with a switch tinted burnt orange (iOS Toggle with `.tint(BrandOrange)`). */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String = label,
) {
    val colors = ZeroBiteTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = ZeroBiteTheme.type.subheadline.medium(), color = colors.textPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.brandOrange,
                checkedThumbColor = Color.White,
                checkedBorderColor = colors.brandOrange,
                uncheckedTrackColor = colors.chipFill,
                uncheckedThumbColor = colors.textMuted,
                uncheckedBorderColor = colors.hairline,
            ),
        )
    }
}
```

- [ ] **Step 4: Settings coordinator and view model** (port of `SettingsViewModel.swift`; the alerts summary waits for Plan 4)

`coordination/SettingsCoordinator.kt`:

```kotlin
package com.mazesys.zerobite.coordination

/** Settings has no pushed routes yet; it surfaces confirmations and the explainer through the parent. */
class SettingsCoordinator(private val parent: AppCoordinator) {

    fun confirm(title: String, message: String, actionTitle: String, isDestructive: Boolean = true, action: () -> Unit) {
        parent.showAlert(
            AlertConfig(
                title = title,
                message = message,
                primary = AlertButton(actionTitle, isDestructive, action),
                secondary = AlertButton("Cancel"),
            ),
        )
    }

    fun showInfo(title: String, message: String) {
        parent.showAlert(AlertConfig(title = title, message = message, primary = AlertButton("OK")))
    }

    fun showHowItWorks() = parent.presentSheet(SheetDestination.HowItWorks)
}
```

In `AppCoordinator.kt`, add `val settings = SettingsCoordinator(this)` after `val recipes`.

`screens/settings/SettingsViewModel.kt`:

```kotlin
package com.mazesys.zerobite.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazesys.zerobite.AppDependencies
import com.mazesys.zerobite.coordination.SettingsCoordinator
import com.mazesys.zerobite.core.model.Allergen
import com.mazesys.zerobite.data.SampleData
import com.mazesys.zerobite.data.SettingsSnapshot
import com.mazesys.zerobite.reminders.NotificationPermission
import com.mazesys.zerobite.reminders.PermissionRequester
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ReminderState {
    ON,
    OFF,
    /** Refused at the system level; only system settings can change it. */
    DENIED,
}

data class SettingsUiState(
    val appVersion: String,
    val snapshot: SettingsSnapshot = SettingsSnapshot(),
    val permission: NotificationPermission = NotificationPermission.NOT_ASKED,
    val activeCount: Int = 0,
    val usedCount: Int = 0,
    val recipeCount: Int = 0,
    val isUpdatingNotifications: Boolean = false,
) {
    val reminderState: ReminderState
        get() = when {
            snapshot.notificationsEnabled && permission == NotificationPermission.GRANTED -> ReminderState.ON
            permission == NotificationPermission.DENIED -> ReminderState.DENIED
            else -> ReminderState.OFF
        }

    val notificationsEnabled: Boolean get() = reminderState == ReminderState.ON
    val notifyTimeText: String get() = "%02d:%02d".format(snapshot.notifyHour, snapshot.notifyMinute)
}

class SettingsViewModel(
    private val dependencies: AppDependencies,
    private val coordinator: SettingsCoordinator,
    appVersion: String,
) : ViewModel() {

    private val settings = dependencies.settings
    private val inventory = dependencies.inventory
    private val permission = MutableStateFlow(dependencies.notifications.permission())
    private val updating = MutableStateFlow(false)
    private val recipeCount = flow { emit(dependencies.recipes.recipes().size) }

    val state: StateFlow<SettingsUiState> =
        combine(settings.state, inventory.items, recipeCount, permission, updating) { s, items, recipes, p, busy ->
            SettingsUiState(
                appVersion = appVersion,
                snapshot = s,
                permission = p,
                activeCount = items.count { !it.isConsumed },
                usedCount = items.count { it.isConsumed },
                recipeCount = recipes,
                isUpdatingNotifications = busy,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState(appVersion))

    /** Call when the screen resumes: the permission may have changed in system settings. */
    fun refreshPermission() {
        permission.value = dependencies.notifications.permission()
    }

    fun setNotificationsEnabled(enabled: Boolean, requester: PermissionRequester) {
        viewModelScope.launch {
            updating.value = true
            if (enabled) {
                val granted = requester.requestNotifications()
                dependencies.notifications.markAsked()
                refreshPermission()
                settings.update { it.copy(notificationsEnabled = granted) }
                if (!granted) {
                    coordinator.showInfo(
                        "Notifications are off",
                        "Allow notifications for Zero Bite in your phone's settings to get expiry reminders.",
                    )
                }
            } else {
                settings.update { it.copy(notificationsEnabled = false) }
            }
            updating.value = false
        }
    }

    fun setLeadDays(days: Int) = update { it.copy(leadDays = days.coerceIn(1, 14)) }

    fun setNotifyTime(hour: Int, minute: Int) = update { it.copy(notifyHour = hour.coerceIn(0, 23), notifyMinute = minute.coerceIn(0, 59)) }

    fun setSoonThresholdDays(days: Int) = update { it.copy(soonThresholdDays = days.coerceIn(1, 14)) }

    fun setRecallChecksEnabled(enabled: Boolean) = update { it.copy(recallChecksEnabled = enabled) }

    fun toggleAllergen(allergen: Allergen) = update {
        it.copy(allergens = if (allergen.raw in it.allergens) it.allergens - allergen.raw else it.allergens + allergen.raw)
    }

    fun sendTest() = dependencies.notifier.sendTest()

    fun loadSampleData() {
        viewModelScope.launch {
            val clock = dependencies.clock
            SampleData.items(clock.today(), clock.now(), dependencies.ids::next).forEach { inventory.add(it) }
        }
    }

    fun clearUsedHistory() {
        viewModelScope.launch {
            val used = inventory.items.first().filter { it.isConsumed }
            if (used.isEmpty()) return@launch
            coordinator.confirm("Clear used-up history?", "Removes ${used.size} items you've already marked as used.", "Clear") {
                viewModelScope.launch { used.forEach { inventory.delete(it.id) } }
            }
        }
    }

    fun deleteEverything() {
        coordinator.confirm("Delete everything?", "This removes every item from your fridge. It can't be undone.", "Delete all") {
            viewModelScope.launch { inventory.deleteAll() }
        }
    }

    fun showHowItWorks() = coordinator.showHowItWorks()

    private fun update(transform: (SettingsSnapshot) -> SettingsSnapshot) {
        viewModelScope.launch { settings.update(transform) }
    }
}
```

- [ ] **Step 5: Settings screen** (follows `SettingsView.swift`. Adaptations: "Allow in Settings app" becomes "Allow in system settings"; the About footer drops "with Apple Vision"; the Food alerts row reads "Arrives in the next build" until Plan 4)

`screens/settings/SettingsScreen.kt`:

```kotlin
package com.mazesys.zerobite.screens.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazesys.zerobite.core.model.Allergen
import com.mazesys.zerobite.reminders.LocalPermissionRequester
import com.mazesys.zerobite.ui.components.ActionRow
import com.mazesys.zerobite.ui.components.AppStepper
import com.mazesys.zerobite.ui.components.CardSection
import com.mazesys.zerobite.ui.components.Chip
import com.mazesys.zerobite.ui.components.InfoRow
import com.mazesys.zerobite.ui.components.Overline
import com.mazesys.zerobite.ui.components.ScreenHeader
import com.mazesys.zerobite.ui.components.SwitchRow
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.medium

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val requester = LocalPermissionRequester.current
    val context = LocalContext.current
    val colors = ZeroBiteTheme.colors
    var pickingTime by remember { mutableStateOf(false) }
    val s = ui.snapshot

    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermission()
        onPauseOrDispose {}
    }

    Column(
        modifier.fillMaxSize().background(colors.backgroundPrimary).verticalScroll(rememberScrollState())
            .padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        ScreenHeader(title = "Settings", overline = "Zero Bite ${ui.appVersion}")

        CardSection(
            title = "Reminders",
            footer = "You'll get a heads-up before the date, one on the day, and one if it slips past.",
            rows = buildList<@Composable () -> Unit> {
                add {
                    SwitchRow(
                        "Expiry reminders", ui.notificationsEnabled,
                        onCheckedChange = { viewModel.setNotificationsEnabled(it, requester) },
                        enabled = !ui.isUpdatingNotifications, testTag = "settings.notificationsToggle",
                    )
                }
                if (ui.reminderState == ReminderState.DENIED) {
                    add {
                        ActionRow("Allow in system settings", onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        }, icon = Icons.Rounded.Settings, tint = colors.brandOrange)
                    }
                }
                add { AppStepper("Remind me ${s.leadDays} days before", s.leadDays, 1..14, viewModel::setLeadDays) }
                add {
                    InfoRow("At", icon = Icons.Rounded.Schedule) {
                        Text(
                            ui.notifyTimeText,
                            style = ZeroBiteTheme.type.subheadline.medium(),
                            color = colors.textPrimary,
                            modifier = Modifier.clip(RoundedCornerShape(Radius.control)).background(colors.chipFill)
                                .clickable { pickingTime = true }.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                add {
                    ActionRow("Send a test notification", viewModel::sendTest, icon = Icons.Rounded.Notifications,
                        tint = colors.brandOrange, enabled = ui.notificationsEnabled)
                }
            },
        )

        CardSection(
            title = "Food safety",
            footer = "Checks the Food Standards Agency for UK recalls and tells you if something in your fridge might be affected.",
            rows = listOf<@Composable () -> Unit>(
                { SwitchRow("Check UK food recalls", s.recallChecksEnabled, viewModel::setRecallChecksEnabled, testTag = "settings.recallsToggle") },
                { InfoRow("Food alerts", "Arrives in the next build", icon = Icons.Rounded.Warning) },
            ),
        )

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Overline("Your allergies")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Allergen.entries.forEach { allergen ->
                    Chip(allergen.label, allergen.raw in s.allergens, onClick = { viewModel.toggleAllergen(allergen) })
                }
            }
            Text("Allergy alerts for these show under Food alerts → For you.", style = ZeroBiteTheme.type.caption, color = colors.textSecondary)
        }

        CardSection(
            title = "Fridge",
            footer = "Recipes that use food inside this window are ranked higher.",
            rows = listOf<@Composable () -> Unit>({
                AppStepper("“Expiring soon” means within ${s.soonThresholdDays} days", s.soonThresholdDays, 1..14, viewModel::setSoonThresholdDays)
            }),
        )

        CardSection(
            title = "Data",
            rows = listOf<@Composable () -> Unit>(
                { InfoRow("In your fridge", "${ui.activeCount}", icon = Icons.Rounded.Kitchen) },
                { InfoRow("Used up", "${ui.usedCount}", icon = Icons.Rounded.Check) },
                { ActionRow("Add sample food", viewModel::loadSampleData, icon = Icons.Rounded.AutoAwesome) },
                { ActionRow("Clear used-up history", viewModel::clearUsedHistory, icon = Icons.Rounded.History, enabled = ui.usedCount > 0) },
                {
                    ActionRow("Delete everything", viewModel::deleteEverything, icon = Icons.Rounded.Delete, tint = colors.statusExpired,
                        enabled = ui.activeCount + ui.usedCount > 0)
                },
            ),
        )

        CardSection(
            title = "About",
            footer = "Dates and barcodes are read on your device. Product names come from Open Food Facts. " +
                "Food alerts come from the Food Standards Agency under the Open Government Licence v3.0.",
            rows = listOf<@Composable () -> Unit>(
                { ActionRow("How Zero Bite works", viewModel::showHowItWorks, icon = Icons.AutoMirrored.Rounded.MenuBook, tint = colors.brandOrange) },
                { InfoRow("Version", ui.appVersion) },
                { InfoRow("Recipes", "${ui.recipeCount}") },
            ),
        )
    }

    if (pickingTime) ReminderTimeDialog(s.notifyHour, s.notifyMinute, onDismiss = { pickingTime = false }) { h, m ->
        pickingTime = false
        viewModel.setNotifyTime(h, m)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onPicked: (Int, Int) -> Unit) {
    val colors = ZeroBiteTheme.colors
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPicked(state.hour, state.minute) }) { Text("Done", color = colors.textPrimary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textSecondary) } },
        text = {
            TimePicker(
                state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = colors.chipFill,
                    selectorColor = colors.brandOrange,
                    timeSelectorSelectedContainerColor = colors.brandOrange.copy(alpha = 0.18f),
                    timeSelectorUnselectedContainerColor = colors.chipFill,
                ),
            )
        },
        containerColor = colors.backgroundPrimary,
        shape = RoundedCornerShape(Radius.card),
    )
}
```

- [ ] **Step 6: Wire the tab**

In `app/build.gradle.kts`, change `buildFeatures { compose = true }` to `buildFeatures { compose = true; buildConfig = true }`.

In `MainScreen.kt`:
- Add:
  ```kotlin
      val settingsScreen = viewModel(key = "settings") {
          SettingsViewModel(dependencies, coordinator.settings, "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
      }
  ```
- Replace the `AppTab.SETTINGS ->` branch with `AppTab.SETTINGS -> SettingsScreen(settingsScreen)`.
- Import `com.mazesys.zerobite.BuildConfig`, `com.mazesys.zerobite.screens.settings.SettingsScreen` and `SettingsViewModel`.

Delete `screens/root/PlaceholderTab.kt`. In `RootScreenTest.back on another tab returns to the fridge before leaving`, nothing changes (it uses the tag).

- [ ] **Step 7: Run the whole suite and record**

```bash
cd android && ./gradlew :app:testDebugUnitTest && ./gradlew :app:recordRoborazziDebug --tests '*SettingsScreenTest'; cd ..
```

Expected: every test PASSES; `settings_light.png` and `settings_dark.png` recorded.

- [ ] **Step 8: Commit**

```bash
git add android/app
git commit -m "Add the Android Settings tab

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: On the device, smoke test and docs

**Files:**
- Modify: `android/app/src/androidTest/kotlin/com/mazesys/zerobite/SmokeTest.kt`, `README.md`

- [ ] **Step 1: Update the smoke test for the real onboarding**

In `SmokeTest.addAnItemByHand`, replace the welcome handling with:

```kotlin
        compose.waitUntil(10_000) { exists("welcome.skip") || exists("inventory.addButton") }
        if (exists("welcome.skip")) {
            compose.onNodeWithTag("welcome.skip").performClick()
            compose.waitUntil(5_000) { exists("welcome.notNow") }
            compose.onNodeWithTag("welcome.notNow").performClick()
        }
```

- [ ] **Step 2: Run on the emulator**

Boot `Medium_Phone_API_36.0` as in Plan 2 Task 14, then run `cd android && ./gradlew :app:connectedDebugAndroidTest; cd ..`.
Expected: `SmokeTest.addAnItemByHand` passes.

- [ ] **Step 3: Check reminders for real**

```bash
ADB=~/Library/Android/sdk/platform-tools/adb
cd android && ./gradlew :app:installDebug; cd ..
$ADB shell pm clear com.mazesys.zerobite
$ADB shell pm grant com.mazesys.zerobite android.permission.POST_NOTIFICATIONS
$ADB shell am start -n com.mazesys.zerobite/.MainActivity
```

Then on the device:
1. Go through onboarding with **Turn on reminders**, then **Try it with sample food**.
2. `$ADB shell dumpsys alarm | grep -c com.mazesys.zerobite` should be > 0 (reminders scheduled).
3. Settings → **Send a test notification**. `$ADB shell dumpsys notification --noredact | grep -A2 "zerobite.test"` should show "Zero Bite reminders are on".
4. Deep link: `$ADB shell am start -n com.mazesys.zerobite/.MainActivity --es com.mazesys.zerobite.extra.ITEM_ID <an item id>` opens that item. Get an id from `$ADB shell run-as com.mazesys.zerobite sqlite3 databases/zerobite.db "select id from food_items limit 1"`, or skip this step if `sqlite3` is missing on the image (DeepLinkTest covers it).
5. Capture Recipes, a recipe, Settings and the welcome pages in light and dark (`$ADB exec-out screencap -p`). Compare them with the iOS App Store screenshots and fix visible mismatches. Each fix needs a test or an inspected re-recorded screenshot.

- [ ] **Step 4: Gate**

Run: `cd android && ./gradlew :core:check :app:testDebugUnitTest :app:lintDebug :app:assembleDebug; cd ..`
Expected: `BUILD SUCCESSFUL`, with no lint errors.

- [ ] **Step 5: Docs**

In `README.md`'s Android section, replace "Scanning, reminders, recipes, alerts and settings arrive in the next plan (see `specs/plans/`)." with "Label scanning and UK food alerts arrive in the next plan (see `specs/plans/`); everything else matches iOS."

- [ ] **Step 6: Commit**

```bash
git add android/app/src/androidTest README.md
git commit -m "Run the Android smoke test through the real onboarding

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## Spec coverage (Plan 3 scope)

| Spec section | Covered by |
|---|---|
| §6 Reminders: shared planner → AlarmManager inexact alarms, receiver posts, replan on inventory/prefs/boot/update/time/zone, POST_NOTIFICATIONS from the welcome step | Tasks 4, 5, 6 |
| §5 Deep links from notification taps via `AppCoordinator.showItem` | Task 5 |
| Recipes tab, recipe detail, "I cooked this", recipes on item detail (iOS parity) | Tasks 1–3 |
| Settings (iOS parity, adapted for Android permissions) | Task 7 |
| Onboarding + replay (iOS parity) | Task 6 |
| §8 VM tests with fakes, Robolectric for alarms/notifications/receivers, screenshots, smoke test | Tasks 1–8 |
| §6 Scanning (CameraX + ML Kit), FSA alerts UI + recall notifications | Plan 4 |
| §7 Launcher icon, §9 signing + Firebase | Plan 5 |
