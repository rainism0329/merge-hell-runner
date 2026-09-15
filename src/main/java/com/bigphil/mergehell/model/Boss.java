package com.bigphil.mergehell.model;

import com.bigphil.mergehell.i18n.GameText;
import com.bigphil.mergehell.engine.ProjectileBudget;
import com.bigphil.mergehell.boss.SingularityPatternMemory;
import com.bigphil.mergehell.boss.ChapterBossEncounter;

import java.awt.*;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * Bosses for the four legacy campaign levels. The Dark mission keeps its bespoke
 * dependency-graph controller; these encounters deliberately use different movement,
 * projectile grammar, summons, phase names, and silhouettes instead of shared stat scaling.
 */
public class Boss {
    private enum Phase { ENTER, IDLE, DASH_WARN, DASH, RECOVER }

    private enum Archetype {
        GENERIC("SYSTEM ANOMALY", new Color(231, 92, 76), new Color(242, 197, 92),
                new String[]{"OBSERVE", "ESCALATE", "TERMINATE"}, "SCANNING"),
        MEMORY_LEAK("HEAP PREDATOR", new Color(166, 226, 46), new Color(249, 38, 114),
                new String[]{"ALLOCATING", "LEAKING", "OUT OF MEMORY"}, "HEAP GROWING"),
        ARCHITECT("BLUEPRINT TYRANT", new Color(64, 196, 255), new Color(181, 137, 0),
                new String[]{"DRAFT", "ENFORCE", "PERFECT FORM"}, "CALCULATING"),
        KERNEL_PANIC("RING-0 EXECUTIONER", new Color(255, 85, 85), new Color(180, 142, 173),
                new String[]{"BOOT", "PANIC", "SYSTEM HALT"}, "WATCHDOG ACTIVE"),
        SINGULARITY("PATTERN DEVOURER", new Color(255, 121, 198), new Color(139, 233, 253),
                new String[]{"OBSERVE", "ASSIMILATE", "COLLAPSE"}, "LEARNING BUILD");

        final String personality;
        final Color primary;
        final Color accent;
        final String[] stages;
        final String idleMove;

        Archetype(String personality, Color primary, Color accent,
                  String[] stages, String idleMove) {
            this.personality = personality;
            this.primary = primary;
            this.accent = accent;
            this.stages = stages;
            this.idleMove = idleMove;
        }

        static Archetype forLevel(int level) {
            return switch (level) {
                case 1 -> MEMORY_LEAK;
                case 2 -> ARCHITECT;
                case 3 -> KERNEL_PANIC;
                case 4 -> SINGULARITY;
                default -> GENERIC;
            };
        }
    }

    private double x;
    private double y;
    private final int width;
    private final int height;
    private final ChapterBossEncounter chapterEncounter;
    private final int maxHp;
    private int hp;
    private final String name;
    private final String symbol;
    private final int bossLevel;
    private final Archetype archetype;
    private final Random random;
    private final Set<String> movesSeen = new LinkedHashSet<>();

    private boolean active;
    private Phase phase = Phase.ENTER;
    private double targetX;
    private double playerX;
    private double playerY;
    private double dashDir = -1;
    private int combatTick;
    private int actionCooldown = 90;
    private int minorCooldown = 45;
    private int patternIndex;
    private int warningTicks;
    private int dashTicks;
    private int moveLabelTicks;
    private int lastStage = 1;
    private int lastGroundY = 480;
    private String moveName = "MATERIALIZING";
    private String dashMoveName = "CORE DUMP";
    private boolean flashing;
    private int vulnerabilityTicks;
    private int blueprintDisruptionTicks;
    private int kernelDisruptionTicks;
    private int kernelRebootTicks;
    private int kernelLaneWarningTicks;
    private final SingularityPatternMemory singularityMemory = new SingularityPatternMemory();
    private boolean singularityPlayerFired;
    private int singularityWarningTicks, singularityDisruptionTicks, singularityRebootTicks;
    private List<PredictedShot> singularityShots = List.of();
    public record PredictedShot(double x, double y, double vx, double vy) { }
    public static final int SINGULARITY_WARNING_TICKS = 75;
    public static final int KERNEL_CHARGE_WARNING_TICKS = 60;
    public static final int KERNEL_LANE_WARNING_TICKS = 75;
    private int hitFlashTicks;
    private int highestStage = 1;
    private double hitRecoilStrength;
    private long damageSequence;
    public static final int MAX_VULNERABILITY_TICKS = 180;

    public Boss(String name, int hpPool, String symbol, double spawnWorldX, int bossLevel) {
        this(name, hpPool, symbol, spawnWorldX, bossLevel, new Random());
    }

    public Boss(String name, int hpPool, String symbol, double spawnWorldX, int bossLevel, long seed) {
        this(name, hpPool, symbol, spawnWorldX, bossLevel, new Random(seed));
    }

    Boss(String name, int hpPool, String symbol, double spawnWorldX,
         int bossLevel, Random random) {
        this.name = Objects.requireNonNull(name, "name");
        this.maxHp = Math.max(1, hpPool);
        this.hp = this.maxHp;
        this.symbol = Objects.requireNonNull(symbol, "symbol");
        this.bossLevel = bossLevel;
        this.archetype = Archetype.forLevel(bossLevel);
        this.random = Objects.requireNonNull(random, "random");
        this.x = spawnWorldX + 200;
        this.targetX = spawnWorldX - (bossLevel == 2 ? 280 + 85 : bossLevel == 3 ? 240 + 85
                : bossLevel == 4 ? 270 + 85 : 150);
        this.y = 100;
        this.chapterEncounter = bossLevel >= 2 && bossLevel <= 4
                ? new ChapterBossEncounter(bossLevel, maxHp, targetX, x) : null;
        this.width = chapterEncounter == null ? 120 : chapterEncounter.width();
        this.height = chapterEncounter == null ? 150 : chapterEncounter.height();
        this.actionCooldown = 70 + this.random.nextInt(25);
        this.minorCooldown = 38 + this.random.nextInt(15);
    }

