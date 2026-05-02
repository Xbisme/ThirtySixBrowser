# Feature Specification: Search Engine Google

**Feature Branch**: `010-search-engine-google`
**Created**: 2026-05-03
**Status**: Draft
**Input**: User description: "Refactor inline `URLEncoder.encode(text, UTF-8)` + `UrlConstants.GOOGLE_SEARCH_URL_TEMPLATE` block in `BrowserViewModel.onAddressBarSubmit` (left over from Spec 009) into a `SearchEngineRepository` at the domain layer + a `BuildSearchUrlUseCase`. Support 3 engines: Google (default), DuckDuckGo, Bing. Engine selection is read from the existing `SettingsRepository` (Spec 006) using the `SearchEngine` enum already present in `domain/model/`. Address-bar UI and `AddressBarInputClassifier` stay unchanged — only the output path of the `Query` branch is replaced (inline encode → repository call). New URL templates for DuckDuckGo and Bing live in `UrlConstants.kt`. Zero new packages expected (pure Kotlin domain logic). 16 KB-safe by construction. Depends on Spec 006 (SettingsRepository + SearchEngine enum) and Spec 009 (AddressBarInputClassifier surface)."

## Clarifications

### Session 2026-05-03

- Q: What are the exact public search-URL templates for DuckDuckGo and Bing that this spec will compile in as `const val`? → A: DuckDuckGo `https://duckduckgo.com/?q=%s` and Bing `https://www.bing.com/search?q=%s` — canonical public endpoints, single `%s` substitution, mobile-friendly UX, no API key / cookie / region pinning required.
- Q: How does `SearchEngineRepository` read the currently-persisted engine from `SettingsRepository` (Spec 006)? → A: Reuse the existing `Flow<UserSettings>` exposed by Spec 006's `ObserveUserSettingsUseCase` / `SettingsRepository`. Inside `buildSearchUrl(query)`, call `.first()` on that flow to obtain a snapshot at submit-time and project the `searchEngine` field. No new API is added to `SettingsRepository`. This keeps the contract Flow-only (project convention), implements the spec's "read-on-submit" semantics for the mid-typing-engine-change edge case, and lets fake test doubles emit a `MutableStateFlow<UserSettings>` without further surface.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Submit a search query with the default engine (Priority: P1)

A user opens the browser for the first time (or after install) and types a free-form query into the address bar (introduced by Spec 009). They press the keyboard's Enter / "Go" action. Because no engine has been explicitly chosen yet, the system uses the default engine (Google) and loads the corresponding search-results URL.

**Why this priority**: This is the user-visible behavior of Spec 009's query-path output, just routed through the new abstraction. P1 because the existing query-submit flow MUST continue to work for every user on first launch — any regression here breaks the most common browser action. This story alone (without US2) already delivers shippable value because it leaves first-time users in a usable state identical to today, with the abstraction wired in for future engine-switching.

**Independent Test**: With a fresh install (no settings written), open the BrowserScreen, tap the address bar, type `kotlin coroutines`, press Enter on the soft keyboard. Verify the WebView loads a Google search results page and the address bar reflects the resolved Google search URL.

**Acceptance Scenarios**:

1. **Given** the app is freshly installed and no engine has been explicitly chosen, **When** the user types `kotlin coroutines` into the address bar and submits, **Then** the system constructs a Google search URL using the project's documented Google search template and the WebView loads it.
2. **Given** any state where Google is the active engine, **When** the user submits a query, **Then** the resolved URL has the structure documented for Google's search endpoint, with the user's query URL-encoded as the search parameter.
3. **Given** the active engine is Google, **When** the user submits a query that is identical to one previously submitted, **Then** the resulting search URL is byte-identical to the prior one (deterministic resolution given the same engine + query input).

---

### User Story 2 — Switch search engines and submit a query with the new engine (Priority: P1)

A user wants to use a privacy-oriented or non-Google engine. The application stores their engine preference (mechanism already provided by Spec 006). Once the preference is recorded as DuckDuckGo (or Bing), every subsequent address-bar query submission is routed to that engine instead of Google. No app restart is required; the change takes effect on the very next query.

**Why this priority**: The *whole point* of this spec is engine selection. Without US2, this spec has no user-visible value beyond an internal refactor. P1 because Spec 016 (Settings screen) — which exposes the engine picker UI — is downstream of this spec; this spec must make the multi-engine query path correct *before* the picker can connect to it. Note that this spec does NOT introduce the picker UI; it only ensures that whichever engine is recorded as the user's preference is honored at query-submit time.

