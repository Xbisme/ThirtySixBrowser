# Implementation Plan: Theme + Typography + Dark Mode

**Branch**: `003-theme-typography-darkmode` | **Date**: 2026-05-01 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from [spec.md](spec.md), research from [research.md](research.md)

## Summary

Spec 003 dựng Material3 theme infrastructure cho ThirtySixBrowser:

1. **Brand color** Deep Teal seed `#0F766E` (light primary) / `#5EEAD4` (dark primary), tertiary Cyan `#0891B2` / `#67E8F9`. Pin 30+ color value cho cả light + dark scheme vào `Color.kt` (generated qua Material Theme Builder, **KHÔNG dùng** `dynamicColorScheme(seed)` runtime API để đảm bảo determinism + offline).
2. **Typography**: Poppins (heading) + Inter (body) bundled local trong `app/src/main/res/font/` (4 weight tổng: Poppins Medium 500, SemiBold 600, Inter Regular 400, Medium 500). Map 16 M3 typography role qua `Type.kt`.
3. **Spacing tokens** 5 mức (xs=4dp, sm=8dp, md=16dp, lg=24dp, xl=32dp) trong `Spacing.kt`. **Shapes** dùng M3 default trong `Shape.kt`.
4. **`ThirtySixTheme` Composable** support Light/Dark/System + dynamic color (Android 12+ via `dynamicLightColorScheme`/`dynamicDarkColorScheme`); fallback Deep Teal seed khi `dynamicColor = false` hoặc API < 31.
5. **In-memory `themeMode`** ở `MainActivity` (`MutableState<ThemeMode>` default System) — chưa wire DataStore (Spec 006), chưa expose UI toggle (Spec 016).
6. **Cold-start window flash fix**: `themes.xml` + `values-night/themes.xml` set `windowBackground = @color/window_background_light/dark` để tránh white flash trên dark mode.
7. **Detekt baseline cleanup**: rewrite `Color.kt` (xóa 6 MagicNumber entries) + rename `ThirdtySixBrowserTheme` → `ThirtySixTheme` (xóa 1 FunctionNaming entry) + verify `Greeting`/`GreetingPreview` đã removed (Spec 002 cleanup); kết quả `<CurrentIssues/>` empty.

**Approach** (rút từ research.md):

- **Compose BOM 2026.04.01** đã include Material3 1.4.0 với cả `dynamicLightColorScheme(context)` và `dynamicDarkColorScheme(context)` API (verified release notes 2026-05-01) — KHÔNG cần thêm dep mới.
- **Material Theme Builder web** (m3.material.io/theme-builder) export Kotlin `Color` constants — dùng tại implement-time, pin tay vào `Color.kt`.
- **Font binary**: download từ Google Fonts (`fonts.google.com/specimen/Poppins` + `fonts.google.com/specimen/Inter`) bản static `.ttf` (không variable) — verify SHA256 để anti-tamper.
- **WCAG ratios**: tính bằng formula WCAG 2.1 + double-check qua Material Theme Builder built-in checker. Document đầy đủ table trong `research.md` cho 24+ critical pair (light + dark).
- KHÔNG thêm package mới vào `libs.versions.toml`. Tất cả API đã có sẵn từ Compose BOM (Spec 001).

## Technical Context

**Language/Version**: Kotlin 2.3.21 (host), Java target 11
**Primary Dependencies (existing — no new in Spec 003)**:

- Compose BOM `2026.04.01` (Spec 001) → resolves Material3 `1.4.0`, Compose UI `1.11.0`
- `androidx.compose.material3:material3:1.4.0` (BOM-managed) — provides `MaterialTheme`, `lightColorScheme`/`darkColorScheme`, `dynamicLightColorScheme`/`dynamicDarkColorScheme`, `Typography`, `Shapes`
- `androidx.compose.ui:ui-text` (BOM-managed) — provides `FontFamily`, `Font(R.font.*)`
- `androidx.core:core-ktx` (Spec 001 baseline)
- AGP 9.1.1, Gradle 9.5.0, Detekt 1.23.8, ktlint plugin 14.2.0 (Spec 001)
- Hilt 2.59.2, Navigation Compose 2.9.8, Lifecycle 2.10.0 (Spec 002 — không đụng Spec 003)

