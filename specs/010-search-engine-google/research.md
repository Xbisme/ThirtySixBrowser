# Research: Search Engine Google (Spec 010)

**Date verified**: 2026-05-03
**Branch**: `010-search-engine-google`

## R1 — DuckDuckGo + Bing canonical search-URL templates

**Decision**: DuckDuckGo `https://duckduckgo.com/?q=%s`, Bing `https://www.bing.com/search?q=%s`. Confirmed via 2026-05-03 clarification session Q1.

**Rationale**: Both forms are the documented public, mobile-friendly canonical search endpoints with single-parameter substitution semantics, mirror what the user types into the address bar (`<host>/<search-path>?q=<query>`), do not require an API key / cookie / region pinning, and accept UTF-8 percent-encoded queries identically to Google. DuckDuckGo's privacy positioning is honored by the canonical `/?q=` form (the user-facing search page), not the lite `/lite/?q=` or HTML `/html/?q=` variants which are intended for low-bandwidth or scraping use.

**Alternatives considered**:
- DuckDuckGo `/html/?q=%s` (no-JS variant) — rejected: degraded UX vs the canonical landing page; intended for scrapers.
- DuckDuckGo `/lite/?q=%s` — rejected: ultra-low-bandwidth variant, not the standard mobile experience.
- Adding `safe-search` / `safesearch` defaults (`&kp=1` for DuckDuckGo, `&safeSearch=Strict` for Bing) — rejected: not a v1.0 requirement; users keep whatever each engine's own server-side default is. If safe-search becomes a feature it gets its own spec (Spec 016 candidate).
- Region-specific domains (e.g., `bing.com.cn`, `google.co.jp`) — explicitly out of scope per spec FR-021.
- Bing `cn.bing.com/search?q=%s` — rejected: locale routing belongs to Bing's own server-side handling.

**16 KB compliance**: N/A — string constants, not native libs.

## R2 — Read-side shape from `SettingsRepository`

**Decision**: Reuse `SettingsRepository.observeSettings(): Flow<UserSettings>` (Spec 006). Inside `SearchEngineRepositoryImpl.buildSearchUrl(query)`, call `.first()` on the flow and project the `searchEngine` field. Confirmed via 2026-05-03 clarification session Q2.

**Rationale**: Spec 006 already exposes a single Flow-only read API on `SettingsRepository` (`observeSettings(): Flow<UserSettings>`). Adding a second per-field getter (`suspend fun getSearchEngine(): SearchEngine` or `val searchEngine: Flow<SearchEngine>`) would expand the interface without need — the existing flow already emits a coherent snapshot on every key change (Spec 006 FR-010). `.first()` produces the read-on-submit semantics the spec's edge-case bullet specifies for "engine preference changes mid-typing", and the underlying `Preferences` read is sub-millisecond once the DataStore is warm. Test doubles only need to expose a `MutableStateFlow<UserSettings>` to drive the engine choice in tests (no extra fake methods).

**Alternatives considered**:
- Add `suspend fun getSearchEngine(): SearchEngine` to `SettingsRepository` — rejected: breaks the project's "Flow-only read side" convention established by Spec 006; adds a second API surface that future settings would have to mirror; adds a method that test fakes must implement.
- Add `val searchEngine: Flow<SearchEngine>` projection — rejected: encourages the consumer to subscribe + cache, which would need cache-invalidation logic and contradicts the read-on-submit semantics.
- Cache the engine in `SearchEngineRepositoryImpl` (`@Volatile var current: SearchEngine`) — rejected: stale cache after Spec 016 picker writes; introduces a synchronization concern; gains no measurable performance over `Flow.first()` (which the underlying `DataStore` already serves from an in-process state, not from disk on every read).

**16 KB compliance**: N/A.

## R3 — URL-encoding form for spaces (Spec 009 → 010 byte-identity)

**Decision**: `java.net.URLEncoder.encode(text, "UTF-8")` (the JDK helper). Same call Spec 009 uses inline. Encoding lives once inside `SearchEngineRepositoryImpl.buildSearchUrl`; applied uniformly across the 3 engines per FR-014.

