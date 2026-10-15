# Research: ViewModel Action/Event

Measured on `026-viewmodel-action-event` at `0372d0f` (2026-10-15). `M` = `app/src/main/kotlin/com/raumanian/thirtysix/browser`, `T` = unit tests, `A` = instrumented tests. Full per-holder inventories are in [data-model.md](data-model.md).

## R1. Forwarding use cases: all 33 go

**Decision**: remove all 33. Each caller injects the contract the use case forwarded to.

Every one of the 33 was re-read on `0372d0f`:

- Each `invoke` makes exactly one call on one constructor property, with no mapping, no wrapping, no dispatcher and no check.
- None has a second public function, and no Hilt module binds one: all are `@Inject constructor`.
- Every contract is a pure-Kotlin interface in `core/domain/repository`. That includes `AppLanguageController` and `DefaultBrowserController`, so no Android type reaches a ViewModel and FR-005 keeps none.

Special cases:

| Use case | What changes |
|---|---|
| `CreateTabUseCase` | Its only extra is a default `url = homeUrl` taken from `@Named("default_home_url")`. Its one caller without an argument, `TabsViewModel.onNewTabClick`, already injects that same `homeUrl` and passes it explicitly. |
| `BuildSearchUrlUseCase`, `ObserveDownloadsUseCase` | Also called by other use cases (`OpenIncomingRequestUseCase`, `ObserveHasActiveDownloadsUseCase`). Those take `SearchEngineRepository` / `DownloadsRepository` instead. |
| `GetUserSettingsUseCase`, `ObserveUserSettingsUseCase`, `RecordAppLaunchUseCase` | Called by `MainActivity` (field injection). It injects `SettingsRepository` / `DefaultBrowserInviteRepository`. App-level wiring may see `core/domain`. |
| `MoveFolderUseCase` | `M/core/domain/model/BookmarkFolder.kt:3` says cycle prevention lives in `MoveFolderUseCase`. It lives in `BookmarkRepositoryImpl` (`BookmarkException.FolderCycle`). The comment is corrected in the bookmarks commit. |

**Tests (FR-006)**: every removed use-case test asserts either a pure pass-through or the behaviour of the (fake or real) repository behind it.

| Removed test | Disposition |
|---|---|
| Pass-through-only tests: `BuildSearchUrlUseCaseTest`, `ObserveHistoryEntriesUseCaseTest`, the pass-through cases in `AppLanguageUseCasesTest`, `SettingsUseCasesTest`, `DefaultBrowserUseCasesTest` | Deleted |
| `CountFolderDescendantsUseCaseTest`, `DeleteBookmarkUseCaseTest`, `DeleteFolderUseCaseTest`, the cycle case of `FolderUseCasesTest`, `ClearAllHistoryUseCaseTest`, `DeleteHistoryEntryUseCaseTest`, the cap and switch cases of `TabUseCasesTest` | Their assertions are about repository behaviour. Each is checked against the `*RepositoryImpl` / DAO tests: an assertion already covered there is dropped with the file, one not covered moves into that repository test. tasks.md lists each assertion and where it lands. |

Test files that also test non-forwarding use cases (`AppLanguageUseCasesTest` for `SetAppLanguage`, `DefaultBrowserUseCasesTest` for `SyncDefaultBrowserInvite`, `FireproofUseCasesTest`, `SiteProtectionUseCasesTest`) keep those cases and only drop the removed ones.

**Alternatives**: keep the use cases that are called from several features, for "symmetry". Rejected: constitution III already allows a direct contract call, and the template's ADR 0001 removes them.

**After this spec**: 82 → 49 use cases. A scan finds no other one-call use case. The closest ones each add a branch or a mapping: `IsSiteFireproof`, `ObserveActiveTab`, `SetSiteFireproof`, `SwitchToTab`, the three `Remove*` use cases and `IsUrlBookmarked`.

## R2. Which classes the action rule covers

**Decision**: the 18 classes named `*ViewModel`. 16 of them take user input and get `onAction`. `PageTextSizeViewModel` and `EntryPointViewModel` have no input (0 public functions) and keep none.

Three more `@HiltViewModel` classes live in `feature/browser/presentation/browser/page/`: `PopupTabs`, `SitePermissionGate` and `LongPressActions`. They are **WebView bridges**, not screen state holders:

- `PopupTabs.decide(...)` returns a `PopupDecision` synchronously inside `WebChromeClient.onCreateWindow`. `take(url)` returns the pop-up `WebView`.
- `SitePermissionGate.resolve` and `apply` are suspend calls that return the outcome to the page-request queue.
- `LongPressActions` is driven by the WebView's long-press menu.

