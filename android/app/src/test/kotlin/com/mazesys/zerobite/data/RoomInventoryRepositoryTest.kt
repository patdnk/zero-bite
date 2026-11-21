package com.mazesys.zerobite.data

import com.mazesys.zerobite.testing.EnglishStrings
import androidx.test.core.app.ApplicationProvider
import com.mazesys.zerobite.core.model.FoodCategory
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.StorageLocation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class RoomInventoryRepositoryTest {

    private val db = ZeroBiteDatabase.inMemory(ApplicationProvider.getApplicationContext())
    private val repository = RoomInventoryRepository(db.foodItems())
    private val added = Instant.parse("2026-09-01T09:00:00Z")

    @After
    fun close() = db.close()

    private fun item(id: String, name: String, expires: LocalDate) = FoodItem(id = id, name = name, addedAt = added, expiresOn = expires)

    @Test
    fun `round trips every field`() = runBlocking {
        val full = FoodItem(
            id = "a", name = "Crème fraîche", quantity = 1.25, unit = "tub", category = FoodCategory.DAIRY,
            storage = StorageLocation.PANTRY, addedAt = added, expiresOn = LocalDate(2026, 9, 12),
            barcode = "5000112637922", brand = "Yeo Valley", notes = "Opened Monday",
            consumedAt = Instant.parse("2026-09-03T10:15:30Z"),
        )
        repository.add(full)
        assertEquals(full, repository.item("a"))
        assertEquals(listOf(full), repository.items.first())
    }

    @Test
    fun `items are ordered by expiry then name ignoring case`() = runBlocking {
        repository.add(item("1", "banana", LocalDate(2026, 9, 10)))
        repository.add(item("2", "Apple", LocalDate(2026, 9, 10)))
        repository.add(item("3", "Zucchini", LocalDate(2026, 9, 8)))
        assertEquals(listOf("Zucchini", "Apple", "banana"), repository.items.first().map { it.name })
    }

    @Test
    fun `update, consume, restore, delete and clear`() = runBlocking {
        repository.add(item("1", "Milk", LocalDate(2026, 9, 10)))
        repository.add(item("2", "Eggs", LocalDate(2026, 9, 20)))

        repository.update(repository.item("1")!!.copy(name = "Whole milk"))
        assertEquals("Whole milk", repository.item("1")!!.name)

        val at = Instant.parse("2026-09-07T08:00:00Z")
        repository.markConsumed(listOf("1", "2"), at)
        assertEquals(listOf(at, at), repository.items.first().map { it.consumedAt })

        repository.restore("2")
        assertNull(repository.item("2")!!.consumedAt)

        repository.delete("1")
        assertEquals(listOf("2"), repository.items.first().map { it.id })

        repository.deleteAll()
        assertEquals(emptyList(), repository.items.first())
    }

    @Test
    fun `sample fridge matches iOS`() {
        var n = 0
        val today = LocalDate(2026, 9, 7)
        val items = SampleData.items(today, added, { "id-${++n}" }, EnglishStrings)
        assertEquals(21, items.size)
        assertEquals(21, items.map { it.id }.toSet().size)
        val yogurt = items.first { it.name == "Greek yogurt" }
        assertEquals(LocalDate(2026, 9, 6), yogurt.expiresOn)
        assertEquals("500 g", yogurt.quantityText)
        assertEquals(StorageLocation.FREEZER, items.first { it.name == "Salmon fillets" }.storage)
        assertEquals(StorageLocation.PANTRY, items.first { it.name == "Spaghetti" }.storage)
    }
}
