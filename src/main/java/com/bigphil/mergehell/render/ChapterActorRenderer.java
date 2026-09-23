package com.bigphil.mergehell.render;
import com.bigphil.mergehell.i18n.GameText;
import com.bigphil.mergehell.model.Boss;
import com.bigphil.mergehell.model.EntityType;
import com.bigphil.mergehell.model.ObstacleManager;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/** Painted, articulated chapter families. Every dangerous mark is sourced from simulation values. */
public final class ChapterActorRenderer {
    private static final Color GOLD = new Color(245, 189, 83);
    private static final Color HEAT = new Color(255, 129, 48);
    private static final Color SPORE = new Color(192, 226, 106);
    private static final Color DARK = new Color(18, 23, 30);
    private static final Font PART_FONT = new Font("SansSerif", Font.BOLD, 10);
    private ChapterActorRenderer() { }
    public static void preload() { ChapterArt.preload(); }

    public static boolean enemy(Graphics2D target, ActorVisuals.Hostile pose, double seconds, boolean flashes) {
        int level = enemyLevel(pose.type());
        if (level < 0) return false;
        ChapterArt art = ChapterArt.load();
        if (!art.hasLevel(level)) return false;
        Graphics2D g = (Graphics2D) target.create();
        try {
            quality(g);

            var tactics = pose.tactics();
            boolean buried = tactics.burrowed();
            g.setColor(new Color(7, 12, 18, 85));
            if(pose.climbDirection()==0)
                g.fill(new Ellipse2D.Double(pose.x() + 3, pose.y() + pose.height() - 3, pose.width() - 6, 7));
            ActorVisuals.wallContact(g,pose);
            if (buried) {
                enemyWarning(g, pose, level);
                // Only the exposed drill crest is visible while the actual enemy is below ground.
                Graphics2D buriedArt = (Graphics2D) g.create();
                try {
                    buriedArt.clip(new Rectangle((int) pose.x(), (int) (pose.y() + pose.height() - 11),
                            (int) pose.width(), 13));
                    mirroredFit(buriedArt, art, level, enemyPart(pose.type()), pose.x(),
                            pose.y() + pose.height() - 18, pose.width(), pose.height(), pose.facing());
                } finally { buriedArt.dispose(); }
                g.setColor(new Color(146, 105, 72, 170));
                for (int i = 0; i < 5; i++) g.fillOval((int) pose.x() + i * 16,
                        (int) (pose.y() + pose.height() - 4 + Math.sin(pose.phase() + i) * 2), 8, 4);
                return true;
            }
            Graphics2D body = (Graphics2D) g.create();
            try {
                if (pose.death() > 0) {
                    body.setComposite(AlphaComposite.SrcOver.derive((float) Math.max(0, 1 - pose.death())));
                    body.rotate(pose.facing() * pose.death() * .5, pose.x() + pose.width() / 2, pose.y() + pose.height());
                    body.translate(0, pose.death() * 10);
                } else if (pose.hit() > 0) body.translate(-pose.facing() * pose.hit() * 1.6, 0);
                mirroredFit(body, art, level, enemyPart(pose.type()), pose.x(), pose.y(),
                        pose.width(), pose.height(), pose.facing());
                if (pose.type() == EntityType.RIGGER || pose.type() == EntityType.INTERRUPT) {
                    body.setStroke(new BasicStroke(1)); body.setColor(new Color(236, 222, 172, 130));
                    double rotor = pose.width() * .23;
                    for (int side = 0; side < 2; side++) {
                        double cx = pose.x() + pose.width() * (side == 0 ? .26 : .74);
                        body.draw(new Ellipse2D.Double(cx - rotor, pose.y() + 2, rotor * 2, 5));
                    }
                }
                if (flashes && pose.hit() > .2) {
                    body.setStroke(new BasicStroke(1.5f)); body.setColor(new Color(255, 237, 178, (int) (pose.hit() * 190)));
                    body.draw(new RoundRectangle2D.Double(pose.x() + 3, pose.y() + 3, pose.width() - 6, pose.height() - 6, 10, 10));
                }
            } finally { body.dispose(); }
            if (pose.death() > 0) return true;
            enemyWarning(g, pose, level);
            if (tactics.shielded()) {
                double sx = pose.facing() < 0 ? pose.x() + 3 : pose.x() + pose.width() - 3;
                g.setStroke(new BasicStroke(4)); g.setColor(new Color(81, 207, 238, 70));
                g.draw(new Line2D.Double(sx, pose.y() + 9, sx, pose.y() + pose.height() - 6));
                g.setStroke(new BasicStroke(1.5f)); g.setColor(new Color(141, 228, 243));
                g.draw(new Line2D.Double(sx, pose.y() + 9, sx, pose.y() + pose.height() - 6));
            }
            if (EnemyWarningRenderer.showHealth(pose)) {
                meter(g, pose.x(), pose.y() - 6, pose.width(), pose.hp() / (double) Math.max(1, pose.maxHp()), accent(level));
            }
        } finally { g.dispose(); }
        return true;
    }

