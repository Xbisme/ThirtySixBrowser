package com.raumanian.thirtysix.browser.core.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * Seeds fingerprinting noise. Memory only, and replaced at process start and on Fire, so a site
 * cannot link visits across either.
 */
interface PrivacyScriptSession {
    /** Lower-case hex; never empty. */
    val secret: StateFlow<String>

    fun regenerate()
}
