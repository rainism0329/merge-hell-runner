package com.bigphil.mergehell;

import com.bigphil.mergehell.combat.Aim;
import com.bigphil.mergehell.combat.WeaponId;
import com.bigphil.mergehell.model.*;
import com.bigphil.mergehell.progression.*;
import com.bigphil.mergehell.render.ActorVisuals;
import com.bigphil.mergehell.world.ExplorationRoute;
import org.junit.jupiter.api.Test;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises actual Player input/fire and production swept-world collision after the anatomical rig change. */
class PlayerMuzzleIntegrationTest {
    @Test void fourCharactersEmitAllSixWeaponsFromThePublishedPoseInEveryAirborneDirection() {
        for (CharacterId character : CharacterId.values()) for (WeaponId weapon : WeaponId.values())
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                var player = player(character, weapon, 300, 220);
                var shots = fire(player, dx, dy);
                assertFalse(player.isGrounded()); assertFalse(player.isCrouching());
                assertEquals(new Aim(dx, dy), player.getAim());
                assertMuzzleAndDirection(player, shots, character + " / " + weapon + " / " + dx + "," + dy);
            }
    }

    @Test void standingAndCrouchingFireStayInFrontOfTheTorsoWithGroundDownMappedToCrouch() {
        for (CharacterId character : CharacterId.values()) for (WeaponId weapon : WeaponId.values())
            for (int facing : new int[]{-1, 1}) for (boolean crouch : new boolean[]{false, true}) {
                var player = player(character, weapon, 300, 450); settle(player);
                var shots = fire(player, facing, crouch ? 1 : 0);
                assertTrue(player.isGrounded()); assertEquals(crouch, player.isCrouching());
                assertEquals(new Aim(facing, 0), player.getAim());
                double fromFeet = player.muzzleY() - (player.getY() + player.getBounds().height);
                assertTrue(fromFeet < -10 && fromFeet > -40, "Horizontal barrel stays between head and hip");
                assertMuzzleAndDirection(player, shots, character + " / " + weapon + " / crouch=" + crouch);
            }
    }

    @Test void firingFlushAgainstEveryChaptersWallCannotDamageTheEnemyOnItsFarSide() {
        for (int chapter = 0; chapter < 5; chapter++) for (CharacterId character : CharacterId.values())
            for (WeaponId weapon : WeaponId.values()) for (int facing : new int[]{-1, 1}) {
                var route = new ExplorationRoute(chapter);
                var wall = route.snapshot().blocks().stream().filter(b -> !b.canopy()).findFirst().orElseThrow().bounds();
                double x = facing > 0 ? wall.x - 30 : wall.getMaxX();
                var player = player(character, weapon, x, 450);
                player.setSolids(route.solids()); settle(player);
                var shots = fire(player, facing, 0);
                double targetX = facing > 0 ? wall.getMaxX() + 3 : wall.x - EntityType.TECHDEBT.width - 3;
                var enemy = new ObstacleManager.Enemy(targetX, 400, EntityType.TECHDEBT, 7L);
                assertWorldConsumesBeforeEnemy(player, shots, enemy, route, chapter + " / " + character + " / " + weapon);
            }
    }

    @Test void upwardFireInEveryLowPassageHitsItsCeilingBeforeAnEnemyAboveIt() {
        for (int chapter : new int[]{0, 1, 3, 4}) for (CharacterId character : CharacterId.values())
            for (WeaponId weapon : WeaponId.values()) {
                var route = new ExplorationRoute(chapter);
                var roof = route.snapshot().blocks().stream().filter(ExplorationRoute.Block::canopy).findFirst().orElseThrow().bounds();
                var player = player(character, weapon, roof.x - 60, 450); settle(player);
                player.setAimInput(false, true, true, false, false); settle(player);
                assertTrue(player.isCrouching());
                player.setSolids(route.solids()); player.setX(roof.getCenterX() - 15);
                var shots = fire(player, 0, -1);
                assertTrue(player.isCrouching(), "Roof prevents standing while aiming up");
                assertEquals(new Aim(0, -1), player.getAim());
                var enemy = new ObstacleManager.Enemy(player.getX() - 10, roof.y - EntityType.TECHDEBT.height - 3,
                        EntityType.TECHDEBT, 7L);
                assertWorldConsumesBeforeEnemy(player, shots, enemy, route, chapter + " / " + character + " / " + weapon);
            }
    }

    @Test void evenThinSolidFacesAreIncludedInTheFirstProjectileSweepAtBothFacings() {
        for (CharacterId character : CharacterId.values()) for (WeaponId weapon : WeaponId.values())
            for (int facing : new int[]{-1, 1}) {
                Rectangle thinWall = new Rectangle(400, 370, 2, 110);
                var player = player(character, weapon, facing > 0 ? 370 : 402, 450);
                player.setSolids(List.of(thinWall)); settle(player);
                for (var shot : fire(player, facing, 0)) {
                    shot.update();
                    assertTrue(Double.isFinite(shot.hitFraction(thinWall)),
                            character + " / " + weapon + " must not originate beyond a thin solid face");
                }
            }
    }

    private static Player player(CharacterId character, WeaponId weapon, double x, double y) {
        var player = new Player((int) x, (int) y); var build = new RunBuild(weapon);
        build.setIdentity(character, GameDifficulty.STANDARD); player.setRunBuild(build); player.setCombatSeed(71);
        return player;
    }
    private static void settle(Player player) {
        player.update(false, false, false, false, 480, 12000, new ArrayList<>(), List.of());
    }
    private static List<Projectile> fire(Player player, int dx, int dy) {
        var shots = new ArrayList<Projectile>();
        player.setAimInput(dy < 0, dy > 0, true, dx < 0, dx > 0);
        for (int tick = 0; tick < 12 && shots.isEmpty(); tick++)
            player.update(dx < 0, dx > 0, false, true, 480, 12000, shots, List.of());
        assertFalse(shots.isEmpty(), "A complete beam charge must emit within twelve simulation steps");
        return shots;
    }
    private static void assertMuzzleAndDirection(Player player, List<Projectile> shots, String detail) {
        var visuals = new ActorVisuals(); visuals.update(player, List.of(), .016);
        var pose = visuals.snapshot().hero();
        assertEquals(player.muzzleX(), pose.x() + pose.facing()
                * com.bigphil.mergehell.combat.HeroAim.local(pose.aimX() * pose.facing(), pose.aimY(),
                pose.crouching(), pose.character()).x(), 1e-9, detail);
        assertTrue((player.muzzleX() - pose.x()) * pose.facing() > 0, detail + " muzzle remains in front");
        assertTrue(player.muzzleY() >= pose.footY() - 65 && player.muzzleY() <= pose.footY(), detail);
        for (var shot : shots) {
            assertEquals(player.muzzleX(), shot.getBounds().getCenterX(), .6, detail);
            assertEquals(player.muzzleY(), shot.getBounds().getCenterY(), .6, detail);
            double projected = shot.getVx() * player.getAim().x() + shot.getVy() * player.getAim().y();
            assertTrue(projected / Math.hypot(shot.getVx(), shot.getVy()) > .8, detail);
        }
    }
    private static void assertWorldConsumesBeforeEnemy(Player player, List<Projectile> shots,
                                                       ObstacleManager.Enemy enemy, ExplorationRoute route, String detail) {
        var collision = new CollisionSystem(); collision.setWorldImpactHandler(route::consumeShot);
        var manager = new ObstacleManager(); manager.getEnemies().add(enemy); int originalHp = enemy.getHp();
        var live = new ArrayList<>(shots); var context = new CollisionSystem.Context();
        for (int tick = 0; tick < 15 && !live.isEmpty(); tick++)
            collision.process(context, live, manager, null, player, GameState.RUNNING, 960, 600,
                    (int) player.getX() - 300, new ArrayList<>(), new ArrayList<>(), ignored -> { });
        assertTrue(live.isEmpty(), detail + " solid consumes the shot");
        assertEquals(originalHp, enemy.getHp(), detail + " far-side target stays protected");
    }
}
