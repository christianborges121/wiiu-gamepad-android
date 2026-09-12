# Decoder-thread confinement

Date: 2026-09-12

## Problem

`VideoDecoder.init`/`release` ran on the main thread while `decodeFrame`
ran on the video worker thread: MediaCodec calls were split across two
threads (checklist 2.7 item open).

## Implementation

`VideoDecoder` owns a `CemuPad-Decoder` HandlerThread. All MediaCodec
calls dispatch to it synchronously (`FutureTask` + timeouts: 10 s init,
2 s decode, 5 s release), so backpressure behavior is unchanged and the
public API (`init`/`decodeFrame`/`release`) did not change — `MainActivity`
is untouched. `release` quits the thread after the stop task completes;
posts to a dead thread degrade to counted drops. `currentFps` is volatile
for the cross-thread telemetry read.

## Validation

- Unit tests + build pass.
- Installed over the live Mario session: app reconnected and decoded a
  steady 60 FPS on the new path with zero codec/decoder log lines.
