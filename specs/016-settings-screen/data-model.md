# Data Model: Settings Screen

**Feature**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Research**: [research.md](research.md)
**Date**: 2026-09-11

This feature adds **no database table and no schema migration** — the Room database stays at version 2. Everything it persists lives in the existing DataStore Preferences file (`thirtysix_settings`, Spec 006). The one piece of state it deliberately does **not** persist itself is the app language, which the platform holds (R1).

---

## 1. `UserSettings` — domain, persisted (amended)

The Spec 006 settings snapshot gains two fields and loses one.

| Field | Type | Default | Storage key (DataStore) | Stored as | Status |
|---|---|---|---|---|---|
| `themeMode` | `ThemeMode` | `System` | `theme_mode` | `String` (`storageValue`) | unchanged |
| `isDynamicColorEnabled` | `Boolean` | `true` | `dynamic_color_enabled` | `Boolean` | **NEW** (FR-009) |
| `searchEngine` | `SearchEngine` | `Google` | `search_engine` | `String` (`storageValue`) | unchanged |
| `historyRetention` | `HistoryRetention` | `Days90` | `history_retention_days` | `Int` (day count) | **NEW** (FR-020) |
| `isOnboardingCompleted` | `Boolean` | `false` | `is_onboarding_completed` | `Boolean` | unchanged |
| ~~`languageOverride`~~ | ~~`LanguageOverride`~~ | — | ~~`language_override`~~ | — | **REMOVED** (FR-017, R4) |

**Defaults** live in `AppDefaults` (Constitution §III): `DYNAMIC_COLOR_ENABLED = true`, `HISTORY_RETENTION = HistoryRetention.Days90`; `LANGUAGE_OVERRIDE` is deleted.

**Decoding rules** (`SettingsMapper`, extending Spec 006 FR-008/FR-009):

- Missing `dynamic_color_enabled` → `true`.
- Missing `history_retention_days`, or any stored integer that is not exactly one of the four day counts → `Days90`. A corrupt or future value can never produce an unbounded or out-of-set window.
- An orphaned `language_override` value left on a developer device from Spec 006 testing is simply never read.

**Schema-evolution rules** (`StorageKeys`, Spec 006 FR-019): both new keys are additions, which the rules permit freely. Removing `language_override` would normally require a one-release `@Deprecated` period, but those rules bind only keys that have **shipped to a user in a release build**; the app has never been released, so the key and its supporting code are deleted outright (R4).

**Platform gating**: `isDynamicColorEnabled` is persisted on every Android version but only *consulted* on Android 12+ (API 31). Below that, the Settings screen hides the control and the theme ignores the value (FR-010). The capability check lives in the presentation layer, never in `UserSettings`.

---

## 2. `HistoryRetention` — domain enum (new)

| Entry | `days` |
|---|---|
| `Days7` | 7 |
| `Days30` | 30 |
| `Days90` | 90 — default |
| `Days180` | 180 |

- **Exactly four entries, no unlimited value** (FR-020). The type makes "keep forever" unrepresentable rather than merely unoffered.
- Day counts are named constants in `core/constants/BrowserLimits.kt`, which **replace** the Spec 014 constant `MAX_HISTORY_DAYS` (§III). The constant's KDoc — "making this user-configurable belongs to Spec 016" — is retired with it.
- `isShorterThan(other)` decides whether a change needs the FR-021 warning. Equal is not shorter, so re-selecting the current value is a no-op (FR-006).
- `fromDaysOrDefault(value: Int?)` implements the decoding rule in §1.

**Change semantics** (FR-021 – FR-024):

```text
               choose longer            choose same
   Current ───────────────────▶ write ◀──────────── (no-op, FR-006)
      │                           │
      │ choose shorter            ▼
      ▼                    prune(new window)   ← runs on every change; deletes nothing extra when lengthening
 Confirm shorten ──confirm──▶ write ──▶ prune(new window) ──▶ Current(new)
      │
      └──cancel──▶ Current (unchanged, nothing deleted)
```

**Write before prune** (R8): if the write fails nothing has been deleted and the user can retry; if the prune fails the window is already recorded and the start-up sweep (FR-024) enforces it next launch. The reverse order could delete history while leaving the old window recorded.

