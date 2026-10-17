# Research: Swipe the Address Bar to Switch Tabs

All decisions were made against the code on `main` `8e5a0ca`.

## R1 — Tab order without touching "the head is active"

**Decision**: leave the repositories' `lastActiveAt DESC` order and the rule that "the active tab has the newest `lastActiveAt`" as they are. Add one pure function in `core/domain/util`, `TabOrder`:

- `inOpeningOrder(tabs)` sorts by `position`, then `createdAt`, then `id` magnitude.
- `neighbours(tabs, activeId)` returns the previous and next tab of the same kind (`isIncognito`) in that order.

The Tabs screen and the swipe both read through it.

**Rationale**: `PageViewModel`, `ObserveAllTabsUseCase`, `TabsViewModel`, `OpenIncomingRequestUseCase` and the entry point all find the active tab at the head or by the newest `lastActiveAt`. Re-sorting the repository flows would change every one of them. A sort applied only where the order is shown changes two call sites.

**Position data**:
- Normal tabs already get `maxPosition + 1` and the home seed gets 0. The column is persisted, so the order survives restarts (FR-005b) with no migration.
- Incognito tabs today get `position = state.size`. After a close, that repeats a position that is still in use. It changes to a running counter in `IncognitoTabRepositoryImpl`, which lives only in memory.

**Alternatives**:
- Order the DAO and the repositories by `position`, and add an `isActive` flag. That needs a schema change and touches every consumer.
- Order by `createdAt` only. Two tabs created in the same millisecond, such as a page opening tabs in a burst, would then have no defined order.

## R2 — Closing the current tab shows its neighbour

**Decision**: in both repositories' `closeTab`, when the closed tab was the newest of its kind, the neighbour (the next tab in opening order, or the previous one when the closed tab was the last) takes the closed tab's `lastActiveAt`. It inherits that value rather than getting a new timestamp.

**Rationale**:
- When the closed tab was the global active tab, the neighbour becomes active (FR-005c).
- When an incognito tab was active and the person closed a normal tab from the Tabs screen, the normal neighbour gets a value older than the incognito tab. It does not jump in front.
- Closing the last incognito tab still falls back to the most recent normal tab, as today.
- The rule sits in the repositories, so every close path follows it: the Tabs screen, popups closing themselves, the entry point's external tab and `CloseTabUseCase`.

**Alternatives**:
- Stamp the neighbour with `now`. Closing a background normal tab would then make it active over an incognito one.
- Choose the next tab in the ViewModel. There are four close paths, and each would need the same logic.

## R3 — Where the gesture is caught

**Decision**: a `Modifier.tabSwipe(…)` on a box around the address pill, and on the Home search field in Home and the incognito start page. It watches pointer events in `PointerEventPass.Initial`:

- It claims the drag, by consuming the moves, only when the pointer has passed touch slop with `|dx| ≥ 2|dy|` and the field is not focused.
- Until then nothing is consumed. A tap or a long-press therefore still reaches `BasicTextField`, which focuses it (FR-008).
- Once claimed, the child's tap detector sees consumed moves and does not fire a tap on release.

The decision logic lives in a pure `TabSwipeTracker`, in the same style as `SwipeGestureTracker`, so it can be unit tested. The modifier only feeds it events.

**Rationale**:
- `BasicTextField` handles taps and long-press selection in its own pointer input. A parent `detectHorizontalDragGestures` in the Main pass would see moves the field had already consumed.
- The Initial pass lets the parent decide first, without taking taps.
- Spec 028's swipe lives on the `WebView`, a different view, so the two never see the same touch (FR-010).

**Velocity**: a `VelocityTracker` (Compose `androidx.compose.ui.input.pointer.util`) decides a flick. The flick speed is `TabSwipeLimits.FLICK_VELOCITY_DP_PER_S` = 1,000 dp/s, and the commit distance is `TabSwipeLimits.COMMIT_FRACTION` = 0.33 of the lane width (FR-002). At either end the drag is damped by `TabSwipeLimits.EDGE_RESISTANCE` = 0.25, and it always springs back (FR-004).

**Alternatives**:
- `AnchoredDraggable`. It owns the offset, but it cannot hold back taps from the child field in the Initial pass.
- A `View`-level touch interceptor. The bar is Compose, so it would need an `AndroidView` wrapper.

## R4 — What moves on screen

**Decision**: one `TabSwipeState` (`@Stable`) per browser screen holds:

- `offsetPx` (`mutableFloatStateOf`, changes every frame);
- `incoming` (the neighbour being shown, or `null`);
- `settling` (true while the commit or the return animates).

During a drag:

