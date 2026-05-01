---

description: "Implementation tasks for Spec 002 — Clean Architecture Skeleton + Hilt DI"
---

# Tasks: Clean Architecture Skeleton + Hilt DI

**Input**: Design documents from `/specs/002-clean-architecture-skeleton-di/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [quickstart.md](quickstart.md)

**Tests**: Included — Spec 002 explicitly requires unit tests per FR-020 (Result + BaseViewModel coverage ≥80%, SC-005). Skipping is NOT allowed.

**Organization**: Tasks grouped by user story (all P1 — foundation-skeleton; no story can be skipped). US1 (navigation + 7 screens) and US2 (core utilities) can run in parallel after Foundational phase. US3 (CI gates) is verification — depends on US1 + US2 completion.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Different files, no dependencies on incomplete tasks → can run in parallel
- **[Story]**: `[US1]` / `[US2]` / `[US3]` maps to spec.md User Stories
- File paths absolute or repo-relative

## Path Convention

This is an Android single-module project. All source under:
- Production: `app/src/main/kotlin/com/raumanian/thirtysix/browser/`
- Resources: `app/src/main/res/`
- Manifest: `app/src/main/AndroidManifest.xml`
- Test (JVM): `app/src/test/kotlin/com/raumanian/thirtysix/browser/`
- Build files: `app/build.gradle.kts`, `gradle/libs.versions.toml`

---

## Phase 1: Setup (Version Catalog + Plugin Wiring + Day-1 Smoke Test)

**Purpose**: Wire Hilt + KSP + Navigation Compose + Lifecycle deps via version catalog, then verify Kotlin 2.3.21 × Hilt 2.59.2 metadata compatibility BEFORE any other code change.

- [ ] T001 Add 6 new versions to `[versions]` section of `gradle/libs.versions.toml`: `hilt = "2.59.2"`, `ksp = "2.3.7"`, `hiltNavigationCompose = "1.3.0"`, `lifecycle = "2.10.0"`, `navigationCompose = "2.9.8"`, `coroutinesTest = "1.10.2"`. Reference research.md §"Final Version Catalog Additions".
- [ ] T002 Add 6 new library entries to `[libraries]` section of `gradle/libs.versions.toml`: `hilt-android`, `hilt-compiler` (artifact ID is `hilt-compiler` NOT `hilt-android-compiler` — research.md Risk #4), `androidx-hilt-navigation-compose`, `androidx-lifecycle-viewmodel-compose`, `androidx-navigation-compose`, `kotlinx-coroutines-test`. Reference research.md §"Final Version Catalog Additions".
- [ ] T003 Add 2 new plugin entries to `[plugins]` section of `gradle/libs.versions.toml`: `ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }` and `hilt-android = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }`.
- [ ] T004 Apply KSP + Hilt plugins in `app/build.gradle.kts`: add `alias(libs.plugins.ksp)` and `alias(libs.plugins.hilt.android)` to the `plugins { }` block. Do NOT add `kotlin-kapt`.
- [ ] T005 Add 6 new `dependencies { }` entries in `app/build.gradle.kts`: `implementation(libs.hilt.android)`, `ksp(libs.hilt.compiler)` ← KSP not kapt, `implementation(libs.androidx.hilt.navigation.compose)`, `implementation(libs.androidx.lifecycle.viewmodel.compose)`, `implementation(libs.androidx.navigation.compose)`, `testImplementation(libs.kotlinx.coroutines.test)`.
- [ ] T006 Create minimal `app/src/main/kotlin/com/raumanian/thirtysix/browser/ThirtySixApplication.kt` containing only `@HiltAndroidApp class ThirtySixApplication : android.app.Application()` (smoke-test stub — full content added in T010 if needed).
- [ ] T007 Update `app/src/main/AndroidManifest.xml` `<application>` element to add attribute `android:name=".ThirtySixApplication"`.
- [ ] T008 **GATE — Day-1 smoke test (research.md Risk #1)**: run `./gradlew :app:kspDebugKotlin --info 2>&1 | tee /tmp/hilt-smoke.log`, then `grep -E "metadata version|kotlinx.metadata" /tmp/hilt-smoke.log`. PASS = grep returns no matches AND command exit 0. If FAIL → apply Risk #1 fallback ladder in research.md (kapt → Kotlin 2.2.20 downgrade → hold). DO NOT proceed to Phase 2 if this gate fails. Record outcome in PR description.

**Checkpoint**: Hilt + KSP + Kotlin 2.3.21 verified compatible. Build infrastructure ready for application code.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Verify the Hilt-aware Application class wires correctly and `assembleDebug` succeeds before any feature code lands.

**⚠️ CRITICAL**: No US1 or US2 work can begin until this phase passes.

- [ ] T009 Run `./gradlew :app:assembleDebug` and verify `BUILD SUCCESSFUL`. Hilt graph compilation must produce no "missing binding" / "scope" errors with the empty `ThirtySixApplication`. (FR-004 baseline check.)
- [ ] T010 Verify resolved KSP artifact via `./gradlew :app:dependencies --configuration kspDebugKotlin | grep -E "hilt-compiler|hilt-android-compiler"`. PASS = output contains `com.google.dagger:hilt-compiler:2.59.2`. FAIL (any line containing `hilt-android-compiler`) → fix `libs.versions.toml` library entry artifact ID. (research.md Risk #4 mitigation.)
- [ ] T011 Verify resolved `lifecycle-viewmodel-compose` version via `./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep "lifecycle-viewmodel-compose"`. PASS = `2.10.0` is the resolved version (research.md Risk #3). If different, add `resolutionStrategy.eachDependency` clamp in `app/build.gradle.kts`.

**Checkpoint**: Hilt graph valid, correct artifacts resolved. US1 and US2 may now start in parallel.

---

## Phase 3: User Story 1 — App khởi động chạm được 7 placeholder screen (Priority: P1) 🎯 MVP

**Goal**: Cài app debug → MainActivity render `BrowserScreen` placeholder. Code có thể navigate đến 6 destination còn lại; mỗi cái render đúng placeholder.

**Independent Test**: Per quickstart.md Step 6 + 8 — `./gradlew :app:installDebug` rồi `adb shell am start ...` → app mở hiển thị "Browser"; instrumented test (manual local, không gate CI) verify 7/7 destinations reachable.

### Implementation for User Story 1

- [ ] T012 [P] [US1] Add 7 placeholder string resources to `app/src/main/res/values/strings.xml`: `<string name="browser_screen_placeholder">Browser</string>`, `tabs_screen_placeholder` → "Tabs", `bookmarks_screen_placeholder` → "Bookmarks", `history_screen_placeholder` → "History", `downloads_screen_placeholder` → "Downloads", `settings_screen_placeholder` → "Settings", `onboarding_screen_placeholder` → "Onboarding". (FR-015. EN only — Spec 004 sẽ thêm 7 locale.)
- [ ] T013 [P] [US1] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/navigation/AppDestination.kt` containing `sealed class AppDestination(val route: String)` with 7 `data object` children: `Browser → "browser"`, `Tabs → "tabs"`, `Bookmarks → "bookmarks"`, `History → "history"`, `Downloads → "downloads"`, `Settings → "settings"`, `Onboarding → "onboarding"`. (FR-011, FR-014.)
- [ ] T014 [P] [US1] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreen.kt` — `@Composable fun BrowserScreen()` rendering single `Text(stringResource(R.string.browser_screen_placeholder), style = MaterialTheme.typography.headlineMedium)`. (FR-013, no inline TextStyle.)
- [ ] T015 [P] [US1] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabsScreen.kt` — same pattern as T014, key `tabs_screen_placeholder`.
- [ ] T016 [P] [US1] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/bookmarks/BookmarksScreen.kt` — same pattern, key `bookmarks_screen_placeholder`.
- [ ] T017 [P] [US1] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/history/HistoryScreen.kt` — same pattern, key `history_screen_placeholder`.
- [ ] T018 [P] [US1] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/downloads/DownloadsScreen.kt` — same pattern, key `downloads_screen_placeholder`.
- [ ] T019 [P] [US1] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/settings/SettingsScreen.kt` — same pattern, key `settings_screen_placeholder`.
- [ ] T020 [P] [US1] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/onboarding/OnboardingScreen.kt` — same pattern, key `onboarding_screen_placeholder`.
- [ ] T021 [US1] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/navigation/AppNavGraph.kt` — `@Composable fun AppNavGraph(navController: NavHostController = rememberNavController(), startDestination: String = AppDestination.Browser.route)` with `NavHost` wiring 7 `composable(AppDestination.<X>.route) { <X>Screen() }` calls. Depends on T013–T020. (FR-012.)
- [ ] T022 [US1] Rewrite `app/src/main/kotlin/com/raumanian/thirtysix/browser/MainActivity.kt`: change to `@AndroidEntryPoint class MainActivity : ComponentActivity()` with `onCreate` calling `setContent { ThirdtySixBrowserTheme { AppNavGraph() } }`. Theme name `ThirdtySixBrowserTheme` (typo) intentionally retained — Spec 003 will rename. Depends on T021. (FR-002.)