    public double getX() { return x; }
    private com.bigphil.mergehell.progression.GameDifficulty combatDifficulty;
    public void configureCombat(com.bigphil.mergehell.progression.GameDifficulty difficulty) {
        combatDifficulty=java.util.Objects.requireNonNull(difficulty);
        if(chapterEncounter!=null) chapterEncounter.configureCombat(difficulty);
    }
    public double getY() { return y; }
    private int recovery(int ticks) { return combatDifficulty==null?ticks:com.bigphil.mergehell.progression.CombatBalance.recovery(ticks,combatDifficulty); }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getGroundY() { return lastGroundY; }
    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
    public boolean isDashing() { return chapterEncounter != null ? chapterEncounter.isDashing() : phase == Phase.DASH; }
    public int getBossLevel() { return bossLevel; }
    public int getCombatTick() { return chapterEncounter != null ? chapterEncounter.tick() : combatTick; }
    public int getWarningTicks() { return chapterEncounter != null ? chapterEncounter.warningTicks() : phase == Phase.DASH_WARN ? warningTicks : 0; }
    public int getKernelLaneWarningTicks() { return chapterEncounter != null && bossLevel == 3 ? chapterEncounter.warningTicks() : kernelLaneWarningTicks; }
    public int getKernelRebootTicks() { return chapterEncounter != null && bossLevel == 3 ? chapterEncounter.recoveryTicks() : kernelRebootTicks; }
    public int getSingularityWarningTicks() { return chapterEncounter != null && bossLevel == 4 ? chapterEncounter.warningTicks() : Math.max(singularityWarningTicks, getWarningTicks()); }
    public int getSingularityRebootTicks() { return chapterEncounter != null && bossLevel == 4 ? chapterEncounter.recoveryTicks() : singularityRebootTicks; }
    public List<PredictedShot> getSingularityPredictedShots() { return chapterEncounter != null ? chapterEncounter.predictedShots() : singularityShots; }
    /** Called only after the player actually emitted a shot, including budget admission. */
    public void recordPlayerShot() { if (archetype == Archetype.SINGULARITY) singularityPlayerFired = true; }
    public double getDashDirection() { return chapterEncounter != null ? chapterEncounter.direction() : dashDir; }
    /** A fault shutdown and safe return cannot hurt the player through body contact. */
    public boolean isContactDangerous() {
        if (chapterEncounter != null) return active && hp > 0 && chapterEncounter.isContactDangerous();
        return active && hp > 0 && (archetype != Archetype.KERNEL_PANIC
                || kernelDisruptionTicks == 0 && kernelRebootTicks == 0 && phase != Phase.RECOVER)
                && (archetype != Archetype.SINGULARITY
                || singularityDisruptionTicks == 0 && singularityRebootTicks == 0
                && phase != Phase.RECOVER && phase != Phase.DASH_WARN);
    }
    public boolean isVulnerable() { return hp > 0 && (vulnerabilityTicks > 0 || chapterEncounter != null && chapterEncounter.exposureTicks() > 0); }
    public int getVulnerabilityTicks() { return hp > 0 ? Math.max(vulnerabilityTicks, chapterEncounter == null ? 0 : chapterEncounter.exposureTicks()) : 0; }
    public int getHitFlashTicks() { return hp > 0 ? hitFlashTicks : 0; }
    public long getDamageSequence() { return damageSequence; }
    public String getPersonalityName() { return chapterEncounter == null ? archetype.personality
            : GameText.message("chapter.boss.name." + bossLevel); }
    public String getMoveName() { return chapterEncounter == null ? moveName : GameText.message(chapterEncounter.moveKey()); }
    public int getCombatStage() {
        double ratio = hp / (double) maxHp;
        return Math.max(highestStage, ratio <= 0.30 ? 3 : ratio <= 0.65 ? 2 : 1);
    }
    public String getStageName() { return chapterEncounter == null ? archetype.stages[getCombatStage() - 1]
            : GameText.message("chapter.boss.stage." + bossLevel + "." + getCombatStage()); }
    public String getEncounterStatus() {
        return "P" + getCombatStage() + " " + getStageName() + "  //  " + getMoveName();
    }

    Set<String> movesSeenForTesting() { return chapterEncounter == null ? Set.copyOf(movesSeen) : chapterEncounter.movesSeen(); }

    public void activate() {
        active = true;
        setMove("MATERIALIZING", 120);
    }

    /** Preparation moves the visible body without advancing attacks, contact damage or cooldowns. */
    public void previewArrival(double progress, int groundY) {
        if (active) return;
        if (chapterEncounter != null) {
            chapterEncounter.preview(progress, groundY);
            x = chapterEncounter.x(); y = chapterEncounter.y(); lastGroundY = groundY;
            return;
        }
        double t = Math.max(0, Math.min(1, progress));
        t = t * t * (3 - 2 * t);
        x = targetX + 350 * (1 - t);
        y = 100 + (Math.max(100, groundY - height - 50) - 100) * t;
    }

    public void update(ObstacleManager obstacleManager, int groundY, double px, double py,
                       List<Projectile> enemyBullets) {
        if (!active || hp <= 0) return;
        if (vulnerabilityTicks > 0) vulnerabilityTicks--;
        if (hitFlashTicks > 0) hitFlashTicks--;
        if (chapterEncounter != null) {
            chapterEncounter.update(groundY, px, py, getCombatStage(), obstacleManager, enemyBullets);
            x = chapterEncounter.x(); y = chapterEncounter.y(); lastGroundY = groundY;
            return;
        }
        playerX = px;
        playerY = py;
        lastGroundY = groundY;
        if (archetype == Archetype.SINGULARITY) {
            singularityMemory.observe(px, py + 15, singularityPlayerFired);
            singularityPlayerFired = false;
            if (singularityDisruptionTicks > 0 || singularityRebootTicks > 0) {
                if (singularityDisruptionTicks > 0) singularityDisruptionTicks--;
                else singularityRebootTicks--;
                actionCooldown = Math.max(actionCooldown, 70);
                return;
            }
        }

        if (kernelDisruptionTicks > 0 || kernelRebootTicks > 0) {
            if (kernelDisruptionTicks > 0) kernelDisruptionTicks--;
            else kernelRebootTicks--;
            actionCooldown = Math.max(actionCooldown, 70);
            if (kernelDisruptionTicks == 0 && kernelRebootTicks == 0) {
                phase = Phase.RECOVER;
                setMove("REBOOTING", 45);
            }
            return;
        }

        if (blueprintDisruptionTicks > 0) {
            blueprintDisruptionTicks--;
            moveLabelTicks = blueprintDisruptionTicks;
            actionCooldown = Math.max(actionCooldown, 55);
            minorCooldown = Math.max(minorCooldown, 40);
            if (blueprintDisruptionTicks == 0) setMove(archetype.idleMove, 0);
            return;
        }

        if (phase == Phase.ENTER) {
            x = Math.max(targetX, x - (5 + bossLevel * 0.35));
            y = clampY(y + (playerY - y - height / 2.0) * 0.025, groundY);
            if (x <= targetX) {
                phase = Phase.IDLE;
                actionCooldown = 55;
                setMove("LOCK ACQUIRED", 55);
            }
            return;
        }

        combatTick++;
        if (moveLabelTicks > 0) {
            moveLabelTicks--;
        } else {
            moveName = archetype.idleMove;
        }

        int stage = getCombatStage();
        if (stage != lastStage) {
            lastStage = stage;
            if (archetype == Archetype.KERNEL_PANIC || archetype == Archetype.SINGULARITY) {
                phase = Phase.RECOVER;
                warningTicks = dashTicks = kernelLaneWarningTicks = 0;
                flashing = false;
                if (archetype == Archetype.KERNEL_PANIC) {
                    kernelRebootTicks = 90;
                    markMove("PHASE_SHIFT_" + stage, "RING-0 RECONFIGURING // STAND CLEAR", 90);
                } else {
                    singularityWarningTicks = 0; singularityShots = List.of();
                    singularityRebootTicks = 90; singularityMemory.reset();
                    markMove("PHASE_SHIFT_" + stage, "PATTERN RESET // STAND CLEAR", 90);
                }
                return;
            }
            x = targetX;
            phase = Phase.IDLE;
            flashing = false;
            actionCooldown = 24;
            radialBurst(enemyBullets, 8 + stage * 3, 3.2 + stage * 0.45, stage == 3);
            markMove("PHASE_SHIFT_" + stage, "PHASE SHIFT // " + getStageName(), 90);
        }

        switch (archetype) {
            case MEMORY_LEAK -> updateMemoryLeak(obstacleManager, groundY, enemyBullets, stage);
            case ARCHITECT -> updateArchitect(obstacleManager, groundY, enemyBullets, stage);
            case KERNEL_PANIC -> updateKernelPanic(obstacleManager, groundY, enemyBullets, stage);
            case SINGULARITY -> updateSingularity(obstacleManager, groundY, enemyBullets, stage);
            case GENERIC -> updateGeneric(obstacleManager, groundY, enemyBullets, stage);
        }
    }

