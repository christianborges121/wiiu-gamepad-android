# Investigation conclusion

Date: 2026-09-12

The reconnect failure is a coordination/lifecycle issue, not an H.264 decoder or pixel-format issue.

1. Android loses its peer address when its process restarts.
2. Android waits for a DSU client callback before starting `VideoStreamClient`.
3. The deployed Cemu instance disconnected when the phone stopped, then did not send a new DSU request.
4. With no DSU callback, Android has no host to retry and correctly remains at `Clients 0` / `Awaiting stream`.

The initial stream itself worked: Cemu accepted Android and received an IDR request. The failure begins only after the disconnect/restart boundary.