**Checkpoint US1**: `./gradlew :app:installDebug` → app khởi động → BrowserScreen visible. Manual `navController.navigate(...)` (via instrumented test or temp dev shortcut) reaches all 7 destinations.

---

## Phase 4: User Story 2 — Core utilities ready for consumption (Priority: P1)

**Goal**: `Result<T>`, `AppError`, `DispatcherProvider` (Hilt-injectable), `BaseViewModel.launchSafely(onError, block)` đầy đủ với type-based exception mapping. Spec sau import + extend ngay.

**Independent Test**: Per quickstart.md Step 9 — `./gradlew :app:testDebugUnitTest` xanh; cụ thể `ResultTest.*` và `BaseViewModelTest.*` pass; coverage 2 file utility ≥ 80%.

### Implementation for User Story 2

- [ ] T023 [P] [US2] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/core/result/Result.kt` containing `sealed class Result<out T>` with `data class Success<T>(val data: T) : Result<T>()` and `data class Error(val throwable: Throwable, val message: String? = null) : Result<Nothing>()`, plus top-level inline extensions `fun <T, R> Result<T>.map(transform: (T) -> R): Result<R>` (Success → `Success(transform(data))`, Error → preserve) and `fun <T, R> Result<T>.fold(onSuccess: (T) -> R, onError: (Throwable) -> R): R`. NO `Loading` state per spec Clarification Q1. (FR-005, FR-006.)
- [ ] T024 [P] [US2] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/core/error/AppError.kt` containing `sealed class AppError(open val throwable: Throwable)` with 3 `data class` children carrying `override val throwable`: `Network`, `Database`, `Unknown`; plus `companion object { fun from(throwable: Throwable): AppError = when (throwable) { is java.io.IOException -> Network(throwable); is android.database.sqlite.SQLiteException -> Database(throwable); else -> Unknown(throwable) } }`. (FR-007, FR-009a mapping rule.)
- [ ] T025 [P] [US2] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/core/dispatcher/DispatcherProvider.kt` declaring `interface DispatcherProvider { val main: CoroutineDispatcher; val io: CoroutineDispatcher; val default: CoroutineDispatcher; val unconfined: CoroutineDispatcher }`. (FR-008.)
- [ ] T026 [P] [US2] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/core/dispatcher/DefaultDispatcherProvider.kt` containing `class DefaultDispatcherProvider @Inject constructor() : DispatcherProvider` with all 4 properties returning corresponding `Dispatchers.*`. (FR-008.)
- [ ] T027 [US2] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/core/di/DispatcherModule.kt` with `@Module @InstallIn(SingletonComponent::class) abstract class DispatcherModule { @Binds @Singleton abstract fun bindDispatcherProvider(impl: DefaultDispatcherProvider): DispatcherProvider }`. Depends on T025, T026. (FR-008 Hilt provision; per Clarification Q4, `core/di/` placement valid.)
- [ ] T028 [US2] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/core/base/BaseViewModel.kt` — `abstract class BaseViewModel : ViewModel()` exposing `protected fun launchSafely(onError: (AppError) -> Unit = {}, block: suspend CoroutineScope.() -> Unit): Job` running `block` on `viewModelScope.launch { try { block() } catch (e: CancellationException) { throw e } catch (e: Throwable) { onError(AppError.from(e)) } }`. Depends on T024 (AppError). (FR-009, FR-009a; CancellationException re-thrown per clarification Q3.)
- [ ] T029 [P] [US2] Create `app/src/test/kotlin/com/raumanian/thirtysix/browser/core/result/ResultTest.kt` with at minimum 4 JUnit tests: `map_success_appliesTransform()` asserting `Result.Success(5).map { it * 2 } == Result.Success(10)`; `map_error_preservesError()` asserting `Result.Error(IOException()).map { fail("not called") }` returns same Error; `fold_success_callsOnSuccess()`; `fold_error_callsOnError()`. Depends on T023. (FR-020 Result test requirement.)
- [ ] T030 [P] [US2] Create `app/src/test/kotlin/com/raumanian/thirtysix/browser/core/base/BaseViewModelTest.kt` with `@Before Dispatchers.setMain(StandardTestDispatcher())` / `@After Dispatchers.resetMain()` and 4 tests: `launchSafely_ioException_mapsToNetworkError()`, `launchSafely_sqliteException_mapsToDatabaseError()`, `launchSafely_genericException_mapsToUnknown()`, `launchSafely_cancellation_isRethrown()`. Each constructs a `FakeViewModel : BaseViewModel()`, calls `launchSafely(onError = { capturedError = it }) { throw <E>() }`, advances test scheduler, asserts `capturedError` is the expected `AppError` subclass (or in cancellation case, `CancellationException` propagates and `capturedError` stays null). Depends on T028, T024. (FR-020 BaseViewModel test requirement.)

