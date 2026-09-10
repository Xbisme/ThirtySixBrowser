package com.raumanian.thirtysix.browser.core.constants

/**
 * Spec 011 — limits for the multi-tab system.
 *
 * Reserved per Constitution §III table row "Magic numbers / limits → core/constants/BrowserLimits.kt".
 * Future limits (max history days, max bookmarks, etc.) live here as the corresponding specs
 * (014 history, 013 bookmarks) ship.
 */
object BrowserLimits {
    /**
     * Maximum number of open tabs the user may keep at once. When reached, the new-tab
     * affordances (BrowserScreen long-press AND TabsScreen "new tab" card) are visually
     * disabled and a localized "max tabs reached" message is surfaced (FR-016 / SC-004).
     *
     * Value 50 is mid-range for Android browsers (Chrome ~100, DuckDuckGo no hard cap,
     * Firefox no hard cap); 50 keeps memory predictable on min-spec devices (Android 7,
     * 2 GB RAM) given Spec 011's single-active-WebView strategy (FR-027 / R8).
     */
    const val MAX_TABS: Int = 50

    /**
     * Spec 012 — independent ceiling for incognito tabs (Q1 clarification).
     *
     * Incognito tabs do NOT count against [MAX_TABS]. A user with [MAX_TABS] normal
     * tabs can still open up to [MAX_INCOGNITO_TABS] incognito tabs. Defaults to the
     * same value as [MAX_TABS] for symmetry; can be tuned independently if memory
     * pressure surfaces in stress tests (SC-005, the 100-cycle gate).
     *
     * Hitting this cap surfaces a distinct localized error string
     * (`tabs_error_max_incognito_tabs_reached`) — different from the [MAX_TABS]
     * cap message — so users immediately understand which kind of tab is full.
     */
    const val MAX_INCOGNITO_TABS: Int = 50

    /**
     * Spec 013 — bookmark title length cap. Enforced by the manual-add and edit
     * dialogs as `maxLength` on the input field (FR-006 / FR-022) and re-asserted
     * inside `AddBookmarkUseCase` / `UpdateBookmarkUseCase` as a safety net before
     * the value reaches the repository. 200 chars accommodates non-Latin scripts.
     */
    const val MAX_BOOKMARK_TITLE_LENGTH: Int = 200

    /**
     * Spec 013 — bookmark URL length cap. Matches the de-facto limit modern
     * browsers enforce for navigation URLs.
     */
    const val MAX_BOOKMARK_URL_LENGTH: Int = 2048

    /**
     * Spec 013 — folder name length cap. Folder names are short labels.
     */
    const val MAX_FOLDER_NAME_LENGTH: Int = 100

    /**
     * Spec 013 — power-user soft envelope from Spec 005 plan. Not enforced as a
     * hard reject in v1.0; reserved for future "approaching capacity" snackbar.
     */
    const val MAX_BOOKMARKS: Int = 10_000

    /**
     * Spec 013 — power-user soft envelope. Same policy as [MAX_BOOKMARKS].
     */
    const val MAX_FOLDERS: Int = 1_000

    /**
     * Spec 014 FR-011a / Q4 clarification — the History screen begins live-filtering only once
     * the user-typed search query reaches this length. Below it (0 or 1 characters), the full
     * unfiltered list is shown. Avoids wasteful filtering on a single common letter that would
     * match nearly every entry, while staying instantaneous past the threshold (no debounce).
     */
    const val SEARCH_MIN_CHARS: Int = 2

    /**
     * Spec 014 — defensive cap on the History search input length to bound the per-keystroke
     * filter cost. 200 characters is far beyond any reasonable URL substring or page-title query
     * a user would type by hand; the search field's `maxLength` enforces it at the input
     * boundary so the in-memory `String.contains` scan never sees a pathological query.
     */
    const val MAX_HISTORY_QUERY_LENGTH: Int = 200

    /**
     * Spec 014 — retention window for browsing history, in days.
     *
     * History is otherwise unbounded: `HistoryRepository.observeAll()` loads the whole
     * table, so memory scales linearly with how long the app has been used. Measured on
     * an API 36 emulator, 10 000 rows cost ~3 MB of Java heap (~300 B/row) — fine today,
     * but a heavy user (~200 page loads/day) reaches 100 000 rows inside two years, and
     * ~30 MB for one screen is not acceptable on a minSdk-24 device with a ~96 MB heap
     * cap.
     *
     * Pruning the table (rather than capping the query with `LIMIT`) is deliberate: it
     * keeps the "what the list shows is what actually exists" invariant, so search can
     * never report "no matches" for a row that is still sitting in the database.
     *
     * 90 days matches the default retention of mainstream browsers. Making this
     * user-configurable belongs to Spec 016 (settings-screen).
     */
    const val MAX_HISTORY_DAYS: Int = 90

    /**
     * Spec 015 FR-005 — hard upper bound on a downloaded file's name, in characters.
     *
     * The name reaching this bound has already been derived from a server-supplied
     * `Content-Disposition` header, which is attacker-controlled: an unbounded name can
     * exhaust a filesystem's per-component limit and make the write fail in ways that are
     * awkward to report. 255 is the per-component limit on every filesystem Android ships
     * on, so bounding here means the sanitiser never emits a name the platform will reject.
     */
    const val MAX_DOWNLOAD_FILENAME_LENGTH: Int = 255

    /**
     * Spec 015 SC-007 — how often the Downloads screen re-reads live transfer state, in
     * milliseconds.
     *
     * SC-007 requires an in-flight row's progress to advance visibly at least once per
     * second, which sets the ceiling; polling faster buys nothing a user can perceive and
     * costs battery. The loop runs only while the screen is in the foreground AND at least
     * one entry is actually in flight (research.md R7), so a list of finished downloads
     * polls zero times.
     */
    const val DOWNLOAD_STATUS_POLL_INTERVAL_MS: Long = 1_000L
}
