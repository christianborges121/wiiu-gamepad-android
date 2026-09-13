# Phase 4.1 Implementation Plan — Zero-Config Auto-Discovery & 1-Click Cemu UI Pairing

## 1. Overview & Objective

### Upstream Isolation Principle
To ensure our streaming work does not interfere with Cemu core developers or cause merge conflicts in upstream Cemu:
- **No invasive edits to core Cemu logic**: All discovery, pairing, and streaming servers are housed inside an isolated directory: [`Cemu/src/streaming/`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/streaming/).
- **Standard DSU Controller Architecture Preserved**: Rather than inventing a custom input path, clicking "Pair" programmatically configures Cemu's existing `DSUControllerProvider` and `ControllerFactory` APIs. To Cemu's core, the phone operates as a standard, fully compliant DSU GamePad.

### What This Accomplishes
1. **Bidirectional Zero-Config Auto-Discovery (UDP 26763)**:
   - Cemu and Android discover each other over local subnet broadcast without manual IP typing.
2. **Cemu UI: "Auto-Discover CemuPad" Button**:
   - Located directly inside Cemu's Input Settings dialog (`InputSettings2.cpp`) next to the "Add" button, plus in the main menu (`Options > Connect Android GamePad...`).
   - Opens a clean pairing window that scans the Wi-Fi network and lists discovered phones.
3. **1-Click Auto-Configuration**:
   - Selecting the phone and clicking **"Pair & Connect"** automatically:
     - Sets Controller 0 (VPAD) as emulated **Wii U GamePad**.
     - Configures the **DSU Client** provider pointing to `<Phone_IP>:26760`.
     - Applies the default Wii U GamePad button, axis, touch, and gyro mappings.
     - Saves `controllerProfiles/controller0.xml`.
     - Initiates the video and audio streams immediately.

---

## 2. Protocol Specification

- **Discovery Port**: `UDP 26763`
- **Broadcast Beacons**:
  - **Phone → Subnet**: `"CEMUPAD_DISCOVER"` sent to `255.255.255.255:26763`.
  - **PC → Subnet**: `"CEMU_DISCOVER"` sent to `255.255.255.255:26763`.
- **Response Format**:
  - String payload (UTF-8): `"CEMUPAD_HERE:<hostname>:<dsu_port>:<video_port>:<audio_port>"`
  - Example: `"CEMUPAD_HERE:Galaxy-S23-FE:26760:26761:26762\n"`

---

## 3. Implementation Checklist & Step-by-Step Code Modifications

- [ ] **Step 3.1: Implement DiscoveryServer in Cemu Subsystem**
  - [ ] Create `Cemu/src/streaming/DiscoveryServer.h` with `DiscoveredDevice` struct and thread-safe registry.
  - [ ] Create `Cemu/src/streaming/DiscoveryServer.cpp` listening on UDP `26763` for `"CEMUPAD_DISCOVER"` and `"CEMUPAD_HERE:"`.
  - [ ] Implement `BroadcastProbe()` sending `"CEMU_DISCOVER"` to `255.255.255.255:26763`.

#### [NEW] [`Cemu/src/streaming/DiscoveryServer.h`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/streaming/DiscoveryServer.h)
```cpp
#pragma once
#include <atomic>
#include <thread>
#include <vector>
#include <string>
#include <mutex>
#include <functional>

struct DiscoveredDevice
{
    std::string ip;
    std::string name;
    uint16_t dsuPort{26760};
    uint16_t videoPort{26761};
    uint16_t audioPort{26762};
    std::chrono::steady_clock::time_point lastSeen;
};

class DiscoveryServer
{
public:
    static DiscoveryServer& GetInstance();

    bool Start(uint16_t port = 26763);
    void Stop();
    void BroadcastProbe();

    std::vector<DiscoveredDevice> GetDiscoveredDevices();
    void SetDeviceDiscoveredCallback(std::function<void(const DiscoveredDevice&)> callback);

private:
    DiscoveryServer() = default;
    ~DiscoveryServer() { Stop(); }

    void WorkerLoop();

    std::atomic<bool> m_isRunning{false};
    std::thread m_workerThread;
    uint16_t m_port{26763};

    std::mutex m_mutex;
    std::vector<DiscoveredDevice> m_devices;
    std::function<void(const DiscoveredDevice&)> m_callback;
};
```

