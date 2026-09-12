# Android decoder hardening (no live stream needed)

Date: 2026-09-12

## Scope

Checklist 2.7 items implementable and verifiable without a game session.
Full decoder-thread confinement deliberately deferred (needs live-stream
observation; init/release run on main, decode on the video worker today).

## Implementation

- `video/AvcNalUnits.kt`: Annex B start-code scanner (3- and 4-byte
  codes), NAL type detection, SPS/PPS/IDR description. Emulation
  prevention guarantees no false start codes, so the scan is sufficient.
- `VideoDecoder`: per-frame NAL validation; SPS/PPS-seen gate with
  bounded IDR recovery (one request per 30 frames until parameter sets
  arrive, then silent); counters for received/dropped/codec-errors/IDR
  requests alongside existing decoded/FPS telemetry; gate reset on init
  and release.
- Steady-state behavior unchanged: healthy streams carry SPS/PPS from
  the connect-time IDR, so no extra requests fire.

## Validation

- `AvcNalUnitsTest`: 5/5 pass (mixed start-code lengths, empty/garbage,
  trailing padding, lone start code).
- `testDebugUnitTest` + `assembleDebug` pass; APK installed on the phone.
- Live observation (recovery firing, counter values under loss) needs a
  game session; counters are exposed on `VideoDecoder` for the overlay
  or logs when that happens.