    private void updateMemoryLeak(ObstacleManager om, int groundY,
                                  List<Projectile> bullets, int stage) {
        double hover = playerY - height / 2.0 + Math.sin(combatTick * 0.045) * (35 + stage * 12);
        y = clampY(y + (hover - y) * 0.045, groundY);

        if (--minorCooldown <= 0) {
            aimedVolley(bullets, 2 + stage, 0.32 + stage * 0.08,
                    4.6 + stage * 0.45, false);
            if (moveLabelTicks == 0) setMove("REFERENCE DRIP", 28);
            minorCooldown = recovery(72 - stage * 10);
        }

        if (--actionCooldown > 0) return;
        patternIndex++;
        if ((patternIndex & 1) == 1) {
            aimedVolley(bullets, 5 + stage * 2, 1.05 + stage * 0.12,
                    4.8 + stage * 0.4, stage == 3);
            om.spawnEnemy((int) x + width + 30, groundY - 55, EntityType.LEAK);
            if (stage >= 2) om.spawnEnemy((int) x + width + 80, groundY - 85, EntityType.TECHDEBT);
            markMove("HEAP_SPRAY", "HEAP SPRAY // RETAINED OBJECTS", 72);
        } else {
            radialBurst(bullets, 10 + stage * 4, 3.2 + stage * 0.55, stage == 3);
            if (stage == 3) aimedVolley(bullets, 7, 0.9, 6.2, true);
            markMove("GC_STORM", "GC STORM // NO SAFE ROOT", 78);
        }
        actionCooldown = recovery(175 - stage * 24);
    }

    private void updateArchitect(ObstacleManager om, int groundY,
                                 List<Projectile> bullets, int stage) {
        int node = (combatTick / 110) % 3;
        double nodeY = 45 + node * Math.max(45, (groundY - height - 70) / 2.0);
        y = clampY(y + (nodeY - y) * 0.04, groundY);

        if (--minorCooldown <= 0) {
            aimedVolley(bullets, 1 + stage, 0.26, 5.8 + stage * 0.45, stage == 3);
            minorCooldown = recovery(78 - stage * 9);
        }

        if (--actionCooldown > 0) return;
        patternIndex++;
        if ((patternIndex & 1) == 1) {
            blueprintGrid(bullets, groundY, stage);
            if (stage == 3) om.spawnEnemy((int) x + width + 35, groundY - 105, EntityType.SENTINEL);
            markMove("BLUEPRINT_GRID", "BLUEPRINT GRID // FIND THE GAP", 82);
        } else {
            int walls = stage == 3 ? 2 : 1;
            for (int i = 0; i < walls; i++) {
                om.spawnEnemy((int) x + width + 45 + i * 115, 0, EntityType.FIREWALL);
            }
            if (stage >= 2) om.spawnEnemy((int) x + width + 10, groundY - 125, EntityType.SENTINEL);
            markMove("RUNTIME_WALL", "RUNTIME WALL // DESIGN IS LAW", 86);
        }
        actionCooldown = recovery(170 - stage * 19);
    }

    private void updateKernelPanic(ObstacleManager om, int groundY,
                                   List<Projectile> bullets, int stage) {
        if (updateDashState(groundY, stage)) return;
        // Grounded charges cross the real fault nodes. The warning locks both height and direction.
        y = groundY - height;
        if (kernelLaneWarningTicks > 0) {
            if (--kernelLaneWarningTicks == 0) {
                panicLanes(bullets, groundY, stage);
                om.spawnEnemy((int) x + width + 25, groundY - 60, EntityType.INTERRUPT);
                markMove("PANIC_LANES", "PANIC LANES // IRQ STORM", 60);
                actionCooldown = 110 - stage * 10;
            }
            return;
        }
        if (--actionCooldown > 0) return;
        patternIndex++;
        if ((patternIndex & 1) == 1) {
            startDash("CORE DUMP", "CORE_DUMP", stage);
        } else {
            kernelLaneWarningTicks = KERNEL_LANE_WARNING_TICKS;
            setMove("IRQ LANES // FIND THE GAP", kernelLaneWarningTicks);
        }
        actionCooldown = 138 - stage * 16;
    }

