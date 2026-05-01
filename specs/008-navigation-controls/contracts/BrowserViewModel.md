# Contract: `BrowserViewModel` public surface (Spec 008 delta)

> Documents the new public methods + `BrowserUiState` field additions that `BrowserViewModel` exposes to `BrowserScreen` after Spec 008 ships. This is an internal-to-the-app contract — `BrowserViewModel` is not a public API to external consumers, but the surface is treated as a contract because Spec 011 (`tabs-management`) and Spec 016 (`settings-screen`) will both depend on it.

## Existing surface (from Spec 007)

```kotlin
@HiltViewModel
class BrowserViewModel @Inject constructor(
    @param:Named("default_home_url") private val defaultHomeUrl: String,
    // ... DispatcherProvider, etc. ...
) : ViewModel() {
    val uiState: StateFlow<BrowserUiState>
    fun onUrlChanged(url: String)                 // from BrowserWebView callback
    fun onLoadingStateChanged(state: LoadingState)
    fun onPageStarted(url: String)
    fun onPageFinished(url: String)
}
```

## New surface (Spec 008 delta)

### State observation methods (called from `BrowserWebViewCallbacks`)

```kotlin
/**
 * Called by BrowserWebView when WebViewClient.doUpdateVisitedHistory fires.
 * Updates the canGoBack flag in BrowserUiState.
 *
 * Source of truth: WebView.canGoBack() at the moment of the platform callback.
 * Always called paired with onCanGoForwardChanged (same callback site).
 */
fun onCanGoBackChanged(canGoBack: Boolean)

/**
 * Called by BrowserWebView when WebViewClient.doUpdateVisitedHistory fires.
 * Updates the canGoForward flag in BrowserUiState.
 */
fun onCanGoForwardChanged(canGoForward: Boolean)
```

### User-action methods (called from `NavigationBottomBar` clicks + `PredictiveBackHandler`)

```kotlin
/**
 * Navigate the WebView one step backward in session history.
 *
 * Pre-condition: uiState.value.canGoBack == true.
 * Calling when canGoBack is false is a no-op (defensive guard, not an error).
 *
 * Side effects:
 *   - Triggers WebView.goBack() via a side-channel (see "WebView side-channel" below).
 *   - Eventually triggers WebViewClient.doUpdateVisitedHistory which fires
 *     onCanGoBackChanged / onCanGoForwardChanged → state recomputes.
 */
fun onBackClick()

/**
 * Navigate the WebView one step forward in session history.
 *
 * Pre-condition: uiState.value.canGoForward == true.
 * Calling when canGoForward is false is a no-op.
 */
fun onForwardClick()

/**
 * Single combined Reload/Stop action.
 *
 * If uiState.value.loadingState is LoadingState.Loading → calls WebView.stopLoading().
 * Otherwise (Idle | Loaded | Failed) → calls WebView.reload().
 *
 * The decision is made at click time (reads current uiState), so there is no
 * possibility of dispatching the wrong action due to stale UI snapshot.
 */
fun onReloadOrStopClick()

/**
 * Load the default home URL (AppDefaults.HOME_URL = https://www.google.com/) in
 * the current WebView. Adds a normal entry to session history (Back returns to
 * the prior page).
 *
 * If the device is offline, the load fails with the same UI as any other offline
 * navigation (handled by Spec 007's LoadingState.Failed(NetworkUnavailable)
 * pipeline).
 */
fun onHomeClick()
```

## State delta — `BrowserUiState`

```kotlin
data class BrowserUiState(
    val currentUrl: String,
    val loadingState: LoadingState,
    val canGoBack: Boolean = false,        // NEW (default false)
    val canGoForward: Boolean = false,     // NEW (default false)
)
```

See [data-model.md](../data-model.md) for full field semantics and invariants.

## WebView side-channel

The four user-action methods need to invoke `WebView` instance methods (`goBack`, `goForward`, `reload`, `stopLoading`, `loadUrl`) — but `WebView` is owned by the Composable layer (`BrowserWebView`), not the ViewModel.

