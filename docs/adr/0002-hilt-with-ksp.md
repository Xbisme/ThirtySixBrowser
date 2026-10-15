# 0002. Hilt on KSP, modules inside each feature

- Status: accepted
- Date: 2026-10-14

## Context

AGP 9 ships Kotlin support itself and no longer supports kapt. Hilt supports KSP from 2.59.2; the
app has used Hilt on KSP from the start.

## Decision

- DI is Hilt with annotation processing on KSP; the processor artifact is `hilt-compiler`, not the
  kapt-era `hilt-android-compiler`, which compiles silently under `ksp(...)` and generates nothing.
- Each feature declares its bindings in `feature/<f>/di/`; app-wide infrastructure (database,
  DataStore, dispatchers, clock, image caches, clipboard, the incognito WebView profile) is in
  `core/di/`.
- Dispatchers come from the injected `DispatcherProvider`; only `di/` names `Dispatchers.IO` or
  `Dispatchers.Default`. Long-lived collectors use the injected `@ApplicationScope` scope instead of a
  scope of their own, so tests can cancel them.
- Code outside injection (a composable creating WebViews) reaches singletons through a Hilt
  `@EntryPoint`, never by naming a data-layer object.

## Consequences

- Deleting a feature deletes its bindings with it.
- Instrumented tests that launch `MainActivity` replace `SettingsDataStoreProviderModule` with a
  per-test DataStore; the class name must stay stable because `@UninstallModules` names it.
