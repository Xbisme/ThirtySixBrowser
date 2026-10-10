# Research: UI Redesign (Spec 019)

Decisions that turn the spec into an implementable design. Each one names what was rejected and why. Facts about the current code were read on branch `019-ui-redesign` (base `263b9c6`).

## R1 — How a tab "shows the Home page" without a schema change

**Decision**: A reserved internal address, `UrlConstants.HOME_PAGE_URL = "thirtysix:home"`, marks a tab whose content is the Home page. The existing `@Named("default_home_url")` binding in `UrlConfigModule` is pointed at it, so every path that already creates a tab with the default address — `CreateTabUseCase`, `TabRepositoryImpl`'s empty-state seed, `TabsViewModel`'s incognito creation — produces a Home tab with no change to those call sites. `BrowserScreen` renders the Home page (or, for an incognito tab, the incognito page) when the active tab's URL equals the sentinel, and never hands the sentinel to the WebView.

**Pressing Home on a loaded page** does not rewrite the tab's URL. It sets an in-memory `homeOverlay` flag on the browser state; the WebView keeps its page underneath, and Back clears the flag, which is exactly the "Home then Back returns to the page" behaviour users expect.

**Back from a page reached from Home**: a fresh tab's first navigation sets an in-memory `cameFromHome` flag. When the WebView reports `canGoBack == false` and the flag is set, Back returns the tab to the Home page instead of leaving the app. After process death the flag is gone and Back leaves the app — Spec 011 already defines in-tab history as best-effort.

**Never recorded or loaded**: the history recorder only records `onPageFinished` URLs and the sentinel is never loaded, so no guard is needed there; one is added anyway in `RecordHistoryEntryUseCase` as a cheap invariant. The address classifier never sees it because the address well shows the hint, not the sentinel (spec A10).

**Rejected**:
- *A new `kind` column on the tab table* — a schema migration (v2→v3) to store a value the URL can already carry.
- *A separate navigation destination for Home* — Home must live inside a tab (tab cards, tab count, Back into it), which a separate route cannot do without duplicating tab state.
- *`about:blank` plus a flag* — `about:blank` is a real address WebView loads and the history recorder could see.

## R2 — Theme architecture: Material 3 scheme plus extended tokens

**Decision**: Design-system tokens map onto the Material 3 `ColorScheme` wherever a role exists, and the rest live in an `@Immutable` `ThirtySixColors` class provided through a `staticCompositionLocalOf` and read as `ThirtySixTheme.colors`.

| Design token | M3 role / extended field |
|---|---|
| primary, on-primary | `primary`, `onPrimary` |
| primary-soft, on-primary-soft | `primaryContainer`, `onPrimaryContainer` |
| surface | `surface`, `background` |
| surface-raised | `surfaceContainerLow` (and `surfaceBright`) |
| surface-container | `surfaceContainer`, `surfaceVariant` |
| ink, ink-muted | `onSurface`, `onSurfaceVariant` |
| outline, hairline | `outline`, `outlineVariant` |
| danger, on-danger | `error`, `onError` |
| scrim | `scrim` |
| fab-start/end, hero-deep/violet/rose, glass/glass-stroke/on-glass/on-glass-muted, tile-* and their inks, positive, surface-incognito, on-incognito | `ThirtySixColors` (extended) |

`static` because these change only with the theme, so a theme change recomposes everything once, which is correct.

**Dynamic color** (FR-005, clarified): the dynamic scheme supplies `primary`, `primaryContainer` and the surface roles; `ThirtySixColors.heroViolet` and `fabStart`/`fabEnd` derive from the dynamic `primary`; every other extended token stays fixed.

**Rejected**: *custom tokens only, no M3 scheme* — the M3 components the app still uses (ModalBottomSheet, TextField, Switch, Snackbar) would then render in default purple instead of the brand.

## R3 — Shapes and elevation

**Decision**: `Shapes(extraSmall = 6.dp, small = 10.dp, medium = 14.dp, large = 20.dp, extraLarge = 28.dp)`; the 32dp sheet radius and `CircleShape` for pills are exposed through `ThirtySixTheme`. Shadows: Compose's `Modifier.shadow` cannot express the design's two-layer soft shadow or the coloured FAB glow, so both are drawn with `drawBehind` + a blurred `Paint` shadow layer (`setShadowLayer`, supported on hardware layers from API 28; on 24–27 a single flat shadow via `Modifier.shadow` is the fallback).

