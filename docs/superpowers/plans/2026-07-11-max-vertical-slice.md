# Merge Hell Runner MAX Vertical Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a release-quality Dark mission vertical slice with a fixed-step runtime, Commit Cannon and Force Push, deterministic upgrade drafts, Overclock/SUDO, a redesigned Legacy Code boss, fixed-canvas HUD, safe persistence migration, and bounded performance.

**Architecture:** Keep the current Java/Swing plugin installable after every task while extracting deterministic domain services from `GamePanel`. Pure combat, progression, mission, and boss components feed a single `GameSession`; a lifecycle-owned `GameLoop` advances it at 60Hz and publishes immutable `RenderSnapshot` values for Swing to paint on the EDT.

**Tech Stack:** Java 17, Swing/Java2D, IntelliJ Platform 2023.2 (build 232), Gradle IntelliJ Plugin 1.17.4, JUnit 5.10.

## Global Constraints

- Preserve all pre-existing worktree changes, especially `README.md`, `src/main/resources/META-INF/plugin.xml`, `LevelTheme.java`, and `LevelManagerTest.java`; stage only files named by each task.
- Do not add third-party runtime dependencies.
- Keep the plugin compatible with Java 17 and IntelliJ build 232+.
- Use a 960×600 logical canvas and 60Hz fixed simulation ticks.
- New game logic must accept an injected `RandomGenerator` or deterministic seed; do not call `Math.random()` from new logic.
- One Dark mission should be tunable to 12–18 minutes; tests use shortened definitions rather than sleeping.
- The first playable upgrade must be reachable within the first 90 seconds of normal play.
- Every upgrade draft contains at least one current-weapon upgrade or its evolution module and has three unique choices.
- New hostile attacks require at least 24 ticks (400ms) of visible telegraph before high damage.
- Hiding the tool window stops world ticks; disposing the project releases executors and audio hooks.
- New visual entities use Java2D vector primitives and code labels rather than platform Emoji glyphs.
- Phase-one scope is Dark, Commit Cannon, Force Push, upgrade selection, Overclock, the new HUD, Legacy Code boss, persistence migration, and performance baselines. Audio and the remaining four missions belong to later plans.

## Planned File Map

### New production files

- `combat/WeaponId.java`: stable weapon identity independent of legacy enum names.
- `combat/ProjectileSpec.java`: immutable damage, motion, pierce, knockback, and ricochet data.
- `combat/CombatStats.java`: immutable effective weapon stats after upgrades and Overclock.
- `combat/WeaponDefinition.java`: one weapon's base stats, evolution identity, and tags.
- `combat/WeaponCatalog.java`: canonical definitions for the phase-one weapons.
- `combat/FireRequest.java`: one deterministic request to create projectiles.
- `combat/WeaponFireController.java`: converts effective stats into projectile volleys.
- `progression/UpgradeId.java`, `UpgradeTag.java`, `UpgradeEffect.java`, `UpgradeDefinition.java`, `UpgradeCatalog.java`: upgrade data and composable effects.
- `progression/BuildStats.java`: immutable combat, mobility, shield, drone, and combo modifiers.
- `progression/RunBuild.java`: selected weapon, ranks, module state, and effective stats.
- `progression/BuildProgress.java`: Build XP and pending-level accounting.
- `progression/UpgradeDraftService.java`: deterministic unique three-card drafts and one reroll.
- `progression/OverclockMeter.java`: charge and exact-duration SUDO state.
- `mission/DirectorInput.java`, `DirectorCommand.java`, `MissionSegment.java`, `DarkMissionDefinition.java`, `EncounterDirector.java`: deterministic Dark pacing and threat budgets.
- `boss/BossPhase.java`, `BossAction.java`, `BossSnapshot.java`, `DependencyNode.java`, `LegacyBossController.java`: Legacy Code's dependency-chain fight.
- `engine/InputFrame.java`, `RenderSnapshot.java`, `GameSession.java`: deterministic session boundary.
- `engine/FixedStepAccumulator.java`, `TickScheduler.java`, `ScheduledTickScheduler.java`, `GameLoop.java`, `GameController.java`: lifecycle-safe runtime.
- `engine/BoundedEntityStore.java`, `EntityLimits.java`, `SessionMetrics.java`: explicit entity budgets and measurements.
- `render/GameViewport.java`, `ViewportTransform.java`, `HudRenderer.java`, `UpgradeOverlayRenderer.java`: fixed-canvas mapping and focused UI layers.
- `persistence/MergeHellState.java`, `LegacyScoreSource.java`, `StateMigrator.java`, `MergeHellStateService.java`: versioned app-level save data.

### Existing files modified

- `model/EntityType.java`: classify Flame/Laser pickups correctly.
- `model/Projectile.java`: use `ProjectileSpec` while retaining legacy constructors during migration.
- `model/Player.java`: delegate phase-one firing to `WeaponFireController` and expose render-safe state.
- `model/ObstacleManager.java`: apply knockback and bounded hostile spawning.
- `CollisionSystem.java`: consume projectile pierce/knockback and emit combat events.
- `GameState.java`: add briefing, upgrade selection, and recoverable error states.
- `GamePanel.java`: become a Swing input/view adapter around `GameController`.
- `GameRenderer.java`: compose render-specific classes and draw from `RenderSnapshot`.
- `MergeHellToolWindowFactory.java`: register lifecycle ownership with IntelliJ `Disposer`.
- `model/ScoreStore.java`: delegate to versioned persistence after migration.

---

### Task 1: Establish Phase-One Weapon Definitions and Correct Pickup Classification

**Files:**
- Create: `src/main/java/com/bigphil/mergehell/combat/WeaponId.java`
- Create: `src/main/java/com/bigphil/mergehell/combat/ProjectileSpec.java`
- Create: `src/main/java/com/bigphil/mergehell/combat/CombatStats.java`
- Create: `src/main/java/com/bigphil/mergehell/combat/WeaponDefinition.java`
- Create: `src/main/java/com/bigphil/mergehell/combat/WeaponCatalog.java`
- Modify: `src/main/java/com/bigphil/mergehell/model/EntityType.java`
- Test: `src/test/java/com/bigphil/mergehell/combat/WeaponCatalogTest.java`
- Modify test: `src/test/java/com/bigphil/mergehell/model/EntityTypeTest.java`

**Interfaces:**
- Produces: `WeaponCatalog.definition(WeaponId): WeaponDefinition`
- Produces: `WeaponDefinition.baseStats(): CombatStats`
- Produces: `ProjectileSpec.withVelocity(double, double): ProjectileSpec`

- [ ] **Step 1: Write failing catalog and pickup tests**

```java
@Test
void phaseOneWeaponsHaveDistinctCombatProfiles() {
    WeaponDefinition commit = WeaponCatalog.definition(WeaponId.COMMIT_CANNON);
    WeaponDefinition force = WeaponCatalog.definition(WeaponId.FORCE_PUSH);
    assertEquals(1, commit.baseStats().pellets());
    assertEquals(5, force.baseStats().pellets());
    assertTrue(commit.baseStats().ricochets() > force.baseStats().ricochets());
    assertTrue(force.baseStats().knockback() > commit.baseStats().knockback());
}

@Test
void everyWeaponPickupIsNonHostile() {
    assertTrue(EntityType.PICKUP_FLAME.isPowerup());
    assertTrue(EntityType.PICKUP_LASER.isPowerup());
    assertFalse(EntityType.PICKUP_FLAME.isHostile());
    assertFalse(EntityType.PICKUP_LASER.isHostile());
}
```

- [ ] **Step 2: Run focused tests and verify they fail**

Run: `.\gradlew.bat test --tests '*WeaponCatalogTest' --tests '*EntityTypeTest'`

Expected: FAIL because the combat package does not exist and Flame/Laser are classified as hostile.

- [ ] **Step 3: Add immutable weapon records and the phase-one catalog**

```java
public enum WeaponId {
    COMMIT_CANNON,
    FORCE_PUSH,
    RAPID_CI,
    GARBAGE_COLLECTOR,
    FIREWALL,
    REFACTOR_BEAM
}

public record ProjectileSpec(
        WeaponId weapon, int damage, double velocityX, double velocityY,
        boolean critical, int remainingPierces, double knockback, int remainingRicochets) {
    public ProjectileSpec {
        Objects.requireNonNull(weapon);
        if (damage <= 0) throw new IllegalArgumentException("damage must be positive");
        if (remainingPierces < 0 || remainingRicochets < 0)
            throw new IllegalArgumentException("counts cannot be negative");
    }

    public ProjectileSpec withVelocity(double x, double y) {
        return new ProjectileSpec(weapon, damage, x, y,
                critical, remainingPierces, knockback, remainingRicochets);
    }
}

public record CombatStats(
        int damage, int cooldownFrames, int pellets, double speed,
        double spreadRadians, int pierces, double knockback,
        double criticalChance, int ricochets) {
    public CombatStats {
        if (damage <= 0 || cooldownFrames <= 0 || pellets <= 0 || speed <= 0)
            throw new IllegalArgumentException("combat stats must be positive");
        if (criticalChance < 0 || criticalChance > 1)
            throw new IllegalArgumentException("criticalChance must be in [0, 1]");
    }

    public CombatStats withRicochets(int value) {
        return new CombatStats(damage, cooldownFrames, pellets, speed, spreadRadians,
                pierces, knockback, criticalChance, value);
    }
    public CombatStats withCriticalChance(double value) {
        return new CombatStats(damage, cooldownFrames, pellets, speed, spreadRadians,
                pierces, knockback, value, ricochets);
    }
    public CombatStats withPelletsAndSpread(int pelletValue, double spreadValue) {
        return new CombatStats(damage, cooldownFrames, pelletValue, speed, spreadValue,
                pierces, knockback, criticalChance, ricochets);
    }
    public CombatStats withKnockback(double value) {
        return new CombatStats(damage, cooldownFrames, pellets, speed, spreadRadians,
                pierces, value, criticalChance, ricochets);
    }
}

public record WeaponDefinition(
        WeaponId id, String displayName, String evolutionName,
        CombatStats baseStats, String[] tags) {
    public WeaponDefinition {
        tags = tags.clone();
    }

    @Override public String[] tags() { return tags.clone(); }
}
```

`WeaponCatalog` must register Commit as damage 25, cooldown 18, one pellet, speed 11, spread 0, pierce 0, knockback 2, crit 0.10, ricochet 1; Force Push as damage 16, cooldown 26, five pellets, speed 9, spread 0.42, pierce 0, knockback 14, crit 0.05, ricochet 0. Unknown IDs throw `IllegalArgumentException` until their implementation phase.

