package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page

import com.raumanian.thirtysix.browser.core.domain.model.SiteCapability
import com.raumanian.thirtysix.browser.feature.browser.domain.model.LongPressAction
import com.raumanian.thirtysix.browser.feature.browser.domain.model.LongPressMenu
/**
 * Something a page asked of the browser that needs the person, tied to the tab and page
 * ([generation]) that made it; a request whose page is gone is cancelled, not shown over another.
 * The platform callback is settled exactly once: answer, cancel or staleness, whichever is first.
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

    /** Answers the page as refused / cancelled. */
    abstract fun cancel()

    /** Camera / microphone / location. [followers] are identical requests merged into this one. */
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

        /** The capabilities the dialog asks about: those not already allowed. */
        var askedCapabilities: Set<SiteCapability> = capabilities

        /** Already allowed but the Android permission was revoked: only the system prompt is needed. */
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

        /** The page withdrew the request itself: settled without answering. */
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

    /** A page's file input; the launcher answers with the chosen files or none. */
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

    /** alert / confirm / prompt; [showSuppress] from the second dialog on a page. */
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

    /** "Leave site?". Cancel means stay. */
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

    /** HTTP authentication. Credentials pass straight through and are never kept. */
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

    /** Asks before an incognito page hands its address to another app; cancelling opens nothing. */
    class OpenExternalApp(
        tabId: Long,
        generation: Long,
        origin: String,
        private val onOpen: () -> Unit,
    ) : PageRequest(tabId, generation, origin) {
        fun open() = settle(onOpen)

        override fun cancel() = settle {}
    }
}

/** The kind of JavaScript dialog a page showed. */
enum class JsDialogKind { Alert, Confirm, Prompt }

/** What a page's file input asked for, free of platform types. */
data class FileChooserRequest(
    val acceptTypes: List<String>,
    val allowMultiple: Boolean,
    val captureRequested: Boolean,
)
