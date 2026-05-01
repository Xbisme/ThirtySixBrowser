# Data Model — Spec 003 Theme Token Catalog

> **Note**: Spec 003 không thêm domain entity (Bookmark/History/Tab — Spec 005). Document này catalog các **theme token** Compose-side và state model in-memory.

## ThemeMode (presentation enum)

```kotlin
enum class ThemeMode { Light, Dark, System }
```

| Value | Behavior | Persistence |
|---|---|---|
| `Light` | Force `colorScheme = LightColorScheme` (or `dynamicLightColorScheme` API 31+) | In-memory only Spec 003. Spec 006 wire DataStore. |
| `Dark` | Force `colorScheme = DarkColorScheme` (or `dynamicDarkColorScheme`) | Same |
| `System` (default) | Follow `isSystemInDarkTheme()` — auto-switch khi user đổi system theme | Same |

**State holder**: `MainActivity.onCreate.setContent { var themeMode by remember { mutableStateOf(ThemeMode.System) } }`. Process death → reset System.

---

## ColorScheme (Material3, 30 role × 2 scheme)

Pin 60+ `Color` constants vào `presentation/theme/Color.kt`, generated qua [Material Theme Builder](https://m3.material.io/theme-builder) với seed `#0F766E` (primary) + `#0891B2` (tertiary).

### Light scheme roles

| Role | Type | Source | WCAG critical pair |
|---|---|---|---|
| `primary` | Color | `#0F766E` (Deep Teal-700) | onPrimary contrast |
| `onPrimary` | Color | `#FFFFFF` | on primary 4.5:1 |
| `primaryContainer` | Color | `~#A7F3D0` (Teal-200 light tint) | onPrimaryContainer 4.5:1 |
| `onPrimaryContainer` | Color | `~#002820` (Teal-900 dark) | — |
| `inversePrimary` | Color | `~#5EEAD4` (= dark.primary) | — |
| `secondary` | Color | `~#475569` (Slate-600 auto) | onSecondary 4.5:1 |
| `onSecondary` | Color | `#FFFFFF` | — |
| `secondaryContainer` | Color | `~#CBD5E1` (Slate-200) | onSecondaryContainer 4.5:1 |
| `onSecondaryContainer` | Color | `~#0F172A` (Slate-900) | — |
| `tertiary` | Color | `#0891B2` (Cyan-600) | onTertiary 4.5:1 |
| `onTertiary` | Color | `#FFFFFF` | — |
| `tertiaryContainer` | Color | `~#CFFAFE` (Cyan-100) | onTertiaryContainer 4.5:1 |
| `onTertiaryContainer` | Color | `~#062B33` (Cyan-950) | — |
| `error` | Color | `#DC2626` (Red-600) | onError 4.5:1 |
| `onError` | Color | `#FFFFFF` | — |
| `errorContainer` | Color | `~#FEE2E2` (Red-100) | onErrorContainer 4.5:1 |
| `onErrorContainer` | Color | `~#7F1D1D` (Red-900) | — |
| `background` | Color | `#FFFFFF` | onBackground 4.5:1 |
| `onBackground` | Color | `~#1F2937` (Gray-800) | — |
| `surface` | Color | `#FAFAF9` (Stone-50) | onSurface 4.5:1 |
| `onSurface` | Color | `~#1F2937` | — |
| `surfaceVariant` | Color | `~#E5E7EB` (Gray-200) | onSurfaceVariant 4.5:1 |
| `onSurfaceVariant` | Color | `~#374151` (Gray-700) | — |
| `outline` | Color | `~#71717A` (Zinc-500) | outline on bg 3:1 |
| `outlineVariant` | Color | `~#D4D4D8` (Zinc-300) | — |
| `scrim` | Color | `#000000` | — |
| `inverseSurface` | Color | `~#1F2937` | inverseOnSurface contrast |
| `inverseOnSurface` | Color | `~#F9FAFB` | — |
| `surfaceTint` | Color | = `primary` | — |

### Dark scheme roles

| Role | Source |
|---|---|
| `primary` | `#5EEAD4` (Teal-300) |
| `onPrimary` | `~#003733` |
| `primaryContainer` | `#0F766E` |
| `onPrimaryContainer` | `~#A7F3D0` |
| `inversePrimary` | `#0F766E` |
| `secondary` | `~#94A3B8` (Slate-400) |
| `onSecondary` | `~#0F172A` |
| `secondaryContainer` | `~#334155` (Slate-700) |
| `onSecondaryContainer` | `~#CBD5E1` |
| `tertiary` | `#67E8F9` (Cyan-300) |
| `onTertiary` | `~#06485A` |
| `tertiaryContainer` | `~#155E75` (Cyan-800) |
| `onTertiaryContainer` | `~#CFFAFE` |
| `error` | `#FCA5A5` (Red-300) |
| `onError` | `~#370B0B` |
| `errorContainer` | `~#7F1D1D` (Red-900) |
| `onErrorContainer` | `~#FEE2E2` |
| `background` | `#0A0F0E` (custom near-black) |
| `onBackground` | `~#E5E7EB` (Gray-200) |
| `surface` | `#0C1413` |
| `onSurface` | `~#E5E7EB` |
| `surfaceVariant` | `~#1F2937` (Gray-800) |
| `onSurfaceVariant` | `~#9CA3AF` (Gray-400) |
| `outline` | `~#71717A` (Zinc-500) |
| `outlineVariant` | `~#3F3F46` (Zinc-700) |
| `scrim` | `#000000` |
| `inverseSurface` | `~#E5E7EB` |
| `inverseOnSurface` | `~#1F2937` |
| `surfaceTint` | = `primary` |

> **`~#XXXXXX` notation**: approximate value pre-export. Final values pin sau Material Theme Builder export tại implement-time.

---

## Typography (Material3, 16 role)

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

### Role mapping

| Role | Font | Weight | M3 default size/lh giữ nguyên |
|---|---|---|---|
| `displayLarge` | Poppins | SemiBold (600) | 57sp / 64sp |
| `displayMedium` | Poppins | SemiBold | 45sp / 52sp |
| `displaySmall` | Poppins | SemiBold | 36sp / 44sp |
| `headlineLarge` | Poppins | SemiBold | 32sp / 40sp |
| `headlineMedium` | Poppins | SemiBold | 28sp / 36sp |
| `headlineSmall` | Poppins | SemiBold | 24sp / 32sp |
| `titleLarge` | Poppins | Medium (500) | 22sp / 28sp |
| `titleMedium` | Inter | Medium | 16sp / 24sp |
| `titleSmall` | Inter | Medium | 14sp / 20sp |
| `bodyLarge` | Inter | Normal (400) | 16sp / 24sp |
| `bodyMedium` | Inter | Normal | 14sp / 20sp |
| `bodySmall` | Inter | Normal | 12sp / 16sp |
| `labelLarge` | Inter | Medium | 14sp / 20sp |
| `labelMedium` | Inter | Medium | 12sp / 16sp |
| `labelSmall` | Inter | Medium | 11sp / 16sp |

**Implementation**: dùng `.copy(fontFamily, fontWeight)` trên M3 default — KHÔNG override `fontSize`/`lineHeight`/`letterSpacing` (giữ nguyên Material3 default tinh chỉnh đẹp sẵn).

---

## Shapes (Material3 default)

| Role | Default | Override? |
|---|---|---|
| `extraSmall` | RoundedCornerShape(4.dp) | KHÔNG |
| `small` | RoundedCornerShape(8.dp) | KHÔNG |
| `medium` | RoundedCornerShape(12.dp) | KHÔNG |
| `large` | RoundedCornerShape(16.dp) | KHÔNG |
| `extraLarge` | RoundedCornerShape(28.dp) | KHÔNG |

```kotlin
internal val Shapes = Shapes()  // M3 default constructor
```

---

## Spacing (custom, presentation token)

```kotlin
object Spacing {
    val xs: Dp = 4.dp   // tight inline gaps (chip internal padding)
    val sm: Dp = 8.dp   // button internal, list item separator
    val md: Dp = 16.dp  // default screen edge padding
    val lg: Dp = 24.dp  // section separator, card outer
    val xl: Dp = 32.dp  // hero block, empty state
}
```

Consume qua `Modifier.padding(Spacing.md)`. KHÔNG dùng cho `Modifier.size(...)` (size cho icon/avatar dùng `.dp` literal allowed per spec FR-010).

---

## Window Background Color Resources

```xml
<!-- res/values/colors.xml -->
<color name="window_background_light">#FAFAF9</color>
<color name="window_background_dark">#0A0F0E</color>
```

Sync với `Color.kt` surface light/dark roles (acceptable redundancy: XML resolved pre-Compose, Color.kt resolved post-Compose).

---

## State Transitions

**ThemeMode toggle** (Spec 016 sẽ wire, Spec 003 placeholder):

```
Light → System → Dark → Light  (cycle, user choice)
```

Mỗi transition trigger Compose recomposition của toàn bộ tree wrap bởi `ThirtySixTheme` — re-evaluate `colorScheme` parameter, re-derive surface/text/icon tints.

Spec 003 KHÔNG implement cycle UI — chỉ infrastructure (var themeMode by remember, default System). Programmatic test có thể flip state bằng `themeMode = ThemeMode.Dark` để verify re-compose.

---

## Validation Rules

| Token | Rule | Enforced where |
|---|---|---|
| `Color(0xFF...)` | Chỉ trong `Color.kt` (whitelist: `Color.Transparent`, `Color.Unspecified`) | Code review (grep) + Detekt MagicNumber |
| `fontSize`/`TextStyle(` | Chỉ trong `Type.kt` | Code review (grep) |
| `.padding(N.dp)` repeated | Phải dùng `Spacing.*` | Code review (grep) — size cho icon/avatar OK |
| `Color(0xFF...)` value WCAG | Phải pass AA cho 24+ critical pair | Material Theme Builder pre-export + manual ratio verify |
| Theme name | `ThirtySixTheme` (canonical), KHÔNG `ThirdtySixBrowserTheme` | Spec 003 rewrite + grep verify |
| Font weight bundle | 4 weight chính xác (Poppins M+SB, Inter R+M) | FR-004 check + APK size SC-007 |
| `R.font.*` reference | Đúng filename lowercase + underscore | Compile-time R class |

---

## No-Hardcode Compliance Map (Constitution §III)

| Constitution §III table row | Spec 003 location | Compliance |
|---|---|---|
| Colors | `presentation/theme/Color.kt` | ✅ Direct (no `core/constants/`) |
| Typography | `presentation/theme/Type.kt` | ✅ Direct |
| Shapes | `presentation/theme/Shape.kt` | ✅ Direct |
| Dimensions (reused ≥2 times) | `presentation/theme/Spacing.kt` | ✅ Direct |
| User-facing strings | (no new strings Spec 003 — Spec 002 đã có `<feature>_screen_placeholder`) | ✅ N/A |
| Magic numbers | (no new magic numbers — color/typography/spacing/shape đều có token) | ✅ N/A |
| Animation durations | (no new animations Spec 003) | ✅ N/A |
| Routes / destinations | (Spec 002 đã `AppDestination`) | ✅ N/A |

Theme tokens là **first-class Compose artifact** — KHÔNG nằm trong `core/constants/` per Constitution §III table dòng "Colors"/"Typography"/"Shapes"/"Dimensions" map cụ thể về `presentation/theme/`.
