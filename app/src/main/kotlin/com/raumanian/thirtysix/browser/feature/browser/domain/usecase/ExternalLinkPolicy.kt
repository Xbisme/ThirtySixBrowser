package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

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
}
