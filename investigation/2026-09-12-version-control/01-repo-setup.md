# Repo setup (2026-09-12)

Two private GitHub repos on `main`, both pushed and SHA-verified:

- `christianborges121/Cemu` at `1161901b` — snapshot of `Cemu/` (16,678
  files). Regenerable vcpkg outputs (`buildtrees/`, `downloads/`,
  `packages/`) and `bin/` build artifacts are ignored; dependency pins are
  recorded in `Cemu/FORK.md`.
- `christianborges121/wiiu-gamepad-android` at `22619134` — Android app,
  `vanilla/` (minus signing key), docs, investigation notes, screenshots.
  `Cemu/`, `build/`, `references/`, Gradle caches, APKs, `local.properties`,
  and `*.jks` are ignored (see root `.gitignore`).

Notes:

- Upstream is `cemu-project/Cemu` (not `CemuProject/Cemu`). The GitHub fork
  was created, then set private, which detaches it from the fork network —
  the repo is now standalone, history restarted as one snapshot commit.
- `gh` CLI 2.100.0 installed via winget; auth via `gh auth login` (browser
  flow). Bare `gh` is not on this harness PATH; use
  `"$env:ProgramFiles\GitHub CLI\gh.exe"`.
- `git -C <repo> status` is clean in both repos. Future work commits here,
  not in chat-only patches.