## R4 — Icons: Material Symbols as individual vectors

**Decision**: Import each needed symbol (about 45, list in data-model.md §5) from Google's `material-design-icons` repository (Apache-2.0), **Material Symbols Rounded, weight 300, grade −25, optical size 24**, outlined plus a filled variant only where the design fills it. Each becomes `res/drawable/ic_<name>.xml` (naming convention `ic_action_name`). The repository commit is pinned in tasks.md. When the last `Icons.*` reference is gone, `material-icons-core` is removed from the catalog — a net dependency **reduction**.

**Rejected**: *`material-icons-extended`* — Constitution convention forbids it, it is huge, and it is weight 400 Material Icons, not Symbols. *The variable icon font at runtime* — a ~300 KB font asset, ligature lookups in every icon, and no compile-time check of names.

## R5 — Frosted glass: translucent fill, no backdrop blur

**Decision**: Glass is a translucent white fill plus a hairline ring on **every** Android version. No backdrop blur.

**Why**: Compose has no backdrop blur. `Modifier.blur` / `RenderEffect` blurs the element's *own* content, not what is behind it. Over the hero, what is behind the glass is a smooth mesh gradient, where a blur is visually indistinguishable from no blur; over a shortcut card's screenshot it would be noticeable, but those tags are 24dp tall. The canvas, built with CSS `backdrop-filter`, looks the same within antialiasing at those sizes.

**Rejected**: *Haze (dev.chrisbanes.haze)* — a new dependency for an effect the user cannot see on the hero. *Snapshotting the hero into a blurred bitmap* — work on every scroll frame, the opposite of Constitution §V.

**Spec impact**: spec A4 and the Android 7–11 edge case said blur on Android 12+; both are corrected to "translucent fill on all versions" in the same commit as this research.

## R6 — Home hero and collapse

**Decision**: The Home page is one `LazyColumn`: item 0 is the hero, then the "Recently visited" header and rows. The mesh is three `Brush.radialGradient` layers drawn in one `drawBehind`, so it is a single draw call with no child layout. The collapse (alpha and scale of the greeting, as the list scrolls) reads `listState.firstVisibleItemScrollOffset` **inside** `graphicsLayer { }`, never in composition. The shortcut row is a `LazyRow` with `key = shortcut.stableKey` and `contentType = shortcut.kind`.

## R7 — Browsing bars that slide away

**Decision**: A `ChromeScrollState` (a plain `@Stable` class holding one `mutableFloatStateOf` offset, clamped 0..max) is created with `remember` in `BrowserScreen`. `BrowserWebView` reports scroll deltas from `View.setOnScrollChangeListener` (API 23+, below minSdk 24); the state consumes them. Top and bottom bars apply `Modifier.offset { IntOffset(0, …) }` reading the state in the lambda — layout-phase only, no recomposition. The WebView is laid out full-screen beneath the bars and shifted by `graphicsLayer { translationY = topBarVisiblePx }` so the page never relayouts while the bars move. Bars snap fully shown when the page is not scrollable, on error, when the keyboard opens (WindowInsets.ime visible) and when loading starts.

**Rejected**: *NestedScrollConnection* — a WebView inside `AndroidView` does not dispatch nested scroll to Compose without `NestedScrollView` wrapping, which breaks WebView's own scrolling. *Changing the WebView's height as the bars move* — a relayout of the platform view every frame.

## R8 — Isolating page-load progress

