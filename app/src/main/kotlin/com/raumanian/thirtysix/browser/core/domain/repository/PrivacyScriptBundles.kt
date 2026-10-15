package com.raumanian.thirtysix.browser.core.domain.repository

import kotlinx.coroutines.flow.StateFlow

/** The page-start scripts bundled in the APK, never downloaded; read once per process off the main thread. */
interface PrivacyScriptBundles {
    /**
     * `null` until loaded; then the scripts in install order — the fingerprinting shield first, so it
     * wraps the APIs before any page or consent-tool code reads them. An empty list if loading
     * failed: the protections are then simply off (fail open).
     */
    val loaded: StateFlow<List<String>?>

    /** Starts loading if it has not started; returns at once. */
    fun preload()
}
