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
