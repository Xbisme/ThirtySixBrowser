# Contract: screen ↔ state holder

The interface every screen exposes after this spec, and the checks that hold it. It is written for contributors and reviewers. Constitution principle III (2.1.0) and ADR 0026 state the rule; this file states the shape.

## Shape

```kotlin
// feature/<f>/presentation/<screen>/XAction.kt
sealed interface XAction {
    data object Refresh : XAction
    data class ItemClicked(val id: Long) : XAction
}

// feature/<f>/presentation/<screen>/XEvent.kt   (only if the holder has one-off effects)
sealed interface XEvent {
    data class OpenUrl(val url: String) : XEvent
}

// holder
@HiltViewModel
class XViewModel @Inject constructor(/* use cases and core/domain contracts only */) : ViewModel() {
    val uiState: StateFlow<XUiState>
    val events: Flow<XEvent>
    fun onAction(action: XAction) { when (action) { /* exhaustive, calls private funs */ } }
}

// feature/<f>/presentation/<screen>/XScreen.kt
@Composable
fun XRoute(onShowBrowser: () -> Unit, modifier: Modifier = Modifier, viewModel: XViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    EventEffect(viewModel.events) { event -> /* navigate, snackbar, platform call */ }
    XScreen(state = state, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
fun XScreen(state: XUiState, onAction: (XAction) -> Unit, modifier: Modifier = Modifier) { /* no holder */ }
```

These are illustrations of the contract, not code to copy. Names and parameters come from each screen.

## Rules

| # | Rule | Check |
|---|---|---|
| C1 | A `*ViewModel` in `presentation` has no public or internal function except `onAction`. Properties (state, `events`, lookup objects, hooks) are allowed | `ArchitectureTest.view models take input only through onAction` (Konsist) |
| C2 | Only a route or embedded host obtains a holder (as a default parameter), and no composable passes a holder to another; WebView bridges are obtained at the browser route and passed down as bridges | compose-rules `vm-injection-check`, `vm-forwarding-check`, no baseline |
| C3 | `XAction` and `XEvent` are sealed and live in the screen's folder | Review |
| C4 | Each effect keeps its delivery kind (Drop or Keep, research R4); a new effect states its kind in its declaration | Review |
| C5 | A synchronous query used while building views is a property (a `fun interface` lookup), not an action | Review |
| C6 | WebView bridges (`PopupTabs`, `SitePermissionGate`, `LongPressActions`) are not named `*ViewModel` and are exempt from C1 (ADR 0026) | Review |
| C7 | Tests set the main dispatcher only through `MainDispatcherRule` | `ArchitectureTest.main dispatcher is set only by the rule` (Konsist, test scope) |
| C8 | A holder receives use cases and `core/domain` / feature `domain` contracts only, and calls a contract directly instead of through a use case that would only forward the call | `PlacementTest.view models never reach past the domain` + Review |

## Failure messages

| Check | Message names |
|---|---|
| C1 | the file, and "send input through onAction(XAction)" |
| C2 | the file:line, and the compose-rules id |
| C7 | the test file, and "use MainDispatcherRule" |
