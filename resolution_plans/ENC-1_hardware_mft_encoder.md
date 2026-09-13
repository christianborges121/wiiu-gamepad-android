# ENC-1: Hardware MFT Encoder Enumeration

**Severity**: 🔴 High  
**File**: `Cemu/src/Cafe/HW/Latte/Renderer/VideoEncoder.cpp`  
**Lines**: 54-56

## Problem

The encoder uses `CoCreateInstance(CLSID_CMSH264EncoderMFT, ...)` which instantiates the **Microsoft software H.264 encoder**. This ignores available hardware encoders (NVENC, AMD AMF, Intel QuickSync) and wastes significant CPU at 854×480@60fps.

## Current Code (Lines 54-56)

```cpp
cemuLog_log(LogType::Force, "VideoEncoder: Activating standard Microsoft H.264 Encoder MFT...");
hr = CoCreateInstance(CLSID_CMSH264EncoderMFT, nullptr, CLSCTX_INPROC_SERVER,
    IID_IMFTransform, (void**)&m_pTransform);
```

## Resolution

Replace the direct `CoCreateInstance` with `MFTEnumEx` that tries hardware MFTs first, then falls back to software.

### Step 1: Add a private helper method to `VideoEncoder`

In `VideoEncoder.h`, add this private method declaration after line 38:

```cpp
IMFTransform* CreateBestEncoder();
```

### Step 2: Implement `CreateBestEncoder()` in `VideoEncoder.cpp`

Add this method **before** the `Initialize()` function (before line 36). This replaces the single `CoCreateInstance` call:

```cpp
IMFTransform* VideoEncoder::CreateBestEncoder()
{
#if defined(_WIN32)
    // Step 1: Try hardware MFTs first (NVENC, AMF, QuickSync)
    MFT_REGISTER_TYPE_INFO outputType = { MFMediaType_Video, MFVideoFormat_H264 };

    IMFActivate** ppActivate = nullptr;
    UINT32 count = 0;

    HRESULT hr = MFTEnumEx(
        MFT_CATEGORY_VIDEO_ENCODER,
        MFT_ENUM_FLAG_HARDWARE | MFT_ENUM_FLAG_SORTANDFILTER,
        nullptr,        // input type: any
        &outputType,    // output type: H.264
        &ppActivate,
        &count
    );

    if (SUCCEEDED(hr) && count > 0)
    {
        for (UINT32 i = 0; i < count; ++i)
        {
            IMFTransform* pTransform = nullptr;
            hr = ppActivate[i]->ActivateObject(IID_PPV_ARGS(&pTransform));
            if (SUCCEEDED(hr) && pTransform)
            {
                // Get friendly name for logging
                LPWSTR friendlyName = nullptr;
                UINT32 nameLen = 0;
                ppActivate[i]->GetAllocatedString(MFT_FRIENDLY_NAME_Attribute, &friendlyName, &nameLen);
                if (friendlyName)
                {
                    char nameBuf[256] = {};
                    WideCharToMultiByte(CP_UTF8, 0, friendlyName, -1, nameBuf, sizeof(nameBuf), nullptr, nullptr);
                    cemuLog_log(LogType::Force, "VideoEncoder: Using hardware encoder: {}", nameBuf);
                    CoTaskMemFree(friendlyName);
                }
                else
                {
                    cemuLog_log(LogType::Force, "VideoEncoder: Using hardware encoder (index {})", i);
                }

                // Free all activation objects
                for (UINT32 j = 0; j < count; ++j)
                    ppActivate[j]->Release();
                CoTaskMemFree(ppActivate);

                return pTransform;
            }
        }
        // Free activation objects if none worked
        for (UINT32 j = 0; j < count; ++j)
            ppActivate[j]->Release();
        CoTaskMemFree(ppActivate);
    }

    // Step 2: Try software MFTs as fallback
    hr = MFTEnumEx(
        MFT_CATEGORY_VIDEO_ENCODER,
        MFT_ENUM_FLAG_SYNCMFT | MFT_ENUM_FLAG_ASYNCMFT | MFT_ENUM_FLAG_SORTANDFILTER,
        nullptr,
        &outputType,
        &ppActivate,
        &count
    );

    if (SUCCEEDED(hr) && count > 0)
    {
        IMFTransform* pTransform = nullptr;
        hr = ppActivate[0]->ActivateObject(IID_PPV_ARGS(&pTransform));
        if (SUCCEEDED(hr) && pTransform)
        {
            cemuLog_log(LogType::Force, "VideoEncoder: Using software H.264 encoder (fallback)");
            for (UINT32 j = 0; j < count; ++j)
                ppActivate[j]->Release();
            CoTaskMemFree(ppActivate);
            return pTransform;
        }
        for (UINT32 j = 0; j < count; ++j)
            ppActivate[j]->Release();
        CoTaskMemFree(ppActivate);
    }

    // Step 3: Last resort — direct CLSID instantiation
    cemuLog_log(LogType::Force, "VideoEncoder: Falling back to CLSID_CMSH264EncoderMFT (software)");
    IMFTransform* pTransform = nullptr;
    hr = CoCreateInstance(CLSID_CMSH264EncoderMFT, nullptr, CLSCTX_INPROC_SERVER,
        IID_IMFTransform, (void**)&pTransform);
    if (SUCCEEDED(hr))
        return pTransform;

    return nullptr;
#else
    return nullptr;
#endif
}
```

### Step 3: Replace the `CoCreateInstance` call in `Initialize()`

In `VideoEncoder.cpp`, replace lines 54-62 (the `cemuLog_log` + `CoCreateInstance` + error check) with:

```cpp
m_pTransform = CreateBestEncoder();
if (!m_pTransform)
{
    cemuLog_log(LogType::Force, "VideoEncoder: Failed to create any H.264 encoder MFT");
    return false;
}
```

### Step 4: Add required header

Add to the top of `VideoEncoder.cpp` (after the existing `#include` block):

```cpp
#if defined(_WIN32)
#include <mfapi.h>  // Already included via VideoEncoder.h, but ensure MFTEnumEx is available
#endif
```

No new header is needed — `MFTEnumEx` is declared in `<mfapi.h>` which is already included via `VideoEncoder.h`.

## Verification

1. Build the Cemu fork: `cmake --build build --config RelWithDebInfo`
2. Launch Cemu and start a game
3. Check the Cemu log output for one of:
   - `"Using hardware encoder: NVIDIA H.264 Encoder MFT"` (or AMD/Intel equivalent)
   - `"Using software H.264 encoder (fallback)"` (if no hardware available)
4. Connect the Android app and verify video streaming still works
5. Compare CPU usage before/after — hardware encoder should drop CPU usage significantly
