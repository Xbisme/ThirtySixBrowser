# 0014. Schema changes are additive migrations; older builds are never installed over newer

- Status: accepted
- Date: 2026-10-14

## Context

The database is at version 5 after four additive migrations. A downgrade has no migration, so an older
build installed over a newer one crashes at start.

## Decision

- Every schema change ships an explicit additive migration and the exported `app/schemas/.../N.json`.
- `fallbackToDestructiveMigration` is never used.
- Never install an older build over a newer one on a device with data (test devices included).
- A DataStore key that has shipped is never reused for another meaning or value type: a change adds a
  new key, migrates once and keeps the old key `@Deprecated` for one release (`SettingsMapperTest`).

## Consequences

- `AppDatabaseMigrationTest` drives the real `Room.databaseBuilder` path on the JVM, so a drifted
  migration fails unit tests.
