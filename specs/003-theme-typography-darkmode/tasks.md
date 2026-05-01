---
description: "Task list for Spec 003 — Theme + Typography + Dark Mode"
---

# Tasks: Theme + Typography + Dark Mode

**Input**: Design documents from `specs/003-theme-typography-darkmode/`
**Prerequisites**: spec.md (3 user stories — all P1), plan.md, research.md, data-model.md, quickstart.md

**Tests**: 4 unit tests REQUIRED per FR-023 (`Spacing`, `Typography`, `ThemeMode`). Compose UI test optional. Tests are implementation tasks, NOT TDD-first.

**Organization**: Tasks grouped by user story to enable independent verification. Note: Spec 003 user stories share heavy infrastructure (theme module) — US1 + US2 + US3 all depend on Foundational phase completing first.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1/US2/US3)
- File paths absolute from repo root

## Path Conventions

- Source: `app/src/main/kotlin/com/raumanian/thirtysix/browser/`
- Resources: `app/src/main/res/`
- Unit tests: `app/src/test/kotlin/com/raumanian/thirtysix/browser/`

---

## Phase 1: Setup (Offline Asset Preparation)

**Purpose**: External asset gathering trước khi viết code (font binary + Material Theme Builder export). Có thể làm offline / pre-implementation.

