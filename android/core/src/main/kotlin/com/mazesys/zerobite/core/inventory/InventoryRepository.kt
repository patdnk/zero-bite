package com.mazesys.zerobite.core.inventory

import com.mazesys.zerobite.core.model.FoodItem
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/** The user's food. Implemented with Room on Android; SwiftData stays on iOS. */
interface InventoryRepository {
    /** Every item, consumed or not, soonest expiry first. Emits again after every change. */
    val items: Flow<List<FoodItem>>

    suspend fun item(id: String): FoodItem?
    suspend fun add(item: FoodItem)
    suspend fun update(item: FoodItem)
    suspend fun markConsumed(ids: List<String>, at: Instant)
    suspend fun restore(id: String)
    suspend fun delete(id: String)
    suspend fun deleteAll()
}
