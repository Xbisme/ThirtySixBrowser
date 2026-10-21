package com.raumanian.thirtysix.browser.feature.settings.data.datasource.local

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.testing.fake.FakeCookieManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidWebDataCleanerTest {

    @Test
    fun `fireproof cookies are written back when cookie removal never finishes`() = runTest {
        val cookies = FakeCookieManager(mapOf("https://$KEPT/" to KEPT_COOKIE))
        val cleaner = AndroidWebDataCleaner(
            ApplicationProvider.getApplicationContext(),
            TestDispatchers(StandardTestDispatcher(testScheduler)),
            { cookies },
        )

        val path = cleaner.clearExcept(setOf(KEPT), emptySet()) { it == KEPT }

        assertNull("a timeout is still a failure", path)
        assertEquals(listOf("http://$KEPT/" to KEPT_COOKIE), cookies.written)
    }

    private class TestDispatchers(private val d: CoroutineDispatcher) : DispatcherProvider {
        override val main: CoroutineDispatcher get() = d
        override val io: CoroutineDispatcher get() = d
        override val default: CoroutineDispatcher get() = d
        override val unconfined: CoroutineDispatcher get() = d
    }

    private companion object {
        const val KEPT = "example.com"
        const val KEPT_COOKIE = "sid=1"
    }
}
