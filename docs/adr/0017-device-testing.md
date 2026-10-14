# 0017. Where performance and device gates are measured

- Status: accepted
- Date: 2026-10-14

## Context

The project's AVDs render in software (`swiftshader_indirect`), so frame-time targets cannot be judged
on them; release builds cannot be seeded with test data; the owner's Galaxy A55 is a personal phone.

## Decision

- Frame-time gates (list p99 ≤ 16 ms) are measured on a release build on Pixel 5-class hardware or
  recorded as deferred; numbers from the AVD are reference only.
- A release-seeding mechanism must exist before such hardware is booked: the debug seeders do not
  exist in release builds and `run-as` fails there.
- The Galaxy A55 is used only with the owner present; check `dumpsys window | grep mCurrentFocus`
  before any scripted input, install only release builds on it, and never run
  `connectedDebugAndroidTest` against it (it uninstalls the debug app and its data).
- Cold-start comparisons use interleaved rounds on one device; single rounds swing by up to 80 ms.
- `adb emu network capture` records nothing on these AVDs; "no network" claims need a proxy.

## Consequences

- Specs list such gates as deferred instead of reporting emulator numbers against hardware targets.
