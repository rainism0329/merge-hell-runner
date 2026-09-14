package com.bigphil.mergehell.combat;

import com.bigphil.mergehell.CollisionSystem;
import com.bigphil.mergehell.GameState;
import com.bigphil.mergehell.model.*;
import com.bigphil.mergehell.progression.*;
import com.bigphil.mergehell.render.ActorVisuals;
import com.bigphil.mergehell.render.IndustrialArt;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** Headless measurements use real Player and Projectile updates, including actual collision culling. */
public final class RunFirePreview {
    private record Mode(String name, int direction, boolean move, boolean upgraded, boolean dash) { }
    private static final List<Mode> MODES = List.of(new Mode("stand_right", 1, false, false, false),
            new Mode("stand_left", -1, false, false, false), new Mode("run_right", 1, true, false, false),
            new Mode("run_left", -1, true, false, false), new Mode("dash_cache_right", 1, true, true, false),
            new Mode("dash_cache_left", -1, true, true, false), new Mode("after_dash_right", 1, true, false, true),
            new Mode("after_dash_left", -1, true, false, true));

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]); Files.createDirectories(out);
        List<String> rows = new ArrayList<>(List.of("weapon,mode,launch_step,player_dx,world_vx,relative_vx,vy,lifetime_ticks,nominal_relative_range,last_visible_tick,last_active_tick,active_relative_distance,removal_tick"));
        for (WeaponId id : WeaponId.values()) for (Mode mode : MODES) rows.add(measure(id, mode));
        Files.write(out.resolve("measurements.csv"), rows);
        render(out);
        System.out.println("Run/fire probe: " + out.toAbsolutePath());
    }

    private static Player player(WeaponId id, Mode mode) {
        Player player = new Player(4000, 450);
        RunBuild build = new RunBuild(id);
        if (mode.upgraded()) for (int i = 0; i < 3; i++) build.apply(UpgradeCatalog.definition(UpgradeId.DASH_CACHE));
        player.setRunBuild(build); player.setCombatSeed(7);
        player.update(mode.direction() < 0, mode.direction() > 0, false, false, 480, 20_000, new ArrayList<>(), List.of());
        if (mode.dash()) player.dash(mode.direction());
        return player;
    }

    private static void tick(Player p, Mode mode, boolean shoot, List<Projectile> bullets) {
        p.update(mode.move() && mode.direction() < 0, mode.move() && mode.direction() > 0,
                false, shoot, 480, 20_000, bullets, List.of());
    }

    private static String measure(WeaponId id, Mode mode) {
        Player player = player(id, mode);
        List<Projectile> bullets = new ArrayList<>();
        int launch = 0; double dx = 0;
        while (bullets.isEmpty() && launch++ < 25) { double old = player.getX(); tick(player, mode, true, bullets); dx = player.getX() - old; }
        Projectile center = bullets.get(bullets.size() / 2);
        double speed = (center.getVx() - dx) * mode.direction();
        int ttl = center.getSpec().effects().lifetimeTicks();
        bullets = new ArrayList<>(List.of(center));
        var collision = new CollisionSystem(); var ctx = new CollisionSystem.Context();
        var enemies = new ObstacleManager();
        double camera = player.getX() - 960 * 0.3;
        int age = 0, visible = 0, active = 0; double distance = 0;
        while (!bullets.isEmpty() && age < 700) {
            // The first projectile step is on its launch tick; later steps include continued motion.
            if (age > 0) tick(player, mode, false, new ArrayList<>());
            camera += (player.getX() - 960 * 0.3 - camera) * 0.15;
            collision.process(ctx, bullets, enemies, null, player, GameState.RUNNING, 960, 600, camera,
                    new ArrayList<>(), new ArrayList<>(), ignored -> { });
            age++;
            if (!bullets.isEmpty()) {
                active = age;
                distance = (center.getX() - (player.getX() + (mode.direction() > 0 ? 30 : 0))) * mode.direction();
                if (center.getX() + center.getBounds().width >= camera && center.getX() <= camera + 960) visible = age;
            }
        }
        return String.format(Locale.ROOT, "%s,%s,%d,%.3f,%.3f,%.3f,%.3f,%d,%.3f,%d,%d,%.3f,%d",
                id, mode.name(), launch, dx, center.getVx(), speed, center.getVy(), ttl, speed * ttl,
                visible, active, distance, age);
    }

    private static void render(Path out) throws Exception {
        IndustrialArt art = IndustrialArt.load();
        for (int age : new int[]{1, 8, 16, 24}) {
            BufferedImage image = new BufferedImage(1440, 720, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setColor(new Color(23, 31, 39)); g.fillRect(0, 0, 1440, 720);
            int row = 0;
            for (WeaponId id : WeaponId.values()) for (int column = 0; column < 3; column++) {
                Mode mode = column == 0 ? MODES.get(0) : column == 1 ? MODES.get(2) : MODES.get(3);
                Player player = player(id, mode); List<Projectile> bullets = new ArrayList<>();
                ActorVisuals rig = new ActorVisuals(); rig.update(player, List.of(), 0);
                while (bullets.isEmpty()) { tick(player, mode, true, bullets); rig.update(player, List.of(), 0.016); }
                for (int step = 0; step < age; step++) {
                    if (step > 0) tick(player, mode, false, new ArrayList<>());
                    bullets.forEach(Projectile::update); rig.update(player, List.of(), 0.016);
                }
                int top = row * 120;
                Graphics2D cell = (Graphics2D) g.create();
                cell.translate(column * 480, top); cell.clipRect(0, 0, 480, 120);
                cell.setColor(new Color(64, 77, 89)); cell.drawRect(0, 0, 479, 119);
                cell.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12)); cell.setColor(Color.WHITE);
                cell.drawString(id + " / " + mode.name() + " / age " + age, 10, 18);
                // Follow the actor to display true muzzle-relative travel; this is not a UI camera capture.
                cell.scale(0.8, 0.8);
                double anchor = mode.direction() > 0 ? 60 : 535;
                cell.translate(anchor - player.getX(), 125 - 480);
                cell.setColor(new Color(66, 127, 103)); cell.drawLine((int)player.getX()-600,480,(int)player.getX()+600,480);
                ActorVisuals.hero(cell, art, rig.snapshot().hero());
                for (Projectile bullet : bullets) if (!bullet.isDead()) bullet.draw(cell);
                cell.dispose();
                if (column == 2) row++;
            }
            g.dispose(); ImageIO.write(image, "png", out.resolve("age-%02d.png".formatted(age)).toFile());
        }
        Files.writeString(out.resolve("README.md"), "Real Player firing/update probe. CSV uses production CollisionSystem culling with a 960px camera following at 0.15. Images use actor-relative framing to compare flight; they are not native UI captures. No TTL, gun art, hitbox or damage is synthesized. Dash Cache changes cooldown, not run speed; no weapon fires during the eight dash ticks. Nominal range is velocity times TTL; final lifetime tick is removed by the existing collision order.\n");
    }
}
