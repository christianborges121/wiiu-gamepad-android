# Decision and next implementation order

Date: 2026-09-12

No source change was made during this investigation.

When implementation resumes, use this order:

1. Close the running game/Cemu before deployment; build the intended Cemu configuration and deploy it through the documented backup-and-hash procedure.
2. Verify the deployed Cemu emits periodic DSU probes after an Android restart, then confirm the phone shows a new client and Cemu logs a new video connection plus IDR request.
3. Persist the last verified Cemu IP (or add an explicit configured host) on Android so a fresh app process can attempt video reconnect without depending solely on a new DSU callback.
4. Add a regression test for process restart / lost DSU callback before changing video codec or transport code.

Do not treat the linker error as resolved merely because the source compiles. Deployment and runtime verification are required.