    private static int enemyLevel(EntityType type) {
        return switch (type) {
            case SENTINEL, WARDEN, RIGGER -> 2;
            case INTERRUPT, DRILLER, SLAG_SPITTER -> 3;
            case MIRROR, SPORE_POD, LURKER -> 4;
            default -> -1;
        };
    }
    private static String enemyPart(EntityType type) {
        return switch (type) {
            case SENTINEL, INTERRUPT, MIRROR -> "enemy-a";
            case WARDEN, DRILLER, SPORE_POD -> "enemy-b";
            default -> "enemy-c";
        };
    }

    private static void enemyWarning(Graphics2D g, ActorVisuals.Hostile pose, int level) {
        EnemyWarningRenderer.render(g, pose, accent(level));
    }

    public static void boss(Graphics2D target, Boss boss, double seconds, boolean flashes, boolean highContrast) {
        if (boss == null || !boss.hasMultipartEncounter()) return;
        ChapterArt art = ChapterArt.load(); int level = boss.getBossLevel();
        Graphics2D g = (Graphics2D) target.create();
        try {
            quality(g);
            bossWarnings(g, boss, highContrast);
            g.setColor(new Color(5, 10, 15, 100));
            g.fill(new Ellipse2D.Double(boss.getX() + 12, boss.getY() + boss.getHeight() - 5,
                    boss.getWidth() - 24, 11));
            if (level == 2) gantry(g, art, boss, seconds, flashes);
            else if (level == 3) siege(g, art, boss, seconds, flashes);
            else rootheart(g, art, boss, seconds, flashes);
            for (var part : boss.getParts()) partStatus(g, part, level, highContrast);
        } finally { g.dispose(); }
    }

