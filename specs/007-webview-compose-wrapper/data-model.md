# Data Model — Spec 007 WebView Compose Wrapper

**Date**: 2026-05-01
**Branch**: `007-webview-compose-wrapper`

Spec 007 introduces no Room entities, no DataStore preferences, and no Domain models. All shapes here live in the **presentation** layer only (`presentation/browser/`). Promotion to `domain/model/` deferred until cross-screen reuse is required (per plan.md "Structure Decision").

---

## Entity 1 — `BrowserUiState` (presentation)

**Location**: `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserUiState.kt`

```kotlin
data class BrowserUiState(
    val currentUrl: String,
    val loadingState: LoadingState,
)
// No `DEFAULT` companion: the initial state is constructed inside BrowserViewModel
// from the Hilt-injected `@Named("default_home_url")` String, so production and
// instrumented tests can produce different initial states without touching this class.
// See tasks T014a + T015 + T036a.
```

**Fields**:

| Field | Type | Source / mutation | Notes |
|-------|------|-------------------|-------|
| `currentUrl` | `String` | Initial value injected via Hilt into `BrowserViewModel` (see contracts/browser-screen-contract.md). Subsequent changes via `BrowserViewModel.onLoadStarted(url)` / `onLoadFinished(url)` (called from `WebViewClient.onPageStarted` / `onPageFinished`). | URL of the page currently loaded or being loaded. Survives config change because `BrowserViewModel` is `@HiltViewModel` (lives in `ViewModelStore` which survives recreation). FR-007. |
| `loadingState` | `LoadingState` (sealed) | Mutated by `onLoadStarted` / `onProgressChanged` / `onLoadFinished` / `onLoadFailed` callbacks | See Entity 2 below. |

**Invariants**:

- `currentUrl` MUST never be empty; `BrowserViewModel`'s constructor receives a Hilt-injected `@Named("default_home_url")` String (production = `UrlConstants.DEFAULT_HOME_URL`) and uses it as the initial value.
- The transition graph for `loadingState` is enforced in `BrowserViewModel`:
  - `Idle → Loading(0f)` on first `onPageStarted`
  - `Loading(p) → Loading(p')` on each `onProgressChanged` (p' = newProgress / 100f)
  - `Loading(*) → Loaded` on `onPageFinished` OR `onProgressChanged(100)`
  - `Loading(*) | Loaded → Failed(reason)` on `onReceivedError` (main frame), `onReceivedHttpError` (main frame), or `onReceivedSslError`
  - `Failed | Loaded → Loading(0f)` on next `onPageStarted` (e.g., when Spec 008 adds reload)
- `Failed` is never reached from `Idle` (must transit through `Loading` first — there is no error before a load attempt).

**Persistence**: in-memory only. Spec 011 will introduce per-tab persistence to Room.

---

## Entity 2 — `LoadingState` (sealed class, presentation)

**Location**: `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/LoadingState.kt`

```kotlin
sealed class LoadingState {
    object Idle : LoadingState()
    data class Loading(val progress: Float) : LoadingState() {
        init { require(progress in 0f..1f) { "progress must be in [0,1], got=$progress" } }
    }
    object Loaded : LoadingState()
    data class Failed(val reason: ErrorReason) : LoadingState()
}
```

> **Phasing note**: this is the *final* shape after all three user stories ship. Tasks T013 (US1) creates `Idle` + `Loading` + `Loaded` only; task T029 (US3) adds the `Failed(reason: ErrorReason)` case together with `ErrorReason` (T028) so the type ordering compiles in a single phase.

**State transitions** (visual):

```
       ┌──────┐                ┌──────────────┐
       │ Idle │ ─────onStart──▶│ Loading(0f)  │
       └──────┘                └─────┬────────┘
                                     │ onProgressChanged(p<100)
                                     ▼
                               ┌──────────────┐
                               │ Loading(p..) │ ◀──┐
                               └─────┬────────┘    │ onProgressChanged
                                     │             │
                ┌────onError──┐      │ onPageFinished│
                │             │      ▼              │
                │       ┌─────────┐                 │
                │       │ Loaded  │ ────onStart─────┘
                │       └─────────┘
                ▼
          ┌──────────────────┐
          │ Failed(reason)   │ ────onStart──▶ Loading(0f)
          └──────────────────┘
```

**Why sealed (not enum)**: `Loading` carries a `progress: Float`, and `Failed` carries a structured `reason`. Enum cannot hold per-instance state.

**Why `object` vs `data class`**: `Idle` and `Loaded` carry no state — `object` reduces allocation and gives free `equals`/`hashCode`. `Loading` and `Failed` carry state — `data class` for value semantics in `StateFlow.update { }` distinct-emission checks.

---

## Entity 3 — `ErrorReason` (sealed class, presentation)

**Location**: `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/ErrorReason.kt`

```kotlin
sealed class ErrorReason {
    object NetworkUnavailable : ErrorReason()
    object DnsFailure : ErrorReason()
    data class HttpError(val statusCode: Int) : ErrorReason()
    object SslError : ErrorReason()
    object Generic : ErrorReason()
}

@StringRes
fun ErrorReason.toUserMessageRes(): Int = when (this) {
    NetworkUnavailable, DnsFailure -> R.string.browser_error_offline_hint
    is HttpError, SslError, Generic -> R.string.browser_error_generic
}
```

**Mapping from WebView callbacks**: see [research.md R6](research.md#r6--error-event-taxonomy-mapping).

**Localization**: 4 string keys under `res/values/strings.xml` plus the 7 locale variants:

| Key | Purpose | EN baseline |
|-----|---------|-------------|
| `browser_loading_a11y` | TalkBack content description for `LinearProgressIndicator` | "Loading page" |
| `browser_error_title` | Headline of the error UI | "Page didn't load" |
| `browser_error_offline_hint` | Body text for `NetworkUnavailable` / `DnsFailure` | "Check your internet connection and try again." |
| `browser_error_generic` | Body text for `HttpError` / `SslError` / `Generic` | "Something went wrong. Try opening the page later." |

> All 8 locale files MUST contain all 4 keys before merge (Spec 004 lint enforcement: `MissingTranslation` = error).

---

## Relationships

```
BrowserUiState ──────────owns──────────▶ LoadingState
                                            │
                                            └── (only when Failed) ──▶ ErrorReason
```

No external relationships — these types do not appear in `domain/`, `data/`, or any other feature module. Should Spec 011 (tabs) need to surface `LoadingState` per tab, the type may then be promoted to `domain/model/`; Spec 007 leaves the door open by depending only on `R.string` resource IDs (cross-layer-safe references) rather than Compose types.

---

## Why nothing in `domain/model/`

Three reasons (also recorded in plan.md "Structure Decision"):

1. **YAGNI / incremental scope** (Constitution §X + user feedback memory `feedback_incremental_scope.md`): the spec needs no domain entity because no Repository or UseCase consumes `BrowserUiState`.
2. **`LoadingState.Loading(progress: Float)` is intrinsically a UI concern** — domain models describe portable business entities; a 0..1 float driving an `M3 LinearProgressIndicator` is not portable.
3. **Forward compatibility**: when Spec 011 actually requires cross-screen state (tab preview thumbnail showing a small spinner if the tab is loading), the type is moved with `git mv` (history preserved) plus an Android-imports audit — same pattern Spec 006 used to relocate `ThemeMode`.
