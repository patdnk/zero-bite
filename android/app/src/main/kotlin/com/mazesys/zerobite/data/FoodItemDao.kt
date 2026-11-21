package com.mazesys.zerobite.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodItemDao {
    @Query("SELECT * FROM food_items ORDER BY expiresOn, name COLLATE NOCASE")
    fun observeAll(): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_items WHERE id = :id")
    suspend fun get(id: String): FoodItemEntity?

    @Insert
    suspend fun insert(item: FoodItemEntity)

    @Update
    suspend fun update(item: FoodItemEntity)

    @Query("UPDATE food_items SET consumedAt = :at WHERE id IN (:ids)")
    suspend fun markConsumed(ids: List<String>, at: Long)

    @Query("UPDATE food_items SET consumedAt = NULL WHERE id = :id")
    suspend fun restore(id: String)

    @Query("DELETE FROM food_items WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM food_items")
    suspend fun deleteAll()
}
