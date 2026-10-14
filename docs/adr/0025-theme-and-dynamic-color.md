# 0025. Dynamic color covers the brand roles; hero and incognito colors stay fixed

- Status: accepted
- Date: 2026-10-14

## Context

On Android 12+ the wallpaper can supply the color scheme. Some colors carry meaning that a wallpaper
must not change: the dark hero, danger, and the incognito look that must not read as dark mode.

## Decision

- Under dynamic color, the wallpaper supplies primary, its containers and the surface roles; danger
  stays the brand's. Of the extended tokens only the hero's main glow and the action gradient follow
  the wallpaper; the hero's dark base, its rose glow, tile pastels, success and incognito plum stay
  fixed. `ThirtySixTheme` ignores the dynamic color flag below Android 12.
- The in-app theme is a Compose concern: the app never calls `AppCompatDelegate.setDefaultNightMode`.
- First composition uses `UserSettings.DEFAULT` so it never blocks; saved settings recompose once read,
  and the `windowBackground` in `themes.xml` hides the gap.
- Card mesh colors come from a hash of the host, not the favicon's average color, which needed a
  bitmap decode per card; a site always gets the same mesh.

## Consequences

- New brand tokens must say whether they follow the wallpaper.
