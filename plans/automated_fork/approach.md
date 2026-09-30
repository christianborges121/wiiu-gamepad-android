# Automated Fork Approach

## Concept
Maintain our existing divergent fork of Cemu (where we have native, perfectly integrated hooks into `Renderer`, `Latte`, `InputManager`, and `Audio`). Since Cemu is heavily against AI code, we will not submit PRs upstream. Instead, we rely on automated CI/CD to keep our fork perpetually up-to-date with upstream Cemu.

## Architecture
1. **Upstream Remote**: Cemu's official GitHub repository (`cemu-project/Cemu`).
2. **Our Fork**: `christianborges121/Cemu` (or similar).
3. **Automated Pipeline**: A GitHub Actions workflow configured to run on a cron schedule (e.g., weekly or daily).

## Pipeline Steps
1. **Fetch Upstream**: Pull the latest `main` branch from `cemu-project/Cemu`.
2. **Rebase/Merge**: Automatically rebase our `cemupad-main` branch onto the latest upstream `main`. 
   * *Conflict Resolution*: Since our hooks are isolated (mostly just `getInstance()` calls in specific places like `LatteRenderTarget.cpp` and `OpenGLRenderer.cpp`), merge conflicts should be minimal and deterministic.
3. **Build**: Run the CMake build for Windows (MSVC) using GitHub Actions runners.
4. **Release**: Automatically draft a GitHub Release with the compiled `Cemu_release.exe` (branded as "CemuPad Edition") and attach the Android APK.

## Pros
* **Ultimate Stability**: Native access to the game engine means perfect video sync, exact audio interception, and no input lag from virtual driver overhead.
* **Low Code Maintenance**: We don't have to fight changing Vulkan/OpenGL APIs to maintain a graphics hook.

## Cons
* **User Friction**: Users must download our specific build of Cemu instead of using the official one.
* **Pipeline Complexity**: If upstream Cemu heavily refactors `LatteRenderTarget` or `VideoStreamServer` dependencies, the automated rebase will fail and require manual developer intervention.
