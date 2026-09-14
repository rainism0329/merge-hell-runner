package com.bigphil.mergehell.render;

import com.bigphil.mergehell.combat.WeaponId;
import com.bigphil.mergehell.model.*;
import com.bigphil.mergehell.progression.RunBuild;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Headless rig inspection, using production ActorVisuals and real Player simulation for motion.
 * Run with production classes and src/main/resources on the classpath. No native window or audio.
 * Outputs go only to the supplied directory (default build/rig-validation/current).
 */
public final class RigVisualPreview {
    private static final double STEP = 0.016;
    private static final int CELL_W = 280, CELL_H = 260;
    private static final Color BG = new Color(25, 31, 37);
    private static final Font LABEL = new Font(Font.MONOSPACED, Font.PLAIN, 14);

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length == 0 ? "build/rig-validation/current" : args[0]);
        Files.createDirectories(output);
        IndustrialArt art = IndustrialArt.load();
        if (!art.has("repair") || !art.has("hostiles"))
            throw new IllegalStateException("Both real atlases are required: " + art.diagnostics());
        heroPoses(output, art);
        gait(output, art);
        enemies(output, art);
        weapons(output, art);
        motion(output, art);
        Files.writeString(output.resolve("README.md"), """
                # Rig inspection

                Production ActorVisuals renders every actor. The static sheets are labeled, controlled
                pose inspections; motion/ contains 180 consecutive real Player updates at 16 ms each.
                Yellow crosses mark the unchanged shot origin; green lines mark the physics floor;
                cyan rectangles mark the unchanged hurtbox. Guides are inspection overlays only.
                Weapon previews show six real projectile patterns with the SAME cannon art.
                This does not claim six separately authored guns, native UI smoothness or audio evidence.
                Open index.html to step through the motion strip. Regenerate after changing atlas metadata.
                """, StandardCharsets.UTF_8);
        System.out.println("Rig preview: " + output.toAbsolutePath());
    }

    private static void heroPoses(Path output, IndustrialArt art) throws Exception {
        BufferedImage image = sheet(6, 3);
        Graphics2D g = image.createGraphics();
        for (ActorVisuals.Action action : ActorVisuals.Action.values()) for (int facing : new int[]{1, -1}) {
            int index = action.ordinal() * 2 + (facing < 0 ? 1 : 0);
            double phase = action == ActorVisuals.Action.MELEE ? 0.5 : action == ActorVisuals.Action.DEAD ? 1 : 0.7;
            var pose = new ActorVisuals.Hero(0, 0, facing, action, phase,
                    action == ActorVisuals.Action.IDLE ? 1 : 0, action == ActorVisuals.Action.LAND ? 1 : 0.8,
                    false, false, 1);
            heroCell(g, art, index, 6, action + (facing > 0 ? " / RIGHT" : " / LEFT"), pose);
        }
        g.dispose();
        ImageIO.write(image, "png", output.resolve("hero-poses.png").toFile());
    }

    private static void gait(Path output, IndustrialArt art) throws Exception {
        BufferedImage image = sheet(6, 3);
        Graphics2D g = image.createGraphics();
        for (int i = 0; i < 12; i++) {
            var pose = new ActorVisuals.Hero(0, 0, 1, ActorVisuals.Action.RUN, Math.PI * 2 * i / 12,
                    0, 0, false, false, 1);
            heroCell(g, art, i, 6, "RUN / " + i + " of 12", pose);
        }
        for (int i = 0; i < 6; i++) {
            var pose = new ActorVisuals.Hero(0, 0, 1, ActorVisuals.Action.MELEE, i / 5.0,
                    0, 0, false, false, 1);
            heroCell(g, art, 12 + i, 6, "MELEE / " + i + " of 5", pose);
        }
        g.dispose();
        ImageIO.write(image, "png", output.resolve("hero-gait.png").toFile());
    }

    private static void enemies(Path output, IndustrialArt art) throws Exception {
        BufferedImage image = sheet(6, 3);
        Graphics2D g = image.createGraphics();
        for (int i = 0; i < 18; i++) {
            EntityType type = i < 6 ? EntityType.BUG : EntityType.TECHDEBT;
            int facing = i % 2 == 0 ? -1 : 1;
            double death = i >= 12 ? (i - 11) / 7.0 : 0;
            Graphics2D cell = cell(g, i, 6, type + (facing > 0 ? " / RIGHT" : " / LEFT") + (death > 0 ? " / DEATH" : ""));
            double scale = type == EntityType.BUG ? 3.4 : 1.8;
            cell.translate(CELL_W / 2.0, CELL_H - 28); cell.scale(scale, scale);
            guides(cell, type.width, type.height, false, facing);
            ActorVisuals.enemy(cell, art, new ActorVisuals.Hostile(type, -type.width / 2.0, -type.height,
                    type.width, type.height, facing, type.maxHp, type.maxHp, i % 3 == 0 ? 12 : 0,
                    Math.PI * i / 3, 0, death));
            cell.dispose();
        }
        g.dispose();
        ImageIO.write(image, "png", output.resolve("enemy-poses.png").toFile());
    }

    private static void weapons(Path output, IndustrialArt art) throws Exception {
        BufferedImage image = sheet(3, 2);
        Graphics2D g = image.createGraphics();
        for (WeaponId id : WeaponId.values()) {
            Player player = new Player(0, 0);
            player.setRunBuild(new RunBuild(id)); player.setCombatSeed(71);
            List<Projectile> shots = new ArrayList<>();
            ActorVisuals rig = new ActorVisuals(); rig.update(player, List.of(), 0);
            for (int tick = 0; tick < 12 && player.getShotSequence() == 0; tick++) {
                player.update(false, false, false, true, 30, 1_000, shots, List.of());
                rig.update(player, List.of(), STEP);
            }
            Graphics2D cell = cell(g, id.ordinal(), 3, id + " / SHARED CANNON");
            cell.translate(75, CELL_H - 40); cell.scale(2.6, 2.6); cell.translate(-15, -30);
            ActorVisuals.hero(cell, art, rig.snapshot().hero());
            for (Projectile shot : shots) {
                shot.update(); shot.update();
                shot.draw(cell);
            }
            cell.dispose();
        }
        g.dispose();
        ImageIO.write(image, "png", output.resolve("six-weapons.png").toFile());
    }

    private static void motion(Path output, IndustrialArt art) throws Exception {
        Path frames = output.resolve("motion"); Files.createDirectories(frames);
        Player player = new Player(100, 120); player.setCombatSeed(713);
        ActorVisuals rig = new ActorVisuals();
        List<Projectile> shots = new ArrayList<>();
        List<ObstacleManager.Enemy> enemies = new ArrayList<>(List.of(
                new ObstacleManager.Enemy(460, 110, EntityType.BUG, 18L),
                new ObstacleManager.Enemy(590, 70, EntityType.TECHDEBT, 19L)));
        List<String> manifest = new ArrayList<>(List.of("frame,action,x,y,facing,shotSequence,meleeProgress"));
        rig.update(player, enemies, 0);
        for (int frame = 0; frame < 180; frame++) {
            if (frame == 48 || frame == 65) player.requestJump();
            if (frame == 99) player.dash(1);
            if (frame == 127) player.melee();
            if (frame == 144) { player.setInvincibleTimer(0); player.takeDamage(10); }
            if (frame == 162) { player.setInvincibleTimer(0); player.takeDamage(200); }
            if (player.getHp() > 0) player.update(frame >= 110 && frame < 125,
                    frame >= 20 && frame < 99, false, frame >= 20 && frame < 44,
                    150, 4_000, shots, List.of());
            for (var enemy : enemies) enemy.update(0.15, player.getX());
            if (frame == 150) enemies.get(0).setDead(true);
            shots.removeIf(Projectile::isDead); shots.forEach(Projectile::update);
            rig.update(player, enemies, STEP);
            BufferedImage image = new BufferedImage(960, 360, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics(); g.setColor(BG); g.fillRect(0, 0, 960, 360);
            g.setFont(LABEL); g.setColor(Color.WHITE);
            g.drawString("REAL PLAYER TICK " + frame + " / " + rig.snapshot().hero().action() + " / 16 ms", 20, 25);
            g.drawString("No collision resizing. Motion inspection only; death held for rig inspection.", 20, 345);
            g.scale(2, 2); g.translate(-Math.max(0, player.getX() - 190), 0);
            g.setColor(new Color(55, 125, 98)); g.drawLine(0, 150, 4_000, 150);
            ActorVisuals.hero(g, art, rig.snapshot().hero());
            for (var enemy : rig.snapshot().enemies()) ActorVisuals.enemy(g, art, enemy);
            shots.forEach(shot -> shot.draw(g));
            g.dispose();
            ImageIO.write(image, "png", frames.resolve("frame-%03d.png".formatted(frame)).toFile());
            manifest.add(frame + "," + rig.snapshot().hero().action() + "," + player.getX() + "," + player.getY()
                    + "," + player.getFacingDir() + "," + player.getShotSequence() + "," + player.getMeleeProgress());
        }
        Files.write(output.resolve("motion.csv"), manifest, StandardCharsets.UTF_8);
        Files.writeString(output.resolve("index.html"), """
                <!doctype html><html lang="en"><meta charset="utf-8"><title>Production rig inspection</title>
                <style>body{background:#192025;color:#eef5f1;font:16px system-ui;max-width:1000px;margin:30px auto}img{width:100%;image-rendering:auto}button,input{margin:8px}input{width:65%}</style>
                <h1>Production rig inspection</h1><p>Actual Player + ActorVisuals updates. Same cannon art for all six weapons.</p>
                <img id="motion" src="motion/frame-000.png"><button id="play">Play / pause</button><input id="seek" type="range" min="0" max="179" value="0"><span id="label">0</span>
                <script>let n=0,playing=false;const image=document.querySelector('#motion'),seek=document.querySelector('#seek');
                function show(){image.src='motion/frame-'+String(n).padStart(3,'0')+'.png';seek.value=n;document.querySelector('#label').textContent=n}
                document.querySelector('#play').onclick=()=>playing=!playing;seek.oninput=()=>{playing=false;n=+seek.value;show()};setInterval(()=>{if(playing){n=(n+1)%180;show()}},16)</script>
                <h2>Pose inspection</h2><img src="hero-poses.png"><img src="hero-gait.png"><img src="enemy-poses.png"><img src="six-weapons.png">
                </html>
                """, StandardCharsets.UTF_8);
    }

    private static BufferedImage sheet(int columns, int rows) {
        BufferedImage image = new BufferedImage(columns * CELL_W, rows * CELL_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics(); g.setColor(BG); g.fillRect(0, 0, image.getWidth(), image.getHeight()); g.dispose();
        return image;
    }

    private static Graphics2D cell(Graphics2D original, int index, int columns, String title) {
        Graphics2D g = (Graphics2D) original.create();
        g.translate((index % columns) * CELL_W, (index / columns) * CELL_H);
        g.clipRect(0, 0, CELL_W, CELL_H);
        g.setColor(new Color(52, 62, 69)); g.drawRect(0, 0, CELL_W - 1, CELL_H - 1);
        g.setFont(LABEL); g.setColor(Color.WHITE); g.drawString(title, 10, 22);
        return g;
    }

    private static void heroCell(Graphics2D target, IndustrialArt art, int index, int columns,
                                 String title, ActorVisuals.Hero pose) {
        Graphics2D g = cell(target, index, columns, title);
        g.translate(CELL_W / 2.0, CELL_H - 35);
        double scale = pose.action() == ActorVisuals.Action.DEAD ? 2.5 : 3.5;
        g.scale(scale, scale);
        guides(g, 30, 30, true, pose.facing());
        ActorVisuals.hero(g, art, pose);
        g.dispose();
    }

    private static void guides(Graphics2D g, double width, double height, boolean muzzle, int facing) {
        g.setStroke(new BasicStroke(0.35f));
        g.setColor(new Color(65, 164, 124)); g.drawLine(-60, 0, 60, 0);
        g.setColor(new Color(50, 103, 121)); g.drawRect((int) (-width / 2), -(int) height, (int) width, (int) height);
        if (muzzle) {
            g.setColor(new Color(239, 186, 61)); int x = facing * 15;
            g.drawLine(x - 4, -15, x + 4, -15); g.drawLine(x, -19, x, -11);
        }
    }
}
