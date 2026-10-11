---
description: "Task list for Spec 020 — Default Browser & Entry Points"
---

# Tasks: Default Browser & Entry Points

**Input**: Design documents from `specs/020-default-browser-entry-points/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: REQUIRED — Constitution v1.5.0 §VI names "incoming-link and intent handling
(Spec 020)" in the mandatory unit-test list. Every row of contracts C2's tables is a test case.

**Organization**: by user story. US1 and US2 are both P1; US1 is the MVP.

## Path Conventions

- `MAIN` = `app/src/main/kotlin/com/raumanian/thirtysix/browser`
- `TEST` = `app/src/test/kotlin/com/raumanian/thirtysix/browser`
- `ATEST` = `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser`
- `RES` = `app/src/main/res`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: constants, strings and the one explicit dependency declaration every story uses.

- [ ] T001 [P] Add constants to `MAIN/core/constants/BrowserLimits.kt`: `MAX_INCOMING_TEXT_LENGTH: Int = 2_048` ("text longer than 2,048 characters is cut to the first 2,048", FR-013), `INCOMING_DUPLICATE_WINDOW_MS: Long = 2_000L` ("within 2 seconds", FR-007), `DEFAULT_BROWSER_INVITE_MIN_LAUNCHES: Int = 3` ("launched at least 3 times", FR-024), each with a KDoc naming its FR
- [ ] T002 [P] Add to `MAIN/core/constants/StorageKeys.kt`: `DEFAULT_BROWSER_INVITE_DISMISSED = booleanPreferencesKey("default_browser_invite_dismissed")` and `APP_LAUNCH_COUNT = intPreferencesKey("app_launch_count")`, extending the schema-evolution comment (data-model §8: defaults `false` / `0`, no migration)
- [ ] T003 [P] Add `EMBEDDED_WEB_URL_REGEX: String = "https?://[^\\s]+"` (used case-insensitively) to `MAIN/core/constants/UrlPatterns.kt` with a KDoc citing research R4
- [ ] T004 [P] Add the 12 string keys of contracts C3.6 to `RES/values/strings.xml` and translations to `RES/values-{vi,de,ru,ko,ja,zh,fr}/strings.xml` (brand "ThirtySix" stays Latin in every locale, Spec 004 R6)
- [ ] T005 [P] Declare `androidx-lifecycle-runtime-compose` on the **existing** `lifecycle` version key in `gradle/libs.versions.toml` and add `implementation(libs.androidx.lifecycle.runtime.compose)` in `app/build.gradle.kts`; confirm with `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` that the resolved version is unchanged (2.10.0, research R12, §IX)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the activity-to-UI hand-off that every story uses (manifest, intent reader, relays, entry view model, navigation).

**⚠️ CRITICAL**: no user-story work begins until this phase is complete.

- [ ] T006 Move `classifyAddressBarInput` and `AddressBarSubmitResult` **unchanged** from `MAIN/presentation/browser/AddressBarInputClassifier.kt` and `MAIN/presentation/browser/AddressBarSubmitResult.kt` into `MAIN/domain/model/AddressInput.kt` (make it `public`), update the import in `MAIN/presentation/browser/BrowserViewModel.kt`, delete the two old files, and move `TEST/presentation/browser/AddressBarInputClassifierTest.kt` to `TEST/domain/model/AddressInputTest.kt` with no assertion changes (research R4)
- [ ] T007 [P] Create `MAIN/domain/model/IncomingRequest.kt` with sealed `IncomingRequest` (`OpenAddress(raw)`, `InterpretText(raw)`, `SearchText(raw)`), sealed `IncomingTarget` (`Load(url)`, `Search(text)`, `Rejected`, `Nothing`) and sealed `OpenOutcome` (`OpenedInNewTab(tabId)`, `OpenedInExistingTab(tabId, url)`, `Duplicate`, `Failed`) exactly as data-model §1–§3
- [ ] T008 [P] Create `MAIN/core/time/MonotonicClock.kt` (`fun interface MonotonicClock { fun nowMs(): Long }`) and its `SystemClock.elapsedRealtime()` implementation `MAIN/data/platform/SystemMonotonicClock.kt` (research R6 duplicate window)
- [ ] T009 [P] Create `MAIN/presentation/entry/IncomingRequestRelay.kt` with three `@Singleton` classes: `IncomingRequestRelay` (`MutableStateFlow<IncomingRequest?>`, `submit`, `take()` that returns and clears — data-model §4), `ActiveTabNavigationRelay` (`MutableSharedFlow<String>`, `replay = 0`, `extraBufferCapacity = 1`, `DROP_OLDEST` — data-model §5), `EntryMessageRelay` (`MutableSharedFlow<Int>` of string ids, same policy — data-model §5a) and `ExternalTabRegistry` (`add`, `remove`, `contains`, `StateFlow<Set<Long>>` — data-model §6)
- [ ] T010 [P] Create `MAIN/presentation/entry/IncomingIntentReader.kt`: `fun read(intent: Intent?): IncomingRequest?` mapping `ACTION_VIEW` `dataString` → `OpenAddress`, `ACTION_SEND` `EXTRA_TEXT` → `InterpretText`, `ACTION_PROCESS_TEXT` `EXTRA_PROCESS_TEXT` → `SearchText`, `ACTION_WEB_SEARCH` `SearchManager.QUERY` → `SearchText`; anything else, a missing extra or a non-`CharSequence` extra → `null`; never throws (contracts C1); no logging (INV-9)
- [ ] T011 [P] Write `TEST/presentation/entry/IncomingIntentReaderTest.kt` (Robolectric): one case per action above, plus unknown action, `SEND` without extra, `SEND` with a non-text extra, `VIEW` with null data → `null`
- [ ] T012 Update `app/src/main/AndroidManifest.xml` per contracts C1: `android:launchMode="singleTask"` and the `APP_BROWSER`, `VIEW` (http + https only, no host), `SEND` text/plain, `PROCESS_TEXT` (label `@string/entry_process_text_label`) and `WEB_SEARCH` filters, each with a comment citing its FR; no new `<uses-permission>` (FR-028)
- [ ] T013 Write `TEST/manifest/EntryPointManifestTest.kt` (Robolectric `PackageManager`/merged manifest): `MainActivity` is `singleTask`; resolves `VIEW http://x` and `VIEW https://x` with `BROWSABLE`; does **not** resolve `VIEW` for `file:`, `content:`, `javascript:`, `intent:`, `data:`, `market:` (C1-1, FR-002)
- [ ] T014 [P] Create `MAIN/presentation/entry/PendingRequestState.kt` (encode an `IncomingRequest` as two strings `kind` + `raw` and decode them back; unknown kind → `null`) with bundle keys added to `MAIN/core/constants/NavArgs.kt`, and `TEST/presentation/entry/PendingRequestStateTest.kt` round-tripping all three variants plus an unknown kind (research R3)
- [ ] T015 Update `MAIN/MainActivity.kt`: inject `IncomingRequestRelay` and `IncomingIntentReader`; in `onCreate` hand `intent` to the relay only when `savedInstanceState == null` and `intent.flags` lacks `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`; override `onNewIntent` to `setIntent(intent)` then hand it over (research R3, INV-3); in `onSaveInstanceState` write an **unconsumed** relay request via `PendingRequestState`, and in `onCreate` with a bundle restore it **only if the relay is empty** (process death, FR-019, contracts C1). Keep `installSplashScreen()` as the first statement (Spec 017 INV-11)
- [ ] T016 Create `MAIN/presentation/entry/EntryPointViewModel.kt` skeleton (`@HiltViewModel`): collects `IncomingRequestRelay` combined with `ObserveUserSettingsUseCase().map { it.isOnboardingCompleted }`; does nothing while onboarding is incomplete (INV-4); exposes one-shot `events: SharedFlow<EntryEvent>` (`ShowBrowser` only) with the project's `replay = 0 / capacity = 1 / DROP_OLDEST` policy; user-facing messages go to `EntryMessageRelay`, never through `events` (research R5). Resolution/opening is wired in US1 (T022)
- [ ] T017 Update `MAIN/presentation/navigation/AppNavGraph.kt`: obtain the **single** `EntryPointViewModel` with `hiltViewModel()` outside the `NavHost` (activity-scoped; no destination may create another) and, in a `LaunchedEffect`, on `ShowBrowser` call `navController.navigate(AppDestination.Browser.route) { popUpTo(AppDestination.Browser.route) { inclusive = false }; launchSingleTop = true }` (FR-005); never navigate while the current route is `Onboarding` (Spec 018 routes out itself)
- [ ] T018 Create `MAIN/di/DefaultBrowserModule.kt` binding `MonotonicClock` → `SystemMonotonicClock` (`@Singleton`); the relays are `@Singleton @Inject constructor` classes and need no binding (controller/repository bindings are added in US2, T036/T039)