#### [NEW] [`Cemu/src/streaming/DiscoveryServer.cpp`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/streaming/DiscoveryServer.cpp)
Listens on UDP 26763 for `"CEMUPAD_DISCOVER"` and `"CEMUPAD_HERE:"` responses, maintaining a thread-safe list of active Android GamePads.

- [ ] **Step 3.2: Implement CemuPadBridge Auto-Configuration Integration**
  - [ ] Implement `Cemu/src/streaming/CemuPadBridge.h` singleton.
  - [ ] Implement `Cemu/src/streaming/CemuPadBridge.cpp` with `AutoConfigureDSUController(deviceIp, dsuPort)`.
  - [ ] Clear previous slot 0 controller, attach new `DSUClient`, apply default mappings, and save profile.

#### [NEW] [`Cemu/src/streaming/CemuPadBridge.h`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/streaming/CemuPadBridge.h)
The clean, single point of contact between Cemu and the streaming module:
```cpp
#pragma once
#include <string>
#include <cstdint>

class CemuPadBridge
{
public:
    static CemuPadBridge& GetInstance();

    void Initialize();
    void Shutdown();

    // 1-Click Programmatic DSU Configuration
    bool AutoConfigureDSUController(const std::string& deviceIp, uint16_t dsuPort = 26760);

    // Stream lifecycle
    bool StartStreaming(const std::string& targetIp);
    void StopStreaming();
};
```

#### [NEW] [`Cemu/src/streaming/CemuPadBridge.cpp`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/streaming/CemuPadBridge.cpp)
```cpp
#include "CemuPadBridge.h"
#include "DiscoveryServer.h"
#include "input/InputManager.h"
#include "input/ControllerFactory.h"
#include "Logging/CemuLogging.h"
#include <fmt/format.h>

CemuPadBridge& CemuPadBridge::GetInstance()
{
    static CemuPadBridge s_instance;
    return s_instance;
}

void CemuPadBridge::Initialize()
{
    DiscoveryServer::GetInstance().Start(26763);
}

void CemuPadBridge::Shutdown()
{
    DiscoveryServer::GetInstance().Stop();
    StopStreaming();
}

bool CemuPadBridge::AutoConfigureDSUController(const std::string& deviceIp, uint16_t dsuPort)
{
    auto& inputMgr = InputManager::instance();
    auto vpad = inputMgr.get_vpad_controller(0);
    if (!vpad)
    {
        cemuLog_log(LogType::Force, "CemuPadBridge: Controller 0 (VPAD) not found");
        return false;
    }

    std::string uuid = fmt::format("{}:{}", deviceIp, dsuPort);
    std::string displayName = fmt::format("CemuPad ({})", deviceIp);

    try
    {
        // 1. Instantiate official DSU Client controller via Cemu's factory
        auto controller = ControllerFactory::CreateController(InputAPI::DSUClient, uuid, displayName.c_str());
        if (!controller)
        {
            cemuLog_log(LogType::Force, "CemuPadBridge: Failed to create DSU controller for {}", uuid);
            return false;
        }

        // 2. Clear previous controller and bind newly discovered DSU controller
        vpad->clear_controllers();
        vpad->add_controller(controller);

        // 3. Apply Cemu default Wii U GamePad layout
        vpad->set_default_mapping(controller);

        // 4. Save to controller0.xml so it persists
        inputMgr.save_controller_profile(0);

        cemuLog_log(LogType::Force, "CemuPadBridge: Controller 0 successfully configured for {}", uuid);
        return true;
    }
    catch (const std::exception& e)
    {
        cemuLog_log(LogType::Force, "CemuPadBridge: Exception during controller configuration: {}", e.what());
        return false;
    }
}
```

- [ ] **Step 3.3: Implement CemuPadPairingDialog in Cemu GUI**
  - [ ] Implement `Cemu/src/gui/wxgui/input/CemuPadPairingDialog.h` header.
  - [ ] Implement `Cemu/src/gui/wxgui/input/CemuPadPairingDialog.cpp` with device list, timer polling, and pair action.

