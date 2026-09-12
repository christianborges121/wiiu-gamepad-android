# Checklist reconciliation against live Phase 4 UX code

Date: 2026-09-12

## Request

Review `copilot_project_checklist.md` and update it to match the recent UI
work: aspect ratios / fit mode, the drawer, the diagnostics overlay, and
persistence.

## Findings vs. live code

1. Duplicate `Phase 4` headings and a `Phase 5: Bidirectional Audio` heading
   that the summary table and its own internal heading call Phase 3.
2. The summary table had no row for the active fullscreen UX phase.
3. Two checked items carried open caveats (`Connection/help-text toggle is
   still missing`; `overlay still needs live confirmation`), so the caveats
   were not tracked as open work.
4. The resolution preset is persisted and displayed but not applied:
   `MainScreen` still hardcodes `holder.setFixedSize(854, 480)` in
   `surfaceCreated`. That open item only existed in `HANDOFF.md`.
5. The 2026-09-12 fit-mode clipping fix
   (`DisplayLayout.aspectFitDimensions` / `aspectFillDimensions`,
   `DisplayLayoutTest`) and its screenshot evidence were not in the progress
   log.

## Changes applied to `copilot_project_checklist.md`

- Renamed the UX section to `Phase 4 UX` and added a summary-table row for it.
- Renamed `Phase 5: Bidirectional Audio` back to `Phase 3` to match the
  summary table and the existing `Phase 3 verification` heading.
- Split the two mixed done/open items into explicit `[x]` and `[ ]` lines.
- Added an open verification item: resolution preset does not yet change the
  SurfaceView buffer/decoder size (fixed 854 x 480).
- Added progress-log entries for the fit-mode fix, screenshots, and this
  reconciliation.

## Validation

Docs-only change; no code or tests touched. `testDebugUnitTest` result is
unaffected.