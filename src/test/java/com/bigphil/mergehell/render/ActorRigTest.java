package com.bigphil.mergehell.render;

import com.bigphil.mergehell.assets.AnimationClip;
import com.bigphil.mergehell.assets.AssetStore;
import com.bigphil.mergehell.model.EntityType;
import com.bigphil.mergehell.model.ObstacleManager;
import com.bigphil.mergehell.model.Player;
import com.bigphil.mergehell.combat.HeroAim;
import com.bigphil.mergehell.progression.CharacterId;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ActorRigTest {
    private static final IndustrialArt ART = IndustrialArt.load();

    @Test void obliqueSourceLimbsAttachBothSocketsWithoutStretchingCircularJoints() {
        for (String id : List.of("repair", "scout", "warden", "engineer", "hostiles")) {
            List<String> parts = !id.equals("hostiles") ? List.of("upper-arm", "forearm", "thigh", "shin")
                    : List.of("bug-upper", "bug-lower", "debt-arm", "debt-leg");
            for (String part : parts) {
                var frame = ART.frame(id, part).orElseThrow();
                var tip = frame.sockets().get("distal");
                var transform = ActorVisuals.segmentTransform(frame, tip, -7, -23, 11, -2);
                assertPoint(-7, -23, transform.transform(point(frame.anchor()), null));
                assertPoint(11, -2, transform.transform(point(tip), null));
                assertUniform(transform);
            }
        }
    }

    @Test void unreachableMeleeTargetsDoNotEnlargeOrDetachTheArm() {
        for (int step = 0; step <= 100; step++) {
            double phase = step / 100.0;
            var limb = ActorVisuals.solveLimb(-5, -29, 12 + Math.sin(phase * Math.PI) * 9,
                    -30 + phase * 24, 9, 11, -1);
            assertEquals(9, limb.joint().distance(-5, -29), 1e-7);
            assertEquals(11, limb.joint().distance(limb.tip()), 1e-7);
            assertTrue(limb.tip().distance(-5, -29) <= 20);
        }
    }

    @Test void plantedBootKeepsItsSoleAboveTheFloorAndItsWorldPositionDuringStance() {
        var shin = ART.frame("repair", "shin").orElseThrow();
        var ankle = shin.sockets().get("ankle");
        double scale = 10 / point(ankle).distance(point(shin.anchor()));
        for (int facing : new int[]{1, -1}) for (int step = 0; step < 20; step++) {
            double distance = step * 0.5, phase = distance * Math.PI / 20;
            Point2D contact = ActorVisuals.footAt(phase);
            var boot = ActorVisuals.bootTransform(shin, contact.getX(), contact.getY(), scale, 0);
            var heel = boot.transform(point(shin.sockets().get("heel")), null);
            var toe = boot.transform(point(shin.sockets().get("toe")), null);
            assertEquals(0, Math.max(heel.getY(), toe.getY()), 1e-9);
            assertEquals(facing * 10, facing * (distance + contact.getX()), 1e-9);
            assertUniform(boot);
        }
        for (int step = 1; step < 20; step++)
            assertTrue(ActorVisuals.footAt(Math.PI + step * Math.PI / 20).getY() < 0);
    }

    @Test void muzzleStaysAtTheActualPlayerEmissionPointInBothDirectionsThroughoutRecoil() {
        for (CharacterId role : CharacterId.values())
        for (int facing : new int[]{1, -1}) for (boolean crouching : new boolean[]{false,true})
        for (double angle : new double[]{-Math.PI/2,-Math.PI/4,0,Math.PI/4,Math.PI/2})
        for (int step = 0; step <= 20; step++) {
            var muzzle = ART.frame(role.art(), "cannon").orElseThrow().sockets().get("muzzle");
            var pose = new ActorVisuals.Hero(115,130,facing,ActorVisuals.Action.IDLE,0,
                    step/20.0,0,false,false,1,Math.cos(angle)*facing,Math.sin(angle),crouching,role);
            var expected=HeroAim.local(Math.cos(angle),Math.sin(angle),crouching,role);
            AffineTransform world = new AffineTransform();
            world.translate(115, 130); world.scale(facing, 1);
            world.concatenate(ActorVisuals.cannonTransform(ART, pose));
            assertPoint(115 + facing * expected.x(), 130+expected.y(), world.transform(point(muzzle), null));
            assertUniform(world);
        }
    }

    @Test void airborneActorsDoNotCarryAFloatingContactShadow() {
        var missing = new IndustrialArt(AssetStore.empty());
        for (var action : List.of(ActorVisuals.Action.RISE, ActorVisuals.Action.FALL, ActorVisuals.Action.DASH)) {
            BufferedImage image = render(missing, 1, 1, action);
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
                assertEquals(0, image.getRGB(x, y) >>> 24, action.toString());
        }
        assertTrue((render(missing, 1, 1, ActorVisuals.Action.IDLE).getRGB(200, 280) >>> 24) > 0);
    }

    @Test void counterMirroredVisorDoesNotDoubleItsOpacityDuringInvincibility() {
        for (int facing : new int[]{1, -1}) {
            BufferedImage image = render(ART, facing, 0.4f, ActorVisuals.Action.IDLE);
            // Interior of the CRT in this 4x inspection render, away from the metal bezel.
            int x = facing > 0 ? 217 : 182;
            for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++)
                assertEquals(102, image.getRGB(x + dx, 108 + dy) >>> 24, "The visor must receive one alpha pass");
        }
    }

    @Test void realStandingSilhouetteRemainsWithinTheAgreedVisualHeight() {
        BufferedImage image = render(ART, 1, 1, ActorVisuals.Action.IDLE);
        int top = image.getHeight();
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int pixel = image.getRGB(x, y);
            if ((pixel >>> 24) > 100 && ((pixel >> 16 & 255) + (pixel >> 8 & 255) + (pixel & 255)) > 40)
                top = Math.min(top, y);
        }
        double height = 70 - top / 4.0;
        assertTrue(height >= 45 && height <= 55, "Standing height including antenna: " + height);
    }

    @Test void stationaryEnemyDoesNotKeepWalkingInPlaceAsSimulationTimePasses() {
        var rig = new ActorVisuals();
        var player = new Player(10, 100);
        var enemy = new ObstacleManager.Enemy(100, 100, EntityType.TECHDEBT, 21);
        rig.update(player, List.of(enemy), 0);
        double phase = rig.snapshot().enemies().get(0).phase();
        for (int step = 0; step < 100; step++) rig.update(player, List.of(enemy), 0.016);
        assertEquals(phase, rig.snapshot().enemies().get(0).phase());
    }

    private static BufferedImage render(IndustrialArt art, int facing, float opacity, ActorVisuals.Action action) {
        BufferedImage image = new BufferedImage(400, 320, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.scale(4, 4);
            ActorVisuals.hero(g, art, new ActorVisuals.Hero(50, 70, facing, action,
                    0, 0, 0, false, false, opacity));
        } finally { g.dispose(); }
        return image;
    }

    private static Point2D point(AnimationClip.Point point) { return new Point2D.Double(point.x(), point.y()); }

    private static void assertPoint(double x, double y, Point2D point) {
        assertEquals(x, point.getX(), 1e-8); assertEquals(y, point.getY(), 1e-8);
    }

    private static void assertUniform(AffineTransform transform) {
        double ax = transform.getScaleX(), ay = transform.getShearY();
        double bx = transform.getShearX(), by = transform.getScaleY();
        assertEquals(Math.hypot(ax, ay), Math.hypot(bx, by), 1e-9);
        assertEquals(0, ax * bx + ay * by, 1e-9);
    }
}
