# Feature Specification: Clean Architecture Skeleton + Hilt DI

**Feature Branch**: `002-clean-architecture-skeleton-di`
**Created**: 2026-05-01
**Status**: Draft
**Input**: User description: "Spec 002 — clean-architecture-skeleton-di. Dựng khung Clean Architecture + MVVM + Hilt DI + Navigation Compose host với 7 placeholder screens, để các spec sau có nền tảng implement features. Nguyên tắc: làm đến đâu tạo đến đó — không pre-create constants/repositories/usecases/entities; ngoại lệ là 7 routes trong AppDestination để control navigation từ đầu."

## Clarifications

### Session 2026-05-01

- Q: `Result<T>` có cần `Loading` state không? → A: Chỉ `Success<T>` + `Error` (terminal). Loading do UiState quản lý riêng.
- Q: `BaseViewModel.launchSafely` truyền lỗi đến caller bằng cách nào? → A: Callback param `onError: (AppError) -> Unit = {}`. Caller tự handle (update UiState).
- Q: Quy tắc map exception → `AppError` trong `launchSafely`? → A: Type-based baseline: `IOException → AppError.Network`, `SQLiteException` (và subclasses, bao gồm Room) `→ AppError.Database`, mọi exception khác `→ AppError.Unknown(throwable)`. `CancellationException` re-throw.
- Q: Constitution §III "ALL @Module annotations live in `di/` package" — interpret thế nào? → A: Liberal — bất kỳ folder tên `di/` đều OK (cả `core/di/` lẫn top-level `app/.../di/`). Module co-located với layer. Spec 002 chỉ tạo `core/di/`; top-level `di/` để spec sau khi cần (DatabaseModule ở Spec 005, etc.).

## User Scenarios & Testing *(mandatory)*

### User Story 1 — App khởi động chạm được 7 placeholder screen (Priority: P1)

Sau khi merge spec 002, một developer (hoặc QA) cài app debug lên emulator/device chạy Android 7.0+, mở app và thấy MainActivity hiển thị một placeholder screen (Browser). Bằng cách điều hướng (qua test code hoặc dev shortcut), 6 placeholder screen còn lại (Tabs, Bookmarks, History, Downloads, Settings, Onboarding) đều hiển thị được — chứng tỏ navigation host hoạt động và 7 destination đã wire đúng.

**Why this priority**: Đây là điều kiện cần để mọi spec sau (003+) có chỗ "đặt feature vào". Nếu navigation host hoặc Hilt graph chưa chạy, không spec nào sau đó có thể demo được trên app thật.

**Independent Test**: Cài APK debug → tap launcher icon → app mở, không crash, hiển thị màn Browser. Chạy 1 instrumented test (hoặc manual button tạm trong dev mode) để navigate qua 6 destination còn lại — mỗi màn render placeholder đúng tên.

**Acceptance Scenarios**:

1. **Given** APK debug đã cài lên device API 36, **When** người dùng tap launcher icon, **Then** MainActivity mở, NavHost render `BrowserScreen` placeholder hiển thị label "Browser", không crash trong vòng 2 giây sau cold start.
2. **Given** app đang ở `BrowserScreen`, **When** code gọi `navController.navigate(AppDestination.Settings.route)`, **Then** `SettingsScreen` placeholder hiển thị label "Settings".
3. **Given** sạch state, **When** lần lượt navigate đến cả 7 destination (`Browser`, `Tabs`, `Bookmarks`, `History`, `Downloads`, `Settings`, `Onboarding`), **Then** mỗi destination render đúng placeholder Composable tương ứng, không có route nào throw "destination not found".
4. **Given** app đang chạy, **When** Android system rotate device hoặc kill process rồi restore, **Then** NavHost giữ destination hiện tại (Compose state restoration mặc định) và không crash.

---

### User Story 2 — Spec sau dùng được core utilities (Priority: P1)

Khi developer bắt đầu Spec 003+, các utility core (`Result<T>`, `AppError`, `DispatcherProvider`, `BaseViewModel`) đã sẵn sàng inject/consume mà không phải tự viết lại. Cụ thể: một ViewModel mới có thể `extends BaseViewModel`, dùng `launchSafely { }` để chạy coroutine an toàn, inject `DispatcherProvider` để chuyển thread, và return `Result<Domain>` từ repository.