**Checkpoint**: an incoming intent reaches `EntryPointViewModel` exactly once and only after onboarding; `./gradlew testDebugUnitTest lintDebug` green.

---

## Phase 3: User Story 1 — Open a link from another app (Priority: P1) 🎯 MVP

**Goal**: an http/https link from any app opens in a new normal tab in the single running instance; Back on that untouched tab returns to the sender.

**Independent Test**: quickstart G1–G5, G7–G10 on the Galaxy A55: `am start -a VIEW -d https://example.org/a -p $PKG` → new active tab; 20 links → 1 recents entry; Back → sender; rejections show the message.

### Tests for User Story 1

- [ ] T019 [P] [US1] Write `TEST/domain/usecase/ResolveIncomingRequestUseCaseOpenAddressTest.kt` covering every `OpenAddress` row of contracts C2 (valid https with query, upper-case scheme, no host ×2, unparseable, the 8 rejected schemes, empty, longer than 2,048 → `Rejected`)
- [ ] T020 [P] [US1] Write `TEST/domain/usecase/OpenIncomingRequestUseCaseTest.kt` with `FakeTabRepository`, `FakeIncognitoTabRepository` and a fake `MonotonicClock`, covering every `OpenIncomingRequestUseCase` row of contracts C2: new tab under the cap; same URL at 2,000 ms → `Duplicate`, at 2,001 ms → opened; different URL inside the window → opened; 50 tabs → `OpenedInExistingTab(id of max lastActiveAt)` + `switchActiveTab` called + no tab created; incognito repository never called (INV-1, INV-5, INV-6); non-cap repository error → `Failed`
- [ ] T021 [P] [US1] Write `TEST/presentation/entry/EntryPointViewModelTest.kt` (Turbine): onboarding incomplete → nothing emitted and relay value kept; onboarding completes → `ShowBrowser`; `Rejected` → `ShowBrowser` + `EntryMessageRelay` emits `R.string.entry_link_rejected`; `Nothing` → `ShowBrowser` only; `OpenedInNewTab(id)` → `ExternalTabRegistry.contains(id)`; `OpenedInExistingTab` → `EntryMessageRelay` emits `R.string.entry_tab_limit_reused` + URL on `ActiveTabNavigationRelay` and registry **not** updated (INV-7); relay cleared on consumption so a resubscription opens nothing twice (INV-3)

