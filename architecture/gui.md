# Architecture: GUI Subsystem (`src/gui/wxgui`)

## Overview
The Cemu GUI subsystem is built heavily on **wxWidgets**, a cross-platform C++ GUI framework. It handles the main window (`MainWindow`), input configuration dialogs (`InputSettings2`, `CemuPadPairingDialog`), game lists, and emulator settings.

## Design Patterns & Core Concepts

### 1. wxWidgets Event System
- Uses wxWidgets' event tables (or dynamic event connecting, though `wxDECLARE_EVENT_TABLE` is common) to map UI events (button clicks, menu selections, timers) to member functions.
- Custom events (e.g., `wxLaunchGameEvent`) are defined using `wxDECLARE_EVENT` and `wxCommandEvent` inheritance to decouple emulator logic from the UI.

### 2. Window Lifetime & Pointers
- UI elements (buttons, lists, dialogs) are typically instantiated via `new` and owned by their parent `wxWindow`. wxWidgets manages the destruction of child windows automatically when the parent is destroyed.
- Pointers to child controls are kept as class members (e.g., `wxButton* m_pairButton{nullptr}`) for easy access. They are usually created in an `InitUI()` method or the constructor.

### 3. Dialogs & Modality
- Secondary windows (like the Pairing Dialog) inherit from `wxDialog` (if they are modal and block input) or `wxFrame` (if they run alongside the main window).
- Periodic UI updates (like polling for devices or waiting for a PIN) are handled using `wxTimer` events on the main thread, rather than spinning up background threads that try to touch the UI (which wxWidgets forbids).

## Memory Management & Threading
- **UI Thread Rule:** *Only* the main UI thread can safely modify wxWidgets controls. Background tasks (like a `DiscoveryServer` searching for a phone) must either use a thread-safe queue checked via a `wxTimer` on the main thread, or post events (`wxQueueEvent`) to the UI thread.
- **Raw Pointers:** Because wxWidgets takes ownership of UI components (`new wxButton(parent, ...)`), raw pointers are standard and expected in this subsystem. Avoid `std::unique_ptr` for wxWidgets children unless they are top-level frames manually managed.

## Coding Style & Idioms
- **Naming Conventions:** 
  - Class members prefix with `m_` (e.g., `m_pollTimer`, `m_deviceList`).
  - Event handlers are consistently prefixed with `On` (e.g., `OnPairClicked`, `OnTimer`, `OnClose`).
- **Initialization:** Members are often initialized in the header using brace initialization (e.g., `wxTimer m_pollTimer; int m_pollTicks{0};`).

## Integration Guidelines (For Refactoring)
When creating or refactoring custom UI components (like `CemuPadPairingDialog`):
- Ensure all UI updates happen on the main thread. If a background socket receives an event, it should set a flag or post a wxEvent, and the UI should read it later (e.g., via `wxTimer::OnTimer`).
- Inherit properly from `wxDialog` and use standard `wxSizer` layouts (BoxSizer, FlexGridSizer) for cross-platform scaling rather than hardcoding pixel positions.
- Connect events cleanly using `Bind()` or event tables, ensuring teardown of timers (`m_timer.Stop()`) happens before destruction to avoid firing events on half-destroyed objects.