**Why this priority**: Đây là phần "nền móng" của Clean Architecture. Không có nó, mỗi spec sau sẽ tự define utility khác nhau → fragmentation. Đặt vào Spec 002 ngay từ đầu.

**Independent Test**: Viết unit test trong `app/src/test/`:
- Test `Result<T>.map` và `Result<T>.fold` chạy đúng cho cả `Success` và `Error` branch.
- Test `BaseViewModel.launchSafely` bắt được exception và emit `AppError.Unknown`.
- Test `DispatcherProvider` inject được qua Hilt và `main`/`io`/`default` trả `CoroutineDispatcher` đúng.

**Acceptance Scenarios**:

1. **Given** một class `FakeViewModel : BaseViewModel()`, **When** trong `viewModelScope` gọi `launchSafely(onError = { capturedError = it }) { throw IOException() }`, **Then** exception được catch, convert thành `AppError.Network` (per FR-009a mapping rule), `onError` được gọi với error đó, viewModelScope không crash.
2. **Given** `Result.Success(5)`, **When** gọi `.map { it * 2 }`, **Then** trả `Result.Success(10)`.
3. **Given** `Result.Error(IOException())`, **When** gọi `.map { it * 2 }`, **Then** giữ nguyên `Result.Error` (không apply transform).
4. **(OPTIONAL — out of scope cho Spec 002)** Given Hilt graph compile xong, **When** một test class `@HiltAndroidTest` inject `DispatcherProvider`, **Then** instance trả về là `DefaultDispatcherProvider` singleton, các property `main`/`io`/`default` không null. → Yêu cầu instrumented-test CI job (đang `if: false` cho đến Spec 007/011), KHÔNG implement ở Spec 002. Verify gián tiếp qua Hilt graph compile pass (FR-004) + manual smoke trên emulator.

---

### User Story 3 — CI quality gates pass (Priority: P1)

Mọi push lên branch 002 đều phải pass đầy đủ 6 job CI hiện có (build, unit-test, lint, static-analysis, verify-16kb, instrumented-test với `if: false`). Thêm package mới (Hilt, Navigation Compose, Lifecycle ViewModel Compose) không được phá lint clean / 16KB compliance / Detekt baseline.

**Why this priority**: Constitution §I/§II/§IX bắt buộc — không spec nào được merge nếu CI red. Vì spec 002 thêm 6 package mới và đụng KSP processor, rủi ro nhất là Hilt code generation phá ktlint/Detekt rules.

**Independent Test**: Push lên remote, đợi GitHub Actions chạy → 5 job xanh + 1 job (instrumented-test) skip do `if: false`.

**Acceptance Scenarios**:

1. **Given** code spec 002 đã commit, **When** chạy `./gradlew assembleDebug`, **Then** build pass không error, không warning về Hilt graph.
2. **Given** code spec 002 đã commit, **When** chạy `./gradlew assembleRelease`, **Then** APK build thành công, signing fallback debug keystore vẫn hoạt động (per Constitution §XI).
3. **Given** APK release vừa build, **When** chạy `.specify/scripts/bash/verify-16kb-alignment.sh`, **Then** mọi `.so` trong APK align ≥ `0x4000` (16KB) — CHỈ có `libandroidx.graphics.path.so` từ Compose, không thêm `.so` mới.
4. **Given** code spec 002 đã commit, **When** chạy `./gradlew lintDebug detekt ktlintCheck`, **Then** tất cả 3 task pass clean — disable list từ Spec 001 vẫn đủ, không phải thêm exception mới (mục tiêu); nếu phải thêm exception thì PHẢI có justification rõ ràng trong PR.
5. **Given** push lên GitHub, **When** workflow chạy 6 job, **Then** 5 job xanh + `instrumented-test` skip.

---

### Edge Cases

