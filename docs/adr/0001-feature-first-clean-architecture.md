# 0001. Feature-first Clean Architecture with MVVM

- Status: accepted
- Date: 2026-10-14

## Context

Specs 001–024 grew the app layer by layer: one `domain/usecase/` folder with 82 use cases, one `di/`
with 17 modules, every DAO and entity in shared folders. Finding everything about one feature meant
searching the whole tree, and nine use cases in `domain/` imported `data/`. The owner's Android
template (cleanarch, 10/2026) packages by feature, then by layer, as android-showcase, Now in
Android and architecture-samples do.

## Decision

- Code lives in `core/` or `feature/<name>/{data, domain, presentation, di}`; `navigation/`,
  `MainActivity` and `ThirtySixApplication` are the only code that joins features.
- The ten features are browser (with Home and the incognito start page, which an empty tab shows
  inside the browser screen), tabs, bookmarks, history, downloads, settings, onboarding, privacy,
  sitepermission and entrypoint.
- A type goes to `core/` only when two or more features use it. Use cases live in the feature whose
  screen calls them; repository implementations live in the feature that owns the data and bind
  the contract wherever it lives.
- The Room database, its DAOs, entities and migrations, and the settings DataStore are in
  `core/data/datasource/local/`. `@Database` must name every entity and `core/` may not import a
  feature, so a database split across features cannot compile under the rules. The template's
  README shows DAOs in features, which its own `ArchitectureTest` would reject once a feature has an
  entity; this gap is reported back to the template.
- Repository implementations keep the `*RepositoryImpl` suffix (the template accepts it or
  `Default*`; one style per project).
- A feature never navigates to another feature's route: it takes callbacks (`BrowserSlots`,
  `SettingsDestinations`, `onShowBrowser`) that `AppNavHost` fills.

## Consequences

- Deleting or extracting a feature is moving one folder plus its lines in `AppNavHost`.
- `core/domain` is larger than in a typical template app, because a browser shares its browsing data
  (tabs, history, bookmarks, settings, site rules) with almost every screen.
- Room names its schema export folder after the database class, so moving `AppDatabase` renames
  `app/schemas/<class>/`; the JSON files themselves hold no package names.
