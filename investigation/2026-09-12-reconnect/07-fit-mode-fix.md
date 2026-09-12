# Fit-mode clipping fix

Date: 2026-09-12

## Root cause

`MainScreen` used `fillMaxWidth().aspectRatio(...)` for Aspect Fit and Original
Wii U GamePad. On the connected 2340 x 1080 landscape display, that selected a
2340 x 1316 surface. The parent clipped the 236 px vertical overflow.

## Change

`DisplayLayout.aspectFitDimensions()` now calculates the largest target-aspect
rectangle that stays within both container dimensions. `MainScreen` applies the
calculated width and height for both fit modes.

At 2340 x 1080, a 16:9 surface now measures 1920 x 1080. This leaves expected
left and right pillarbox bars instead of clipping the video. Screen Fill and
Stretch remain full-screen modes and were not changed.

## Validation

- Added `DisplayLayoutTest`, covering height-limited wide displays and
  width-limited narrow displays.
- `testDebugUnitTest` passed.
- Built and installed the debug APK successfully.
- Live screenshot after installation:
  [aspect-fit-after-layout-fix-20260912.png](../../aspect-fit-after-layout-fix-20260912.png)
  shows the stream fully contained at 29 FPS with one connected client.

Original Wii U GamePad uses the same corrected fit calculation with its native
854:480 ratio, so it should have the same non-clipping behavior. A manual
Original-mode confirmation remains useful because it is a distinct UI setting.