### Implementation for User Story 1

- [ ] T022 [US1] Create `MAIN/domain/usecase/ResolveIncomingRequestUseCase.kt` implementing the `OpenAddress` rules of contracts C2 with `java.net.URI` (scheme `http`/`https` case-insensitive, non-empty host, ≤ `MAX_INCOMING_TEXT_LENGTH`), returning `Rejected` otherwise; `InterpretText`/`SearchText` branches are added in US3/US4 (temporarily `Nothing`)
- [ ] T023 [US1] Create `MAIN/domain/usecase/OpenIncomingRequestUseCase.kt`: `Search` → `BuildSearchUrlUseCase`; duplicate window per final URL using `MonotonicClock` and `INCOMING_DUPLICATE_WINDOW_MS`; `CreateTabUseCase(url)`; on `MaxTabsReachedException` pick the normal tab with the highest `lastActiveAt` from `TabRepository`'s current list and call `SwitchActiveTabUseCase` → `OpenedInExistingTab`; never touches `IncognitoTabRepository` (research R6, FR-017, FR-018)
- [ ] T024 [US1] Complete `MAIN/presentation/entry/EntryPointViewModel.kt` per contracts C2's `EntryPointViewModel` table: `relay.take()` first, then resolve → open → registry/relay updates → events
- [ ] T025 [US1] Update `MAIN/presentation/browser/BrowserScreen.kt`: collect `ActiveTabNavigationRelay` in a `LaunchedEffect(Unit)` and call `webViewActions.loadUrl(url)` (research R6); collect `EntryMessageRelay` in a second `LaunchedEffect(Unit)` and show each id through the existing `SnackbarHostState` (research R5 — `BrowserScreen` never calls `hiltViewModel<EntryPointViewModel>()`)
- [ ] T026 [US1] Add `fun onBackLeavingExternalTab()` to `MAIN/presentation/browser/BrowserViewModel.kt` that closes the active tab with `CloseTabUseCase` and removes it from `ExternalTabRegistry`; inject `ExternalTabRegistry` and expose `isActiveTabExternal: StateFlow<Boolean>` (registry ∩ active tab id)
- [ ] T027 [P] [US1] Create `MAIN/presentation/util/ContextExt.kt` with `fun Context.findActivity(): Activity?` (unwrap `ContextWrapper`), and switch `MAIN/presentation/onboarding/OnboardingScreen.kt`'s private `findHostActivity` to it
- [ ] T028 [US1] In `MAIN/presentation/browser/BrowserScreen.kt` add a `BackHandler(enabled = !state.canGoBack && isActiveTabExternal && !state.isAddressBarFocused)` composed **after** `HomeBackHandlers` that calls `viewModel.onBackLeavingExternalTab()` then `LocalContext.current.findActivity()?.moveTaskToBack(true)` (FR-006, research R2/R7)
- [ ] T029 [US1] Remove closed tabs from `ExternalTabRegistry` on every close path: `MAIN/domain/usecase/CloseTabUseCase.kt` callers in `MAIN/presentation/tabs/TabsViewModel.kt` (close button, swipe, close all) — or have `ExternalTabRegistry` prune ids missing from `ObserveTabsUseCase` (pick the pruning approach; data-model §6)
- [ ] T030 [US1] Update `FakeTabRepository`/test constructors that build `BrowserViewModel` (`TEST/presentation/browser/*`, `ATEST/presentation/browser/*`) for the new `ExternalTabRegistry` parameter, without changing assertions
- [ ] T031 [P] [US1] Write `TEST/presentation/browser/BrowserViewModelExternalTabTest.kt`: `onBackLeavingExternalTab` closes the active tab and clears its registry entry; `isActiveTabExternal` false for unmarked and reused tabs
- [ ] T032 [US1] Write `ATEST/presentation/entry/IncomingLinkInstrumentedTest.kt` (`@HiltAndroidTest`, `ActivityScenario.launch(Intent(ACTION_VIEW, Uri.parse(<local fixture or example.org>)))` with onboarding marked complete): a new active tab exists with that URL; a second `onNewIntent` delivery adds exactly one more tab; scenario `recreate()` adds none (FR-003, FR-004, FR-008)
- [ ] T033 [US1] Run device gates G1, G2, G3, G4, G5, G7, G8, G9, G10, G18 (quickstart) on the Galaxy A55 release build and record results in "Device Gate Results" below

