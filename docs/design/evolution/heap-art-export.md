# Heap material export

The Heap panorama and three machine parts are exported by `scripts/art/export_heap_art.py`.
Both generated source files and their prompts remain under `docs/design/evolution/sources`.
The exporter never writes those sources or any previous game atlas.

| Atlas | Mode | Size | Clips |
| --- | --- | --- | --- |
| `heap-background.png` | RGB | 1774 × 887 | `backdrop` |
| `heap-actors.png` | RGBA | 1311 × 778 | `boss-shell`, `leak-body`, `shutter` |

The background retains all source pixels. It occupies a separate atlas so the machine atlas
does not carry a panorama-sized transparent area. Boss, Leak and shutter frames measure
737 × 770, 378 × 441 and 180 × 510 pixels, including a transparent 1px guard. Every frame has
a tight nonzero-alpha bounding box inside that guard.

Unlike earlier RGB rig sheets, this generated actor sheet already has genuine alpha.
The exporter directly crops it: no connected-background matte, alpha normalization, edge
defringing, manual hole removal or disconnected-component filtering is applied. Boss is
uniformly reduced to a maximum 768px content edge, with subpixel rounding to integer image
dimensions; Leak and shutter are not resampled. Source RGB and alpha are verified unchanged
before that optional resize.

Most source body pixels have alpha 252 or 253, rather than 255. Those values remain intact.
Eight inspected pipe/hose apertures retain their original near-transparent coverage
(alpha 1–4 after export). The black reactor cavity remains dark material with alpha 251 at
its protected center sample. Nine protected metal, glass, light and cavity samples retain
the source RGBA before resizing and high coverage after resizing. No brightness-based test
turns pale metal into holes.

Boss and Leak use bottom-center frame anchors. Boss also exposes `core`, `core-left`,
`core-right` and `fan`; Leak exposes `core`, `eye` and `flow`. The shutter uses a center
anchor and may be mirrored as a pair. All points in the properties file are frame-local.
The provenance records their inspected source-space coordinates and export transforms.
Preserve the frames' aspect ratios when fitting the 120 × 150 Boss and 44 × 44 Leak logical
areas; these exported dimensions do not change collision geometry.

The production HeapActorRenderer uniformly fits each body into its existing logical box,
centers it horizontally and aligns its bottom. The Boss core and its radius use the same
scale/offset as the `core` / `core-left` sockets. The separate shutter retains its source
aspect ratio and is mirrored as a pair; the real vulnerability timer opens the doors.
The core is a code-rendered light inside the generated dark cavity, so live state remains
separate from the illustration. Leak facing mirrors the complete body without distortion.

HeapWorldRenderer eagerly preloads the two reflected 1200 × 600 background images and
invokes HeapActorRenderer's preload hook during GameRenderer construction. The actor
atlas uses AssetStore's existing minified-frame cache. Drawing performs no file I/O,
decoding or image cropping. The earlier code-rendered machines and pipework remain as
missing-resource fallbacks. GC stations, exact pool bounds, returning memory blocks and
their real controller states stay in the code layer. No runtime dependency was added.

The following command reproduces the production resources and inspection composites:

```powershell
E:/Python310/python.exe -B scripts/art/export_heap_art.py
```

Use separate destinations to verify reproducibility without replacing production output:

```powershell
E:/Python310/python.exe -B scripts/art/export_heap_art.py --output-dir build/heap-art-export/repro/resources --qa-dir build/heap-art-export/repro/proofs --provenance build/heap-art-export/repro/heap-art-provenance.json
```

The verified environment uses Python 3.10.6, Pillow 12.2.0, numpy 2.2.6 and scipy 1.15.3.
Only existing font/hash/data helpers are imported from `export_rig_atlases.py`; its RGB matte
is not invoked. The Java runtime acquires no Python or imaging dependency.

Validation completed on 2026-09-11:

- Independent export reproduced all four resource files and provenance byte-for-byte.
- Background pixels exactly match the generated RGB source; both input hashes remained unchanged.
- All three frame guards, eight existing apertures and nine protected samples passed.
- `proof-black.png`, `proof-white.png`, `proof-color.png`, `pivots.png` and the background
  preview under `build/heap-art-export` were visually inspected. Pipe gaps remain visible,
  bright metal remains solid, and no checkerboard is baked into the cutouts.
- No project-local Python bytecode cache was left behind.

These proof sheets are asset inspection composites. The separate HeapVisualPreview main
has also exported 62 production-renderer inspection images at native/compact sizes with
actual Heap controller actions and Boss damage state. Seven HeapRendererTest checks pass,
including real loader/alpha/socket contracts. GamePanel integration, runtime performance
and formal Gradle checks remain the main task's separate work; this exporter does not run them.
