# 0016. Page scripts are bundled, one-way and installed in one place

- Status: accepted
- Date: 2026-10-14

## Context

Cookie-banner refusal and basic fingerprinting protection need scripts in the page. A bridge from page
to app (`addJavascriptInterface`) would expose app objects to any site.

## Decision

- Page-start scripts are installed only through `ProtectionHooks` (`PrivacyScriptInstaller`,
  `addDocumentStartJavaScript(…, "*")`), and only from files in the APK.
- The prelude is built from local data only (the session secret, the exceptions); nothing comes back
  from the page. The scripts stand down by the top-level page's protection setting.
- autoconsent (16.51.0, MPL-2.0) must stay `optOut` with `heuristicMode: 'off'`: upstream `tier2`
  clicks Accept. It is rebuilt only with `tools/privacy-scripts/build.sh`.
- The prelude is never assigned to `window`, and every string in it is JSON that is safe inside
  `<script>`. Scripts are reinstalled (old handler removed first) when the session secret, the bundles
  or the exceptions change, and before a per-site toggle reloads the page.

## Consequences

- No page can call into the app; new page features go through the same installer.
