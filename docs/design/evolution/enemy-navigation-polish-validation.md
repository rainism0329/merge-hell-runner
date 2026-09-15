# Enemy terrain navigation validation

2026-09-15. This check covers the enemy navigation changes only. The probe uses the production `ObstacleManager`, `ActorVisuals`, `ChapterActorRenderer`, `ExplorationRenderer`, and shipped chapter artwork. Fixtures set starting positions once; no enemy positions, health, movement timers, or attack states are injected during simulation.

## Behavior and regression coverage

Ground troops approach a wall before climbing vertically beside its face, cross the top, and descend beside the opposite face. Vertical movement is restricted to three pixels outside a physical wall face; a distant starting position cannot produce a diagonal floating ascent. Winged troops retain the shorter upper/lower route, and drillers move continuously underground until their entire body has room to emerge. Navigation suspends firing and resets the commitment before firing resumes. Ground movement segment limits remain in force. A full-body swept check prevents fast movement or knockback from embedding an enemy inside a wall.

`EnemyTerrainNavigationTest` checks every authored wall and low roof in all five chapters with the corresponding enemy families. Further cases cover an overhang, adjacent walls, a high-speed movement step, repeated knockback, safe driller emergence when the player stands over the foundation, movement boundaries, firing after traversal, and approaching the wall before a vertical climb. The model package run passed **133/133** tests; output: `build/polish-nav/model-tests.log`.

## Production render keyframes

Reproduce with the test main `com.bigphil.mergehell.model.EnemyNavigationVisualProbe`, optionally passing an output directory. The latest wall-contact revision is in `build/polish-nav/visual-wall-contact`; the earlier diagonal-path comparison is retained in `build/polish-nav/visual`.

- `navigation-keyframes.png`: seven rows, four actual simulation frames per row.
- `route-1.png` through `route-7.png`: individual rows at original resolution.
- `results.csv`: traversal and firing measurements.
- `stress.txt`: bounded navigation workload timings.

| Fixture | Enemy | Frames to cross | Frames from crossing to next shot |
|---|---|---:|---:|
| Chapter 1, street wall | Conflict | 124 | 75 |
| Chapter 2, low maintenance roof | Tech debt | 335 | 79 |
| Chapter 3, high gantry | Warden | 286 | melee unit |
| Chapter 4, foundry wall | Slag spitter | 290 | 113 |
| Chapter 5, chitin wall | Mirror | 218 | 100 |
| Chapter 3 material, overhead beam fixture | Rigger | 89 | 94 |
| Chapter 4 material, joined foundations fixture | Driller | 205 | melee unit |

The first five fixtures use their chapter's authored geometry. The last two deliberately compose an overhead beam and adjacent foundations to exercise alternate routing. Visual inspection confirms continuous ascent, crossing, and return to ground; the rigger uses the lower opening, and the driller shows its underground crest before emerging outside the foundations. No hovering deadlock or firing failure occurred. Climbing is represented by translated and animated existing enemy bodies, without adding a dedicated hand-grip animation.

## Timing and limits

The stress probe simulates 32 enemies and two obstacles for 420 ticks, with two warmup runs and ten measured runs. On this machine, combined model/update/navigation time was **p50 0.004 ms, p95 0.008 ms, p99 0.015 ms, maximum 1.733 ms**. The first cold scenario's maximum was 4.512 ms. These timings exclude Swing painting, IDE scheduling, collision combat outside navigation, and audio; they are not end-to-end FPS claims. Planning uses configuration-space rectangle intersections; movement itself retains fine per-step collision validation.

This is deterministic headless validation and inspection of production-rendered frames. It does not replace an interactive IDE playtest. Ground units continue to respect existing land-segment edges; the navigator is intentionally local and does not seek a path across an entire level or cross an unrelated gap boundary.
