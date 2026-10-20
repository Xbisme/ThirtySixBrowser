# Contract: Hand-off to Other Apps

This is what happens when a page navigates to an address that is not a web address.

## Decision table (`ExternalLinkPolicy`)

| Main frame | Tap (`hasGesture`) | Incognito | Decision | What the person sees |
|---|---|---|---|---|
| no | any | any | `Drop` | Nothing |
| yes | no | any | `Offer` | Snackbar "This page wants to open another app", with an "Open" action |
| yes | yes | no | `Launch` | The other app opens |
| yes | yes | yes | `Confirm` | Dialog "Open in another app? The app will see this link.", with Open and Cancel |

- **"Open" on the snackbar**: the navigation is re-decided as tapped. In a normal tab it goes to `Launch`; in incognito it goes to `Confirm`.
- **Scope of the policy**: `http`, `https`, `about`, `data` and `blob` never reach it, because the WebView handles them. `javascript`, `file` and `content` are `Drop` in every row, since a page has no business handing them to another app.

## Launch (`ExternalAppLauncher`)

1. **Build the intent.**
   - For an `intent:` address, `Intent.parseUri(url, Intent.URI_INTENT_SCHEME)`.
   - For anything else, `Intent(ACTION_VIEW, Uri.parse(url))`.
2. **Make it safe.** Always set `addCategory(CATEGORY_BROWSABLE)`, `component = null`, `selector = null` and `FLAG_ACTIVITY_NEW_TASK`.
3. **Start it.** `startActivity`.
   - On `ActivityNotFoundException`: if the intent carries a `browser_fallback_url` extra that is `http(s)`, return it to the tab, which loads it through `loadProtected`. Otherwise do nothing.
   - On any other throwable: log `"External app hand-off failed"` with the throwable and no address.

## Lifetime

- **Offers** belong to the page generation that made them. An offer from a page that has since been replaced is dropped, like `onPopupBlocked`.
- **Confirmations** are `PageRequest`s. Switching tabs, navigating away or closing the screen cancels them, and cancelling opens nothing.

## Strings (all 8 locales)

| Key | EN |
|---|---|
| `external_app_offer` | This page wants to open another app |
| `external_app_open` | Open |
| `external_app_confirm_title` | Open in another app? |
| `external_app_confirm_body` | The app will see this link. |
| `downloads_unsupported_snackbar` | This kind of download isn't supported |

`external_app_open` reuses the existing `popup_open` string if its wording matches in every locale. Otherwise it is a new key. The Cancel button reuses the dialog frame's existing cancel string.
