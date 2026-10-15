# Research: Browser View Model Split

Measured on `027-browser-viewmodel-split` at `90cd024` (2026-10-15). `F` = `app/src/main/kotlin/com/raumanian/thirtysix/browser/feature/browser`.

## R1. What each piece of `BrowserViewModel` reads and writes

| State | Written by | Read by |
|---|---|---|
| `currentUrl` | page (`UrlChanged`, `LoadFinished`), active-tab sync, Back-without-history (→ Home URL) | address bar (suggestions), Home (`isHomeContent`, `Home.Pressed`), star (toggle, `isBookmarked`), page (history, persist), screen |
| page title (`currentTitleCache`) | page (`TitleReceived`; reset on `LoadStarted` and tab switch), Back-without-history (reset) | page (history row, tab persist), star (fallback title) |
| active tab | `ObserveActiveTabUseCase` (`stateIn` WhileSubscribed) | page (persist, screenshot), desktop mode (`.value` at toggle), find (close on switch), screen |
| incognito | `ObserveActiveTabIsIncognitoUseCase` | page (favicon, screenshot, history gates), Back-without-history, screen |
| `loadingState`, `canGoBack/Forward`, `loadProgress` | page callbacks | screen |
| `addressBarText`, `isAddressBarFocused`, `homeOverlay`, `cameFromHome` | address bar and Home actions; `cameFromHome` also by Home submit | screen, Back-without-history |
| `isBookmarked` | derived from `currentUrl` | screen |
| `isDesktopMode`, desktop set | desktop toggle; derived per active tab | screen, `desktopMode` lookup |
| find state | find actions, `FindResult`; closed by `LoadStarted` and tab switch | screen |
| history token (`pendingHistoryUrl`, last row) | page | page |

Only `currentUrl`, the page title and the active tab (with its incognito flag) cross concerns. Everything else belongs to one concern.

## R2. The shared page state

**Decision**: add one in-memory store behind a domain contract:

- **Contract**: `F/domain/repository/CurrentPageRepository`.
- **Model**: `F/domain/model/CurrentPage`, holding `url`, `title`, `activeTab: Tab?`, `isIncognito` and `generation: Long`.
- **Implementation**: `F/data/repository/CurrentPageRepositoryImpl`, a `MutableStateFlow` with synchronous setters. It starts at the injected default home URL.
- **Binding**: `@ActivityRetainedScoped` in `F/di/`.

Every holder reads `page: StateFlow<CurrentPage>`. Writes keep today's moments:

- The page holder writes the URL when the page reports it, writes the title, and writes the tab and incognito state as the tab flows emit.
- Back-without-history writes the Home URL and clears the title.
- `generation` increments on every load start and every tab switch. The find holder closes on each change, replacing today's direct `onFindClosed()` calls.

**Rationale**:
- Constitution III and the Android architecture guidance put state shared by several holders in the data layer.
- An in-memory `StateFlow` updated synchronously gives every reader the value today's single holder sees at the same moment, so a star tapped right after a redirect still stars the new URL.
- The `ActivityRetained` scope gives every holder of the browser screen one instance that survives configuration changes, as their `ViewModel` state does. Process death resets it, as it resets today's state.
- The store keeps the active tab with its writer subscribed. Today's `activeTabFlow.value` read at toggle time stays correct even when no screen collects a given holder's tab flow.

**Alternatives**:
- Each holder observes the tab repository and the history database. Rejected: the URL a page reports reaches the database only through `updateActiveTabUrlAndTitle`, one coroutine later, so the star and Home would read a stale URL.
- A parent holder passes state down. Rejected: a holder cannot own another.
- `@Singleton` scope. Rejected: it would outlive the activity for no reason.

## R3. The holders

| Holder | Actions (from `BrowserAction`) | State | Events | Depends on |
|---|---|---|---|---|
| `PageViewModel` | `LoadStarted`, `ProgressChanged`, `LoadFinished`, `LoadFailed`, `LoadStopped`, `UrlChanged`, `TitleReceived`, `IconReceived`, `ScreenshotReady`, `CanGoBackChanged`, `CanGoForwardChanged` (11) | `PageUiState(currentUrl, loadingState, canGoBack, canGoForward, isIncognito)`, `loadProgress`, `activeTab`, `tabCount` | none | `CurrentPageRepository`, active-tab and incognito use cases, `UpdateActiveTabUrlAndTitle`, favicon and screenshot caches, `RecordHistoryEntry`, `UpdateHistoryEntryTitle`, `TabRepository` (count) (9) |
| `AddressBarViewModel` | `TextChange`, `FocusChange`, `Submit`, `Clear`, `Home.Pressed`, `Home.OverlayDismissed`, `Home.Submit`, `Home.NavigateFrom`, `BackWithoutHistory` (9) | `AddressBarUiState(text, isFocused, homeOverlay, cameFromHome, isHomeContent)` | `LoadUrl` (Keep) | `CurrentPageRepository`, `SearchEngineRepository`, `UpdateActiveTabUrlAndTitle` (3) |
| `FindInPageViewModel` | `Opened`, `QueryChanged`, `Closed`, `Result` (4) | `FindInPageState` | none | `CurrentPageRepository` (1) |
| `DownloadRequestViewModel` | `Requested`, `StoragePermissionDenied` (2) | none | `Download` (Keep) | `StartDownloadUseCase` (1) |
| `PageActionsViewModel` | `StarTapped`, `ToggleDesktopMode`, `LongPressNewTab` (3) | `PageActionsUiState(isBookmarked, isDesktopMode)`, `desktopMode` lookup | `Bookmark`, `TabLimit`, `SetDesktopMode` (Keep) | `CurrentPageRepository`, `IsUrlBookmarked`, `ToggleBookmark`, `TabRepository`, home URL (5) |

