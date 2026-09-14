# Bilingual production panel captures

2026-09-11. The final formally compiled `LanguagePreview` exported 60 screenshots:
15 scenes × English/Simplified Chinese × 960×600/600×400. Fourteen representative
PNGs are retained here alongside the complete final drawing transcript.

The preview uses the production GamePanel, ActionMap, simulation, FrameMailbox and
EDT painting. Chapter entry, actor placement, completion and error states are explicit
fixtures. The paired upgrade drafts may contain different random cards; this is not
a controlled same-seed playthrough comparison. Additional tests measure all upgrade
titles, descriptions and effect sections in both languages.

The `displayed-text.txt` transcript records final text from the drawing helper, grouped
by scene and language. Running `scripts/verify-languages.py` against it requires all
60 scenes and checks language consistency with narrow keycap/code-syntax exceptions.
The scan passed. The `[ L ]` keycap was also visually checked after the numeric-template
fix; it remains a keycap in both languages.

These are headless actual panel captures, not native IDE installation, physical
audio, performance measurements or a normal campaign-completion claim.

See [the complete QA and package record](../../2026-09-11-bilingual-interface.md).
