package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.protection

import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoProtectionExceptions
import com.raumanian.thirtysix.browser.core.domain.repository.PrivacyScriptBundles
import com.raumanian.thirtysix.browser.core.domain.repository.PrivacyScriptSession
import com.raumanian.thirtysix.browser.core.domain.repository.ProtectionExceptionRepository
import com.raumanian.thirtysix.browser.feature.browser.domain.protection.PrivacyScriptComposer
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** What every [PrivacyScriptInstaller] reads. */
class PrivacyScriptInputs @Inject constructor(
    val session: PrivacyScriptSession,
    val bundles: PrivacyScriptBundles,
    val exceptions: ProtectionExceptionRepository,
    val incognitoExceptions: IncognitoProtectionExceptions,
)

/**
 * The page-start scripts of one WebView, via `addDocumentStartJavaScript(…, "*")`, the app's only
 * script injection. Reinstalled when the session secret, bundles or exceptions change; without the
 * engine feature nothing is installed. Main thread only.
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

    /** Re-installs now, so the reload after a per-site switch already runs the new rule. */
    fun reinstall() {
        val bundles = inputs.bundles.loaded.value ?: return
        install(inputs.session.secret.value, bundles, exceptedSites(inputs.exceptions.currentSites()))
    }

    fun detach() {
        job?.cancel()
        handler?.remove()
        handler = null
    }

    /** Incognito never reads the saved list, and the saved list never sees incognito choices. */
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
        /** Every frame origin; the scripts narrow it by the top-level page themselves. */
        val ALL_ORIGINS: Set<String> = setOf("*")
    }
}