    private void updateSingularity(ObstacleManager om, int groundY,
                                   List<Projectile> bullets, int stage) {
        if (updateDashState(groundY, stage)) return;
        if (singularityWarningTicks > 0) {
            if (--singularityWarningTicks == 0) {
                var lockedShots = singularityShots;
                ProjectileBudget.emit(bullets, lockedShots.size(), () -> {
                    List<Projectile> emitted = new ArrayList<>();
                    for (var shot : lockedShots)
                        emitted.add(new Projectile(shot.x(), shot.y(), shot.vx(), shot.vy(), ProjectileType.ENEMY));
                    return emitted;
                });
                singularityShots = List.of();
                actionCooldown = 125 - stage * 10;
                setMove("ECHO RELEASED // REPOSITION", actionCooldown);
            }
            return;
        }
        x = targetX + Math.sin(combatTick * 0.027) * (18 + stage * 5);
        double orbitY = (groundY - height) / 2.0 + Math.sin(combatTick * 0.041) * (65 + stage * 18);
        y = clampY(y + (orbitY - y) * 0.06, groundY);
        if (--actionCooldown > 0) return;
        patternIndex++;
        var reading = singularityMemory.select(playerX, playerY + 15);
        if (reading.pattern() == SingularityPatternMemory.Pattern.KERNEL_ECHO) {
            y = groundY - height;
            startDash("KERNEL ECHO", "KERNEL_ECHO", stage);
            dashDir = reading.targetX() > x ? 1 : -1;
            return;
        }
        List<PredictedShot> shots = new ArrayList<>();
        double cx = x + width / 2.0, cy = y + height / 2.0;
        if (reading.pattern() == SingularityPatternMemory.Pattern.BLUEPRINT_ECHO) {
            // Leave a generous, announced corridor around the historical height. No weapon is disabled.
            double gapY = Math.max(95, Math.min(groundY - 55, reading.targetY()));
            for (int i = 0; i < 8; i++) {
                double laneY = 35 + (groundY - 70) * i / 7.0;
                if (Math.abs(laneY - gapY) < 65) continue;
                shots.add(new PredictedShot(cx, laneY, -(5.4 + stage * .45), 0));
            }
        } else {
            int count = reading.pattern() == SingularityPatternMemory.Pattern.MEMORY_ECHO ? 4 + stage : 8 + stage;
            double base = Math.atan2(reading.targetY() - cy, reading.targetX() - cx);
            for (int i = 0; i < count; i++) {
                double angle = reading.pattern() == SingularityPatternMemory.Pattern.MEMORY_ECHO
                        ? base + (i / (double) (count - 1) - .5) * .7
                        : Math.PI * 2 * i / count + .18;
                double speed = 4.1 + stage * .35;
                shots.add(new PredictedShot(cx, cy, Math.cos(angle) * speed, Math.sin(angle) * speed));
            }
        }
        singularityShots = List.copyOf(shots);
        singularityWarningTicks = SINGULARITY_WARNING_TICKS;
        String label = switch (reading.pattern()) {
            case MEMORY_ECHO -> "MEMORY LOCK // MOVE FROM THE MARK";
            case BLUEPRINT_ECHO -> "BLUEPRINT ECHO // MARKED SAFE GAP";
            default -> "EVENT HORIZON // WATCH THE SPOKES";
        };
        markMove(reading.pattern().name(), label, singularityWarningTicks);
    }

    private void updateGeneric(ObstacleManager om, int groundY,
                               List<Projectile> bullets, int stage) {
        y = clampY(y + (playerY - y - height / 2.0) * 0.04, groundY);
        if (--minorCooldown <= 0) {
            aimedVolley(bullets, 2 + stage, 0.4, 5.0 + stage, false);
            minorCooldown = 65;
        }
        if (--actionCooldown <= 0) {
            radialBurst(bullets, 10 + stage * 2, 4.0 + stage * 0.5, stage == 3);
            om.spawnEnemy((int) x + width, groundY - 60, EntityType.BUG);
            markMove("SYSTEM_BURST", "SYSTEM BURST", 65);
            actionCooldown = 150;
        }
    }

    private void startDash(String displayName, String moveId, int stage) {
        dashMoveName = displayName;
        dashDir = playerX > x ? 1 : -1;
        warningTicks = groundedCharge() ? KERNEL_CHARGE_WARNING_TICKS : Math.max(16, 34 - stage * 5);
        phase = Phase.DASH_WARN;
        markMove(moveId, displayName + " // TELEGRAPH", warningTicks + 20);
    }

    private boolean updateDashState(int groundY, int stage) {
        if (phase == Phase.DASH_WARN) {
            flashing = (warningTicks / 4) % 2 == 0;
            y = groundedCharge() ? groundY - height
                    : clampY(y + (playerY - y - height / 2.0) * 0.10, groundY);
            if (--warningTicks <= 0) {
                phase = Phase.DASH;
                dashTicks = (groundedCharge() ? 22 : 15) + stage * 2;
                setMove(dashMoveName + " // EXECUTE", dashTicks + 18);
            }
            return true;
        }
        if (phase == Phase.DASH) {
            if (groundedCharge() && dashTicks <= 0) {
                phase = Phase.RECOVER;
                return true;
            }
            x += dashDir * (20 + stage * 3);
            if (--dashTicks <= 0 || x < targetX - 760 || x > targetX + 140) {
                if (groundedCharge()) dashTicks = 0;
                else phase = Phase.RECOVER;
            }
            return true;
        }
        if (phase == Phase.RECOVER) {
            double distance = targetX - x;
            x += Math.copySign(Math.min(Math.abs(distance), 12 + stage * 2), distance);
            if (Math.abs(targetX - x) < 1) {
                x = targetX;
                phase = Phase.IDLE;
                flashing = false;
                actionCooldown = 48;
                setMove("REBOOTING", 32);
            }
            return true;
        }
        return false;
    }

    private void aimedVolley(List<Projectile> bullets, int count, double spread,
                             double speed, boolean critical) {
        ProjectileBudget.emit(bullets, count, () -> {
            List<Projectile> accepted = new ArrayList<>();
            double cx = x + width / 2.0;
            double cy = y + height / 2.0;
            double base = Math.atan2(playerY + 15 - cy, playerX - cx);
            for (int i = 0; i < count; i++) {
                double offset = count == 1 ? 0 : (i / (double) (count - 1) - 0.5) * spread;
                double angle = base + offset;
                ProjectileType type = critical && (i % 2 == 0) ? ProjectileType.CRITICAL : ProjectileType.ENEMY;
                accepted.add(new Projectile(cx, cy,
                        Math.cos(angle) * speed, Math.sin(angle) * speed, type));
            }
            return accepted;
        });
    }

