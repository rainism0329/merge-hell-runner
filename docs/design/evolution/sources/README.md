# Industrial salvage source assets

Created on 2026-09-10–11 using the built-in `image_gen` tool. The visual reference is
`../concepts/01-industrial-salvage.png`, selected by the user as the primary direction.
These are generated illustrations, not captured gameplay or third-party stock images.
The exact generation and revision prompts are retained beside the files.

| Source | Intended use | Current verification |
| --- | --- | --- |
| `city-generated.png` | Distant server-city panorama | 1774 × 887 RGB; copied unchanged into `game/art/city.png` |
| `repair-rig-generated.png` | Eight separate hero parts | 1774 × 887 RGB source with baked checkerboard; selected for the verified RGBA repair export |
| `hostiles-rig-generated.png` | Bug, TechDebt, Legacy core and node parts | 1774 × 887 RGB source with baked checkerboard; selected for the verified RGBA hostiles export |
| `repair-alpha-retry.png` | Image-generation background-removal retry | Still RGB; not accepted as a transparent runtime atlas |
| `hostiles-alpha-retry.png` | Image-generation background-removal retry | Visible checkerboard remains; not accepted as a runtime atlas |
| `first-wave-generated.png` | Complete Conflict, Crash and Lock bodies; initial broad Firewall variant | 1261 × 1247 RGB with baked checkerboard; first three bodies selected for the verified RGBA first-wave export |
| `first-wave-firewall-generated.png` | Narrow furnace revision for the 60 × 120 Firewall footprint | 1024 × 1536 RGB with baked checkerboard; selected over the broad variant and uniformly fitted |
| `heap-background-generated.png` | Dedicated storage-tank and industrial-pipeworks panorama | 1774 × 887 RGB; copied pixel-for-pixel into `heap-background.png`; prompt in `heap-background-prompt.txt` |
| `heap-actors-generated.png` | Memory Reclaimer outer shell, Leak pressure canister and separate sliding shutter | 1448 × 1086 native RGBA; source alpha and pale-metal coverage preserved; prompt in `heap-actors-prompt.txt` |

Hero cell order, left to right then top to bottom: head, torso, upper-arm,
forearm, thigh, shin, cannon, scarf. Enemy order: bug-body, bug-upper,
bug-lower, debt-body, debt-arm, debt-leg, legacy-core, node.

The user explicitly authorized code-based background removal and atlas cutting.
That export is complete: `repair`, `hostiles` and `first-wave` have genuine RGBA
runtime PNGs and matching frame/anchor/socket properties under `src/main/resources/game/art`.
Their black/white/color cutout proofs, actual-size renderer previews and real loader
checks passed. The original four resource sets, including the intentionally opaque `city`
panorama, are connected through IndustrialArt. The independent `heap-background` and
`heap-actors` resources are preloaded by HeapWorldRenderer / HeapActorRenderer. Code
fallbacks remain available for missing resources and enemy types outside these sets;
the supplied mechanical characters use the exported art.

All generated sources remain unmodified. `scripts/art/export_rig_atlases.py` exports
the original two rig atlases; `scripts/art/export_first_wave.py` exports the independent
full-body first-wave atlas. `scripts/art/export_heap_art.py` exports the Heap panorama
and three actor parts; this source has genuine alpha and does not use checkerboard removal.
The provenance files and export reports in the parent
directory record selected inputs, inspected apertures, output hashes and verification.
The initial broad Firewall stays in its original sheet as an unselected variant.

Heap now uses its own illustrated pipeworks panorama. The city is still shared by the
other districts with local lighting tints; these do not constitute five completed world
environment sets. Hero animations are driven by
simulation poses and articulated pieces, while collision bounds remain unchanged.

The paragraph above records the state of the 2026-09-11 export. On 2026-09-14, the chapter
overhaul added independent painted construction-citadel, geothermal-foundry and alien-hive
environments plus nine specialists and three modular Boss sets. Their untouched sources and
exact built-in generation prompts are in [chapter-premium](chapter-premium/README.md), and
the runtime export/alpha verification is documented in
[chapter-premium-art.md](../chapter-premium-art.md). The later chapter sets do not reuse the
city panorama or a colored procedural substitute.
