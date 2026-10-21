package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The intent handed to another app is always browsable and never names a component. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ExternalAppLauncherTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun failWhenNothingResolves() {
        shadowOf(app).checkActivities(true)
    }

    private fun handler(scheme: String) {
        val filter = IntentFilter(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addCategory(Intent.CATEGORY_BROWSABLE)
            addDataScheme(scheme)
        }
        val component = ComponentName("com.store", "com.store.View")
        val info = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = component.packageName
                name = component.className
                exported = true
            }
        }
        shadowOf(app.packageManager).apply {
            addOrUpdateActivity(info.activityInfo)
            addIntentFilterForActivity(component, filter)
        }
    }

    private fun started(): Intent = shadowOf(app).nextStartedActivity

    @Test
    fun `a market link starts a browsable view with no component or selector`() {
        handler("market")

        val fallback = ExternalAppLauncher.launch(app, "market://details?id=x")

        assertNull(fallback)
        val intent = started()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(Uri.parse("market://details?id=x"), intent.data)
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertNull(intent.component)
        assertNull(intent.selector)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `an intent link has its component and selector cleared`() {
        handler("x")

        val url = "intent://y#Intent;scheme=x;launchFlags=0x1;component=com.evil/.Hidden;SEL;action=a;end"

        ExternalAppLauncher.launch(app, url)

        val intent = started()
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertNull(intent.component)
        assertNull(intent.selector)
        assertEquals("no URI permission grant from the link", 0, intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    @Test
    fun `an intent link no app takes returns its web fallback`() {
        val url = "intent://x#Intent;component=com.evil/.Hidden;S.browser_fallback_url=https%3A%2F%2Fexample.com;end"

        assertEquals("https://example.com", ExternalAppLauncher.launch(app, url))
    }

    @Test
    fun `a javascript fallback is refused`() {
        val url = "intent://x#Intent;scheme=nothing;S.browser_fallback_url=javascript%3Aalert(1);end"

        assertNull(ExternalAppLauncher.launch(app, url))
    }

    @Test
    fun `a malformed intent link returns nothing and does not throw`() {
        assertNull(ExternalAppLauncher.launch(app, "intent:#Intent;S.x=%zz;end"))
        assertNull(ExternalAppLauncher.launch(app, "intent://x#Intent;component=;end"))
    }

    @Test
    fun `an intent link to this app loads its address in the tab and starts nothing`() {
        handler("https")
        val url = "intent://x#Intent;scheme=https;package=${app.packageName};end"

        assertEquals("https://x", ExternalAppLauncher.launch(app, url))
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test
    fun `an intent link with a web address loads it in the tab and starts nothing`() {
        handler("https")

        assertEquals("https://y", ExternalAppLauncher.launch(app, "intent://y#Intent;scheme=https;end"))
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test
    fun `a link no app takes, without a fallback, returns nothing`() {
        assertNull(ExternalAppLauncher.launch(app, "tel:1"))
    }
}
