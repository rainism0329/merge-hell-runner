package com.bigphil.mergehell;

import com.bigphil.mergehell.engine.GameLoop;
import com.bigphil.mergehell.engine.GameSession;
import com.bigphil.mergehell.model.*;
import com.bigphil.mergehell.persistence.MergeHellState;
import com.bigphil.mergehell.persistence.MergeHellStateService;
import com.bigphil.mergehell.progression.*;
import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;

/**
 * Bounded counterplay diagnostic, not an assertion of human difficulty. Only chapter, seed,
 * normal starting build and the completed-route Boss gate are fixtures. After that entry,
 * every attack, movement, death and respawn is caused by ordinary ActionMap input and the
 * production simulation. No LAB, health injection, projectile injection or direct Boss damage.
 */
public final class ChapterBossPlaythroughProbe {
    private static final Method STEP;
    static {
        try { STEP = GamePanel.class.getDeclaredMethod("advanceSimulation"); STEP.setAccessible(true); }
        catch (ReflectiveOperationException ex) { throw new ExceptionInInitializerError(ex); }
    }
    private record Aim(String id, Rectangle bounds) { }
    private final Path out;
    private final int stepLimit;
    private final List<String> summary = new ArrayList<>(List.of(
            "profile,chapter,seed,result,steps,bossHp,bossMaxHp,bossStage,playerHp,lives,bombs,kills,partBreaks,damageEvents,longestDamageStall,reason"));

    private ChapterBossPlaythroughProbe(Path out, int stepLimit) throws Exception {
        if (stepLimit < 1 || stepLimit > 18_000) throw new IllegalArgumentException("Budget must be 1..18000");
        this.out = out; this.stepLimit = stepLimit; Files.createDirectories(out);
    }
    public static void main(String[] args) throws Exception {
        var probe = new ChapterBossPlaythroughProbe(Path.of(args.length == 0
                ? "build/chapter-overhaul/boss-playthrough" : args[0]), args.length > 3 ? Integer.parseInt(args[3]) : 18_000);
        Integer chapter = args.length > 1 ? Integer.parseInt(args[1]) : null;
        if (chapter != null && (chapter < 2 || chapter > 4)) throw new IllegalArgumentException("Chapter is zero-based 2..4");
        MergeHellState saved = MergeHellStateService.getInstance().getState();
        try {
            if (chapter != null) probe.run(chapter, args.length > 2 && Boolean.parseBoolean(args[2]));
            else for (boolean developed : new boolean[]{false, true})
                for (int level = 2; level <= 4; level++) probe.run(level, developed);
        } finally { MergeHellStateService.getInstance().loadState(saved); }
        Files.writeString(probe.out.resolve("README.md"), "# Boss input playthrough diagnostic\n\n"
                + "Each deterministic run is capped at " + probe.stepLimit + " production simulation steps. "
                + "The entry fixtures select the chapter and seed, mark the preceding route complete, and place a fresh "
                + "100 HP / three-life player at the ordinary Boss gate. The normal warning and arrival then run. "
                + "Base uses Commit Cannon with no upgrades; developed applies eight legal RunBuild upgrades before entry: "
                + "two drone ranks, two Commit critical ranks, two Commit ricochet ranks, dash cache and shield reboot. "
                + "There is no LAB, infinite health, forced part state, injected projectile, forced Boss damage, enemy clearing "
                + "or repositioning during the fight. Movement and shooting use actual held press/release ActionMap edges; "
                + "jumps, dashes, melee, finite bombs and natural drafts use normal key presses. Rendering is excluded from the loop. "
                + "The bot reads authoritative part bounds and warning volumes to choose targets and evade. Its limitations "
                + "include local movement planning and short projectile prediction. A failed run locates a counterplay problem "
                + "for review; it does not establish that a human cannot win.\n");
        System.out.println("Boss playthrough evidence: " + probe.out.toAbsolutePath());
    }

