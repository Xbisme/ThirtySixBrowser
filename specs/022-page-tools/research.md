# Research: Page Tools (Spec 022)

**Date**: 2026-10-11 · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)

Every decision below was taken against the code on `main` at `38d13b1`. No new dependency is needed. Every platform API used is either in `android.webkit` (system WebView) or `android.print`, or in `androidx.webkit` 1.17.0, which is already on the classpath (Spec 016).

---

## R1 — Where per-tab desktop mode lives

**Facts found.** `BrowserScreen` builds one `BrowserWebView` per tab activation, inside `key(activeTabId)` (`BrowserScreen.kt:450`). Switching tab therefore destroys the WebView and builds a new one. Any per-tab setting stored on the WebView would be lost on the first tab switch.

**Decision.** `BrowserViewModel` holds `desktopModeTabs: MutableStateFlow<Set<Long>>`, an in-memory set of tab ids. `BrowserUiState` gains a derived `isDesktopMode: Boolean` for the active tab. The WebView factory reads it **before** its initial `loadUrl`, so a tab re-activated in desktop mode loads in desktop mode. Toggling calls a new `WebViewActionsHandle.setDesktopMode(enabled)`, which reconfigures the live WebView and reloads it (FR-025).

**Why this satisfies the spec at no cost.**
- FR-028 (not remembered): a ViewModel dies with the process.
- FR-027 (new tabs start mobile): a new tab id is never in the set. This includes pop-ups (Spec 021), links from other apps (Spec 020) and "open in new tab".
- A closed tab's id is removed from the set in `onTabClosed` to keep the set bounded. A stale id is harmless anyway, because tab ids are never reused.

**Alternatives rejected.**
- A column on `TabEntity`: needs a Room migration (FR-037 forbids it) and would make the mode persist (FR-028 forbids that too).
- A `@Singleton` store in `data/`: adds a repository for state that is deliberately ephemeral and screen-scoped.
- Re-keying `BrowserWebView` on the mode: destroys the WebView, and with it the tab's back/forward history.

## R2 — How the page is made to look like a desktop browser

**Decision.** Three settings, applied together, and reverted together when the mode is turned off:

1. **User agent.** Derived from `WebSettings.getDefaultUserAgent(context)`, so the Chrome version stays truthful:
   - the `(Linux; Android …; …)` platform token becomes `(X11; Linux x86_64)`;
   - the `; wv` token is removed;
   - the ` Mobile` token is removed.

   This is the string Chrome for Android sends in its own desktop mode. Turning the mode off sets `userAgentString = null`, which restores the default.
2. **Viewport.** `useWideViewPort = true` and `loadWithOverviewMode = true`, so a page without a viewport tag is laid out at desktop width and zoomed out to fit.
3. **User-agent client hints.** Through `WebSettingsCompat.setUserAgentMetadata(...)` with `mobile = false` and platform `"Linux"`, guarded by `WebViewFeature.isFeatureSupported(USER_AGENT_METADATA)`. Sites increasingly read `Sec-CH-UA-Mobile` instead of the UA string. Where the feature is missing (older WebView), the UA string alone still works for most sites.

The derivation is a pure function, `DesktopUserAgent.from(defaultUa)`, unit-tested on recorded UA strings from API 24, 29 and 36.

**Known limit (recorded, not fixed).** A page that sends `width=device-width` in its viewport tag even to a desktop UA still renders narrow; WebView cannot override that tag. SC-006 picks sites that serve a different desktop layout, which is the case this spec targets.

**Verify at implementation.** That `setUserAgentMetadata` and `UserAgentMetadata.Builder` exist in `androidx.webkit` 1.17.0 under these names. If they do not, ship (1) and (2) only and note it in tasks.md.

## R3 — Find in page

**Decision.** Use `WebView.findAllAsync(text)`, `findNext(forward)`, `clearMatches()` and `setFindListener`. All are available since API 16. Matching is case-insensitive and wraps at both ends by default, which matches FR-007 and FR-008.

- **Results.** The listener's `onFindResultReceived(activeMatchOrdinal, numberOfMatches, isDoneCounting)` flows into the ViewModel through a new callback. The ordinal is 0-based; the bar shows `ordinal + 1`.
- **State.** `FindInPageState(isOpen, query, current, total)` lives in `BrowserViewModel` but is exposed as its **own** `StateFlow`. Only the find bar collects it (§V rule 3), because results arrive on every keystroke and while the page is still loading.
- **Imperative calls.** `WebViewActionsHandle` gains `findAll(String)`, `findNext(Boolean)` and `clearMatches()`, wired in the factory like the existing actions. This keeps the WebView out of the ViewModel (§IV).
- **Closing.** The bar closes and highlights are cleared on:
  - Close, or system Back;
  - `onLoadStarted` (a main-frame navigation, including Back, Forward and Reload);
  - a change of active tab id. The old WebView is destroyed on a tab switch, so there are no highlights left to clear.
