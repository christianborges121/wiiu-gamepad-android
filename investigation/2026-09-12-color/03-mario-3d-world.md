# Mario 3D World color + stream verification

Date: 2026-09-12. Substituted for Hyrule Warriors (blocked: title has
issues running on this setup; separate Vulkan device-loss crash also seen
today at 13:54:38, `failed to submit command buffer, Error -4`).

## Evidence

Local screenshot `mario-3dworld-phone-20260912.png` (untracked): intro
cinematic with Luigi, Blue Toad, Mario, and Peach in the clear pipe over
the pink starfield, overlay reporting 60 FPS, client c1.

## Verdict: RGBA-path colors correct, full speed

- Peach pink dress, blonde hair, Mario red/blue, Luigi green, Toad blue
  spots, pink starfield, natural skin, white sparkles. Every hue family
  correct — validates the RGBA8/BGRA8 capture path, complementing Wind
  Waker's packed-A2B10G10R10 coverage.
- Phone decoded a steady 60 FPS, which bounds emulation at full 60 FPS
  during streaming (decoder cannot outrun the rendered frames).
- Cemu log for the session: encoder init 854x480@60, client connect +
  IDR clean, zero errors/failures/format complaints/device loss.
- Multiple connect/disconnect cycles across the day all recovered to
  video with no decoder crashes and no stuck black screens.
