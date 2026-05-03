# Feature Specification: Tabs Management

**Feature Branch**: `011-tabs-management`
**Created**: 2026-05-03
**Status**: Draft
**Input**: User description: "Multi-tab browsing: user can open multiple WebView tabs in parallel, switch between them via a grid switcher with preview thumbnails, open new / duplicate / close tabs, and have non-incognito tabs persist across app restarts via Room TabEntity. Incognito tabs are out of scope (Spec 012)."

## Clarifications

### Session 2026-05-03

- Q: Tab thumbnail strategy — text-only cards versus actual WebView screenshot previews? → A: **Text-only cards** — each tab card displays page title + hostname + a generated placeholder (background color + first-letter glyph derived deterministically from the hostname). NO screenshot capture, NO on-disk thumbnail cache, NO new APIs. This matches the project's "minimalist Android browser inspired by DuckDuckGo Browser but simpler" DNA, aligns with the memory-vs-fidelity trade-off already chosen in FR-027 (inactive tabs reload on switch), and zero new disk usage / APK delta. Screenshot previews are explicitly NOT a v1.0 feature; if user feedback later demands them, they ship as a follow-up spec (Spec 011.1 / Spec 020).
- Q: How does the user open a new tab from the BrowserScreen without going through the tab switcher first? → A: **Long-press the 5th BottomAppBar button (the switcher button)** — single tap on that button opens the tab switcher (existing A5 behavior); long-press on the same button opens a new home tab directly and makes it active without showing the switcher. Chrome Android pattern. Zero extra BottomAppBar slots, zero new icons, no impact on Spec 008's 5-callback bundle (no new field added to `NavigationBottomBarCallbacks`; the long-press handler attaches to the same button). Inside the tab switcher itself, the explicit "new tab" affordance from FR-011 remains the primary discoverable path; the long-press is a shortcut for power users.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Open multiple tabs and switch between them via a grid switcher (Priority: P1)

