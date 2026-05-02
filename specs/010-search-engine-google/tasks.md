---

description: "Task list for Spec 010 — Search Engine Google"
---

# Tasks: Search Engine Google (Spec 010)

**Input**: Design documents from `specs/010-search-engine-google/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/SearchEngineRepository.md](contracts/SearchEngineRepository.md), [contracts/BuildSearchUrlUseCase.md](contracts/BuildSearchUrlUseCase.md), [contracts/BrowserViewModel.md](contracts/BrowserViewModel.md), [quickstart.md](quickstart.md)

**Tests**: Tests are INCLUDED — Constitution §VI mandates unit tests for all business logic. Spec 009 baseline 143/143 → ~155 expected after this spec (~12 new). No instrumented tests required for this domain-only refactor (research R8); manual on-device gates per the Spec 008 / 009 deferred pattern.

**Organization**: Tasks are grouped by user story (US1–US3) so each story can be implemented and tested independently. Foundational refactors that span all 3 stories live in Phase 2.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Different file, no dependency on incomplete tasks — safe to run in parallel
- **[Story]**: `[US1]`–`[US3]` for tasks tied to a specific user story; absent for Setup / Foundational / Polish

## Path Conventions

Single-module Android app. All paths below are relative from repo root:

- Production source: `app/src/main/kotlin/com/raumanian/thirtysix/browser/...`
- Unit tests: `app/src/test/kotlin/com/raumanian/thirtysix/browser/...`
- Resources: not modified by this spec (zero new strings)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify the spec-010 branch is current and the working tree is clean before structural changes begin.

- [x] T001 Verify on branch `010-search-engine-google` with clean working tree: run `git status` and `git rev-parse --abbrev-ref HEAD`; abort if dirty or on a different branch.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Add the two new URL templates, extend the `SearchEngine` enum, and stand up the new domain interface + repository implementation + use case + Hilt binding. All 3 user stories (US1, US2, US3) read from these new types — without them, no story phase can begin. **No `BrowserViewModel` rewire here** — that lands in US1 (the integration is itself the US1 deliverable).

**⚠️ CRITICAL**: No user story phase may begin until this phase is complete.

- [x] T002 [P] Extend [app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/UrlConstants.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/UrlConstants.kt) — add TWO new `const val`s next to the existing `GOOGLE_SEARCH_URL_TEMPLATE`: (1) `const val DUCKDUCKGO_SEARCH_URL_TEMPLATE = "https://duckduckgo.com/?q=%s"` with KDoc citing Spec 010 + 2026-05-03 clarification Q1; (2) `const val BING_SEARCH_URL_TEMPLATE = "https://www.bing.com/search?q=%s"` with same KDoc. Update the file-level KDoc to mention "Spec 010 (`search-engine-google`) adds DuckDuckGo + Bing templates alongside Google; selection is owned by `SearchEngineRepositoryImpl`."
- [x] T003 [P] Extend [app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/model/SearchEngine.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/model/SearchEngine.kt) — add two enum entries after the existing `Google("google")`: `DuckDuckGo("duckduckgo")` and `Bing("bing")`. Update the file-level KDoc comment block (currently mentions only Google for v1.0) to reflect the 3 entries shipping in Spec 010 and the unchanged `fromStorageValueOrDefault(...)` semantics. Trailing comma on the last entry retained per project style.
- [x] T004 Create the new domain interface at `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/repository/SearchEngineRepository.kt` per the [contract](contracts/SearchEngineRepository.md). Single `suspend fun buildSearchUrl(query: String): String`. Pure-Kotlin imports only (no Android SDK).
- [x] T005 Create the new repository implementation at `app/src/main/kotlin/com/raumanian/thirtysix/browser/data/repository/SearchEngineRepositoryImpl.kt`. `class @Inject constructor(private val settingsRepository: SettingsRepository)`. Implementation per [data-model.md Entity 4](data-model.md#entity-4--searchenginerepositoryimpl-new-class): (1) `engine = settingsRepository.observeSettings().first().searchEngine`, (2) `encoded = URLEncoder.encode(query, Charsets.UTF_8.name())` — same form as Spec 009 inline (preserves SC-001 byte-identity), (3) `template = when (engine) { Google -> UrlConstants.GOOGLE_SEARCH_URL_TEMPLATE; DuckDuckGo -> UrlConstants.DUCKDUCKGO_SEARCH_URL_TEMPLATE; Bing -> UrlConstants.BING_SEARCH_URL_TEMPLATE }` (exhaustive `when` over closed enum — compiler-checked), (4) `return String.format(template, encoded)`. Imports: `kotlinx.coroutines.flow.first`, `java.net.URLEncoder`, project domain types.
- [x] T006 Create the new use case at `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/BuildSearchUrlUseCase.kt` per the [contract](contracts/BuildSearchUrlUseCase.md). `class @Inject constructor(private val repository: SearchEngineRepository) { suspend operator fun invoke(query: String): String = repository.buildSearchUrl(query) }`. KDoc references Spec 010 + the `ObserveUserSettingsUseCase` precedent.
- [x] T007 Create the new Hilt binding module at `app/src/main/kotlin/com/raumanian/thirtysix/browser/di/SearchEngineModule.kt` per [contracts/SearchEngineRepository.md § Hilt binding](contracts/SearchEngineRepository.md#hilt-binding). `@Module @InstallIn(SingletonComponent::class) abstract class SearchEngineModule { @Binds @Singleton abstract fun bindSearchEngineRepository(impl: SearchEngineRepositoryImpl): SearchEngineRepository }`. Sibling to existing `SettingsModule.kt`.
- [x] T008 Run `./gradlew assembleDebug` to verify (a) the new files compile, (b) Hilt KSP processes the new `@Binds` without error, (c) no stale references break Spec 009. Abort and fix if any error.

**Checkpoint**: Foundation complete. New constants + enum entries + domain interface + impl + use case + Hilt binding wired. Build is green. User story implementation can begin.

---

## Phase 3: User Story 1 — Submit a search query with the default engine (Priority: P1) 🎯 MVP

**Goal**: First-time user (no prior settings) types a query into the address bar, presses Enter; the system uses the Google default engine and routes through the new domain abstraction (instead of Spec 009's inline encoding) to construct the search URL. Google output is byte-identical to Spec 009 (SC-001 non-regression).

**Independent Test**: With a fresh install (no DataStore writes have occurred), submit `kotlin coroutines` from the address bar. Verify the WebView loads `https://www.google.com/search?q=kotlin+coroutines` — byte-identical to what Spec 009 produced. Address bar (hostname-only) shows `www.google.com`.

