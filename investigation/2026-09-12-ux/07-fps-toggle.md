# 30/60 FPS decode toggle

Date: 2026-09-12

## Request

Configurable max FPS, default 30. Scoped down per user from 30/60/120/180
to a 30-vs-60 toggle (Wii U content never exceeds 60 FPS).

## Implementation

- `DisplaySettings.limitTo30Fps` (default `true`), codec key
  `limit_to_30_fps` (missing decodes to `true`).
- `video/FrameRateLimiter`: pure PTS gate. Drops frames arriving sooner
  than the cap interval; non-monotonic timestamps always pass so a bad
  clock can never stall the picture.
- `VideoDecoder.maxFps` (volatile, 30/60 mapped in `MainActivity` on
  settings change and decoder creation); gate sits inside
  `doDecodeFrame` after SPS/PPS tracking so parameter sets are never
  starved; skips counted in `totalFramesRateLimited`. Decoded-FPS
  telemetry therefore reports the capped rate honestly.
- Drawer "Limit to 30 FPS" switch with helper text.

## Validation

- `FrameRateLimiterTest` 5/5, codec round-trip/default tests pass.
- Installed; cold launch into idle is clean (no crash on new code paths).
- Live cap check (30 vs 60 overlay FPS on a 60 FPS stream) needs a game
  session; Cemu was off at install time.
- Follow-up (needs Cemu idle + rebuild): Cemu-side encode cap via a new
  control opcode to also save bandwidth and encoder CPU.

## Hotfix the same day: phone-side dropping removed

User reported stuttering + ghosting on screen transitions. Root cause:
dropping P-frames ahead of a stateful H.264 decoder corrupts its
reference chain (each P-frame predicts from the previous one); drift
accumulates until the next IDR, worst on high-delta transitions. NAL
validity is irrelevant — valid frames still break the chain when a
predecessor is missing.

Fix: enforcement deleted from `doDecodeFrame` (setting, UI, limiter,
and tests stay for the future encode-side contract); drawer text now
states Cemu support is pending. Live-verified after reinstall: clean
60 FPS file-select screen, no ghosting. Rate caps must skip
capture/encode, never decode.
