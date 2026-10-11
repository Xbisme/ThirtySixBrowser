# Research: Default Browser & Entry Points (Spec 020)

All findings below were checked against the code on `main` at `3a51613` (2026-10-11).

## R1 — How the app becomes "a browser" to the system

**Decision**: Add to `MainActivity` (the only activity) these intent filters:

| Filter | Purpose | FR |
|---|---|---|
| `VIEW` + `DEFAULT` + `BROWSABLE`, `scheme="http"` and `scheme="https"`, no host | Appears in "Open with" for every web link; required to be eligible for the browser role | FR-001 |
| `MAIN` + `APP_BROWSER` | Marks the app as a browser for launchers and `Intent.makeMainSelectorActivity` | FR-001 |
| `SEND` + `DEFAULT`, `mimeType="text/plain"` | Share sheet | FR-009 |
| `PROCESS_TEXT` + `DEFAULT`, `mimeType="text/plain"`, `android:label="@string/entry_process_text_label"` | Text-selection menu entry | FR-011 |
| `WEB_SEARCH` + `DEFAULT` | System web-search request | FR-012 |

No other scheme is declared (FR-002). `autoVerify` is not used — that is for app links to
the app's own domain, which ThirtySix does not have.

**Rationale**: The browser role (API 29+) is granted only to apps whose activity handles
`ACTION_VIEW` http(s) with `BROWSABLE` + `DEFAULT`; below API 29 the same filter is what puts
the app in the system default-apps "Browser app" list.

**Alternatives considered**: a separate trampoline activity for incoming intents — rejected:
it adds a second entry in recents during the hand-off and a second theme/splash path, while
the single activity can receive everything through `onCreate` / `onNewIntent`.

## R2 — One instance, and Back returning to the sending app

**Decision**: `MainActivity` gets `android:launchMode="singleTask"`. New intents arrive in
`onNewIntent`. When Back closes an externally opened tab (FR-006), the activity calls
`moveTaskToBack(true)`, which returns the user to the task that was in front before
ThirtySix — the sending app.

**Rationale**: With the default `standard` launch mode, each link from another app creates a
new `MainActivity` **inside the sender's task** — one recents entry per link and a second
copy of the WebView (fails FR-004/SC-002). `singleTask` keeps one instance in ThirtySix's own
task. `singleTop` is not enough: it only reuses an instance already on top of the *same*
task. `moveTaskToBack(true)` is the established browser pattern for "close this tab and go
back where the link came from"; `finish()` would destroy the whole browser.

**Checked against existing behaviour**:
- Spec 017's splash: `installSplashScreen()` runs in `onCreate`; `onNewIntent` does not show
  a launch screen, which is correct (the app is already running).
- Spec 019's `configChanges="locale|layoutDirection|screenLayout"` is unaffected.
- `singleTask` changes the launcher's behaviour only in that re-tapping the icon brings the
  existing task forward — which is what already happens in practice.

**Alternatives considered**: `singleInstance` — rejected, it would force every other activity
the app starts (system pickers, role request) into separate tasks and break result delivery.

## R3 — Never opening an intent twice (FR-008)

**Decision**: `MainActivity` hands an intent to the app exactly once:
- in `onCreate` **only when `savedInstanceState == null`** and the intent does not carry
  `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`;
- in `onNewIntent` always, then `setIntent(intent)`.

**Rationale**: After rotation (rotation is not in `configChanges`) or after process death,
Android recreates the activity with the **original** launch intent, and reopening from
recents after the task was cleared redelivers it with `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`.
Handling the intent on those paths would reopen the link — the duplicate SC-008 forbids.
A rotation therefore never reaches the handler; a fresh launch always does.

**Alternatives considered**: remembering a hash of handled intents in DataStore — rejected:
more state, and a user who genuinely taps the same link again a day later must get a tab.

## R4 — Parsing and resolving incoming content

**Decision**: Two steps, split by layer:
1. **Presentation adapter** (`IncomingIntentReader`) turns an `Intent` into a pure-Kotlin
   `IncomingRequest` — `OpenAddress(raw)`, `InterpretText(raw)` or `SearchText(raw)` — reading
   only `action`, `dataString`, `EXTRA_TEXT`, `EXTRA_PROCESS_TEXT` and `SearchManager.QUERY`.
   Anything else maps to `null` (ignored).
2. **Domain use case** `ResolveIncomingRequestUseCase` turns an `IncomingRequest` into an
   `IncomingTarget`: `Load(url)`, `Search(text)`, `Rejected` or `Nothing`. Pure Kotlin
   (`java.net.URI` only), fully unit-testable (§VI).

Rules (table in [contracts/incoming-requests.md](contracts/incoming-requests.md)):
- `OpenAddress`: scheme MUST be `http`/`https` (case-insensitive) and the host non-empty,
  else `Rejected` (FR-015). Longer than 2,048 chars → `Rejected` (an address that long is not
  a link a person tapped; truncating it would load a different URL).
