# Character, navigation and warning polish — 2026-09-15

This pass addresses disconnected-looking character parts, enemies blocked by the new route walls,
and overly explicit ordinary-enemy trajectory warnings. It preserves the five authored chapters,
character selection, difficulty choices, eight-way input, bilingual UI and default mute setting.

## Character construction

- Calibrated each character's neck, shoulder, hip, elbow, palm and gun sockets from the visible
  source features. The torso now anchors at the hip and supplies the shoulder and neck hierarchy.
- Both hands attach to separate gun grips. The rifle sits at chest height and raises beside the
  head for upward aim. `HeroAim` supplies the same muzzle to the Player and renderer; recoil rotates
  around that point. Limb transforms keep uniform scale rather than stretching bearings sideways.
- Shortened the arms, corrected the forward knee bend when crouching, and adjusted Scout's visible
  helmet size separately from its antenna. Boot contacts retain the existing planted-foot solver.
- Replaced only the engineer's head, torso and forearm with targeted built-in imagegen edits. The
  new torso has no baked-in extra sleeves, the head has no duplicate scarf, and the hand grips the
  weapon. All other parts retain their approved source pixels.
- Prompts, original sources, native-alpha manifests and measured crop coordinates are preserved in
  `sources/characters-v2/`. `scripts/art/export_characters.py` deterministically packs the selected
  pieces and joint metadata. No live-game or preview screenshot is generated artwork.

## Enemy navigation

Ground enemies approach the face, climb vertically beside the wall, cross its top, and descend on
the other face. Flyers can take the shorter top or bottom route. Drill enemies move continuously
underground and emerge only when their entire body clears the wall. Full-body clearance, adjacent
props, ground-region boundaries and high-speed swept motion are respected. Navigation cancels stale
attack commitments, and normal attack warning restarts after crossing.

The render snapshot carries wall-climb direction and facing. Climbing faces the contacted wall,
animation phase advances on vertical movement, contact chips stay at the wall, and no floating floor
shadow is drawn under the climber. Ground units still use their existing artwork during climbing;
this pass does not claim newly authored climbing sprite clips for every enemy.

## Warning readability

Ordinary direct, split and lobbed shots use local weapon/organ charge. The piercing Sentinel keeps
two short directional marks, at most 44 pixels from its committed muzzle. Rush, leap and burrow
attacks retain small ground danger areas. Full projected bullet/leap trajectories, floating warning
badges and full-health bars are removed. Damaged enemies retain health bars. Boss danger geometry,
attack timing and damage are unchanged by the warning presentation change.

## Verification and evidence

- `build buildPlugin verifyPlugin --offline`: **745 tests, zero failures/errors/skips**, 117 suites;
  complete build and plugin verification passed. `git diff --check` passed.
- Real Player input tests cover four characters, six weapons, eight airborne directions, standing
  and crouching fire, both sides of every chapter's wall, real low ceilings and 2-pixel thin walls.
  Actual projectile/world/enemy collision confirms the far-side target remains protected.
- Navigation tests and a seven-scenario production simulation cover five chapter props, a flying
  route under a beam and continuous underground travel through joined foundations. Shooting enemies
  recover their attacks after crossing. A 32-enemy navigation-only stress probe was also run; its
  timings are diagnostic, not a claim about total IDE frame time.
- Forty production character poses were visually inspected at 3x and native size, with a separate
  review covering the heads, sleeves, hands, shoulders, knees and gun connections.
- `CharacterMotionPreview` then ran 288 real simulation ticks for each character (1,152 Player
  updates), using the packaged JAR. All four reached eight aim directions, crouch movement,
  airborne downward shooting, running, landing, dash, melee and hurt states. Consecutive frame PNGs,
  an HTML player and the numeric pose/shot trace are retained under `build/polish/motion/`; selected
  sequence sheets and the coverage summary are included in the durable evidence directory.
- The actual instrumented JAR was extracted from the release ZIP. All eight character atlas and
  metadata entries were compared byte-for-byte with production resources, and the three new runtime
  classes were confirmed present. Swing menu/control/world captures use this packaged JAR, including
  Chinese and English at 960×600 and 600×400. These are headless production paints, not a native IDE
  manual playtest.

Durable images and the package manifest are in `docs/qa/data/2026-09-15-character-terrain-polish/`.
Detailed model evidence is in `enemy-navigation-polish-validation.md`; warning evidence is in
`enemy-warning-polish-validation.md`. Reproduction helpers are `CharacterPolishPreview`,
`CharacterMotionPreview`, `DepthScenePreview`, and `EnemyNavigationVisualProbe` under `src/test/java`.

Artifact: `build/distributions/merge-hell-runner-1.0.0.zip`. The SHA256 is recorded with the retained
package verification evidence rather than embedded in the source release metadata.