- [X] T001 Export Material Theme Builder palette tại [m3.material.io/theme-builder](https://m3.material.io/theme-builder) với primary seed `#0F766E` + tertiary seed `#0891B2`. Verify "Accessibility" tab tất cả 24+ pair badge ✓ AA. Export "Kotlin" → save 60+ Color constants vào temp file để dùng cho T004. Screenshot config + paste expected hex preview vào PR description.
- [X] T002 [P] Download Poppins + Inter font binary từ Google Fonts:
  - `https://fonts.google.com/specimen/Poppins` → verify "About" tab ghi **"SIL Open Font License, version 1.1"** (cho phép bundle commercial — FR-025) → extract `Poppins-Medium.ttf` + `Poppins-SemiBold.ttf` (static, không variable)
  - `https://fonts.google.com/specimen/Inter` → verify "About" tab ghi **"SIL Open Font License, version 1.1"** → extract `Inter-Regular.ttf` + `Inter-Medium.ttf` (static)
  - Rename lowercase + underscore: `poppins_medium.ttf`, `poppins_semibold.ttf`, `inter_regular.ttf`, `inter_medium.ttf`
  - Tính `shasum -a 256 *.ttf` và record vào PR description (anti-tamper)
  - Verify mỗi file < 60KB

---

## Phase 2: Foundational (Theme Module Infrastructure)

**Purpose**: Theme module skeleton + tất cả tokens. BLOCKS toàn bộ user story phases.

**⚠️ CRITICAL**: T012 phải xong trước khi US1/US2/US3 có thể bắt đầu (US1 wire MainActivity, US2 verify dynamic color, US3 viết tests).

- [X] T003 Create directory `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/theme/` (nếu chưa tồn tại) — Spec 002 đặt theme files ở `ui/theme/` (package `com.raumanian.thirtysix.browser.ui.theme`), Spec 003 di chuyển sang `presentation/theme/` (package `com.raumanian.thirtysix.browser.presentation.theme`). **Hai package KHÁC NHAU nên top-level val cùng tên ở 2 nơi không conflict compile** — cho phép T004/T006/T010 (create new) chạy trước T011 (delete legacy).
- [X] T004 [P] Create (replacing legacy `ui/theme/Color.kt`) `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/theme/Color.kt`: package `presentation.theme` (KHÔNG `ui.theme`). Paste 60+ `Color` constants từ T001 export (`md_theme_light_*` + `md_theme_dark_*` prefix), khai báo `internal val LightColorScheme = lightColorScheme(...)` và `internal val DarkColorScheme = darkColorScheme(...)` với 30 role mapping mỗi cái. Add file header comment với date + Material Theme Builder URL + WCAG verification reference.
- [X] T005 [P] Place 4 font binary từ T002 vào `app/src/main/res/font/` directory (tạo nếu chưa có):
  - `poppins_medium.ttf`
  - `poppins_semibold.ttf`
  - `inter_regular.ttf`
  - `inter_medium.ttf`
- [X] T006 [P] Create (replacing legacy `ui/theme/Type.kt`) `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/theme/Type.kt`: package `presentation.theme`. Declare `val Poppins = FontFamily(...)` + `val Inter = FontFamily(...)`, khai báo `internal val Typography = Typography(...)` với 16 M3 role mapping per data-model.md (display/headline = Poppins SemiBold, titleLarge = Poppins Medium, body* + label* + titleMedium/Small = Inter Regular/Medium). Override chỉ `fontFamily` + `fontWeight` qua `.copy()`, KHÔNG đụng size/lh/letterSpacing.
- [X] T007 [P] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/theme/Shape.kt`: `internal val Shapes = Shapes()` (M3 defaults — extraSmall=4, small=8, medium=12, large=16, extraLarge=28).
- [X] T008 [P] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/theme/Spacing.kt`: `object Spacing { val xs = 4.dp; val sm = 8.dp; val md = 16.dp; val lg = 24.dp; val xl = 32.dp }`. Imports: `androidx.compose.ui.unit.Dp`, `androidx.compose.ui.unit.dp`.
- [X] T009 [P] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/theme/ThemeMode.kt`: `enum class ThemeMode { Light, Dark, System }`.
- [X] T010 Create (replacing legacy `ui/theme/Theme.kt`) `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/theme/Theme.kt`: package `presentation.theme`. `@Composable fun ThirtySixTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = true, content: @Composable () -> Unit)` với logic:
  - Nếu `dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` → dùng `dynamicDarkColorScheme(LocalContext.current)` hoặc `dynamicLightColorScheme(LocalContext.current)` theo `darkTheme`
  - Else → dùng `DarkColorScheme` hoặc `LightColorScheme` (từ T004)
  - Wrap với `MaterialTheme(colorScheme, typography = Typography, shapes = Shapes, content)`
  - Phụ thuộc T004, T006, T007. **Đây là task hỗ trợ cả US1 (light/dark fallback) lẫn US2 (dynamic color guard)**.
- [X] T011 Delete legacy theme files trong `app/src/main/kotlin/com/raumanian/thirtysix/browser/ui/theme/` (Spec 001 template) **và verify không còn reference**:
  - Delete: `ui/theme/Color.kt`, `ui/theme/Theme.kt` (chứa `ThirdtySixBrowserTheme` typo), `ui/theme/Type.kt`
  - Empty directory `ui/theme/` cũng xóa nếu không còn file nào
  - Grep verify (FR-019 — placeholder Composable + bất kỳ file nào khác KHÔNG còn import theme name cũ):
    ```bash
    grep -rn "ThirdtySixBrowserTheme\|com\.raumanian\.thirtysix\.browser\.ui\.theme" app/src/main/kotlin/
    # Expected: empty output
    ```
  - Nếu output không empty → update reference trong file đó (vd: 7 placeholder Composable `BrowserScreen`/`TabsScreen`/.../`OnboardingScreen.kt`) replace import + reference sang `com.raumanian.thirtysix.browser.presentation.theme.ThirtySixTheme`
- [X] T012 Verify Spec 002 đã xóa `Greeting` + `GreetingPreview` Composable trong `MainActivity.kt`. Chạy:
  ```bash
  grep -n "fun Greeting\|fun GreetingPreview" app/src/main/kotlin/com/raumanian/thirtysix/browser/MainActivity.kt
  ```
  Nếu output rỗng → OK, skip cleanup. Nếu còn function → xóa cả 2 function khỏi MainActivity.kt như part Spec 003 cleanup.

**Checkpoint**: Theme module foundation ready. ThirtySixTheme Composable, 6 token files, font assets all in place. US1/US2/US3 có thể bắt đầu.

---

## Phase 3: User Story 1 - Light/Dark/System Theme + Cold-Start Window Flash Fix (Priority: P1) 🎯 MVP

**Goal**: App render đúng theme Light/Dark/System khi user đổi system theme; cold start KHÔNG flash trắng trên dark mode.

**Independent Test**: Cài APK debug → mở app → toggle Quick Settings Dark mode → app re-compose dark surface trong < 100ms. Force-stop + relaunch trong dark mode → window background dark, không flash trắng.

### Implementation for User Story 1

- [X] T013 [US1] Rewrite `app/src/main/kotlin/com/raumanian/thirtysix/browser/MainActivity.kt`:
  - Import `ThirtySixTheme`, `ThemeMode`, `AppNavGraph`, `isSystemInDarkTheme`, `mutableStateOf`, `remember`, `getValue`, `setValue`
  - Trong `setContent { ... }`: declare `var themeMode by remember { mutableStateOf(ThemeMode.System) }`
  - Compute `darkTheme` qua `when (themeMode) { Light → false; Dark → true; System → isSystemInDarkTheme() }`
  - Wrap: `ThirtySixTheme(darkTheme = darkTheme) { AppNavGraph() }`
  - Xóa import + reference cũ `ThirdtySixBrowserTheme`. Phụ thuộc T010, T009, T011.
- [X] T014 [P] [US1] Create or modify `app/src/main/res/values/colors.xml`:
  ```xml
  <resources>
      <color name="window_background_light">#FAFAF9</color>
      <color name="window_background_dark">#0A0F0E</color>
  </resources>
  ```
  Sync với surface light/dark trong T004 Color.kt.
- [X] T015 [P] [US1] Modify `app/src/main/res/values/themes.xml` — replace template theme với `Theme.ThirtySix`:
  ```xml
  <resources>
      <style name="Theme.ThirtySix" parent="Theme.Material3.DayNight.NoActionBar">
          <item name="android:windowBackground">@color/window_background_light</item>
          <item name="android:statusBarColor">@android:color/transparent</item>
      </style>
  </resources>
  ```
- [X] T016 [P] [US1] Create `app/src/main/res/values-night/themes.xml` (NEW directory + file):
  ```xml
  <resources>
      <style name="Theme.ThirtySix" parent="Theme.Material3.DayNight.NoActionBar">
          <item name="android:windowBackground">@color/window_background_dark</item>
          <item name="android:statusBarColor">@android:color/transparent</item>
      </style>
  </resources>
  ```
- [X] T017 [US1] Verify `app/src/main/AndroidManifest.xml` `<application>` element có `android:theme="@style/Theme.ThirtySix"`. Nếu Spec 002 set tên khác, update sang `Theme.ThirtySix`. Phụ thuộc T015.

**Checkpoint**: User Story 1 fully functional. Theme toggle + cold-start window fix verified manually trên emulator.

---

## Phase 4: User Story 2 - Dynamic Color (Android 12+) (Priority: P1)

**Goal**: Trên Android 12+ với wallpaper user-customized, app sinh palette Material You từ wallpaper. Trên Android 7-11 hoặc `dynamicColor = false`, fallback Deep Teal seed.

**Independent Test**: Trên emulator API 36, đổi wallpaper sang màu cam → mở app → primary tilt cam. Trên emulator API 24, mở app → primary = Deep Teal seed.

> **Note**: Implementation đã hoàn tất ở T010 (Theme.kt với API guard `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`). Phase này chỉ có verification tasks.

- [X] T018 [US2] Code review verify `Theme.kt` (T010) có đúng API guard `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` trước khi gọi `dynamicLightColorScheme`/`dynamicDarkColorScheme`. Quên guard → app crash runtime trên API < 31. Grep:
  ```bash
  grep -n "Build.VERSION.SDK_INT" app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/theme/Theme.kt
  # Expected: ít nhất 1 reference đến VERSION_CODES.S
  ```
- [X] T019 [US2] Verify `./gradlew :app:dependencies | grep material3` resolved version ≥ 1.4.0 (cần `dynamicDarkColorScheme` API). Nếu < 1.4.0 → bump Compose BOM trong Spec 001 catalog (research.md Risk #1).

**Checkpoint**: User Story 2 verified. Dynamic color path active trên API 31+, fallback static seed cho API 24-30.

---

## Phase 5: User Story 3 - Design Tokens for Future Specs + Detekt Baseline Cleanup (Priority: P1)

**Goal**: Spec 004+ có thể consume `MaterialTheme.colorScheme.*`, `MaterialTheme.typography.*`, `MaterialTheme.shapes.*`, `Spacing.*` mà không vi phạm Constitution §III. Detekt baseline `<CurrentIssues/>` empty post-rewrite.

**Independent Test**: Chạy `./gradlew detekt` → 0 violation, baseline empty. Run unit tests → 4+ test pass cho Spacing, Typography, ThemeMode. Code reviewer mở Color.kt/Spacing.kt/Type.kt → identify token trong < 30s.

### Implementation for User Story 3

- [X] T020 [P] [US3] Create unit test `app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/theme/SpacingTest.kt`:
  ```kotlin
  class SpacingTest {
      @Test fun xs_is_4dp() = assertEquals(4.dp, Spacing.xs)
      @Test fun sm_is_8dp() = assertEquals(8.dp, Spacing.sm)
      @Test fun md_is_16dp() = assertEquals(16.dp, Spacing.md)
      @Test fun lg_is_24dp() = assertEquals(24.dp, Spacing.lg)
      @Test fun xl_is_32dp() = assertEquals(32.dp, Spacing.xl)
  }
  ```
- [X] T021 [P] [US3] Create unit test `app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/theme/TypographyTest.kt`:
  ```kotlin
  class TypographyTest {
      @Test fun headlineMedium_uses_Poppins() = assertEquals(Poppins, Typography.headlineMedium.fontFamily)
      @Test fun bodyLarge_uses_Inter() = assertEquals(Inter, Typography.bodyLarge.fontFamily)
      @Test fun titleMedium_uses_Inter() = assertEquals(Inter, Typography.titleMedium.fontFamily)
      @Test fun titleLarge_uses_Poppins() = assertEquals(Poppins, Typography.titleLarge.fontFamily)
  }
  ```
- [X] T022 [P] [US3] Create unit test `app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/theme/ThemeModeTest.kt`:
  ```kotlin
  class ThemeModeTest {
      @Test fun has_three_values() = assertEquals(3, ThemeMode.values().size)
      @Test fun contains_Light_Dark_System() {
          assertTrue(ThemeMode.values().contains(ThemeMode.Light))
          assertTrue(ThemeMode.values().contains(ThemeMode.Dark))
          assertTrue(ThemeMode.values().contains(ThemeMode.System))
      }
  }
  ```
- [X] T023 [US3] Clear Detekt baseline `app/detekt-baseline.xml` về:
  ```xml
  <?xml version="1.0" ?>
  <SmellBaseline>
    <ManuallySuppressedIssues/>
    <CurrentIssues/>
  </SmellBaseline>
  ```
  Phụ thuộc T004 (xóa 6 MagicNumber stale entries), T010 (xóa 1 FunctionNaming `ThirdtySixBrowserTheme` entry), T012 (xóa 2 FunctionNaming `Greeting`/`GreetingPreview` entries nếu cần).
- [X] T024 [US3] Chạy `./gradlew detekt` post-baseline-clear. Nếu fail với violation mới (vd: Hilt-generated code trigger MagicNumber) → fix root cause hoặc add `@Suppress` targeted với rationale comment, KHÔNG cover bằng baseline mới.

**Checkpoint**: All 3 user stories independently functional. Test suite 11 new tests pass. Detekt baseline empty.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Quality gate verification, manual UI test, CI verify, success criteria measurement.

- [X] T025 Run all 6 quality gates locally:
  ```bash
  ./gradlew :app:assembleDebug
  ./gradlew :app:assembleRelease
  ./gradlew :app:testDebugUnitTest          # 11 new + 8 existing Spec 002 = 19+ test pass
  ./gradlew :app:lintDebug                  # 0 warning
  ./gradlew detekt                          # 0 violation, baseline empty
  ./gradlew ktlintCheck                     # 0 violation
  ```
  Tất cả MUST pass.
- [X] T026 [P] Run 16KB alignment script `bash .specify/scripts/bash/verify-16kb-alignment.sh`. Expected: tất cả `.so` (chỉ `libandroidx.graphics.path.so` từ Compose) align 0x4000.
- [X] T027 [P] WCAG contrast ratio verification:
  - Mở Material Theme Builder với cùng config (primary `#0F766E`, tertiary `#0891B2`)
  - Tab "Accessibility" → screenshot 24+ pair với badge ✓ AA
  - So sánh với research.md preliminary table — confirm không có pair fail
  - Nếu fail → adjust luminance + re-export Color.kt + re-test
  - Document final ratio table trong PR description
