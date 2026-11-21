package com.mazesys.zerobite.core.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * A food alert (product recall or allergy alert) from the UK Food Standards Agency or Poland's
 * Chief Sanitary Inspectorate (GIS), reduced to what the app needs. Serializable so the fetched
 * list can be cached on disk.
 *
 * FSA: public sector information licensed under the Open Government Licence v3.0.
 * GIS: text from gov.pl, Główny Inspektorat Sanitarny, CC BY-SA 4.0.
 */
@Serializable
data class FoodAlert(
    val id: String,
    val title: String,
    val shortTitle: String? = null,
    val kind: Kind,
    val created: LocalDate,
    val url: String? = null,
    val business: String? = null,
    val otherBusinesses: List<String> = emptyList(),
    val products: List<Product> = emptyList(),
    val allergens: List<String> = emptyList(),
    val risk: String? = null,
    val consumerAdvice: String? = null,
) {
    /** Where an alert comes from. */
    enum class Source { FSA, GIS }

    enum class Kind(val code: String, val label: String) {
        /** An allergen is missing from or wrong on the label. */
        ALLERGY("AA", "Allergy alert"),
        /** Product recall information notice. */
        RECALL("PRIN", "Product recall"),
        /** A recall that also needs local authority action. */
        ACTION_REQUIRED("FAFA", "Product recall");

        companion object {
            fun fromCode(code: String): Kind? = entries.firstOrNull { it.code == code }
        }
    }

    @Serializable
    data class Batch(
        val batchCode: String? = null,
        val bestBefore: String? = null,
        val useBy: String? = null,
        val lotNumber: String? = null,
    ) {
        /** e.g. "Batch L6232 · Best before May 2027". */
        val summary: String
            get() {
                // "All batches" / "All use by dates" read on their own; don't prefix them.
                fun label(prefix: String, value: String?): String? {
                    val trimmed = value?.trim()
                    if (trimmed.isNullOrEmpty()) return null
                    return if (trimmed.lowercase().startsWith("all")) trimmed else "$prefix $trimmed"
                }
                return listOfNotNull(
                    label("Batch", batchCode),
                    label("Lot", lotNumber),
                    label("Use by", useBy),
                    label("Best before", bestBefore),
                ).joinToString(" · ")
            }

        /** True when the notice covers every batch ("All batches", "All best before dates"). */
        val coversAllBatches: Boolean
            get() = listOfNotNull(batchCode, bestBefore, useBy).any { it.lowercase().contains("all ") }
    }

    @Serializable
    data class Product(
        val name: String,
        val packSize: String? = null,
        val productCode: String? = null,
        val batches: List<Batch> = emptyList(),
    )

    /** GIS alert ids are prefixed "GIS-"; everything else came from the FSA. */
    val source: Source get() = if (id.startsWith("GIS-")) Source.GIS else Source.FSA

    val displayTitle: String get() = shortTitle ?: title

    /** Businesses named on the notice: manufacturer first, then retailers. */
    val businesses: List<String> get() = listOfNotNull(business) + otherBusinesses

    /** The user's allergens this allergy alert concerns. */
    fun concerns(profile: List<Allergen>): List<Allergen> =
        if (kind != Kind.ALLERGY) emptyList() else profile.filter { a -> allergens.any { a.matches(it) } }
}
