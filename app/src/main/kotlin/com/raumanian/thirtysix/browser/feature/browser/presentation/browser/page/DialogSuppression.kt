package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page

import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
/** Whether a page's dialog is shown, and with the "Don't let this site show more dialogs" box. */
sealed interface DialogAdmission {
    data class Show(val offerSuppress: Boolean) : DialogAdmission

    /** The person stopped this page's dialogs: answered as cancelled, not shown. */
    data object Suppressed : DialogAdmission
}

/**
 * Flood control for a page's alert / confirm / prompt, counted per page (tab + generation): the
 * second dialog offers the checkbox; once ticked, later ones are cancelled until the page changes.
 */
class DialogSuppression {
    private var page: Pair<Long, Long>? = null
    private var count = 0
    private var suppressed = false

    fun admit(tabId: Long, generation: Long): DialogAdmission {
        resetIfNewPage(tabId, generation)
        if (suppressed) return DialogAdmission.Suppressed
        count += 1
        return DialogAdmission.Show(offerSuppress = count >= BrowserLimits.DIALOG_SUPPRESS_FROM_COUNT)
    }

    fun suppress(tabId: Long, generation: Long) {
        resetIfNewPage(tabId, generation)
        suppressed = true
    }

    private fun resetIfNewPage(tabId: Long, generation: Long) {
        val current = tabId to generation
        if (page != current) {
            page = current
            count = 0
            suppressed = false
        }
    }
}
