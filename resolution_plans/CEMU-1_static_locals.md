# CEMU-1: Static Locals in `HandleStreamingCapture`

**Severity**: 🟢 Low  
**File**: `Cemu/src/Cafe/HW/Latte/Renderer/Vulkan/VulkanRenderer.cpp`  
**Lines**: 1055-1064

## Problem

All staging buffers, mapped pointers, command buffer IDs, frame counters, etc. in `HandleStreamingCapture()` are `static` local variables. This works because there's only one `VulkanRenderer` and one render thread, but it's a design smell that would break under any future multi-instance or multi-GPU scenario.

## Resolution

**No change recommended at this time.** Moving these to instance members of `VulkanRenderer` would be the correct fix, but it touches the Vulkan renderer class header which is shared with upstream Cemu. The risk of merge conflicts with upstream updates outweighs the benefit for a design smell that has no functional impact.

### Future Implementation (If Needed)

1. In `VulkanRenderer.h`, add private members:
   ```cpp
   // Streaming capture double buffer
   VkBuffer m_streamStagingBuffers[2]{ VK_NULL_HANDLE, VK_NULL_HANDLE };
   VkDeviceMemory m_streamStagingMemory[2]{ VK_NULL_HANDLE, VK_NULL_HANDLE };
   void* m_streamMappedPtrs[2]{ nullptr, nullptr };
   uint64 m_streamCommandBufferIds[2]{ 0, 0 };
   uint32 m_streamWidths[2]{ 0, 0 };
   uint32 m_streamHeights[2]{ 0, 0 };
   uint32 m_streamPitches[2]{ 0, 0 };
   StreamingPixelFormat m_streamPixelFormats[2]{ StreamingPixelFormat::Rgba8, StreamingPixelFormat::Rgba8 };
   int m_streamBufferIndex{ 0 };
   uint32 m_streamBufferSize{ 0 };
   int m_streamFrameCount{ 0 };
   ```

2. Replace all `s_` static locals in `HandleStreamingCapture()` with the `m_stream` members.

3. Add cleanup in the destructor or a dedicated `CleanupStreamingResources()` method.

## Status: Won't Fix (Acceptable)
