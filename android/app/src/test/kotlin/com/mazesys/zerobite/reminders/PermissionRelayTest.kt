package com.mazesys.zerobite.reminders

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PermissionRelayTest {

    @Test
    fun `a result delivered by a recreated activity still reaches the waiting request`() = runBlocking {
        val relay = PermissionRelay() // lives in AppViewModel, so it survives recreation
        var launched = 0
        val waiting = async { relay.request { launched++ } }
        yield()
        assertEquals(1, launched)
        // The old activity is gone; the new one receives the dialog's result and hands it on.
        relay.deliver(true)
        assertTrue(waiting.await())
    }
}
