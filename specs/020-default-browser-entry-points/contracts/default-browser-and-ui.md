# Contract C3 — Default-browser seam and the two new UI pieces

## C3.1 `DefaultBrowserController` (domain interface, data implementation)

```kotlin
interface DefaultBrowserController {
    /** True when the system currently names this app its default browser. Total; failure → false. */
    fun isDefaultBrowser(): Boolean
}
```

Implementation `AndroidDefaultBrowserController` (research R8), bound in a new
`di/DefaultBrowserModule.kt`:

| API level | Mechanism |
|---|---|
| 29+ | `RoleManager.isRoleAvailable(ROLE_BROWSER) && isRoleHeld(ROLE_BROWSER)` |
| 24–28 | `resolveActivity(VIEW https://example.com, MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == packageName` |

Every platform call is `runCatching`-wrapped (project convention for platform seams).

## C3.2 `rememberDefaultBrowserRequest` (presentation)

```kotlin
@Composable
fun rememberDefaultBrowserRequest(onUnavailable: () -> Unit): () -> Unit
```

| API level / condition | Intent launched |
|---|---|
| 29+ and role available, not held | `RoleManager.createRequestRoleIntent(ROLE_BROWSER)` |
| otherwise | `Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS` |
| both throw `ActivityNotFoundException` | none; `onUnavailable()` (FR-023) |

Before launching, it tells `MainActivity` not to count the coming return as a launch
(research R9). The activity result is ignored; state is re-read on resume (FR-022).

## C3.3 `DefaultBrowserInviteRepository` (domain interface, data implementation)

```kotlin
interface DefaultBrowserInviteRepository {
    fun observeInviteState(): Flow<InviteState>          // dismissed + launchCount
    suspend fun markInviteDismissed(): Result<Unit>      // never un-dismisses (INV-8)
    suspend fun recordLaunch(): Result<Unit>             // +1, capped at 3
}
// SyncDefaultBrowserInviteUseCase (run on every MainActivity.onStart):
//   if (controller.isDefaultBrowser()) repository.markInviteDismissed()   // FR-026
// LaunchCountPolicy.shouldCount(stoppedForConfigChange, returningFromOwnSystemScreen) =
//   !stoppedForConfigChange && !returningFromOwnSystemScreen             // FR-024a
data class InviteState(val dismissed: Boolean, val launchCount: Int)
```

## C3.4 Settings row — "Default browser"

New section header + row in `SettingsScreen`'s `LazyColumn`, placed **first** (above
Appearance), keyed `KEY_SECTION_DEFAULT_BROWSER` / `KEY_ROW_DEFAULT_BROWSER`, built with the
existing Spec 019 `ListRow`.

| State | Title | Summary | Click |
|---|---|---|---|
| not default | `settings_default_browser_title` | `settings_default_browser_summary_not_default` | runs `rememberDefaultBrowserRequest` |
| default | `settings_default_browser_title` | `settings_default_browser_summary_is_default` | none (row not clickable, no chevron) |

State comes from `SettingsViewModel.isDefaultBrowser: StateFlow<Boolean>`, refreshed from a
`LifecycleResumeEffect` in the screen.

## C3.5 Home invitation card

New `LazyColumn` item in `HomePage`, key `KEY_DEFAULT_BROWSER_INVITE`, content type
`TYPE_INVITE`, directly after the hero. Rendered only when
`HomeUiState.showDefaultBrowserInvite` (data-model §8).

| Element | String / component | Action |
|---|---|---|
| title | `home_default_browser_invite_title` | — |
| body | `home_default_browser_invite_body` | — |
| primary button | `home_default_browser_invite_action` | `rememberDefaultBrowserRequest(onUnavailable = show entry_default_browser_unavailable)`; does **not** dismiss (FR-025, FR-027) |
| dismiss | `RoundIconButton`, close icon, `contentDescription = home_default_browser_invite_dismiss` | `markInviteDismissed()` |

Built from the Home page's card surface and tokens (Spec 019); static content, so no
frame-rate state is read (§V). Test tags: `TEST_TAG_HOME_DEFAULT_BROWSER_INVITE`,
`…_ACTION`, `…_DISMISS`.

## C3.6 New strings (all 8 locales, FR-029)

| Key | EN |
|---|---|
| `settings_section_default_browser` | Default browser |
| `settings_default_browser_title` | Default browser |
| `settings_default_browser_summary_not_default` | ThirtySix isn't your default browser. Tap to change. |
| `settings_default_browser_summary_is_default` | ThirtySix is your default browser |
| `home_default_browser_invite_title` | Open links in ThirtySix |
| `home_default_browser_invite_body` | Make ThirtySix your default browser so links from other apps open here. |
| `home_default_browser_invite_action` | Set as default |
| `home_default_browser_invite_dismiss` | Dismiss |
| `entry_process_text_label` | Search with ThirtySix |
| `entry_link_rejected` | This link can't be opened |
| `entry_tab_limit_reused` | Tab limit reached — opened in your current tab |
| `entry_default_browser_unavailable` | Default apps can't be changed on this device |
