package com.raumanian.thirtysix.browser.core.domain.model

enum class SiteDataClearOutcome {
    /**
     * Everything removed in one operation, service workers and Cache Storage included; it also
     * empties the web cache.
     */
    Complete,

    /**
     * An older engine: cookies and web storage removed, service workers, Cache Storage and the
     * web cache not. Reported as cleared, not failed.
     */
    Partial,

    /** A required platform call threw or never completed. */
    Failed,
}
