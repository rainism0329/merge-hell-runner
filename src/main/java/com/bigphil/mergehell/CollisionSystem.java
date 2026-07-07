package com.bigphil.mergehell;

import com.bigphil.mergehell.model.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

public class CollisionSystem {

    public static class Context {
        public int score;
        public int combo;
        public int comboTimer;
        public int shakeTimer;
        public int flashTimer;
        public int newKills;
    }

    public void process(Context ctx,
                        List<Projectile> projectiles,
                        ObstacleManager enemyManager,
                        Boss boss,
                        Player player,
                        GameState state,
                        int panelWidth, int panelHeight,
                        double cameraX,
                        List<Particle> particles,
                        List<FloatingText> texts,
                        Consumer<String> logger) {

        processProjectileCollisions(ctx, projectiles, enemyManager, boss, state, panelWidth, panelHeight, cameraX, particles, texts);
        processEnemyBullets(ctx, enemyManager.getEnemyBullets(), player, projectiles, particles, texts, panelWidth, panelHeight, cameraX);
        processMeleeAttack(ctx, player, enemyManager, boss, particles, texts);
        processPlayerEnemyCollisions(ctx, player, enemyManager, particles, texts, logger);
        processPlayerBossCollision(ctx, player, boss, state, texts, logger);
    }

    private void processProjectileCollisions(Context ctx,
                                             List<Projectile> projectiles,
                                             ObstacleManager enemyManager,
                                             Boss boss,
                                             GameState state,
                                             int panelWidth, int panelHeight,
                                             double cameraX,
                                             List<Particle> particles,
                                             List<FloatingText> texts) {

        double leftBound = cameraX - 100;
        double rightBound = cameraX + panelWidth + 100;
        List<Runnable> deferredSpawns = new ArrayList<>();
        Iterator<Projectile> pIt = projectiles.iterator();
        while (pIt.hasNext()) {
            Projectile p = pIt.next();
            p.update();
            if (p.getX() < leftBound || p.getX() > rightBound
                    || p.getY() > panelHeight + 50 || p.getY() < -50 || p.isDead()) {
                pIt.remove();
                continue;
            }

            for (ObstacleManager.Enemy en : enemyManager.getEnemies()) {
                if (!en.isDead() && en.getType().isHostile() && !p.isDead() && p.getBounds().intersects(en.getBounds())) {
                    en.takeDamage(p.getDamage());
                    if (p.getType() != ProjectileType.SUDO) p.setDead(true);

                    if (en.isDead()) {
                        ctx.newKills++;
                        int particleCount = en.getType().maxHp > 2 ? 30 : 15;
                        spawnExplosion(particles, (int) en.getX(), (int) en.getY(), particleCount, en.getColor());
                        // Heavy enemies shake screen
                        if (en.getType().maxHp > 2) ctx.shakeTimer = Math.max(ctx.shakeTimer, 10);
                        ctx.combo++;
                        ctx.comboTimer = 100;
                        double mult = comboMultiplier(ctx.combo);
                        int points = (int) (en.getType().pointValue * mult);
                        ctx.score += points;
                        // Defer drop spawn to avoid ConcurrentModificationException
                        final double ex = en.getX(), ey = en.getY();
                        deferredSpawns.add(() -> maybeSpawnDrop(ex, ey, enemyManager));

                        Color popColor = mult >= 3 ? Color.ORANGE
                                : mult >= 2 ? Color.YELLOW : Color.WHITE;
                        String text = ctx.combo > 1
                                ? "Combo " + ctx.combo + "! +" + points
                                : "+" + points;
                        texts.add(new FloatingText(en.getX(), en.getY(), text, popColor));
                    } else {
                        spawnExplosion(particles, (int) en.getX(), (int) en.getY(), 5, Color.WHITE);
                    }
                }
            }

            if (state == GameState.BOSS_FIGHT && boss != null && boss.isActive()
                    && boss.getHp() > 0 && p.getBounds().intersects(boss.getBounds())) {
                boss.takeDamage(p.getDamage());
                p.setDead(true);
                ctx.shakeTimer = Math.max(ctx.shakeTimer, 4);
                spawnExplosion(particles, (int) p.getX(), (int) p.getY(), 5, Color.WHITE);
                texts.add(new FloatingText(p.getX(), p.getY() - 10, "-" + p.getDamage(), Color.YELLOW));
            }
        }
        // Execute deferred spawns after iteration
        for (Runnable r : deferredSpawns) r.run();
    }