```java
public final class WeaponCatalog {
    private static final Map<WeaponId, WeaponDefinition> DEFINITIONS = Map.of(
            WeaponId.COMMIT_CANNON,
            new WeaponDefinition(WeaponId.COMMIT_CANNON, "Commit Cannon", "Cherry Pick",
                    new CombatStats(25, 18, 1, 11, 0, 0, 2, 0.10, 1),
                    new String[]{"precision", "critical", "ricochet"}),
            WeaponId.FORCE_PUSH,
            new WeaponDefinition(WeaponId.FORCE_PUSH, "Force Push", "Force Push --force",
                    new CombatStats(16, 26, 5, 9, 0.42, 0, 14, 0.05, 0),
                    new String[]{"spread", "close-range", "knockback"}));

    public static WeaponDefinition definition(WeaponId id) {
        WeaponDefinition value = DEFINITIONS.get(id);
        if (value == null) throw new IllegalArgumentException("Weapon not implemented: " + id);
        return value;
    }

    private WeaponCatalog() {}
}
```

- [ ] **Step 4: Include Flame and Laser in `EntityType.isPowerup()`**

```java
public boolean isPowerup() {
    return switch (this) {
        case PICKUP_SPREAD, PICKUP_RAPID, PICKUP_HEAVY,
             PICKUP_FLAME, PICKUP_LASER, POWERUP_SHIELD, HEALTH -> true;
        default -> false;
    };
}
```

- [ ] **Step 5: Run the focused tests and the full suite**

Run: `.\gradlew.bat test --tests '*WeaponCatalogTest' --tests '*EntityTypeTest'`

Expected: PASS.

Run: `.\gradlew.bat test`

Expected: PASS with no existing test regression.

- [ ] **Step 6: Commit only Task 1 files**

```bash
git add src/main/java/com/bigphil/mergehell/combat src/main/java/com/bigphil/mergehell/model/EntityType.java src/test/java/com/bigphil/mergehell/combat/WeaponCatalogTest.java src/test/java/com/bigphil/mergehell/model/EntityTypeTest.java
git commit -m "feat: define distinct phase-one weapons"
```

### Task 2: Build Deterministic Upgrade Drafts and Run Build State

**Files:**
- Create: `src/main/java/com/bigphil/mergehell/progression/UpgradeTag.java`
- Create: `src/main/java/com/bigphil/mergehell/progression/UpgradeId.java`
- Create: `src/main/java/com/bigphil/mergehell/progression/UpgradeEffect.java`
- Create: `src/main/java/com/bigphil/mergehell/progression/UpgradeDefinition.java`
- Create: `src/main/java/com/bigphil/mergehell/progression/UpgradeCatalog.java`
- Create: `src/main/java/com/bigphil/mergehell/progression/BuildStats.java`
- Create: `src/main/java/com/bigphil/mergehell/progression/RunBuild.java`
- Create: `src/main/java/com/bigphil/mergehell/progression/BuildProgress.java`
- Create: `src/main/java/com/bigphil/mergehell/progression/UpgradeDraftService.java`
- Test: `src/test/java/com/bigphil/mergehell/progression/RunBuildTest.java`
- Test: `src/test/java/com/bigphil/mergehell/progression/UpgradeDraftServiceTest.java`
- Test: `src/test/java/com/bigphil/mergehell/progression/BuildProgressTest.java`

**Interfaces:**
- Consumes: `WeaponCatalog.definition(WeaponId)` and `CombatStats`
- Produces: `RunBuild.effectiveStats(): CombatStats`
- Produces: `UpgradeDraftService.draft(RunBuild): List<UpgradeDefinition>`
- Produces: `UpgradeDraftService.reroll(RunBuild): List<UpgradeDefinition>`
- Produces: `BuildProgress.addXp(int): int` returning the number of newly pending upgrade choices

- [ ] **Step 1: Write failing tests for effects, XP, uniqueness, relevance, and reroll**

```java
@Test
void commitRicochetChangesEffectiveStats() {
    RunBuild build = new RunBuild(WeaponId.COMMIT_CANNON);
    build.apply(UpgradeCatalog.definition(UpgradeId.COMMIT_RICOCHET));
    assertEquals(2, build.effectiveStats().ricochets());
}

@Test
void draftIsUniqueAndContainsCurrentWeaponChoice() {
    UpgradeDraftService service = new UpgradeDraftService(new Random(7));
    RunBuild build = new RunBuild(WeaponId.FORCE_PUSH);
    List<UpgradeDefinition> cards = service.draft(build);
    assertEquals(3, cards.size());
    assertEquals(3, cards.stream().map(UpgradeDefinition::id).distinct().count());
    assertTrue(cards.stream().anyMatch(card -> card.isWeaponRelevant(WeaponId.FORCE_PUSH)));
}

@Test
void rerollCanOnlyBeUsedOnce() {
    UpgradeDraftService service = new UpgradeDraftService(new Random(9));
    RunBuild build = new RunBuild(WeaponId.COMMIT_CANNON);
    service.draft(build);
    service.reroll(build);
    assertThrows(IllegalStateException.class, () -> service.reroll(build));
}

@Test
void xpCarriesAcrossMultipleThresholds() {
    BuildProgress progress = new BuildProgress();
    assertEquals(2, progress.addXp(275));
    assertEquals(2, progress.pendingChoices());
    assertTrue(progress.currentXp() < progress.nextThreshold());
}

@Test
void evolutionRequiresWeaponLevelFiveAndMatchingCore() {
    RunBuild build = new RunBuild(WeaponId.COMMIT_CANNON);
    assertFalse(build.tryEvolve());
    for (int i = 0; i < 4; i++) {
        build.apply(UpgradeCatalog.definition(i % 2 == 0
                ? UpgradeId.COMMIT_RICOCHET : UpgradeId.COMMIT_CRITICAL));
    }
    assertEquals(5, build.weaponLevel());
    assertFalse(build.tryEvolve());
    build.apply(UpgradeCatalog.definition(UpgradeId.DEPENDENCY_CORE_COMMIT));
    assertTrue(build.tryEvolve());
    assertTrue(build.evolved());
}

@Test
void globalUpgradesChangeEveryNonWeaponModifier() {
    RunBuild build = new RunBuild(WeaponId.COMMIT_CANNON);
    build.apply(UpgradeCatalog.definition(UpgradeId.DASH_CACHE));
    build.apply(UpgradeCatalog.definition(UpgradeId.SHIELD_REBOOT));
    build.apply(UpgradeCatalog.definition(UpgradeId.DRONE_COPILOT));
    build.apply(UpgradeCatalog.definition(UpgradeId.COMBO_WINDOW));
    assertEquals(0.85, build.buildStats().dashCooldownMultiplier(), 0.0001);
    assertEquals(1, build.buildStats().shieldReboots());
    assertEquals(1, build.buildStats().droneLevel());
    assertEquals(30, build.buildStats().comboGraceTicks());
}
```

- [ ] **Step 2: Run the progression tests and verify they fail**

Run: `.\gradlew.bat test --tests 'com.bigphil.mergehell.progression.*'`

Expected: FAIL because the progression classes do not exist.

- [ ] **Step 3: Implement immutable upgrade definitions and effects**

```java
public enum UpgradeId {
    COMMIT_RICOCHET,
    COMMIT_CRITICAL,
    FORCE_EXTRA_PELLETS,
    FORCE_KNOCKBACK,
    DASH_CACHE,
    SHIELD_REBOOT,
    DRONE_COPILOT,
    COMBO_WINDOW,
    DEPENDENCY_CORE_COMMIT,
    DEPENDENCY_CORE_FORCE
}

public enum UpgradeTag {
    WEAPON,
    MOVEMENT,
    DRONE,
    CRITICAL,
    SHIELD,
    COMBO,
    EVOLUTION_CORE
}

@FunctionalInterface
public interface UpgradeEffect {
    BuildStats apply(BuildStats current);
}

public record BuildStats(
        CombatStats combat, double dashCooldownMultiplier,
        int shieldReboots, int droneLevel, int comboGraceTicks) {
    public BuildStats {
        Objects.requireNonNull(combat);
        if (dashCooldownMultiplier <= 0 || shieldReboots < 0 || droneLevel < 0 || comboGraceTicks < 0)
            throw new IllegalArgumentException("build modifiers are invalid");
    }

    public static BuildStats base(CombatStats combat) {
        return new BuildStats(combat, 1.0, 0, 0, 0);
    }

    public BuildStats withCombat(CombatStats value) {
        return new BuildStats(value, dashCooldownMultiplier, shieldReboots, droneLevel, comboGraceTicks);
    }
    public BuildStats withDashCooldown(double value) {
        return new BuildStats(combat, value, shieldReboots, droneLevel, comboGraceTicks);
    }
    public BuildStats withShieldReboots(int value) {
        return new BuildStats(combat, dashCooldownMultiplier, value, droneLevel, comboGraceTicks);
    }
    public BuildStats withDroneLevel(int value) {
        return new BuildStats(combat, dashCooldownMultiplier, shieldReboots, value, comboGraceTicks);
    }
    public BuildStats withComboGrace(int value) {
        return new BuildStats(combat, dashCooldownMultiplier, shieldReboots, droneLevel, value);
    }
}

public record UpgradeDefinition(
        UpgradeId id, String title, String description,
        UpgradeTag tag, Set<WeaponId> supportedWeapons,
        int maxRank, UpgradeEffect effect) {
    public boolean isWeaponRelevant(WeaponId weapon) {
        return (tag == UpgradeTag.WEAPON || tag == UpgradeTag.EVOLUTION_CORE)
                && supportedWeapons.contains(weapon);
    }
}
```

`UpgradeCatalog` registers all ten IDs in an immutable map. Weapon upgrades have max rank 3 except Force pellets (2); movement, shield, drone, and combo have max rank 3; evolution cores have max rank 1. Effects are exact: Commit ricochet `+1 ricochet`; Commit critical `+0.10` capped at `0.75`; Force pellets `+2 pellets` with spread capped at `0.62`; Force knockback `+4`; Dash Cache multiplies cooldown by `0.85` with floor `0.55`; Shield Reboot adds one reboot; Drone Copilot adds one drone level; Combo Window adds 30 grace ticks; core modules leave stats unchanged and set the matching core flag in `RunBuild.apply`.

Implement the immutable catalog with these exact entries:

```java
public final class UpgradeCatalog {
    private static final Set<WeaponId> COMMIT = Set.of(WeaponId.COMMIT_CANNON);
    private static final Set<WeaponId> FORCE = Set.of(WeaponId.FORCE_PUSH);
    private static final Set<WeaponId> GLOBAL = Set.of();

    private static UpgradeEffect combat(UnaryOperator<CombatStats> effect) {
        return stats -> stats.withCombat(effect.apply(stats.combat()));
    }

    private static final Map<UpgradeId, UpgradeDefinition> DEFINITIONS = Map.ofEntries(
            Map.entry(UpgradeId.COMMIT_RICOCHET,
                    new UpgradeDefinition(UpgradeId.COMMIT_RICOCHET, "Cherry Pick",
                            "+1 projectile ricochet", UpgradeTag.WEAPON, COMMIT, 3,
                            combat(s -> s.withRicochets(s.ricochets() + 1)))),
            Map.entry(UpgradeId.COMMIT_CRITICAL,
                    new UpgradeDefinition(UpgradeId.COMMIT_CRITICAL, "Signed Commit",
                            "+10% critical chance", UpgradeTag.WEAPON, COMMIT, 3,
                            combat(s -> s.withCriticalChance(Math.min(0.75, s.criticalChance() + 0.10))))),
            Map.entry(UpgradeId.FORCE_EXTRA_PELLETS,
                    new UpgradeDefinition(UpgradeId.FORCE_EXTRA_PELLETS, "More Reviewers",
                            "+2 pellets", UpgradeTag.WEAPON, FORCE, 2,
                            combat(s -> s.withPelletsAndSpread(s.pellets() + 2,
                                    Math.min(0.62, s.spreadRadians() + 0.08))))),
            Map.entry(UpgradeId.FORCE_KNOCKBACK,
                    new UpgradeDefinition(UpgradeId.FORCE_KNOCKBACK, "Protected Branch",
                            "+4 knockback", UpgradeTag.WEAPON, FORCE, 3,
                            combat(s -> s.withKnockback(s.knockback() + 4)))),
            Map.entry(UpgradeId.DASH_CACHE,
                    new UpgradeDefinition(UpgradeId.DASH_CACHE, "Dash Cache",
                            "-15% dash cooldown", UpgradeTag.MOVEMENT, GLOBAL, 3,
                            s -> s.withDashCooldown(Math.max(0.55, s.dashCooldownMultiplier() * 0.85)))),
            Map.entry(UpgradeId.SHIELD_REBOOT,
                    new UpgradeDefinition(UpgradeId.SHIELD_REBOOT, "Hot Standby",
                            "+1 shield reboot", UpgradeTag.SHIELD, GLOBAL, 3,
                            s -> s.withShieldReboots(s.shieldReboots() + 1))),
            Map.entry(UpgradeId.DRONE_COPILOT,
                    new UpgradeDefinition(UpgradeId.DRONE_COPILOT, "AI Pair Programmer",
                            "+1 drone level", UpgradeTag.DRONE, GLOBAL, 3,
                            s -> s.withDroneLevel(s.droneLevel() + 1))),
            Map.entry(UpgradeId.COMBO_WINDOW,
                    new UpgradeDefinition(UpgradeId.COMBO_WINDOW, "Long Transaction",
                            "+30 combo grace ticks", UpgradeTag.COMBO, GLOBAL, 3,
                            s -> s.withComboGrace(s.comboGraceTicks() + 30))),
            Map.entry(UpgradeId.DEPENDENCY_CORE_COMMIT,
                    new UpgradeDefinition(UpgradeId.DEPENDENCY_CORE_COMMIT, "Cherry-Pick Core",
                            "Unlock Commit evolution", UpgradeTag.EVOLUTION_CORE, COMMIT, 1, s -> s)),
            Map.entry(UpgradeId.DEPENDENCY_CORE_FORCE,
                    new UpgradeDefinition(UpgradeId.DEPENDENCY_CORE_FORCE, "Force Core",
                            "Unlock Force Push evolution", UpgradeTag.EVOLUTION_CORE, FORCE, 1, s -> s)));

    public static UpgradeDefinition definition(UpgradeId id) {
        UpgradeDefinition value = DEFINITIONS.get(id);
        if (value == null) throw new IllegalArgumentException("Unknown upgrade: " + id);
        return value;
    }

    public static Collection<UpgradeDefinition> all() { return DEFINITIONS.values(); }
    private UpgradeCatalog() {}
}
```

- [ ] **Step 4: Implement `RunBuild`, `BuildProgress`, and deterministic drafting**

```java
public final class RunBuild {
    private final WeaponId weapon;
    private final EnumMap<UpgradeId, Integer> ranks = new EnumMap<>(UpgradeId.class);
    private BuildStats buildStats;
    private int weaponLevel = 1;
    private boolean evolutionCoreInstalled;
    private boolean evolved;

    public RunBuild(WeaponId weapon) {
        this.weapon = Objects.requireNonNull(weapon);
        this.buildStats = BuildStats.base(WeaponCatalog.definition(weapon).baseStats());
    }

    public void apply(UpgradeDefinition upgrade) {
        int rank = ranks.getOrDefault(upgrade.id(), 0);
        if (rank >= upgrade.maxRank()) throw new IllegalStateException("upgrade is max rank");
        buildStats = upgrade.effect().apply(buildStats);
        ranks.put(upgrade.id(), rank + 1);
        if (upgrade.tag() == UpgradeTag.WEAPON) weaponLevel = Math.min(5, weaponLevel + 1);
        if (upgrade.tag() == UpgradeTag.EVOLUTION_CORE) evolutionCoreInstalled = true;
    }

    public boolean tryEvolve() {
        if (evolved || weaponLevel < 5 || !evolutionCoreInstalled) return false;
        CombatStats current = buildStats.combat();
        CombatStats evolvedStats = weapon == WeaponId.COMMIT_CANNON
                ? new CombatStats(current.damage(), current.cooldownFrames(), current.pellets(),
                    current.speed(), current.spreadRadians(), current.pierces(), current.knockback(),
                    Math.min(1, current.criticalChance() + 0.15), current.ricochets() + 2)
                : new CombatStats(current.damage(), current.cooldownFrames(), current.pellets() + 4,
                    current.speed(), current.spreadRadians(), current.pierces() + 1,
                    current.knockback() + 12, current.criticalChance(), current.ricochets());
        buildStats = buildStats.withCombat(evolvedStats);
        evolved = true;
        return true;
    }

    public WeaponId weapon() { return weapon; }
    public CombatStats effectiveStats() { return buildStats.combat(); }
    public BuildStats buildStats() { return buildStats; }
    public int rank(UpgradeId id) { return ranks.getOrDefault(id, 0); }
    public int weaponLevel() { return weaponLevel; }
    public boolean evolved() { return evolved; }
}
```

`UpgradeDraftService` must filter max-rank cards, shuffle with its injected `RandomGenerator`, reserve slot zero for a current-weapon/core card, fill two unique remaining slots from the global pool, remember the last draft, and reject a second reroll. `BuildProgress` uses thresholds `100 + (level - 1) * 50`, loops while XP crosses thresholds, and never drops overflow XP.

- [ ] **Step 5: Run focused and full tests**

Run: `.\gradlew.bat test --tests 'com.bigphil.mergehell.progression.*'`

Expected: PASS.

Run: `.\gradlew.bat test`

Expected: PASS.

- [ ] **Step 6: Commit Task 2**

```bash
git add src/main/java/com/bigphil/mergehell/progression src/test/java/com/bigphil/mergehell/progression
git commit -m "feat: add deterministic run upgrades"
```

### Task 3: Make Commit Cannon and Force Push Mechanically Distinct

**Files:**
- Create: `src/main/java/com/bigphil/mergehell/combat/FireRequest.java`
- Create: `src/main/java/com/bigphil/mergehell/combat/WeaponFireController.java`
- Create: `src/main/java/com/bigphil/mergehell/combat/CombatEvent.java`
- Create: `src/main/java/com/bigphil/mergehell/combat/CombatEventSink.java`
- Modify: `src/main/java/com/bigphil/mergehell/model/Projectile.java`
- Modify: `src/main/java/com/bigphil/mergehell/model/Player.java`
- Modify: `src/main/java/com/bigphil/mergehell/model/ObstacleManager.java`
- Modify: `src/main/java/com/bigphil/mergehell/CollisionSystem.java`
- Test: `src/test/java/com/bigphil/mergehell/combat/WeaponFireControllerTest.java`
- Modify test: `src/test/java/com/bigphil/mergehell/CollisionSystemTest.java`

**Interfaces:**
- Consumes: `RunBuild.effectiveStats()`
- Produces: `WeaponFireController.fire(FireRequest): List<Projectile>`
- Produces: `CombatEventSink.accept(CombatEvent): void`

- [ ] **Step 1: Write failing volley and collision tests**

```java
@Test
void forcePushProducesSymmetricFivePelletSpread() {
    RunBuild build = new RunBuild(WeaponId.FORCE_PUSH);
    FireRequest request = new FireRequest(WeaponId.FORCE_PUSH,
            100, 200, 1, build.effectiveStats(), false, new Random(1));
    List<Projectile> volley = new WeaponFireController().fire(request);
    assertEquals(5, volley.size());
    assertEquals(-volley.get(0).getVy(), volley.get(4).getVy(), 0.0001);
    assertEquals(-volley.get(1).getVy(), volley.get(3).getVy(), 0.0001);
}

@Test
void forcePushAppliesKnockbackWithoutDeletingHealthyEnemy() {
    ObstacleManager.Enemy enemy = new ObstacleManager.Enemy(120, 200, EntityType.TECHDEBT);
    double before = enemy.getX();
    enemy.takeHit(16, 14, 1);
    assertTrue(enemy.getX() > before);
    assertFalse(enemy.isDead());
}

@Test
void commitRicochetConsumesOneRicochetAndSelectsNearestEnemy() {
    Projectile projectile = new Projectile(100, 200,
            new ProjectileSpec(WeaponId.COMMIT_CANNON, 25, 10, 0,
                    false, 0, 0, 1));
    ObstacleManager.Enemy near = new ObstacleManager.Enemy(160, 200, EntityType.BUG);
    ObstacleManager.Enemy far = new ObstacleManager.Enemy(260, 200, EntityType.BUG);
    projectile.ricochetToward(List.of(far, near));
    assertEquals(0, projectile.getRemainingRicochets());
    assertTrue(projectile.getVx() > 0);
}
```

- [ ] **Step 2: Run focused tests and verify behavior is missing**

Run: `.\gradlew.bat test --tests '*WeaponFireControllerTest' --tests '*CollisionSystemTest'`

Expected: FAIL because volleys, knockback, and ricochet APIs do not exist.

- [ ] **Step 3: Implement deterministic volley creation**

```java
public record FireRequest(
        WeaponId weapon, double originX, double originY, int facing,
        CombatStats stats, boolean overclocked,
        RandomGenerator random) {
    public FireRequest {
        if (facing != -1 && facing != 1) throw new IllegalArgumentException("facing must be -1 or 1");
    }
}

public final class WeaponFireController {
    public List<Projectile> fire(FireRequest request) {
        CombatStats stats = request.stats();
        int baseDamage = request.overclocked()
                ? (int) Math.ceil(stats.damage() * 1.35) : stats.damage();
        int pierces = stats.pierces() + (request.overclocked() ? 2 : 0);
        List<Projectile> result = new ArrayList<>(stats.pellets());
        for (int i = 0; i < stats.pellets(); i++) {
            double offset = stats.pellets() == 1 ? 0
                    : (i - (stats.pellets() - 1) / 2.0) * stats.spreadRadians() / (stats.pellets() - 1);
            double vx = Math.cos(offset) * stats.speed() * request.facing();
            double vy = Math.sin(offset) * stats.speed();
            boolean critical = request.random().nextDouble() < stats.criticalChance();
            int damage = critical ? baseDamage * 2 : baseDamage;
            ProjectileSpec spec = new ProjectileSpec(
                    request.weapon(), damage, vx, vy, critical, pierces,
                    stats.knockback(), stats.ricochets());
            result.add(new Projectile(request.originX(), request.originY(), spec));
        }
        return result;
    }
}

public interface CombatEvent {
    record EnemyKilled(EntityType type, int points, double x, double y) implements CombatEvent {}
}

@FunctionalInterface
public interface CombatEventSink {
    void accept(CombatEvent event);
}
```

