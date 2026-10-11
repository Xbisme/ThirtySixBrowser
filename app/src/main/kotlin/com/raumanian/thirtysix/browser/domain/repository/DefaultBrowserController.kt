package com.raumanian.thirtysix.browser.domain.repository

/**
 * Spec 020 contracts C3.1 — whether the system currently names this app its default browser.
 *
 * The system owns this state; the app never stores it (data-model §7) and re-reads it every time
 * it comes back to the foreground (FR-022). Kept in `domain/repository/` beside Spec 016's
 * `AppLanguageController`, the placement chosen for platform-state seams that use cases consume.
 * Only the implementation touches Android.
 *
 * Total: a failed platform call answers `false`, never throws. Called on the main thread.
 */
fun interface DefaultBrowserController {
    fun isDefaultBrowser(): Boolean
}
