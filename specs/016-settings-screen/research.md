# Research: Settings Screen

**Feature**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Data model**: [data-model.md](data-model.md)
**Date**: 2026-09-11

The spec's clarifications settled every product question — one of them, R5, was put to the product owner during planning once the platform findings turned it into a product choice — so this document holds **no NEEDS CLARIFICATION**, only questions about the platform and the codebase. Each item records a **Decision**, its **Rationale**, and the **Alternatives considered**, with a **Basis** tag:

- **verified-here** — confirmed by reading this repository's code
- **documented** — confirmed from official Android / AndroidX / Chromium sources, cited inline
- **measured** — confirmed by a build experiment run during planning
- **gate** — cannot be settled off-device; closed by a manual gate in [quickstart.md](quickstart.md)

## Summary

| R# | Topic | Decision (preview) | Basis |
|----|-------|--------------------|-------|
| R1 | Per-app language mechanism | AndroidX per-app language API with automatic locale storage; the platform is the single source of truth | documented + gate |
| R2 | `MainActivity` base class and window theme | `AppCompatActivity`; both theme files re-parented to AppCompat NoActionBar themes, window-background items kept | documented + gate |
| R3 | Size cost of the new libraries (SC-014) | Both measured in release builds: appcompat +478,564 B, webkit +16,549 B, zero new `.so`; budget +699,913 B | measured |
| R4 | Retiring the Spec 006 language value | Delete outright — never read, never released | verified-here |
| R5 | Clearing cookies and site data | `androidx.webkit` complete removal where the engine supports it; framework fallback otherwise (product-owner decision) | documented + gate |
| R6 | Clearing cached images and files | Web cache via a detached throwaway `WebView` unless R5 already emptied it; icon and preview caches via `clearAll()` | documented + gate |
| R7 | Discarding the incognito cookie set-aside | Replace the held snapshot with `EMPTY`, never null; discard **before** wiping | verified-here |
| R8 | Retention persistence and enforcement | Day count in DataStore; write-then-prune with three outcomes; start-up sweep reads the setting | verified-here |
| R9 | Dynamic color persistence and capability | New Boolean key; reuse the theme's existing `dynamicColor` parameter; capability injected as a Boolean | verified-here |
| R10 | Showing the installed version | Enable `buildConfig`; show `BuildConfig.VERSION_NAME` | documented |
| R11 | Language chooser labels and locale parity | Non-translatable endonym resources; unit test pins parity with `locales_config.xml` | verified-here |
| R12 | Keeping the displayed language fresh | Re-read the platform value every time the screen starts | verified-here + gate |
| R13 | Files and tests from earlier specs touched | Enumerated; no default no-op members added to dodge test churn | verified-here |
| R14 | Reusable choosers for Spec 018 | Stateless composables; no onboarding code written | verified-here |
| R15 | Seeding data for the SC-009 gate | Reuse `HistorySeeder`; add a debug-only `TabSeeder` | verified-here |

---

## R1 — Per-app language mechanism

**Decision**: Switch the app language through `androidx.appcompat`'s per-app language API — `AppCompatDelegate.setApplicationLocales` / `getApplicationLocales` — behind an app-owned `AppLanguageController` seam ([contracts/AppLanguageController.kt](contracts/AppLanguageController.kt)). Declare `AppLocalesMetadataHolderService` with `autoStoreLocales = true` so the library persists the choice below Android 13, and let the framework persist it on Android 13+. The app keeps no copy of its own (FR-017).

