# Feature Specification: Theme + Typography + Dark Mode

**Feature Branch**: `003-theme-typography-darkmode`
**Created**: 2026-05-01
**Status**: Draft
**Input**: User description: "Spec 003 — theme-typography-darkmode. Dựng Material3 theme infrastructure cho ThirtySixBrowser: brand color Deep Teal (seed `#0F766E`), typography Poppins (heading) + Inter (body) bundled local trong `res/font/`, Spacing tokens (xs/sm/md/lg/xl), Shape tokens, hỗ trợ Light/Dark/System mode + dynamic color (Android 12+). Spec 003 chỉ làm theme infrastructure — theme persistence (DataStore) defer Spec 006, UI toggle defer Spec 016. Rewrite + rename `ThirdtySixBrowserTheme` → `ThirtySixTheme`, clear toàn bộ 9 entries trong detekt baseline."

## Clarifications

### Session 2026-05-01

- Q: Có nên add explicit Success Criterion verify WCAG contrast cho custom Deep Teal palette không? → A: A — Add SC-010 yêu cầu 100% critical color pairs (primary/onPrimary, secondary/onSecondary, tertiary/onTertiary, surface/onSurface, error/onError) đạt ≥ WCAG AA (4.5:1 normal text, 3:1 large/icon) trên cả light + dark scheme; document ratio table trong `research.md`.
- Q: Spec 003 có nên fix theme-aware `windowBackground` để tránh white flash trên dark mode trong cold start không? → A: A — Update `res/values/themes.xml` + tạo `res/values-night/themes.xml` set `windowBackground` theo surface color light/dark; add FR-026 + acceptance scenario. KHÔNG đụng `core-splashscreen` API (giữ Spec 017 scope nguyên).

## User Scenarios & Testing *(mandatory)*

### User Story 1 — App render đúng theme Light/Dark/System (Priority: P1)

Sau khi merge spec 003, một developer hoặc QA cài app debug lên emulator/device chạy Android 7.0+, thay đổi system theme (Light ↔ Dark trong Quick Settings) và thấy app re-compose toàn bộ surface/text/background sang màu tương ứng. Cụ thể: 7 placeholder screen hiện có (từ Spec 002) đều đổi màu nền + màu text khi system theme đổi, không còn màu purple/pink template, mà dùng palette Deep Teal seed.

**Why this priority**: Đây là điều kiện cần để mọi UI spec sau (007 WebView, 009 Omnibox, 011 Tabs, 013 Bookmarks, ...) có nền tảng visual nhất quán. Không có theme infrastructure, mỗi spec sau sẽ tự define color/typography → fragmentation và vi phạm Constitution §III.

**Independent Test**: Cài APK debug → mở app → vào Settings system → toggle Dark mode → quay lại app → toàn bộ 7 placeholder screen đổi sang dark surface + text sáng. Toggle về Light → đổi ngược lại. Verify không có hex `0xFF...` raw nào còn xuất hiện trong UI (tất cả qua `MaterialTheme.colorScheme.*`).

**Acceptance Scenarios**:

1. **Given** APK debug đã cài lên device API 36 với system theme = Light, **When** người dùng mở app, **Then** `BrowserScreen` placeholder render với background = `colorScheme.background` light và text = `colorScheme.onBackground` light (không phải màu purple template Spec 001).
2. **Given** app đang mở ở Light theme, **When** người dùng đổi system theme sang Dark, **Then** app re-compose tự động: background sang `colorScheme.background` dark, text sang `colorScheme.onBackground` dark — không cần restart app.
3. **Given** code gọi `ThirtySixTheme(darkTheme = true) { ... }` programmatic, **When** Composable render, **Then** dark scheme được áp dụng bất kể system setting.
4. **Given** cả 7 placeholder Composable từ Spec 002, **When** spec 003 merge, **Then** mỗi screen vẫn render label đúng (text "Browser", "Tabs", ...) với typography mới (Poppins headline) thay vì Roboto template.
5. **Given** device API 36 với system Dark mode, **When** cold start app từ launcher, **Then** window background hiển thị màu surface dark (~`#0A0F0E`) suốt khoảng cold start cho đến khi `BrowserScreen` render — KHÔNG flash trắng. Tương tự với Light mode hiển thị surface light, không flash đen.

