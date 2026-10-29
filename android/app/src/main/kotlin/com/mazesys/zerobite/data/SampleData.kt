package com.mazesys.zerobite.data

import androidx.annotation.StringRes
import com.mazesys.zerobite.R
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
import com.mazesys.zerobite.ui.text.Strings
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.time.Instant

/** A realistic fridge for "Try it with sample food", previews and screenshots, in the app language. */
object SampleData {

    fun items(today: LocalDate, now: Instant, newId: () -> String, strings: Strings): List<FoodItem> {
        fun item(@StringRes name: Int, quantity: Double, unit: String, category: FoodCategory, days: Int, storage: StorageLocation = FRIDGE) =
            FoodItem(
                id = newId(), name = strings.get(name), quantity = quantity, unit = unit, category = category,
                storage = storage, addedAt = now, expiresOn = today.plus(days, DateTimeUnit.DAY),
            )
        // "pcs" and "pack" are stored as codes the UI translates; the rest are written in the app language.
        val bag = strings.get(R.string.sample_unit_bag)
        return listOf(
            item(R.string.sample_greek_yogurt, 500.0, "g", DAIRY, -1),
            item(R.string.sample_spinach, 1.0, bag, VEGETABLES, 0),
            item(R.string.sample_chicken_breast, 2.0, "pcs", MEAT, 1),
            item(R.string.sample_milk, 2.0, strings.get(R.string.sample_unit_pints), DAIRY, 2),
            item(R.string.sample_mushrooms, 250.0, "g", VEGETABLES, 2),
            item(R.string.sample_sourdough, 1.0, strings.get(R.string.sample_unit_loaf), BAKERY, 2),
            item(R.string.sample_cherry_tomatoes, 1.0, strings.get(R.string.sample_unit_punnet), VEGETABLES, 3),
            item(R.string.sample_red_pepper, 2.0, "pcs", VEGETABLES, 4),
            item(R.string.sample_bacon, 1.0, "pack", MEAT, 5),
            item(R.string.sample_lemon, 3.0, "pcs", FRUIT, 8),
            item(R.string.sample_bananas, 5.0, "pcs", FRUIT, 4),
            item(R.string.sample_carrots, 6.0, "pcs", VEGETABLES, 10),
            item(R.string.sample_onion, 3.0, "pcs", VEGETABLES, 12),
            item(R.string.sample_eggs, 6.0, "pcs", DAIRY, 12),
            item(R.string.sample_cheddar, 350.0, "g", DAIRY, 14),
            item(R.string.sample_potatoes, 1.5, "kg", VEGETABLES, 14, PANTRY),
            item(R.string.sample_garlic, 1.0, strings.get(R.string.sample_unit_bulb), VEGETABLES, 30, PANTRY),
            item(R.string.sample_butter, 250.0, "g", DAIRY, 30),
            item(R.string.sample_salmon, 2.0, "pcs", FISH, 60, FREEZER),
            item(R.string.sample_rice, 1.0, "kg", GRAINS, 300, PANTRY),
            item(R.string.sample_spaghetti, 500.0, "g", GRAINS, 400, PANTRY),
        )
    }
}
