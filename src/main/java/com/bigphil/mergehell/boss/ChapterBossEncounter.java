package com.bigphil.mergehell.boss;

import com.bigphil.mergehell.engine.ProjectileBudget;
import com.bigphil.mergehell.model.Boss;
import com.bigphil.mergehell.model.EntityType;
import com.bigphil.mergehell.model.ObstacleManager;
import com.bigphil.mergehell.model.Projectile;
import com.bigphil.mergehell.model.ProjectileType;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Three authored encounters. Part poses, warnings, damage volumes and shot origins all come
 * from this simulation; renderers never invent collision geometry or advance an animation.
 * Each encounter owns exactly two appendage health pools and at most eight attack volumes.
 */
public final class ChapterBossEncounter {
    public enum Action {
        ARRIVAL, READY, RECONFIGURE, EXPOSED, REBUILD,
        LEFT_STAMP, RIGHT_STAMP, CROSSBEAM, GIRDER_FALL, LEFT_SWEEP, RIGHT_SWEEP, GANTRY_LOCK,
        DRILL_CHARGE, MAGMA_MORTAR, VENT_PURGE, COOLING, OVERHEATED,
        LEFT_TENDRIL, RIGHT_TENDRIL, SPORE_FAN, HATCH, HEART_CRAWL, HEART_PULSE, ROOT_SURGE
    }
    private enum Step { REST, WARNING, ACTIVE }
    public record Damage(int coreDamage, int partDamage) { }

    private final int chapter, width, height, appendageMax;
    private final double homeX;
    private final int[] appendageHp = new int[2];
    private final Set<String> movesSeen = new LinkedHashSet<>();
    private double x, y, chargeEndX, direction = -1;
    private int groundY = 480, stage = 1, tick, remaining = 60, warningDuration;
    private int exposure, heat, coolingTicks, restoreCount;
    private boolean arrived, regrowAfterRecovery;
    private Action action = Action.ARRIVAL;
    private Step step = Step.REST;
    private int pattern;
    private com.bigphil.mergehell.progression.GameDifficulty combatDifficulty;
    public void configureCombat(com.bigphil.mergehell.progression.GameDifficulty difficulty) {
        combatDifficulty=java.util.Objects.requireNonNull(difficulty);
    }
    private List<Boss.Bounds> volumes = List.of();
    private List<Boss.PredictedShot> shots = List.of();
    private Boss.Bounds siegeTrail;
    private int trailWarning, trailActive;
    private double chargeStartX;
    private static final int TRAIL_WARNING = 36, TRAIL_DURATION = 90;

    public ChapterBossEncounter(int chapter, int maxHp, double homeX, double spawnX) {
        if (chapter < 2 || chapter > 4) throw new IllegalArgumentException("Multipart chapter must be 2–4");
        this.chapter = chapter;
        this.homeX = homeX;
        this.x = spawnX;
        width = chapter == 2 ? 280 : chapter == 3 ? 240 : 270;
        height = chapter == 2 ? 170 : chapter == 3 ? 160 : 220;
        appendageMax = Math.max(14, maxHp / (chapter == 3 ? 7 : 10));
        appendageHp[0] = appendageHp[1] = appendageMax;
        y = 100;
    }

