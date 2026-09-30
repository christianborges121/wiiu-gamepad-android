# Architecture: Input Subsystem (`src/input`)

## Overview
The `src/input` subsystem in Cemu handles abstracting physical input devices (keyboards, gamepads via SDL/XInput/DSU/DirectInput) and mapping them to emulated Wii U controllers (VPAD, Pro Controller, Classic Controller, etc.).

## Design Patterns & Core Concepts

### 1. Singleton Manager
- **`InputManager`** acts as the central hub and inherits from `Singleton<InputManager>`. It manages the lifecycle, saving/loading of profiles, and active controller states.
- It spins up a dedicated background thread (`m_update_thread`) that continuously updates input state in a polling loop.

### 2. Provider and Factory Patterns
- **Providers:** The system uses a Provider pattern to group implementations by API (e.g., `KeyboardControllerProvider`, `DSUControllerProvider`). These classes manage enumerating devices for their respective APIs.
- **`ControllerFactory`:** A factory class is responsible for instantiating both **Physical Controllers** (e.g., creating a controller from a UUID and API type) and **Emulated Controllers** (e.g., instantiating a `VPADController` or `ProController`).
- Registration uses modern C++ concepts (e.g., `template<std::derived_from<ControllerProviderBase> TProvider> void create_provider()`).

### 3. Abstraction Layers
- **Physical Layer (`ControllerBase`):** Represents an actual piece of hardware (or an API endpoint like DSU). 
- **Emulated Layer (`EmulatedController`):** Represents the emulated target (VPAD, Pro Controller).
- The `EmulatedController` maintains mappings from the Physical Layer's buttons/axes to the Emulated Layer's buttons/axes.

## Memory Management & Threading
- **Smart Pointers:** The codebase extensively uses `std::shared_ptr` and `std::weak_ptr` (e.g., `ControllerProviderPtr`, `EmulatedControllerPtr`) instead of raw pointers to manage lifetimes, preventing leaks when controllers disconnect or profiles switch.
- **Thread Safety:** State shared across the update thread and the main emulation thread is heavily protected using `std::shared_mutex` (`m_mutex`). 

## Coding Style & Idioms
- **Naming Conventions:** Class members use the `m_` prefix with `snake_case` (e.g., `m_update_thread`, `m_api_available`). Function names often use `snake_case` (e.g., `get_api_provider`, `set_controller`), though some older ones or OS-level overrides might use `PascalCase`.
- **Modern C++ Features:** 
  - Uses `std::string_view` for string parameters that don't need ownership.
  - Uses `std::array` instead of C-style arrays for fixed-size boundaries (e.g., `std::array<EmulatedControllerPtr, kMaxVPADControllers>`).
  - Uses `constexpr` for sizing variables.
- **Error Handling:** Relies on C++ exceptions (`try...catch`) during initialization and file parsing, logging failures via `cemuLog_log`.

## Serialization / Configuration
- **pugixml:** The `InputManager` saves and loads controller profiles using XML (via `pugixml`). It no longer uses INI for profiles, though it retains legacy INI migration code using `boost::property_tree::ini_parser`.

## Integration Guidelines (For Refactoring)
When making changes or adding to the input subsystem (like Gamepad streaming):
- **Avoid Global State:** Do not add global variables. Inject state into `InputManager` or register a new `ControllerProvider`.
- **Use Smart Pointers:** Always use `std::shared_ptr` or `std::unique_ptr` for dynamically allocated objects.
- **Protect Shared State:** Any state written by a network/streaming thread and read by the emulator thread must be wrapped in `std::shared_mutex` using `std::unique_lock` (for writes) and `std::shared_lock` (for reads).
