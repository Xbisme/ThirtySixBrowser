# 0029. First-run start pays for compiling the redesign's shaders

- Status: accepted
- Date: 2026-10-17

## Context

Spec 019's G15 measured the first run (onboarding) on the Galaxy A55 at ≈265 → ≈345 ms against
Spec 018, while the returning start was +4.2%. Perfetto traces on `TA016_API36_16K`, release
builds, fresh install, interleaved with Spec 018 (`492e4f3`):

- The first onboarding frame takes ≈45 ms on the RenderThread against ≈18 ms for Spec 018. Almost
  all of it is `shader_compile` cache misses: seven programs (radial-gradient fill for the mesh,
  texture for the brand mark, text, four rounded-rect variants for glass fills, strokes and the
  gradient pill) against two in Spec 018.
- HWUI keeps compiled shaders on disk per app, so only the first launch after an install pays.
  The AVD recompiles on every launch, which is why its returning starts do not show this.
- Main-thread work, Hilt graph and the protection-list parse started from `Application.onCreate`
  add a few milliseconds at most, below what interleaved runs on the AVD can separate.

## Decision

The first-run start may exceed the cold-start budget by the cost of compiling the design system's
shaders, once per install. The returning start keeps the ±5% budget. The onboarding keeps the
Spec 019 look; it is not simplified to save the compile.

## Consequences

- Cold-start gates report first run and returning separately, and judge first run against the
  previous build with the same design system.
- A new kind of drawing on the first screen (a new gradient, blur or shape style) adds a shader
  compile to every new install; check it in a trace before adding one there.
- Spec 019 G15 is closed as accepted.
