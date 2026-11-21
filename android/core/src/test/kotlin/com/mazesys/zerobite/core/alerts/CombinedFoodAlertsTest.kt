package com.mazesys.zerobite.core.alerts

import com.mazesys.zerobite.core.model.FoodAlert
import com.mazesys.zerobite.core.model.FoodAlert.Source
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CombinedFoodAlertsTest {

    private fun alert(id: String, day: Int) =
        FoodAlert(id = id, title = id, kind = FoodAlert.Kind.RECALL, created = LocalDate(2026, 10, day))

    /** Records its calls; returns `result(known)` or throws. */
    private class Stub(private val result: (List<FoodAlert>) -> List<FoodAlert>) : FoodAlertsFetching {
        val knownSeen = CopyOnWriteArrayList<List<FoodAlert>>()
        val calls get() = knownSeen.size

        override suspend fun fetchAlerts(since: LocalDate, known: List<FoodAlert>): List<FoodAlert> {
            knownSeen.add(known)
            return result(known)
        }
    }

    private val since = LocalDate(2026, 6, 1)
    private val failing = { Stub { error("offline") } }

    @Test
    fun `combined fetches only enabled sources`() = runTest {
        val fsa = Stub { listOf(alert("fsa-1", 1)) }
        val gis = Stub { listOf(alert("GIS-1", 2)) }

        val alerts = CombinedFoodAlerts(mapOf(Source.FSA to fsa, Source.GIS to gis)) { setOf(Source.GIS) }
            .fetchAlerts(since, emptyList())

        assertEquals(0, fsa.calls)
        assertEquals(1, gis.calls)
        assertEquals(listOf("GIS-1"), alerts.map { it.id })
    }

    @Test
    fun `combined merges the sources newest first and hands each its own known alerts`() = runTest {
        val knownFsa = alert("fsa-old", 1)
        val knownGis = alert("GIS-old", 2)
        val fsa = Stub { it + alert("fsa-new", 5) }
        val gis = Stub { it + alert("GIS-new", 9) }

        val alerts = CombinedFoodAlerts(mapOf(Source.FSA to fsa, Source.GIS to gis)) { setOf(Source.FSA, Source.GIS) }
            .fetchAlerts(since, listOf(knownFsa, knownGis))

        assertEquals(listOf(listOf(knownFsa)), fsa.knownSeen.toList())
        assertEquals(listOf(listOf(knownGis)), gis.knownSeen.toList())
        assertEquals(listOf("GIS-new", "fsa-new", "GIS-old", "fsa-old"), alerts.map { it.id })
    }

    @Test
    fun `drops a disabled source without network`() = runTest {
        val known = listOf(alert("fsa-1", 1), alert("GIS-1", 2))
        val fsa = failing()
        val gis = failing()

        val failure = assertFailsWith<FoodAlertsPartialFailure> {
            CombinedFoodAlerts(mapOf(Source.FSA to fsa, Source.GIS to gis)) { setOf(Source.FSA) }
                .fetchAlerts(since, known)
        }

        assertEquals(0, gis.calls)
        assertEquals(setOf(Source.FSA), failure.failed)
        assertEquals(listOf("fsa-1"), failure.alerts.map { it.id })
    }

    @Test
    fun `keeps cached GIS alerts when GIS fails`() = runTest {
        val cachedGis = alert("GIS-cached", 3)
        val fsa = Stub { listOf(alert("fsa-new", 7)) }

        val failure = assertFailsWith<FoodAlertsPartialFailure> {
            CombinedFoodAlerts(mapOf(Source.FSA to fsa, Source.GIS to failing())) { setOf(Source.FSA, Source.GIS) }
                .fetchAlerts(since, listOf(alert("fsa-cached", 1), cachedGis))
        }

        assertEquals(setOf(Source.GIS), failure.failed)
        // The FSA result replaces its cache; the GIS cache stays.
        assertEquals(listOf("fsa-new", "GIS-cached"), failure.alerts.map { it.id })
    }

    @Test
    fun `combined reads the enabled sources on every fetch`() = runTest {
        var enabled = setOf(Source.FSA)
        val fsa = Stub { listOf(alert("fsa-1", 1)) }
        val gis = Stub { listOf(alert("GIS-1", 2)) }
        val combined = CombinedFoodAlerts(mapOf(Source.FSA to fsa, Source.GIS to gis)) { enabled }

        assertEquals(listOf("fsa-1"), combined.fetchAlerts(since, emptyList()).map { it.id })
        enabled = setOf(Source.FSA, Source.GIS)
        assertEquals(listOf("GIS-1", "fsa-1"), combined.fetchAlerts(since, emptyList()).map { it.id })
        assertTrue(gis.calls == 1)
    }

    @Test
    fun `an enabled source without a client is ignored`() = runTest {
        val alerts = CombinedFoodAlerts(mapOf(Source.FSA to Stub { listOf(alert("fsa-1", 1)) })) { setOf(Source.FSA, Source.GIS) }
            .fetchAlerts(since, emptyList())
        assertEquals(listOf("fsa-1"), alerts.map { it.id })
    }
}
