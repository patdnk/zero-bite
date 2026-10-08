package com.mazesys.zerobite.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class BundledRecipeRepositoryTest {
    @Test
    fun `loads the bundled catalogue once`() = runBlocking {
        val repository = BundledRecipeRepository(ApplicationProvider.getApplicationContext<android.content.Context>().assets)
        val first = repository.recipes()
        assertTrue(first.size >= 30)
        assertSame(first, repository.recipes())
    }
}
