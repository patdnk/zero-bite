package com.mazesys.zerobite.ui.text

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.mazesys.zerobite.R
import com.mazesys.zerobite.core.model.Allergen
import com.mazesys.zerobite.core.model.ExpiryStatus
import com.mazesys.zerobite.core.model.FoodCategory
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.StorageLocation
import com.mazesys.zerobite.core.model.formatQuantity
import com.mazesys.zerobite.ui.format.DateText
import com.mazesys.zerobite.ui.format.appLocale
import kotlinx.datetime.LocalDate
import java.text.DecimalFormatSymbols
import java.util.Locale

// Display text for core models. Core stays language-free; the app maps its values to resources.

@get:StringRes
val FoodCategory.labelRes: Int
    get() = when (this) {
        FoodCategory.DAIRY -> R.string.category_dairy
        FoodCategory.MEAT -> R.string.category_meat
        FoodCategory.FISH -> R.string.category_fish
        FoodCategory.VEGETABLES -> R.string.category_vegetables
        FoodCategory.FRUIT -> R.string.category_fruit
        FoodCategory.BAKERY -> R.string.category_bakery
        FoodCategory.GRAINS -> R.string.category_grains
        FoodCategory.CONDIMENTS -> R.string.category_condiments
        FoodCategory.DRINKS -> R.string.category_drinks
        FoodCategory.LEFTOVERS -> R.string.category_leftovers
        FoodCategory.OTHER -> R.string.category_other
    }

@get:StringRes
val StorageLocation.labelRes: Int
    get() = when (this) {
        StorageLocation.FRIDGE -> R.string.storage_fridge
        StorageLocation.FREEZER -> R.string.storage_freezer
        StorageLocation.PANTRY -> R.string.storage_pantry
    }

/** Section headings on the Fridge: "Expired", "Expires today", "Expiring soon", "Fresh". */
@get:StringRes
val ExpiryStatus.titleRes: Int
    get() = when (this) {
        ExpiryStatus.EXPIRED -> R.string.status_expired
        ExpiryStatus.TODAY -> R.string.status_today
        ExpiryStatus.SOON -> R.string.status_soon
        ExpiryStatus.FRESH -> R.string.status_fresh
    }

@get:StringRes
val Allergen.labelRes: Int
    get() = when (this) {
        Allergen.CELERY -> R.string.allergen_celery
        Allergen.GLUTEN -> R.string.allergen_gluten
        Allergen.CRUSTACEANS -> R.string.allergen_crustaceans
        Allergen.EGG -> R.string.allergen_egg
        Allergen.FISH -> R.string.allergen_fish
        Allergen.LUPIN -> R.string.allergen_lupin
        Allergen.MILK -> R.string.allergen_milk
        Allergen.MOLLUSCS -> R.string.allergen_molluscs
        Allergen.MUSTARD -> R.string.allergen_mustard
        Allergen.NUTS -> R.string.allergen_nuts
        Allergen.PEANUTS -> R.string.allergen_peanuts
        Allergen.SESAME -> R.string.allergen_sesame
        Allergen.SOYA -> R.string.allergen_soya
        Allergen.SULPHITES -> R.string.allergen_sulphites
    }

/** Stored unit codes from the form's suggestions that read differently per language ("pcs" → "szt."). */
@StringRes
fun unitLabelRes(unit: String): Int? = when (unit) {
    "pcs" -> R.string.unit_pcs
    "pack" -> R.string.unit_pack
    "slices" -> R.string.unit_slices
    else -> null
}

@Composable
@ReadOnlyComposable
fun unitLabel(unit: String): String = unitLabelRes(unit)?.let { stringResource(it) } ?: unit

/** Up to two decimals with the language's decimal separator: "1.5" / "1,5". */
fun formatQuantity(quantity: Double, locale: Locale): String {
    val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    return formatQuantity(quantity).replace('.', separator)
}

/** "500 g", "2 pcs" / "2 szt.". */
@Composable
@ReadOnlyComposable
fun FoodItem.quantityLabel(): String = "${formatQuantity(quantity, appLocale())} ${unitLabel(unit)}"

/** "500 g · Fridge". */
@Composable
@ReadOnlyComposable
fun FoodItem.quantityAndStorage(): String = stringResource(R.string.quantity_and_storage, quantityLabel(), stringResource(storage.labelRes))

/** "Expires in 3 days", "Expired yesterday", ... */
@Composable
@ReadOnlyComposable
fun FoodItem.expiryText(today: LocalDate): String {
    val days = daysUntilExpiry(today)
    return when {
        days < -1 -> pluralStringResource(R.plurals.expiry_expired_days_ago, -days, -days)
        days == -1 -> stringResource(R.string.expiry_expired_yesterday)
        days == 0 -> stringResource(R.string.expiry_expires_today)
        days == 1 -> stringResource(R.string.expiry_expires_tomorrow)
        else -> pluralStringResource(R.plurals.expiry_expires_in_days, days, days)
    }
}

/** Compact label for badges: "Expired", "Today", "2d", "12 Sep". */
@Composable
@ReadOnlyComposable
fun FoodItem.shortExpiryText(today: LocalDate): String {
    val days = daysUntilExpiry(today)
    return when {
        days < 0 -> stringResource(R.string.short_expired)
        days == 0 -> stringResource(R.string.short_today)
        days == 1 -> stringResource(R.string.short_tomorrow)
        days <= 14 -> stringResource(R.string.short_days, days)
        else -> DateText.dayMonth(expiresOn, appLocale())
    }
}
