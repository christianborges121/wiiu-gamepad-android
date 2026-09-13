# Phase 4.5 Implementation Plan — Cross-Platform POSIX Socket Compatibility (Cemu Host)

## 1. Overview & Objective
Make the CemuPad streaming subsystem in Cemu 100% cross-platform compliant so that Windows (MSVC), Linux (GCC/Clang on x86_64 & AArch64), and macOS (Apple Clang on x86_64 & ARM64) all compile without errors in CI/CD.

---

## 2. Implementation Checklist

- [x] **Step 2.1: POSIX Headers & Types in `VideoStreamServer.h`**
  - [x] Add `#include <sys/socket.h>`, `<netinet/in.h>`, `<netinet/tcp.h>`, `<arpa/inet.h>`, `<unistd.h>`, `<fcntl.h>`, `<errno.h>` under `#else`.
  - [x] Define `SOCKET`, `INVALID_SOCKET`, `SOCKET_ERROR`.
  - [x] Replace `m_rxThreads` with detached thread lifecycle.

- [x] **Step 2.2: Portable Socket Helpers in `VideoStreamServer.cpp`**
  - [x] Implement `CloseSocket(SOCKET s)`.
  - [x] Implement `SetSocketNonBlocking(SOCKET s)`.
  - [x] Implement `SetSocketRecvTimeout(SOCKET s, int ms)`.
  - [x] Add `MSG_NOSIGNAL` flag on POSIX send calls.

- [x] **Step 2.3: Call Site Updates in `VideoStreamServer.cpp`**
  - [x] Update `SendUdpFrame` with `sockaddr_in` and `sendto`.
  - [x] Update `BroadcastAudio` with `sendto`.
  - [x] Update `ServerThreadFunc` with `SetSocketRecvTimeout` and detached `ClientRxThreadFunc`.
  - [x] Update `MicRxThreadFunc` with `SetSocketRecvTimeout` and `socklen_t`.

- [ ] **Step 2.4: Build & CI Verification**
  - [x] Local MSVC Windows build verification (`cmake --build build --config Release --target CemuBin` passed with code 0).
  - [ ] Push to `christianborges121/Cemu` and verify GitHub Actions `build-windows`, `build-ubuntu-x64`, `build-ubuntu-arm`, `build-macos`.

