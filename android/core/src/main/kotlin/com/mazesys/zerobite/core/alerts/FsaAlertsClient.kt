package com.mazesys.zerobite.core.alerts

import com.mazesys.zerobite.core.model.FoodAlert
import com.mazesys.zerobite.core.network.ZeroBiteHttp
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.LocalDate

interface FoodAlertsFetching {
    /**
     * Alerts published since `since`. `known` are the alerts already held, so a client that has
     * to download each one separately can skip those it has.
     */
    suspend fun fetchAlerts(since: LocalDate, known: List<FoodAlert>): List<FoodAlert>
}

class FoodAlertsUnavailable(val status: Int) : Exception("FSA food alerts returned HTTP $status")

/**
 * The free, keyless FSA Food Alerts API (Open Government Licence v3.0).
 * https://data.food.gov.uk/food-alerts/ui/reference — note the date filter is `min-created`;
 * `since` returns 400.
 */
class FsaAlertsClient(private val http: HttpClient) : FoodAlertsFetching {

    /** Ignores `known`: one request returns everything. */
    override suspend fun fetchAlerts(since: LocalDate, known: List<FoodAlert>): List<FoodAlert> {
        val response = http.get("https://data.food.gov.uk/food-alerts/id.json") {
            parameter("_view", "full")
            parameter("_sort", "-created")
            parameter("_limit", "200")
            parameter("min-created", since.toString())
            header(HttpHeaders.Accept, "application/json")
            header(HttpHeaders.UserAgent, ZeroBiteHttp.USER_AGENT)
        }
        if (response.status != HttpStatusCode.OK) throw FoodAlertsUnavailable(response.status.value)
        return FsaAlertDecoder.decode(response.bodyAsText())
    }
}