---

### User Story 2 — Dynamic color áp dụng từ wallpaper trên Android 12+ (Priority: P1)

Trên thiết bị Android 12+ (API 31+), khi user đổi wallpaper với màu chủ đạo khác (vd: cam, xanh lá), app tự động sinh palette Material You từ wallpaper và áp dụng cho UI thay vì dùng Deep Teal fixed seed. Trên Android 7-11 hoặc khi user explicit disable dynamic color, app fallback về Deep Teal seed.

**Why this priority**: Material3 best practice — tôn trọng user customization của system. Android 12+ là default behavior khi `dynamicLightColorScheme(LocalContext.current)` được dùng. Không support dynamic color sẽ đi ngược UX Material3 hiện đại.

**Independent Test**: Trên emulator API 36, đổi wallpaper sang màu cam đậm → mở app → primary color tilt sang cam (chứ không Deep Teal). Trên emulator API 24, mở app → primary color = Deep Teal seed (vì API < 31).

**Acceptance Scenarios**:

1. **Given** device API 31+ với wallpaper màu cam, **When** app mở, **Then** `colorScheme.primary` lấy từ `dynamicLightColorScheme(context)` → tilt cam, không phải Deep Teal.
2. **Given** device API 24 (Android 7.0), **When** app mở, **Then** `colorScheme.primary` = Deep Teal seed (`#0F766E` light scheme; `#5EEAD4` dark scheme) — fallback static seed.
3. **Given** code gọi `ThirtySixTheme(dynamicColor = false)`, **When** Composable render trên Android 12+, **Then** app dùng Deep Teal seed (override dynamic).

---

### User Story 3 — Spec sau dùng được tokens chuẩn (Priority: P1)

Khi developer bắt đầu Spec 004+ (đặc biệt 007 WebView, 009 Omnibox, 011 Tabs, 016 Settings), các token color/typography/shape/spacing đã sẵn sàng consume qua `MaterialTheme.*` và `Spacing.*` — không phải tự define hex/dp/sp inline. Concretely: một Composable mới có thể dùng `MaterialTheme.colorScheme.primary`, `MaterialTheme.typography.titleLarge`, `MaterialTheme.shapes.medium`, `Spacing.md` mà không vi phạm Constitution §III No-Hardcode.

**Why this priority**: Đây là phần "design system foundation". Không có nó, mỗi UI spec sau sẽ phải tự define spacing/shape → fragmentation. Đặt vào Spec 003 ngay.

**Independent Test**: Viết unit test cho:
- `Spacing` object có 5 property `xs/sm/md/lg/xl` kiểu `Dp` với giá trị 4/8/16/24/32.
- Composable test render `Text` với `MaterialTheme.typography.headlineMedium` → kiểu `FontFamily` resolved về Poppins (kiểm qua `LocalDensity` hoặc snapshot screenshot).
- Composable test render `Surface(shape = MaterialTheme.shapes.medium)` không crash + dùng đúng `RoundedCornerShape(12.dp)` (M3 default).

**Acceptance Scenarios**:

1. **Given** một Composable mới gọi `Modifier.padding(Spacing.md)`, **When** preview/render, **Then** padding = 16.dp đồng nhất.
2. **Given** một `Text` Composable dùng `MaterialTheme.typography.headlineMedium`, **When** render, **Then** font family = Poppins (verify qua test bằng `androidx.compose.ui.test` + `assertFontFamily` hoặc snapshot).
3. **Given** một `Text` Composable dùng `MaterialTheme.typography.bodyLarge`, **When** render, **Then** font family = Inter.
4. **Given** Constitution §III gate (Detekt MagicNumber + ktlint + Lint), **When** chạy `./gradlew detekt`, **Then** baseline file `app/detekt-baseline.xml` còn **0 entries** (clean state) — 9 entries cũ đều đã removed do file gốc bị rewrite.

---

### Edge Cases

