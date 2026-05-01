# Implementation Plan: Clean Architecture Skeleton + Hilt DI

**Branch**: `002-clean-architecture-skeleton-di` | **Date**: 2026-05-01 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from [spec.md](spec.md), research from [research.md](research.md)

## Summary

Spec 002 dựng khung Clean Architecture + MVVM cho ThirtySixBrowser: wire Hilt DI (`@HiltAndroidApp` + `@AndroidEntryPoint`) với KSP processor, expose 4 core utilities (`Result<T>`, `AppError` sealed class với 3 baseline types, `DispatcherProvider` interface + Hilt provision, `BaseViewModel` với helper `launchSafely(onError, block)` áp dụng type-based exception → AppError mapping), dựng Navigation Compose host với `AppDestination` sealed class chứa đủ 7 routes, và 7 placeholder Screen Composable hiển thị `Text` đúng tên screen. Tuân thủ nguyên tắc "làm đến đâu tạo đến đó" — không pre-create constants/repositories/usecases/entities; KHÔNG tạo thư mục `data/`, `domain/`, `core/constants/`, top-level `di/`, hay locale `values-{vi,...}` ở spec này.

**Approach** (rút từ research.md):
- Hilt **2.59.2** (first AGP-9-compatible release) qua **KSP 2.3.7**, NOT kapt — fallback kapt hoặc Kotlin 2.2.x downgrade nếu Day-1 smoke test fail (Risk #1 trong research.md)
- **Critical artifact ID**: `com.google.dagger:hilt-compiler` (NOT `hilt-android-compiler` — đó là kapt-era artifact)
- `lifecycle-viewmodel-compose:2.10.0` PIN RIÊNG (Compose BOM không quản lý nhóm `androidx.lifecycle:*`)
- Navigation Compose **2.9.8**, Hilt Navigation Compose **1.3.0**
- Test deps: `kotlinx-coroutines-test:1.10.2` cho `BaseViewModel.launchSafely` test (cần `Dispatchers.setMain(testDispatcher)`)

## Technical Context

**Language/Version**: Kotlin 2.3.21 (host), Java target 11
**Primary Dependencies** (new in Spec 002):
- `com.google.dagger:hilt-android:2.59.2`
- `com.google.dagger:hilt-compiler:2.59.2` (via KSP)
- `com.google.dagger:hilt-android-gradle-plugin:2.59.2` (Gradle plugin)
- `androidx.hilt:hilt-navigation-compose:1.3.0`
- `androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0`
- `androidx.navigation:navigation-compose:2.9.8`
- KSP plugin `com.google.devtools.ksp:2.3.7`
- `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2` (test only)
**Existing dependencies** (from Spec 001): AGP 9.1.1, Gradle 9.5.0, Compose BOM 2026.04.01, Detekt 1.23.8, ktlint plugin 14.2.0
**Storage**: N/A — Spec 002 không động data layer (Spec 005/006 sẽ làm)
**Testing**: JUnit 4.13.2 + `kotlinx-coroutines-test:1.10.2` cho unit test trên `BaseViewModel.launchSafely`. Compose UI test KHÔNG cần thêm — placeholder Screens không cần test UI sâu (Spec 003+ sẽ thêm khi feature thực sự).
**Target Platform**: Android 7.0 (API 24) → Android 16 (API 36, compileSdk 36 + minorApiLevel 1). Min SDK 24, target SDK 36.
**Project Type**: Mobile app (Android single-module).
**Performance Goals**: Cold start ≤ 1.5s đến render BrowserScreen placeholder (per spec SC-001); Hilt graph compile + KSP processor không tăng build time > 50% so với Spec 001.
**Constraints**: 16KB page size compliance (Constitution §IX) — tất cả 6 package mới Kotlin/Java only, expected APK release `.so` set không thay đổi (vẫn chỉ `libandroidx.graphics.path.so` từ Compose, đã verified 0x4000). APK size delta ≤ 1MB.
**Scale/Scope**: 7 placeholder Composable + 4 core utility files + 1 Hilt module + 2 navigation files + Application + MainActivity rewrite ≈ 16 file mới hoặc sửa.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Note |
|---|---|---|
| **I. Privacy & Security First** | ✅ PASS | Spec 002 không thêm data path, không network, không storage. Hilt + Navigation Compose là Kotlin lib in-process. |
| **II. Google Play Compliance** | ✅ PASS | Không thêm permission, không động WebView, không thêm `addJavascriptInterface`. |
| **III. Code Quality & Safety + No-Hardcode** | ✅ PASS với chú ý | Routes lưu `AppDestination.X.route` (FR-014); Strings dùng `stringResource(R.string.<feature>_screen_placeholder)` (FR-015); Typography dùng `MaterialTheme.typography.*` (FR-013). Detekt + ktlint phải clean (FR-018). 7 entry strings trong `values/strings.xml` không vi phạm — đó là chỗ đúng. |
| **IV. Clean Architecture** | ✅ PASS | Spec 002 chính là tạo skeleton cho principle này. Chỉ tạo `core/` + `presentation/` ở spec này; `data/` và `domain/` để spec sau (per "làm đến đâu tạo đến đó"). Không vi phạm layer rules vì chưa có data flow. |
| **V. Performance Excellence** | ✅ PASS | Cold start ≤1.5s mục tiêu (SC-001). Placeholder Screen render minimal (chỉ `Text`). Hilt singleton scope cho `DispatcherProvider` không tạo overhead startup. |
| **VI. Testing Discipline** | ✅ PASS | FR-020 yêu cầu unit test `Result.map` (cả 2 branch) + `BaseViewModel.launchSafely` exception conversion. Coverage utility ≥80% (SC-005). |
| **VII. Offline-First Architecture** | N/A | Spec 002 không động persistence. Spec 005/006 sẽ áp dụng. |
| **VIII. Localization & Accessibility** | ✅ PASS với deferral | 7 string entries chỉ thêm vào `values/strings.xml` (EN), 7 locale khác Spec 004 sẽ làm. Constitution §VIII nói "all user-facing strings MUST be externalized" — đã externalized đúng (chỉ chưa translate). Không hardcode. |
| **IX. Dependency Currency & 16KB** | ✅ PASS | research.md verified 7 package versions tại 2026-05-01 từ central.sonatype.com / GitHub releases. Tất cả Kotlin/Java only — không có `.so` mới. CI verify-16kb job tiếp tục pass với set `.so` không đổi. |
| **X. Simplicity & Build Order** | ✅ PASS | Spec 002 là Phase 1 spec đúng thứ tự (sau 001). Không pre-create cho spec 003-006 (làm đến đâu tạo đến đó — Q4 clarification). YAGNI áp dụng: bỏ `BaseUseCase` (FR-010), bỏ `data/` + `domain/` directories. |
| **XI. Build Configuration** | ✅ PASS | Không thêm flavor. Signing config từ Spec 001 giữ nguyên (debug fallback active). KSP plugin add vào `gradle/libs.versions.toml`, không hardcode trong `build.gradle.kts`. |

**Gate result**: PASS. No constitution violations.

### Complexity Tracking

> Empty — không có deviation cần justify.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (none) | | |

## Project Structure

### Documentation (this feature)

```text
specs/002-clean-architecture-skeleton-di/
├── spec.md              ✅ Done (spec + 4 clarifications)
├── plan.md              ← This file
├── research.md          ✅ Done (7 package versions verified, 6 risks documented)
├── quickstart.md        ← Phase 1 output (verification steps)
├── checklists/
│   └── requirements.md  ✅ Done (spec quality checklist)
└── tasks.md             (Phase 2 — generated by /speckit.tasks)
```

> **No `data-model.md`** — Spec 002 không introduce data entity. Spec 005 sẽ tạo file này khi thêm Bookmark/History/Tab entities.
>
> **No `contracts/`** — Spec 002 không expose external interface (no API, no public CLI, no IPC). Internal interfaces (`Repository`, `UseCase` contracts) sẽ được mỗi feature spec sau document trong `contracts/` của nó.

### Source Code (repository root)

**Existing tree** (post Spec 001):
```text
app/src/main/kotlin/com/raumanian/thirtysix/browser/
├── MainActivity.kt                          (template — sẽ rewrite)
└── ui/theme/                                (template — KHÔNG sửa, Spec 003 lo)
    ├── Color.kt
    ├── Theme.kt          (ThirdtySixBrowserTheme — typo, giữ)
    └── Type.kt

app/src/main/res/
├── values/strings.xml                       (chỉ có app_name)
├── values/themes.xml
├── drawable/
└── mipmap-*/
```

**Tree after Spec 002 implementation**:
```text
app/src/main/kotlin/com/raumanian/thirtysix/browser/
├── ThirtySixApplication.kt                  ★ NEW — @HiltAndroidApp
├── MainActivity.kt                          ✏️  REWRITE — @AndroidEntryPoint, host AppNavGraph
├── core/
│   ├── base/
│   │   └── BaseViewModel.kt                 ★ NEW — launchSafely(onError, block)
│   ├── result/
│   │   └── Result.kt                        ★ NEW — sealed Success/Error + map/fold
│   ├── error/
│   │   └── AppError.kt                      ★ NEW — sealed Network/Database/Unknown
│   ├── dispatcher/
│   │   ├── DispatcherProvider.kt            ★ NEW — interface
│   │   └── DefaultDispatcherProvider.kt     ★ NEW — impl, @Inject
│   └── di/
│       └── DispatcherModule.kt              ★ NEW — @Module @InstallIn(SingletonComponent)
├── presentation/
│   ├── navigation/
│   │   ├── AppDestination.kt                ★ NEW — sealed class, 7 routes
│   │   └── AppNavGraph.kt                   ★ NEW — NavHost composable
│   ├── browser/BrowserScreen.kt             ★ NEW — placeholder
│   ├── tabs/TabsScreen.kt                   ★ NEW — placeholder
│   ├── bookmarks/BookmarksScreen.kt         ★ NEW — placeholder
│   ├── history/HistoryScreen.kt             ★ NEW — placeholder
│   ├── downloads/DownloadsScreen.kt         ★ NEW — placeholder
│   ├── settings/SettingsScreen.kt           ★ NEW — placeholder
│   └── onboarding/OnboardingScreen.kt       ★ NEW — placeholder
└── ui/theme/                                (unchanged from Spec 001)

app/src/main/
├── AndroidManifest.xml                      ✏️  EDIT — add android:name=".ThirtySixApplication"
└── res/values/strings.xml                   ✏️  EDIT — add 7 placeholder labels

app/src/test/kotlin/com/raumanian/thirtysix/browser/
├── core/
│   ├── result/ResultTest.kt                 ★ NEW — map success + map error branch
│   └── base/BaseViewModelTest.kt            ★ NEW — launchSafely catches → AppError
└── (existing template tests preserved)

app/build.gradle.kts                         ✏️  EDIT — apply KSP + Hilt plugins, add deps
gradle/libs.versions.toml                    ✏️  EDIT — add 6 new versions, libraries, plugins
```

**Explicitly NOT created (per "làm đến đâu tạo đến đó")**:
- `core/constants/*` — mỗi spec sau tự thêm khi cần (Constitution §III categorized table; Spec 005 sẽ thêm `BrowserLimits.kt`, etc.)
- `data/` directory — Spec 005/006
- `domain/` directory — Spec 005 hoặc 013
- Top-level `app/.../di/` — Spec 005 (`DatabaseModule.kt` lúc đó)
- `core/extensions/` — thêm khi extension đầu tiên thực sự cần
- `app/src/main/res/values-{vi,de,ru,ko,ja,zh,fr}/` — Spec 004
- Repository / UseCase / Entity / Mapper classes — mỗi feature spec
- `BaseUseCase.kt` (FR-010) — Spec 005 hoặc 013

**Structure Decision**: Single-module Android app, package-by-feature within Clean Architecture layers. Module structure trong `app/src/main/kotlin/com/raumanian/thirtysix/browser/` khớp Constitution §IV layout, Spec 002 chỉ tạo bộ phận `core/` + `presentation/` cần thiết cho navigation skeleton + Hilt + utilities.

## Phase 1 — Implementation Steps

> Phase 0 (research) đã hoàn tất tại [research.md](research.md). Phase 1 = design artifacts + agent context update. Tasks chi tiết sẽ được generate bởi `/speckit.tasks` (Phase 2).

### 1.1 Version catalog additions

Edit `gradle/libs.versions.toml`:
1. Append to `[versions]`: `hilt`, `ksp`, `hiltNavigationCompose`, `lifecycle`, `navigationCompose`, `coroutinesTest` (versions per research.md preview block).
2. Append to `[libraries]`:
   - `hilt-android`, `hilt-compiler` (KSP — note artifact ID), `androidx-hilt-navigation-compose`
   - `androidx-lifecycle-viewmodel-compose`
   - `androidx-navigation-compose`
   - `kotlinx-coroutines-test`
3. Append to `[plugins]`:
   - `ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }`
   - `hilt-android = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }`

### 1.2 Module-level `app/build.gradle.kts`

1. In `plugins { }` block: add `alias(libs.plugins.ksp)` + `alias(libs.plugins.hilt.android)`.
2. In `dependencies { }`:
   - `implementation(libs.hilt.android)`
   - `ksp(libs.hilt.compiler)` ← **KSP, NOT kapt; uses `hilt-compiler` artifact**
   - `implementation(libs.androidx.hilt.navigation.compose)`
   - `implementation(libs.androidx.lifecycle.viewmodel.compose)`
   - `implementation(libs.androidx.navigation.compose)`
   - `testImplementation(libs.kotlinx.coroutines.test)`
3. NO `kotlin-kapt` plugin. NO change to existing R8/minify/signing config.

### 1.3 Day-1 Hilt smoke test (BLOCKING — per Risk #1 in research.md)

Before writing any other code:
1. Add `ThirtySixApplication.kt` with `@HiltAndroidApp class ThirtySixApplication : Application()`.
2. Add `android:name=".ThirtySixApplication"` to `<application>` in `AndroidManifest.xml`.
3. Run `./gradlew :app:kspDebugKotlin --info 2>&1 | tee /tmp/hilt-smoke.log`.
4. **Pass criterion**: KSP completes without "metadata version mismatch" or "kotlinx.metadata" errors. Build does not need to fully pass — only KSP step.
5. **Fail handling**: apply Risk #1 fallback ladder in research.md:
   - First try: switch to kapt (apply `kotlin-kapt` plugin, change `ksp(...)` → `kapt(...)`, change artifact `hilt-compiler` → `hilt-android-compiler`).
   - If still fails: pin `kotlin = "2.2.20"` in catalog and re-run.
   - If still fails: hold spec 002 PR, file issue at github.com/google/dagger.

### 1.4 Core utilities

Implementation per spec FR-005..FR-009a:

**`Result.kt`** (sealed class, `core/result/`):
```kotlin
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val throwable: Throwable, val message: String? = null) : Result<Nothing>()
}

inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> = when (this) {
    is Result.Success -> Result.Success(transform(data))
    is Result.Error -> this
}

inline fun <T, R> Result<T>.fold(onSuccess: (T) -> R, onError: (Throwable) -> R): R = when (this) {
    is Result.Success -> onSuccess(data)
    is Result.Error -> onError(throwable)
}
```

**`AppError.kt`** (sealed class, `core/error/`):
```kotlin
sealed class AppError(open val throwable: Throwable) {
    data class Network(override val throwable: Throwable) : AppError(throwable)
    data class Database(override val throwable: Throwable) : AppError(throwable)
    data class Unknown(override val throwable: Throwable) : AppError(throwable)

    companion object {
        fun from(throwable: Throwable): AppError = when (throwable) {
            is java.io.IOException -> Network(throwable)
            is android.database.sqlite.SQLiteException -> Database(throwable)
            else -> Unknown(throwable)
        }
    }
}
```

**`DispatcherProvider.kt` + `DefaultDispatcherProvider.kt`** (`core/dispatcher/`):
```kotlin
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val unconfined: CoroutineDispatcher
}

class DefaultDispatcherProvider @Inject constructor() : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
}
```

**`DispatcherModule.kt`** (`core/di/`):
```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class DispatcherModule {
    @Binds
    @Singleton
    abstract fun bindDispatcherProvider(impl: DefaultDispatcherProvider): DispatcherProvider
}
```

**`BaseViewModel.kt`** (`core/base/`):
```kotlin
abstract class BaseViewModel : ViewModel() {
    protected fun launchSafely(
        onError: (AppError) -> Unit = {},
        block: suspend CoroutineScope.() -> Unit
    ): Job = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e  // Per FR-009a: re-throw to preserve cancellation semantics
        } catch (e: Throwable) {
            onError(AppError.from(e))
        }
    }
}
```

### 1.5 Navigation skeleton

**`AppDestination.kt`** (`presentation/navigation/`):
```kotlin
sealed class AppDestination(val route: String) {
    data object Browser : AppDestination("browser")
    data object Tabs : AppDestination("tabs")
    data object Bookmarks : AppDestination("bookmarks")
    data object History : AppDestination("history")
    data object Downloads : AppDestination("downloads")
    data object Settings : AppDestination("settings")
    data object Onboarding : AppDestination("onboarding")
}
```

**`AppNavGraph.kt`** (`presentation/navigation/`):
```kotlin
@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = AppDestination.Browser.route,
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(AppDestination.Browser.route) { BrowserScreen() }
        composable(AppDestination.Tabs.route) { TabsScreen() }
        composable(AppDestination.Bookmarks.route) { BookmarksScreen() }
        composable(AppDestination.History.route) { HistoryScreen() }
        composable(AppDestination.Downloads.route) { DownloadsScreen() }
        composable(AppDestination.Settings.route) { SettingsScreen() }
        composable(AppDestination.Onboarding.route) { OnboardingScreen() }
    }
}
```

### 1.6 Placeholder Screens (×7)

Mỗi file format giống nhau, ví dụ `BrowserScreen.kt`:
```kotlin
@Composable
fun BrowserScreen() {
    Text(
        text = stringResource(R.string.browser_screen_placeholder),
        style = MaterialTheme.typography.headlineMedium,
    )
}
```

Strings trong `app/src/main/res/values/strings.xml`:
```xml
<resources>
    <!-- existing app_name -->
    <string name="browser_screen_placeholder">Browser</string>
    <string name="tabs_screen_placeholder">Tabs</string>
    <string name="bookmarks_screen_placeholder">Bookmarks</string>
    <string name="history_screen_placeholder">History</string>
    <string name="downloads_screen_placeholder">Downloads</string>
    <string name="settings_screen_placeholder">Settings</string>
    <string name="onboarding_screen_placeholder">Onboarding</string>
</resources>
```

### 1.7 MainActivity rewrite

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ThirdtySixBrowserTheme {  // typo from template — Spec 003 will rename
                AppNavGraph()
            }
        }
    }
}
```

### 1.8 Unit tests

**`ResultTest.kt`** — verify FR-006:
- `map_success_appliesTransform()`: `Result.Success(5).map { it * 2 }` → `Result.Success(10)`
- `map_error_preservesError()`: `Result.Error(IOException()).map { ... }` → still `Result.Error`
- `fold_success_callsOnSuccess()`, `fold_error_callsOnError()`

**`BaseViewModelTest.kt`** — verify FR-009 + FR-009a:
- `@Before`: `Dispatchers.setMain(StandardTestDispatcher())`; `@After`: `Dispatchers.resetMain()`
- `launchSafely_ioException_mapsToNetworkError()`: throw `IOException` → `onError` receives `AppError.Network`
- `launchSafely_sqliteException_mapsToDatabaseError()`: throw `SQLiteException` → `AppError.Database`
- `launchSafely_genericException_mapsToUnknown()`: throw `RuntimeException` → `AppError.Unknown`
- `launchSafely_cancellation_isRethrown()`: throw `CancellationException` → onError NOT called, exception propagates

### 1.9 Quality gates verification (per FR-018, FR-019)

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew detekt
./gradlew ktlintCheck
bash .specify/scripts/bash/verify-16kb-alignment.sh
```