    private static void gantry(Graphics2D g, ChapterArt art, Boss boss, double seconds, boolean flashes) {
        double x = boss.getX(), y = boss.getY();
        // The stationary architect hangs from a real hoist, with layered rigging behind its body.
        double railY = Math.min(y - 35, Math.max(80, y - 120));
        double postX = x + 316;
        // The same painted steel, braces and hoists used by the route form the gantry.
        // These are scenery supports, not additional combat parts or invisible obstacles.
        art.fitTerrain(g, 2, "support", postX - 40, railY + 20, 80, boss.getGroundY() - railY - 20);
        art.terrain(g, 2, "platform", x - 44, railY - 8, 400, 72);
        for (int side = 0; side < 2; side++) {
            double hx = x + (side == 0 ? 40 : 238);
            art.fitTerrain(g, 2, "prop-b", hx - 23, railY + 34, 46, 48);
            for (int cable = -1; cable <= 1; cable += 2) {
                double cx = hx + cable * 8;
                g.setStroke(new BasicStroke(5)); g.setColor(new Color(24, 36, 43));
                g.draw(new Line2D.Double(cx, railY + 78, cx, y + 22));
                g.setStroke(new BasicStroke(1.2f)); g.setColor(new Color(151, 168, 171));
                g.draw(new Line2D.Double(cx - 1, railY + 78, cx - 1, y + 22));
                for (int groove = (int) railY + 82; groove < y + 18; groove += 11)
                    g.draw(new Line2D.Double(cx - 2, groove + 2, cx + 2, groove));
            }
        }
        for (var part : boss.getParts()) if (!part.id().equals("core")) {
            boolean right = part.id().startsWith("right");
            double jx = x + (right ? 244 : 30), jy = y + 78;
            if (!part.destroyed()) {
                var b = part.bounds();
                mechanicalLink(g, jx, jy, b.x() + 29, b.y() + 11, right);
                mirroredFit(g, art, 2, "boss-limb", b.x(), b.y(), b.width(), b.height(), right ? 1 : -1);
                if (flashes && boss.getHitFlashTicks() > 0) spark(g, b.x() + b.width() / 2, b.y() + 30, GOLD, boss.getHitFlashTicks());
            } else {
                brokenJoint(g, jx, jy, GOLD);
                rebuildPart(g, art, 2, part, boss.getRebuildProgress(), right ? 1 : -1);
            }
        }
        art.fitPart(g, 2, "boss-shell", x, y, 280, 140);
        for (var tell : boss.getAttackTelegraphs()) if (tell.active()) {
            var b = tell.bounds();
            if (boss.getEncounterAction().equals("GIRDER_FALL")) {
                g.setPaint(new GradientPaint((float) b.x(), 0, new Color(100, 112, 114),
                        (float) (b.x() + b.width()), 0, new Color(31, 44, 51)));
                g.fill(new Rectangle((int) b.x() + 5, (int) b.y(), (int) b.width() - 10, (int) b.height()));
                g.setColor(new Color(16, 28, 33)); g.fillRect((int) b.x() + 18, (int) b.y(), (int) b.width() - 36, (int) b.height());
                g.setStroke(new BasicStroke(3)); g.setColor(new Color(200, 195, 167));
                g.draw(new Line2D.Double(b.x() + 8, b.y(), b.x() + 8, b.y() + b.height()));
                g.draw(new Line2D.Double(b.x() + b.width() - 9, b.y(), b.x() + b.width() - 9, b.y() + b.height()));
                for (int bolt = (int) b.y() + 12; bolt < b.y() + b.height(); bolt += 32) {
                    g.setColor(new Color(215, 167, 74));
                    g.fillOval((int) b.x() + 9, bolt, 4, 4); g.fillOval((int) (b.x() + b.width()) - 14, bolt, 4, 4);
                }
                g.setColor(GOLD); g.fillRect((int) b.x(), (int) (b.y() + b.height()) - 10, (int) b.width(), 10);
                g.setColor(DARK); g.setStroke(new BasicStroke(5));
                for (int mark = 2; mark < b.width(); mark += 16) g.drawLine((int) b.x() + mark,
                        (int) (b.y() + b.height()) - 1, (int) b.x() + mark + 8, (int) (b.y() + b.height()) - 9);
            } else if (boss.getEncounterAction().equals("CROSSBEAM")
                    || boss.getEncounterAction().equals("LEFT_SWEEP") || boss.getEncounterAction().equals("RIGHT_SWEEP")) {
                double cy = b.y() + b.height() / 2;
                g.setColor(new Color(255, 165, 58, 150)); g.setStroke(new BasicStroke(18));
                g.draw(new Line2D.Double(b.x(), cy, b.x() + b.width(), cy));
                g.setColor(new Color(255, 226, 156)); g.setStroke(new BasicStroke(7));
                g.draw(new Line2D.Double(b.x(), cy, b.x() + b.width(), cy));
                g.setColor(new Color(255, 253, 226)); g.setStroke(new BasicStroke(2));
                g.draw(new Line2D.Double(b.x(), cy, b.x() + b.width(), cy));
            } else if (boss.getEncounterAction().equals("GANTRY_LOCK")) {
                // The circuit appears only inside the committed dangerous half; the
                // unpainted central corridor remains easy to read at IDE window sizes.
                g.setColor(new Color(249, 194, 91, 175)); g.setStroke(new BasicStroke(2));
                for (double cable = b.x() + 12; cable < b.x() + b.width(); cable += 30)
                    g.draw(new Line2D.Double(cable, b.y(), cable, b.y() + b.height()));
                g.setColor(new Color(245, 224, 167, 150)); g.setStroke(new BasicStroke(1));
                for (double bar = b.y() + 20; bar < b.y() + b.height(); bar += 36)
                    g.draw(new Line2D.Double(b.x(), bar, b.x() + b.width(), bar));
            }
        }
        if (boss.getEncounterAction().endsWith("SWEEP") || boss.getEncounterAction().equals("GANTRY_LOCK")) {
            boolean lock = boss.getEncounterAction().equals("GANTRY_LOCK");
            for (var arm : boss.getParts()) if (!arm.destroyed() && !arm.id().equals("core")) {
                boolean right = arm.id().startsWith("right");
                if (!lock && right != boss.getEncounterAction().startsWith("RIGHT")) continue;
                var b = arm.bounds();
                targetBrackets(g, b, GOLD, 1.5f);
                var tells = boss.getAttackTelegraphs();
                int armIndex = right && part(boss, "left-arm") != null && !part(boss, "left-arm").destroyed() ? 1 : 0;
                for (int i = 0; i < tells.size(); i++) {
                    var tell = tells.get(i);
                    var region = tell.bounds();
                    if (lock && i != armIndex) continue;
                    g.setStroke(new BasicStroke(tell.active() ? 2.5f : 1.2f));
                    g.setColor(new Color(255, 212, 119, tell.active() ? 220 : 115));
                    g.draw(new Line2D.Double(b.x() + b.width() / 2, b.y() + b.height() - 14,
                            region.x() + region.width() / 2, region.y() + (lock ? 6 : region.height() / 2)));
                }
            }
        }
        var core = part(boss, "core");
        if (core != null) {
            component(g, art, 2, "boss-core", core, core.weak() ? 1 : .56f);
            if (!core.weak()) shutters(g, core.bounds(), new Color(62, 69, 70, 180));
            else targetBrackets(g, core.bounds(), GOLD, 2);
        }
    }