The use case reports the three possible outcomes as `HistoryRetentionChangeResult` — `Applied(rowsRemoved)`, `SavedPruneDeferred`, `NotSaved` ([contracts/SettingsUseCases.kt](contracts/SettingsUseCases.kt)). A deferred prune is surfaced to the user rather than swallowed, because the user explicitly asked for that history to be deleted (FR-022, FR-043).

---

## 3. `AppLanguage` — domain enum (new, never persisted by the app)

| Entry | Language tag | Listed as (endonym, FR-014) |
|---|---|---|
| `FollowSystem` | *(none)* | localized "Follow system" label |
| `English` | `en` | English |
| `Vietnamese` | `vi` | Tiếng Việt |
| `German` | `de` | Deutsch |
| `Russian` | `ru` | Русский |
| `Korean` | `ko` | 한국어 |
| `Japanese` | `ja` | 日本語 |
| `Chinese` | `zh` | 中文 |
| `French` | `fr` | Français |

- **Source of truth is the platform's per-app language setting** (FR-016, FR-017, R1). The app reads it through the `AppLanguageController` seam and writes through it; no copy exists in `UserSettings` or anywhere else.
- `FollowSystem` corresponds to the platform reporting **no** app-specific language.
- Mapping from a platform-reported tag matches on the **primary language subtag**, so a tag carrying a region or script (for example `zh-Hans-CN`) still resolves to `Chinese`. An unrecognised language maps to `FollowSystem` rather than failing.
- **Parity invariant**: the eight tags MUST equal the eight `<locale>` entries in `app/src/main/res/xml/locales_config.xml` (Spec 004). A unit test asserts this, so adding a locale in one place and not the other fails the build.
- Endonyms are string resources marked `translatable="false"` in the default `values/strings.xml` only (R11), so every locale shows the same, self-identifying names.

---

## 4. `ClearBrowsingDataCategory` and `ClearBrowsingDataResult` — domain (new, transient)

`ClearBrowsingDataCategory` is an enum of the three user-facing categories. Each maps to the work it performs:

| Category | Clears | Mechanism |
|---|---|---|
| `History` | every history row | `HistoryRepository.clearAll()` (Spec 014) |
| `CookiesAndSiteData` | empties the incognito set-aside **first** (§5); then all cookies and all site data — including service workers and Cache Storage where the web engine supports it, which also empties the web page cache (spec A16); cookies and web storage only on older engines (spec A17) | `CookieJarSnapshotManager.discardSetAsideCookies()` (R7), then `WebDataCleaner.clearCookiesAndSiteData()` (R5) |
| `CachedImagesAndFiles` | the web page cache (unless the site-data step already emptied it); cached site icons; tab preview images | `WebDataCleaner.clearWebCache()` (R6) + `FaviconCache.clearAll()` (**new**) + `ScreenshotCache.clearAll()` (Spec 011) |

`ClearBrowsingDataResult`:

| Field | Type | Meaning |
|---|---|---|
| `requested` | `Set<ClearBrowsingDataCategory>` | What the user selected. Never empty — the dialog disables confirm otherwise (FR-026). |
| `failed` | `Set<ClearBrowsingDataCategory>` | Categories whose work threw or reported failure. Always a subset of `requested`. |

- `failed.isEmpty()` → completion message; otherwise the partial-failure message (FR-033).
- The site-data step reports a `SiteDataClearOutcome` — `Complete` · `Partial` · `Failed` ([contracts/WebDataCleaner.kt](contracts/WebDataCleaner.kt)). `Partial` is the older-engine limitation and counts as **cleared** (spec A17); only `Failed` marks the category failed.
- **Every selected category is attempted even if an earlier one fails** — each runs inside its own failure boundary (FR-033, FR-043).
- A category counts as failed if **any** of its steps fails; the remaining steps of that same category still run, so a failing web cache clear does not leave stale site icons behind.
- Never persisted, never logged with content.

---

## 5. Incognito cookie set-aside — Spec 012 state, amended

Spec 012's `CookieJarSnapshotManager` holds, while an incognito session is active, the normal-browsing cookies that it restores when the last incognito tab closes. FR-030 adds one transition.