### Tests for User Story 1 (write before / alongside implementation)

- [x] T009 [P] [US1] Create `app/src/test/kotlin/com/raumanian/thirtysix/browser/data/repository/SearchEngineRepositoryImplTest.kt` with a hand-rolled `FakeSettingsRepository : SettingsRepository` backed by `MutableStateFlow<UserSettings>(UserSettings.DEFAULT)` (research R8 pattern). Add the parameterized test "Google produces byte-identical Spec 009 output for canonical inputs" covering all 10 query inputs from [quickstart.md Gate 6](quickstart.md#gate-6--searchenginerepository--byte-identical-google-output-sc-001). Each assertion: `assertEquals(String.format(UrlConstants.GOOGLE_SEARCH_URL_TEMPLATE, URLEncoder.encode(input, Charsets.UTF_8.name())), repo.buildSearchUrl(input))` — i.e., the actual must equal the expected formula reproduced inline. Uses `runTest` + `kotlinx-coroutines-test`.
- [x] T010 [P] [US1] In the same `SearchEngineRepositoryImplTest`, add the "unknown storageValue falls back to Google" test: drive the fake to emit `UserSettings(searchEngine = SearchEngine.fromStorageValueOrDefault("yandex"), ...)` (which returns `Google`), submit a query, assert the URL matches the Google template. Documents the I1 invariant.
- [x] T011 [P] [US1] Create `app/src/test/kotlin/com/raumanian/thirtysix/browser/domain/usecase/BuildSearchUrlUseCaseTest.kt` with the single delegation test from the [contract](contracts/BuildSearchUrlUseCase.md#test-surface). Uses an inline anonymous `SearchEngineRepository` (no fake class needed for this test).
- [x] T012 [US1] Update `app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModelTest.kt` — every test that constructs `BrowserViewModel` directly now passes a fake `BuildSearchUrlUseCase` as the second constructor argument. Recommended pattern: build a `FakeSearchEngineRepository` (single-method fake), wrap it via `BuildSearchUrlUseCase(repository = fakeRepo)` (positional or named-arg `repository = ...` matching the contract), and pass that into `BrowserViewModel(defaultHomeUrl = "https://example.com", buildSearchUrl = useCase)`. Exercising the full chain via the fake repo is preferred over subclassing the use case (which is `final` — Kotlin default). Existing assertions for the Google query path stay byte-identical (SC-001). Run `./gradlew testDebugUnitTest --tests "*BrowserViewModelTest*"` and confirm all existing tests pass.

### Implementation for User Story 1

- [x] T013 [US1] Modify [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModel.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModel.kt) constructor per [contracts/BrowserViewModel.md § Constructor delta](contracts/BrowserViewModel.md#constructor-delta). Add `private val buildSearchUrl: BuildSearchUrlUseCase` as the second constructor parameter (after the existing `@Named("default_home_url") private val defaultHomeUrl: String`). Remove the `import java.net.URLEncoder` and `import com.raumanian.thirtysix.browser.core.constants.UrlConstants` lines (no longer used after T014). Add `import androidx.lifecycle.viewModelScope`, `import com.raumanian.thirtysix.browser.domain.usecase.BuildSearchUrlUseCase`, `import kotlinx.coroutines.launch`.
- [x] T014 [US1] Rewrite the Query branch of `onAddressBarSubmit` in `BrowserViewModel.kt` per [contracts/BrowserViewModel.md § Method delta](contracts/BrowserViewModel.md#method-delta--onaddressbarsubmit): replace the synchronous `URLEncoder.encode(...) + String.format(GOOGLE_SEARCH_URL_TEMPLATE, ...) + loadUrl(searchUrl)` block with `viewModelScope.launch { val searchUrl = buildSearchUrl(classified.text); loadUrl(searchUrl) }`. The function still returns `true` synchronously for the non-empty Query branch (Spec 009 FR-013a focus/keyboard release). Empty + URL branches unchanged.
- [x] T015 [US1] Update the KDoc on `onAddressBarSubmit` (currently lines 164–175) to reflect Spec 010: remove the "Spec 010 will refactor..." sentence, replace with a sentence noting that the Query branch now dispatches through `BuildSearchUrlUseCase` via `viewModelScope.launch`. Keep the focus/keyboard/`loadUrl` semantics description intact.
- [x] T016 [US1] Run `./gradlew testDebugUnitTest` — every test must pass. New test count expected ≥ Spec 009 baseline (143) + at least 11 (T009 covers ~10 parameterized cases that count as ~6 test methods + T010 + T011 + T012 added cases). If any existing Spec 007/008/009 test fails, the rewrite is wrong; fix before moving on.
- [x] T017 [US1] **MANUAL on-device gate** — on a real Android device or emulator: fresh install the Spec 010 build, submit `kotlin coroutines` from the address bar, observe a Google search-results page renders. Compare the address bar's resolved URL to a Spec 009 build's output for the same input — they MUST be byte-identical. Mirrors [quickstart.md Gate 7](quickstart.md#gate-7--on-device--default-engine-query-us1-sc-003-partial). DEFER to user-device pass per Spec 008/009 precedent.

**Checkpoint**: US1 done — Google query path byte-identical to Spec 009 via the new abstraction. MVP delivered. The system is shippable here even without US2 / US3 because the user-visible behavior is unchanged from Spec 009 + the abstraction is in place for future picker UI.

---

## Phase 4: User Story 2 — Switch search engines and submit a query with the new engine (Priority: P1)

**Goal**: When the persisted engine preference is DuckDuckGo or Bing, address-bar query submissions route to the corresponding engine's search URL — the new abstraction's whole reason to exist.

**Independent Test**: Persist the engine preference as DuckDuckGo via the existing Spec 006 setter, submit `android compose`, verify the WebView loads `https://duckduckgo.com/?q=android+compose`. Repeat with Bing and verify `https://www.bing.com/search?q=android+compose`.

### Tests for User Story 2

- [x] T018 [P] [US2] In `SearchEngineRepositoryImplTest`, add the "DuckDuckGo route produces canonical URL" parameterized test covering 3 representative queries: `android compose` (ASCII), `cà phê sữa` (Vietnamese / non-Latin), `weather & forecast` (special chars). Drive the fake to emit `UserSettings(searchEngine = DuckDuckGo, ...)`. Assert the result starts with `"https://duckduckgo.com/?q="` and contains the URL-encoded query.
- [x] T019 [P] [US2] In `SearchEngineRepositoryImplTest`, add the "Bing route produces canonical URL" parameterized test with the same 3 queries; assert results start with `"https://www.bing.com/search?q="` and contain the encoded query.
- [x] T020 [P] [US2] In `SearchEngineRepositoryImplTest`, add the "engine change between calls switches template" test: call `buildSearchUrl("foo")` with `Google` (assert Google URL); update the fake's `MutableStateFlow` to emit `UserSettings(searchEngine = DuckDuckGo, ...)`; call `buildSearchUrl("foo")` again (assert DuckDuckGo URL). Documents I4 invariant + SC-002 read-on-submit semantics.
- [x] T021 [P] [US2] Extend (or create) `app/src/test/kotlin/com/raumanian/thirtysix/browser/domain/model/SearchEngineTest.kt` to assert `fromStorageValueOrDefault("duckduckgo") == DuckDuckGo`, `fromStorageValueOrDefault("bing") == Bing`, `fromStorageValueOrDefault("google") == Google`, and `fromStorageValueOrDefault("yandex") == Google` (unknown fallback).

### Implementation for User Story 2

US2 has **no production-code change** beyond what Phase 2 + US1 already shipped — the engine-resolution `when` expression in `SearchEngineRepositoryImpl` (T005) already covers all 3 entries. US2 is verification + tests only.

- [x] T022 [US2] Run `./gradlew testDebugUnitTest --tests "*SearchEngineRepositoryImplTest*" --tests "*SearchEngineTest*"`. All 3-engine assertions must pass. If a `when` branch is missing or the encoding diverges per-engine, T005 needs revision.
- [x] T023 [US2] **MANUAL on-device gate** — flip the persisted engine to DuckDuckGo via a debug-build hook (call `SettingsRepository.setSearchEngine(DuckDuckGo)` from any temporary activity hook OR via `adb shell run-as` mutation). Submit `android compose`. Verify WebView loads `https://duckduckgo.com/?q=android+compose`. Repeat with Bing. Mirrors [quickstart.md Gate 8](quickstart.md#gate-8--on-device--switch-engine-to-duckduckgo-us2-sc-002). DEFER to user-device pass.

**Checkpoint**: US2 done — engine switch takes effect on the next submit. SC-002 satisfied.

---

## Phase 5: User Story 3 — Query with special characters and non-Latin scripts encodes correctly (Priority: P2)

**Goal**: For each of the 3 engines, submitting a query containing whitespace, punctuation, emoji, or non-Latin scripts (Vietnamese / Japanese / Chinese / Russian / Korean) produces a URL where the query is correctly UTF-8 percent-encoded — engines render results for the original phrase, not corrupted bytes.

**Independent Test**: For each engine × `{cà phê sữa, 東京タワー, weather & forecast}` pair (9 combinations), submit and verify the engine's results page renders for the literal phrase.

### Tests for User Story 3

- [x] T024 [P] [US3] In `SearchEngineRepositoryImplTest`, add the "Unicode + special chars encode correctly across engines" parameterized test covering the table from [quickstart.md Gate 6](quickstart.md#gate-6--searchenginerepository--byte-identical-google-output-sc-001) rows 4 (`cà phê sữa`), 5 (`東京タワー`), 6 (`красная площадь`), 7 (`weather & forecast`), 9 (`🍕 pizza`), 10 (`검색`) — 6 inputs × 3 engines = 18 assertions, structured as 6 test methods each looping over 3 engines. Verifies that the encoding is engine-independent (FR-014 — abstraction owns the encoding) by asserting the encoded substring appears unchanged across all 3 templates.

### Implementation for User Story 3

US3, like US2, has **no production-code change** — the `URLEncoder.encode(query, Charsets.UTF_8.name())` call in T005 already handles all UTF-8 input. US3 verifies that no engine code path bypasses this central encoding step.

- [x] T025 [US3] Run `./gradlew testDebugUnitTest --tests "*SearchEngineRepositoryImplTest*"`. All Unicode + special-char assertions must pass.
- [x] T026 [US3] **MANUAL on-device gate** — for each engine in `{Google, DuckDuckGo, Bing}` × each query in `{cà phê sữa, 東京タワー, weather & forecast}`: persist the engine, submit the query, verify the engine's results page renders for the original phrase (not for a corrupted-encoding string). 9 manual verifications. Mirrors [quickstart.md Gate 9](quickstart.md#gate-9--on-device--special-characters-across-engines-us3-sc-003-full). DEFER to user-device pass.

**Checkpoint**: US3 done — encoding contract verified across all 3 engines × Unicode/special-char inputs. SC-003 fully satisfied.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final verification gates that span all 3 stories. Most are automated quality gates that re-run the full test + lint + build suite; the rest are manual on-device checks aligned with quickstart.md.

- [x] T027 [P] Run `./gradlew lintDebug` — zero warnings (Constitution §III + Spec 010 SC-007). If a `MissingTranslation` / `UnusedResources` / etc. fires, fix before proceeding (none expected — this spec adds zero strings).
- [x] T028 [P] Run `./gradlew detekt` — zero new violations, baseline UNCHANGED from Spec 009. New code (interface + impl + use case + module) follows the existing Spec 006 pattern that already complies.
- [x] T029 [P] Run `./gradlew ktlintCheck` — zero violations. Watch for the `class-signature` rule (project disabled for `@Inject constructor` multi-line params per Spec 006) — the new files follow the same convention.
- [x] T030 Run `./gradlew assembleRelease` and measure APK size: `ls -lh app/build/outputs/apk/release/app-release.apk`. Expected ≤ 2.07 MB (Spec 009 baseline 2.05 MB + 20 KB SC-005 budget). If overrun, document the actual delta + root cause in CLAUDE.md Recent Changes.
- [x] T031 Run the 16 KB CI alignment check from [quickstart.md Gate 5](quickstart.md#gate-5--16-kb-ci-alignment-sc-006): `unzip -p app-release.apk lib/arm64-v8a/lib*.so | objdump -p - | grep LOAD | awk '{print $NF}'`. Every value must be `0x4000` or larger. Zero new `.so` introduced (no new packages) — count must match Spec 009's 28/28 entries. If any new `.so` appears, abort and investigate (could be transitive dependency surprise).
- [x] T032 Re-run Constitution Check from [plan.md](plan.md#constitution-check) post-implementation. Expected 11/11 PASS. Document the Repository → Repository exception ack in the PR description (already captured in Complexity Tracking).
- [x] T033 **MANUAL on-device gate — zero-migration upgrade** (SC-009): on a device with the previous Spec 009 build installed (with `searchEngine = "google"` in DataStore), `./gradlew installDebug` overwriting with Spec 010. Launch and submit a query — Google search renders identically to before upgrade. Mirrors [quickstart.md Gate 10](quickstart.md#gate-10--on-device--zero-migration-upgrade-sc-009). DEFER to user-device pass.
- [x] T034 **MANUAL on-device gate — build-time performance** (SC-010): on a Pixel 5+-class device, instrument or measure submit-to-`loadUrl` time for a query — must be ≤ 50 ms. Mirrors [quickstart.md Gate 11](quickstart.md#gate-11--on-device--build-time-performance-sc-010). DEFER to user-device pass.
- [x] T035 Update [CLAUDE.md](CLAUDE.md) Recent Changes with the Spec 010 implementation summary: implementation date, file count, test count delta, APK size delta vs Spec 009 baseline, 16 KB gate result, Constitution Check result, deferred gates list. Bump Active Spec block status to `✅ Implemented` and roll Spec Roadmap row to `✅ Done` once all manual gates pass (or to `✅ Implemented (manual gates deferred)` if PR opens with gates outstanding — Spec 008 / 009 precedent).
- [ ] T036 Open PR for `010-search-engine-google` → `main`: title `feat: Implement Search Engine Google (Spec 010) refactoring address bar query path through SearchEngineRepository + adding DuckDuckGo + Bing`. Body includes Constitution Check 11/11 PASS, file delta summary, test count delta, APK delta, deferred manual gates list, and the documented Repository → Repository exception ack from Complexity Tracking.

**Checkpoint**: All quality gates green. PR opened. User runs the 3 deferred manual gates (T017, T023, T026, T033, T034) on a real device.

---

## Dependencies

```text
T001 (Setup)
  │
  ├─ T002 [P]  ┐
  ├─ T003 [P]  ├─ all touchable in parallel (different files)
  ├─ T004      │  T002–T003: constants + enum (no inter-file dep)
  │            │  T004: depends on T003 (imports SearchEngine)
  ├─ T005      │  depends on T002 + T003 + T004 (uses all three)
  ├─ T006      │  depends on T004 (imports SearchEngineRepository)
  ├─ T007      │  depends on T004 + T005 (binds impl to interface)
  └─ T008      │  build verification — depends on T002–T007
                ▼
              Phase 2 complete → ALL user stories unblocked
                │
        ┌───────┼───────────────────────────┐
        ▼       ▼                           ▼
       US1     US2                          US3
       T009 [P]      T018 [P]               T024 [P]
       T010 [P]      T019 [P]               (production code already done in T005)
       T011 [P]      T020 [P]               T025  (verify tests)
       T012  (depends on T009/10/11 patterns)
                     T021 [P]
       T013  (BrowserViewModel constructor)
       T014  (depends on T013 — same file)
       T015  (depends on T014 — same file)        (US2 has no prod-code change)
       T016  (verify tests)         T022  (verify tests)
       T017  (manual)                T023  (manual)         T026  (manual)
                                                                    │
                                                                    ▼
                                                            Phase 6 Polish
                                                            T027 [P]–T032 (gates)
                                                            T033, T034 (manual)
                                                            T035 (CLAUDE.md)
                                                            T036 (PR open)
```

### Story-level dependencies

- **US1 → all P2/P3 work**: US1 introduces the `BuildSearchUrlUseCase` constructor injection on `BrowserViewModel`. T013–T015 modify the same file, so they MUST be sequential within US1. After US1, US2 and US3 add tests only — they touch different test files and can run in parallel with each other (T018–T021 + T024 all `[P]`).
- **Foundational independence**: T002 (UrlConstants) and T003 (SearchEngine enum) are different files and can parallelize. T004–T007 are sequential because each new file imports the previous one (interface → impl → use case → module binding).
- **No story-to-story blocking after T013–T015**: US2 and US3 do not modify any production source — only the test file `SearchEngineRepositoryImplTest.kt` (additive, multiple test methods can be added in parallel) and `SearchEngineTest.kt` (T021 only).

### Parallel opportunities

- **Phase 2 setup**: T002 and T003 (different files, no cross-import). T004 must follow T003. T005 must follow T002+T003+T004. T006 must follow T004. T007 must follow T004+T005. T008 is the build gate after all six.
- **Phase 3 (US1) tests**: T009, T010, T011 are all `[P]` (different test files OR independent test methods in the same file, all read-only against new production code).
- **Phase 4 (US2) tests**: T018, T019, T020, T021 — all `[P]`, all in test files that don't conflict.
- **Phase 5 (US3) test**: T024 is `[P]` — single test method addition to an existing test file.
- **Phase 6 polish**: T027 (`lintDebug`), T028 (`detekt`), T029 (`ktlintCheck`) are all `[P]` — independent Gradle tasks. T030–T036 sequential (assembleRelease, then 16KB check on the produced APK, then Constitution review, then docs, then PR).

---

## Implementation Strategy

### MVP scope = Phase 1 + Phase 2 + Phase 3 (US1)

After T001–T017, the abstraction is in place + the Google query path is byte-identical to Spec 009. PR-ready as a pure refactor — user-visible behavior unchanged for the 100% of users who haven't touched the engine setting (which is everyone, since Spec 016's picker UI doesn't exist yet).

### Incremental delivery

- **Increment 1 (US1, P1)**: Refactor Google path through the new abstraction. No new user-visible behavior. Locks in SC-001 byte-identity. (~6 prod files modified or added + ~4 test files = ~150 prod LOC + ~150 test LOC.)
- **Increment 2 (US2, P1)**: Adds verification that DuckDuckGo + Bing routes work. **Pure test addition**; no production-code change beyond what US1+Phase 2 shipped. (~3–4 test methods.)
- **Increment 3 (US3, P2)**: Adds verification that Unicode / special-char inputs encode correctly across all 3 engines. **Pure test addition**. (~6 test methods × 3-engine loop.)
- **Polish**: 9 quality gates (T027–T034) + docs (T035) + PR (T036).

US1 alone is shippable. US2 and US3 are protective tests for the abstraction's contract — not strictly required for v1.0 to function but required for SC-002, SC-003, and Constitution §VI test-coverage compliance.

### Parallel-execution plan (single developer)

Day 1 (~2–3 h):
- T001 → T002+T003 (parallel) → T004 → T005 → T006 → T007 → T008. Foundation done.

Day 2 (~3–4 h):
- T009+T010+T011 (parallel test scaffolding). T012 (BrowserViewModelTest update).
- T013 → T014 → T015 (sequential — same file).
- T016 (run tests). T017 deferred.

Day 3 (~1 h):
- T018+T019+T020+T021 (parallel — pure test additions for US2). T022 (run tests). T023 deferred.

Day 4 (~30 min):
- T024 (US3 test). T025 (run tests). T026 deferred.

Day 5 (~1 h):
- T027+T028+T029 (parallel quality gates). T030 → T031 → T032. T033, T034 deferred.
- T035 (CLAUDE.md). T036 (PR).

Total estimate: ~8–10 hours of focused work + the user's manual gates batch.
