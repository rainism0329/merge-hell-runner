# Heap chapter: actual panel verification captures

Captured on 2026-09-11 with `HeapChapterPreview`, compiled from the final production and
test sources by the formal Gradle verification run. The preview uses the actual
GamePanel simulation, ActionMap, FrameMailbox and EDT painting at 960 × 600 and
600 × 400. Ten representative PNGs are retained here; the complete output consists
of 20 scene PNGs and 24 sequential motion PNGs in `build/heap-game-preview`.

Chapter entry, actor positions and selected upgrade progress are explicit fixture
setup. Interactions, damage, GC exposure, HUD and pause then use production state.
`frames.csv` records the sampled state and tick counters. These are headless panel
captures, not a normal playthrough, native IDE capture or performance measurement.

- `gc-choice-*`: real E/F availability and compact HUD separation.
- `salvage-warning-960`: actual risk choice and warning phase.
- `leak-active-600`: active pool bounds and used station in the compact viewport.
- `gc-channel-960` / `gc-cleaned-960`: channel and completed cleanup.
- `boss-reflux-960`: returning memory-block encounter.
- `boss-exposed-960`: opened mechanical core and actual +50% window.
- `boss-impact-960`: actual 450-damage bomb hit, trailing HP and warm flash.
- `boss-paused-600`: pause overlay above frozen Boss HUD and environment.

The complete implementation and verification record is
[Heap chapter and Boss feedback](../../2026-09-11-heap-chapter-and-boss-feedback.md).
