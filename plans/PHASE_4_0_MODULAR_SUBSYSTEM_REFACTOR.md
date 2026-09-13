# Phase 4.0 Implementation Plan — CemuPad Subsystem Modularization & 1-Click Cemu UI Pairing

## 1. Overview & Objective

### Upstream Isolation Principle
To ensure our streaming work does not interfere with Cemu core developers or cause merge conflicts with upstream Cemu:
1. **Isolated Subsystem (`Cemu/src/streaming/`)**:
   - Extract all streaming logic, hardware encoders, network servers, and discovery responders into an isolated directory: [`Cemu/src/streaming/`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/streaming/).
   - Core Cemu subsystems (`Latte/Renderer`, `snd_core`, `vpad`, `EmulatedController`) only interact with the streaming subsystem via a thin, non-invasive delegate class ([`CemuPadBridge`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/streaming/CemuPadBridge.h)).
   - When streaming is inactive, the delegate calls are 100% no-op with zero runtime overhead and zero impact on other developers.
2. **1-Click Auto-Configuration via Cemu GUI**:
   - Add an **"Auto-Discover CemuPad"** button in Cemu's Input Settings ([`InputSettings2.cpp`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/gui/wxgui/input/InputSettings2.cpp)) and main menu (`Options > Connect Android GamePad...`).
   - Clicking the button opens a wxWidgets pairing dialog that detects running CemuPad Android apps over the local subnet.
   - Selecting a device programmatically configures Controller 0 as an emulated **Wii U GamePad** using Cemu's official **`DSUClient`** provider with `<Device_IP>:26760`, applying default mappings and saving `controller0.xml`.
   - **Zero manual IP typing, zero XML editing, and zero custom input hacks.**

---

## 2. Component Layout & Directory Structure

```text
Cemu/src/streaming/
├── CMakeLists.txt            // Independent static library (CemuStreaming)
├── CemuPadBridge.h           // Public observer/bridge interface
├── CemuPadBridge.cpp
├── DiscoveryServer.h         // UDP 26763 discovery responder & scanner
├── DiscoveryServer.cpp
├── VideoStreamServer.h       // TCP 26761 control & UDP 26761 video streamer
├── VideoStreamServer.cpp
├── VideoEncoder.h            // Hardware H.264 MFT video encoder
├── VideoEncoder.cpp
├── StreamingCapture.h        // Vulkan double-buffered GamePad framebuffer capture
├── StreamingCapture.cpp
└── AudioStreamServer.h       // UDP 26762 48 kHz stereo audio streamer
```

---

## 3. Implementation Checklist & Step-by-Step Code Modifications

- [ ] **Step 3.1: Subsystem Directory & Build Isolation**
  - [ ] Create `Cemu/src/streaming/` directory.
  - [ ] Move `VideoStreamServer.*`, `VideoEncoder.*`, and `StreamingCapture.*` into `Cemu/src/streaming/`.
  - [ ] Create `Cemu/src/streaming/CMakeLists.txt` compiling the `CemuStreaming` static library.
  - [ ] In `Cemu/src/CMakeLists.txt`, add `add_subdirectory(streaming)` and link `CemuStreaming` to `CemuBin`.

```cmake
# Cemu/src/streaming/CMakeLists.txt
add_library(CemuStreaming STATIC
    CemuPadBridge.cpp
    DiscoveryServer.cpp
    VideoStreamServer.cpp
    VideoEncoder.cpp
    StreamingCapture.cpp
)

target_include_directories(CemuStreaming PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_SOURCE_DIR}/src
    ${CMAKE_SOURCE_DIR}/src/Common
    ${CMAKE_SOURCE_DIR}/src/input
    ${CMAKE_SOURCE_DIR}/src/Cafe
)

target_link_libraries(CemuStreaming PUBLIC
    CemuCommon
    CemuInput
    CemuCafe
)

if(MSVC)
    target_link_libraries(CemuStreaming PUBLIC ws2_32 mfplat mfuuid mfreadwrite)
endif()
```

