package com.bigphil.mergehell.render;

import com.bigphil.mergehell.i18n.GameText;

import com.bigphil.mergehell.model.*;
import com.bigphil.mergehell.assets.AnimationClip;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.util.*;
import java.util.List;

/** Simulation-owned animation; published poses contain values only, never mutable game entities. */
public final class ActorVisuals {
    public enum Action { IDLE, RUN, RISE, FALL, LAND, DASH, MELEE, HURT, DEAD }
    public record Hero(double x, double footY, int facing, Action action, double phase,
                       double shot, double impact, boolean shield, boolean sudo, float opacity) { }
    public record Hostile(EntityType type, double x, double y, double width, double height,
                          int facing, int hp, int maxHp, int warning, double phase, double hit, double death,
                          ObstacleManager.Enemy.Tactics tactics) {
        public Hostile(EntityType type, double x, double y, double width, double height,
                       int facing, int hp, int maxHp, int warning, double phase, double hit, double death) {
            this(type, x, y, width, height, facing, hp, maxHp, warning, phase, hit, death,
                    ObstacleManager.Enemy.Tactics.NONE);
        }
    }
    public record Snapshot(Hero hero, List<Hostile> enemies) {
        public Snapshot { enemies = List.copyOf(enemies); }
    }
    private static final class Track {
        double x, hit, death, phase;
        int hp;
        boolean seen;
        Hostile last;
        Track(ObstacleManager.Enemy e) { x = e.getX(); hp = e.getHp(); }
    }
    private final IdentityHashMap<ObstacleManager.Enemy, Track> tracks = new IdentityHashMap<>();
    private Snapshot snapshot = new Snapshot(null, List.of());
    private double time, lastX, shot, impact, landing, runPhase;
    private int lastHp = -1;
    private long lastShot;
    private boolean grounded;

    public Snapshot snapshot() { return snapshot; }
    public void reset() { tracks.clear(); time = shot = impact = landing = runPhase = 0; lastHp = -1; lastShot = 0;
        snapshot = new Snapshot(null, List.of()); }

    public void update(Player player, List<ObstacleManager.Enemy> enemies, double seconds) {
        double dt = Math.max(0, Math.min(seconds, 0.08));
        time += dt;
        shot = Math.max(0, shot - dt * 14);
        impact = Math.max(0, impact - dt * 5);
        landing = Math.max(0, landing - dt * 8);
        if (lastHp >= 0 && player.getShotSequence() != lastShot) shot = 1;
        if (lastHp >= 0 && player.getHp() < lastHp) impact = 1;
        if (!grounded && player.isGrounded() && lastHp >= 0) landing = 1;
        boolean moving = lastHp >= 0 && Math.abs(player.getX() - lastX) > 0.1;
        if (moving && player.isGrounded() && !player.isDashing())
            runPhase = (runPhase + Math.abs(player.getX() - lastX) * Math.PI / 20) % (Math.PI * 2);
        Action action = player.getHp() <= 0 ? Action.DEAD
                : player.isDashing() ? Action.DASH : player.isMeleeActive() ? Action.MELEE
                : impact > 0.6 ? Action.HURT : !player.isGrounded()
                ? (player.getVerticalVelocity() < 0 ? Action.RISE : Action.FALL)
                : moving ? Action.RUN : landing > 0.2 ? Action.LAND : Action.IDLE;
        float opacity = player.getInvincibleTimer() > 0 && !player.isDashing()
                && (player.getInvincibleTimer() / 4) % 2 == 0 ? 0.42f : 1f;
        Hero hero = new Hero(player.getX() + player.getBounds().width / 2.0,
                player.getY() + player.getBounds().height, player.getFacingDir(), action,
                action == Action.MELEE ? player.getMeleeProgress() : action == Action.RUN ? runPhase : time * 15,
                shot, Math.max(impact, landing), player.getShieldTimer() > 0, player.getSudoTimer() > 0, opacity);
        lastX = player.getX(); lastHp = player.getHp(); lastShot = player.getShotSequence(); grounded = player.isGrounded();
        tracks.values().forEach(track -> track.seen = false);
        List<Hostile> poses = new ArrayList<>();
        for (var enemy : enemies) {
            Track track = tracks.computeIfAbsent(enemy, Track::new);
            track.seen = !enemy.isDead();
            if (!track.seen) continue;
            track.hit = enemy.getHp() < track.hp ? 1 : Math.max(0, track.hit - dt * 7);
            int facing = enemy.getX() - track.x > 0.05 ? 1 : enemy.getX() - track.x < -0.05 ? -1
                    : enemy.getX() > player.getX() ? -1 : 1;
            if (enemy.getType().isChapterSpecialist()) facing = enemy.getTactics().facing();
            track.phase = (track.phase + Math.abs(enemy.getX() - track.x) * Math.PI / 12) % (Math.PI * 2);
            track.x = enemy.getX(); track.hp = enemy.getHp();
            track.last = new Hostile(enemy.getType(), enemy.getX(), enemy.getY(), enemy.getBounds().width,
                    enemy.getType().height, facing, enemy.getHp(), enemy.getMaxHp(),
                    enemy.getTelegraphTicks(), track.phase, track.hit, 0, enemy.getTactics());
            poses.add(track.last);
        }
        for (Iterator<Map.Entry<ObstacleManager.Enemy, Track>> it = tracks.entrySet().iterator(); it.hasNext();) {
            Track t = it.next().getValue();
            if (t.seen) continue;
            t.death += dt;
            if (t.last == null || t.death > 0.3 || !t.last.type().isHostile()) { it.remove(); continue; }
            Hostile p = t.last;
            poses.add(new Hostile(p.type(), p.x(), p.y(), p.width(), p.height(), p.facing(), 0, p.maxHp(),
                    0, p.phase(), 0, t.death / 0.3, p.tactics()));
        }
        snapshot = new Snapshot(hero, poses);
    }

