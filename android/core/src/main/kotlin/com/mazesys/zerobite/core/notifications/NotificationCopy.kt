package com.mazesys.zerobite.core.notifications

/**
 * The words of an expiry reminder. Core stays language-free: the app builds this from its
 * string resources; [ENGLISH] is the default and the copy in testdata/notification-plans.json.
 */
interface NotificationCopy {
    /** Heads-up title `leadDays` (≥ 1) before expiry: "Milk expires tomorrow", "Milk expires in 3 days". */
    fun soonTitle(name: String, leadDays: Int): String
    val soonBody: String

    fun todayTitle(name: String): String
    val todayBody: String

    fun expiredTitle(name: String): String
    val expiredBody: String

    companion object {
        val ENGLISH: NotificationCopy = object : NotificationCopy {
            override fun soonTitle(name: String, leadDays: Int) =
                if (leadDays == 1) "$name expires tomorrow" else "$name expires in $leadDays days"
            override val soonBody = "Plan a meal around it — Zero Bite has recipe ideas."
            override fun todayTitle(name: String) = "$name expires today"
            override val todayBody = "Use it today or freeze it."
            override fun expiredTitle(name: String) = "$name has expired"
            override val expiredBody = "Check it and update your list."
        }
    }
}