In [`Cemu/src/CMakeLists.txt`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/CMakeLists.txt), add:
```cmake
add_subdirectory(streaming)
```
And link `CemuStreaming` to `CemuBin`.

- [ ] **Step 3.2: Implement CemuPadBridge Delegate System**
  - [ ] Implement `CemuPadBridge.h` singleton and delegate interface.
  - [ ] Implement `CemuPadBridge.cpp` with lifecycle control.
  - [ ] Implement `AutoConfigureDSUController(deviceIp, dsuPort)` using Cemu's native `ControllerFactory` and `InputManager`.
  - [ ] Implement non-invasive delegates: `OnGamepadFrame`, `OnAudioDMA`, `OnVPADRumble`, `OnVPADClearRumble`.

#### [`Cemu/src/streaming/CemuPadBridge.h`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/streaming/CemuPadBridge.h)
```cpp
#pragma once
#include <cstdint>
#include <string>
#include <vector>
#include <memory>

class CemuPadBridge
{
public:
    static CemuPadBridge& GetInstance();

    void Initialize();
    void Shutdown();
    bool IsActive() const;

    // Non-invasive delegate hooks called by Cemu core
    void OnGamepadFrame(const uint8_t* rgbaPixels, uint32_t width, uint32_t height, uint64_t ptsUs);
    void OnAudioDMA(const void* pcmData, size_t byteSize);
    void OnVPADRumble(uint8_t channel, const uint8_t* pattern, uint8_t length);
    void OnVPADClearRumble(uint8_t channel);

    // Programmatic 1-Click DSU Controller Configuration
    bool AutoConfigureDSUController(const std::string& deviceIp, uint16_t dsuPort = 26760);

    // Stream Session Control
    bool StartStreaming(const std::string& clientIp);
    void StopStreaming();

private:
    CemuPadBridge() = default;
    ~CemuPadBridge() = default;
    std::atomic<bool> m_isActive{false};
};
```

#### [`Cemu/src/streaming/CemuPadBridge.cpp`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/streaming/CemuPadBridge.cpp)
```cpp
#include "CemuPadBridge.h"
#include "VideoStreamServer.h"
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
    VideoStreamServer::GetInstance().Start(26761);
    m_isActive = true;
    cemuLog_log(LogType::Force, "CemuPadBridge: Subsystem initialized");
}

void CemuPadBridge::Shutdown()
{
    m_isActive = false;
    VideoStreamServer::GetInstance().Stop();
    DiscoveryServer::GetInstance().Stop();
    cemuLog_log(LogType::Force, "CemuPadBridge: Subsystem stopped");
}

bool CemuPadBridge::IsActive() const
{
    return m_isActive.load();
}

void CemuPadBridge::OnAudioDMA(const void* pcmData, size_t byteSize)
{
    if (!m_isActive.load()) return;
    VideoStreamServer::GetInstance().BroadcastAudio(pcmData, byteSize);
}

void CemuPadBridge::OnVPADRumble(uint8_t channel, const uint8_t* pattern, uint8_t length)
{
    if (!m_isActive.load() || !pattern || length == 0) return;
    // Calculate duty cycle active ratio and envelope duration
    size_t activeCount = 0;
    int len = length;
    int byteIdx = 0;
    while (len > 0)
    {
        uint8_t p = pattern[byteIdx++];
        for (int j = 0; j < 8 && j < len; j += 2)
        {
            if (p & (3 << j)) activeCount++;
        }
        len -= 8;
    }
    uint16_t durationMs = std::max<uint16_t>(50, (length * 1000) / 60);
    uint8_t intensity = activeCount > 0 ? static_cast<uint8_t>((activeCount * 255) / (length / 2)) : 0;
    VideoStreamServer::GetInstance().BroadcastRumble(activeCount > 0, intensity, durationMs);
}

void CemuPadBridge::OnVPADClearRumble(uint8_t channel)
{
    if (!m_isActive.load()) return;
    VideoStreamServer::GetInstance().BroadcastRumble(false, 0, 0);
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
        // 1. Create standard DSUClient controller using Cemu's official factory
        auto controller = ControllerFactory::CreateController(InputAPI::DSUClient, uuid, displayName.c_str());
        if (!controller)
        {
            cemuLog_log(LogType::Force, "CemuPadBridge: Failed to create DSU controller for {}", uuid);
            return false;
        }

        // 2. Clear old controllers on slot 0 and assign new DSU controller
        vpad->clear_controllers();
        vpad->add_controller(controller);

        // 3. Apply standard Cemu default GamePad mappings
        vpad->set_default_mapping(controller);

        // 4. Save to controller0.xml
        inputMgr.save_controller_profile(0);

        cemuLog_log(LogType::Force, "CemuPadBridge: Successfully configured Controller 0 as DSU {}", uuid);
        return true;
    }
    catch (const std::exception& e)
    {
        cemuLog_log(LogType::Force, "CemuPadBridge: Exception configuring DSU controller: {}", e.what());
        return false;
    }
}
```

