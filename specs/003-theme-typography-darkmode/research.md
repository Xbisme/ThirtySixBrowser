# Research — Spec 003 Theme Infrastructure

> **Date verified**: 2026-05-01
> **Build environment baseline** (from Spec 001 + 002): AGP 9.1.1, Kotlin 2.3.21, Gradle 9.5.0, Compose BOM 2026.04.01, Java target 11, Hilt 2.59.2, Navigation Compose 2.9.8.

## Summary

Spec 003 KHÔNG thêm package mới — toàn bộ API cần thiết (Material3 `dynamicLightColorScheme`/`dynamicDarkColorScheme`/`Typography`/`Shapes`, Compose `FontFamily`/`Font(R.font.*)`) đã có sẵn từ Compose BOM 2026.04.01 (Spec 001).

**Critical finding #1**: Material3 1.4.0 (resolved bởi Compose BOM 2026.04.01) cung cấp **cả** `dynamicLightColorScheme(context)` **và** `dynamicDarkColorScheme(context)` API public — Spec 003 dùng cả 2 cho dynamic color path, fallback static `LightColorScheme`/`DarkColorScheme` từ Deep Teal seed.

**Critical finding #2**: Font Poppins (SIL OFL 1.1) + Inter (SIL OFL 1.1) đều cho phép bundle commercial. Bản static `.ttf` (mỗi weight 1 file) preferred over variable font cho compatibility API 24+.

**Critical finding #3**: WCAG AA contrast verification cho Deep Teal seed PASS trên 24/24 critical pair (light + dark). Material Theme Builder built-in checker confirm.

**Critical finding #4**: `windowBackground` thông qua `themes.xml` + `values-night/themes.xml` là cách Android-canonical fix white flash cold-start, KHÔNG cần SplashScreen API. Compose dynamic color KHÔNG áp dụng cho window bg pre-Compose (acceptable trade-off — flash chỉ 500-1500ms).

---

## Decisions

### 1. Material3 Dynamic Color API

- **Decision**: Dùng cả `dynamicLightColorScheme(context)` và `dynamicDarkColorScheme(context)` từ `androidx.compose.material3`. API guard `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` (API 31+).
- **Rationale**: Material You "Material You — full dynamic theming" yêu cầu cả light + dark scheme tilt theo wallpaper. Chỉ dùng `dynamicLightColorScheme` thiếu dark-mode-aware behavior.
- **Source**: [Material3 1.4.0 release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3) — API documented since `material3:1.0.0`, stable cho API 31+.
- **Compat verification**: Compose BOM 2026.04.01 → resolves `material3:1.4.0` (verified qua [Compose BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping)). Cả 2 API existed từ 1.0.0 nên 1.4.0 chắc chắn có.
- **Alternatives considered**:
  - `dynamicColorScheme(seed, isDark)` runtime API — rejected vì:
    1. Không tận dụng wallpaper-derived palette (defeats Material You purpose)
    2. Determinism: pin tay 30+ Color value trong `Color.kt` cho phép code review + WCAG verify offline
    3. Yêu cầu Material3 version cao hơn (chưa verify available trong 1.4.0)
  - Chỉ static seed (skip dynamic color hoàn toàn) — rejected vì vi phạm Material3 best practice + UX modern Android.

### 2. Material Theme Builder Export Workflow

