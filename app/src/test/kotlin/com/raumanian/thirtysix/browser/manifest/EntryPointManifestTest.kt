package com.raumanian.thirtysix.browser.manifest

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.raumanian.thirtysix.browser.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Spec 020 T013 / contracts C1 — the merged manifest declares ThirtySix a browser for http and
 * https only (FR-001, FR-002) and keeps a single instance (FR-004).
 *
 * Runs under a plain [Application] so Robolectric does not boot the Hilt application and its
 * start-up sweep (the Spec 016 `SettingsModuleSmokeTest` finding).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EntryPointManifestTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val packageManager: PackageManager = context.packageManager

    private fun handlesView(uri: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setPackage(context.packageName)
        return packageManager.queryIntentActivities(intent, 0)
            .any { it.activityInfo.name == MainActivity::class.java.name }
    }

    @Test
    fun `main activity is single task`() {
        val info = packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, info.launchMode)
    }

    @Test
    fun `http and https links are handled`() {
        assertTrue(handlesView("http://example.org"))
        assertTrue(handlesView("https://example.org/a?b=1"))
    }

    @Test
    fun `no other scheme is handled`() {
        listOf(
            "file:///sdcard/a.html",
            "content://com.example/x",
            "javascript:alert(1)",
            "intent://x#Intent;end",
            "data:text/html,hi",
            "market://details?id=x",
        ).forEach { uri -> assertFalse(uri, handlesView(uri)) }
    }

    @Test
    fun `shared text, selected text and web search are handled`() {
        listOf(
            Intent(Intent.ACTION_SEND).setType("text/plain"),
            Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain"),
            Intent(Intent.ACTION_WEB_SEARCH),
        ).forEach { intent ->
            val resolved = packageManager.queryIntentActivities(intent.setPackage(context.packageName), 0)
            assertTrue(intent.action, resolved.any { it.activityInfo.name == MainActivity::class.java.name })
        }
    }
}