- [ ] **Step 3.3: Implement CemuPadPairingDialog in Cemu GUI**
  - [ ] Implement `Cemu/src/gui/wxgui/input/CemuPadPairingDialog.h` header.
  - [ ] Implement `Cemu/src/gui/wxgui/input/CemuPadPairingDialog.cpp` with device list, timer polling, and pair action.
  - [ ] Wire modal pairing action to call `CemuPadBridge::GetInstance().AutoConfigureDSUController(ipStr, 26760)`.

#### [`Cemu/src/gui/wxgui/input/CemuPadPairingDialog.h`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/gui/wxgui/input/CemuPadPairingDialog.h)
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

#### [`Cemu/src/gui/wxgui/input/CemuPadPairingDialog.cpp`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/gui/wxgui/input/CemuPadPairingDialog.cpp)
```cpp
#include "CemuPadPairingDialog.h"
#include "streaming/CemuPadBridge.h"
#include "streaming/DiscoveryServer.h"
#include <wx/sizer.h>
#include <wx/msgdlg.h>

CemuPadPairingDialog::CemuPadPairingDialog(wxWindow* parent)
    : wxDialog(parent, wxID_ANY, "Pair CemuPad Android GamePad", wxDefaultPosition, wxSize(480, 320), wxDEFAULT_DIALOG_STYLE | wxRESIZE_BORDER)
{
    InitUI();
    m_pollTimer.Bind(wxEVT_TIMER, &CemuPadPairingDialog::OnTimer, this);
    m_pollTimer.Start(1000);
    DiscoveryServer::GetInstance().BroadcastProbe();
}

CemuPadPairingDialog::~CemuPadPairingDialog()
{
    m_pollTimer.Stop();
}

void CemuPadPairingDialog::InitUI()
{
    auto* rootSizer = new wxBoxSizer(wxVERTICAL);

    auto* headerText = new wxStaticText(this, wxID_ANY, "Discovered CemuPad Devices on Local Wi-Fi:");
    rootSizer->Add(headerText, 0, wxALL, 10);

    m_deviceList = new wxListView(this, wxID_ANY, wxDefaultPosition, wxDefaultSize, wxLC_REPORT | wxLC_SINGLE_SEL);
    m_deviceList->AppendColumn("Device Name", wxLIST_FORMAT_LEFT, 200);
    m_deviceList->AppendColumn("IP Address", wxLIST_FORMAT_LEFT, 130);
    m_deviceList->AppendColumn("Status", wxLIST_FORMAT_LEFT, 100);
    rootSizer->Add(m_deviceList, 1, wxEXPAND | wxLEFT | wxRIGHT, 10);

    m_statusText = new wxStaticText(this, wxID_ANY, "Scanning for CemuPad devices on UDP 26763...");
    rootSizer->Add(m_statusText, 0, wxALL, 10);

    auto* btnSizer = new wxBoxSizer(wxHORIZONTAL);
    m_rescanButton = new wxButton(this, wxID_ANY, "Rescan");
    m_rescanButton->Bind(wxEVT_BUTTON, &CemuPadPairingDialog::OnRescanClicked, this);
    btnSizer->Add(m_rescanButton, 0, wxRIGHT, 8);

    btnSizer->AddStretchSpacer();

    m_pairButton = new wxButton(this, wxID_OK, "Pair & Connect");
    m_pairButton->Bind(wxEVT_BUTTON, &CemuPadPairingDialog::OnPairClicked, this);
    m_pairButton->Disable();
    btnSizer->Add(m_pairButton, 0, wxRIGHT, 8);

    auto* cancelBtn = new wxButton(this, wxID_CANCEL, "Cancel");
    btnSizer->Add(cancelBtn, 0);

    rootSizer->Add(btnSizer, 0, wxEXPAND | wxALL, 10);

    m_deviceList->Bind(wxEVT_LIST_ITEM_SELECTED, [this](wxListEvent&) {
        m_pairButton->Enable(m_deviceList->GetFirstSelected() != -1);
    });

    SetSizer(rootSizer);
}

void CemuPadPairingDialog::OnRescanClicked(wxCommandEvent&)
{
    DiscoveryServer::GetInstance().BroadcastProbe();
    RefreshDeviceList();
}

void CemuPadPairingDialog::OnTimer(wxTimerEvent&)
{
    RefreshDeviceList();
}

void CemuPadPairingDialog::RefreshDeviceList()
{
    auto devices = DiscoveryServer::GetInstance().GetDiscoveredDevices();
    m_deviceList->DeleteAllItems();
    long idx = 0;
    for (const auto& dev : devices)
    {
        long item = m_deviceList->InsertItem(idx, wxString::FromUTF8(dev.name));
        m_deviceList->SetItem(item, 1, wxString::FromUTF8(dev.ip));
        m_deviceList->SetItem(item, 2, "Ready");
        idx++;
    }
    if (devices.empty())
    {
        m_statusText->SetLabel("Scanning... Open CemuPad on your Android phone.");
    }
    else
    {
        m_statusText->SetLabel(wxString::Format("Found %zu device(s). Select your phone and click Pair & Connect.", devices.size()));
    }
}

void CemuPadPairingDialog::OnPairClicked(wxCommandEvent&)
{
    long sel = m_deviceList->GetFirstSelected();
    if (sel == -1) return;

    wxString ip = m_deviceList->GetItemText(sel, 1);
    std::string ipStr = ip.ToStdString();

    bool success = CemuPadBridge::GetInstance().AutoConfigureDSUController(ipStr, 26760);
    if (success)
    {
        EndModal(wxID_OK);
    }
    else
    {
        wxMessageBox("Failed to configure DSU controller. Ensure Cemu input settings are not locked.", "Error", wxOK | wxICON_ERROR, this);
    }
}
```