    private void processEnemyBullets(Context ctx,
                                      List<Projectile> enemyBullets,
                                      Player player,
                                      List<Projectile> playerProjectiles,
                                      List<Particle> particles,
                                      List<FloatingText> texts,
                                      int panelWidth, int panelHeight,
                                      double cameraX) {

        double leftBound = cameraX - 100;
        double rightBound = cameraX + panelWidth + 100;
        Iterator<Projectile> it = enemyBullets.iterator();
        while (it.hasNext()) {
            Projectile b = it.next();
            b.update();
            if (b.getX() < leftBound || b.getX() > rightBound
                    || b.getY() < -50 || b.getY() > panelHeight + 50 || b.isDead()) {
                it.remove();
                continue;
            }

            // Player projectiles destroy normal enemy bullets (not critical)
            if (!b.getType().undestroyable) {
                for (Projectile pp : playerProjectiles) {
                    if (!pp.isDead() && b.getBounds().intersects(pp.getBounds())) {
                        b.setDead(true);
                        pp.setDead(true);
                        spawnExplosion(particles, (int) b.getX(), (int) b.getY(), 5, Color.ORANGE);
                        break;
                    }
                }
            }
            if (b.isDead()) { it.remove(); continue; }

            // Enemy bullet hits player
            if (player.getBounds().intersects(b.getBounds())) {
                b.setDead(true);
                if (player.getShieldTimer() > 0 || player.getInvincibleTimer() > 0) {
                    spawnExplosion(particles, (int) b.getX(), (int) b.getY(), 8, GameColors.SHIELD_CYAN);
                } else {
                    player.takeDamage(b.getDamage());
                    ctx.shakeTimer = 8;
                    ctx.flashTimer = 8;
                    spawnExplosion(particles, (int) b.getX(), (int) b.getY(), 10, GameColors.DANGER_RED);
                    texts.add(new FloatingText(b.getX(), b.getY(), "-" + b.getDamage(), Color.RED));
                }
                it.remove();
            }
        }
    }

    private void processMeleeAttack(Context ctx, Player player,
                                     ObstacleManager enemyManager, Boss boss,
                                     List<Particle> particles, List<FloatingText> texts) {
        Rectangle melee = player.getMeleeBounds();
        if (melee == null) return;

        for (ObstacleManager.Enemy en : enemyManager.getEnemies()) {
            if (!en.isDead() && en.getType().isHostile() && melee.intersects(en.getBounds())) {
                en.takeDamage(50);
                spawnExplosion(particles, (int) en.getX(), (int) en.getY(), 8, Color.WHITE);
                if (en.isDead()) {
                    ctx.newKills++;
                    ctx.combo++;
                    ctx.comboTimer = 100;
                    int points = (int) (en.getType().pointValue * comboMultiplier(ctx.combo));
                    ctx.score += points;
                    spawnExplosion(particles, (int) en.getX(), (int) en.getY(), 20, en.getColor());
                    texts.add(new FloatingText(en.getX(), en.getY(), "+" + points, Color.ORANGE));
                }
            }
        }
        if (boss != null && boss.isActive() && melee.intersects(boss.getBounds())) {
            boss.takeDamage(50);
            spawnExplosion(particles, (int) (melee.x + melee.width / 2), (int) (melee.y + melee.height / 2), 12, Color.WHITE);
            texts.add(new FloatingText(melee.x, melee.y, "-50", Color.ORANGE));
        }
    }

