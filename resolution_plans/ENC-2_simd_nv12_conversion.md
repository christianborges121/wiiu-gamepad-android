# ENC-2: CPU-Bound Scalar NV12 Conversion

**Severity**: 🟡 Medium  
**File**: `Cemu/src/Cafe/HW/Latte/Renderer/VideoEncoder.cpp`  
**Lines**: 196-247

## Problem

`ConvertRGBAToNV12()` is a pixel-by-pixel C++ loop with no SIMD optimization. At 854×480 = 410,880 pixels per frame at 60fps, this consumes significant CPU time. The BT.601 coefficient math (multiply, shift, add, clamp) for each pixel is a good candidate for vectorization.

## Resolution

Add an SSE2 optimized fast path for the common BGRA8/RGBA8 case. Keep the scalar path as fallback for A2B10G10R10 and non-x86 platforms.

### Step 1: Add SSE2 header

In `VideoEncoder.cpp`, add this include after the existing includes (after line 4):

```cpp
#if defined(_MSC_VER) || defined(__SSE2__)
#include <emmintrin.h>  // SSE2 intrinsics
#define HAS_SSE2 1
#else
#define HAS_SSE2 0
#endif
```

### Step 2: Add an SSE2 row conversion helper

Add this function **before** `ConvertRGBAToNV12` (before line 196):

```cpp
#if HAS_SSE2
// Converts 8 RGBA/BGRA pixels to 8 Y values using BT.601 limited range.
// Input: 8 pixels as 32 bytes (8 x RGBA). Output: 8 Y bytes written to yOut.
static void ConvertRow8_SSE2(const uint8* src, uint8* yOut, bool isBgra)
{
	// BT.601: Y = ((66*R + 129*G + 25*B + 128) >> 8) + 16
	// Process 4 pixels at a time, twice
	for (int batch = 0; batch < 2; ++batch)
	{
		__m128i pix = _mm_loadu_si128(reinterpret_cast<const __m128i*>(src + batch * 16));
		// Unpack to 16-bit: pixel order is [R/B, G, B/R, A, R/B, G, B/R, A, ...]
		__m128i zero = _mm_setzero_si128();
		// Process pixel 0 and 1
		for (int p = 0; p < 4; ++p)
		{
			int off = p * 4;
			uint8 r = src[batch * 16 + off + (isBgra ? 2 : 0)];
			uint8 g = src[batch * 16 + off + 1];
			uint8 b = src[batch * 16 + off + (isBgra ? 0 : 2)];
			uint32 yVal = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
			yOut[batch * 4 + p] = static_cast<uint8>(std::clamp<uint32>(yVal, 16, 235));
		}
	}
}
#endif
```

> **NOTE**: A truly optimized SSE2/AVX2 implementation would use `_mm_maddubs_epi16` and operate on 16 pixels at a time. The above is a placeholder that reduces function call overhead. For a full SIMD rewrite, consider using the `libyuv` library (Google's open-source RGBA→NV12 converter) which is already highly optimized.

### Alternative: Use libyuv (Recommended for Production)

A better long-term solution is to use Google's `libyuv` library:

1. Add `libyuv` to `vcpkg.json`:
   ```json
   { "name": "libyuv" }
   ```

2. Replace `ConvertRGBAToNV12` with:
   ```cpp
   #include "libyuv.h"

   void VideoEncoder::ConvertRGBAToNV12(const uint8* pixels, uint32 srcWidth, uint32 srcHeight, uint32 pitch, StreamingPixelFormat pixelFormat, uint8* nv12Y, uint8* nv12UV)
   {
       if (pixelFormat == StreamingPixelFormat::Bgra8 || pixelFormat == StreamingPixelFormat::Rgba8)
       {
           auto convertFunc = (pixelFormat == StreamingPixelFormat::Bgra8)
               ? libyuv::ARGBToNV12  // BGRA in memory = ARGB in libyuv naming
               : libyuv::ABGRToNV12; // RGBA in memory = ABGR in libyuv naming

           if (srcWidth == m_width && srcHeight == m_height)
           {
               convertFunc(pixels, pitch, nv12Y, m_width, nv12UV, m_width, m_width, m_height);
               return;
           }
           // Scale then convert
           // ... (use libyuv::ARGBScale + convertFunc)
       }
       // Fallback to scalar for A2B10G10R10
       // ... (keep existing scalar loop for this format)
   }
   ```

This would be significantly faster than any hand-written SIMD and handles edge cases.

### Simplest Improvement (No New Dependencies)

If adding libyuv is too complex, the simplest improvement is to **skip the nearest-neighbor scaling** when source and destination dimensions match (which they always do when the DRC renders at 854×480):

In `ConvertRGBAToNV12` (line 200), add a fast-path at the beginning:

```cpp
void VideoEncoder::ConvertRGBAToNV12(const uint8* pixels, uint32 srcWidth, uint32 srcHeight, uint32 pitch, StreamingPixelFormat pixelFormat, uint8* nv12Y, uint8* nv12UV)
{
	const bool noScale = (srcWidth == m_width && srcHeight == m_height && pitch == srcWidth * 4);
	const bool sourceIsBgra = (pixelFormat == StreamingPixelFormat::Bgra8);
	const bool isA2B10G10R10 = (pixelFormat == StreamingPixelFormat::A2B10G10R10);
	const int rOff = (sourceIsBgra && !isA2B10G10R10) ? 2 : 0;
	const int bOff = (sourceIsBgra && !isA2B10G10R10) ? 0 : 2;

	for (uint32 y = 0; y < m_height; ++y)
	{
		const uint8* row = noScale
			? (pixels + y * pitch)
			: (pixels + ((y * srcHeight) / m_height) * pitch);
		uint8* yPlaneRow = nv12Y + (y * m_width);
		uint8* uvPlaneRow = nv12UV + ((y / 2) * m_width);

		for (uint32 x = 0; x < m_width; ++x)
		{
			const uint32 srcX = noScale ? x : ((x * srcWidth) / m_width);
			// ... rest of existing pixel conversion logic
```

This eliminates the per-pixel `(x * srcWidth) / m_width` and `(y * srcHeight) / m_height` divisions when no scaling is needed, which is the common case.

## Verification

1. Build the Cemu fork
2. Stream video to the Android app
3. Compare CPU usage with a profiler (Task Manager or VTune) before/after
4. Verify video quality is unchanged (no color shifts)
