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
