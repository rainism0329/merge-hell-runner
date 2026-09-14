# Mechanical rig atlas export

These scripts process the already approved generated PNG sheets. They do not generate new art and never overwrite the originals. The original repair/hostiles/first-wave sheets are RGB images with a baked checkerboard. The newer Heap actor sheet has native RGBA and follows the direct alpha-preserving export below.

Install the offline art-tool dependencies in a Python 3.10+ environment:

```powershell
python -m pip install Pillow==12.2.0 numpy==2.2.6 scipy==1.15.3
python scripts/art/export_rig_atlases.py
python scripts/art/verify_rig_atlases.py
```

On the verified Windows workstation the interpreter is `E:/Python310/python.exe`. No Python or imaging dependency is added to the Java game runtime.

The exporter writes `repair.png`, `repair.properties`, `hostiles.png` and `hostiles.properties` under `src/main/resources/game/art`; it records source/output hashes, crop rectangles, anchors, sockets, inspected aperture seeds and alpha statistics in `docs/design/evolution/rig-atlas-provenance.json`. The verifier checks the exported pixels against those records and the unchanged source RGB.

Visual proof sheets and individual parts are written to `build/art-export`. Open both `*-proof-black.png`, `*-proof-white.png` and `*-proof-color.png`: the light background reveals missing dark geometry; the dark and colored backgrounds expose checkerboard spill and closed holes. `*-pivots.png` marks each frame's anchor in mint and sockets in pink. These are asset inspection composites, not game screenshots or playthrough evidence.

Extraction uses neutral/bright pixels only as background candidates. Candidates must connect to the cell border or a manually inspected aperture seed before removal. Enclosed pale armor and light sources remain intact. A narrow 2px silhouette band receives interior RGB defringing and a 0.45px coverage matte. Every frame has a tight nonzero-alpha bounding box plus one transparent guard pixel. Production parts are neither stretched nor resampled.

When the source changes, re-inspect all hardcoded source-space anchors, sockets and aperture seeds in `RIGS`; they are specific to the selected 1774×887 sheets. Do not replace the topology step with a blanket brightness threshold. The `PROTECTED` samples in the verifier preserve the ivory head casing, scarf reflection and enemy/core lights. Keep the original sources under version control so the process stays reproducible.

The `.properties` use the existing `AssetStore` format 1. Anchors and sockets are frame-local pixels. They are not atlas coordinates. Limbs preserve their original angles; align the anchor→`distal` vector using rotation and uniform scaling. `distal` is the lower joint for upper arms/thighs, the grip for forearms, a sole reference for boots and the pointed tip for bug lower legs. Repair `shin` additionally exposes `ankle`, `heel` and `toe`; heel/toe lie just below the high-coverage sole edge, and their maximum Y is the floor-contact line. The cannon anchor is also `muzzle`, with a separate `grip`; `head` exposes `neck`, `visor` and visor bounds; `torso` exposes `neck`, `shoulder` and `hip`. Repair parts face right. Hostile bodies face left; the bug upper leg runs from its right hip toward its left elbow. Mirror the assembled rig consistently.

## First-wave full-body enemies

```powershell
python scripts/art/export_first_wave.py
```

This independent exporter writes only `first-wave.png` / `first-wave.properties` and its own provenance/proofs. It reuses the inspected matte function from `export_rig_atlases.py` without re-exporting the existing repair or hostile rigs. It suppresses Python bytecode output so imported helpers do not leave `__pycache__` in the repository.

The first three enemies come from `first-wave-generated.png`; the narrow furnace comes from `first-wave-firewall-generated.png`. Both were generated with the built-in image tool, with complete prompts stored next to the source files. The original broad furnace remains in the first sheet as an unselected variant. Generated source files are never overwritten.

These are complete body clips (`conflict`, `crash`, `lock`, `firewall`). Their anchors are bottom-center contact references. `core` marks a visible optic or furnace bar; Crash also has `wheel` / `wheel-rim` for rotating the circular inner hub, and Lock has `aperture` for checking its transparent shackle opening. The tall furnace is uniformly reduced to a maximum 768px content edge before adding a transparent guard; production rendering preserves each body's aspect ratio inside its existing logical size.

`build/first-wave/proof-{black,white,color}.png` checks cutouts. `FirstWaveVisualPreview` renders native-size, 600×400 scaled, pose and phase inspection images through production ActorVisuals. Its output explicitly identifies itself as an art inspection, not a GamePanel capture. The main `GameVisualPreview` performs the separate full-panel integration check. `ProductionArtTest` and `FirstWaveArtTest` protect real alpha, required clips/sockets, contact alignment, circular geometry, actual logic-driven warning transitions and the absence of an airborne contact shadow.

## Heap pipeworks and machines

```powershell
python -B scripts/art/export_heap_art.py
```

This exports only `heap-background.png/.properties` and `heap-actors.png/.properties`.
The background stays RGB and pixel-identical to its source. The actor sheet is already
RGBA: its original alpha, metallic highlights, dark core material and existing hose
holes are preserved without threshold removal. Boss is uniformly reduced to a maximum
768 px content edge; all three cropped frames receive a transparent guard. The separate
shutter clip can be mirrored for code-driven opening/closing around the core socket.

See `docs/design/evolution/heap-art-export.md` and `heap-art-provenance.json` for exact
sizes, coordinates, protected samples, deterministic output hashes and independent
re-export commands. The black/white/color proofs are in `build/heap-art-export`.
`HeapVisualPreview` uses the real controller and production renderers, while
`HeapRendererTest` checks true-resource contracts and state-driven visuals.
