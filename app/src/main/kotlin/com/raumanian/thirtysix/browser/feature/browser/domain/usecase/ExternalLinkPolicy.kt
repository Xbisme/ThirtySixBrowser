package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.feature.browser.domain.model.ExternalLinkDecision
import com.raumanian.thirtysix.browser.feature.browser.domain.model.ExternalNavigation

/** A page opens another app only from the main frame after a tap; incognito asks first. */
object ExternalLinkPolicy {
    private val NEVER_HANDED_OFF: Set<String> = setOf("javascript", "file", "content")

    fun decide(nav: ExternalNavigation): ExternalLinkDecision = when {
        nav.scheme in NEVER_HANDED_OFF || !nav.isMainFrame -> ExternalLinkDecision.Drop
        !nav.hasGesture -> ExternalLinkDecision.Offer
        nav.isIncognito -> ExternalLinkDecision.Confirm
        else -> ExternalLinkDecision.Launch
    }

    // WebView drops the gesture bit on a server redirect, so a redirect right after a tap counts as tapped.
    fun isTapped(hasGesture: Boolean, isRedirect: Boolean, sinceLastTapMs: Long?): Boolean =
        hasGesture || isRedirect && sinceLastTapMs != null && sinceLastTapMs in 0..BrowserLimits.EXTERNAL_TAP_WINDOW_MS
}