- **Process death + theme mode in-memory**: Spec 003 lưu `themeMode` trong `MutableState` ở `MainActivity` (chưa DataStore). Khi process death + restore, theme mode reset về System default — chấp nhận được vì Spec 006 sẽ persist qua DataStore. Document trong Assumptions.
- **Font load fail runtime**: Compose `FontFamily(Font(R.font.poppins_medium, FontWeight.Medium))` đọc từ `res/font/` sync — nếu file `.ttf` corrupt hoặc thiếu, build fail compile time (R.font.* unresolved). Không có runtime risk như downloadable fonts.
- **API 24-26 vs API 31+ dynamic color**: `dynamicLightColorScheme()` chỉ available API 31+. Code phải `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` guard. Quên guard → compile pass nhưng crash runtime trên API <31.
- **Theme rename `ThirdtySixBrowserTheme` → `ThirtySixTheme`**: 7 placeholder Composable Spec 002 đang reference theme name template (qua import hoặc khi MainActivity wrap). Spec 003 phải update toàn bộ reference + xóa typo cũ.
- **Detekt baseline 9 entries**: 3 FunctionNaming entries reference `MainActivity.Greeting` + `MainActivity.GreetingPreview` (đã removed Spec 002?) + `Theme.ThirdtySixBrowserTheme` (sẽ rename). 6 MagicNumber entries reference `Color.kt` template hex. Spec 003 rewrite Color.kt → 6 entries hết hiệu lực; rename theme → 1 entry hết hiệu lực; Greeting đã gone hoặc sẽ gone → 2 entries hết hiệu lực. Mục tiêu: baseline file = `<CurrentIssues/>` empty.
- **Bundle size impact** (measured 2026-05-01): 4 font file `.ttf` tổng ~1.1MB (Poppins Medium 158KB, SemiBold 157KB, Inter Regular 407KB, Medium 411KB). Original estimate 30-50KB mỗi file sai vì Inter v4.0 ship full Unicode 15 charset (~410KB/file) — không có static Latin-only variant. SC-007 budget cập nhật từ 500KB → 1.5MB (xem SC-007). Vẫn safe under Constitution §V 10MB total APK budget.
- **Cold-start window flash**: Giữa launcher tap và Compose first render (~1.5s) Android OS hiển thị window background từ `themes.xml`. Default template thường = trắng → flash trắng trên dark mode. FR-026 fix qua `values-night/themes.xml` + 2 color resource. Lưu ý: dynamic color KHÔNG áp dụng được cho window background pre-Compose (XML resource resolved trước khi Compose chạy) → window bg luôn dùng Deep Teal seed surface, KHÔNG match wallpaper dynamic color. Acceptable trade-off vì đây chỉ là khoảnh khắc cold start ngắn.
- **Material3 colorScheme với seed Deep Teal**: cần generate cả light + dark scheme đầy đủ ~30 color roles (primary, onPrimary, primaryContainer, onPrimaryContainer, secondary, ...). Plan phase sẽ chọn approach: (a) Material Theme Builder export tay, hoặc (b) dùng `dynamicColorScheme(seed)` API (nếu Material3 1.4.0 có). Verify ở `research.md`.
- **NO custom font rule conflict**: CLAUDE.md hiện viết "NO custom font for v1.0 (Material3 default Roboto)". Spec 003 thay đổi quyết định này — phải update CLAUDE.md + project-context.md sau merge để không còn mâu thuẫn.

## Requirements *(mandatory)*

### Functional Requirements

#### Color tokens