**Checkpoint US2**: All 4 utility files exist + 8 tests pass via `./gradlew :app:testDebugUnitTest --tests "*ResultTest" --tests "*BaseViewModelTest"`. Coverage ≥ 80% for `Result.kt` + `BaseViewModel.kt`.

---

## Phase 5: User Story 3 — CI quality gates pass (Priority: P1)

**Goal**: 5/6 CI job xanh + 1 instrumented-test skipped (`if: false` giữ nguyên). Lint + Detekt + ktlint clean. 16KB compliance hold (no new `.so`).

**Independent Test**: Per quickstart.md Step 5 + 11 — local `verify-16kb-alignment.sh` pass; push branch → CI 5/6 xanh + 1 skipped.

### Implementation for User Story 3

- [ ] T031 [US3] Run full local quality-gate sequence: `./gradlew clean :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug detekt ktlintCheck`. All 6 task MUST `BUILD SUCCESSFUL`. Lint zero warnings; Detekt zero violations beyond existing baseline; ktlint zero violations. Depends on US1 + US2 complete. (FR-018.)
- [ ] T032 [US3] Run `bash .specify/scripts/bash/verify-16kb-alignment.sh`. PASS = every `.so` in release APK aligned ≥ `0x4000`. Expected output identical to Spec 001 baseline (only `libandroidx.graphics.path.so` × 4 ABIs). If a new `.so` appeared (transitive surprise), investigate. (FR-019, SC-004.)
- [ ] T033 [US3] Inspect `app/detekt-baseline.xml` diff vs main branch. Goal: zero new entries (FR-021). If Hilt-generated code triggers Detekt rules in baseline-suppressed locations, add justification to PR description listing each new entry and reason. If new feature-code violations appear, fix in source instead of suppressing.
- [ ] T034 [US3] Push branch `002-clean-architecture-skeleton-di` to GitHub: `git push -u origin 002-clean-architecture-skeleton-di`. Open draft PR. Wait for GitHub Actions CI run; verify scoreboard: build ✅, unit-test ✅, lint ✅, static-analysis ✅, verify-16kb ✅, instrumented-test ⏭ skipped. (SC-003.)
- [ ] T034a [US3] APK size delta verification: capture release APK size with `stat -f %z app/build/outputs/apk/release/app-release.apk` (macOS) or `stat -c %s ...` (Linux). Compare with Spec 001 baseline (capture from `git show <spec-001-merge-commit>` if not already recorded). Record actual delta in PR description; PASS = delta ≤ 1MB (1048576 bytes). (SC-007.)