- [ ] **Step 4: Migrate projectile hits while preserving legacy constructors**

Add a `ProjectileSpec spec`, hit-ID set, and remaining counts to `Projectile`. A normal hit marks the projectile dead when no pierces remain; otherwise it decrements pierce. Ricochet chooses the nearest living hostile not already hit, rewrites velocity toward its center at the original speed, decrements ricochet, and clears the dead flag. Retain the existing `ProjectileType` constructor as an adapter used by enemy bullets and unchanged tests.

Add this exact enemy method:

```java
public void takeHit(int damage, double knockback, int hitDirection) {
    takeDamage(damage);
    if (!dead && knockback > 0) {
        x += Math.copySign(Math.min(knockback, 24), hitDirection);
    }
}
```

Update `CollisionSystem` to call `takeHit`, then pierce or ricochet instead of always calling `setDead(true)`. Emit `CombatEvent.EnemyKilled(type, points, x, y)` through an injected sink; keep the current `Context` scoring fields until Task 6 switches ownership.

- [ ] **Step 5: Delegate player firing to `WeaponFireController`**

Add `RunBuild runBuild` and `WeaponFireController fireController` to `Player`, initialized to Commit Cannon. Replace the weapon-specific projectile loop with a `FireRequest`. Keep legacy pickup mapping by translating `WeaponType.COMMIT -> COMMIT_CANNON` and `WeaponType.SPREAD -> FORCE_PUSH`; other legacy weapons continue on the old path until phase two.

- [ ] **Step 6: Run all combat and player tests**

Run: `.\gradlew.bat test --tests '*WeaponFireControllerTest' --tests '*CollisionSystemTest' --tests '*PlayerTest'`

Expected: PASS.

Run: `.\gradlew.bat test`

Expected: PASS.

- [ ] **Step 7: Commit Task 3**

```bash
git add src/main/java/com/bigphil/mergehell/combat src/main/java/com/bigphil/mergehell/model/Projectile.java src/main/java/com/bigphil/mergehell/model/Player.java src/main/java/com/bigphil/mergehell/model/ObstacleManager.java src/main/java/com/bigphil/mergehell/CollisionSystem.java src/test/java/com/bigphil/mergehell/combat/WeaponFireControllerTest.java src/test/java/com/bigphil/mergehell/CollisionSystemTest.java
git commit -m "feat: differentiate commit and force push combat"
```

### Task 4: Add Build XP, Upgrade Selection, and Overclock/SUDO

**Files:**
- Create: `src/main/java/com/bigphil/mergehell/progression/OverclockMeter.java`
- Create: `src/main/java/com/bigphil/mergehell/engine/InputFrame.java`
- Create: `src/main/java/com/bigphil/mergehell/engine/GameSession.java`
- Modify: `src/main/java/com/bigphil/mergehell/GameState.java`
- Modify: `src/main/java/com/bigphil/mergehell/GamePanel.java`
- Modify: `src/main/java/com/bigphil/mergehell/CollisionSystem.java`
- Test: `src/test/java/com/bigphil/mergehell/progression/OverclockMeterTest.java`
- Test: `src/test/java/com/bigphil/mergehell/engine/GameSessionTest.java`

**Interfaces:**
- Consumes: `CombatEventSink`, `BuildProgress`, `UpgradeDraftService`, and `RunBuild`
- Produces: `GameSession.tick(InputFrame): void`
- Produces: `GameSession.chooseUpgrade(int): void`
- Produces: `GameSession.rerollUpgrades(): void`
- Produces: `GameSession.isOverclocked(): boolean`

- [ ] **Step 1: Write failing state and meter tests**

```java
@Test
void overclockActivatesAtCapacityForExactlyEightSeconds() {
    OverclockMeter meter = new OverclockMeter(100, 480);
    meter.addCharge(100);
    assertTrue(meter.isActive());
    for (int i = 0; i < 479; i++) meter.tick();
    assertTrue(meter.isActive());
    meter.tick();
    assertFalse(meter.isActive());
    assertEquals(0, meter.charge());
}

@Test
void pendingUpgradePausesWorldUntilChoice() {
    GameSession session = new GameSession(4L);
    session.awardBuildXp(100);
    assertEquals(GameState.UPGRADE_SELECTION, session.state());
    long before = session.worldTick();
    session.tick(InputFrame.NONE);
    assertEquals(before, session.worldTick());
    session.chooseUpgrade(0);
    assertEquals(GameState.RUNNING, session.state());
}
```

- [ ] **Step 2: Run focused tests and verify failure**

Run: `.\gradlew.bat test --tests '*OverclockMeterTest' --tests '*GameSessionTest'`

Expected: FAIL because the session and meter do not exist.

- [ ] **Step 3: Implement exact Overclock state**

```java
public final class OverclockMeter {
    private final int capacity;
    private final int durationTicks;
    private int charge;
    private int activeTicks;

    public OverclockMeter(int capacity, int durationTicks) {
        if (capacity <= 0 || durationTicks <= 0) throw new IllegalArgumentException();
        this.capacity = capacity;
        this.durationTicks = durationTicks;
    }

    public void addCharge(int amount) {
        if (activeTicks > 0 || amount <= 0) return;
        charge = Math.min(capacity, charge + amount);
        if (charge == capacity) activeTicks = durationTicks;
    }

    public void tick() {
        if (activeTicks > 0 && --activeTicks == 0) charge = 0;
    }

    public boolean isActive() { return activeTicks > 0; }
    public int charge() { return charge; }
    public double ratio() { return isActive() ? 1.0 : charge / (double) capacity; }
    public int activeTicks() { return activeTicks; }
}
```

- [ ] **Step 4: Add upgrade states and the first `GameSession` boundary**

Add `BRIEFING`, `UPGRADE_SELECTION`, and `ERROR` to `GameState`. `GameSession` owns `RunBuild`, `BuildProgress`, `UpgradeDraftService`, `OverclockMeter`, selected cards, state, and world tick. `awardBuildXp` opens a draft when a choice becomes pending. `tick` advances `worldTick` and Overclock only in active gameplay states. `chooseUpgrade` validates index `0..2`, applies the card, consumes one pending choice, and either opens the next draft or returns to `RUNNING`.

Use this input record:

```java
public record InputFrame(
        boolean left, boolean right, boolean jumpPressed,
        boolean shootHeld, boolean dashPressed, boolean meleePressed) {
    public static final InputFrame NONE = new InputFrame(false, false, false, false, false, false);
}
```

- [ ] **Step 5: Route kill events into XP and Overclock**

`GameSession.accept(CombatEvent)` awards `20 + pointValue / 20` Build XP and `8 + min(12, pointValue / 50)` Overclock charge for hostile kills. `CollisionSystem` must emit exactly one event per enemy death, including melee deaths. Remove direct SUDO random activation from the Dark path; keep legacy pickup behavior for non-Dark missions until phase two.

- [ ] **Step 6: Bind `1/2/3` and `R` in `GamePanel`**

Map number presses to `session.chooseUpgrade(0..2)` only in `UPGRADE_SELECTION`. Map `R` to `session.rerollUpgrades()`. Suppress movement/shooting while the selection is open. Existing Space/Enter start behavior remains unchanged.

- [ ] **Step 7: Run focused and full tests**

Run: `.\gradlew.bat test --tests '*OverclockMeterTest' --tests '*GameSessionTest' --tests '*CollisionSystemTest'`

Expected: PASS.

Run: `.\gradlew.bat test`

Expected: PASS.

- [ ] **Step 8: Commit Task 4**

```bash
git add src/main/java/com/bigphil/mergehell/progression/OverclockMeter.java src/main/java/com/bigphil/mergehell/engine src/main/java/com/bigphil/mergehell/GameState.java src/main/java/com/bigphil/mergehell/GamePanel.java src/main/java/com/bigphil/mergehell/CollisionSystem.java src/test/java/com/bigphil/mergehell/progression/OverclockMeterTest.java src/test/java/com/bigphil/mergehell/engine/GameSessionTest.java
git commit -m "feat: add upgrade pauses and overclock"
```

### Task 5: Replace Dark's Random Spawns with a Threat-Budget Director

**Files:**
- Create: `src/main/java/com/bigphil/mergehell/mission/DirectorInput.java`
- Create: `src/main/java/com/bigphil/mergehell/mission/DirectorCommand.java`
- Create: `src/main/java/com/bigphil/mergehell/mission/MissionSegment.java`
- Create: `src/main/java/com/bigphil/mergehell/mission/DarkMissionDefinition.java`
- Create: `src/main/java/com/bigphil/mergehell/mission/EncounterDirector.java`
- Modify: `src/main/java/com/bigphil/mergehell/engine/GameSession.java`
- Modify: `src/main/java/com/bigphil/mergehell/LevelManager.java`
- Modify: `src/main/java/com/bigphil/mergehell/GamePanel.java`
- Modify: `src/main/java/com/bigphil/mergehell/model/ObstacleManager.java`
- Test: `src/test/java/com/bigphil/mergehell/mission/EncounterDirectorTest.java`
- Test: `src/test/java/com/bigphil/mergehell/model/ObstacleManagerTest.java`
- Modify test: `src/test/java/com/bigphil/mergehell/engine/GameSessionTest.java`

**Interfaces:**
- Produces: `DarkMissionDefinition.standard(): DarkMissionDefinition`
- Produces: `EncounterDirector.tick(DirectorInput): List<DirectorCommand>`
- Produces: `EncounterDirector.enterSegment(MissionSegment): void` for isolated segment tests
- Consumes: `DirectorCommand.Spawn`, `.BeginArena`, `.BeginRecovery`, `.SpawnBoss`

- [ ] **Step 1: Write deterministic pacing tests**