- **Back.** The find bar's `BackHandler` is composed **after** the existing back handlers, so it takes precedence over predictive back while the bar is open (FR-010).
- **Rotation and language change.** `MainActivity` already handles both without recreation (Specs 019 and 021). Because the state lives in the ViewModel, FR-012 is satisfied twice over.
- **Accessibility.** The "n/m" text is a polite `liveRegion`, so TalkBack announces count changes (FR-036).

**Alternatives rejected.** `WebView.findAll()` is synchronous and deprecated. A JavaScript search would be script injection, which §II limits to bundled one-way scripts, and it would gain nothing here.

## R4 — Text size

**Decision.** `WebSettings.textZoom` takes a percentage, which is exactly the model.

- **Model.** New domain enum `TextSize(percent)` with the nine levels (FR-029), mirroring `HistoryRetention(days)`. It is decoded with `fromPercentOrDefault`.
- **Storage.** New DataStore key `text_zoom_percent` (`intPreferencesKey`), default 100. `UserSettings` gains `textSize`. The settings file is already in Auto Backup, and the value is not touched by Clear browsing data, whose use case only clears web data and history (FR-032).
- **Applying it.**
  - `BrowserViewModel` exposes `textZoomPercent: StateFlow<Int>`.
  - `BrowserWebView` takes a `textZoomPercent: Int` parameter and applies it in the factory, before the initial load.
  - A `LaunchedEffect(textZoomPercent)` keyed honestly (§V rule 8) applies it to the live WebView.
  - Only the active tab has a live WebView. Every other tab is built later with the current value, so "every open tab without reload" (FR-031) holds by construction.
- **UI.** Settings gains a `TextSizeChooser` entry in `SettingsDialog` plus a row. The hub's Text size tile opens it through `QuickSettingsHost` as a new `QuickSetting.TextSize`, the same route Theme and Language already take. One chooser, one ViewModel, so the two entry points cannot disagree.

**Alternatives rejected.** `WebView.zoomBy()` / page scale changes layout zoom rather than text size, and resets on every load. `WebSettings.textSize` (the old enum) has been deprecated since API 14.

## R5 — Autocomplete data path

**Constraint carried from Spec 014.** FR-017 requires every character to match only itself. Spec 014 rejected SQL `LIKE` for that reason. ⚠️ Finding while researching: `BookmarkDao.searchByTitleOrUrl` (Spec 013) **does** use `LIKE '%' || :query || '%'` without `ESCAPE`, so `_` and `%` are wildcards in bookmark search today. That is out of scope here and is recorded as debt.

**Decision.** Build a snapshot on focus and filter it in memory.

1. **Load.** When an address entry gains focus, load a **suggestion snapshot** once, off the main thread:
   - all bookmarks, most recently saved first;
   - all history, newest visit first, reduced to one entry per address (its most recent visit);
   - history addresses already present as bookmarks are dropped (FR-016).
2. **Filter.** On each keystroke, run a pure `filter(snapshot, query, limit = 6)`. It uses `String.contains(query, ignoreCase = true)` on title or address, and stops at the 6th hit. Kotlin's `contains` is literal and Unicode case-insensitive. SQLite's `LOWER()` is ASCII-only, which matters for VI, RU and DE titles.
3. **Release.** The snapshot is released when editing ends, so it costs memory only while the person types.

