# CemuPadServer Approach (DLL Injector)

## Concept
Create a standalone application (`CemuPadServer.exe`) that the user runs alongside the official Cemu release. To capture the Wii U GamePad screen without requiring the user to keep the GamePad window open, this server will inject a custom DLL (`cemupad_hook.dll`) into the running `Cemu.exe` process. 

## Architecture
1. **CemuPadServer.exe (The Host App)**
   * Manages discovery (`DiscoveryServer`) and pairing with the Android app.
   * Manages the H.264 video encoding (`VideoEncoder`) and streaming (`VideoStreamServer`).
   * Hosts a virtual controller (via ViGEmBus) or relays DSU motion/touch data to Cemu's local port.
   * Listens on a local IPC channel (e.g., Named Pipes or Shared Memory) for raw frame data from the injected DLL.

2. **cemupad_hook.dll (The Injector)**
   * Injected into `Cemu.exe` at runtime.
   * Hooks graphics APIs (Vulkan `vkQueuePresentKHR` and OpenGL `wglSwapBuffers`).
   * Identifies the render target associated with the GamePad (this is notoriously difficult since it requires heuristics to distinguish the GamePad texture from the TV texture if they aren't explicitly labeled in the API calls).
   * Copies the raw pixel buffer and sends it over IPC to `CemuPadServer.exe`.

## Pros
* **No Divergent Fork**: Users can use the official, vanilla Cemu release. No GitHub Actions rebase pipelines to maintain.
* **Separation of Concerns**: Cemu handles emulation, CemuPadServer handles streaming.

## Cons
* **Extreme Complexity**: Hooking Vulkan and OpenGL dynamically is very hard. Distinguishing the GamePad texture from the TV texture via a generic graphics hook (without native engine access) requires unreliable heuristics (like checking texture aspect ratios or dimensions).
* **High Maintenance**: Whenever Cemu changes how it issues draw calls, clears buffers, or manages swapchains, the heuristic hooks will break. 
* **Anti-Cheat/Antivirus**: DLL injection is often flagged by Windows Defender or third-party antivirus software as malicious behavior.