- **KSP × Kotlin 2.3.21 compat**: Hilt KSP processor latest có thể chưa rebuild cho Kotlin 2.3.x → Plan phase phải research và pick version Hilt + KSP tương thích. Nếu KSP fail, fallback strategy được xác định: (a) tạm dùng kapt cho Hilt module này (chậm hơn), HOẶC (b) downgrade Kotlin 2.2.x trong scope spec này. Decision phải document trong `research.md`.
- **Hilt Gradle plugin × AGP 9.1.1**: Hilt Gradle plugin 2.x release sau 2025-Q4 có thể chưa test trên AGP 9.x. Plan phải verify version cụ thể nào support AGP 9.1.1 — nếu không có, dùng `dagger.hilt.android.plugin` legacy approach.
- **`lifecycle-viewmodel-compose` version**: ✅ Resolved trong [research.md §5](research.md): NOT BOM-managed — Compose BOM 2026.04.01 chỉ quản lý nhóm `androidx.compose.{animation,foundation,material,material3,runtime,ui}`; `androidx.lifecycle:*` group phải pin riêng. Spec 002 dùng `lifecycle = "2.10.0"` qua catalog.
- **`BrowserScreen` placeholder vs full Spec 007**: Composable `BrowserScreen` ở Spec 002 chỉ render `Text("Browser")` — Spec 007 sẽ rewrite. File và package giữ nguyên để không phải xoá → tạo lại.
- **Theme name mismatch**: `ThirdtySixBrowserTheme` (typo từ template Spec 001) — Spec 002 KHÔNG sửa, chỉ reuse. Spec 003 sẽ rename + rewrite theme.
- **Start destination = Browser**: Spec 002 set start = `Browser`. Spec 018 (Onboarding) sẽ thay logic check first-launch để chọn `Onboarding` hoặc `Browser`. Code Spec 002 phải để chỗ này dễ thay (ví dụ: `startDestination` là param của `AppNavGraph`, không hardcode).
- **Detekt MagicNumber gate × placeholder UI**: 7 placeholder Composable dùng `MaterialTheme.typography` + `stringResource` → không trigger MagicNumber. Tuy nhiên nếu thêm `Modifier.padding(...)` inline phải dùng giá trị đã token-hoá hoặc add tới `Spacing` token (Spec 003 sẽ làm) — Spec 002 ưu tiên KHÔNG thêm padding inline để tránh tạo nợ kỹ thuật.

## Requirements *(mandatory)*

### Functional Requirements

#### Hilt DI

- **FR-001**: System MUST khai báo `ThirtySixApplication` annotated `@HiltAndroidApp` và register class này trong `AndroidManifest.xml` (`android:name`).
- **FR-002**: System MUST annotate `MainActivity` với `@AndroidEntryPoint` để cho phép Hilt inject vào composable scope.
- **FR-003**: Hilt Gradle plugin và Hilt KSP processor MUST được wire qua `gradle/libs.versions.toml` (KHÔNG hardcode trong `app/build.gradle.kts`).
- **FR-004**: Khi build debug/release, Hilt graph compilation MUST pass — không có "missing binding", "duplicate binding", hoặc "scope" error.

#### Core utilities

- **FR-005**: System MUST cung cấp `Result<T>` sealed class trong `core/result/` với đúng 2 sub-class terminal: `Success<T>(data: T)` và `Error(throwable: Throwable, message: String? = null)`. KHÔNG thêm `Loading` state — loading do UI layer quản lý qua field riêng trên `UiState` (vd: `isLoading: Boolean`).
- **FR-006**: `Result<T>` MUST expose extension functions `map { transform: (T) -> R }` và `fold(onSuccess, onError)` với behavior:
  - `Success.map { f }` → `Success(f(data))`
  - `Error.map { _ }` → giữ nguyên `Error`
  - `fold` trả về kiểu `R` từ branch tương ứng