**Rationale** (documented, verified 2026-09-11):
- On API 33+ the call is handed to the framework `LocaleManager`, which persists the value and backs it up with the user's Backup & Restore setting ([AppCompatDelegate](https://developer.android.com/reference/androidx/appcompat/app/AppCompatDelegate), [LocaleManager](https://developer.android.com/reference/android/app/LocaleManager)).
- On API 24–32 the guide is explicit: "you must extend your activity from AppCompatActivity. Otherwise, setting the app locale won't work" ([app-languages guide](https://developer.android.com/guide/topics/resources/app-languages)). No official AndroidX or framework path exists below API 33 without AppCompat; `LocaleManagerCompat` can only *read*.
- The guide recommends the public APIs over custom logic because they "automatically sync with system settings", which is precisely FR-016.
- A language change is a configuration change, so the activity is recreated and Compose rebuilds the UI ([runtime changes](https://developer.android.com/guide/topics/resources/runtime-changes)). Spec A6 accepts the resulting page reload.
- Constraints carried into the implementation: call `setApplicationLocales` **on the main thread** and **after** `Activity.onCreate`; `autoStoreLocales` performs a blocking main-thread disk read at start-up below API 33 (a StrictMode disk-read notice, not a crash — the app does not enable StrictMode).
- Constitution §VIII already names this API, so no amendment is needed.

**Alternatives considered**:
- **Framework `LocaleManager` only, Android 13+** — rejected during clarification: no in-app control on Android 7.0–12, and §VIII would need amending.
- **App-managed storage in DataStore, applied in `attachBaseContext` below API 33** — rejected: it re-creates the second copy FR-017 forbids, still needs a blocking read before `onCreate`, and is the "custom app logic" the guide advises against.
- **A hand-rolled `createConfigurationContext` wrapper** — rejected: undocumented, fragile across OEM builds, and would not sync with Android 13+ system settings.

**Two further details** (documented):
- `getApplicationLocales()` "Returns a `LocaleListCompat#getEmptyLocaleList()` if no app-specific locales are set", and passing that empty list "reset[s] to the system locale". `AppLanguage.FollowSystem` therefore maps to the empty list in both directions.
- Below API 33 AppCompat skips a request identical to the stored one (`if (!locales.equals(sRequestedAppLocales))`); on API 33+ it always forwards to `LocaleManager`, and whether the framework then recreates the activity for an identical value is **unverified**. `SetAppLanguageUseCase` therefore compares against the current value first and never makes that call (FR-006) — the question never arises.

**Gate**: the behaviour on API 24 and API 36, including the system-settings round trip on API 36, is proven on device in quickstart **G4**.

---

## R2 — `MainActivity` base class and window theme

**Decision**: `MainActivity` extends `AppCompatActivity`. `Theme.ThirtySix` is re-parented to `Theme.AppCompat.Light.NoActionBar` in `values/themes.xml` and `Theme.AppCompat.NoActionBar` in `values-night/themes.xml`, keeping both existing items — `android:windowBackground` and `android:statusBarColor` — so Spec 003's cold-start fix carries over untouched. The app does not call `AppCompatDelegate.setDefaultNightMode`.

**Rationale** (documented):
- An AppCompat theme is mandatory, not stylistic. `AppCompatDelegateImpl` throws `"You need to use a Theme.AppCompat theme (or descendant) with this activity."` while setting up its content view, and Compose's `setContent` calls `setContentView` ([AppCompatDelegateImpl.java](https://github.com/androidx/androidx/blob/androidx-main/appcompat/appcompat/src/main/java/androidx/appcompat/app/AppCompatDelegateImpl.java)). With today's platform `android:Theme.Material.*` parents, the app would crash on launch.
- All three NoActionBar variants exist in appcompat 1.8.0's `values.xml`. No other theme attribute was found to be required for a Compose-only activity.
- `setContent` is an extension on `ComponentActivity`, which `AppCompatActivity` extends via `FragmentActivity`; Hilt "only supports activities that extend ComponentActivity, such as AppCompatActivity" ([Hilt guide](https://developer.android.com/training/dependency-injection/hilt-android)). `enableEdgeToEdge()` and the Spec 012 screenshot-protection effect operate on the `ComponentActivity` and its window, so neither changes (verified-here, `MainActivity.kt`).
- AppCompat's default night mode is `MODE_NIGHT_UNSPECIFIED`, which defers to the system `uiMode` ([AppCompatDelegate](https://developer.android.com/reference/androidx/appcompat/app/AppCompatDelegate)). The `values-night` window background therefore keeps following the **system** dark setting — exactly as it does today.

**Pre-existing behaviour, not a regression**: when the in-app theme differs from the system dark setting, the window background follows the system while Compose content follows the in-app choice. This has been true since Spec 003 and this spec neither causes nor fixes it.

**Alternatives considered**:
- **A single `Theme.AppCompat.DayNight.NoActionBar` parent in `values/` only** — functionally equivalent, but it would dissolve Spec 003's explicit two-file layout, which exists to carry two different window-background colours.
- **Driving AppCompat's night mode from the in-app theme** — not pursued: it would route every theme change through an activity-level configuration change, putting FR-008's "no rebuild of the app's screens" at risk for a cosmetic cold-start detail.

**Gate**: quickstart **G1** launches the app on API 24 and API 36 (a wrong theme parent crashes immediately) and **G10** re-checks the Spec 003 cold-start no-flash behaviour in both system themes.

---

## R3 — Size cost of the new libraries (SC-014)

**Decision**: Measure each new library's contribution to the release APK and set SC-014's budget exactly as clarification Q4 prescribes — the measured contributions plus 200 KB for the feature's own code. Both libraries are measured below, which fixes the budget at **+699,913 bytes**: 495,113 B for the two libraries plus 204,800 B — 200 KiB, the unit this project's size figures have always used — for the feature. Against the measured baseline of 2,558,178 B (the Spec 015 figure reported as 2.44 MB), the release APK may reach **3,258,091 bytes** at most. Recorded in spec SC-014.

**Measurement 1 — `androidx.appcompat` 1.8.0** (measured):

Isolated git worktree at `52e3053`; release build with R8 and resource shrinking; debug-signing fallback. Version looked up 2026-09-11 03:11 UTC from Google Maven as the latest stable. The spike applied the R1 + R2 shape without feature code: the dependency, `AppCompatActivity`, AppCompat parents in both theme files, the locale-storage service entry, and one reachable `getApplicationLocales()` call.

| Metric | Baseline | With appcompat | Delta |
|---|---|---|---|
| Release APK | 2,558,178 B | 3,036,742 B | **+478,564 B (+467.3 KiB, +18.7 %)** |
| `classes.dex` (stored) | 1,453,659 B | 1,621,178 B | +167,519 B |
| `resources.arsc` | 246,704 B | 398,056 B | +151,352 B |
| Files in APK | 142 | 426 | +284 |

Where the growth comes from:
- **Code, about 35 %**: 191 AppCompat classes survive R8 once `AppCompatDelegate` is reachable.
- **Resources, about 57 %**: the resource table grows from 315 to 1,154 entries (styles 5 → 214, attrs 15 → 276, drawables 3 → 91, dimens 0 → 58) and 277 files are added (150 PNGs, 127 XMLs). The shrinker keeps them because the mandatory AppCompat theme parents (R2) reference them. The rest is zip overhead for the extra entries.
- **Native code**: the same eight `.so` entries before and after, byte-identical; `verify-16kb-alignment.sh` exits 0 with every segment at `align=0x4000` (SC-015).
- **New runtime artifacts**: `appcompat`, `appcompat-resources`, `cursoradapter`, `drawerlayout`, `emoji2-views-helper`, `resourceinspection-annotation`, `vectordrawable`, `vectordrawable-animated`; `fragment` moves from 1.5.1 to 1.5.4. `lintVitalRelease` passed with the theme change.

**Context**: about 2.3 times the +200 KB budget Specs 014 and 015 each used, while the release APK (≈ 3.0 MB) stays far inside Constitution §V's 10 MB ceiling. The cost is structural: every mechanism that meets FR-015 on Android 7.0–12 needs `AppCompatActivity` (R1), which needs the AppCompat theme (R2), which is what keeps the resources.

**Measurement 2 — `androidx.webkit` 1.17.0, on top of appcompat** (measured):

Same worktree base and build settings. The version was looked up 2026-09-11 03:29 UTC from the release notes; the Maven metadata's `<release>` tag points at 1.18.0-alpha01, which was excluded. The R5 call was made reachable behind a branch R8 cannot prove false, and R8's mapping confirms that `WebStorageCompat.deleteBrowsingData` and the feature check survived — so the figure is code that would actually ship. Both reference builds reproduced Measurement 1 byte for byte.

| Build | Release APK | Delta vs baseline |
|---|---|---|
| Baseline | 2,558,178 B | — |
| + appcompat | 3,036,742 B | +478,564 B |
| + appcompat + webkit | 3,053,291 B | **+495,113 B** |

- **webkit alone: +16,549 B**, all of it code; `resources.arsc` is unchanged.
- **Native code**: still the same eight `.so` entries; `verify-16kb-alignment.sh` exits 0 on this build too.
- **New runtime artifacts**: only `androidx.webkit:webkit:1.17.0` itself — every dependency it declares was already resolved.

**Informational, not adopted — restricting packaged locales.** Adding `androidResources { localeFilters += listOf("en", "vi", "de", "ru", "ko", "ja", "zh", "fr") }`, which is valid in AGP 9.1.1, shrinks the combined build by **221,076 B**, all from `resources.arsc`, by cutting the 86 packaged locale qualifiers that existing AndroidX libraries bring in down to 7 (English comes from the default resources). It would offset almost half of both libraries' cost, but it changes every screen rather than this feature, so it is left out of this plan. If it is ever adopted, weigh its side effect: library strings for regional variants such as `zh-rTW`, `zh-rHK` and `en-rGB` would fall back to plain `zh` or to the default.

**Alternatives considered**: none that preserve FR-015 (see R1) or FR-028 (see R5).

---

## R4 — Retiring the Spec 006 language value

**Decision**: Delete the whole Spec 006 language slice: the `LanguageOverride` model, `StorageKeys.LANGUAGE_OVERRIDE`, `AppDefaults.LANGUAGE_OVERRIDE`, `UserSettings.languageOverride`, the mapper branch, `SettingsDataStore.setLanguageOverride`, `SettingsRepository.setLanguageOverride` and its implementation, and `SetLanguageOverrideUseCase`. Update the five test files that reference them: `SettingsDataStoreTest`, `SettingsMapperTest`, `SettingsRepositoryImplTest`, `SettingsUseCasesTest`, `SearchEngineRepositoryImplTest`.

**Rationale** (verified-here):
- A repository-wide search finds the value referenced only by the storage slice itself and its tests. **Nothing in production ever reads it**: `MainActivity` consults `themeMode` alone.
- `StorageKeys`' schema-evolution rules (Spec 006 FR-019) protect keys "once … shipped to a user via a release build". The app has never been released — Constitution follow-up `TODO(PLAY_LISTING)` is still open — so no user holds the key and nothing needs migrating.
- Keeping it would be exactly the stale second copy FR-017 prohibits.

**Alternatives considered**:
- **Keep it as a mirror of the platform value** — rejected: it goes stale whenever the language changes from system settings (FR-016).
- **`@Deprecated` for one release cycle** — rejected: no release has ever carried the key, so the cycle would protect nobody while keeping dead code and tests alive.

---

## R5 — Clearing cookies and site data

**Decision** (put to the product owner once the findings below were in, 2026-09-11): adopt `androidx.webkit`. When `WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)` is true, clear with `WebStorageCompat.deleteBrowsingData(WebStorage.getInstance(), callback)`. Otherwise fall back to the framework: `CookieManager.removeAllCookies(callback)` followed by `CookieManager.flush()`, plus `WebStorage.getInstance().deleteAllData()`. The seam reports `Complete`, `Partial` (fallback) or `Failed` ([contracts/WebDataCleaner.kt](contracts/WebDataCleaner.kt)).

**Rationale** (documented):
- **The framework storage call cannot meet FR-028 on any device.** Its documentation still says it covers "Web SQL Database and the HTML5 Web Storage APIs", but Chromium's implementation — `// (Legacy) Clear all web storage data except cookies.` — removes only `REMOVE_DATA_MASK_FILE_SYSTEMS | REMOVE_DATA_MASK_INDEXEDDB | REMOVE_DATA_MASK_LOCAL_STORAGE`, the last of which "Includes both local storage and session storage", with a no-op completion callback ([aw_quota_manager_bridge.cc](https://chromium.googlesource.com/chromium/src/+/main/android_webview/browser/aw_quota_manager_bridge.cc), [storage_partition.h](https://chromium.googlesource.com/chromium/src/+/main/content/public/browser/storage_partition.h)). **Cache Storage and service-worker registrations are never removed.** WebSQL left the mask between development tags 138 and 139.
- **Cookie removal is complete.** `removeAllCookies` passes an empty filter, which "matches all cookies" — session, persistent and partitioned ([cookie_manager.cc](https://chromium.googlesource.com/chromium/src/+/main/android_webview/browser/cookie_manager.cc), [cookie_manager.mojom](https://chromium.googlesource.com/chromium/src/+/main/services/network/public/mojom/cookie_manager.mojom)). WebView persists session cookies across restarts, and the cookie store writes only every 30 seconds or after 512 changes, so the fallback calls `flush()` once the removal callback runs; that this is *required* for the removal to survive process death is an inference, not documentation.
- **`WebStorageCompat.deleteBrowsingData` is complete.** Its Javadoc: "Delete all data stored by websites … This includes network cache, cookies, and any JavaScript-readable storage"; the 1.13.0 release notes add that it will "guarantee deletion of all local storage, including the network cache and cookies, as well as any installed service workers" ([WebStorageCompat.java](https://github.com/androidx/androidx/blob/androidx-main/webkit/webkit/src/main/java/androidx/webkit/WebStorageCompat.java), [release notes](https://developer.android.com/jetpack/androidx/releases/webkit)). It is `@UiThread`, reports completion through a callback, is "not an atomic operation", and throws `UnsupportedOperationException` when unsupported — hence the feature check.
- **Support depends on the installed web engine**, not on the Android version. No minimum is documented; the feature appears in Chromium's supported-feature list between development tags 132.0.6834.0 and 133.0.6943.0. Devices with an older engine take the fallback.
- **Dependency posture (§IX)**: `androidx.webkit` **1.17.0** (released 2026-08-12) is the latest stable; 1.18.0-alpha01 exists and is excluded. Its minSdk has been 24 — this project's — since 1.16.0-alpha03. The AAR and every AndroidX artifact in its declared dependency graph contain **zero** `.so` files. Size is measured in R3.

**Consequences recorded in the spec**:
- On supported engines the same operation empties the web page cache, and there is no way to keep it — spec **A16**. The app's own site icons and tab previews are unaffected unless their category is selected.
- On older engines, service workers and Cache Storage survive a clear. The category is still reported as cleared, because nothing further is possible on that device — spec **A17**.

**Alternatives considered**:
- **Framework APIs only** — rejected by the product owner: service workers and Cache Storage would never be removed on any device, and FR-028 would have to be narrowed.
- **`deleteBrowsingDataForSite` per site** — rejected: it needs a list of sites the app does not keep, and it removes the cache for each site just the same.
- **A separate web profile for incognito, discarded on exit** — rejected: an incognito redesign, far outside this spec.

**Gate**: quickstart **G5** runs the clear on both AVDs, records each device's web engine version and which path ran, and verifies what was and was not removed on each path.

---

## R6 — Clearing cached images and files

**Decision**:
- **Web page cache**: if the same clear already ran the site-data step with outcome `Complete`, the cache is already empty — skip. Otherwise construct a `WebView` on the main thread, call `clearCache(true)`, and `destroy()` it. The instance is never attached to a window and never loads a page.
- **App caches**: `FaviconCache.clearAll()` (new) and `ScreenshotCache.clearAll()` (existing since Spec 011). Both bump their `version` flow, so visible cards and rows fall back to placeholders at once.

**Rationale** (documented):
- `clearCache`: "the cache is per-application, so this will clear the cache for all WebViews used"; with `false`, "only the RAM cache is cleared"; it must run on the thread that created the instance ([WebView](https://developer.android.com/reference/android/webkit/WebView)). In Chromium, `true` starts an all-time removal of the HTTP cache, code caches, shader cache and prefetch caches, which also resets learned transport-security state such as HSTS ([aw_contents.cc](https://chromium.googlesource.com/chromium/src/+/main/android_webview/browser/aw_contents.cc), [browsing_data_remover_impl.cc](https://chromium.googlesource.com/chromium/src/+/main/content/browser/browsing_data/browsing_data_remover_impl.cc)). The removal is asynchronous with no completion signal, so success means the calls returned without throwing.
- No API empties **only** the web cache without a `WebView` instance, and `deleteBrowsingData` would also delete cookies, breaking the independence of the categories when only the cache is selected.
- `ScreenshotCache.clearAll()` already exists; `FaviconCache` has no equivalent today (verified-here), hence the amendment in [contracts/ExistingInterfaceAmendments.kt](contracts/ExistingInterfaceAmendments.kt).

**Accepted departure from guidance**: the `WebView` reference says an instance "should always be instantiated with an Activity Context". The cleaner is an application-scoped seam with no activity, and the throwaway instance shows no UI, opens no dialog and loads nothing — the features that need an activity are never reached. Whether the in-memory half of `clearCache` reaches a renderer that never started, and what constructing the instance costs, are **unverified**; the disk removal is engine-wide either way. Quickstart **G5** confirms on both AVDs that the path runs without a crash and that previously cached pages are re-fetched.

**Alternatives considered**:
- **Hand an activity context from the screen to the cleaner** — rejected: it threads a platform object with a lifetime hazard through the view-model, which FR-042 exists to prevent.
- **Skip the web cache when only this category is selected** — rejected: FR-029 requires it.
- **Use `deleteBrowsingData` for the cache too** — rejected: it removes cookies as well.

---

## R7 — Discarding the incognito cookie set-aside (FR-030)

**Decision**: Add `CookieJarSnapshotManager.discardSetAsideCookies()`. When a snapshot is held it is **replaced with `CookieJarSnapshot.EMPTY`**; when none is held the call is a no-op. It runs under the manager's existing mutex. `ClearBrowsingDataUseCase` calls it **before** wiping the cookie jar. Contract: [contracts/ExistingInterfaceAmendments.kt](contracts/ExistingInterfaceAmendments.kt).

**Rationale** (verified-here, `data/local/cookies/CookieJarSnapshotManagerImpl.kt`):
- `restoreSnapshot()` begins `val held = snapshot ?: return@withLock` (line 84). With nothing held it returns **without wiping the jar**. Discarding by setting the snapshot to null would therefore make the end of the incognito session skip its wipe, and cookies set *during* the incognito session would survive into normal browsing — a privacy leak.
- `CookieJarSnapshot.EMPTY` already exists, documented as the marker for "captured nothing". Restoring it wipes the jar and writes nothing back, which is exactly FR-030.
- **Ordering**: with discard-then-wipe, a last-incognito-tab close landing between the two steps restores `EMPTY` (a wipe) and the wipe that follows completes the clear. With wipe-then-discard, the same interleaving restores the *old* snapshot first, resurrecting the cookies the user just cleared.
- `captureSnapshot` is a no-op while any snapshot — including `EMPTY` — is held, so a new incognito tab opening after the clear cannot re-capture the cleared cookies.

**Alternatives considered**:
- **Set the snapshot to null** — rejected: the leak above.
- **Make `restoreSnapshot()` always wipe** — rejected: it would change Spec 012's documented defensive contract ("restoring when no snapshot is held is a no-op"), which exists to survive lifecycle-event reordering.
- **Disable the cookie option, or close incognito tabs first** — both rejected by the product owner during clarification.

---

## R8 — Retention persistence and enforcement

**Decision**:
- Persist the window as `intPreferencesKey("history_retention_days")` holding the day count; decode anything other than 7, 30, 90 or 180 to 90.
- Model it as the `HistoryRetention` enum, with its day counts as named constants in `BrowserLimits` replacing `MAX_HISTORY_DAYS`.
- `PruneOldHistoryUseCase` reads the persisted window (first emission of `observeSettings()`) instead of the constant.
- `ChangeHistoryRetentionUseCase` writes, then prunes, and reports `Applied` / `SavedPruneDeferred` / `NotSaved`.

**Rationale** (verified-here):
- `detekt.yml` activates `MagicNumber` with `ignoreConstantDeclaration: true` but does not set `ignoreEnums`, so `Days7(7)` would be flagged; `Days7(BrowserLimits.HISTORY_RETENTION_DAYS_7)` is not. §III is satisfied by construction.
- `HistoryRepository.pruneOlderThan(cutoffMillis): Int` and `clearAll(): Int` already exist (Spec 014), so no DAO or repository change is needed for history itself.
- The History screen observes the table, so rows removed by a prune disappear from an open History screen without extra wiring — which covers the "History further back in the navigation stack" edge case.
- Coordination belongs in a use case: `SettingsRepository` must not depend on `HistoryRepository` (§IV).
- Write-then-prune is analysed in data-model §2.

**Alternatives considered**:
- **Store the enum's name as a string** — viable, but it adds a mapping table and makes the stored value less self-describing, for no gain.
- **Prune in the view-model** — rejected: domain logic belongs in the domain layer, where JVM tests cover it.
- **Swallow a failed prune** — rejected: the user explicitly asked for that history to be deleted, so FR-043 requires telling them.

---

## R9 — Dynamic color persistence and capability

**Decision**:
- Add `booleanPreferencesKey("dynamic_color_enabled")`, defaulting to `true`.
- `MainActivity` passes `settings.isDynamicColorEnabled` to the **existing** `ThirtySixTheme(dynamicColor = …)` parameter.
- The Settings view-model learns whether the device supports dynamic color from an injected Boolean, provided by a Hilt module that evaluates `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`. The qualifier name is a `const val`.

**Rationale** (verified-here):
- `presentation/theme/Theme.kt` already declares `dynamicColor: Boolean = true` and already gates it on API 31, so the theme needs **no** refactor. The current behaviour is exactly "always on", which is why the default is `true` (spec A11).
- JVM unit tests see `Build.VERSION.SDK_INT == 0`, so the view-model cannot read it directly. Injecting a named primitive mirrors the existing `@Named("default_home_url")` binding in `di/UrlConfigModule.kt`.

**Alternatives considered**:
- **A capability interface** — heavier than one Boolean, with no second capability on the horizon.
- **Hide the control by reading the build version inside the composable** — rejected: it would move a presentation decision out of the view-model's testable state.

---

## R10 — Showing the installed version

**Decision**: Enable `android { buildFeatures { buildConfig = true } }` in `app/build.gradle.kts` and show `BuildConfig.VERSION_NAME`, injected into the Settings view-model as a named `String` (the R9 pattern).

**Rationale** (documented):
- AGP 9's `BuildFeatures.buildConfig`: "Flag to enable/disable generation of the BuildConfig class … Default value is false" ([BuildFeatures](https://developer.android.com/reference/tools/gradle-api/9.0/com/android/build/api/dsl/BuildFeatures)). AGP 9.0 removed the `gradle.properties` defaults flag ([AGP 9.0.0 release notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes)), so the module DSL is the only switch — and today it is off (`buildFeatures { compose = true }` only).
- A compile-time field is never null and needs no API-level branching. The runtime alternative needs `getPackageInfo(String, PackageInfoFlags)` on API 33+ and the `int` overload below, and `PackageInfo.versionName` is documented as "null if there was none".
- Constitution §XI names `BuildConfig` fields as a sanctioned home for build-variant values.
- The generated field's name is confirmed only in AGP 9.1.1's generator class, not on a documentation page; if it were absent the build would fail at compile time, so the implementation cannot silently get this wrong.

**Alternatives considered**:
- **`PackageManager` lookup at runtime** — rejected for the branching and nullability above.
- **A version string in resources** — rejected: it drifts from `versionName` in the build script.

---

## R11 — Language chooser labels and locale parity

**Decision**:
- The eight endonyms (English, Tiếng Việt, Deutsch, Русский, 한국어, 日本語, 中文, Français) are string resources marked `translatable="false"` in `values/strings.xml` only. The "Follow system" label is an ordinary translated string.
- The supported tags live in the `AppLanguage` enum.
- A JVM unit test reads `app/src/main/res/xml/locales_config.xml` from the source tree and asserts that its `<locale>` entries equal the enum's tags.

**Rationale** (verified-here):
- FR-014 requires names that are identical whatever the UI language. `translatable="false"` is the lint-sanctioned form for that, so `MissingTranslation` and `ExtraTranslation`, which Spec 004 raised to error severity, stay green; implementation confirms with `lintDebug`.
- `locales_config.xml` currently lists exactly `en vi de ru ko ja zh fr`. Nothing today stops the two lists drifting apart; the parity test does.

**Alternatives considered**:
- **Derive names with `Locale.getDisplayName(locale)`** — rejected: output varies by Android and ICU version, including casing (the Vietnamese endonym comes back lowercase), so the list would render inconsistently across devices.
- **Inline string literals in the composable** — rejected by §III.

---

## R12 — Keeping the displayed language fresh

**Decision**: The Settings view-model re-reads the platform language through `GetAppLanguageUseCase` every time the Settings screen enters the STARTED state, not only when the view-model is created.

**Rationale** (verified-here):
- A language change — from the in-app chooser or from Android 13+ system settings — recreates the activity, but the navigation back-stack entry and its view-model survive recreation. A value read once at creation would therefore go stale exactly in the FR-016 scenario.
- `AppNavGraph` uses `rememberNavController()`, whose back stack is saved and restored across recreation, so the user comes back to the Settings screen after changing the language rather than being dropped at the browser. This is also confirmed on device in **G4**.

**Alternatives considered**:
- **Derive the value from the composition's current configuration** — rejected: it would tie a domain value to a UI locale object and bypass the seam FR-042 requires.

---

## R13 — Files and tests from earlier specs touched

**Decision**: Accept and enumerate the ripple. Specifically:

| Change | Production files | Test files |
|---|---|---|
| `FaviconCache.clearAll()` (new abstract member) | `DiskFaviconCache` | **10** doubles — androidTest: `BrowserScreenInstrumentedTest`, `BrowserScreenOfflineErrorTest`, `BrowserScreenLoadingIndicatorTest`, `HistoryScreenTestDoubles`; test: `TabsViewModelTest`, `BrowserViewModelTest`, `BrowserViewModelHistoryRecordSequenceTest`, `BrowserViewModelIncognitoCacheGateTest` (no-op and recording doubles), `BrowserViewModelStarToggleTest`, `HistoryViewModelTest` |
| `CookieJarSnapshotManager.discardSetAsideCookies()` | `CookieJarSnapshotManagerImpl` | `FakeCookieJarSnapshotManager` |
| `SettingsRepository` members added and removed | `SettingsRepositoryImpl`, `SettingsDataStore`, `SettingsMapper` | the five files in R4 |
| `UserSettings` shape | `UserSettings`, `SettingsMapper`, `MainActivity` | via the files above |
| `PruneOldHistoryUseCase` gains a dependency | `PruneOldHistoryUseCase` | `PruneOldHistoryUseCaseTest` |
| Overflow menu gains Settings | `BrowserOverflowMenu`, `BrowserOverflowMenuCallbacks`, `BrowserScreen` | `BrowserOverflowMenuTest` (androidTest) — the only test that constructs the callbacks |
| Settings route gains a nav controller | `AppNavGraph` | — (no test references `SettingsScreen` or `AppDestination.Settings`) |
| Placeholder string retired (FR-040) | `settings_screen_placeholder` in all 8 `values*/strings.xml` | — |

No test references `MainActivity`, so its base-class change (R2) ripples into no test file.

The Hilt test host `HiltTestActivity` lives in the **debug** source set and extends `ComponentActivity`. The Settings screen's Compose tests do not need AppCompat, so it stays as it is. Real per-app language switching below API 33 would need an `AppCompatActivity` host, so it is proven by manual gate **G4** on both AVDs rather than by an instrumented test.

**Rationale**: an honest list lets the tasks and the PR review cover every touched file from Specs 006, 011, 012, 014 and 015.

**Alternatives considered**:
- **Give `clearAll()` a default no-op body in the interface** to avoid editing ten test doubles — rejected: a future production implementor could then silently skip clearing site icons, which is a privacy defect that nothing would catch.

---

## R14 — Reusable choosers for Spec 018

**Decision**: `ThemeModeChooserDialog`, `AppLanguageChooserDialog` and `SearchEngineChooserDialog` are **stateless** composables taking `selected`, `onSelect` and `onDismiss`, built on one private single-choice dialog, in `presentation/settings/components/`. No onboarding code is written.

**Rationale**: FR-037 asks for reusability, and Constitution §X forbids speculative code. Stateless composition delivers the first without violating the second: Spec 018 can call these composables with its own state, and nothing in this spec anticipates how onboarding will look.

**Alternatives considered**:
- **A shared `presentation/common/` package now** — rejected as speculative structure; it can move when a second consumer actually exists.

---

## R15 — Seeding data for the SC-009 gate

**Decision**:
- Reuse the debug-only `HistorySeeder` for the 10,000 history entries.
- Add a debug-only `TabSeeder`, a `BroadcastReceiver` in `app/src/debug/` registered in the debug manifest, that tops the normal-tab count up to 50 — never beyond `BrowserLimits.MAX_TABS`, which is 50, since the app always holds at least one tab — and writes a placeholder preview image and site icon for each, so the cache category has real files to delete.

**Rationale** (verified-here): `app/src/debug/kotlin/.../dev/` contains only `HistorySeeder` and `DownloadSeeder`, so nothing can create 50 tabs today. Living in the debug source set means the class is absent from the release artifact — the precedent Specs 014 and 015 set.

**Consequence for the non-gating hardware figure**: the seeders exist only in debug builds, and the debug build installs under the `.debug` applicationId, so they cannot populate a **release** install. The recorded 3-second measurement (SC-009, non-gating) therefore needs data created through the UI or another path, and quickstart says so rather than implying a debug-build number stands in for it.
