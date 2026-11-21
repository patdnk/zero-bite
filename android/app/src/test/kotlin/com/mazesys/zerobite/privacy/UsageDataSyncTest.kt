package com.mazesys.zerobite.privacy

import com.mazesys.zerobite.data.SettingsSnapshot
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.FakeUsageData
import com.mazesys.zerobite.testing.testDependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

class UsageDataSyncTest {

    private val settings = FakeSettings(SettingsSnapshot(shareUsageData = true))
    private val usageData = FakeUsageData()
    private val dependencies = testDependencies(settings = settings, usageData = usageData)

    @Test
    fun `applies the saved setting at launch and on every change`() = runBlocking {
        val job = dependencies.usageData.start(CoroutineScope(Dispatchers.Unconfined))
        assertEquals(listOf(true), usageData.applied)

        settings.update { it.copy(shareUsageData = false) }
        assertEquals(listOf(true, false), usageData.applied)

        settings.update { it.copy(soonThresholdDays = 5) } // other settings leave collection alone
        settings.update { it.copy(shareUsageData = true) }
        assertEquals(listOf(true, false, true), usageData.applied)
        job.cancel()
    }

    @Test
    fun `launching with sharing off never turns collection on`() = runBlocking {
        val off = FakeUsageData()
        val offSettings = FakeSettings()
        val job = testDependencies(settings = offSettings, usageData = off).usageData.start(CoroutineScope(Dispatchers.Unconfined))
        offSettings.update { it.copy(hasCompletedWelcome = true, notificationsEnabled = true) }
        assertEquals(listOf(false), off.applied)
        job.cancel()
    }
}
