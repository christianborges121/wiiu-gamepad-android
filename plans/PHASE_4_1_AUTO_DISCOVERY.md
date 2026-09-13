# Phase 4.1 Implementation Plan — Zero-Config UDP Broadcast Auto-Discovery

## 1. Overview & Objective
Currently, pairing an Android device with Cemu requires manual configuration:
- The user must enter their Android phone's IP address into Cemu's DSU Controller Settings (`192.168.x.x:26760`).
- The user must manually input the PC's IP address into the Android app or wait for a DSU handshake before video starts.

**Objective**:
Implement a lightweight UDP broadcast beacon and responder protocol operating on UDP port `26763`.
- **Cemu Backend**: Runs a background UDP listener on port `26763`. Upon receiving the string `"CEMUPAD_DISCOVER"`, it responds back to the sender with `"CEMUPAD_HERE:<hostname>:<dsu_port>:<video_port>:<audio_port>"`.
- **Android Frontend**: The existing `DiscoveryClient.kt` broadcasts `"CEMUPAD_DISCOVER"` to `255.255.255.255:26763`. When discovered, `MainScreen.kt` displays a "Discovered Cemu Host" card with a 1-tap **Connect** button, automatically connecting video, audio, and DSU.

---

## 2. Protocol Specification

- **Discovery Port**: `UDP 26763`
- **Broadcast Request**:
  - String payload (UTF-8): `"CEMUPAD_DISCOVER"`
  - Sent by Android client to subnet broadcast address `255.255.255.255:26763`.
- **Unicast Response**:
  - String payload (UTF-8): `"CEMUPAD_HERE:<hostname>:<dsu_port>:<video_port>:<audio_port>"`
  - Example: `"CEMUPAD_HERE:DESKTOP-GAMING:26760:26761:26762"`
  - Sent by Cemu directly back to the requesting client's IP and port.

---

## 3. Files to Modify & Create

### Cemu Backend (`Cemu`)

#### [NEW] [`Cemu/src/Cafe/HW/Latte/Renderer/DiscoveryServer.h`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/HW/Latte/Renderer/DiscoveryServer.h)
Header declaring the discovery responder thread.
```cpp
#pragma once
#include <atomic>
#include <thread>
#include <string>

class DiscoveryServer
{
public:
    static DiscoveryServer& GetInstance();

    bool Start(uint16 port = 26763);
    void Stop();
    bool IsRunning() const { return m_isRunning.load(); }

private:
    DiscoveryServer() = default;
    ~DiscoveryServer() { Stop(); }

    void WorkerLoop();

    std::atomic<bool> m_isRunning{false};
    std::thread m_workerThread;
    uint16 m_port{26763};
};
```

