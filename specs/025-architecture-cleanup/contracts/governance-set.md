# Contract: Governance set

The documents a contributor or agent reads, what each holds, and its limits. One home per fact.

| File | Holds | Limit | Loaded by agents |
|---|---|---|---|
| `.specify/memory/constitution.md` | 8 principles (template), each MUST with **Enforced by** or **Review**; Product Constraints; Quality Gates; Governance. Version **2.0.0** | < 250 lines | at `/speckit-plan` gates |
| `AGENTS.md` | commands (with the JBR `JAVA_HOME` prefix), package map, writing rules, comment rules, test rules | < 100 lines | every request |
| `CLAUDE.md` | `@AGENTS.md` only | 1 line | every request |
| `docs/adr/NNNN-*.md` | one decision each: context, decision, consequences | — | when linked |
| `docs/constitution-history.md` | one line per amendment; 1.0.0–1.6.0 summarised, 2.0.0 added | — | no |
| `docs/archive/` | `changelog-001-024.md` (old `CLAUDE.md` Recent Changes), `dev-workflow.md`, `meeting-note.md`, `project-context.md`, `sdd-roadmap.md` — verbatim, first line marks them as archive | — | **never referenced** |
| `specs/NNN-*/` | per-spec history and gate results | — | per spec |

## Constitution 2.0.0 — Product Constraints content

Filled from constitution 1.6.0 and current code:

- **Data and privacy**: offline-first, no account, no cloud sync, no analytics or crash reporting, nothing sent to a ThirtySix-controlled server; all user data on device; database excluded from backup and device transfer, settings included.
- **Web content**: `android.webkit.WebView` only; page scripts only from APK-bundled files, one-way (no data back to the app); no `addJavascriptInterface` unless origin-gated and justified in a spec; protection and script data ship in the APK, never fetched.
- **Permissions** table: `INTERNET`, `ACCESS_NETWORK_STATE`, `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion="28"`), `CAMERA`, `RECORD_AUDIO`, `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION` (each asked only after the person allows a site), `MODIFY_AUDIO_SETTINGS`; `POST_NOTIFICATIONS` deliberately absent; no `MANAGE_EXTERNAL_STORAGE`.
- **Budgets**: release APK and cold start as measured in step 1 of this spec, ±5 % per spec unless the spec states its own budget.
- **Locales**: EN (default), VI, DE, RU, KO, JA, ZH, FR; 48 dp targets, WCAG AA, TalkBack labels.
- **SDK**: minSdk 24, targetSdk 36, compileSdk 36; 16 KB alignment for every `.so`.
- **Build**: `debug` + `release` only; release signed with the release keystore for distribution, debug-key fallback allowed locally and in CI with a warning.
- **Critical flows with UI tests**: open link → page loads; tabs (normal + incognito) open/close; bookmark star; history record; Fire.

## ADR list

| # | Title |
|---|---|
| 0001 | Feature-first Clean Architecture (template), `*RepositoryImpl` naming, shared contracts and the database in `core/` |
| 0002 | Hilt on KSP, modules inside each feature |
| 0003 | Every rule a machine can check is a failing check |
| 0004 | Comments only where the code cannot speak |
| 0005 | Phase order retired after Phases 1–6 (Specs 001–024) |
| 0006 | Home is the reserved address `thirtysix:home`, never loaded into the WebView |
| 0007 | Each incognito session gets its own WebView profile; older engines use the cookie snapshot |
| 0008 | Fire runs in a fixed order behind a persisted in-progress marker |
| 0009 | Language changes apply in place (`configChanges` includes `screenLayout`) |
| 0010 | CI emulator always cold-boots; no AVD snapshot cache |
| 0011 | `MainActivity` is `singleTask`; incoming links via one-slot relays |
| 0012 | HTTPS upgrade falls back from the error page's `onPageFinished` |

Further ADRs are added during the comment pass when a removed comment carried a reason longer than three lines.
