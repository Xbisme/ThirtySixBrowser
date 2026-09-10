# Feature Specification: History View

**Feature Branch**: `014-history-view`
**Created**: 2026-05-08
**Status**: Draft
**Input**: User description: "history-view: User-facing browsing history screen. Auto-record each successful page load from BrowserWebView (URL + title + visited_at + optional favicon) into the existing HistoryEntryEntity (Room, Spec 005) — but ONLY for non-incognito tabs (Spec 012 rule: incognito MUST NOT persist history). Provide a HistoryScreen reachable from the bottom-bar (mirroring the bookmarks entry pattern from Spec 013) that shows visits grouped by day section headers (Today / Yesterday / specific date), each row showing favicon + page title + hostname + time-of-visit, in reverse-chronological order. Tap a row → replace active tab with that URL (mirror Spec 013 Q2 bookmark-tap behaviour). Long-press → action sheet (Open in new tab / Delete entry / Copy URL). Real-time global search box at the top filters across title + URL substring match (case-insensitive). Clear-all affordance with confirmation dialog (cascade delete all rows). Empty state when no history. Localize all UI strings across all 8 locales (EN/VI/DE/RU/KO/JA/ZH/FR). Out of scope: pagination/lazy loading (load all rows for v1.0; HistoryDao Flow already observes), per-day-bucket clear, per-site grouping, history retention/pruning policy (deferred to Spec 016 settings), browser-engine forward-history (separate concept). Respect Constitution §III no-hardcode + Clean Architecture (HistoryRepository interface + impl + use cases + ViewModel + UiState + sealed events). Zero new packages preferred — `material-icons-core` + Compose BOM should suffice. APK delta budget should be modest given mostly UI + i18n."

## Clarifications

### Session 2026-05-08

- Q: When the active tab is incognito and the user opens the History screen, should past (non-incognito) history still be displayed, or should the screen be locked-down for the incognito session? → A: Show history normally regardless of which tab is active — ThirtySixBrowser uses per-tab incognito (Spec 012), not Chrome's per-window pattern; screen behaviour is identical between normal and incognito contexts.
- Q: For "Open in new tab" from a history row, should the new tab match the active tab's incognito mode or always be a normal tab? → A: Always open in a normal (non-incognito) tab, regardless of the active tab's mode. History entries are inherently non-incognito (FR-002), so opening them in a normal tab matches the source-of-truth privacy posture. Cap-check uses `MAX_TABS` only.
- Q: When the same URL is visited multiple times, should the History screen show each visit as a separate row or visually collapse repeats? → A: One row per visit — no display-layer collapse. The chronological-log invariant from Spec 005 surfaces 1:1; "Delete entry" deletes exactly one DB row; search results match what users see. SC-005 perf budget already sized for the worst case.
- Q: How responsive should the search filter be — filter on every keystroke, debounce, or minimum-character threshold? → A: Filter on every keystroke from 2+ characters; 0–1 character shows the full unfiltered list. No debounce. Avoids wasteful 1-char filters that match nearly everything, while staying instantaneous past the threshold.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Browse my visit history grouped by day (Priority: P1)

A user who has visited several pages over the last few days opens the History screen from the bottom bar and sees their past visits grouped under day headers (Today, Yesterday, then specific dates), each row showing the page favicon, title, hostname, and time of visit, with the most recent visits at the top. They can scroll through to find a page they remember visiting, and tapping a row reopens that page in their current active tab.

**Why this priority**: This is the core value of the feature — without it, the screen is not useful. The auto-record mechanism + the day-grouped list is the minimum viable slice that delivers user value (find and re-visit something I already looked at). All other stories depend on this scaffolding existing.

**Independent Test**: With the app freshly installed, visit 3 distinct URLs in a non-incognito tab over a short period, then open History from the bottom bar. The list shows exactly those 3 entries grouped under "Today", in reverse-chronological order, each with correct title + hostname + time. Tapping any row navigates the active tab to that URL.

**Acceptance Scenarios**:

1. **Given** a non-incognito tab finishes loading a page successfully, **When** the page reaches the loaded state with a valid title, **Then** a new history entry is recorded containing the final URL, page title, current timestamp, and (when available) the favicon reference.
2. **Given** the user has visits across today, yesterday, and an earlier date, **When** they open the History screen, **Then** entries are visually grouped under three section headers in this order: "Today", "Yesterday", then the explicit localized date for the older group, and within each group entries are ordered newest-first.
3. **Given** the History screen is open and entries are visible, **When** the user taps a history row, **Then** the active tab navigates to the entry's URL and the History screen is dismissed (returning the user to the browser).
4. **Given** the user is browsing in an incognito tab, **When** any page successfully loads in that tab, **Then** no history entry is created for that visit.
5. **Given** a non-incognito page reload of a URL the user already visited earlier, **When** the page successfully loads again, **Then** a new (separate) history entry is recorded with the new timestamp (chronological-log invariant from Spec 005 — the same URL appears multiple times).

---

### User Story 2 - Search my history by title or URL (Priority: P2)

A user who remembers part of a page title or URL but cannot scroll-locate it types into a search box at the top of the History screen. The list filters in real time to show only entries whose title or URL contains the entered text (case-insensitive). When they clear the search box, the full list is restored.

**Why this priority**: Once a user has accumulated more than ~20 visits, scrolling to find one becomes tedious. Search makes the feature useful at realistic data volumes. It is independent of long-press actions (US3) and clear-all (US4) — search alone with the US1 list provides clear standalone value.

**Independent Test**: With at least 5 history entries spanning multiple titles/hostnames, type a substring matching exactly one entry's title; only that entry remains visible. Type a substring matching only a URL hostname; the matching entries appear. Clear the box; the full grouped list returns unchanged.

**Acceptance Scenarios**:

1. **Given** the History screen has multiple entries and the search box is empty, **When** the user types text into the search box, **Then** the visible entries shrink to only those whose title OR URL contains the typed text, treating uppercase and lowercase as equivalent.
2. **Given** the search box has text and matches exist, **When** results are shown, **Then** they remain grouped under the appropriate day headers (groups with zero matches are hidden), preserving reverse-chronological order within each group.
3. **Given** the search box has text and zero entries match, **When** the user looks at the screen, **Then** a clear "no matches" message is displayed in place of the list.
4. **Given** the user has typed search text, **When** they clear the search box (or tap a clear-text affordance), **Then** the full grouped list is restored immediately.
5. **Given** the search box is non-empty, **When** new history entries arrive in the background (because another tab finishes loading), **Then** the visible filtered results update to include any new entry that matches the active query.

---

### User Story 3 - Per-entry actions: open in new tab, delete, copy URL (Priority: P2)

A user who long-presses a history row sees an action sheet offering three actions: "Open in new tab" (creates a new non-incognito tab loading that URL), "Delete entry" (removes only that single visit from history), and "Copy URL" (copies the URL to the system clipboard). Each action provides immediate feedback and dismisses the action sheet.

**Why this priority**: These three operations cover the most common per-entry needs without bloating the UI. They are independent of bulk clear (US4): a user might never clear-all but routinely groom individual entries.

**Independent Test**: Long-press any history row → action sheet appears. Pick "Open in new tab" → a new tab opens loading that URL while the History screen dismisses. Long-press another row → "Delete entry" → that one row disappears from the list, others are untouched. Long-press a third row → "Copy URL" → paste into the address bar of any tab confirms the exact URL was copied.

**Acceptance Scenarios**:

1. **Given** the History screen displays at least one entry, **When** the user long-presses a row, **Then** an action sheet appears showing the three actions in this order: "Open in new tab", "Delete entry", "Copy URL".
2. **Given** the action sheet is open, **When** the user picks "Open in new tab", **Then** a new non-incognito tab is created loading that URL, the History screen closes, and the new tab becomes the active tab.
3. **Given** the action sheet is open, **When** the user picks "Delete entry", **Then** only that single history row is removed (other rows for the same URL at different timestamps are untouched), the action sheet closes, and the list updates immediately.
4. **Given** the action sheet is open, **When** the user picks "Copy URL", **Then** the URL is placed in the system clipboard, the action sheet closes, and a brief confirmation message is shown to the user.
5. **Given** the action sheet is open, **When** the user taps outside it or presses system-back, **Then** the sheet dismisses without performing any action.

---

### User Story 4 - Clear all history with confirmation (Priority: P3)

A user who wants to wipe their entire browsing history (e.g., before lending the device to someone) taps a "Clear all" affordance in the History screen's top bar. A confirmation dialog warns that all history entries will be permanently deleted; on confirm, all entries are removed and the empty state is shown.