- `InterpretText`: trim; empty → `Nothing`; cut to 2,048 chars (FR-013). If the whole text is
  a single token (no whitespace) that the address bar would classify as a URL (Spec 009's
  `classifyAddressBarInput`), load the address it produces; else take the **first** match of
  a new `UrlPatterns.EMBEDDED_WEB_URL_REGEX` (`https?://[^\s]+`, case-insensitive), trimming
  trailing `.,;:!?)]}'"`; else `Search`.

**Sharing the address-bar classifier**: `classifyAddressBarInput` and its result type are
already pure Kotlin (they import only `core/constants`), but live in
`presentation/browser/`, which the domain layer must not import (§IV). They are **moved
unchanged** to `domain/model/AddressInput.kt`; the address bar and the new use case then
call the same function, so "what counts as an address" cannot drift between typing it and
sharing it. The existing classifier tests move with it. No behaviour change for Spec 009.
- `SearchText`: trim, empty → `Nothing`, cut to 2,048, always `Search` (FR-011).

**Rationale**: Intent extraction is the only part that needs Android; everything with
decisions in it stays in the domain, where it can be tested on the JVM. Bare words without a
scheme inside a sentence ("see file.txt today") are deliberately **not** treated as
addresses — only explicit `http(s)://` links, so a sentence is never mistaken for a domain.

**Alternatives considered**: `android.util.Patterns.WEB_URL` — rejected, it is an Android
class (keeps the rule out of the JVM tests) and matches bare words like `file.txt`.

## R5 — Delivering a request to the right screen, and holding it during onboarding

**Decision**: A process-scoped `IncomingRequestRelay` (`@Singleton`, in-memory
`MutableStateFlow<IncomingRequest?>`) sits between the activity and the UI.
- `MainActivity` writes to it (R3).
- An activity-scoped `EntryPointViewModel`, created in `AppNavGraph`, collects it. If
  onboarding is not completed (read through the existing `ObserveUserSettingsUseCase`), it
  waits; once completed — or immediately when it already is — it consumes the request,
  runs `OpenIncomingRequestUseCase`, and emits a one-shot "show browser" event.
- `AppNavGraph` reacts to that event with
  `navigate(Browser) { popUpTo(Browser) { inclusive = false }; launchSingleTop = true }`,
  so History/Settings/Tabs/Downloads/Bookmarks are popped and the browser shows (FR-005).

