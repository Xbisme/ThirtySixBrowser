# Contract: `BrowserViewModel` Query branch (Spec 010 MODIFY)

**Layer**: `presentation/browser/`
**File**: [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModel.kt](../../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModel.kt)

This contract documents the delta to `BrowserViewModel` from Spec 010. All other ViewModel methods (Spec 007 / 008 / 009) remain unchanged.

## Constructor delta

### Before (Spec 009)

```kotlin
@HiltViewModel
@Suppress("TooManyFunctions")
class BrowserViewModel @Inject constructor(
    @param:Named("default_home_url") private val defaultHomeUrl: String,
) : ViewModel() {
    // ...
}
```

### After (Spec 010)

```kotlin
@HiltViewModel
@Suppress("TooManyFunctions")
class BrowserViewModel @Inject constructor(
    @param:Named("default_home_url") private val defaultHomeUrl: String,
    private val buildSearchUrl: BuildSearchUrlUseCase,
) : ViewModel() {
    // ...
}
```

A second injected dependency is added. The Hilt `@HiltViewModel` annotation already activates constructor injection for all `@Inject`-bound dependencies — `BuildSearchUrlUseCase` is auto-bound by virtue of its own `@Inject constructor`. Zero new `@Module` lines.

## Method delta — `onAddressBarSubmit`

### Before (Spec 009 — file lines 176-191)

```kotlin
fun onAddressBarSubmit(loadUrl: (String) -> Unit): Boolean {
    val raw = _uiState.value.addressBarText
    return when (val classified = classifyAddressBarInput(raw)) {
        AddressBarSubmitResult.Empty -> false
        is AddressBarSubmitResult.Url -> {
            loadUrl(classified.target)
            true
        }
        is AddressBarSubmitResult.Query -> {
            val encoded = URLEncoder.encode(classified.text, Charsets.UTF_8.name())
            val searchUrl = String.format(UrlConstants.GOOGLE_SEARCH_URL_TEMPLATE, encoded)
            loadUrl(searchUrl)
            true
        }
    }
}
```

### After (Spec 010)

```kotlin
fun onAddressBarSubmit(loadUrl: (String) -> Unit): Boolean {
    val raw = _uiState.value.addressBarText
    return when (val classified = classifyAddressBarInput(raw)) {
        AddressBarSubmitResult.Empty -> false
        is AddressBarSubmitResult.Url -> {
            loadUrl(classified.target)
            true
        }
        is AddressBarSubmitResult.Query -> {
            viewModelScope.launch {
                val searchUrl = buildSearchUrl(classified.text)
                loadUrl(searchUrl)
            }
            true
        }
    }
}
```

## Behavior preserved

| Behavior | Mechanism |
|----------|-----------|
| Synchronous `Boolean` return value (Spec 009 FR-013a focus/keyboard release) | The Composable still gets `true` immediately on non-empty submit, before the async URL build completes. |
| Empty-input no-op (Spec 009 FR-012) | `AddressBarSubmitResult.Empty` branch unchanged → `false` returned. |
| URL branch unchanged (Spec 009 FR-009 https-prefix) | The `Url` branch still calls `loadUrl(classified.target)` directly — no use-case routing. Engines apply only to free-form queries (Spec 010 FR-012). |
| Cancel-previous-load on new submit (Spec 009 FR-013) | The WebView's own `loadUrl(...)` cancels any in-flight load when called again. `viewModelScope.launch` does NOT introduce a new race because the new submit will eventually call `loadUrl(...)` again — the WebView's cancellation is the authoritative coordinator. |
| Source-of-truth for `currentUrl` (Spec 009 FR-019/19a/19b) | Unchanged — `onUrlChanged` continues to be the only mutator of `BrowserUiState.currentUrl`, fired from `WebViewClient.onPageStarted` + `doUpdateVisitedHistory`. |

## Behavior changed (intended)

| Behavior | Before (Spec 009) | After (Spec 010) |
|----------|-------------------|-------------------|
| Search URL construction | Inline, synchronous, Google-only. | Domain-layer `BuildSearchUrlUseCase`, suspend, engine-agnostic. |
| Query → URL latency | Microseconds (just string ops). | Microseconds + one `Flow.first()` read on warm DataStore (typically sub-1 ms). SC-010 budgets 50 ms. |
| Behavior when `searchEngine = DuckDuckGo` or `Bing` (impossible under Spec 009) | N/A — only `Google` existed. | Routes to the appropriate template per FR-016/017/018. |

## Imports delta

```kotlin
// REMOVED:
import com.raumanian.thirtysix.browser.core.constants.UrlConstants  // (only used for GOOGLE_SEARCH_URL_TEMPLATE — now lives in SearchEngineRepositoryImpl)
import java.net.URLEncoder

// ADDED:
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.domain.usecase.BuildSearchUrlUseCase
import kotlinx.coroutines.launch
```

`UrlConstants` import is removed because the file no longer uses `GOOGLE_SEARCH_URL_TEMPLATE`. `defaultHomeUrl` is already injected as a `@Named` String, so `UrlConstants.DEFAULT_HOME_URL` was never referenced from this file.

## Test surface delta

`app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModelTest.kt`:

- **Existing tests adjusted**: any tests asserting Spec 009's inline `https://www.google.com/search?q=...` Google output now construct the ViewModel with a fake `BuildSearchUrlUseCase` returning the same URL string. The assertion text is unchanged because SC-001 mandates byte-identity.
- **New tests added** (per SC-004):
  - `submit query routes to BuildSearchUrlUseCase via viewModelScope` (uses `runTest` + `StandardTestDispatcher` to drain the launched coroutine).
  - `submit query forwards encoded URL to loadUrl lambda` (asserts the `loadUrl` callback receives the use case's return value).
  - `submit URL does NOT call BuildSearchUrlUseCase` (negative-path: the URL branch still bypasses the use case).
  - `submit empty input returns false and does not launch coroutine` (idle behavior preserved).

## Hilt impact

None. `BuildSearchUrlUseCase` is constructor-injected via its own `@Inject constructor`; `SearchEngineRepository` is bound via the new `SearchEngineModule`; both are auto-discovered. No changes to existing modules.
