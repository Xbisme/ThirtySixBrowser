# Data Model: DataStore Settings

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Research**: [research.md](research.md)

This document defines the **domain models**, **on-disk DataStore Preferences shape**, and **mapper rules** that translate between the two boundaries. The on-disk layer is intentionally simple (key-value protobuf via DataStore Preferences); the typing and exhaustive branching are layered on top in `domain/model/`.

---

## Diagram

```text
┌─────────────────────────────────────────────────────────────────────────┐
│                        Consumers (presentation layer)                    │
│                              MainActivity                                │
│           Spec 016 SettingsViewModel (future), Spec 018 (future)         │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ injects (Hilt @Inject)
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     domain/usecase/  (5 use cases)                       │
│  ObserveUserSettingsUseCase           SetThemeModeUseCase                │
│  SetLanguageOverrideUseCase           SetSearchEngineUseCase             │
│  SetOnboardingCompletedUseCase                                           │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ delegates 1:1 to
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                domain/repository/SettingsRepository  (interface)         │
│  observeSettings(): Flow<UserSettings>                                   │
│  setThemeMode(mode): Result<Unit>                                        │
│  setLanguageOverride(override): Result<Unit>                             │
│  setSearchEngine(engine): Result<Unit>                                   │
│  setOnboardingCompleted(value): Result<Unit>                             │
└─────────────────────────────────────────────────────────────────────────┘
                                    │ implements
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│            data/repository/SettingsRepositoryImpl                        │
│  - observeSettings = dataStore.data                                      │
│      .map { mapper.toDomain(it) }.distinctUntilChanged()                 │
│  - setters delegate to SettingsDataStore                                 │
└─────────────────────────────────────────────────────────────────────────┘
                                    │ uses
                                    ▼
┌──────────────────────────────────┐  ┌────────────────────────────────────┐
│  data/local/datastore/           │  │  data/mapper/SettingsMapper        │
│  SettingsDataStore (wrapper)     │  │  Preferences ↔ UserSettings        │
│  - read: dataStore.data: Flow    │  │  Per-field encode/decode helpers   │
│  - write: dataStore.edit { }     │  │  Handles missing-key, unknown enum │
│  - returns Result<Unit>          │  │  values per FR-008, FR-009         │
└──────────────────────────────────┘  └────────────────────────────────────┘
                                    │ wraps
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│            DataStore<Preferences>  (Hilt singleton from SettingsModule)  │
│            File: <files>/datastore/thirtysix_settings.preferences_pb     │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Domain models

### `UserSettings` — snapshot entity

**Location**: `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/model/UserSettings.kt`

| Field | Type | Default | Notes |
|---|---|---|---|
| `themeMode` | `ThemeMode` | `ThemeMode.System` | Spec 003 enum, relocated to domain |
| `languageOverride` | `LanguageOverride` | `LanguageOverride.FollowSystem` | Sealed interface (Q2 clarification) |
| `searchEngine` | `SearchEngine` | `SearchEngine.Google` | Single-entry enum (extensible) |
| `isOnboardingCompleted` | `Boolean` | `false` | Read by Spec 017 / 018 |

```kotlin
package com.raumanian.thirtysix.browser.domain.model

data class UserSettings(
    val themeMode: ThemeMode,
    val languageOverride: LanguageOverride,
    val searchEngine: SearchEngine,
    val isOnboardingCompleted: Boolean,
) {
    companion object {
        val DEFAULT = UserSettings(
            themeMode = AppDefaults.THEME_MODE,
            languageOverride = AppDefaults.LANGUAGE_OVERRIDE,
            searchEngine = AppDefaults.SEARCH_ENGINE,
            isOnboardingCompleted = AppDefaults.IS_ONBOARDING_COMPLETED,
        )
    }
}
```

**Invariants**:

- Immutable by construction (all `val`, `data class`).
- `DEFAULT` is structurally identical to "settings file does not yet exist" — guarantees FR-008 default-handling correctness for the first-frame initial value (R6 in research.md).

---

### `ThemeMode` — enum (relocated from `presentation/theme/`)

**Location**: `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/model/ThemeMode.kt`
**Previous location** (Spec 003): `app/src/main/kotlin/.../presentation/theme/ThemeMode.kt` — DELETE after move.

| Variant | Storage value | Behavior |
|---|---|---|
| `Light` | `"light"` | Force light theme always |
| `Dark` | `"dark"` | Force dark theme always |
| `System` | `"system"` | Follow `isSystemInDarkTheme()` |

```kotlin
package com.raumanian.thirtysix.browser.domain.model

