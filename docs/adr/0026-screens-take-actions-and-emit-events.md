# 0026. Screens take actions and emit events

- Status: accepted
- Date: 2026-10-15

## Context

Constitution 2.0.0 asked for `onAction(XAction)` and `XEvent`, but 18 state holders exposed 1–33
public functions each. They used three ways to send one-off effects: a `SharedFlow` that drops an
effect nobody collects, a `Channel` that keeps it, and a nullable `UiState` field cleared by a
`consume…` call. Screens obtained holders in child composables and passed them down (23 compose-rules
findings). 33 use cases only forwarded one call to a repository. This decision extends ADR 0001.

## Decision

- **Route and screen.** `XRoute` obtains the holders as default parameters, collects state once, runs
  `EventEffect` and calls `XScreen`. `XScreen` and its parts take state, flows, lookups and callbacks,
  never a holder. Hosts shown inside another screen (`HomePage`, `QuickSettingsHost`, `FireOverlayHost`,
  `rememberFireButton`) obtain their holder the same way.
- **One input.** A `*ViewModel` exposes `onAction(XAction)` and no other public or internal function.
  `XAction` and `XEvent` are sealed types in the screen folder. `BrowserAction` nests `Page`,
  `AddressBar`, `Home`, `Find` and `Download`. A holder with no user input has no action type.
- **Two event kinds, kept as they were.** An effect is either **Drop** (a `MutableSharedFlow` with no
  replay: lost when nobody collects) or **Keep** (a buffered `Channel`: delivered when a collector
  arrives). Effects that were held in `UiState` are Keep. Each effect keeps the kind it had, so no
  message starts or stops appearing. A route shows snackbars apart from its event collector, so a
  showing message never holds up navigation.
- **Queries are properties.** A synchronous read made while building views (`TabsViewModel.images`,
  `BrowserViewModel.desktopMode`) is a read-only lookup property, not an action.
- **WebView bridges are not screen holders.** `PopupTabs`, `SitePermissionGate` and `LongPressActions`
  answer WebView callbacks synchronously (a pop-up decision, the pop-up `WebView`, a permission
  outcome). They live in a ViewModel only for its scope, are not named `*ViewModel`, keep their
  functions, and are obtained at `BrowserRoute`.
- **No forwarding use cases.** A holder calls the domain contract when a use case would only forward
  one call. The tests of the 33 removed use cases were deleted when they checked only the pass-through;
  an assertion about repository behaviour that no repository test covered moved to that repository's
  test.

## Consequences

- A rotation while a Keep snackbar shows no longer shows it again; it was shown once already.
- `ArchitectureTest.view models take input only through onAction` and compose-rules without a baseline
  enforce the shape. detekt's `CyclomaticComplexMethod` ignores a function that is one `when`, the
  shape of every `onAction`. `PlacementTest` no longer reads a `Default*` interface in a `domain`
  package as an implementation: `DefaultBrowserController` is a contract.
- State-holder tests send actions and read `events`; `MainDispatcherRule` sets the main dispatcher.
- Splitting `BrowserViewModel` into smaller holders is left to a later decision.
