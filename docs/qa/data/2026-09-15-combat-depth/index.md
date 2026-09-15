# Combat depth evidence · 2026-09-15

The [validation report](../../../design/evolution/combat-depth-validation.md) explains the fixtures, results and limitations.

- `scenes/`: production Swing frames loaded from the packaged JAR, in two languages and at 960×600 / 600×400. Scene composition uses explicit chapter, position and enemy-clear fixtures; these are visual evidence, not clean playthrough claims.
- `characters-preview.png`: actual articulated renderer at inspection scale, showing the four characters and aim/crouch poses.
- `playthrough/`: normal-input route and Boss traces for base and developed entry builds. Each folder includes its own fixture description. `base-route4` and `base-route4-crawl` preserve failed cover-planning attempts; `base-route4-cover` is the passing follow-up with ordinary repositioning input.
- `performance/`: all raw samples and summaries from the 30 headless simulation/publish/EDT cases. This does not measure native display FPS.
- `build-release.log`, `tests.json`: final offline Gradle build, test and package verification evidence.
- `segment-ui.log`, `segment-xml.log`: focused safe-segment continuation and real IntelliJ XML serializer checks, also included in the final test suite.
- `package.json`: package byte size and SHA-256; new classes, art, metadata and both locale bundles were inspected inside the ZIP's instrumented JAR.
- `shipping-smoke-final.log`: final packaged-resource rendering smoke run; an empty log with the successful exit recorded in `tests.json` is normal.

Artwork sources and complete prompts are under `docs/design/evolution/sources/characters` and `docs/design/evolution/sources/routes`. No generated source is referenced only from the Codex cache.
