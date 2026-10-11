# Data Model: Default Browser & Entry Points (Spec 020)

No Room change — the schema stays at **v2**. Two DataStore keys are added; everything else is
in memory or owned by the system.

## 1. `IncomingRequest` (domain, in memory)

What another app handed to ThirtySix, after the presentation adapter has read the `Intent`
(research R4). Sealed:

| Variant | Produced from | Field |
|---|---|---|
| `OpenAddress(raw: String)` | `ACTION_VIEW` `dataString` | the address as delivered |
| `InterpretText(raw: String)` | `ACTION_SEND` `EXTRA_TEXT` | shared text |
| `SearchText(raw: String)` | `ACTION_PROCESS_TEXT` `EXTRA_PROCESS_TEXT`, `ACTION_WEB_SEARCH` `SearchManager.QUERY` | text to search |

An intent with any other action, or with a missing/non-string field, produces **no** request.
Lifetime: from the activity until `EntryPointViewModel` consumes it (R5). Never persisted,
never logged (FR-016).

## 2. `IncomingTarget` (domain, in memory)

Result of `ResolveIncomingRequestUseCase` — see the full rule table in
[contracts/incoming-requests.md](contracts/incoming-requests.md).

| Variant | Meaning |
|---|---|
| `Load(url: String)` | a validated http/https address to open |
| `Search(text: String)` | trimmed text (≤ 2,048 chars) to search with the chosen engine |
| `Rejected` | unsafe or malformed address — show "could not open link" (FR-015) |
| `Nothing` | empty after trimming — open the app, do nothing else (FR-013) |

## 3. `OpenOutcome` (domain, in memory)

Result of `OpenIncomingRequestUseCase`.

| Variant | Effect on tabs | UI follow-up |
|---|---|---|
| `OpenedInNewTab(tabId)` | new normal tab, active | browser shown; tab marked external (§6) |
| `OpenedInExistingTab(tabId, url)` | most recently used normal tab made active | browser shown; URL sent through `ActiveTabNavigationRelay`; "tab limit reached" notice (FR-018) |
| `Duplicate` | none | browser shown, nothing else (FR-007) |
| `Failed` | none | browser shown, generic error message |

## 4. `IncomingRequestRelay` (process singleton)

`MutableStateFlow<IncomingRequest?>`. One slot: a newer request replaces an unconsumed older
one (the person's latest tap wins). Written by `MainActivity`, consumed (set back to `null`)
by `EntryPointViewModel` only after onboarding is completed (FR-019). Survives configuration
changes in memory; an **unconsumed** request also survives process death through the
activity's saved state (research R3: saved as `kind` + `raw`, restored only into an empty
relay).

## 5. `ActiveTabNavigationRelay` (process singleton)

`MutableSharedFlow<String>` (`replay = 0`, `extraBufferCapacity = 1`, `DROP_OLDEST`, the
project's one-shot policy). Carries the URL to load into the current tab for
`OpenedInExistingTab` (research R6). Collected by `BrowserScreen`.

## 5a. `EntryMessageRelay` (process singleton)

`MutableSharedFlow<Int>` of string resource ids (`replay = 0`, `extraBufferCapacity = 1`,
`DROP_OLDEST`). Written by `EntryPointViewModel`, shown by `BrowserScreen`'s snackbar host
(research R5).

## 6. `ExternalTabRegistry` (process singleton)

`Set<Long>` of tab ids opened by `OpenedInNewTab`.

| Transition | Trigger |
|---|---|
| id added | `OpenedInNewTab` |
| id removed | that tab is closed (any path: Back on it, close button, swipe, close all) |

Read by `BrowserScreen` to enable the "close tab and return to sender" Back handler
(FR-006). Not persisted — after process death the tab behaves like any other.

## 7. Default-browser state (system-owned)

Boolean read through `DefaultBrowserController.isDefaultBrowser()` (research R8). Never
stored. Re-read on every `ON_RESUME` by the screens that show it (FR-022).

## 8. Invitation state (DataStore, `thirtysix_settings`)

| Key (`StorageKeys`) | Type | Default | Written when |
|---|---|---|---|
| `default_browser_invite_dismissed` | Boolean | `false` | the person taps the invitation's close button (FR-025), or the app sees ThirtySix as the default on any foreground (FR-026, research R9) — never by the invitation's action alone (FR-027) |
| `app_launch_count` | Int | `0` | `MainActivity.onStart` when `LaunchCountPolicy.shouldCount(...)` (research R9); stops at 3 |

Exposed only through `DefaultBrowserInviteRepository` (new, narrow; does not touch
`SettingsRepository` / `UserSettings`). Old installs read the defaults — no migration.

### Invitation visibility (derived, `HomeUiState.showDefaultBrowserInvite`)

```
show = !dismissed
    && launchCount >= 3
    && !isDefaultBrowser          // re-read on resume
    && !activeTabIsIncognito
```

Onboarding completion is implied: Home is only reachable after it (Spec 018).

## Invariants

- **INV-1**: An incoming request never creates or modifies an incognito tab (FR-017).
- **INV-2**: A `Rejected` target never reaches the WebView (FR-015).
- **INV-3**: A given `Intent` instance is handed to the relay at most once; a request restored from saved state is one that was never consumed (research R3, FR-008, FR-019).
- **INV-4**: The relay is not consumed while onboarding is incomplete (FR-019).
- **INV-5**: A request never produces more than one tab; under the cap it produces none (FR-018).
- **INV-6**: Two identical final URLs within 2,000 ms produce one outcome (FR-007).
- **INV-7**: Only `OpenedInNewTab` tabs enter `ExternalTabRegistry` (FR-006, FR-018).
- **INV-8**: `default_browser_invite_dismissed` is never written back to `false`.
- **INV-9**: No component in this spec writes a URL, shared text or query to a log (FR-016).
