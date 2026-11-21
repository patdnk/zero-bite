package com.mazesys.zerobite.core.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class FoodItemTest {

    private val today = LocalDate(2026, 9, 7)

    private fun item(days: Int, quantity: Double = 1.0, unit: String = "pcs") = FoodItem(
        id = "id-$days",
        name = "Milk",
        quantity = quantity,
        unit = unit,
        addedAt = Instant.parse("2026-09-01T09:00:00Z"),
        expiresOn = today.plus(days, DateTimeUnit.DAY),
    )

    @Test
    fun `status buckets use calendar days`() {
        assertEquals(ExpiryStatus.EXPIRED, item(-1).status(today))
        assertEquals(ExpiryStatus.TODAY, item(0).status(today))
        assertEquals(ExpiryStatus.SOON, item(3).status(today))
        assertEquals(ExpiryStatus.FRESH, item(4).status(today))
        assertEquals(ExpiryStatus.SOON, item(5).status(today, soonThresholdDays = 5))
    }

    @Test
    fun `statuses are ordered most urgent first`() {
        assertEquals(listOf(ExpiryStatus.EXPIRED, ExpiryStatus.TODAY, ExpiryStatus.SOON, ExpiryStatus.FRESH), ExpiryStatus.entries.sorted())
        assertTrue(ExpiryStatus.SOON.needsAttention)
        assertFalse(ExpiryStatus.FRESH.needsAttention)
    }

    @Test
    fun `expiry descriptions read naturally`() {
        assertEquals("Expired 3 days ago", item(-3).expiryDescription(today))
        assertEquals("Expired yesterday", item(-1).expiryDescription(today))
        assertEquals("Expires today", item(0).expiryDescription(today))
        assertEquals("Expires tomorrow", item(1).expiryDescription(today))
        assertEquals("Expires in 5 days", item(5).expiryDescription(today))
    }

    @Test
    fun `short labels switch to a date after two weeks`() {
        assertEquals("Expired", item(-2).shortExpiryLabel(today))
        assertEquals("Today", item(0).shortExpiryLabel(today))
        assertEquals("Tomorrow", item(1).shortExpiryLabel(today))
        assertEquals("4d", item(4).shortExpiryLabel(today))
        assertEquals("14d", item(14).shortExpiryLabel(today))
        assertEquals("22 Sep", item(15).shortExpiryLabel(today))
    }

    @Test
    fun `quantity text drops trailing zeros`() {
        assertEquals("1 pcs", item(0).quantityText)
        assertEquals("0.5 kg", item(0, 0.5, "kg").quantityText)
        assertEquals("1.25 l", item(0, 1.25, "l").quantityText)
        assertEquals("2 pack", item(0, 2.0, "pack").quantityText)
    }

    @Test
    fun `raw values round trip and unknown values fall back`() {
        assertEquals(FoodCategory.DAIRY, FoodCategory.fromRaw("dairy"))
        assertEquals(FoodCategory.OTHER, FoodCategory.fromRaw("spaceship"))
        assertEquals("leftovers", FoodCategory.LEFTOVERS.raw)
        assertEquals(StorageLocation.PANTRY, StorageLocation.fromRaw("pantry"))
        assertEquals(StorageLocation.FRIDGE, StorageLocation.fromRaw(""))
        assertEquals("Cupboard", StorageLocation.PANTRY.label)
    }
}