- [X] T028 [P] No-Hardcode grep verification (SC-008):
  ```bash
  # 0 inline Color(0xFF...) ngoài Color.kt
  grep -rn "Color(0x" app/src/main/kotlin/ \
    | grep -v "presentation/theme/Color.kt" \
    | grep -v "Color.Transparent\|Color.Unspecified"
  # Expected: empty

  # 0 inline TextStyle(/fontSize ngoài Type.kt
  grep -rn -E "TextStyle\(|fontSize\s*=\s*[0-9]" app/src/main/kotlin/ \
    | grep -v "presentation/theme/Type.kt"
  # Expected: empty

  # 0 inline .padding(N.dp) ngoài Spacing.kt
  grep -rnE "\.padding\([0-9]+\.dp\)" app/src/main/kotlin/ \
    | grep -v "presentation/theme/Spacing.kt"
  # Expected: empty
  ```
- [X] T029 APK size measurement (SC-007):
  ```bash
  ./gradlew :app:assembleRelease
  du -h app/build/outputs/apk/release/app-release.apk
  ```
  So sánh với Spec 002 baseline (record trong CLAUDE.md "Recent Changes" — Spec 002 = +88KB so với Spec 001). Spec 003 delta vs Spec 002 baseline phải ≤ 500KB. Document concrete number trong PR description.
