package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.app.Application
import com.raumanian.thirtysix.browser.core.testing.fake.FakeCookieManager
import com.raumanian.thirtysix.browser.core.testing.fake.FakeIncognitoWebProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A download carries the cookies of the jar its tab browses with, never the other one. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WebViewProfilesTest {

    private val defaultJar = FakeCookieManager()
    private val profileJar = FakeCookieManager()

    private fun cookieManagerFor(incognito: Boolean, profiles: FakeIncognitoWebProfile, profileFound: Boolean = true) =
        WebViewProfiles.cookieManagerFor(
            incognito = incognito,
            profiles = profiles,
            profileCookies = { name ->
                assertEquals(profiles.sessionProfileName(), name)
                profileJar.takeIf { profileFound }
            },
            defaultCookies = { defaultJar },
        )

    @Test
    fun `an incognito tab with profiles uses its profile's jar`() {
        assertSame(profileJar, cookieManagerFor(incognito = true, profiles = FakeIncognitoWebProfile(isolated = true)))
    }

    @Test
    fun `an incognito tab without profiles uses the default jar, which holds the swapped-in incognito one`() {
        assertSame(defaultJar, cookieManagerFor(incognito = true, profiles = FakeIncognitoWebProfile(isolated = false)))
    }

    @Test
    fun `an incognito tab whose profile cannot be found sends no cookies rather than normal ones`() {
        val profiles = FakeIncognitoWebProfile(isolated = true)
        assertNull(cookieManagerFor(incognito = true, profiles = profiles, profileFound = false))
    }

    @Test
    fun `a normal tab uses the default jar even when profiles exist`() {
        assertSame(defaultJar, cookieManagerFor(incognito = false, profiles = FakeIncognitoWebProfile(isolated = true)))
    }
}
