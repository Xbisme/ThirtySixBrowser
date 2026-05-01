# Feature Specification: Navigation Controls

**Feature Branch**: `008-navigation-controls`
**Created**: 2026-05-01
**Status**: Draft
**Input**: User description: "navigation-controls — Back / Forward / Reload / Stop / Home + predictive back gesture cho BrowserScreen. Build trên BrowserWebView từ Spec 007. Surface canGoBack/goBack/canGoForward/goForward/reload/stopLoading qua BrowserViewModel. Predictive back Android 14+. Home loads default home URL. Bottom bar UI placement TBD trong /clarify. Disable state khi can*=false. Stop visible khi Loading, ngược lại là Reload."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Move backward and forward through visited pages (Priority: P1)

After the user has visited several pages within a tab, they need to retrace their navigation history. They tap a Back affordance to return to the previous page, and a Forward affordance to move ahead again to a page they had already retreated from. Each step matches the order they visited pages in.

**Why this priority**: This is the most fundamental browser interaction outside of loading a URL. Without it, the browser is effectively single-page. Every browser users have ever used provides this — its absence would be perceived as broken.

**Independent Test**: Open the browser, load page A, tap a link to navigate to page B, then page C. Tap Back twice → user returns to A through B. Tap Forward twice → user returns to C through B. Verifies entire P1 flow with no other features needed.

**Acceptance Scenarios**:

1. **Given** a tab with three pages in its session history (A → B → C, currently on C), **When** the user activates Back, **Then** page B is rendered and the URL display reflects B.
2. **Given** the same history with the user now back on B after step 1, **When** the user activates Forward, **Then** page C is rendered and the URL display reflects C.
3. **Given** a tab whose session history has no prior page (fresh load of A only), **When** the Back affordance is presented, **Then** it is shown as disabled / non-actionable.
4. **Given** a tab where the user is already on the most recent page in history, **When** the Forward affordance is presented, **Then** it is shown as disabled / non-actionable.
5. **Given** navigation history that is at the maximum supported depth, **When** the user taps Back repeatedly, **Then** each tap moves backward one entry until Back becomes disabled at the first entry.

---

### User Story 2 - Use the system back gesture to navigate WebView history (Priority: P1)

Users on Android expect the system back gesture (edge-swipe on Android 10+, hardware/navigation-bar back on older devices) to behave like the in-app Back button when there is page history to retreat through. When the WebView has no further history within the current screen, the gesture falls through to the system's default behavior (exiting the screen / app), so the user is never trapped.

**Why this priority**: Edge-swipe back is the dominant navigation idiom on modern Android. If it doesn't behave correctly inside the browser, the app feels broken to native users. On Android 14+, this must use the predictive back animation so the user can preview the destination page before committing.

**Independent Test**: With the browser at page C (history A → B → C), perform a system back gesture. Verify page B renders. Repeat to land on A. Repeat once more — the gesture exits the browser screen. On Android 14+ devices, mid-gesture the user sees a preview of the destination before commit / cancel.

**Acceptance Scenarios**:

1. **Given** WebView has at least one prior history entry, **When** the user performs a system back gesture, **Then** the WebView navigates to the previous entry and the gesture is consumed (no screen exit).
2. **Given** WebView is at its first / only history entry, **When** the user performs a system back gesture, **Then** the gesture is NOT consumed and the system performs its default back behavior (exit the screen, then exit the app from the root screen).
3. **Given** the device runs Android 14 or newer, **When** the user begins a predictive back gesture inside a WebView with history, **Then** a preview of the previous page is shown during the gesture and the navigation commits only when the gesture completes; cancelling the gesture restores the current page with no navigation.
4. **Given** the device runs Android 13 or older, **When** the user activates back, **Then** the same logical behavior applies (back through history, then exit) without the predictive preview animation.

---

### User Story 3 - Reload the current page or stop a load in progress (Priority: P2)