    private static void siege(Graphics2D g, ChapterArt art, Boss boss, double seconds, boolean flashes) {
        double x = boss.getX(), y = boss.getY(); int facing = boss.getDashDirection() > 0 ? 1 : -1;
        siegeTrail(g, boss);
        mirroredFit(g, art, 3, "boss-shell", x, y + 38, 240, 122, facing);
        Boss.PartView plate = part(boss, "armor-plate"), vent = part(boss, "heat-vent"), core = part(boss, "core");
        if (plate != null && !plate.destroyed()) {
            var b = plate.bounds();
            mechanicalLink(g, x + (facing < 0 ? 34 : 206), y + 70, b.x() + b.width() / 2, b.y() + 10, facing > 0);
            mirroredFit(g, art, 3, "boss-limb", b.x(), b.y(), b.width(), b.height(), facing);
        } else if (plate != null) {
            var b = plate.bounds();
            brokenJoint(g, b.x() + b.width() / 2, b.y() + 45, HEAT);
            // Broken drive links remain visible after the temporary hit flash ends.
            g.setStroke(new BasicStroke(4)); g.setColor(new Color(29, 24, 21));
            for (int i = 0; i < 4; i++) {
                double tx = x + (facing < 0 ? 20 + i * 15 : 220 - i * 15);
                g.draw(new Line2D.Double(tx - 5, y + 146, tx + 3, y + 157));
            }
        }
        if (vent != null) {
            var b = vent.bounds();
            g.setColor(new Color(29, 26, 24)); g.setStroke(new BasicStroke(8, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(x + (facing < 0 ? 231 : 9), y + 83, b.x() + b.width() / 2, b.y() + 10));
            component(g, art, 3, "boss-core", vent, vent.weak() ? 1 : .58f);
            if (vent.weak()) {
                g.setColor(new Color(254, 206, 105, 135)); g.setStroke(new BasicStroke(1.2f));
                for (int i = 0; i < 3; i++) {
                    double rise = (boss.getCombatTick() * 1.1 + i * 11) % 35;
                    g.draw(new Line2D.Double(b.x() + 10 + i * 12, b.y() - rise,
                            b.x() + 14 + i * 12, b.y() - rise - 8));
                }
                targetBrackets(g, b, HEAT, 1.4f);
            }
        }
        if (core != null && core.weak()) {
            var b = core.bounds();
            g.setColor(new Color(36, 23, 16, 240)); g.fill(new RoundRectangle2D.Double(b.x() + 9, b.y() + 4, b.width() - 18, b.height() - 8, 13, 13));
            component(g, art, 3, "boss-core", core, 1);
            targetBrackets(g, b, HEAT, 2);
        }
        if (boss.getEncounterAction().equals("VENT_PURGE")) {
            for (var tell : boss.getAttackTelegraphs()) if (!tell.id().equals("slag-trail")) {
                var b = tell.bounds(); double cy = b.y() + b.height() / 2;
                if (tell.active()) {
                    g.setColor(new Color(232, 167, 104, 175)); g.setStroke(new BasicStroke(18));
                    g.draw(new Line2D.Double(b.x(), cy, b.x() + b.width(), cy));
                    g.setColor(new Color(255, 232, 188, 230)); g.setStroke(new BasicStroke(6));
                    g.draw(new Line2D.Double(b.x(), cy, b.x() + b.width(), cy));
                    for (double px = b.x() + 8; px < b.x() + b.width(); px += 28) {
                        g.setColor(new Color(253, 238, 212, 150)); g.setStroke(new BasicStroke(1.5f));
                        g.draw(new Ellipse2D.Double(px, b.y() + 3, 19, b.height() - 6));
                    }
                } else if (vent != null) {
                    var v = vent.bounds();
                    double r = 7 + 8 * (1 - tell.warningTicks() / (double) tell.fullWarningTicks());
                    g.setColor(new Color(255, 214, 155)); g.setStroke(new BasicStroke(2));
                    g.draw(new Ellipse2D.Double(v.x() + v.width() / 2 - r, cy - r, 2 * r, 2 * r));
                }
            }
        }
        if (boss.isDashing()) {
            g.setStroke(new BasicStroke(2)); g.setColor(new Color(232, 164, 87, 130));
            for (int i = 0; i < 6; i++) {
                int d = (int) ((boss.getCombatTick() * 4L + i * 13) % 55);
                g.drawLine((int) (x + (facing < 0 ? 235 + d : 5 - d)), (int) y + 158,
                        (int) (x + (facing < 0 ? 240 + d : -d)), (int) y + 152 - i % 3 * 3);
            }
        }
        if (flashes && boss.getHitFlashTicks() > 0) spark(g, x + 118, y + 84, HEAT, boss.getHitFlashTicks());
    }

    private static void siegeTrail(Graphics2D g, Boss boss) {
        for (var tell : boss.getAttackTelegraphs()) if (tell.id().equals("slag-trail")) {
            var b = tell.bounds();
            g.setColor(new Color(47, 38, 31)); g.fill(b.rectangle());
            // Split steel and tread impressions distinguish this temporary heat strip
            // from the route's flowing lava. Its top never exceeds the damage volume.
            for (int i = 0; i < 6; i++) {
                double sx = b.x() + 7 + i * 20;
                Path2D crack = new Path2D.Double(); crack.moveTo(sx - 5, b.y() + b.height() - 2);
                crack.lineTo(sx + 4, b.y() + 9); crack.lineTo(sx - 1, b.y() + 5); crack.lineTo(sx + 8, b.y() + 1);
                g.setStroke(new BasicStroke(tell.active() ? 3 : 1));
                g.setColor(tell.active() ? new Color(255, 168, 63) : new Color(190, 110, 52)); g.draw(crack);
                if (tell.active()) {
                    g.setStroke(new BasicStroke(1)); g.setColor(new Color(255, 239, 165)); g.draw(crack);
                }
            }
            g.setColor(tell.active() ? new Color(255, 210, 102) : new Color(199, 144, 75));
            g.setStroke(new BasicStroke(2));
            g.draw(new Line2D.Double(b.x(), b.y() + b.height() - 1, b.x() + b.width(), b.y() + b.height() - 1));
        }
    }

    private static void rootheart(Graphics2D g, ChapterArt art, Boss boss, double seconds, boolean flashes) {
        double x = boss.getX(), y = boss.getY();
        int stage = boss.getCombatStage();
        nestRoots(g, boss, stage);
        if (stage < 3) art.fitPart(g, 4, "boss-shell", x, y, 270, 220);
        else {
            // Shed ribs stay beside the exposed crawling heart. The center is visibly open.
            for (int side = 0; side < 2; side++) {
                Graphics2D remnant = (Graphics2D) g.create();
                try {
                    remnant.setComposite(AlphaComposite.SrcOver.derive(.62f));
                    remnant.clip(new Rectangle((int) (x + (side == 0 ? 0 : 194)), (int) y, 76, 220));
                    art.fitPart(remnant, 4, "boss-shell", x + (side == 0 ? -14 : 14), y + 28, 270, 198);
                } finally { remnant.dispose(); }
            }
        }
        for (var organ : boss.getParts()) if (!organ.id().equals("core")) {
            boolean right = organ.id().startsWith("right"); var b = organ.bounds();
            if (organ.destroyed()) {
                if (stage < 3) {
                    severedStem(g, b.x() + b.width() / 2, b.y() + 14);
                    rebuildPart(g, art, 4, organ, boss.getRebuildProgress(), right ? 1 : -1);
                }
                continue;
            }
            organicLink(g, x + (right ? 222 : 50), y + 113, b.x() + b.width() / 2, b.y() + 10, right, false);
            mirroredFit(g, art, 4, "boss-limb", b.x(), b.y(), b.width(), b.height(), right ? 1 : -1);
            if (boss.getEncounterAction().equals("HATCH")) {
                targetBrackets(g, b, SPORE, 1.5f);
                g.setColor(new Color(208, 235, 139, 160));
                g.drawOval((int) b.x() + 13, (int) b.y() + 29, 37, 38);
            }
        }
        var core = part(boss, "core");
        if (core != null) {
            component(g, art, 4, "boss-core", core, core.weak() ? 1 : .57f);
            if (core.weak()) targetBrackets(g, core.bounds(), SPORE, 2);
            else {
                g.setStroke(new BasicStroke(4)); g.setColor(new Color(109, 103, 123, 175));
                var b = core.bounds();
                for (int i = 0; i < 4; i++) {
                    double by = b.y() + 15 + i * 26;
                    g.draw(new Line2D.Double(b.x() + 12, by, b.x() + b.width() - 13, by + 14));
                }
            }
        }
        if (stage == 2) {
            g.setColor(new Color(228, 239, 154, 165)); g.setStroke(new BasicStroke(1.3f));
            for (int side = 0; side < 2; side++) {
                double sx = x + (side == 0 ? 55 : 215);
                Path2D crack = new Path2D.Double(); crack.moveTo(sx, y + 45); crack.lineTo(sx - 8, y + 72);
                crack.lineTo(sx + 5, y + 96); crack.lineTo(sx - 4, y + 124); g.draw(crack);
            }
        }
        if (boss.getEncounterAction().endsWith("TENDRIL")) {
            boolean right = boss.getEncounterAction().startsWith("RIGHT");
            for (var tell : boss.getAttackTelegraphs()) {
                var b = tell.bounds(); double top = b.y() + 10;
                organicLink(g, x + (right ? 230 : 40), y + 160, b.x() + b.width() / 2, top, right, tell.active());
                if (tell.active()) {
                    g.setColor(new Color(83, 59, 103)); g.setStroke(new BasicStroke(21, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(new Line2D.Double(b.x() + b.width() / 2, top, b.x() + b.width() / 2, b.y() + b.height() - 15));
                    g.setColor(new Color(229, 228, 199)); g.setStroke(new BasicStroke(4));
                    for (int claw = -1; claw <= 1; claw++) g.draw(new Line2D.Double(b.x() + b.width() / 2,
                            b.y() + b.height() - 38, b.x() + b.width() / 2 + claw * 28, b.y() + b.height() - 3));
                }
            }
        }
        if (boss.getEncounterAction().equals("HEART_PULSE")) {
            for (var shot : boss.getSingularityPredictedShots()) {
                double charge = 1 - boss.getWarningTicks() / 86.0;
                double r = 5 + charge * 7;
                g.setColor(new Color(194, 226, 119, 180)); g.setStroke(new BasicStroke(2));
                g.draw(new Ellipse2D.Double(shot.x() + 7 - r, shot.y() + 7 - r, r * 2, r * 2));
                g.setColor(new Color(245, 247, 181));
                g.fill(new Ellipse2D.Double(shot.x() + 4, shot.y() + 4, 6, 6));
            }
        } else if (boss.getEncounterAction().equals("ROOT_SURGE")) {
            for (var tell : boss.getAttackTelegraphs()) {
                var b = tell.bounds();
                double rise = tell.active() ? b.height() : 6;
                g.setColor(new Color(47, 34, 56)); g.setStroke(new BasicStroke(7, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Double(b.x() + 3, b.y() + b.height() - 3,
                        b.x() + b.width() - 3, b.y() + b.height() - 3));
                for (double root = b.x() + 12; root < b.x() + b.width() - 8; root += 25) {
                    Path2D spike = new Path2D.Double();
                    spike.moveTo(root - 8, b.y() + b.height());
                    spike.curveTo(root - 9, b.y() + b.height() - rise * .5,
                            root + 9, b.y() + b.height() - rise * .6, root + 3, b.y() + b.height() - rise);
                    spike.lineTo(root + 10, b.y() + b.height()); spike.closePath();
                    g.setColor(tell.active() ? new Color(150, 158, 100) : new Color(113, 113, 93)); g.fill(spike);
                    g.setColor(SPORE); g.setStroke(new BasicStroke(1)); g.draw(spike);
                }
            }
        }
        if (flashes && boss.getHitFlashTicks() > 0 && core != null)
            spark(g, core.bounds().x() + 40, core.bounds().y() + 55, SPORE, boss.getHitFlashTicks());
    }

    private static void nestRoots(Graphics2D g, Boss boss, int stage) {
        double x = boss.getX(), floor = boss.getGroundY();
        boolean crawling = stage == 3 && boss.getEncounterAction().equals("HEART_CRAWL") && boss.getWarningTicks() == 0;
        int roots = stage == 3 ? 4 : 5;
        for (int i = 0; i < roots; i++) {
            double anchorX = x + 103 + i * (stage == 3 ? 20 : 15);
            double anchorY = boss.getY() + (stage == 3 ? 187 : 174);
            double footX = x + (stage == 3 ? 78 + i * 39 : 25 + i * 54);
            if (crawling) footX += Math.sin(boss.getCombatTick() * .23 + i * Math.PI) * 8;
            Path2D root = new Path2D.Double(); root.moveTo(anchorX, anchorY);
            root.curveTo(anchorX + (footX - anchorX) * .35, anchorY + 28,
                    footX - 8, floor - 25, footX, floor - 1);
            g.setColor(new Color(33, 26, 45));
            g.setStroke(new BasicStroke(stage == 3 ? 8 : 12, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.draw(root);
            g.setColor(new Color(99, 84, 108));
            g.setStroke(new BasicStroke(stage == 3 ? 4 : 6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.draw(root);
            g.setColor(new Color(174, 170, 139, 200)); g.setStroke(new BasicStroke(1.5f)); g.draw(root);
            g.setColor(new Color(34, 34, 39, 175)); g.fill(new Ellipse2D.Double(footX - 10, floor - 4, 20, 5));
            if (stage < 3) {
                g.setStroke(new BasicStroke(2)); g.setColor(new Color(90, 79, 105));
                g.draw(new Line2D.Double(footX, floor - 5, footX - 13, floor - 1));
                g.draw(new Line2D.Double(footX, floor - 8, footX + 13, floor - 1));
            }
        }
    }
    private static void bossWarnings(Graphics2D g, Boss boss, boolean contrast) {
        Color color = contrast ? Color.WHITE : accent(boss.getBossLevel());
        for (var tell : boss.getAttackTelegraphs()) {
            var b = tell.bounds(); boolean dangerous = tell.active() && tell.damage() > 0;
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), dangerous ? 78 : 24));
            g.fill(b.rectangle());
            g.setColor(new Color(10, 14, 20, 180));
            g.setStroke(new BasicStroke(dangerous ? 5 : 4)); g.draw(b.rectangle());
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), dangerous ? 240 : 230));
            g.setStroke(new BasicStroke(dangerous ? 3 : contrast ? 2 : 1.5f, BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_MITER, 10, dangerous ? null : new float[]{8, 7}, 0));
            g.draw(b.rectangle());
            // Floor chevrons and progress bars remain readable when impact flashes are disabled.
            double progress = tell.warningTicks() > 0 ? 1 - tell.warningTicks() / (double) Math.max(1, tell.fullWarningTicks()) : 1;
            g.setStroke(new BasicStroke(3));
            g.draw(new Line2D.Double(b.x(), b.y() + b.height() - 3,
                    b.x() + b.width() * progress, b.y() + b.height() - 3));
        }
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 150));
        g.setStroke(new BasicStroke(1.25f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10, new float[]{5, 7}, 0));
        for (var shot : boss.getSingularityPredictedShots()) {
            double length = 650 / Math.max(1, Math.hypot(shot.vx(), shot.vy()));
            g.draw(new Line2D.Double(shot.x(), shot.y(), shot.x() + shot.vx() * length, shot.y() + shot.vy() * length));
        }
        if (boss.getEncounterAction().equals("MAGMA_MORTAR") && boss.getWarningTicks() > 0) {
            for (var landing : boss.getAttackTelegraphs()) {
                double ox = boss.getX() + boss.getWidth() / 2.0, oy = boss.getY() + 19;
                double dx = landing.bounds().x() + landing.bounds().width() / 2;
                double dy = landing.bounds().y() + landing.bounds().height() - 9;
                Path2D arc = new Path2D.Double(); arc.moveTo(ox, oy);
                for (int i = 1; i <= 30; i++) {
                    double t = i / 30.0; arc.lineTo(ox + (dx - ox) * t, oy + (dy - oy) * t - 160 * 4 * t * (1 - t));
                }
                g.draw(arc);
            }
        }
    }

