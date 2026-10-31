package com.mazesys.zerobite.screens.recipes

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.data.BundledRecipeRepository
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A bundled recipe in Polish: overlay text, Polish tags, and "Jajka" matching the eggs. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pl-w411dp-h891dp-xxhdpi")
class RecipeDetailScreenPolishTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the omelette reads in polish`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val eggs = FoodItem(id = "eggs", name = "Jajka", addedAt = TestClock.now, expiresOn = TestClock.today.plus(1, DateTimeUnit.DAY))
        val vm = RecipeDetailViewModel(
            "classic-omelette", FakeInventoryRepository(listOf(eggs)), FakeSettings(),
            BundledRecipeRepository(context.assets, MutableStateFlow("pl")), TestClock.fixed(),
            onShowItem = {}, onCooked = {},
        )
        compose.setContent { ZeroBiteTheme { RecipeDetailScreen(vm, onBack = {}) } }

        // The catalogue loads on the IO dispatcher.
        compose.waitUntil(5_000) { vm.state.value.recipe != null }
        compose.waitForIdle()
        compose.onNodeWithText("Klasyczny omlet z serem").assertExists()
        compose.onNodeWithText("śniadanie").assertExists()
        compose.onNodeWithText("3 jajka").assertExists()
        compose.onNodeWithText("Masz: Jajka · Jutro", substring = true).assertExists()
        compose.onNodeWithText("1 łyżka masła").assertExists()
        compose.onNodeWithText("Brakuje: masła.").assertExists()
        compose.onNodeWithText("Roztrzep jajka z odrobiną mleka, solą i pieprzem.").assertExists()
    }
}