**Checkpoint**: US1 is a shippable MVP — ThirtySix opens links from other apps correctly.

---

## Phase 4: User Story 2 — Make ThirtySix the default browser (Priority: P1)

**Goal**: a Settings row and a Home invitation (from the 3rd launch, dismissible forever) take the user through the system role request (API 29+) or the default-apps screen (API 24–28); state refreshes on return.

**Independent Test**: quickstart G11–G13: with another default browser, use the Settings row → confirm → row shows "is your default" within 1 s; fresh install shows the card only from launch 3; dismissal sticks over 10 relaunches.

### Tests for User Story 2

- [ ] T034 [P] [US2] Write `TEST/data/platform/AndroidDefaultBrowserControllerTest.kt` (Robolectric, `@Config(sdk = [28])` and `[29]`/`[33]`): role held / not held / role unavailable; API 28 resolve-to-self / resolve-to-other / resolver null; any thrown platform exception → `false`
- [ ] T035 [P] [US2] Write `TEST/data/repository/DefaultBrowserInviteRepositoryImplTest.kt` (JVM `PreferenceDataStoreFactory` + `TemporaryFolder`, Spec 006 pattern): defaults `dismissed = false`, `launchCount = 0`; `recordLaunch` ×5 → 3 (capped); `markInviteDismissed` is sticky and never reverts (INV-8)

