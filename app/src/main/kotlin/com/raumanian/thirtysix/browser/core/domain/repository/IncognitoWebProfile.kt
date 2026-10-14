package com.raumanian.thirtysix.browser.core.domain.repository

/**
 * The incognito session's WebView profile, where the engine has profiles (`MULTI_PROFILE`).
 *
 * With a profile of its own, incognito has its own cookies, storage, cache and service workers;
 * normal browsing's are never touched, and ending the session wipes the profile.
 */
interface IncognitoWebProfile {
    /** Whether incognito WebViews get a profile of their own on this engine. */
    fun isIsolated(): Boolean

    /** The first incognito tab opens: later incognito WebViews use a fresh profile. */
    fun beginSession()

    /** The last incognito tab closed: everything the session's profile holds is wiped. */
    fun endSession()

    /** The open session's profile name, starting a session when none is open. Main thread. */
    fun sessionProfileName(): String

    /** Deletes the profiles earlier processes left; does nothing after the first call. Main thread. */
    fun collectLeftovers()

    /** Whether [name] is one of this app's incognito profiles. */
    fun isSessionProfile(name: String): Boolean

    /** Names of incognito profiles that still exist on disk; for tests and diagnostics. Main thread. */
    fun existingProfileNames(): List<String>
}
