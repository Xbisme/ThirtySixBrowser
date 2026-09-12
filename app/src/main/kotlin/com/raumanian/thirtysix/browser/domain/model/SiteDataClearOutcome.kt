package com.raumanian.thirtysix.browser.domain.model

/** Spec 016 — what the site-data step of a clear achieved. See research R5 and spec A16 / A17. */
enum class SiteDataClearOutcome {
    /**
     * The web engine removed cookies and every kind of site data — including service workers and
     * Cache Storage — in one operation. That operation also emptied the web page cache (spec A16).
     */
    Complete,

    /**
     * The web engine predates complete removal. Cookies and web storage (local and session
     * storage, IndexedDB, file-system storage) were removed; service workers and Cache Storage
     * were not, and the web page cache was not touched (spec A17). Reported as cleared, not failed.
     */
    Partial,

    /** A required platform call threw or never completed. The category is reported as failed. */
    Failed,
}