**Checkpoint US3**: CI green; PR ready for human review.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Update agent context + tracking docs with Spec 002 completion.

- [ ] T035 [P] Update `CLAUDE.md` "Recent Changes" section: prepend a new entry `2026-XX-XX: ✅ **Spec 002 done** — Clean Architecture skeleton + Hilt DI wired (Hilt 2.59.2 via KSP 2.3.7, Navigation Compose 2.9.8, lifecycle-viewmodel-compose 2.10.0); 4 core utilities (Result/AppError/DispatcherProvider/BaseViewModel with type-based exception mapping); 7 placeholder Screens via AppDestination sealed class. Day-1 Hilt smoke test outcome: <KSP-OK or kapt-fallback-applied>.` Replace `<KSP-OK or kapt-fallback-applied>` with actual outcome from T008.
- [ ] T036 [P] Update `.claude/claude-app/sdd-roadmap.md` Spec 002 row in "Spec List (v1.0)" table from `⬜` to `✅ Done <YYYY-MM-DD>` (use merge date).
- [ ] T037 [P] Update `.claude/claude-app/project-context.md` "Key Decisions Log" with new dated section "### YYYY-MM-DD — Spec 002 hoàn tất" listing actual version pins used + Day-1 smoke test result + any fallback applied.
- [ ] T038 Run `quickstart.md` end-to-end one more time post-merge to verify nothing regressed in main. Document outcome on PR.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 → T002 → T003 → T004 → T005 → T006 → T007 → **T008 GATE**. Sequential — each task depends on previous file/section state.
- **Foundational (Phase 2)**: After T008 passes. T009 → T010 (parallel possible) → T011 (parallel possible). T010 + T011 [P] together.
- **Phase 3 (US1)** + **Phase 4 (US2)**: After Phase 2 complete. **Both can run in parallel** — different files, no cross-dependency. Single dev: do US2 first (utility code shorter), then US1 (more files but mechanical).
- **Phase 5 (US3)**: After both US1 and US2 complete.
- **Phase 6 (Polish)**: After PR merged. T035 + T036 + T037 [P] together.

