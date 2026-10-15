package com.raumanian.thirtysix.browser.core.domain.repository

/**
 * Whether the system names this app its default browser. The system owns this, so it is re-read
 * on every return to the foreground. Answers false on failure. Called on the main thread.
 */
fun interface DefaultBrowserController {
    fun isDefaultBrowser(): Boolean
}