- **FR-007**: System MUST cung cấp `AppError` sealed class trong `core/error/` với 3 sub-class baseline: `Network(throwable: Throwable)`, `Database(throwable: Throwable)`, `Unknown(throwable: Throwable)` — cả 3 đều giữ original throwable để debug/log. Mở rộng (vd: `NotFound`, `Validation`) chỉ thêm khi spec sau có nhu cầu thực tế.
- **FR-008**: System MUST cung cấp `DispatcherProvider` interface trong `core/dispatcher/` exposing 4 properties: `main`, `io`, `default`, `unconfined` — kiểu `CoroutineDispatcher`. Default implementation `DefaultDispatcherProvider` MUST inject được qua Hilt singleton scope.
- **FR-009**: System MUST cung cấp `BaseViewModel` abstract class trong `core/base/` extends `androidx.lifecycle.ViewModel`, exposes helper:
  ```kotlin
  protected fun launchSafely(
      onError: (AppError) -> Unit = {},
      block: suspend CoroutineScope.() -> Unit
  ): Job
  ```
  Chạy `block` trên `viewModelScope` với try/catch; khi exception, map → `AppError` theo rule baseline (xem FR-009a) và invoke `onError(error)`. Caller (VM con) chịu trách nhiệm update UiState trong callback. Không enforce generic `UiState` pattern.

- **FR-009a**: Exception → `AppError` mapping rule baseline (dùng trong `launchSafely`):
  - `java.io.IOException` (và subclasses, vd `SocketTimeoutException`, `UnknownHostException`) → `AppError.Network(throwable)`
  - `android.database.sqlite.SQLiteException` (và subclasses, bao gồm Room exceptions) → `AppError.Database(throwable)`
  - Mọi exception khác → `AppError.Unknown(throwable)`
  - `CancellationException` PHẢI re-throw (không map) để coroutine cancellation hoạt động đúng — đây là idiom Kotlin Coroutines.
  - Spec sau có thể mở rộng rule nếu thêm sub-class `AppError`.
- **FR-010**: System MUST KHÔNG tạo `BaseUseCase` ở spec này — UseCase abstraction sẽ thêm khi spec đầu tiên cần (Spec 005 hoặc 013) để tránh empty abstraction.

#### Navigation skeleton

- **FR-011**: System MUST khai báo `AppDestination` sealed class trong `presentation/navigation/` với đúng 7 object con, mỗi object có property `route: String`:
  - `Browser` → `"browser"`
  - `Tabs` → `"tabs"`
  - `Bookmarks` → `"bookmarks"`
  - `History` → `"history"`
  - `Downloads` → `"downloads"`
  - `Settings` → `"settings"`
  - `Onboarding` → `"onboarding"`
- **FR-012**: `AppNavGraph` Composable MUST nhận `NavHostController` và optional `startDestination` (default = `AppDestination.Browser.route`) — wire 7 destination đến 7 placeholder Composable tương ứng.
- **FR-013**: System MUST cung cấp 7 placeholder Composable, mỗi cái 1 file riêng dưới `presentation/<feature>/<Feature>Screen.kt`, hiển thị 1 `Text` với label tên screen, dùng `MaterialTheme.typography.headlineMedium` (hoặc style M3 phù hợp) và `stringResource(R.string.<feature>_screen_placeholder)`.
- **FR-014**: System MUST KHÔNG hardcode route path string ở caller — luôn dùng `AppDestination.<X>.route`. Constitution §III No-Hardcode Rule.
- **FR-015**: System MUST KHÔNG hardcode UI text trong Composable — 7 string resource cho 7 placeholder label MUST nằm trong `app/src/main/res/values/strings.xml` (EN). 7 locale còn lại (VI/DE/RU/KO/JA/ZH/FR) KHÔNG bắt buộc ở spec này — Spec 004 sẽ lo full localization.

#### Module structure

- **FR-016**: System MUST tạo các package directory + file sau đây — KHÔNG hơn, KHÔNG ít:
  - `core/base/` chứa `BaseViewModel.kt`
  - `core/result/` chứa `Result.kt`
  - `core/error/` chứa `AppError.kt`
  - `core/dispatcher/` chứa `DispatcherProvider.kt` + `DefaultDispatcherProvider.kt` (cùng file hoặc tách 2 file — implementer chọn)
  - `core/di/` chứa `DispatcherModule.kt`
  - `presentation/navigation/` chứa `AppDestination.kt` + `AppNavGraph.kt`
  - `presentation/{browser,tabs,bookmarks,history,downloads,settings,onboarding}/` mỗi cái chứa `<Feature>Screen.kt`
  - Root: `ThirtySixApplication.kt`, `MainActivity.kt` (đã có từ template — modify)