    private void radialBurst(List<Projectile> bullets, int count, double speed, boolean critical) {
        ProjectileBudget.emit(bullets, count, () -> {
            List<Projectile> accepted = new ArrayList<>();
            double cx = x + width / 2.0;
            double cy = y + height / 2.0;
            double rotation = combatTick * 0.035;
            for (int i = 0; i < count; i++) {
                double angle = rotation + Math.PI * 2 * i / count;
                ProjectileType type = critical && (i % 3 == 0) ? ProjectileType.CRITICAL : ProjectileType.ENEMY;
                accepted.add(new Projectile(cx, cy,
                        Math.cos(angle) * speed, Math.sin(angle) * speed, type));
            }
            return accepted;
        });
    }

    private void blueprintGrid(List<Projectile> bullets, int groundY, int stage) {
        ProjectileBudget.emit(bullets, 3 + stage, () -> {
            List<Projectile> accepted = new ArrayList<>();
            int lanes = 3 + stage;
            double speed = 6.0 + stage * 0.65;
            for (int i = 0; i < lanes; i++) {
                double laneY = 34 + (groundY - 68) * (i + 1) / (double) (lanes + 1);
                ProjectileType type = stage == 3 && (i & 1) == 0
                        ? ProjectileType.CRITICAL : ProjectileType.ENEMY;
                accepted.add(new Projectile(x + 10 + (i % 2) * 28, laneY, -speed, 0, type));
            }
            return accepted;
        });
    }

    private void panicLanes(List<Projectile> bullets, int groundY, int stage) {
        ProjectileBudget.emit(bullets, 4 + stage, () -> {
            List<Projectile> accepted = new ArrayList<>();
            int lanes = 4 + stage;
            for (int i = 0; i < lanes; i++) {
                double laneY = 28 + (groundY - 56) * i / (double) Math.max(1, lanes - 1);
                double vy = ((i & 1) == 0 ? 0.45 : -0.45) * stage;
                ProjectileType type = (i + patternIndex) % 3 == 0
                        ? ProjectileType.CRITICAL : ProjectileType.ENEMY;
                accepted.add(new Projectile(x + width / 2.0, laneY,
                        -(7.0 + stage * 0.8), vy, type));
            }
            return accepted;
        });
    }

    private void spiralShot(List<Projectile> bullets, double speed, boolean critical) {
        ProjectileBudget.emit(bullets, 1, () -> {
            List<Projectile> accepted = new ArrayList<>();
            double angle = combatTick * 0.23 + patternIndex * 0.8;
            accepted.add(new Projectile(x + width / 2.0, y + height / 2.0,
                    Math.cos(angle) * speed, Math.sin(angle) * speed,
                    critical ? ProjectileType.CRITICAL : ProjectileType.ENEMY));
            return accepted;
        });
    }

    private double clampY(double value, int groundY) {
        return Math.max(20, Math.min(value, groundY - height));
    }

    private boolean groundedCharge() {
        return archetype == Archetype.KERNEL_PANIC || archetype == Archetype.SINGULARITY;
    }

    private void markMove(String id, String displayName, int ticks) {
        movesSeen.add(id);
        setMove(displayName, ticks);
    }

    private void setMove(String displayName, int ticks) {
        moveName = displayName;
        moveLabelTicks = Math.max(1, ticks);
    }

    public void takeDamage(int amount) {
        damage(amount);
    }

    /** Confirmed damage after the external GC exposure bonus, capped to remaining health. */
    public int damage(int amount) {
        return damage(amount, false);
    }

    /** Support fire deals the same confirmed damage but produces a quieter local reaction. */
    public int damage(int amount, boolean supportFire) {
        if (!active || hp <= 0 || amount <= 0) return 0;
        long amplified = isVulnerable() ? ((long) amount * 3 + 1) / 2 : amount;
        if (chapterEncounter != null) chapterEncounter.splashParts(amount);
        return confirmDamage((int) Math.min(Integer.MAX_VALUE, amplified), supportFire);
    }

    private int confirmDamage(int amount, boolean supportFire) {
        int accepted = Math.min(hp, amount);
        hp -= accepted;
        highestStage = getCombatStage();
        hitFlashTicks = Math.max(hitFlashTicks, supportFire ? 2 : 6);
        hitRecoilStrength = Math.max(hitFlashTicks > 2 ? hitRecoilStrength : 0, supportFire ? .7 : 3);
        damageSequence++;
        return accepted;
    }

    public void openVulnerability(int ticks) {
        if (!active || hp <= 0 || ticks <= 0) return;
        vulnerabilityTicks = Math.max(vulnerabilityTicks, Math.min(MAX_VULNERABILITY_TICKS, ticks));
    }

    /** Removing an Architect support interrupts its attacks for the same exposed-core window. */
    public void interruptBlueprint(int ticks) {
        if (chapterEncounter != null) {
            // Route consoles do not replace shooting the actual boss components.
            return;
        }
        if (archetype != Archetype.ARCHITECT || !active || hp <= 0 || ticks <= 0) return;
        openVulnerability(ticks);
        blueprintDisruptionTicks = Math.max(blueprintDisruptionTicks, Math.min(MAX_VULNERABILITY_TICKS, ticks));
        phase = Phase.IDLE;
        warningTicks = dashTicks = 0;
        flashing = false;
        setMove("SUPPORTS OFFLINE // CORE EXPOSED", blueprintDisruptionTicks);
    }

    /** An armed fault node stops the actual charge and opens a bounded damage window. */
    public void interruptKernel(int ticks) {
        if (chapterEncounter != null) {
            // Route consoles do not replace shooting the actual boss components.
            return;
        }
        if (archetype != Archetype.KERNEL_PANIC || !active || hp <= 0 || ticks <= 0) return;
        openVulnerability(ticks);
        kernelDisruptionTicks = Math.max(kernelDisruptionTicks, Math.min(MAX_VULNERABILITY_TICKS, ticks));
        kernelRebootTicks = kernelLaneWarningTicks = warningTicks = dashTicks = 0;
        phase = Phase.RECOVER;
        flashing = false;
        setMove("FAULT TRIPPED // CORE EXPOSED", kernelDisruptionTicks);
    }

    public void interruptSingularity(int ticks) {
        if (chapterEncounter != null) {
            // Route consoles do not replace shooting the actual boss components.
            return;
        }
        if (archetype != Archetype.SINGULARITY || !active || hp <= 0 || ticks <= 0) return;
        openVulnerability(ticks);
        singularityDisruptionTicks = Math.max(singularityDisruptionTicks, Math.min(MAX_VULNERABILITY_TICKS, ticks));
        singularityRebootTicks = singularityWarningTicks = warningTicks = dashTicks = 0;
        singularityShots = List.of(); singularityMemory.reset(); singularityPlayerFired = false;
        phase = Phase.RECOVER; flashing = false;
        setMove("ANCHORS LINKED // CORE EXPOSED", singularityDisruptionTicks);
    }

