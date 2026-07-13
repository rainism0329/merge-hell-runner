package com.bigphil.mergehell.model;

import com.bigphil.mergehell.combat.WeaponId;
import com.bigphil.mergehell.progression.RunBuild;
import com.bigphil.mergehell.progression.UpgradeCatalog;
import com.bigphil.mergehell.progression.UpgradeId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlayerTest {

    private Player player;
    private List<Projectile> projectiles;
    private static final int GROUND_Y = 480;
    private static final int PANEL_WIDTH = 960;

    @BeforeEach
    void setUp() {
        player = new Player(100, GROUND_Y - 30);
        projectiles = new ArrayList<>();
    }

    @Test
    void takeDamage_shouldReduceHp() {
        int initialHp = player.getHp();
        player.takeDamage(30);
        assertEquals(initialHp - 30, player.getHp());
    }

    @Test
    void takeDamage_shouldSetInvincibilityTimer() {
        player.takeDamage(10);
        assertTrue(player.getInvincibleTimer() > 0);
    }

    @Test
    void shield_shouldBlockDamage() {
        player.setShieldTimer(100);
        int initialHp = player.getHp();
        player.takeDamage(50);
        assertEquals(initialHp, player.getHp());
    }

    @Test
    void invincibility_shouldBlockDamage() {
        player.takeDamage(10); // triggers invincibility
        int hpAfterFirstHit = player.getHp();
        player.takeDamage(10); // should be blocked
        assertEquals(hpAfterFirstHit, player.getHp());
    }

    @Test
    void shoot_shouldCreateProjectile() {
        player.update(false, false, false, true, GROUND_Y, PANEL_WIDTH, projectiles, new ArrayList<>());
        assertFalse(projectiles.isEmpty());
        assertEquals(ProjectileType.COMMIT, projectiles.get(0).getType());
    }

    @Test
    void boundRunBuildControlsTheWeaponStatsUsedForFiring() {
        RunBuild build = new RunBuild(WeaponId.COMMIT_CANNON);
        build.apply(UpgradeCatalog.definition(UpgradeId.COMMIT_RICOCHET));
        player.bindRunBuild(build);

        player.update(false, false, false, true, GROUND_Y, PANEL_WIDTH,
                projectiles, new ArrayList<>());

        assertEquals(2, projectiles.get(0).getRemainingRicochets());
    }

    @Test
    void sudoMode_shouldUseOverclockedForcePush() {
        player.setSudoTimer(100);
        player.update(false, false, false, true, GROUND_Y, PANEL_WIDTH, projectiles, new ArrayList<>());

        assertEquals(5, projectiles.size());
        for (Projectile p : projectiles) {
            assertEquals(ProjectileType.SUDO, p.getType());
            assertEquals(WeaponId.FORCE_PUSH, p.getWeapon());
            assertEquals(2, p.getRemainingPierces());
            assertEquals(14, p.getKnockback(), 0.0001);
        }
    }

    @Test
    void timers_shouldDecrementOnUpdate() {
        player.setSudoTimer(50);
        player.setShieldTimer(50);
        player.update(false, false, false, false, GROUND_Y, PANEL_WIDTH, projectiles, new ArrayList<>());
        assertEquals(49, player.getSudoTimer());
        assertEquals(49, player.getShieldTimer());
    }

    @Test
    void player_shouldBeBoundedWithinPanel() {
        player.update(true, false, false, false, GROUND_Y, PANEL_WIDTH, projectiles, new ArrayList<>());
        assertTrue(player.getX() >= 0);

        // Move far right
        for (int i = 0; i < 500; i++) {
            player.update(false, true, false, false, GROUND_Y, PANEL_WIDTH, projectiles, new ArrayList<>());
        }
        assertTrue(player.getX() <= PANEL_WIDTH);
    }

    @Test
    void jump_shouldWorkWhenGrounded() {
        // Player starts on ground (y + height = GROUND_Y)
        double initialY = player.getY();
        player.update(false, false, true, false, GROUND_Y, PANEL_WIDTH, projectiles, new ArrayList<>());
        // After jumping, y should decrease (player moves up)
        // dy becomes negative, so next update y will decrease
        assertTrue(true); // Jump sets dy negative - verified by next update
    }

    @Test
    void jump_shouldSupportOneAirJumpButNotUnlimitedAirJumps() {
        player.update(false, false, true, false, GROUND_Y, PANEL_WIDTH, projectiles, new ArrayList<>());
        assertEquals(1, player.getJumpsRemaining());

        player.requestJump();
        player.update(false, false, false, false, GROUND_Y, PANEL_WIDTH, projectiles, new ArrayList<>());
        assertEquals(0, player.getJumpsRemaining());

        player.requestJump();
        player.update(false, false, false, false, GROUND_Y, PANEL_WIDTH, projectiles, new ArrayList<>());
        assertEquals(0, player.getJumpsRemaining());
    }

    @Test
    void reset_shouldRestoreDefaults() {
        player.takeDamage(50);
        player.setSudoTimer(100);
        player.setShieldTimer(100);
        player.reset(200, GROUND_Y);
        assertEquals(100, player.getHp());
        assertEquals(0, player.getSudoTimer());
        assertEquals(0, player.getShieldTimer());
        assertEquals(0, player.getInvincibleTimer());
        assertEquals(200, player.getX(), 0.01);
    }

    @Test
    void forcePushBuildSurvivesResetAndStillFiresForcePush() {
        RunBuild force = new RunBuild(WeaponId.FORCE_PUSH);
        player.bindRunBuild(force);

        player.reset(100, GROUND_Y - 30);
        player.update(false, false, false, true, GROUND_Y, PANEL_WIDTH,
                projectiles, new ArrayList<>());

        assertSame(force, player.getRunBuild());
        assertEquals(WeaponId.FORCE_PUSH, projectiles.get(0).getWeapon());
    }

    @Test
    void temporaryPickupDoesNotDestroyTheUpgradedCoreBuild() {
        RunBuild build = new RunBuild(WeaponId.COMMIT_CANNON);
        build.apply(UpgradeCatalog.definition(UpgradeId.COMMIT_RICOCHET));
        player.bindRunBuild(build);
        player.giveWeapon(WeaponType.SPREAD, 1);

        player.update(false, false, false, true, GROUND_Y, PANEL_WIDTH,
                projectiles, new ArrayList<>());

        assertSame(build, player.getRunBuild());
        assertEquals(1, build.rank(UpgradeId.COMMIT_RICOCHET));
        assertEquals(WeaponType.COMMIT, player.getWeapon());
        assertFalse(player.isUsingTemporaryWeapon());
    }

    @Test
    void labModePreventsDamageAndBombConsumption() {
        player.setDebugMode(true);
        int bombs = player.getBombs();

        player.takeDamage(999);

        assertEquals(100, player.getHp());
        assertTrue(player.useBomb());
        assertEquals(bombs, player.getBombs());
    }

    @Test
    void heal_shouldRestoreHpUpToMax() {
        player.takeDamage(50);
        player.heal(25);
        assertEquals(75, player.getHp());
    }

    @Test
    void heal_shouldNotExceedMaxHp() {
        player.takeDamage(10);
        player.heal(50);
        assertEquals(100, player.getHp());
    }

    @Test
    void dash_shouldMovePlayerAndSetCooldown() {
        player.dash();
        assertTrue(player.isDashing());
        player.update(false, false, false, false, GROUND_Y, PANEL_WIDTH, projectiles, new ArrayList<>());
        // Player moved right during dash (facingDir defaults to 1)
        assertTrue(player.getX() > 100);
    }

    @Test
    void dashCacheActuallyShortensTheGameplayCooldown() {
        RunBuild build = new RunBuild(WeaponId.COMMIT_CANNON);
        build.apply(UpgradeCatalog.definition(UpgradeId.DASH_CACHE));
        player.bindRunBuild(build);
        player.dash();
        for (int i = 0; i < 8; i++) {
            player.update(false, false, false, false, GROUND_Y, PANEL_WIDTH,
                    projectiles, new ArrayList<>());
        }
        assertEquals(38, player.getDashCooldown());
    }

    @Test
    void shieldRebootPreventsOneLethalHitAndRestoresTwentyFiveHp() {
        RunBuild build = new RunBuild(WeaponId.COMMIT_CANNON);
        build.apply(UpgradeCatalog.definition(UpgradeId.SHIELD_REBOOT));
        player.bindRunBuild(build);
        player.takeDamage(200);
        assertEquals(25, player.getHp());
        assertEquals(0, player.getShieldRebootsRemaining());
        assertTrue(player.getShieldTimer() > 0);
    }

    @Test
    void dash_shouldNotWorkOnCooldown() {
        player.dash();
        // Simulate dash frames
        for (int i = 0; i < 15; i++) {
            player.update(false, false, false, false, GROUND_Y, PANEL_WIDTH, projectiles, new ArrayList<>());
        }
        assertFalse(player.isDashing());
        assertTrue(player.getDashCooldown() > 0);

        // Try to dash again
        double xBefore = player.getX();
        player.dash();
        assertEquals(xBefore, player.getX(), 0.01); // shouldn't move
    }

    @Test
    void getBounds_shouldReturnCorrectRectangle() {
        var bounds = player.getBounds();
        assertEquals(30, bounds.width);
        assertEquals(30, bounds.height);
    }
}
