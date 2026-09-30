# Phase 10: Security Hardening — Code-Level Implementation Plan

> **For: lower-level free model** — follow line numbers exactly, keep formatting, verify with `.\gradlew.bat testDebugUnitTest` and `cmake --build Cemu/build --config Release --target CemuBin` before checking off. Each step is isolated and safe to commit independently.

## 1. Objective

Resolve **all 10 actionable findings** from the Security & Safety Review. Covers both Cemu (C++) and CemuPad Android (Kotlin) changes. No protocol-breaking changes — existing clients continue to work.

---

## 2. Pre-Flight (read first)

* Active branch: `feature/phase-7-wip` (or `main`). All Phase 8 & 9 tasks are `[x]`.
* **Cemu build**: `cmake --build c:\Projects\wiiu-gamepad-android\Cemu\build --config Release --target CemuBin`
* **Cemu deploy**: `Stop-Process -Name Cemu -Force ; Copy-Item c:\Projects\wiiu-gamepad-android\Cemu\build\bin\Release\Cemu.exe C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu\Cemu.exe -Force`
* **Android build**: `cd c:\Projects\wiiu-gamepad-android\android-gamepad-app ; .\gradlew.bat testDebugUnitTest assembleDebug`
* **Android deploy**: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
* Source paths are from project root `c:\Projects\wiiu-gamepad-android\`.

---

## 3. Implementation Checklist

---

### Step 10.0 — Default PIN to ON (Finding #1, #3)

**Goal:** Flip the default so new installs require a PIN. Add auth attempt rate-limiting. Widen the PIN to 6 digits.

#### 10.0.1 — Default `m_requirePin` to `true`
**File:** `Cemu/src/streaming/CemuPadBridge.h`
**Line 111:**
```diff
-	std::atomic<bool> m_requirePin{false};
+	std::atomic<bool> m_requirePin{true};
```
- [x] Done

#### 10.0.2 — Widen PIN to 6 digits (100,000–999,999 vs 9,000 values)
**File:** `Cemu/src/streaming/CemuPadBridge.cpp`
**Line 306:**
```diff
-	std::uniform_int_distribution<uint32_t> dist(1000, 9999);
+	std::uniform_int_distribution<uint32_t> dist(100000, 999999);
```
- [x] Done

#### 10.0.3 — Update PIN format string to 6 digits
**File:** `Cemu/src/streaming/CemuPadBridge.cpp`
**Line 296:**
```diff
-		required ? fmt::format("required (PIN {:04d})", m_currentPin.load()) : "not required");
+		required ? fmt::format("required (PIN {:06d})", m_currentPin.load()) : "not required");
```
- [x] Done

#### 10.0.4 — Add rate-limiting to `Authenticate()`
**File:** `Cemu/src/streaming/CemuPadBridge.h`
Add these members **after line 115** (after `m_tokenRng`):
```cpp
	std::atomic<uint32_t> m_authFailCount{0};
	std::chrono::steady_clock::time_point m_lastAuthFail{};
```
- [x] Done

**File:** `Cemu/src/streaming/CemuPadBridge.cpp`
**Line 312** — at the start of `Authenticate()`, **before** `outToken = 0;`, insert:
```cpp
	// Rate-limit: after 3 consecutive failures, lock out for 30 seconds.
	if (m_authFailCount.load() >= 3)
	{
		auto now = std::chrono::steady_clock::now();
		auto elapsed = std::chrono::duration_cast<std::chrono::seconds>(now - m_lastAuthFail).count();
		if (elapsed < 30)
		{
			cemuLog_log(LogType::Force, "CemuPadBridge: Auth rate-limited ({} seconds remaining)", 30 - elapsed);
			outToken = 0;
			return false;
		}
		m_authFailCount.store(0); // cooldown expired, reset
	}
```

Then at **line 358** (the final `return false;` at the end of `Authenticate()`), replace:
```diff
-	return false;
+	m_authFailCount.fetch_add(1);
+	m_lastAuthFail = std::chrono::steady_clock::now();
+	return false;
```

And after each **successful** auth path (line 337 `outToken = issueToken(); return true;` and line 347 `outToken = token; return true;` and line 356 `outToken = issueToken(); return true;`), **add before each `return true;`**:
```cpp
		m_authFailCount.store(0);
