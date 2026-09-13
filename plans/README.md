# Wii U GamePad for Android — Autonomous Next Phases Implementation Plans

This directory contains comprehensive, code-level implementation plans for all remaining phases of the **Wii U GamePad for Android** project.

Each document is fully self-contained and formatted specifically so that any AI coding agent (including free-tier or open-weight models) can pick up the task, follow the instructions sequentially, execute the code modifications, and autonomously verify the results using the included test harnesses.

---

## 🗺️ Roadmap & Phase Index

| Plan Document | Target Feature | Primary Tech Stack | Status |
|:---|:---|:---|:---:|
| [**PHASE_4_1_AUTO_DISCOVERY.md**](file:///c:/Projects/wiiu-gamepad-android/plans/PHASE_4_1_AUTO_DISCOVERY.md) | Zero-Config UDP Broadcast Auto-Discovery | C++ Winsock / Kotlin UDP Datagram | Ready for Execution |
| [**PHASE_4_2_DYNAMIC_VIDEO_ENCODING.md**](file:///c:/Projects/wiiu-gamepad-android/plans/PHASE_4_2_DYNAMIC_VIDEO_ENCODING.md) | Dynamic Bitrate & Resolution Encoder Controls | C++ Windows Media Foundation / Jetpack Compose | Ready for Execution |
| [**PHASE_4_3_VOICE_PCM_STREAMING.md**](file:///c:/Projects/wiiu-gamepad-android/plans/PHASE_4_3_VOICE_PCM_STREAMING.md) | Direct 32 kHz Voice PCM Microphone Streaming | C++ Cafe OS `mic.cpp` / Android `AudioRecord` | Ready for Execution |
| [**PHASE_4_4_SESSION_SECURITY_AND_PIN.md**](file:///c:/Projects/wiiu-gamepad-android/plans/PHASE_4_4_SESSION_SECURITY_AND_PIN.md) | Session Security, Host Selection & PIN Pairing | TCP Control Protocol / Crypto / Jetpack Compose | Ready for Execution |
| [**PHASE_5_RELEASE_AND_PACKAGING.md**](file:///c:/Projects/wiiu-gamepad-android/plans/PHASE_5_RELEASE_AND_PACKAGING.md) | Release Hardening, ProGuard/R8 & CI/CD Pipeline | Gradle / ProGuard / GitHub Actions CI | Ready for Execution |

---

## 🛠️ Instructions for Autonomous AI Models

When taking over any of the phases listed above:

1. **Pick One Plan**: Open the corresponding `.md` file (e.g. `PHASE_4_1_AUTO_DISCOVERY.md`).
2. **Read Section 1 & 2**: Review the architectural overview, protocol specifications, and target file list.
3. **Execute Step-by-Step Code Changes**: Follow Section 3. Use `replace_file_content` or `multi_replace_file_content` targeting the specified lines. Preserve all existing comments and formatting.
4. **Compile & Run Automated Tests**: Use the exact shell commands in Section 4 (Android unit tests + Cemu CMake build).
5. **Live Verification**: Run the autonomous ADB and logcat verification commands to confirm feature behavior on the connected device.
6. **Commit & Push**: Commit the verified changes with a descriptive message and push to GitHub `main`.