- [ ] T030 Manual UI verification trên emulator API 36 (SC-001..003):
  1. Cài APK debug, mở app trên system Light → verify `BrowserScreen` placeholder render Deep Teal accent (không purple template) + heading dùng Poppins
  2. Toggle Quick Settings → Dark → verify re-compose ≤ 100ms tới dark surface
  3. Force-stop + relaunch dark mode → verify KHÔNG flash trắng (window bg dark)
  4. Đổi wallpaper màu cam (API 31+) → relaunch → verify primary tilt cam (dynamic color)
  5. Đo cold start: `adb shell am start -W -n com.raumanian.thirtysix.browser/.MainActivity` → expected `WaitTime` ≤ 1500ms
- [ ] T031 Run [quickstart.md](quickstart.md) Step 1-7 đầy đủ — confirm tất cả 10 SC pass. **Cuối Step 4 (US3 verification)**: time stopwatch reviewer (hoặc self-time) identify (a) brand color seed location, (b) cách thêm spacing token mới, (c) cách thay font heading — record actual time vào PR description (SC-009 yêu cầu ≤ 15 phút).
- [ ] T032 Push branch + verify CI (SC-004):
  ```bash
  git push -u origin 003-theme-typography-darkmode
  ```
  Expected GitHub Actions: 5 job xanh + 1 job (instrumented-test) skipped.
