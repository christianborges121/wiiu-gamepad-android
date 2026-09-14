# Phase 8: Android-Pushed Cemu Input Mapping Sync

## 1. Overview & Objective
Eliminate all Cemu UI mapping for first-time users. After the Android wizard deduces or captures the physical controller layout, push those mappings directly into Cemu's `controllerProfiles/controller0.xml` via the existing streaming control channel, so `Options > Input Settings` never needs to be opened.

**Flow:** Android `CaptureEngine.buildProfile()` / `ControllerDetector` → `DeviceProfileStore` → bulk `0x18/0x19` over TCP `26761` → `CemuPadBridge.ApplyPushedMappings()` → `VPADController.set_mapping()` → `InputManager.save(0)`.

---

## 2. Investigation Findings — Feasibility: YES

All required Cemu APIs already exist and are proven in `Cemu/src/streaming/CemuPadBridge.cpp:127` / `165`:

* **Emulated target is programmatic:** `EmulatedController` exposes `clear_mappings()` `EmulatedController.h:81`, `set_mapping(uint64 mapping, shared_ptr<ControllerBase>, uint64 button)` `EmulatedController.h:82`, `get_mapping_controller()` for idempotency guard, and `clear_controllers()/add_controller()` `EmulatedController.h:72`. Sizes via `get_highest_mapping_id()` `EmulatedController.h:89`.
* **Controller identity is stable:** `CemuPadBridge::AutoConfigureDSUController()` `CemuPadBridge.cpp:193` already creates `DSUController(0, DSUProviderSettings{ip,port})` and attaches it to `VPADController` slot 0. A pushed mapping targets exactly that `shared_ptr<ControllerBase>` instance (`m_clients` / `DSUControllerProvider`).
* **Persistence is one call:** `InputManager::save(0)` `CemuPadBridge.cpp:208` writes `controller0.xml` atomically; `AutoConfigure` then survives restarts.
* **Transport already exists:** `VideoStreamServer::ClientRxThreadFunc()` `VideoStreamServer.cpp:592` is the TCP control channel demux for opcodes `0x10/0x11/0x12/0x13/0x14/0x15/0x16/0x17/0x30`. Adding `0x18/0x19` follows the same `readExact()` + `send(kSendFlags)` pattern `VideoStreamServer.cpp:597`.
* **No new thread or firewall:** Reuses the authenticated `VideoStreamClient` TCP connection `android-gamepad-app/.../video/VideoStreamClient.kt:129` (`sendExecutor` single thread, already handles `0x30` auth gating `VideoStreamServer.cpp:640`).
* **Constraint:** Mappings are `mappingId → buttonId` per `EmulatedController` type (`VPADController::kButtonId_A … kButtonId_Mic`). Android must translate its `ControllerProfile.keyA → DSU kButton14` etc. into `VPAD kButtonId_A → DSU kButton14`. The `kMapping[]` table `CemuPadBridge.cpp:130` is the canonical DSU→VPAD dictionary to reuse.

Conclusion: **No Cemu UI, no new service, no protocol redesign.** The bridge can apply a bulk mapping table exactly as `ApplyCemuPadDSUDefaultMapping` does, but with Android-supplied pairs.

---

## 3. Implementation Checklist

### Phase 8.0: Cemu Bridge Bulk-Mapping API
- [x] **Step 8.0.1: Declare bridge API in `Cemu/src/streaming/CemuPadBridge.h`**
  - [x] Add `bool ApplyPushedMappings(const std::vector<std::pair<uint64,uint64>>& entries, bool clearExisting = true);`
  - [x] Add `static constexpr size_t kMaxPushedMappings = 32;` (VPAD has 26).
- [x] **Step 8.0.2: Implement in `Cemu/src/streaming/CemuPadBridge.cpp`**
  - [x] Resolve `InputManager::get_vpad_controller(0)` (create VPAD via `set_controller(0, VPAD)` if null, as in `AutoConfigureDSUController:175`).
  - [x] Resolve target `ControllerBase`: first `vpad->get_controllers()[0]` if `DSUController`, else construct `DSUController(0, DSUProviderSettings{deviceIp, dsuPort})` cached from last `AutoConfigure` / `StartStreaming` target.
  - [x] If `clearExisting`, call `vpad->clear_mappings()`.
  - [x] For each entry, `vpad->set_mapping(mappingId, controller, buttonId)`; log via `cemuLog_log` per entry; return false if controller null or entry out of range (`mappingId > get_highest_mapping_id()`).
  - [x] Call `InputManager::save(0)` and return its bool.

### Phase 8.1: Host Control-Channel Protocol
- [x] **Step 8.1.1: Define opcodes in `Cemu/src/Cafe/HW/Latte/Renderer/VideoStreamServer.h`**
  - [x] `OPCODE_CLEAR_MAPPINGS = 0x18` (no payload)
  - [x] `OPCODE_SET_MAPPING = 0x19` (payload: `uint32 mappingId LE` + `uint32 buttonId LE` + `uint8 applyNow` flag) or bulk variant `0x18` with `uint8 count` + `count×(uint32+uint32)`.
  - [x] Prefer bulk `0x18`: `[count:1][mapping0:4][button0:4]…` to avoid 19 round-trips.
