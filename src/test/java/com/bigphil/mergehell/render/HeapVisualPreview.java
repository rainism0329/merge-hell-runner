package com.bigphil.mergehell.render;

import com.bigphil.mergehell.model.Boss;
import com.bigphil.mergehell.model.EntityType;
import com.bigphil.mergehell.model.ObstacleManager;
import com.bigphil.mergehell.model.Projectile;
import com.bigphil.mergehell.world.HeapDistrictController;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Headless art probe using actual Heap controller interactions and production rendering, not a playthrough. */
public final class HeapVisualPreview {
    private static final HeapWorldRenderer WORLD = new HeapWorldRenderer();
    private static final IndustrialDeck DECK = new IndustrialDeck();
    private static IndustrialArt art;

    public static void main(String[] args) throws Exception {
        if (!GraphicsEnvironment.isHeadless()) throw new IllegalStateException("Run with -Djava.awt.headless=true");
        Path out = Path.of(args.length == 0 ? "build/heap-visual/preview" : args[0]);
        Files.createDirectories(out.resolve("motion"));
        art = IndustrialArt.load();
        if (!art.diagnostics().isEmpty()) throw new IllegalStateException(art.diagnostics().toString());
        HeapDistrictController route = HeapDistrictController.standard(42);
        route.update(routeInput(true));
        save(out, "route-ready", route.snapshot(), null, false);
        route.interact(HeapDistrictController.Choice.PURGE);
        advance(route, routeInput(true), 24);
        save(out, "route-channeling", route.snapshot(), null, false);
        advance(route, routeInput(true), 24);
        save(out, "route-purged", route.snapshot(), null, false);

        HeapDistrictController risk = HeapDistrictController.standard(42);
        risk.update(routeInput(true)); risk.interact(HeapDistrictController.Choice.SALVAGE);
        save(out, "risk-warning", risk.snapshot(), null, false);
        advance(risk, routeInput(true), HeapDistrictController.POOL_WARNING_TICKS);
        save(out, "risk-active", risk.snapshot(), null, false);
        save(out, "risk-active-compact", risk.snapshot(), null, true);

        Boss boss = new Boss("MEMORY LEAK DAEMON", 4000, "", 850, 1, 42);
        boss.activate();
        ObstacleManager enemies = new ObstacleManager(42);
        List<Projectile> bullets = new ArrayList<>();
        for (int i = 0; i < 90; i++) boss.update(enemies, 480, 133, 360, bullets);
        HeapDistrictController arena = HeapDistrictController.standard(42);
        for (int i = 0; i < 120; i++) arena.update(bossInput(boss, true));
        save(out, "boss-reflux-warning", arena.snapshot(), boss, false);
        advance(arena, bossInput(boss, true), 31);
        save(out, "boss-reflux-active", arena.snapshot(), boss, false);
        arena.interact(HeapDistrictController.Choice.PURGE);
        advance(arena, bossInput(boss, true), 24);
        save(out, "boss-channeling", arena.snapshot(), boss, false);
        advance(arena, bossInput(boss, true), 24);
        apply(arena, boss);
        save(out, "boss-exposed", arena.snapshot(), boss, false);
        boss.damage(80);
        save(out, "boss-hit", arena.snapshot(), boss, false);
        boss.damage(900);
        boss.update(enemies, 480, 133, 360, bullets);
        arena.update(bossInput(boss, true));
        save(out, "boss-stage3", arena.snapshot(), boss, false);
        save(out, "boss-stage3-compact", arena.snapshot(), boss, true);
        for (int frame = 0; frame < 48; frame++) {
            boss.update(enemies, 480, 133, 340 + Math.sin(frame * .12) * 35, bullets);
            arena.update(bossInput(boss, true)); apply(arena, boss);
            ImageIO.write(scene(arena.snapshot(), boss, false), "png", out.resolve("motion/frame-%03d.png".formatted(frame)).toFile());
        }
        details(out);
        Files.writeString(out.resolve("README.md"), """
                # Heap production renderer inspection

                This main uses actual HeapDistrictController PURGE/SALVAGE actions, warning timers,
                channel completion, Boss damage/vulnerability state and production HeapWorldRenderer /
                HeapActorRenderer / ActorVisuals / IndustrialDeck drawing. Controlled enemy placements,
                labels and camera positions make this an art probe, not a GamePanel capture or playthrough.
                Route files show ready/channeling/used GC stations and actual warning/active pool states.
                Boss files show real returning-block states, GC cooldown/weakpoint opening, hit and stage 3.
                The 600x400 compact images render compact labels first, then use the real 0.625 display
                scale plus letterboxing. The 48 motion images advance simulation ticks without a wall clock.
                `details-3x.png` magnifies standard/damaged Leak and closed/exposed/enraged Boss states.

                Run `com.bigphil.mergehell.render.HeapVisualPreview [output-directory]` with Java 17,
                `-Djava.awt.headless=true`, compiled main/test classes and src/main/resources on the
                classpath. No audio, image generation or desktop controls are used.
                """, StandardCharsets.UTF_8);
        System.out.println("Heap production renderer preview: " + out.toAbsolutePath());
    }

