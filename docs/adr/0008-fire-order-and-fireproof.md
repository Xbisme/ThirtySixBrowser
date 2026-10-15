# 0008. Fire runs in a fixed order behind a persisted marker

- Status: accepted
- Date: 2026-10-14

## Context

Fire closes every tab and clears browsing data except fireproof sites, bookmarks, downloads and
settings. A run stopped part-way (process death) must not leave data half-cleared, and later steps
depend on data earlier steps would destroy.

## Decision

- `RunFireUseCase` runs behind the process-wide `FireCoordinator`, in the `@ApplicationScope` scope.
- A DataStore marker is written before the first step; `MainActivity` finishes an interrupted run
  before showing any tab.
- The order is load-bearing: hosts to clear are collected → incognito closed → tabs → history →
  cookies and site data → cache → site permissions → favicons (except bookmarked and fireproof
  hosts) → in-memory session traces (fingerprint noise secret, failed HTTPS upgrades).
- Fireproof cookies survive as snapshot → per-site delete → wipe → restore, using
  `CookieManagerCompat.getCookieInfo` (attributes kept) and `WebStorageCompat.deleteBrowsingDataForSite`.
  A host-only cookie is reported with `domain=<host>` and no dot, so the restore drops that attribute.
  Engines without these features take a documented fallback.
- Incognito ends before any cookie is read, so no incognito cookie can be kept for a fireproof site;
  tabs close before anything slow, so an interrupted run never brings an old tab back. Each step has
  its own failure boundary and is idempotent, so the whole run can be repeated. A second trigger
  during a run is ignored.
- Secure fireproof cookies are written back through `https`.

## Consequences

- A change to the step order needs a new ADR and the Fire tests on both engine paths.
- `FireStepDelayReceiver` (debug builds) slows every step so a run can be force-stopped part-way.