- **The pill lane** clips its content. The current pill moves by `offsetPx`, and a "ghost pill" with the neighbour's host (or "Home", or the incognito label) sits one lane-width away on the incoming side.
- **The page area** (the `TabWebView` box and the Home overlay) moves by `offsetPx` scaled to the screen width. A `TabSwipePreview` draws the neighbour beside it:
  - a normal tab with a screenshot shows its `ScreenshotCache` file through the existing `rememberFileImage`;
  - a normal tab without one shows `SeededMeshArt` from its host, the Tabs card placeholder;
  - an incognito tab shows `surfaceIncognito` with the incognito mark, and never an image (FR-012);
  - Home shows the Home look.
- Every read of `offsetPx` happens in `graphicsLayer { translationX = … }` (Principle IV). Composition only reads `incoming`, which changes once per drag.

**Commit** (FR-002, FR-007):
1. `Animatable` finishes the slide.
2. Then `TabSwipeAction.SwitchTo(id)` calls `SwitchToTabUseCase`, the same path as the Tabs screen.
3. The preview stays over the page until the new tab's WebView reports its first visible frame (`onPageCommitVisible`, or the restored page's first draw), or until `TabSwipeLimits.PREVIEW_HOLD_MAX_MS` = 600 ms. A blank WebView never shows between the slide and the page.
4. The offset resets when the active tab's id changes.

**Reduced motion** (FR-015): there is no helper today. Add `core/presentation/util/rememberAnimationsEnabled()`, which reads `ValueAnimator.areAnimatorsEnabled()` (API 26+) or `Settings.Global.ANIMATOR_DURATION_SCALE != 0` (API 24–25). When animations are off, the commit and the return snap with no `Animatable`. The finger-following drag stays, since it is direct manipulation and not an animation.

**Alternatives**:
- A `HorizontalPager` of live WebViews. That means one WebView per tab, against Spec 028's single re-keyed WebView. It would cost memory and break `TabSessionHooks`.
- Moving only the pill, with no page preview. Simpler, but the spec asks for the preview.

## R5 — State holder and use case

**Decision**:
- **Use case**: a new `ObserveTabNeighboursUseCase` in `feature/browser/domain/usecase`. It maps `ObserveAllTabsUseCase` through `TabOrder.neighbours` for the active tab and emits `TabNeighbours(previous, next)`.
- **Holder**: a new `TabSwipeViewModel` in `feature/browser/presentation/browser/`. It exposes:
  - `StateFlow<TabSwipeUiState(previous, next, enabled)>`, where `enabled` comes from settings;
  - `onAction(TabSwipeAction)`, with `SwitchTo(id)` and `Announce` handled as events;
  - like `TabsViewModel`, `screenshotVersion: StateFlow<Long>` and a `previews` lookup over `ScreenshotCache`.

**Rationale**:
- `PageViewModel` is already at detekt's limits.
- Spec 027 split the screen into holders, one job each.
- The switch reuses `SwitchToTabUseCase`, so a swiped tab is treated exactly like one picked elsewhere (FR-007).

**Mid-drag changes** (FR-011): the drag keeps the neighbour id it started with. If a new `TabSwipeUiState` arrives with a different neighbour on that side, the drag cancels and springs back.

## R6 — The Tabs screen in opening order

**Decision**:
- `TabsViewModel` sorts `observeAllTabs()` through `TabOrder.inOpeningOrder` before filtering by mode. `activeTabId` keeps coming from the newest `lastActiveAt`.
- `TabsScreen` scrolls the grid to the active card when it opens, since the active tab is no longer always first.

**Leaves alone**: the Home tab shortcuts (`HomeViewModel.TabShortcut`) are "recent tabs" and stay most recent first.

## R7 — Accessibility

**Decision**: the pill and the Home search field get `semantics { customActions = … }`:

- "Next tab" when `next != null`, and "Previous tab" when `previous != null` (FR-014).
- Each action sends `SwitchTo`, then announces through `View.announceForAccessibility`:
  - a normal tab: its title, or else its host, or else "Home";
  - an incognito tab: "Incognito tab", matching the Tabs card, which never speaks an incognito title.
- The actions stay when the gesture setting is off.

## R8 — Setting

**Decision**: new pieces for the setting:

- DataStore key `tab_swipe_enabled` (`StorageKeys.TAB_SWIPE_ENABLED`);
- `AppDefaults.TAB_SWIPE_ENABLED = true`;
- `UserSettings.isTabSwipeEnabled`;
- `SettingsRepository.setTabSwipeEnabled`;
- `SettingsAction.TabSwipeToggled`;
- a row under Spec 028's row, keyed `row_tab_swipe` and tagged `settings_tab_swipe_row`.

Settings are already included in backup, so this one is too.

## R9 — Measurement

- **SC-005**: frame timing on the A55 release build with `dumpsys gfxinfo` while scripting 20 swipes. The owner allows release builds on it; check `mCurrentFocus` first (ADR 0017).
- **SC-008**: cold start in alternating blocks on `TA016_API36_16K` against `8e5a0ca`, as done for 029.
- **Layout Inspector**: the recomposition count during one drag needs Android Studio. It is recorded as DEFERRED unless the owner runs it.
