package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import javax.inject.Inject

/** Spec 021 contracts C1.3 — what happens to a page's request for a new window. */
sealed interface PopupDecision {
    /** A new tab, active, as incognito as its opener (FR-025). */
    data class OpenInNewTab(val incognito: Boolean) : PopupDecision

    /** At the tab cap: the existing tab-limit message (FR-028). */
    data object TabLimit : PopupDecision

    /** Not triggered by the person: blocked, with a notice (FR-026). */
    data object Blocked : PopupDecision
}

/**
 * Spec 021 contracts C1.3 — pop-up policy. A window the person asked for (a tap) opens as a new
 * tab; one the page opened on its own is blocked. [openUrlFor] decides whether the blocked
 * notice can offer Open: only for an http(s) address (FR-026a).
 */
class DecidePopupUseCase @Inject constructor() {
    operator fun invoke(isUserGesture: Boolean, isOpenerIncognito: Boolean, atTabCap: Boolean): PopupDecision =
        when {
            !isUserGesture -> PopupDecision.Blocked
            atTabCap -> PopupDecision.TabLimit
            else -> PopupDecision.OpenInNewTab(incognito = isOpenerIncognito)
        }

    fun openUrlFor(target: String?): String? {
        val scheme = target?.substringBefore(':', missingDelimiterValue = "")?.lowercase()
        return target?.takeIf { scheme == HTTP || scheme == HTTPS }
    }

    private companion object {
        const val HTTP = "http"
        const val HTTPS = "https"
    }
}
