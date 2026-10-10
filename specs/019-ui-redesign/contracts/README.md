# Contracts: UI Redesign (Spec 019)

The app exposes no external API; these are the internal seams other code and tests rely on. Signatures are normative; bodies are not.

## C1 — `BrowserViewModel` additions

```kotlin
/** Page-load progress, 0f..1f. Deliberately NOT part of uiState (R8, Constitution §V rule 3). */
val loadProgress: StateFlow<Float>

fun onHomePressed()            // sets homeOverlay on a loaded page; on a Home tab, no-op
fun onBackFromHomeOverlay()    // clears homeOverlay
/** Home search bar submit: same path as the address bar (AddressBarInputClassifier → URL or
 *  BuildSearchUrlUseCase), loaded in the active tab; sets cameFromHome. Blank input is a no-op. */
fun onHomeSubmit(input: String)
/** Shortcut / recent-row tap. Kind Tab → activate tabId (no load, cameFromHome untouched);
 *  Saved or Recent → load url in the active tab and set cameFromHome. */
fun onHomeShortcut(shortcut: HomeShortcut)
fun onHomeRecent(url: String)  // load url in the active tab, set cameFromHome
/** Called when the WebView cannot go back. Returns true if it moved the tab to Home. */
fun onBackWithoutHistory(): Boolean
```

**Guarantees**
- `onProgressChanged(n)` emits on `loadProgress` and **never** emits a new `uiState`.
- `uiState.isHomeContent` is true exactly when the active tab is at `HOME_PAGE_URL` or `homeOverlay` is set.
- `onBackWithoutHistory()` returns true only when `cameFromHome` is set and the tab is not incognito.
- `onHomeSubmit` produces byte-identical URLs to `onAddressBarSubmit` for the same input (FR-012).
- `hasActiveDownloads: StateFlow<Boolean>` is built with `stateIn(viewModelScope, SharingStarted.WhileSubscribed(…), false)` and collected **only inside `HubSheet`**, so the platform download status is polled only while the hub is open (analysis U2).

## C1a — Reserved-URL tolerance

Every consumer of `Tab.url` MUST treat `UrlConstants.HOME_PAGE_URL` as "no web page": skip it in `CookieJarSnapshotManager` origin enumeration, favicon/host extraction, screenshot capture, history recording, external-intent handling and the address well (analysis U3).

## C2 — `ChromeScrollState`

```kotlin
@Stable
class ChromeScrollState(maxOffsetPx: Float) {
    val offsetPx: Float            // snapshot-backed; read ONLY inside offset {} / graphicsLayer {}
    fun onScrollDelta(dy: Float)   // dy > 0 scrolls content down (hides bars)
    fun show()
}
@Composable fun rememberChromeScrollState(maxOffsetPx: Float): ChromeScrollState
```

**Guarantees**: `offsetPx` ∈ `[0, maxOffsetPx]`; `show()` sets 0; no allocation per delta.

## C3 — `BrowserWebView` callback addition

```kotlin
data class BrowserNavigationCallbacks(
    /* existing fields */
    val onScrollDelta: (dy: Float) -> Unit,   // new; from View.setOnScrollChangeListener
)
```

## C4 — Home

```kotlin
class ObserveHomeContentUseCase @Inject constructor(/* tabs, bookmarks, history, settings */) {
    operator fun invoke(activeTabId: Long?): Flow<HomeContent>   // shortcuts ≤ 8, recent ≤ 5
}
@HiltViewModel class HomeViewModel : ViewModel {
    val uiState: StateFlow<HomeUiState>
    fun onResume()                                     // recompute greeting
}
@Composable fun HomePage(
    state: HomeUiState,
    onSearchSubmit: (String) -> Unit,
    onShortcut: (HomeShortcut) -> Unit,
    onRecent: (HistoryEntry) -> Unit,
    onSeeAllHistory: () -> Unit,
    onNewIncognitoTab: () -> Unit,
    modifier: Modifier = Modifier,
)
```

`HomeContent`, `HomeUiState`, `HomeShortcut`, `GreetingPeriod`: data-model.md §3.

## C5 — Repository / DAO additions

```kotlin
// BookmarkDao / BookmarkRepository
fun observeRecent(limit: Int): Flow<List<…>>   // created_at DESC
// HistoryDao / HistoryRepository
fun observeRecent(limit: Int): Flow<List<…>>   // visited_at DESC, uses existing index
```

## C6 — Composable phase contract (applies to every component below)

| Component | Frame-rate input | Type | Read only inside |
|---|---|---|---|
| `PageLoadProgressLine` | load progress | `() -> Float` | `drawBehind` |
| `DownloadProgressBar` | download fraction | `() -> Float` | `drawBehind` |
| `BrowserTopBar`, `BrowserBottomBar` | chrome offset | `ChromeScrollState` | `Modifier.offset {}` |
| WebView host | top-bar visible px | `ChromeScrollState` | `graphicsLayer { translationY }` |
| `HomeHero` | list scroll | `LazyListState` | `graphicsLayer { alpha; scaleX/Y }` |
| `TabCard` | swipe offset | `Animatable<Float>` | `graphicsLayer { translationX; alpha }` |
| `HubSheet` | drag offset | owned by `ModalBottomSheet` | (component-internal, layout phase) |

A composable in this table MUST NOT take the moving value as a plain `Float`/`Dp` parameter, and its callers MUST NOT read it with `by` delegation.

## C7 — Theme access

```kotlin
object ThirtySixTheme {
    val colors: ThirtySixColors @Composable @ReadOnlyComposable get()
    val shapes: ThirtySixShapes @Composable @ReadOnlyComposable get()   // adds sheet, full
}
```

Feature code reads M3 roles from `MaterialTheme.colorScheme` and extended tokens from `ThirtySixTheme.colors`; never a `Color(0x…)` literal (Constitution §III).
