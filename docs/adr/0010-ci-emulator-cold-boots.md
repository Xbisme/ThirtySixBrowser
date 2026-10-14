# 0010. The CI emulator always cold-boots

- Status: accepted
- Date: 2026-10-14

## Context

Three different AVD-snapshot bugs wedged the instrumented job (Specs 008, 017, 018): an unversioned
cache key restoring a snapshot from an older runner image, a snapshot written by a failed run, and a
snapshot written by a passing run that then failed to boot. The signature was `Starting 0 tests`
with `Broken pipe`, `DeadSystemException` or `stop: Not implemented`.

## Decision

- The emulator job has no AVD snapshot cache and boots with `-no-snapshot`; QEMU is killed at the
  end of the script with `ANDROID_EMULATOR_WAIT_TIME_BEFORE_KILL=5`.
- The script kills `qemu-system` and `crashpad_handler` itself, on one line, found with
  `ps aux | awk '/[q]emu-system/'`: emulator-runner's terminate hangs on emulator 36+
  (`stop: Not implemented`), `pkill` truncates names to 15 characters, and `pkill -f` matched the
  script's own shell.

## Consequences

- The job takes a few minutes longer and is deterministic. Reintroducing a snapshot cache needs a
  new ADR.
