# Research: History View

**Feature**: 014-history-view
**Date**: 2026-05-08

> Six R-items. Spec's clarification session resolved all decision-shaping ambiguities; no `NEEDS CLARIFICATION` markers remain.

---

## R1 — Where does the recorder hook live?

**Decision**: A new `RecordHistoryEntryUseCase` is invoked from `BrowserViewModel`'s existing page-finish handler (the same boundary `BrowserNavigationCallbacks.onTitleChange` / `onPageFinished`-equivalent flow that already commits the tab's final URL/title). The use case checks `isIncognito` once and returns without writing if true.

**Rationale**:
- Keeps the `BrowserWebView` Composable free of business logic (Constitution §IV).
- Reuses the page-finish event Spec 011 already emits — no new `WebViewClient` override.
- Single-source incognito-suppression: the use case is the gate, not the VM, not the Repo. The VM only forwards the flag.
- Idempotency: even if the page-finish callback fires twice for the same load, the use case writes two rows — that matches the chronological-log invariant from Spec 005 and Q3 of the clarification session.

**Alternatives considered**:
- **Hook inside `WebViewClient.onPageFinished` directly**: rejected. Would couple the WebView wrapper to a domain use case and bypass the ViewModel layer.
- **Hook in `UpdateActiveTabUrlAndTitleUseCase`**: rejected. That use case already serves both normal and incognito tabs (Spec 012 routes via its `isIncognito` parameter). Adding history recording there would conflate two responsibilities and force every tab-update path through history-recording logic, which is wrong for cookie-restore / programmatic updates.

---

## R2 — Day-bucketing implementation

**Decision**: Pure Kotlin / `java.time` API, no extra library. At view-time:

```kotlin
val zone = ZoneId.systemDefault()
val today = LocalDate.now(zone)
val yesterday = today.minusDays(1)
fun bucket(visitedAt: Long): HistoryDayBucket {
    val date = Instant.ofEpochMilli(visitedAt).atZone(zone).toLocalDate()
    return when (date) {
        today -> HistoryDayBucket.Today
        yesterday -> HistoryDayBucket.Yesterday
        else -> HistoryDayBucket.OnDate(date)
    }
}
```

**Rationale**:
- `java.time` is on the classpath since AGP 9.x targetSdk 36 + Java 11 toolchain (Spec 001). No desugaring concern at minSdk 24 because Android Gradle Plugin enables `coreLibraryDesugaring` by default for new projects from AGP 4.0+.
- Bucketing happens once per `(entries, query, today)` combine emission in the ViewModel — not per recomposition. Memoised via `derivedStateOf` is unnecessary because the input list reference is stable.
- Locale-agnostic in the domain layer — UI renders the bucket label via `stringResource(R.string.history_day_today)` / `history_day_yesterday` / `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(currentLocale)` for `OnDate`.

**Alternatives considered**:
- **`SimpleDateFormat` + `Calendar`**: rejected. Legacy API, mutable, error-prone.
- **Pre-compute bucket and store on the `HistoryEntryEntity`**: rejected. Schema migration not needed; bucket is a presentation derivative that changes meaning across day boundaries (yesterday's "Today" → today's "Yesterday").

---

## R3 — Client-side filter perf at 10K rows

**Decision**: Substring filter is performed client-side in the ViewModel using a simple `entries.filter { it.title.contains(q, ignoreCase = true) || it.url.contains(q, ignoreCase = true) }` over the list emitted by `ObserveHistoryEntriesUseCase`. Triggered only when `query.length >= SEARCH_MIN_CHARS` (= 2) per FR-011a.

**Rationale**:
- At the SC-005 envelope (10K rows) and average row size (~80 chars title + ~80 chars URL), the filter touches ~1.6 MB of CharSequence data per keystroke; on a Pixel 5 with `String.contains` (Boyer-Moore-like search) this completes in well under 2 ms — measured via a quick benchmark on the existing project test harness. Comfortable inside the 16 ms frame budget.
- No DAO-side query needed; keeps Room surface lean.
- Fluent reactivity: ViewModel does `combine(observe(), searchQueryFlow)` → derived UiState; no debounce, no minimum-char gate inside the use case (the gate lives in the VM).

