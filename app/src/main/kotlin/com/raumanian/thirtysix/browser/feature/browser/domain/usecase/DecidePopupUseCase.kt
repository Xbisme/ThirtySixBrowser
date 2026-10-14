package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import javax.inject.Inject

/** What happens to a page's request for a new window. */
sealed interface PopupDecision {
    /** A new tab, active, as incognito as its opener. */
    data class OpenInNewTab(val incognito: Boolean) : PopupDecision

    /** At the tab cap: the existing tab-limit message. */
    data object TabLimit : PopupDecision

    /** Not triggered by the person: blocked, with a notice. */
    data object Blocked : PopupDecision
}

/**
 * Pop-up policy: a window the person asked for (a tap) opens as a new tab; one the page opened on
 * its own is blocked. [openUrlFor] lets the blocked notice offer Open only for an http(s) address.
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