**Independent Test**: Persist the engine preference as DuckDuckGo (via the existing Spec 006 settings write API; UI for flipping it is intentionally out of scope of this spec). Re-enter the BrowserScreen, type `android compose`, submit. Verify the WebView loads a DuckDuckGo search-results URL containing the URL-encoded query. Repeat with Bing as the persisted engine.

**Acceptance Scenarios**:

1. **Given** the persisted engine preference is DuckDuckGo, **When** the user submits the query `android compose`, **Then** the system constructs the DuckDuckGo search URL per the project's documented template and the WebView loads it.
2. **Given** the persisted engine preference is Bing, **When** the user submits the query `android compose`, **Then** the system constructs the Bing search URL per the project's documented template and the WebView loads it.
3. **Given** the engine preference is changed at runtime from Google to DuckDuckGo (Spec 006 write API; flow does not require an app restart), **When** the user submits a query immediately after the change, **Then** the next query is routed to DuckDuckGo, not Google.
4. **Given** the persisted engine value on disk is unrecognized (e.g., a future build wrote `"yandex"` and the user downgraded), **When** the user submits a query, **Then** the system falls back to the default engine (Google) without crashing or showing an error — same fallback semantics already documented for the `SearchEngine` enum in Spec 006.

---

### User Story 3 — Query with special characters and non-Latin scripts encodes correctly (Priority: P2)

A user types a query containing whitespace, punctuation, and characters outside the ASCII range — for example a Vietnamese phrase, a Japanese phrase, or a query with `?`, `&`, `+`, `#` characters. The query is correctly UTF-8 encoded into the search URL so the engine receives exactly what the user typed.

**Why this priority**: This is correctness, not new functionality. The inline `URLEncoder.encode(text, UTF-8)` block in Spec 009 already handles this — the requirement is that the new abstraction MUST preserve that behavior across all 3 engines. P2 because the happy path (US1) already exercises basic encoding via the Google path; this story adds the explicit guarantee that the abstraction does not regress the encoding contract for non-Latin input or special characters, which matters for the project's 8-locale users (VI, JA, ZH, RU, KO).

**Independent Test**: With each of the 3 engines as the persisted preference, submit each of the following queries: `hello world` (space), `c++ tutorial` (special chars), `cà phê sữa` (Vietnamese), `東京タワー` (Japanese), `красная площадь` (Russian), `weather & forecast` (ampersand). For each (engine × query) pair, verify the loaded URL contains the query encoded as UTF-8 percent-escaped octets and that the engine renders results for the original query (not for a corrupted encoding).

**Acceptance Scenarios**:

