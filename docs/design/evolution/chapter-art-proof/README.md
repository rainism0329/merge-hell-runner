# Chapter art export checks

These are asset validation plates, not captured gameplay.

- `chapter{3,4,5}-{black,white,blue}.png`: the six individual parts on contrasting colors.
  The small lower insets use the intended entity boxes and are capped at 58 pixels tall to
  fit the plate; they are silhouette checks, not final Boss scale.
- `chapter{3,4,5}-background-960.png`: full-panorama material previews scaled to 960 × 480.
- `chapter{3,4,5}-terrain-{black,white,blue}.png`: the six painted foreground pieces on
  contrasting colors; all three blue sheets were inspected after component separation.
- `chapter{3,4,5}-renderer-960.png`: Java `ChapterArt` draws the actual runtime resources at
  960 × 600, with all three enemies at their gameplay boxes and a composed Boss at large scale.
  The solid lower shade is a viewing aid. It is not a playable map or HUD. Each image is marked
  `ASSET SCALE PROOF - NOT GAMEPLAY`.

All three blue-background plates and all three renderer plates were visually inspected. No
checkerboard, gradient matte, clipped connected body part or erased pale material was found.
Generation inputs, prompts, crop geometry and output hashes are recorded in
`../chapter-art-provenance.json` and `../sources/chapter-premium/`.
