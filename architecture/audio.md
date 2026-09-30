# Architecture: Audio & Mic Subsystem (`src/Cafe/OS/libs/mic`, `snd_core`)

## Overview
The audio subsystems handle hardware-level sound processing and Wii U microphone emulation. The `mic` module is an HLE (High-Level Emulation) implementation of the Wii U's `mic` shared library (`COSModule`). It bridges host PC microphones (via Cubeb/DirectSound) and network streams (via `CemuPadBridge`) into the emulated Wii U environment.

## Design Patterns & Core Concepts

### 1. HLE Module Pattern (`COSModule`)
- The microphone library implements the `COSModule` interface.
- It registers exported functions (e.g., `MICInit`, `MICOpen`, `MICGetStatus`) that the emulated PowerPC CPU calls during gameplay.
- These exported functions are mapped directly to `PPCInterpreter_t* hCPU` callbacks, where arguments are read from PowerPC registers (`hCPU->gpr[3]`, etc.) and results are written back or passed to `osLib_returnFromFunction`.

### 2. Audio Processing Loop
- Audio is processed synchronously with the main AX (Audio) frame updates (`mic_updateOnAXFrame`).
- The system feeds samples in small blocks (e.g., 3ms at 32kHz, which is 96 samples).
- If a real microphone (`g_inputAudio`) is not available or playing, it falls back to checking `CemuPadBridge::GetInstance().DequeueMicSamples()` for networked microphone audio.

### 3. Ring Buffers
- The emulated application allocates a ring buffer in emulated memory. The host implementation maps this buffer (`memory_getPointerFromVirtualOffset`) and writes host microphone samples into it directly.
- The `micStatus_t` and `micRingbuffer_t` structures enforce exact binary layouts (`uint32be`, big-endian) that the PowerPC application expects.

## Memory Management & Threading
- **Endianness:** All data written to the emulated application's memory must be strictly endian-swapped (`_swapEndianU16`, `_swapEndianU32`) because the Wii U uses Big-Endian while the host PC uses Little-Endian.
- **Mutexes:** Hardware audio resources are protected by shared mutexes (e.g., `std::shared_lock lock(g_audioInputMutex)`).
- **Static Status Structs:** The microphone status is maintained in a globally static struct (`MICStatus`), simulating the singleton nature of the hardware peripheral.

## Coding Style & Idioms
- **Naming Conventions:**
  - Emulated module exports use `micExport_FunctionName` (e.g., `micExport_MICInit`).
  - HLE state enums use `UPPER_SNAKE_CASE` (e.g., `MIC_STATUS_FLAGS`, `MIC_RESULT`).
- **Debugging:** Uses `debug_printf` extensively to trace PPC function calls and their arguments.
- **Data Types:** Uses specific fixed-width big-endian types (`uint32be`) provided by Cemu's base headers for structures directly mapping to guest memory.

## Integration Guidelines (For Refactoring)
When interacting with the audio or mic subsystems (e.g., streaming microphone data):
- Never block in the `mic_updateOnAXFrame` callback; it runs on the critical audio thread. Use non-blocking queues (like `DequeueMicSamples`).
- Ensure any data copied into the Wii U's ring buffers is appropriately converted to big-endian PCM16 if necessary, although currently the `_swapEndianU16` takes care of the swap internally within the feed function.
- Rely on standard locking (`g_audioInputMutex`) when interacting with global audio devices, but prefer lock-free queues for passing network audio buffers to avoid stalling the audio thread.
