# Data Model: ViewModel Action/Event

No stored data changes: there are no Room, DataStore or asset changes and no migration (FR-002). The "entities" here are the code shapes this spec introduces, with an inventory of every holder taken on `0372d0f`. Action case names are indicative. tasks.md fixes them per feature.

## Shapes

| Shape | Lives in | Rule |
|---|---|---|
| `XAction` | `feature/<f>/presentation/<screen>/XAction.kt` | `sealed interface`. Cases are `data object` / `data class` with `val`s only. One per holder with input. `BrowserAction` nests `Page`, `AddressBar`, `Home`, `Find`, `Download`, `Tab` |
| `XEvent` | `…/<screen>/XEvent.kt` (an existing event type is renamed or wrapped) | `sealed interface`. One per holder with effects |
| `onAction` | the holder | `fun onAction(action: XAction)`, an exhaustive `when` over private functions; the only public/internal function |
| `events` | the holder | `val events: Flow<XEvent>`, built from a Drop `MutableSharedFlow`, a Keep `Channel`, or both merged (research R4) |
| `XRoute` | `…/<screen>/XScreen.kt` (same file as the screen) | Obtains holder(s) as default parameters, collects state once, runs `EventEffect`, calls `XScreen` |
| `XScreen` | same file | `(state, onAction, …plain callbacks, modifier)`; never a holder |
| `EventEffect` | `core/presentation/util/EventEffect.kt` | `LaunchedEffect(events) { events.collect { latestHandler(it) } }` |
| `MainDispatcherRule` | `T/core/testing/MainDispatcherRule.kt` | JUnit 4 `TestWatcher`; `setMain` on start, `resetMain` on finish |
| Lookup properties | holder | `val images: TabImages`, `val desktopMode: DesktopModeLookup`: `fun interface`s for synchronous queries (research R3) |

## Holders

Public functions are counted on `0372d0f` (data from the inventory). "→ Action" means each function becomes one case.

| Holder (feature) | Public fns | Action type | Event type | Notes |
|---|---|---|---|---|
| `BrowserViewModel` (browser) | 33 | `BrowserAction` (nested) | `BrowserEvent`: `TabLimit(TabsErrorEvent)`, `Bookmark(BookmarkSnackbarEvent)`, `Download(DownloadSnackbarEvent)`, `LoadUrl(url)`, `SetDesktopMode(enabled)` | 12 WebView callbacks become `Page.*`. `isDesktopMode` becomes `desktopMode` lookup. Three `consume…` functions and their `UiState` fields go. Extra state flows (`loadProgress`, `activeTab`, `tabCount`, `findInPage`) stay |
| `BookmarksViewModel` (bookmarks) | 27 | `BookmarksAction` | `BookmarksEvent`: `OpenUrl(url)`, `Error(BookmarksErrorEvent)` | `consumeOpenUrl` and `consumeErrorEvent` go, along with `openUrl` and `errorEvent` in `UiState` |
| `SettingsViewModel` (settings) | 21 | `SettingsAction` (incl. `Started`, `Resumed`) | `SettingsEvent` (existing) | Shared by `SettingsRoute` and `QuickSettingsHost`. `SettingsDialogHost` takes `state` + `onAction` |
| `TabsViewModel` (tabs) | 14 | `TabsAction` | `TabsEvent`: `ShowBrowser` (Drop), `Error(TabsErrorEvent)` (Keep) | `faviconFor` and `screenshotFor` become `images` lookup; `consumeErrorEvent` goes. Injects `TabRepository` / `IncognitoTabRepository` for the 5 removed use cases |
| `HistoryViewModel` (history) | 12 | `HistoryAction` | `HistoryEvent`: `OpenUrl(url)` (Keep), `Snackbar(HistoryErrorEvent)` (Drop) | `consumeOpenUrl` goes. `faviconCache` property stays |
| `DownloadsViewModel` (downloads) | 9 + 1 internal | `DownloadsAction` | `DownloadsEvent` (existing) | `emitEvent` becomes private |
| `SitePermissionsViewModel` (sitepermission) | 7 | `SitePermissionsAction` | none | |
| `OnboardingViewModel` (onboarding) | 6 | `OnboardingAction` | `OnboardingEvent.Leave` (was `leaveFlow: Unit`) | `onBack(): Boolean` becomes a route decision on `isFirstSlide` |
| `AddressSuggestionsViewModel` (browser) | 4 | `AddressSuggestionsAction`: `TextChanged`, `FocusChanged` | none | `onEditingStarted` and `onEditingEnded` become private |
| `ProtectionExceptionsViewModel` (privacy) | 4 | `ProtectionExceptionsAction` | none | |
| `FireproofSitesViewModel` (privacy) | 4 | `FireproofSitesAction` | none | |
| `HomeViewModel` (browser) | 2 | `HomeAction`: `Resumed`, `TabShortcut(tabId)` | none | |
| `DefaultBrowserInviteViewModel` (browser) | 2 | `DefaultBrowserInviteAction`: `Resumed`, `Dismiss` | none | |
| `FireViewModel` (privacy) | 2 | `FireAction`: `Fire`, `Acknowledge` | none | `FireState` stays the coordinator's state |
| `ProtectionViewModel` (browser) | 2 | `ProtectionAction`: `ProtectionSwitched(on)`, `FireproofSwitched(on)` | `ProtectionEvent.Reload` (was `reloadEvents: Unit`) | `hooks` property stays |
| `HubViewModel` (browser) | 1 | `HubAction.NewIncognitoTab` | `HubEvent` (existing) | |
| `PageTextSizeViewModel` (browser) | 0 | none | none | No input |
| `EntryPointViewModel` (entrypoint) | 0 | none | `EntryEvent` (existing, Keep) | No input; `browserHooks` stays (ADR 0011) |