```
- [x] Done

#### 10.0.5 — Update Android PIN dialog to accept 6 digits
**File:** `android-gamepad-app/app/src/main/java/com/cemupad/MainActivity.kt`
Search for `"Enter the 4-digit"` or similar PIN prompt text. Change the prompt from referencing "4-digit" to "6-digit". Also search for any `maxLength` or digit length validation and update from 4 to 6.

**Grep command to find exact lines:**
```powershell
Select-String -Path "android-gamepad-app\app\src\main\java\com\cemupad\MainActivity.kt" -Pattern "4-digit|4.digit|maxLength.*4|length.*4.*pin" -CaseSensitive:$false
```
Update every occurrence.
- [x] Done

---

### Step 10.1 — Authenticate Mic UDP Port (Finding #2)

**Goal:** Only accept mic datagrams from IPs that have an authorized TCP session.

#### 10.1.1 — Add authorized-IP tracking
**File:** `Cemu/src/Cafe/HW/Latte/Renderer/VideoStreamServer.h`
**After line 125** (`mutable std::mutex m_clientsMutex;`), add:
```cpp
	// Set of authorized client IPs (uint32 host-order) for mic UDP filtering.
	// Updated when clients authenticate or disconnect.
	mutable std::mutex m_authorizedIpsMutex;
	std::set<uint32_t> m_authorizedIps;
```
Also add `#include <set>` at the top of the file (**after line 5**, after `#include <mutex>`):
```cpp
#include <set>
```
- [x] Done

#### 10.1.2 — Populate authorized IPs on auth success
**File:** `Cemu/src/Cafe/HW/Latte/Renderer/VideoStreamServer.cpp`
**Line 708** — after `SetClientAuthorized(clientSocket, true);`, add:
```cpp
				// Track authorized IP for mic UDP filtering
				{
					uint32_t clientIpHost = 0;
					{
						std::lock_guard<std::mutex> clientLock(m_clientsMutex);
						for (const auto& client : m_clients)
						{
							if (client.socket == clientSocket)
							{
								clientIpHost = ntohl(client.addr.sin_addr.s_addr);
								break;
							}
						}
					}
					if (clientIpHost != 0)
					{
						std::lock_guard<std::mutex> ipLock(m_authorizedIpsMutex);
						m_authorizedIps.insert(clientIpHost);
					}
				}
```
- [x] Done

#### 10.1.3 — Remove authorized IP on client disconnect
**File:** `Cemu/src/Cafe/HW/Latte/Renderer/VideoStreamServer.cpp`
**Line 867-878** — Replace the existing block at lines 867-878:

```cpp
	{
		std::lock_guard<std::mutex> lock(m_clientsMutex);
		for (auto it = m_clients.begin(); it != m_clients.end(); ++it)
		{
			if (it->socket == clientSocket)
			{
				uint32_t disconnectedIp = ntohl(it->addr.sin_addr.s_addr);
				m_clients.erase(it);
				cemuLog_log(LogType::Force, "VideoStreamServer: Client control connection closed");
				// Revoke mic access if no other authorized client shares this IP
				bool ipStillAuthorized = false;
				for (const auto& c : m_clients)
				{
					if (c.authorized && ntohl(c.addr.sin_addr.s_addr) == disconnectedIp)
					{
						ipStillAuthorized = true;
						break;
					}
				}
				if (!ipStillAuthorized)
				{
					std::lock_guard<std::mutex> ipLock(m_authorizedIpsMutex);
					m_authorizedIps.erase(disconnectedIp);
				}
				break;
			}
		}
	}
```
- [x] Done

#### 10.1.4 — Filter mic datagrams by authorized IP
**File:** `Cemu/src/Cafe/HW/Latte/Renderer/VideoStreamServer.cpp`
**Line 925** — after `if (bytes <= 8) continue;`, add:
```cpp
		// Only accept mic audio from authorized (TCP-authenticated) clients
		{
			uint32_t senderIpHost = ntohl(sender.sin_addr.s_addr);
			std::lock_guard<std::mutex> ipLock(m_authorizedIpsMutex);
			if (m_authorizedIps.find(senderIpHost) == m_authorizedIps.end())
				continue;
		}
```
- [x] Done

