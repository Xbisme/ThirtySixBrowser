# 0022. Two repositories may read other repositories

- Status: accepted
- Date: 2026-10-14

## Context

Repositories do not depend on other repositories or on system services; data from several sources
is joined in a use case. Two cases do not fit that rule without moving platform state into the domain.

## Decision

- `SearchEngineRepositoryImpl` reads `SettingsRepository`: settings are the app-wide configuration,
  read the same way `ObserveUserSettingsUseCase` reads them. Queries are form-encoded
  (`URLEncoder.encode(query, "UTF-8")`, `+` for space).
- `IncognitoSnapshotOrigins` reads tabs, history and fireproof sites at incognito session start to
  build the cookie snapshot set on engines without profiles. Each read is defensive: a failed read
  keeps the others' results and malformed URLs are dropped. `IncognitoTabRepositoryImpl` itself
  depends only on platform-state managers.
- Everything else joins in use cases: downloads join Room records and live platform status in
  `ResolveDownloadStatusUseCase` (0018).

## Consequences

- These two are the only allowed exceptions; a third needs a new ADR.
