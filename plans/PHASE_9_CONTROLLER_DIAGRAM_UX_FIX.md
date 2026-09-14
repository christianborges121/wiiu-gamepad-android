# Phase 9: Controller Diagram UX & Test-Input Isolation — Code-Level Fix Plan

> **For: lower-level free model** — follow line numbers exactly, keep formatting, verify with `.\gradlew.bat testDebugUnitTest` and `cmake --build ... --target CemuBin` before checking off. No Cemu rebuild needed for this phase (Android-only).

## 1. Objective
* Diagram from `PHASE_8`/`PHASE_7-wip` (`android-gamepad-app/app/src/main/java/com/cemupad/ui/mapping/MappingWizard.kt:387`) is functional but too small / cramped, and Testing still leaks inputs to the game. Fix both with isolated, low-risk Android edits.

---

## 2. Pre-Flight (read first)
* `HANDOFF.md:1` is stale (last Phase 4 & 6) — ignore its Next Phase line.
* Active branch is `feature/phase-7-wip` (`d3d76c4` has diagram, `925dc6b` placed Home last). `plans/PHASE_8...` is already `- [x]` but live stick push still under test — treat 9 as additive, not blocking 8.
* Real verification devices: Samsung Galaxy S23 FE (`192.168.68.109:44985`) + Lenovo Legion Tab; `adb` path `plans/AI_HARNESS_TESTING_GUIDE.md:101`.

---

## 3. Implementation Checklist

### Step 9.0 — Make Diagram Bigger (file: `MappingWizard.kt`)
- [x] **9.0.1 Enlarge container** `MappingWizard.kt:393`
  - Change `widthIn(max = 520.dp)` → `widthIn(max = 640.dp)` and outer `padding(12.dp)` → `padding(16.dp)`.
  - Change card `padding(12.dp)` already at line 396 — keep, this is the controller shape padding.
- [x] **9.0.2 Increase button & font scale**
  - `DiagramShoulder` `MappingWizard.kt:449` : `padding(horizontal = 10.dp, vertical = 4.dp)` → `12.dp, 6.dp`, `fontSize = 11.sp` → `12.sp`
  - `DiagramSmall` `MappingWizard.kt:461` : same padding bump `8.dp,4.dp` → `10.dp,6.dp`, `11.sp` → `12.sp`
  - `DiagramFace` `MappingWizard.kt:528` : `12.dp,6.dp` → `14.dp,8.dp`, `12.sp` → `13.sp`
  - `DiagramPadArrow` `MappingWizard.kt:473` : `8.dp,4.dp` → `10.dp,6.dp`, `11.sp` → `12.sp`
  - `DiagramStick` arrows `MappingWizard.kt:498` : `8.dp,2.dp` → `10.dp,4.dp`, `10.sp` → `11.sp`; center `10.dp,4.dp` → `12.dp,6.dp`
  - Implemented as `wDp 230→300, hDp 135→175` + Badge bumps `22×14→26×16`, `16×16→18×18` etc., verified `testDebugUnitTest` green.
- [x] **9.0.3 Spread button sets apart**
  - Outer `Column verticalArrangement = Arrangement.spacedBy(10.dp)` `MappingWizard.kt:399` → `16.dp`
  - Shoulders `Row spacedBy(6.dp)` `MappingWizard.kt:402` + `406` → `12.dp` and `10.dp`
  - Main clusters `Row spacedBy` left/right `MappingWizard.kt:412` currently `SpaceBetween` with `weight(1f)` columns and `Spacer width 12.dp` — change spacer to `24.dp`, and inner `Column spacedBy(8.dp)` → `12.dp` for both left/right clusters.
  - Face cluster `Row spacedBy(6.dp)` `MappingWizard.kt:517` → `10.dp`; `Column spacedBy(2.dp)` `MappingWizard.kt:515` → `6.dp`
  - Keep card `verticalScroll` — diagram now `~640dp` width, `300×175` chassis, still scrolls but not cramped. Verified via `assembleDebug` + `adb screencap`.

### Step 9.1 — Stop Testing Inputs Leaking to Game (file: `MainActivity.kt`)
- [x] **9.1.1 Root cause** — `MainActivity.kt:1073` `dispatchGenericMotionEvent` handles hat/stick for `MappingWizardScreen.Testing` but never returns `true`, so it falls through to `gamepadHandler.onGenericMotionEvent(event) -> DSU -> Cemu -> game moves`. `dispatchKeyEvent:1014` correctly returns `true` for Testing, so keys are safe — only motion leaks.
  - Fix: after Testing hat/stick block `MainActivity.kt:1082-1120`, add `return true` when `wizardScreen.value is MappingWizardScreen.Testing` and an input was consumed. Do it immediately after `wizardScreen.value = testing.copy(...)` for both hat and stick branches, plus a final `if (wizardScreen.value is Testing) return true` guard before the `if (::gamepadHandler...)` fallthrough. Preserve existing `Capturing` early `return true` `MainActivity.kt:1096`.
  - Also add same guard at top of `dispatchGenericMotionEvent` mirroring `dispatchKeyEvent:948` eat-first: `if (wizardScreen.value is Testing && !isSystemPassthrough) return true` after handling, so idle Testing (no stick hat) still suppresses stray motion that would otherwise drive sticks in game.
  - Implemented as unconditional `return true` after Testing hat+stick handling `MainActivity.kt:1241`, verified `testDebugUnitTest` still green, manual motion no longer moves game.
- [x] **9.1.2 Keep `isSystemPassthroughKey` untouched** — volume/power must still pass. Do not add motion passthrough.

### Step 9.2 — Size Regression Guard
- [x] **9.2.1 Add screenshot check** — no new unit test needed (Compose layout). Manual verification: `adb shell am start -n com.cemupad/.MainActivity`, open `Map → Test layout` (needs controller), verify diagram fills ~80% of card width, gaps between shoulders/center and left/right clusters clearly visible (>16dp), no overlap on 1080p landscape.
- [x] **9.2.2 Keep existing tests green** — `CaptureEngineTest` (stick per-direction) and `InputMappingPushTest` must stay green. No logic change, only UI + one `return true`. `.\gradlew.bat testDebugUnitTest` must be `BUILD SUCCESSFUL` (24 tasks) before checking off. Verified 24/24 tasks green.

---

## 4. Verification (must do before checking off)

```powershell
cd c:\Projects\wiiu-gamepad-android\android-gamepad-app
.\gradlew.bat testDebugUnitTest  # expect BUILD SUCCESSFUL
.\gradlew.bat assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.cemupad/.MainActivity
# Manual: controller → MainScreen → Map → Test layout
# 1) Wiggle sticks / D-pad — game behind must NOT move (was bug)
# 2) Diagram buttons visibly larger and spaced (shoulder gap, face diamond gap)
adb exec-out screencap -p > phone_screen.png  # inspect
Get-Content "C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu\log.txt" -Tail 10
```

*No Cemu build needed* — `Cemu/src` untouched. If you touch it, run `cmake --build Cemu/build --config Release --target CemuBin`.

---

## 5. Risks & Notes for Free Model
* Do not change `ControllerProfile` or `CaptureEngine` enum — this phase is UI + dispatch only. Touching those breaks Phase 8 push.
* Keep `ProControllerDiagram` as pure function `getHighlight: (String)->Color, targetLabel: String?` — do not store state.
* The two `return true` fixes are the only behavior change; everything else is padding/font. If game still moves during Test, you missed the second `return true` after the hat block.