    private void processPlayerEnemyCollisions(Context ctx,
                                              Player player,
                                              ObstacleManager enemyManager,
                                              List<Particle> particles,
                                              List<FloatingText> texts,
                                              Consumer<String> logger) {

        for (ObstacleManager.Enemy en : enemyManager.getEnemies()) {
            if (!en.isDead() && player.getBounds().intersects(en.getBounds())) {
                if (en.getType().toWeapon() != null) {
                    en.setDead(true);
                    WeaponType w = en.getType().toWeapon();
                    int ammo = w == WeaponType.SPREAD ? 30 : w == WeaponType.RAPID ? 50 : 20;
                    player.giveWeapon(w, ammo);
                    spawnExplosion(particles, (int) player.getX(), (int) player.getY(), 20, en.getColor());
                    logger.accept("Picked up " + w.name() + " (" + ammo + " rounds)");
                    texts.add(new FloatingText(player.getX(), player.getY() - 30, w.name() + "!", Color.YELLOW));
                } else if (en.getType() == EntityType.POWERUP_SHIELD) {
                    en.setDead(true);
                    player.setShieldTimer(400);
                    spawnExplosion(particles, (int) player.getX(), (int) player.getY(), 20, GameColors.SHIELD_CYAN);
                    logger.accept("Firewall rules updated (Shield Up).");
                    texts.add(new FloatingText(player.getX(), player.getY() - 30, "SHIELD UP!", Color.CYAN));
                } else if (en.getType() == EntityType.HEALTH) {
                    en.setDead(true);
                    player.heal(25);
                    spawnExplosion(particles, (int) player.getX(), (int) player.getY(), 15, GameColors.HEALTH_GREEN);
                    logger.accept("Health pack collected. +25 HP");
                    texts.add(new FloatingText(player.getX(), player.getY() - 30, "+25 HP", Color.GREEN));
                } else {
                    en.setDead(true);
                    player.takeDamage(en.getDamage());
                    ctx.shakeTimer = 15;
                    ctx.flashTimer = 12;
                    ctx.combo = 0;
                    spawnExplosion(particles, (int) player.getX(), (int) player.getY(), 15, Color.RED);
                    texts.add(new FloatingText(player.getX(), player.getY(), "ERROR!", Color.RED));
                }
            }
        }
    }

    private void processPlayerBossCollision(Context ctx,
                                            Player player,
                                            Boss boss,
                                            GameState state,
                                            List<FloatingText> texts,
                                            Consumer<String> logger) {

        if (state != GameState.BOSS_FIGHT || boss == null || !boss.isActive() || boss.getHp() <= 0) return;
        if (!player.getBounds().intersects(boss.getBounds())) return;

        int damage = boss.isDashing() ? 35 : 5;
        if (player.getShieldTimer() > 0 || player.getInvincibleTimer() > 0) return;

        player.takeDamage(damage);

        if (boss.isDashing()) {
            ctx.shakeTimer = 30;
            ctx.flashTimer = 15;
            texts.add(new FloatingText(player.getX(), player.getY(), "CRITICAL ERROR!", Color.RED));
            logger.accept("CRITICAL: Hit by core dump!");
        } else {
            ctx.shakeTimer = 5;
            texts.add(new FloatingText(player.getX(), player.getY(), "CONTACT -" + damage, Color.ORANGE));
        }
    }

    private void maybeSpawnDrop(double x, double y, ObstacleManager om) {
        if (Math.random() > 0.10) return;
        EntityType drop = switch ((int) (Math.random() * 4)) {
            case 0 -> EntityType.HEALTH;
            case 1 -> EntityType.PICKUP_RAPID;
            case 2 -> EntityType.PICKUP_SPREAD;
            default -> EntityType.PICKUP_HEAVY;
        };
        om.spawnEnemy((int) x, (int) y - 20, drop);
    }

    private static double comboMultiplier(int combo) {
        if (combo >= 20) return 3.0;
        if (combo >= 10) return 2.0;
        if (combo >= 5) return 1.5;
        return 1.0;
    }

    private void spawnExplosion(List<Particle> particles, int x, int y, int count, Color c) {
        for (int i = 0; i < count; i++) {
            double angle = Math.random() * Math.PI * 2;
            double speed = 2 + Math.random() * 8;
            particles.add(new Particle(x, y, c,
                    Math.cos(angle) * speed,
                    Math.sin(angle) * speed - 3,
                    0.03f + (float) Math.random() * 0.03f));
        }
    }
}
