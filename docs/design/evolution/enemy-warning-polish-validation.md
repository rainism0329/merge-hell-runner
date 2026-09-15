# Enemy warning readability pass

The enemy presentation now distinguishes routine shots from dangerous commitments. Ordinary direct,
split and ballistic volleys charge at an attached weapon or organ. The piercing Sentinel shot retains
two short direction marks, ending 44 pixels from its committed projectile origin. Charges and leaps
retain a narrow marker over their committed ground footprint; buried drillers mark the surface with
fissures. No ordinary enemy draws its complete future projectile or leap trajectory.

Full health bars and floating warning badges were removed from the painted enemy renderers. Damaged
enemies retain health bars. Boss warning geometry, attack timing, targeting commitments and projectile
damage were not changed by this presentation pass. The shared renderer reads immutable poses and does
not depend on wall clock time or random flashing.

## Evidence

- `build/telegraph-polish/before.png`: nine real warning poses before the change.
- `build/telegraph-polish/after.png`: the same poses at the beginning of their existing warning.
- `build/telegraph-polish/after-late.png`: real simulation poses with five warning ticks remaining.
- `EnemyWarningPreview` captures these at equal scale. Its actor labels are review annotations,
  not text added to the game. These are Java2D renders of the existing artwork, not generated mockups.

## Focused verification

Eight focused tests passed: five in `EnemyWarningRendererTest`, plus the existing three
`ChapterActorRendererTest` checks. They cover local charge bounds, retained landing markers, bounded
sniper direction, surface-only burrow warning, absent/dead warning suppression, unchanged volley
timing and vectors after repeated paints, and retained Boss danger geometry and render isolation.
The first-wave regression now checks attached charge and absence of the old floating exclamation badge.
Full integrated tests and plugin packaging are handled by the enclosing polish pass.
