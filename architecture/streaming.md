# Architecture: Streaming Subsystem (`src/streaming`)

## Overview
The `src/streaming` subsystem is a custom addition for the CemuPad project. It bridges the gap between Cemu's core emulation logic and external Android devices over Wi-Fi. It handles network discovery, control socket management (TCP), video stream negotiation (UDP/TCP), and input packet parsing.

## Design Patterns & Core Concepts

### 1. Bridge Pattern (`CemuPadBridge`)
- `CemuPadBridge` acts as the primary orchestrator and a Singleton.
- It decouples the raw network sockets (`DiscoveryServer`, `VideoStreamServer`) from the rest of the Cemu codebase, preventing Cemu's input or rendering systems from needing to know about sockets or JSON payloads.

### 2. Network Discovery (`DiscoveryServer`)
- Runs a background UDP broadcast listener.
- Responds to `CemuPad?` pings with connection details, allowing phones to auto-discover the PC without IP configuration.
- Operates strictly asynchronously to avoid blocking.

### 3. Forward Error Correction (`ReedSolomon`)
- Incorporates RS coding for UDP packet recovery.
- Essential for maintaining video smoothness over lossy Wi-Fi networks where TCP retransmission delays would cause stuttering.

## Memory Management & Threading
- **Strict Thread Isolation:** The streaming subsystem operates on its own dedicated threads (e.g., `DiscoveryThreadFunc`). It must *never* block the main Cemu thread, the GUI thread, or the Audio/Render threads.
- **Lock-Free or Minimal-Lock Handoffs:** When passing data between the network thread and the input/audio threads, the architecture favors lock-free ring buffers or tightly scoped `std::mutex` locks.
- **Lifetime Management:** Worker threads are managed via `std::thread` and must be gracefully shut down (using `m_isRunning` atomics and `join()`) during the bridge's destructor to prevent crashes on exit.

## Coding Style & Idioms (Target Alignment)
To align with Cemu's core architecture, this subsystem should adhere to:
- **Naming Conventions:** Class members use the `m_` prefix. Methods use `PascalCase` (e.g., `StartServer()`, `BroadcastFrame()`).
- **Standard Types:** Prefer C++ standard fixed-width types (`uint32_t`, `int16_t`) or Cemu's typedefs (`uint32`, `sint16`).
- **Error Handling:** Use `cemuLog_log(LogType::Force, ...)` rather than `std::cout` for debugging. Avoid throwing exceptions across thread boundaries.

## Integration Guidelines (For Refactoring)
As we re-evaluate our changes against Cemu's original design:
- **Avoid Global State:** Do not inject raw global variables into Cemu's core files. If the GUI needs to talk to the Bridge, it should call `CemuPadBridge::GetInstance()`.
- **Observer/Event Patterns:** Instead of the Bridge directly manipulating `InputManager` internals, it should feed data through a registered `ControllerProvider` or use a thread-safe message queue.
- **Mutex Discipline:** Review all uses of `std::mutex` in `CemuPadBridge` and `VideoStreamServer`. Ensure we do not hold locks while calling out into unknown GUI or Input code (which could cause deadlocks, as seen in previous revisions).