    /** A respawn or route reset closes the external window without altering the encounter. */
    public void clearVulnerability() {
        vulnerabilityTicks = 0;
        if (chapterEncounter != null) { chapterEncounter.resetTransient(); return; }
        if (blueprintDisruptionTicks > 0) setMove(archetype.idleMove, 0);
        blueprintDisruptionTicks = 0;
        if (archetype == Archetype.KERNEL_PANIC) {
            kernelDisruptionTicks = kernelLaneWarningTicks = warningTicks = dashTicks = 0;
            kernelRebootTicks = 90;
            phase = Phase.RECOVER;
            setMove("RING-0 RECONFIGURING // STAND CLEAR", kernelRebootTicks);
        }
        if (archetype == Archetype.SINGULARITY) {
            singularityDisruptionTicks = singularityWarningTicks = warningTicks = dashTicks = 0;
            singularityRebootTicks = 90; singularityShots = List.of(); singularityMemory.reset();
            singularityPlayerFired = false; phase = Phase.RECOVER;
            setMove("PATTERN RESET // STAND CLEAR", singularityRebootTicks);
        }
    }

    /** Return blocks can heal a living boss, but cannot revive it or roll an unlocked phase back. */
    public int heal(int amount) {
        if (!active || hp <= 0 || amount <= 0) return 0;
        int accepted = (int) Math.min((long) maxHp - hp, amount);
        hp += accepted;
        return accepted;
    }

    public void draw(Graphics2D g) {
        draw(g, true);
    }

    public void draw(Graphics2D g, boolean impactFlashes) {
        if (!active) return;
        if (chapterEncounter != null) {
            com.bigphil.mergehell.render.ChapterActorRenderer.boss(g, this, getCombatTick() * .016,
                    impactFlashes, false);
            return;
        }
        Paint oldPaint = g.getPaint();
        Stroke oldStroke = g.getStroke();
        Composite oldComposite = g.getComposite();
        Font oldFont = g.getFont();

        g.setColor(new Color(0, 0, 0, 95));
        g.fillRoundRect((int) x + 7, (int) y + 9, width, height, 24, 24);
        java.awt.geom.AffineTransform bodyTransform = g.getTransform();
        if (getHitFlashTicks() > 0)
            g.translate(Math.sin((7 - getHitFlashTicks()) * Math.PI / 7) * hitRecoilStrength, 0);
        switch (archetype) {
            case MEMORY_LEAK -> drawMemoryLeak(g);
            case ARCHITECT -> drawArchitect(g);
            case KERNEL_PANIC -> drawKernelPanic(g);
            case SINGULARITY -> drawSingularity(g);
            case GENERIC -> drawGeneric(g);
        }

        if (impactFlashes && getHitFlashTicks() > 0) {
            g.setColor(new Color(255, 242, 213, getHitFlashTicks() * 20));
            g.setStroke(new BasicStroke(2));
            g.drawRoundRect((int) x + 12, (int) y + 16, width - 24, height - 32, 14, 14);
        }
        g.setTransform(bodyTransform);

        drawIdentity(g);
        drawTelegraph(g);

        g.setPaint(oldPaint);
        g.setStroke(oldStroke);
        g.setComposite(oldComposite);
        g.setFont(GameText.font(oldFont));
    }

    private void drawMemoryLeak(Graphics2D g) {
        Color body = stageColor(archetype.primary, archetype.accent);
        g.setPaint(new GradientPaint((float) x, (float) y, body.brighter(),
                (float) x, (float) y + height, new Color(45, 65, 40)));
        g.fillRoundRect((int) x, (int) y, width, height, 42, 42);
        g.setColor(archetype.accent);
        g.setStroke(new BasicStroke(3));
        g.drawRoundRect((int) x, (int) y, width, height, 42, 42);

        int bubbles = 3 + getCombatStage();
        for (int i = 0; i < bubbles; i++) {
            double angle = combatTick * 0.035 + Math.PI * 2 * i / bubbles;
            int radius = 70 + i * 5;
            int bx = (int) (x + width / 2.0 + Math.cos(angle) * radius);
            int by = (int) (y + height / 2.0 + Math.sin(angle) * radius * 0.55);
            g.setColor(new Color(archetype.accent.getRed(), archetype.accent.getGreen(),
                    archetype.accent.getBlue(), 125));
            g.fillOval(bx - 7, by - 7, 14, 14);
        }
        g.setFont(GameText.font(new Font("JetBrains Mono", Font.BOLD, 23)));
        g.setColor(Color.BLACK);
        GameText.draw(g, "HEAP", (int) x + 28, (int) y + 77);
        g.setColor(Color.WHITE);
        GameText.draw(g, "HEAP", (int) x + 26, (int) y + 75);
        g.setFont(GameText.font(new Font("JetBrains Mono", Font.PLAIN, 10)));
        g.setColor(archetype.accent);
        GameText.draw(g, "retained=" + (100 - hp * 100 / maxHp) + "%", (int) x + 16, (int) y + 106);
        for (int i = 0; i < getCombatStage(); i++) {
            int leakY = (int) y + height + (combatTick * 2 + i * 17) % 35;
            g.fillRect((int) x + 24 + i * 31, leakY, 7, 7);
        }
    }

    private void drawArchitect(Graphics2D g) {
        Color body = stageColor(new Color(10, 46, 58), new Color(62, 82, 92));
        g.setColor(body);
        g.fillRect((int) x, (int) y, width, height);
        g.setColor(archetype.primary);
        g.setStroke(new BasicStroke(2));
        for (int gx = 15; gx < width; gx += 20) g.drawLine((int) x + gx, (int) y, (int) x + gx, (int) y + height);
        for (int gy = 15; gy < height; gy += 20) g.drawLine((int) x, (int) y + gy, (int) x + width, (int) y + gy);
        g.setColor(archetype.accent);
        g.setStroke(new BasicStroke(4));
        g.drawRect((int) x, (int) y, width, height);
        g.drawLine((int) x + 15, (int) y + 116, (int) x + 60, (int) y + 35);
        g.drawLine((int) x + 60, (int) y + 35, (int) x + 106, (int) y + 116);
        g.drawLine((int) x + 15, (int) y + 116, (int) x + 106, (int) y + 116);
        g.setFont(GameText.font(new Font("JetBrains Mono", Font.BOLD, 17)));
        g.setColor(Color.WHITE);
        GameText.draw(g, "ARCH", (int) x + 37, (int) y + 88);
        for (int i = 0; i < 4; i++) {
            g.fillOval((int) x + 12 + i * 31, (int) y + 124, 7, 7);
        }
    }

