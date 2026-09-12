# Repo setup (2026-09-12)

## App repo (private)

- `christianborges121/wiiu-gamepad-android`, branch `main`.
- Android app, `vanilla/` (minus signing key), docs, investigation notes,
  screenshots. `Cemu/`, `build/`, `references/`, Gradle caches, APKs,
  `local.properties`, and `*.jks` are ignored (root `.gitignore`).

## Cemu fork (public, attached)

- `christianborges121/Cemu`, branch `main` at `5886003d`, one commit ahead
  of upstream `cemu-project/Cemu@3310f3b8` (2026-09-10), zero behind.
- Compare view (exactly the fork delta, 24 files):
  `https://github.com/cemu-project/Cemu/compare/main...christianborges121:main`
- The old detached snapshot (`1161901b`) is preserved as
  `C:\Projects\cemu-snapshot-backup-20260912.tar` (outside both repos).
  Its remote backup branch was deleted because in-repo compares against it
  (unrelated history, ~16k files) drown out the real fork delta. The only
  meaningful compare is cross-fork against upstream (24 files).
- Dependencies are real submodules at the fork pins in `Cemu/FORK.md`
  (base pins were older; `imgui` is pinned older than base — revisit).
- Local `Cemu/` repo has remotes `origin` (fork) and `upstream`
  (`cemu-project/Cemu`). `git status` shows only the contained nested
  `m dependencies/cubeb` marker (sanitizers override, see `FORK.md`).

## Tooling notes

- `gh` CLI 2.100.0 via winget; auth via `gh auth login` (browser flow) plus
  `gh auth refresh -s delete_repo` for repo deletion. Bare `gh` is not on
  this harness PATH; use `"$env:ProgramFiles\GitHub CLI\gh.exe"`.
- Upstream path is `cemu-project/Cemu` (lowercase org). Setting a fork
  private detaches it from the fork network — keep this one public.