#### [NEW] [`Cemu/src/gui/wxgui/input/CemuPadPairingDialog.h`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/gui/wxgui/input/CemuPadPairingDialog.h)
```cpp
#pragma once
#include <wx/dialog.h>
#include <wx/listctrl.h>
#include <wx/button.h>
#include <wx/stattext.h>
#include <wx/timer.h>

class CemuPadPairingDialog : public wxDialog
{
public:
    CemuPadPairingDialog(wxWindow* parent);
    ~CemuPadPairingDialog();

private:
    void InitUI();
    void RefreshDeviceList();
    void OnPairClicked(wxCommandEvent& event);
    void OnRescanClicked(wxCommandEvent& event);
    void OnTimer(wxTimerEvent& event);

    wxListView* m_deviceList{nullptr};
    wxButton* m_pairButton{nullptr};
    wxButton* m_rescanButton{nullptr};
    wxStaticText* m_statusText{nullptr};
    wxTimer m_pollTimer;
};
```

#### [NEW] [`Cemu/src/gui/wxgui/input/CemuPadPairingDialog.cpp`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/gui/wxgui/input/CemuPadPairingDialog.cpp)
Implements the dialog with a clean wxListView showing:
- Device Name (e.g. `Samsung Galaxy S23 FE`)
- IP Address (`192.168.68.114`)
- Status (`Ready on port 26760`)
- **[ Pair & Connect ]** button: Triggers `CemuPadBridge::AutoConfigureDSUController(ip)` and starts video streaming.

- [ ] **Step 3.4: Integrate "Auto-Discover CemuPad..." Button in InputSettings2.cpp**
  - [ ] Add button to `controller_btn_sizer` next to the "Add" button.
  - [ ] Bind click event to show `CemuPadPairingDialog` and refresh controller list upon success.

#### [MODIFY] [`Cemu/src/gui/wxgui/input/InputSettings2.cpp`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/gui/wxgui/input/InputSettings2.cpp)
Next to the existing "Add" button on the Controller row, add:
```cpp
auto* cemupad_btn = new wxButton(this, wxID_ANY, _("Auto-Discover CemuPad"));
cemupad_btn->Bind(wxEVT_BUTTON, [this](wxCommandEvent&) {
    CemuPadPairingDialog dlg(this);
    if (dlg.ShowModal() == wxID_OK) {
        // Refresh controller UI to reflect the newly assigned DSU controller
        refresh_controllers();
    }
});
```

- [ ] **Step 3.5: Android Auto-Discovery Responder**
  - [ ] Listen on UDP 26763 in `android-gamepad-app` and respond with `"CEMUPAD_HERE:<device_name>:26760:26761:26762"`.
  - [ ] Update `MainScreen.kt` connection card to indicate broadcast status and show detected Cemu hosts.

#### [MODIFY] [`android-gamepad-app/app/src/main/java/com/cemupad/ui/main/MainScreen.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/ui/main/MainScreen.kt)
On the disconnected connection card:
- Displays: *"Broadcast discovery active on port 26763"*
- When Cemu broadcasts or responds, displays: *"Cemu Host Detected: <PC_Name>"* with a 1-tap **"Connect"** button.

---

## 4. Verification & Testing Checklist

- [ ] **4.1 Automated Build Verification**
  - [ ] Verify clean compilation of Cemu Release target:
    ```powershell
    cmake -B Cemu/build -S Cemu -DCMAKE_BUILD_TYPE=Release
    cmake --build Cemu/build --config Release --target Cemu
    ```
- [ ] **4.2 GUI Auto-Discovery Verification**
  - [ ] Open `Options > Input Settings`.
  - [ ] Click the new **"Auto-Discover CemuPad"** button.
  - [ ] Confirm dialog opens, broadcasts pings, and displays any running CemuPad app.
- [ ] **4.3 1-Click Auto-Configuration Verification**
  - [ ] Select detected phone and click **"Pair & Connect"**.
  - [ ] Verify `controllerProfiles/controller0.xml` is automatically generated with `InputAPI::DSUClient`.
  - [ ] Verify Controller 0 switches to Emulated: Wii U GamePad with all mappings populated.
  - [ ] Verify video and audio streaming start automatically.