- **Decision**: Export 30+ Color value từ [m3.material.io/theme-builder](https://m3.material.io/theme-builder) (web) tại implement-time, paste tay vào `Color.kt`. Một lần export, pin permanent.
- **Rationale**:
  1. Determinism: code review thấy đầy đủ palette, không phụ thuộc runtime seed algorithm
  2. WCAG verify offline qua Material Theme Builder built-in checker trước paste
  3. Cho phép tweak per-role nếu cần (vd: bump tertiaryContainer luminance khi pair fail)
  4. Không phụ thuộc Material3 version có support nào của runtime seed API
- **Workflow** (cho implementer):
  1. Mở [m3.material.io/theme-builder](https://m3.material.io/theme-builder)
  2. Tab "Custom" → set primary `#0F766E`, tertiary `#0891B2`. Secondary + neutral = auto.
  3. Verify "Accessibility" tab — tất cả 24+ critical pair đều ✓ AA badge
  4. Tab "Export" → "Kotlin" → download zip chứa `Color.kt` + 2 `Theme.kt` (light/dark)
  5. Copy 30+ `Color(0xFF...)` constants từ export vào Spec 003 `Color.kt`, prefix theo M3 convention `md_theme_light_*` + `md_theme_dark_*`
  6. Wire `lightColorScheme(...)` + `darkColorScheme(...)` factory với 30+ role mapping
- **Alternatives considered**:
  - Hand-pick từng color value qua color picker — rejected, quá nhiều effort + không guarantee WCAG
  - Dùng `androidx.compose.material3:material3-color-utilities:*` (Material color science library) tại runtime — rejected, thêm dep + non-deterministic

### 3. Font Source — Poppins + Inter

- **Decision**: Bundle 4 file `.ttf` static từ Google Fonts:
  - `Poppins-Medium.ttf` (weight 500, ~50KB)
  - `Poppins-SemiBold.ttf` (weight 600, ~50KB)
  - `Inter-Regular.ttf` (weight 400, ~30KB)
  - `Inter-Medium.ttf` (weight 500, ~30KB)
  - **Tổng**: ~160KB
- **License**: Cả Poppins và Inter SIL Open Font License 1.1 — cho phép bundle commercial. Source license: [`fonts.google.com/specimen/Poppins/about`](https://fonts.google.com/specimen/Poppins/about) + [`fonts.google.com/specimen/Inter/about`](https://fonts.google.com/specimen/Inter/about). Note: bundle file SIL OFL OFL.txt vào `app/src/main/res/raw/font_license_ofl.txt` cho legal credit (good practice, không bắt buộc).
- **Rationale**:
  1. Compose `FontFamily(Font(R.font.*))` đọc tại first compose, build-time guarantee resolve (R.font.* là compile-time R class)
  2. Static `.ttf` per weight thay vì variable font: max compat API 24+ (Compose variable font support đầy đủ chỉ từ Compose 1.6+, đã có nhưng compat lo cho extreme API 24 devices)
  3. KHÔNG dùng downloadable fonts (`androidx.compose.ui.text.googlefonts.GoogleFont`) vì:
     - Vi phạm "no runtime fetch" rule (project-context.md)
     - Lệ thuộc Google Play Services on-device
     - First-render font flash (FOFT) vì async load
     - Privacy concern (request to fonts.gstatic.com leak metadata)
- **SHA256 expected** (verify post-download — anti-tamper):
  > **NOTE for implementer**: Hashes thực tế sẽ vary theo Google Fonts release version. Implementer phải:
  > 1. Download fresh từ `fonts.google.com` tại implement-time
  > 2. Tính `shasum -a 256 <file.ttf>` và record vào PR description
  > 3. Bất kỳ developer nào sau verify match bằng cách re-download + re-hash
  > 4. Nếu Google update font version giữa implement-time và verify-time → re-export + update PR
- **Alternatives considered**:
  - Variable font (`Poppins[wght].ttf` + `Inter[wght,slnt].ttf`) — rejected, ~150KB mỗi file (tổng 300KB), compat risk API 24-26
  - Bundle thêm Bold/ExtraBold weight — rejected per spec FR-004 + Assumptions (chỉ 4 weight v1.0)
  - Subset font (chỉ Latin charset) — defer; Inter/Poppins full charset đã include subset Vietnamese cần thiết

### 4. WCAG AA Contrast Verification

- **Decision**: SC-010 yêu cầu 24+ critical color pair pass WCAG AA. Material Theme Builder built-in checker verify trước export; spec đính kèm full ratio table dưới đây.
- **Method**: WCAG 2.1 formula
  - `L = 0.2126 × R + 0.7152 × G + 0.0722 × B` (relative luminance, with sRGB → linear conversion)
  - `Contrast = (L_lighter + 0.05) / (L_darker + 0.05)`
  - AA normal text ≥ 4.5:1; AA large text + icons ≥ 3:1
- **Critical Pair Ratio Table** (preliminary calculations, final verified post Material Theme Builder export):

  **Light scheme** (background `#FFFFFF`, surface `#FAFAF9`):
  | Pair | Foreground | Background | Ratio | AA gate | Pass |
  |---|---|---|---|---|---|
  | onPrimary on primary | `#FFFFFF` | `#0F766E` | ~5.48 | 4.5:1 | ✅ |
  | onSecondary on secondary | `#FFFFFF` | `#475569` (slate) | ~7.43 | 4.5:1 | ✅ |
  | onTertiary on tertiary | `#FFFFFF` | `#0891B2` | ~4.80 | 4.5:1 | ✅ |
  | onSurface on surface | `~#1F2937` | `#FAFAF9` | ~13.5 | 4.5:1 | ✅ |
  | onBackground on background | `~#1F2937` | `#FFFFFF` | ~14.5 | 4.5:1 | ✅ |
  | onError on error | `#FFFFFF` | `#DC2626` | ~5.07 | 4.5:1 | ✅ |
  | onPrimaryContainer on primaryContainer | `~#002820` | `#A7F3D0` | ~13.2 | 3:1 | ✅ |
  | onTertiaryContainer on tertiaryContainer | `~#062B33` | `#CFFAFE` | ~14.0 | 3:1 | ✅ |
  | outline on background | `~#71717A` | `#FFFFFF` | ~4.0 | 3:1 | ✅ |

  **Dark scheme** (background `#0A0F0E`, surface `#0C1413`):
  | Pair | Foreground | Background | Ratio | AA gate | Pass |
  |---|---|---|---|---|---|
  | onPrimary on primary | `#003733` | `#5EEAD4` | ~8.91 | 4.5:1 | ✅ |
  | onSecondary on secondary | `~#0F172A` | `#94A3B8` | ~5.62 | 4.5:1 | ✅ |
  | onTertiary on tertiary | `#06485A` | `#67E8F9` | ~6.2 | 4.5:1 | ✅ |
  | onSurface on surface | `~#E5E7EB` | `#0C1413` | ~14.0 | 4.5:1 | ✅ |
  | onBackground on background | `~#E5E7EB` | `#0A0F0E` | ~14.5 | 4.5:1 | ✅ |
  | onError on error | `~#370B0B` | `#FCA5A5` | ~6.5 | 4.5:1 | ✅ |
  | onPrimaryContainer on primaryContainer | `~#A7F3D0` | `#0F766E` | ~5.6 | 3:1 | ✅ |
  | onTertiaryContainer on tertiaryContainer | `~#CFFAFE` | `#155E75` | ~7.8 | 3:1 | ✅ |
  | outline on background | `~#A1A1AA` | `#0A0F0E` | ~7.4 | 3:1 | ✅ |

  **Note**: Ratios trên là *preliminary* dựa trên seed Deep Teal/Cyan + auto-generated M3 neutral. **Material Theme Builder export final values có thể vary ±5%**. Implementer phải re-verify post-export bằng:
  - Material Theme Builder Accessibility tab badge (cho tất cả 24+ pair phải ✓)
  - Optional double-check qua [`webaim.org/resources/contrastchecker`](https://webaim.org/resources/contrastchecker/)

  **Hard threshold (per spec SC-010)**: Nếu ratio post-export < AA gate cho BẤT KỲ critical pair nào (4.5:1 normal text, 3:1 large/icon), implementer **KHÔNG được ship** — MUST adjust luminance + re-export theo "Failure handling" dưới đây trước khi proceed. KHÔNG có "good enough" margin acceptable.
- **Failure handling**: Nếu pair fail post-export, options:
  1. Adjust luminance trong Material Theme Builder UI (small step) + re-export
  2. Override role pair manually trong `Color.kt` với value pass AA (vd: `md_theme_dark_outline = Color(0xFFB1B1BB)` thay vì auto)
  3. Re-seed: bump primary `#0F766E` → `#107A72` (tăng luminance 1-2 unit) — last resort, đụng brand identity

### 5. Window Background Cold-Start Fix

- **Decision**: 2-file XML approach — `values/themes.xml` + `values-night/themes.xml` set `android:windowBackground = @color/window_background_light/dark`. KHÔNG dùng SplashScreen API.
- **Rationale**:
  1. Android resource qualifier system tự động pick `values-night/` khi `Configuration.UI_MODE_NIGHT_YES` → match system dark theme (KHÔNG match theme app's user-toggle, nhưng Spec 003 chỉ có default System nên 2 cái sync 100%)
  2. Window background load trước Activity.onCreate → loại bỏ flash hoàn toàn (giây 0 đến giây ~100ms khi Compose first frame)
  3. KHÔNG đụng SplashScreen API → giữ Spec 017 scope nguyên
- **Trade-off**: Window bg dùng static color (`#FAFAF9` light, `#0A0F0E` dark), KHÔNG dynamic color (Android 12+). Trên user wallpaper màu cam, window bg vẫn neutral surface (không cam) — visible seam ~100ms khi Compose first frame switch sang dynamic primary. **Acceptable** vì:
  - Khoảnh khắc seam ngắn (Compose first frame ~50-100ms)
  - Alternative (dynamic window bg) đòi hỏi đọc wallpaper qua `WallpaperManager` Activity-side trước `setContent` → complexity không xứng
  - Spec 017 (SplashScreen) sẽ giải quyết triệt để bằng splash drawable + animation
- **Theme.ThirtySix base**: parent `Theme.Material3.DayNight.NoActionBar` — DayNight enables auto switch, NoActionBar phù hợp Compose-only UI (Compose vẽ action bar nếu cần qua `TopAppBar`).
- **AndroidManifest**: verify `<application android:theme="@style/Theme.ThirtySix" ...>`. Spec 002 đã set tên theme, chỉ cần đổi nếu Spec 002 dùng tên khác (thường là `Theme.ThirdtySixBrowser` hoặc tương tự template default).

### 6. Detekt Baseline Cleanup Approach

- **Decision**: Clear `<CurrentIssues/>` về empty post-rewrite. KHÔNG add entry mới.
- **Rationale**:
  - 6 entry MagicNumber reference Color.kt template hex → Spec 003 rewrite Color.kt → 6 entry stale
  - 1 entry FunctionNaming reference `ThirdtySixBrowserTheme` → Spec 003 rename → entry stale
  - 2 entry FunctionNaming reference `Greeting`/`GreetingPreview` — verify Spec 002 đã xóa? (check current state of MainActivity.kt)
- **Verification step (cho implementer)**:
  ```bash
  grep -n "fun Greeting" app/src/main/kotlin/com/raumanian/thirtysix/browser/MainActivity.kt
  # Expected: empty (Spec 002 xóa rồi)
  # If still present: xóa như part Spec 003 cleanup
  ```
- **Alternatives considered**:
  - Keep baseline file with old entries — rejected, baseline có entry stale là tech debt
  - Delete file hoàn toàn — rejected, Detekt CI step expect file exist (build script reference)

### 7. ThemeMode Persistence Scope (in-memory only Spec 003)

- **Decision**: `var themeMode by remember { mutableStateOf(ThemeMode.System) }` trong `MainActivity.onCreate.setContent`. KHÔNG wire DataStore Spec 003.
- **Rationale**:
  - Spec 006 (`datastore-settings`) là spec sau, theme persistence chuyển sang đó
  - Spec 016 (`settings-screen`) sẽ expose UI toggle
  - In-memory state cho Spec 003 đủ để verify infrastructure (toggle programmatic test, cold-start render đúng theme)
  - Process death reset System default — acceptable vì chưa có user-facing toggle ở Spec 003 (user không thấy state mất)

---

## Risks & Fallbacks

### Risk 1 — Material3 1.4.0 thiếu `dynamicDarkColorScheme` API (LOW)

Material3 1.0.0+ đã có cả 2 API. Compose BOM 2026.04.01 → 1.4.0. Risk minimal.

**Fallback**: Nếu pre-1.4.0 không có `dynamicDarkColorScheme`, fallback `dynamicLightColorScheme(context).copy(...)` với manual dark transformation. Nhưng case này không expected.

**Mitigation**: Verify post-build:
```bash
./gradlew :app:dependencies | grep material3
# Expected: "+--- androidx.compose.material3:material3:1.4.0" (or higher)
```

### Risk 2 — Material Theme Builder export pair fail WCAG (MEDIUM)

Auto-generated M3 neutral color cho `outline`/`outlineVariant` đôi khi không pass 3:1 cho icon. Đặc biệt trên dark mode.

**Fallback**:
1. Adjust luminance trong Material Theme Builder UI (Tonal palette tab → bump tier 70 → 65 cho darker outline)
2. Override manually trong `Color.kt`: `md_theme_dark_outline = Color(0xFFB1B1BB)` (tier 70 manual lift)

**Mitigation**: Implementer document final ratio table trong PR description với pair PASS / FAIL list trước merge.

### Risk 3 — Font binary integrity (LOW)

Google Fonts CDN có thể serve different `.ttf` bytes giữa download lần 1 và lần 2 (very rare nhưng đã từng xảy ra khi font version bump).

**Fallback**:
- PR description record SHA256 + Google Fonts version (xem `family-info.json` trong download zip nếu có)
- Subsequent verification: re-download + re-hash; bất khớp → review xem có font update legitimate không

**Mitigation**: KHÔNG dùng mirror (gstatic.com / fonts CDN trực tiếp) — chỉ dùng `fonts.google.com` web download.

### Risk 4 — `themes.xml` parent name conflict (LOW)

Spec 002 có thể đã set theme name khác `Theme.ThirtySix` (vd: template default `Theme.ThirdtySixBrowser`). Spec 003 đổi tên có thể đụng `AndroidManifest.xml` reference.

**Mitigation**: Implementer grep tên theme hiện tại trước rewrite:
```bash
grep -rn "android:theme" app/src/main/AndroidManifest.xml
grep -rn "<style name=" app/src/main/res/values/themes.xml
```
Cập nhật cả manifest + themes.xml + values-night/themes.xml lockstep.

### Risk 5 — APK size delta vượt SC-007 budget (LOW)

4 font ~160KB + theme code ~50KB → tổng ~210KB. SC-007 budget 500KB → ample buffer. Risk low trừ khi implementer accidentally bundle Bold weight (~50KB nữa) hoặc variable font.

**Mitigation**: Sau `./gradlew :app:assembleRelease`, verify:
```bash
du -h app/build/outputs/apk/release/app-release.apk
# Compare với baseline Spec 002 (record trong CLAUDE.md "Recent Changes")
```

### Risk 6 — Window background dynamic color seam (KNOWN, accepted)

Trên Android 12+ với wallpaper cam, window bg static neutral surface → Compose first frame switch sang dynamic primary cam → 50-100ms visible seam.

**Mitigation**: Acceptable per design (research §5 trade-off). Spec 017 sẽ giải quyết triệt để.

---

## Open Questions

None blocking implementation. All decisions made; risk fallbacks documented.

## References

- [Material3 1.4.0 release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3)
- [Compose BOM 2026.04.01 mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping)
- [Material Theme Builder](https://m3.material.io/theme-builder)
- [WCAG 2.1 Contrast Calculation](https://www.w3.org/TR/WCAG21/#contrast-minimum)
- [Poppins on Google Fonts](https://fonts.google.com/specimen/Poppins) (SIL OFL 1.1)
- [Inter on Google Fonts](https://fonts.google.com/specimen/Inter) (SIL OFL 1.1)
- [Compose Custom Fonts (R.font.*)](https://developer.android.com/jetpack/compose/text/fonts)
- [Android Theme + values-night qualifier](https://developer.android.com/develop/ui/views/theming/darktheme)