    private void drawKernelPanic(Graphics2D g) {
        int glitch = (int) Math.round(Math.sin(combatTick * 0.71) * (2 + getCombatStage() * 2));
        g.setColor(new Color(22, 18, 29));
        g.fillRect((int) x, (int) y, width, height);
        g.setColor(archetype.accent);
        g.fillRect((int) x + glitch, (int) y + 18, width - 12, 22);
        g.setColor(archetype.primary);
        g.fillRect((int) x - glitch, (int) y + 57, width + 8, 16);
        g.fillRect((int) x + glitch * 2, (int) y + 112, width - 18, 25);
        g.setColor(Color.WHITE);
        g.setFont(GameText.font(new Font("JetBrains Mono", Font.BOLD, 20)));
        GameText.draw(g, "PANIC", (int) x + 28 + glitch, (int) y + 101);
        g.setStroke(new BasicStroke(3));
        g.setColor(flashing ? Color.WHITE : archetype.primary);
        g.drawRect((int) x, (int) y, width, height);
    }

    private void drawSingularity(Graphics2D g) {
        int cx = (int) x + width / 2;
        int cy = (int) y + height / 2;
        g.setColor(new Color(15, 12, 26));
        g.fillOval((int) x, (int) y + 14, width, width);
        for (int i = 0; i < 4 + getCombatStage(); i++) {
            int radius = 27 + i * 10;
            int start = (combatTick * (2 + i) + i * 47) % 360;
            g.setColor(i % 2 == 0 ? archetype.primary : archetype.accent);
            g.setStroke(new BasicStroke(i == 0 ? 4 : 2));
            g.drawArc(cx - radius, cy - radius, radius * 2, radius * 2, start, 170);
        }
        g.setPaint(new RadialGradientPaint(cx, cy, 27,
                new float[]{0f, 0.55f, 1f},
                new Color[]{Color.BLACK, new Color(70, 20, 95), archetype.primary}));
        g.fillOval(cx - 27, cy - 27, 54, 54);
        g.setFont(GameText.font(new Font("JetBrains Mono", Font.BOLD, 12)));
        g.setColor(Color.WHITE);
        GameText.draw(g, "∞ MERGE", cx - 25, cy + 5);
    }

    private void drawGeneric(Graphics2D g) {
        Color body = stageColor(archetype.primary, archetype.accent);
        g.setPaint(new GradientPaint((float) x, (float) y, body.brighter(),
                (float) x, (float) y + height, body.darker()));
        g.fillRect((int) x, (int) y, width, height);
        g.setColor(archetype.accent);
        g.setStroke(new BasicStroke(3));
        g.drawRect((int) x, (int) y, width, height);
        g.setFont(GameText.font(new Font("SansSerif", Font.BOLD, 52)));
        g.setColor(Color.WHITE);
        FontMetrics fm = g.getFontMetrics();
        GameText.draw(g, symbol, (int) x + (width - fm.stringWidth(GameText.text(symbol))) / 2, (int) y + 92);
    }

    private void drawIdentity(Graphics2D g) {
        g.setFont(GameText.font(new Font("JetBrains Mono", Font.BOLD, 11)));
        g.setColor(Color.BLACK);
        GameText.draw(g, "P" + getCombatStage() + " // " + getStageName(), (int) x + 1, (int) y - 9);
        g.setColor(archetype.accent);
        GameText.draw(g, "P" + getCombatStage() + " // " + getStageName(), (int) x, (int) y - 10);
        g.setFont(GameText.font(new Font("JetBrains Mono", Font.BOLD, 9)));
        g.setColor(new Color(255, 255, 255, 210));
        String shortMove = moveName.length() > 23 ? moveName.substring(0, 23) : moveName;
        GameText.draw(g, shortMove, (int) x + 5, (int) y + height - 8);
    }

    /** Existing attack geometry stays available when a world-specific body renderer is used. */
    public void drawAttackTelegraph(Graphics2D target) {
        if (!active) return;
        if (chapterEncounter != null) {
            Graphics2D g = (Graphics2D) target.create();
            try { drawChapterTelegraphs(g); } finally { g.dispose(); }
            return;
        }
        Graphics2D g = (Graphics2D) target.create();
        try { drawTelegraph(g); }
        finally { g.dispose(); }
    }