- [X] T033 Update [CLAUDE.md](../../CLAUDE.md) `<!-- SPECKIT START -->` section: confirm Active Spec đã point đến Spec 003 plan (đã update bởi `/speckit-plan`). Sau khi merge to main, sẽ update sang Spec 004 trong Spec 004 PR.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: T001, T002 chạy parallel, không có dep
- **Phase 2 (Foundational)**: T003 (mkdir) → T004-T009 (parallel) → T010 (Theme.kt depends on T004,T006,T007,T009) → T011 (delete legacy) → T012 (verify cleanup)
- **Phase 3 (US1)**: Cần Phase 2 xong (T010 cho ThirtySixTheme, T011 cho legacy cleanup). T013 (MainActivity) sequential; T014/T015/T016 parallel (different XML files); T017 sequential after T015
- **Phase 4 (US2)**: Verification only — cần Phase 2 xong (T010 có API guard). T018, T019 sequential
- **Phase 5 (US3)**: Cần Phase 2 xong (cần Spacing/Typography/ThemeMode tồn tại). T020/T021/T022 parallel; T023 cần T004+T010+T012; T024 sau T023
- **Phase 6 (Polish)**: Cần tất cả phase trước xong. T025 sequential block; T026-T028 parallel; T029-T033 sequential

### User Story Dependencies

Spec 003 đặc biệt: 3 user stories share heavy infrastructure (theme module). Khác với pattern thông thường, tất cả 3 stories đều P1 và đều phụ thuộc Foundational hoàn tất.

- **US1**: Cần T010 (Theme.kt với cả light/dark scheme). Implementation = MainActivity wire + window flash fix
- **US2**: Cần T010 (Theme.kt với API guard cho dynamic color). Implementation = chỉ verification (không có code mới)
- **US3**: Cần T004 + T006 + T007 + T008 + T009 + T010 (toàn bộ tokens). Implementation = unit tests + detekt baseline clear

### Within Each User Story

- Phase 2 Foundational là CRITICAL block — không story nào start trước khi Foundational xong
- US1: T013 (MainActivity) trước T014-T017 (XML files có thể parallel với T013)
- US3: Tests (T020-T022) parallel với nhau; T023-T024 sequential

### Parallel Opportunities