**Storage**: N/A — Spec 003 không động data layer (theme persistence defer Spec 006)
**Testing**: JUnit 4.13.2 (Spec 001) cho 4 unit test mới: `Spacing` value, `Typography` font family mapping, `ThemeMode` enum, optional Compose UI test cho `ThirtySixTheme` render
**Target Platform**: Android 7.0 (API 24) → Android 16 (API 36, compileSdk 36 + minorApiLevel 1). Min SDK 24
**Project Type**: Mobile app (Android single-module, Spec 002 Clean Architecture skeleton)
**Performance Goals**:

- Cold start ≤ 1.5s đến render `BrowserScreen` placeholder (SC-001, giữ nguyên Spec 002)
- Theme toggle re-compose ≤ 100ms (SC-002, native Compose recomposition)

**Constraints**:

- 16KB page size compliance (Constitution §IX) — Spec 003 KHÔNG thêm package có `.so`. Font `.ttf` là asset, không phải native lib. Set `.so` hiện tại không đổi (vẫn `libandroidx.graphics.path.so` từ Compose, đã verified 0x4000)
- APK size delta ≤ 500KB (SC-007 — kỳ vọng: 4 font ~150-200KB + theme code <50KB → tổng ~250KB)
- WCAG AA contrast ≥ 4.5:1 normal text + 3:1 large/icon cho 24+ critical color pair (SC-010)
- 0 hardcoded `Color(0xFF...)` ngoài `Color.kt`, 0 hardcoded `fontSize`/`TextStyle(` ngoài `Type.kt`, 0 hardcoded `.padding(N.dp)` ngoài `Spacing.kt` (SC-008)

**Scale/Scope**: 6 file Kotlin (mới) + 4 font asset + 2 themes.xml + 1 colors.xml + 4 unit test ≈ 17 file mới hoặc sửa. Plus rename refactor cho `MainActivity.kt` + 7 placeholder Composable nếu chúng reference theme name cũ.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Note |
|---|---|---|
| **I. Privacy & Security First** | ✅ PASS | Theme infrastructure không thêm data path, không network, không storage. Font binary local, KHÔNG dùng downloadable fonts (vi phạm "no runtime fetch"). |
| **II. Google Play Compliance** | ✅ PASS | Không thêm permission. Material3 + dynamic color là Play-approved API. Font Poppins + Inter SIL OFL 1.1 license cho phép bundle commercial. |
| **III. Code Quality & Safety + No-Hardcode** | ✅ PASS với chú ý | Color tokens trong `Color.kt` (đúng layout Constitution §III table dòng "Colors"); Typography trong `Type.kt`; Shapes trong `Shape.kt`; Spacing trong `Spacing.kt`. SC-008 enforce 0 inline literal. Detekt baseline target = empty (FR-016). MaterialTheme.colorScheme.* / typography.* / shapes.* exclusively trong consumers. |
| **IV. Clean Architecture** | ✅ PASS | Theme thuần presentation layer. KHÔNG đụng `data/`, `domain/`. ThemeMode enum là presentation state, KHÔNG repo/usecase. |
| **V. Performance Excellence** | ✅ PASS | Cold start ≤1.5s giữ nguyên (font load tại first compose, Compose 1.x đã optimize). Theme toggle native Compose recomposition. |
| **VI. Testing Discipline** | ✅ PASS | FR-023 yêu cầu 4 unit test mới. Compose UI test optional. |
| **VII. Offline-First Architecture** | N/A | Spec 003 không động persistence; theme mode in-memory chỉ. Spec 006 wire DataStore. |
| **VIII. Localization & Accessibility** | ✅ PASS với chú ý | **WCAG AA contrast** SC-010 enforce 100% critical color pair pass — match Constitution §VIII "Color contrast MUST meet WCAG AA (4.5:1 / 3:1)". Dynamic font scaling: dùng `sp` qua `MaterialTheme.typography.*` nên auto support system font scale. KHÔNG hardcode `fontSize` ở consumers. |
| **IX. Dependency Currency & 16KB** | ✅ PASS | KHÔNG thêm package mới. Set `.so` không thay đổi → 16KB CI gate continue pass. Font `.ttf` không phải `.so`. |
| **X. Simplicity & Build Order** | ✅ PASS | Spec 003 đúng thứ tự (sau 002). KHÔNG pre-create cho spec 004-006 (incremental scope per project memory). YAGNI: bỏ `ThemeViewModel`/`ThemeRepository`/`ToggleThemeUseCase` (FR-015) — defer Spec 006/016. |
| **XI. Build Configuration** | ✅ PASS | Không thêm flavor. Signing/R8 từ Spec 001 giữ nguyên. Theme tokens KHÔNG đụng `buildConfigField`. |

