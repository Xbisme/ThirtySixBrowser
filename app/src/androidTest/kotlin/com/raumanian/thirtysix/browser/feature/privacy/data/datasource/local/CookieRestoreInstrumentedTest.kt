package com.raumanian.thirtysix.browser.feature.privacy.data.datasource.local

import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.raumanian.thirtysix.browser.core.domain.repository.CookieJarSnapshotManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cookie snapshot and restore end to end on a real `CookieManager`, through the Hilt-injected
 * production manager.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CookieRestoreInstrumentedTest {

    @get:Rule(order = 0)
    val hiltRule: HiltAndroidRule = HiltAndroidRule(this)

    @Inject
    lateinit var manager: CookieJarSnapshotManager

    @Before
    fun setup() = runBlocking {
        hiltRule.inject()
        // CookieManager is process-global; earlier tests in the same APK may have left state.
        wipeCookieJarOnMainThread()
        // If a snapshot survived from a prior test, purge it.
        if (manager.hasSnapshot()) {
            manager.restoreSnapshot()
        }
        wipeCookieJarOnMainThread()
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun snapshot_capture_then_restore_preserves_normal_cookies_and_wipes_incognito_writes() = runBlocking {
        val cookieManager = CookieManager.getInstance()

        // 1. Pre-incognito state: a known cookie on the normal origin.
        cookieManager.setCookie(ORIGIN_NORMAL, "session=normal-keep")
        cookieManager.flush()

        // 2. Capture, as on the first incognito tab.
        manager.captureSnapshot(listOf(ORIGIN_NORMAL))
        assertTrue("snapshot must be held after capture", manager.hasSnapshot())

        // 3. An incognito-session cookie write that should be wiped on close-of-last.
        cookieManager.setCookie(ORIGIN_NORMAL, "session=incognito-leak")
        cookieManager.setCookie(ORIGIN_INCOGNITO, "secret=should-disappear")
        cookieManager.flush()

        // 4. Close-of-last simulation — restore.
        manager.restoreSnapshot()
        assertFalse("snapshot must be released after restore", manager.hasSnapshot())

        // 5. Verify outcome.
        val normalAfter = cookieManager.getCookie(ORIGIN_NORMAL).orEmpty()
        val incognitoAfter = cookieManager.getCookie(ORIGIN_INCOGNITO).orEmpty()
        assertTrue(
            "original normal cookie restored, got=$normalAfter",
            normalAfter.contains("session=normal-keep"),
        )
        assertFalse(
            "incognito-session leak wiped from normal origin, got=$normalAfter",
            normalAfter.contains("session=incognito-leak"),
        )
        assertFalse(
            "incognito-session cookie wiped from incognito origin, got=$incognitoAfter",
            incognitoAfter.contains("secret=should-disappear"),
        )
    }

    /**
     * Clearing cookies during an incognito session: restore must still wipe the jar (the incognito
     * write is gone) and write nothing back (the cleared cookie does not return).
     */
    @Test(timeout = TEST_TIMEOUT_MS)
    fun discard_then_restore_still_wipes_the_jar_and_writes_nothing_back() = runBlocking {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setCookie(ORIGIN_NORMAL, "session=cleared-by-user")
        cookieManager.flush()
        manager.captureSnapshot(listOf(ORIGIN_NORMAL))
        cookieManager.setCookie(ORIGIN_INCOGNITO, "secret=incognito-write")
        cookieManager.flush()

        manager.discardSetAsideCookies()
        assertTrue("the set-aside is EMPTY, not released", manager.hasSnapshot())
        manager.restoreSnapshot()

        val normalAfter = cookieManager.getCookie(ORIGIN_NORMAL).orEmpty()
        val incognitoAfter = cookieManager.getCookie(ORIGIN_INCOGNITO).orEmpty()
        assertFalse(
            "a cleared cookie must not be written back, got=$normalAfter",
            normalAfter.contains("session=cleared-by-user"),
        )
        assertFalse(
            "restore must still wipe incognito writes, got=$incognitoAfter",
            incognitoAfter.contains("secret=incognito-write"),
        )
    }

    /**
     * Proof that the discard left `EMPTY` rather than `null`: a second capture while `EMPTY` is held
     * stays a no-op, otherwise cookie B would be written back on restore.
     */
    @Test(timeout = TEST_TIMEOUT_MS)
    fun a_capture_after_discard_cannot_bring_cookies_back() = runBlocking {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setCookie(ORIGIN_NORMAL, "first=cleared-a")
        cookieManager.flush()
        manager.captureSnapshot(listOf(ORIGIN_NORMAL))

        manager.discardSetAsideCookies()
        cookieManager.setCookie(ORIGIN_NORMAL, "second=later-b")
        cookieManager.flush()
        manager.captureSnapshot(listOf(ORIGIN_NORMAL))
        manager.restoreSnapshot()

        val after = cookieManager.getCookie(ORIGIN_NORMAL).orEmpty()
        assertFalse("cookie A must stay cleared, got=$after", after.contains("first=cleared-a"))
        assertFalse("cookie B must not be written back, got=$after", after.contains("second=later-b"))
    }

    // The callback form of removeAllCookies needs a thread with a Looper; the test has no
    // runCatching fallback, so it hops to the main thread.
    private fun wipeCookieJarOnMainThread() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val latch = CountDownLatch(1)
        instrumentation.runOnMainSync {
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies { latch.countDown() }
            cookieManager.flush()
        }
        latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    private companion object {
        const val ORIGIN_NORMAL: String = "https://normal.test.example"
        const val ORIGIN_INCOGNITO: String = "https://incognito.test.example"
        const val LATCH_TIMEOUT_SECONDS: Long = 5L
        const val TEST_TIMEOUT_MS: Long = 30_000L
    }
}
