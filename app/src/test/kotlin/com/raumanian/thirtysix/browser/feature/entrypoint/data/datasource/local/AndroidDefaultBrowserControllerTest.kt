package com.raumanian.thirtysix.browser.feature.entrypoint.data.datasource.local

import android.app.Application
import android.app.role.RoleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The default-browser read on both API branches. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AndroidDefaultBrowserControllerTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    private fun roles() = shadowOf(context.getSystemService(RoleManager::class.java))

    @Test
    @Config(sdk = [29])
    fun `API 29 - holding the available browser role means default`() {
        roles().addAvailableRole(RoleManager.ROLE_BROWSER)
        roles().addHeldRole(RoleManager.ROLE_BROWSER)
        assertTrue(AndroidDefaultBrowserController(context).isDefaultBrowser())
    }

    @Test
    @Config(sdk = [33])
    fun `API 33 - an available role that is not held means not default`() {
        roles().addAvailableRole(RoleManager.ROLE_BROWSER)
        assertFalse(AndroidDefaultBrowserController(context).isDefaultBrowser())
    }

    @Test
    @Config(sdk = [29])
    fun `API 29 - an unavailable role means not default`() {
        // Nothing added: the shadow starts with no available roles.
        assertFalse(AndroidDefaultBrowserController(context).isDefaultBrowser())
    }

    @Test
    @Config(sdk = [28])
    fun `API 28 - web links resolving to this app means default`() {
        // The merged manifest makes MainActivity the only web-link handler on the test device.
        assertTrue(AndroidDefaultBrowserController(context).isDefaultBrowser())
    }

    @Test
    @Config(sdk = [28])
    fun `a failing platform call answers false`() {
        val broken = object : ContextWrapper(context) {
            override fun getPackageManager(): PackageManager = error("gone")
            override fun getApplicationContext(): Context = this
        }
        assertFalse(AndroidDefaultBrowserController(broken).isDefaultBrowser())
    }
}
