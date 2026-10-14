package com.raumanian.thirtysix.browser.presentation.browser.privacy

import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoProtectionExceptions
import com.raumanian.thirtysix.browser.core.domain.repository.PrivacyScriptBundles
import com.raumanian.thirtysix.browser.core.domain.repository.PrivacyScriptSession
import com.raumanian.thirtysix.browser.core.domain.repository.ProtectionExceptionRepository
import com.raumanian.thirtysix.browser.domain.privacy.PrivacyScriptComposer
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** Spec 024 research R6 — what every [PrivacyScriptInstaller] reads. */
class PrivacyScriptInputs @Inject constructor(
    val session: PrivacyScriptSession,
    val bundles: PrivacyScriptBundles,
    val exceptions: ProtectionExceptionRepository,
    val incognitoExceptions: IncognitoProtectionExceptions,
)

/**
 * Spec 024 research R5, R6 (contracts C20) — the page-start scripts of one WebView.
 *
 * Installed with `WebViewCompat.addDocumentStartJavaScript(…, "*")` — the only script-injection
 * call in the app (Constitution §II-2, INV-8) — so they run at the start of every frame. The
 * composed text carries the sites with protection off, and the scripts themselves stand down when
 * the top-level page belongs to one (FR-020). On a web engine without the feature nothing is
 * installed and the protections are simply off (FR-022).
 *
 * The script is installed again — the old handler removed first — whenever the session secret
 * (Fire), the bundles (first load) or the saved exceptions change, and on [reinstall], which the
 * per-site switch calls before reloading so the reload already runs the new rule. Main thread only.
 */
class PrivacyScriptInstaller(
    private val webView: WebView,
    private val isIncognito: Boolean,
    private val inputs: PrivacyScriptInputs,
    scope: CoroutineScope,
) {
    private var handler: ScriptHandler? = null

    private val job: Job? = if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
        scope.launch {
            combine(
                inputs.session.secret,
                inputs.bundles.loaded.filterNotNull(),
                inputs.exceptions.observeAll(),
            ) { secret, bundles, saved -> Triple(secret, bundles, saved.mapTo(HashSet()) { it.site }) }
                .collect { (secret, bundles, saved) -> install(secret, bundles, exceptedSites(saved)) }
        }
    } else {
        null
    }

    /** Re-installs now from the current inputs (after a per-site switch, before the reload). */
    fun reinstall() {
        val bundles = inputs.bundles.loaded.value ?: return
        install(inputs.session.secret.value, bundles, exceptedSites(inputs.exceptions.currentSites()))
    }

    /** The WebView is going away. */
    fun detach() {
        job?.cancel()
        handler?.remove()
        handler = null
    }

    /** Incognito never reads the saved list, and the saved list never sees incognito choices (Spec 023 FR-014). */
    private fun exceptedSites(saved: Set<String>): Set<String> =
        if (isIncognito) inputs.incognitoExceptions.snapshot() else saved

    private fun install(secret: String, bundles: List<String>, excepted: Set<String>) {
        handler?.remove()
        handler = null
        if (bundles.isEmpty()) return
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            val script = PrivacyScriptComposer.compose(secret, excepted, bundles)
            handler = runCatching { WebViewCompat.addDocumentStartJavaScript(webView, script, ALL_ORIGINS) }.getOrNull()
        }
    }

    private companion object {
        /** Research R5 — every frame origin; the scripts narrow it by the top-level page themselves. */
        val ALL_ORIGINS: Set<String> = setOf("*")
    }
}
