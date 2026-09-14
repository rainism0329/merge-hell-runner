package com.bigphil.mergehell;

import com.bigphil.mergehell.engine.GameSession;
import com.bigphil.mergehell.engine.GameLoop;
import com.bigphil.mergehell.model.*;
import com.bigphil.mergehell.persistence.MergeHellState;
import com.bigphil.mergehell.persistence.MergeHellStateService;
import com.bigphil.mergehell.progression.*;
import com.bigphil.mergehell.world.ChapterRouteController;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.event.ActionEvent;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;

/**
 * A finite, deliberately simple player bot. Only chapter/seed/build entry are fixtures.
 * Every later move, shot, jump, draft, bomb, collision, death and respawn uses production input/simulation.
 * Bot failure diagnoses a location; it is not evidence that a human cannot finish the chapter.
 */
public final class ChapterPlaythroughProbe {
    private static final int DEFAULT_STEP_LIMIT = 12_000;
    private static final Method STEP;
    static {
        try { STEP = GamePanel.class.getDeclaredMethod("advanceSimulation"); STEP.setAccessible(true); }
        catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }
    private record Aim(String id, Rectangle bounds, ObstacleManager.Enemy enemy) { }
    private final Path out;
    private final int stepLimit;
    private final boolean bombOnStall;
    private final List<String> summary = new ArrayList<>(List.of("profile,chapter,seed,result,steps,maxX,endX,hp,lives,bombs,kills,upgrades,brokenProps,hostiles,reason"));
    private ChapterPlaythroughProbe(Path out, int stepLimit, boolean bombOnStall) throws Exception {
        if (stepLimit < 1 || stepLimit > 14_000) throw new IllegalArgumentException("Probe budget must be 1..14000");
        this.out = out; this.stepLimit = stepLimit; this.bombOnStall = bombOnStall; Files.createDirectories(out);
    }
    public static void main(String[] args) throws Exception {
        var probe = new ChapterPlaythroughProbe(Path.of(args.length == 0 ? "build/chapter-overhaul/playthrough" : args[0]),
                args.length > 3 ? Integer.parseInt(args[3]) : DEFAULT_STEP_LIMIT,
                args.length > 4 && Boolean.parseBoolean(args[4]));
        Integer selectedChapter = args.length > 1 && !args[1].equalsIgnoreCase("all") ? Integer.parseInt(args[1]) : null;
        if (selectedChapter != null && (selectedChapter < 2 || selectedChapter > 4)) throw new IllegalArgumentException("chapter is zero-based 2..4");
        MergeHellState saved = MergeHellStateService.getInstance().getState();
        try {
            if (selectedChapter != null) probe.run(selectedChapter, args.length > 2 && Boolean.parseBoolean(args[2]));
            else for (boolean developed : new boolean[]{false, true}) for (int chapter = 2; chapter <= 4; chapter++)
                    probe.run(chapter, developed);
        } finally { MergeHellStateService.getInstance().loadState(saved); }
        Files.write(probe.out.resolve("summary.csv"), probe.summary);
        Files.writeString(probe.out.resolve("README.md"), "# Automated chapter route probe\n\n"
                + (selectedChapter == null ? "Six deterministic runs" : "One deterministic follow-up run")
                + ", each limited to " + probe.stepLimit + " simulation steps. "
                + (probe.stepLimit > DEFAULT_STEP_LIMIT ? "The original 12,000-step budget is explicitly extended to 14,000 for this validation. " : "")
                + (probe.bombOnStall ? "Input policy: after 600 ticks without forward progress with a nearby hostile, the bot presses B to spend an available normal bomb. This addresses its inability to step off a ledge while targeting a nearby enemy below it. Bombs are also used for dense threats or low-HP emergencies. " : "")
                + "Chapter entry and seed are fixtures. Layout, encounters, drops, upgrade choices and the player's independent combat RNG all use the fixed run seed. "
                + "Base profile starts with an ordinary Commit Cannon build. Developed profile applies eight legal permanent upgrades "
                + "through RunBuild.apply before entering: two drone ranks, two critical ranks, two ricochet ranks, one dash cache, one shield reboot. "
                + "No LAB, HP injection, direct entity clearing, position overrides, route shortcuts or debug immunity are used during traversal. Normal bombs, damage, shield upgrades and automatic respawn/rescue behavior remain active. "
                + "Natural upgrade drafts are selected by real number-key input. The bot stops at the actual boss fight, game over, or step limit. "
                + "The bot has no general path planning and cannot establish human difficulty or impossibility.\n");
        System.out.println("Playthrough evidence: " + probe.out.toAbsolutePath());
    }