While a page is loading, the user wants to abandon the load (e.g., it's hanging, they changed their mind). When a page is fully loaded, they want to reload it (e.g., content looks stale, they want fresh data). A single affordance covers both: it shows as Stop while loading and as Reload when idle, so the user always has the right action available without a mode switch.

**Why this priority**: Reload is a daily action. Stop is needed less often but is critical when it's needed (slow networks, infinite-loading pages). Combining them into one toggle saves bottom-bar real estate.

**Independent Test**: Load a slow page; before it finishes, tap the affordance — load aborts and the page stops at its partial state. Once loading completes on the next attempt, tap the same affordance — the page reloads from the network.

**Acceptance Scenarios**:

1. **Given** a page is actively loading, **When** the affordance is presented, **Then** it shows the Stop semantic (icon + content description "Stop loading").
2. **Given** the same loading page, **When** the user activates the Stop affordance, **Then** the load is cancelled, the loading indicator disappears, and the page settles at whatever state it had reached.
3. **Given** a page has finished loading (or has not started), **When** the affordance is presented, **Then** it shows the Reload semantic (icon + content description "Reload page").
4. **Given** the same idle page, **When** the user activates Reload, **Then** the current URL is fetched again, the loading indicator appears, and the page re-renders.
5. **Given** the affordance is mid-transition between Loading and Loaded, **When** the user activates it, **Then** whichever semantic is currently displayed is the action that executes — there is no ambiguity.

---

### User Story 4 - Return to the home page from anywhere (Priority: P2)

The user has navigated several pages deep and wants a one-tap escape to a known starting point. Tapping Home loads the configured default home URL in the current tab.

**Why this priority**: Home is a standard browser convenience that lets the user reset context without manually retyping a URL or closing the tab. Lower than Back/Forward because users can usually reach a known page through history, but valuable enough to warrant a permanent affordance.

**Independent Test**: From any loaded page, tap Home. Verify the current tab navigates to the configured default home URL and the URL display updates accordingly.

**Acceptance Scenarios**:

1. **Given** a tab on any URL, **When** the user activates Home, **Then** the tab loads the default home URL and the URL display updates.
2. **Given** the user has activated Home, **When** they then activate Back, **Then** they return to the page they were on before activating Home (Home creates a new entry in history; it does not erase prior entries).
3. **Given** the tab is already on the default home URL, **When** the user activates Home, **Then** the page reloads (same behavior as Reload on the home URL — no special-case no-op).

---

### Edge Cases

- **Empty WebView (no page loaded yet)**: Back, Forward, Stop, and Reload are all disabled or hidden; only Home is actionable. The user cannot trigger an operation on an empty WebView.
- **Failed page load (network/SSL/HTTP error from Spec 007 ErrorReason)**: Reload remains actionable so the user can retry; Back/Forward respect history as-if the failed attempt did not occur (the failed entry MAY appear in WebView history depending on platform behavior — this spec accepts platform default).
- **Rapid repeated taps**: Activating Back/Forward/Reload/Home in rapid succession must not crash or skip beyond the available history. The system processes them serially; impossible operations are no-ops.
- **Predictive back cancelled mid-gesture**: If the user starts a predictive back gesture and cancels it (releases without committing), no navigation occurs and the WebView is restored to its current state.
- **Home URL is unreachable (offline)**: Activating Home when offline produces the same localized error UI used by Spec 007 (NetworkUnavailable). The action is not blocked; the failure is surfaced as a normal load error.
- **Stop activated when loading is already finishing**: If the user taps Stop in the brief window between content arrival and loaded-state transition, the operation is a no-op (or completes; either is acceptable since the user-visible result is identical).
- **Configuration change (rotation) mid-navigation**: Rotating the device while a navigation is in flight does not lose the back/forward state; the affordance enabled/disabled states recompute correctly post-rotation.
- **Process death / restoration**: WebView session history is platform-managed and not persisted across process death in v1.0. Cross-process-death history persistence is out of scope (deferred to Spec 011 tabs-management which will introduce tab persistence).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The browser MUST present a Back affordance that, when activated, navigates the current tab to the previous entry in WebView session history.
- **FR-002**: The browser MUST present a Forward affordance that, when activated, navigates the current tab to the next entry in WebView session history.
- **FR-003**: The Back affordance MUST be presented in a disabled (non-actionable) state when the WebView has no prior history entry.
- **FR-004**: The Forward affordance MUST be presented in a disabled (non-actionable) state when the WebView has no next history entry.
- **FR-005**: The browser MUST present a single combined Reload/Stop affordance that displays Stop while a page is loading and Reload at all other times.
- **FR-006**: Activating Stop MUST cancel the in-flight page load and settle the WebView at whatever state it had reached.
- **FR-007**: Activating Reload MUST re-fetch the current URL.
- **FR-008**: The browser MUST present a Home affordance that, when activated, loads the configured default home URL in the current tab.
- **FR-009**: Activating Home MUST add a normal entry to WebView session history (the user can subsequently activate Back to return to the prior page).
- **FR-010**: The system back gesture (edge-swipe on Android 10+, hardware/navigation-bar back on older devices) MUST navigate the WebView backward through history when prior history exists.
- **FR-011**: The system back gesture MUST fall through to the platform default (exit the screen / app) when no prior WebView history exists.
- **FR-012**: On Android 14 and newer, the system back gesture MUST present a predictive-back preview animation showing the destination state before the gesture commits, and MUST NOT commit navigation if the user cancels the gesture.
- **FR-013**: All five affordances (Back, Forward, Reload/Stop, Home) MUST be reachable on the BrowserScreen without entering a menu or secondary surface.
- **FR-014**: The disabled / enabled state of each affordance MUST update reactively as the WebView session history changes (after every successful navigation, including those triggered by link taps inside the page).
- **FR-015**: All affordances MUST expose an accessible content description in the user's active locale (8 locales from Spec 004) so screen readers announce them correctly.
- **FR-016**: The configured default home URL MUST be the value defined in the project's app defaults; users cannot change this URL in v1.0 (the Settings screen — Spec 016 — will introduce user-overridable home URL in a later release).
- **FR-017**: Affordance activation MUST NOT crash, freeze, or leave the UI in an inconsistent state when invoked rapidly or during a configuration change (rotation, locale switch).
- **FR-018**: The bottom-bar surface that hosts these affordances MUST [NEEDS CLARIFICATION: bar layout / icon set / persistence behavior — see /speckit-clarify; specifically: should the bar always be visible, auto-hide on scroll, or be revealed by gesture? Should it use Material 3 BottomAppBar, NavigationBar, or a custom Row?].

### Key Entities *(include if feature involves data)*

- **WebView Session History**: Platform-provided per-tab navigation stack. Each entry is a (URL, page state) pair created when the WebView commits a navigation. v1.0 stores this in WebView memory only; persistence across process death is out of scope.
- **Loading State** (extended from Spec 007): The existing `LoadingState` already distinguishes Idle / Loading / Loaded / Failed. The Reload/Stop affordance reads this state to decide which semantic to display; no new state is introduced by this spec.
- **Default Home URL**: Constant value defined in app defaults (Spec 006 introduced the constants location; the URL itself ships with this spec). User-configurable home URL is deferred to Spec 016.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can navigate Back through three pages of history and Forward through them again with 100% reliability across 50 consecutive trials on a reference device (no skipped pages, no crashes, no stuck states).
- **SC-002**: System back gesture from a page with prior history reaches the previous page in under 300 ms on a reference mid-tier device (measured from gesture commit to first frame of the previous page).
- **SC-003**: On Android 14+, predictive back preview begins rendering within 100 ms of gesture start in 95% of trials (perceived as immediate by the user).
- **SC-004**: Reload from a fully loaded page begins re-fetching within 200 ms of activation, and Stop cancels an in-flight load within 200 ms of activation, on a reference device.
- **SC-005**: Disabled Back/Forward affordances cannot be activated; 100% of attempts on a disabled affordance produce no state change (verifiable via instrumented test).
- **SC-006**: Reload/Stop affordance shows the correct semantic (Stop during Loading, Reload otherwise) in 100% of state transitions across 50 page loads — there are no observed visual lags where the wrong semantic is displayed for more than 100 ms.
- **SC-007**: Affordance content descriptions are present and locale-appropriate in all 8 supported locales (verifiable via lint translation completeness check, same gate as Spec 004).
- **SC-008**: The APK release size delta introduced by this spec stays under 50 KB (no new third-party packages expected; only Compose icons and Kotlin code).
- **SC-009**: 16KB page-size CI gate remains green: zero new native libraries are introduced.
- **SC-010**: User-perceived task completion: in usability sampling, 9 out of 10 first-time users locate and successfully use Back, Forward, Reload, and Home without prompting within 30 seconds of opening the browser.

## Assumptions

- WebView session-history APIs from `android.webkit.WebView` (`canGoBack`, `goBack`, `canGoForward`, `goForward`, `reload`, `stopLoading`) provide reliable behavior across the project's minSdk 24 to targetSdk 36 range. No vendor-specific WebView quirks are expected to require workarounds in v1.0.
- The default home URL is a constant defined in app defaults at implementation time (Spec 006 introduced `AppDefaults.kt`). User-configurable home URL is deferred to Spec 016 (Settings screen).
- Predictive back animation is opt-in via the `android:enableOnBackInvokedCallback="true"` manifest attribute and the `OnBackInvokedDispatcher` API on Android 13+ / 14+. The app already targets SDK 36, so the API surface is available; the manifest opt-in is expected to be added in this spec.
- Cookie / cache behavior on Reload follows the Android WebView default (normal reload, not hard reload). A hard-reload (cache-bypass) variant is out of scope for v1.0.
- Long-press history dropdowns on Back/Forward (offered by some browsers) are out of scope for v1.0.
- WebView session history is in-memory only in v1.0; cross-process-death persistence is the responsibility of Spec 011 (tabs-management) when tab state begins to persist.
- The spec assumes a single active tab. Multi-tab back behavior (e.g., "back closes the current tab when its history is empty") is deferred to Spec 011 / Spec 012.
- Bottom-bar visual design (placement, icon set, surface treatment, scroll behavior) is intentionally left for `/speckit-clarify` and `/speckit-plan` since it depends on Compose Material 3 component selection and project-specific spacing tokens — both implementation concerns rather than user-value concerns.

## Dependencies

- **Spec 007 (`webview-compose-wrapper`)** — provides `BrowserWebView`, `BrowserViewModel`, `BrowserUiState`, and `LoadingState`. This spec extends those without rewriting them.
- **Spec 006 (`datastore-settings`)** — provides `AppDefaults.kt` location for the default home URL constant. The constant value is added in this spec (not in 006).
- **Spec 004 (`localization-multi-language`)** — provides the 8-locale string resource infrastructure used for accessibility content descriptions.
- **Spec 002 (`clean-architecture-skeleton-di`)** — provides `BaseViewModel.launchSafely` patterns used to wire navigation actions through the existing state machine.
