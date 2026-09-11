package com.raumanian.thirtysix.browser.data.local.cookies

import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.raumanian.thirtysix.browser.domain.repository.CookieJarSnapshotManager
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
 * Spec 012 — T036 (US2 / FR-011a) — instrumented end-to-end of the cookie
 * snapshot/restore lifecycle on a real Android `CookieManager`.
 *
 * Test scenario (mirrors quickstart G1 step 7):
 *  1. Set a known cookie on `ORIGIN_NORMAL` representing the pre-incognito jar.
 *  2. Capture a snapshot via the production [CookieJarSnapshotManager].
 *  3. Set a fresh cookie on `ORIGIN_NORMAL` representing an incognito-session
 *     write that MUST be wiped on close-of-last.
 *  4. Restore the snapshot.
 *  5. Verify the original cookie is back AND the incognito-session cookie is
 *     gone.
 *
 * Hilt-injected manager exercises the production wiring (Singleton scope,
 * `runCatching` crash-safety, suspending `removeAllCookies` wrapper).
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
        // Force-clean the global cookie store before each run — CookieManager
        // is process-global, prior tests in the same APK may have left state.
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

        // 2. Capture snapshot — simulates the 0→1 incognito-tab transition
        //    snapshot capture trigger inside IncognitoTabRepositoryImpl.
        manager.captureSnapshot(listOf(ORIGIN_NORMAL))
        assertTrue("snapshot must be held after capture", manager.hasSnapshot())

        // 3. Simulate an incognito-session cookie write that should be wiped
        //    on close-of-last.
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
     * Spec 016 T069 — ⚠️ non-negotiable privacy test (FR-030, research R7).
     *
     * The user clears cookies while an incognito session is open. Discarding the set-aside must
     * leave restore still WIPING the jar — so the incognito write is gone — while writing NOTHING
     * back — so the cookie the user cleared does not return when the last incognito tab closes.
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
     * Spec 016 T069 — the observable proof that the discard left `EMPTY` behind rather than
     * `null`: a second capture while `EMPTY` is held must stay a no-op. Had it replaced the
     * snapshot, cookie B would be written back on restore. `hasSnapshot()` alone cannot tell
     * the two apart.
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

    /**
     * Wipe the global cookie jar synchronously. The callback variant of
     * `CookieManager.removeAllCookies` is documented to require a thread
     * with a Looper (the JavaScriptThread / WebView core thread); coroutine
     * IO threads do NOT carry a Looper, which is why a direct
     * `suspendCancellableCoroutine` wrapper is unsafe in instrumented tests.
     * Production code wraps the call in `runCatching` so failures degrade to
     * "cookies wiped" — the test does not have that fallback, so we hop
     * onto the main thread (which has a Looper) for the cleanup helper.
     */
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
