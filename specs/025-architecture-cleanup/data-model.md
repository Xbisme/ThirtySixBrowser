# Data Model: Architecture Cleanup

This spec stores no new data. Its "model" is the target package layout and the rules that place each file. User data (Room v5, `thirtysix_settings`, caches, assets) is unchanged — see invariants at the end.

Package root: `com.raumanian.thirtysix.browser` (below as `…`). Source root stays `app/src/main/kotlin/`.

## Target layout

```text
…/
├── ThirtySixApplication.kt, MainActivity.kt
├── navigation/                     AppNavHost (joins features, hosts Fire, fills slots)
├── core/
│   ├── data/
│   │   ├── datasource/local/       AppDatabase, 8 *Dao, migration/, entity/ (8 *Entity), datastore/SettingsDataStore
│   │   └── util/                   SystemMonotonicClock, Android impls of shared contracts (clipboard, favicon cache)
│   ├── domain/
│   │   ├── model/                  Tab, Bookmark, HistoryEntry, SearchEngine, ThemeMode, TextSize, AppLanguage,
│   │   │                           SiteCapability, SiteOrigin, FireState, UserSettings, Max*ReachedException
│   │   ├── repository/             shared contracts (R2): repositories + platform contracts, no Android types
│   │   ├── usecase/                use cases called by ≥ 2 features' screens
│   │   └── util/                   Result, AppError, DispatcherProvider, MonotonicClock, ApplicationScope, url helpers
│   ├── presentation/
│   │   ├── designsystem/theme/     Color, Type, Shape, Spacing, Dimens, Elevation, AppIcons, Theme, ThirtySixColors
│   │   ├── designsystem/component/ ListRow, PillButton, SheetFrame, ConfirmDialog, ChoiceDialog, …, SheetActionRow
│   │   └── util/                   BaseViewModel, SecureWindowEffect, ContextExt, labels for core models,
│   │                               default-browser request launcher, shared messages
│   └── di/                         DatabaseModule, DispatcherModule (+ DefaultDispatcherProvider), ClockModule,
│                                   ClipboardModule, FaviconCacheModule
└── feature/<f>/
    ├── data/
    │   ├── datasource/local/       feature-only platform sources (e.g. downloads gateway impl, protection assets)
    │   ├── mapper/                 Entity ↔ domain
    │   └── repository/             *RepositoryImpl (binds a core or feature contract)
    ├── domain/{model, repository, usecase}   only what this feature alone uses
    │                               (browser also has domain/protection/ for the page-time matchers)
    ├── presentation/
    │   ├── <screen>/               <S>Screen, <S>ViewModel, <S>UiState, existing event types, component/
    │   └── navigation/             route + NavGraphBuilder.<f>Screen() + NavController.navigateTo<F>()
    └── di/                         this feature's Hilt modules
```

Features: `browser`, `tabs`, `bookmarks`, `history`, `downloads`, `settings`, `onboarding`, `privacy`, `sitepermission`, `entrypoint` (ownership in research R1).

Screens per feature (one package each):

| Feature | Screens (`presentation/<screen>/`) |
|---|---|
| browser | `browser` (screen, WebView host, hub, find, page tools; sub-packages `browser/page` for page requests and dialogs and `browser/protection` for the protection engine and dashboard — parts of the browser screen, not screens), `home`, `incognito`, `suggestions` |
| tabs | `tabs` |
| bookmarks | `bookmarks` |
| history | `history` |
| downloads | `downloads` |
| settings | `settings` |
| onboarding | `onboarding` |
| privacy | `fire` (view model, dialog, overlay), `fireproof`, `protectionexceptions` |
| sitepermission | `sitepermissions` |
| entrypoint | `entry` (view model, intent reader, relay) |

## Placement rules (what the checks enforce)

| Type | Home |
|---|---|
| `*ViewModel`, `*UiState` | `..presentation..` |
| `*UseCase` | `..domain.usecase..` |
| `interface *Repository`, abstract `*Repository` | `..domain.repository..` |
| `*RepositoryImpl` | `..data.repository..` |
| `*Dao` | `..data.datasource.local..` |
| `*Entity` | `..data.datasource.local.entity..` |
| `@Module` | `..di..` |
| Domain models | no `Dto`/`Entity`/`Response`/`Request` suffix |

Dependency rules: `presentation → domain ← data`; `di → domain, data`; `domain` imports no `android.*`/`androidx.*`; a feature imports no other feature; `core` imports no feature. Only `MainActivity`, `ThirtySixApplication` and `navigation/` reference several features.

Decision rule for a type used outside its owner: if two or more features use it, it moves to `core/` (research R2); otherwise it stays in its feature.

## Allowlists (temporary)

| Allowlist | Check | Entry | End state |
|---|---|---|---|
| `KNOWN_VIOLATIONS` in `KonsistScope.kt` | Konsist tests | file path suffix | empty |
| `config/detekt/baseline.xml` | detekt (`ForbiddenComment` etc.) | detekt issue id | empty, then file removed |
| ktlint baseline | compose-rules | file + rule | empty, then file removed |

Each feature commit removes its own entries; an entry can only be removed, never added after step 2.

## Invariants (must hold after every commit)

1. `applicationId` `com.raumanian.thirtysix.browser`; debug suffix `.debug`.
2. `AppDatabase` version 5; no migration added. The schema export folder is named after the database class (`app/schemas/com.raumanian.thirtysix.browser.data.local.database.AppDatabase/`), so it is renamed with `git mv` to the new class name in the same commit that moves `AppDatabase`; `1.json`–`5.json` stay byte-identical (they hold no package names — checked).
3. Settings file `thirtysix_settings` and every key string unchanged.
4. Asset paths under `assets/protection/` and `assets/privacy/` unchanged.
5. Navigation route strings unchanged.
6. Manifest component names: `.MainActivity`, `.ThirtySixApplication`; debug: `.HiltTestActivity`, `.dev.*`; broadcast actions unchanged.
7. Cache directories (`cacheDir/favicons/`, `cacheDir/screenshots/`) and file formats unchanged.
8. Hilt module class names used by `@UninstallModules` in tests unchanged.
