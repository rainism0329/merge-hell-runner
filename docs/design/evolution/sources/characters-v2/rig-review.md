# Character rig source review — 2026-09-15

Read-only production review of `ActorVisuals.heroBody`, `partTransform`, `segmentTransform`, `export_characters.py`, the four resource atlases, and `build/depth/characters-preview.png`.

## Confirmed causes

- Engineer's original head already contains a scarf and neck. The torso contains another scarf and both upper-arm sleeves. Rendering the separate upper arm and scarf introduces duplicated anatomy. Anchor tuning alone cannot fully repair this source.
- Scout and Warden head parts include long necks / neck collars; their torsos also contain necks. Generic head neck `(0.55w, 0.92h)` joins these end-to-end instead of overlapping the neck inside the torso collar.
- The three newer rigs use common percentage anchors despite differing silhouettes. Robot near-side joint disks sit left of the frame center. In particular Warden's upper-arm anchor `(92.5,59.5)` is about 60 source pixels right of its visible shoulder disk center.
- Generic gun grip `(0.30w,0.86h)` lands near Warden's magazine rather than the pistol handle. Forearm distal at approximately 90% height targets fingertips instead of the center of the gripping palm.
- Original waist-level horizontal muzzle and single-arm rendering make a resting gun look detached. Current 9+11 IK bone lengths cannot quite reach Repair's original forward/upward grip: forward 20.34, diagonal-up 21.89, up 20.56 world units. Engineer forward reaches 20.12. The other measured basic directions are within reach.

## Revised engineer source

`engineer-source.png` was produced with the built-in image generation edit tool, using the original engineer source as the edit target. `engineer-prompt.txt` contains the complete prompt. `engineer-manifest.json` records the original generated path, original project source, SHA256, alpha validation and crop bounds.

The image is 1774×887 RGBA, with alpha extrema 0 and 255; 68.35% of pixels have alpha 0. Visual inspection confirms that the head's scarf is removed, the torso has no arm sleeves, and the jacket, employee ID and character identity remain. Only the revised head and torso should be consumed; retaining the other six clips from the old source guarantees that their pixels stay unchanged.

The first row remains four equal cells. The second row retains the old custom x intervals: 0–443, 443–820, 820–1340, 1340–1774. The trimmed head is 348×329 at source bounds `(65,46,413,375)`. The trimmed torso is 309×427 at `(524,9,833,436)`.

## Calibration starting points

All coordinates below are **pixels local to the trimmed frame**, visually estimated from the visible joint / hand. They are initial values for pose-sheet inspection, not algorithmically verified final coordinates. Expected final adjustment is roughly 0.5–1 world pixel.

| Rig | Head neck | Torso neck | Shoulder | Hip |
| --- | --- | --- | --- | --- |
| Repair, existing | 191,271 | 153,31 | 44,104 | 95,267 |
| Scout | 123,292 | 143,64 | 45,111 | 123,349 |
| Warden | 132,274 | 220,65 | 57,121 | 188,369 |
| Engineer v2 | 151,298 | 168,45 | 36,116 | 155,378 |

| Rig | Gun muzzle | Gun grip | Gun support hand | Forearm proximal | Forearm palm |
| --- | --- | --- | --- | --- | --- |
| Repair | 358,67 | 66,150 | 205,100 | 48,40 | 85,235 |
| Scout | 429,80 | 121,158 | 295,113 | 35,56 | 45,332 |
| Warden | 558,116 | 96,191 | 368,160 | 40,49 | 69,340 |
| Engineer, original arm/gun | 482,105 | 145,196 | 323,143 | 45,69 | 66,290 |

| Rig | Upper arm proximal → elbow | Thigh proximal → knee | Shin proximal → ankle |
| --- | --- | --- | --- |
| Scout | 30,70 → 64,350 | 36,58 → 82,361 | 53,40 → 54,298 |
| Warden | 31,86 → 100,368 | 44,61 → 64,356 | 51,56 → 62,297 |
| Engineer, original parts | 74,42 → 65,320 | 98,38 → 113,340 | 103,26 → 78,299 |

Repair's hand-authored body metadata already follows visible mechanical joint centers; retaining it is preferable to applying generic percentages. Head/torso overlap, chest-level shared muzzle geometry, a supporting hand and per-role proportions should be verified in forward, up, diagonal-up, down, run and crouch poses in both facing directions. Do not hide a failed arm reach by drawing the gun independently after the IK clamps its tip.

