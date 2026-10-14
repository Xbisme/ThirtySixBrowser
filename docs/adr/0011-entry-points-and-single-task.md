# 0011. One activity receives links, and the browser is brought forward by popping

- Status: accepted
- Date: 2026-10-14

## Context

ThirtySix is a browser for the rest of the phone: links, shares, text searches and the default-browser
role all arrive as intents.

## Decision

- `MainActivity` is `singleTask`. Links arrive in `onNewIntent`; an intent is handed over only when
  `savedInstanceState == null` and not `LAUNCHED_FROM_HISTORY`, so rotation and process death never
  reopen a link. An unconsumed request is saved and restored only into an empty relay.
- Entry relays are one-slot `Channel`s, not `SharedFlow(replay = 0)`: a cold start has no collector
  yet.
- There is one `EntryPointViewModel`, created outside the `NavHost`; no destination creates its own.
- The browser is shown by popping the screens above it. `navigate(browser) { launchSingleTop }` on
  the browser itself replaces its back-stack entry and recreates the screen, reloading the WebView
  and cancelling the entry's snackbar.
- The start route is read once, off the main thread, before `setContent`: `NavHost` captures its
  start destination at first composition, and the settings flow's first value always says
  "onboarding not done".
- Default-browser state is re-read on resume: the role dialog is translucent, so the activity is
  never stopped.
- The launch screen is never held open (no `setKeepOnScreenCondition`, no exit animation); it ends at
  the first frame and covers the start-route read.
- Back on an untouched tab opened from another app calls `moveTaskToBack` to return to the sender.
- At the tab cap an incoming link reuses the most recently used normal tab. If that tab is not
  active, its stored address is set before switching, so the switch load opens the new page; if it is
  active, the link loads into the live page so the old page stays one Back away.
- Each start counts one launch toward the default-browser invitation, except a rotation or a return
  from the role or default-apps screen the app itself opened.

## Consequences

- `ActivityScenario` loses its activity if `onNewIntent` sets an intent with different data; tests
  launch a fresh scenario per link.