### Implementation for User Story 2

- [ ] T036 [US2] Create `MAIN/domain/repository/DefaultBrowserController.kt` (contracts C3.1, total, KDoc in the Spec 016 seam style) and `MAIN/data/platform/AndroidDefaultBrowserController.kt` (API 29+ `RoleManager.isRoleAvailable && isRoleHeld(ROLE_BROWSER)`; API 24–28 `resolveActivity(VIEW https://example.com, MATCH_DEFAULT_ONLY)` package == own; every call `runCatching`); bind it in `MAIN/di/DefaultBrowserModule.kt`
- [ ] T037 [US2] Create `MAIN/domain/repository/DefaultBrowserInviteRepository.kt` + `InviteState` (contracts C3.3)
- [ ] T038 [US2] Add read/write for the two new keys to `MAIN/data/local/datastore/SettingsDataStore.kt` **without** changing `UserSettings` or `SettingsRepository` (research R9; avoids breaking the four settings fakes)
- [ ] T039 [US2] Create `MAIN/data/repository/DefaultBrowserInviteRepositoryImpl.kt` over `SettingsDataStore` (`recordLaunch` caps at `DEFAULT_BROWSER_INVITE_MIN_LAUNCHES`; `markInviteDismissed` writes `true` only) and bind it in `MAIN/di/DefaultBrowserModule.kt`
- [ ] T040 [P] [US2] Create use cases `MAIN/domain/usecase/ObserveDefaultBrowserInviteUseCase.kt`, `DismissDefaultBrowserInviteUseCase.kt`, `RecordAppLaunchUseCase.kt`, `IsDefaultBrowserUseCase.kt`, with tests `TEST/domain/usecase/DefaultBrowserUseCasesTest.kt`
- [ ] T041 [P] [US2] Create `MAIN/presentation/entry/LaunchCountPolicy.kt` (`fun shouldCount(stoppedForConfigChange: Boolean, returningFromOwnSystemScreen: Boolean): Boolean = !stoppedForConfigChange && !returningFromOwnSystemScreen`) and `TEST/presentation/entry/LaunchCountPolicyTest.kt` covering all four combinations (research R9, FR-024a)
- [ ] T042 [P] [US2] Create `MAIN/domain/usecase/SyncDefaultBrowserInviteUseCase.kt` (if `DefaultBrowserController.isDefaultBrowser()` then `markInviteDismissed()`) and `TEST/domain/usecase/SyncDefaultBrowserInviteUseCaseTest.kt`: default → dismissed; not default → untouched; already dismissed → stays dismissed (FR-026, INV-8)
- [ ] T043 [US2] Wire `MAIN/MainActivity.kt` `onStart` (research R9): feed `LaunchCountPolicy` the flags (last `onStop` had `isChangingConfigurations == true`; a `returningFromDefaultBrowserScreen` flag, cleared in the same `onStart`) and call `RecordAppLaunchUseCase` when it returns true; then always run `SyncDefaultBrowserInviteUseCase` (FR-026); expose `fun markLeavingForDefaultBrowserScreen()` for T044
- [ ] T044 [US2] Create `MAIN/presentation/entry/DefaultBrowserRequest.kt`: `@Composable fun rememberDefaultBrowserRequest(onUnavailable: () -> Unit): () -> Unit` per contracts C3.2 (`RoleManager.createRequestRoleIntent(ROLE_BROWSER)` on API 29+ when available and not held, else `Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS`; `ActivityNotFoundException` on both → `onUnavailable()`); calls `markLeavingForDefaultBrowserScreen()` on the host activity before launching
- [ ] T045 [US2] Update `MAIN/presentation/settings/SettingsViewModel.kt`: `isDefaultBrowser: StateFlow<Boolean>` + `refreshDefaultBrowser()`; and `MAIN/presentation/settings/SettingsScreen.kt`: new first section `KEY_SECTION_DEFAULT_BROWSER` + row `KEY_ROW_DEFAULT_BROWSER` built with `ListRow` per contracts C3.4 (clickable with chevron only when not default; `entry_default_browser_unavailable` snackbar on `onUnavailable`); `LifecycleResumeEffect` calls `refreshDefaultBrowser()` (FR-022)
- [ ] T046 [US2] Update `MAIN/presentation/home/HomeUiState.kt` (+ `showDefaultBrowserInvite: Boolean`) and `MAIN/presentation/home/HomeViewModel.kt`: combine `ObserveDefaultBrowserInviteUseCase`, a refreshable default-browser state and `ObserveActiveTabIsIncognitoUseCase` into `show = !dismissed && launchCount >= DEFAULT_BROWSER_INVITE_MIN_LAUNCHES && !isDefault && !incognito` (data-model §8, FR-024); `onInviteDismissed()` calls `DismissDefaultBrowserInviteUseCase` (FR-025); the action does **not** dismiss (FR-027) — a resume refresh that finds ThirtySix is now the default hides the card, and `SyncDefaultBrowserInviteUseCase` records it (FR-026); state derived off the main thread (`flowOn(dispatchers.default)`, Spec 014 trap)
- [ ] T047 [US2] Create `MAIN/presentation/home/DefaultBrowserInviteCard.kt` from the Home card surface + Spec 019 tokens + `RoundIconButton(AppIcons.Close, contentDescription = home_default_browser_invite_dismiss)` with test tags per contracts C3.5; the action uses `rememberDefaultBrowserRequest(onUnavailable = …)` showing `entry_default_browser_unavailable` (FR-023); static content only (§V); and add it to `MAIN/presentation/home/HomePage.kt` as item `KEY_DEFAULT_BROWSER_INVITE` / `TYPE_INVITE` directly after the hero, with `LifecycleResumeEffect` refreshing the default-browser state
- [ ] T048 [P] [US2] Write `TEST/presentation/home/HomeViewModelInviteTest.kt`: all 16 combinations of (dismissed, launchCount ≥ 3, isDefault, incognito) → visible only for (false, true, false, false); dismiss persists dismissal; using the action alone does **not** (declined confirmation keeps the card, FR-027); becoming default hides it and, once recorded, a later "not default" does not bring it back (FR-026)
- [ ] T049 [P] [US2] Write `TEST/presentation/settings/SettingsViewModelDefaultBrowserTest.kt`: refresh picks up a changed controller value; and `ATEST/presentation/settings/SettingsDefaultBrowserRowTest.kt` + `ATEST/presentation/home/DefaultBrowserInviteCardTest.kt` (Compose): row text per state, row not clickable when default, card dismiss/action callbacks, dismiss control has its content description (FR-030)
- [ ] T050 [US2] Run device gates G11 (Galaxy A55), G12 (`TA016_API24`) and G13 and record results