    private void drawTelegraph(Graphics2D g) {
        if (groundedCharge()) {
            drawKernelTelegraph(g);
            if (archetype == Archetype.SINGULARITY && singularityWarningTicks > 0) {
                g.setColor(new Color(245, 168, 118, 190));
                g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{8, 6}, 0));
                for (var shot : singularityShots) {
                    double scale = 840 / Math.hypot(shot.vx(), shot.vy());
                    g.draw(new java.awt.geom.Line2D.Double(shot.x(), shot.y(),
                            shot.x() + shot.vx() * scale, shot.y() + shot.vy() * scale));
                }
            }
            return;
        }
        if (phase == Phase.DASH_WARN) {
            g.setColor(new Color(255, 40, 40, flashing ? 210 : 95));
            g.setStroke(new BasicStroke(3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
                    0, new float[]{10, 7}, 0));
            int startX = (int) (x + width / 2.0);
            int startY = (int) (y + height / 2.0);
            g.drawLine(startX, startY, (int) (startX + dashDir * 760), startY);
        }
        if (moveName.contains("GRID") || moveName.contains("LANES")) {
            g.setColor(new Color(archetype.primary.getRed(), archetype.primary.getGreen(),
                    archetype.primary.getBlue(), 55));
            g.setStroke(new BasicStroke(1));
            for (int i = 1; i <= 4; i++) {
                int lineY = i * lastGroundY / 5;
                g.drawLine((int) targetX - 800, lineY, (int) targetX + width, lineY);
            }
        }
        if (moveName.contains("STORM") || moveName.contains("HORIZON")) {
            g.setColor(new Color(archetype.accent.getRed(), archetype.accent.getGreen(),
                    archetype.accent.getBlue(), 75));
            int pulse = 35 + combatTick % 45;
            g.drawOval((int) x + width / 2 - pulse, (int) y + height / 2 - pulse,
                    pulse * 2, pulse * 2);
        }
    }

    /** Uses the very same lane origins/velocities as panicLanes, even with flashes disabled. */
    private void drawKernelTelegraph(Graphics2D g) {
        if (phase == Phase.DASH_WARN) {
            double destination = x;
            int stage = getCombatStage();
            for (int step = 0; step < 22 + stage * 2; step++) {
                destination += dashDir * (20 + stage * 3);
                if (destination < targetX - 760 || destination > targetX + 140) break;
            }
            int end = (int) destination;
            int left = (int) Math.min(x, end), right = (int) Math.max(x + width, end + width);
            g.setColor(new Color(238, 150, 91, 35));
            g.fillRect(left, lastGroundY - height, right - left, height);
            g.setColor(new Color(248, 173, 101, 210));
            g.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{10, 7}, 0));
            g.drawRect(left, lastGroundY - height, right - left, height);
            int cy = lastGroundY - height / 2;
            for (int px = left + 30; px < right - 20; px += 65) {
                int d = (int) dashDir;
                g.drawLine(px - d * 10, cy - 7, px + d * 4, cy);
                g.drawLine(px - d * 10, cy + 7, px + d * 4, cy);
            }
        }
        if (kernelLaneWarningTicks > 0) {
            int stage = getCombatStage(), lanes = 4 + stage;
            double speed = 7.0 + stage * .8;
            g.setColor(new Color(246, 167, 95, 155));
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{7, 5}, 0));
            for (int i = 0; i < lanes; i++) {
                double originY = 28 + (lastGroundY - 56) * i / (double) (lanes - 1);
                double vy = ((i & 1) == 0 ? .45 : -.45) * stage;
                double length = 820;
                g.drawLine((int) (x + width / 2.0), (int) originY,
                        (int) (x + width / 2.0 - length), (int) (originY + length / speed * vy));
            }
        }
    }

    private Color stageColor(Color calm, Color danger) {
        return switch (getCombatStage()) {
            case 1 -> calm;
            case 2 -> blend(calm, danger, 0.45);
            default -> danger;
        };
    }

    private static Color blend(Color a, Color b, double ratio) {
        double keep = 1.0 - ratio;
        return new Color(
                (int) (a.getRed() * keep + b.getRed() * ratio),
                (int) (a.getGreen() * keep + b.getGreen() * ratio),
                (int) (a.getBlue() * keep + b.getBlue() * ratio));
    }


    /** Immutable simulation geometry, safe to retain in a published frame. */
    public record Bounds(double x, double y, double width, double height) {
        public Rectangle rectangle() {
            int left = (int) Math.floor(x), top = (int) Math.floor(y);
            return new Rectangle(left, top, (int) Math.ceil(x + width) - left,
                    (int) Math.ceil(y + height) - top);
        }
        public boolean intersects(Rectangle other) {
            return other.getMaxX() > x && other.getX() < x + width
                    && other.getMaxY() > y && other.getY() < y + height;
        }
    }
    public record PartView(String id, Bounds bounds, int hp, int maxHp,
                           boolean targetable, boolean weak, boolean destroyed) { }
    public record AttackTelegraph(String id, Bounds bounds, int warningTicks,
                                  boolean active, int damage, int fullWarningTicks) { }

    public boolean hasMultipartEncounter() { return chapterEncounter != null; }
    public String getEncounterAction() { return chapterEncounter == null ? phase.name() : chapterEncounter.action().name(); }
    public String getEncounterHint() { return chapterEncounter == null ? "" : chapterEncounter.hintKey(); }
    public int getHeat() { return chapterEncounter == null ? 0 : chapterEncounter.heat(); }
    public double getRebuildProgress() { return chapterEncounter == null ? 0 : chapterEncounter.rebuildProgress(); }
    public List<PartView> getParts() {
        if (hp <= 0) return List.of();
        return chapterEncounter == null ? List.of(new PartView("core", new Bounds(x, y, width, height),
                hp, maxHp, true, isVulnerable(), false)) : chapterEncounter.parts(hp, maxHp);
    }
    public List<AttackTelegraph> getAttackTelegraphs() {
        return !active || hp <= 0 || chapterEncounter == null ? List.of() : chapterEncounter.telegraphs();
    }
    public List<AttackTelegraph> getActiveHazards() {
        return getAttackTelegraphs().stream().filter(t -> t.active() && t.damage() > 0).toList();
    }
    public List<Bounds> getContactBounds() {
        if (!isContactDangerous()) return List.of();
        return chapterEncounter == null ? List.of(new Bounds(x, y, width, height)) : chapterEncounter.contacts();
    }
    public boolean canHit(Rectangle hit) {
        return active && hp > 0 && (chapterEncounter == null ? getBounds().intersects(hit)
                : chapterEncounter.canHit(hit, hp, maxHp));
    }
    public int damageAt(Rectangle hit, int amount, boolean supportFire) {
        if (!active || hp <= 0 || amount <= 0 || hit == null) return 0;
        if (chapterEncounter == null) return getBounds().intersects(hit) ? damage(amount, supportFire) : 0;
        ChapterBossEncounter.Damage result = chapterEncounter.hit(hit, amount, hp, maxHp);
        if (result.coreDamage() == 0 && result.partDamage() == 0) return 0;
        return confirmDamage(result.coreDamage(), supportFire);
    }
    public int damageAt(Rectangle hit, int amount) { return damageAt(hit, amount, false); }

    private void drawChapterTelegraphs(Graphics2D g) {
        for (AttackTelegraph tell : getAttackTelegraphs()) {
            Rectangle r = tell.bounds().rectangle();
            boolean activeHazard = tell.active() && tell.damage() > 0;
            g.setColor(activeHazard ? new Color(255, 87, 54, 110) : new Color(255, 192, 86, 35));
            g.fill(r);
            g.setColor(activeHazard ? new Color(255, 144, 82, 235) : new Color(255, 204, 119, 210));
            g.setStroke(new BasicStroke(activeHazard ? 3 : 2, BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_MITER, 10, activeHazard ? null : new float[]{8, 6}, 0));
            g.draw(r);
        }
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(new Color(246, 183, 131, 175));
        for (PredictedShot shot : chapterEncounter.predictedShots()) {
            double length = 620 / Math.max(1, Math.hypot(shot.vx(), shot.vy()));
            g.draw(new java.awt.geom.Line2D.Double(shot.x(), shot.y(),
                    shot.x() + shot.vx() * length, shot.y() + shot.vy() * length));
        }
    }

    public Rectangle getBounds() {
        Rectangle bounds = new Rectangle((int) x, (int) y, width, height);
        if (chapterEncounter != null) for (PartView part : getParts())
            if (part.targetable()) bounds = bounds.union(part.bounds().rectangle());
        return bounds;
    }
}