**Rationale**: The relay survives rotation (FR-019 "survives a configuration change during
onboarding") because it is process-scoped, and keeps the activity free of tab logic (§IV).
Onboarding's two write sites (Spec 018) are untouched: the view model only *observes* the
flag. Holding the request across **process death** during onboarding is not required by the
spec and is not attempted; the request is simply lost in that rare case and the user taps the
link again.

**Alternatives considered**: putting the request into the NavHost start route as an argument
— rejected: it only works for cold starts, not `onNewIntent`, and would couple Spec 018's
routing to incoming content.

## R6 — Opening the target: new tab, tab cap, duplicates

**Decision**: `OpenIncomingRequestUseCase(target)`:
1. `Search(text)` → URL via the existing `BuildSearchUrlUseCase` (Spec 010).
2. **Duplicate window** (FR-007): if the same final URL was opened within the last 2,000 ms
   (monotonic clock seam), return `Duplicate` and do nothing.
3. `CreateTabUseCase(url)` (normal `TabRepository` only — never incognito, FR-017). Success →
   `OpenedInNewTab(tabId)`; the new tab is active because `CreateTabUseCase` already sets
   `lastActiveAt`.
4. On `MaxTabsReachedException` (FR-018): pick the normal tab with the highest
   `lastActiveAt` (from `ObserveTabsUseCase`'s current list), make it active with
   `SwitchActiveTabUseCase`, and return `OpenedInExistingTab(tabId, url)`.

The browser then loads the URL:
- `OpenedInNewTab` → `BrowserScreen`'s existing `LaunchedEffect(activeTab?.id)` loads the new
  tab's URL, exactly as for a tab opened from the switcher.
- `OpenedInExistingTab` → the tab id may not change, so the URL is sent through a small
  `ActiveTabNavigationRelay` that `BrowserScreen` collects and passes to
  `webViewActions.loadUrl(url)` — a normal navigation, so the replaced page is one Back away.

**Known limitation (flag for review)**: the app keeps one WebView and recreates it on tab
switch (Spec 011), so per-tab back history does not survive a switch. When the reused tab
was **already active** (the normal case — the user was browsing it), Back returns to the
replaced page as FR-018 promises. When the active tab was **incognito**, the reused normal tab
is switched to, its WebView is rebuilt, and the replaced page is not reachable with Back.
Spec's edge case says "the most recently used normal tab is used"; the Back promise can only
be met when that tab is already active. Recorded in Complexity Tracking; the spec edge case
is amended in the same change to say so.

**Alternatives considered**: closing the oldest tab — rejected by the owner in clarification.

## R7 — "Opened from another app" marker and Back (FR-006)

**Decision**: `ExternalTabRegistry` (`@Singleton`, in-memory `Set<Long>`) records the tab id
on `OpenedInNewTab`, and drops it when that tab is closed. `BrowserScreen` adds a
`BackHandler` enabled when `!canGoBack && activeTabIsExternal && !isAddressBarFocused`; it
calls `BrowserViewModel.onBackLeavingExternalTab()` → `CloseTabUseCase(tabId)` and then the
screen calls `activity.moveTaskToBack(true)` (R2).

Tabs reused under the cap (R6 step 4) are **not** marked (FR-018's last sentence). The marker
is not persisted: after process death the tab behaves like any other tab, which is the safe
fallback.

**Precedence with Spec 019's Home handlers**: an external tab is never `cameFromHome`, so the
two handlers cannot both be enabled; the new handler is composed after `HomeBackHandlers`.

## R8 — Default-browser state and request

**Decision**: a platform seam in the Spec 016 style.
- Domain: `interface DefaultBrowserController { fun isDefaultBrowser(): Boolean }` (total,
  main-thread, failure → `false`).
- Data: `AndroidDefaultBrowserController` —
  - API 29+: `RoleManager.isRoleAvailable(ROLE_BROWSER) && isRoleHeld(ROLE_BROWSER)`;
  - API 24–28: `packageManager.resolveActivity(Intent(ACTION_VIEW, "https://example.com"),
    MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == packageName`.
- Presentation: `rememberDefaultBrowserRequest(onUnavailable)` returns a launcher built on
  `rememberLauncherForActivityResult(StartActivityForResult())` with the intent:
  - API 29+ with the role available: `RoleManager.createRequestRoleIntent(ROLE_BROWSER)`;
  - otherwise `Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS`;
  - `ActivityNotFoundException` on both → `onUnavailable()` shows the short message (FR-023).

The result code is ignored on purpose: the state is **re-read on every `ON_RESUME`**
(`LifecycleResumeEffect`) by the Settings row and the Home invitation (FR-022), which also
covers changes made in system settings later (US2-8).

**Rationale**: `createRequestRoleIntent` shows the system's own confirmation (FR-021).
`ACTION_MANAGE_DEFAULT_APPS_SETTINGS` exists from API 24 and is the default-apps screen.
No permission is needed for either (FR-028). Below API 30 package-visibility rules do not
apply, so `resolveActivity` sees the real default.

## R9 — Invitation dismissal and launch count storage

**Decision**: Two new DataStore keys in the existing `thirtysix_settings` file:
`default_browser_invite_dismissed` (Boolean, default `false`) and `app_launch_count`
(Int, default `0`, stops at 3). They are exposed through a **new** narrow repository,
`DefaultBrowserInviteRepository`, backed by the existing `SettingsDataStore` wrapper —
**not** by adding members to `SettingsRepository`/`UserSettings`.

**Rationale**: Spec 018 found that one new `SettingsRepository` member broke four
hand-written fakes. A separate interface keeps those fakes untouched and keeps the
invitation's concerns out of the settings model. The keys are app state, not user settings,
and stay inside Auto Backup's existing policy for the settings file (a restored device that
already dismissed the invitation should not see it again).

**Counting (FR-024a)**: `MainActivity.onStart` increments the count (capped at 3) unless
- the previous `onStop` happened during a configuration change (`isChangingConfigurations`),
  or
- the app itself launched the default-browser screen and is returning from it (a flag set
  just before launching, cleared on the next `onStart`).

## R10 — Where the invitation sits on Home

**Decision**: a new `LazyColumn` item `KEY_DEFAULT_BROWSER_INVITE`, between the hero and the
shortcut cards, built from the Home page's existing card surface and `RoundIconButton` for
dismiss; its visibility is a `HomeUiState` boolean computed in `HomeViewModel` from: invite
not dismissed, launch count ≥ 3, onboarding completed (always true on Home), not default
(re-read on resume), active tab not incognito (FR-024). Accepting runs the same launcher as
Settings and marks the invitation dismissed (FR-025/FR-026).

**Rationale**: Home already composes from a single `LazyColumn` with stable keys and content
types (Spec 019, §V rule 6); an extra keyed item does not disturb the others. The card is
static content — no frame-rate state — so §V's phase rules are met trivially.

## R11 — Privacy of incoming content (FR-016)

**Decision**: No new code path logs a URL, shared text or query; the reader, use cases and
relays take no logger. Code review checklist item in tasks. **Pre-existing**: `FaviconCache`
logs a page URL when a favicon write fails (`Log.w(..., "Failed to write favicon for $url")`).
That predates this spec and is about pages, not incoming content; it is noted here for Spec
023 rather than changed in this spec.

## R12 — Dependencies and size

**Decision**: **No new dependency.** `RoleManager`, `Intent`, `Settings` are platform APIs;
`rememberLauncherForActivityResult` comes from `activity-compose` (declared), and
`LifecycleResumeEffect` from `lifecycle-runtime-compose` 2.10.0 — the artifact that already
provides `collectAsStateWithLifecycle` throughout the app. It reaches the classpath
transitively today; because this spec calls it directly, the plan declares it explicitly in
the version catalog on the **existing** `lifecycle = "2.10.0"` key (same version, no new
artifact in the build graph, verified at implementation per §IX). The 16 KB
gate is unaffected (no `.so`). Expected APK delta: well under SC-010's 60 KB (strings ×8,
a few small classes).