- **FR-001**: System MUST cung cấp `Color.kt` trong `presentation/theme/` chứa **chỉ** color value object (`val md_theme_light_primary = Color(0xFF0F766E)` v.v.) — KHÔNG export Composable từ file này. Toàn bộ 30 M3 color role (primary, onPrimary, primaryContainer, onPrimaryContainer, secondary, onSecondary, secondaryContainer, onSecondaryContainer, tertiary, onTertiary, tertiaryContainer, onTertiaryContainer, error, onError, errorContainer, onErrorContainer, background, onBackground, surface, onSurface, surfaceVariant, onSurfaceVariant, outline, outlineVariant, scrim, inverseSurface, inverseOnSurface, inversePrimary, surfaceTint, surfaceBright/Dim/Container nếu M3 1.4.0 có) MUST có giá trị cho cả light + dark scheme.
- **FR-002**: System MUST seed light + dark color scheme từ Deep Teal `#0F766E` (light primary) / `#5EEAD4` (dark primary), tertiary tilt cyan `#0891B2` (light) / `#67E8F9` (dark). Source giá trị: Material Theme Builder export hoặc tương đương — pin tay vào `Color.kt`.
- **FR-003**: System MUST KHÔNG dùng inline `Color(0xFF...)` ở bất kỳ Composable nào ngoài `Color.kt`. Composable consume color qua `MaterialTheme.colorScheme.<role>`. Allowed exceptions: `Color.Transparent`, `Color.Unspecified` (Constitution §III).

#### Typography

- **FR-004**: System MUST bundle 4 font file `.ttf` trong `app/src/main/res/font/`:
  - `poppins_medium.ttf` (weight 500)
  - `poppins_semibold.ttf` (weight 600)
  - `inter_regular.ttf` (weight 400)
  - `inter_medium.ttf` (weight 500)
  Files bundle local, KHÔNG dùng downloadable fonts (vi phạm "no runtime fetch" rule).
- **FR-005**: System MUST khai báo `FontFamily` cho Poppins + Inter trong `Type.kt`:
  ```kotlin
  val Poppins = FontFamily(
      Font(R.font.poppins_medium, FontWeight.Medium),
      Font(R.font.poppins_semibold, FontWeight.SemiBold),
  )
  val Inter = FontFamily(
      Font(R.font.inter_regular, FontWeight.Normal),
      Font(R.font.inter_medium, FontWeight.Medium),
  )
  ```
- **FR-006**: System MUST khai báo `Typography` (M3) trong `Type.kt` map các role sau (16 role M3 tổng):
  - **Poppins SemiBold (600)**: `displayLarge`, `displayMedium`, `displaySmall`, `headlineLarge`, `headlineMedium`, `headlineSmall`
  - **Poppins Medium (500)**: `titleLarge`
  - **Inter Medium (500)**: `titleMedium`, `titleSmall`, `labelLarge`, `labelMedium`, `labelSmall`
  - **Inter Regular (400)**: `bodyLarge`, `bodyMedium`, `bodySmall`
  Font size + line height giữ M3 default (KHÔNG override) — chỉ override `fontFamily` + `fontWeight`.
- **FR-007**: System MUST KHÔNG hardcode `fontSize`/`fontWeight`/`TextStyle(...)` trong Composable ngoài `Type.kt`. Composable consume qua `MaterialTheme.typography.<role>`. Override per-element MUST dùng `.copy()`. (Constitution §III)

#### Shape + Spacing tokens

- **FR-008**: System MUST cung cấp `Shape.kt` trong `presentation/theme/` với `Shapes` object dùng M3 default (`extraSmall = RoundedCornerShape(4.dp)`, `small = 8.dp`, `medium = 12.dp`, `large = 16.dp`, `extraLarge = 28.dp`). KHÔNG override.
- **FR-009**: System MUST cung cấp `Spacing.kt` trong `presentation/theme/` với object `Spacing`:
  ```kotlin
  object Spacing {
      val xs: Dp = 4.dp
      val sm: Dp = 8.dp
      val md: Dp = 16.dp
      val lg: Dp = 24.dp
      val xl: Dp = 32.dp
  }
  ```
  5 token đủ cho v1.0; nếu spec sau cần `xxl=48.dp` sẽ thêm khi cần. Composable consume qua `Spacing.md` v.v.
- **FR-010**: System MUST KHÔNG hardcode `.dp` cho padding/margin/spacer trong Composable ngoài `Spacing.kt`. Allowed: `0.dp` cho zero-padding override; sizes specific cho icon/avatar (vd `Modifier.size(24.dp)` cho icon — KHÔNG dùng spacing tokens cho size).

#### Theme Composable

