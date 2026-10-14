# 0024. Browsing data is excluded from backup and device transfer

- Status: accepted
- Date: 2026-10-14

## Context

Auto Backup and device-to-device transfer copy app data by default. Bookmarks, history and tabs are
private; settings are harmless and worth restoring.

## Decision

- `backup_rules.xml` and `data_extraction_rules.xml` exclude the Room database and its WAL and SHM
  files.
- Settings (DataStore under `files/`) stay in through the default. No `<include>` line is added:
  any `<include>` flips the rules to exclude-by-default.

## Consequences

- A new database or private file needs its own exclude line in both files.
