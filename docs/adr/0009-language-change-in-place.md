# 0009. A language change is applied in place

- Status: accepted
- Date: 2026-10-14

## Context

The app language uses the AndroidX per-app API. Recreating the activity on a change showed
111–288 ms of black frames, reloaded the WebView and lost unsaved UI state.

## Decision

- `MainActivity` declares `configChanges="locale|layoutDirection|screenLayout"`. `screenLayout` is
  needed: the first change away from "follow system" also reports it (Galaxy A55, Android 16), and
  without it the activity was recreated on exactly that change.
- The language chooser closes only once the new configuration has arrived (or after
  `BrowserLimits.LANGUAGE_APPLY_TIMEOUT_MS`), so the screen never shows the old language after the
  tap.
- The platform owns the language; nothing in the app stores it.
- `MainActivity` is an `AppCompatActivity`: below Android 13 `AppCompatDelegate.setApplicationLocales`
  takes effect only there, and AppCompat's `autoStoreLocales` keeps the choice. `installSplashScreen()` must
  run before `super.onCreate()`, because AppCompat reads the theme while building its delegate and
  the splash theme is not an AppCompat theme.
- Rotation and size changes are also handled in place, so fullscreen video keeps playing.

## Consequences

- Diagnose with `adb logcat | grep "Checking to restart"`; record a real device with
  `scrcpy --record` (`adb shell screenrecord` stops on a configuration change).