```java
@Test
void recoverySegmentsNeverSpawnHostiles() {
    EncounterDirector director = new EncounterDirector(DarkMissionDefinition.forTest(), new Random(11));
    director.enterSegment(MissionSegment.recovery(120));
    for (int i = 0; i < 120; i++) {
        assertTrue(director.tick(new DirectorInput(i, 0, 0, 0)).stream()
                .noneMatch(DirectorCommand.Spawn.class::isInstance));
    }
}

@Test
void spawnCostsNeverExceedAvailableThreat() {
    EncounterDirector director = new EncounterDirector(DarkMissionDefinition.forTest(), new Random(12));
    for (int tick = 0; tick < 600; tick++) {
        List<DirectorCommand> commands = director.tick(new DirectorInput(tick, 1200, 0.35, 3));
        int spent = commands.stream().filter(DirectorCommand.Spawn.class::isInstance)
                .map(DirectorCommand.Spawn.class::cast).mapToInt(DirectorCommand.Spawn::cost).sum();
        assertTrue(spent <= director.lastAvailableBudget());
    }
}

@Test
void bossOnlySpawnsAfterFinalRecovery() {
    DarkMissionDefinition mission = DarkMissionDefinition.forTest();
    EncounterDirector director = new EncounterDirector(mission, new Random(13));
    int bossCommands = 0;
    for (int tick = 0; tick <= mission.totalTicks(); tick++) {
        List<DirectorCommand> commands = director.tick(new DirectorInput(tick, tick * 5.0, 0.4, 0));
        if (tick < mission.bossGateStartTick()) {
            assertTrue(commands.stream().noneMatch(DirectorCommand.SpawnBoss.class::isInstance));
        }
        bossCommands += (int) commands.stream()
                .filter(DirectorCommand.SpawnBoss.class::isInstance).count();
    }
    assertEquals(1, bossCommands);
}

@Test
void rangedEnemyTelegraphsBeforeFiring() {
    ObstacleManager.Enemy enemy = new ObstacleManager.Enemy(300, 200, EntityType.CONFLICT);
    enemy.setShootTimerForTest(1);
    assertNull(enemy.maybeShoot(200));
    assertTrue(enemy.getTelegraphTicks() >= 24);
    for (int i = 0; i < 23; i++) assertNull(enemy.maybeShoot(200));
    assertNotNull(enemy.maybeShoot(200));
}

@Test
void losingLifeReturnsToLatestRecoveryCheckpoint() {
    GameSession session = new GameSession(14L, DarkMissionDefinition.forTest());
    session.recordRecoveryCheckpoint(2_400, 420);
    session.applyFatalDamageForTest();
    assertEquals(2_400, session.playerX(), 0.001);
    assertEquals(420, session.playerY(), 0.001);
    assertTrue(session.playerInvincibilityTicks() >= 90);
    assertEquals(0, session.activeHostiles());
    assertEquals(0, session.enemyProjectileCount());
}
```

- [ ] **Step 2: Run the director tests and verify failure**

Run: `.\gradlew.bat test --tests '*EncounterDirectorTest'`

Expected: FAIL because mission/director types do not exist.

- [ ] **Step 3: Implement typed segments and commands**

```java
public record MissionSegment(Kind kind, int durationTicks, int threatPerSecond, String objective) {
    public enum Kind { COMBAT, ARENA, RECOVERY, BOSS_GATE }
    public static MissionSegment recovery(int ticks) {
        return new MissionSegment(Kind.RECOVERY, ticks, 0, "RECOVER");
    }
}

public record DirectorInput(
        long worldTick, double playerX, double pressure, int activeHostiles) {
    public DirectorInput {
        pressure = Math.max(0, Math.min(1, pressure));
        if (activeHostiles < 0) throw new IllegalArgumentException("activeHostiles cannot be negative");
    }
}

public sealed interface DirectorCommand {
    record Spawn(EntityType type, int side, int cost) implements DirectorCommand {}
    record BeginArena(String objective, int durationTicks) implements DirectorCommand {}
    record BeginRecovery(int durationTicks) implements DirectorCommand {}
    record SpawnBoss() implements DirectorCommand {
        public static final SpawnBoss INSTANCE = new SpawnBoss();
    }
}

public record DarkMissionDefinition(List<MissionSegment> segments, int bossHp) {
    public DarkMissionDefinition { segments = List.copyOf(segments); }

    public static DarkMissionDefinition standard() {
        return new DarkMissionDefinition(List.of(
                new MissionSegment(MissionSegment.Kind.COMBAT, 6_000, 24, "SHIP 30 LINES"),
                MissionSegment.recovery(900),
                new MissionSegment(MissionSegment.Kind.ARENA, 6_000, 30, "DELETE ERROR NODES"),
                new MissionSegment(MissionSegment.Kind.COMBAT, 12_000, 34, "KEEP THE BUILD GREEN"),
                MissionSegment.recovery(900),
                new MissionSegment(MissionSegment.Kind.ARENA, 9_000, 42, "PROTECT CI"),
                new MissionSegment(MissionSegment.Kind.COMBAT, 12_000, 48, "REACH THE BOSS GATE"),
                new MissionSegment(MissionSegment.Kind.BOSS_GATE, 600, 0, "LEGACY CODE")),
                2_400);
    }

    public static DarkMissionDefinition forTest() {
        List<MissionSegment> shortSegments = standard().segments().stream()
                .map(s -> new MissionSegment(s.kind(), Math.max(1, s.durationTicks() / 60),
                        s.threatPerSecond(), s.objective()))
                .toList();
        return new DarkMissionDefinition(shortSegments, 120);
    }

    public int totalTicks() {
        return segments.stream().mapToInt(MissionSegment::durationTicks).sum();
    }

    public int bossGateStartTick() {
        return segments.stream().limit(segments.size() - 1L)
                .mapToInt(MissionSegment::durationTicks).sum();
    }
}
```

`DarkMissionDefinition.standard()` defines eight segments totaling 47,400 ticks before the 90–150 second boss fight. `forTest()` uses the same order with segment durations divided by 60 and a 120-HP core.

- [ ] **Step 4: Implement the threat budget**

Each tick adds `threatPerSecond / 60.0`, capped at twice one second's rate. Spawn candidates have explicit costs: Bug 2, Conflict 4, Crash 5, Lock 6, Tech Debt 12, Firewall 10. The director may emit spawns only when `activeHostiles < 30` and budget covers the candidate. Player pressure above 0.8 selects cheaper enemies; pressure below 0.35 permits elites. Recovery emits `BeginRecovery` once and no hostile spawn. The final segment emits `SpawnBoss` once.

- [ ] **Step 5: Add spawn protection, ranged telegraphs, checkpoints, and evolution events**

New enemies start with 30 ticks of collision protection. Ranged enemies enter a 30-tick telegraph state when their shoot timer expires; `maybeShoot` returns null during the telegraph and emits the projectile only when the counter reaches zero. `ObstacleManager.Enemy.getTelegraphTicks()` feeds `RenderSnapshot.EntityView` in Task 7.

When `BeginRecovery` fires, `GameSession` records the player's current world position as the checkpoint. Losing a life clears hostiles and enemy projectiles, restores the checkpoint position, heals to full, and grants 90 invincibility ticks. Zero remaining lives enters `GAME_OVER`. The package-private `applyFatalDamageForTest()` invokes the same private death handler as production damage; it cannot set position, lives, or boss state directly.

When the elite Arena completes, call `RunBuild.tryEvolve()`. A successful evolution emits a session event used by Task 8 for the evolution title card.

- [ ] **Step 6: Integrate only level zero**

`GameSession` owns the director for Dark. `GamePanel` consumes its commands using the existing `ObstacleManager` spawn APIs. Disable `spawnRandom` and legacy trigger polling for level zero; levels one through four keep the old `LevelManager` path until phase two. Expose `LevelManager.isLegacyMission(int level)` to make the branch explicit and testable.

- [ ] **Step 7: Run director, level, and full tests**

Run: `.\gradlew.bat test --tests '*EncounterDirectorTest' --tests '*LevelManagerTest' --tests '*ObstacleManagerTest' --tests '*GameSessionTest'`

Expected: PASS.

Run: `.\gradlew.bat test`

Expected: PASS.

- [ ] **Step 8: Commit Task 5**

```bash
git add src/main/java/com/bigphil/mergehell/mission src/main/java/com/bigphil/mergehell/engine/GameSession.java src/main/java/com/bigphil/mergehell/LevelManager.java src/main/java/com/bigphil/mergehell/GamePanel.java src/main/java/com/bigphil/mergehell/model/ObstacleManager.java src/test/java/com/bigphil/mergehell/mission/EncounterDirectorTest.java src/test/java/com/bigphil/mergehell/model/ObstacleManagerTest.java src/test/java/com/bigphil/mergehell/engine/GameSessionTest.java
git commit -m "feat: direct dark mission pacing"
```

### Task 6: Implement the Legacy Code Dependency-Chain Boss

**Files:**
- Create: `src/main/java/com/bigphil/mergehell/boss/BossPhase.java`
- Create: `src/main/java/com/bigphil/mergehell/boss/BossAction.java`
- Create: `src/main/java/com/bigphil/mergehell/boss/BossSnapshot.java`
- Create: `src/main/java/com/bigphil/mergehell/boss/DependencyNode.java`
- Create: `src/main/java/com/bigphil/mergehell/boss/LegacyBossController.java`
- Modify: `src/main/java/com/bigphil/mergehell/engine/GameSession.java`
- Modify: `src/main/java/com/bigphil/mergehell/GamePanel.java`
- Modify: `src/main/java/com/bigphil/mergehell/CollisionSystem.java`
- Modify: `src/main/java/com/bigphil/mergehell/combat/CombatEvent.java`
- Test: `src/test/java/com/bigphil/mergehell/boss/LegacyBossControllerTest.java`

**Interfaces:**
- Produces: `LegacyBossController.tick(double playerX, double playerY): List<BossAction>`
- Produces: `LegacyBossController.damageNode(int, int): void`
- Produces: `LegacyBossController.damageCore(int): int` returning accepted damage
- Produces: `LegacyBossController.snapshot(): BossSnapshot`

- [ ] **Step 1: Write failing phase, shield, and telegraph tests**

```java
@Test
void coreRejectsDamageWhileDependencyNodesLive() {
    LegacyBossController boss = new LegacyBossController(new Random(20), 120);
    int hp = boss.coreHp();
    assertEquals(0, boss.damageCore(100));
    assertEquals(hp, boss.coreHp());
}

@Test
void destroyingDependenciesExposesCore() {
    LegacyBossController boss = new LegacyBossController(new Random(21), 120);
    for (DependencyNode node : boss.nodes()) boss.damageNode(node.id(), node.maxHp());
    assertEquals(BossPhase.CORE_EXPOSED, boss.phase());
    assertEquals(100, boss.damageCore(100));
}

@Test
void highDamageSweepTelegraphsForAtLeastTwentyFourTicks() {
    LegacyBossController boss = new LegacyBossController(new Random(22), 120);
    BossAction.Sweep sweep = null;
    for (int tick = 0; tick < 2_000 && sweep == null; tick++) {
        for (BossAction action : boss.tick(100, 200)) {
            if (action instanceof BossAction.Sweep found) sweep = found;
        }
    }
    assertNotNull(sweep);
    assertTrue(sweep.telegraphTicks() >= 24);
}
```

- [ ] **Step 2: Run boss tests and verify failure**

Run: `.\gradlew.bat test --tests '*LegacyBossControllerTest'`

Expected: FAIL because the controller does not exist.

- [ ] **Step 3: Implement dependency and phase state**