**Why this priority**: Privacy-conscious users expect a wipe-everything affordance, but this is less frequently exercised than per-entry deletion (US3) and is destructive enough to require an extra confirmation step. P3 because it polishes off privacy needs after the main browsing/search/per-entry-action flow is in place.

**Independent Test**: With multiple history entries present, tap "Clear all" in the top bar → a confirmation dialog appears. Cancel → list is unchanged. Re-tap "Clear all" → confirm → list becomes empty and the empty state placeholder is shown. Re-open History after navigating away and back → still empty.

**Acceptance Scenarios**:

1. **Given** the History screen has at least one entry, **When** the user taps the "Clear all" affordance, **Then** a confirmation dialog appears with localized title, body, a destructive "Clear" button, and a "Cancel" button.
2. **Given** the confirmation dialog is open, **When** the user taps "Cancel" or dismisses it via system-back, **Then** no history is deleted and the dialog closes.
3. **Given** the confirmation dialog is open, **When** the user confirms, **Then** every history row in the database is deleted, the list updates to the empty state, and the confirmation dialog closes.
4. **Given** the History screen has zero entries, **When** the user looks at the top bar, **Then** the "Clear all" affordance is hidden or disabled (no-op state should not be reachable).

---

### User Story 5 - Empty state when there is no history (Priority: P3)

A user who has just installed the app, or who has just cleared their history, opens the History screen and sees a friendly empty state explaining that visited pages will appear here, instead of a blank/confusing screen.

**Why this priority**: Polish-level requirement. A visible empty-state copy is standard good UX but the screen still functions without it. P3.

**Independent Test**: With a fresh database (no recorded visits), open History → an empty-state graphic + localized message is visible, no list rows shown, no "Clear all" affordance shown.

**Acceptance Scenarios**:

1. **Given** there are zero history entries (whether by fresh install or after Clear All), **When** the user opens the History screen, **Then** an empty-state composition with an icon plus a localized message is displayed in place of the list.
2. **Given** the empty state is visible, **When** any non-incognito page successfully loads, **Then** the empty state is replaced by the list with the new entry appearing immediately under "Today".

---

### Edge Cases

