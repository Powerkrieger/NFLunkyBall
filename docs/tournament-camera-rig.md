# Tournament camera rig

Hardware plan for filming a Flunkyball tournament from the roof of the stable so the
footage can be fed through the overhead video-analysis pipeline (`data/wm-squads/analysis/`,
gitignored) that was first used on the WM7 phone recording. Decided 2026-09-15.

## Requirements

* Court is 9 × 4 m; we want the court **plus 3–5 m of player zone on each end** in view
  (~16–18 m along the long axis), so player movement can be analysed, not just the throws.
* Three views in a `\ | /` layout: one camera straight down over the court centre, two 5 m
  off-centre looking obliquely. **All three views are used for analysis**, so all three
  cameras must meet the same spec.
* ≥ 25 fps at 4K. The ball crosses the court in ~0.27 s (≈ 8 frames at 30 fps); the
  pipeline's ball detector needs those frames. Resolution matters more than frame rate
  (pose already ran at imgsz 1920 on 4K), but 20 fps is too little.
* Manual exposure/shutter (≤ 1/500 s) so brightness does not pump during the day and the
  ball-streak and bottle-standing detectors see consistent input.
* Works fully offline — no cloud, no account, no internet needed.
* Weatherproof, PoE-powered, recordable from a laptop over Ethernet (RTSP), plus local
  microSD as backup.

## Geometry

Ground coverage = 2 · h · tan(FOV/2). Roof beams are 6–9 m up; mount on the lowest beam
over the court centre you can reach.

| lens | height | covers (long × short) | extra per side | px / m | bottle / ball ⌀ |
|---|---|---|---|---|---|
| 2.8 mm (~107° H) | 6 m | 16 × 6.5 m | 3.5 m | ~240 | ~17 px |
| **2.8 mm** | **7 m** | **19 × 7.4 m** | **5 m** | **~200** | **~15 px** |
| 4 mm (~87° H) | 9 m | 17 × 8 m | 4 m | ~225 | ~16 px |

→ 2.8 mm at 6–7 m. Only if the centre camera has to go on the ridge (8–9 m) swap it for
the 4 mm variant (`-0360` = 3.6 mm is the Dahua alternative).

Side cameras: 5 m off-centre at ~7 m is ~35° off vertical; slant distance to the far edge
~10 m → ~200 px/m there. Same lens as the centre camera.

## Camera choice

Constraint set: 4K @ ≥ 25 fps, manual shutter, ~2.8 mm, SD slot, PoE, EU firmware,
~€200. That set is narrow:

| camera | 4K fps | manual shutter | notes |
|---|---|---|---|
| **Dahua IPC-HDW3841EMP-S-0280B-S2** | 25/30 | yes | **chosen** — €222.99 incl. VAT at germanprotect.com |
| Hikvision DS-2CD2387G2H-LIU 2.8 mm | 25 | yes | from ~€265 |
| TP-Link VIGI C485 2.8 mm | 25 | yes | ~€270 in DE at time of writing (4 mm ~€190) |
| TP-Link VIGI C385 2.8 mm (bullet) | 25 | yes | ~€130–160; needs a right-angle mount; the only cheaper honest option |
| Reolink RLC-833A / 820A | 25 | **no** | rejected: no fixed shutter |
| Dahua 2-series (28xx), Hikvision 2383G2/2386G2, Uniview IPC3618, Amcrest turrets | **20** | | rejected: fps |
| Raspberry Pi + Camera Module 3 | 4608×2592 @ 14 fps or 2304×1296 @ 56 fps | scripted | rejected: no 4K30 mode, Pi 5 has no HW encoder, ~€160/station anyway |
| used GoPro / DJI Osmo Action | **4K60** | yes (Protune) | real alternative *if* live streaming is dropped: USB-powered via PoE splitter, records to card, overheats at 4K60 in sun, sync by clap |

Dahua model-number decoding: `IPC` IP camera (not `HAC` = analog HDCVI) · `HDW` eyeball/turret
(`HFW` = bullet) · `38xx` 3-series → 25/30 fps at 8 MP (`28xx` 2-series → 20 fps) · `E` fixed
lens · `M` mic · `P` PAL/EU firmware · `-S` SD slot · `0280` 2.8 mm · `B`/`W` black/white ·
`S2` hardware revision.

Verified from the S2 datasheet: main stream 3840 × 2160 @ 1–25/30 fps; shutter
auto/manual 1/3–1/100 000 s; 2.8 mm = 107° H × 56° V, F1.4; tilt 0–78° (0° = straight
down, so the turret hangs from a beam without extra brackets); microSD ≤ 256 GB; 4.9 W PoE
(8.9 W max with IR).