```java
public enum BossPhase {
    DEPENDENCIES,
    CORE_EXPOSED,
    ENRAGED,
    DEFEATED
}

public interface CombatEvent {
    record EnemyKilled(EntityType type, int points, double x, double y) implements CombatEvent {}
    record BossPhaseChanged(BossPhase phase) implements CombatEvent {}
}

public sealed interface BossAction {
    record Sweep(int telegraphTicks, double originX, int direction, int damage) implements BossAction {}
    record Dash(int telegraphTicks, int direction, int damage) implements BossAction {}
    record Spawn(EntityType type, int count) implements BossAction {}
}

public record BossSnapshot(
        BossPhase phase, int coreHp, int maxCoreHp,
        List<NodeView> nodes, int transitionTicks, BossAction telegraph) {
    public BossSnapshot { nodes = List.copyOf(nodes); }
    public record NodeView(int id, double x, double y, int hp, int maxHp, boolean alive) {}
}

public final class DependencyNode {
    private final int id;
    private final int maxHp;
    private int hp;

    public DependencyNode(int id, int maxHp) {
        this.id = id;
        this.maxHp = maxHp;
        this.hp = maxHp;
    }

    public void damage(int amount) { hp = Math.max(0, hp - Math.max(0, amount)); }
    public int id() { return id; }
    public int hp() { return hp; }
    public int maxHp() { return maxHp; }
    public boolean alive() { return hp > 0; }
}
```

`LegacyBossController(RandomGenerator random, int coreHp)` starts with three nodes whose HP is `max(30, coreHp / 10)` and a core using the supplied HP. Production passes 2,400; tests pass 120. Destroying all nodes exposes the core. At 40% core HP it enters `ENRAGED`; at zero it enters `DEFEATED`. Core damage returns zero while shielded. The action scheduler uses the injected RNG and only emits high-damage `Sweep` or `Dash` actions after a 24–42 tick telegraph.

- [ ] **Step 4: Route boss hitboxes through collision**

Represent each node and the core as separate rectangles in the boss snapshot. `CollisionSystem` checks node bounds before core bounds. Node destruction emits a combat event and 120 Build XP; phase transitions emit `CombatEvent.BossPhaseChanged`. During phase transition the boss does not attack for 45 ticks and accepts core damage.

- [ ] **Step 5: Use the new boss only in Dark**

When `DirectorCommand.SpawnBoss` fires in level zero, create `LegacyBossController`; retain the legacy `Boss` class for levels one through four. When the controller reaches `DEFEATED`, use the existing mission-complete transition and score saving path.

- [ ] **Step 6: Run boss, collision, and full tests**

Run: `.\gradlew.bat test --tests '*LegacyBossControllerTest' --tests '*CollisionSystemTest' --tests '*BossTest'`

Expected: PASS.

Run: `.\gradlew.bat test`

Expected: PASS.

- [ ] **Step 7: Commit Task 6**

```bash
git add src/main/java/com/bigphil/mergehell/boss src/main/java/com/bigphil/mergehell/combat/CombatEvent.java src/main/java/com/bigphil/mergehell/engine/GameSession.java src/main/java/com/bigphil/mergehell/GamePanel.java src/main/java/com/bigphil/mergehell/CollisionSystem.java src/test/java/com/bigphil/mergehell/boss/LegacyBossControllerTest.java
git commit -m "feat: add legacy dependency boss"
```

### Task 7: Move Simulation to a Lifecycle-Owned Fixed-Step Loop

**Files:**
- Create: `src/main/java/com/bigphil/mergehell/engine/FixedStepAccumulator.java`
- Create: `src/main/java/com/bigphil/mergehell/engine/TickScheduler.java`
- Create: `src/main/java/com/bigphil/mergehell/engine/ScheduledTickScheduler.java`
- Create: `src/main/java/com/bigphil/mergehell/engine/GameLoop.java`
- Create: `src/main/java/com/bigphil/mergehell/engine/GameController.java`
- Create: `src/main/java/com/bigphil/mergehell/engine/RenderSnapshot.java`
- Modify: `src/main/java/com/bigphil/mergehell/GamePanel.java`
- Modify: `src/main/java/com/bigphil/mergehell/MergeHellToolWindowFactory.java`
- Test: `src/test/java/com/bigphil/mergehell/engine/FixedStepAccumulatorTest.java`
- Test: `src/test/java/com/bigphil/mergehell/engine/GameLoopTest.java`
- Test support: `src/test/java/com/bigphil/mergehell/engine/FakeTickScheduler.java`

**Interfaces:**
- Produces: `FixedStepAccumulator.consume(long nowNanos, Runnable tick): int`
- Produces: `GameLoop.start()`, `pause()`, `resume()`, and `dispose()`
- Produces: `GameController.snapshot(): RenderSnapshot`
- Consumes: `GameSession.tick(InputFrame)`

- [ ] **Step 1: Write failing fixed-step and lifecycle tests**

```java
@Test
void accumulatorCapsCatchUpAtFiveTicks() {
    FixedStepAccumulator accumulator = new FixedStepAccumulator(16_666_667L, 5);
    AtomicInteger ticks = new AtomicInteger();
    accumulator.consume(0, ticks::incrementAndGet);
    int consumed = accumulator.consume(1_000_000_000L, ticks::incrementAndGet);
    assertEquals(5, consumed);
    assertEquals(5, ticks.get());
}

@Test
void pauseAndDisposeStopScheduledTicks() {
    FakeTickScheduler scheduler = new FakeTickScheduler();
    AtomicInteger ticks = new AtomicInteger();
    GameLoop loop = new GameLoop(scheduler, ticks::incrementAndGet, error -> fail(error));
    loop.start();
    scheduler.fire();
    assertEquals(1, ticks.get());
    loop.pause();
    scheduler.fire();
    assertEquals(1, ticks.get());
    loop.dispose();
    assertTrue(scheduler.disposed());
}
```

- [ ] **Step 2: Run engine tests and verify failure**

Run: `.\gradlew.bat test --tests '*FixedStepAccumulatorTest' --tests '*GameLoopTest'`

Expected: FAIL because the loop types do not exist.

- [ ] **Step 3: Implement bounded fixed-step accumulation**

```java
public final class FixedStepAccumulator {
    private final long stepNanos;
    private final int maxCatchUpTicks;
    private long lastNanos = Long.MIN_VALUE;
    private long accumulated;

    public FixedStepAccumulator(long stepNanos, int maxCatchUpTicks) {
        this.stepNanos = stepNanos;
        this.maxCatchUpTicks = maxCatchUpTicks;
    }

    public int consume(long nowNanos, Runnable tick) {
        if (lastNanos == Long.MIN_VALUE) { lastNanos = nowNanos; return 0; }
        accumulated = Math.min(accumulated + Math.max(0, nowNanos - lastNanos), stepNanos * maxCatchUpTicks);
        lastNanos = nowNanos;
        int count = 0;
        while (accumulated >= stepNanos && count < maxCatchUpTicks) {
            tick.run();
            accumulated -= stepNanos;
            count++;
        }
        return count;
    }

    public void reset(long nowNanos) { lastNanos = nowNanos; accumulated = 0; }
}
```

- [ ] **Step 4: Implement the scheduler and loop lifecycle**

`TickScheduler` exposes `scheduleAtFixedRate(Runnable, long periodMillis)` and `dispose()`. `ScheduledTickScheduler` owns one daemon `ScheduledExecutorService` named `MergeHell-GameLoop`. `GameLoop` keeps atomic running/paused/disposed flags, catches every tick exception, pauses, and invokes its error consumer once. `GameController` converts that error into an `ERROR` snapshot with a nonblank `errorMessage`; Task 9 persists recoverable progress before publishing the error. `resume` resets the accumulator to `System.nanoTime()` so hidden time is not replayed.

```java
public interface TickScheduler extends Disposable {
    void scheduleAtFixedRate(Runnable task, long periodMillis);
}

final class FakeTickScheduler implements TickScheduler {
    private Runnable task;
    private boolean disposed;

    @Override public void scheduleAtFixedRate(Runnable task, long periodMillis) {
        this.task = task;
    }

    void fire() {
        if (!disposed && task != null) task.run();
    }

    @Override public void dispose() { disposed = true; }
    boolean disposed() { return disposed; }
}
```

- [ ] **Step 5: Publish immutable snapshots**

`RenderSnapshot` is a record containing state, player render data, immutable entity/projectile lists, score, Build XP ratio, Overclock ratio/ticks, current upgrade cards, objective, level, and boss snapshot. `GameController` owns `AtomicReference<RenderSnapshot>` and swaps it after each completed session tick. Snapshot lists use `List.copyOf`.

`GameController` exposes `GameController(GameSession session, TickScheduler scheduler)` for deterministic tests and `GameController.createDefault()` for the tool window. `start`, `onToolWindowHidden`, `onToolWindowShown`, `chooseUpgrade`, `rerollUpgrades`, `snapshot`, and `dispose` are the only public lifecycle/UI methods.

Use these exact nested view types so render code never receives mutable model objects:

```java
public record RenderSnapshot(
        GameState state, long worldTick, PlayerView player,
        List<EntityView> entities, List<ProjectileView> projectiles,
        int score, double buildXpRatio, double overclockRatio, int overclockTicks,
        List<UpgradeDefinition> upgradeCards, boolean rerollAvailable,
        String objective, String banner, int bannerTicks,
        String errorMessage, int level, BossSnapshot boss) {
    public RenderSnapshot {
        entities = List.copyOf(entities);
        projectiles = List.copyOf(projectiles);
        upgradeCards = List.copyOf(upgradeCards);
    }
    public record PlayerView(double x, double y, int hp, int maxHp, int lives,
                             WeaponId weapon, int weaponLevel, boolean shielded, boolean dashing) {}
    public record EntityView(EntityType type, double x, double y, int hp, int maxHp,
                             int telegraphTicks) {}
    public record ProjectileView(double x, double y, double vx, double vy,
                                 WeaponId weapon, boolean hostile, boolean critical) {}
}
```

- [ ] **Step 6: Move all mutable world ownership into `GameSession`**

Move `Player`, `ObstacleManager`, `CollisionSystem`, player/enemy projectile lists, particles, floating texts, platforms, coins, Dark director, Legacy boss, score/combo context, camera position, shake/flash/hitstop counters, and random generator out of `GamePanel`. `GameSession.tick` becomes the only method allowed to update these objects and uses this order: apply edge input, player physics/fire, director commands, enemy/boss updates, collisions/events, progression/Overclock, cleanup/caps, camera, then snapshot data. No mutable model getter is exposed to Swing.

Apply every phase-one `BuildStats` field in the session: dash cooldown is `round(45 * dashCooldownMultiplier)`; a shield reboot consumes one charge to restore 25 HP when damage would otherwise end a life; each drone level fires one 40%-damage Commit projectile at the nearest hostile every 90 ticks; combo expiry is `100 + comboGraceTicks`.