    private void run(int chapter, boolean developed) throws Exception {
        String profile = developed ? "developed" : "base", name = profile + "-chapter" + (chapter + 1);
        long seed = 2026091400L + chapter;
        var stored = new MergeHellState(); stored.settings.muted = true;
        MergeHellStateService.getInstance().loadState(stored);
        List<String> trace = new ArrayList<>(List.of("tick,x,y,hp,lives,bombs,state,battle,wave,alive,kills,brokenProps,target,reason,worldTick,keyLeft,keyRight,hitstop,ctxHitstop,camera,sessionState"));
        try (var h = new HeapGameHarness()) {
            var session = new GameSession(seed);
            if (developed) for (UpgradeId id : new UpgradeId[]{UpgradeId.DRONE_COPILOT, UpgradeId.DRONE_COPILOT,
                    UpgradeId.COMMIT_CRITICAL, UpgradeId.COMMIT_CRITICAL, UpgradeId.COMMIT_RICOCHET,
                    UpgradeId.COMMIT_RICOCHET, UpgradeId.DASH_CACHE, UpgradeId.SHIELD_REBOOT})
                session.runBuild().apply(UpgradeCatalog.definition(id));
            h.set("session", session); h.player().setRunBuild(session.runBuild()); h.player().setCombatSeed(seed);
            h.set("level", chapter); h.invoke("advanceLevel"); STEP.invoke(h.panel);
            var route = (ChapterRouteController) h.get("chapterRoute");
            var level = (LevelManager) h.get("levelManager");
            int tick = 0, lastJump = -100, lastDash = -100, lastBomb = -300, lastMelee = -50, lastProgress = 0;
            int lastHp = h.player().getHp(), lastLives = h.player().getLives(), lastKills = 0, lastBroken = 0;
            double maxX = h.player().getX(), lastProgressX = maxX; String reason = "entrance", targetName = "none";
            GameState previous = h.state();
            for (; tick < stepLimit; tick++) {
                Player p = h.player();
                if (p.isDebugMode()) throw new IllegalStateException("Probe must not enable LAB");
                GameState state = h.state();
                if (state == GameState.BOSS_FIGHT || state == GameState.GAME_OVER || state == GameState.ERROR) break;
                if (state == GameState.UPGRADE_SELECTION) {
                    int index = chooseUpgrade(session, p);
                    h.key("UPGRADE_" + (index + 1)); reason = "upgrade:" + session.upgradeChoices().get(index).id();
                } else if (state == GameState.RUNNING) {
                    var scene = route.snapshot();
                    Aim aim = chooseAim(h, scene);
                    targetName = aim == null ? "route" : aim.id();
                    double dx = aim == null ? 999 : aim.bounds().getCenterX() - p.getBounds().getCenterX();
                    boolean closeThreat = aim != null && aim.enemy() != null && Math.abs(dx) < 110;
                    boolean clearShotHeight = aim == null || p.getY() + 14 >= aim.bounds().y - 8
                            && p.getY() + 14 <= aim.bounds().getMaxY() + 8;
                    boolean stopToShoot = aim != null && clearShotHeight && Math.abs(dx) < 280 && Math.abs(dx) > 100;
                    int direction = aim != null && dx < -25 ? -1 : stopToShoot ? 0 : 1;
                    if (aim != null && Math.abs(dx) < 90) direction = p.getFacingDir() == (dx < 0 ? -1 : 1) ? 0 : dx < 0 ? -1 : 1;
                    // Advance through cleared arenas; off-screen enemies are approached instead of deleting them.
                    held(h, "LEFT", "LEFT_R", "keyLeft", direction < 0);
                    held(h, "RIGHT", "RIGHT_R", "keyRight", direction > 0);
                    held(h, "SHOOT", "SHOOT_R", "keyShoot", true);
                    reason = aim == null ? "advance" : "engage:" + aim.id();
                    boolean hazardAhead = scene.surfaces().stream().anyMatch(s ->
                            (s.kind() == ChapterRouteController.SurfaceKind.GAP || s.active())
                                    && p.getX() + 110 > s.x() && p.getX() < s.x() + s.width()
                                    && p.getY() + 30 > 340);
                    boolean threateningWarning = h.enemies().getEnemies().stream().anyMatch(e -> !e.isDead()
                            && e.getTelegraphTicks() > 0 && e.getTelegraphTicks() < 16
                            && Math.abs(e.getX() - p.getX()) < 440 && e.getType() != EntityType.RIGGER);
                    boolean needHeight = aim != null && aim.bounds().getMaxY() < p.getY() + 6;
                    boolean jump = p.isGrounded() && (hazardAhead || threateningWarning || needHeight || closeThreat);
                    boolean doubleJump = !p.isGrounded() && p.getVerticalVelocity() > -1 && tick - lastJump > 18
                            && (hazardAhead || needHeight || closeThreat);
                    if ((jump || doubleJump) && tick - lastJump > 12) {
                        h.key("JUMP"); lastJump = tick; reason = doubleJump ? "double-jump" : "jump";
                    }
                    if (hazardAhead && !p.isGrounded() && p.getVerticalVelocity() > 1 && tick - lastDash > 80) {
                        h.key("DASH"); lastDash = tick; reason = "hazard-dash";
                    }
                    if (aim != null && Math.abs(dx) < 77 && Math.abs(aim.bounds().getCenterY() - p.getBounds().getCenterY()) < 50
                            && tick - lastMelee > 32) { h.key("MELEE"); lastMelee = tick; }
                    if (tick % 24 == 0) h.key("HEAP_PURGE");
                    long pressure = h.enemies().getEnemies().stream().filter(e -> !e.isDead() && e.getType().isHostile()
                            && Math.abs(e.getX() - p.getX()) < 320).count();
                    if (p.getBombs() > 0 && tick - lastBomb > 250 && (pressure >= 4 || p.getHp() <= 35 && pressure >= 2
                            || bombOnStall && tick - lastProgress > 600 && pressure > 0)) {
                        h.key("BOMB"); lastBomb = tick; reason = "emergency-bomb";
                    }
                }
                h.clock.addAndGet(GameLoop.LEGACY_STEP_NANOS);
                STEP.invoke(h.panel);
                int broken = (int) route.snapshot().props().stream().filter(prop -> prop.hp() == 0).count();
                maxX = Math.max(maxX, p.getX());
                if (maxX > lastProgressX + 5) { lastProgressX = maxX; lastProgress = tick; }
                boolean changed = state != previous || p.getHp() < lastHp || p.getLives() != lastLives
                        || session.hostileKills() != lastKills || broken != lastBroken;
                if (changed || tick % 120 == 0) trace.add(row(tick, h, level, broken, targetName,
                        (p.getHp() < lastHp ? "damage:" : "") + reason));
                if (tick - lastProgress > 1600 && tick % 600 == 0)
                    trace.add(row(tick, h, level, broken, targetName, "progress-stalled:" + (tick - lastProgress)));
                previous = state; lastHp = p.getHp(); lastLives = p.getLives(); lastKills = session.hostileKills(); lastBroken = broken;
            }
            String result = h.state() == GameState.BOSS_FIGHT ? "REACHED_BOSS" : h.state().name();
            if (tick >= stepLimit) result = "STEP_LIMIT";
            trace.add(row(tick, h, level, lastBroken, targetName, "END:" + result));
            Files.write(out.resolve(name + ".csv"), trace);
            summary.add(String.format(Locale.ROOT, "%s,%d,%d,%s,%d,%.1f,%.1f,%d,%d,%d,%d,%d,%d,%d,%s",
                    profile, chapter + 1, seed, result, tick, maxX, h.player().getX(), h.player().getHp(), h.player().getLives(),
                    h.player().getBombs(), session.hostileKills(), session.metrics().upgradeCount(), lastBroken,
                    h.enemies().getEnemies().stream().filter(e -> e.getType().isHostile() && !e.isDead()).count(), targetName));
            h.tick();
            BufferedImage frame = new BufferedImage(960, 600, BufferedImage.TYPE_INT_ARGB);
            SwingUtilities.invokeAndWait(() -> { var g = frame.createGraphics(); try { h.panel.paint(g); } finally { g.dispose(); } });
            ImageIO.write(frame, "png", out.resolve(name + "-end.png").toFile()); frame.flush();
            System.out.println(summary.get(summary.size() - 1));
            Files.write(out.resolve("summary.csv"), summary);
        }
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
    private static Aim chooseAim(HeapGameHarness h, ChapterRouteController.Snapshot scene) throws Exception {
        double x = h.player().getX();
        var enemy = h.enemies().getEnemies().stream().filter(e -> !e.isDead() && e.getType().isHostile()
                        && Math.abs(e.getX() - x) < 720)
                .min(Comparator.comparingDouble(e -> Math.abs(e.getX() - x))).orElse(null);
        if (enemy != null) return new Aim(enemy.getType().name(), enemy.getBounds(), enemy);
        var prop = scene.props().stream().filter(p -> p.hp() > 0 && p.kind() != ChapterRouteController.Kind.COOLANT
                        && p.bounds().getMaxX() > x - 20 && p.bounds().x - x < 330)
                .min(Comparator.comparingDouble(p -> Math.abs(p.bounds().x - x))).orElse(null);
        return prop == null ? null : new Aim(prop.kind().name(), prop.bounds(), null);
    }
    private static void held(HeapGameHarness h, String press, String release, String field, boolean wanted) throws Exception {
        if ((boolean) h.get(field) != wanted) {
            String action = wanted ? press : release;
            SwingUtilities.invokeAndWait(() -> {
                // Menus/respawns clear logical movement without synthesizing a physical key-up.
                // Re-press after that boundary must first deliver the player's real release edge.
                if (wanted) h.panel.getActionMap().get(release).actionPerformed(new ActionEvent(h.panel, 0, release));
                h.panel.getActionMap().get(action).actionPerformed(new ActionEvent(h.panel, 0, action));
            });
        }
    }
    private static String row(int tick, HeapGameHarness h, LevelManager level, int broken, String target, String reason) throws Exception {
        var p = h.player();
        return String.format(Locale.ROOT, "%d,%.1f,%.1f,%d,%d,%d,%s,%s,%d,%d,%d,%d,%s,%s,%d,%s,%s,%s,%d,%s,%s", tick,
                p.getX(), p.getY(), p.getHp(), p.getLives(), p.getBombs(), h.state(), level.isInBattle(), level.getCurrentWave(),
                h.enemies().getEnemies().stream().filter(e -> e.getType().isHostile() && !e.isDead()).count(),
                h.panel.getSession().hostileKills(), broken, target, reason, h.panel.getSession().worldTick(),
                h.get("keyLeft"), h.get("keyRight"), h.get("hitstop"), h.context().hitstop, h.get("cameraX"), h.panel.getSession().state());
    }
}
