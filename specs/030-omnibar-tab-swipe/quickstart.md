# Quickstart: Validate the Address-Bar Tab Swipe

Prefix every Gradle call with `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`. Use the AVDs with animations off for the automated parts. The A55 is used only for the frame-timing check in G5, after checking `mCurrentFocus`.

## G1 — Static checks and unit tests

`./gradlew detekt ktlintCheck testDebugUnitTest lintDebug` must pass. The unit tests cover:

- **`TabOrder`**: opening order with ties; neighbours per kind; ends; a missing active id.
- **Repositories**:
  - closing the active tab makes the next one active, or the previous one when it was last;
  - closing a background tab leaves the active tab alone;
  - with an incognito tab active, closing a normal tab does not take over;
  - incognito positions never repeat after closes.
- **`ObserveTabNeighboursUseCase`**: it follows the active tab and never crosses kinds.
- **`TabSwipeTracker`**:
  - slop and the sideways ratio;
  - the commit fraction and the flick;
  - resistance at the ends;
  - a second pointer cancels;
  - RTL;
  - focused means no swipe.
- **`TabSwipeViewModel`** (Turbine): neighbours to previews; incognito previews carry no title or host; `SwitchTo` calls the switch; `Announce` is sent only for accessibility actions; the setting maps to `enabled`.
- **`TabsViewModel`**: the cards stay in opening order after switches.
- **`SettingsViewModel`**: the toggle persists.

## G2 — Release and size

- `./gradlew assembleRelease`, then the 16 KB script.
- The APK grows by ≤ 40 KB.
- The Compose compiler report (`-PcomposeReports`) has no unexplained non-skippable composables in the pill lane, the preview or the Settings row.

## G3 — Device walk on `TA016_API36_16K`

1. **US1**: open tabs A, B and C, with C shown. Then:
   - Swipe right twice: B, then A.
   - Swipe left three times: B, C, then nothing at the end (it resists).
   - Open Tabs: the order is A, B, C.
   - Close B while it is current: C shows.
2. **US1, short release and flick**: a short slow drag returns. A short fast flick switches.
3. **US2**: with normal tabs N1, N2 and incognito tabs I1, I2:
   - From N2, no swipe reaches an incognito tab.
   - From I1, swiping right resists and swiping left reaches I2.
   - The incoming incognito preview is the plum surface with no image.
4. **US3**:
   - 20 taps on the pill: it opens for typing 20 times.
   - With the pill focused, dragging sideways moves the cursor.
   - An in-page edge swipe goes back (Spec 028), and the tab is unchanged.
   - 50 vertical scrolls: no tab change.
5. **US3, Home**: a tab showing Home swipes from its search field. The incoming Home tab shows the Home look.
6. **US4**:
   - With animations off, a commit snaps.
   - With the setting off, a drag does nothing.
   - In the accessibility actions on the pill, "Next tab" switches and announces. Use `uiautomator` or a Compose test to list the actions; TalkBack itself is a person's check.
7. **API 24**: on `TA016_API24`, one swipe each way, and one tap that focuses the pill.

## G4 — Instrumented tests

`connectedDebugAndroidTest` on `TA016_API36_16K`, including the new `BrowserTabSwipeTest`:

- swipe the pill and the tab changes;
- tap the pill and it is focused with no tab change;
- the accessibility action switches;
- with the setting off, the swipe does nothing.

## G5 — Measurements

- **SC-005**: on the A55 release build, 20 scripted swipes. `dumpsys gfxinfo <pkg> reset` before and read after: janky frames ≤ 1 %.
- **SC-008**: cold start in alternating blocks against `8e5a0ca` on `TA016_API36_16K`: the median stays within 5 %.
- **Layout Inspector** recomposition count during one drag: Android Studio by hand, or DEFERRED.
