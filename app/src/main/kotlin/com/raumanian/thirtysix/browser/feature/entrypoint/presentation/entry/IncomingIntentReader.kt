package com.raumanian.thirtysix.browser.feature.entrypoint.presentation.entry

import android.app.SearchManager
import android.content.Intent
import com.raumanian.thirtysix.browser.feature.entrypoint.domain.model.IncomingRequest
import javax.inject.Inject

/**
 * Spec 020 research R4 / contracts C1 — turns an `Intent` from another app into an
 * [IncomingRequest]. The only place that touches `Intent` for this feature; every decision
 * about the content is made by the domain layer afterwards.
 *
 * Total: an unknown action, a missing extra or an extra of the wrong type yields `null`, and
 * nothing here throws. Nothing is logged (FR-016).
 */
class IncomingIntentReader @Inject constructor() {

    fun read(intent: Intent?): IncomingRequest? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString?.let(IncomingRequest::OpenAddress)
            Intent.ACTION_SEND -> intent.textExtra(Intent.EXTRA_TEXT)?.let(IncomingRequest::InterpretText)
            Intent.ACTION_PROCESS_TEXT ->
                intent.textExtra(Intent.EXTRA_PROCESS_TEXT)?.let(IncomingRequest::SearchText)
            Intent.ACTION_WEB_SEARCH -> intent.textExtra(SearchManager.QUERY)?.let(IncomingRequest::SearchText)
            else -> null
        }
    }

    /** A text extra, accepting any `CharSequence` (styled text included); anything else → null. */
    private fun Intent.textExtra(name: String): String? =
        runCatching { extras?.getCharSequence(name)?.toString() }.getOrNull()
}
