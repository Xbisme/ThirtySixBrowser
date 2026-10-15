---

description: "Task list for Spec 026 ViewModel Action/Event"
---

# Tasks: ViewModel Action/Event

**Input**: Design documents from `specs/026-viewmodel-action-event/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/screen-contract.md](contracts/screen-contract.md), [quickstart.md](quickstart.md)

**Tests**: no new test suite is requested. The existing unit and instrumented suites are the safety net (spec Input, FR-021/FR-022). Test tasks below change existing tests to the new shape, add the two Konsist checks (FR-016, R6) and handle tests of removed use cases (FR-006).

**Paths**:

| Short | Path |
|---|---|
| `M` | `app/src/main/kotlin/com/raumanian/thirtysix/browser` |
| `C` | `M/core` |
| `F` | `M/feature` |
| `T` | `app/src/test/kotlin/com/raumanian/thirtysix/browser` |
| `A` | `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser` |

Gradle calls are prefixed with `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` and checked for `BUILD SUCCESSFUL`.

**How every feature task is done** (applies to T007–T039; [contracts/screen-contract.md](contracts/screen-contract.md)):

1. **Use cases** [US3]: delete each listed `*UseCase.kt` and inject the contract named in [data-model.md § Use cases removed](data-model.md#use-cases-removed). Keep argument names and call the same contract method. For each removed use-case test, follow research R1: pass-through only → delete; asserts repository behaviour → confirm the `*RepositoryImpl`/DAO test covers it, otherwise move that assertion there. Update the holder's test factory or fixture to build the holder with the fake repository instead of the use case.
2. **Action/Event** [US2]: add `XAction.kt` (a `sealed interface` with `data object`/`data class` cases and `val`s only), and add `XEvent.kt` where the holder has effects. Add `fun onAction(action: XAction)` with an exhaustive `when` that calls the old functions, which become `private`. Effects go to one `val events: Flow<XEvent>` and keep their kind exactly as [data-model.md § Effects](data-model.md#effects) says:
   - **Drop**: the same `MutableSharedFlow(...)` arguments and `tryEmit`.
   - **Keep**: `Channel<XEvent>(Channel.BUFFERED)`, `trySend`, `receiveAsFlow()`.
   - **Both**: `merge(...)`.

   Delete the `UiState` one-off fields and their `consume…` functions.
3. **Route/Screen** [US2]:
   - In `XScreen.kt`, add `XRoute(…, modifier: Modifier = Modifier, viewModel: XViewModel = hiltViewModel())`. It collects `uiState` once with `collectAsStateWithLifecycle()`, handles effects with `EventEffect(viewModel.events) { … }` (the same snackbar, navigation or platform call as before, in the same order), and calls `XScreen(state, onAction = viewModel::onAction, …)`.
   - `XScreen` and every child take values and callbacks only, never a holder.
   - `F/<f>/presentation/navigation/<F>Navigation.kt` calls `XRoute`.
   - Delete the file's lines from `config/ktlint/baseline.xml`.
4. **Tests** [US2]: the holder's unit test uses `MainDispatcherRule` (already switched in T005), sends actions with `onAction(...)`, and reads effects from `events` with Turbine. Instrumented tests that called `XScreen(viewModel = …)` call `XRoute(viewModel = …)`. Assertions stay the same; only calls change.
5. **Gate**: run quickstart G1, plus the feature's instrumented tests on `TA016_API36_16K` when it has them, then **commit** `refactor(026): <feature> actions and events`.

---

## Phase 1: Setup

**Purpose**: baseline numbers to compare against (SC-001, SC-007).

- [ ] T001 Run quickstart G0 on `0372d0f` (the branch tip before any change) and record it in the "Baseline" table at the end of this file:
  - unit test count (`./gradlew testDebugUnitTest`)
  - instrumented test count on `TA016_API36_16K`
  - release APK bytes (`app/build/outputs/apk/release/app-release.apk`)
  - Compose report restartable / non-skippable (`-PcomposeReports`)
  - `grep -c '<error' config/ktlint/baseline.xml` (expect 23)
  - `grep -rl 'Dispatchers.setMain' app/src/test | wc -l` (expect 22)
  - use-case count `find M -name '*UseCase.kt' | wc -l` (expect 82)
- [ ] T002 Build and keep the `0372d0f` debug and release APKs outside the repository, in the session scratchpad directory (never committed) for G5 upgrade and cold-start comparison. Install the debug one on `TA016_API36_16K` and seed as Spec 025 T005: tabs, history and downloads seeders (`app/src/debug/AndroidManifest.xml` actions); a bookmark folder, a fireproof site, a site-permission decision and a protection exception by hand.

---

## Phase 2: Foundational (blocking prerequisites; delivers US5)

**Purpose**: the shared test rule and event helper that every feature commit uses.

- [ ] T003 Create `T/core/testing/MainDispatcherRule.kt`: `class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher()` with `Dispatchers.setMain(dispatcher)` in `starting` and `Dispatchers.resetMain()` in `finished` (JUnit 4).
- [ ] T004 [P] Switch these 22 files from `@Before setMain` / `@After resetMain` to `@get:Rule val mainDispatcherRule = MainDispatcherRule(<the dispatcher the file uses today>)`. The dispatcher type must stay the same (`StandardTestDispatcher` or `UnconfinedTestDispatcher`); drop the emptied `@Before`/`@After` functions.
  - `T/core/presentation/util/BaseViewModelTest.kt`
  - `T/feature/bookmarks/presentation/bookmarks/BookmarksViewModelTest.kt`
  - `T/feature/browser/presentation/browser/{BrowserViewModelDesktopModeTest,BrowserViewModelFindTest,BrowserViewModelHistoryRecordSequenceTest,BrowserViewModelIncognitoCacheGateTest,BrowserViewModelStarToggleTest,BrowserViewModelTest}.kt`
  - `T/feature/browser/presentation/browser/protection/ProtectionViewModelTest.kt`
  - `T/feature/browser/presentation/home/{DefaultBrowserInviteViewModelTest,HomeViewModelTest}.kt`
  - `T/feature/browser/presentation/suggestions/AddressSuggestionsViewModelTest.kt`
  - `T/feature/downloads/presentation/downloads/DownloadsViewModelTest.kt`
  - `T/feature/entrypoint/presentation/entry/EntryPointViewModelTest.kt`
  - `T/feature/history/presentation/history/HistoryViewModelTest.kt`
  - `T/feature/onboarding/presentation/onboarding/OnboardingViewModelTest.kt`
  - `T/feature/privacy/presentation/protectionexceptions/ProtectionExceptionsViewModelTest.kt`
  - `T/feature/settings/presentation/settings/{SettingsViewModelDefaultBrowserTest,SettingsViewModelTest}.kt`
  - `T/feature/sitepermission/presentation/sitepermissions/SitePermissionsViewModelTest.kt`
  - `T/feature/tabs/presentation/tabs/{TabsViewModelChipModeTest,TabsViewModelTest}.kt`
- [ ] T005 Add `` `main dispatcher is set only by the rule` `` to `T/architecture/ArchitectureTest.kt`: Konsist `scopeFromTest(moduleName = "app")`, every file except `MainDispatcherRule.kt` must not contain `Dispatchers.setMain`, `additionalMessage = "use MainDispatcherRule"`. Prove it as in quickstart G2 step 3 on a scratch change, then revert. Run G1. **Commit** `test(026): one main dispatcher rule`.
- [ ] T006 Create `C/presentation/util/EventEffect.kt`: `@Composable fun <E> EventEffect(events: Flow<E>, onEvent: suspend (E) -> Unit)`. It wraps `val latest by rememberUpdatedState(onEvent)` and `LaunchedEffect(events) { events.collect { latest(it) } }`; collection is not lifecycle-gated (research R4). Run G1. **Commit** `feat(026): EventEffect`.

**Checkpoint**: unit suite green with the rule in place; `EventEffect` available.

---

## Phase 3: User Stories 2 and 3, one feature per commit (P1 + P2)

**Goal (US2)**: every screen talks to its holder through `onAction` and `events`, with a Route/Screen split.
**Goal (US3)**: no use case that only forwards one call is left.
**Independent test**: after each feature commit, that feature's holders expose only `onAction` (plus properties), its screen composables receive no holder, its baseline lines are gone, and its unit and instrumented tests pass unchanged in what they assert.

The two stories land together per feature because the holder's constructor (US3) and its input surface (US2) change in the same file (research R9). Order is leaf-first.

### sitepermission

- [ ] T007 [US3] Remove `F/sitepermission/domain/usecase/{ObserveSitePermissionsUseCase,SetSitePermissionUseCase}.kt`. `F/sitepermission/presentation/sitepermissions/SitePermissionsViewModel.kt` injects `SitePermissionRepository` (`observeAll()`, `save(origin, capability, decision)`). Neither use case has a test of its own; update `T/feature/sitepermission/presentation/sitepermissions/SitePermissionsViewModelTest.kt` and `A/feature/sitepermission/presentation/sitepermissions/SitePermissionsScreenTest.kt`, which construct the holder.
- [ ] T008 [US2] Add `SitePermissionsAction` with 7 cases: `DecisionClick(origin, capability, current)`, `DecisionChosen(decision)`, `EditDismissed`, `RemoveSite(origin)`, `RemoveAllClick`, `RemoveAllConfirmed`, `RemoveAllDismissed`. Add `onAction`; there is no event type. Split `SitePermissionsScreen.kt` into `SitePermissionsRoute` + `SitePermissionsScreen(state, onAction, onBack)`. `siteGroup(…)` and `SitePermissionsDialogs(…)` take `onAction` instead of the holder. `SitePermissionNavigation.kt` calls the route.
- [ ] T009 [US2] Update `SitePermissionsViewModelTest` to drive `onAction` and `SitePermissionsScreenTest` to call `SitePermissionsRoute(viewModel = …)`. Delete the `SitePermissionsScreen.kt:95` line from `config/ktlint/baseline.xml`. Gate and **commit** `refactor(026): sitepermission actions and events`.

### privacy

- [ ] T010 [US3] Remove `F/privacy/domain/usecase/ObserveProtectionExceptionsUseCase.kt`. `ProtectionExceptionsViewModel` injects `ProtectionExceptionRepository.observeAll()`. In `T/feature/browser/domain/usecase/SiteProtectionUseCasesTest.kt`, read state back through the fake repository instead (it is pass-through only). `FireproofSitesViewModel` injects `FireproofSiteRepository.observeAll()` in place of `ObserveFireproofSitesUseCase`, but the use case file stays until T037 because `ProtectionViewModel` still uses it.
- [ ] T011 [US2] Add the actions:
  - `ProtectionExceptionsAction`: `TurnOn(site)`, `TurnAllOnClick`, `TurnAllOnConfirmed`, `TurnAllOnDismissed`.
  - `FireproofSitesAction`: `Remove(site)`, `RemoveAllClick`, `RemoveAllConfirmed`, `RemoveAllDismissed`.
  - `FireAction`: `Fire`, `Acknowledge`.

  Add `onAction` to each holder. Split the screens into `ProtectionExceptionsRoute`/`Screen` and `FireproofSitesRoute`/`Screen`, and point `PrivacyNavigation.kt` at the routes. `RememberFireButton.kt` sends `FireAction.Fire` and `FireOverlayHost.kt` sends `FireAction.Acknowledge`; both stay hosts that obtain `FireViewModel`.
- [ ] T012 [US2] Update `T/feature/privacy/presentation/protectionexceptions/ProtectionExceptionsViewModelTest.kt`, to drive `onAction` (`FireproofSitesViewModel` and `FireViewModel` have no unit test; their screens are covered by the instrumented tests below and `FireCoordinatorImplTest`). Update `A/feature/privacy/presentation/fireproof/FireproofSitesScreenTest.kt` and `A/feature/browser/presentation/browser/protection/ProtectionScreensTest.kt` to call the routes. Gate (instrumented tests included) and **commit** `refactor(026): privacy actions and events`.

### downloads

- [ ] T013 [US3] Remove `C/domain/usecase/ObserveDownloadsUseCase.kt`.
  - `F/downloads/presentation/downloads/DownloadsViewModel.kt` and `F/browser/domain/usecase/ObserveHasActiveDownloadsUseCase.kt` inject `DownloadsRepository.observeAll()`.
  - Update `T/…/ObserveHasActiveDownloadsUseCaseTest.kt`, `DownloadsViewModelTest.kt` and `A/feature/downloads/presentation/downloads/DownloadsScreenTestDoubles.kt`.
- [ ] T014 [US2] Add `DownloadsAction` with 9 cases, one per `on…` function, carrying the same parameters. `DownloadsEvent` keeps its cases and is exposed as `events: Flow<DownloadsEvent>`; it is Drop, with `MutableSharedFlow(replay = 0, extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)` unchanged. `emitEvent` becomes `private`. Split `DownloadsScreen.kt` into `DownloadsRoute` and `DownloadsScreen(state, onAction, onBack)`. The route's `EventEffect` shows the same snackbars; the `FileMissing` "Remove" action calls `onAction(RemoveFromListClicked(item))`. `DownloadsNavigation.kt` calls the route.
- [ ] T015 [US2] Update `DownloadsViewModelTest`, and make `A/…/downloads/{DownloadsScreenBrowseTest,DownloadsScreenCancelTest,DownloadsScreenActionSheetTest}.kt` call `DownloadsRoute(viewModel = vm)`. Delete the `DownloadsScreen.kt:42` baseline line. Gate and **commit** `refactor(026): downloads actions and events`.

### history

- [ ] T016 [US3] Remove `F/history/domain/usecase/{ObserveHistoryEntriesUseCase,DeleteHistoryEntryUseCase,ClearAllHistoryUseCase}.kt`. `HistoryViewModel` injects `HistoryRepository` (`observeAll()`, `deleteById(id)`, `clearAll()`) and `TabRepository.createTab(url)` in place of `CreateTabUseCase`; that use case stays until T037.
  - Delete `T/feature/history/domain/usecase/ObserveHistoryEntriesUseCaseTest.kt` (pass-through).
  - For `ClearAllHistoryUseCaseTest` (removed count, 0 when empty) and `DeleteHistoryEntryUseCaseTest` (1 or 0; other visits kept), check the `HistoryRepositoryImpl`/`HistoryDao` tests and move any assertion not already covered, then delete both files.
  - Update `A/…/history/HistoryScreenTestDoubles.kt` and `HistoryRecorderIntegrationTest`.
- [ ] T017 [US2] Add `HistoryAction` with 11 cases, one per `on…` function. `consumeOpenUrl` goes. Add `HistoryEvent`:
  - `OpenUrl(url)`: **Keep**, `Channel(BUFFERED)`, replacing `HistoryUiState.openUrl`.
  - `Snackbar(HistoryErrorEvent)`: **Drop**, the existing `MutableSharedFlow(replay = 0, extraBufferCapacity = 1, DROP_OLDEST)`.

  Expose `events = merge(...)`. `faviconCache` stays a property. Split `HistoryScreen.kt` into `HistoryRoute` (its `EventEffect` calls `onShowBrowser()` for `OpenUrl` and shows the snackbar for `Snackbar`) and `HistoryScreen(state, onAction, faviconCache, onBack)`. `HistoryContent` takes `onAction`. `HistoryNavigation.kt` calls the route.
- [ ] T018 [US2] Update `HistoryViewModelTest` so `openUrl` assertions read `HistoryEvent.OpenUrl` from `events`. Make `A/…/history/{HistoryScreenBrowseTest,HistoryScreenSearchTest,HistoryScreenLongPressTest}.kt` call `HistoryRoute(viewModel = …)`. Delete the two `HistoryScreen.kt` baseline lines. Gate and **commit** `refactor(026): history actions and events`.

### onboarding

- [ ] T019 [US3] Remove `F/onboarding/domain/usecase/SetOnboardingCompletedUseCase.kt`. `OnboardingViewModel` injects `SettingsRepository` (`observeSettings()`, `setThemeMode`, `setSearchEngine`, `setOnboardingCompleted`) and `AppLanguageController.current()` in place of `ObserveUserSettings`, `GetAppLanguage`, `SetThemeMode` and `SetSearchEngine`; those core use cases stay until their last caller goes. Drop the onboarding case from `T/core/domain/usecase/SettingsUseCasesTest.kt`.
- [ ] T020 [US2] Add `OnboardingAction`: `Forward`, `Back`, `Skip`, `ThemeSelected(mode)`, `SearchEngineSelected(engine)`, `LanguageSelected(language)`. `Back` returns nothing. Add `OnboardingEvent.Leave`, replacing `leaveFlow`; it is Drop, with the same `MutableSharedFlow(replay = 0, extraBufferCapacity = 1, DROP_OLDEST)`. Split `OnboardingScreen.kt` into `OnboardingRoute` and `OnboardingScreen(state, onAction)`.
  - The route's `BackHandler` does `if (state.isFirstSlide) activity.finish() else onAction(Back)`. This is the same outcome as today's `onBack(): Boolean` (research R3), and the existing comment on why slide 1 leaves without writing the flag stays.
  - The top-bar Back sends `Back`.
  - `SlideBody` takes `onAction`; `LaunchedLeaveEffect` is replaced by `EventEffect`.
  - `OnboardingNavigation.kt` calls the route.
- [ ] T021 [US2] Update `OnboardingViewModelTest`. The `onBack()` return-value assertions become state assertions (`isFirstSlide` before `Back`; `Back` on slide 1 leaves state unchanged). Delete the two `OnboardingScreen.kt` baseline lines. Gate and **commit** `refactor(026): onboarding actions and events`.

### settings

- [ ] T022 [US3] Remove `F/settings/domain/usecase/{SetDynamicColorEnabledUseCase,SetTextSizeUseCase}.kt`. `SettingsViewModel` injects `SettingsRepository` and `DefaultBrowserController` in place of the 7 use cases listed in data-model.md. Delete from `T/core/domain/usecase/{SettingsUseCasesTest,AppLanguageUseCasesTest}.kt` and `T/feature/browser/domain/usecase/DefaultBrowserUseCasesTest.kt` the cases for removed use cases only (pass-through); keep the `SetAppLanguage` and `SyncDefaultBrowserInvite` cases. Update `T/…/settings/SettingsViewModelTestFactory.kt` and `A/…/settings/SettingsScreenTestDoubles.kt`.
- [ ] T023 [US2] Add `SettingsAction` with 21 cases:
  - `Started`, `Resumed` (was `refreshDefaultBrowser`), `DismissDialog`
  - `ThemeRowClick`, `ThemeSelected`, `TextSizeRowClick`, `TextSizeSelected`, `SearchEngineRowClick`, `SearchEngineSelected`
  - `LanguageRowClick`, `LanguageSelected`, `LanguageApplied`
  - `ClearBrowsingDataRowClick`, `ClearCategoryToggled`, `ClearBrowsingDataConfirmed`, `ClearBrowsingDataDismissed`
  - `RetentionRowClick`, `RetentionSelected`, `ShortenRetentionConfirmed`, `ShortenRetentionCancelled`
  - `DynamicColorToggled`

  Expose `SettingsEvent` as `events`; it is Drop with unchanged settings. Split `SettingsScreen.kt` into `SettingsRoute` (`LifecycleStartEffect` → `Started`, `LifecycleResumeEffect` → `Resumed`) and `SettingsScreen(state, onAction, destinations)`. The section builders, `SettingsDialogHost(state, onAction)` and `SettingsSnackbarEffect(events, snackbarHostState)` take no holder. `QuickSettingsHost.kt` keeps obtaining `SettingsViewModel`, sends `…RowClick` actions from its `LaunchedEffect(request)`, and passes `state` + `onAction` to `SettingsDialogHost`. `SettingsNavigation.kt` calls the route.
- [ ] T024 [US2] Update `SettingsViewModelTest` and `SettingsViewModelDefaultBrowserTest`. Make the 8 `A/…/settings/SettingsScreen*Test.kt` / `SettingsDefaultBrowserRowTest.kt` call `SettingsRoute(viewModel = settingsViewModel(...))`. Delete the four `SettingsScreen.kt`/`QuickSettingsHost.kt` baseline lines. Gate (instrumented tests included; quick settings from the hub by hand) and **commit** `refactor(026): settings actions and events`.

### tabs

- [ ] T025 [US3] Remove `F/tabs/domain/usecase/CloseAllIncognitoTabsUseCase.kt`. `TabsViewModel` stops using `CreateTab`, `CreateIncognitoTab`, `SwitchActiveTab` and `CloseIncognitoTab`; their core files are deleted in T037, once `PopupTabs`, `HubViewModel` and `HomeViewModel` are switched too.
  - `TabsViewModel` injects `TabRepository` (`createTab(homeUrl)`: pass its injected `homeUrl` explicitly where `createTab()` was called with no argument; `switchActiveTab`) and `IncognitoTabRepository` (`createTab`, `closeTab`, `closeAll`). `CloseTab`, `CloseAllTabs` and `ObserveAllTabs` stay; they are not forwards.
  - From `T/core/domain/usecase/TabUseCasesTest.kt`, move the cap (`MaxTabsReachedException`) and switch-active cases to the `TabRepositoryImpl` test if they are not covered there, and delete the pass-through cases.
- [ ] T026 [US2] Add `TabsAction` with 11 cases: `TabClick(tabId)`, `CloseTab(tabId)`, `NewTabClick`, `NewIncognitoTabClick`, `CloseAllRequested`, `CloseAllDismissed`, `CloseAllConfirmed`, `CloseAllIncognitoRequested`, `CloseAllIncognitoDismissed`, `CloseAllIncognitoConfirmed`, `ModeSelected(mode)`. `consumeErrorEvent` goes.
  - Add `TabsEvent`:
    - `ShowBrowser`: **Drop**, the existing `MutableSharedFlow(replay = 0, extraBufferCapacity = 1)` with default SUSPEND overflow.
    - `Error(TabsErrorEvent)`: **Keep**, `Channel(BUFFERED)`, replacing `TabsUiState.errorEvent`.

    Expose `events = merge(...)`.
  - `faviconFor`/`screenshotFor` become `val images: TabImages`, a `fun interface`-style lookup in `F/tabs/presentation/tabs/TabImages.kt` with `favicon(url): File?` and `screenshot(tabId): File?`.
  - Split `TabsScreen.kt` into `TabsRoute` (collects `uiState`, `faviconVersion`, `screenshotVersion`; `EventEffect` → `showBrowser()` / snackbar) and `TabsScreen(state, onAction, images, versions, fireButton)`. `TabsConfirmDialogs` takes `onAction`. `TabsNavigation.kt` calls the route.
- [ ] T027 [US2] Update `TabsViewModelTest` and `TabsViewModelChipModeTest`: `errorEvent` assertions read `TabsEvent.Error`, and `popBackEvent` assertions read `TabsEvent.ShowBrowser`. Delete the `TabsScreen.kt:82` baseline line. Run the tabs instrumented tests (`CloseAllIncognitoFlowTest`, `TabSwipeTest`, `TabPersistenceProcessDeathTest`, `IncognitoStressInstrumentedTest`). Gate and **commit** `refactor(026): tabs actions and events`.

### bookmarks

- [ ] T028 [US3] Remove `F/bookmarks/domain/usecase/{CountFolderDescendantsUseCase,DeleteBookmarkUseCase,DeleteFolderUseCase,MoveBookmarkUseCase,MoveFolderUseCase,ObserveAllFoldersUseCase}.kt`. `BookmarksViewModel` injects `BookmarkRepository` (`countDescendants`, `deleteBookmark`, `deleteFolderCascade`, `moveBookmarkToFolder`, `moveFolder`, `observeAllFolders`).
  - For `T/feature/bookmarks/domain/usecase/{CountFolderDescendantsUseCaseTest,DeleteBookmarkUseCaseTest,DeleteFolderUseCaseTest}.kt` and the move and cycle cases of `FolderUseCasesTest.kt`, check the `BookmarkRepositoryImpl`/DAO tests and move any assertion not covered there (recursive count on a 4-level tree, empty folder 0, `BookmarkNotFound`, cascade counts, `FolderNotFound`, `FolderCycle`). Then delete the files or cases.
  - Fix `C/domain/model/BookmarkFolder.kt:3` so it says cycle prevention lives in `BookmarkRepositoryImpl` (research R1).
- [ ] T029 [US2] Add `BookmarksAction` with 25 cases, one per `on…` function (`FolderTap`, `BackToParent`, `BreadcrumbSegmentTap`, `BookmarkTap`, `LongPressBookmark`, `LongPressFolder`, search open/close/query, create/add, rename/move/edit/delete clicks, `DialogDismiss`, the seven `Confirm…` cases with their parameters). `consumeOpenUrl` and `consumeErrorEvent` go. Add `BookmarksEvent`:
  - `OpenUrl(url)`: **Keep**, `Channel(BUFFERED)`.
  - `Error(BookmarksErrorEvent)`: **Keep**.

  Both replace `UiState` fields. Split `BookmarksScreen.kt` into `BookmarksRoute` and `BookmarksScreen(state, onAction, onBack)`. `BookmarksContent` and `BookmarksDialogs` take `onAction`. `BookmarksNavigation.kt` calls the route.
- [ ] T030 [US2] Update `BookmarksViewModelTest`: `openUrl` and `errorEvent` assertions read `events`. Delete the two `BookmarksScreen.kt` baseline lines. Gate and **commit** `refactor(026): bookmarks actions and events`.

### entrypoint

- [ ] T031 [US3] Remove `C/domain/usecase/GetUserSettingsUseCase.kt` and `F/entrypoint/domain/usecase/RecordAppLaunchUseCase.kt`.
  - `M/MainActivity.kt` injects `SettingsRepository` (`currentSettings()`, `observeSettings()`) and `DefaultBrowserInviteRepository.recordLaunch()`.
  - `F/entrypoint/domain/usecase/OpenIncomingRequestUseCase.kt` injects `SearchEngineRepository.buildSearchUrl` in place of `BuildSearchUrlUseCase`.
  - `EntryPointViewModel` injects `SettingsRepository.observeSettings()` and `TabRepository.observeTabs()`. It has no action type (0 public functions); `events` and `browserHooks` are unchanged (ADR 0011).
  - Drop the record-launch case from `DefaultBrowserUseCasesTest`. `SettingsRepositoryCurrentSettingsTest` stays as it is (it tests the repository). Update `OpenIncomingRequestUseCaseTest` and `EntryPointViewModelTest`.
- [ ] T032 [US2] Run `IncomingLinkInstrumentedTest` and `PopupInstrumentedTest`. Gate and **commit** `refactor(026): entrypoint uses contracts`.

### browser

- [ ] T033 [P] [US2] `F/browser/presentation/home/`:
  - Add `HomeAction` (`Resumed`, `TabShortcut(tabId)`) and `DefaultBrowserInviteAction` (`Resumed`, `Dismiss`), with `onAction` on both holders.
  - `HomeViewModel` injects `SettingsRepository` and `TabRepository.switchActiveTab`.
  - `DefaultBrowserInviteViewModel` injects `DefaultBrowserInviteRepository` (`observeInviteState`, `markInviteDismissed`) and `DefaultBrowserController`; remove `F/browser/domain/usecase/{DismissDefaultBrowserInviteUseCase,ObserveDefaultBrowserInviteUseCase}.kt`.
  - `HomePage.kt` stays the host, sending `Resumed` from `LifecycleResumeEffect` and drawing `HomeContent`.
  - Update `HomeViewModelTest` and `DefaultBrowserInviteViewModelTest`. Remove their cases from `DefaultBrowserUseCasesTest`; delete the file if it is left empty, otherwise keep `SyncDefaultBrowserInvite`.
- [ ] T034 [P] [US2] Hub, suggestions and protection:
  - **Hub**: add `HubAction.NewIncognitoTab` to `HubViewModel`; `HubEvent` stays Drop and is exposed as `events`. Inject `IncognitoTabRepository.createTab`.
  - **Suggestions**: add `AddressSuggestionsAction` (`TextChanged(text, currentPageUrl)`, `FocusChanged(focused)`) to `AddressSuggestionsViewModel`; `onEditingStarted` and `onEditingEnded` become private. Update `AddressSuggestionsViewModelTest` and `A/…/suggestions/AddressBarSuggestionsFlowTest.kt` to send `FocusChanged`.
  - **Protection**: add `ProtectionAction` (`ProtectionSwitched(on)`, `FireproofSwitched(on)`) to `ProtectionViewModel`. Add `ProtectionEvent.Reload`, replacing `reloadEvents`; it is Drop with unchanged settings. Inject `FireproofSiteRepository.observeAll()`. `hooks` stays.
  - `PageTextSizeViewModel` injects `SettingsRepository.observeSettings()` and keeps no action.
  - Update `ProtectionViewModelTest`.
- [ ] T035 [US2] `F/browser/presentation/browser/BrowserViewModel.kt`:
  - Add `BrowserAction` with nested groups:
    - `Page.*`: the 12 WebView callbacks with their parameters.
    - `AddressBar.*`: `TextChange`, `FocusChange`, `Submit`, `Clear`.
    - `Home.*`: `Pressed`, `OverlayDismissed`, `Submit(input)`, `NavigateFrom`.
    - `Find.*`: `Opened`, `QueryChanged`, `Closed`.
    - `Download.*`: `Requested(url, userAgent, contentDisposition, mimeType)`, `StoragePermissionDenied(permanently)`.
    - Top-level: `StarTapped`, `ToggleDesktopMode`, `BackWithoutHistory`, `LongPressNewTab`.
  - Add `BrowserEvent`, all **Keep** via `Channel(BUFFERED)`:
    - `TabLimit(TabsErrorEvent)`
    - `Bookmark(BookmarkSnackbarEvent)`
    - `Download(DownloadSnackbarEvent)`
    - `LoadUrl(url)`: emitted where the `loadUrl` lambda was called in `onAddressBarSubmit`/`onHomeSubmit`.
    - `SetDesktopMode(enabled)`: emitted where `onDesktopModeToggled` returned non-null.
  - Delete `tabsEvent`, `bookmarkSnackbarEvent` and `downloadSnackbarEvent` from `BrowserUiState`, along with the three `consume…` functions.
  - `isDesktopMode(tabId)` becomes `val desktopMode: DesktopModeLookup` (`fun interface`, in `F/browser/presentation/browser/DesktopModeLookup.kt`).
  - Inject `SearchEngineRepository`, `TabRepository` (`observeTabs`, `createTab`) in place of BuildSearchUrl, ObserveTabs, CreateTab.
  - `loadProgress`, `activeTab`, `tabCount` and `findInPage` stay separate flows (ADR 0015).
- [ ] T036 [US2] Split `F/browser/presentation/browser/BrowserScreen.kt` into `BrowserRoute(slots, viewModel, hubViewModel)` and `BrowserScreen(...)`:
  - **Route**: obtains Browser, Hub, AddressSuggestions, PageTextSize and Protection holders, plus the WebView bridges `PopupTabs` and `LongPressActions` (moved up from `BrowserScreen.kt:102,323`) and `SitePermissionGate` (moved up from the default parameters at `page/BrowserPageRequests.kt:21,42`, which then take it as a required parameter). It collects state once and runs one `EventEffect` per holder:
    - `LoadUrl` → hide keyboard, clear focus, `webViewActions.loadUrl(url)`, in that order.
    - `SetDesktopMode` → `webViewActions.setDesktopMode`.
    - `ProtectionEvent.Reload` → `webViewActions.reload()`.
    - Snackbars as before.
  - Children take `onAction`s instead of holders: `HomeBackHandlers`, `BrowserHub`, `HubEventEffect`, `BrowserContent`, `BrowserSnackbarEffects`, `rememberFindCallbacks`, `rememberAddressBarCallbacks`, `rememberHomeCallbacks`, `PageToolsHost.rememberPageToolHandler`/`rememberShareAction`, and `component/PrivacyToolsHosts.kt`.
  - The route passes the `desktopMode` lookup down to `BrowserContent`, which reads it once per tab when building `BrowserWebView` (`remember(activeTabId) { desktopMode.isDesktop(activeTabId) }`, as today).
  - Per-frame reads stay in `drawBehind`/`graphicsLayer`.
  - `BrowserNavigation.kt` calls the route.
  - Delete the 10 `BrowserScreen.kt` baseline lines; `config/ktlint/baseline.xml` is now empty.
- [ ] T037 [US3] Delete the core use cases that now have no caller:
  - `C/domain/usecase/{BuildSearchUrlUseCase,CreateTabUseCase,CreateIncognitoTabUseCase,CloseIncognitoTabUseCase,SwitchActiveTabUseCase,ObserveTabsUseCase,ObserveUserSettingsUseCase,GetAppLanguageUseCase,IsDefaultBrowserUseCase,SetSearchEngineUseCase,SetThemeModeUseCase,ObserveFireproofSitesUseCase}.kt`
  - `F/browser/presentation/browser/page/PopupTabs.kt` first injects `TabRepository`/`IncognitoTabRepository` in place of CreateTab, CreateIncognitoTab and CloseIncognitoTab. Its public shape is unchanged (research R2).
  - Delete `T/core/domain/usecase/BuildSearchUrlUseCaseTest.kt`, and the remaining pass-through cases in `SettingsUseCasesTest`, `AppLanguageUseCasesTest` (keep `SetAppLanguage`), `TabUseCasesTest` and `FireproofUseCasesTest` (keep the non-forwarding fireproof use cases).
  - `find M -name '*UseCase.kt' | wc -l` must print 49.
- [ ] T038 [US2] Update the 6 `T/feature/browser/presentation/browser/BrowserViewModel*Test.kt` and `BrowserViewModelFixture` to send `BrowserAction`s. Snackbar assertions read `events`; the submit tests assert `BrowserEvent.LoadUrl(url)` where they asserted the lambda call. Make `A/…/browser/{BrowserScreenInstrumentedTest,BrowserScreenOfflineErrorTest,BrowserScreenLoadingIndicatorTest}.kt` call `BrowserRoute(slots = NoSlots, viewModel = viewModel)`.
- [ ] T039 [US2] Run G1, the full instrumented suite on `TA016_API36_16K` and the Compose report (0 non-skippable). **Commit** `refactor(026): browser actions and events`.

**Checkpoint**: every covered holder takes `onAction`; `config/ktlint/baseline.xml` has no entries; 49 use cases.

---

## Phase 4: User Story 4, the rule is checked (P2)

**Goal**: the action rule breaks the build, and the constitution says so.
**Independent test**: quickstart G2 steps 1–2 fail with the file named; G3 passes.

- [ ] T040 [US4] Add `` `view models take input only through onAction` `` to `T/architecture/ArchitectureTest.kt`:
  - Scope: Konsist `productionScope()` classes whose name ends with `ViewModel` and whose package contains `.presentation`.
  - Assertion: every `public` or `internal` function is named `onAction`.
  - `additionalMessage = "send input through onAction(XAction)"`.

  Properties are not checked. `PopupTabs`, `SitePermissionGate` and `LongPressActions` fall outside the name match (research R2).
- [ ] T041 [US4] Delete `config/ktlint/baseline.xml` and the `baseline.set(file("$rootDir/config/ktlint/baseline.xml"))` line in `app/build.gradle.kts` (`ktlint { … }` block, around lines 135–139). Run `./gradlew ktlintCheck`; it should pass with no baseline.
- [ ] T042 [US4] Run quickstart G2 steps 1 and 2 (and step 3 again) on a scratch change. Record each failure message in the "Rule proof" table below, then revert.
- [ ] T043 [US4] Governance, in one commit:
  - **`.specify/memory/constitution.md`**:
    - In principle III, change **Enforced by** to `PlacementTest`, `ArchitectureTest.dispatchers are injected`, `ArchitectureTest.view models take input only through onAction` (Konsist), compose-rules `vm-forwarding-check` / `vm-injection-check`.
    - Change **Review** to `KSP.`, removing the Spec 026 note.
    - Version **2.1.0**, Last Amended = commit date.
  - **`docs/constitution-history.md`**: add the top row `| 2.1.0 | <date> | Action/event shape moved from Review to Enforced: Konsist onAction check and compose-rules without a baseline |`.
  - **`docs/adr/0026-screens-take-actions-and-emit-events.md`** (format of `docs/adr/0000-template.md`) recording:
    - route/screen split
    - sealed `XAction`/`XEvent`
    - Drop vs Keep and why both stay
    - the snackbar-on-rotation difference
    - WebView bridges exempt
    - sync lookups as properties
    - forwarding use cases removed
  - **`docs/adr/0001-feature-first-clean-architecture.md`**: one consequence line saying forwarding use cases were removed (ADR 0026).
  - **`AGENTS.md`**: delete "## Open work" and its two lines. Under "Writing code" add one line: "Screens are `XRoute` + `XScreen(state, onAction)`; input is `onAction(XAction)`, one-off effects are `XEvent`; tests use `MainDispatcherRule`." The file must stay under 100 lines.

  Run `python3 scripts/comment-check.py` on the touched Kotlin files and G1. **Commit** `docs(026): constitution 2.1.0, ADR 0026, AGENTS.md`.

**Checkpoint**: the rule is enforced; constitution 2.1.0.

---

## Phase 5: User Story 1, nothing changes for the person (P1, verification)

**Goal**: prove US1 on the finished branch. It is verified last because every earlier phase changes code.
**Independent test**: quickstart G4 and G5 pass.

- [ ] T044 [US1] Run quickstart G3 and record the results in the "Results" table: no baseline, `setMain` only in the rule, 49 use cases, no `fun consume`, `onAction` missing only in PageTextSize and EntryPoint.
- [ ] T045 [US1] Run quickstart G4 (behaviour walk on the 16 KB AVD, release build) and record each row as pass or fail. Repeat the Browser, Tabs, History, Settings and Fire rows on `TA016_API24` with a debug build.
- [ ] T046 [US1] Run quickstart G5:
  - Upgrade over the seeded `0372d0f` debug install from T002 and record every store intact.
  - Release APK bytes vs T001 (±2 %).
  - 10 interleaved cold starts vs `0372d0f` (±5 %).
  - Compose report: 0 non-skippable.
- [ ] T047 [US1] Run the full unit suite and the instrumented suite on `TA016_API36_16K`. Compare test class and method names with T001: removed use-case tests are only those listed in T007–T037, and every other test is present.

---

## Phase 6: Polish and delivery

- [ ] T048 Fill the "Use-case test disposition" table below: each removed test, its assertions, and where each one lands (deleted as pass-through / covered by `<test>` / moved to `<test>`).
- [ ] T049 Push the branch and open PR `026-viewmodel-action-event` → `main`. The description has:
  - one line per commit
  - Baseline vs Results
  - Rule proof
  - the one accepted behaviour difference (research R4)
  - the attribution footer

  Merge only with CI 6/6 (G6).
- [ ] T050 After merge, update the memory note `spec-025-architecture-cleanup.md` (Spec 026 done) and add a Spec 026 note to `MEMORY.md`.

---

## Dependencies & Execution Order

- **Setup (T001–T002)**: first, on the untouched tip.
- **Foundational (T003–T006)**: blocks every feature commit. T004 needs T003; T005 needs T004.
- **Phase 3 features**: run in the listed order, because core use cases are deleted when their last caller moves (T037 needs T010, T016, T019, T022, T025, T031, T033, T034, T035).
  - Inside a feature: US3 task → US2 holder/screen task → US2 tests → commit.
  - privacy before browser, because `ObserveFireproofSites` stays until T037.
  - settings before browser, because `QuickSettingsHost` is embedded in the browser screen.
- **US4 (T040–T043)**: after T039. T040 would fail on any holder not yet moved.
- **US1 (T044–T047)**: after T043.
- **Polish (T048–T050)**: T048 any time after T037; T049 after T047; T050 after merge.

### Story completion order

Foundational (US5) → US2 + US3 (per feature) → US4 → US1 verification.

## Parallel Opportunities

- T004 can be split across files: each test file is independent.
- T033 and T034 touch different files (home vs hub/suggestions/protection) and can run in parallel before T035.
- Feature phases are sequential by design (one green commit each), but the analysis part of each US3 task (checking which repository test covers an assertion) can be done ahead for all features in parallel.

### Parallel example (browser)

```text
Task: "T033 [US2] home + invite actions in F/browser/presentation/home/"
Task: "T034 [US2] hub, suggestions, protection actions in F/browser/presentation/{browser,suggestions,browser/protection}/"
```

## Implementation Strategy

- **MVP**: Foundational plus the first feature (sitepermission, T003–T009). This proves the pattern end to end (rule, helper, action, route, tests, baseline line removed) on the smallest screen.
- **Incremental**: one feature commit at a time, each green and mergeable; the ktlint baseline shrinks with each.
- **Close**: the check and governance (US4), then the verification gates (US1) and the PR.

---

## Baseline (T001)

| Measure | Value |
|---|---|
| Unit tests | |
| Instrumented tests (`TA016_API36_16K`) | |
| Release APK bytes | |
| Cold start median (ms), 10 interleaved | |
| Compose restartable / non-skippable | |
| ktlint baseline entries | 23 (expected) |
| Test files calling `Dispatchers.setMain` | 22 (expected) |
| Use cases | 82 (expected) |

## Results (T044–T047)

| Measure | Value | Pass |
|---|---|---|

## Rule proof (T042)

| Rule | Scratch violation | Failed with file named |
|---|---|---|

## Use-case test disposition (T048)

| Removed test / case | Asserted | Disposition |
|---|---|---|
