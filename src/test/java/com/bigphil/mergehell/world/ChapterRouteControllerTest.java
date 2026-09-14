package com.bigphil.mergehell.world;

import com.bigphil.mergehell.combat.WeaponId;
import com.bigphil.mergehell.model.Player;
import com.bigphil.mergehell.model.Projectile;
import com.bigphil.mergehell.model.ProjectileType;
import com.bigphil.mergehell.progression.RunBuild;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class ChapterRouteControllerTest {
    private static final int GROUND = 480;

    private static final class Walk {
        final ChapterRouteController route;
        final Player player;
        final List<Projectile> shots = new ArrayList<>();
        final List<ChapterRouteController.Event> events = new ArrayList<>();
        int aliveHostiles;
        boolean arena;
        Walk(int level, int x) { this(level, x, GROUND - 30); }
        Walk(int level, int x, int y) {
            route = new ChapterRouteController(level, GROUND);
            player = new Player(x, y);
        }
        void tick(boolean left, boolean right, boolean shoot) {
            route.beforeMove(player, arena);
            double before = player.getX();
            player.update(left, right, false, shoot, route.groundFor(player.getX(), 30),
                    9000, shots, route.platforms());
            route.afterMove(player, before, aliveHostiles);
            for (Projectile shot : shots) {
                shot.update();
                route.consumeShot(shot, Double.POSITIVE_INFINITY);
            }
            shots.removeIf(shot -> shot.isDead() || shot.getX() > 9200 || shot.getX() < -100);
            events.addAll(route.drainEvents());
        }
        ChapterRouteController.Prop prop(int id) {
            return route.snapshot().props().stream().filter(prop -> prop.id() == id).findFirst().orElseThrow();
        }
        long count(String key) { return events.stream().filter(event -> event.key().equals(key)).count(); }
    }

    @Test void allFourBridgeGapsCanBeCrossedWithRealWalkingDoubleJumpAndDash() {
        for (int gapX : new int[]{860, 2800, 4500, 6610}) {
            Walk w = new Walk(2, gapX - 240);
            int calls = 0;
            while (w.player.getX() < gapX - 45 && calls++ < 60) w.tick(false, true, false);
            assertTrue(w.player.isGrounded(), "walk-up bridge abutment at " + gapX);
            w.player.requestJump();
            for (int frame = 0; frame < 65; frame++) {
                if (frame == 16) w.player.requestJump();
                if (frame == 26) w.player.dash(1);
                w.tick(false, w.player.getX() < gapX + 260, false);
            }
            for (int frame = 0; frame < 80 && !w.player.isGrounded(); frame++) w.tick(false, false, false);
            assertTrue(w.player.getX() > gapX + 165, "gap landing at " + gapX);
            assertTrue(w.player.isGrounded(), "must land after crossing " + gapX);
            assertEquals(100, w.player.getHp());
            assertEquals(0, w.count("chapter.bridge.rescued"), "no rescue teleport may complete the jump");
        }
    }

    @Test void standingOnLiftOneRemainsOnItsIdWhenCounterweightZeroIsDestroyed() {
        Walk w = new Walk(2, 2850, 260);
        for (int i = 0; i < 180 && !w.player.isGrounded(); i++) w.tick(false, false, false);
        var support = w.route.snapshot().lifts().stream().filter(lift -> lift.id() == 1).findFirst().orElseThrow();
        assertEquals(support.platform().y, w.player.getY() + 30, 1e-6);
        double standingX = w.player.getX();
        Rectangle counter = w.prop(0).bounds();
        for (int n = 0; n < 20 && w.prop(0).hp() > 0; n++) {
            Projectile shot = new Projectile(counter.x - 20, counter.y + 12, 25, 0, ProjectileType.COMMIT);
            shot.update(); assertTrue(w.route.consumeShot(shot, Double.POSITIVE_INFINITY));
        }
        assertEquals(0, w.prop(0).hp());
        for (int i = 0; i < 100; i++) {
            w.tick(false, false, false);
            var lift = w.route.snapshot().lifts().stream().filter(value -> value.id() == 1).findFirst().orElseThrow();
            assertEquals(standingX, w.player.getX(), 1e-6, "no shift to another lift");
            assertEquals(lift.platform().y, w.player.getY() + 30, 1e-6, "standing actor carried exactly once");
            assertTrue(w.player.isGrounded());
        }
    }

    @Test void releasingOnesOwnCounterweightKeepsTheRiderOnTheSettledBridge() {
        Walk w = new Walk(2, 900, 260);
        while (w.route.snapshot().tick() < 235) w.tick(false, false, false);
        w.tick(true, false, false); w.tick(true, false, false);
        assertTrue(w.player.isGrounded());
        assertTrue(w.player.getY() + 30 > GROUND - 48, "lift currently below its eventual bridge height");
        w.player.melee(); w.route.melee(w.player.getMeleeBounds());
        assertEquals(10, w.prop(0).hp());
        for (int i = 0; i < 30; i++) w.tick(false, false, false);
        w.player.melee(); w.route.melee(w.player.getMeleeBounds());
        assertEquals(0, w.prop(0).hp(), "real melee can release a bridge while riding its lift");
        double beforeSettlingX = w.player.getX();
        for (int i = 0; i < 60; i++) w.tick(false, false, false);
        assertEquals(100, w.player.getHp(), "releasing the route aid must not drop its rider into the shaft");
        assertEquals(beforeSettlingX, w.player.getX(), 1e-6, "widening a centered bridge must not slide its rider left");
        assertEquals(GROUND - 48, w.player.getY() + 30, 1e-6);
        assertEquals(0, w.count("chapter.bridge.rescued"));
    }

    @Test void membraneBlocksWalkingAndDashFromBothSidesAndDoubleJumpClearsIt() {
        Walk left = new Walk(4, 1470);
        double membraneRight = left.prop(0).bounds().getMaxX();
        for (int i = 0; i < 40; i++) left.tick(false, true, false);
        assertEquals(1550, left.player.getX());
        left.player.dash(1);
        for (int i = 0; i < 10; i++) left.tick(false, true, false);
        assertEquals(1550, left.player.getX());
        left.player.requestJump();
        for (int i = 0; i < 62; i++) {
            if (i == 12) left.player.requestJump();
            left.tick(false, true, false);
        }
        assertTrue(left.player.getX() > membraneRight, "real double jump clears the entire 150px membrane");
        assertEquals(160, left.prop(0).hp(), "crossing must not secretly destroy the wall");
        Walk right = new Walk(4, 1690);
        for (int i = 0; i < 35; i++) right.tick(true, false, false);
        assertEquals(membraneRight, right.player.getX());
        right.player.dash(-1);
        for (int i = 0; i < 10; i++) right.tick(true, false, false);
        assertEquals(membraneRight, right.player.getX());
    }

    @Test void respawnCorrectionEscapesMembraneOverlapWithoutRestoringItsHp() {
        Walk w = new Walk(4, 1590);
        Rectangle membrane = w.prop(0).bounds();
        w.route.melee(membrane);
        assertEquals(110, w.prop(0).hp());
        w.route.resetTransient();
        w.tick(false, false, false);
        assertFalse(w.player.getBounds().intersects(membrane));
        assertEquals(110, w.prop(0).hp());
        assertTrue(w.player.getX() == membrane.x - w.player.getBounds().width
                || w.player.getX() == membrane.getMaxX());
    }

    @Test void allSixBaseWeaponsBreakEveryKindOfPropThroughActualPlayerFire() {
        for (WeaponId weapon : WeaponId.values()) {
            for (int target = 0; target < 4; target++) {
                int level = target == 0 ? 2 : target == 1 ? 3 : 4;
                int spawn = target == 0 ? 600 : target == 1 ? 880 : target == 2 ? 1460 : 420;
                int id = target == 3 ? 4 : 0;
                Walk w = new Walk(level, spawn);
                w.player.setRunBuild(new RunBuild(weapon));
                w.player.setCombatSeed(1324);
                // Walk into firing position normally so a counterweight above the ground is
                // approached from its real staircase instead of relocating the muzzle upward.
                if (target == 0) for (int i = 0; i < 36; i++) w.tick(false, true, false);
                for (int i = 0; i < 500 && w.prop(id).hp() > 0; i++) w.tick(false, false, true);
                assertEquals(0, w.prop(id).hp(), weapon + " failed against " + w.prop(id).kind());
                assertTrue(w.player.getShotSequence() > 0);
                assertFalse(w.player.isDebugMode());
                long broken = w.events.stream().filter(event -> event.key().endsWith(".broken")).count();
                assertEquals(1, broken, "one destruction event only: " + weapon + '/' + target);
            }
        }
    }

    @Test void coolantDestructionHasOneTemporaryEffectAndRespawnDoesNotRefillTheCanister() {
        Walk w = new Walk(3, 940);
        w.tick(false, false, false);
        w.route.interact(w.player);
        assertEquals(420, w.prop(0).effectTicks());
        assertFalse(w.route.snapshot().surfaces().stream().filter(s -> s.kind() == ChapterRouteController.SurfaceKind.MOLTEN)
                .findFirst().orElseThrow().active());
        for (int i = 0; i < 120 && w.prop(0).hp() > 0; i++) w.tick(false, false, true);
        assertEquals(0, w.prop(0).hp());
        assertEquals(600, w.prop(0).effectTicks());
        w.route.resetTransient();
        w.route.interact(w.player);
        assertEquals(0, w.prop(0).hp());
        assertEquals(600, w.prop(0).effectTicks());
        for (int i = 0; i < 600; i++) w.tick(false, false, false);
        assertEquals(0, w.prop(0).effectTicks());
        assertTrue(w.route.snapshot().surfaces().stream().filter(s -> s.kind() == ChapterRouteController.SurfaceKind.MOLTEN)
                .findFirst().orElseThrow().active());
        assertEquals(1, w.count("chapter.prop.coolant.broken"));
    }

    @Test void nestsRespectLocalHostileCapAndThreeHatchLimitAndStayDestroyedAfterReset() {
        Walk w = new Walk(4, 470); w.aliveHostiles = 6;
        for (int i = 0; i < 180; i++) w.tick(false, false, false);
        assertEquals(0, w.count("chapter.nest.hatch"));
        w.aliveHostiles = 5; w.tick(false, false, false);
        assertEquals(1, w.count("chapter.nest.hatch"));
        w.aliveHostiles = 6;
        for (int i = 0; i < 360; i++) w.tick(false, false, false);
        assertEquals(1, w.count("chapter.nest.hatch"));
        w.aliveHostiles = 5;
        for (int i = 0; i < 1400; i++) w.tick(false, false, false);
        assertEquals(3, w.count("chapter.nest.hatch"));
        for (int i = 0; i < 250 && w.prop(4).hp() > 0; i++) w.tick(false, false, true);
        assertEquals(0, w.prop(4).hp());
        w.route.resetTransient();
        for (int i = 0; i < 1400; i++) w.tick(false, false, false);
        assertEquals(3, w.count("chapter.nest.hatch"));
        assertEquals(1, w.count("chapter.prop.nest.broken"));
    }

    @Test void arenaFreezesPhysicalRouteHazardsAndEarlierCollisionKeepsPropUntouched() {
        Walk w = new Walk(4, 1470); w.arena = true;
        for (int i = 0; i < 150; i++) w.tick(false, true, true);
        assertTrue(w.player.getX() > 1624);
        assertEquals(160, w.prop(0).hp());
        assertEquals(0, w.count("chapter.nest.hatch"));
        assertTrue(w.route.snapshot().surfaces().stream().noneMatch(ChapterRouteController.Surface::active));
        Walk collision = new Walk(4, 1470);
        Projectile shot = new Projectile(1540, 430, 100, 0, ProjectileType.COMMIT);
        shot.update();
        assertFalse(collision.route.consumeShot(shot, .01));
        assertEquals(160, collision.prop(0).hp()); assertFalse(shot.isDead());
        assertTrue(collision.route.consumeShot(shot, Double.POSITIVE_INFINITY));
        assertTrue(collision.prop(0).hp() < 160); assertTrue(shot.isDead());
    }

    @Test void destructionKeysDoNotDependOnTheIdeOperatingSystemLocale() {
        Locale old = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            Walk w = new Walk(2, 600);
            Rectangle bounds = w.prop(0).bounds();
            for (int i = 0; i < 3; i++) {
                Projectile shot = new Projectile(bounds.x - 20, bounds.y + 10, 25, 0, ProjectileType.COMMIT);
                shot.update(); assertTrue(w.route.consumeShot(shot, Double.POSITIVE_INFINITY));
            }
            assertTrue(w.route.drainEvents().stream().anyMatch(event -> event.key().equals("chapter.prop.counterweight.broken")));
        } finally { Locale.setDefault(old); }
    }
}