**Gate result**: PASS. No constitution violations.

### Complexity Tracking

> Empty — không có deviation cần justify.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (none) | | |

## Project Structure

### Documentation (this feature)

```text
specs/003-theme-typography-darkmode/
├── spec.md              ✅ Done (spec + 2 clarifications: WCAG SC-010, window bg flash fix)
├── plan.md              ← This file
├── research.md          ✅ Done (Material3 1.4.0 dynamic API verified, Material Theme Builder workflow,
│                            WCAG ratio table 24 pair, font sources + SHA256, themes.xml fix approach)
├── data-model.md        ← Phase 1 output (theme token catalog — Color/Type/Shape/Spacing/ThemeMode)
├── quickstart.md        ← Phase 1 output (verification steps for SC-001..010)
├── checklists/
│   └── requirements.md  ✅ Done (spec quality checklist, all items pass)
└── tasks.md             (Phase 2 — generated by /speckit-tasks)
```

> **No `contracts/`** — Spec 003 không expose external interface (no API, no public CLI, no IPC). Theme tokens là internal Compose API; không có repo/usecase contract trong scope spec này.

### Source Code (repository root)

**Existing tree** (post Spec 002):

```text
app/src/main/kotlin/com/raumanian/thirtysix/browser/
├── ThirtySixApplication.kt              (Spec 002)
├── MainActivity.kt                      (Spec 002 — wraps AppNavGraph trong ThirdtySixBrowserTheme — typo)
├── core/
│   ├── base/BaseViewModel.kt            (Spec 002)
│   ├── result/Result.kt                 (Spec 002)
│   ├── error/AppError.kt                (Spec 002)
│   ├── dispatcher/{DispatcherProvider,DefaultDispatcherProvider}.kt
│   └── di/DispatcherModule.kt
├── presentation/
│   ├── navigation/{AppDestination,AppNavGraph}.kt
│   └── {browser,tabs,bookmarks,history,downloads,settings,onboarding}/<Feature>Screen.kt
└── ui/theme/                            (template từ Spec 001, Spec 002 KHÔNG sửa — Spec 003 sẽ rewrite)
    ├── Color.kt                          (6 hex template — sẽ rewrite Deep Teal)
    ├── Theme.kt                          (ThirdtySixBrowserTheme typo — sẽ rename)
    └── Type.kt                           (Roboto default — sẽ rewrite Poppins/Inter)

app/src/main/res/
├── values/
│   ├── strings.xml                       (Spec 002: 7 placeholder labels + app_name)
│   └── themes.xml                        (template — sẽ modify windowBackground)
└── (no values-night/ yet)
```

**Tree after Spec 003 implementation**:

```text
app/src/main/kotlin/com/raumanian/thirtysix/browser/
├── ThirtySixApplication.kt              (unchanged)
├── MainActivity.kt                      ✏️  EDIT — themeMode state + ThirtySixTheme wrap
├── core/                                (unchanged)
└── presentation/
    ├── navigation/                      (unchanged)
    ├── theme/                           ★ NEW package (replace ui/theme/)
    │   ├── Color.kt                     ★ REWRITE — Deep Teal palette ~30 role × 2 scheme
    │   ├── Type.kt                      ★ REWRITE — Poppins + Inter FontFamily + Typography
    │   ├── Theme.kt                     ★ REWRITE — ThirtySixTheme Composable
    │   ├── Shape.kt                     ★ NEW — M3 default Shapes
    │   ├── Spacing.kt                   ★ NEW — 5-token Dp object
    │   └── ThemeMode.kt                 ★ NEW — enum Light/Dark/System
    └── {browser,tabs,...}/...            (unchanged — chỉ update import nếu reference theme)

app/src/main/res/
├── font/                                ★ NEW directory
│   ├── poppins_medium.ttf               ★ NEW asset (~50KB)
│   ├── poppins_semibold.ttf             ★ NEW asset (~50KB)
│   ├── inter_regular.ttf                ★ NEW asset (~30KB)
│   └── inter_medium.ttf                 ★ NEW asset (~30KB)
├── values/
│   ├── strings.xml                      (unchanged)
│   ├── themes.xml                       ✏️  EDIT — set windowBackground light + Theme.ThirtySix base
│   └── colors.xml                       ★ NEW (or modify) — window_background_light + window_background_dark
└── values-night/
    └── themes.xml                       ★ NEW — override windowBackground dark

app/
└── detekt-baseline.xml                  ✏️  CLEAR — <CurrentIssues/> empty

app/src/test/kotlin/com/raumanian/thirtysix/browser/
└── presentation/theme/
    ├── SpacingTest.kt                   ★ NEW — verify 5 Dp values
    ├── TypographyTest.kt                ★ NEW — verify FontFamily mapping (Poppins for headings, Inter for body)
    └── ThemeModeTest.kt                 ★ NEW — verify enum 3 values
```

**Explicitly NOT created (per "incremental scope")**:

- `data/datastore/SettingsDataStore.kt` — Spec 006
- `domain/model/ThemePreferences.kt` — Spec 006
- `presentation/settings/ThemeToggleSection.kt` — Spec 016
- `core/constants/ThemeConstants.kt` — không cần (theme tokens là Compose first-class artifacts, không phải constants per Constitution §III table)
- 8 locale `values-{vi,...}/strings.xml` — Spec 004 (theme không có user-facing copy mới)
- `ThemeViewModel`, `ThemeRepository`, `ToggleThemeUseCase` — Spec 006/016
- High-contrast accessibility theme — defer post-v1.0
- SplashScreen API (`androidx.core:core-splashscreen`) — Spec 017
- Theme transition animation custom — Spec 016

**Migration note**: Spec 002 tạo theme files trong `ui/theme/` (template path). Spec 003 sẽ **move** sang `presentation/theme/` để align với Constitution §IV layout. Files cũ trong `ui/theme/` sẽ bị xóa hoàn toàn (không backward compat alias).

**Structure Decision**: Single-module Android app, package-by-feature trong Clean Architecture layers. Theme module ở `presentation/theme/` per Constitution §IV layout. Spec 003 hoàn tất foundation design system; Spec 004+ consume qua `MaterialTheme.*` và `Spacing.*`.

## Phase 1 — Implementation Steps

> Phase 0 (research) đã hoàn tất tại [research.md](research.md). Phase 1 = design artifacts + agent context update. Tasks chi tiết sẽ được generate bởi `/speckit-tasks` (Phase 2).

### 1.1 Material Theme Builder export