**Rationale**: SC-001 mandates byte-identical Google output post-refactor. `URLEncoder.encode("hello world", "UTF-8")` returns `"hello+world"` (form-encoded). All 3 target engines accept form-encoded query strings on their search endpoints (verified across each engine's documented public surface). Routing the encoding through a single point keeps the contract uniform — FR-014 explicitly forbids per-engine encoding code paths.

**Alternatives considered**:
- `Uri.encode(text)` (Android) — rejected: produces `%20` for spaces, NOT form-encoded; would diverge from Spec 009's bytes (SC-001 fail).
- `URLEncoder.encode(text, StandardCharsets.UTF_8)` (Kotlin/JDK 10+ overload) — equivalent at runtime; rejected only because Spec 009 already uses the `String name` overload (`Charsets.UTF_8.name()`) and SC-001's byte-identity goal is easier to satisfy with a literal copy of the existing call. Could be safely flipped if future code-style sweep prefers it.
- Custom percent-encoder — rejected: YAGNI.

**16 KB compliance**: N/A — JDK API.

## R4 — Should engine selection be exposed as `Flow<SearchEngine>` for UI consumers?

**Decision**: NO. UI consumers (Spec 016 picker) read engine state directly from `SettingsRepository.observeSettings()` via the existing `ObserveUserSettingsUseCase`. `SearchEngineRepository`'s public surface is intentionally narrow — `suspend fun buildSearchUrl(query: String): String` only.

**Rationale**: Avoids a "split-brain" — two sources of truth for the engine. `SettingsRepository` owns the state; `SearchEngineRepository` is purely an action (translate `(engine, query) → url`). Spec 016's picker UI already has the right read-side wiring via Spec 006; this spec adds zero surface for that consumer.

**Alternatives considered**:
- Expose `engine: Flow<SearchEngine>` on the new repo — rejected: duplicates Spec 006's surface; risks divergence on cache write delays.
- Make `SearchEngineRepository` a stateful singleton holding `engine: StateFlow<SearchEngine>` — rejected: adds state, requires init logic, conflicts with the "translate-only" mental model.

## R5 — Repository → Repository concern (Constitution §IV)

**Decision**: `SearchEngineRepositoryImpl` depends on `SettingsRepository`. Documented as an accepted exception with reviewer-visible rationale.

**Rationale**: Constitution §IV's "Repositories MUST NOT depend on other Repositories" rule is intended to forbid cross-feature entanglement (e.g., `BookmarkRepository` reaching into `HistoryRepository` for cross-cutting joins) which would violate the layer's single-responsibility intent. `SettingsRepository` is a project-wide configuration backbone — depending on it is structurally identical to depending on `DispatcherProvider` or any other core service. Spec 006 itself already exposes a use case (`ObserveUserSettingsUseCase`) over `SettingsRepository`, signaling its role as a system-wide read service rather than a feature-scoped repo. Rather than rewrite Constitution §IV mid-spec to formalize the exception (a v1.3.0 candidate, NOT this spec's job), we document the dependency in Complexity Tracking and reuse Spec 006's already-approved precedent.

**Alternatives considered**:
- Pass the engine as an argument to `buildSearchUrl(query, engine)` and have the use case + ViewModel resolve the engine themselves — rejected: leaks settings/persistence concerns into `BrowserViewModel`, defeating the abstraction. The whole point of `BuildSearchUrlUseCase` is that the caller doesn't know or care about engine selection.
- Inject `DataStore<Preferences>` directly into `SearchEngineRepositoryImpl` — rejected: bypasses the repository pattern entirely (Constitution §IV violation), double-couples to DataStore types, breaks substitutability for tests.
- Move the engine-resolution logic into the use case (`BuildSearchUrlUseCase` injects both `SettingsRepository` and a stateless `SearchUrlBuilder`) — viable alternative; rejected for symmetry with the project's established "thin use case → repository" pattern (Spec 006: `ObserveUserSettingsUseCase`, all 5 setters wrap a single repository call). Re-evaluable in Spec 016 if engine resolution needs to grow more logic.
- Promote settings to a `domain/service/` layer to make it not-a-repository — rejected: scope creep; would require renaming Spec 006 surface; no immediate benefit.

**16 KB compliance**: N/A.

## R6 — Hilt module placement

**Decision**: New file `app/.../di/SearchEngineModule.kt`. Single `abstract class` with `@Binds @Singleton fun bindSearchEngineRepository(impl: SearchEngineRepositoryImpl): SearchEngineRepository`. `@InstallIn(SingletonComponent::class)`.

**Rationale**: Sibling to existing `SettingsModule.kt` (Spec 006). One module per feature surface keeps the discoverability pattern reviewers established. `@Singleton` matches `SettingsRepository`'s lifetime — no scope mismatch, no per-call instance allocation.

**Alternatives considered**:
- Add the `@Binds` to `SettingsModule.kt` — rejected: bloats Spec 006's module with cross-feature concern.
- Use `@Provides` factory function in an `object` module — equivalent at runtime; `@Binds` is preferred when no constructor-arg massaging is needed (Hilt best practice, less generated code).
- `@ViewModelScoped` instead of `@Singleton` — rejected: state-free repository; per-ViewModel allocation is wasteful and inconsistent with `SettingsRepository`.

## R7 — `suspend` vs eager URL construction in `onAddressBarSubmit`

**Decision**: `BuildSearchUrlUseCase.invoke(query)` is `suspend`. `BrowserViewModel.onAddressBarSubmit(loadUrl: (String) -> Unit): Boolean` keeps its synchronous `Boolean` return value (the Composable's focus/keyboard release decision per Spec 009 FR-013a) and dispatches the URL build + load to a `viewModelScope.launch { ... }` coroutine for the Query branch. The URL branch (which doesn't read settings) stays fully synchronous as it is in Spec 009.

**Rationale**:
- The Composable cannot be made `suspend` cheaply — its `KeyboardActions(onGo = { vm.onAddressBarSubmit { handle.loadUrl(it) } })` callback is invoked from a non-suspend context.
- Spec 009 FR-013a requires focus + keyboard release to happen *before* navigation begins. Returning `Boolean` synchronously (based on whether classification yielded a non-empty result) lets the Composable release IME state immediately while the actual URL build happens in the background.
- Coroutine launch overhead is negligible (microseconds); user perception is unaffected since the IME release is the visible event.
- Cancellation: a fresh submit while a previous coroutine is in flight does not need to cancel the previous — both will eventually call `loadUrl(...)` and the WebView's own cancellation (FR-013) handles the race. If load reordering becomes an issue we can add `Job.cancel()` of the previous launch, but not in v1 (YAGNI).

**Alternatives considered**:
- Make `onAddressBarSubmit` `suspend` — rejected: cascading change to the Composable callback signature, significant Spec 009 churn.
- Make `BuildSearchUrlUseCase.invoke` non-`suspend` by caching the engine in `SearchEngineRepositoryImpl` — rejected per R2 (cache invalidation problem).
- Use `runBlocking` inside the synchronous `onAddressBarSubmit` — rejected: blocks the main thread on a DataStore read during keystroke handling, Constitution §V violation.

## R8 — Test strategy (fake `SettingsRepository`)

**Decision**: Hand-rolled fake class `FakeSettingsRepository : SettingsRepository` backed by a `MutableStateFlow<UserSettings>(UserSettings.DEFAULT)`. Setters update the flow; `observeSettings()` returns the flow. Pattern reused from Spec 006's `SettingsRepositoryImplTest` test scaffolding.

**Rationale**: No mocking framework dependency required (matches the project's existing convention — MockK is on the classpath but not used in the data layer per Spec 006 precedent). Easy to extend per test by writing setters directly. `kotlinx-coroutines-test` covers the suspend assertion side.

**Alternatives considered**:
- MockK / Mockito-Kotlin — rejected: heavier than necessary for a 4-method interface; project convention prefers fakes for Repository tests.
- In-memory subclass of `SettingsRepositoryImpl` with overridden DataStore — rejected: needs a real DataStore wiring that the test pyramid intentionally avoids at this level.

## R9 — Should `SearchEngine` carry the URL template directly?

**Decision**: NO. `SearchEngine` enum entries carry only the on-disk `storageValue: String`. Mapping enum → template is a `when` expression inside `SearchEngineRepositoryImpl`.

**Rationale**: Constitution §III's No-Hardcode Rule, URL row, mandates `core/constants/UrlConstants.kt` as the home for "URLs (default home, search)". Embedding the template into the enum would put a `const val` URL inside `domain/model/SearchEngine.kt` — the reviewer-friendly `UrlConstants` file would then no longer be the single source of truth for search URLs. Trade-off: ~3 extra lines in `SearchEngineRepositoryImpl` (the `when` expression) vs a clear violation of the documented file mapping. The mapping wins.

**Alternatives considered**:
- Add `val searchUrlTemplate: String` parameter to enum entries — rejected per the No-Hardcode Rule rationale above.
- Map via a top-level `Map<SearchEngine, String>` in `SearchEngineRepositoryImpl` — equivalent semantics; rejected for being slightly less ergonomic than `when` (compiler doesn't statically check map-coverage; `when` does for sealed hierarchies and exhaustive enum matches).

## R10 — Spec 016 readiness check

**Decision**: This spec leaves Spec 016 (`settings-screen`) unblocked.

**Rationale**: Spec 016 will need to (a) display the currently-selected engine — already available via `ObserveUserSettingsUseCase().searchEngine` field (Spec 006), (b) write the user's choice — already available via `SettingsRepository.setSearchEngine(...)` (Spec 006), (c) localize the picker UI — Spec 016's own concern. Nothing about Spec 010's domain layer prevents Spec 016 from connecting a picker.

**Alternatives considered**:
- Pre-emptively expose the current engine as `Flow<SearchEngine>` from `SearchEngineRepository` — rejected per R4 (split-brain risk).
- Pre-emptively localize engine display names (e.g., string keys for `Google`, `DuckDuckGo`, `Bing`) — rejected: these are proper-noun product names, not user-facing copy that varies per locale; Spec 016 will decide whether to add `engine_name_*` keys or just render the enum's `storageValue.replaceFirstChar { it.uppercase() }`.
