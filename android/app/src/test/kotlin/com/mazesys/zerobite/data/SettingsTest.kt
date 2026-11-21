package com.mazesys.zerobite.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class SettingsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun open(file: File): Pair<DataStoreSettings, Job> {
        val job = Job()
        val scope = CoroutineScope(Dispatchers.IO + job)
        return DataStoreSettings(PreferenceDataStoreFactory.create(scope = scope) { file }, scope) to job
    }

    @Test
    fun `defaults match iOS`() = runBlocking {
        val (settings, job) = open(File(folder.root, "settings.preferences_pb"))
        val snapshot = settings.current()
        assertEquals(false, snapshot.hasCompletedWelcome)
        assertEquals(3, snapshot.soonThresholdDays)
        assertEquals(true, snapshot.recallChecksEnabled)
        assertEquals(false, snapshot.shareUsageData)
        assertEquals(com.mazesys.zerobite.core.notifications.NotificationPreferences.DEFAULT, snapshot.notificationPreferences)
        job.cancelAndJoin()
    }

    @Test
    fun `updates persist and reach the state flow`() = runBlocking {
        val file = File(folder.root, "settings.preferences_pb")
        val (settings, job) = open(file)
        settings.update { it.copy(hasCompletedWelcome = true, soonThresholdDays = 5, allergens = setOf("milk", "peanuts"), shareUsageData = true) }
        assertEquals(5, settings.state.first { it.soonThresholdDays == 5 }.soonThresholdDays)
        job.cancelAndJoin()

        val (reopened, job2) = open(file)
        val snapshot = reopened.current()
        assertEquals(true, snapshot.hasCompletedWelcome)
        assertEquals(setOf("milk", "peanuts"), snapshot.allergens)
        assertEquals(true, snapshot.shareUsageData)
        assertEquals(true, reopened.saved.first().shareUsageData, "saved starts from the file, not the defaults")
        job2.cancelAndJoin()
    }
}