**Checkpoint**: US1 + US2 make ThirtySix a complete default-capable browser.

---

## Phase 5: User Story 3 — Share text or a link to ThirtySix (Priority: P2)

**Goal**: shared text opens the first web address it contains, or searches the text.

**Independent Test**: quickstart G6 (share part): bare address → opened; sentence with address → that address; sentence without → search with the chosen engine.

- [ ] T051 [P] [US3] Write `TEST/domain/usecase/ResolveIncomingRequestUseCaseInterpretTextTest.kt` covering every `InterpretText` row of contracts C2 (whitespace → `Nothing`; bare https; `example.org` via the moved classifier; sentence with one address; two addresses → first with `,` trimmed; `(…)` trimmed; `see file.txt today` → `Search`; `javascript:alert(1)` → `Search`; 5,000 chars → first 2,048)
- [ ] T052 [US3] Implement the `InterpretText` branch in `MAIN/domain/usecase/ResolveIncomingRequestUseCase.kt`: trim, `Nothing` if empty, cut to `MAX_INCOMING_TEXT_LENGTH`; single token + `classifyAddressBarInput` says URL → `Load(target)`; else first `EMBEDDED_WEB_URL_REGEX` match with trailing `.,;:!?)]}'"` trimmed and validated like `OpenAddress` → `Load`; else `Search` (research R4, FR-010, FR-013)
- [ ] T053 [US3] Add a `SEND` case to `ATEST/presentation/entry/IncomingLinkInstrumentedTest.kt`: shared sentence with an address opens a tab on that address
- [ ] T054 [US3] Run the share part of device gate G6 (10 trials with an address, 10 without — SC-007) and record

