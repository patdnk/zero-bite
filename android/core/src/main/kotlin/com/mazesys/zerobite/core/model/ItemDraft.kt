package com.mazesys.zerobite.core.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.time.Instant

/** Editing buffer for the add/edit item form; nothing is saved until the user commits. */
data class ItemDraft(
    val name: String = "",
    val category: FoodCategory = FoodCategory.OTHER,
    val storage: StorageLocation = StorageLocation.FRIDGE,
    val quantity: Double = 1.0,
    val unit: String = "pcs",
    val expiresOn: LocalDate,
    val notes: String = "",
    val barcode: String? = null,
    val brand: String? = null,
) {
    val trimmedName: String get() = name.trim()
    val isValid: Boolean get() = trimmedName.isNotEmpty() && quantity > 0

    private val resolvedUnit: String get() = if (unit.trim().isEmpty()) "pcs" else unit

    fun makeItem(id: String, now: Instant): FoodItem = FoodItem(
        id = id,
        name = trimmedName,
        quantity = quantity,
        unit = resolvedUnit,
        category = category,
        storage = storage,
        addedAt = now,
        expiresOn = expiresOn,
        barcode = barcode,
        brand = brand,
        notes = notes,
    )

    /** The edited item: same id, added date and consumed state. */
    fun applyTo(item: FoodItem): FoodItem = item.copy(
        name = trimmedName,
        quantity = quantity,
        unit = resolvedUnit,
        category = category,
        storage = storage,
        expiresOn = expiresOn,
        notes = notes,
        barcode = barcode,
        brand = brand,
    )

    companion object {
        val unitSuggestions = listOf("pcs", "g", "kg", "ml", "l", "pack", "slices")

        fun new(today: LocalDate): ItemDraft = ItemDraft(expiresOn = today.plus(7, DateTimeUnit.DAY))

        fun from(item: FoodItem): ItemDraft = ItemDraft(
            name = item.name,
            category = item.category,
            storage = item.storage,
            quantity = item.quantity,
            unit = item.unit,
            expiresOn = item.expiresOn,
            notes = item.notes,
            barcode = item.barcode,
            brand = item.brand,
        )
    }
}
