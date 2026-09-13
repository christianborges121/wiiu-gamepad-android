# Input outage: DHCP churn vs static DSU config

Date: 2026-09-12 evening.

## Symptom

Video streaming fine (59 FPS Mario file select), but no input. Phone
overlay: `c0 tx0 rx0` — zero DSU clients, zero DSU traffic.

## Root cause

The phone's DHCP address churned twice today (.193 -> .109 -> .114).
Video survives because it is phone-initiated toward the stable PC IP
(persisted `last_cemu_ip`). Input dies because it is Cemu-initiated:
`controller0.xml` still points the DSU client at `192.168.68.109`
(written 21:14), a dead address. Cemu polls nobody; nothing phone-side
can fix that — the protocol direction only allows Cemu to poll.

This also explains the afternoon "no input" report (phone was .109 while
Cemu likely still had .193).

## Fixes, in order

1. Now: set Cemu's DSU controller IP to the phone's current address
   (tonight: `192.168.68.114`), via Cemu input settings UI. Do not
   hand-edit the XML while Cemu runs; it rewrites these files itself.
   Done 2026-09-12 evening, input works again.
2. Durable: DHCP reservation (or static IP) for the phone so the address
   stops moving. Done 2026-09-12 evening (router reservation for .114).
3. Proper: UDP discovery on port `26763` (Phase 4 checklist) so Cemu
   finds the phone dynamically and static IPs disappear entirely.
   Still open; urgency lowered now that the address is reserved.
