# Data Model: Browser View Model Split

No stored data changes (FR-002). The entities are in-memory shapes.

## CurrentPage (domain model, `feature/browser/domain/model/`)

| Field | Type | Written when | Initial |
|---|---|---|---|
| `url` | `String` | page reports `UrlChanged` / `LoadFinished`; active tab emits; Back-without-history (Home URL) | injected default home URL |
| `title` | `String` | `TitleReceived`; cleared on `LoadStarted`, tab switch, Back-without-history | `""` |
| `activeTab` | `Tab?` | the active-tab flow emits | `null` |
| `isIncognito` | `Boolean` | the incognito flow emits | `false` |
| `generation` | `Long` | +1 on every `LoadStarted` and every change of active tab id | `0` |

## CurrentPageRepository (domain contract, `feature/browser/domain/repository/`)

| Member | Meaning |
|---|---|
| `page: StateFlow<CurrentPage>` | the one source every browser holder reads |
| `setUrl(url)`, `setTitle(title)`, `setActiveTab(tab)`, `setIncognito(incognito)`, `startNewPage()` | synchronous writes; `startNewPage` bumps `generation` and clears the title |

Implementation `CurrentPageRepositoryImpl` (`feature/browser/data/repository/`), bound `@ActivityRetainedScoped` in `feature/browser/di/`.

## Holders, states, actions, events

See [research.md § R3](research.md#r3-the-holders). UI state types:

| Type | Fields |
|---|---|
| `PageUiState` | `currentUrl`, `loadingState`, `canGoBack`, `canGoForward`, `isIncognito` |
| `AddressBarUiState` | `text`, `isFocused`, `homeOverlay`, `cameFromHome`, `isHomeContent` |
| `PageActionsUiState` | `isBookmarked`, `isDesktopMode` |
| `FindInPageState` | unchanged |
| `BrowserUiState` | screen-level value assembled at `BrowserRoute` from the three above; same fields as today |

## Invariants

- One history row per finished navigation, title patched later; none in incognito; none for a failed load.
- The find bar closes whenever `generation` changes.
- Desktop mode is per tab id, in memory, read once when a tab's WebView is built.
- No holder receives a data source, DAO, entity or `*RepositoryImpl`.
