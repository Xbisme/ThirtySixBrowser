package com.raumanian.thirtysix.browser.data.local.cookies

import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.domain.repository.CookieJarSnapshotManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
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
        suspendRemoveAllCookies()
        // If a snapshot survived from a prior test, purge it.
        if (manager.hasSnapshot()) {
            manager.restoreSnapshot()
        }
        suspendRemoveAllCookies()
    }

    @Test
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

    private suspend fun suspendRemoveAllCookies() {
        val cookieManager = CookieManager.getInstance()
        suspendCancellableCoroutine<Unit> { cont ->
            cookieManager.removeAllCookies { if (cont.isActive) cont.resume(Unit) }
        }
        cookieManager.flush()
    }

    private companion object {
        const val ORIGIN_NORMAL: String = "https://normal.test.example"
        const val ORIGIN_INCOGNITO: String = "https://incognito.test.example"
    }
}