    public int width() { return width; }
    public int height() { return height; }
    public double x() { return x; }
    public double y() { return y; }
    public int tick() { return tick; }
    public Action action() { return action; }
    public int heat() { return heat; }
    public int exposureTicks() { return coreContracting() ? 0 : exposure; }
    public int recoveryTicks() { return action == Action.RECONFIGURE ? remaining : 0; }
    public double rebuildProgress() { return action == Action.REBUILD ? 1 - remaining / (chapter == 2 ? 90.0 : 110.0) : 0; }
    public int warningTicks() { return step == Step.WARNING ? remaining : 0; }
    public double direction() { return direction; }
    public boolean isDashing() { return action == Action.DRILL_CHARGE && step == Step.ACTIVE; }
    public boolean isContactDangerous() {
        return arrived && action != Action.RECONFIGURE && action != Action.REBUILD
                && action != Action.EXPOSED && action != Action.OVERHEATED && step != Step.WARNING;
    }
    public List<Boss.PredictedShot> predictedShots() { return shots; }
    public Set<String> movesSeen() { return Set.copyOf(movesSeen); }
    public String moveKey() { return "chapter.boss.move." + action.name().toLowerCase(java.util.Locale.ROOT); }
    public String hintKey() {
        if (coreContracting()) return "chapter.boss.core.transition";
        return switch (chapter) {
            case 2 -> exposure > 0 ? "chapter.boss.architect.open"
                    : action == Action.GANTRY_LOCK ? "chapter.boss.architect.corridor"
                    : action == Action.LEFT_SWEEP || action == Action.RIGHT_SWEEP ? "chapter.boss.architect.sweep"
                    : "chapter.boss.architect.arms";
            case 3 -> action == Action.OVERHEATED ? "chapter.boss.siege.open"
                    : action == Action.VENT_PURGE ? "chapter.boss.siege.purge"
                    : siegeTrail != null ? "chapter.boss.siege.trail"
                    : appendageHp[0] == 0 ? "chapter.boss.siege.crippled"
                    : coolingTicks > 0 || appendageHp[0] == 0 ? "chapter.boss.siege.vent" : "chapter.boss.siege.plate";
            default -> action == Action.HEART_PULSE ? "chapter.boss.rootheart.pulse"
                    : action == Action.ROOT_SURGE ? "chapter.boss.rootheart.roots"
                    : exposure > 0 || stage == 3 ? "chapter.boss.rootheart.open" : "chapter.boss.rootheart.organs";
        };
    }

    public void preview(double progress, int groundY) {
        if (arrived) return;
        this.groundY = groundY;
        double t = Math.max(0, Math.min(1, progress));
        t = t * t * (3 - 2 * t);
        x = homeX + 350 * (1 - t);
        y = 100 + (baseY() - 100) * t;
    }

    /** Called once per simulation step, never by the renderer. */
    public void update(int groundY, double playerX, double playerY, int stage,
                       ObstacleManager enemies, List<Projectile> destination) {
        this.groundY = groundY;
        if (!arrived) {
            x = Math.max(homeX, x - 7);
            y += Math.copySign(Math.min(Math.abs(baseY() - y), 5), baseY() - y);
            if (x == homeX && Math.abs(y - baseY()) < .1) {
                arrived = true;
                rest(Action.READY, 60);
            }
            return;
        }
        tick++;
        if (stage != this.stage) {
            this.stage = stage;
            pattern = 0;
            // A phase transition cannot teleport the body, retaliate, or preserve an active strike.
            clearAttack();
            clearSiegeTrail();
            rest(Action.RECONFIGURE, 90);
            if (chapter == 4 && stage == 3) {
                appendageHp[0] = appendageHp[1] = 0;
                exposure = 0; regrowAfterRecovery = false;
            }
            return;
        }
        updateSiegeTrail();
        if (action == Action.RECONFIGURE || action == Action.REBUILD) {
            if (--remaining <= 0) {
                if (action == Action.RECONFIGURE && regrowAfterRecovery) {
                    regrowAfterRecovery = false;
                    rest(Action.REBUILD, chapter == 2 ? 90 : 110);
                    return;
                }
                if (action == Action.REBUILD) {
                    appendageHp[0] = appendageHp[1] = appendageMax;
                    restoreCount++;
                }
                if (action == Action.RECONFIGURE) {
                    // Preserve an earned part-break window, but do not stack another
                    // idle delay onto the already harmless phase transition.
                    if (exposure > 0) rest(chapter == 3 ? Action.OVERHEATED : Action.EXPOSED, exposure);
                    else if (chapter == 2) chooseArchitect(playerX, playerY);
                    else if (chapter == 3) chooseSiege(playerX, playerY);
                    else chooseRootheart(playerX, playerY);
                } else rest(Action.READY, 50);
            }
            return;
        }
        if (exposure > 0) {
            if (--exposure == 0) {
                if (chapter == 3) {
                    heat = 0;
                    coolingTicks = 0;
                    rest(Action.READY, 65);
                } else if (chapter != 4 || stage < 3) {
                    rest(Action.REBUILD, chapter == 2 ? 90 : 110);
                } else rest(Action.READY, 55);
            }
            return;
        }
        if (coolingTicks > 0 && --coolingTicks == 0) heat = Math.max(0, heat - 25);
        if (step == Step.WARNING) {
            if (--remaining == 0) release(enemies, destination);
            return;
        }
        if (step == Step.ACTIVE) {
            if (action == Action.DRILL_CHARGE || action == Action.HEART_CRAWL) {
                double speed = action == Action.DRILL_CHARGE ? chargeSpeed() : 3.5 + stage;
                x += Math.copySign(Math.min(speed, Math.abs(chargeEndX - x)), chargeEndX - x);
            }
            if (--remaining <= 0) finishAttack();
            return;
        }
        if (--remaining > 0) return;
        switch (chapter) {
            case 2 -> chooseArchitect(playerX, playerY);
            case 3 -> chooseSiege(playerX, playerY);
            case 4 -> chooseRootheart(playerX, playerY);
            default -> throw new IllegalStateException();
        }
    }