- [ ] **Step 7: Convert `GamePanel` to input/view adapter**

Remove its Swing simulation `Timer`. Key bindings update atomic input flags or enqueue edge commands through `GameController`. `paintComponent` reads one snapshot and passes it to the renderer. `addNotify` resumes; `removeNotify` pauses. Do not mutate session objects from the EDT.

- [ ] **Step 8: Register controller disposal**

In `MergeHellToolWindowFactory`, create `GameController`, pass it to `GamePanel`, and call `Disposer.register(project, controller)`. Keep `content.setPreferredFocusableComponent(gamePanel)` and the existing focus restoration behavior.

- [ ] **Step 9: Run engine and complete tests**

Run: `.\gradlew.bat test --tests '*FixedStepAccumulatorTest' --tests '*GameLoopTest' --tests '*GameSessionTest'`

Expected: PASS.

Run: `.\gradlew.bat test`

Expected: PASS without hanging non-daemon threads.

- [ ] **Step 10: Commit Task 7**

```bash
git add src/main/java/com/bigphil/mergehell/engine src/main/java/com/bigphil/mergehell/GamePanel.java src/main/java/com/bigphil/mergehell/MergeHellToolWindowFactory.java src/test/java/com/bigphil/mergehell/engine
git commit -m "refactor: isolate fixed-step game runtime"
```

### Task 8: Add Fixed-Canvas Rendering, MAX HUD, and Upgrade Cards

**Files:**
- Create: `src/main/java/com/bigphil/mergehell/render/GameViewport.java`
- Create: `src/main/java/com/bigphil/mergehell/render/ViewportTransform.java`
- Create: `src/main/java/com/bigphil/mergehell/render/HudRenderer.java`
- Create: `src/main/java/com/bigphil/mergehell/render/UpgradeOverlayRenderer.java`
- Create: `src/main/java/com/bigphil/mergehell/render/VectorEntityRenderer.java`
- Modify: `src/main/java/com/bigphil/mergehell/GameRenderer.java`
- Modify: `src/main/java/com/bigphil/mergehell/GamePanel.java`
- Test: `src/test/java/com/bigphil/mergehell/render/GameViewportTest.java`
- Test: `src/test/java/com/bigphil/mergehell/render/GameRendererSmokeTest.java`
- Test support: `src/test/java/com/bigphil/mergehell/render/RenderSnapshotFixtures.java`

**Interfaces:**
- Produces: `GameViewport.fit(int panelWidth, int panelHeight): ViewportTransform`
- Produces: `HudRenderer.render(Graphics2D, RenderSnapshot): void`
- Produces: `UpgradeOverlayRenderer.cardBounds(int index): Rectangle`
- Consumes: `RenderSnapshot`

- [ ] **Step 1: Write failing viewport and render smoke tests**

```java
@Test
void viewportLetterboxesWithoutChangingAspectRatio() {
    ViewportTransform transform = GameViewport.fit(1200, 600);
    assertEquals(1.0, transform.scale(), 0.0001);
    assertEquals(120, transform.offsetX());
    assertEquals(0, transform.offsetY());
}

@Test
void upgradeCardsStayInsideLogicalCanvas() {
    UpgradeOverlayRenderer renderer = new UpgradeOverlayRenderer();
    for (int i = 0; i < 3; i++) {
        Rectangle card = renderer.cardBounds(i);
        assertTrue(new Rectangle(0, 0, 960, 600).contains(card));
    }
}

@ParameterizedTest
@CsvSource({"320,240", "960,600", "1440,700"})
void allPrimaryStatesRenderWithoutException(int width, int height) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    GameRenderer renderer = new GameRenderer();
    for (GameState state : List.of(GameState.MENU, GameState.RUNNING,
            GameState.UPGRADE_SELECTION, GameState.BOSS_FIGHT,
            GameState.GAME_OVER, GameState.ERROR)) {
        assertDoesNotThrow(() -> renderer.render(
                image.createGraphics(), width, height, RenderSnapshotFixtures.forState(state)));
    }
}
```

- [ ] **Step 2: Run render tests and verify failure**

Run: `.\gradlew.bat test --tests '*GameViewportTest' --tests '*GameRendererSmokeTest'`

Expected: FAIL because the render package does not exist.

- [ ] **Step 3: Implement viewport mapping**

```java
public final class GameViewport {
    public static final int LOGICAL_WIDTH = 960;
    public static final int LOGICAL_HEIGHT = 600;

    public static ViewportTransform fit(int panelWidth, int panelHeight) {
        double scale = Math.min(panelWidth / (double) LOGICAL_WIDTH, panelHeight / (double) LOGICAL_HEIGHT);
        int drawWidth = (int) Math.round(LOGICAL_WIDTH * scale);
        int drawHeight = (int) Math.round(LOGICAL_HEIGHT * scale);
        return new ViewportTransform(scale, (panelWidth - drawWidth) / 2, (panelHeight - drawHeight) / 2,
                drawWidth, drawHeight);
    }

    private GameViewport() {}
}

public record ViewportTransform(
        double scale, int offsetX, int offsetY, int drawWidth, int drawHeight) {}
```

Place `ViewportTransform` in its own `ViewportTransform.java` file because it is a public top-level record.

Use this complete render fixture in the test source set:

```java
final class RenderSnapshotFixtures {
    static RenderSnapshot forState(GameState state) {
        return new RenderSnapshot(
                state, 120,
                new RenderSnapshot.PlayerView(120, 420, 75, 100, 3,
                        WeaponId.COMMIT_CANNON, 2, false, false),
                List.of(), List.of(), 2_400, 0.45, 0.70, 0,
                List.of(), true, "SHIP 30 LINES", "", 0,
                state == GameState.ERROR ? "GAME LOOP PAUSED" : "", 0, null);
    }

    private RenderSnapshotFixtures() {}
}
```

- [ ] **Step 4: Render to one reusable logical buffer**

`GamePanel` owns one 960×600 `BufferedImage`. On paint, render the latest snapshot into that buffer, clear the physical panel with `GameColors.BG`, apply bilinear interpolation, then draw the buffer using `ViewportTransform`. Convert mouse coordinates back with `(physical - offset) / scale`; ignore clicks outside the draw rectangle.

- [ ] **Step 5: Split HUD and upgrade overlay**

`HudRenderer` draws only HP, Build XP, weapon/evolution label, combo, Overclock, current objective, and boss state. Use cached `Font` and `Color` constants. `UpgradeOverlayRenderer` draws three 250×260 cards at x positions 75, 355, and 635, y 165; card number, title, exact description, rank, and weapon relevance are required. A reroll hint appears only when available.

`VectorEntityRenderer` renders Dark enemies without Emoji: Bug is a red hex body with two antenna strokes and `BUG`; Conflict is opposing amber chevrons with `HEAD`; Crash is an orange diamond with an expanding fuse ring; Lock is a green bracketed node with `LOCK`; Tech Debt is a stacked gray block with `DEBT`; Firewall is a blue-red vertical grid with one visible safe gap. A nonzero telegraph counter draws a pulsing outline and aim line, and offscreen telegraphs draw a directional edge marker.

`GameRenderer` exposes `render(Graphics2D g, int panelWidth, int panelHeight, RenderSnapshot snapshot)`, delegates Dark entities to `VectorEntityRenderer`, removes the unused `drawControls` method, and reduces the terminal to two lines. During Overclock, add a cyan-gold edge vignette and projectile trails without changing attack telegraph colors.

When `snapshot.state() == ERROR`, draw an opaque safe overlay, `GAME LOOP PAUSED`, the sanitized nonblank `errorMessage`, and `[ ENTER ] RETURN TO BRIEFING`. Never render a Java stack trace in the game panel; the full exception goes to the IntelliJ logger.

- [ ] **Step 6: Add mouse upgrade selection**

On mouse release in `UPGRADE_SELECTION`, convert to logical coordinates, find the card rectangle, and call `controller.chooseUpgrade(index)`. Keyboard `1/2/3` remains the primary path.

- [ ] **Step 7: Run render and full tests**

Run: `.\gradlew.bat test --tests '*GameViewportTest' --tests '*GameRendererSmokeTest'`

Expected: PASS.

Run: `.\gradlew.bat test`

Expected: PASS.

- [ ] **Step 8: Commit Task 8**

```bash
git add src/main/java/com/bigphil/mergehell/render src/main/java/com/bigphil/mergehell/GameRenderer.java src/main/java/com/bigphil/mergehell/GamePanel.java src/test/java/com/bigphil/mergehell/render
git commit -m "feat: add max hud and upgrade overlay"
```

### Task 9: Introduce Versioned Persistence and Migrate Existing Scores

**Files:**
- Create: `src/main/java/com/bigphil/mergehell/persistence/MergeHellState.java`
- Create: `src/main/java/com/bigphil/mergehell/persistence/LegacyScoreSource.java`
- Create: `src/main/java/com/bigphil/mergehell/persistence/StateMigrator.java`
- Create: `src/main/java/com/bigphil/mergehell/persistence/MergeHellStateService.java`
- Modify: `src/main/java/com/bigphil/mergehell/model/ScoreStore.java`
- Modify: `src/main/java/com/bigphil/mergehell/engine/GameSession.java`
- Test: `src/test/java/com/bigphil/mergehell/persistence/StateMigratorTest.java`
- Test: `src/test/java/com/bigphil/mergehell/persistence/MergeHellStateServiceTest.java`

**Interfaces:**
- Produces: `MergeHellStateService.getInstance(): MergeHellStateService`
- Produces: `recordScore(int)`, `unlockWeapon(WeaponId)`, `completeMission(int, int)`, `updateSettings(MergeHellState.Settings)`, `saveActiveRun(MergeHellState.ActiveRun)`, and `clearActiveRun()`
- Consumes: legacy score CSV through `LegacyScoreSource.read()`

- [ ] **Step 1: Write failing migration and corruption tests**

```java
@Test
void migratesSortsAndTruncatesLegacyScores() {
    MergeHellState state = new StateMigrator(() -> "40,200,bad,100,300,50").migrate(null);
    assertEquals(List.of(300, 200, 100, 50, 40), state.topScores);
    assertEquals(1, state.schemaVersion);
}

@Test
void invalidStateFallsBackWithoutThrowing() {
    MergeHellState invalid = new MergeHellState();
    invalid.schemaVersion = -20;
    MergeHellState repaired = new StateMigrator(() -> null).migrate(invalid);
    assertEquals(1, repaired.schemaVersion);
    assertEquals(Set.of(WeaponId.COMMIT_CANNON), repaired.unlockedWeapons);
}
```

- [ ] **Step 2: Run persistence tests and verify failure**

Run: `.\gradlew.bat test --tests 'com.bigphil.mergehell.persistence.*'`

Expected: FAIL because persistence types do not exist.

- [ ] **Step 3: Implement the serializable state and migration**