1. **Given** the active engine is Google, **When** the user submits `hello world`, **Then** the resolved URL contains `hello%20world` or `hello+world` (whichever encoding form the project's URL-encode helper produces — must be consistent across engines).
2. **Given** the active engine is DuckDuckGo, **When** the user submits `cà phê sữa`, **Then** the resolved URL contains the UTF-8 percent-encoded byte sequence for the Vietnamese phrase, and DuckDuckGo's search page renders results for the phrase as intended.
3. **Given** the active engine is Bing, **When** the user submits `weather & forecast`, **Then** the `&` character is escaped (not interpreted as a query-string separator), and Bing renders results for the literal phrase.
4. **Given** any engine, **When** the user submits a single-character query (`?`), **Then** the character is URL-encoded and the search page loads without error.

---

### Edge Cases

- **First-launch default**: No settings have been written yet. The system MUST treat this as Google selected (the documented default in Spec 006 / `AppDefaults.SEARCH_ENGINE`), not as "no engine" or as an error.
- **Settings read fails transiently** (rare — DataStore IO error): The system MUST fall back to the default engine (Google) for that submit and not crash. A subsequent submit re-reads and may succeed.
- **Engine preference changes mid-typing**: The user has typed a query but not submitted; meanwhile the persisted preference flips (Spec 016 picker scenario). On submit, the engine in effect is the one persisted at submit time, not the one persisted when the user started typing. (Standard "read-on-submit" semantics; no need to invalidate in-flight typing.)
- **Whitespace-only or empty submit**: Already handled by Spec 009 (FR-012) as a no-op before the query path is reached. This spec inherits that behavior unchanged — the search-URL builder is never invoked for an empty input.
- **Extremely long query** (e.g., 4000 characters): URL-encoded value may exceed conservative URL length limits. The WebView itself enforces its own limit; this spec does NOT pre-truncate. If the engine's server returns an error, the existing Spec 007 error UI handles display.
- **Engine template unexpectedly missing or malformed at runtime**: Should not be possible because templates are compiled-in `const val`s, but the contract states that if the resolved template ever fails substitution, the system falls back to the Google template (defensive — keeps the user able to search even after a future refactor mistake).
- **Unicode-only query** (e.g., `🍕 pizza near me`): Emoji + space + ASCII; UTF-8 encoding handles emoji bytes, engine support varies but the URL itself is well-formed.

## Requirements *(mandatory)*

### Functional Requirements

#### Search engine model

- **FR-001**: System MUST extend the existing `SearchEngine` enum (introduced in Spec 006, currently single-entry `Google`) to include two additional entries: `DuckDuckGo` and `Bing`. Each entry retains a stable on-disk `storageValue` string so the existing settings persistence schema continues to work without migration.
- **FR-002**: Each `SearchEngine` entry MUST be associated with a search-URL template (a string with a single `%s` substitution slot) that resolves to a valid HTTPS search-results URL when the encoded query is substituted. Templates MUST live in `core/constants/UrlConstants.kt` per Constitution §III No-Hardcode Rule.
- **FR-003**: The default engine when no preference has been persisted MUST remain Google (already documented in `AppDefaults.SEARCH_ENGINE` per Spec 006). This spec does NOT change the default.
- **FR-004**: When an unknown / unrecognized `storageValue` is read from disk (forward-compat / downgrade scenario), the system MUST fall back to the default engine. This semantics already exists in Spec 006's `fromStorageValueOrDefault(...)` and MUST be preserved unchanged.

#### Domain abstraction

- **FR-005**: System MUST introduce a domain-layer interface (working name `SearchEngineRepository`) responsible for: (a) exposing the currently-selected engine as a value or observable; (b) constructing a fully-formed search URL for a given trimmed query string under the currently-selected engine.
- **FR-006**: The domain abstraction MUST live in the `domain/repository/` package per the project's Clean Architecture rules. Its concrete implementation lives in `data/repository/` and depends on the existing `SettingsRepository` (Spec 006) for the engine preference. The implementation MUST NOT depend on any other repository — only on `SettingsRepository` per Constitution §IV.
- **FR-007**: System MUST introduce a use case (working name `BuildSearchUrlUseCase`) in `domain/usecase/` that wraps the search-URL construction call. `BrowserViewModel` MUST invoke this use case rather than the repository directly, consistent with the project's existing use-case-per-action pattern (Specs 005/006).
- **FR-008**: The use case MUST accept a single argument — the trimmed, non-empty query string — and return a single result — the fully-formed search URL string. It MUST NOT accept the engine as an argument; engine selection is internal to the abstraction.
- **FR-009**: The use case MUST be a `suspend` function (or expose a `suspend` invoke operator) so that the underlying settings read can be awaited safely off the main thread, matching the rest of the project's coroutine-based domain layer.

#### Address-bar integration

- **FR-010**: `BrowserViewModel.onAddressBarSubmit` MUST be modified so that when the input classifier returns a `Query` result, the ViewModel invokes the new use case to obtain the search URL, instead of inlining `URLEncoder.encode(...)` + `String.format(GOOGLE_SEARCH_URL_TEMPLATE, ...)`.
- **FR-011**: The classifier surface (`AddressBarInputClassifier` and the sealed `AddressBarSubmitResult`) introduced in Spec 009 MUST remain unchanged. This spec touches only the post-classification handling of the `Query` branch.
- **FR-012**: The `URL` branch of the classifier (when input is a URL with or without scheme) MUST remain unchanged — it does NOT route through the new abstraction. Search engines apply only to free-form queries.
- **FR-013**: The address-bar Composable, its UI state, focus / keyboard / clear-button behavior, hostname-only display, and rotation persistence (all from Spec 009) MUST remain unchanged. Visual and interaction behavior is identical to Spec 009 from the user's perspective when Google remains the engine.

#### URL construction contract

- **FR-014**: The query string passed to URL construction MUST be URL-encoded as UTF-8 before substitution into the engine template. The encoding produced for a given input MUST be consistent across all 3 engines (i.e., the abstraction owns the encoding, not the per-engine code path).
- **FR-015**: The constructed URL MUST be a valid absolute HTTPS URL for every supported engine. No HTTP-only or scheme-relative templates are permitted.
- **FR-016**: System MUST construct the search URL for `Google` queries using the project's documented Google search template (the existing `UrlConstants.GOOGLE_SEARCH_URL_TEMPLATE`). The byte-for-byte output for a given encoded query MUST be identical to what Spec 009's inline implementation produced — this spec is a non-regression refactor of the Google path.
- **FR-017**: System MUST construct the search URL for `DuckDuckGo` queries by substituting the URL-encoded query into the constant `https://duckduckgo.com/?q=%s` (added in `UrlConstants.kt` as `DUCKDUCKGO_SEARCH_URL_TEMPLATE`, single `%s` slot). The resolved URL MUST land on DuckDuckGo's canonical mobile-friendly search-results page for the query.
- **FR-018**: System MUST construct the search URL for `Bing` queries by substituting the URL-encoded query into the constant `https://www.bing.com/search?q=%s` (added in `UrlConstants.kt` as `BING_SEARCH_URL_TEMPLATE`, single `%s` slot). The resolved URL MUST land on Bing's standard search-results page for the query.

#### Out-of-scope (explicitly deferred)

- **FR-019**: The settings-screen UI for selecting the engine is OUT OF SCOPE for this spec; it is Spec 016's responsibility. This spec only ensures the read-side path is correct so Spec 016 can connect a picker without further domain changes.
- **FR-020**: Search suggestions / autocomplete (per-engine APIs like Google's suggest endpoint) are OUT OF SCOPE; deferred per the same reason as Spec 009 FR-028.
- **FR-021**: Region-specific search domains (e.g., `google.co.jp`, `bing.com.cn`) are OUT OF SCOPE; templates use the global default domains. Locale-aware search is a future enhancement.
- **FR-022**: Custom user-defined engines (user pastes a search-URL template) are OUT OF SCOPE; only the 3 fixed engines are supported in v1.0.
- **FR-023**: Encrypted-query / "incognito" behavior is OUT OF SCOPE; queries are sent as plain HTTPS GET parameters per each engine's standard behavior. Spec 012 will handle incognito mode separately.

### Key Entities *(include if feature involves data)*

This spec introduces no new persistent storage and no new database tables. It introduces:

- **SearchEngine (extended)**: The existing Spec 006 enum, augmented with two new entries (`DuckDuckGo`, `Bing`). Each entry retains a stable `storageValue` string used by `SettingsRepository` to round-trip the user's preference through DataStore. No schema migration is required because DataStore Preferences keys are string-typed and the existing `fromStorageValueOrDefault(...)` already absorbs unknown values into the default.
- **SearchEngineRepository contract**: Domain-layer interface that owns search-URL construction. Stateless from the caller's perspective; reads engine preference internally via `SettingsRepository`. No persistence of its own.
- **BuildSearchUrlUseCase contract**: Domain-layer use case; thin wrapper over the repository's URL-construction call, providing the use-case-per-action shape the rest of the project's domain layer uses.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: With Google as the active engine, the URL produced by the new abstraction for any query is byte-identical to what Spec 009's inline implementation produced. Verifiable by a parameterized unit test covering at least 10 representative queries (ASCII, mixed-case, whitespace, punctuation, Vietnamese, Japanese, Chinese, Russian, Korean, emoji).
- **SC-002**: After the user changes the persisted engine preference, the very next address-bar query submission routes to the new engine. No app restart is required. Verifiable by a unit test that mutates the fake `SettingsRepository`'s emitted value and asserts the next URL produced uses the new template.
- **SC-003**: Address-bar query submission for any of the 3 engines produces a valid HTTPS URL that, when loaded, delivers a usable search-results page in 100% of online cases. Verifiable manually on a real device for the 3 engines × 3 representative queries (English, Vietnamese, Japanese) — 9 manual verifications. Offline cases are handled by Spec 007's existing error UI (out-of-scope).
- **SC-004**: All 102 existing unit tests from Specs 005/006/007/008 + the 41 new tests from Spec 009 (143 total before this spec) MUST continue to pass after this spec ships. Plus at least 12 new unit tests added by this spec covering: 3 engines × 4 query shapes (ASCII, special chars, non-Latin, empty-after-trim guard) + the unknown-`storageValue` fallback test + the `BuildSearchUrlUseCase` happy-path test.
- **SC-005**: APK release size delta vs the Spec 009 baseline (2.05 MB) is at most +20 KB. Realistic budget — only 2 new `const val` strings, 1 new domain interface, 1 new repository implementation, 1 new use case, and 2 new enum entries. No new packages and no new resources.
- **SC-006**: 16 KB CI alignment gate continues to pass: every native library entry in the release APK aligns to `0x4000` or larger. No new `.so` files introduced (zero new packages).
- **SC-007**: Static analysis stays green — `lintDebug` zero warnings, `detekt` baseline UNCHANGED from Spec 009, `ktlintCheck` zero violations.
- **SC-008**: Constitution Check 11/11 PASS pre and post implementation.
- **SC-009**: No persisted-data migration is required. A user upgrading from a Spec 009 build (with `search_engine = "google"` already on disk) MUST see no behavior change after upgrade — same engine, same URLs, same UX. A user who has never touched the setting (key absent from DataStore) MUST also see Google by default. Verifiable by a fresh-install integration check + a settings-already-written integration check.
- **SC-010**: The `BuildSearchUrlUseCase` returns a result for any non-empty trimmed query in under 50 ms on a Pixel 5+-class device, including the DataStore read for the engine preference. (Realistic: DataStore reads are sub-millisecond after first read, and URL construction is a single string operation.)

## Assumptions

- **A1**: The `SearchEngineRepository` reads the currently-persisted engine via the existing `Flow<UserSettings>` exposed by Spec 006 (no new API is added to `SettingsRepository`). Inside `buildSearchUrl(query)` it calls `.first()` on the flow to obtain a snapshot at submit-time and projects the `searchEngine` field. This shape was confirmed in the 2026-05-03 clarification session and implements the spec's read-on-submit semantics. The repository depends only on `SettingsRepository` per Constitution §IV — no DAO or DataStore access.
- **A2**: The existing `BrowserViewModel.onAddressBarSubmit` is the right call site for the use-case invocation. No new ViewModel is introduced. The signature change to the query branch is self-contained — no impact on the URL branch, no impact on `WebViewActionsHandle`, no impact on the `AddressBar` Composable.
- **A3**: The DuckDuckGo and Bing search-URL templates were confirmed in the 2026-05-03 clarification session as `https://duckduckgo.com/?q=%s` and `https://www.bing.com/search?q=%s` respectively. Both are global-domain canonical public search endpoints with a single `%s` substitution slot, do not require API keys, cookies, or region pinning, and render the engine's standard mobile UX. These literals are added as `const val`s in `UrlConstants.kt` per FR-017 / FR-018.
- **A4**: Adding two enum entries to `SearchEngine` is a non-breaking change to Spec 006's persistence contract because `fromStorageValueOrDefault(...)` already handles unknown values gracefully. No DataStore migration is required.
- **A5**: The URL-encoding helper used by the new abstraction will produce the same encoding (`URLEncoder.encode(text, "UTF-8")`) as Spec 009's inline path, so the Google-engine output is byte-identical post-refactor. SC-001's test enforces this.
- **A6**: The existing `BrowserViewModelTest` happy-path tests for query submission (Spec 009) will be updated to inject a fake `BuildSearchUrlUseCase` returning the same Google URL they previously asserted. This is a pure-test-side change with no production-code regression.
- **A7**: `BuildSearchUrlUseCase` ships as a `class` with `@Inject constructor` per the project's existing use-case pattern (Spec 006), bound by Hilt via the existing `domain/usecase/` discovery rules — no new Hilt module, no new `@Module`/`@Binds` lines beyond what one new repository binding requires.
- **A8**: The new repository interface is bound to its implementation via a NEW Hilt module file `app/.../di/SearchEngineModule.kt` (sibling to `SettingsModule.kt`). Locked in `/speckit-plan` research R6 — one `@Binds` line, `@InstallIn(SingletonComponent::class)`, `@Singleton` scope matching `SettingsRepository`. No new Hilt component, no scope change.
- **A9**: No new strings, no new resources, no new locales, no new permissions, no new manifest entries. The user-visible behavior surface is identical to Spec 009 when Google is the engine; the picker UI for switching is Spec 016's job.
- **A10**: Test coverage approach mirrors Spec 006: pure-JVM unit tests with a fake/in-memory `SettingsRepository`. No Robolectric, no instrumented tests required for this spec (Spec 016 will exercise the engine-picker flow end-to-end on device).