**Decision** (per [research.md](../research.md) R7-adjacent): Use a Compose-side imperative handle exposed as a `WebViewActionsHandle` (data class of lambdas) returned from `BrowserWebView`'s `factory { }` block via a state holder. The handle is captured into a `remember { }` and threaded into `BrowserViewModel` via dedicated setter methods OR (cleaner) the bottom-bar's click lambdas in `BrowserScreen` call the handle directly:

```kotlin
@Composable
fun BrowserScreen(viewModel: BrowserViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val webViewActions = remember { WebViewActionsHandle() }

    Scaffold(
        bottomBar = {
            NavigationBottomBar(
                canGoBack = uiState.canGoBack,
                canGoForward = uiState.canGoForward,
                isLoading = uiState.loadingState is LoadingState.Loading,
                onBack = webViewActions::goBack,
                onForward = webViewActions::goForward,
                onReloadOrStop = {
                    if (uiState.loadingState is LoadingState.Loading)
                        webViewActions.stopLoading()
                    else
                        webViewActions.reload()
                },
                onHome = webViewActions::loadHome,
            )
        }
    ) { padding ->
        BrowserWebView(
            // ...
            actionsHandle = webViewActions,
            modifier = Modifier.padding(padding),
        )
    }

    PredictiveBackHandler(enabled = uiState.canGoBack) { progress ->
        try {
            progress.collect { /* preview rendered by system */ }
            webViewActions.goBack()
        } catch (e: CancellationException) { throw e }
    }
}
```

`WebViewActionsHandle` lives in the Composable scope; `BrowserViewModel` does not need a reference to `WebView` itself. The ViewModel still owns `BrowserUiState` and exposes the `onCanGoBackChanged` / etc. callbacks for state mutation.

This avoids the anti-pattern of leaking `WebView` (an Android UI primitive) into a ViewModel. The "user-action methods" listed under "User-action methods" above are therefore **logical actions on the bottom bar**; their concrete imperative WebView calls live in the Composable layer.

**Refined contract** (replacing the simpler version above):

| Surface | Owner | Responsibility |
|---------|-------|----------------|
| `BrowserViewModel.uiState` | ViewModel | Holds `BrowserUiState` truth |
| `BrowserViewModel.onCanGoBackChanged(...)` etc. | ViewModel | Mutates `BrowserUiState` from WebView events |
| `BrowserViewModel.homeUrl` (getter) | ViewModel | Provides `AppDefaults.HOME_URL` (already injected as `defaultHomeUrl`) |
| `WebViewActionsHandle` | Composable (`BrowserScreen`) | Imperative handle to `WebView` actions |
| `NavigationBottomBar` click lambdas | Composable | Read `uiState` + dispatch via `WebViewActionsHandle` |
| `PredictiveBackHandler` | Composable | Read `uiState.canGoBack` + dispatch via `WebViewActionsHandle.goBack()` |

This refined contract is what tasks.md will implement. The "User-action methods" section above is retained as the conceptual surface but in code, those methods are realized as `WebViewActionsHandle` lambdas plus inline ternaries in click handlers — NOT as ViewModel methods. The ViewModel only sees state-update events flowing in from the WebView; click events route directly to the Composable-side handle.

## Backward compatibility

- The two new `BrowserUiState` fields default to `false`, so existing tests that construct `BrowserUiState(currentUrl, loadingState)` continue to compile (kotlin default-arg behavior).
- The two new `BrowserWebViewCallbacks` fields are required (no default), but every call site in production code is the `BrowserScreen` composable, which is updated atomically in this spec.
- The Spec 007 Hilt test override pattern (`@UninstallModules(UrlConfigModule::class)` + nested `FakeUrlConfigModule`) continues to work — the only change to `UrlConfigModule` is the URL string value provided.

## Threading

- `onCanGoBackChanged` / `onCanGoForwardChanged` are called from the WebView main-thread callback chain. They synchronously call `MutableStateFlow.update { copy(...) }`, which is thread-safe per `StateFlow` contract.
- `WebViewActionsHandle` lambdas are invoked from Compose click handlers (main thread); they call WebView instance methods which require main thread per platform contract.
- `PredictiveBackHandler` runs the `onBack` lambda on the Compose composition's coroutine context (main dispatcher).

No new threading concerns over Spec 007 baseline.
