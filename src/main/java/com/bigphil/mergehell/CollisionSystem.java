package com.bigphil.mergehell;

import com.bigphil.mergehell.model.*;

import java.awt.*;
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
    }

    public void process(Context ctx,
                        List<Projectile> projectiles,
                        ObstacleManager enemyManager,
                        Boss boss,
                        Player player,
                        GameState state,
                        int panelWidth,
                        int panelHeight,
                        List<Particle> particles,
                        List<FloatingText> texts,
                        Consumer<String> logger) {

        processProjectileCollisions(ctx, projectiles, enemyManager, boss, state, panelWidth, panelHeight, particles, texts);
        processPlayerEnemyCollisions(ctx, player, enemyManager, particles, texts, logger);
        processPlayerBossCollision(ctx, player, boss, state, texts, logger);
    }

    private void processProjectileCollisions(Context ctx,
                                             List<Projectile> projectiles,
                                             ObstacleManager enemyManager,
                                             Boss boss,
                                             GameState state,
                                             int panelWidth,
                                             int panelHeight,
                                             List<Particle> particles,
                                             List<FloatingText> texts) {

        Iterator<Projectile> pIt = projectiles.iterator();
        while (pIt.hasNext()) {
            Projectile p = pIt.next();
            p.update();
            if (p.getX() > panelWidth || p.getX() < 0 || p.getY() > panelHeight || p.getY() < 0 || p.isDead()) {
                pIt.remove();
                continue;
            }

            for (ObstacleManager.Enemy en : enemyManager.getEnemies()) {
                if (!en.isDead() && en.getType().isHostile() && !p.isDead() && p.getBounds().intersects(en.getBounds())) {
                    en.takeDamage(p.getDamage());
                    if (p.getType() != ProjectileType.SUDO) p.setDead(true);

                    if (en.isDead()) {
                        spawnExplosion(particles, (int) en.getX(), (int) en.getY(), 15, en.getColor());
                        ctx.combo++;
                        ctx.comboTimer = 100;
                        int bonus = 50 + (ctx.combo * 10);
                        ctx.score += bonus;
                        String text = ctx.combo > 1 ? "Combo " + ctx.combo + "!" : "+" + bonus;
                        texts.add(new FloatingText(en.getX(), en.getY(), text, Color.WHITE));
                    } else {
                        spawnExplosion(particles, (int) en.getX(), (int) en.getY(), 5, Color.WHITE);
                    }
                }
            }

            if (state == GameState.BOSS_FIGHT && boss.isActive() && p.getBounds().intersects(boss.getBounds())) {
                boss.takeDamage(p.getDamage());
                p.setDead(true);
                spawnExplosion(particles, (int) p.getX(), (int) p.getY(), 3, Color.WHITE);
                texts.add(new FloatingText(p.getX(), p.getY(), "-" + p.getDamage(), Color.LIGHT_GRAY));
            }
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
                if (en.getType() == EntityType.POWERUP_SUDO) {
                    en.setDead(true);
                    player.setSudoTimer(600);
                    spawnExplosion(particles, (int) player.getX(), (int) player.getY(), 20, GameColors.SUDO_YELLOW);
                    logger.accept("ROOT ACCESS GRANTED: Spread shot enabled!");
                    texts.add(new FloatingText(player.getX(), player.getY() - 30, "SUDO MODE!", Color.YELLOW));
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

        if (state != GameState.BOSS_FIGHT || !boss.isActive()) return;
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