- **FR-011**: System MUST cung cấp `ThirtySixTheme` Composable trong `presentation/theme/Theme.kt` với chữ ký:
  ```kotlin
  @Composable
  fun ThirtySixTheme(
      darkTheme: Boolean = isSystemInDarkTheme(),
      dynamicColor: Boolean = true,
      content: @Composable () -> Unit,
  )
  ```
  Logic:
  1. Nếu `dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` → dùng `dynamicDarkColorScheme(context)` hoặc `dynamicLightColorScheme(context)` theo `darkTheme`.
  2. Else → dùng `DarkColorScheme` (Deep Teal dark) hoặc `LightColorScheme` (Deep Teal light) seed từ `Color.kt`.
  3. Wrap với `MaterialTheme(colorScheme, typography = Typography, shapes = Shapes, content)`.
- **FR-012**: System MUST rename `ThirdtySixBrowserTheme` (typo) → `ThirtySixTheme` — update tất cả reference trong `MainActivity.kt`, 7 placeholder Composable, và bất kỳ preview annotation nào. File `Theme.kt` được rewrite hoàn toàn.

#### Window background (cold-start)

- **FR-026**: System MUST set theme-aware `windowBackground` để tránh white flash giữa launcher tap và `BrowserScreen` first compose:
  - `app/src/main/res/values/themes.xml` set `<item name="android:windowBackground">@color/window_background_light</item>` trên `Theme.ThirtySix` parent.
  - `app/src/main/res/values-night/themes.xml` (mới) override `<item name="android:windowBackground">@color/window_background_dark</item>`.
  - 2 color resource `window_background_light` (= surface light, ~`#FAFAF9`) và `window_background_dark` (= surface dark, ~`#0A0F0E`) khai báo trong `app/src/main/res/values/colors.xml` — sync với value từ `Color.kt` (acceptable redundancy: `colors.xml` cho window pre-Compose, `Color.kt` cho Compose runtime).
- **FR-027**: System MUST KHÔNG dùng `androidx.core:core-splashscreen` SplashScreen API ở Spec 003 — giữ scope cho Spec 017. Window background fix là cách tối thiểu (XML theme attribute) tránh flash, không tạo splash dedicated.

#### In-memory theme mode (Spec 003 scope)

- **FR-013**: System MUST cung cấp enum `ThemeMode` trong `presentation/theme/`:
  ```kotlin
  enum class ThemeMode { Light, Dark, System }
  ```
- **FR-014**: `MainActivity.kt` MUST hold `themeMode: MutableState<ThemeMode>` với default `ThemeMode.System`, pass đến `ThirtySixTheme` qua mapping:
  - `Light` → `darkTheme = false`
  - `Dark` → `darkTheme = true`
  - `System` → `darkTheme = isSystemInDarkTheme()`
  KHÔNG persist qua DataStore — Spec 006 sẽ wire DataStore. KHÔNG expose UI toggle — Spec 016 sẽ làm.
- **FR-015**: System MUST KHÔNG tạo `ThemeViewModel`, `ThemeRepository`, `SettingsDataStore` ở Spec 003. Theme mode chỉ dùng `MutableState` raw cho dev/test purpose (incremental scope per project memory).

#### Detekt baseline cleanup

- **FR-016**: System MUST clear `app/detekt-baseline.xml` về trạng thái empty:
  ```xml
  <?xml version="1.0" ?>
  <SmellBaseline>
    <ManuallySuppressedIssues/>
    <CurrentIssues/>
  </SmellBaseline>
  ```
  Vì Spec 003 rewrite `Color.kt` (xóa 6 hex template) + rename `ThirdtySixBrowserTheme` (xóa 1 entry FunctionNaming) + verify `Greeting`/`GreetingPreview` đã removed (xóa 2 entry FunctionNaming còn lại). Chạy `./gradlew detekt` post-rewrite phải pass clean với baseline empty.
- **FR-017**: System MUST KHÔNG add violation mới vào baseline. Nếu phát sinh violation từ font/spacing code → fix root cause hoặc dùng `@Suppress` targeted với rationale comment, KHÔNG cover bằng baseline.

#### Module structure

