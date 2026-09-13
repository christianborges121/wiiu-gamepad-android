# Phase 6 Implementation Plan — First-Connect Input Mapping Wizard (Android)

## 1. Overview & Objective

Today the phone assumes a standard Android gamepad layout (`ControllerProfile.DEFAULT`).
Anything exotic (8BitDo in the wrong mode, generic HID pads, Backbone/Razer with odd
axes) must be fixed from Cemu's Input Settings UI on the PC.

**Objective**: when the app meets a physical controller for the first time, run an
in-app mapping flow so the user never touches the Cemu UI:
1. **Deduce**: enumerate `InputDevice`s, match VID/PID + source/capability fingerprints
   against a built-in table (Xbox, PlayStation, Switch Pro/Joy-Cons, Backbone, Razer
   Kishi, Gamesir, generic) and auto-apply the matching `ControllerProfile`.
2. **Verify**: show the deduced layout with a live test pad (press buttons, see DSU
   controls light up). User confirms or drops into manual capture.
3. **Capture (fallback)**: walk each Wii U control in order and have the user press /
   move the corresponding physical input. Persist per-device; reuse on reconnect.

Out of scope: touch overlay layout editing, motion calibration (exists), Cemu-side
mappings (untouched — phone always speaks the standard DSU layout from
`GamepadInputHandler`).

### DSU target layout (what capture assigns TO — fixed, from `GamepadInputHandler`)

| Wii U control | DSU destination |
|:---|:---|
| A / B / X / Y | Circle(13) / Cross(14) / Triangle(12) / Square(15) |
| D-Pad Up/Down/Left/Right | state1 d-pad bits (keys and/or hat axis) |
| L / R / ZL / ZR | state2 bits (+ analog L2/R2 byte) |
| Plus / Minus / Home | state1 Options/Share, psButton |
| Stick L/R press | state1 L3/R3 bits |
| Left / Right stick | `lx,ly` / `rx,ry` bytes (Y inverted: 255 = UP) |
| ZL / ZR analog | `l2` / `r2` bytes |

---

## 2. Architecture

```text
MainActivity.dispatchKeyEvent / onGenericMotionEvent
  └─> InputMapper (new orchestrator)
        ├─ captureMode? ──> CaptureEngine.record(event) ──> wizard UI state
        └─ normal ──> GamepadInputHandler(profile = DeviceProfileStore.active)

DeviceProfileStore (per InputDevice.descriptor)
  ControllerDetector.detect(device) -> known ControllerProfile | null
  ProfileCodec (SharedPreferences, AppSettingsCodec-style pure codec + tests)
```

---

## 3. Implementation Checklist & Step-by-Step Code Modifications

- [x] **Step 3.1: ControllerDetector (deduce path)**
  - [x] Create `input/ControllerDetector.kt` with `detect(device: InputDevice): DetectedProfile?`.
  - [x] Fingerprint = `vendorId` + `productId` + `sources` + presence of key axes
    (`AXIS_X/Y/Z/RZ/RX/RY/BRAKE/GAS/HAT_X/HAT_Y`).
  - [x] Ship table: Xbox Wireless (045E), DualSense/DualShock4 (054C), Switch Pro
    (057E/2009), Joy-Cons (057E/2006/2007), Backbone (various + name match
    "Backbone"), Razer Kishi, Gamesir (name match), generic fallback `null`.
  - [x] Name-substring fallback ("Xbox", "DualSense", "DualShock", "Pro Controller",
    "Backbone", "Kishi", "Gamesir", "8BitDo") when VID/PID are 0 (common on BT).
  - [x] Return confidence (`EXACT` / `HEURISTIC` / `null`) so the wizard can word
    the confirm screen honestly ("looks like…" vs "recognized").

- [x] **Step 3.2: Mapping model + per-device persistence** *(plus: D-pad keys + hat flag made profile-driven since capture assigns them; handler defaults preserve legacy behavior)*
  - [x] Extend `ControllerProfile` with `deviceDescriptor: String = ""` and
    `displayName: String = name` (pure data, defaults keep old call sites compiling).
  - [x] Create `config/InputMappingCodec.kt`: pure `encode/decode` profile ↔ map
    (mirrors `AppSettingsCodec` so tests stay JVM-only).
  - [x] Create `input/DeviceProfileStore.kt`: `activeFor(descriptor)` (stored →
    detected → `DEFAULT`), `save(profile)`, `clear(descriptor)`; SharedPreferences
    backing with `input_profile_<hash>` keys.
  - [x] `MainActivity`: on `InputDevice` attach (existing `onStart` + device-change
    listener), resolve store profile into `gamepadHandler.profile`; record
    `hasMappedBefore(descriptor)` flag to decide first-run wizard trigger.

