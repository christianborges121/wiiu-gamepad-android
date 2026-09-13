# UDP video transport protocol (v1, no FEC)

Date: 2026-09-12

## Goals

Replace TCP head-of-line blocking for video while keeping TCP as the
control channel and fallback. Phone-initiated negotiation; no Cemu
config changes.

## Ports

- TCP `26761`: control (existing `IDR_REQUEST 0x10`) + fallback video.
  Unchanged framing.
- UDP `26761`: primary video once negotiated (same number, different
  protocol; no conflict).

## Negotiation (TCP control opcodes, phone -> Cemu)

- `0x11 TRANSPORT_UDP`: send video via UDP from now on.
- `0x12 TRANSPORT_TCP`: resume video via TCP (fallback).

Cemu default per client is TCP. Phone sends `TRANSPORT_UDP` after TCP
connect (+IDR as today). Phone ignores TCP video packets once UDP
frames flow (no duplicate decode); on 2 s without a complete UDP frame
it sends `TRANSPORT_TCP` and resumes from TCP. Stays on TCP until the
next reconnect (stable, no flapping).

## Datagram layout (all multi-byte little-endian)

```text
offset size field
0      2    magic = 0x5043 ('C','P')
2      1    version = 1
3      1    flags: 0x01 START | 0x02 END | 0x04 IDR (0x08 reserved: parity)
4      4    frameId (monotonic per encoded frame, wraps)
8      4    seq (monotonic per packet, wraps; loss detection)
12     2    packetIndex (0-based)
14     2    packetCount
16     8    ptsUs
24     <=1400 payload (Annex B H.264 bytes in original order)
```

1424 bytes max on the wire: safe under a 1500 MTU.

## Reassembly (Android)

- Slot per `frameId`, at most 4 concurrent; packet bitmap by index.
- Complete (all `packetCount` seen) -> concatenate in index order and
  feed the decoder (byte-identical to the TCP payload).
- Drop incomplete after 100 ms or when superseded (keep-latest policy:
  a newer `frameId` evicts the oldest slot). Count loss via `seq` gaps.
- Lost IDR (incomplete frame with IDR flag) -> immediate IDR request;
  bounded like the existing SPS/PPS recovery.
- Ground truth for frame type stays `AvcNalUnits` parsing; the IDR flag
  is a hint only.

## Explicitly v1-out

FEC parity packets, loss feedback to Cemu, reference-frame
invalidation, encryption. Header reserves the parity flag bit.
