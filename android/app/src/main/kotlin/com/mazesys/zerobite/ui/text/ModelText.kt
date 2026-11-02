package com.mazesys.zerobite.ui.text

import androidx.annotation.PluralsRes
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
import com.mazesys.zerobite.core.model.ItemDraft
import com.mazesys.zerobite.core.model.StorageLocation
import com.mazesys.zerobite.core.model.formatQuantity
import com.mazesys.zerobite.ui.format.DateText
import com.mazesys.zerobite.ui.format.appLocale
import kotlinx.datetime.LocalDate
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.round

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

/** Button that returns a used-up item to where it was stored. */
@get:StringRes
val StorageLocation.putBackRes: Int
    get() = when (this) {
        StorageLocation.FRIDGE -> R.string.detail_put_back_fridge
        StorageLocation.FREEZER -> R.string.detail_put_back_freezer
        StorageLocation.PANTRY -> R.string.detail_put_back_pantry
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

/**
 * How a stored unit code reads in the app language. Codes come from the form's suggestions and
 * the sample food; anything else is text the user typed and is shown as is. Count nouns that
 * inflect in Polish agree with the number ("1 plaster", "5 plastrów"); fractions take [fraction].
 */
private class UnitText(
    @StringRes val label: Int,
    @PluralsRes val counted: Int? = null,
    @StringRes val fraction: Int? = null,
)

private val UNITS = mapOf(
    "pcs" to UnitText(R.string.unit_pcs),
    "pack" to UnitText(R.string.unit_pack),
    "bag" to UnitText(R.string.unit_bag),
    "punnet" to UnitText(R.string.unit_punnet),
    "slices" to UnitText(R.string.unit_slices, R.plurals.unit_slices_count, R.string.unit_slices_fraction),
    "pints" to UnitText(R.string.unit_pints, R.plurals.unit_pints_count, R.string.unit_pints_fraction),
    "loaf" to UnitText(R.string.unit_loaf, R.plurals.unit_loaf_count, R.string.unit_loaf_fraction),
    "bulb" to UnitText(R.string.unit_bulb, R.plurals.unit_bulb_count, R.string.unit_bulb_fraction),
)

/** The unit on its own, for the form's chips and field: "pcs" → "szt.". */
@Composable
@ReadOnlyComposable
fun unitLabel(unit: String): String = UNITS[unit]?.let { stringResource(it.label) } ?: unit

/** Up to two decimals with the language's decimal separator: "1.5" / "1,5". */
fun formatQuantity(quantity: Double, locale: Locale): String {
    val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    return formatQuantity(quantity).replace('.', separator)
}

/** "500 g", "2 pcs" / "2 szt.", "5 plastrów". */
@Composable
@ReadOnlyComposable
fun FoodItem.quantityLabel(): String = "${formatQuantity(quantity, appLocale())} ${unitLabel(unit, quantity)}"

/** The unit as it reads after [quantity]: "1 plaster", "5 plastrów", "1,5 plastra". */
@Composable
@ReadOnlyComposable
fun unitLabel(unit: String, quantity: Double): String {
    val text = UNITS[unit] ?: return unit
    val counted = text.counted ?: return stringResource(text.label)
    val hundredths = round(quantity * 100).toLong()
    return if (hundredths % 100 == 0L && hundredths / 100 <= Int.MAX_VALUE) {
        val whole = (hundredths / 100).toInt()
        pluralStringResource(counted, whole, whole)
    } else {
        // Fractions take the genitive singular, which no whole number selects in Polish.
        stringResource(text.fraction ?: text.label)
    }
}

/**
 * Maps a typed unit label back to its stored code ("szt." → "pcs"); anything else is kept as typed.
 * In Polish "pack", "bag" and "punnet" all read "opak.", so it always stores "pack" (the first match).
 */
@Composable
@ReadOnlyComposable
fun unitCodes(): Map<String, String> {
    val codes = HashMap<String, String>()
    for (code in ItemDraft.unitSuggestions + UNITS.keys) codes.putIfAbsent(unitLabel(code), code)
    return codes
}

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