    private static void partStatus(Graphics2D g, Boss.PartView part, int level, boolean contrast) {
        if (part.id().equals("core") && !part.weak()) return;
        var b = part.bounds();
        Color c = part.destroyed() ? new Color(133, 141, 141) : contrast ? Color.WHITE : accent(level);
        double w = Math.max(38, Math.min(76, b.width()));
        double left = b.x() + (b.width() - w) / 2, top = b.y() - 16;
        String label = GameText.message("chapter.boss.part." + part.id());
        g.setFont(GameText.font(PART_FONT));
        while (g.getFontMetrics().stringWidth(label) > w + 10 && g.getFont().getSize() > 8)
            g.setFont(g.getFont().deriveFont((float) g.getFont().getSize() - 1));
        int tw = g.getFontMetrics().stringWidth(label);
        g.setColor(new Color(9, 15, 20, 220));
        g.fillRoundRect((int) (left + w / 2 - tw / 2.0 - 4), (int) top - 9, tw + 8, 14, 4, 4);
        g.setColor(c); GameText.draw(g, label, (int) (left + w / 2 - tw / 2.0), (int) top + 1);
        if (part.id().equals("core")) return;
        double ratio = part.id().equals("heat-vent") ? 1 - part.hp() / (double) part.maxHp()
                : part.hp() / (double) Math.max(1, part.maxHp());
        meter(g, left, b.y() - 6, w, ratio, c);
        if (part.destroyed()) { g.setStroke(new BasicStroke(1)); g.drawLine((int) left, (int) b.y() - 4, (int) (left + w), (int) b.y() - 4); }
    }

