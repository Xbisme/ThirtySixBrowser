# Contract C2 — UI pieces, navigation and strings

All UI is built from Spec 019 components (design waiver in spec Assumptions). Static content
only; no frame-rate state (§V).

## C2.1 Permission dialog (`SitePermissionDialog`)

- Title: `site_permission_title` with the origin, e.g. "**meet.google.com** wants to use your
  camera and microphone". Capability names from `site_capability_camera/microphone/location`.
- Buttons (vertical, design-system pills): `site_permission_always_allow`,
  `site_permission_allow_once`, `site_permission_deny`. Incognito: allow-once + deny only.
- Permanently denied follow-up: `site_permission_blocked_title/body` + `site_permission_open_settings`.

## C2.2 JS dialogs (`SiteJsDialog`) and leave-page

- Header: `site_dialog_from` ("**example.org** says"); body = the page's text, unchanged.
- Alert: OK. Confirm: OK / Cancel. Prompt: text field (default text) + OK / Cancel.
- From the 2nd dialog: checkbox `site_dialog_suppress`.
- Leave page: `site_leave_title` + `site_leave_leave` / `site_leave_stay`.

## C2.3 HTTP auth dialog (`HttpAuthDialog`)

`http_auth_title` ("Sign in to **host**"), `http_auth_realm` (when present), fields
`http_auth_username` / `http_auth_password` (password masked, `KeyboardType.Password`, autofill
off), `http_auth_sign_in` / `http_auth_cancel`, and the line `http_auth_not_saved` ("Not saved.
You'll be asked again next time.").

## C2.4 Long-press sheet (`LongPressSheet`)

Bottom sheet; header = address (single line, middle-ellipsized); one row per `LongPressAction`
(`long_press_open_new_tab`, `long_press_open_incognito_tab`, `long_press_copy_link`,
`long_press_share_link`, `long_press_download_link`, `long_press_open_image`,
`long_press_download_image`, `long_press_copy_image`, `long_press_share_image`). Snackbars:
`long_press_opened_background` with action `long_press_switch`, `long_press_copied`.

## C2.5 Pop-up notice

Snackbar `popup_blocked` with action `popup_open` when an address exists.

## C2.6 Fullscreen

No new UI; system bars hidden; Back exits.

## C2.7 Site permissions screen (`SitePermissionsScreen`, new `AppDestination.SitePermissions`)

- Settings → Privacy gains row `settings_site_permissions` → this screen.
- List: one section per origin; rows camera / microphone / location for capabilities that have a
  decision, each showing `site_decision_allowed` / `site_decision_denied`, tap → choice dialog
  (Always allow / Don't allow); per-site action `site_permissions_remove_site`; top bar action
  `site_permissions_remove_all` → confirm dialog `site_permissions_remove_all_confirm`.
- Empty state: `site_permissions_empty`.

## C2.8 Clear browsing data

New checkbox row `clear_data_site_permissions` (unticked by default, FR-010a).

## C2.9 Strings

About 45 new keys (the names above plus `site_capability_*`, `site_permission_*`, `site_dialog_*`,
`site_leave_*`, `http_auth_*`, `long_press_*`, `popup_*`, `settings_site_permissions`,
`site_permissions_*`, `clear_data_site_permissions`) × 8 locales; lint parity at error severity.
