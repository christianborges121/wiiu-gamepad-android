# Diagnostics overlay restyle

Date: 2026-09-12

## Request

Remove the overlay background so the game remains visible. Put the text on one
line in the corner of the screen.

## Implementation

In `MainScreen.kt`, the Material `Card` overlay was replaced with a single
`Text` aligned `TopStart` with 8 dp padding. There is no fill or card. A black
text shadow (`blurRadius = 6`) keeps the line readable over bright GamePad
pixels.

Example line:

```text
192.168.x.x:26760  29 FPS  c1  tx123  rx456
```

When video is not streaming, FPS is replaced with `awaiting`.

## Validation

- Debug APK assembled and installed over wireless ADB after the restyle.
- Live visual confirmation on the phone is the next check: open Configuration,
  enable Diagnostics overlay, confirm the game is still visible under the line.