| State | Meaning |
|---|---|
| `None` | No incognito session; nothing set aside. |
| `Held(snapshot)` | Incognito session active; `snapshot` will be written back on restore. |
| `Held(EMPTY)` | Incognito session active; restore will wipe the jar and write **nothing** back. |

| From | Event | To | Effect |
|---|---|---|---|
| `None` | first incognito tab opens → `captureSnapshot` | `Held(snapshot)` | Spec 012, unchanged |
| `Held(x)` | further capture | `Held(x)` | Spec 012 idempotency, unchanged |
| `Held(x)` | last incognito tab closes → `restoreSnapshot` | `None` | wipe jar, write `x` back |
| `Held(x)` | **clear cookies and site data → `discardSetAsideCookies`** | **`Held(EMPTY)`** | **NEW** — nothing written back later |
| `None` | **clear cookies and site data → `discardSetAsideCookies`** | **`None`** | **NEW** — no-op |

**Why `EMPTY` and not `None`** (R7): `restoreSnapshot` returns early when nothing is held, *without* wiping the jar. Discarding by setting the state to `None` would therefore make the end of the session skip its wipe, leaking cookies set during the incognito session into normal browsing. `Held(EMPTY)` keeps the wipe and removes only the write-back.

**Ordering inside `ClearBrowsingDataUseCase`** (R7): discard **first**, then wipe. If the last incognito tab closes between the two steps, the restore writes nothing (the set-aside is already empty) and the subsequent wipe completes the clear. The reverse order leaves a window in which a restore resurrects the cookies the user just cleared.

---

## 6. Presentation state

### `SettingsUiState` — immutable `data class`

| Field | Type | Source |
|---|---|---|
| `themeMode` | `ThemeMode` | `ObserveUserSettingsUseCase` |
| `isDynamicColorEnabled` | `Boolean` | `ObserveUserSettingsUseCase` |
| `isDynamicColorSupported` | `Boolean` | platform capability, injected (R9) |
| `searchEngine` | `SearchEngine` | `ObserveUserSettingsUseCase` |
| `historyRetention` | `HistoryRetention` | `ObserveUserSettingsUseCase` |
| `appLanguage` | `AppLanguage` | `GetAppLanguageUseCase`, re-read whenever the screen starts (R12) |
| `appVersionName` | `String` | build metadata, injected (R10) |
| `dialog` | `SettingsDialog?` | view-model; at most one open at a time |

### `SettingsDialog` — sealed

| Variant | Carries |
|---|---|
| `ThemeChooser` | — |
| `LanguageChooser` | — |
| `SearchEngineChooser` | — |
| `RetentionChooser` | — |
| `ConfirmShortenRetention` | `target: HistoryRetention` |
| `ClearBrowsingData` | `selection: Set<ClearBrowsingDataCategory>` (all three on open, FR-025), `inProgress: Boolean` |

While `ClearBrowsingData.inProgress` is `true` the confirm action is disabled and the dialog cannot be dismissed, so a clear can be neither re-submitted nor abandoned midway (FR-032).

### `SettingsEvent` — sealed, one-shot

Delivered on a `SharedFlow` with `replay = 0 / extraBufferCapacity = 1 / DROP_OLDEST` — the Spec 013 channel policy that Specs 014 and 015 reused.

| Variant | Shown as |
|---|---|
| `BrowsingDataCleared` | completion snackbar (FR-033) |
| `BrowsingDataPartiallyCleared` | partial-failure snackbar (FR-033) |
| `SettingNotSaved` | localized "couldn't save" snackbar when a write returns `Result.Error` (FR-043) |
| `RetentionPruneDeferred` | localized message that older history could not be deleted now and will be removed the next time the app starts (FR-022, FR-043) |
| `LanguageNotApplied` | localized message when the platform rejects the change (FR-043) |

---

## 7. What does not change

- **Room database**: version 2, no new table, no migration. `fallbackToDestructiveMigration` stays at zero call sites.
- **Backup posture**: the two new DataStore keys follow the existing "settings included, database excluded" rule from Spec 006. The platform's per-app language storage follows the platform's own behaviour (spec A9).
- **Tabs, bookmarks, downloads**: no model changes. Clearing never touches them (FR-031).
