package com.mazesys.zerobite.coordination

/** Settings has no pushed routes yet; it surfaces confirmations and the explainer through the parent. */
class SettingsCoordinator(private val parent: AppCoordinator) {

    fun confirm(title: String, message: String, actionTitle: String, isDestructive: Boolean = true, action: () -> Unit) {
        parent.showAlert(
            AlertConfig(
                title = title,
                message = message,
                primary = AlertButton(actionTitle, isDestructive, action),
                secondary = AlertButton("Cancel"),
            ),
        )
    }

    fun showInfo(title: String, message: String) {
        parent.showAlert(AlertConfig(title = title, message = message, primary = AlertButton("OK")))
    }

    fun showHowItWorks() = parent.presentSheet(SheetDestination.HowItWorks)
}
