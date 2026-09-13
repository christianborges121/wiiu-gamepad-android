# Dolphin Android input-mapping investigation → wizard key-capture resolution plan

Date: 2026-09-13. Source: `dolphin-emu/dolphin` master (shallow clone in
`C:\Users\chris\AppData\Local\Temp\opencode\dolphin` — delete when done).
Question under test: why does our Phase 6 wizard ignore the user's B button
(backs out instead of mapping), and how does Dolphin avoid this class of bug?

## 1. How Dolphin Android does it (findings)

### F1. Capture happens in a dedicated dialog that eats ALL input
`Source/Android/.../features/input/ui/MotionAlertDialog.kt:53-74` overrides
`dispatchKeyEvent` / `dispatchGenericMotionEvent` **on the dialog itself** and
returns `true` unconditionally. Key/motion events therefore never reach focus
traversal, buttons, or the activity while mapping. Our equivalent is
activity-level swallowing in `MainActivity.dispatchKeyEvent` — same intent,
but ours is conditional (`isWizardEatingKey`), so anything it doesn't
recognize leaks to focus nav. Dolphin has no such leak: eat first, classify later.

### F2. Back is neutralized; only LONG-press Back cancels
Same file, lines 56-61: a short Back press is swallowed silently. Only
`KEYCODE_BACK + isLongPress` clears the binding and dismisses (a documented
escape hatch "intended for non-touch devices"). Touch cancel goes through an
on-screen path instead (`SOURCE_CLASS_POINTER` events are passed to super).

### F3. Keys are identified by keycode→name lookup, unknowns tolerated
`Source/Core/InputCommon/ControllerInterface/Android/Android.cpp:1054-1095`:
`dispatchKeyEventNative` maps action→state, looks the device up by
`device_id`, converts keycode→name (`ConstructKeyName`, full `KEYCODE_NAMES`
table to SDK 31), and sets per-device input state. Unknown keycodes log an
error and return false — they degrade to "unmappable", never to "does
something destructive".

### F4. Detection is STATE-POLLING, not event-driven
`ControllerInterface/CoreDevice.cpp:432-482` (`InputDetector::Update`): every
input's live state is polled; a press crossing threshold starts a detection,
release (+confirmation wait) or timeout completes it. The Java dialog feeds
events into native state and additionally polls on a **10 ms timer**
(`MotionAlertDialog.kt:91-96`). Consequence: a missed/dropped KeyEvent cannot
strand the UI — the timer still observes the held state. (Android offers no
public key-state poll API, so we cannot copy this verbatim — see plan P3.)

### F5. Consumption follows backend truth, everywhere the same way
`EmulationActivity.kt:551-557`, `SettingsActivity.kt:269-271`,
`AdvancedMappingDialog.kt:67-69`: every entry point forwards to
`ControllerInterface.dispatchKeyEvent(event)` and consumes iff it returns
true. One rule, no per-screen special cases.

## 2. Diagnosis of OUR bug (in Dolphin terms)

| # | Our behavior | Dolphin contrast | Verdict |
|:--|:--|:--|:--|
| D1 | `isWizardEatingKey` gate: uneaten keys reach focus nav | F1: dialog eats everything | **Likely contributor.** Any key failing the gate (unknown source, odd keycode) moves focus — matches "hover over Back". |
| D2 | Short Back press cancels/backs out of the wizard | F2: short Back is a no-op while mapping | **Prime suspect for "B backs me out".** If the user's pad emits B as `KEYCODE_BACK` (common on TV-mode/8BitDo/keyboard-mode pads), every B press exits/back-steps by design. Unconfirmed — needs the `WizardKeys` log run. |
| D3 | Capture only sees keys that pass the gamepad gate | Dolphin's `allDevices` mode + name table accept anything | **Contributor.** A pad in keyboard mode sends letters/media keys; we never feed them to `recordKey`. |
| D4 | Pure event-driven capture; no fallback observation | F4: 10 ms poll + press-and-release completion | Not directly portable (no Android key-state API), but the timeout auto-skip already covers stuck targets. |

## 3. Resolution plan (for approval — no code changed yet)

- [ ] **P1. Eat-first dispatch while wizard open** (`MainActivity.dispatchKeyEvent`):
  feed EVERY `ACTION_DOWN` (except volume/power) to the wizard/capture path and
  consume; route volume/power to super. Mirrors F1; kills D1 and D3 at the root.
- [ ] **P2. Dolphin-style Back semantics**: short Back = no-op everywhere in the
  wizard EXCEPT capture mode, where it becomes a *bindable press* (assigned to
  the armed BUTTON/DPAD/TRIGGER target like any key); long-press Back (or the
  on-screen Cancel/Skip/Back touch buttons) = cancel/step-back. Fixes D2 even
  if the user's B arrives as Back. Touch buttons remain the primary navigation
  so a Back-emitting pad stays fully usable.
- [ ] **P3. Keep event-driven capture, harden the edges**: 15 s auto-skip stays
  (our answer to F4's timeout); conflict flash stays; add `recordKey` acceptance
  logging so the next unknown pad is diagnosable from logcat in one run.
- [ ] **P4. Tests**: `CaptureEngineTest` — Back binds as a button; keyboard-mode
  letter key binds; long-press is NOT bindable (unit-test via synthetic codes,
  engine already ignores nothing — add explicit BACK acceptance + a
  `isLongPress`-style flag passthrough); `MainActivity` routing is covered by
  existing assemble + manual log run.
- [ ] **P5. Live verification**: reinstall → user opens capture, presses B →
  `WizardKeys` log must show the raw code BEFORE any fix validation; then
  confirm B binds, focus never moves, full 19-target pass completes on their pad.

## 4. Deliberately NOT borrowed

- Native/JNI input layer: massive overkill for a DSU sender; our Kotlin handler
  already works live in gameplay.
- Per-device expression strings (`DeviceQualifier` model): our
  `ControllerProfile` + `DeviceProfileStore` cover the same ground natively.
- 10 ms native poll loop: no public Android API for key-state polling; our
  timeout + touch navigation is the equivalent guarantee.

## 5. Open question for the live run

What keycode does the user's B actually send? If `WizardKeys` shows
`KEYCODE_BACK`, P2 is confirmed sufficient. If it shows an exotic code with a
non-gamepad source, P1 covers it. If NOTHING arrives at dispatch at all (system
eats it pre-activity, e.g. captured by the foreground IME/gesture), the fallback
is on-screen "press-to-bind via `KeyEvent` accessibility"... to be designed
only if observed.