    public static void hero(Graphics2D target, IndustrialArt art, Hero p) {
        Graphics2D local = (Graphics2D) target.create();
        try { drawHero(local, art, p); } finally { local.dispose(); }
    }

    private static void drawHero(Graphics2D target, IndustrialArt art, Hero p) {
        if (p == null) return;
        if (p.action() != Action.RISE && p.action() != Action.FALL && p.action() != Action.DASH)
            shadow(target, p.x(), p.footY(), 29, 0.5f);
        if (p.action() == Action.DASH) {
            for (int i = 3; i > 0; i--) {
                Graphics2D trail = (Graphics2D) target.create();
                trail.translate(-p.facing() * i * 12, 0);
                opacity(trail, 0.09f * (4 - i));
                heroBody(trail, art, p); trail.dispose();
            }
        }
        Graphics2D g = (Graphics2D) target.create();
        opacity(g, p.opacity());
        heroBody(g, art, p);
        g.dispose();
        if (p.shield() || p.sudo()) {
            g = (Graphics2D) target.create();
            g.setColor(p.sudo() ? new Color(255, 195, 69, 145) : new Color(102, 238, 245, 155));
            g.setStroke(new BasicStroke(1.3f));
            g.drawOval((int) p.x() - 24, (int) p.footY() - 50, 48, 54);
            g.dispose();
        }
    }