1. Mở [m3.material.io/theme-builder](https://m3.material.io/theme-builder).
2. Set primary seed = `#0F766E` (Deep Teal). Set secondary = auto (M3 sinh slate-blend); set tertiary seed = `#0891B2` (Cyan-600). Set neutral = auto.
3. Export "Kotlin" → 2 file `light.kt` + `dark.kt` chứa ~30 `Color` constant mỗi scheme.
4. Verify built-in WCAG AA badge ✓ trên cả light + dark trước khi export.
5. Pin values vào `Color.kt` (single file, 60+ constants — `md_theme_light_*` + `md_theme_dark_*` prefix theo M3 convention).

### 1.2 Font binary download

1. `https://fonts.google.com/specimen/Poppins` → download family → extract `Poppins-Medium.ttf` + `Poppins-SemiBold.ttf` (static, không variable).
2. `https://fonts.google.com/specimen/Inter` → download family → extract `Inter-Regular.ttf` + `Inter-Medium.ttf` (static).
3. Rename: lowercase + underscore (Android `R.font.*` requirement). Final 4 file:
   - `app/src/main/res/font/poppins_medium.ttf`
   - `app/src/main/res/font/poppins_semibold.ttf`
   - `app/src/main/res/font/inter_regular.ttf`
   - `app/src/main/res/font/inter_medium.ttf`
4. Verify SHA256 (research.md có expected hashes — anti-tamper) + file size mỗi file < 60KB.
5. KHÔNG bundle `.otf` variable font (vi phạm spec FR-004 + có nguy cơ render bug API 24-26).

### 1.3 Theme tokens — `presentation/theme/`

**`Color.kt`** — paste exports từ step 1.1:

```kotlin
val md_theme_light_primary = Color(0xFF0F766E)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFA7F3D0)
// ... ~28 other roles light
val md_theme_dark_primary = Color(0xFF5EEAD4)
val md_theme_dark_onPrimary = Color(0xFF003733)
// ... ~28 other roles dark

internal val LightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    // ... full 30-role mapping
)

internal val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    // ... full 30-role mapping
)
```

**`Type.kt`**:

```kotlin
val Poppins = FontFamily(
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
)
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
)

internal val Typography = Typography(
    displayLarge = Typography().displayLarge.copy(fontFamily = Poppins, fontWeight = FontWeight.SemiBold),
    displayMedium = Typography().displayMedium.copy(fontFamily = Poppins, fontWeight = FontWeight.SemiBold),
    displaySmall = Typography().displaySmall.copy(fontFamily = Poppins, fontWeight = FontWeight.SemiBold),
    headlineLarge = Typography().headlineLarge.copy(fontFamily = Poppins, fontWeight = FontWeight.SemiBold),
    headlineMedium = Typography().headlineMedium.copy(fontFamily = Poppins, fontWeight = FontWeight.SemiBold),
    headlineSmall = Typography().headlineSmall.copy(fontFamily = Poppins, fontWeight = FontWeight.SemiBold),
    titleLarge = Typography().titleLarge.copy(fontFamily = Poppins, fontWeight = FontWeight.Medium),
    titleMedium = Typography().titleMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    titleSmall = Typography().titleSmall.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    bodyLarge = Typography().bodyLarge.copy(fontFamily = Inter, fontWeight = FontWeight.Normal),
    bodyMedium = Typography().bodyMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Normal),
    bodySmall = Typography().bodySmall.copy(fontFamily = Inter, fontWeight = FontWeight.Normal),
    labelLarge = Typography().labelLarge.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    labelMedium = Typography().labelMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    labelSmall = Typography().labelSmall.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
)
```

**`Shape.kt`**:

```kotlin
internal val Shapes = Shapes()  // M3 defaults: extraSmall=4, small=8, medium=12, large=16, extraLarge=28
```

**`Spacing.kt`**:

```kotlin
object Spacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
}
```

**`ThemeMode.kt`**:

```kotlin
enum class ThemeMode { Light, Dark, System }
```

**`Theme.kt`**:

```kotlin
@Composable
fun ThirtySixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
```

### 1.4 MainActivity — themeMode state

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var themeMode by remember { mutableStateOf(ThemeMode.System) }
            val darkTheme = when (themeMode) {
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
                ThemeMode.System -> isSystemInDarkTheme()
            }
            ThirtySixTheme(darkTheme = darkTheme) {
                AppNavGraph()
            }
        }
    }
}
```

### 1.5 Window background fix — `themes.xml`

**`app/src/main/res/values/colors.xml`** (new or modify):

```xml
<resources>
    <color name="window_background_light">#FAFAF9</color>
    <color name="window_background_dark">#0A0F0E</color>
