# Heap District production visual inspection

Heap now has an independent illustrated industrial pipeworks backdrop, a textured
44 × 44 Leak pressure-canister enemy and a textured Memory Reclaimer Boss inside its
existing 120 × 150 logical box. This keeps the selected warm, worn industrial material
direction. It replaces the flat code prototype in normal rendering; that prototype
remains available when a resource is missing.

The background and actor sheet were generated with the built-in image_gen tool. Both
unmodified sources and complete prompts are in `docs/design/evolution/sources`. Export
details, native alpha, protected highlights and reproducibility are recorded in
`docs/design/evolution/heap-art-export.md` and `heap-art-provenance.json`.

HeapWorldRenderer draws the backdrop in screen coordinates, then GC stations, leakage
pools and returning memory blocks in world coordinates. The latter consume exact
HeapDistrictController snapshot bounds and states. Warning hatch, active surface,
channel progress, used station and cooldown indicators never infer state from a clock
or attack label. HeapActorRenderer fits source parts uniformly and uses the same core
socket transform for the light and paired sliding shutters. Actual Boss vulnerability
opens the chest; hit ticks add a local impact highlight. It does not invent attack lanes.

Seven tests passed on Java 17 against the real IC 232 SDK in an isolated output directory:

- Risk-pool warning/active transitions keep the same real controller geometry.
- GC channel completion changes the visible station and painting does not mutate state.
- Inactive snapshots and their background animation render identically.
- Leak's native body is visible and the renderer declines other enemy types.
- A real break-window value opens the core without shifting the outer body.
- Drawing preserves caller graphics state.
- Both production atlases load without diagnostics and retain alpha guards, substantial
  material coverage, required clips and ordered core sockets.

`HeapVisualPreview` produced 62 PNGs in `build/heap-visual/preview`: route GC states,
risk-pool warning/active states, returning block warning/motion, Boss channel/exposure/
hit/stage-3 states, compact 600 × 400 views, a magnified part sheet and 48 sequential
simulation frames. Native and compact screenshots were inspected after final texture
integration. The worn metal remains visible, the Boss port stays round, the Leak keeps
its natural proportions, and ground danger remains distinct from the background.

These images are labeled art probes with controlled placement and real controller
actions. They are not GamePanel captures or playthrough evidence. Full-panel integration,
formal Gradle/package checks and overall game completion are the main task's separate
validation. No formal Gradle run or shared production class directory was changed here.
