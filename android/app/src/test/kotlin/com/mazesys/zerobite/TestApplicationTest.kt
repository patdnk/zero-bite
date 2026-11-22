package com.mazesys.zerobite

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.isActive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ZeroBiteApplication starts background syncs on a scope that outlives the test; left running, they
 * hit a torn-down Robolectric sandbox and fail whichever test runs next. So tests get a plain
 * Application (robolectric.properties) unless they opt in, and the real one cancels its scope on teardown.
 */
@RunWith(RobolectricTestRunner::class)
class TestApplicationTest {

    @Test
    fun `tests run on a plain Application by default`() {
        assertFalse(ApplicationProvider.getApplicationContext<android.app.Application>() is ZeroBiteApplication)
    }

    @Test
    @Config(application = ZeroBiteApplication::class)
    fun `the real application cancels its background scope on terminate`() {
        val app = ApplicationProvider.getApplicationContext<ZeroBiteApplication>()
        val scope = app.dependencies.appScope
        assertTrue(scope.isActive)
        app.onTerminate()
        assertFalse(scope.isActive)
    }
}