All MUST pass. Detekt baseline `app/detekt-baseline.xml` ideally not extended — if Hilt-generated code triggers MagicNumber, add `@file:Suppress` on generated path or extend baseline with PR justification (per FR-021).

### 1.10 Agent context update

Update [CLAUDE.md](../../CLAUDE.md) between `<!-- SPECKIT START -->` and `<!-- SPECKIT END -->` markers to point Active Spec to `specs/002-clean-architecture-skeleton-di/plan.md`. Done as part of `/speckit.plan` execution (this command).

## Phase 2 — Tasks (NOT created by /speckit.plan)

`tasks.md` will be generated by `/speckit.tasks` based on this plan + spec. Expected ~15-20 tasks ordered by:
1. Day-1 smoke test (Risk #1) — gate before all other tasks
2. Version catalog + build.gradle.kts wiring
3. Core utilities (Result, AppError, DispatcherProvider, BaseViewModel) + tests
4. Navigation files (AppDestination, AppNavGraph)
5. 7 placeholder Screens + strings.xml
6. Application class + AndroidManifest + MainActivity rewrite
7. Quality gate verification (assembleDebug/Release, lint, detekt, ktlint, 16KB)
8. Agent context update + commit

## Constitution Re-check (post-design)

| Principle | Status |
|---|---|
| All 11 principles | ✅ Still PASS |

No new violations introduced by detailed design. Hilt artifact ID correction (`hilt-compiler` not `hilt-android-compiler`) updated; Spec FR-022 referenced the wrong ID — research.md flagged this and plan uses correct ID. Spec amendment may be done via `/speckit.clarify` follow-up or accepted as research-driven correction (recommend latter — research.md explicitly captures it as Risk #4 mitigation).

## Open items for implementer

1. **Day-1 Hilt smoke test result** (research.md Risk #1) — must be verified BEFORE building rest of spec. Document outcome in PR description.
2. **`./gradlew :app:dependencies | grep lifecycle`** check after build — confirm `lifecycle-viewmodel-compose:2.10.0` is the resolved version (research.md Risk #3).
3. **`./gradlew :app:dependencies --configuration kspDebugKotlin | grep hilt`** check — confirm `hilt-compiler:2.59.2` (NOT `hilt-android-compiler`) is resolved (research.md Risk #4).
4. **Detekt baseline diff** — ideally zero new entries (FR-021). If Hilt-generated code triggers, document in PR.