enum class ThemeMode(val storageValue: String) {
    Light("light"),
    Dark("dark"),
    System("system"),
    ;

    companion object {
        fun fromStorageValueOrDefault(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: AppDefaults.THEME_MODE
    }
}
```

**FR-021 migration**: Move file via `git mv` to preserve history. Update single import in `MainActivity.kt`. No other consumers exist (verified via grep on 2026-05-01: 2 files reference `ThemeMode` total — the enum file itself + MainActivity).

**FR-009 unknown-value handling**: `fromStorageValueOrDefault` falls back to `AppDefaults.THEME_MODE` (= `System`) if a future build wrote an unrecognized value or if the value is null/missing.

---

### `LanguageOverride` — sealed interface (Q2 clarification)

**Location**: `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/model/LanguageOverride.kt`

| Variant | Construction | On-disk encoding |
|---|---|---|
| `FollowSystem` | `LanguageOverride.FollowSystem` (data object) | absent key OR `null` |
| `Explicit(bcp47)` | `LanguageOverride.Explicit("vi")` | non-empty `String` (e.g. `"vi"`, `"de"`, `"zh"`) |

```kotlin
package com.raumanian.thirtysix.browser.domain.model

sealed interface LanguageOverride {
    data object FollowSystem : LanguageOverride
    data class Explicit(val bcp47: String) : LanguageOverride
}
```

**Constraints**:

- `Explicit("")` is forbidden by convention — to clear the override the caller MUST pass `FollowSystem`. The mapper's decode rule (`String?.toLanguageOverride()`) treats blank/empty strings as `FollowSystem` defensively, but `setLanguageOverride(Explicit(""))` is a programmer error and MAY be enforced via a `require(bcp47.isNotBlank())` in a future spec when an in-app picker exists.
- BCP-47 validation deferred to Spec 016's picker. v1.0 callers pass one of Spec 004's eight tags.

---

### `SearchEngine` — enum (extensible)

**Location**: `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/model/SearchEngine.kt`

| Variant | Storage value | Notes |
|---|---|---|
| `Google` | `"google"` | The only v1.0 entry |

```kotlin
package com.raumanian.thirtysix.browser.domain.model

enum class SearchEngine(val storageValue: String) {
    Google("google"),
    ;

    companion object {
        fun fromStorageValueOrDefault(value: String?): SearchEngine =
            entries.firstOrNull { it.storageValue == value } ?: AppDefaults.SEARCH_ENGINE
    }
}
```

**Future-extension note**: When Spec 010 / a future spec adds Bing or DuckDuckGo, only this enum + `AppDefaults` need updates — no DataStore migration needed because the storage value remains a `String`. The unknown-value fallback (FR-009) ensures a downgraded build that encounters `"bing"` falls back to `Google` rather than crashing.

---

### `AppError.Database` — reused (Spec 002)

**Location**: `core/error/AppError.kt` — already exists. Spec 006 reuses the existing `AppError.Database(throwable)` variant for write failures.

```kotlin
// Existing — Spec 002 — referenced unchanged
sealed class AppError(open val cause: Throwable?) {
    data class Network(override val cause: Throwable?) : AppError(cause)
    data class Database(override val cause: Throwable?) : AppError(cause)
    data class Unknown(override val cause: Throwable?) : AppError(cause)