    private static void mechanicalLink(Graphics2D g, double ax, double ay, double bx, double by, boolean right) {
        double mx = (ax + bx) / 2 + (right ? 6 : -6), my = Math.min(ay, by) - Math.min(34, Math.abs(ax - bx) * .12);
        Path2D arm = new Path2D.Double(); arm.moveTo(ax, ay); arm.lineTo(mx, my); arm.lineTo(bx, by);
        g.setStroke(new BasicStroke(17, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.setColor(new Color(30, 32, 32)); g.draw(arm);
        g.setStroke(new BasicStroke(10, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.setColor(new Color(104, 110, 103)); g.draw(arm);
        g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.setColor(new Color(214, 211, 181));
        g.draw(new Line2D.Double(mx, my - 2, bx, by - 2));
        for (double[] point : new double[][]{{ax, ay}, {mx, my}, {bx, by}}) {
            g.setColor(new Color(24, 30, 31)); g.fill(new Ellipse2D.Double(point[0] - 9, point[1] - 9, 18, 18));
            g.setColor(new Color(207, 167, 83)); g.setStroke(new BasicStroke(2)); g.draw(new Ellipse2D.Double(point[0] - 7, point[1] - 7, 14, 14));
            g.setColor(new Color(147, 159, 152)); g.fill(new Ellipse2D.Double(point[0] - 2, point[1] - 2, 4, 4));
        }
    }
    private static void organicLink(Graphics2D g, double ax, double ay, double bx, double by, boolean right, boolean active) {
        Path2D stem = new Path2D.Double(); stem.moveTo(ax, ay);
        stem.curveTo(ax + (right ? 28 : -28), ay - 64, bx, by - 45, bx, by);
        g.setStroke(new BasicStroke(active ? 18 : 10, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(43, 28, 58)); g.draw(stem);
        g.setStroke(new BasicStroke(active ? 9 : 4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(114, 82, 125)); g.draw(stem);
        g.setStroke(new BasicStroke(1.2f)); g.setColor(new Color(158, 176, 105, 175)); g.draw(stem);
    }
    private static void severedStem(Graphics2D g, double x, double y) {
        g.setColor(new Color(54, 29, 58)); g.fill(new Ellipse2D.Double(x - 11, y - 6, 22, 16));
        g.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(94, 65, 110));
        for (int i = 0; i < 3; i++) {
            Path2D vein = new Path2D.Double(); vein.moveTo(x - 8 + i * 8, y);
            vein.curveTo(x - 11 + i * 8, y + 5, x - 1 + i * 5, y + 6, x - 7 + i * 7, y + 13 - i * 2);
            g.draw(vein);
        }
        g.setColor(new Color(166, 187, 90, 150)); g.fill(new Ellipse2D.Double(x - 5, y + 4, 8, 5));
    }
    private static void brokenJoint(Graphics2D g, double x, double y, Color color) {
        g.setColor(new Color(17, 21, 24)); g.fill(new Ellipse2D.Double(x - 12, y - 9, 24, 18));
        g.setStroke(new BasicStroke(3)); g.setColor(color.darker());
        for (int i = 0; i < 4; i++) g.draw(new Line2D.Double(x - 9 + i * 6, y - 3, x - 10 + i * 6, y + 7 + (i % 2) * 5));
    }
    private static void shutters(Graphics2D g, Boss.Bounds b, Color color) {
        g.setColor(color); g.setStroke(new BasicStroke(5));
        for (int i = 0; i < 3; i++) g.draw(new Line2D.Double(b.x() + 7, b.y() + 15 + i * 18, b.x() + b.width() - 7, b.y() + 15 + i * 18));
    }
    private static void rebuildPart(Graphics2D target, ChapterArt art, int level, Boss.PartView part, double progress, int facing) {
        if (progress <= 0) return;
        Graphics2D g = (Graphics2D) target.create();
        try {
            var b = part.bounds();
            g.clip(new Rectangle((int) b.x(), (int) b.y(), (int) b.width(), (int) (b.height() * progress)));
            g.setComposite(AlphaComposite.SrcOver.derive((float) (.25 + progress * .4)));
            mirroredFit(g, art, level, "boss-limb", b.x(), b.y(), b.width(), b.height(), facing);
            g.setColor(accent(level)); g.setStroke(new BasicStroke(1.5f));
            g.draw(new Line2D.Double(b.x(), b.y() + b.height() * progress - 1, b.x() + b.width(), b.y() + b.height() * progress - 1));
        } finally { g.dispose(); }
    }
    private static void component(Graphics2D target, ChapterArt art, int level, String asset, Boss.PartView part, float opacity) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            g.setComposite(AlphaComposite.SrcOver.derive(opacity)); var b = part.bounds();
            art.fitPart(g, level, asset, b.x(), b.y(), b.width(), b.height());
        } finally { g.dispose(); }
    }
    private static void mirroredFit(Graphics2D target, ChapterArt art, int level, String asset,
                                    double x, double y, double w, double h, int facing) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            if (facing > 0) { g.translate(x * 2 + w, 0); g.scale(-1, 1); }
            if (asset.startsWith("enemy-")) art.fitOutline(g, level, asset, x, y, w, h);
            art.fitPart(g, level, asset, x, y, w, h);
        } finally { g.dispose(); }
    }
    private static void meter(Graphics2D g, double x, double y, double w, double ratio, Color c) {
        g.setColor(new Color(8, 14, 18, 225)); g.fill(new RoundRectangle2D.Double(x - 1, y - 1, w + 2, 5, 3, 3));
        g.setColor(c); g.fill(new RoundRectangle2D.Double(x, y, w * Math.max(0, Math.min(1, ratio)), 3, 2, 2));
    }
    private static void targetBrackets(Graphics2D g, Boss.Bounds b, Color c, float stroke) {
        g.setStroke(new BasicStroke(stroke)); g.setColor(c);
        for (int side = 0; side < 2; side++) {
            double x = b.x() + (side == 0 ? -3 : b.width() + 3), inward = side == 0 ? 8 : -8;
            for (int end = 0; end < 2; end++) {
                double y = b.y() + (end == 0 ? -3 : b.height() + 3), down = end == 0 ? 8 : -8;
                g.draw(new Line2D.Double(x, y, x + inward, y)); g.draw(new Line2D.Double(x, y, x, y + down));
            }
        }
    }
    private static void spark(Graphics2D g, double x, double y, Color c, int ticks) {
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.min(240, ticks * 35)));
        g.setStroke(new BasicStroke(1.8f));
        for (int i = 0; i < 5; i++) {
            double angle = i * Math.PI * .4, r = 5 + (6 - ticks) * 2;
            g.draw(new Line2D.Double(x + Math.cos(angle) * r, y + Math.sin(angle) * r,
                    x + Math.cos(angle) * (r + 8), y + Math.sin(angle) * (r + 8)));
        }
    }
    private static Boss.PartView part(Boss boss, String id) {
        return boss.getParts().stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);
    }
    private static Color accent(int level) { return level == 2 ? GOLD : level == 3 ? HEAT : SPORE; }
    private static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }
}


