package com.mazesys.zerobite.core.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class ItemDraftTest {

    private val today = LocalDate(2026, 9, 7)
    private val now = Instant.parse("2026-09-07T08:00:00Z")

    @Test
    fun `a new draft expires in a week`() {
        assertEquals(LocalDate(2026, 9, 14), ItemDraft.new(today).expiresOn)
    }

    @Test
    fun `needs a name and a positive quantity`() {
        val draft = ItemDraft.new(today)
        assertFalse(draft.isValid)
        assertFalse(draft.copy(name = "   ").isValid)
        assertFalse(draft.copy(name = "Milk", quantity = 0.0).isValid)
        assertTrue(draft.copy(name = "Milk").isValid)
    }

    @Test
    fun `making an item trims the name and defaults a blank unit`() {
        val item = ItemDraft.new(today).copy(name = "  Milk ", unit = " ", category = FoodCategory.DAIRY).makeItem("abc", now)
        assertEquals("abc", item.id)
        assertEquals("Milk", item.name)
        assertEquals("pcs", item.unit)
        assertEquals(FoodCategory.DAIRY, item.category)
        assertEquals(now, item.addedAt)
        assertEquals(LocalDate(2026, 9, 14), item.expiresOn)
    }

    @Test
    fun `applying keeps identity and history`() {
        val consumed = Instant.parse("2026-09-08T10:00:00Z")
        val original = ItemDraft.new(today).copy(name = "Milk").makeItem("abc", now).copy(consumedAt = consumed)
        val edited = ItemDraft.from(original).copy(name = "Whole milk", quantity = 2.0).applyTo(original)
        assertEquals("abc", edited.id)
        assertEquals(now, edited.addedAt)
        assertEquals(consumed, edited.consumedAt)
        assertEquals("Whole milk", edited.name)
        assertEquals(2.0, edited.quantity)
    }
}