Every holder stays within SC-004 (≤ 10 dependencies, ≤ 12 actions). `FindResult` moves from the page group to `FindInPageAction.Result`: it comes from the WebView but changes only find state.

**Event order**: today one channel carries every browser event. After the split each holder has its own Keep channel, and `BrowserRoute` runs one `EventEffect` per holder. No pair of events depends on order across concerns: a load and a message are independent. Within a concern, order is kept because each concern uses one channel.

## R4. The screen keeps `BrowserUiState`

**Decision**: `BrowserUiState` becomes a screen-level value. `BrowserRoute` assembles it from `PageUiState`, `AddressBarUiState` and `PageActionsUiState`. The children of `BrowserScreen` keep their parameters (top bar, WebView, back handlers), and `isHomeContent` comes from the address-bar state.

**Rationale**: It is one assembly line at the route, and the many composables that read `state.x` stay unchanged, which keeps the screen diff to wiring.

**Alternative**: pass three states down. Rejected: about 20 composable signatures would change for no gain.

## R5. Splitting `BrowserScreen.kt`

The file is divided by part, keeping every composable as it is:

| File | Content |
|---|---|
| `BrowserRoute.kt` | route, effects, assembly |
| `BrowserScreen.kt` | the `Box` layout |
| `BrowserContent.kt` | WebView host |
| `BrowserHubHost.kt` | the hub and page-tool handler |
| `BrowserBackHandlers.kt` | back handlers |
| `BrowserCallbacks.kt` | address-bar, Home and find callbacks |

`BrowserScreenSources` and `BrowserScreenActions` grow by the new holders' entry points but lose the `LongParameterList` suppression: their fields are grouped into the holders' flows and actions. The aim is no file over 300 lines and no size suppression on these files.

## R6. Tests

| Today | After |
|---|---|
| `BrowserViewModelTest` (mixed) | split by concern into `PageViewModelTest`, `AddressBarViewModelTest`, `PageActionsViewModelTest`, `DownloadRequestViewModelTest` |
| `BrowserViewModelFindTest` | `FindInPageViewModelTest` |
| `BrowserViewModelDesktopModeTest` | `PageActionsViewModel` desktop-mode tests |
| `BrowserViewModelStarToggleTest` | `PageActionsViewModel` star tests |
| `BrowserViewModelHistoryRecordSequenceTest`, `BrowserViewModelIncognitoCacheGateTest` | `PageViewModel` tests |
| `BrowserViewModelFixture` | `BrowserHoldersFixture`, which builds every holder over one `CurrentPageRepositoryImpl` and the fakes |
| cross-concern tests (e.g. submit then `UrlChanged`) | drive two holders over the same store |

Each assertion is kept. `CurrentPageRepositoryImpl` gets its own test. The instrumented `BrowserScreen*Test`s pass the page holder they build to `BrowserRoute(pageViewModel = …)`.

## R7. Cleanup

- `BaseViewModel` and `BaseViewModelTest` are deleted.
- These size suppressions go when their files are split: `BrowserViewModel.kt`, the four in `BrowserScreen.kt` and the one in `BrowserScreenInputs.kt`, 7 in all (SC-003).
- Others stay with their reasons: WebView clients, page requests, sheets, `HomePage`, `HomeViewModel`, `PopupTabs`.

## R8. Order of work

One commit per step, each green on G1 plus androidTest compile:

1. Baseline.
2. `CurrentPageRepository` with its implementation and test. `BrowserViewModel` reads and writes the URL, title, tab and generation through it, with no other change.
3. Find.
4. Downloads.
5. Page actions.
6. Address bar and Home.
7. Rename the remainder to `PageViewModel`.
8. Split `BrowserScreen.kt` and drop the suppressions.
9. Delete `BaseViewModel`.
10. ADR 0027 and `AGENTS.md`.
11. Device gates and PR.

## R9. Governance

- **ADR 0027 "Browser screen state is split by concern over one page store"**: records the store, its scope, the holders and why `BrowserUiState` stays a screen-level value.
- **Constitution**: no rule changes. The store is a domain contract with a data implementation, as principle I and II already require.
- **`AGENTS.md`**: unchanged.