These are exempt from `onAction` as WebView bridges (research R2) and unchanged: `PopupTabs`, `SitePermissionGate`, `LongPressActions`.

## Effects

Every one-off effect in a covered holder, with the kind it keeps (FR-009).

| Holder | Effect today | Mechanism today | Kind | Becomes |
|---|---|---|---|---|
| Onboarding | `leaveFlow` | SharedFlow(0, 1, DROP_OLDEST), `tryEmit` | Drop | `OnboardingEvent.Leave` |
| Tabs | `popBackEvent` | SharedFlow(0, 1, SUSPEND), `tryEmit` | Drop | `TabsEvent.ShowBrowser` |
| Tabs | `TabsUiState.errorEvent` + `consumeErrorEvent` | state field | Keep | `TabsEvent.Error(e)` |
| Downloads | `events` | SharedFlow(0, 1, DROP_OLDEST), `tryEmit` | Drop | `DownloadsEvent` |
| Bookmarks | `BookmarksUiState.errorEvent` + `consumeErrorEvent` | state field | Keep | `BookmarksEvent.Error(e)` |
| Bookmarks | `BookmarksUiState.openUrl` + `consumeOpenUrl` | state field | Keep | `BookmarksEvent.OpenUrl(url)` |
| EntryPoint | `events` | Channel(1, DROP_OLDEST), `trySend` | Keep | unchanged |
| History | `historySnackbarEvent` | SharedFlow(0, 1, DROP_OLDEST), `tryEmit` | Drop | `HistoryEvent.Snackbar(e)` |
| History | `HistoryUiState.openUrl` + `consumeOpenUrl` | state field | Keep | `HistoryEvent.OpenUrl(url)` |
| Browser | `BrowserUiState.tabsEvent` + `consumeTabsEvent` | state field | Keep | `BrowserEvent.TabLimit(e)` |
| Browser | `BrowserUiState.bookmarkSnackbarEvent` + consume | state field | Keep | `BrowserEvent.Bookmark(e)` |
| Browser | `BrowserUiState.downloadSnackbarEvent` + consume | state field | Keep | `BrowserEvent.Download(e)` |
| Browser | return of `onAddressBarSubmit` / `onHomeSubmit` → `loadUrl` lambda | synchronous call | Keep (screen is collecting) | `BrowserEvent.LoadUrl(url)` |
| Browser | return of `onDesktopModeToggled` | synchronous call | Keep | `BrowserEvent.SetDesktopMode(b)` |
| Hub | `events` | SharedFlow(0, 1, DROP_OLDEST), `tryEmit` | Drop | `HubEvent` |
| Settings | `events` | SharedFlow(0, 1, DROP_OLDEST), `tryEmit` | Drop | `SettingsEvent` |
| Protection | `reloadEvents` | SharedFlow(0, 1, DROP_OLDEST), `tryEmit` | Drop | `ProtectionEvent.Reload` |

