package com.mazesys.zerobite.core.alerts

import com.mazesys.zerobite.core.model.FoodAlert
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.LocalDate

/**
 * Thrown when some enabled sources failed. `alerts` is still the best list available: fresh
 * alerts from the sources that worked, plus the known alerts of the ones that didn't.
 */
class FoodAlertsPartialFailure(val alerts: List<FoodAlert>, val failed: Set<FoodAlert.Source>) :
    Exception("Food alerts unavailable from: ${failed.joinToString()}")

/**
 * Merges the food alert sources (UK FSA, Poland GIS) into one list. Only the sources the user
 * has switched on are queried. One source failing doesn't hide the others: its known alerts
 * stay, and the failure is reported alongside the merged list.
 *
 * `enabled` is read on every fetch, so changing the setting applies to the next refresh.
 */
class CombinedFoodAlerts(
    private val clients: Map<FoodAlert.Source, FoodAlertsFetching>,
    private val enabled: () -> Set<FoodAlert.Source>,
) : FoodAlertsFetching {

    override suspend fun fetchAlerts(since: LocalDate, known: List<FoodAlert>): List<FoodAlert> {
        // Alerts of a disabled source are dropped, so turning one off clears it from the list.
        val sources = enabled().filter { it in clients }
        val outcomes = coroutineScope {
            sources.map { source ->
                async {
                    val own = known.filter { it.source == source }
                    source to try {
                        Result.success(clients.getValue(source).fetchAlerts(since, own))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
            }.awaitAll()
        }

        val merged = mutableListOf<FoodAlert>()
        val failed = mutableSetOf<FoodAlert.Source>()
        for ((source, outcome) in outcomes) {
            outcome.fold(
                onSuccess = { merged += it },
                onFailure = {
                    failed += source
                    merged += known.filter { alert -> alert.source == source }
                },
            )
        }
        merged.sortWith(compareByDescending<FoodAlert> { it.created }.thenBy { it.id })
        if (failed.isNotEmpty()) throw FoodAlertsPartialFailure(merged, failed)
        return merged
    }
}