- [x] **Step 3.3: CaptureEngine (manual path)** *(adapted: Y-inversion auto-detect dropped — unnecessary, `normalizeAxis(invertY=true)` is a fixed handler contract asserted in tests; stick capture records the axis pair only)*
  - [x] Create `input/CaptureEngine.kt` as a UI-independent state machine (unit-testable,
    no Android deps beyond `KeyEvent`/`MotionEvent` constants):
    - `start(targets: List<MappableControl>)`, `recordKey(keyCode)`, `recordAxis(axis, value)`,
      `skip()`, `back()`, `cancel()`, `result(): ControllerProfile?`.
    - Button capture: first `ACTION_DOWN` keycode while armed (ignore volume/power/system).
    - Stick capture: largest-deflection axis pair while user circles the stick;
      record axis ids + auto-detect Y inversion (up must yield DSU 255).
    - D-Pad capture: accept keys OR hat (`AXIS_HAT_X/Y` thresholds ±0.5).
    - Trigger capture: prefer analog axis (`BRAKE/GAS/RX/RY/LTRIGGER/RTRIGGER` travel),
      fall back to digital key.
    - Duplicate assignment → keep first, flag conflict in UI (no silent overwrite).
    - Per-target 15s timeout → auto-skip with "skipped" mark.
  - [x] `MappableControl` order: A, B, X, Y, D-Pad(4), L, R, ZL, ZR, Plus, Minus,
    Home, StickL-press, StickR-press, StickL-move, StickR-move.

- [x] **Step 3.4: Wizard UI (Compose)** *(adapted: dismissible banner card instead of Snackbar — MainScreen has no Scaffold; drawer "Physical controller" row with live device name + Map entry)*
  - [x] Create `ui/mapping/MappingWizard.kt` (dialog-style, dark card theme):
    - Screen 1 *Detected*: device name + confidence + layout summary + [Test & Confirm].
    - Screen 2 *Test*: live DSU readout (reuse handler state; each target lights up
      on input) + [Looks right] / [Remap manually].
    - Screen 3 *Capture*: current target prompt ("Press A…"), progress `7/18`,
      [Skip] [Back] [Cancel]; conflict + timeout toasts.
    - Screen 4 *Done*: summary + [Save] (persists via store, hot-swaps handler profile).
  - [x] Trigger: first input event from an unmapped descriptor (snackbar "New controller
    — set up?" + drawer entry "Input mapping" for manual launch/re-map).
  - [x] `MainScreen` params: `showMappingWizard`, `wizardDeviceName`, `onWizardDismiss`;
    state lives in `MainActivity` like the (removed) PIN dialog pattern.

- [x] **Step 3.5: Event-routing wiring**
  - [x] `MainActivity.dispatchKeyEvent` / `onGenericMotionEvent`: when capture armed,
    route to `CaptureEngine` and consume (return true); else existing handler path.
  - [x] Virtual overlay (`setVirtualButton`/`setVirtualStick`) bypasses capture
    (overlay always speaks DSU directly — never remapped).
  - [x] Back button during capture = `back()` (consistent with drawer Back semantics),
    never closes the app mid-wizard.

---

## 4. Verification & Testing Checklist

- [x] **4.1 Android Unit Testing** *(88/88 green: Detector 4, Codec 4, Store 4, Capture 10)*
  - [x] `ControllerDetectorTest`: table hits (fake descriptors), name-fallback, unknown → null.
  - [x] `InputMappingCodecTest`: round-trip incl. defaults + unknown-field tolerance.
  - [x] `CaptureEngineTest`: key assign, duplicate conflict, stick axis-pair + inversion,
    hat d-pad, trigger analog→digital fallback, skip/back/cancel/timeout.
  - [x] Execute: `.\gradlew.bat testDebugUnitTest` (+ `assembleDebug`).
- [ ] **4.2 Live Device Verification**
  - [ ] Known controller (e.g. Xbox/Backbone): auto-detected, test screen lights up, gameplay works, no Cemu UI touched.
  - [ ] Unknown/generic pad: full manual pass completes, profile persists across app restart, sticks move the right way (Y-up = up).
  - [ ] Reconnect same device: no wizard re-prompt; drawer re-map edits and saves.
- [ ] **4.3 Cemu-Side Confirmation**
  - [ ] `controller0.xml` untouched by the flow; Cemu mapping screen shows standard DSU
    inputs live; in-game buttons/sticks/triggers correct (SM3DW spot-check).

---

## 5. Notes for the implementer

- `ControllerProfile` defaults must keep compiling every existing call site
  (`GamepadInputHandler` ctor default, tests) — additive fields only.
- D-Pad key AND hat states already coexist in the handler (`dpadKey*`/`dpadHat*`) —
  capture only decides which source(s) feed them.
- The existing `normalizeAxis(invertY=true)` contract is what stick capture must satisfy;
  assert it in `CaptureEngineTest` (up → 255).
- Keep `DeviceProfileStore` keys stable (`input_profile_<descriptorHash>`) — users will
  accumulate one entry per pad, which is correct.