    companion object {
        fun from(throwable: Throwable): AppError = when (throwable) {
            is IOException -> Database(throwable)
            // ...other mappings from Spec 002
            else -> Unknown(throwable)
        }
    }
}
```

DataStore raises `IOException` for disk failures → maps to `AppError.Database` automatically.

---

## On-disk DataStore Preferences shape

**File path**: `<app-files-dir>/datastore/thirtysix_settings.preferences_pb`
- Production: `/data/data/com.raumanian.thirtysix.browser/files/datastore/thirtysix_settings.preferences_pb`
- Debug variant: `/data/data/com.raumanian.thirtysix.browser.debug/files/datastore/thirtysix_settings.preferences_pb` (if debug applicationIdSuffix is enabled — currently it is not, per Spec 001)

**File name constant**: `AppConstants.SETTINGS_DATASTORE_FILE_NAME = "thirtysix_settings"`. The `.preferences_pb` extension is appended by DataStore at file creation time.

**Backup posture** (FR-013): INCLUDED in cloud backup AND device-to-device transfer. See [research.md R3](research.md#r3--backup-posture-for-settings-file-fr-013-fr-014) for XML implementation.

### Key inventory

**Defined in** `core/constants/StorageKeys.kt` — single source of truth per Constitution §III.

| Key (storage name) | Preferences type | Domain field | Default behavior on missing |
|---|---|---|---|
| `theme_mode` | `stringPreferencesKey("theme_mode")` | `UserSettings.themeMode` | Map to `ThemeMode.System` |
| `language_override` | `stringPreferencesKey("language_override")` | `UserSettings.languageOverride` | Map to `LanguageOverride.FollowSystem` |
| `search_engine` | `stringPreferencesKey("search_engine")` | `UserSettings.searchEngine` | Map to `SearchEngine.Google` |
| `is_onboarding_completed` | `booleanPreferencesKey("is_onboarding_completed")` | `UserSettings.isOnboardingCompleted` | Map to `false` |

All four key strings are declared as `const val` companion fields if reused across decode/encode call sites, OR as the `Preferences.Key<T>` symbol itself if used only as the key handle. The chosen pattern is the latter (the `Preferences.Key<T>` IS the constant).

### Schema evolution rules (FR-019)

Documented as a doc-comment block at the top of `StorageKeys.kt` per [research.md R7](research.md#r7--schema-evolution-rule-fr-019--implementation). Summary:

1. Once shipped, key names are immutable.
2. Once shipped, key value types are immutable.
3. To change semantics, introduce a NEW key + one-time migration; keep old key `@Deprecated` for one release.
4. Removal: `@Deprecated` for one release, then delete.

Negative-path test (`SettingsMapperTest#renamedKey_returnsDefault_oldValueGone`) demonstrates rule 1 cannot be silently broken.

---

## `core/constants/AppDefaults.kt`

**Location**: `app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/AppDefaults.kt` (NEW per Constitution §III).

```kotlin
package com.raumanian.thirtysix.browser.core.constants

import com.raumanian.thirtysix.browser.domain.model.LanguageOverride
import com.raumanian.thirtysix.browser.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.domain.model.ThemeMode

/**
 * Application-wide default values for user-overridable settings.
 *
 * Per Constitution §III No-Hardcode Rule: defaults live HERE, not inline at
 * call sites. Mapper + UserSettings.DEFAULT both read from this object.
 */
object AppDefaults {
    val THEME_MODE: ThemeMode = ThemeMode.System
    val LANGUAGE_OVERRIDE: LanguageOverride = LanguageOverride.FollowSystem
    val SEARCH_ENGINE: SearchEngine = SearchEngine.Google
    const val IS_ONBOARDING_COMPLETED: Boolean = false
}
```

> **Note on dependency direction**: `core/constants/` is allowed to import from `domain/model/` because the constants ARE references to domain values. This does NOT violate Clean Architecture — `core/` is a horizontal cross-cutting layer, not part of the data/domain/presentation vertical.

---

## Mapper rules (`data/mapper/SettingsMapper.kt`)

**Location**: `app/src/main/kotlin/com/raumanian/thirtysix/browser/data/mapper/SettingsMapper.kt`

```kotlin
package com.raumanian.thirtysix.browser.data.mapper

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.raumanian.thirtysix.browser.core.constants.AppDefaults
import com.raumanian.thirtysix.browser.core.constants.StorageKeys
import com.raumanian.thirtysix.browser.domain.model.LanguageOverride
import com.raumanian.thirtysix.browser.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.domain.model.UserSettings
import javax.inject.Inject

class SettingsMapper @Inject constructor() {

    fun toDomain(prefs: Preferences): UserSettings = UserSettings(
        themeMode = ThemeMode.fromStorageValueOrDefault(prefs[StorageKeys.THEME_MODE]),
        languageOverride = prefs[StorageKeys.LANGUAGE_OVERRIDE].toLanguageOverride(),
        searchEngine = SearchEngine.fromStorageValueOrDefault(prefs[StorageKeys.SEARCH_ENGINE]),
        isOnboardingCompleted = prefs[StorageKeys.IS_ONBOARDING_COMPLETED]
            ?: AppDefaults.IS_ONBOARDING_COMPLETED,
    )

    private fun String?.toLanguageOverride(): LanguageOverride =
        if (this.isNullOrBlank()) LanguageOverride.FollowSystem
        else LanguageOverride.Explicit(this)
}
```

### Mapper test surface

| Test | Purpose | FR / SC link |
|---|---|---|
| `emptyPrefs_returnsAllDefaults` | First-launch: every key missing → `UserSettings.DEFAULT` | FR-008, US2 |
| `unknownThemeValue_fallsBackToDefault` | Storage has `"future_theme"` → maps to `System` | FR-009 |
| `unknownSearchEngineValue_fallsBackToDefault` | Storage has `"bing"` → maps to `Google` | FR-009 |
| `nullLanguage_isFollowSystem` | Absent OR null OR `""` → `FollowSystem` | FR-005, R5 |
| `explicitLanguage_isExplicit` | `"vi"` → `Explicit("vi")` | FR-005 |
| `renamedKey_returnsDefault_oldValueGone` | Negative-path: simulated rename of `theme_mode` → `theme` | FR-019, US7 |
| `roundTrip_allFieldsPreserved` | Encode every value via `dataStore.edit`, decode → identity | SC-003 |

---

## State transitions

The `UserSettings` snapshot is **immutable** — there are no field-level state transitions. Each setter call produces a new `Preferences` snapshot on disk and a new `UserSettings` snapshot in the observed Flow. The diagram below shows the data flow per setter call:

```text
caller.setThemeMode(Dark)
        │
        ▼
SetThemeModeUseCase.invoke(Dark): Result<Unit>
        │ (delegate)
        ▼
SettingsRepository.setThemeMode(Dark): Result<Unit>
        │ (delegate)
        ▼
SettingsDataStore.setThemeMode(Dark): Result<Unit>
        │
        ▼ try { dataStore.edit { it[THEME_MODE] = "dark" }; Success } catch (IOException) { Error(AppError.from(io)) }
        │
        ▼ ON SUCCESS: DataStore writes new protobuf snapshot, notifies all observers
        │
        ▼
SettingsRepositoryImpl.observeSettings()
   .map { mapper.toDomain(it) }
   .distinctUntilChanged()
        │
        ▼
ObserveUserSettingsUseCase().collectAsStateWithLifecycle(...)
        │
        ▼
MainActivity recomposes with new UserSettings (themeMode = Dark)
        │
        ▼
ThirtySixTheme(darkTheme = true) re-applies
```

**ON FAILURE** (e.g. disk full): `Result.Error(AppError.Database(IOException))` returns through use case → caller. The Flow does NOT emit (the failed `dataStore.edit` did not write). Observers continue to see the previous snapshot. Caller decides UX (retry / surface error to user / silently ignore in non-critical paths).

---

## Validation rules

| Rule | Where enforced | Rationale |
|---|---|---|
| `themeMode` is one of {Light, Dark, System} | Compile-time (sealed enum) + `fromStorageValueOrDefault` decode | FR-009 |
| `languageOverride` is `FollowSystem` OR `Explicit(non-empty)` | `toLanguageOverride()` decode treats blank as `FollowSystem` | FR-005 + R5 defensive |
| `searchEngine` is one of {Google} (v1.0) | Compile-time (enum) + `fromStorageValueOrDefault` decode | FR-006, FR-009 |
| `isOnboardingCompleted` is Boolean | DataStore `booleanPreferencesKey` enforces type | FR-007 |
| Setters never throw to caller | `try/catch IOException → Result.Error` in `SettingsDataStore` | FR-011, R4 |
| Concurrent writes to different keys both persist | DataStore internal serialization | FR-012, US6 |
| Concurrent writes to same key: last-writer-wins | DataStore internal serialization | FR-012 |