    private double baseY() { return groundY - height - (chapter == 2 ? 12 : 0); }

    private void chooseArchitect(double px, double py) {
        int cycle = pattern++;
        Action next = stage == 1 ? switch (cycle % 4) {
            case 0 -> Action.LEFT_STAMP;
            case 1 -> Action.CROSSBEAM;
            case 2 -> Action.RIGHT_STAMP;
            default -> Action.GIRDER_FALL;
        } : stage == 2 ? switch (cycle % 6) {
            case 0 -> Action.LEFT_SWEEP;
            case 1 -> Action.RIGHT_STAMP;
            case 2 -> Action.GIRDER_FALL;
            case 3 -> Action.RIGHT_SWEEP;
            case 4 -> Action.LEFT_STAMP;
            default -> Action.CROSSBEAM;
        } : switch (cycle % 5) {
            case 0 -> Action.GANTRY_LOCK;
            case 1 -> Action.LEFT_SWEEP;
            case 2 -> Action.RIGHT_STAMP;
            case 3 -> Action.RIGHT_SWEEP;
            default -> Action.GIRDER_FALL;
        };
        if ((ownsLeftArm(next) && appendageHp[0] == 0)
                || (ownsRightArm(next) && appendageHp[1] == 0)) {
            rest(Action.READY, 45); return;
        }
        if (next == Action.GANTRY_LOCK) {
            // Each surviving arm controls one side. The 144px corridor is locked once,
            // clear of the boss body, and reachable even by Warden during the full wind-up.
            double left = homeX - 640, right = homeX + 150;
            double shift = px + 16 < homeX - 330 ? 150 : -150;
            double center = Math.max(left + 110, Math.min(homeX - 95, px + 16 + shift));
            List<Boss.Bounds> closing = new ArrayList<>();
            if (appendageHp[0] > 0) closing.add(new Boss.Bounds(left, 80, center - 72 - left, groundY - 80));
            if (appendageHp[1] > 0) closing.add(new Boss.Bounds(center + 72, 80, right - center - 72, groundY - 80));
            warn(next, 110, closing, List.of());
        } else if (next == Action.LEFT_SWEEP || next == Action.RIGHT_SWEEP) {
            // A suspended arm sweeps at standing chest height: duck or jump, rather than
            // repeating the stamp's lateral dodge. Breaking that arm removes this attack.
            double left = next == Action.LEFT_SWEEP ? homeX - 640 : homeX - 285;
            warn(next, 88, List.of(new Boss.Bounds(left, groundY - 44, 435, 20)), List.of());
        } else if (next == Action.LEFT_STAMP || next == Action.RIGHT_STAMP) {
            double locked = clampArena(px - 28, 100);
            warn(next, 72, List.of(new Boss.Bounds(locked, groundY - 162, 96, 162)), List.of());
        } else if (next == Action.CROSSBEAM) {
            // The two arms own separate beam heights. Removing an arm removes its beam.
            List<Boss.Bounds> beams = new ArrayList<>();
            if (appendageHp[0] > 0) beams.add(new Boss.Bounds(homeX - 640, groundY - 52, 790, 28));
            if (appendageHp[1] > 0) beams.add(new Boss.Bounds(homeX - 640, groundY - 204, 790, 28));
            warn(next, 78, beams, List.of());
        } else {
            double locked = clampArena(px - 34, 82);
            List<Boss.Bounds> strikes = new ArrayList<>();
            strikes.add(new Boss.Bounds(locked, 36, 68, groundY - 36));
            if (stage >= 2) {
                // A second visible column leaves a 112-pixel ground corridor, never a solid wall.
                double adjacent = clampArena(locked + (locked < homeX - 330 ? 190 : -190), 68);
                strikes.add(new Boss.Bounds(adjacent, 36, 68, groundY - 36));
            }
            warn(next, 85, strikes, List.of());
        }
    }