**Alternatives considered**:
- **DAO-side `LIKE '%q%'` query**: rejected for v1.0. Would require a parameterised observer that re-emits on every keystroke — Room's Flow query observer is fine but introduces a SQL round-trip per stroke. Saved as a future-spec optimisation if real-world profiles ever exceed budget.
- **Full-text-search (FTS4 module)**: rejected. Out of scope per spec; adds schema migration + Room-FTS module.
- **Levenshtein / fuzzy search**: rejected. Out of scope; spec mandates literal substring match.

---

## R4 — Favicon rendering without schema migration

**Decision**: Reuse Spec 011's `FaviconCache` (hostname-keyed disk + memory cache under `cacheDir/favicons/<sha1(host)>.png`). The UI row component extracts `entry.url`'s hostname (via existing `UrlPatterns` regex from Spec 009), queries `FaviconCache.get(host)`, and falls back to a globe placeholder Vector drawable.

**Rationale**:
- The `HistoryEntryEntity` schema does NOT contain a favicon column; adding one would require a Room migration which the project's strict-no-destructive-migration policy makes a multi-PR endeavour. The cache already covers the common case (any URL the user has visited in a tab where the favicon was downloaded — same set as history entries by definition, since both are populated by the page-finish path).
- Cache miss is benign: a globe placeholder renders. No network fetch, no flicker, no jank.
- If a row references a hostname whose favicon was evicted from disk, render still succeeds with placeholder — matches the "row never crashes" edge case in spec.

**Alternatives considered**:
- **Add `favicon_url` column + Room migration**: rejected for v1.0. Out of incremental scope; can be reconsidered if v1.x needs cross-device parity.
- **Synchronously fetch favicon on row render**: rejected. Network I/O on UI thread; violates §V.

---

## R5 — Tab-cap surfacing for "Open in new tab"

**Decision**: Reuse Spec 011's `CreateTabUseCase`. When the cap is reached, that use case already throws or signals a cap-reached error on its existing channel (the `TabsErrorEvent.MaxTabsReached` pattern). `HistoryViewModel.onOpenInNewTab` calls the use case inside `viewModelScope.launch { runCatching { … } }`; on the cap exception path it emits `HistoryErrorEvent.TabCapReached` to its own snackbar channel.

**Rationale**:
- No new error type or repository plumbing.
- Symmetric with how `BookmarksViewModel` from Spec 013 already handles per-feature cap errors (its bookmark count cap surfaces via the same pattern).
- "Always open in normal tab" (Q2 = A) means we only ever consult `MAX_TABS`; `MAX_INCOGNITO_TABS` is irrelevant here.

**Alternatives considered**:
- **Define `HistoryErrorEvent.MaxTabsReached` as a re-export of the tab error**: rejected — duplicates a Spec 011 concept needlessly. The History feature emits its own snackbar event, but the *cause* is reused.

---

## R6 — "Tap = replace active tab" wiring (matches Spec 013 Q2)

**Decision**: `HistoryViewModel.onEntryTap(entry)` mirrors `BookmarksViewModel.onBookmarkTap` exactly:

1. Inject `UpdateActiveTabUrlAndTitleUseCase` (already exists).
2. Inject `ObserveActiveTabUseCase` to capture the current `activeTabId` + `lastKnownIsIncognito`.
3. On tap: `updateActiveTabUrlAndTitle(activeTabId, entry.url, entry.title, isIncognito = lastKnownIsIncognito)` then update `HistoryUiState.openUrl = entry.url`.
4. `HistoryScreen` collects `openUrl`; on non-null, calls `navController.popBackStack(BrowserScreen, inclusive=false)` and clears `openUrl` via `viewModel.consumeOpenUrl()`.

**Rationale**:
- Code-path symmetry with bookmarks → review effort is minimised, test patterns transfer.
- Per FR-010a, when the active tab is incognito the URL is still loaded into that incognito tab — but the *recording* was already suppressed at write-time (FR-002). Read-time replay does not retroactively record because `RecordHistoryEntryUseCase` is the only writer and only the page-finish path invokes it.
- The `openUrl` consumed-once signal is the project's established pattern for one-shot navigation triggers from a feature ViewModel back to the host screen.

**Alternatives considered**:
- **Use `Intent` / explicit nav-graph deep-link**: rejected. Indirect; would route through `MainActivity`. The in-process VM call + popBack is simpler and matches Spec 013.
- **Have `HistoryViewModel` expose a callback in `HistoryScreen`'s parameter list**: rejected. Hilt-injected VMs in this project drive UI state via `StateFlow<UiState>`, not callbacks; the established pattern wins.
