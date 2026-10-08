package com.mazesys.zerobite.core.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
import kotlin.math.round
import kotlin.time.Instant

enum class FoodCategory(val label: String, val defaultShelfLifeDays: Int) {
    DAIRY("Dairy & eggs", 7),
    MEAT("Meat", 3),
    FISH("Fish & seafood", 2),
    VEGETABLES("Vegetables", 7),
    FRUIT("Fruit", 6),
    BAKERY("Bakery", 4),
    GRAINS("Grains & pasta", 180),
    CONDIMENTS("Condiments", 90),
    DRINKS("Drinks", 30),
    LEFTOVERS("Leftovers", 3),
    OTHER("Other", 14);

    /** Stored value, identical to the iOS raw value. */
    val raw: String get() = name.lowercase()

    companion object {
        fun fromRaw(raw: String): FoodCategory = entries.firstOrNull { it.raw == raw } ?: OTHER
    }
}

enum class StorageLocation(val label: String) {
    FRIDGE("Fridge"),
    FREEZER("Freezer"),
    PANTRY("Cupboard");

    val raw: String get() = name.lowercase()

    companion object {
        fun fromRaw(raw: String): StorageLocation = entries.firstOrNull { it.raw == raw } ?: FRIDGE
    }
}

/** Lifecycle of an item relative to its expiry date, declared from most to least urgent. */
enum class ExpiryStatus(val sectionTitle: String) {
    EXPIRED("Expired"),
    TODAY("Expires today"),
    SOON("Expiring soon"),
    FRESH("Fresh");

    /** Expired, expiring today, or within the "soon" window. */
    val needsAttention: Boolean get() = this != FRESH
}

/** A single thing in the fridge, freezer or cupboard. */
data class FoodItem(
    val id: String,
    val name: String,
    val quantity: Double = 1.0,
    val unit: String = "pcs",
    val category: FoodCategory = FoodCategory.OTHER,
    val storage: StorageLocation = StorageLocation.FRIDGE,
    val addedAt: Instant,
    val expiresOn: LocalDate,
    val barcode: String? = null,
    /** Brand from the barcode lookup; helps match product recalls. */
    val brand: String? = null,
    val notes: String = "",
    val consumedAt: Instant? = null,
) {
    val isConsumed: Boolean get() = consumedAt != null

    val quantityText: String get() = "${formatQuantity(quantity)} $unit"

    /** Whole calendar days until expiry. Negative when already expired. */
    fun daysUntilExpiry(today: LocalDate): Int = today.daysUntil(expiresOn)

    fun status(today: LocalDate, soonThresholdDays: Int = 3): ExpiryStatus {
        val days = daysUntilExpiry(today)
        return when {
            days < 0 -> ExpiryStatus.EXPIRED
            days == 0 -> ExpiryStatus.TODAY
            days <= soonThresholdDays -> ExpiryStatus.SOON
            else -> ExpiryStatus.FRESH
        }
    }

    /** "Expires in 3 days", "Expired yesterday", ... */
    fun expiryDescription(today: LocalDate): String {
        val days = daysUntilExpiry(today)
        return when {
            days < -1 -> "Expired ${-days} days ago"
            days == -1 -> "Expired yesterday"
            days == 0 -> "Expires today"
            days == 1 -> "Expires tomorrow"
            else -> "Expires in $days days"
        }
    }

    /** Compact label for badges: "Expired", "Today", "2d", "12 Sep". */
    fun shortExpiryLabel(today: LocalDate): String {
        val days = daysUntilExpiry(today)
        return when {
            days < 0 -> "Expired"
            days == 0 -> "Today"
            days == 1 -> "Tomorrow"
            days <= 14 -> "${days}d"
            else -> "${expiresOn.day} ${MONTH_ABBREVIATIONS[expiresOn.month.number - 1]}"
        }
    }
}

internal val MONTH_ABBREVIATIONS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** Up to two decimal places, no trailing zeros: 1 → "1", 0.5 → "0.5", 1.25 → "1.25". */
fun formatQuantity(quantity: Double): String {
    val hundredths = round(quantity * 100).toLong()
    val whole = hundredths / 100
    val fraction = hundredths % 100
    return when {
        fraction == 0L -> "$whole"
        fraction % 10 == 0L -> "$whole.${fraction / 10}"
        else -> "$whole.${fraction.toString().padStart(2, '0')}"
    }
}
