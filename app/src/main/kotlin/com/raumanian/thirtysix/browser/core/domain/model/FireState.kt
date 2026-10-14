package com.raumanian.thirtysix.browser.core.domain.model

sealed interface FireState {
    data object Idle : FireState

    /** The overlay is shown and the Fire controls are disabled. */
    data object Running : FireState

    /** The run ended; the overlay fades out, then the UI acknowledges it and the state is [Idle]. */
    data class Finished(val result: FireResult) : FireState
}

/** Each step runs in its own failure boundary. */
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
 * [Complete]: no fireproof site, the engine cleared everything. [PerSite]: fireproof sites kept,
 * every other site deleted one by one. [Fallback]: an older engine; some site data may remain.
 */
enum class SiteDataPath { Complete, PerSite, Fallback }

/** Never shown as an error; it is logged. */
data class FireResult(
    val failedSteps: Set<FireStep>,
    val siteDataPath: SiteDataPath?,
)
