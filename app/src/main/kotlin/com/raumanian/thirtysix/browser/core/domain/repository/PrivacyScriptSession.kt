package com.raumanian.thirtysix.browser.core.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * Spec 024 FR-026, FR-003a — the per-session secret that seeds fingerprinting noise. In memory
 * only: it changes when the process starts and when Fire runs, so a site cannot link visits across
 * either (research R9).
 */
interface PrivacyScriptSession {
    /** Lower-case hex; never empty. */
    val secret: StateFlow<String>

    /** Replace [secret] with a fresh random value. */
    fun regenerate()
}
