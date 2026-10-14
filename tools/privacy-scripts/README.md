# Privacy scripts generator (Spec 024)

Builds `app/src/main/assets/privacy/consent.js`, the cookie-banner auto-decline script, from
[DuckDuckGo autoconsent](https://github.com/duckduckgo/autoconsent) (MPL-2.0). See
`specs/024-privacy-tools/research.md` R7.

**Run by hand only — never from Gradle or CI.** The committed asset is what a release ships;
the app never downloads rules (Constitution §I).

```bash
tools/privacy-scripts/build.sh
```

It needs Node.js ≥ 20. It installs the pinned versions from `package-lock.json`, then:

1. keeps only autoconsent's generic and frame rules (`trim-rules.mjs`);
2. bundles `entry.ts` (our own file) with esbuild, minified, as an IIFE;
3. wraps it as `(function(cfg){ … })` behind the MPL-2.0 notice.

The app's `PrivacyScriptComposer` calls that function with `{v, s, x}`.

## Rules that must never change

- `autoAction: 'optOut'` only. Never `'optIn'`.
- `heuristicMode: 'off'` and `enableHeuristicDetection: false`. Upstream's `'tier2'` clicks
  **Accept** on banners that have no reject button. FR-024 forbids that.
- No `window` property may be created, and no message may be sent to the app (§II-4).

## Licence

autoconsent is MPL-2.0. These files are not modified; `entry.ts`, `trim-rules.mjs` and
`build.sh` are ThirtySix's own. The built file keeps the MPL notice and the source link, and
Settings → About shows the attribution.

## Updating

1. Bump the version in `package.json`.
2. Run `npm install`, which updates the lockfile.
3. Run `build.sh`.
4. Re-run the consent fixtures (`CookieBannerTest`) and gate G11.
