# Stream-stop round trip (Cemu off)

Date: 2026-09-12

## Observation

User ran Cemu with the stream up, then turned Cemu off. Phone screenshot
shows the idle state:

![Startup card after Cemu stopped](../../cemu-off-startup-card-20260912.png) *(local screenshot, untracked)*

- Startup card visible again: "Connect Cemu and wait for the Wii U GamePad
  stream to load.", Local IP 192.168.0.193, DSU 26760, video 26761.
- Touch-surface placeholder with cyan border behind the card.
- Logcat quiet around the transition: no disconnect crash, no exception
  spam, no stuck streaming state.

## Conclusion

- Card hides while streaming (user-observed) and reappears on stop
  (screenshot-verified). Checklist 4.6 startup-card item closed.
- Stream-dependent items that remain (overlay behavior against live video,
  end-to-end resolution scaling) still need a game session with the
  harness watching; deferred, not blocking.