A user opens the browser, lands on the default home page (Spec 008's `https://www.google.com/`), and starts reading. They want to keep that page open while looking up something else. They open a new tab, type a different URL or search query, and read it. They then return to the first tab without losing its state. To find tabs they have open, they invoke a grid switcher that shows every open tab as a card, and tap the one they want.

**Why this priority**: This is the headline capability of the spec. Without it, the browser remains single-tab and behaves like Specs 007–010. P1 because every user-visible scenario downstream (US2 persistence, US3 close, Spec 012 incognito, Spec 013/014 bookmarks-from-tab, etc.) depends on the multi-tab model existing. This story alone (without persistence) already delivers shippable value because users can keep multiple pages open within a single session.

**Independent Test**: From a fresh launch with one home tab visible, invoke the new-tab affordance, observe a second tab opens with the home page, navigate that tab to a different URL, invoke the tab switcher, observe two cards (one per open tab) with their distinct titles and hostnames, tap the first card, observe the original page restored as active, and verify the address bar (Spec 009) shows the original URL again.

**Acceptance Scenarios**:

1. **Given** the app is open with exactly one tab on the home page, **When** the user invokes the new-tab affordance, **Then** a second tab is created on the home page, becomes the active tab, and the address bar reflects the home page URL.
2. **Given** the user has two open tabs displaying different URLs, **When** the user invokes the tab switcher, **Then** the switcher displays one card per open tab with each tab's title and hostname, in a stable order (most-recently-active first or by creation order — see FR-014).
3. **Given** the tab switcher is open showing N cards, **When** the user taps a non-active card, **Then** the switcher closes, that tab becomes the active tab, and its WebView content is restored (or reloaded from its URL — see FR-027) without losing the user's reading position when memory permits.
4. **Given** the user is on tab A and switches to tab B, **When** the user invokes the system back gesture or the back button (Spec 008), **Then** the back action operates within tab B's own navigation history, NOT across tabs (tab switching is not part of WebView history).
5. **Given** the user has reached the maximum allowed tab count (FR-016), **When** the user invokes the new-tab affordance, **Then** the system blocks the action and surfaces a clear, localized message indicating the limit has been reached.

---

### User Story 2 — Tabs persist across app kill / restart (Priority: P1)

A user has multiple tabs open with different content. They put the phone away, the OS later kills the app process for memory, or they explicitly swipe the app away from Recents. When they relaunch the app, they expect to find the same tabs still open in the same order, with the previously active tab restored as the active one. URLs are reloaded; per-tab in-memory state (form input, scroll position) is best-effort and not guaranteed.

**Why this priority**: This is the second half of the value proposition — without persistence, multi-tab provides no benefit beyond a single session. P1 because Specs 005's `TabEntity` was introduced specifically for this purpose; Spec 011 is the first consumer that proves the schema. Without US2, the persistence column in Room sits unused and the user never gets the "I never lose my work" guarantee that justifies multi-tab over single-tab.

**Independent Test**: Open the app, manually create three tabs with three different URLs, navigate the second tab to be the active one, force-stop the app from system Settings → Apps, relaunch the app, and verify all three tabs are present in the same order, the second tab is active, and the address bar (Spec 009) shows the second tab's URL.

**Acceptance Scenarios**:

1. **Given** the user has 3 open tabs with the second tab active, **When** the OS terminates the app process and the user relaunches the app, **Then** all 3 tabs are restored in the same order and the second tab is the active one.
2. **Given** the user has 0 persisted tabs (first launch ever, or after a "close all" — see US3), **When** the user opens the app, **Then** the system auto-creates exactly one tab pointing to the default home URL and presents it as the active tab.
3. **Given** the user has 5 tabs open, **When** the user opens a new tab and immediately force-stops the app, **Then** on relaunch all 6 tabs are present (the just-opened one is persisted, not lost in a write race).
4. **Given** the persisted tab list contains a URL that the WebView fails to load on restoration, **When** that tab is activated, **Then** the existing Spec 007 error UI is displayed for that tab and the rest of the tab list remains intact.
5. **Given** the user has tabs persisted from a prior session, **When** the app cold-starts, **Then** restoration completes within a budget that does not visually freeze the launch (see SC-010); the active tab's WebView begins loading its URL while the switcher metadata is hydrated in the background.

---

### User Story 3 — Close individual tabs and "close all tabs" (Priority: P2)

A user no longer needs a particular tab. From the tab switcher, they tap a close (×) affordance on the tab's card to remove it. They can also close every tab at once via a switcher-level action. Closing the last tab does NOT leave the user on an empty browser; instead, the system auto-creates a fresh home tab so there is always at least one tab open.

**Why this priority**: Memory and UX hygiene. Without close, users accumulate tabs indefinitely and the WebView per-tab cost (even with the inactive-tab strategy in FR-027) eventually pressures device memory. P2 because users CAN reach the multi-tab MVP without close (US1 + US2) — they just can't tidy up. The close affordance is a fast follow-up rather than a blocker for shipping the MVP.

**Independent Test**: Open three tabs. From the switcher, tap × on the middle tab; verify the switcher now shows two cards and the active tab pointer either remains on the previously active tab (if it survived) or falls back to the most-recently-active surviving tab. Then tap a "close all tabs" action; verify the switcher closes, exactly one fresh home tab exists, and it is the active one.

**Acceptance Scenarios**:

1. **Given** the user has 3 tabs open in the switcher, **When** the user taps × on a non-active tab card, **Then** that card disappears from the switcher and the persisted tab list shrinks by one entry; the active tab remains unchanged.
2. **Given** the user has 3 tabs and the active one is the second, **When** the user taps × on the active tab from the switcher, **Then** that tab is removed and the next-most-recently-active tab becomes the new active tab; if the user re-opens the switcher they see the remaining two cards.
3. **Given** the user has only 1 tab open, **When** the user taps × on that tab, **Then** the system auto-creates a fresh home tab to take its place (the rule "always ≥ 1 tab" holds), and the address bar reflects the new home tab's URL.
4. **Given** the user has N tabs open, **When** the user invokes "close all tabs" from the switcher, **Then** all N tabs are removed from the persisted list, exactly one fresh home tab is auto-created, the switcher closes, and the user is returned to the BrowserScreen showing that fresh tab.
5. **Given** the user closes a tab and then immediately force-stops the app, **When** the user relaunches, **Then** the closed tab does NOT reappear (the close write was durable before the kill — see FR-024).

---

### Edge Cases

- **Empty persisted state**: First launch ever or post "close all" → exactly one tab auto-created with the default home URL (FR-019). Never present an empty BrowserScreen.
- **Maximum tab count reached**: The user attempts a new tab while `BrowserLimits.MAX_TABS` is already met. New-tab affordance is blocked and a localized message is surfaced (FR-016). Existing tabs are unaffected.
- **Active tab killed via close**: Falls back to the most-recently-active surviving tab; if none survive, falls back to the auto-created home tab rule (US3 #3).
- **Restoration with a malformed URL**: A tab persisted with an invalid or unreachable URL → that tab still appears in the switcher; on activation, Spec 007's error UI displays inside that tab. The error does not break restoration of other tabs.
- **Restoration speed budget**: Cold-start tab restoration must not visibly freeze the launch (SC-010). The active tab's WebView begins loading immediately; switcher metadata for inactive tabs is hydrated lazily.
- **Memory pressure with many tabs**: Inactive tabs do NOT keep a live WebView in memory by default (FR-027). When a user switches to an inactive tab, the WebView is recreated from the persisted URL and re-loads the page. This is a deliberate trade-off favoring memory hygiene over scroll-position preservation across tab switches.
- **Cookies / WebView storage across tabs**: Non-incognito tabs share a single cookie jar and storage (Android WebView default per Spec 007). Logging into a site in tab A means tab B sees the same login. Incognito tabs are Spec 012's job.
- **Predictive back from the tab switcher** (Android 14+): The system back gesture invoked while the switcher is open closes the switcher first (returns to the active tab), not the active WebView. Spec 008's `PredictiveBackHandler` is reused.
- **Rotation while in the tab switcher**: The switcher remains open after rotation. The visible card grid recomposes correctly; tab list state survives configuration change because it is sourced from Room (Flow-backed) plus a `rememberSaveable` for the switcher-open boolean.
- **Concurrent writes during multi-tab activity** (rapid open + close): Write order matches user action order. The persisted list always converges to the user's intended state; the SQLite WAL mode (Spec 005) and the `TabDao`'s `suspend` write API serialize per-coroutine.
- **Tab title not yet known**: A freshly created tab loading the home URL has its title fall back to a localized "New tab" / hostname placeholder until the WebView reports the real `title` via `WebViewClient.onReceivedTitle`. The card never displays an empty title.
- **"Open link in new tab" from a long-press / context menu**: OUT OF SCOPE for this spec (FR-033). Spec 011 only adds the new-tab affordance from the tab switcher and the BrowserScreen toolbar; per-link "open in new tab" is a future enhancement.

## Requirements *(mandatory)*

### Functional Requirements

#### Multi-tab model

- **FR-001**: System MUST support exactly one active tab at any given time. The active tab is the one whose WebView occupies the BrowserScreen content area and whose URL/title backs the address bar (Spec 009) and navigation controls (Spec 008).
- **FR-002**: System MUST allow the user to create a new tab from exactly two entry points: (a) the explicit "new tab" affordance inside the tab switcher (FR-011); (b) a long-press gesture on the 5th BottomAppBar button (the switcher button — see A5). The long-press MUST open a fresh home tab and make it active immediately without first showing the switcher (Chrome Android pattern; locked via 2026-05-03 clarification Q2). Single-tap on the same button MUST continue to open the tab switcher per A5.
- **FR-003**: A newly created tab MUST start at the default home URL (`UrlConstants.DEFAULT_HOME_URL`, currently `https://www.google.com/` per Spec 008). This spec does NOT introduce a "new tab page" feature; the home URL is the new-tab landing.
- **FR-004**: System MUST track each tab's identity as the union of: stable identifier, current URL, current page title, position in the user's tab order, creation timestamp, and last-active timestamp. These fields MUST round-trip through the `TabEntity` schema introduced in Spec 005 with NO schema migration (Spec 005's `TabEntity` columns are the exact source of truth for this spec — see Key Entities).
- **FR-005**: When the user is on tab A and switches to tab B, the system MUST update tab A's last-active timestamp BEFORE switching, so that tab A remains the most-recently-active among all non-currently-active tabs.
- **FR-006**: When the active tab navigates internally (Spec 008 Back / Forward / new URL load via Spec 009 address bar), the navigation MUST stay within that tab's WebView history; it MUST NOT cause a tab switch or affect any other tab's URL or history.

#### Tab switcher UI

- **FR-007**: System MUST provide a tab switcher screen (or overlay) that lists every currently-open tab as an individually-tappable card.
- **FR-008**: Each tab card in the switcher MUST display, at minimum: the page title, the hostname extracted from the URL, and a close (×) affordance. Visual / tactile distinction between the active tab and inactive tabs MUST be clear.
- **FR-009**: Each tab card MUST display a visual preview area as a deterministic text-only placeholder: a solid background color + the first letter of the URL hostname rendered in a contrasting foreground color. The background color and first letter MUST be derived deterministically from the hostname so the same site always renders the same placeholder across sessions. NO WebView screenshot capture is performed in v1.0 (locked via 2026-05-03 clarification Q1).
- **FR-010**: The switcher MUST be invokable from the BrowserScreen via the entry point assumed in A5 (a 5th button in Spec 008's `BottomAppBar`, rightmost, displaying the current tab count as a numeric badge).
- **FR-011**: The switcher MUST display a "new tab" affordance — visually distinct from the existing-tab cards — that creates a fresh home tab and immediately switches to it (closing the switcher).
- **FR-012**: The switcher MUST display a "close all tabs" affordance that triggers the US3 #4 behavior. The action MUST surface a localized confirmation prompt before destruction (irreversible action; Constitution-aligned UX).
- **FR-013**: Tapping a non-active card in the switcher MUST: (a) close the switcher; (b) make the tapped tab active; (c) update the tapped tab's last-active timestamp; (d) display its WebView content (or trigger reload per FR-027 if the WebView was discarded).
- **FR-014**: Cards in the switcher MUST be ordered by descending last-active timestamp (most-recently-active first), so the previously active tab appears at the top of the grid for fast switching back. This is independent of the persisted `position` column, which is reserved for a future drag-to-reorder enhancement (out of scope per FR-034).
- **FR-015**: The switcher MUST surface a real-time count of open tabs visible to the user as the `TopAppBar` title (e.g., "5 tabs" in EN, localized via Android plural string resources `tabs_switcher_title_count` per locale's CLDR plural rules). This count MUST match what the BrowserScreen entry-point badge (`BadgedBox` over the 5th BottomAppBar button) shows — both derive from the same `observeTabs()` flow size.

#### Limits

- **FR-016**: System MUST enforce a maximum tab count via a new constant `BrowserLimits.MAX_TABS`. Default value is 50 (mid-range Android browser industry norm); the constant lives in `core/constants/BrowserLimits.kt` per Constitution §III. When the limit is reached, the new-tab affordances (both the BrowserScreen entry and the switcher entry) MUST be visually disabled AND a localized message surfaced when tapped.
- **FR-017**: The minimum tab count MUST be exactly 1 — closing the last tab triggers the auto-create-fresh-home-tab rule (US3 #3 / FR-019). The user CANNOT reach a 0-tab state through the UI.

#### Persistence

- **FR-018**: Every non-incognito tab CRUD operation (create, switch, navigate-and-update-title, close) MUST write through `TabDao` (Spec 005) so the on-disk state always matches the user's intent. No tab state is held only in memory beyond the session-active fields (current scroll, current WebView instance for the active tab).
- **FR-019**: On every cold start, the system MUST hydrate the tab list from Room. If the persisted list is empty (zero rows in `tabs` table), the system MUST auto-create exactly one tab pointing to the default home URL and persist it before presenting the BrowserScreen.
- **FR-020**: On cold start, the active tab MUST be selected as the row with the maximum last-active timestamp (i.e., the tab the user was on when the app was last backgrounded / killed). This avoids introducing an `is_active` column and any associated Room migration.
- **FR-021**: Tab title and last-active timestamp updates triggered by WebView callbacks (e.g., `WebViewClient.onReceivedTitle`, address-bar URL load) MUST be persisted asynchronously without blocking the UI thread.
- **FR-022**: URL changes within a tab (user navigates the WebView) MUST update the tab's persisted URL field so that on the next cold start, restoration loads the URL the tab was on at last interaction — NOT the URL the tab was created with.
- **FR-023**: Tab creation order MUST be reflected in the persisted `position` field so that future enhancements (drag-to-reorder, tab groups) have a stable schema basis. v1.0 of Spec 011 uses `position` only as a tiebreaker, not as a primary ordering signal (see FR-014).
- **FR-024**: A close operation MUST be durable — once the close UI returns control to the user, the row MUST be deleted from `TabDao` (or marked deleted) such that an immediate force-stop + relaunch does NOT bring the tab back. No in-memory-only deletes.
- **FR-025**: Concurrent close + switch + create operations MUST converge to the user's serialized action order; the SQLite WAL mode (Spec 005) is the underlying guarantee.

#### Lifecycle

- **FR-026**: The active tab's WebView MUST follow Spec 007's lifecycle contract (`DisposableEffect` cleanup, `LifecycleEventObserver` for ON_PAUSE / ON_RESUME, `loadUrl("about:blank") + removeAllViews() + destroy()` on disposal).
- **FR-027**: Inactive tabs MUST NOT retain a live `WebView` instance in memory by default. When a user switches to an inactive tab, the system MUST instantiate a fresh `WebView` for that tab and load its persisted URL. This is a deliberate trade-off favoring memory hygiene; per-tab scroll/form state across switches is NOT preserved in v1.0.
- **FR-028**: All WebView lockdown settings (file access disabled, no `addJavascriptInterface`, `MIXED_CONTENT_NEVER_ALLOW`, permissions denied silently) from Spec 007 MUST apply identically to every tab's WebView.
- **FR-029**: Cookies and WebView storage are SHARED across all non-incognito tabs (Android WebView default behavior). This spec does NOT introduce per-tab storage isolation; that is Spec 012's responsibility.
- **FR-030**: When the app process is backgrounded (ON_PAUSE for the BrowserScreen), the active tab's WebView state (URL + title) MUST already be durable in Room. Spec 011's incremental write-through strategy satisfies this by design: `BrowserViewModel.onUrlChanged` writes the URL synchronously on every navigation event, and `onTitleReceived` (M1 plumbing) writes the title on every `WebChromeClient.onReceivedTitle` callback. Because both write-throughs fire BEFORE the user can background the app (they run on the foreground UI thread during interaction), there is no separate ON_PAUSE-tied persistence step required. This is the durability guarantee behind FR-022.

#### Out of scope (explicitly deferred)

- **FR-031**: Incognito tabs are OUT OF SCOPE — Spec 012's responsibility. The `TabEntity` schema (Spec 005) intentionally has no `is_incognito` column; incognito tabs will be in-memory-only when Spec 012 ships.
- **FR-032**: Tab thumbnails as actual WebView screenshots are OUT OF SCOPE for v1.0 (locked via 2026-05-03 clarification Q1 → text-only). Screenshot capture, on-disk thumbnail cache, invalidation on URL change, and eviction under memory pressure are all deferred. If user feedback later justifies the addition, they ship as a follow-up spec (Spec 011.1 / Spec 020) without changing the v1.0 schema or APIs.
- **FR-033**: "Open link in new tab" via long-press / context menu on a hyperlink within a WebView is OUT OF SCOPE; Spec 011 only adds new-tab affordances from the BrowserScreen toolbar and the tab switcher. Long-press context menus are a future enhancement.
- **FR-034**: Drag-to-reorder tabs in the switcher is OUT OF SCOPE; the persisted `position` column is reserved for it but the v1.0 ordering is by last-active timestamp (FR-014).
- **FR-035**: Tab groups, tab pinning, tab muting, and tab search are OUT OF SCOPE; not part of v1.0 roadmap.
- **FR-036**: Multi-window / split-screen tab support is OUT OF SCOPE; standard Android single-window behavior applies.
- **FR-037**: A "recently closed tabs" / undo-close affordance is OUT OF SCOPE; closes are immediately durable per FR-024 with no recovery path in v1.0.

### Key Entities *(include if feature involves data)*

This spec is the FIRST consumer of the `tabs` table introduced (but unused) in Spec 005's Room schema. No schema migration is performed; the existing v1 schema is sufficient.

- **Tab (extends `TabEntity` from Spec 005)**: Represents a single open browser tab. Attributes: stable id (auto-incrementing), current URL, current page title, position (insertion order; reserved for future reorder UX), creation timestamp (epoch millis), last-active timestamp (epoch millis — also the source of "which tab is active on cold start"). NO `is_incognito` column (Spec 012's tabs are in-memory-only).
- **Active-tab pointer**: Derived, NOT a separate persisted column. Computed at cold start as `MAX(last_active_at)` over all rows in the `tabs` table; mutated in-session when the user switches tabs (writes the about-to-leave tab's `last_active_at = now()`).
- **Tab list view-model state**: A `Flow<List<Tab>>` sourced from `TabDao.observeAll()` (Spec 005), mapped through a domain-layer `TabRepository` (new in this spec) into a `TabsUiState`. The active tab's WebView and per-tab in-memory state (current scroll, in-flight load) are session-scoped and not persisted.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can open at least 5 tabs simultaneously and switch between them via the grid switcher, with each tab maintaining its own URL and address-bar state. Verifiable by manual user-device test (5 tabs × distinct URLs × switch round-robin).
- **SC-002**: After the user has 3 tabs open and the second is active, force-stopping the app and relaunching restores all 3 tabs in the same order with the second tab active. Verifiable by manual user-device test + an instrumented integration test that simulates process death via `InstrumentationRegistry`.
- **SC-003**: Closing a tab from the switcher removes it from the persisted store within 100 ms (durable before user can re-tap), and re-opening the app does not bring it back. Verifiable by an instrumented test that closes a tab, asserts the `TabDao` row count, and force-stops + relaunches.
- **SC-004**: When the user has reached `BrowserLimits.MAX_TABS = 50` open tabs, the new-tab affordance is visually disabled AND attempting to invoke it surfaces a localized message in all 8 supported locales. Verifiable by instrumented UI test + 8-locale visual sweep manual gate.
- **SC-005**: The tab switcher renders the visible card grid for up to 50 tabs in under 200 ms on a Pixel 5+-class device. Verifiable by an instrumented benchmark or manual user-device test.
- **SC-006**: All 162 existing unit tests from Specs 005–010 MUST continue to pass after this spec ships. Plus at least 25 new unit tests covering: `TabRepository` CRUD (5), active-tab pointer derivation from `MAX(last_active_at)` (3), auto-create-home-tab on empty hydration (2), close-active-tab fallback (2), max-tab-count enforcement (2), `TabsViewModel` state mutators (8), URL/title persistence on WebView callbacks (3).
- **SC-007**: APK release size delta vs the Spec 010 baseline (2.0 MB) is at most +200 KB. Realistic budget — multiple new Composables (switcher screen + tab card + new-tab + close-all dialogs), new ViewModel + Repository + UseCases, ~6 new string keys × 8 locales = ~48 translations, possibly 1–2 new Material icons. No new packages expected.
- **SC-008**: 16 KB CI alignment gate continues to pass: every native library entry in the release APK aligns to `0x4000` or larger. No new `.so` files introduced.
- **SC-009**: Static analysis stays green — `lintDebug` zero warnings, `detekt` baseline UNCHANGED from Spec 010, `ktlintCheck` zero violations.
- **SC-010**: Cold-start tab restoration completes within 500 ms on a Pixel 5+-class device for up to 50 persisted tabs (active tab WebView begins loading immediately; inactive metadata hydrated lazily). The launch never visually freezes due to restoration. Verifiable by manual user-device test (force-stop with 50 tabs × launch × measure perceived responsiveness).
- **SC-011**: Constitution Check 11/11 PASS pre and post implementation.
- **SC-012**: Per-tab WebView lockdown (Spec 007 settings) verified identical for the home tab, a created tab, and a restored-from-disk tab. Verifiable by an instrumented test that asserts the WebView settings on each.

## Assumptions

- **A1**: `TabEntity` and `TabDao` from Spec 005 are used as-is, NO schema migration is performed. The existing columns (`id`, `url`, `title`, `position`, `created_at`, `last_active_at`) are exactly sufficient for v1.0 of Spec 011. The intentional absence of `is_incognito` is honored per Spec 005's design: incognito = in-memory only, deferred to Spec 012.
- **A2**: The active-tab pointer is derived as `MAX(last_active_at)` at cold start and mutated in-session by writing the about-to-leave tab's `last_active_at = System.currentTimeMillis()` immediately before the switch. NO new boolean column; NO Room migration. This was explicitly considered and rejected as scope creep when the alternative (adding `is_active` Boolean) would force a v1 → v2 schema migration that Spec 005 took care to avoid.
- **A3**: New tabs load `UrlConstants.DEFAULT_HOME_URL` (currently `https://www.google.com/` per Spec 008). This spec does NOT introduce a "new tab page" feature; the home URL is the landing.
- **A4**: Cookies and WebView storage are shared across all non-incognito tabs by Android's default WebView behavior (Spec 007). No isolation work in this spec; Spec 012 will introduce per-context isolation for incognito.
- **A5**: The tab switcher is invoked from the BrowserScreen via a 5th icon button added to Spec 008's `BottomAppBar`, placed to the right of the existing 4 buttons (Back / Forward / Reload-Stop / Home), displaying the current tab count as a numeric badge overlay. This is consistent with Spec 008's bottom-bar pattern; alternative placements (overflow menu, address-bar tab counter) were considered and rejected to keep the affordance discoverable on first launch. The same button ALSO accepts a long-press gesture as the new-tab shortcut (FR-002 / clarification Q2 2026-05-03) — long-press opens a fresh home tab without first showing the switcher. Both gestures attach to the same Composable via `Modifier.combinedClickable` (single-tap + long-press accepted on the same target); `NavigationBottomBarCallbacks` gains TWO new lambda fields — `onTabsSwitcherClick` (single-tap → navigate to switcher) and `onTabsSwitcherLongClick` (long-press → create + activate fresh home tab). Both callbacks are hoisted to the caller for testability per Spec 008's hoist precedent. The bundle becomes 6 fields — exactly at Spec 008's documented `LongParameterList.functionThreshold = 6` (PASSES; rule fails on >6). If a future review tightens the threshold, callbacks will need re-bundling per Spec 008's nested-data-class precedent.
- **A6**: Inactive tabs do NOT keep a live WebView in memory (FR-027). Tab switch from inactive → active recreates the WebView and reloads from URL. Per-tab scroll position / form input across switches is NOT preserved in v1.0; this is an explicit memory-vs-fidelity trade-off. A future enhancement may introduce a small LRU cache of recent WebViews if user feedback demands it.
- **A7**: Empty persisted state on cold start triggers auto-creation of exactly one tab on the default home URL (FR-019). This is industry-standard browser behavior and avoids ever presenting an empty BrowserScreen.
- **A8**: `BrowserLimits.MAX_TABS = 50` — a new constant introduced in this spec under `core/constants/BrowserLimits.kt`. 50 is mid-range for Android browsers (Chrome allows ~100, DuckDuckGo no hard cap, Firefox no hard cap; we cap at 50 to keep memory predictable on min-spec devices).
- **A9**: Predictive back from the tab switcher (Android 14+) closes the switcher first; from BrowserScreen the existing Spec 008 `PredictiveBackHandler` for in-tab back is unchanged. The switcher MUST register its own back handler when open and unregister when closed.
- **A10**: Rotation preserves: tab list (sourced from Room Flow, recomposes correctly), active tab (recomputed from Room state), switcher-open boolean (`rememberSaveable`). The active tab's WebView is reloaded on rotation per Spec 007's existing behavior; full DOM/scroll preservation is Spec 011 scope only insofar as Spec 007 already guarantees it for the active tab.
- **A11**: A new `TabsViewModel` + `TabRepository` interface + `TabRepositoryImpl` + 4–6 use cases (`ObserveTabsUseCase`, `CreateTabUseCase`, `SwitchTabUseCase`, `CloseTabUseCase`, `CloseAllTabsUseCase`, `UpdateTabUrlAndTitleUseCase`) form the new domain + data slices. New `TabsScreen` Composable + `TabSwitcherCard` component. `BrowserScreen` (Spec 007) and `BrowserViewModel` (Spec 007/008/009/010) are extended (NOT rewritten) to source the active tab's URL from `TabsViewModel` rather than holding it as their own state.
- **A12**: No new permissions, no new manifest entries. No new packages expected (Compose BOM 2026.04.01 already supplies `LazyVerticalGrid` / `LazyVerticalStaggeredGrid` for the switcher; Material3 `BadgedBox` for the tab-count badge; `material-icons-core` from Spec 007 has Tabs / Add / Close icons).
- **A13**: Test coverage approach mirrors Specs 005/006/010: pure-JVM unit tests using a fake `TabRepository` for ViewModel tests + Robolectric (already wired in Spec 005) for any DAO-touching test. Instrumented tests follow Spec 007/008's `HiltTestActivity` + `@UninstallModules` pattern for end-to-end flow verification.
- **A14**: At least one of US1's manual tests, US2's process-death test, US4's max-tab manual test, and SC-010's 50-tab cold-start test will need a manual user-device gate (mirrors Spec 008 T032/T049 and Spec 010 T017/T023/T026 deferred-then-verified pattern).
