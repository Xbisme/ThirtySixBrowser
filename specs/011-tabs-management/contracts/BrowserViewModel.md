# Contract: `BrowserViewModel` (Spec 011 delta)

**Layer**: `presentation/browser/`
**File**: `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModel.kt`

This document describes ONLY the deltas Spec 011 introduces. The Specs 007/008/009/010 surface (load lifecycle, navigation state, address-bar text, Stop, search-engine query path) is unchanged.

## Constructor changes

Before (Spec 010):

```kotlin
class BrowserViewModel @Inject constructor(
    @param:Named("default_home_url") private val defaultHomeUrl: String,
    private val buildSearchUrl: BuildSearchUrlUseCase,
) : ViewModel()
```

After (Spec 011):

```kotlin
class BrowserViewModel @Inject constructor(
    @param:Named("default_home_url") private val defaultHomeUrl: String,
    private val buildSearchUrl: BuildSearchUrlUseCase,
    observeActiveTab: ObserveActiveTabUseCase,
    observeTabs: ObserveTabsUseCase,
    private val updateActiveTabUrlAndTitle: UpdateActiveTabUrlAndTitleUseCase,
    private val createTab: CreateTabUseCase,
) : ViewModel()
```

4 new dependencies, all use cases (R4 / R7). `observeTabs` powers the `tabCount: StateFlow<Int>` exposed for the BottomAppBar's `BadgedBox` (T036) — derived as `observeTabs().map { it.size }.stateIn(viewModelScope, WhileSubscribed(5_000), 1)` (initial value `1` because the auto-create-on-empty rule from R5 guarantees ≥ 1 tab once the Flow has emitted; using `1` as initial avoids an empty-badge flash on cold start).

## State seeding from active tab

The `_uiState` initial `currentUrl` field MUST source from the active tab when present. Pattern:

```kotlin
private val _uiState: MutableStateFlow<BrowserUiState> = MutableStateFlow(
    BrowserUiState(
        currentUrl = defaultHomeUrl,        // pre-Flow seed; replaced by Flow emission below
        loadingState = LoadingState.Idle,
    ),
)

init {
    observeActiveTab()
        .onEach { tab ->
            tab?.let {
                _uiState.update { state -> state.copy(currentUrl = it.url) }
            }
        }
        .launchIn(viewModelScope)
}
```

## New methods

```kotlin
/**
 * Spec 011 — long-press on the 5th BottomAppBar button (Q2 clarification).
 * Creates a fresh home tab; on cap-reached, surfaces a one-shot transient
 * event for the snackbar.
 */
fun onLongPressNewTab() {
    viewModelScope.launch {
        val result = createTab(defaultHomeUrl)
        if (result is Result.Error && result.throwable is MaxTabsReachedException) {
            _uiState.update { it.copy(tabsEvent = TabsErrorEvent.MaxTabsReached) }
        }
    }
}

fun consumeTabsEvent() {
    _uiState.update { it.copy(tabsEvent = null) }
}
```

## Modified methods

`onUrlChanged(url)` — extended to write through to `TabRepository`:

```kotlin
fun onUrlChanged(url: String) {
    _uiState.update { it.copy(currentUrl = url) }
    val activeId = _activeTab.value?.id
    if (activeId != null) {
        viewModelScope.launch {
            updateActiveTabUrlAndTitle(activeId, url, _currentTitle.value)
        }
    }
}
```

`onLoadFinished(url)` — extended to write through the post-load title:

```kotlin
fun onLoadFinished(url: String) {
    _uiState.update { current ->
        // existing Spec 007 idempotent update (unchanged)
    }
    val activeId = _activeTab.value?.id
    if (activeId != null) {
        viewModelScope.launch {
            updateActiveTabUrlAndTitle(activeId, url, _currentTitle.value)
        }
    }
}
```

## New method — `onTitleReceived(title: String)`

```kotlin
/**
 * Spec 011 — invoked by the WebView host (via `BrowserNavigationCallbacks.onTitleChange`,
 * extending Spec 008's callbacks bundle) on every `WebChromeClient.onReceivedTitle`.
 * Updates the in-memory current-title cache and writes through to the active tab.
 */
fun onTitleReceived(title: String) {
    _currentTitle.value = title
    val activeId = _activeTab.value?.id
    if (activeId != null) {
        viewModelScope.launch {
            updateActiveTabUrlAndTitle(activeId, _uiState.value.currentUrl, title)
        }
    }
}
```

## Source of truth for title

- `_activeTab: StateFlow<Tab?>` — internal, populated by `observeActiveTab().stateIn(...)` in `init {}`.
- `_currentTitle: MutableStateFlow<String>` — internal, default `""`. Reset to `""` whenever `_activeTab.value?.id` changes (via `onEach { _currentTitle.value = "" }` collector in `init {}`) so a stale title from a previous tab does not leak into the next tab's first write-through. Updated by `onTitleReceived(title)`.
- The title write-through fires from THREE callsites: `onUrlChanged` (URL committed but title not yet known — uses cached title which may be stale or empty; corrected by the next `onTitleReceived`), `onLoadFinished` (URL + title both stable), and `onTitleReceived` (immediate title write). All three are idempotent; double-writes are absorbed by Room.

## Behavior preserved (NO changes)

- `onAddressBarSubmit(loadUrl)` — Spec 009/010 contract unchanged. Returns `Boolean` synchronously. Query branch still wraps in `viewModelScope.launch { val url = buildSearchUrl(...); loadUrl(url) }`.
- `onCanGoBackChanged` / `onCanGoForwardChanged` / `onLoadStopped` — Spec 008 contract unchanged.
- Hilt URL injection (`@Named("default_home_url")`) — Spec 007 contract unchanged. The constant continues to drive the fallback URL when no active tab exists yet (e.g., during the brief moment between `init {}` registration and the first `Flow` emission).

## Test surface (additions to existing `BrowserViewModelTest`)

| Test name | Scenario |
|-----------|----------|
| init_seedsCurrentUrlFromActiveTab | FakeObserveActiveTab emits Tab(url="https://example.org") → `_uiState.value.currentUrl == "https://example.org"` |
| onUrlChanged_writesThroughToActiveTab | onUrlChanged("https://newurl.com") → fake `updateActiveTabUrlAndTitle` invoked with that URL |
| onLoadFinished_writesThroughTitle | onLoadFinished after title change → fake `updateActiveTabUrlAndTitle` invoked with new title |
| onLongPressNewTab_underCap_succeeds | fake `createTab` returns Success → no errorEvent in state |
| onLongPressNewTab_atCap_setsErrorEvent | fake `createTab` returns Failure(MaxTabsReached) → `tabsEvent == MaxTabsReached` |
| consumeTabsEvent_clearsField | After tabsEvent set, consumeTabsEvent() → `tabsEvent == null` |

Total additions: **6 tests** (counted into the 25 new unit-test target).
