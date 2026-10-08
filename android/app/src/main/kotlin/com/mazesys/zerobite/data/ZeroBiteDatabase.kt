package com.mazesys.zerobite.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FoodItemEntity::class], version = 1, exportSchema = true)
abstract class ZeroBiteDatabase : RoomDatabase() {
    abstract fun foodItems(): FoodItemDao

    companion object {
        fun open(context: Context): ZeroBiteDatabase =
            Room.databaseBuilder(context, ZeroBiteDatabase::class.java, "zerobite.db").build()

        /** Throwaway database for tests and previews. */
        fun inMemory(context: Context): ZeroBiteDatabase =
            Room.inMemoryDatabaseBuilder(context, ZeroBiteDatabase::class.java).allowMainThreadQueries().build()
    }
}