    private void chooseSiege(double px, double py) {
        int beat = pattern++ % (stage == 1 ? 3 : 4);
        if (stage == 3 && beat == 0) {
            // Rear pressure builds with the vent visibly open. Crouching, jumping,
            // or overheating that physical component are three valid responses.
            Boss.Bounds vent = appendageBounds(1);
            double emitter = vent.x() + vent.width() / 2;
            boolean right = direction < 0;
            double start = right ? emitter : Math.max(homeX - 635, emitter - 300);
            double end = right ? Math.min(homeX + 325, emitter + 300) : emitter;
            coolingTicks = 124;
            warn(Action.VENT_PURGE, 92,
                    List.of(new Boss.Bounds(start, groundY - 44, Math.max(30, end - start), 20)), List.of());
        } else if (beat == (stage == 3 ? 2 : 1)) {
            List<Boss.Bounds> landings = new ArrayList<>();

            for (int i = 0; i < 3; i++) {
                double landing = clampArena(px - 26 + (i - 1) * 132, 52);
                landings.add(new Boss.Bounds(landing, groundY - 50, 52, 50));

            }
            warn(Action.MAGMA_MORTAR, 100, landings, List.of());
        } else if (beat == (stage == 1 ? 2 : 3)) {
            coolingTicks = 180;
            rest(Action.COOLING, 180);
        } else {
            direction = px + 16 < x + width / 2.0 ? -1 : 1;
            // Commit only to a bounded run. No homing after the warning begins.
            double range = appendageHp[0] == 0 ? 180 : 330 + stage * 35;
            chargeStartX = x;
            chargeEndX = Math.max(homeX - 540, Math.min(homeX + 70, x + direction * range));
            if (Math.abs(chargeEndX - x) < 90) {
                direction = -direction;
                chargeEndX = Math.max(homeX - 540, Math.min(homeX + 70, x + direction * range));
            }
            double left = Math.min(x, chargeEndX), right = Math.max(x, chargeEndX) + width;
            warn(Action.DRILL_CHARGE, 80,
                    List.of(new Boss.Bounds(left + 12, groundY - 112, right - left - 24, 112)), List.of());
        }
    }

    private double chargeSpeed() { return appendageHp[0] == 0 ? 10 : 14 + stage * 2; }

    private void updateSiegeTrail() {
        if (siegeTrail == null) return;
        if (trailWarning > 0) trailWarning--;
        else if (--trailActive <= 0) clearSiegeTrail();
    }

    private void clearSiegeTrail() { siegeTrail = null; trailWarning = trailActive = 0; }

