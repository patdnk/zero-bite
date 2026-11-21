package com.mazesys.zerobite.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mazesys.zerobite.core.model.FoodCategory
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.StorageLocation
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * Row for one item. Dates are stored as ISO text (sorts correctly) and instants as epoch
 * millis, so no type converters are needed. Category and storage use the iOS raw values.
 */
@Entity(tableName = "food_items", indices = [Index("expiresOn")])
data class FoodItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val quantity: Double,
    val unit: String,
    val category: String,
    val storage: String,
    val addedAt: Long,
    val expiresOn: String,
    val barcode: String?,
    val brand: String?,
    val notes: String,
    val consumedAt: Long?,
) {
    fun toModel(): FoodItem = FoodItem(
        id = id,
        name = name,
        quantity = quantity,
        unit = unit,
        category = FoodCategory.fromRaw(category),
        storage = StorageLocation.fromRaw(storage),
        addedAt = Instant.fromEpochMilliseconds(addedAt),
        expiresOn = LocalDate.parse(expiresOn),
        barcode = barcode,
        brand = brand,
        notes = notes,
        consumedAt = consumedAt?.let(Instant::fromEpochMilliseconds),
    )

    companion object {
        fun from(item: FoodItem) = FoodItemEntity(
            id = item.id,
            name = item.name,
            quantity = item.quantity,
            unit = item.unit,
            category = item.category.raw,
            storage = item.storage.raw,
            addedAt = item.addedAt.toEpochMilliseconds(),
            expiresOn = item.expiresOn.toString(),
            barcode = item.barcode,
            brand = item.brand,
            notes = item.notes,
            consumedAt = item.consumedAt?.toEpochMilliseconds(),
        )
    }
}
