# Brightness parity: phone darker than desktop (parked)

Date: 2026-09-12. Parked at user's request; revisit if needed.

## Observation

Same Controller Selection frame: desktop TV and desktop pad view match
each other (bright, washed chartreuse), phone renders darker and more
saturated (olive parchment, deep-green cards). Whites/blacks agree.
User confirms TV + pad view brighter than phone.

## Proven (numeric, phone PNG patches)

- No channel swap: inventory blue banner B=224>>R=63, gold title
  R=181>>B=73, green card G=180 dominant.
- Phone-vs-desktop parchment ratio ~0.63-0.90 per channel with blue
  lowest; luma drops desktop ~178 to phone ~135. Not a clean global
  gamma, but captures were minutes apart on an animated background, so
  patch statistics carry mottle noise.

## Ruled out

- 10-bit unpack bit order: R is bits 0-9 per Vulkan MSB-first naming,
  DXGI cross-check, and Cemu's own texture decoder. A swap would turn
  the blue banner orange; it is blue. (Harness doc note corrected.)
- NV12 conversion math: textbook BT.601 limited-range on inspection.
- sRGB output transform: `drcBufferUsesSRGB` is false for WW's
  `R10_G10_B10_A2_UNORM` (no SRGB bit), game/default gamma inert, so the
  pad view shows raw bytes exactly like our capture takes them.
- Missing MF matrix/range flags alone cannot explain a luma drop
  (encoder/decoder agree by default at 854x480; flags only matter if
  someone converts, and nobody does).
- Contrasty pack: user reports the difference persists with it disabled.
- Panel vivid mode: user switched S23 FE to Natural; helped some,
  residual remains. HDR is off on the desktop side.

## Still open hypotheses

- Encoder-side behavior inside the MS H.264 MFT (unverified black box).
- Android decode/display handling on this device.
- Residual AMOLED-vs-LCD perception in live viewing (files still differ,
  so this cannot be the whole story).

## Update: NV12 math proven lossless, pack ruled out for Mario

- Exact port of `ConvertRGBAToNV12` + standard BT.601-limited inverse
  roundtrips 11 test colors (primaries, parchment, grass, skin, measured
  desktop values) with max error +/-1 LSB. The conversion cannot produce
  the observed darkening.
- Mario boots with an EMPTY graphic-pack section: Contrasty was never
  active here, so the pack cannot explain the Mario gap either.
- Menu-button whites match desktop within ~6%; animated/textured-region
  comparisons are confounded by capture timing and content mismatch
  (map vs level), so they carry little weight.
- Net: unpack, NV12 math, sRGB path, and pack are all exonerated. No
  code change is justified until a synchronized same-content pair shows
  a real, reproducible delta.

## Next step when reopened

Synchronized same-moment capture pair (desktop snip + phone pull) and
flat-element comparison (white controllers, card flats, title text).
If flats match, perception/animation; if the luma drop reproduces,
replicate the pad-view output transform behind a LUT in the fork and
rebuild.
