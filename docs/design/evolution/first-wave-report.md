# First-wave mechanical enemy art — 2026-09-11

Added four mechanical enemies to the approved industrial-salvage direction: Conflict, Crash, Lock and Firewall. Their real ActorVisuals path now uses the new `first-wave` atlas. No gameplay logic, collision dimensions, player weapon code or HUD was changed in this work.

| Enemy | Existing logical size | Visual identity | Rendering behavior |
| --- | --- | --- | --- |
| Conflict | 40×40 | Wide split/Y shape with two opposing clamp arms | Complete rigid body; existing movement, hit reaction and death fade |
| Crash | 50×50 | Low wedge ram and prominent demolition wheel | Rigid ram/exhaust with a rotating circular inner hub; hit/death feedback |
| Lock | 40×40 | Thick padlock arch, open shackle hole and shield-shell optic | Follows the real logical hover height with slight roll; no floating contact shadow |
| Firewall | 60×120 | Narrow furnace tower, two chimneys and three recessed hot bars | Tall body fit without stretching its bearings; hit/death feedback |

The red/orange lights are paired with distinct silhouettes, rather than being the only enemy identifier. Conflict and Lock display extra warning feedback only when their real `Enemy.getTelegraphTicks()` is positive. Crash and Firewall do not receive invented attack warnings from their animation phase. All four preserve the existing facing transform, fallback behavior when an asset is unavailable, health display and tracked death duration.

## Generation and export

The built-in `image_gen` tool produced the initial 2×2 sheet from `01-industrial-salvage.png` and the existing hostile-rig reference. The first Firewall result was too broad to fit its tall collision rectangle without either leaving excessive space or stretching the material, so a second built-in edit produced the selected narrow tower. Source images, both complete prompts and the unused broad furnace remain preserved under `sources/`:

- `first-wave-generated.png` and `first-wave.prompt.txt`
- `first-wave-firewall-generated.png` and `first-wave-firewall.prompt.txt`

Both generated PNGs were RGB with a baked checkerboard despite the explicit transparency request. Under the user's existing authorization for code-based extraction, `scripts/art/export_first_wave.py` reconstructs alpha using the established topology-aware matte. Neutral/bright candidates are removed only when connected to the crop border or six visually inspected aperture seeds. It preserves the metal highlights and red optics. The nominal sheet cells overlap slightly; inspected crops select each complete connected body instead of cutting the clamp at the grid boundary.

The shipped atlas is **1300×1556 RGBA** with four tightly bounded frames and one transparent guard pixel. The original three bodies keep their source resolution; the tall furnace receives uniform downsampling before the final guard is added. The repair/hostiles source files, atlas PNGs and properties were not modified. `first-wave-provenance.json` records source hashes, matte helper/script hashes, source crops, inspected holes, contact anchors, sockets, alpha counts and output hashes.

## Checks and limits

Black, white and blue proof sheets were visually checked. No remaining checkerboard rectangles were visible around the silhouettes or in the inspected lock/handle/pipe holes. The lock opening remains transparent and all four core samples remain opaque. An isolated repeat export under `build/first-wave/repro/` produced identical PNG, properties and provenance hashes without touching the frozen production outputs. The original rig verifier also still passes all 16 original frames, 34 original aperture seeds and seven protected highlights.

Seven targeted JUnit checks passed using JDK 17 and the real production asset loader, compiled to the separate `build/first-wave/classes` directory. They cover all four shipped atlas resources, alpha/opaque/antialiased coverage, required clips and sockets, uniform body fitting/contact alignment, preserved light pixels and lock aperture, actual warning onset/clearing through Enemy shooting state, and no contact shadow below an airborne Lock. No formal Gradle run was started by this subtask; the root task owns the integrated build.

`FirstWaveVisualPreview` outputs `native-960x600.png`, `scaled-600x400.png`, a 2× pose sheet and 24 phase samples in `build/first-wave/preview/`. Native and reduced previews were inspected: the fork, wheel, shackle and tall furnace remain distinguishable, and the furnace fits its intended height without widening into a TechDebt-like giant. The three small enemy bodies are still small at 600×400, so fine paint wear is intentionally subordinate to silhouette and glowing functional parts.

These are real renderer inspections with fixed poses, not a natural level playthrough or GamePanel screenshot. The separate full-panel preview and formal integration checks remain the root task's responsibility. The new art uses complete rigid bodies plus local hub/hover feedback; it does not claim separately articulated Conflict limbs or a fully authored animation sheet for every enemy action.