**Cost.**
- **Memory.** The snapshot holds about 300 B per row (Spec 014's heap measurement), so about 3 MB transiently for 10,000 rows. History is bounded by the retention window (at most 180 days).
- **Time.** The worst case is a filter scanning every row without reaching 6 hits: about 21,000 `contains` calls, a few milliseconds on `Dispatchers.Default`. Collection uses `mapLatest`, so a superseded keystroke is cancelled. SC-003's 200 ms budget is mostly the one-time load, which happens on focus, before the first keystroke.

**Layering.**
- `BookmarkRepository.getAllBookmarks()` and `HistoryRepository.getLatestVisitPerUrl()` are new one-shot suspend reads.
- `LoadAddressSuggestionsSourceUseCase` combines them. Repositories never depend on each other (§IV).
- `FilterAddressSuggestionsUseCase` is pure.

**Where the state lives.** A separate `@HiltViewModel AddressSuggestionsViewModel`, like `PopupTabs` and `LongPressActions` in Spec 021, so `BrowserViewModel` (639 lines) does not grow. It writes nothing anywhere, so typing in an incognito tab is never recorded (FR-022).

**Alternatives rejected.**
- SQL `instr()`: literal, but case-insensitive only for ASCII.
- An FTS table: needs a migration (FR-037).
- Keeping all history in memory permanently: costs about 3 MB for the app's whole life, for a feature used a few seconds at a time.

## R6 — Suggestions on Home

**Facts found.** `HomeSearchBar` (`HomeHero.kt:178`) keeps its text in local `remember` state and reports only on submit.

**Decision.** Add an `onTextChange` and an `onFocusChange` to `HomeSearchBar`. Both forward to the same `AddressSuggestionsViewModel`. The suggestion list renders under the Home search field there, and under the top bar on a web page. One composable, `AddressSuggestionList`, serves both. It is a `LazyColumn` keyed by address with a `contentType` (§V rule 6), and it consumes the `ime` insets (§V rule 7).

- **FR-021.** On a web page the address bar shows the current address when focused (Spec 009), so no suggestions appear until the text differs from `currentUrl`. On Home the field starts empty, so FR-021 holds there too.
- **Choosing a suggestion.** It reuses the existing submit path with the suggestion's address, so focus release, keyboard dismissal and history recording all behave exactly as a typed address (FR-019).

## R7 — Share

**Decision.**
- **Intent.** `Intent.ACTION_SEND`, `text/plain`, `EXTRA_TEXT` = current address, `EXTRA_SUBJECT` = title when not blank, wrapped in `Intent.createChooser`.
- **Launching.** Started from the composable's `LocalContext` inside `runCatching`. The platform chooser always exists, but the project wraps every platform call (Spec 012 posture).
- **Title.** The active tab's title, already observed by `BrowserScreen` through `activeTab`.
- **Incognito.** No branch: sharing is identical (FR-024), and nothing is recorded because nothing is written.

**Alternative rejected.** A Hilt-bound `ShareLauncher` seam: there is no logic to unit-test behind it. The only decisions are "title blank → no subject", which lives in a pure `ShareContent.of(url, title)` with a unit test, and the availability rules in R9.

## R8 — Print

**Decision.**
- **Action.** `WebViewActionsHandle.print(jobName)` is wired in the factory. It calls:
  ```kotlin
  (wv.context.getSystemService(PRINT_SERVICE) as PrintManager)
      .print(jobName, wv.createPrintDocumentAdapter(jobName), null)
  ```
  inside `runCatching`.
- **Context.** The WebView is created with the `AndroidView` factory context, which is the activity, and `PrintManager.print` needs an activity context.
- **Job name.** The title, else the hostname (a pure `PrintJobName.of(title, url)`, unit-tested).
- **Availability.** Print is offered only while `LoadingState.Loaded` (FR-033). Starting a print from a half-rendered page would print a half-rendered page.
- **Permissions and records.** No permission is needed. The app records nothing (FR-034); "Save as PDF" writes wherever the person picks through the system UI.

## R9 — Tool availability and the hub

**Decision.** A pure `PageToolsAvailability.of(isHomeContent, loadingState)` returns which tools are enabled, unit-tested against the spec's table:

| Page state | Find | Share | Desktop | Text size | Print |
|---|---|---|---|---|---|
| Home / incognito start page | — | — | — | ✓ | — |
| Loading | ✓ | ✓ | ✓ | ✓ | — |
| Loaded | ✓ | ✓ | ✓ | ✓ | ✓ |
| Failed | — | ✓ | ✓ | ✓ | — |

**Hub layout.** A new **page tools** section above the places row, in two rows:
- Find in page · Share · Print
- Desktop site · Text size

Five tiles do not fit one row at 360dp in German, which Spec 019 G11 showed already strains at four. A tool that is not available is **not shown** (FR-002), rather than shown disabled. On Home the section therefore shrinks to the Text size tile alone.

**`QuickTile` changes** (design waiver, Spec 019 component). Two new optional parameters, both defaulted, so existing call sites are unchanged:
- `selected: Boolean`: a checked look, plus `toggleable` semantics so TalkBack says "on" or "off";
- `value: String?`: a second line, for example "125%".

## R10 — Bottom bar and top bar while finding

**Decision.** `BrowserScreen` swaps `BrowserTopBar` for a new `FindInPageBar` when `findInPage.isOpen`, and does not compose `BrowserBottomBar` at all while finding (FR-005). Hiding it rather than sliding it keeps the sliding bars' scroll state untouched.

The find bar consumes `statusBars` insets like the top bar it replaces (§V rule 7). The keyboard pushes nothing, because the bar sits at the top.

## R11 — Testing approach

- **Unit (JVM):**
  - `DesktopUserAgent`, `TextSize`, `PageToolsAvailability`, `ShareContent`, `PrintJobName`;
  - `FilterAddressSuggestionsUseCase`: literal characters, Unicode case folding, ordering, dedupe, cap;
  - `LoadAddressSuggestionsSourceUseCase`;
  - the DataStore round-trip of `text_zoom_percent`;
  - the `BrowserViewModel` find and desktop-mode state machines;
  - `AddressSuggestionsViewModel`.
- **Instrumented:**
  - find in page against a local test page with a known match count;
  - the desktop-mode UA reaching the server, checked with `MockWebServer` or a local echo page;
  - text zoom applied to a live WebView;
  - `AddressSuggestionList` Compose tests;
  - Room DAO tests for the two new queries.
- **Device gates:** see [quickstart.md](quickstart.md).
