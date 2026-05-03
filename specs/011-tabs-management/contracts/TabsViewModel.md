# Contract: `TabsViewModel`

**Layer**: `presentation/tabs/`
**File**: `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabsViewModel.kt`

## ViewModel API

```kotlin
@HiltViewModel
class TabsViewModel @Inject constructor(
    observeTabs: ObserveTabsUseCase,
    private val createTab: CreateTabUseCase,
    private val switchActiveTab: SwitchActiveTabUseCase,
    private val closeTab: CloseTabUseCase,
    private val closeAllTabs: CloseAllTabsUseCase,
) : ViewModel() {

    val uiState: StateFlow<TabsUiState>
    val popBackEvent: SharedFlow<Unit>      // one-shot signal: the Composable should pop the switcher route

    fun onTabClick(tabId: Long)              // user tapped a card → switch + emit popBackEvent
    fun onCloseTab(tabId: Long)              // user tapped × on a card
    fun onNewTabClick()                       // user tapped the "new tab" card → if Success: emit popBackEvent
    fun onCloseAllRequested()                // user tapped "close all" → opens dialog
    fun onCloseAllConfirmed()                // user confirmed in dialog → wipe + emit popBackEvent
    fun onCloseAllDismissed()                // user dismissed dialog
    fun consumeErrorEvent()                  // caller acknowledges errorEvent shown
}
```

`popBackEvent` is a `MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)` exposed read-only as `SharedFlow<Unit>`. Emitted ONLY when the user takes an action that should return them to BrowserScreen (`onTabClick`, `onCloseAllConfirmed`, `onNewTabClick` on success). NOT emitted on activeTab changes that originate from elsewhere (e.g., `BrowserViewModel.onUrlChanged` write-through does not emit because it does not change `activeTabId`). Replaces an earlier `LaunchedEffect(activeTabId)` heuristic that incorrectly fired on initial composition.

## State derivation

`uiState` is built by combining the active-tab-bearing `Flow<List<Tab>>` from `ObserveTabsUseCase`:

```kotlin
val uiState: StateFlow<TabsUiState> = observeTabs()
    .map { tabs ->
        TabsUiState(
            tabs = tabs,
            activeTabId = tabs.maxByOrNull { it.lastActiveAt }?.id,
            isCloseAllDialogVisible = _uiState.value.isCloseAllDialogVisible,
            errorEvent = _uiState.value.errorEvent,
        )
    }
    .combine(_localState) { fromRoom, local ->
        fromRoom.copy(
            isCloseAllDialogVisible = local.isCloseAllDialogVisible,
            errorEvent = local.errorEvent,
        )
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TabsUiState.EMPTY)
```

(Implementation may simplify this — the contract is: `tabs` + `activeTabId` come from Room (Flow-driven); `isCloseAllDialogVisible` + `errorEvent` come from internal `MutableStateFlow` for UI-only transient state.)

## Action contracts

| Action | Behavior | Side effect |
|--------|----------|-------------|
| `onTabClick(tabId)` | `viewModelScope.launch { switchActiveTab(tabId) }` | Caller observes `activeTabId` change via Flow; navController.popBackStack() initiated by Composable's `LaunchedEffect(activeTabId)` watcher (avoids ViewModel knowing about NavController). |
| `onCloseTab(tabId)` | `viewModelScope.launch { closeTab(tabId) }` | None visible to user beyond the card disappearing from the grid (Flow re-emits). |
| `onNewTabClick()` | `viewModelScope.launch { createTab(homeUrl) }`. On `Result.Error(throwable = MaxTabsReachedException)` → `_uiState.update { it.copy(errorEvent = MaxTabsReached) }`. On success → emit `popBackEvent` (Composable collects → popBackStack). | Snackbar surface or popBackStack. |
| `onCloseAllRequested()` | `_uiState.update { it.copy(isCloseAllDialogVisible = true) }` | Dialog opens. |
| `onCloseAllConfirmed()` | `viewModelScope.launch { closeAllTabs() }` then `_uiState.update { it.copy(isCloseAllDialogVisible = false) }` | Single fresh home tab remains; navController popBackStack. |
| `onCloseAllDismissed()` | `_uiState.update { it.copy(isCloseAllDialogVisible = false) }` | Dialog closes. |
| `consumeErrorEvent()` | `_uiState.update { it.copy(errorEvent = null) }` | Cleared after the snackbar is displayed (Composable side calls this in `LaunchedEffect(errorEvent)`). |

## Test surface

| Test name | Scenario | File |
|-----------|----------|------|
| initialState_emptyRepo_emitsSeededHomeTab | FakeTabRepository emits one home tab → `TabsUiState.tabs.size == 1`, `activeTabId == it.id` | `TabsViewModelTest` |
| onTabClick_switchesActive | 3 tabs in repo, click tab id 2 → `activeTabId` becomes 2 | `TabsViewModelTest` |
| onCloseTab_removesFromList | 3 tabs, close tab id 2 → tabs.size == 2, neither contains id 2 | `TabsViewModelTest` |
| onCloseTab_lastTab_recreatesHome | 1 tab, close it → exactly 1 fresh home tab remains, becomes active | `TabsViewModelTest` |
| onNewTabClick_underCap_addsTab | createTab succeeds → tabs.size grows by 1 | `TabsViewModelTest` |
| onNewTabClick_atCap_emitsErrorEvent | repo returns Failure(MaxTabsReached) → `errorEvent == MaxTabsReached`, tabs unchanged | `TabsViewModelTest` |
| onCloseAllConfirmed_wipesAndSeeds | 3 tabs → confirm → exactly 1 fresh home tab remains | `TabsViewModelTest` |
| consumeErrorEvent_clearsField | After errorEvent set, consumeErrorEvent() → `errorEvent == null` | `TabsViewModelTest` |

Total for this file: **8 tests** (counted into the 25 new unit-test target).

## NavController coupling

`TabsViewModel` does NOT hold a `NavController` reference (constitution-aligned — ViewModels are platform-free). The Composable layer (`TabsScreen`) holds a `NavHostController` and collects the `popBackEvent: SharedFlow<Unit>` exposed by the ViewModel:

```kotlin
LaunchedEffect(Unit) {
    viewModel.popBackEvent.collect { navController.popBackStack(AppDestination.Browser.route, inclusive = false) }
}
```

This explicit one-shot event flow (instead of an `activeTabId`-watcher heuristic) avoids the initial-composition firing edge case AND the false-positive case where `BrowserViewModel.onUrlChanged` write-through changes `url`/`title` but not `lastActiveAt` (so `activeTabId` is stable and a stale watcher would not fire incorrectly — but the explicit-event design is safer regardless).