    private void run(int chapter, boolean developed) throws Exception {
        String profile = developed ? "developed" : "base", name = profile + "-chapter" + (chapter + 1);
        long seed = 2026091450L + chapter;
        var stored = new MergeHellState(); stored.settings.muted = true;
        MergeHellStateService.getInstance().loadState(stored);
        List<String> trace = new ArrayList<>(List.of(
                "tick,state,x,y,vy,hp,lives,bombs,bossX,bossY,bossHp,stage,action,warning,exposure,heat,hostiles,bullets,parts,target,reason"));
        try (var h = new HeapGameHarness()) {
            var session = new GameSession(seed);
            if (developed) for (UpgradeId id : new UpgradeId[]{UpgradeId.DRONE_COPILOT, UpgradeId.DRONE_COPILOT,
                    UpgradeId.COMMIT_CRITICAL, UpgradeId.COMMIT_CRITICAL, UpgradeId.COMMIT_RICOCHET,
                    UpgradeId.COMMIT_RICOCHET, UpgradeId.DASH_CACHE, UpgradeId.SHIELD_REBOOT})
                session.runBuild().apply(UpgradeCatalog.definition(id));
            h.set("session", session); h.player().setRunBuild(session.runBuild()); h.player().setCombatSeed(seed);
            h.set("level", chapter); h.invoke("advanceLevel"); step(h);
            ((LevelManager) h.get("levelManager")).advanceToBossGateForTesting();
            h.finishExplorationFixture();
            double gate=h.panel.getLevelManager().getBossGateX();
            h.player().setX(gate); h.player().setY(450); h.set("cameraX", gate-288); step(h);
            for (int i = 0; i < 260 && h.state() != GameState.BOSS_FIGHT; i++) step(h);
            if (h.state() != GameState.BOSS_FIGHT || h.player().getHp() != 100
                    || h.player().getLives() != 3 || h.player().isDebugMode())
                throw new IllegalStateException("Entry must be a fresh ordinary Boss fight: " + h.state());

            int tick = 0, lastJump = -100, lastDash = -100, lastMelee = -100, lastBomb = -300;
            int lastHp = 100, lastLives = 3, lastBossHp = h.boss().getHp(), lastDamage = 0, longestStall = 0;
            int breaks = 0, damageEvents = 0;
            Map<String, Boolean> destroyed = new HashMap<>();
            String target = "entry", reason = "arrival", lastAction = "";
            trace.add(row(0, h, target, reason));
            for (; tick < stepLimit; tick++) {
                Player p = h.player(); Boss boss = h.boss();
                if (p.isDebugMode()) throw new IllegalStateException("LAB cannot be used by this probe");
                if (boss == null || boss.getHp() <= 0 || h.state() == GameState.GAME_OVER || h.state() == GameState.ERROR) break;
                if (h.state() == GameState.UPGRADE_SELECTION) {
                    int selected = chooseUpgrade(session, p);
                    reason = "draft:" + session.upgradeChoices().get(selected).id();
                    h.key("UPGRADE_" + (selected + 1));
                } else if (h.state() == GameState.BOSS_FIGHT) {
                    Aim aim = chooseAim(h, chapter); target = aim.id();
                    double camera = ((Number) h.get("cameraX")).doubleValue();
                    double minX = camera + 20, maxX = camera + 910;
                    double center = aim.bounds().getCenterX(), dx = center - (p.getX() + 15);
                    double desired = center - 175;
                    if (chapter == 3 && target.equals("heat-vent") && center > boss.getX() + boss.getWidth() / 2.0)
                        desired = center + 115;
                    desired = clamp(desired, minX, maxX);
                    String action = boss.getEncounterAction();
                    boolean crossbeam = action.equals("CROSSBEAM");
                    var volumes = boss.getAttackTelegraphs();
                    boolean columnAttack = !crossbeam && !action.equals("SPORE_FAN") && !action.equals("HATCH");
                    boolean escaping = false;
                    if (columnAttack && !volumes.isEmpty()) {
                        double safe = safePosition(p.getX(), desired, minX, maxX, volumes);
                        escaping = Math.abs(safe - desired) > 2;
                        desired = safe;
                    }
                    // A shot cannot hit an organ below a platform: leave its actual edge before firing again.
                    if (!escaping && p.isGrounded() && p.getY() + 15 < aim.bounds().y - 10) {
                        Platform support = supportingPlatform(h, p);
                        if (support != null) desired = clamp(p.getX() < support.x + support.width / 2.0
                                ? support.x - 38 : support.x + support.width + 8, minX, maxX);
                    }
                    int direction = desired < p.getX() - 8 ? -1 : desired > p.getX() + 8 ? 1 : 0;
                    if (direction == 0 && Math.abs(dx) > 30 && p.getFacingDir() != (dx < 0 ? -1 : 1)) direction = dx < 0 ? -1 : 1;
                    double dy=aim.bounds().getCenterY()-(p.getY()+p.getBounds().height*.5);
                    int aimVertical=Math.abs(dy)>Math.abs(dx)*.414?(dy<0?-1:1):0;
                    int aimHorizontal=Math.abs(dy)>Math.abs(dx)*2.414?0:dx<0?-1:1;
                    held(h,"AIM_LOCK","AIM_LOCK_R","keyAimLock",direction==0);
                    held(h,"MENU_UP","MENU_UP_R","keyUp",aimVertical<0);
                    held(h,"MENU_DOWN","MENU_DOWN_R","keyDown",aimVertical>0 && !p.isGrounded());
                    held(h, "LEFT", "LEFT_R", "keyLeft", direction==0?aimHorizontal<0:direction<0);
                    held(h, "RIGHT", "RIGHT_R", "keyRight", direction==0?aimHorizontal>0:direction>0);
                    held(h, "SHOOT", "SHOOT_R", "keyShoot", true);
                    reason = escaping ? "evade:" + action : "aim:" + target;

                    boolean needHeight = p.getY() + 15 > aim.bounds().getMaxY() - 4;
                    boolean beamJump = crossbeam && (boss.getWarningTicks() > 0 && boss.getWarningTicks() <= 21
                            || boss.getWarningTicks() == 0) && p.getY() + 30 > 423;
                    boolean bulletDanger = bulletDanger(h, p);
                    boolean closeEnemy = target.startsWith("enemy:") && Math.abs(dx) < 100;
                    boolean jump = p.isGrounded() && (needHeight || beamJump || bulletDanger || closeEnemy);
                    boolean doubleJump = !p.isGrounded() && !crossbeam && p.getVerticalVelocity() > -1
                            && tick - lastJump > 20 && needHeight;
                    if ((jump || doubleJump) && tick - lastJump > 12) {
                        h.key("JUMP"); lastJump = tick; reason += doubleJump ? ":double-jump" : ":jump";
                    }
                    if (tick - lastDash > 65 && (escaping && Math.abs(desired - p.getX()) > 55 && boss.getWarningTicks() < 22
                            || bulletDanger && !p.isGrounded() && p.getInvincibleTimer() == 0)) {
                        h.key("DASH"); lastDash = tick; reason += ":dash";
                    }
                    if (Math.abs(dx) < 74 && Math.abs(aim.bounds().getCenterY() - p.getY() - 15) < 46
                            && tick - lastMelee > 34) { h.key("MELEE"); lastMelee = tick; reason += ":melee"; }
                    long pressure = h.enemies().getEnemies().stream().filter(e -> e.getType().isHostile()
                            && !e.isDead() && Math.abs(e.getX() - p.getX()) < 400).count();
                    if (p.getBombs() > 0 && tick - lastBomb > 260 && (pressure >= 3
                            || p.getHp() <= 32 && bulletDanger || boss.isVulnerable() && boss.getHp() < 550)) {
                        h.key("BOMB"); lastBomb = tick; reason += ":bomb";
                    }
                }
                step(h);
                Boss after = h.boss();
                if (after != null) {
                    if (after.getHp() < lastBossHp) { longestStall = Math.max(longestStall, tick - lastDamage); lastDamage = tick; }
                    for (var part : after.getParts()) {
                        if (part.destroyed() && !destroyed.getOrDefault(part.id(), false)) breaks++;
                        destroyed.put(part.id(), part.destroyed());
                    }
                    if (p.getHp() < lastHp || p.getLives() != lastLives) damageEvents++;
                    if (tick % 120 == 0 || p.getHp() < lastHp || p.getLives() != lastLives
                            || !after.getEncounterAction().equals(lastAction))
                        trace.add(row(tick, h, target, (p.getHp() < lastHp ? "damage:" : "") + reason));
                    lastAction = after.getEncounterAction(); lastBossHp = after.getHp();
                }
                lastHp = p.getHp(); lastLives = p.getLives();
            }
            Boss boss = h.boss();
            String result = boss == null || boss.getHp() <= 0 ? "BOSS_DEFEATED"
                    : tick >= stepLimit ? "STEP_LIMIT" : h.state().name();
            longestStall = Math.max(longestStall, tick - lastDamage);
            trace.add(row(tick, h, target, "END:" + result));
            Files.write(out.resolve(name + ".csv"), trace);
            summary.add(String.format(Locale.ROOT, "%s,%d,%d,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%s",
                    profile, chapter + 1, seed, result, tick, boss == null ? 0 : boss.getHp(), boss == null ? 0 : boss.getMaxHp(),
                    boss == null ? 3 : boss.getCombatStage(), h.player().getHp(), h.player().getLives(), h.player().getBombs(),
                    session.hostileKills(), breaks, damageEvents, longestStall, reason));
            Files.write(out.resolve("summary.csv"), summary);
            System.out.println(summary.get(summary.size() - 1));
        }
    }

