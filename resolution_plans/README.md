# Resolution Plans Index

Each plan contains the exact file, lines, current code, replacement code, and verification steps needed for a model to implement the fix without additional context.

---

## 🔴 High Priority

| ID | Plan | Component | Summary |
|:---|:---|:---|:---|
| **ENC-1** | [ENC-1_hardware_mft_encoder.md](resolution_plans/ENC-1_hardware_mft_encoder.md) | Cemu `VideoEncoder` | Enumerate hardware H.264 MFTs (NVENC/AMF/QSV) before falling back to software |
| **SRV-1** | [SRV-1_mutex_during_send.md](resolution_plans/SRV-1_mutex_during_send.md) | Cemu `VideoStreamServer` | Snapshot client list under lock, send outside lock (also fixes SRV-4) |
| **SRV-4** | [SRV-4_tcp_partial_writes.md](resolution_plans/SRV-4_tcp_partial_writes.md) | Cemu `VideoStreamServer` | Loop on `send()` until all bytes written (standalone plan if SRV-1 not taken) |

## 🟡 Medium Priority

| ID | Plan | Component | Summary |
|:---|:---|:---|:---|
| **VID-1** | [VID-1_thread_churn_opcode_send.md](resolution_plans/VID-1_thread_churn_opcode_send.md) | Android `VideoStreamClient` | Replace per-call `Thread` with single-thread executor |
| **VID-2** | [VID-2_dead_frame_rate_limiter.md](resolution_plans/VID-2_dead_frame_rate_limiter.md) | Android `VideoDecoder` / UI | Grey out the "Limit to 30 FPS" toggle until encoder-side support is added |
| **UI-2** | [UI-2_fragile_display_settings.md](resolution_plans/UI-2_fragile_display_settings.md) | Android `MainScreen` | Extract `currentSettings()` helper, use `copy()` at all 6 toggle sites |
| **ENC-2** | [ENC-2_simd_nv12_conversion.md](resolution_plans/ENC-2_simd_nv12_conversion.md) | Cemu `VideoEncoder` | Optimize NV12 conversion (skip scaling fast path, or integrate libyuv) |
| **SRV-2** | [SRV-2_unbounded_rx_threads.md](resolution_plans/SRV-2_unbounded_rx_threads.md) | Cemu `VideoStreamServer` | Prune finished RX threads, or use detached threads |

## 🟢 Low Priority

| ID | Plan | Component | Summary |
|:---|:---|:---|:---|
| **DS-1** | [DS-1_shared_packet_counter.md](resolution_plans/DS-1_shared_packet_counter.md) | Android `DSUServer` | Won't fix — single-client design acceptable |
| **TOUCH-1** | [TOUCH-1_stale_touch_pointer_up.md](resolution_plans/TOUCH-1_stale_touch_pointer_up.md) | Android `TouchInputHandler` | Re-evaluate remaining pointers on `ACTION_POINTER_UP` |
| **ACT-1** | [ACT-1_redundant_input_dispatch.md](resolution_plans/ACT-1_redundant_input_dispatch.md) | Android `MainActivity` | Remove redundant `onKeyDown`/`onKeyUp`/`onGenericMotionEvent` overrides |
| **ACT-2** | [ACT-2_noop_self_assignment.md](resolution_plans/ACT-2_noop_self_assignment.md) | Android `MainActivity` | Remove `lastKnownClientIp = lastKnownClientIp` no-op |
| **ACT-3** | [ACT-3_deprecated_flag_fullscreen.md](resolution_plans/ACT-3_deprecated_flag_fullscreen.md) | Android `MainActivity` | Remove deprecated `FLAG_FULLSCREEN` |
| **UI-1** | [UI-1_deprecated_divider.md](resolution_plans/UI-1_deprecated_divider.md) | Android `MainScreen` | Replace `Divider` with `HorizontalDivider` |
| **CEMU-1** | [CEMU-1_static_locals.md](resolution_plans/CEMU-1_static_locals.md) | Cemu `VulkanRenderer` | Won't fix — single instance OK, upstream merge risk |
| **SRV-3** | [SRV-3_dword_so_rcvtimeo.md](resolution_plans/SRV-3_dword_so_rcvtimeo.md) | Cemu `VideoStreamServer` | Guard `DWORD` timeout with `#if defined(_WIN32)` |
| **NAV-1** | [NAV-1_dead_navigation_code.md](resolution_plans/NAV-1_dead_navigation_code.md) | Android `Navigation.kt` | Delete dead code file |

---

## Dependency Notes

- **SRV-1 includes SRV-4**: The SRV-1 rewrite of `BroadcastFrame()` already adds the partial-write loop. If implementing SRV-1, skip SRV-4's standalone plan.
- **ENC-1 is independent**: Can be implemented without any other changes.
- **All Android fixes are independent**: Can be implemented in any order.
- **Low-priority items** marked "Won't Fix" have no planned code changes.