All three are hosted in a ViewModel only to get a scope that survives configuration changes. Forcing them through a fire-and-forget `onAction` would break synchronous WebView contracts (ADR 0013). They keep their shape and names, and are obtained at `BrowserRoute` and passed down (R5). The Konsist check matches the `ViewModel` name suffix, and ADR 0026 records the exemption and its reason.

**Alternatives**:
- Rename them `*ViewModel` and give them `onAction`. Rejected: the return values cannot be events.
- Use plain `@ActivityRetainedScoped` classes. Rejected: that is a lifecycle change to WebView glue, which this spec does not touch.

## R3. Action shape

**Decision**:

- One `sealed interface XAction` per screen, in a file `XAction.kt` in the screen's folder. Cases are `data object` or `data class`.
- The holder exposes `fun onAction(action: XAction)` with an exhaustive `when` that calls private functions named as today.
- `BrowserAction` groups its cases in nested sealed interfaces: `Page` (12 WebView callbacks), `AddressBar`, `Home`, `Find`, `Download`, `Tab`, plus top-level cases.

Every public function becomes an action. Lifecycle calls also become actions: `HomeAction.Resumed`, `DefaultBrowserInviteAction.Resumed`, `SettingsAction.Started` and `SettingsAction.Resumed`. The four holders whose functions are not all user taps are handled as follows:

| Today | After |
|---|---|
| `OnboardingViewModel.onBack(): Boolean` (false on the first slide, then the screen calls `finish()`) | The route reads `state.isFirstSlide`: if true it calls `finish()`, otherwise it sends `OnboardingAction.Back`. Same outcome; the decision is made from state the screen already has. |
| `BrowserViewModel.onAddressBarSubmit(loadUrl): Boolean` and `onHomeSubmit(input, loadUrl): Boolean` | `BrowserAction.AddressBar.Submit` / `Home.Submit(input)`. When the holder decides to load, it emits `BrowserEvent.LoadUrl(url)`. The route's event handler hides the keyboard, clears focus, then loads, in that order. The return value was unused by callers (`Unit` after the call). |
| `BrowserViewModel.onDesktopModeToggled(): Boolean?` | `BrowserAction.ToggleDesktopMode`. When there is a new mode it emits `BrowserEvent.SetDesktopMode(enabled)`, and the route calls `webViewActions.setDesktopMode`. |
| `BrowserViewModel.onBackWithoutHistory(): Boolean` | `BrowserAction.BackWithoutHistory`. The return value is ignored by its only caller (`BackHandler`). |
| `AddressSuggestionsViewModel.onEditingStarted/onEditingEnded` | Not called from UI; made private (`onFocusChanged` calls them). Tests drive `FocusChanged`. |
| `DownloadsViewModel.emitEvent` (internal) | Private. |

**Synchronous lookups** are not inputs. They stay read-only properties, like `ProtectionViewModel.hooks` and `EntryPointViewModel.browserHooks` today:

- `TabsViewModel.faviconFor(url): File?` and `screenshotFor(tabId): File?` become `val images: TabImages`, a small `fun`-based interface in the tabs presentation folder.
- `BrowserViewModel.isDesktopMode(tabId): Boolean` is read once when a tab's `WebView` is built (ADR 0015: by tab id, not from state). It becomes `val desktopMode: DesktopModeLookup`.
- `HistoryViewModel.faviconCache` is already a property and stays.

**Rationale**: an exhaustive `when` lists the whole input surface in one place. Sync lookups that return files or flags at build time are queries, not intents, and turning them into state would change when disk I/O happens.

**Alternatives**:
- One flat `BrowserAction` with ~30 cases. Rejected because nesting reads better, and the owner allowed either (Clarifications).
- Putting favicon and screenshot files in `TabsUiState`. Rejected: it moves `File.exists` checks to state-building time.

## R4. Event delivery: keep each effect's semantics

**Decision**: every holder with one-off effects exposes `val events: Flow<XEvent>` of one sealed `XEvent`, collected at the route. Each effect keeps the delivery it has today (owner, Clarifications Q4):