    private void chooseRootheart(double px, double py) {
        if (stage == 3) {
            chooseExposedHeart(px);
            return;
        }
        Action next = switch (pattern++ % 4) {
            case 0 -> Action.SPORE_FAN;
            case 1 -> Action.LEFT_TENDRIL;
            case 2 -> Action.HATCH;
            default -> Action.RIGHT_TENDRIL;
        };
        if ((next == Action.LEFT_TENDRIL && appendageHp[0] == 0)
                || (next == Action.RIGHT_TENDRIL && appendageHp[1] == 0)) next = Action.SPORE_FAN;
        if (next == Action.HATCH && appendageHp[0] == 0 && appendageHp[1] == 0) next = Action.SPORE_FAN;
        if (next == Action.SPORE_FAN) {
            int count = 7 + stage * 2;
            double cx = x + 135, cy = y + 106;
            double aim = Math.atan2(py + 16 - cy, px + 16 - cx);
            List<Boss.PredictedShot> fan = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                // Leave a deliberately empty spoke at the locked player's previous position.
                if (i == count / 2) continue;
                double angle = aim + (i - count / 2.0) * .13;
                fan.add(new Boss.PredictedShot(cx, cy, Math.cos(angle) * (4 + stage * .4),
                        Math.sin(angle) * (4 + stage * .4)));
            }
            warn(next, 80, List.of(), fan);
        } else if (next == Action.HATCH) {
            List<Boss.Bounds> eggs = new ArrayList<>();
            for (int i = 0; i < 2; i++) if (appendageHp[i] > 0) eggs.add(appendageBounds(i));
            warn(next, 95, eggs, List.of());
        } else {
            // A tentacle marks the player's previous location, then commits to that one column.
            double locked = clampArena(px - 23, 86);
            List<Boss.Bounds> slam = new ArrayList<>();
            slam.add(new Boss.Bounds(locked, groundY - 205, 82, 205));
            if (stage >= 2 && appendageHp[0] > 0 && appendageHp[1] > 0) {
                double second = clampArena(locked + (next == Action.LEFT_TENDRIL ? 186 : -186), 82);
                slam.add(new Boss.Bounds(second, groundY - 105, 82, 105));
            }
            warn(next, 82, slam, List.of());
        }
    }

    private void chooseExposedHeart(double px) {
        int beat = pattern++ % 3;
        if (beat == 0) {
            direction = px + 16 < x + width / 2.0 ? -1 : 1;
            chargeEndX = Math.max(homeX - 490, Math.min(homeX + 70, x + direction * 260));
            // Reposition away from the nearest boundary instead of issuing a zero-length crawl.
            if (Math.abs(chargeEndX - x) < 90) {
                direction = -direction;
                chargeEndX = Math.max(homeX - 490, Math.min(homeX + 70, x + direction * 220));
            }
            double left = Math.min(x, chargeEndX), right = Math.max(x, chargeEndX) + width;
            warn(Action.HEART_CRAWL, 90,
                    List.of(new Boss.Bounds(left + 72, groundY - 160, right - left - 144, 150)), List.of());
        } else if (beat == 1) {
            // The exposed heart fires from visible ports in two horizontal tiers. Ground
            // crouching clears the low tier; jumping blindly meets the upper one.
            List<Boss.PredictedShot> pulse = new ArrayList<>();
            for (int side : new int[]{-1, 1}) for (int rise : new int[]{38, 126})
                pulse.add(new Boss.PredictedShot(x + (side < 0 ? 87 : 169), groundY - rise, side * 4.8, 0));
            warn(Action.HEART_PULSE, 86, List.of(), pulse);
        } else {
            // Its surviving locomotion roots strike the floor, not the severed organs.
            // A short, locked patch asks for a jump or a move out, after the pulse has cleared.
            warn(Action.ROOT_SURGE, 88,
                    List.of(new Boss.Bounds(clampArena(px - 134, 300), groundY - 48, 300, 48)), List.of());
        }
    }

    private static boolean ownsLeftArm(Action action) {
        return action == Action.LEFT_STAMP || action == Action.LEFT_SWEEP;
    }
    private static boolean ownsRightArm(Action action) {
        return action == Action.RIGHT_STAMP || action == Action.RIGHT_SWEEP;
    }

    private double clampArena(double at, double w) {
        return Math.max(homeX - 640, Math.min(homeX + 150 - w, at));
    }

    private void warn(Action action, int ticks, List<Boss.Bounds> volumes, List<Boss.PredictedShot> shots) {
        this.action = action;
        this.step = Step.WARNING;
        remaining = warningDuration = ticks;
        this.volumes = List.copyOf(volumes);
        this.shots = List.copyOf(shots);
        movesSeen.add(action.name());
    }

    private void release(ObstacleManager enemies, List<Projectile> destination) {
        step = Step.ACTIVE;
        remaining = switch (action) {
            case DRILL_CHARGE -> (int) Math.ceil(Math.abs(chargeEndX - x) / chargeSpeed()) + 1;
            case HEART_CRAWL -> (int) Math.ceil(Math.abs(chargeEndX - x) / (3.5 + stage)) + 1;
            case CROSSBEAM, LEFT_SWEEP, RIGHT_SWEEP -> 26;
            case GANTRY_LOCK -> 38;
            case ROOT_SURGE -> 24;
            case VENT_PURGE -> 32;
            case MAGMA_MORTAR -> MagmaMortarProjectile.FLIGHT_TICKS + 2;
            case SPORE_FAN, HEART_PULSE, HATCH -> 1;
            default -> 18;
        };
        if (action == Action.SPORE_FAN || action == Action.HEART_PULSE) {
            List<Boss.PredictedShot> locked = shots;
            ProjectileBudget.emit(destination, locked.size(), () -> locked.stream().map(shot ->
                    new Projectile(shot.x(), shot.y(), shot.vx(), shot.vy(), ProjectileType.ENEMY)).toList());
            shots = List.of();
        } else if (action == Action.HATCH) {
            long living = enemies.getEnemies().stream().filter(e -> !e.isDead() && e.getType().isHostile()).count();
            for (int i = 0; i < 2 && living < 6; i++) if (appendageHp[i] > 0) {
                Boss.Bounds egg = appendageBounds(i);
                enemies.spawnEnemy((int) egg.x(), groundY - 48, hatchlingType(), i == 0 ? 1 : -1);
                living++;
            }
        } else if (action == Action.GIRDER_FALL) {
            // One complete, announced volley. Saturation cannot partially remove its safe gaps.
            List<Boss.Bounds> columns = volumes;
            ProjectileBudget.emit(destination, columns.size(), () -> columns.stream().map(column ->
                    new Projectile(column.x() + column.width() / 2 - 5, 36, 0, 7, ProjectileType.ENEMY)).toList());
        } else if (action == Action.MAGMA_MORTAR) {
            List<Boss.Bounds> landings = volumes;
            ProjectileBudget.emit(destination, landings.size(), () -> landings.stream().<Projectile>map(landing ->
                    new MagmaMortarProjectile(x + width / 2.0 - 9, y + 10,
                            landing.x() + landing.width() / 2 - 9, groundY - 18)).toList());
            shots = List.of();
        }
    }

    private static EntityType hatchlingType() {
        return EntityType.LURKER;
    }

    private void finishAttack() {
        Action completed = action;
        clearAttack();
        if (chapter == 3) {
            if (completed == Action.DRILL_CHARGE && stage >= 2 && appendageHp[0] > 0) {
                // Only a short central strip heats up after the vehicle has passed.
                double center = (chargeStartX + x) / 2 + width / 2.0;
                siegeTrail = new Boss.Bounds(clampArena(center - 60, 120), groundY - 16, 120, 16);
                trailWarning = TRAIL_WARNING; trailActive = TRAIL_DURATION;
            }
            coolingTicks = Math.max(coolingTicks, 115);
            rest(Action.COOLING, 115);
        } else if (chapter == 4 && stage == 3) {
            rest(Action.READY, completed == Action.HEART_PULSE ? 120 : 90);
        } else rest(Action.READY, Math.max(46, 84 - stage * 10));
    }

    private void rest(Action action, int ticks) {
        if(combatDifficulty!=null && action==Action.READY) {
            ticks=com.bigphil.mergehell.progression.CombatBalance.recovery(ticks,combatDifficulty);
            // Later stages pair attacks, with a full warning before each commitment.
            if(stage>=2 && pattern%2==1 && !(chapter==4 && stage==3)) ticks=Math.max(24,ticks/2);
            if(chapter==4 && stage==3) ticks=Math.max(72,ticks);
        }
        this.action = action;
        step = Step.REST;
        remaining = ticks;
        volumes = List.of();
        shots = List.of();
    }

    private void clearAttack() {
        volumes = List.of(); shots = List.of(); step = Step.REST;
    }

    private Boss.Bounds appendageBounds(int i) {
        if (chapter == 2) {
            double armX = x + (i == 0 ? 0 : 222), armY = y + 62;
            boolean stamping = i == 0 && action == Action.LEFT_STAMP || i == 1 && action == Action.RIGHT_STAMP;
            if (stamping && step != Step.REST && !volumes.isEmpty()) {
                double progress = step == Step.WARNING ? 1 - remaining / (double) warningDuration : 1;
                progress = progress * progress * (3 - 2 * progress);
                armX += (volumes.get(0).x() + 19 - armX) * progress;
                armY = step == Step.WARNING ? armY - 80 * progress : groundY - 108;
            }
            return new Boss.Bounds(armX, armY, 58, 108);
        }
        if (chapter == 3) {
            if (i == 0) return new Boss.Bounds(x + (direction < 0 ? 2 : 176), y + 47, 62, 91);
            return new Boss.Bounds(x + (direction < 0 ? 178 : 8), y + 86, 54, 63);
        }
        return new Boss.Bounds(x + (i == 0 ? 0 : 206), y + 123, 64, 87);
    }

    public List<Boss.PartView> parts(int hp, int maxHp) {
        List<Boss.PartView> result = new ArrayList<>(4);
        String[] names = chapter == 2 ? new String[]{"left-arm", "right-arm"}
                : chapter == 3 ? new String[]{"armor-plate", "heat-vent"} : new String[]{"left-organ", "right-organ"};
        for (int i = 0; i < 2; i++) {
            boolean destroyed = appendageHp[i] <= 0;
            boolean vent = chapter == 3 && i == 1;
            // A vent is a permanent component; its meter represents absorbed heat, never removal.
            int partHp = vent ? Math.max(0, 100 - heat) : appendageHp[i];
            result.add(new Boss.PartView(names[i], appendageBounds(i), partHp,
                    vent ? 100 : appendageMax, vent || !destroyed,
                    vent && (coolingTicks > 0 || appendageHp[0] == 0), !vent && destroyed));
        }
        Boss.Bounds core = chapter == 2 ? new Boss.Bounds(x + 110, y + 34, 64, 68)
                : chapter == 3 ? new Boss.Bounds(x + 74, y + 62, 96, 82)
                : new Boss.Bounds(x + 88, y + (stage == 3 ? 84 : 44), 94, stage == 3 ? 132 : 128);
        result.add(new Boss.PartView("core", core, hp, maxHp, true,
                coreOpen() && !coreContracting(), false));
        return List.copyOf(result);
    }

    private boolean coreOpen() { return exposure > 0 || chapter == 4 && stage == 3; }
    private boolean coreContracting() { return action == Action.RECONFIGURE; }

    public boolean canHit(Rectangle hit, int hp, int maxHp) {
        return parts(hp, maxHp).stream().anyMatch(p -> p.targetable() && p.bounds().intersects(hit));
    }

    /** Resolve one projectile against one component. A single large projectile never double hits. */
    public Damage hit(Rectangle hit, int amount, int hp, int maxHp) {
        if (amount <= 0) return new Damage(0, 0);
        List<Boss.PartView> candidates = parts(hp, maxHp);
        Boss.PartView selected = null;
        double largestArea = 0;
        for (Boss.PartView part : candidates) {
            if (!part.targetable() || !part.bounds().intersects(hit)) continue;
            Rectangle overlap = part.bounds().rectangle().intersection(hit);
            double area = overlap.getWidth() * overlap.getHeight();
            if (area > largestArea) { selected = part; largestArea = area; }
        }
        if (selected == null) return new Damage(0, 0);
        if (selected.id().equals("core")) {
            // The visible closing core loses its bonus only; it never becomes immune.
            // Already-armored cores keep their ordinary protection during reconfiguration.
            double factor = coreContracting() && coreOpen() ? 1.0
                    : selected.weak() ? (chapter == 4 ? 2.2 : 2.0) : chapter == 4 ? .40 : .60;
            return new Damage(scale(amount, factor), 0);
        }
        int index = selected.id().startsWith("right") || selected.id().equals("heat-vent") ? 1 : 0;
        if (chapter == 3 && index == 1) {
            if (selected.weak()) {
                heat = (int) Math.min(100, (long) heat + Math.max(4, (long) amount * 100 / appendageMax));
                if (heat >= 100 && exposure == 0) expose(Action.OVERHEATED, 190);
                return new Damage(scale(amount, 1.6), amount);
            }
            return new Damage(scale(amount, .3), 0);
        }
        int accepted = Math.min(appendageHp[index], amount);
        appendageHp[index] -= accepted;
        if (appendageHp[index] == 0) onPartDestroyed(index);
        return new Damage(scale(amount, chapter == 3 ? .35 : .25), accepted);
    }

    private static int scale(int amount, double factor) {
        // Authored multipliers use hundredths. Integer rounding avoids e.g. 100 * 2.2
        // becoming 220.00000000000003 and incorrectly dealing 221 damage.
        long scaled = ((long) amount * Math.round(factor * 100) + 99) / 100;
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, scaled));
    }

    /** Area damage affects both appendages once, without multiplying damage to the shared core. */
    public void splashParts(int amount) {
        int splash = Math.max(0, amount / 3);
        if (splash == 0) return;
        for (int i = 0; i < (chapter == 3 ? 1 : 2); i++) if (appendageHp[i] > 0) {
            appendageHp[i] = Math.max(0, appendageHp[i] - splash);
            if (appendageHp[i] == 0) onPartDestroyed(i);
        }
    }

    private void onPartDestroyed(int index) {
        if (chapter == 3) {
            coolingTicks = Math.max(coolingTicks, 160);
            clearSiegeTrail();
            if (action == Action.DRILL_CHARGE) rest(Action.COOLING, 160);
            return;
        }
        if (appendageHp[0] == 0 && appendageHp[1] == 0) {
            expose(Action.EXPOSED, chapter == 2 ? 240 : 260);
            return;
        }
        if ((index == 0 && (ownsLeftArm(action) || action == Action.LEFT_TENDRIL))
                || (index == 1 && (ownsRightArm(action) || action == Action.RIGHT_TENDRIL))) {
            rest(Action.READY, 65);
        } else if (action == Action.CROSSBEAM || action == Action.GANTRY_LOCK || action == Action.HATCH) {
            // Cancel and re-warn rather than editing an already announced attack under the player.
            rest(Action.READY, 65);
        }
    }

    private void expose(Action action, int ticks) {
        clearSiegeTrail();
        exposure = ticks;
        rest(action, ticks);
        movesSeen.add(action.name());
    }

    /** Accessibility/old-save hooks interrupt safely but do not regenerate any destroyed parts. */
    public void interrupt(int ticks) { expose(chapter == 3 ? Action.OVERHEATED : Action.EXPOSED, Math.min(180, ticks)); }

    public void resetTransient() {
        clearSiegeTrail();
        exposure = heat = coolingTicks = 0;
        regrowAfterRecovery = chapter != 3 && !(chapter == 4 && stage == 3)
                && appendageHp[0] == 0 && appendageHp[1] == 0;
        clearAttack();
        rest(Action.RECONFIGURE, 90);
    }

    public List<Boss.AttackTelegraph> telegraphs() {
        List<Boss.AttackTelegraph> result = new ArrayList<>();
        boolean damaging = step == Step.ACTIVE && action != Action.HATCH
                && action != Action.DRILL_CHARGE && action != Action.HEART_CRAWL
                && action != Action.MAGMA_MORTAR;
        if (step != Step.REST) for (int i = 0; i < volumes.size(); i++) result.add(new Boss.AttackTelegraph(
                action.name().toLowerCase(java.util.Locale.ROOT) + "-" + i, volumes.get(i),
                warningTicks(), step == Step.ACTIVE, damaging ? (chapter == 4 ? 22 : 18) : 0,
                warningDuration));
        if (siegeTrail != null) result.add(new Boss.AttackTelegraph("slag-trail", siegeTrail,
                trailWarning, trailWarning == 0, trailWarning == 0 ? 12 : 0, TRAIL_WARNING));
        return List.copyOf(result);
    }

    public List<Boss.Bounds> contacts() {
        if (!isContactDangerous()) return List.of();
        if (chapter == 2) {
            List<Boss.Bounds> result = new ArrayList<>();
            result.add(new Boss.Bounds(x + 110, y + 34, 64, 68));
            for (int i = 0; i < 2; i++) if (appendageHp[i] > 0) result.add(appendageBounds(i));
            return List.copyOf(result);
        }
        if (chapter == 3) return List.of(new Boss.Bounds(x + 12, y + 48, width - 24, height - 48));
        return List.of(new Boss.Bounds(x + 72, y + 60, 126, 150));
    }
}