---

## Phase 6: User Story 4 — Search selected text (Priority: P3)

**Goal**: text from the selection menu or a system web-search request is always searched.

**Independent Test**: quickstart G6 (`PROCESS_TEXT` and `WEB_SEARCH` commands) → searches for "phở" and "weather hanoi" with the chosen engine.

- [ ] T055 [P] [US4] Write `TEST/domain/usecase/ResolveIncomingRequestUseCaseSearchTextTest.kt` covering every `SearchText` row of contracts C2 (whitespace → `Nothing`; `phở`; an https address → `Search`, not `Load` (FR-011); 5,000 chars → first 2,048)
- [ ] T056 [US4] Implement the `SearchText` branch in `MAIN/domain/usecase/ResolveIncomingRequestUseCase.kt` (trim, `Nothing` if empty, cut to `MAX_INCOMING_TEXT_LENGTH`, always `Search`)
- [ ] T057 [US4] Run the `PROCESS_TEXT` (including the selection-menu label in VI/EN on the A55) and `WEB_SEARCH` parts of device gate G6 and record

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T058 [P] Privacy review (FR-016, INV-9): grep the new/changed files for `Log.`/`println`/`Timber` and confirm none print a URL, shared text or query; run device gate G16 (logcat during G1–G6) and record
- [ ] T059 [P] Locales + accessibility gate G14: Settings row, invitation, three messages and the selection-menu label in all 8 locales; DE and RU at 360dp on the A55 (restore `wm density 420` after); TalkBack pass recorded **DEFERRED** to a person, with a code-review check that every new control has a label
- [ ] T060 Compose compiler report (`./gradlew assembleRelease -PcomposeReports --rerun-tasks`): no unexplained non-skippable restartable composables in `DefaultBrowserInviteCard.kt`, `HomePage.kt`, `SettingsScreen.kt` (Testing Gate 9); Layout Inspector count for Home scroll with the card visible recorded or marked "needs a person" (Testing Gate 10)
- [ ] T061 Full automated gates: `./gradlew testDebugUnitTest lintDebug detekt ktlintCheck` then `assembleRelease` (separate invocation — Spec 016 tooling note), 16 KB script, and `connectedDebugAndroidTest` on the A55 after force-stopping the app and clearing its per-app language
- [ ] T062 Constitution Testing Gate 8 — device gate G17: install the release build on `TA016_API36_16K`, run cold start, G1 and G2 there, zero FATAL in logcat; record
- [ ] T063 Gate G15 on the Galaxy A55 release build: link → page visible ≤ 1 s warm / ≤ 2 s cold (median of 10, SC-001); release APK delta vs `main` ≤ 60 KB, zero new `.so` (SC-010); record
- [ ] T064 Update docs: `CLAUDE.md` (Spec Roadmap row 020, Recent Changes, Active Spec, Known Runtime Considerations: `singleTask`, intent handled once, relay/onboarding hold), `.claude/claude-app/sdd-roadmap.md`, `.claude/claude-app/dev-workflow.md`, `.claude/claude-app/project-context.md`
- [ ] T065 Open the PR `020-default-browser-entry-points` → `main` with the gate table, Constitution Check line (10 ✅, §X waiver), the R6 incognito-reuse limitation, APK delta and test counts

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)**: none — T001–T005 all parallel.
- **Foundational (Phase 2)**: needs Phase 1. T006 first (domain move touches `BrowserViewModel`); T007–T011 and T014 parallel; T012 → T013; T015 after T009, T010, T014; T016 after T007, T009; T017 after T016; T018 after T008.
- **US1 (Phase 3)**: needs Phase 2. Tests T019–T021 first (fail), then T022 → T023 → T024; T025–T029 after T024 (T027 before T028); T030 with T026; T032–T033 last.
- **US2 (Phase 4)**: needs Phase 2 only — **independent of US1** (can run in parallel by another person). T034–T035 first; T036–T039 → T040; T041 and T042 alongside them; T043 after T039–T042; then T044–T047; T048–T050 last.
- **US3 (Phase 5)**: needs US1's T022 (the use case file and `Load` validation).
- **US4 (Phase 6)**: needs US1's T022; independent of US3 (different branch of the same file — sequence T052/T056 to avoid conflicts).
- **Polish (Phase 7)**: after the stories that are in scope.