#### [NEW] [`Cemu/src/Cafe/HW/Latte/Renderer/DiscoveryServer.cpp`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/HW/Latte/Renderer/DiscoveryServer.cpp)
Implementation using standard Winsock UDP socket.
```cpp
#include "DiscoveryServer.h"
#include "VideoStreamServer.h"
#include "Common/cemu_assert.h"
#include "Logging/CemuLogging.h"

#ifdef _WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <winsock2.h>
#include <ws2tcpip.h>
#endif

DiscoveryServer& DiscoveryServer::GetInstance()
{
    static DiscoveryServer s_instance;
    return s_instance;
}

bool DiscoveryServer::Start(uint16 port)
{
    if (m_isRunning.load())
        return true;

    m_port = port;
    m_isRunning = true;
    m_workerThread = std::thread(&DiscoveryServer::WorkerLoop, this);
    cemuLog_log(LogType::Force, "DiscoveryServer: Started listening on UDP port {}", port);
    return true;
}

void DiscoveryServer::Stop()
{
    if (!m_isRunning.load())
        return;

    m_isRunning = false;
    if (m_workerThread.joinable())
        m_workerThread.join();

    cemuLog_log(LogType::Force, "DiscoveryServer: Stopped");
}

void DiscoveryServer::WorkerLoop()
{
    SOCKET sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (sock == INVALID_SOCKET)
    {
        cemuLog_log(LogType::Force, "DiscoveryServer: Failed to create socket");
        m_isRunning = false;
        return;
    }

    // Set non-blocking or short receive timeout (500ms) to allow clean termination
    DWORD timeoutMs = 500;
    setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, (const char*)&timeoutMs, sizeof(timeoutMs));

    BOOL reuse = TRUE;
    setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, (const char*)&reuse, sizeof(reuse));

    sockaddr_in bindAddr{};
    bindAddr.sin_family = AF_INET;
    bindAddr.sin_addr.s_addr = htonl(INADDR_ANY);
    bindAddr.sin_port = htons(m_port);

    if (bind(sock, (sockaddr*)&bindAddr, sizeof(bindAddr)) == SOCKET_ERROR)
    {
        cemuLog_log(LogType::Force, "DiscoveryServer: Bind failed on port {}", m_port);
        closesocket(sock);
        m_isRunning = false;
        return;
    }

    char hostName[128] = "Cemu-Host";
    gethostname(hostName, sizeof(hostName));

    char buffer[256];
    sockaddr_in clientAddr{};
    int clientAddrLen = sizeof(clientAddr);

    while (m_isRunning.load())
    {
        clientAddrLen = sizeof(clientAddr);
        int bytesReceived = recvfrom(sock, buffer, sizeof(buffer) - 1, 0, (sockaddr*)&clientAddr, &clientAddrLen);
        if (bytesReceived > 0)
        {
            buffer[bytesReceived] = '\0';
            if (strncmp(buffer, "CEMUPAD_DISCOVER", 16) == 0)
            {
                // Format: CEMUPAD_HERE:<hostname>:<dsu_port>:<video_port>:<audio_port>
                char response[256];
                int responseLen = snprintf(
                    response, sizeof(response),
                    "CEMUPAD_HERE:%s:26760:26761:26762\n",
                    hostName
                );

                sendto(sock, response, responseLen, 0, (sockaddr*)&clientAddr, clientAddrLen);
                cemuLog_log(LogType::Force, "DiscoveryServer: Responded to discovery probe from client");
            }
        }
    }

    closesocket(sock);
}
```

#### [MODIFY] [`Cemu/src/Cafe/HW/Latte/Renderer/VideoStreamServer.cpp`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/Cafe/HW/Latte/Renderer/VideoStreamServer.cpp)
- In `VideoStreamServer::Start(uint16 port)`:
  - Add call: `DiscoveryServer::GetInstance().Start(DISCOVERY_PORT);`
- In `VideoStreamServer::Stop()`:
  - Add call: `DiscoveryServer::GetInstance().Stop();`

---

### Android Frontend (`android-gamepad-app`)

#### [MODIFY] [`android-gamepad-app/app/src/main/java/com/cemupad/ui/main/MainScreen.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/ui/main/MainScreen.kt)
Enhance the connection help card shown when disconnected:
- When `discoveredServer != null` and `!isVideoStreaming`:
  - Show a highlighted banner: `"Discovered Cemu: ${discoveredServer.hostname} (${discoveredServer.ip})"`.
  - Include a prominent **"Connect to PC"** button calling `onConnectToServer(discoveredServer.ip)`.

---

## 4. Automated Testing & Build Verification

1. **Build Cemu Release**:
   ```powershell
   cmake --build c:\Projects\wiiu-gamepad-android\Cemu\build --config Release --target Cemu
   ```
2. **Deploy to EmuDeck**:
   ```powershell
   Copy-Item c:\Projects\wiiu-gamepad-android\Cemu\build\bin\Release\Cemu.exe C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu\Cemu.exe -Force
   ```
3. **Build and Test Android App**:
   ```powershell
   cd c:\Projects\wiiu-gamepad-android\android-gamepad-app
   .\gradlew.bat testDebugUnitTest
   .\gradlew.bat assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 5. Live In-Game Verification
1. Launch Cemu and start *Super Mario 3D World*.
2. Open CemuPad on the phone with no IP pre-configured.
3. Check Android logcat:
   ```powershell
   adb logcat -s DiscoveryClient
   ```
   **Expected**: `DiscoveryClient: Discovered Cemu at 192.168.x.x (DESKTOP-...)`.
4. Verify the discovered Cemu host appears on the main screen card.
5. Tap **Connect** and verify video starts within 500ms without entering any manual IP address.
