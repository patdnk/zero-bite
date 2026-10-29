package com.mazesys.zerobite.coordination

import com.mazesys.zerobite.R
import com.mazesys.zerobite.ui.text.UiText
import com.mazesys.zerobite.ui.text.uiText

/** Settings has no pushed routes yet; it surfaces confirmations and the explainer through the parent. */
class SettingsCoordinator(private val parent: AppCoordinator) {

    fun confirm(title: UiText, message: UiText, actionTitle: UiText, isDestructive: Boolean = true, action: () -> Unit) {
        parent.showAlert(
            AlertConfig(
                title = title,
                message = message,
                primary = AlertButton(actionTitle, isDestructive, action),
                secondary = AlertButton(uiText(R.string.action_cancel)),
            ),
        )
    }

    fun showInfo(title: UiText, message: UiText) {
        parent.showAlert(AlertConfig(title = title, message = message, primary = AlertButton(uiText(R.string.action_ok))))
    }

    fun showHowItWorks() = parent.presentSheet(SheetDestination.HowItWorks)
}