| Today | Kind | After |
|---|---|---|
| `MutableSharedFlow(replay = 0, extraBufferCapacity = 1, …)` + `tryEmit` (7 streams in the holders) | **Drop**: lost when nobody collects | Same `MutableSharedFlow` settings, now typed `XEvent` |
| `Channel(capacity = 1, DROP_OLDEST)` + `trySend` (`EntryPointViewModel`) | **Keep**: delivered when a collector arrives | Unchanged |
| A nullable field in `UiState` plus `consume…()` after handling (7 fields: `Tabs.errorEvent`, `Bookmarks.errorEvent`, `Bookmarks.openUrl`, `History.openUrl`, `Browser.tabsEvent`, `Browser.bookmarkSnackbarEvent`, `Browser.downloadSnackbarEvent`) | **Keep** | `Channel<XEvent>(Channel.BUFFERED)` + `receiveAsFlow()`. The `UiState` field and its `consume…` function are deleted |

A holder with both kinds (Tabs: `popBackEvent` is Drop, `errorEvent` is Keep; History: the snackbar is Drop, `openUrl` is Keep) exposes `events = merge(keep.receiveAsFlow(), drop)`. Each case is sent through the path it used before. The per-effect list with its kind is in [data-model.md](data-model.md#effects).

**One visible difference, accepted by the spec**: a Keep effect held in `UiState` was consumed only after its snackbar finished. If the screen rotated while that snackbar was showing, the snackbar appeared again. With a channel the event is delivered once, so a rotation mid-snackbar does not repeat it. Spec US1 scenario 2 asks for exactly this ("does not happen again after rotation"). It is the only behaviour change and is listed in the PR.

**Collection** stays as today: a `LaunchedEffect` in the composition, not lifecycle-gated. Moving to `repeatOnLifecycle(STARTED)` would change when Drop effects are lost. One helper, `core/presentation/util/EventEffect.kt` (`@Composable fun <E> EventEffect(events: Flow<E>, onEvent: suspend (E) -> Unit)`), replaces the hand-written `LaunchedEffect { collect }` and `LaunchedEffect(field) { …; consume() }` blocks. It is keyed on the flow and uses `rememberUpdatedState` for the handler.

**Not changed**:

- `FireState.Finished` lives in the singleton `FireCoordinator` and is the coordinator's state: the overlay animation runs until `acknowledge`.
- The two singleton relays (`ActiveTabNavigationRelay`, `EntryMessageRelay`) are not holders.
- `PopupTabs` and `LongPressActions` already expose typed `events` (R2).

**Alternatives**:
- Make everything Keep. Rejected by the owner: 10 Drop effects would start appearing where they did not before.
- Use a shared event-bus class. Rejected: two standard declarations cover both kinds.

## R5. Route and Screen

**Decision**:

- Each navigation destination gets `XRoute(navController/callbacks…, viewModel: XViewModel = hiltViewModel())`.
- The route collects `uiState` with `collectAsStateWithLifecycle()` once, runs `EventEffect`, handles navigation and platform calls (`finish()`, keyboard, WebView handle), and calls `XScreen(state, onAction, …)`.
- `XScreen` and every child take plain values and callbacks; no child receives a holder.
- `feature/*/presentation/navigation/*Navigation.kt` calls the route.

| Destination / host | Route | Holders obtained there |
|---|---|---|
| browser | `BrowserRoute` | Browser, Hub, AddressSuggestions, PageTextSize, Protection; plus the WebView bridges `PopupTabs`, `LongPressActions`, `SitePermissionGate`, obtained here (today they are obtained inside `BrowserScreen` and `BrowserPageRequests`) and passed down |
| tabs, bookmarks, history, downloads, settings, sitepermissions, protectionexceptions, fireproof, onboarding | `TabsRoute`, `BookmarksRoute`, … | Their one holder |
| Home overlay inside the browser screen | `HomePage` stays the route-level host: it already obtains `HomeViewModel` and `DefaultBrowserInviteViewModel` as default parameters and draws the stateless `HomeContent` | Home, DefaultBrowserInvite |
| Quick settings inside the browser screen | `QuickSettingsHost` stays the host. `SettingsDialogHost` and the snackbar effect take `state` + `onAction` instead of the holder | Settings |
| Fire | `rememberFireButton` and `FireOverlayHost` stay hosts and send `FireAction` | Fire |
| App root | `AppNavHost` keeps `EntryPointViewModel` (ADR 0011) | EntryPoint |

compose-rules defines a "route" as the composable that obtains the holder as a default parameter and does not forward it. Hosts embedded through `BrowserSlots` satisfy the same rule. That is the check behind FR-013, so no extra Konsist rule is needed for composables.

**Instrumented tests**: tests that call `SettingsScreen(viewModel = …)`, `HistoryScreen(viewModel = …)`, `DownloadsScreen(viewModel = …)`, `FireproofSitesScreen(…)`, `SitePermissionsScreen(…)`, `ProtectionExceptionsScreen(…)` and `BrowserScreen(slots, viewModel)` switch to `XRoute(viewModel = …)`. They keep exercising the real holder, and their assertions do not change. `HomePageTest` already uses the stateless `HomeContent`.

## R6. Main dispatcher rule

**Decision**: add `T/core/testing/MainDispatcherRule.kt`, a JUnit 4 `TestWatcher(val dispatcher: TestDispatcher = UnconfinedTestDispatcher())` that calls `setMain` in `starting` and `resetMain` in `finished`. The 22 test files that set the main dispatcher by hand switch to `@get:Rule val main = MainDispatcherRule(testDispatcher)`, passing the dispatcher they use today: `StandardTestDispatcher` in 17, `UnconfinedTestDispatcher` in 5. Scheduling inside each test is therefore unchanged.

A Konsist test over the test source set, `ArchitectureTest.main dispatcher is set only by the rule`, fails on `Dispatchers.setMain` anywhere except `MainDispatcherRule.kt`.

**Alternatives**: a JUnit 5 extension. Rejected: the project is on JUnit 4.13.2.

## R7. Checks

| Check | Rule | Where |
|---|---|---|
| `ArchitectureTest.view models take input only through onAction` (Konsist) | A class named `*ViewModel` in `presentation` has no public or internal function other than `onAction`. Properties are allowed. Private functions are free | `T/architecture/ArchitectureTest.kt` |
| `ArchitectureTest.main dispatcher is set only by the rule` (Konsist) | R6 | same |
| compose-rules `vm-forwarding-check`, `vm-injection-check` | Already active. The baseline (23 entries, all in `BookmarksScreen`, `BrowserScreen` (10), `DownloadsScreen`, `HistoryScreen`, `OnboardingScreen`, `QuickSettingsHost`, `SettingsScreen`, `SitePermissionsScreen`, `TabsScreen`) is deleted along with `baseline.set(…)` in `app/build.gradle.kts` | `config/ktlint/baseline.xml` |

The onAction test lands in the last code commit, after every holder has moved, so it needs no allowlist (`KNOWN_VIOLATIONS` stays empty). Each feature commit removes that feature's baseline lines, so the ktlint baseline shrinks commit by commit.

## R8. Governance

- **Constitution 2.1.0** (MINOR: a check was added). In principle III:
  - The **Enforced by** line gains `ArchitectureTest.view models take input only through onAction` and compose-rules `vm-forwarding-check` / `vm-injection-check`.
  - **Review** keeps only KSP.
  - The Spec 026 note is removed.
  - The first bullet's "may call a repository interface directly" stays as is.
- **`docs/constitution-history.md`**: one line for 2.1.0.
- **ADR 0026 "Screens take actions and emit events"**: route/screen, sealed `XAction`, the Drop and Keep event kinds and why both remain, the WebView bridges exempt from `onAction` (R2), and sync lookups as properties (R3).
- **ADR 0001 / template**: a short consequence line saying forwarding use cases were removed (R1).
- **`AGENTS.md` "Open work"**: the Spec 026 line is deleted. Writing-code gains: "Screens are `XRoute` + `XScreen(state, onAction)`; one-off effects are `XEvent`."

## R9. Order of work

One branch and one commit per step, with each commit green (G1). The order is leaf features first, so each feature's forwarding use cases are removed together with the holder that called them:

1. Baseline (G0).
2. `MainDispatcherRule` + the 22 tests + its Konsist check.
3. `EventEffect` helper.
4. Features: sitepermission → privacy (protection exceptions, fireproof, fire) → downloads → history → onboarding → settings (incl. `QuickSettingsHost`) → tabs → bookmarks → entrypoint (`MainActivity`, `OpenIncomingRequestUseCase`) → browser (home, invite, hub, suggestions, protection, browser).
5. A core use case is deleted in the commit that removes its last caller.
6. The onAction Konsist check + ktlint baseline deletion + governance.
7. Device gates and the PR.

Within a feature commit, the order is: remove its use cases, rewrite the holder to `onAction`/`events`, split route/screen, then update the holder test and the instrumented tests.

## R10. Performance

- `BrowserAction.Page.ProgressChanged(newProgress)` allocates one small object per WebView progress callback (≈ 10–30 per page load). This is negligible beside the WebView itself. `loadProgress` stays its own flow, read in `drawBehind` (ADR 0015).
- Screen composables taking `onAction: (XAction) -> Unit` receive a stable method reference (`viewModel::onAction`, remembered at the route). The Compose compiler report must stay at 0 non-skippable (G8).
- Release APK and cold start are compared with `0372d0f` (SC-007).
