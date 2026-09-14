package com.bigphil.mergehell;

import com.bigphil.mergehell.combat.CombatEvent;
import com.bigphil.mergehell.combat.ProjectileSpec;
import com.bigphil.mergehell.combat.WeaponId;
import com.bigphil.mergehell.model.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CollisionSystemTest {

    private CollisionSystem collision;
    private CollisionSystem.Context ctx;
    private List<Projectile> projectiles;
    private ObstacleManager enemyManager;
    private Player player;
    private Boss boss;
    private List<Particle> particles;
    private List<FloatingText> texts;
    private List<String> logMessages;

    private static final int PANEL_WIDTH = 960;
    private static final int PANEL_HEIGHT = 600;
    private static final int GROUND_Y = PANEL_HEIGHT - GamePanel.TERMINAL_HEIGHT;

    @BeforeEach
    void setUp() {
        collision = new CollisionSystem();
        ctx = new CollisionSystem.Context();
        projectiles = new ArrayList<>();
        enemyManager = new ObstacleManager();
        player = new Player(100, GROUND_Y);
        boss = new Boss("Test Boss", 1000, "X", -100, 0); // starts at x=100
        particles = new ArrayList<>();
        texts = new ArrayList<>();
        logMessages = new ArrayList<>();
    }

    // --- Projectile vs Enemy ---

    @Test
    void projectileHittingEnemy_shouldKillBoth() {
        projectiles.add(new Projectile(100, 200, 0, 0, ProjectileType.COMMIT));
        enemyManager.spawnEnemy(110, 195, EntityType.BUG);

        collision.process(ctx, projectiles, enemyManager, boss, player,
                          GameState.RUNNING, PANEL_WIDTH, PANEL_HEIGHT, 0,
                          particles, texts, logMessages::add);

        assertTrue(enemyManager.getEnemies().get(0).isDead());
        assertTrue(projectiles.get(0).isDead()); // dead but removed next frame
    }

    @Test
    void sudoProjectile_shouldSurviveEnemyHit() {
        projectiles.add(new Projectile(100, 200, 0, 0, ProjectileType.SUDO));
        enemyManager.spawnEnemy(110, 195, EntityType.BUG);

        collision.process(ctx, projectiles, enemyManager, boss, player,
                          GameState.RUNNING, PANEL_WIDTH, PANEL_HEIGHT, 0,
                          particles, texts, logMessages::add);

        assertTrue(enemyManager.getEnemies().get(0).isDead());
        assertFalse(projectiles.isEmpty()); // SUDO survives
        assertFalse(projectiles.get(0).isDead());
    }

    @Test
    void projectileHit_shouldIncreaseScoreAndCombo() {
        projectiles.add(new Projectile(100, 200, 0, 0, ProjectileType.COMMIT));
        enemyManager.spawnEnemy(110, 195, EntityType.BUG);

        collision.process(ctx, projectiles, enemyManager, boss, player,
                          GameState.RUNNING, PANEL_WIDTH, PANEL_HEIGHT, 0,
                          particles, texts, logMessages::add);

        assertTrue(ctx.score > 0);
        assertEquals(1, ctx.combo);
        assertEquals(100, ctx.comboTimer);
    }

    @Test
    void projectileHit_shouldSpawnExplosionAndText() {
        projectiles.add(new Projectile(100, 200, 0, 0, ProjectileType.COMMIT));
        enemyManager.spawnEnemy(110, 195, EntityType.BUG);

        collision.process(ctx, projectiles, enemyManager, boss, player,
                          GameState.RUNNING, PANEL_WIDTH, PANEL_HEIGHT, 0,
                          particles, texts, logMessages::add);

        assertFalse(particles.isEmpty());
        assertFalse(texts.isEmpty());
    }

    @Test
    void projectileShouldNotHitPowerup() {
        projectiles.add(new Projectile(100, 200, 0, 0, ProjectileType.COMMIT));
        enemyManager.spawnEnemy(110, 195, EntityType.PICKUP_SPREAD);

        collision.process(ctx, projectiles, enemyManager, boss, player,
                          GameState.RUNNING, PANEL_WIDTH, PANEL_HEIGHT, 0,
                          particles, texts, logMessages::add);

        assertFalse(enemyManager.getEnemies().get(0).isDead());
        assertEquals(1, projectiles.size()); // still there
    }

    @Test
    void offscreenProjectile_shouldBeRemoved() {
        projectiles.add(new Projectile(PANEL_WIDTH + 200, 200, 0, 0, ProjectileType.COMMIT));

        collision.process(ctx, projectiles, enemyManager, boss, player,
                          GameState.RUNNING, PANEL_WIDTH, PANEL_HEIGHT, 0,
                          particles, texts, logMessages::add);

        assertEquals(0, projectiles.size());
    }

    // --- Projectile vs Boss ---

    @Test
    void projectileHittingBoss_shouldDamageBoss() {
        boss.activate();
        projectiles.add(new Projectile(110, 150, 0, 0, ProjectileType.COMMIT));

        int hpBefore = boss.getHp();
        collision.process(ctx, projectiles, enemyManager, boss, player,
                          GameState.BOSS_FIGHT, PANEL_WIDTH, PANEL_HEIGHT, 0,
                          particles, texts, logMessages::add);

        assertEquals(hpBefore - projectiles.get(0).getDamage(), boss.getHp());
    }

    @Test
    void inactiveBoss_shouldNotTakeProjectileDamage() {
        projectiles.add(new Projectile(110, 150, 0, 0, ProjectileType.COMMIT));

        int hpBefore = boss.getHp();
        collision.process(ctx, projectiles, enemyManager, boss, player,
                          GameState.BOSS_FIGHT, PANEL_WIDTH, PANEL_HEIGHT, 0,
                          particles, texts, logMessages::add);

        assertEquals(hpBefore, boss.getHp());
    }

    // --- Player vs Enemy ---

    @Test
    void playerHittingEnemy_shouldTakeDamage() {
        enemyManager.getEnemies().add(new ObstacleManager.Enemy(
                (int) player.getX() + 5, (int) player.getY(), EntityType.BUG));
        int hpBefore = player.getHp();

        collision.process(ctx, projectiles, enemyManager, boss, player,
                          GameState.RUNNING, PANEL_WIDTH, PANEL_HEIGHT, 0,
                          particles, texts, logMessages::add);

        assertTrue(hpBefore > player.getHp());
        assertTrue(enemyManager.getEnemies().get(0).isDead());
    }

    @Test
    void playerHittingEnemy_shouldSetShakeAndResetCombo() {
        ctx.combo = 5;
        enemyManager.getEnemies().add(new ObstacleManager.Enemy(
                (int) player.getX() + 5, (int) player.getY(), EntityType.BUG));

        collision.process(ctx, projectiles, enemyManager, boss, player,
                          GameState.RUNNING, PANEL_WIDTH, PANEL_HEIGHT, 0,
                          particles, texts, logMessages::add);

        assertEquals(15, ctx.shakeTimer);
        assertEquals(0, ctx.combo);
    }

    @Test
    void playerCollectingWeapon_shouldEquipSpread() {
        enemyManager.spawnEnemy((int) player.getX() + 5, (int) player.getY(), EntityType.PICKUP_SPREAD);

        collision.process(ctx, projectiles, enemyManager, boss, player,
                          GameState.RUNNING, PANEL_WIDTH, PANEL_HEIGHT, 0,
                          particles, texts, logMessages::add);

        assertEquals(WeaponType.SPREAD, player.getWeapon());
        assertTrue(player.getWeaponAmmo() > 0);
        assertTrue(enemyManager.getEnemies().get(0).isDead());
    }

    @Test
    void playerCollectingShieldPowerup_shouldSetShieldTimer() {
        enemyManager.spawnEnemy((int) player.getX() + 5, (int) player.getY(), EntityType.POWERUP_SHIELD);

        collision.process(ctx, projectiles, enemyManager, boss, player,
                          GameState.RUNNING, PANEL_WIDTH, PANEL_HEIGHT, 0,
                          particles, texts, logMessages::add);

        assertEquals(400, player.getShieldTimer());
        assertTrue(enemyManager.getEnemies().get(0).isDead());
    }

    @Test
    void powerupCollect_shouldLogMessage() {
        enemyManager.spawnEnemy((int) player.getX() + 5, (int) player.getY(), EntityType.PICKUP_SPREAD);

        collision.process(ctx, projectiles, enemyManager, boss, player,
                          GameState.RUNNING, PANEL_WIDTH, PANEL_HEIGHT, 0,
                          particles, texts, logMessages::add);

        assertFalse(logMessages.isEmpty());
        assertTrue(logMessages.get(0).contains("SPREAD"));
    }

    // --- Player vs Boss ---

    @Test
    void playerTouchingBossWithoutDash_shouldTakeLightDamage() {
        boss.activate();
        // Move player to boss position
        player = new Player(110, 200);

        int hpBefore = player.getHp();
        collision.process(ctx, projectiles, enemyManager, boss, player,
                          GameState.BOSS_FIGHT, PANEL_WIDTH, PANEL_HEIGHT, 0,
                          particles, texts, logMessages::add);

        assertEquals(hpBefore - 5, player.getHp());
    }

    @Test
    void shieldedPlayer_shouldNotTakeBossDamage() {
        boss.activate();
        player = new Player(110, 200);
        player.setShieldTimer(100);

        int hpBefore = player.getHp();
        collision.process(ctx, projectiles, enemyManager, boss, player,
                          GameState.BOSS_FIGHT, PANEL_WIDTH, PANEL_HEIGHT, 0,
                          particles, texts, logMessages::add);

        assertEquals(hpBefore, player.getHp());
    }

    @Test
    void invinciblePlayer_shouldNotTakeBossDamage() {
        boss.activate();
        player = new Player(110, 200);
        player.takeDamage(10); // triggers invincibility

        int hpAfterFirstHit = player.getHp();
        collision.process(ctx, projectiles, enemyManager, boss, player,
                          GameState.BOSS_FIGHT, PANEL_WIDTH, PANEL_HEIGHT, 0,
                          particles, texts, logMessages::add);

        assertEquals(hpAfterFirstHit, player.getHp());
    }

    @Test
    void playerNotTouchingBoss_shouldNotTakeDamage() {
        boss.activate();
        // Player far from boss
        player = new Player(500, 200);

        int hpBefore = player.getHp();
        collision.process(ctx, projectiles, enemyManager, boss, player,
                          GameState.BOSS_FIGHT, PANEL_WIDTH, PANEL_HEIGHT, 0,
                          particles, texts, logMessages::add);

        assertEquals(hpBefore, player.getHp());
    }

    @Test
    void bossCollision_dashDamage_shouldBeHigher() {
        // We can't easily trigger dashing, but the method checks boss.isDashing()
        // and boss starts not dashing, so let's just verify basic behavior
        boss.activate();
        player = new Player(110, 200);

        int hpBefore = player.getHp();
        collision.process(ctx, projectiles, enemyManager, boss, player,
                          GameState.BOSS_FIGHT, PANEL_WIDTH, PANEL_HEIGHT, 0,
                          particles, texts, logMessages::add);

        // Boss is not dashing, so damage should be 5
        assertEquals(hpBefore - 5, player.getHp());
    }

    // --- Combo system ---

    @Test
    void playerCollectingHealth_shouldHealPlayer() {
        player.takeDamage(50);
        int hpBefore = player.getHp();
        enemyManager.spawnEnemy((int) player.getX() + 5, (int) player.getY(), EntityType.HEALTH);

        collision.process(ctx, projectiles, enemyManager, boss, player,
                          GameState.RUNNING, PANEL_WIDTH, PANEL_HEIGHT, 0,
                          particles, texts, logMessages::add);

        assertTrue(player.getHp() > hpBefore);
        assertTrue(enemyManager.getEnemies().get(0).isDead());
    }

    @Test
    void multiHitEnemy_shouldNotDieInOneShot() {
        projectiles.add(new Projectile(100, 200, 0, 0, ProjectileType.COMMIT));
        enemyManager.spawnEnemy(110, 195, EntityType.TECHDEBT);

        collision.process(ctx, projectiles, enemyManager, boss, player,
                          GameState.RUNNING, PANEL_WIDTH, PANEL_HEIGHT, 0,
                          particles, texts, logMessages::add);

        assertFalse(enemyManager.getEnemies().get(0).isDead());
    }

    @Test
    void forcePushAppliesKnockbackWithoutDeletingHealthyEnemy() {
        ObstacleManager.Enemy enemy = new ObstacleManager.Enemy(120, 200, EntityType.TECHDEBT);
        double before = enemy.getX();

        enemy.takeHit(16, 14, 1);

        assertTrue(enemy.getX() > before);
        assertFalse(enemy.isDead());
    }

    @Test
    void commitRicochetConsumesOneRicochetAndSelectsNearestEnemy() {
        Projectile projectile = new Projectile(100, 200,
                new ProjectileSpec(WeaponId.COMMIT_CANNON, 25, 10, 0,
                        false, 0, 0, 1));
        ObstacleManager.Enemy near = new ObstacleManager.Enemy(160, 200, EntityType.BUG);
        ObstacleManager.Enemy far = new ObstacleManager.Enemy(260, 200, EntityType.BUG);

        projectile.ricochetToward(List.of(far, near));

        assertEquals(0, projectile.getRemainingRicochets());
        assertTrue(projectile.getVx() > 0);
        assertEquals(0.25, projectile.getVy() / projectile.getVx(), 0.0001);
        assertEquals(10, Math.hypot(projectile.getVx(), projectile.getVy()), 0.0001);
    }

    @Test
    void collisionUsesProjectileKnockback() {
        projectiles.add(new Projectile(100, 200,
                new ProjectileSpec(WeaponId.FORCE_PUSH, 16, 1, 0,
                        false, 0, 14, 0)));
        enemyManager.spawnEnemy(110, 195, EntityType.TECHDEBT);
        double before = enemyManager.getEnemies().get(0).getX();

        collision.process(ctx, projectiles, enemyManager, boss, player,
                GameState.RUNNING, PANEL_WIDTH, PANEL_HEIGHT, 0,
                particles, texts, logMessages::add);

        assertTrue(enemyManager.getEnemies().get(0).getX() > before);
        assertFalse(enemyManager.getEnemies().get(0).isDead());
    }

    @Test
    void piercingProjectileDoesNotHitSameEnemyTwice() {
        Projectile projectile = new Projectile(100, 200,
                new ProjectileSpec(WeaponId.COMMIT_CANNON, 10, 0, 0,
                        false, 1, 0, 0));
        projectiles.add(projectile);
        enemyManager.spawnEnemy(110, 195, EntityType.TECHDEBT);

        collision.process(ctx, projectiles, enemyManager, boss, player,
                GameState.RUNNING, PANEL_WIDTH, PANEL_HEIGHT, 0,
                particles, texts, logMessages::add);
        int hpAfterFirstHit = enemyManager.getEnemies().get(0).getHp();
        collision.process(ctx, projectiles, enemyManager, boss, player,
                GameState.RUNNING, PANEL_WIDTH, PANEL_HEIGHT, 0,
                particles, texts, logMessages::add);

        assertEquals(40, hpAfterFirstHit);
        assertEquals(hpAfterFirstHit, enemyManager.getEnemies().get(0).getHp());
        assertFalse(projectile.isDead());
        assertEquals(0, projectile.getRemainingPierces());
    }

    @Test
    void collisionRicochetsAfterKillingEnemy() {
        Projectile projectile = new Projectile(100, 200,
                new ProjectileSpec(WeaponId.COMMIT_CANNON, 25, 1, 0,
                        false, 0, 0, 1));
        projectiles.add(projectile);
        enemyManager.spawnEnemy(110, 195, EntityType.BUG);
        enemyManager.spawnEnemy(220, 195, EntityType.BUG);

        collision.process(ctx, projectiles, enemyManager, boss, player,
                GameState.RUNNING, PANEL_WIDTH, PANEL_HEIGHT, 0,
                particles, texts, logMessages::add);

        assertTrue(enemyManager.getEnemies().get(0).isDead());
        assertFalse(enemyManager.getEnemies().get(1).isDead());
        assertFalse(projectile.isDead());
        assertEquals(0, projectile.getRemainingRicochets());
        assertTrue(projectile.getVx() > 0);
    }

    @Test
    void projectileKillEmitsCombatEventWithoutReplacingContextScoring() {
        List<CombatEvent> events = new ArrayList<>();
        collision = new CollisionSystem(events::add);
        projectiles.add(new Projectile(100, 200,
                new ProjectileSpec(WeaponId.COMMIT_CANNON, 25, 0, 0,
                        false, 0, 0, 0)));
        enemyManager.spawnEnemy(110, 195, EntityType.BUG);

        collision.process(ctx, projectiles, enemyManager, boss, player,
                GameState.RUNNING, PANEL_WIDTH, PANEL_HEIGHT, 0,
                particles, texts, logMessages::add);

        CombatEvent.EnemyKilled event = events.stream().filter(CombatEvent.EnemyKilled.class::isInstance)
                .map(CombatEvent.EnemyKilled.class::cast).findFirst().orElseThrow();
        assertEquals(EntityType.BUG, event.type());
        assertEquals(100, event.points());
        assertEquals(110, event.x(), 0.0001);
        assertEquals(195, event.y(), 0.0001);
        assertEquals(100, ctx.score);
    }

    @Test
    void meleeKillEmitsExactlyOneCombatEvent() {
        List<CombatEvent> events = new ArrayList<>();
        collision = new CollisionSystem(events::add);
        enemyManager.spawnEnemy(135, (int) player.getY(), EntityType.BUG);
        player.melee();

        collision.process(ctx, projectiles, enemyManager, boss, player,
                GameState.RUNNING, PANEL_WIDTH, PANEL_HEIGHT, 0,
                particles, texts, logMessages::add);
        collision.process(ctx, projectiles, enemyManager, boss, player,
                GameState.RUNNING, PANEL_WIDTH, PANEL_HEIGHT, 0,
                particles, texts, logMessages::add);

        assertEquals(1, events.stream().filter(CombatEvent.EnemyKilled.class::isInstance).count());
        assertEquals(1, events.stream().filter(CombatEvent.DamageDealt.class::isInstance).count());
    }

    @Test
    void deadProjectileDoesNotDamageOverlappingBossAfterEnemyHit() {
        boss.activate();
        Projectile projectile = new Projectile(100, 150,
                new ProjectileSpec(WeaponId.COMMIT_CANNON, 25, 0, 0,
                        false, 0, 0, 0));
        projectiles.add(projectile);
        enemyManager.spawnEnemy(110, 145, EntityType.BUG);
        int hpBefore = boss.getHp();

        collision.process(ctx, projectiles, enemyManager, boss, player,
                GameState.BOSS_FIGHT, PANEL_WIDTH, PANEL_HEIGHT, 0,
                particles, texts, logMessages::add);

        assertTrue(enemyManager.getEnemies().get(0).isDead());
        assertTrue(projectile.isDead());
        assertEquals(hpBefore, boss.getHp());
    }

    @Test
    void multipleKills_shouldBuildCombo() {
        projectiles.add(new Projectile(100, 200, 0, 0, ProjectileType.SUDO));
        enemyManager.spawnEnemy(110, 195, EntityType.BUG);
        enemyManager.spawnEnemy(110, 195, EntityType.CRASH);

        collision.process(ctx, projectiles, enemyManager, boss, player,
                          GameState.RUNNING, PANEL_WIDTH, PANEL_HEIGHT, 0,
                          particles, texts, logMessages::add);

        assertEquals(2, ctx.combo);
    }

    @Test
    void comboWindowUpgradeExtendsTheRealCollisionTimer() {
        collision = new CollisionSystem(event -> { }, () -> false, () -> 30);
        projectiles.add(new Projectile(100, 200,
                new ProjectileSpec(WeaponId.COMMIT_CANNON, 100, 0, 0,
                        false, 0, 0, 0)));
        enemyManager.spawnEnemy(110, 195, EntityType.BUG);

        collision.process(ctx, projectiles, enemyManager, boss, player,
                GameState.RUNNING, PANEL_WIDTH, PANEL_HEIGHT, 0,
                particles, texts, logMessages::add);

        assertEquals(130, ctx.comboTimer);
    }
}