- **FR-018**: System MUST tạo / rewrite các file:
  - `presentation/theme/Color.kt` (rewrite — Deep Teal palette)
  - `presentation/theme/Type.kt` (rewrite — Poppins + Inter Typography)
  - `presentation/theme/Theme.kt` (rewrite — `ThirtySixTheme`)
  - `presentation/theme/Shape.kt` (new — M3 default Shapes)
  - `presentation/theme/Spacing.kt` (new — 5 token Dp)
  - `presentation/theme/ThemeMode.kt` (new — enum)
  - `app/src/main/res/font/poppins_medium.ttf` (new asset)
  - `app/src/main/res/font/poppins_semibold.ttf` (new asset)
  - `app/src/main/res/font/inter_regular.ttf` (new asset)
  - `app/src/main/res/font/inter_medium.ttf` (new asset)
  - `app/detekt-baseline.xml` (empty `<CurrentIssues/>`)
  - `app/src/main/res/values/themes.xml` (modify — set windowBackground)
  - `app/src/main/res/values-night/themes.xml` (new — override windowBackground dark)
  - `app/src/main/res/values/colors.xml` (modify hoặc new — `window_background_light` + `window_background_dark` color resource)
- **FR-019**: System MUST update reference (rename) trong:
  - `MainActivity.kt` (theme wrap + themeMode state)
  - 7 placeholder Composable nếu có import `ThirdtySixBrowserTheme` (verify Spec 002 code)
- **FR-020**: System MUST KHÔNG tạo:
  - `data/datastore/` (Spec 006 lo)
  - `domain/` layer mới
  - `core/constants/ThemeConstants.kt` (không cần — theme tokens KHÔNG phải constants per project memory: theme tokens là Compose first-class artifacts)
  - `presentation/settings/ThemeToggleSection.kt` (Spec 016 lo)
  - 8 locale entries cho theme strings (Spec 004 lo nếu cần)
  - `ThemeViewModel`, `ThemeRepository`, `ToggleThemeUseCase`

#### Quality gates

- **FR-021**: Build MUST pass cho cả 6 Gradle task: `assembleDebug`, `assembleRelease`, `testDebugUnitTest`, `lintDebug`, `detekt`, `ktlintCheck`. Detekt MUST pass với baseline empty.
- **FR-022**: 16KB alignment script MUST pass post-`assembleRelease` — Spec 003 KHÔNG thêm package có `.so` (font `.ttf` không phải native lib). Chỉ `libandroidx.graphics.path.so` từ Compose vẫn align 0x4000.
- **FR-023**: System MUST có ít nhất 4 unit test mới trong `app/src/test/`:
  - `Spacing.xs/sm/md/lg/xl` đúng giá trị `Dp`.
  - `Typography` mapping: `headlineMedium.fontFamily == Poppins`, `bodyLarge.fontFamily == Inter` (test object identity hoặc lookup font resource ID).
  - `ThemeMode` enum 3 value đúng tên.
  - Compose UI test (optional, nếu thêm dễ): render `ThirtySixTheme(darkTheme = true) { Text("x") }` không crash.

#### Package versions

- **FR-024**: Spec 003 KHÔNG thêm package mới vào `libs.versions.toml`. `androidx.compose.ui:ui-text` (đã có trong Compose BOM) đã hỗ trợ `FontFamily(Font(R.font.*))` — không cần dep extra. `androidx.compose.material3:material3` (đã có) cung cấp `MaterialTheme`/`Typography`/`Shapes`.
- **FR-025**: Font binary `.ttf` MUST được verify license cho phép bundle commercial (Poppins + Inter đều SIL Open Font License → OK). Source file: Google Fonts hoặc GitHub release official.

### Key Entities

