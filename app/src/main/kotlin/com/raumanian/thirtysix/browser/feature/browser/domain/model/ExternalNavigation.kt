package com.raumanian.thirtysix.browser.feature.browser.domain.model

/** A page's navigation to an address the WebView does not load itself; [scheme] is lower-case. */
data class ExternalNavigation(
    val url: String,
    val scheme: String,
    val isMainFrame: Boolean,
    val hasGesture: Boolean,
    val isIncognito: Boolean,
)

/** What happens to an [ExternalNavigation]. */
sealed interface ExternalLinkDecision {
    /** Nothing opens and nothing is shown. */
    data object Drop : ExternalLinkDecision

    /** Nothing opens; the person is offered to open the app. */
    data object Offer : ExternalLinkDecision

    /** The person confirms first that the other app may see the link. */
    data object Confirm : ExternalLinkDecision

    data object Launch : ExternalLinkDecision
}
