# Architecture: Renderer Subsystem (`src/Cafe/HW/Latte/Renderer`)

## Overview
The `Renderer` subsystem is the heart of Cemu's visual output, abstracting OpenGL, Vulkan, and (partially) Metal backends. It handles capturing the framebuffer of the emulated Latte GPU and displaying it, as well as piping outputs to our `StreamingCapture` and `VideoStreamServer` for Gamepad streaming.

## Design Patterns & Core Concepts

### 1. Abstract Base Class (`Renderer`)
- The `Renderer` class is a massive abstract base class that defines the contract for all rendering backends.
- Includes virtual functions for frame flushing, swapchains, reading back textures (`texture_createReadback`), and handling screenshot/streaming requests (`HandleStreamingCapture`).

### 2. Global State via `g_renderer`
- Unlike the `InputManager` which uses a Singleton mixin (`Singleton<T>`), the active Renderer is managed via a globally accessible smart pointer: `extern std::unique_ptr<Renderer> g_renderer`.

### 3. Streaming and Capture
- **`StreamingCapture`** (Our addition) is modeled as a Singleton (`GetInstance()`). It intercepts DRC (Gamepad) frames during the backend's swap cycle and queues them for off-thread processing.
- **`VideoEncoder`** (Our addition) handles the actual NVENC/AMF hardware encoding.
- **`VideoStreamServer`** (Our addition) handles networking (UDP/TCP encapsulation) to send encoded H.264 packets to the Android client. 

## Memory Management & Threading
- **Producer-Consumer Queue (`StreamingCapture`):** 
  - Uses `std::deque<CapturedFrame>` protected by `std::mutex` and signaled via `std::condition_variable`.
  - Frame data is copied into `std::vector<uint8>` before queuing so the rendering thread can continue without blocking.
- **Thread Lifetime (`VideoStreamServer`, `StreamingCapture`):**
  - Worker threads (`std::thread`) are explicitly joined during the `Shutdown()` phase. They use `std::atomic<bool>` (like `m_workerStopping`, `m_isRunning`) for safe teardown loops.
- **Raw Pointers for GL/Vulkan Views:** Functions like `OnNewDRCFrame(LatteTextureView* texView)` use raw pointers, implying that `StreamingCapture` does not assume ownership of the `texView` and must only use it synchronously before returning control to the renderer.

## Coding Style & Idioms
- **Naming Conventions:** Class members heavily use the `m_` prefix and camelCase/PascalCase (e.g., `m_rendererAPI`, `m_selectedDeviceName`, `m_screenshot_requested`).
- **Method Naming:** Cemu's `Renderer.h` uses an idiosyncratic pattern where virtual methods are prefixed by their domain: e.g., `renderTarget_setViewport`, `texture_acquireTextureUploadBuffer`, `bufferCache_init`. 
- **Modern C++:**
  - Employs `std::optional` for operations that might fail (e.g., screenshots).
  - Uses `std::span` (or `std::vector`) to pass contiguous memory safely.
  - Constants in networking/streaming are marked `constexpr` inside the class definition rather than `#define`.

## Integration Guidelines (For Refactoring)
When making changes to the streaming architecture:
- Maintain the strict separation of concerns: `StreamingCapture` extracts the frame, `VideoEncoder` compresses it, and `VideoStreamServer` transmits it.
- Never block the `Renderer` thread. All operations triggered via `HandleStreamingCapture` must be offloaded to worker threads using `std::mutex` and condition variables.
- Ensure any raw pointers provided by the `Renderer` (like `LatteTextureView*`) are consumed synchronously. If the data needs asynchronous processing, it must be copied or read-back into a `std::vector<uint8>` immediately.
