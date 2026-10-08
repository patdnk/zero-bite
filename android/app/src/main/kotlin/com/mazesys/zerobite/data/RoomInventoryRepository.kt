package com.mazesys.zerobite.data

import com.mazesys.zerobite.core.inventory.InventoryRepository
import com.mazesys.zerobite.core.model.FoodItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

class RoomInventoryRepository(private val dao: FoodItemDao) : InventoryRepository {

    override val items: Flow<List<FoodItem>> = dao.observeAll().map { rows -> rows.map(FoodItemEntity::toModel) }

    override suspend fun item(id: String): FoodItem? = dao.get(id)?.toModel()

    override suspend fun add(item: FoodItem) = dao.insert(FoodItemEntity.from(item))

    override suspend fun update(item: FoodItem) = dao.update(FoodItemEntity.from(item))

    override suspend fun markConsumed(ids: List<String>, at: Instant) = dao.markConsumed(ids, at.toEpochMilliseconds())

    override suspend fun restore(id: String) = dao.restore(id)

    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun deleteAll() = dao.deleteAll()
}