- **Visit succeeds but page has no `<title>`**: history entry is still recorded; the row falls back to displaying the URL or hostname in place of the title (the user must still be able to find it).
- **Same URL visited many times in a row**: each successful load produces a separate entry — the chronological-log invariant from Spec 005 is preserved (no de-duplication in v1.0).
- **A page redirects through several intermediate URLs**: only the final settled URL is recorded (matches the current Spec 011 tab-state lifecycle that already commits the final URL on `onPageFinished`).
- **Page load fails or is aborted**: no history entry is created for failed/cancelled loads.
- **System clock changes (timezone shift, DST, manual change) between visits**: day-grouping uses the device's current local time at view-time, so visits may appear under different day buckets after the change. This is acceptable; no migration is performed.
- **Search query contains characters that may have meaning in lookup syntax (e.g., `%`, `_`, `'`, `"`, `\`)**: the search MUST match them as literal characters and not interpret them as wildcards or operators.
- **Database is very large (≥ 10K entries) on a low-end device**: the screen must still open without ANR on a Pixel 5+ class device within reasonable bounds — see SC-005.
- **A history entry references a URL whose hostname can no longer be parsed**: the row still renders, falling back to the raw URL string in the hostname slot rather than crashing.
- **User taps "Open in new tab" while the tab cap is reached** (Spec 011 `MAX_TABS`, Spec 012 `MAX_INCOGNITO_TABS`): the existing cap-reached error event surfaces to the user (reuses the existing tab-management error channel — does not introduce a separate path).
- **Action sheet open when a new history entry arrives in the background**: the sheet stays open and bound to its row; the underlying list updates without dismissing the sheet.

## Requirements *(mandatory)*

### Functional Requirements

#### Auto-recording

- **FR-001**: The system MUST record a new history entry when a page in a non-incognito tab finishes loading successfully, capturing the final settled URL, the resolved page title (or empty string if unavailable), the visit timestamp (current device time in epoch milliseconds), and a reference to the page favicon when available.
- **FR-002**: The system MUST NOT record any history entry for page loads occurring in incognito tabs (per Spec 012 incognito-no-persistence rule).
- **FR-003**: The system MUST NOT record entries for page loads that fail, are cancelled, or never reach the loaded state.
- **FR-004**: The system MUST treat repeat visits to the same URL as separate entries (each successful visit creates a new row with its own timestamp).
- **FR-005**: The system MUST persist history entries durably across app restarts and process death (using the existing local database from Spec 005 — no cloud, no backup).

#### Listing & grouping

- **FR-006**: The system MUST expose a History screen reachable from the bottom-bar in the same affordance pattern used by the bookmarks bottom-bar entry (Spec 013). The screen MUST be reachable and fully functional regardless of whether the currently-active tab is normal or incognito (per-tab incognito model, Spec 012); no incognito-context lock-down or placeholder is applied.
- **FR-007**: The History screen MUST display recorded entries in reverse-chronological order (most recent visit first), with **one row per visit** — repeat visits to the same URL appear as multiple distinct rows, with no display-layer collapse, expand/collapse toggle, or "(N visits)" aggregation.
- **FR-008**: The History screen MUST visually group entries under day section headers labelled "Today", "Yesterday", and explicit localized date strings for older days, where the bucket boundary is the device's local-time day boundary (midnight).
- **FR-009**: Each history row MUST show: the page favicon when available (with a generic globe placeholder otherwise), the page title (with a fallback to URL or hostname when the title is empty), the page hostname, and the time-of-visit formatted as a localized short time string (e.g., "14:32" or "2:32 PM").
- **FR-010**: When the user taps a history row, the system MUST navigate the currently-active tab to that entry's URL and dismiss the History screen (mirrors the Spec 013 Q2 "replace active tab" decision).
- **FR-010a**: When the active tab is incognito and the user taps a history row, the system MUST still load the URL into that incognito tab (the URL navigation itself does not bypass the incognito policy — it is only the recording that is suppressed for incognito).

#### Search

- **FR-011**: The History screen MUST present a search input field at the top that filters the visible entries in real-time once the active query reaches the minimum-character threshold (FR-011a).
- **FR-011a**: Filtering MUST be skipped (and the full unfiltered list MUST be shown) when the search query length is 0 or 1 character. Filtering MUST kick in starting at 2 characters and re-run on every subsequent keystroke (no debounce).
- **FR-012**: The search filter MUST match a substring of either the page title OR the page URL, treating uppercase and lowercase as equivalent.
- **FR-013**: The search filter MUST treat all user-typed characters literally (no wildcard or operator interpretation).
- **FR-014**: While the active search query meets the threshold (FR-011a) and matches exist, day-section grouping MUST be preserved for matching entries; groups with zero matches MUST be hidden.
- **FR-015**: When the active search query meets the threshold (FR-011a) and zero entries match, the system MUST display a "no matches" message in place of the list.
- **FR-016**: Clearing the search input MUST restore the full grouped list immediately.
- **FR-017**: While a threshold-meeting search query is active, newly recorded history entries that match the active query MUST appear in the filtered results in real time without requiring the user to re-trigger search.

#### Per-entry actions

- **FR-018**: Long-pressing a history row MUST open an action sheet listing exactly three actions in this order: "Open in new tab", "Delete entry", "Copy URL".
- **FR-019**: "Open in new tab" MUST always create a new **non-incognito (normal)** tab loading the entry's URL, regardless of whether the currently-active tab is normal or incognito at the moment of invocation. The History screen is then dismissed and the new normal tab becomes the active tab.
- **FR-019a**: When the normal-tab cap (Spec 011 `MAX_TABS`) is already reached, the "Open in new tab" action MUST surface the existing cap-reached error message rather than silently failing. The incognito-tab cap (`MAX_INCOGNITO_TABS`) is NOT consulted for this action because new tabs from history rows are always normal.
- **FR-020**: "Delete entry" MUST remove only the targeted history row (other rows pointing to the same URL at different timestamps MUST be untouched), and the list MUST reflect the deletion immediately.
- **FR-021**: "Copy URL" MUST copy the entry's URL string to the system clipboard and show the user a transient snackbar confirmation message.
- **FR-022**: The action sheet MUST be dismissable by tapping outside it or pressing the system back gesture, in which case no action is performed.

#### Clear-all

- **FR-023**: The History screen MUST provide a "Clear all" affordance in the top bar when at least one entry exists; when zero entries exist, the affordance MUST be hidden or disabled.
- **FR-024**: Tapping "Clear all" MUST open a confirmation dialog with a localized title, body explaining the destructive nature, a destructive-styled "Clear" button, and a "Cancel" button.
- **FR-025**: Confirming "Clear" MUST delete all history entries and update the list to the empty state.
- **FR-026**: Cancelling the dialog (via Cancel button, tap-outside, or system back) MUST leave history unchanged.

#### Empty state

- **FR-027**: When the History screen contains zero entries (whether on first install or after Clear All), the system MUST display a localized empty-state composition (icon + message) in place of the list.

#### Localization & accessibility

- **FR-028**: All user-facing strings introduced by this feature MUST be localized in all 8 supported locales (EN, VI, DE, RU, KO, JA, ZH, FR).
- **FR-029**: Date and time strings rendered on the screen (day headers, time-of-visit) MUST follow the user's locale conventions for short date and short time formatting.
- **FR-030**: All interactive elements (rows, search box, action-sheet items, top-bar actions) MUST expose localized accessibility labels suitable for screen readers.

#### Architecture & policy

- **FR-031**: The feature MUST follow the project's Clean Architecture pattern: a History repository interface in the domain layer, a single repository implementation in the data layer, distinct domain use cases for the observable read paths and each mutation, a feature ViewModel exposing an immutable UiState, and a sealed event channel for transient one-shot signals (clipboard confirmations, error toasts).
- **FR-032**: The feature MUST not introduce any user-facing string, dimension, color, URL, key, or magic number outside the project's existing constants/theme/string-resource conventions (Constitution §III).
- **FR-033**: The feature SHOULD NOT introduce any new external library dependency; reuse what is already on the project's classpath.

### Key Entities *(include if feature involves data)*

- **History Entry**: A single record of a successfully-loaded page visit in a non-incognito tab. Attributes: a unique identifier, the final settled URL, the page title at load time (may be empty), a visit timestamp (epoch milliseconds), and an optional reference to the favicon resource. The same URL may appear in many entries differentiated by timestamp. Backed by the existing `HistoryEntryEntity` from Spec 005 (no schema change required for v1.0).
- **Day Group**: A presentation-only grouping of history entries that share the same local-day bucket. Attributes: a label ("Today", "Yesterday", or a localized explicit date) and the list of entries within that day. Has no persistent storage and is recomputed on demand from the entries list.
- **Search Query**: An ephemeral, screen-local string that the user types to filter the visible entries. Not persisted across screen visits.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After a fresh install, a user who visits 3 distinct URLs in a non-incognito tab can open the History screen and see all 3 entries listed under "Today" in correct reverse-chronological order, in fewer than 5 seconds total elapsed time from the third page finishing to the History screen being visible.
- **SC-002**: 100% of successful page loads in non-incognito tabs result in exactly one new history entry, and 0% of incognito page loads create any history entry (verified across at least 10 visits per case).
- **SC-003**: A user searching for any visited entry by typing 3 or more characters of its title or hostname locates that entry in the filtered results in under 1 second of typing-to-result-visible latency on a Pixel 5-class device.
- **SC-004**: From a History screen displaying ≥ 100 entries, the user can long-press any row, pick "Delete entry", and see the row disappear from the list within 500 ms.
- **SC-005**: The History screen opens without ANR or visible jank (frame budget ≤ 16 ms p99 during initial layout) on a Pixel 5-class device when the database contains 10,000 entries.
- **SC-006**: After "Clear all" is confirmed, every history row is removed and the empty state is visible within 1 second on the same 10,000-entry case.
- **SC-007**: 100% of user-facing strings on the screen are translated in all 8 supported locales — verified by build-time enforcement (no missing or extra translation keys).
- **SC-008**: APK release size delta vs the prior spec's baseline is within +200 KB. (Modest budget given mostly UI + i18n; matches the precedent set by Spec 008 +63 KB and Spec 010 -50 KB.)
- **SC-009**: Native library 16 KB page-size alignment remains green: every native library entry in the release APK is 16 KB-aligned (Constitution §IX).
- **SC-010**: Constitution Check returns 11/11 PASS both pre- and post-implementation.

## Assumptions

- **A1 — Recording trigger**: A "successful page load" is the same boundary the existing browser pipeline already commits as the tab's final URL/title at `onPageFinished` (Spec 011 lifecycle). No new boundary is introduced. Failed loads, in-flight cancellations, and intermediate redirects do not produce entries.
- **A2 — Title at record time**: The title captured is whatever the page has reported by the time `onPageFinished` resolves. If the page later updates its title via JavaScript, the history entry is NOT updated retroactively (matches the project's current "snapshot at load" tab-state behaviour). Acceptable per the Spec 011 precedent.
- **A3 — Day-bucket boundary**: "Today" / "Yesterday" / older are computed from the device's current local date at the moment the screen renders. No background re-bucketing service is needed because the screen recomputes when re-opened.
- **A4 — Search execution**: Filtering happens on the in-memory observed list (not via a parameterized SQL query) since the v1.0 envelope (Spec 005 power-user target ~100K history rows) is small enough to filter client-side in well under 16 ms per keystroke for realistic queries. If profiling later shows otherwise, adding a DAO-side query is a non-breaking change deferred to a future spec.
- **A5 — Database-side load**: All entries are loaded as a single observable stream. No paging in v1.0. The Spec 005 envelope is the upper bound (100K rows); past that, performance is out of scope until a settings-layer retention policy lands in Spec 016.
- **A6 — Tap-to-replace tab**: "Replace active tab" uses the same code path that Spec 013 Q2 already established for bookmark taps; if the active tab is incognito the navigation still happens in that incognito tab (FR-010a), and recording is still suppressed.
- **A7 — Delete is hard-delete**: There is no soft-delete / undo / trash bin in v1.0. "Delete entry" and "Clear all" both produce permanent removals. Privacy-aligned choice and consistent with the project's "no cloud / no backup" posture.
- **A8 — Clipboard confirmation surface**: The "Copy URL" confirmation reuses whatever transient one-shot UI surface the project already uses for similar Bookmark feedback events (Spec 013 introduced a sealed event channel; this spec adds a parallel channel without inventing a new pattern).
- **A9 — Open-in-new-tab + cap policy**: When the tab cap is reached, the existing cap-reached error event is surfaced (FR-019a). No new error type is introduced; this feature is a consumer of Spec 011's existing tab-creation policy.
- **A10 — Reuse existing schema**: The existing `HistoryEntryEntity` (id, url, title, visited_at, favicon_url?) shipped in Spec 005 is sufficient. No schema migration is required for v1.0; index on `visited_at` from Spec 005 backs the chronological query.
- **A11 — Zero new packages**: Material icons-core (already on classpath since Spec 007), Compose BOM, Room, kotlinx-coroutines/Flow, and the system clipboard service together cover all required UI and data needs. No new dependency is expected.

## Dependencies

- **Spec 005 — Room database schema**: `HistoryEntryEntity`, `HistoryDao`, `AppDatabase` already shipped. Adds whatever DAO methods Spec 014 needs (single-row delete, clear-all) without schema migration.
- **Spec 007 — WebView Compose Wrapper**: Provides the `onPageFinished` / final-URL / final-title signal that the recorder hooks into.
- **Spec 011 — Tabs management**: Provides the active-tab state, the tab-creation API used by "Open in new tab", and the existing tab-cap error surface (FR-019a).
- **Spec 012 — Private/Incognito mode**: Provides the per-tab `isIncognito` flag the recorder reads to suppress recording (FR-002).
- **Spec 013 — Bookmarks CRUD**: Establishes the bottom-bar entry pattern (FR-006), the tap-replace-active-tab decision (FR-010), and the sealed-event-channel UI feedback pattern (A8) that this spec mirrors.

## Out of Scope (v1.0)

- Pagination / lazy / windowed loading of history rows.
- Per-day-bucket clear (e.g., "Clear today only").
- Per-site grouping or per-site clear (e.g., "Forget everything from example.com").
- History retention / pruning policies (auto-delete after N days / N rows). Deferred to **Spec 016 settings-screen**.
- Full-text search across page body / cached content. Title + URL substring is the v1.0 contract.
- Browser-engine forward-history (per-tab back/forward stack) — that is a separate concept owned by Spec 008 / Spec 011.
- Sync / cloud / cross-device history — explicitly out of scope per project privacy posture.
- Undo / soft-delete for any history operation.
- Sharing a history entry via the Android share sheet (could be a polish add later).
- Surfacing history suggestions in the address bar / omnibox — that overlaps Spec 009 and is intentionally not folded in here.