    private static HeapDistrictController.Input routeInput(boolean active) {
        return new HeapDistrictController.Input(new HeapDistrictController.Bounds(698, 416, 36, 64),
                400, 960, 480, active, false, null, 1);
    }
    private static HeapDistrictController.Input bossInput(Boss boss, boolean active) {
        return new HeapDistrictController.Input(new HeapDistrictController.Bounds(115, 416, 36, 64),
                0, 960, 480, active, true,
                new HeapDistrictController.Bounds(boss.getX(), boss.getY(), boss.getWidth(), boss.getHeight()), boss.getCombatStage());
    }
    private static void advance(HeapDistrictController c, HeapDistrictController.Input input, int ticks) {
        for (int i = 0; i < ticks; i++) c.update(input);
    }
    private static void apply(HeapDistrictController arena, Boss boss) {
        for (var event : arena.drainEvents()) {
            if (event instanceof HeapDistrictController.Cleaned clean) boss.openVulnerability(clean.bossExposeTicks());
            if (event instanceof HeapDistrictController.RefluxArrived arrived) boss.heal(arrived.healAmount());
            if (event instanceof HeapDistrictController.RefluxBroken broken) boss.openVulnerability(broken.bossExposeTicks());
        }
    }
    private static void save(Path out, String name, HeapDistrictController.Snapshot snapshot, Boss boss, boolean compact) throws Exception {
        BufferedImage image = scene(snapshot, boss, compact);
        if (compact) {
            BufferedImage small = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = small.createGraphics();
            try { g.setColor(Color.BLACK); g.fillRect(0, 0, 600, 400); g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR); g.drawImage(image, 0, 12, 600, 375, null); }
            finally { g.dispose(); }
            image = small;
        }
        ImageIO.write(image, "png", out.resolve(name + ".png").toFile());
    }
    private static BufferedImage scene(HeapDistrictController.Snapshot snapshot, Boss boss, boolean compact) {
        BufferedImage image = new BufferedImage(960, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        double camera = boss == null ? 400 : 0, seconds = snapshot.tick() * .016;
        try {
            WORLD.drawBackground(g, 960, 600, 480, camera, seconds, boss != null, false);
            Graphics2D world = (Graphics2D) g.create();
            try {
                world.translate(-camera, 0); DECK.draw(world, 960, 480, camera);
                WORLD.drawWorld(world, snapshot, camera, 960, compact, true, false);
                ActorVisuals.hero(world, art, new ActorVisuals.Hero(boss == null ? 716 : 133, 480, 1,
                        ActorVisuals.Action.IDLE, seconds, 0, 0, false, false, 1));
                for (int i = 0; i < (boss == null ? 3 : 2); i++) HeapActorRenderer.renderLeak(world,
                        new ActorVisuals.Hostile(EntityType.LEAK, camera + 490 + i * 105, 412 - i * 37,
                                44, 44, -1, 220, 220, 0, seconds + i, 0, 0));
                if (boss != null) HeapActorRenderer.renderBoss(world, visual(boss, seconds));
            } finally { world.dispose(); }
            g.setColor(new Color(4, 14, 18, 235)); g.fillRect(16, 16, 928, 66);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, compact ? 22 : 18)); g.setColor(new Color(239, 221, 175));
            g.drawString("HEAP DISTRICT / PRODUCTION RENDERER INSPECTION", 30, 42);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, compact ? 17 : 12));
            g.drawString("Real controller states; controlled art probe, not a GamePanel screenshot.", 30, 67);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, compact ? 17 : 13));
            g.drawString(boss == null ? "GC PIPEWORKS / PRESSURE " + snapshot.pressure() :
                    "MEMORY LEAK / P" + boss.getCombatStage() + " / " + (boss.isVulnerable() ? "CORE EXPOSED" : "SHELL CLOSED"), 30, 114);
        } finally { g.dispose(); }
        return image;
    }
    private static HeapActorRenderer.BossVisual visual(Boss b, double seconds) {
        return new HeapActorRenderer.BossVisual(b.getX(), b.getY(), b.getWidth(), b.getHeight(),
                b.getHp(), b.getMaxHp(), b.getCombatStage(), b.getHitFlashTicks(), b.getVulnerabilityTicks(), seconds, true, false);
    }
    private static void details(Path out) throws Exception {
        BufferedImage image = new BufferedImage(1100, 680, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(15, 28, 30)); g.fillRect(0, 0, 1100, 680);
            for (int col = 0; col < 3; col++) {
                Graphics2D actor = (Graphics2D) g.create();
                try {
                    actor.translate(35 + col * 360, 28); actor.scale(2.5, 2.5);
                    HeapActorRenderer.renderBoss(actor, new HeapActorRenderer.BossVisual(0, 0, 120, 150,
                            600, 1000, col == 2 ? 3 : 1, col == 2 ? 5 : 0, col == 0 ? 0 : 100, 3, true, false));
                } finally { actor.dispose(); }
                g.setColor(new Color(242, 221, 176)); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
                g.drawString(new String[]{"SHELL CLOSED", "GC CORE EXPOSED", "STAGE 3 / HIT"}[col], 35 + col * 360, 429);
            }
            for (int col = 0; col < 4; col++) {
                Graphics2D actor = (Graphics2D) g.create();
                try {
                    actor.translate(60 + col * 260, 467); actor.scale(3, 3);
                    HeapActorRenderer.renderLeak(actor, new ActorVisuals.Hostile(EntityType.LEAK, 0, 0, 44, 44,
                            col == 1 ? 1 : -1, 220, 220, 0, col, col == 2 ? 1 : 0, col == 3 ? .6 : 0));
                } finally { actor.dispose(); }
                g.setColor(new Color(232, 215, 175)); g.drawString(new String[]{"LEAK / LEFT", "LEAK / RIGHT", "LEAK / HIT", "LEAK / DEATH"}[col], 45 + col * 260, 638);
            }
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13)); g.drawString("Boss 2.5x / Leak 3x detail; shipping actor sizes remain 120x150 and 44x44.", 30, 671);
        } finally { g.dispose(); }
        ImageIO.write(image, "png", out.resolve("details-3x.png").toFile());
    }
}