```java
public final class MergeHellState {
    public int schemaVersion = 1;
    public List<Integer> topScores = new ArrayList<>();
    public Set<WeaponId> unlockedWeapons = EnumSet.of(WeaponId.COMMIT_CANNON);
    public Set<Integer> completedMissions = new HashSet<>();
    public int refactorPoints;
    public boolean legacyScoresMigrated;
    public Settings settings = new Settings();
    public ActiveRun activeRun;

    public static final class Settings {
        public int shakePercent = 70;
        public boolean crt = true;
        public boolean flashes = true;
        public int particlePercent = 100;
        public int volumePercent = 35;
        public boolean autoFire;
        public boolean highContrast;
    }

    public static final class ActiveRun {
        public int mission;
        public double checkpointX;
        public double checkpointY;
        public int lives;
        public WeaponId weapon = WeaponId.COMMIT_CANNON;
        public Map<UpgradeId, Integer> upgradeRanks = new EnumMap<>(UpgradeId.class);
        public long worldTick;
    }
}

@FunctionalInterface
public interface LegacyScoreSource {
    String read();
}
```

`StateMigrator` accepts null, clamps settings to 0–100, removes negative scores, sorts descending, keeps five, ensures Commit is unlocked, repairs null collections, and migrates legacy CSV only when the versioned score list is empty.

- [ ] **Step 4: Implement the IntelliJ app service**

Annotate `MergeHellStateService` with `@Service(Service.Level.APP)` and `@State(name = "MergeHellRunner", storages = @Storage("mergeHellRunner.xml"))`. Implement `PersistentStateComponent<MergeHellState>`. `loadState` passes data through `StateMigrator`; mutations copy or normalize collections and never expose the mutable state directly.

- [ ] **Step 5: Adapt `ScoreStore` and session completion**

Keep `ScoreStore.load/save/isHighScore` signatures, but delegate them to `MergeHellStateService`. On first service load, read `PropertiesComponent` key `com.bigphil.mergehell.topScores`; after successful migration set a boolean `legacyScoresMigrated` in state so the CSV is not re-imported. `GameSession` awards Refactor Points only for first mission completion and boss grade.

`GameSession.activeRunState()` returns a defensive snapshot of mission, latest checkpoint, lives, weapon, upgrade ranks, and world tick. Save it at recovery checkpoints and immediately inside `GameController`'s loop-error handler before publishing `ERROR`. Clear it on mission completion or explicit new-run confirmation.

- [ ] **Step 6: Run persistence and full tests**

Run: `.\gradlew.bat test --tests 'com.bigphil.mergehell.persistence.*' --tests '*PlayerTest'`

Expected: PASS.

Run: `.\gradlew.bat test`

Expected: PASS.

- [ ] **Step 7: Commit Task 9**

```bash
git add src/main/java/com/bigphil/mergehell/persistence src/main/java/com/bigphil/mergehell/model/ScoreStore.java src/main/java/com/bigphil/mergehell/engine/GameSession.java src/test/java/com/bigphil/mergehell/persistence
git commit -m "feat: persist max progression safely"
```

### Task 10: Add Entity Caps, Long-Run Simulation, and Vertical-Slice Verification

**Files:**
- Create: `src/main/java/com/bigphil/mergehell/engine/EntityLimits.java`
- Create: `src/main/java/com/bigphil/mergehell/engine/BoundedEntityStore.java`
- Create: `src/main/java/com/bigphil/mergehell/engine/SessionMetrics.java`
- Modify: `src/main/java/com/bigphil/mergehell/engine/GameSession.java`
- Modify: `src/main/java/com/bigphil/mergehell/model/ObstacleManager.java`
- Modify: `src/main/java/com/bigphil/mergehell/GamePanel.java`
- Modify: `src/main/java/com/bigphil/mergehell/GameRenderer.java`
- Test: `src/test/java/com/bigphil/mergehell/engine/VerticalSliceSimulationTest.java`
- Test: `src/test/java/com/bigphil/mergehell/engine/EntityLimitsTest.java`

**Interfaces:**
- Produces: `EntityLimits` constants and `GameSession.metrics(): SessionMetrics`
- Consumes: all phase-one systems

- [ ] **Step 1: Write failing entity-cap and accelerated mission tests**

```java
@Test
void purelyVisualEntitiesAreDroppedBeforeLogicalProjectiles() {
    BoundedEntityStore<Integer> logical = BoundedEntityStore.rejectNew(3);
    assertTrue(logical.add(1));
    assertTrue(logical.add(2));
    assertTrue(logical.add(3));
    assertFalse(logical.add(4));
    assertEquals(List.of(1, 2, 3), logical.snapshot());
    assertEquals(1, logical.rejectedCount());
}

@Test
void acceleratedDarkMissionReachesBossAndCompletes() {
    GameSession session = new GameSession(32L, DarkMissionDefinition.forTest());
    for (int tick = 0; tick < 60 * 20 && !session.isComplete(); tick++) {
        if (session.state() == GameState.UPGRADE_SELECTION) {
            session.chooseUpgrade(0);
        } else {
            session.tick(new InputFrame(false, true, tick % 90 == 0,
                    true, tick % 120 == 0, false));
        }
    }
    assertTrue(session.bossSpawned());
    assertTrue(session.isComplete());
    assertTrue(session.metrics().maxHostiles() <= EntityLimits.MAX_HOSTILES);
}

@Test
void hiddenControllerDoesNotAdvanceWorld() {
    FakeTickScheduler scheduler = new FakeTickScheduler();
    GameController controller = new GameController(
            new GameSession(33L, DarkMissionDefinition.forTest()), scheduler);
    controller.start();
    controller.onToolWindowHidden();
    long before = controller.snapshot().worldTick();
    scheduler.fire();
    assertEquals(before, controller.snapshot().worldTick());
}
```

- [ ] **Step 2: Run simulation tests and verify failure**

Run: `.\gradlew.bat test --tests '*EntityLimitsTest' --tests '*VerticalSliceSimulationTest'`

Expected: FAIL because limits, metrics, and accelerated mission helpers do not exist.

- [ ] **Step 3: Implement explicit limits and metrics**

```java
public final class EntityLimits {
    public static final int MAX_HOSTILES = 40;
    public static final int MAX_PROJECTILES = 320;
    public static final int MAX_ENEMY_PROJECTILES = 180;
    public static final int MAX_PARTICLES = 1_200;
    public static final int MAX_FLOATING_TEXTS = 80;
    private EntityLimits() {}
}

public record SessionMetrics(
        int hostiles, int projectiles, int enemyProjectiles, int particles,
        int floatingTexts, int maxHostiles, int rejectedProjectiles,
        long worldTick, int upgradeCount, long bossSpawnTick, long completionTick) {}

public final class BoundedEntityStore<T> {
    private final int capacity;
    private final boolean replaceOldest;
    private final ArrayDeque<T> values = new ArrayDeque<>();
    private int rejectedCount;

    private BoundedEntityStore(int capacity, boolean replaceOldest) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
        this.replaceOldest = replaceOldest;
    }

    public static <T> BoundedEntityStore<T> rejectNew(int capacity) {
        return new BoundedEntityStore<>(capacity, false);
    }

    public static <T> BoundedEntityStore<T> replaceOldest(int capacity) {
        return new BoundedEntityStore<>(capacity, true);
    }

    public boolean add(T value) {
        if (values.size() == capacity) {
            if (!replaceOldest) { rejectedCount++; return false; }
            values.removeFirst();
        }
        values.addLast(Objects.requireNonNull(value));
        return true;
    }

    public List<T> snapshot() { return List.copyOf(values); }
    public int rejectedCount() { return rejectedCount; }
}
```

Before adding a logical entity, enforce its specific cap and increment a rejected counter. Before adding visual entities, remove the oldest expired/lowest-priority element first. Never remove active logical projectiles to make room for particles. `SessionMetrics` records current/max entity counts, rejected counts, world tick, upgrade count, boss spawn tick, and completion tick.

- [ ] **Step 4: Add accelerated definitions without production shortcuts**

The test constructs `GameSession(seed, DarkMissionDefinition.forTest())`, which injects shortened segment durations and a 120-HP boss but uses normal production systems. The loop moves, jumps, dashes, fires, and chooses the first upgrade through the same public input and choice APIs used by `GameController`. It does not call damage or phase-transition shortcuts; test boss hitboxes are positioned on the standard firing lane.

- [ ] **Step 5: Verify no per-frame font construction in the new renderer**

Move fonts and static colors used by `HudRenderer`, `UpgradeOverlayRenderer`, and new vector entities into `static final` constants. Search with:

Run: `rg -n "new Font\(" src/main/java/com/bigphil/mergehell/render src/main/java/com/bigphil/mergehell/GameRenderer.java`

Expected: matches only in static field initializers or font-cache creation, not inside render methods.

- [ ] **Step 6: Run the complete automated verification**

Run: `.\gradlew.bat clean test buildPlugin`

Expected: BUILD SUCCESSFUL; all unit, simulation, persistence, and render smoke tests pass; `build/distributions/merge-hell-runner.zip` is produced.

- [ ] **Step 7: Run plugin verification**

Run: `.\gradlew.bat verifyPluginConfiguration`

Expected: BUILD SUCCESSFUL with since-build 232 and no configuration error.

- [ ] **Step 8: Perform the IDE manual checklist**

Run: `.\gradlew.bat runIde`

Verify in the sandbox IDE:

- Merge Hell opens without stealing editor shortcuts until its panel is focused.
- Space/Enter starts Dark; movement, double jump, dash, melee, and fire work.
- Build XP opens a three-card pause; keyboard and mouse selection resume the same run.
- Reroll works once and is then disabled.
- Commit ricochets; Force Push visibly spreads and knocks enemies back.
- Overclock lasts eight seconds and produces a clear but readable visual peak.
- Dark alternates combat and recovery, then spawns the dependency-node Legacy Boss.
- Hiding and reopening the tool window pauses/resumes without a time jump or stuck key.
- Completing or failing saves scores and campaign state; restarting IDEA reloads them.
- Compact and wide tool windows preserve the 960×600 aspect ratio and usable HUD.

- [ ] **Step 9: Commit the verification hardening**

```bash
git add src/main/java/com/bigphil/mergehell/engine/EntityLimits.java src/main/java/com/bigphil/mergehell/engine/BoundedEntityStore.java src/main/java/com/bigphil/mergehell/engine/SessionMetrics.java src/main/java/com/bigphil/mergehell/engine/GameSession.java src/main/java/com/bigphil/mergehell/model/ObstacleManager.java src/main/java/com/bigphil/mergehell/GamePanel.java src/main/java/com/bigphil/mergehell/GameRenderer.java src/test/java/com/bigphil/mergehell/engine
git commit -m "test: harden max vertical slice"
```

## Plan Completion Gate

The vertical slice is complete only when all ten task commits exist, `clean test buildPlugin` passes, the manual checklist passes in `runIde`, no unrelated pre-existing files were staged, and the Dark mission can be played from briefing through Legacy Boss completion using either keyboard-only or keyboard-plus-mouse upgrade selection.