**Decision**: `BrowserViewModel` exposes `loadProgress: StateFlow<Float>` separately from `uiState`. `LoadingState.Loading` loses its `progress` field, so a progress tick no longer produces a new `BrowserUiState`. At the screen root, progress is collected with `collectAsStateWithLifecycle()` into a `State<Float>` **held, not delegated** (`val progress = vm.loadProgress.collectAsStateWithLifecycle()`, never `by`), and passed as `{ progress.value }` to the progress line, which reads it only inside `drawBehind`. A progress update therefore invalidates one draw of one 2dp line. The same pattern carries download progress in `DownloadRow` (the fraction lambda reads from the row's model inside `drawBehind`).

**Before** (measured in this research by reading the code): `BrowserScreen` collects `uiState` with `by` at the top; every `onProgressChanged` produces a new `BrowserUiState`; the whole browser chrome recomposes per tick. This is the precedent named in Constitution §V rule 3.

## R9 — Stability: strong skipping plus a stability configuration file

**Decision**: Kotlin 2.3's Compose compiler runs strong skipping by default, so unstable parameters are compared by instance and still skip when the same instance is passed. On top of that:
- `compose_stability.conf` at the repository root lists `kotlin.collections.*`, `java.time.*` and the app's domain models (`com.raumanian.thirtysix.browser.domain.model.*`), which are immutable `data class`es of stable fields.
- UI models introduced here (`HomeShortcut`, `HomeUiState`, `TabCardModel`, …) are `@Immutable`.
- `composeCompiler { }` in `app/build.gradle.kts` sets `stabilityConfigurationFiles` always, and `reportsDestination` / `metricsDestination` only when the Gradle property `composeReports` is present (`./gradlew assembleRelease -PcomposeReports`), so normal builds pay nothing.

**Rejected**: *`kotlinx-collections-immutable`* — a new dependency whose job the stability file already does in a single-module app.

## R10 — Measuring recomposition (Testing Gates 9–10)

**Decision**: Two layers.
1. **Automated, JVM**: `BrowserViewModelTest` asserts that 50 `onProgressChanged` calls emit **zero** new `uiState` values and 50 `loadProgress` values (state isolation is what makes the UI claim true). Similar tests for `ChromeScrollState` (pure class).
2. **Manual, device**: Layout Inspector recomposition counts for the six hot interactions in User Story 6, before (on `main`) and after, recorded in tasks.md "Device Gate Results", plus the compiler report summary.

**Rejected**: *a recomposition-probe hook in production composables* for automated counting — test plumbing in shipped code, and Layout Inspector already gives the number the Constitution asks for.

## R11 — Swipe to close a tab (clarification Q1)

**Decision**: Each tab card has an `Animatable<Float>` horizontal offset driven by `pointerInput { detectHorizontalDragGestures }`. The offset and the matching alpha are read in `graphicsLayer { translationX; alpha }`. Released past 50% of the card width → animate off-screen, then call the existing close action; otherwise spring back. The card's existing close button and its accessibility label are unchanged; a custom accessibility action is not needed because the button is the accessible path. Vertical scrolling of the grid is unaffected because only horizontal drags are consumed.

## R12 — Hub sheet

**Decision**: Material 3 `ModalBottomSheet` (already used by five sheets in the app), restyled through the theme (`sheet` shape, `surfaceContainerLow`, drag handle colour). The sheet's own offset is handled inside the component in the layout phase. `BrowserOverflowMenu` is deleted. Hub content takes only primitive or `@Immutable` parameters (`canGoBack`, `canGoForward`, `hasActiveDownloads`) plus remembered callbacks, so dragging the sheet does not recompose its tiles.

## R13 — Home data

**Decision**: A new `HomeViewModel` with one use case, `ObserveHomeContentUseCase`, which combines:
- open normal tabs from `ObserveAllTabsUseCase`, excluding incognito and the active tab;
- `BookmarkRepository.observeRecent(limit)` — **new DAO query**, `ORDER BY created_at DESC LIMIT :limit`;
- `HistoryRepository.observeRecent(limit)` — **new DAO query**, `ORDER BY visited_at DESC LIMIT :limit` (the `visited_at` index already exists, so this does not scan 10,000 rows the way the full History list does).

It de-duplicates by URL in that priority order and caps shortcuts at 8 and recent rows at 5 (constants in `BrowserLimits`). New queries only — **Room stays at v2**.

**Card art**: the cached tab screenshot (Spec 011 `ScreenshotCache`) when present; otherwise a colour seed computed as the average of the favicon's opaque pixels from `FaviconCache`, off the main thread, memoised per host. No `androidx.palette` dependency.

## R14 — Greeting

**Decision**: `GreetingPeriod.of(LocalTime)`: 05:00–11:59 morning, 12:00–17:59 afternoon, otherwise evening. The ViewModel takes an injected `java.time.Clock` (Hilt `@Provides Clock.systemDefaultZone()`), recomputes on `ON_RESUME`, and does not tick — a greeting changing while the user looks at it would be distracting and costs a timer.

## R15 — Brand assets

**Decision**: Regenerate every representation from the design system's mark (`thirtysix-mark.svg`, gradient `#6C4CF1 → #8F6FFF`) using Spec 017's pipeline (research R7, A10 there): `ic_brand_mark.xml` (static), `drawable-v31/ic_brand_mark.xml` (animated, same name), `ic_brand_mark_mono.xml`, `ic_launcher_foreground.xml` / `ic_launcher_background.xml`, and the five density `.webp` bitmaps for API 24–25. Numerals stay solid paths. The approved source SVG is committed under `specs/019-ui-redesign/assets/`.

## R16 — Window insets

**Decision**: `enableEdgeToEdge()` stays. Every bar applies its own inset: top bars `windowInsetsPadding(WindowInsets.statusBars)`, bottom bar and sheets `WindowInsets.navigationBars`, the Home search bar and address field `imePadding()` where they can be covered. The browser `Scaffold`'s `contentWindowInsets` is set to `WindowInsets(0)` because the bars, not the scaffold, own the insets. Display cutouts are covered by `safeDrawing` on the hero.

## R17 — Strings

**Decision**: New keys only where meaning is new; reuse otherwise (FR-055). Expected new keys (≈30 × 8 locales): three greetings, the Home privacy line, "Recently visited", "See all", three shortcut-kind tags, two hub banners (title + body), "Quick settings" and four tile labels, hub content descriptions, tab chip labels, the incognito page (title, subtitle, four lines, footnote), onboarding "Start browsing". Existing keys removed where their only use disappears (`browser_action_more` and the overflow menu's labels if unused). Lint parity at error severity keeps all 8 locales in step.

## R18 — Delivery order

**Decision**: Implement in the spec's story order, each step leaving the app working: (1) tokens, theme, icons, brand assets, compiler reports and baseline Layout Inspector counts; (2) browsing chrome + progress isolation + bar sliding + bottom bar + hub (US3, US6 core); (3) Home page (US2); (4) Tabs incl. swipe (US4); (5) History/Bookmarks/Downloads/Settings/dialogs (US4); (6) incognito page and onboarding (US5); (7) gates. Taking the chrome before Home means the measurement baseline is captured against the old UI first.

## Summary table

| # | Decision |
|---|---|
| R1 | Home = sentinel `thirtysix:home` via the existing default-URL binding; Home button and back-to-Home are in-memory flags; no schema change |
| R2 | M3 ColorScheme + `@Immutable ThirtySixColors` extended tokens; dynamic color feeds primary, surfaces and hero glow |
| R3 | New Shapes scale; soft and glow shadows drawn in `drawBehind` |
| R4 | Material Symbols Rounded w300 g−25 as individual vectors; remove `material-icons-core` |
| R5 | Glass = translucent fill on all versions; no blur, no Haze |
| R6 | Home = one LazyColumn; mesh in one drawBehind; collapse read in graphicsLayer |
| R7 | `ChromeScrollState` + WebView scroll listener; bars in `offset {}`; WebView shifted in `graphicsLayer` |
| R8 | `loadProgress` StateFlow separate from `uiState`; read only in `drawBehind` |
| R9 | Strong skipping + `compose_stability.conf`; reports behind `-PcomposeReports` |
| R10 | JVM state-isolation tests + Layout Inspector counts |
| R11 | Swipe-to-close with Animatable offset in graphicsLayer, 50% threshold |
| R12 | ModalBottomSheet hub; overflow menu deleted |
| R13 | `ObserveHomeContentUseCase`; two new LIMIT queries; favicon colour average, no palette lib |
| R14 | Greeting from injected Clock, recomputed on resume |
| R15 | Regenerate all brand assets from the violet mark via Spec 017's pipeline |
| R16 | Bars own their insets; scaffold insets zeroed |
| R17 | ≈30 new keys × 8 locales; remove orphaned ones |
| R18 | Theme → chrome → Home → Tabs → libraries/settings → incognito/onboarding → gates |

**New dependencies: none. Removed: `material-icons-core`. Room: stays v2.**
