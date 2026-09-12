# Watchdog hardening for silent worker death

Date: 2026-09-12

## Report

User: if Cemu stops and starts again, CemuPad does not pick it up without
closing and reopening the app. Code walk of the subscribe/prune/resubscribe
and video retry paths shows recovery *should* work, so the prime suspect is
a silently dead worker loop (an uncaught throwable kills the thread while
`isRunning` stays true; the app looks alive but never retries).

## Fix (phone-only, game-safe)

- `VideoStreamClient`: retry loop catches `Throwable` and continues;
  interrupt exits only when stopping; tripwire log if the loop ever exits
  while supposed to run. Added `isWorkerAlive()` and `restartIfStalled()`.
- `DSUServer`: stored recv/push thread refs, same Throwable guards and
  tripwires, `areWorkersAlive()` + `ensureThreads()` respawn.
- `MainActivity`: 5 s watchdog (started/stopped with the activity)
  calling both. No behavior change when healthy.
- `DSUServerLifecycleTest`: start/stop/start + ensureThreads
  no-op/idle cases on an ephemeral port.

## Validation

- Unit tests pass; APK installed.
- Soak test (force-stop, launch, HOME, foreground): process stable, no
  crashes, no tripwire lines, DSU socket provably bound
  (`/proc/net/udp6` shows `:::26760`). Earlier logcat silence was
  healthy-quiet (socket is IPv6 dual-stack; the first probe only read
  the IPv4 table).
- True Cemu-down/up recovery still needs a live cycle to confirm
  end-to-end; the tripwire logs will pinpoint it if it ever strands again.
