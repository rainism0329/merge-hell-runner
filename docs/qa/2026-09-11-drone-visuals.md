# Friendly drone visual inspection

The shipping `DroneRenderer` draws a 32 px orange mechanical body with a cyan glass eye,
twin metal thrusters and a turret rotating around the simulation's body center. Rank 3
adds shoulder armor and a cyan ring, making its shell about 36 px wide. Rank 2 uses two
standard bodies; rank 3 uses two reinforced bodies. Counts, formation, aiming, recoil,
thrust and shot timing come from `DroneController.Snapshot`, without renderer-owned clocks
or random state. Two body images are rasterized once in memory; drawing performs no file I/O.

The barrel has a stationary muzzle collar at the controller's 18 px muzzle distance. Its
receiver slides backward on a telescoping rail for recoil, while the flash starts at the
snapshot's exact `muzzleX/Y`. This preserves the actual projectile emission point during recoil.

## Reproducible inspection

`src/test/java/com/bigphil/mergehell/render/DroneVisualPreview.java` is a headless main,
not a JUnit test. Run it with Java 17, `-Djava.awt.headless=true`, compiled production/test
classes and `src/main/resources` on the classpath. The optional output argument defaults
to `build/drone-visual/preview`. The current inspection used the real IC 232 SDK and a
separate `build/drone-visual/classes` directory; formal Gradle output was not changed.

The entry exports 99 PNGs: a 960×600 native-size scene, the same scene scaled to the
600×375 drawing area of a 600×400 window, a 4× detail sheet, and 96 sequential motion
frames. The motion fixture drives the real controller with moving player/target inputs,
then draws real `DroneRenderer`, `ActorVisuals`, `IndustrialArt`, `IndustrialDeck` and
`Projectile` output. Its CSV records body/muzzle positions, aim, recoil and thrust.
These are controlled art probes, not full GamePanel captures or playthrough evidence.

Native-size and scaled inspection confirms that the body, two thrusters and friendly
cyan eye remain visible. The rank 3 silhouette and ring are distinct from the standard
body. The 4× sheet checks left/right, upward firing and downward turret angles.

The initial turn fixture exposed a brief overlap when formation sides followed player
facing. The controller now keeps each slot on a fixed world side and rotates only the
turret. Re-running all 96 frames with that frozen controller preserves a 72 px horizontal
gap in this scene (minimum center distance 73.83 px); the previously overlapping frame 50
now shows both complete bodies. The controller's separate movement/viewport tests cover
its minimum spacing rule at screen edges. No renderer offset conceals a simulation mismatch.

## Automated checks

`DroneRendererTest` has four passing Java 17/JUnit Jupiter checks:

- Real controller shots in four directions begin at the same center as the painted flash.
- A paused controller retains its snapshot and renders exactly the same pixels.
- Rendering preserves the caller's Graphics2D transform, clip, stroke, color and composite.
- Rank 3 adds visible armor/ring pixels outside the standard native-size silhouette.

These checks cover the rendering contract. Full-panel integration and the broader combat
test suite are tracked by the main task; this document does not claim those checks from
the isolated renderer run.