</resources>
```

**`app/src/main/res/values/themes.xml`** (modify):

```xml
<resources>
    <style name="Theme.ThirtySix" parent="Theme.Material3.DayNight.NoActionBar">
        <item name="android:windowBackground">@color/window_background_light</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
    </style>
</resources>
```

**`app/src/main/res/values-night/themes.xml`** (NEW):

```xml
<resources>
    <style name="Theme.ThirtySix" parent="Theme.Material3.DayNight.NoActionBar">
        <item name="android:windowBackground">@color/window_background_dark</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
    </style>
</resources>
```

**`AndroidManifest.xml`** (verify): `<application android:theme="@style/Theme.ThirtySix" ...>`. Spec 002 đã set theme name; chỉ cần đổi nếu cần khớp `Theme.ThirtySix`.

### 1.6 Detekt baseline cleanup

Sau khi rewrite Color.kt + rename `ThirdtySixBrowserTheme` → `ThirtySixTheme`, baseline file `app/detekt-baseline.xml`:

```xml
<?xml version="1.0" ?>
<SmellBaseline>
  <ManuallySuppressedIssues/>
  <CurrentIssues/>
</SmellBaseline>
```

Trước khi clear, verify Spec 002 đã thực sự xóa `Greeting`/`GreetingPreview` từ `MainActivity.kt`. Nếu chưa, xóa 2 function đó như một phần Spec 003 cleanup. Chạy `./gradlew detekt` post-rewrite — nếu có entry còn lại không hợp lệ, fix root cause hoặc add `@Suppress` targeted với comment (KHÔNG cover bằng baseline mới).

### 1.7 Refactor: rename theme references

Files reference `ThirdtySixBrowserTheme` (typo):

- `MainActivity.kt` — đã rewrite step 1.4
- `ui/theme/Theme.kt` — file gốc; xóa hoàn toàn
- 7 placeholder Composable Spec 002 — verify import; thường KHÔNG import theme name (chỉ `MaterialTheme.typography.*` reference qua wrapper) — nếu có thì update
- Preview annotation (nếu có) — update

Move `ui/theme/{Color,Type,Theme}.kt` → `presentation/theme/{Color,Type,Theme}.kt` qua `git mv` (preserve history).

### 1.8 Unit tests — `app/src/test/kotlin/.../presentation/theme/`

**`SpacingTest.kt`**:

```kotlin
class SpacingTest {
    @Test fun xs_is_4dp() = assertEquals(4.dp, Spacing.xs)
    @Test fun sm_is_8dp() = assertEquals(8.dp, Spacing.sm)
    @Test fun md_is_16dp() = assertEquals(16.dp, Spacing.md)
    @Test fun lg_is_24dp() = assertEquals(24.dp, Spacing.lg)
    @Test fun xl_is_32dp() = assertEquals(32.dp, Spacing.xl)
}
```

**`TypographyTest.kt`** — verify FontFamily mapping (test object identity):

```kotlin
class TypographyTest {
    @Test fun headlineMedium_uses_Poppins() = assertEquals(Poppins, Typography.headlineMedium.fontFamily)
    @Test fun bodyLarge_uses_Inter() = assertEquals(Inter, Typography.bodyLarge.fontFamily)
    @Test fun titleMedium_uses_Inter() = assertEquals(Inter, Typography.titleMedium.fontFamily)
    @Test fun titleLarge_uses_Poppins() = assertEquals(Poppins, Typography.titleLarge.fontFamily)
}
```

**`ThemeModeTest.kt`**:

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

### 1.9 Quality gates verification

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew detekt              # MUST pass with empty baseline
./gradlew ktlintCheck
bash .specify/scripts/bash/verify-16kb-alignment.sh
```

