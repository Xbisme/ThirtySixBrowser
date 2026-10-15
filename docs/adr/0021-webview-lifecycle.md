# 0021. A WebView is sized by its parent, torn down in order and replaced after a renderer loss

- Status: accepted
- Date: 2026-10-14

## Context

The browser screen hosts WebViews inside Compose `AndroidView`. Three failures were seen: pages
using `height: 100%` rendered blank, the app died with its renderer, and destroyed WebViews raced
native resources on WebView 116+.

## Decision

- The WebView gets `MATCH_PARENT` layout params; the Compose modifier still sizes it. `AndroidView`
  otherwise gives `WRAP_CONTENT`, the layout viewport becomes 0 px while `innerHeight` stays right,
  and maps, drawing apps and charts render blank.
- `onRenderProcessGone` returns `true`: the WebView is detached and `WebViewHost.generation` advances,
  so the dead WebView is disposed (destroy only; the platform forbids other calls) and a replacement
  is built for the same tab. After a crash or unknown cause the replacement stays empty with the
  error UI until Reload, so a page that crashes its renderer cannot loop; after a system reclaim it
  reloads by itself. Below API 26 the renderer is in-process and there is nothing to recover.
- Pop-up WebViews and the blocked-pop-up catcher also destroy themselves on a lost renderer.
- `releaseWebView` order is fixed: `stopLoading` → for incognito `clearHistory`, `clearFormData`,
  `clearMatches`, `clearSslPreferences`, `clearCache(true)` → `loadUrl("about:blank")` →
  `removeAllViews` → `destroy`. The blank load avoids the WebView 116+ race; the incognito clears
  leave no per-WebView state behind.

## Consequences

- New WebView-creating code must set the layout params and handle `onRenderProcessGone`.