    private static Aim chooseAim(HeapGameHarness h, int chapter) throws Exception {
        Player p = h.player(); Boss boss = h.boss();
        var nearby = h.enemies().getEnemies().stream().filter(e -> e.getType().isHostile() && !e.isDead()
                        && Math.abs(e.getX() - p.getX()) < 220)
                .min(Comparator.comparingDouble(e -> Math.abs(e.getX() - p.getX()))).orElse(null);
        if (nearby != null) return new Aim("enemy:" + nearby.getType(), nearby.getBounds());
        var parts = boss.getParts();
        if (boss.isVulnerable() || chapter == 4 && boss.getCombatStage() == 3)
            for (var part : parts) if (part.id().equals("core")) return new Aim(part.id(), part.bounds().rectangle());
        if (chapter == 3) {
            var plate = parts.stream().filter(part -> part.id().equals("armor-plate") && part.targetable()).findFirst();
            if (plate.isPresent()) return new Aim(plate.get().id(), plate.get().bounds().rectangle());
            for (var part : parts) if (part.id().equals("heat-vent")) return new Aim(part.id(), part.bounds().rectangle());
        }
        return parts.stream().filter(part -> part.targetable() && !part.id().equals("core"))
                .min(Comparator.comparingDouble(part -> Math.abs(part.bounds().x() - p.getX())))
                .map(part -> new Aim(part.id(), part.bounds().rectangle()))
                .orElseGet(() -> new Aim("core", parts.stream().filter(part -> part.id().equals("core"))
                        .findFirst().orElseThrow().bounds().rectangle()));
    }