## Review of poses-v2.png

The new cleaned engineer head/torso removes the duplicate sleeve and neck problem. The raised muzzle visually places the weapon at the chest and the neck overlaps are much better. Three remaining visible issues in this intermediate pose sheet were reported to the implementation agent:

1. The crouch leg solver used `bend=-1`, sending both knees backward into a reverse-jointed shape. Human/mechanical humanoid knees should remain forward; a rear leg may bend toward a kneeling contact, while the front foot sits slightly ahead.
2. Generic world forearm length increased to 12 at the same time that the source end point changed from fingertips to the palm. This enlarged the whole hand. Preserve independent hand size or use a more appropriate forearm length. The original engineer source also has an open hand, which cannot be turned into a grip through IK.
3. Scout's full 392-pixel head frame includes a long antenna and collar. A 16-world-pixel frame gives its actual helmet only about 7.3 world pixels. Judge cranial mass rather than the entire frame extent; roughly 22 world pixels for the frame is a useful next preview starting point.

## Engineer gripping forearm replacement

`engineer-grip-source.png` is a second built-in image-generation edit of only the original source's top-right forearm cell. The new hand is compact and closed in a gripping pose; the rolled sleeve and skin material remain consistent. `engineer-grip-prompt.txt` is the complete edit prompt and `engineer-grip-manifest.json` provides source provenance, SHA256, alpha verification and crop bounds.

The output is 1774×887 RGBA with alpha extrema 0–255. Consume only `forearm`. Its source bounds are `(1489,50,1675,406)` and its trimmed size is 186×356. Suggested local metadata: elbow anchor `(48,68)`, wrist `(86,240)`, grip palm `(127,298)`; begin pose calibration at a world forearm length around 9.5. These are visual calibration starting points. The other seven pieces from this generation should not replace existing approved parts.

## Final static production-pose review

**Passed independent visual review** of `build/polish/characters/{repair,scout,warden,engineer}.png`. Each production-rendered matrix contains forward, diagonal-up, up, diagonal-down, down, left, crouch-left, two running phases and melee, shown at 3× and native size: 40 poses viewed at both scales.

The final matrices show continuous shoulder/arm connections, hands placed on the weapons, compact engineer gripping hands, coherent head-to-body proportions, and forward-bending crouched knees. The earlier duplicated engineer sleeves/scarves, exaggerated loose hands, detached-looking weapons, undersized Scout head and reversed crouch knees are no longer visibly present in these samples. Repair's larger CRT head and Warden's heavier frame read as intentional role silhouettes.

No remaining obvious anatomy or attachment defect was found in the reviewed static matrices. This is a static visual finding; continuous animation, aim-transition timing, collision and actual gameplay still require the implementation's separate checks.

## Continuous real-player sequence QA

Added the standalone test-source helper `src/test/java/com/bigphil/mergehell/render/CharacterMotionPreview.java`, compiled and run with **only the packaged production JAR** plus the helper classes. Both `Player` and `ActorVisuals` code-source locations were confirmed as `build/polish/packaged/mergehell.jar`; no production sources were changed for this check.

The helper runs 288 consecutive 16-ms simulation ticks per role, 1152 player updates total. All eight aim directions are reached through actual input state, and every role covers IDLE, RUN, RISE, FALL, LAND, DASH, MELEE and HURT. Double jump, airborne downward shooting, crouching movement in both directions, upward aim while hurt, and repeated direction changes occur without rebuilding the player or fabricating `Hero` poses. Each role emits 16 real volleys. Final HP is Repair 90, Scout 89, Warden 91 and Engineer 90 after the same 10-point damage event.

Artifacts: `build/polish/motion/index.html` provides 288-frame playback and slow motion; `frames/frame-000.png` through `frame-287.png` show 3× tracking views plus native-size views preserving real jump height. `motion.csv` contains every state; `summary.txt` records coverage and class provenance. Four `*-sequence.png` sheets contain 20 sampled actual frames per role.

**Visual result:** inspected all four sampled sequence sheets, plus the upward-aim hurt frames 208, 210 and 213, adjacent crouch-walk frames 156–157, and melee frames 216 and 221. No obvious hand/weapon separation or broken joint was visible at 3× or native scale in these inspected frames. Repair's upward aim during the hurt recoil remained visibly connected to its grip. This is a real continuously simulated sequence with sampled visual inspection, not a claim that every intermediate frame or every possible combat combination has been manually inspected.
