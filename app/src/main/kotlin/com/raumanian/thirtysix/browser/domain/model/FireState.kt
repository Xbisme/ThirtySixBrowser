package com.raumanian.thirtysix.browser.domain.model

/** Spec 024 data-model "FireState" — the app-wide state of the Fire action. */
sealed interface FireState {
    /** No run in progress; the Fire controls are enabled. */
    data object Idle : FireState

    /** A run is in progress; the overlay is shown and the Fire controls are disabled (INV-5). */
    data object Running : FireState

    /** The run ended; the overlay fades out, then the UI acknowledges it and the state is [Idle]. */
    data class Finished(val result: FireResult) : FireState
}

/** Spec 024 data-model "Fire run" — the steps that run in their own failure boundary. */
enum class FireStep {
    CollectHosts,
    CloseIncognito,
    CloseTabs,
    History,
    CookiesAndSiteData,
    WebCache,
    SitePermissions,
    Favicons,
    SessionTraces,
}

/**
 * Spec 024 data-model — how cookies and site data were cleared.
 * - [Complete]: no fireproof site, and the web engine cleared everything (Spec 016 path).
 * - [PerSite]: fireproof sites kept; every other site found was deleted site by site.
 * - [Fallback]: an older web engine; some kinds of site data may remain (research R3, FR-003b).
 */
enum class SiteDataPath { Complete, PerSite, Fallback }

/** Spec 024 — the outcome of one Fire run. Never shown as an error; it is logged. */
data class FireResult(
    val failedSteps: Set<FireStep>,
    val siteDataPath: SiteDataPath?,
)
