package com.mazesys.zerobite.screens.welcome

import com.mazesys.zerobite.reminders.NotificationPermission
import com.mazesys.zerobite.testing.FakeAccess
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WelcomeViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private var completed = 0
    private val settings = FakeSettings()
    private val access = FakeAccess(NotificationPermission.NOT_ASKED)
    private fun vm(mode: WelcomeMode = WelcomeMode.FIRST_RUN) = WelcomeViewModel(mode, settings, access, onComplete = { completed++ })
    private val never = com.mazesys.zerobite.reminders.PermissionRequester { false }

    @Test
    fun `first run pages through to the reminders step`() {
        val model = vm()
        assertEquals(WelcomePage.REMINDERS, model.state.value.pages.last())
        assertEquals("Show me how", model.state.value.primaryTitle)
        repeat(model.state.value.pages.size - 1) { model.primaryAction(never) }
        assertEquals(WelcomePage.REMINDERS, model.state.value.currentPage)
        assertTrue(model.state.value.isLastPage)
        assertEquals("Turn on reminders", model.state.value.primaryTitle)
        assertEquals(0, completed)
    }

    @Test
    fun `skip jumps to reminders on first run`() {
        val model = vm()
        model.skipIntro()
        assertEquals(WelcomePage.REMINDERS, model.state.value.currentPage)
    }

    @Test
    fun `replay has no permission step and finishes on the last page`() {
        val model = vm(WelcomeMode.REPLAY)
        assertFalse(WelcomePage.REMINDERS in model.state.value.pages)
        assertEquals("Close", model.state.value.skipTitle)
        repeat(model.state.value.pages.size) { model.primaryAction(never) }
        assertEquals(1, completed)
    }

    @Test
    fun `granting finishes with reminders on`() {
        val model = vm()
        model.skipIntro()
        model.primaryAction { access.current = NotificationPermission.GRANTED; true }
        assertTrue(settings.state.value.notificationsEnabled)
        assertTrue(access.asked)
        assertEquals(1, completed)
    }

    @Test
    fun `refusing shows the denied step, then continues`() {
        val model = vm()
        model.skipIntro()
        model.primaryAction(never)
        assertEquals(WelcomeStep.DENIED, model.state.value.step)
        assertEquals("Continue", model.state.value.primaryTitle)
        assertFalse(model.state.value.showsNotNow)
        model.primaryAction(never)
        assertEquals(1, completed)
    }

    @Test
    fun `not now finishes with reminders off`() {
        val model = vm()
        model.skipIntro()
        model.skip()
        assertFalse(settings.state.value.notificationsEnabled)
        assertEquals(1, completed)
    }
}
