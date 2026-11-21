package com.mazesys.zerobite.core.model

/**
 * The 14 allergens UK law requires on labels, and how they map to the more specific labels
 * used in FSA allergy alerts ("Gluten" covers "Wheat" and "Barley").
 */
enum class Allergen(val label: String, private val alertTerms: List<String>) {
    CELERY("Celery", listOf("celery", "celeriac")),
    GLUTEN("Gluten", listOf("gluten", "wheat", "barley", "rye", "oat", "spelt", "kamut")),
    CRUSTACEANS("Crustaceans", listOf("crustacean", "crab", "lobster", "prawn", "shrimp", "langoustine")),
    EGG("Egg", listOf("egg")),
    FISH("Fish", listOf("fish")),
    LUPIN("Lupin", listOf("lupin")),
    MILK("Milk", listOf("milk", "lactose", "whey", "casein")),
    MOLLUSCS("Molluscs", listOf("mollusc", "mussel", "oyster", "squid", "clam", "scallop")),
    MUSTARD("Mustard", listOf("mustard")),
    NUTS("Tree nuts", listOf("nut", "almond", "hazelnut", "walnut", "cashew", "pecan", "brazil", "pistachio", "macadamia")),
    PEANUTS("Peanuts", listOf("peanut")),
    SESAME("Sesame", listOf("sesame")),
    SOYA("Soya", listOf("soy")),
    SULPHITES("Sulphites", listOf("sulphite", "sulfite", "sulphur dioxide"));

    /** Stored value, identical to the iOS raw value. */
    val raw: String get() = name.lowercase()

    fun matches(alertLabel: String): Boolean {
        val label = alertLabel.lowercase()
        // "Peanuts" is its own allergen, not a tree nut.
        if (this == NUTS && label.startsWith("peanut")) return false
        return alertTerms.any { label.startsWith(it) || label.contains(" $it") }
    }

    companion object {
        fun fromRaw(raw: String): Allergen? = entries.firstOrNull { it.raw == raw }
    }
}