## Buy list

| item | qty | ≈ € | notes |
|---|---|---|---|
| Dahua IPC-HDW3841EMP-S-0280B-S2 | 3 | 669 | germanprotect.com (€222.99 each); Amazon.de ASIN B0BM3YHSRD lists the same SKU |
| TP-Link TL-SG105PE (5-port gigabit, 4× PoE+ 802.3af/at, 65 W) | 1 | 35 | Amazon B08GDC61NS. Managed "Easy Smart" → per-port PoE power-cycle from the laptop. Unmanaged TL-SG1005P also fine. **Never passive 24 V PoE.** |
| deleyCON 30 m CAT7 Outdoor, copper, S/FTP, 2× RJ45 | 3 | 81 | Amazon B0H3VJTJ46 (€26.99). Chosen over the ~€20 CCA cables (B0BLH6KG7D / B0CBJVL7K6): CCA works at 30 m / 9 W but is brittle at the plug and corrodes outdoors. B0C4B6K5SY (AWG 26 CU, €29.90) is the same family. |
| SanDisk High Endurance 128 GB microSD | 3 | 70–108 | B07NY23WBG (€36; often €20–25 elsewhere). Not Extreme/Extreme PRO — endurance rating matters for continuous recording, speed doesn't (4K H.265 ≈ 2 MB/s). |
| USB SSD | 1 | 105–240 | Crucial X9 1 TB ≈ €105 is plenty (a day ≈ 130 GB); 2 TB (B0CGW18S6Y) €240 |
| Screws / L-brackets / zip ties | — | 20 | |
| **Total** | | **≈ €980–1 150** | |

## Data budget

3 cameras × 4K25/30 H.265 at ~12–16 Mbit/s ≈ 16–22 GB/h → **≈ 130–180 GB per 8 h day**
(H.264 roughly doubles that). Network load ~50 Mbit/s total. Upload to citlab6 at ~5 MB/s
≈ 7–10 h → start uploading centre-cam segments during the day.

## Camera setup (once, at home on the bench)

1. First login sets the admin password. Disable **P2P** (Network → Access Platform),
   UPnP, DDNS, Bonjour, auto-registration. Do not use the DMSS phone app.
2. Static IP on a private subnet (e.g. `192.168.10.11–13`), **no default gateway**.
   Laptop on `192.168.10.2`. If the laptop also needs internet, use its Wi‑Fi for that.
3. Time: run `chrony` on the laptop (`allow 192.168.10.0/24`) and point all cameras'
   NTP at `192.168.10.2` so timestamps line up; still do a clap/flash at match start.
4. Event → **Smart Plan: deselect everything** (IVS, SMD…). AI features are useless here
   and can cap the frame rate.
5. Camera → Conditions: Exposure **manual**, shutter 1/500 (or 1/500–1/1000 range),
   gain capped, **WDR off**, AI SSA off, Day/Night = colour, IR off, BLC/HLC off.
6. Camera → Video → Encode: H.265 (not H.265+/Smart Codec — variable GOP), 3840 × 2160,
   **30 fps** (switch System → General → Video Standard to NTSC if only 25 is offered),
   CBR 12–16 Mbit/s, I-frame interval 30.
7. Storage: format the microSD in the camera UI, schedule continuous recording as backup.
8. Calibrate: film an A3 checkerboard for `cv2.calibrateCamera` (2.8 mm has visible
   barrel distortion); court corner points for the ground-plane homography.

## Recording on the day

```
ffmpeg -rtsp_transport tcp \
  -i "rtsp://user:pw@192.168.10.11:554/cam/realmonitor?channel=1&subtype=0" \
  -c copy -f segment -segment_time 600 -reset_timestamps 1 \
  /media/ssd/cam1_%03d.mp4
```

One process per camera, 10-minute segments so a crash loses at most 10 min. `-c copy` →
near-zero CPU. Put switch + laptop on a power strip you control (cameras reboot for
~60 s on power loss). Live analysis of 3 × 4K needs a GPU (yolo11x-pose ran at ~22 fps
per stream on an L40S) — record on the day, run the pipeline on citlab6 afterwards.

## Pipeline changes needed

* undistort step (2.8 mm), then homography per camera into a shared court frame
* re-measure fixed bottle-spot coordinates; retrain the 48 px bottle CNN on the new views
* cross-camera track association if side-view detections are merged with the centre view
* test session at the stable ~1 week before: mount, record 5 min of throws, run everything.
