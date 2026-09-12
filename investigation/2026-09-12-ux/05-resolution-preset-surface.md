# Resolution preset drives the SurfaceView buffer size

Date: 2026-09-12

## Request

Checklist 4.6: the resolution preset was stored and shown but the surface
stayed fixed at 854x480 (`holder.setFixedSize(854, 480)`).

## Implementation

- `DisplayLayout.surfaceBufferSize(preset, containerWidthPx,
  containerHeightPx)`: explicit presets use their own dimensions;
  Device Auto follows the container. Covered by two new
  `DisplayLayoutTest` cases.
- `MainScreen` keeps the `SurfaceHolder`, applies the preset at surface
  creation and on every preset change via
  `LaunchedEffect(selectedResolution, videoHolder)`.
- The decoder needs no change: it is configured for the stream's 854x480
  with `SCALE_TO_FIT`, and the `Surface` object survives geometry changes.

## Findings during verification

1. Stale-closure stomp: the first version called the apply helper from the
   `surfaceCreated` callback, which captures the composition-time preset.
   `setFixedSize` recreates the surface, the callback re-fired with the old
   preset, and the buffer snapped back to 854x480. Fix: apply only from the
   effect keyed on both preset and holder (recreation reuses the same
   holder instance, so no loop).
2. Measurement trap: with no stream running, SurfaceFlinger keeps showing
   the decoder's idle 854x480 output buffer. The real signal is the
   `surfaceChanged` callback, which reported 1920x1080 on Full HD and
   854x480 on Native, both directions live on the phone.
3. Drawer reachability: the extra drawer row pushed Resolution/Close below
   the fold with no scroll. The drawer column is now scrollable.

## Validation

- `testDebugUnitTest` passes (5/5 layout tests).
- Debug APK installed; `surfaceChanged` log confirmed 1920x1080 and
  854x480 on the live phone. Diagnostic logging was removed afterwards and
  the final APK reinstalled. Prefs left at Native 854x480, help on.
- End-to-end scaling against a running Cemu stream still needs a live
  game session.
