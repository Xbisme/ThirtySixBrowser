package com.raumanian.thirtysix.browser.presentation.browser.page

import com.raumanian.thirtysix.browser.domain.model.LongPressAction
import com.raumanian.thirtysix.browser.domain.model.LongPressMenu
import com.raumanian.thirtysix.browser.domain.model.SiteCapability

/**
 * Spec 021 research R1 / data-model §3 — something a page asked of the browser that needs the
 * person. Built by the WebView clients, shown one at a time by `PageRequestHost`.
 *
 * Each request is tied to the tab and the page ([generation]) that made it; a request whose page
 * is gone is cancelled rather than shown over another page (FR-034). The platform callback is
 * hidden behind lambdas and settled **exactly once** — whichever of answer, cancel or staleness
 * comes first wins (data-model INV-3).
 */
@Suppress("LongParameterList") // Each variant carries its page identity plus the platform answers.
sealed class PageRequest(val tabId: Long, val generation: Long, val origin: String) {
    private var settled = false

    val isSettled: Boolean get() = settled

    /** Runs [answer] only if nothing has answered this request yet. Main thread only. */
    protected fun settle(answer: () -> Unit) {
        if (settled) return
        settled = true
        answer()
    }

    /** Answers the page as refused / cancelled (FR-034, FR-033). */
    abstract fun cancel()

    /** Camera / microphone / location (FR-001). [followers] are identical requests merged into this one. */
    class Permission(
        tabId: Long,
        generation: Long,
        origin: String,
        val capabilities: Set<SiteCapability>,
        val isIncognito: Boolean,
        private val onGrant: () -> Unit,
        private val onDeny: () -> Unit,
        /** The platform object this request answers, so a page's own cancellation can find it. */
        val token: Any? = null,
    ) : PageRequest(tabId, generation, origin) {
        private val followers = mutableListOf<Permission>()

        /** What the dialog asks about — the capabilities not already allowed (contracts C1.1 `Ask`). */
        var askedCapabilities: Set<SiteCapability> = capabilities

        /**
         * The site is already allowed but the app lacks the Android permission (revoked in system
         * settings): only the system prompt is needed, not the app's dialog.
         */
        var needsSystemPermissionOnly: Boolean = false

        fun grant() = settle {
            onGrant()
            followers.forEach(Permission::grant)
        }

        fun deny() = settle {
            onDeny()
            followers.forEach(Permission::deny)
        }

        override fun cancel() = deny()

        /** The page withdrew the request itself: settled without answering (C3.1). */
        fun withdraw() = settle {}

        fun isSameAs(other: Permission): Boolean =
            tabId == other.tabId &&
                generation == other.generation &&
                origin == other.origin &&
                capabilities == other.capabilities

        fun absorb(other: Permission) {
            followers += other
        }
    }

    /** A page's file input (FR-011 – FR-013). The launcher answers with the chosen files or none. */
    class FileChooser(
        tabId: Long,
        generation: Long,
        origin: String,
        val request: FileChooserRequest,
        private val onResult: (Array<android.net.Uri>?) -> Unit,
    ) : PageRequest(tabId, generation, origin) {
        fun answer(uris: Array<android.net.Uri>?) = settle { onResult(uris) }

        override fun cancel() = answer(null)
    }

    /** alert / confirm / prompt (FR-029). [showSuppress] from the second dialog on a page (FR-031). */
    class JsDialog(
        tabId: Long,
        generation: Long,
        origin: String,
        val kind: JsDialogKind,
        val message: String,
        val defaultText: String?,
        val showSuppress: Boolean,
        private val onConfirm: (String?) -> Unit,
        private val onCancel: () -> Unit,
    ) : PageRequest(tabId, generation, origin) {
        fun confirm(text: String? = null) = settle { onConfirm(text) }

        override fun cancel() = settle(onCancel)
    }

    /** "Leave site?" (FR-030). Cancel means stay. */
    class LeavePage(
        tabId: Long,
        generation: Long,
        origin: String,
        private val onLeave: () -> Unit,
        private val onStay: () -> Unit,
    ) : PageRequest(tabId, generation, origin) {
        fun leave() = settle(onLeave)

        override fun cancel() = settle(onStay)
    }

    /** HTTP authentication (FR-032). Credentials pass straight through and are never kept. */
    class HttpAuth(
        tabId: Long,
        generation: Long,
        origin: String,
        val host: String,
        val realm: String?,
        private val onProceed: (String, String) -> Unit,
        private val onCancel: () -> Unit,
    ) : PageRequest(tabId, generation, origin) {
        fun proceed(username: String, password: String) = settle { onProceed(username, password) }

        override fun cancel() = settle(onCancel)
    }

    /** Long-press on a link or an image (FR-018 – FR-024). */
    class LongPress(
        tabId: Long,
        generation: Long,
        origin: String,
        val menu: LongPressMenu,
        private val onAction: (LongPressAction) -> Unit,
    ) : PageRequest(tabId, generation, origin) {
        fun choose(action: LongPressAction) = settle { onAction(action) }

        override fun cancel() = settle {}
    }
}

/** Spec 021 — the kind of JavaScript dialog a page showed. */
enum class JsDialogKind { Alert, Confirm, Prompt }

/** Spec 021 research R3 — what a page's file input asked for, free of platform types. */
data class FileChooserRequest(
    val acceptTypes: List<String>,
    val allowMultiple: Boolean,
    val captureRequested: Boolean,
)
