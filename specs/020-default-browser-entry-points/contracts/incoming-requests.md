# Contract C2 — Resolving and opening incoming content

Pure-domain contracts. Every row below is a unit-test case (Constitution §VI, research R4/R6).

## `ResolveIncomingRequestUseCase(request: IncomingRequest): IncomingTarget`

`MAX = 2_048` characters.

### `OpenAddress(raw)`

| Input | Result |
|---|---|
| `https://example.org/a?b=1` | `Load("https://example.org/a?b=1")` |
| `HTTP://Example.org` (scheme case) | `Load` (scheme compared case-insensitively) |
| `http://` (no host) | `Rejected` |
| `https:///path` (no host) | `Rejected` |
| `https://exa mple.org` (not parseable) | `Rejected` |
| `javascript:alert(1)`, `intent://x#Intent;end`, `file:///sdcard/a`, `content://x/y`, `data:text/html,hi`, `market://details?id=x`, `about:blank`, `chrome://flags` | `Rejected` |
| empty / whitespace | `Rejected` |
| longer than `MAX` | `Rejected` |

### `InterpretText(raw)`

| Input | Result |
|---|---|
| `"   "` | `Nothing` |
| `https://example.org/a` | `Load("https://example.org/a")` |
| `example.org` (single token, classifier says URL) | `Load(<classifier's address>)` |
| `Read this https://example.org/a — so good` | `Load("https://example.org/a")` |
| `See https://a.org, then https://b.org.` | `Load("https://a.org")` (first; trailing `,` trimmed) |
| `(https://example.org/x)` | `Load("https://example.org/x")` (trailing `)` trimmed) |
| `see file.txt today` | `Search("see file.txt today")` (no scheme inside a sentence → not an address) |
| `best pho in hanoi` | `Search("best pho in hanoi")` |
| `javascript:alert(1)` (single token) | `Search("javascript:alert(1)")` — never loaded; searching the literal text is harmless |
| 5,000 chars without a link | `Search(first 2,048 chars, trimmed)` |

### `SearchText(raw)`

| Input | Result |
|---|---|
| `"  "` | `Nothing` |
| `phở` | `Search("phở")` |
| `https://example.org` | `Search("https://example.org")` (FR-011: always search) |
| 5,000 chars | `Search(first 2,048 chars)` |

## `OpenIncomingRequestUseCase(target: IncomingTarget): OpenOutcome`

Only `Load` and `Search` reach it (`Rejected`/`Nothing` are handled by the view model).

| Situation | Outcome |
|---|---|
| `Load(u)`, < 50 normal tabs | `OpenedInNewTab(newId)`; `CreateTabUseCase(u)` called once; incognito repository never touched |
| `Search(t)` | URL = `BuildSearchUrlUseCase(t)`, then as `Load` |
| same final URL opened ≤ 2,000 ms ago | `Duplicate`; no repository call |
| same final URL opened 2,001 ms ago | opened again |
| different URL within 2,000 ms | opened (window is per URL) |
| 50 normal tabs | `OpenedInExistingTab(idOfMaxLastActiveAt, u)`; `SwitchActiveTabUseCase` called with that id; no tab created |
| active tab is incognito, < 50 normal tabs | `OpenedInNewTab` (normal) |
| repository error other than the cap | `Failed` |

## `EntryPointViewModel` (presentation)

| State | On a relay value |
|---|---|
| onboarding incomplete | keep it; do nothing |
| onboarding becomes complete while a value is held | resolve → open → emit `ShowBrowser` |
| onboarding complete | resolve → open → emit `ShowBrowser` |
| target `Rejected` | emit `ShowBrowser`; `EntryMessageRelay` ← `entry_link_rejected` |
| target `Nothing` | emit `ShowBrowser` only |
| outcome `OpenedInExistingTab` | emit `ShowBrowser`; `EntryMessageRelay` ← `entry_tab_limit_reused`; push URL to `ActiveTabNavigationRelay` |
| outcome `OpenedInNewTab(id)` | `ExternalTabRegistry.add(id)`; emit `ShowBrowser` |

The relay value is cleared as soon as it is consumed, before the open completes, so a
recomposition or resubscription cannot open it twice (INV-3).
