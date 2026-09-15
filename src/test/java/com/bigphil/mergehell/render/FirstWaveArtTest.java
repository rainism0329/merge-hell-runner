package com.bigphil.mergehell.render;

import com.bigphil.mergehell.assets.AnimationClip;
import com.bigphil.mergehell.model.EntityType;
import com.bigphil.mergehell.model.ObstacleManager;
import com.bigphil.mergehell.model.Player;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FirstWaveArtTest {
    private static final IndustrialArt ART = IndustrialArt.load();
    private static final Map<EntityType, String> PARTS = Map.of(EntityType.CONFLICT, "conflict",
            EntityType.CRASH, "crash", EntityType.LOCK, "lock", EntityType.FIREWALL, "firewall");

    @Test
    void bodyFitPreservesAspectRatioAndTheLogicalContactPlane() {
        assertTrue(ART.diagnostics().isEmpty(), () -> ART.diagnostics().toString());
        PARTS.forEach((type, part) -> {
            AnimationClip.Frame frame = ART.frame("first-wave", part).orElseThrow();
            AffineTransform transform = ActorVisuals.firstWaveTransform(frame, type.width, type.height);
            assertEquals(transform.getScaleX(), transform.getScaleY(), 1e-12, part + " must keep bearings circular");
            Point2D contact = transform.transform(new Point2D.Double(frame.anchor().x(), frame.anchor().y()), null);
            assertEquals(0, contact.getX(), 1e-9);
            assertEquals(0, contact.getY(), 1e-9);
            Point2D topLeft = transform.transform(new Point2D.Double(0, 0), null);
            Point2D bottomRight = transform.transform(new Point2D.Double(frame.width(), frame.height()), null);
            assertTrue(topLeft.getX() >= -type.width / 2.0 - 1e-9);
            assertTrue(bottomRight.getX() <= type.width / 2.0 + 1e-9);
            assertTrue(topLeft.getY() >= -type.height - 1e-9);
            assertTrue(bottomRight.getY() <= 1, "Only the transparent guard may cross the contact plane");
        });
    }

    @Test
    void lockApertureIsTransparentWhileItsCoreAndOtherEnemyCoresRemainVisible() throws IOException {
        try (InputStream source = getClass().getClassLoader().getResourceAsStream("game/art/first-wave.png")) {
            assertNotNull(source);
            BufferedImage atlas = ImageIO.read(source);
            assertTrue(atlas.getColorModel().hasAlpha());
            PARTS.forEach((type, part) -> {
                AnimationClip.Frame frame = ART.frame("first-wave", part).orElseThrow();
                assertTrue(alphaAt(atlas, frame, frame.sockets().get("core")) > 240, part + " optic was erased");
            });
            AnimationClip.Frame lock = ART.frame("first-wave", "lock").orElseThrow();
            assertEquals(0, alphaAt(atlas, lock, lock.sockets().get("aperture")));
        }
    }

    @Test
    void attachedChargeFollowsActualEnemyTelegraphAndDisappearsAfterTheShot() {
        for (EntityType type : List.of(EntityType.CONFLICT, EntityType.LOCK)) {
            Player player = new Player(10, 100);
            ObstacleManager.Enemy enemy = new ObstacleManager.Enemy(100, 100, type, 73L);
            ActorVisuals visuals = new ActorVisuals();
            visuals.update(player, List.of(enemy), .016);
            assertEquals(0, chargePixels(visuals.snapshot().enemies().get(0)));
            for (int i = 0; i < 121 && enemy.getTelegraphTicks() == 0; i++) enemy.maybeShoot(110);
            assertTrue(enemy.getTelegraphTicks() > 0, "Real shooter must enter its warning state");
            visuals.update(player, List.of(enemy), .016);
            assertEquals(enemy.getTelegraphTicks(), visuals.snapshot().enemies().get(0).warning());
            assertTrue(chargePixels(visuals.snapshot().enemies().get(0)) > 0);
            while (enemy.getTelegraphTicks() > 0) enemy.maybeShoot(110);
            visuals.update(player, List.of(enemy), .016);
            assertEquals(0, chargePixels(visuals.snapshot().enemies().get(0)));
        }
    }

    @Test
    void floatingLockDoesNotCarryAFakeGroundContactShadowThroughTheAir() {
        BufferedImage image = render(new ActorVisuals.Hostile(EntityType.LOCK, 100, 100, 40, 40,
                -1, 1, 1, 0, 0, 0, 0));
        for (int y = 141; y < 145; y++) for (int x = 90; x < 151; x++)
            assertEquals(0, image.getRGB(x, y) >>> 24, "No contact shadow below an airborne drone");
    }

    private static int alphaAt(BufferedImage atlas, AnimationClip.Frame frame, AnimationClip.Point point) {
        return atlas.getRGB(frame.x() + (int) Math.round(point.x()), frame.y() + (int) Math.round(point.y())) >>> 24;
    }

    private static BufferedImage render(ActorVisuals.Hostile pose) {
        BufferedImage image = new BufferedImage(240, 240, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try { ActorVisuals.enemy(graphics, ART, pose); } finally { graphics.dispose(); }
        return image;
    }

    private static int chargePixels(ActorVisuals.Hostile pose) {
        var quiet = new ActorVisuals.Hostile(pose.type(), pose.x(), pose.y(), pose.width(), pose.height(),
                pose.facing(), pose.hp(), pose.maxHp(), 0, pose.phase(), pose.hit(), pose.death(), pose.tactics());
        BufferedImage actual = render(pose), idle = render(quiet);
        int count = 0;
        for (int y = 0; y < actual.getHeight(); y++) for (int x = 0; x < actual.getWidth(); x++)
            if (actual.getRGB(x, y) != idle.getRGB(x, y)) count++;
        // No old floating exclamation badge remains above the body.
        for (int y = 71; y < 90; y++) for (int x = 109; x < 132; x++)
            assertEquals(0, actual.getRGB(x, y) >>> 24);
        return count;
    }
}