- [ ] **Step 3.4: Add the "Auto-Discover CemuPad..." Button to InputSettings2.cpp**
  - [ ] Include `CemuPadPairingDialog.h` in `Cemu/src/gui/wxgui/input/InputSettings2.cpp`.
  - [ ] Add `auto_discover_btn` to `controller_btn_sizer` next to the "Add" button.
  - [ ] Bind click event to display `CemuPadPairingDialog` and refresh the controller list upon successful pairing.

In [`Cemu/src/gui/wxgui/input/InputSettings2.cpp`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/gui/wxgui/input/InputSettings2.cpp):
Directly next to the existing **"Add"** button for controllers:
```cpp
#include "CemuPadPairingDialog.h"

// In InputSettings2 sizer setup:
auto* auto_discover_btn = new wxButton(this, wxID_ANY, _("Auto-Discover CemuPad..."));
auto_discover_btn->Bind(wxEVT_BUTTON, [this](wxCommandEvent&) {
    CemuPadPairingDialog dlg(this);
    if (dlg.ShowModal() == wxID_OK) {
        // Automatically refresh controller list to show newly paired CemuPad
        refresh_controllers();
    }
});
controller_btn_sizer->Add(auto_discover_btn, 0, wxALL, 5);
```

- [ ] **Step 3.5: Clean Up Core Cemu Files (Non-Invasive 1-Line Hooks)**
  - [ ] Update `vpad.cpp`: Forward rumble calls to `CemuPadBridge::GetInstance().OnVPADRumble(...)` and `OnVPADClearRumble(...)`.
  - [ ] Update `snd_core.cpp`: Forward DRC audio DMA samples to `CemuPadBridge::GetInstance().OnAudioDMA(...)`.
  - [ ] Update `StreamingCapture.cpp`: Forward Vulkan readback frame to `CemuPadBridge::GetInstance().OnGamepadFrame(...)`.

