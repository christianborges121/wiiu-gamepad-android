# Fit-mode screenshot comparison

Date: 2026-09-12

Screenshots captured from the connected Android device:

- [Original Wii U GamePad](../../original-wiiu-gamepad-20260912.png) *(local screenshot, untracked)*
- [Screen Fill](../../screen-fill-20260912.png) *(local screenshot, untracked)*

The Original-mode image is visibly clipped at the display bounds. Screen Fill
shows the intended full-screen framing and retains the complete visible GamePad
composition. Both captures report `Video 30 FPS | Clients 1`, so this is a
layout/scaling issue rather than a stream-connection issue.

The relevant source uses a fixed `SurfaceView` buffer (`854 x 480`) and applies
`.aspectRatio(854f / 480f)` for Original mode. That ratio is effectively 16:9,
but the modifier is combined with `fillMaxWidth()` inside a full-screen parent;
the resulting measured height can exceed the available landscape height and be
clipped. Screen Fill instead uses `fillMaxSize()`. This is the leading layout
hypothesis to test before changing touch-coordinate mapping.