- **FR-017**: System MUST KHÔNG tạo file/directory cho:
  - `core/constants/` (mỗi spec sau tự thêm constants nó cần)
  - `data/` layer (Spec 005/006 sẽ lo)
  - `domain/` layer (mỗi feature spec sau tự thêm)
  - `di/` top-level (tạo khi spec sau có Hilt module thực sự cần — `DatabaseModule` ở Spec 005, etc.)
  - Repository interfaces / implementations
  - UseCase classes
  - Entity / Domain model classes
  - 7 string entries trong locale `values-{vi,de,ru,ko,ja,zh,fr}/strings.xml`

#### Quality gates

- **FR-018**: Build MUST pass cho cả 6 Gradle task: `assembleDebug`, `assembleRelease`, `testDebugUnitTest`, `lintDebug`, `detekt`, `ktlintCheck`.
- **FR-019**: 16KB alignment script `.specify/scripts/bash/verify-16kb-alignment.sh` MUST pass post-`assembleRelease` — không có `.so` mới được introduce; chỉ `libandroidx.graphics.path.so` từ Compose vẫn align 0x4000.
- **FR-020**: System MUST có ít nhất 2 unit test mới trong `app/src/test/` cho `Result<T>` (cả 2 branch của `map`) và 1 test cho `BaseViewModel.launchSafely` (xác nhận exception được convert → `AppError`). Hilt instrumented test cho `DispatcherProvider` inject là **OUT OF SCOPE** cho Spec 002 — yêu cầu re-enable `instrumented-test` CI job (đang block đến Spec 007/011 per pending task). Spec 007 hoặc 011 sẽ thêm instrumented Hilt test khi job được bật lại.
- **FR-021**: Detekt baseline `app/detekt-baseline.xml` SHOULD KHÔNG cần thêm violation mới do Spec 002 — nếu cần thêm thì PR description PHẢI justify từng entry. (Mục tiêu chứ không phải ràng buộc cứng — Spec 003 sẽ clear baseline khi rewrite theme.)

#### Package versions

- **FR-022**: Versions cho 6 production package mới + 1 KSP plugin + 1 test dep (`hilt-android`, **`hilt-compiler`** qua KSP, `hilt-android-gradle-plugin`, `hilt-navigation-compose`, `lifecycle-viewmodel-compose`, `navigation-compose`, KSP plugin `com.google.devtools.ksp`, `kotlinx-coroutines-test`) MUST được lookup tại thời điểm implement qua `central.sonatype.com` và GitHub release notes — KHÔNG hardcode từ trí nhớ. Constitution §IX. **Artifact ID quan trọng**: dùng `com.google.dagger:hilt-compiler` cho KSP processor (KHÔNG dùng `hilt-android-compiler` — đó là artifact kapt-era; `ksp("...hilt-android-compiler")` compile pass nhưng generate ZERO Hilt code → app crash runtime).
- **FR-023**: Phải verify trước khi merge: 6 package mới CÓ phải Kotlin/Java only (không `.so`)? Nếu có `.so` thì phải pass 16KB alignment check. (Expected: tất cả Kotlin only — confirm trong `research.md`.)

### Key Entities