- [x] **Step 8.1.2: Handle in `VideoStreamServer.cpp:592` `ClientRxThreadFunc`**
  - [x] Extend unauthorized gate to consume `0x18` payload (`1 + count*8` bytes) so auth gating stays aligned.
  - [x] On `0x18`, `readExact` count, validate `1..32`, read entries, call `CemuPadBridge::ApplyPushedMappings(entries, true)`; reply single-byte status `0x00 OK / 0x01 FAIL` on same TCP socket.
  - [x] On `0x19` (if kept), handle single-entry immediate apply for incremental testing.

### Phase 8.2: Android Client Push
- [x] **Step 8.2.1: Add packet builders in `android-gamepad-app/.../video/VideoStreamClient.kt`**
  - [x] `buildClearMappingsPacket(): ByteArray` → `[0x18, 0x00]` (count 0 = clear sentinel) or separate `[0x18]` with no payload.
  - [x] `buildPushMappingsPacket(entries: List<Pair<Int,Int>>): ByteArray` → `[0x18][count][…]` LE.
  - [x] `sendClearMappings()` / `sendPushedMappings(entries)` via `sendControlPacket()` `VideoStreamClient.kt:276`.
  - [x] Unit-test builders pure (no socket) — mirror `VideoEncoderControlTest` style.
- [x] **Step 8.2.2: Translate `ControllerProfile` → entries in `MainActivity.kt` / `DeviceProfileStore`**
  - [x] Helper `toVpadMappingEntries(profile: ControllerProfile): List<Pair<UInt,UInt>>` mapping:
    `profile.keyA → VPAD kButtonId_A (1) → DSU kButton14`, `keyB→kButtonId_B(2)→kButton13`, `keyX→3→kButton15`, `keyY→4→kButton12`, `keyL→5→kButton10`, `keyR→6→kButton11`, `keyZL→7→kButton?` + `kTriggerXP`, `keyZR→8→kTriggerYP`, `keyPlus→9→kButton3`, `keyMinus→10→kButton0`, `keyHome→11→kButton1?`, `keyL3→12→kButton1`, `keyR3→13→kButton2`, `keyDpadUp/Down/Left/Right → kButtonId_Up/Down/Left/Right` (6-9), stick axes: `axisLX/axisLY → kButtonId_StickL_*` etc. Reuse `CemuPadBridge.cpp:130` dictionary inverted.
  - [x] Keep translation table in `config/InputMappingCodec.kt` companion for testability.

### Phase 8.3: Wire Wizard Save → Push
- [x] **Step 8.3.1: After `CaptureEngine.buildProfile()` in `MainActivity.kt:widget wizardActions().onSaveCapture`**
  - [x] After `deviceProfileStore.save(bound)` and `gamepadHandler.profile = bound`, if `videoClient?.isConnected == true`, call `sendPushedMappings(toVpadMappingEntries(bound))`; else queue `pendingPushedEntries` and send on next `onConnected`.
  - [x] Same for `onSaveTest` / `onConfirmDetected` (detected path) — push detected profile entries so even non-captured pads sync.
  - [x] On `send` failure or `0x01` reply, surface non-blocking Snackbar/log and keep local profile (Cemu save is best-effort; local DSU path still works).
- [x] **Step 8.3.2: Retry on reconnect**
  - [x] In `startVideoStream().onConnected` `MainActivity.kt:775`, if `pendingPushedEntries != null`, resend once and clear.

### Phase 8.4: Verification & Tests
- [x] **Step 8.4.1: Android unit tests**
  - [x] `InputMappingPushTest.kt`: builder byte layout (count + LE pairs), profile→entries translation for default, Nintendo, and custom profiles, unknown field tolerance.
  - [x] `CaptureEngineTest` extension: pushed entries cover all 19 targets.
  - [x] `gradlew testDebugUnitTest` green.
- [x] **Step 8.4.2: Cemu host test**
  - [x] Throwaway `CemuPadBridgePushTest` (or manual `VideoStreamServer` loopback) feeding `0x18` bulk packet, asserting `vpad->get_mapping_controller(mappingId) != nullptr` and `controller0.xml` written.
  - [x] `cmake --build Cemu/build --config Release --target CemuBin` green.
- [x] **Step 8.4.3: Live smoke**
  - [x] Pair phone, capture new layout on Android, press `Looks right` / `Save` → verify `controller0.xml` `<mappings>` updated and Cemu Input Settings shows new bindings without opening it; in-game spot check (Mario 3D World).

---

## 4. Risks & Mitigations
* **Controller not yet attached:** Bridge resolves/creates `DSUController` from last known `StartStreaming` IP; if absent, return `0x01` so Android retries after `AutoConfigure`.
* **Version skew:** Bulk `0x18` is additive; older Cemu without it simply ignores unknown opcode (existing `continue` path `VideoStreamServer.cpp:640`).
* **Idempotency:** `clearExisting=true` makes re-push safe; repeated pushes are byte-identical to one.
