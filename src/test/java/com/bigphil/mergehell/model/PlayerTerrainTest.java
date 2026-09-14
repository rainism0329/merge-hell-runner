package com.bigphil.mergehell.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlayerTerrainTest {
    private static final int GROUND = 480;

    @Test void ordinaryWalkingClimbsLowStepsInBothDirections() {
        Player right = settled(110);
        tick(right, false, true, List.of(new Platform(140, 456, 80, 24)));
        assertEquals(115, right.getX()); assertEquals(426, right.getY());
        assertTrue(right.isGrounded()); assertEquals(2, right.getJumpsRemaining());

        Player left = settled(200);
        tick(left, true, false, List.of(new Platform(100, 456, 100, 24)));
        assertEquals(195, left.getX()); assertEquals(426, left.getY());
        assertTrue(left.isGrounded());
    }

    @Test void consecutiveTwentyFourPixelStepsReachTheUpperWalkway() {
        Player player = settled(110);
        List<Platform> stairs = List.of(new Platform(220, 432, 120, 48), new Platform(140, 456, 80, 24));
        for (int frame = 0; frame < 25; frame++) tick(player, false, true, stairs);
        assertEquals(235, player.getX()); assertEquals(402, player.getY());
        assertTrue(player.isGrounded());
    }

    @Test void tallPlatformsRequireJumpingAndAirborneActorsCannotAutoStep() {
        Player player = settled(110);
        List<Platform> high = List.of(new Platform(140, 400, 120, 80));
        for (int frame = 0; frame < 12; frame++) tick(player, false, true, high);
        assertEquals(450, player.getY(), "An 80px high deck cannot lift the player from below");

        Player airborne = new Player(110, 440);
        tick(airborne, false, true, List.of(new Platform(140, 456, 120, 24)));
        assertEquals(440.6, airborne.getY(), 1e-9);
        assertFalse(airborne.isGrounded());
    }

    @Test void fastFallsLandOnTheHighestCrossedSurfaceRegardlessOfListOrder() throws Exception {
        Platform upper = new Platform(80, 350, 200, 4);
        Platform lower = new Platform(80, 420, 200, 4);
        for (List<Platform> platforms : List.of(List.of(lower, upper), List.of(upper, lower))) {
            Player player = new Player(100, 270);
            setVerticalVelocity(player, 250);
            tick(player, false, false, platforms);
            assertEquals(320, player.getY(), "Thin platform must catch the fall before the ground");
            assertEquals(0, player.getVerticalVelocity()); assertTrue(player.isGrounded());
        }
    }

    @Test void jumpingFromUnderAPlatformPassesThroughThenLandsFromAbove() {
        Player player = settled(160);
        List<Platform> bridge = List.of(new Platform(100, 400, 240, 80));
        player.requestJump(); tick(player, false, false, bridge);
        assertEquals(450, player.getY(), "Jump must launch from ground, not teleport onto the overhead deck");
        assertEquals(-13, player.getVerticalVelocity());
        assertEquals(1, player.getJumpsRemaining());
        for (int frame = 0; frame < 4; frame++) {
            tick(player, false, false, bridge);
            assertTrue(player.getVerticalVelocity() < 0); assertFalse(player.isGrounded());
        }
        for (int frame = 0; frame < 60 && !player.isGrounded(); frame++) tick(player, false, false, bridge);
        assertTrue(player.isGrounded()); assertEquals(370, player.getY());
        assertEquals(2, player.getJumpsRemaining());
    }

    @Test void descendingBelowADeckCannotBeSnappedOntoItsTop() throws Exception {
        Player player = new Player(160, 430);
        setVerticalVelocity(player, 1);
        tick(player, false, false, List.of(new Platform(100, 400, 240, 80)));
        assertEquals(431.6, player.getY(), 1e-9); assertFalse(player.isGrounded());
    }

    @Test void leavingTheBridgeStartsFallingAndPreservesTheCoyoteJump() {
        Player player = new Player(100, 370);
        List<Platform> bridge = List.of(new Platform(80, 400, 60, 8));
        tick(player, false, false, bridge); assertTrue(player.isGrounded());
        for (int frame = 0; frame < 8; frame++) tick(player, false, true, bridge);
        assertFalse(player.isGrounded()); assertTrue(player.getY() > 370);
        assertTrue(player.getVerticalVelocity() > 0);
        player.requestJump(); tick(player, false, true, bridge);
        assertEquals(-13, player.getVerticalVelocity()); assertEquals(1, player.getJumpsRemaining());
    }

    @Test void dashUnderAHigherDeckKeepsItsEightTickTravelWithoutTeleportingAtExit() {
        Player player = settled(100);
        List<Platform> bridge = List.of(new Platform(135, 400, 240, 80));
        player.dash(1);
        for (int frame = 0; frame < 8; frame++) {
            tick(player, false, true, bridge);
            assertEquals(110 + frame * 10, player.getX()); assertEquals(450, player.getY());
        }
        assertFalse(player.isDashing()); assertEquals(45, player.getDashCooldown());
        tick(player, false, true, bridge);
        assertEquals(185, player.getX()); assertEquals(450, player.getY());
    }

    @Test void shootingWhileClimbingUsesTheNewMuzzleHeightAndOnlyHorizontalRunMomentum() {
        Player player = settled(110);
        List<Projectile> shots = new ArrayList<>();
        player.update(false, true, false, true, GROUND, 4000, shots,
                List.of(new Platform(140, 456, 120, 24)));
        assertEquals(1, shots.size()); assertEquals(16, shots.get(0).getVx());
        assertEquals(0, shots.get(0).getVy()); assertEquals(441, shots.get(0).getY());
    }

    private static Player settled(int x) {
        Player player = new Player(x, GROUND - 30);
        tick(player, false, false, List.of()); assertTrue(player.isGrounded());
        return player;
    }

    private static void tick(Player player, boolean left, boolean right, List<Platform> platforms) {
        player.update(left, right, false, false, GROUND, 4000, new ArrayList<>(), platforms);
    }

    private static void setVerticalVelocity(Player player, double value) throws Exception {
        Field velocity = Player.class.getDeclaredField("dy"); velocity.setAccessible(true); velocity.setDouble(player, value);
    }
}