### User-story dependency graph

```text
Setup → Foundational ─┬─> US1 (P1, MVP) ─┬─> US3 (P2)
                      │                  └─> US4 (P3)
                      └─> US2 (P1)  (independent)
                                   └──────────────> Polish
```

## Parallel Execution Examples

**Phase 1**: T001, T002, T003, T004, T005 together.

**Phase 2**: after T006 — T007, T008, T009, T010, T011, T014 together.

**US1 tests**: T019, T020, T021 together (three files).

**US2**: T034 and T035 together; T041 and T042 together; T040 alongside T038–T039; T048 and T049 together.

**US3 / US4 tests**: T051 and T055 together.

## Implementation Strategy

### MVP first (US1)

1. Phases 1–2.
2. Phase 3 (US1) → device gates G1–G5, G7–G10 → **stop and validate**: ThirtySix is a working "Open with" browser.

### Incremental delivery

1. + US2 → default-browser row and invitation (G11–G13).
2. + US3 → Share sheet (G6 share).
3. + US4 → selection menu and web search (G6 search).
4. Polish → G14–G16, docs, PR.

## Notes

- `[P]` = different files, no dependency on an incomplete task.
- Write each story's tests first and see them fail before implementing.
- Force-stop the app and clear its per-app language before every `connectedDebugAndroidTest` on the A55 (Specs 018/019 findings).
- Never report SC-001 from an emulator; the reference is the A55 release build.
- Commit after each task or logical group; never commit with a red gate.

---

## Device Gate Results

*(Filled during implementation — device, build type and date per gate; DEFERRED stated explicitly.)*

| Gate | Result | Notes |
|---|---|---|
| G1 "Open with" | — | |
| G2 Single instance | — | |
| G3 Back to sender | — | |
| G4 Other screens | — | |
| G5 Rejections | — | |
| G6 Share / selection / web search | — | |
| G7 Duplicates & recreation | — | |
| G8 Before onboarding | — | |
| G9 Tab cap | — | |
| G10 Incognito active | — | |
| G11 Default browser, Android 10+ | — | |
| G12 Default browser, Android 7–9 | — | |
| G13 Home invitation | — | |
| G14 Locales & accessibility | — | |
| G15 Speed & size | — | |
| G16 Privacy | — | |
| G17 16 KB emulator (Gate 8) | — | |
| G18 Process death in onboarding | — | |
| TalkBack | — | |
