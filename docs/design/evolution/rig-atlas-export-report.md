# Mechanical rig cutout verification — 2026-09-11

Exported the original repair and hostile rig sheets into two genuine RGBA atlases, retaining the original metal wear and ivory repair-head casing. The alpha-retry variants were inspected but not selected because their material detail was smoother. All four input files remain unchanged; their hashes are recorded in `rig-atlas-provenance.json`.

| Atlas | Output size | Parts | Result |
| --- | --- | --- | --- |
| `repair.png` | 1604×774 | head, torso, upper-arm, forearm, thigh, shin, cannon, scarf | Tight RGBA frames, right-facing source direction |
| `hostiles.png` | 1640×840 | bug-body, bug-upper, bug-lower, debt-body, debt-arm, debt-leg, legacy-core, node | Tight RGBA frames, left-facing bodies |

Both selected 1774×887 input PNGs are RGB with baked checkerboard pixels. Transparency was reconstructed using connected background candidates and manually inspected internal apertures. Brightness alone never determines the alpha mask. Production pieces retain their original aspect ratio and source pixels apart from a narrow silhouette defringe band; no production part was stretched, rotated or resampled during export. The original alpha cannot be recovered exactly from a flattened RGB image, so edge coverage is a documented reconstruction.

Six full proof sheets were inspected at black, white and blue backgrounds (`build/art-export/{repair,hostiles}-proof-{black,white,color}.png`). The dark and colored proofs show no remaining rectangular checkerboard patches around the selected silhouettes. White proofs retain the dark claws, pipes and thin joints. Manual source-versus-cutout inspection opened 34 seeded apertures, including the head carry handle, cannon trigger/carry-rail gaps, small scarf tears, the bug belly pipe, hostile piston gaps, core cable loops and the node carry handle. The bright bug eyes, core light, node lens, scarf collar reflection and ivory head material remain opaque. Ambiguous metallic reflection pixels were preserved rather than automatically treated as holes.

Automated verification passed for both RGBA atlases, all 16 frame bounds with exactly one transparent guard pixel, all 34 aperture seeds, seven explicit protected light/material samples and 830,905 unchanged eroded interior RGB pixels. Every frame contains fully transparent, fully opaque and intermediate-alpha pixels. A second full export produced byte-identical PNGs, properties and provenance JSON (five files).

`ProductionArtTest` adds three production resource contract tests. Java 17 compiled the real asset package and the test in the isolated `build/art-export/classes` directory. All three passed using JUnit Platform Console 1.10.0: the real loader opens repair/hostiles/city with no diagnostics; every mechanical part has true alpha with visible material and antialiased edges; required clips and in-frame attachment sockets exist. This targeted run does not replace the project's full Gradle verification.

The `.properties` contain frame-local anchors and sockets, with source-space coordinates also recorded in provenance. Limb anchors mark proximal joints and `distal` marks lower joints, grips or sole/tip references. Cannon exposes `muzzle` and `grip`. Repair shin additionally exposes `ankle=(54,274)`, `heel=(20,368)` and `toe=(220,372)` for keeping the foot level; its existing anchor/distal are unchanged. The large round mechanical joints should use uniform scale and rotation. These coordinates and all eight clips per rig are ready for the actual Java skeleton; asset proof sheets are not game screenshots or playthrough evidence.

Reproduce with the commands in `scripts/art/README.md`. No Python dependency was added to the game runtime. This work changed the two atlas pairs, art scripts/report/provenance and the new production resource test; it did not change production Java rendering or gameplay.