1. **[`Cemu/src/Cafe/OS/libs/vpad/vpad.cpp`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/OS/libs/vpad/vpad.cpp)**:
   In `vpadExport_VPADControlMotor`:
   ```cpp
   CemuPadBridge::GetInstance().OnVPADRumble(channel, pattern, length);
   ```
   In `vpadExport_VPADStopMotor`:
   ```cpp
   CemuPadBridge::GetInstance().OnVPADClearRumble(channel);
   ```
2. **[`Cemu/src/Cafe/OS/libs/snd_core/snd_core.cpp`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/OS/libs/snd_core/snd_core.cpp)**:
   In DRC DMA sample output:
   ```cpp
   CemuPadBridge::GetInstance().OnAudioDMA(buffer, byteSize);
   ```
3. **[`Cemu/src/Cafe/HW/Latte/Renderer/StreamingCapture.cpp`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/HW/Latte/Renderer/StreamingCapture.cpp)**:
   Forward readback texture pixels directly to `CemuPadBridge::GetInstance().OnGamepadFrame(...)`.

---

## 4. Verification & Testing Checklist

- [ ] **4.1 Automated Build Verification**
  - [ ] Run CMake configure and compile the `Cemu` target:
    ```powershell
    cmake -B Cemu/build -S Cemu -DCMAKE_BUILD_TYPE=Release
    cmake --build Cemu/build --config Release --target Cemu
    ```
- [ ] **4.2 Binary Deployment**
  - [ ] Stop any running Cemu process and deploy:
    ```powershell
    Stop-Process -Name Cemu -Force -ErrorAction SilentlyContinue
    Copy-Item c:\Projects\wiiu-gamepad-android\Cemu\build\bin\Release\Cemu.exe C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu\Cemu.exe -Force
    ```
- [ ] **4.3 Live Cemu GUI & 1-Click Pairing Verification**
  - [ ] Launch Cemu standalone:
    ```powershell
    Start-Process "C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu\Cemu.exe" -WorkingDirectory "C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu"
    ```
  - [ ] Open `Options > Input Settings`.
  - [ ] Confirm **"Auto-Discover CemuPad..."** button appears next to the Add button.
  - [ ] Click button → verify dialog scans UDP `26763` and lists the active Android phone.
  - [ ] Select phone and click **"Pair & Connect"**.
  - [ ] Confirm `C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu\controllerProfiles\controller0.xml` is populated with `InputAPI::DSUClient` on `<phone_ip>:26760`.
- [ ] **4.4 Direct Game Launch & Live Telemetry Verification**
  - [ ] Launch *Super Mario 3D World* via command line:
    ```powershell
    Stop-Process -Name Cemu -Force -ErrorAction SilentlyContinue
    Start-Process "C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu\Cemu.exe" -ArgumentList '-g "D:\Emulation\roms\wiiu\SUPER MARIO 3D WORLD (US).wua"' -WorkingDirectory "C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu"
    ```
  - [ ] Inspect host Cemu log file:
    ```powershell
    Get-Content "C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu\log.txt" -Tail 30
    ```
  - [ ] Inspect Android phone logs:
    ```powershell
    adb logcat -d -s CemuPad:*
    ```
  - [ ] Capture phone screenshot to verify rendering:
    ```powershell
    adb exec-out screencap -p > c:\Projects\wiiu-gamepad-android\phone_screen.png
    ```
  - [ ] Verify video stream, audio, touch, gyro, and rumble immediately work without manual intervention.