- T001 + T002 (Setup, parallel) — cả 2 task offline asset prep
- T004 + T005 + T006 + T007 + T008 + T009 (Foundational tokens, parallel) — 6 task tạo file độc lập
- T014 + T015 + T016 (US1 XML, parallel) — 3 file resource khác nhau
- T020 + T021 + T022 (US3 tests, parallel) — 3 file test khác nhau
- T026 + T027 + T028 (Polish verify, parallel) — 3 verification độc lập

---

## Parallel Example: Phase 2 Foundational

```bash
# Sau khi T003 mkdir xong, launch 6 token file tasks parallel:
Task T004: Create Color.kt với Material Theme Builder export
Task T005: Place 4 .ttf font files vào res/font/
Task T006: Create Type.kt với Poppins/Inter Typography
Task T007: Create Shape.kt với M3 default Shapes
Task T008: Create Spacing.kt với 5 token Dp
Task T009: Create ThemeMode.kt enum

# Sau khi tất cả 6 xong, sequential T010:
Task T010: Create Theme.kt với ThirtySixTheme Composable (depends on T004, T006, T007, T009)
```

## Parallel Example: Phase 6 Polish

```bash
# Sau T025 (quality gates) xong, launch 3 verification tasks parallel:
Task T026: 16KB alignment script
Task T027: WCAG contrast ratio verify
Task T028: No-Hardcode grep verify

# Sequential T029-T033 sau khi 3 task trên xong
```

---

## Implementation Strategy

### MVP First (Foundational + US1 only)

1. Complete **Phase 1**: Setup (T001, T002) — offline asset prep
2. Complete **Phase 2**: Foundational (T003-T012) — theme module + cleanup. **CRITICAL — blocks tất cả stories**
3. Complete **Phase 3**: US1 (T013-T017) — Light/Dark/System toggle + cold-start fix
4. **STOP and VALIDATE**: cài APK + manual test theme toggle + cold start dark
5. (Optional) deploy/demo MVP nếu cần

US2 (T018-T019) chỉ có verification — có thể chạy ngay sau US1 (~5 phút).
US3 (T020-T024) là tests + baseline cleanup — cần thiết cho CI nhưng không phải user-facing feature.

### Incremental Delivery (Recommended cho Spec 003)

1. Setup + Foundational → theme module ready
2. US1 → cài APK, test toggle Light/Dark/System trên emulator → ✅
3. US2 verification → đổi wallpaper test dynamic color → ✅
4. US3 tests + baseline clear → CI prep
5. Phase 6 Polish → quality gates + APK size + WCAG + manual UI verify
6. Push branch → CI 5/6 + 1 skip → PR review → merge

### Solo Developer Strategy (Spec 003 reality)

Vì Spec 003 single-developer (Xbism3 + Claude), parallel execution chủ yếu giảm "wait time" giữa task chứ không scale bằng team. Order recommended sequential:

1. T001 (Material Theme Builder export) — manual web UI ~10 phút
2. T002 (font download) — parallel với T001 nếu để Claude download — ~5 phút
3. T003-T012 (Foundational) — viết code Kotlin tuần tự, ~30-45 phút
4. T013-T017 (US1) — wire MainActivity + XML, ~15-20 phút
5. T018-T019 (US2) — code review + dependency check, ~5 phút
6. T020-T024 (US3) — viết 3 test file + clear baseline, ~15 phút
7. T025-T033 (Polish) — quality gates + verify, ~30 phút

**Tổng estimate**: 2-3 giờ implementation + 30 phút verification = **~3-4 giờ end-to-end**.

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks trong cùng phase
- [Story] label maps task → user story để traceability với spec.md acceptance scenarios
- Tất cả 3 user story đều P1 → không thể skip story nào
- US2 chỉ có verification tasks (T018-T019) vì implementation đã ở T010
- Avoid: viết test trước khi tokens tồn tại (test fail compile); xóa baseline trước khi rewrite Color.kt (entries vẫn valid)
- Commit recommendation: 1 commit per phase hoàn tất (Phase 1 → 1 commit, Phase 2 → 1 commit, ...) hoặc per logical group (Foundational tokens 1 commit, Theme.kt + cleanup 1 commit, US1 wire 1 commit, US3 tests 1 commit, Polish manual checks 1 commit)
- Stop after Phase 3 validation nếu MVP scope yêu cầu, hoàn tất Phase 4-6 sau
