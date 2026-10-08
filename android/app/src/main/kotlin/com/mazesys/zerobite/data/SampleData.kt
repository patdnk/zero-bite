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