    private static void heroBody(Graphics2D target, IndustrialArt art, Hero p) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            g.translate(p.x(), p.footY()); g.scale(p.facing(), 1);
            boolean run = p.action() == Action.RUN, air = p.action() == Action.RISE || p.action() == Action.FALL;
            boolean dash = p.action() == Action.DASH, melee = p.action() == Action.MELEE;
            double bob = run ? Math.abs(Math.sin(p.phase())) * 1.1 : Math.sin(p.phase() * 0.2) * 0.5;
            if (p.action() == Action.LAND || run) bob += p.impact() * (run ? 1.5 : 3);
            if (p.action() == Action.DEAD) { g.translate(-3, -5); g.rotate(-Math.PI / 2, 0, -8); bob = 0; }
            double lean = dash ? 0.28 : run ? 0.08 : p.action() == Action.HURT ? -0.16 * p.impact() : 0;
            AffineTransform torso = partTransform(art, "repair", "torso", 0, -25 + bob, 17.5, lean, null);
            Point2D hip = socket(art, "repair", "torso", torso, "hip", -2, -17 + bob);
            Point2D shoulder = socket(art, "repair", "torso", torso, "shoulder", -5, -27 + bob);
            Point2D neck = socket(art, "repair", "torso", torso, "neck", 1, -32 + bob);
            // Stance feet travel opposite body movement; recovery feet lift above the contact plane.
            Point2D backFoot = run ? footAt(p.phase() + Math.PI) : new Point2D.Double(air ? -6 : dash ? -10 : -5, air ? -8 : dash ? -5 : 0);
            Point2D frontFoot = run ? footAt(p.phase()) : new Point2D.Double(air ? 5 : dash ? -2 : 5, air ? -4 : dash ? -2 : 0);
            leg(g, art, hip.getX() - 2.5, hip.getY(), backFoot.getX(), backFoot.getY(), 0.65f);
            leg(g, art, hip.getX() + 2.5, hip.getY(), frontFoot.getX(), frontFoot.getY(), 1f);
            rigidPart(g, art, "repair", "scarf", neck.getX(), neck.getY() + 1, 22,
                    Math.sin(p.phase() * 0.8) * 0.08 - (dash ? 0.12 : 0), null);
            transformedPart(g, art, "repair", "torso", torso);
            AffineTransform head = partTransform(art, "repair", "head", neck.getX(), neck.getY(), 23.5, lean * 0.4, "neck");
            drawHead(g, art, head, p.facing());
            // Muzzle sits exactly at the unchanged Player emission point: center +/-15, footY-15.
            double muzzleX = 15, muzzleY = -15;
            // Recoil pivots around the emitted muzzle point, preserving both facing directions.
            AffineTransform cannon = cannonTransform(art, p.shot());
            Point2D grip = socket(art, "repair", "cannon", cannon, "grip", -3, -11);
            double armEndX = melee ? 12 + Math.sin(p.phase() * Math.PI) * 9 : grip.getX();
            double armEndY = melee ? -30 + p.phase() * 24 : grip.getY();
            arm(g, art, shoulder.getX(), shoulder.getY(), armEndX, armEndY);
            if (!melee) {
                transformedPart(g, art, "repair", "cannon", cannon);
                if (p.shot() > 0 && p.action() != Action.DEAD) {
                    g.setColor(new Color(255, 163, 46, (int) (210 * p.shot())));
                    Path2D flame = new Path2D.Double(); flame.moveTo(muzzleX, muzzleY);
                    flame.lineTo(muzzleX + 12 * p.shot(), muzzleY - 3); flame.lineTo(muzzleX + 6, muzzleY);
                    flame.lineTo(muzzleX + 12 * p.shot(), muzzleY + 3); flame.closePath(); g.fill(flame);
                    g.setColor(new Color(255, 248, 201, (int) (255 * p.shot()))); g.fillOval(14, -17, 5, 4);
                }
            } else {
                double sweep = Math.sin(p.phase() * Math.PI);
                g.setColor(new Color(255, 168, 65, (int) (100 * sweep))); g.setStroke(new BasicStroke(3));
                g.drawArc(3, -38, 34, 36, -65, 140);
                g.setColor(new Color(194, 244, 255, (int) (200 * sweep))); g.setStroke(new BasicStroke(1.2f));
                g.drawArc(3, -38, 34, 36, -65, 140);
            }
        } finally { g.dispose(); }
    }

    private static void leg(Graphics2D target, IndustrialArt art, double hx, double hy, double fx, double fy, float opacity) {
        Graphics2D g = (Graphics2D) target.create();
        opacity(g, opacity);
        try {
            var shin = art.frame("repair", "shin").orElse(null);
            var ankle = shin == null ? null : shin.sockets().get("ankle");
            if (ankle == null) {
                Limb limb = solveLimb(hx, hy, fx, fy, 9, 11, 1);
                segment(g, art, "repair", "thigh", hx, hy, limb.joint().getX(), limb.joint().getY());
                segment(g, art, "repair", "shin", limb.joint().getX(), limb.joint().getY(), limb.tip().getX(), limb.tip().getY());
                return;
            }
            double lowerLength = 10;
            double scale = lowerLength / Math.hypot(ankle.x() - shin.anchor().x(), ankle.y() - shin.anchor().y());
            AffineTransform boot = bootTransform(shin, fx, fy, scale, fy == 0 ? 0 : -0.18);
            Point2D targetAnkle = boot.transform(new Point2D.Double(ankle.x(), ankle.y()), null);
            Limb limb = solveLimb(hx, hy, targetAnkle.getX(), targetAnkle.getY(), 9, lowerLength, 1);
            segment(g, art, "repair", "thigh", hx, hy, limb.joint().getX(), limb.joint().getY());
            AffineTransform calf = segmentTransform(shin, ankle, limb.joint().getX(), limb.joint().getY(),
                    limb.tip().getX(), limb.tip().getY());
            // The painted ankle cap overlaps both clips and conceals the articulation seam.
            transformedPart(g, art, "repair", "shin", calf,
                    new Rectangle2D.Double(0, 0, shin.width(), ankle.y() + 14));
            AffineTransform reachCorrection = AffineTransform.getTranslateInstance(
                    limb.tip().getX() - targetAnkle.getX(), limb.tip().getY() - targetAnkle.getY());
            reachCorrection.concatenate(boot);
            transformedPart(g, art, "repair", "shin", reachCorrection,
                    new Rectangle2D.Double(0, ankle.y() - 40, shin.width(), shin.height() - ankle.y() + 40));
        } finally { g.dispose(); }
    }

    static AffineTransform bootTransform(AnimationClip.Frame frame, double x, double y, double scale, double angle) {
        var sole = frame.sockets().get("distal");
        var heel = frame.sockets().getOrDefault("heel", sole);
        var toe = frame.sockets().getOrDefault("toe", sole);
        AffineTransform transform = new AffineTransform();
        transform.translate(x, y); transform.rotate(angle); transform.scale(scale, scale);
        transform.translate(-sole.x(), -Math.max(heel.y(), toe.y()));
        return transform;
    }

    private static void arm(Graphics2D g, IndustrialArt art, double sx, double sy, double ex, double ey) {
        Limb limb = solveLimb(sx, sy, ex, ey, 9, 11, -1);
        segment(g, art, "repair", "upper-arm", sx, sy, limb.joint().getX(), limb.joint().getY());
        segment(g, art, "repair", "forearm", limb.joint().getX(), limb.joint().getY(), limb.tip().getX(), limb.tip().getY());
    }

    private static void segment(Graphics2D g, IndustrialArt art, String id, String part,
                                double x, double y, double ex, double ey) {
        var frame = art.frame(id, part).orElse(null);
        if (frame == null) return;
        var tip = frame.sockets().get("distal");
        if (tip == null) return;
        transformedPart(g, art, id, part, segmentTransform(frame, tip, x, y, ex, ey));
    }

    record Limb(Point2D joint, Point2D tip) { }

    static AffineTransform cannonTransform(IndustrialArt art, double recoil) {
        return partTransform(art, "repair", "cannon", 15, -15, 22, -recoil * 0.06, "muzzle");
    }

    /** A reachable target changes joint angles, never the authored proportions or bone lengths. */
    static Limb solveLimb(double x, double y, double ex, double ey, double upper, double lower, int bend) {
        double dx = ex - x, dy = ey - y, distance = Math.hypot(dx, dy);
        if (distance < 1e-9) { dx = 0; dy = 1; distance = 1; }
        double reach = Math.max(Math.abs(upper - lower) + 1e-6, Math.min(upper + lower - 1e-6, distance));
        double ux = dx / distance, uy = dy / distance;
        double along = (upper * upper - lower * lower + reach * reach) / (2 * reach);
        double offset = Math.sqrt(Math.max(0, upper * upper - along * along)) * bend;
        return new Limb(new Point2D.Double(x + ux * along + uy * offset, y + uy * along - ux * offset),
                new Point2D.Double(x + ux * reach, y + uy * reach));
    }

    /** Counter-mirror only the CRT glass; the metal head and its direction keep the source silhouette. */
    private static void drawHead(Graphics2D g, IndustrialArt art, AffineTransform head, int facing) {
        var frame = art.frame("repair", "head").orElse(null);
        Shape glass = frame == null ? null : visorClip(frame);
        if (facing >= 0 || glass == null) {
            transformedPart(g, art, "repair", "head", head);
            return;
        }
        Area shell = new Area(new Rectangle2D.Double(0, 0, frame.width(), frame.height()));
        shell.subtract(new Area(glass));
        // Disjoint regions prevent the source glyph ghosting or gaining opacity during invincibility.
        transformedPart(g, art, "repair", "head", head, shell);
        readableVisor(g, art, head);
    }

    private static Shape visorClip(AnimationClip.Frame frame) {
        var top = frame.sockets().get("visor-top-left");
        var bottom = frame.sockets().get("visor-bottom-right");
        return top == null || bottom == null ? null : new RoundRectangle2D.Double(top.x(), top.y(),
                bottom.x() - top.x(), bottom.y() - top.y(), 20, 20);
    }

    private static void readableVisor(Graphics2D g, IndustrialArt art, AffineTransform head) {
        var frame = art.frame("repair", "head").orElse(null);
        if (frame == null || head == null) return;
        var top = frame.sockets().get("visor-top-left");
        var bottom = frame.sockets().get("visor-bottom-right");
        if (top == null || bottom == null) return;
        Graphics2D visor = (Graphics2D) g.create();
        try {
            visor.transform(head);
            visor.clip(visorClip(frame));
            visor.translate(top.x() + bottom.x(), 0); visor.scale(-1, 1);
            art.part(visor, "repair", "head", 0, 0, frame.width(), frame.height());
        } finally { visor.dispose(); }
    }

    static Point2D footAt(double phase) {
        double position = ((phase % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2);
        if (position < Math.PI) return new Point2D.Double(10 - 20 * position / Math.PI, 0);
        double recovery = (position - Math.PI) / Math.PI;
        return new Point2D.Double(-10 + recovery * 20, -6 * Math.sin(recovery * Math.PI));
    }

    static AffineTransform segmentTransform(AnimationClip.Frame frame, AnimationClip.Point tip,
                                            double x, double y, double ex, double ey) {
        double sourceX = tip.x() - frame.anchor().x(), sourceY = tip.y() - frame.anchor().y();
        double sourceLength = Math.hypot(sourceX, sourceY);
        if (sourceLength <= 0) throw new IllegalArgumentException("Coincident limb sockets: " + frame.id());
        double scale = Math.hypot(ex - x, ey - y) / sourceLength;
        AffineTransform transform = new AffineTransform();
        transform.translate(x, y);
        transform.rotate(Math.atan2(ey - y, ex - x) - Math.atan2(sourceY, sourceX));
        transform.scale(scale, scale);
        transform.translate(-frame.anchor().x(), -frame.anchor().y());
        return transform;
    }

    static AffineTransform partTransform(IndustrialArt art, String id, String part,
                                        double x, double y, double width, double angle, String anchorSocket) {
        var frame = art.frame(id, part).orElse(null);
        if (frame == null) return null;
        var anchor = anchorSocket == null ? frame.anchor() : frame.sockets().getOrDefault(anchorSocket, frame.anchor());
        AffineTransform transform = new AffineTransform();
        transform.translate(x, y); transform.rotate(angle);
        double scale = width / frame.width();
        transform.scale(scale, scale); transform.translate(-anchor.x(), -anchor.y());
        return transform;
    }

    private static void transformedPart(Graphics2D g, IndustrialArt art, String id, String part, AffineTransform transform) {
        transformedPart(g, art, id, part, transform, null);
    }

    private static void transformedPart(Graphics2D g, IndustrialArt art, String id, String part,
                                        AffineTransform transform, Shape clip) {
        var frame = art.frame(id, part).orElse(null);
        if (frame == null || transform == null) return;
        Graphics2D local = (Graphics2D) g.create();
        try {
            local.transform(transform);
            if (clip != null) local.clip(clip);
            art.part(local, id, part, 0, 0, frame.width(), frame.height());
        } finally { local.dispose(); }
    }

    private static void rigidPart(Graphics2D g, IndustrialArt art, String id, String part,
                                   double x, double y, double width, double angle, String anchor) {
        transformedPart(g, art, id, part, partTransform(art, id, part, x, y, width, angle, anchor));
    }

    private static Point2D socket(IndustrialArt art, String id, String part, AffineTransform transform,
                                  String name, double fallbackX, double fallbackY) {
        var frame = art.frame(id, part).orElse(null);
        var point = frame == null ? null : frame.sockets().get(name);
        if (point == null || transform == null) return new Point2D.Double(fallbackX, fallbackY);
        return transform.transform(new Point2D.Double(point.x(), point.y()), null);
    }

    public static void enemy(Graphics2D target, IndustrialArt art, Hostile p) {
        Graphics2D local = (Graphics2D) target.create();
        try { drawEnemy(local, art, p); } finally { local.dispose(); }
    }

    private static void drawEnemy(Graphics2D target, IndustrialArt art, Hostile p) {
        if (!p.type().isHostile()) { pickup(target, p); return; }
        String firstWavePart = firstWavePart(p.type());
        boolean fullBody = firstWavePart != null && art.frame("first-wave", firstWavePart).isPresent();
        boolean rigged = art.has("hostiles") && (p.type() == EntityType.BUG || p.type() == EntityType.TECHDEBT);
        if (!fullBody && !rigged) {
            if (p.death() == 0) VectorEntityRenderer.render(target, p.type(), (int) p.x(), (int) p.y(),
                    (int) p.width(), (int) p.height(), p.hp(), p.maxHp(), p.warning());
            return;
        }
        double center = p.x() + p.width() / 2, foot = p.y() + p.height();
        // Lock's logical bottom is airborne; there is no ground projection in this value snapshot.
        if (p.type() != EntityType.LOCK)
            shadow(target, center, foot, p.width(), (float) (0.55 * (1 - p.death())));
        Graphics2D g = (Graphics2D) target.create();
        try {
            g.translate(center, foot);
            // Enemy pieces were authored facing left.
            g.scale(-p.facing(), 1);
            if (p.death() > 0) {
                g.translate(0, -Math.sin(p.death() * Math.PI) * 9);
                g.rotate(p.death() * 0.7);
                opacity(g, (float) (1 - p.death()));
            }
            double bob = Math.abs(Math.sin(p.phase())) * (p.type() == EntityType.BUG ? 1.2 : 0.9);
            if (fullBody) {
                drawFirstWave(g, art, firstWavePart, p);
            } else if (p.type() == EntityType.BUG) {
                for (int back = 1; back >= 0; back--) for (int i = 0; i < 3; i++) {
                    double hx = -9 + i * 9, hy = -17 + bob;
                    double kx = hx + (i - 1) * 9, ky = hy - 5;
                    double stride = Math.sin(p.phase() + i * 1.8 + back * Math.PI);
                    segment(g, art, "hostiles", "bug-upper", hx, hy, kx, ky);
                    segment(g, art, "hostiles", "bug-lower", kx, ky, kx + stride * 5, -Math.max(0, stride) * 4);
                }
                rigidPart(g, art, "hostiles", "bug-body", 0, -24 + bob, 36, 0, null);
            } else {
                for (int i = 0; i < 2; i++) {
                    Point2D contact = footAt(p.phase() + i * Math.PI);
                    rigidPart(g, art, "hostiles", "debt-leg", (i == 0 ? -17 : 3) - contact.getX() * 0.6,
                            contact.getY() * 0.5 - 0.8, 22, 0, "distal");
                }
                AffineTransform body = partTransform(art, "hostiles", "debt-body", 0, -51 + bob, 50, 0, null);
                Point2D rear = socket(art, "hostiles", "debt-body", body, "shoulder-right", 20, -53 + bob);
                Point2D front = socket(art, "hostiles", "debt-body", body, "shoulder-left", -22, -47 + bob);
                rigidPart(g, art, "hostiles", "debt-arm", rear.getX(), rear.getY(), 23, -0.12, null);
                transformedPart(g, art, "hostiles", "debt-body", body);
                rigidPart(g, art, "hostiles", "debt-arm", front.getX(), front.getY(), 25,
                        p.warning() > 0 ? 0.25 : Math.sin(p.phase()) * 0.09, null);
            }
            if (p.hit() > 0) {
                g.setColor(new Color(255, 236, 191, (int) (150 * p.hit())));
                g.setStroke(new BasicStroke(2)); g.drawArc(-(int) p.width() / 2,
                        -(int) p.height(), (int) p.width(), (int) p.height(), 45, 170);
            }
        } finally { g.dispose(); }
        if (p.death() > 0) return;
        if (p.maxHp() > 1) {
            target.setColor(new Color(11, 12, 14, 225)); target.fillRoundRect((int) p.x(), (int) p.y() - 11, (int) p.width(), 4, 3, 3);
            target.setColor(new Color(255, 87, 65)); target.fillRoundRect((int) p.x(), (int) p.y() - 11,
                    (int) (p.width() * p.hp() / p.maxHp()), 4, 3, 3);
        }
        if (p.warning() > 0) {
            target.setColor(new Color(255, 73, 55, 200)); target.setStroke(new BasicStroke(1.8f));
            target.drawLine((int) center, (int) (p.y() + p.height() / 2),
                    (int) center + p.facing() * 90, (int) (p.y() + p.height() / 2));
            target.drawOval((int) center - 7, (int) p.y() - 27, 14, 14);
            target.setFont(GameText.font(new Font(Font.MONOSPACED, Font.BOLD, 11))); GameText.draw(target, "!", (int) center - 3, (int) p.y() - 16);
        }
    }

    private static String firstWavePart(EntityType type) {
        return switch (type) {
            case CONFLICT -> "conflict";
            case CRASH -> "crash";
            case LOCK -> "lock";
            case FIREWALL -> "firewall";
            default -> null;
        };
    }

    /** Uniform fit keeps circular bearings round and the contact plane at the logical bottom. */
    static AffineTransform firstWaveTransform(AnimationClip.Frame frame, double width, double height) {
        double scale = Math.min(width / frame.width(), height / frame.height());
        AffineTransform body = new AffineTransform();
        body.scale(scale, scale);
        body.translate(-frame.anchor().x(), -frame.anchor().y());
        return body;
    }

    private static void drawFirstWave(Graphics2D target, IndustrialArt art, String part, Hostile p) {
        AnimationClip.Frame frame = art.frame("first-wave", part).orElseThrow();
        Graphics2D g = (Graphics2D) target.create();
        try {
            // Lock's altitude already comes from Enemy.update; this adds only a small visual roll.
            if (p.type() == EntityType.LOCK) g.rotate(Math.sin(p.phase()) * 0.035, 0, -p.height() * 0.45);
            if (p.hit() > 0) g.translate(p.hit() * 1.4, 0);
            AffineTransform body = firstWaveTransform(frame, p.width(), p.height());
            transformedPart(g, art, "first-wave", part, body);
            if (p.type() == EntityType.CRASH && p.death() == 0) {
                AnimationClip.Point wheel = frame.sockets().get("wheel");
                AnimationClip.Point rim = frame.sockets().get("wheel-rim");
                if (wheel != null && rim != null) {
                    double radius = Math.hypot(rim.x() - wheel.x(), rim.y() - wheel.y());
                    Graphics2D rotatingHub = (Graphics2D) g.create();
                    try {
                        rotatingHub.transform(body);
                        // Reuse only the circular inner hub. The ram, exhaust and casing stay rigid.
                        rotatingHub.clip(new Ellipse2D.Double(wheel.x() - radius, wheel.y() - radius,
                                radius * 2, radius * 2));
                        rotatingHub.rotate(-p.phase(), wheel.x(), wheel.y());
                        art.part(rotatingHub, "first-wave", part, 0, 0, frame.width(), frame.height());
                    } finally { rotatingHub.dispose(); }
                }
            }
            // Heat/optic artwork is decorative; an extra danger pulse requires real telegraph ticks.
            if (p.warning() > 0 && p.death() == 0) {
                AnimationClip.Point core = frame.sockets().get("core");
                if (core != null) {
                    Point2D point = body.transform(new Point2D.Double(core.x(), core.y()), null);
                    double radius = 1.8 + Math.abs(Math.sin(p.warning() * 0.35));
                    g.setColor(new Color(255, 72, 38, 175));
                    g.fill(new Ellipse2D.Double(point.getX() - radius, point.getY() - radius,
                            radius * 2, radius * 2));
                }
            }
        } finally { g.dispose(); }
    }

    private static void pickup(Graphics2D g, Hostile p) {
        int x = (int) p.x(), y = (int) p.y();
        g.setPaint(new GradientPaint(x, y, new Color(91, 79, 57), x + 25, y + 25, new Color(21, 25, 28)));
        g.fillRoundRect(x, y, 28, 28, 5, 5);
        g.setColor(p.type().color); g.drawRoundRect(x, y, 28, 28, 5, 5);
        g.setFont(GameText.font(new Font(Font.MONOSPACED, Font.BOLD, 13)));
        String mark = p.type() == EntityType.HEALTH ? "+" : p.type() == EntityType.POWERUP_SHIELD ? "S"
                : p.type().toWeapon() == null ? "?" : p.type().toWeapon().name().substring(0, 1);
        GameText.draw(g, mark, x + 10, y + 19);
    }

    private static void shadow(Graphics2D g, double x, double y, double width, float alpha) {
        g.setColor(new Color(0, 0, 0, Math.max(0, Math.min(150, (int) (alpha * 150)))));
        g.fillOval((int) (x - width * 0.6), (int) y - 3, (int) (width * 1.2), 6);
    }

    private static void opacity(Graphics2D g, float factor) {
        if (g.getComposite() instanceof AlphaComposite composite)
            g.setComposite(composite.derive(composite.getAlpha() * factor));
    }
}