(Spec này không thêm data entity. `ThemeMode` enum là presentation state, không phải domain model.)

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: App debug cài lên emulator API 36 với system Light theme → cold start trong **≤ 1.5 giây** đến khi `BrowserScreen` placeholder render với background light từ Deep Teal seed (giữ perf target Spec 002).
- **SC-002**: Toggle system theme Light ↔ Dark trên running app **re-compose ≤ 100ms** (Compose recomposition tự động khi `isSystemInDarkTheme()` đổi).
- **SC-003**: Trên Android 12+ với wallpaper màu cam, `colorScheme.primary` **KHÔNG** = `#0F766E` (Deep Teal seed) — verify qua dynamic color hoạt động. Trên Android 7-11 hoặc `dynamicColor = false`, `colorScheme.primary` = `#0F766E` ± Material Theme Builder transform.
- **SC-004**: CI pipeline run trên PR spec 003 đạt **5/6 job xanh + 1 job skipped** (instrumented-test) — 0 job fail.
- **SC-005**: Sau `assembleRelease`, **100% `.so` trong APK** align ≥ 0x4000 (script verify-16kb-alignment pass). Không có `.so` mới.
- **SC-006**: Detekt baseline file post-merge có **0 entries** trong `<CurrentIssues>`. `./gradlew detekt` chạy clean không cần baseline cover.
- **SC-007**: Bundle size APK release Spec 003 tăng **≤ 1.5MB** so với Spec 002 baseline. Actual measurement (2026-05-01): Poppins Medium 158KB + Poppins SemiBold 157KB + Inter Regular 407KB + Inter Medium 411KB = 1133KB tổng cho 4 font binary `.ttf`. Inter v4.0 stable static TTFs ~410KB mỗi file vì include full Unicode 15 charset (Latin Extended + Vietnamese diacritics + IPA + symbol). Original budget 500KB ước tính sai; cập nhật post-T002 download + measurement. Vẫn well within Constitution §V total APK ≤10MB ràng buộc (4 font + Compose runtime + Hilt + Spec 003 code ≈ 6-8MB total, safe margin). Spec 004+ subset nếu cần (defer optimization).
- **SC-008**: 0 occurrence của `Color(0xFF...)` (regex search) trong file Kotlin ngoài `presentation/theme/Color.kt`. 0 occurrence của `fontSize = ` hoặc `TextStyle(` trong Composable ngoài `Type.kt`. 0 occurrence của `.padding(N.dp)` với N hardcoded ngoài `Spacing.kt` cho padding (size cho icon/avatar OK).
- **SC-009**: Một developer mới đọc code spec 003 có thể trong **≤ 15 phút** identify đúng nơi để: (a) đổi brand color seed, (b) thêm spacing token mới, (c) thay font heading.
- **SC-010**: 100% critical color pairs đạt **WCAG AA contrast** trên cả Light + Dark scheme:
  - Normal text (≥ 4.5:1): `primary/onPrimary`, `secondary/onSecondary`, `tertiary/onTertiary`, `surface/onSurface`, `background/onBackground`, `error/onError`, `surfaceVariant/onSurfaceVariant`
  - Large text + icons (≥ 3:1): `primaryContainer/onPrimaryContainer`, `secondaryContainer/onSecondaryContainer`, `tertiaryContainer/onTertiaryContainer`, `errorContainer/onErrorContainer`, `outline/background`
  - Document đầy đủ contrast ratio table (light + dark, ~24 pair tổng) trong `research.md` — tính bằng formula WCAG 2.1 hoặc tool [`webaim.org/resources/contrastchecker`](https://webaim.org/resources/contrastchecker/) / [Material Theme Builder built-in checker](https://m3.material.io/theme-builder).
  - Pair NÀO không đạt → adjust luminance trong Color.kt + retest cho đến khi pass; KHÔNG ship pair fail.

## Assumptions

- **Spacing values** = 4/8/16/24/32 dp (xs/sm/md/lg/xl) — Material 4dp base scale standard. Spec sau có thể thêm `xxl=48.dp` cho onboarding hero nếu cần — chưa thêm để giữ scope nhỏ.
- **Font weights** chốt 4: Poppins Medium 500 + SemiBold 600 + Inter Regular 400 + Medium 500. Bold/ExtraBold/Light KHÔNG bundle ở v1.0 — thêm sau nếu spec UI thực sự cần emphasis ngoài SemiBold.
- **Theme persistence** in-memory `MutableState` ở Spec 003. DataStore wire ở Spec 006; UI toggle ở Spec 016. Process death → reset System default — chấp nhận được vì chưa có user-facing toggle ở Spec 003.
- **Detekt baseline cleanup** scope IN Spec 003 (xóa 9 entries do rewrite Theme.kt + Color.kt + verify Greeting đã removed). Nếu Spec 002 chưa xóa Greeting/GreetingPreview thật sự, Spec 003 phải xóa thêm trong `MainActivity.kt`.
- **Brand color**: Deep Teal seed `#0F766E` (light primary) / `#5EEAD4` (dark primary) — quyết định bởi user 2026-05-01 (option A trong gợi ý 5 brand color). Color values cụ thể cho 30 M3 role sẽ được generated qua Material Theme Builder ở Plan phase và pin vào `Color.kt` — KHÔNG dùng `dynamicColorScheme(seed)` runtime API (đảm bảo determinism + offline).
- **Tertiary accent** = Cyan `#0891B2` light / `#67E8F9` dark — pair tự nhiên với Teal trong cùng gam xanh-blue, dùng cho links/active state/secondary CTA.
- **Material Theme Builder** là tool tin cậy để generate full 30-role palette từ seed. Plan phase sẽ document URL + screenshot config (light + dark export) trong `research.md`.
- **Compose BOM 2026.04.01** đã include Material3 1.4.0+ với `dynamicLightColorScheme`/`dynamicDarkColorScheme` API. Verify ở Plan.
- **Font licenses**: Poppins (SIL OFL 1.1) + Inter (SIL OFL 1.1) — đều cho phép bundle commercial. Source binary: Google Fonts download (`.ttf` static, KHÔNG variable font để giữ size nhỏ + max compat API 24+).
- **Dynamic color trên dark theme**: `dynamicDarkColorScheme(context)` API 31+ — verify exists trong Material3 1.4.0; nếu thiếu, fallback `dynamicLightColorScheme + .copy()` hoặc Spec 003 chỉ support dynamic light, dark fallback static seed.
- **Theme rewrite KHÔNG đụng package name**: file `Theme.kt` vẫn ở `presentation/theme/` — chỉ rename Composable function bên trong.
- **Re-enable instrumented-test CI job**: vẫn `if: false` cho Spec 003 — chưa cần emulator. Trigger giữ nguyên ở Spec 007/011.

## Dependencies

- **Constitution v1.2.0** — comply §III (No-Hardcode: 18-row category table áp dụng cho color/dimens/strings), §IX (16KB), §XI (Signing two-scope).
- **Spec 001** — `project-init-build-config` đã merge: version catalog, Detekt + ktlint + Lint setup, CI 6-job pipeline, 16KB script active.
- **Spec 002** — `clean-architecture-skeleton-di` đã merge: 7 placeholder Composable, `AppDestination`, `MainActivity`, theme template (sẽ rewrite). Spec 003 sửa references trong các file này.
- KHÔNG có dependency package mới. Tất cả đã có từ Compose BOM (Spec 001) + Material3 1.4.0 (Spec 001).

## Out of Scope (Spec sau sẽ làm)

- **Theme mode persistence qua DataStore** — Spec 006 (`datastore-settings`).
- **UI Settings toggle Light/Dark/System** — Spec 016 (`settings-screen`).
- **Localization 8 locales** — Spec 004 (`localization-multi-language`). Spec 003 không thêm string resource mới (theme không có user-facing copy).
- **Custom font khác Poppins/Inter** — không có kế hoạch v1.0.
- **Variable font** (`.ttf` variable axis) — dùng static `.ttf` per weight để giữ tương thích API 24+.
- **Theme animation** (smooth transition khi toggle Light/Dark) — Compose default crossfade đủ; nếu cần tween animation custom, defer Spec 016.
- **Per-screen theme override** — không có nhu cầu v1.0.
- **High-contrast accessibility theme** — defer post-v1.0 (Material3 đã accessible color contrast mặc định cho seed Deep Teal).
- **Re-enable instrumented-test CI job** — Spec 007 hoặc 011.
- **Update CLAUDE.md / project-context.md** xóa rule "NO custom font" — sẽ làm trong commit cuối Spec 003 (post-merge context update step per dev-workflow.md Bước 8).
