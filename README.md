# Wii U GamePad for Android

Use an Android phone as a Wii U GamePad for Cemu: DSU controller input over
UDP `26760`, plus a Cemu fork that captures the DRC framebuffer and streams
H.264 video over TCP `26761`.

Start with `AI_HARNESS_INSTRUCTIONS.md` (harness onboarding), then
`investigation/HANDOFF.md` (resume point) and `copilot_project_checklist.md`
(active plan; `PROJECT_CHECKLIST.md` is the frozen backup).

Layout:

```text
android-gamepad-app/  Kotlin Android app (com.cemupad)
vanilla/              Protocol reference implementation
investigation/        Session notes and HANDOFF.md
```

The Cemu fork in `Cemu/` is tracked in a separate private repo (snapshot with
dependency pins in `Cemu/FORK.md`). `references/` (Apollo/Artemis research
checkouts) and all build outputs are intentionally untracked.