#### 10.1.5 — Clear authorized IPs on Stop()
**File:** `Cemu/src/Cafe/HW/Latte/Renderer/VideoStreamServer.cpp`
**Line 112** — inside `Stop()`, after the `m_clientsMutex` lock scope closes (line 113's `}`), add:
```cpp
	{
		std::lock_guard<std::mutex> ipLock(m_authorizedIpsMutex);
		m_authorizedIps.clear();
	}
```
- [x] Done

---

### Step 10.2 — Cap Detached Client Threads (Finding #5)

**Goal:** Limit concurrent TCP connections to 4. Track threads for clean shutdown.

#### 10.2.1 — Add max-client limit
**File:** `Cemu/src/Cafe/HW/Latte/Renderer/VideoStreamServer.h`
**After line 88** (after `static constexpr size_t UDP_MAX_PAYLOAD = 1400;`), add:
```cpp
	static constexpr size_t MAX_CLIENTS = 4;
```
- [x] Done

#### 10.2.2 — Reject connections over limit
**File:** `Cemu/src/Cafe/HW/Latte/Renderer/VideoStreamServer.cpp`
**Line 555** — after `cemuLog_log(LogType::Force, "VideoStreamServer: Android client connected to video stream!");`, and **before** the `{` block at line 557 that adds to `m_clients`, add:
```cpp
		// Enforce maximum client limit
		{
			std::lock_guard<std::mutex> lock(m_clientsMutex);
			if (m_clients.size() >= MAX_CLIENTS)
			{
				cemuLog_log(LogType::Force, "VideoStreamServer: Rejecting client — max {} connections reached", MAX_CLIENTS);
				CloseSocket(clientSock);
				continue;
			}
		}
```
- [x] Done

---

### Step 10.3 — Clamp Remote-Configurable Encoder Parameters (Finding #9)

**Goal:** Clamp bitrate to 500 Kbps – 50 Mbps. Validate resolution.

#### 10.3.1 — Clamp bitrate in opcode handler
**File:** `Cemu/src/Cafe/HW/Latte/Renderer/VideoStreamServer.cpp`
**Lines 746-753** — replace the bitrate handler body:
```diff
 			const uint32 bitrate =
 				static_cast<uint32>(payload[0]) |
 				(static_cast<uint32>(payload[1]) << 8) |
 				(static_cast<uint32>(payload[2]) << 16) |
 				(static_cast<uint32>(payload[3]) << 24);
-			cemuLog_log(LogType::Force, "VideoStreamServer: Received SET_BITRATE = {} bps", bitrate);
-			VideoEncoder::GetInstance().SetBitrate(bitrate);
+			constexpr uint32 kMinBitrate = 500000;   // 500 Kbps
+			constexpr uint32 kMaxBitrate = 50000000;  // 50 Mbps
+			const uint32 clampedBitrate = std::max(kMinBitrate, std::min(kMaxBitrate, bitrate));
+			cemuLog_log(LogType::Force, "VideoStreamServer: Received SET_BITRATE = {} bps (clamped to {})", bitrate, clampedBitrate);
+			VideoEncoder::GetInstance().SetBitrate(clampedBitrate);
```
- [x] Done

#### 10.3.2 — Validate resolution in opcode handler
**File:** `Cemu/src/Cafe/HW/Latte/Renderer/VideoStreamServer.cpp`
**Lines 760-768** — after parsing width/height, add validation:
```diff
 			const uint16 width = static_cast<uint16>(payload[0] | (payload[1] << 8));
 			const uint16 height = static_cast<uint16>(payload[2] | (payload[3] << 8));
-			cemuLog_log(LogType::Force, "VideoStreamServer: Received SET_RESOLUTION = {}x{}", width, height);
-			if (VideoEncoder::GetInstance().SetResolution(width, height))
+			// Sanity bounds: minimum 320x240, maximum 1920x1080
+			if (width < 320 || width > 1920 || height < 240 || height > 1080)
+			{
+				cemuLog_log(LogType::Force, "VideoStreamServer: Rejected SET_RESOLUTION {}x{} (out of allowed range)", width, height);
+			}
+			else
 			{
-				// New SPS/PPS: force a keyframe so the phone re-syncs immediately.
-				VideoEncoder::GetInstance().RequestKeyframe();
+				cemuLog_log(LogType::Force, "VideoStreamServer: Received SET_RESOLUTION = {}x{}", width, height);
+				if (VideoEncoder::GetInstance().SetResolution(width, height))
+				{
+					// New SPS/PPS: force a keyframe so the phone re-syncs immediately.
+					VideoEncoder::GetInstance().RequestKeyframe();
+				}
 			}
```
- [x] Done

---

### Step 10.4 — Rate-Limit `OPCODE_PUSH_MAPPINGS` Disk Writes (Finding #7 partial)

**Goal:** Prevent rapid disk writes from repeated push-mapping commands.

#### 10.4.1 — Add rate-limit to push mappings handler
**File:** `Cemu/src/Cafe/HW/Latte/Renderer/VideoStreamServer.h`
Add to private section (**after line 122**, after `m_lastBitrateAdapt`):
```cpp
	std::chrono::steady_clock::time_point m_lastMappingPush{ std::chrono::steady_clock::now() };
```
- [x] Done

#### 10.4.2 — Apply rate-limit in handler
**File:** `Cemu/src/Cafe/HW/Latte/Renderer/VideoStreamServer.cpp`
**Line 848** — before `bool ok = CemuPadBridge::GetInstance().ApplyPushedMappings(entries, true);`, add:
```cpp
			// Rate-limit mapping pushes to once per 5 seconds to prevent disk write flooding
			auto now = std::chrono::steady_clock::now();
			if (now - m_lastMappingPush < std::chrono::seconds(5))
			{
				cemuLog_log(LogType::Force, "VideoStreamServer: PUSH_MAPPINGS rate-limited, try again later");
				uint8 status = 0x01;
				send(s, reinterpret_cast<const char*>(&status), 1, kSendFlags);
				continue;
			}
			m_lastMappingPush = now;
```
- [x] Done

---

### Step 10.5 — Set `android:allowBackup="false"` (Finding #4)

**Goal:** Prevent ADB backup from leaking auth tokens and settings.

#### 10.5.1 — Disable backup
**File:** `android-gamepad-app/app/src/main/AndroidManifest.xml`
**Line 16:**
```diff
-        android:allowBackup="true"
+        android:allowBackup="false"
```
- [x] Done

---

### Step 10.6 — Remove `local.properties` from Git (Finding #10)

**Goal:** Stop tracking the file that leaks the local SDK path and username.

#### 10.6.1 — Untrack the file
Run:
```powershell
cd c:\Projects\wiiu-gamepad-android\android-gamepad-app
git rm --cached local.properties
```
- [x] Done

#### 10.6.2 — Verify `.gitignore` covers it
**File:** `android-gamepad-app/.gitignore`
Ensure this line exists (it likely does, but the file was committed before the ignore):
```
local.properties
```
If missing, add it.
- [x] Done

---

### Step 10.7 — Audio Port Authentication on Android Side (Finding #2, Android complement)

**Goal:** The Android mic sender should include a marker so a future protocol can validate. For now, since the server filters by IP (Step 10.1), no wire change is needed. Just document.

#### 10.7.1 — Add comment to mic sender
**File:** `android-gamepad-app/app/src/main/java/com/cemupad/audio/` — find the mic send file.

**Grep to find it:**
```powershell
Select-String -Path "android-gamepad-app\app\src\main\java\com\cemupad\audio\*.kt" -Pattern "26764|MIC_PORT|mic.*send" -CaseSensitive:$false
```

Add a comment near the UDP send explaining:
```kotlin
// Security note: Cemu filters mic UDP by authorized TCP client IP.
// The phone must have an authenticated TCP video session before mic audio is accepted.
```
- [x] Done

---

### Step 10.8 — PIN UI Restoration in CemuPadPairingDialog (Cemu GUI)

**Goal:** The pairing dialog has the PIN UI commented out (line 45-48 of `CemuPadPairingDialog.cpp`). Now that PIN defaults to ON, add a display of the current PIN and a toggle checkbox.

#### 10.8.1 — Add PIN display and toggle to `InitUI()`
**File:** `Cemu/src/gui/wxgui/input/CemuPadPairingDialog.h`
Add members **after line 33** (after `int m_pollTicks{0};`):
```cpp
	wxStaticText* m_pinLabel{nullptr};
	wxCheckBox* m_pinCheckbox{nullptr};
```
Also add:
```cpp
	void OnPinToggle(wxCommandEvent& event);
	void UpdatePinDisplay();
```
- [x] Done

**File:** `Cemu/src/gui/wxgui/input/CemuPadPairingDialog.cpp`
Add `#include <wx/checkbox.h>` at the top after line 4 (`#include <wx/msgdlg.h>`).

**Line 45-48** — Replace the NOTE comment block:
```diff
-	// NOTE (2026-09-13): session PIN UI disabled per user decision — the
-	// pairing flow stays open-session. Bridge PIN API + server auth remain
-	// compiled (open sessions auto-approve); restore a checkbox calling
-	// CemuPadBridge::SetRequirePin()/GetCurrentPin() to re-enable.
+	// Session PIN security
+	auto& bridge = CemuPadBridge::GetInstance();
+	m_pinCheckbox = new wxCheckBox(this, wxID_ANY, "Require PIN to connect");
+	m_pinCheckbox->SetValue(bridge.IsPinRequired());
+	m_pinCheckbox->Bind(wxEVT_CHECKBOX, &CemuPadPairingDialog::OnPinToggle, this);
+	rootSizer->Add(m_pinCheckbox, 0, wxLEFT | wxRIGHT, 10);
+
+	uint32_t pin = bridge.GetCurrentPin();
+	if (pin == 0) pin = bridge.RegeneratePin();
+	m_pinLabel = new wxStaticText(this, wxID_ANY,
+		wxString::Format("Session PIN: %06u", pin));
+	rootSizer->Add(m_pinLabel, 0, wxLEFT | wxRIGHT | wxBOTTOM, 10);
+	m_pinLabel->Show(bridge.IsPinRequired());
```
- [x] Done

Add new methods **after `OnPairClicked` (after line 150)**:
```cpp
void CemuPadPairingDialog::OnPinToggle(wxCommandEvent&)
{
	bool enabled = m_pinCheckbox->GetValue();
	CemuPadBridge::GetInstance().SetRequirePin(enabled);
	UpdatePinDisplay();
}

void CemuPadPairingDialog::UpdatePinDisplay()
{
	auto& bridge = CemuPadBridge::GetInstance();
	bool pinRequired = bridge.IsPinRequired();
	if (pinRequired)
	{
		uint32_t pin = bridge.GetCurrentPin();
		if (pin == 0) pin = bridge.RegeneratePin();
		m_pinLabel->SetLabel(wxString::Format("Session PIN: %06u", pin));
	}
	m_pinLabel->Show(pinRequired);
	Layout();
}
```
- [x] Done

#### 10.8.2 — Also add `#include <wx/checkbox.h>` to header if not present
**File:** `Cemu/src/gui/wxgui/input/CemuPadPairingDialog.h`
After `#include <wx/stattext.h>` (line 8), add:
```cpp
#include <wx/checkbox.h>
```
- [x] Done

---

### Step 10.10 — `when PIN is off` — Auto-approve but still track authorized IPs for mic

**Goal:** When PIN is disabled, clients are auto-approved (`authorized=true` at connect time). The mic filter (Step 10.1) must still work — authorized IPs must be populated.

#### 10.10.1 — Track authorized IP on auto-approve at connect
**File:** `Cemu/src/Cafe/HW/Latte/Renderer/VideoStreamServer.cpp`
**Line 565** — the block that sets `info.authorized = !CemuPadBridge::GetInstance().IsPinRequired();` already marks them authorized. **After** adding the client to `m_clients` (after line 566 `m_clients.push_back(info);`), and **still inside** the lock scope, add:

```cpp
		// (after the m_clientsMutex scope, approx line 567)
		// When auto-approved (no PIN), immediately whitelist IP for mic UDP
		if (!CemuPadBridge::GetInstance().IsPinRequired())
		{
			std::lock_guard<std::mutex> ipLock(m_authorizedIpsMutex);
			m_authorizedIps.insert(ntohl(clientAddr.sin_addr.s_addr));
		}
```
- [x] Done

---

## 4. Verification

### 4.1 Cemu Build
```powershell
cmake --build c:\Projects\wiiu-gamepad-android\Cemu\build --config Release --target CemuBin
# Must compile cleanly — no errors
```

### 4.2 Android Build + Tests
```powershell
cd c:\Projects\wiiu-gamepad-android\android-gamepad-app
.\gradlew.bat testDebugUnitTest
# Must be BUILD SUCCESSFUL
.\gradlew.bat assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 4.3 Manual Verification Checklist

1. **PIN default ON:** Launch fresh Cemu. Open `Input Settings → Pair CemuPad`. Verify:
   - [x] "Require PIN" checkbox is checked by default
   - [x] A 6-digit PIN is displayed (e.g. `Session PIN: 482917`)

2. **PIN auth flow:** On Android app, connect. Verify:
   - [x] The app prompts for the 6-digit PIN
   - [x] Entering the correct PIN authenticates and video starts
   - [x] Entering wrong PIN 3 times, then waiting < 30s, results in rejection
   - [x] After 30s cooldown, correct PIN works again

3. **Mic auth:** With PIN off, connect normally. Then from a second machine on the same network, try sending a raw UDP datagram to port 26764:
   ```powershell
   # From attacker machine — should be silently dropped
   $udp = New-Object System.Net.Sockets.UdpClient
   $bytes = [byte[]]::new(1024)
   $udp.Send($bytes, $bytes.Length, "TARGET_IP", 26764)
   ```
   Verify Cemu log shows no "QueueMicSamples" entries from the attacker IP. The real phone's mic should still work.

4. **Max client cap:** Open 5 simultaneous TCP connections to port 26761 (e.g. with `ncat` or `telnet`). Verify the 5th is rejected with log `"Rejecting client — max 4 connections reached"`.

5. **Bitrate clamp:** Send `OPCODE_SET_BITRATE` with value 0 or 0xFFFFFFFF. Verify Cemu log shows clamped value (500000 or 50000000).

6. **Resolution clamp:** Send `OPCODE_SET_RESOLUTION` with 0x0 or 9999x9999. Verify Cemu log shows `"Rejected SET_RESOLUTION"`.

7. **Push mapping rate limit:** Send `OPCODE_PUSH_MAPPINGS` twice within 5 seconds. Verify second one returns `0x01` (fail) and Cemu log shows `"rate-limited"`.

8. **Backup disabled:** Run `adb backup com.cemupad` — verify it produces an empty/minimal backup (no SharedPreferences exported).

---

## 5. Risks & Notes for Free Model

* **Do NOT change** any protocol opcodes or packet layouts — all changes are server-side validation, not wire format.
* **Do NOT touch** `ReedSolomon.cpp`, `VideoEncoder.cpp`, `StreamingCapture.cpp`, or any `DSUServer.kt` / `DSUPacket.kt` — these are out of scope.
* **Lock order rule:** Always acquire `m_authorizedIpsMutex` *after* releasing `m_clientsMutex`, never nest them.
* The `m_lastMappingPush` member is only accessed from client Rx threads, each of which handles one opcode at a time. If multiple clients push simultaneously, they share the same `m_lastMappingPush` — this is intentional (global rate limit, not per-client).
* **imgui upgrade (Finding #11)** is deferred — it requires submodule pin changes and regression testing of the Cemu GUI. Create a separate PR for that.
* **TLS (Finding #16)** and **HMAC on discovery (Finding #9 in recommendations)** are deferred — they are defense-in-depth for LAN streaming, comparable to Moonlight/Sunshine, and require significant protocol changes.
* **DSU protocol auth (Finding #8)** is inherent to the DSU spec and cannot be fixed without breaking compatibility with all DSU clients. Documented as accepted risk.
