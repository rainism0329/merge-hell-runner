# Chapter 3–5 generated source paintings

Generated with the built-in `image_gen` tool on 2026-09-14. These are original generated
illustrations for the game's requested chapter overhaul, not gameplay screenshots or stock art.
The user selected the worn industrial, volumetric 2D direction in `../../concepts/01-industrial-salvage.png`;
the new third/fourth/fifth worlds deliberately evolve from aerial construction to deep mining
and finally alien infestation. No input image was edited: each of the following was a new
generation described by the adjacent exact prompt.

| Original source | Built-in generation ID | Source format | Exact prompt |
| --- | --- | --- | --- |
| chapter3-background-generated.png | exec-bb301fdb-327f-4d0c-b810-201afdbbd533 | 1774 × 887 RGB | chapter3-background-prompt.txt |
| chapter4-background-generated.png | exec-f25a6640-07b5-4dd4-b624-b76d3b945eb4 | 1774 × 887 RGB | chapter4-background-prompt.txt |
| chapter5-background-generated.png | exec-a88257ac-f05e-4cdd-ac67-b35a2610a88f | 1774 × 887 RGB | chapter5-background-prompt.txt |
| chapter3-actors-generated.png | exec-ccf26709-0fa2-479c-85d2-fa5a9c9b4130 | 1536 × 1024 native RGBA | chapter3-actors-prompt.txt |
| chapter4-actors-generated.png | exec-a675c3a5-83d7-4161-bc3f-b7b6603c02d9 | 1536 × 1024 native RGBA | chapter4-actors-prompt.txt |
| chapter5-actors-generated.png | exec-4bf476ff-779e-4869-8d6b-1df792a67a7b | 1536 × 1024 native RGBA | chapter5-actors-prompt.txt |
| chapter3-terrain-generated.png | exec-ff7d42aa-7449-469c-b86e-81628b30ab8c | 1536 × 1024 native RGBA | chapter3-terrain-prompt.txt |
| chapter4-terrain-generated.png | exec-ec0b4fa2-9844-4a54-8357-0b5e1f40f662 | 1536 × 1024 native RGBA | chapter4-terrain-prompt.txt |
| chapter5-terrain-generated.png | exec-4ee0a3c9-d9f5-4332-9d9a-b41836a90da1 | 1536 × 1024 native RGBA | chapter5-terrain-prompt.txt |

All nine sources were copied unchanged from the tool's generated-images directory into this project.
The tool preview may display hidden RGB values behind the alpha; compositing the actual files
onto black, white and blue backgrounds verifies real transparency. No background-removal retry
was necessary. Source alpha on opaque material is usually 252/255; it is preserved, not promoted.

Source originals never serve as the atlas directly. `scripts/art/export_chapter_art.py` crops
the inspected six separated shapes, trims remote near-transparent noise by an alpha ≥ 8 frame
boundary, retains the native RGBA within the frame, uniformly downsamples only when required,
adds transparent guards and writes runtime atlas rectangles, anchors and sockets.
The script verifies that all originals remain byte-identical after export.

The three additional foreground sheets were made after full-game preview showed that procedural
deck braces and flat props did not match the painted actors. They contain six independently
painted terrain pieces each: deck, platform, support, prop-a, prop-b and trim. Their staggered
layout has overlapping rectangular bounding boxes, so `scripts/art/export_chapter_terrain.py`
uses the six inspected opaque alpha components to assign their native soft edges, then crops
each independent silhouette. It preserves all assigned source RGB/alpha values and all original
files; this is atlas separation, with no opaque background removal or alpha promotion.
See `../../chapter-terrain-provenance.json` for component seeds, bounds and output hashes.

See `../../chapter-premium-art.md` for integration and validation details and
`../../chapter-art-provenance.json` for exact source/export hashes. Prior user authorization
for programmatic atlas cutting is recorded in `../README.md`.