### Within User Story 1

- T012 (strings.xml) parallel with T013 (AppDestination) — independent files
- T013 + T014–T020 (7 Screens) parallel — all independent files
- T021 (AppNavGraph) depends on T013 + T014–T020 (imports them)
- T022 (MainActivity rewrite) depends on T021

### Within User Story 2

- T023 (Result) + T024 (AppError) + T025 (DispatcherProvider interface) parallel
- T026 (DefaultDispatcherProvider impl) depends on T025
- T027 (DispatcherModule) depends on T025 + T026
- T028 (BaseViewModel) depends on T024 (uses AppError.from)
- T029 (ResultTest) depends on T023, parallel with T030
- T030 (BaseViewModelTest) depends on T028 + T024

### Parallel Opportunities

- **Phase 3 [P] burst**: T012, T013, T014, T015, T016, T017, T018, T019, T020 — all 9 independent files (after Phase 2)
- **Phase 4 [P] bursts**:
  - First burst: T023, T024, T025 (3 files in parallel)
  - Second burst: T026 (after T025) — single
  - Third burst: T029, T030 (after T023+T028) — 2 tests in parallel
- **Phase 6 [P] burst**: T035, T036, T037 (3 doc updates in parallel)

---

## Parallel Example: Phase 3 (User Story 1)

Once Phase 2 passes, single dev can knock these out one after another (each ~2-3 minutes):

```text
1. T012 — strings.xml (1 file edit, 7 entries)
2. T013 — AppDestination.kt (1 sealed class, 7 objects)
3. T014–T020 — 7 placeholder Screens (each ~5 lines, mechanical copy)
4. T021 — AppNavGraph.kt (NavHost + 7 composable() calls)
5. T022 — MainActivity rewrite
```

For multi-dev (unlikely solo Xbism3, documented for completeness):
- Dev A: T012 + T013 + T021 + T022 (navigation backbone)
- Dev B: T014, T015, T016 (3 Screens)
- Dev C: T017, T018, T019, T020 (4 Screens)

---

## Implementation Strategy

### MVP-first (single-dev recommended path)

1. **Phase 1 Setup** — T001 → T008. **GATE T008 BLOCKING**: if Day-1 smoke test fails, stop, apply fallback (kapt or Kotlin downgrade) before continuing. Document outcome.
2. **Phase 2 Foundational** — T009 → T010 + T011.
3. **Phase 4 US2 first** (utilities — purely backend code, shortest path) — T023 → T024 → T025 → T026 → T027 → T028 → T029 → T030.
4. **Phase 3 US1** (navigation + 7 placeholders) — T012 → T013 → T014–T020 → T021 → T022.
5. **Phase 5 US3** (CI gates) — T031 → T032 → T033 → T034.
6. **Phase 6 Polish** — T035 + T036 + T037 → T038.

### Why US2 before US1

- US2 contains all the utility code referenced by Constitution §IV — having `BaseViewModel` ready means Spec 003+ ViewModels have a parent class to extend.
- Placeholder Screens (US1) are mechanical and can be cranked through quickly.
- If implementation runs into time pressure, US2 done = Spec 003 unblocked even before navigation work merges.

### Bail-out checkpoints

- **After T008**: if Day-1 smoke test fails AND fallback ladder also fails, hold spec — file Dagger issue. Do NOT downgrade silently.
- **After Phase 4**: utility coverage report ready; if <80%, add tests before proceeding to US3.
- **After T031**: if any Detekt/lint violation, fix in source (NOT suppress) before T032.

---

## Notes

- `[P]` means different file + no dep on incomplete task → safe parallel.
- `[Story]` label maps to spec.md US1/US2/US3.
- Every task has explicit file path and concrete action — no "implement feature X" vagueness.
- CancellationException re-throw in T028 BaseViewModel is non-negotiable — drops cancellation semantics if missed.
- Artifact ID `hilt-compiler` (NOT `hilt-android-compiler`) in T002 + T010 — wrong ID compiles silently but produces zero Hilt code.
- Theme `ThirdtySixBrowserTheme` typo retained on purpose (T022) — Spec 003 rewrites.
- Re-enable `instrumented-test` CI job is OUT OF SCOPE — Spec 007 or 011 trigger.
- Commit cadence: per logical group (T001-T005 setup; T006-T008 smoke test; each US phase; polish).