(Spec này không thêm data entity. Domain/data layer trống — Spec 005 sẽ thêm `BookmarkEntity`, `HistoryEntity`, `TabEntity`.)

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: App debug cài lên emulator API 36 và cold start trong **≤ 1.5 giây** đến khi `BrowserScreen` placeholder render xong (per perf target trong project-context.md).
- **SC-002**: Test code/manual QA navigate đến **7/7 destination** thành công — 0 lỗi "destination not found" hoặc crash.
- **SC-003**: CI pipeline run trên PR spec 002 đạt **5/6 job xanh + 1 job skipped** (instrumented-test) — 0 job fail.
- **SC-004**: Sau khi `assembleRelease` thành công, **100% `.so` trong APK** align ≥ 0x4000 (script verify-16kb-alignment pass).
- **SC-005**: 8 unit test cụ thể cho `Result<T>` (4 test: `map_success`, `map_error`, `fold_success`, `fold_error`) và `BaseViewModel.launchSafely` (4 test: `ioException → Network`, `sqliteException → Database`, `genericException → Unknown`, `cancellationException → rethrown`) **PASS** trên `./gradlew :app:testDebugUnitTest`. (Quantitative coverage threshold ≥80% được defer sang spec sau khi jacoco gradle plugin được wire — Spec 002 KHÔNG add jacoco để giữ scope nhỏ.)
- **SC-006**: Một developer mới đọc code spec 002 có thể trong **≤ 30 phút** identify đúng nơi để thêm 1 ViewModel mới (extends `BaseViewModel`), 1 Composable Screen mới (đặt ở `presentation/<feature>/`), và 1 destination mới (thêm vào `AppDestination`).
- **SC-007**: Bundle size của APK release Spec 002 tăng **≤ 1MB** so với Spec 001 baseline (thêm Hilt + Navigation Compose runtime — kỳ vọng dưới 1MB do Kotlin-only).

## Assumptions

- KSP và Hilt processor có version tương thích Kotlin 2.3.21 vào thời điểm implement (2026-05-01). Nếu không, Plan phase sẽ chọn fallback (kapt hoặc Kotlin downgrade) và document quyết định.
- AGP 9.1.1 đang dùng (Spec 001) tương thích với Hilt Gradle plugin latest. Verify ở Plan phase.
- ~~Compose BOM 2026.04.01 (Spec 001) đã include `lifecycle-viewmodel-compose`~~ → Resolved per research.md: BOM KHÔNG quản lý `androidx.lifecycle:*` group, `lifecycle-viewmodel-compose:2.10.0` được pin riêng trong `libs.versions.toml`.
- Theme `ThirdtySixBrowserTheme` (typo) và toàn bộ template theme files giữ nguyên — Spec 003 sẽ refactor.
- Strings cho 7 placeholder dùng tiếng Anh đơn giản ("Browser", "Tabs", "Bookmarks", "History", "Downloads", "Settings", "Onboarding") — không phải UX copy production.
- Start destination = `Browser` cho Spec 002. Spec 018 sẽ override bằng logic check first-launch.
- Domain/data package directory KHÔNG được tạo physical folder ở Spec 002 (làm đến đâu tạo đến đó). Nếu Gradle/IDE phàn nàn về missing folder, sẽ revisit ở spec đầu tiên cần (Spec 005 cho `data/`, Spec 005 hoặc 013 cho `domain/`).
- Instrumented test job trong CI vẫn `if: false` cho Spec 002 — chưa cần emulator. Re-enable trigger giữ nguyên ở Spec 007/011 per pending CI task.

## Dependencies

- **Constitution v1.2.0** — phải comply mọi nguyên tắc, đặc biệt §III (No-Hardcode), §IX (16KB), §XI (Signing two-scope).
- **Spec 001** — `project-init-build-config` đã merge: version catalog, Detekt + ktlint + Lint setup, CI 6-job pipeline, 16KB script active, debug-signed release fallback.
- KHÔNG có dependency nào khác. Spec 002 là gốc của Phase 1 nhánh dưới Spec 001.

## Out of Scope (Spec sau sẽ làm)

- Theme/Typography rewrite + rename `ThirdtySixBrowserTheme` → `ThirtySixBrowserTheme` (Spec 003)
- Localization 8 locales (Spec 004)
- Room AppDatabase + entities (Spec 005)
- DataStore Preferences (Spec 006)
- WebView Composable wrapper (Spec 007)
- Repository pattern thực sự (mỗi data spec)
- UseCase abstractions (thêm khi spec đầu tiên cần)
- Constants files trong `core/constants/` (mỗi spec tự thêm)
- Splash screen branding (Spec 017)
- Onboarding logic check first-launch (Spec 018)
- Re-enable instrumented-test CI job (Spec 007 hoặc 011)