All MUST pass. APK size delta vs Spec 002 baseline ≤ 500KB (SC-007).

### 1.10 Manual verification (per User Stories)

1. Cài APK debug → mở app trên emulator API 36 system Light → verify `BrowserScreen` placeholder render Deep Teal accent (không purple template).
2. Toggle Quick Settings → Dark → verify app re-compose dark surface.
3. Cold start trên dark mode → verify KHÔNG flash trắng (window bg dark).
4. Đổi wallpaper sang màu cam (API 31+) → mở app → verify `colorScheme.primary` tilt cam (dynamic color).
5. Programmatic test: `ThirtySixTheme(darkTheme = true, dynamicColor = false) { Text(...) }` → render dark Deep Teal scheme bất kể system.

### 1.11 Agent context update

Update [CLAUDE.md](../../CLAUDE.md) giữa `<!-- SPECKIT START -->` và `<!-- SPECKIT END -->` markers point Active Spec → `specs/003-theme-typography-darkmode/plan.md`.

Cũng update CLAUDE.md "Code Style" section: currently nói "NO custom font for v1.0" → đổi thành "Poppins (heading) + Inter (body) bundled local trong `res/font/`, KHÔNG dùng downloadable fonts". Same cho `project-context.md` (trong `.claude/claude-app/`). **Defer post-merge** per dev-workflow Bước 8 — tránh đụng nhiều file trong implement phase.

## Phase 2 — Tasks (NOT created by /speckit-plan)

`tasks.md` will be generated by `/speckit-tasks` based on this plan + spec. Expected ~15-20 tasks ordered by:

1. Material Theme Builder export + paste vào `Color.kt` (1.1, 1.3-Color)
2. Font binary download + verify SHA256 + place vào `res/font/` (1.2)
3. `Type.kt` + `Shape.kt` + `Spacing.kt` + `ThemeMode.kt` + `Theme.kt` (1.3)
4. `MainActivity.kt` rewrite — themeMode state + ThirtySixTheme wrap (1.4)
5. `themes.xml` + `values-night/themes.xml` + `colors.xml` (1.5)
6. Move `ui/theme/` → `presentation/theme/` qua `git mv` + cleanup orphan refs (1.7)
7. Detekt baseline clear (1.6)
8. 4 unit test (1.8)
9. Quality gate verification + 16KB script (1.9)
10. Manual verification trên emulator (1.10)
11. Agent context update (1.11)

## Constitution Re-check (post-design)

| Principle | Status |
|---|---|
| All 11 principles | ✅ Still PASS |

No new violations introduced by detailed design. WCAG AA gate (SC-010) match Constitution §VIII; Material Theme Builder built-in checker double-verifies. Detekt baseline empty target match Constitution §III enforcement. Font bundle local match §I no-runtime-fetch + §II Play-approved (no downloadable font privacy concern).

## Open items for implementer

1. **Material Theme Builder export** — verify built-in WCAG AA badge ✓ trước export. Nếu có pair fail, adjust seed luminance trong builder + re-export. Document final export config screenshot trong PR description.
2. **Font SHA256 verification** — research.md có expected hashes; bất khớp → re-download từ Google Fonts (không dùng mirror).
3. **`./gradlew :app:dependencies | grep material3`** — confirm `material3:1.4.0` (or higher) resolved từ Compose BOM; nếu < 1.4.0 thì `dynamicDarkColorScheme` API có thể missing → bump BOM (research.md Risk #2).
4. **Detekt baseline diff** — sau rewrite phải = `<CurrentIssues/>` empty. Nếu Hilt-generated code (Spec 002) trigger MagicNumber, document trong PR và targeted `@Suppress` (KHÔNG add baseline entry).
5. **APK size measurement** — chạy `./gradlew :app:assembleRelease` rồi `du -h app/build/outputs/apk/release/app-release.apk`. So sánh với baseline Spec 002 (record trong `recent changes` Spec 002). Delta ≤ 500KB pass SC-007.
