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

## Live confirmation (Cemu restart, same day)

User closed/reopened Cemu to Mario; phone sat at `awaiting c1` with no
video despite the server listening. Diagnosis: the phone process (pid
20994, uptime 69 min) predated the watchdog APK install (14:47:27) —
`install -r` does not restart a running process, so the old code without
watchdog/guards was still executing. Its push loop had died silently
(no prune ever ran: c1 stuck, resubscribe never fired, video never
restarted), while the receive thread kept answering polls (counters
growing). Force-stop + relaunch (fresh watchdog build, pid 24802)
recovered instantly: DSU subscribe, video connect + IDR in Cemu's log,
60 FPS Mario title. Root cause and fix both confirmed live.

## New observation: DSU request storm

Fresh counters show ~10-12k DSU req/s (tx/rx +142k in ~10-12 s at 1:1
ratio). The phone push loop is capped at 100 Hz, so Cemu originates the
storm (per-response request chaining with no pacing). Wasteful for
WiFi/battery/CPU; input needs at most ~120 Hz. Needs Cemu-side pacing
(rebuild when no game runs).