Keep in the target means `Channel<XEvent>(Channel.BUFFERED)`, except for EntryPoint, which keeps its `capacity = 1, DROP_OLDEST`. Drop keeps each stream's exact `MutableSharedFlow` arguments; Tabs keeps its default SUSPEND overflow.

Unchanged and out of scope: `TabsErrorEvent` (core, used by tabs and browser), the unused `BookmarksErrorEvent.EmptyTitle/EmptyUrl/EmptyFolderName` cases, and the separate tab-limit event types in Hub, History, PopupTabs and LongPressActions. Merging them would change messages.

## Use cases removed

The 33 in `specs/025-architecture-cleanup/tasks.md` ("Spec 026 — forwarding use cases"), all confirmed pure forwards (research R1). Each holder's constructor swaps them for these contracts:

| Holder / caller | Removed use cases | Injects instead |
|---|---|---|
| `TabsViewModel` | CreateTab, CreateIncognitoTab, SwitchActiveTab, CloseIncognitoTab, CloseAllIncognitoTabs | `TabRepository`, `IncognitoTabRepository` |
| `BrowserViewModel` | BuildSearchUrl, ObserveTabs, CreateTab | `SearchEngineRepository`, `TabRepository` |
| `HubViewModel`, `PopupTabs` | CreateIncognitoTab (+ CreateTab, CloseIncognitoTab in PopupTabs) | `TabRepository`, `IncognitoTabRepository` |
| `HistoryViewModel` | ObserveHistoryEntries, DeleteHistoryEntry, ClearAllHistory, CreateTab | `HistoryRepository`, `TabRepository` |
| `BookmarksViewModel` | CountFolderDescendants, DeleteBookmark, DeleteFolder, MoveBookmark, MoveFolder, ObserveAllFolders | `BookmarkRepository` |
| `SettingsViewModel` | GetAppLanguage, IsDefaultBrowser, ObserveUserSettings, SetSearchEngine, SetThemeMode, SetDynamicColorEnabled, SetTextSize | `AppLanguageController`, `DefaultBrowserController`, `SettingsRepository` |
| `OnboardingViewModel` | ObserveUserSettings, GetAppLanguage, SetThemeMode, SetSearchEngine, SetOnboardingCompleted | `SettingsRepository`, `AppLanguageController` |
| `HomeViewModel` | ObserveUserSettings, SwitchActiveTab | `SettingsRepository`, `TabRepository` |
| `PageTextSizeViewModel` | ObserveUserSettings | `SettingsRepository` |
| `DefaultBrowserInviteViewModel` | ObserveDefaultBrowserInvite, IsDefaultBrowser, DismissDefaultBrowserInvite | `DefaultBrowserInviteRepository`, `DefaultBrowserController` |
| `EntryPointViewModel` | ObserveUserSettings, ObserveTabs | `SettingsRepository`, `TabRepository` |
| `DownloadsViewModel` | ObserveDownloads | `DownloadsRepository` |
| `FireproofSitesViewModel`, `ProtectionViewModel` | ObserveFireproofSites | `FireproofSiteRepository` |
| `ProtectionExceptionsViewModel` | ObserveProtectionExceptions | `ProtectionExceptionRepository` |
| `SitePermissionsViewModel` | ObserveSitePermissions, SetSitePermission | `SitePermissionRepository` |
| `MainActivity` | GetUserSettings, ObserveUserSettings, RecordAppLaunch | `SettingsRepository`, `DefaultBrowserInviteRepository` |
| `OpenIncomingRequestUseCase` | BuildSearchUrl | `SearchEngineRepository` |
| `ObserveHasActiveDownloadsUseCase` | ObserveDownloads | `DownloadsRepository` |

## Invariants

- The database name, schema v5, DataStore keys, manifest, routes (`*_ROUTE` strings) and `SavedStateHandle` keys are unchanged.
- A holder's constructor never gains a DAO, data source, entity or `*RepositoryImpl`; `PlacementTest.view models never reach past the domain` still passes.
- Every `UiState` stays an immutable `data class`. Removing the one-off fields only shrinks it.