    private static double safePosition(double current, double desired, double min, double max, List<Boss.AttackTelegraph> volumes) {
        boolean desiredSafe = volumes.stream().noneMatch(v -> overlapsX(desired, v.bounds(), 25));
        if (desiredSafe && volumes.stream().noneMatch(v -> overlapsX(current, v.bounds(), 12))) return desired;
        double best = current, bestCost = Double.MAX_VALUE;
        for (double candidate = min; candidate <= max; candidate += 10) {
            final double x = candidate;
            if (volumes.stream().anyMatch(v -> overlapsX(x, v.bounds(), 22))) continue;
            double cost = Math.abs(x - current) + .13 * Math.abs(x - desired);
            if (cost < bestCost) { bestCost = cost; best = x; }
        }
        return best;
    }
    private static boolean overlapsX(double x, Boss.Bounds b, int padding) {
        return x + 30 + padding > b.x() && x - padding < b.x() + b.width();
    }
    @SuppressWarnings("unchecked")
    private static Platform supportingPlatform(HeapGameHarness h, Player p) throws Exception {
        for (Platform platform : (List<Platform>) h.get("combatPlatforms"))
            if (Math.abs(p.getY() + 30 - platform.y) < 3 && p.getX() + 30 > platform.x
                    && p.getX() < platform.x + platform.width) return platform;
        return null;
    }
    private static boolean bulletDanger(HeapGameHarness h, Player p) throws Exception {
        Rectangle box = new Rectangle((int) p.getX() - 16, (int) p.getY() - 10, 62, 50);
        for (Projectile shot : h.enemies().getEnemyBullets()) if (!shot.isDead())
            for (int t = 0; t <= 10; t += 2) {
                Rectangle future = new Rectangle((int) (shot.getX() + shot.getVx() * t),
                        (int) (shot.getY() + shot.getVy() * t + shot.getVerticalAcceleration() * t * (t + 1) / 2),
                        shot.getType().width, shot.getType().height);
                if (box.intersects(future)) return true;
            }
        return false;
    }
    private static int chooseUpgrade(GameSession session, Player player) {
        int selected = 0, best = -1;
        for (int i = 0; i < session.upgradeChoices().size(); i++) {
            UpgradeId id = session.upgradeChoices().get(i).id();
            int priority = id == UpgradeId.SUPPLY_REPAIR && player.getHp() < 65 ? 12
                    : id == UpgradeId.DRONE_COPILOT ? 10 : id == UpgradeId.SHIELD_REBOOT ? 8
                    : id == UpgradeId.DEPENDENCY_CORE_COMMIT && session.runBuild().weaponLevel() >= 4 ? 9
                    : id == UpgradeId.COMMIT_CRITICAL || id == UpgradeId.COMMIT_RICOCHET ? 7
                    : id == UpgradeId.SUPPLY_BOMB && player.getBombs() < 2 ? 8 : 2;
            if (priority > best) { best = priority; selected = i; }
        }
        return selected;
    }
    private static void held(HeapGameHarness h, String press, String release, String field, boolean wanted) throws Exception {
        if ((boolean) h.get(field) != wanted) {
            SwingUtilities.invokeAndWait(() -> {
                if (wanted) h.panel.getActionMap().get(release).actionPerformed(new ActionEvent(h.panel, 0, release));
                String action = wanted ? press : release;
                h.panel.getActionMap().get(action).actionPerformed(new ActionEvent(h.panel, 0, action));
            });
        }
    }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private static void step(HeapGameHarness h) throws Exception {
        h.clock.addAndGet(GameLoop.LEGACY_STEP_NANOS); STEP.invoke(h.panel);
    }
    private static String row(int tick, HeapGameHarness h, String target, String reason) throws Exception {
        Player p = h.player(); Boss b = h.boss();
        String parts = b == null ? "none" : String.join("|", b.getParts().stream().map(part ->
                part.id() + ":" + part.hp() + ":" + (int) part.bounds().x() + ":" + (int) part.bounds().y()
                        + ":" + (int) part.bounds().width() + ":" + (int) part.bounds().height()).toList());
        return String.format(Locale.ROOT, "%d,%s,%.1f,%.1f,%.1f,%d,%d,%d,%.1f,%.1f,%d,%d,%s,%d,%d,%d,%d,%d,%s,%s,%s",
                tick, h.state(), p.getX(), p.getY(), p.getVerticalVelocity(), p.getHp(), p.getLives(), p.getBombs(),
                b == null ? 0 : b.getX(), b == null ? 0 : b.getY(), b == null ? 0 : b.getHp(),
                b == null ? 3 : b.getCombatStage(), b == null ? "DEFEATED" : b.getEncounterAction(),
                b == null ? 0 : b.getWarningTicks(), b == null ? 0 : b.getVulnerabilityTicks(), b == null ? 0 : b.getHeat(),
                h.enemies().getEnemies().stream().filter(e -> e.getType().isHostile() && !e.isDead()).count(),
                h.enemies().getEnemyBullets().size(), parts, target, reason);
    }
}
