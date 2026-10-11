package com.raumanian.thirtysix.browser.manifest

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Spec 021 T022 / contracts C3.3 — the four site-capability permissions are declared, the matching
 * hardware is optional, and no other dangerous permission sneaks in (FR-037, Constitution §II).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WebCapabilitiesManifestTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    @Suppress("DEPRECATION")
    private val packageInfo = context.packageManager.getPackageInfo(
        context.packageName,
        PackageManager.GET_PERMISSIONS or PackageManager.GET_CONFIGURATIONS,
    )

    private val siteCapabilityPermissions = setOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )

    private val requested: Set<String> get() = packageInfo.requestedPermissions.orEmpty().toSet()

    @Test
    fun `the four site capability permissions are declared`() {
        assertTrue(requested.containsAll(siteCapabilityPermissions))
    }

    @Test
    fun `no permission beyond the Constitution table is declared`() {
        // Robolectric has no framework permission metadata, so the check is an exact allow-list:
        // the Constitution §II table, plus the library-added signature permission scoped to this app.
        val allowed = siteCapabilityPermissions + setOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
        val extra = requested.filterNot { it in allowed || it.startsWith(context.packageName + ".") }
        assertEquals(emptyList<String>(), extra)
    }

    @Test
    fun `camera, microphone and location hardware are optional`() {
        val features = packageInfo.reqFeatures.orEmpty().filter { it.name != null }.associateBy { it.name }
        listOf("android.hardware.camera", "android.hardware.microphone", "android.hardware.location").forEach { name ->
            val feature = features[name]
            assertTrue("$name declared", feature != null)
            assertEquals("$name optional", 0, feature!!.flags and android.content.pm.FeatureInfo.FLAG_REQUIRED)
        }
    }

    @Test
    fun `rotation and size changes do not recreate the browser`() {
        val info = context.packageManager.getActivityInfo(
            android.content.ComponentName(context, com.raumanian.thirtysix.browser.MainActivity::class.java),
            0,
        )
        val handled = android.content.pm.ActivityInfo.CONFIG_ORIENTATION or
            android.content.pm.ActivityInfo.CONFIG_SCREEN_SIZE or
            android.content.pm.ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE or
            android.content.pm.ActivityInfo.CONFIG_KEYBOARD_HIDDEN or
            android.content.pm.ActivityInfo.CONFIG_LOCALE or
            android.content.pm.ActivityInfo.CONFIG_SCREEN_LAYOUT
        assertEquals(handled, info.configChanges and handled)
    }
}
