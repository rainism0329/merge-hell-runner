package com.bigphil.mergehell.model;

import com.bigphil.mergehell.i18n.GameText;

import com.bigphil.mergehell.engine.EntityLimits;
import com.bigphil.mergehell.engine.ProjectileBudget;
import com.bigphil.mergehell.engine.ProjectileBuffer;
import com.bigphil.mergehell.render.VectorEntityRenderer;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class ObstacleManager {

    public static class Enemy {
        private double x;
        private double y;
        private final double vx;
        private final int width, height;
        private final EntityType type;
        private final Color color;
        private final String symbol;
        private boolean dead = false;
        private final int damage;
        private int hp;
        private static final double STEP_SECONDS = 0.016;
        private final double motionPhase;
        private final Random random;
        private long ageTicks;
        private int shootTimer;
        private int moveDir = 1; // 1 = right-to-left, -1 = left-to-right
        private int chargeTimer = 0;
        private double chargeVx = 0;
        private int spawnProtectionTicks;
        private int telegraphTicks;
        public enum Mode { APPROACH, AIM, ATTACK, RECOVER, GUARD, BURROWED, EMERGE }
        public record AimLane(double x, double y, double velocityX, double velocityY,
                              double gravity, ProjectileType projectileType) { }
        /** Values only: the renderer shows the exact commitment made by the simulation. */
        public record Tactics(Mode mode, int ticks, int totalTicks, double targetX, double targetY,
                              double originX, double originY, double velocityX, double velocityY,
                              double gravity, int volleySize, boolean shielded, boolean burrowed,
                              boolean contactDangerous, int facing, List<AimLane> lanes) {
            public Tactics { lanes = List.copyOf(lanes); }
            public static final Tactics NONE = new Tactics(Mode.APPROACH, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, false, false, true, -1, List.of());
        }
        private List<AimLane> shotLanes = List.of();
        private Mode mode = Mode.APPROACH;
        private int modeTicks, modeTotal;
        private double targetX, targetY, originX, originY, attackVx, attackVy, gravity;
        private double playerPositionX, playerPositionY, homeY, floorY, hoverY;
        private boolean onScreen = true, explicitViewport;
        private int attackFacing = -1;
        private double leapStartX, leapStartY;
        private double movementLeft = Double.NEGATIVE_INFINITY, movementRight = Double.POSITIVE_INFINITY;

        public Enemy(double x, double y, EntityType type) {
            this(x, y, type, 1);
        }

        public Enemy(double x, double y, EntityType type, int moveDir) {
            this(x, y, type, moveDir, new Random().nextLong());
        }

        public Enemy(double x, double y, EntityType type, long seed) {
            this(x, y, type, 1, seed);
        }

        public Enemy(double x, double y, EntityType type, int moveDir, long seed) {
            this.x = x;
            this.y = y;
            this.type = type;
            this.width = type.width;
            this.height = type.height;
            this.vx = type.vx;
            this.color = type.color;
            this.symbol = type.symbol;
            this.damage = type.damage > 0 ? type.damage : 20;
            this.hp = type.maxHp;
            this.random = new Random(seed);
            this.motionPhase = new Random(seed ^ 0x4D4F54494F4E5F31L).nextDouble() * Math.PI * 2;
            this.shootTimer = 40 + random.nextInt(80);
            this.moveDir = moveDir;
            this.chargeTimer = 120 + random.nextInt(180);
            homeY = y; hoverY = y; floorY = y + height;
            playerPositionX = x - 300 * moveDir;
            playerPositionY = y;
            attackFacing = -moveDir;
            if (type == EntityType.WARDEN) enter(Mode.GUARD, 80);
            if (type == EntityType.DRILLER) enter(Mode.BURROWED, 65);
            if (type.isChapterSpecialist()) shootTimer = 35 + random.nextInt(30);
        }

        public double getX() { return x; }
        public double getY() { return y; }
        public double getWidth() { return width; }
        public EntityType getType() { return type; }
        public Color getColor() { return color; }
        public int getDamage() { return damage; }
        public int getHp() { return hp; }
        public int getMaxHp() { return type.maxHp; }
        /** Land edges are world coordinates. Ground bodies and their committed paths stay inside them. */
        public void setMovementBounds(double left, double right) {
            if (!Double.isFinite(left) || !Double.isFinite(right) || right - left < width)
                throw new IllegalArgumentException("Movement segment must contain the whole enemy");
            movementLeft = left; movementRight = right - width;
            if (type.isChapterSpecialist() && groundedSpecialist()) x = clampMovement(x);
        }
        public boolean isDead() { return dead; }
        public int getTelegraphTicks() { return telegraphTicks; }
        public boolean isCollisionProtected() { return type.isHostile() && !isContactDangerous(); }
        public boolean isContactDangerous() {
            return !dead && spawnProtectionTicks == 0
                    && !(type == EntityType.DRILLER && (mode == Mode.BURROWED || mode == Mode.EMERGE));
        }
        public Tactics getTactics() {
            if (!type.isChapterSpecialist()) return Tactics.NONE;
            return new Tactics(mode, telegraphTicks > 0 ? telegraphTicks : modeTicks, modeTotal,
                    targetX, targetY, originX, originY, attackVx, attackVy, gravity, volleySize(),
                    isShielded(), type == EntityType.DRILLER && (mode == Mode.BURROWED || mode == Mode.EMERGE),
                    isContactDangerous(), attackFacing, shotLanes);
        }
        private boolean isShielded() {
            return type == EntityType.WARDEN && (mode == Mode.GUARD || mode == Mode.APPROACH);
        }
        Enemy protectOnSpawn() { spawnProtectionTicks = 30; return this; }
        public void setDead(boolean dead) { this.dead = dead; }

        public void takeDamage(int dmg) {
            hp -= dmg;
            if (hp <= 0) dead = true;
        }

        public void takeHit(int damage, double knockback, int hitDirection) {
            if (damage <= 0) return;
            boolean armor = isShielded() && Integer.signum(hitDirection) != attackFacing;
            takeDamage(armor ? Math.max(1, (int) Math.ceil(damage * 0.35)) : damage);
            if (!dead && knockback > 0) {
                // Committed aim and leaps do not move their already published danger path.
                if (!type.isChapterSpecialist() || mode == Mode.APPROACH || mode == Mode.GUARD || mode == Mode.RECOVER)
                    x += Math.copySign(Math.min(knockback, armor ? 4 : 24), hitDirection);
                if (type.isChapterSpecialist() && groundedSpecialist()) x = clampMovement(x);
            }
        }

        public Projectile maybeShoot(double playerY) {
            playerPositionY = playerY;
            if (!readyToShoot()) return null;
            return type.isChapterSpecialist() ? createVolley().get(0) : createShot(playerY);
        }

        /** A saturated field holds the final warning frame and retries without creating a shot. */
        public boolean maybeShoot(double playerY, List<Projectile> destination) {
            playerPositionY = playerY;
            if (!readyToShoot()) return false;
            if (type.isChapterSpecialist()) {
                if (ProjectileBudget.emit(destination, volleySize(), this::createVolley)) return true;
                telegraphTicks = 1;
                return false;
            }
            if (ProjectileBudget.emitOne(destination, () -> createShot(playerY))) return true;
            telegraphTicks = 1;
            return false;
        }

        private boolean readyToShoot() {
            if (!canShoot()) return false;
            if (type.isChapterSpecialist()) {
                if (!onScreen || spawnProtectionTicks > 0 || dead) return false;
                if (mode == Mode.RECOVER || mode == Mode.ATTACK) return false;
                if (telegraphTicks > 0) return --telegraphTicks == 0;
                if (--shootTimer > 0) return false;
                lockShot();
                return false;
            }
            if (telegraphTicks > 0) {
                return --telegraphTicks == 0;
            }
            if (--shootTimer > 0) return false;
            telegraphTicks = 30;
            return false;
        }

        private Projectile createShot(double playerY) {
            shootTimer = 60 + random.nextInt(100);
            double bulletX = x;
            double bulletY = y + height / 2.0;
            double tracking = type == EntityType.SENTINEL ? 0.045
                    : type == EntityType.MIRROR ? 0.035 : 0.03;
            double dy = (playerY - bulletY) * tracking;
            double speed = type == EntityType.SENTINEL ? 8
                    : type == EntityType.MIRROR ? 5.5 : 6;
            double bulletVx = -speed * moveDir;
            int criticalChance = type == EntityType.SENTINEL ? 3
                    : type == EntityType.MIRROR ? 4 : 5;
            ProjectileType bulletType = random.nextInt(criticalChance) == 0
                    ? ProjectileType.CRITICAL : ProjectileType.ENEMY;
            return new Projectile(bulletX, bulletY, bulletVx, dy, bulletType);
        }

        private boolean canShoot() {
            return type == EntityType.CONFLICT || type == EntityType.LOCK
                    || type == EntityType.TECHDEBT || type == EntityType.SENTINEL
                    || type == EntityType.MIRROR || type == EntityType.RIGGER
                    || type == EntityType.SLAG_SPITTER || type == EntityType.SPORE_POD;
        }

        private int volleySize() {
            return switch (type) {
                case RIGGER, MIRROR -> 2;
                case SPORE_POD -> 3;
                case SENTINEL, SLAG_SPITTER -> 1;
                default -> 0;
            };
        }

        private void lockShot() {
            // Projectiles store their top-left corner. Aim their 14/16px body at the 30px hero's center.
            targetX = playerPositionX + 8;
            targetY = playerPositionY + 8;
            attackFacing = targetX < x + width / 2.0 ? -1 : 1;
            originX = x + width / 2.0 + attackFacing * width * 0.38;
            originY = y + height * 0.43;
            int warning = switch (type) {
                case SENTINEL, SPORE_POD -> 75;
                case SLAG_SPITTER -> 64;
                case MIRROR -> 54;
                default -> 48;
            };
            enter(Mode.AIM, warning);
            telegraphTicks = warning;
            gravity = type == EntityType.SPORE_POD ? 0.115 : type == EntityType.SLAG_SPITTER ? 0.16 : 0;
            if (gravity > 0) {
                double duration = type == EntityType.SPORE_POD ? 68 : 55;
                attackVx = (targetX - originX) / duration;
                attackVy = (targetY - originY - gravity * duration * (duration - 1) / 2) / duration;
            } else {
                double distance = Math.max(1, Math.hypot(targetX - originX, targetY - originY));
                double speed = type == EntityType.SENTINEL ? 12 : type == EntityType.MIRROR ? 7 : 5.3;
                attackVx = (targetX - originX) / distance * speed;
                attackVy = (targetY - originY) / distance * speed;
            }
            shotLanes = lockedLanes();
        }

        /** All lanes are admitted together, and remain aimed at the visible warning's locked point. */
        private List<Projectile> createVolley() {
            List<Projectile> result = new ArrayList<>(shotLanes.size());
            for (AimLane lane : shotLanes) {
                result.add(lane.gravity() > 0 ? Projectile.ballistic(lane.x(), lane.y(), lane.velocityX(),
                        lane.velocityY(), lane.projectileType(), lane.gravity())
                        : new Projectile(lane.x(), lane.y(), lane.velocityX(), lane.velocityY(), lane.projectileType()));
            }
            telegraphTicks = 0;
            shootTimer = switch (type) {
                case SENTINEL -> 54;
                case SPORE_POD -> 75;
                case SLAG_SPITTER -> 60;
                default -> 42;
            };
            enter(Mode.RECOVER, type == EntityType.SENTINEL ? 80 : 60);
            return result;
        }

        private List<AimLane> lockedLanes() {
            List<AimLane> result = new ArrayList<>(volleySize());
            for (int i = 0; i < volleySize(); i++) {
                double dx = attackVx, dy = attackVy, py = originY;
                if (type == EntityType.RIGGER) py += i == 0 ? -33 : 33;
                if (type == EntityType.MIRROR) {
                    double angle = i == 0 ? -0.15 : 0.15;
                    dx = attackVx * Math.cos(angle) - attackVy * Math.sin(angle);
                    dy = attackVx * Math.sin(angle) + attackVy * Math.cos(angle);
                }
                if (type == EntityType.SPORE_POD) dx += (i - 1) * 0.95;
                ProjectileType kind = type == EntityType.SENTINEL ? ProjectileType.CRITICAL : ProjectileType.ENEMY;
                result.add(new AimLane(originX, py, dx, dy, gravity, kind));
            }
            return List.copyOf(result);
        }

        public void update(double difficultySpeed, double playerX) {
            if (type.isChapterSpecialist()) {
                updateSpecialist(difficultySpeed, playerX, playerPositionY, false, 0, 0, (int) floorY);
                return;
            }
            if (spawnProtectionTicks > 0) spawnProtectionTicks--;
            double t = ++ageTicks * STEP_SECONDS;

            // Charge attack: telegraph while advancing normally, then commit to a short dash.
            // The previous branching never entered the actual dash path once chargeVx was set.
            if (chargeVx != 0) {
                x += chargeVx;
                if (--chargeTimer <= 0) {
                    chargeVx = 0;
                    chargeTimer = 150 + random.nextInt(200);
                }
            } else {
                double speed = vx * difficultySpeed;
                x -= speed * moveDir;
                boolean canCharge = type == EntityType.BUG || type == EntityType.CRASH
                        || type == EntityType.INTERRUPT;
                if (canCharge && --chargeTimer <= 0) {
                    if (Math.abs(x - playerX) < 350) {
                        chargeVx = (playerX > x ? 1 : -1) * vx * difficultySpeed * 3.5;
                        chargeTimer = 15;
                    } else {
                        chargeTimer = 150 + random.nextInt(200);
                    }
                }
            }

            switch (type) {
                case LOCK -> y += Math.sin(t * 2.5 + motionPhase) * 2.5;
                case CRASH -> y += Math.sin(t * 8.0 + motionPhase) * 6;
                case BUG -> y += Math.cos(t * 4.0 + motionPhase) * 1.5;
                case CONFLICT -> { /* steady advance */ }
                case TECHDEBT -> { /* slow and heavy, no wobble */ }
                case FIREWALL -> { /* straight line, blocks path */ }
                case LEAK -> y += Math.sin(t * 3.2 + motionPhase) * 3.2;
                case SENTINEL -> y += Math.sin(t * 1.6 + motionPhase) * 1.1;
                case INTERRUPT -> y += Math.signum(Math.sin(t * 9.5 + motionPhase)) * 3.8;
                case MIRROR -> y += Math.cos(t * 2.1 + motionPhase) * 2.4;
                case WARDEN, RIGGER, DRILLER, SLAG_SPITTER, SPORE_POD, LURKER -> { }
                case PICKUP_SPREAD, PICKUP_RAPID, PICKUP_HEAVY, PICKUP_FLAME, PICKUP_LASER, POWERUP_SHIELD, HEALTH ->
                        y += Math.sin(t * 1.5 + motionPhase) * 1.5;
            }
        }

        /** Explicit viewport makes every specialist finish its warning on screen before attacking. */
        public void update(double difficultySpeed, double playerX, double playerY,
                           int cameraLeft, int cameraRight, int groundY) {
            if (!type.isChapterSpecialist()) { update(difficultySpeed, playerX); return; }
            updateSpecialist(difficultySpeed, playerX, playerY, true, cameraLeft, cameraRight, groundY);
        }

        private void updateSpecialist(double difficultySpeed, double playerX, double playerY,
                                      boolean viewport, int cameraLeft, int cameraRight, int groundY) {
            if (dead) return;
            ageTicks++;
            if (spawnProtectionTicks > 0) spawnProtectionTicks--;
            playerPositionX = playerX; playerPositionY = playerY;
            floorY = groundY;
            if (viewport && !explicitViewport) {
                explicitViewport = true;
                hoverY = Math.max(100, Math.min(homeY, groundY - height - (type == EntityType.SPORE_POD ? 105 : 80)));
            }
            boolean visibleNow = !viewport || (x >= cameraLeft + 8 && x + width <= cameraRight - 8);
            if (!visibleNow) {
                // Cancel an incomplete warning when the camera loses it. It must be shown in full again.
                if (onScreen || telegraphTicks > 0) resetCommitment();
                onScreen = false;
                if (viewport) moveX(x < cameraLeft + 8 ? vx : -vx);
                if (groundedSpecialist()) y = groundY - height;
                return;
            }
            onScreen = true;
            if (spawnProtectionTicks > 0) return;
            double speed = Math.min(1.45, Math.max(0.6, difficultySpeed));
            double center = x + width / 2.0;
            double distance = playerX + 15 - center;
            if (mode != Mode.AIM && mode != Mode.ATTACK && mode != Mode.EMERGE)
                attackFacing = distance < 0 ? -1 : 1;
            if (groundedSpecialist() && mode != Mode.ATTACK) y = groundY - height;

            if (type == EntityType.WARDEN) { updateWarden(distance, speed); return; }
            if (type == EntityType.DRILLER) { updateDriller(distance, speed); return; }
            if (type == EntityType.LURKER || type == EntityType.INTERRUPT) {
                updateLeaper(distance, speed); return;
            }
            if (mode == Mode.RECOVER) {
                if (type == EntityType.MIRROR) moveX(-attackFacing * 0.7);
                if (type == EntityType.RIGGER) y = hoverY + Math.sin(ageTicks * .045 + motionPhase) * 14;
                if (--modeTicks <= 0) enter(Mode.APPROACH, 0);
                return;
            }
            if (mode == Mode.AIM) return;
            if (type == EntityType.RIGGER || type == EntityType.SPORE_POD)
                y = hoverY + Math.sin(ageTicks * .035 + motionPhase) * (type == EntityType.RIGGER ? 17 : 8);
            if (type == EntityType.RIGGER && Math.abs(distance) > 290) moveX(attackFacing * 1.4 * speed);
            if (type == EntityType.SLAG_SPITTER && Math.abs(distance) > 360) moveX(attackFacing * .8 * speed);
            if (type == EntityType.MIRROR) {
                if (Math.abs(distance) > 350) moveX(attackFacing * 1.3 * speed);
                else if (Math.abs(distance) < 180) moveX(-attackFacing * 1.2 * speed);
            }
        }

        private boolean groundedSpecialist() {
            return switch (type) {
                case SENTINEL, WARDEN, DRILLER, SLAG_SPITTER, MIRROR, LURKER -> true;
                default -> false;
            };
        }

        private void updateWarden(double distance, double speed) {
            if (mode == Mode.AIM) {
                telegraphTicks = Math.max(0, telegraphTicks - 1);
                if (--modeTicks <= 0) enter(Mode.ATTACK, 16);
            } else if (mode == Mode.ATTACK) {
                moveX(attackVx);
                if (--modeTicks <= 0) enter(Mode.RECOVER, 76);
            } else if (mode == Mode.RECOVER) {
                if (--modeTicks <= 0) enter(Mode.GUARD, 65);
            } else {
                if (Math.abs(distance) > 100) moveX(attackFacing * .85 * speed);
                if (modeTicks > 0) modeTicks--;
                if (modeTicks == 0 && Math.abs(distance) < 245) {
                    lockLeap(48, Math.max(-160, Math.min(160, distance)), 0);
                    attackVx = attackFacing * 8.5;
                    targetX = clampMovement(originX + attackVx * 16);
                    attackVx = (targetX - originX) / 16;
                    targetY = y;
                }
            }
        }

        private void updateDriller(double distance, double speed) {
            if (mode == Mode.BURROWED) {
                if (Math.abs(distance) > 135) moveX(attackFacing * 2.0 * speed);
                if (--modeTicks <= 0) {
                    originX = x; originY = y; targetX = x; targetY = y;
                    enter(Mode.EMERGE, 58); telegraphTicks = 58;
                }
            } else if (mode == Mode.EMERGE) {
                telegraphTicks = Math.max(0, telegraphTicks - 1);
                if (--modeTicks <= 0) {
                    leapStartX = x; leapStartY = y;
                    enter(Mode.ATTACK, 30);
                }
            } else if (mode == Mode.ATTACK) {
                double progress = (modeTotal - modeTicks + 1.0) / modeTotal;
                y = leapStartY - Math.sin(progress * Math.PI) * 56;
                if (--modeTicks <= 0) { y = floorY - height; enter(Mode.RECOVER, 94); }
            } else if (--modeTicks <= 0) enter(Mode.BURROWED, 70);
        }

        private void updateLeaper(double distance, double speed) {
            boolean flying = type == EntityType.INTERRUPT;
            if (mode == Mode.AIM) {
                telegraphTicks = Math.max(0, telegraphTicks - 1);
                if (--modeTicks <= 0) {
                    leapStartX = x; leapStartY = y;
                    enter(Mode.ATTACK, flying ? 28 : 36);
                }
            } else if (mode == Mode.ATTACK) {
                double progress = (modeTotal - modeTicks + 1.0) / modeTotal;
                x = clampMovement(leapStartX + (targetX - leapStartX) * progress);
                y = leapStartY + (targetY - leapStartY) * progress
                        - Math.sin(progress * Math.PI) * (flying ? 28 : 108);
                if (--modeTicks <= 0) enter(Mode.RECOVER, flying ? 72 : 68);
            } else if (mode == Mode.RECOVER) {
                if (flying) y += (hoverY - y) * .045;
                if (--modeTicks <= 0) enter(Mode.APPROACH, 45);
            } else {
                if (flying) y = hoverY + Math.sin(ageTicks * .07 + motionPhase) * 10;
                if (Math.abs(distance) > (flying ? 210 : 120)) moveX(attackFacing * (flying ? 1.8 : 1.7) * speed);
                if (modeTicks > 0) modeTicks--;
                if (modeTicks <= 0 && Math.abs(distance) < (flying ? 340 : 300)) {
                    lockLeap(flying ? 50 : 48, Math.max(-300, Math.min(300, distance)),
                            flying ? floorY - height - 5 : floorY - height);
                }
            }
        }

        private void lockLeap(int ticks, double travel, double destinationY) {
            originX = x; originY = y;
            targetX = clampMovement(x + travel);
            targetY = destinationY;
            attackVx = travel / (type == EntityType.INTERRUPT ? 28 : 36);
            attackVy = 0; gravity = 0;
            enter(Mode.AIM, ticks); telegraphTicks = ticks;
        }

        private void enter(Mode next, int ticks) { mode = next; modeTicks = ticks; modeTotal = ticks; }

        private double clampMovement(double value) {
            return groundedSpecialist() ? Math.max(movementLeft, Math.min(movementRight, value)) : value;
        }

        private void moveX(double delta) { x = clampMovement(x + delta); }

        private void resetCommitment() {
            telegraphTicks = 0; shootTimer = 36; shotLanes = List.of();
            if (type == EntityType.DRILLER) enter(Mode.BURROWED, 65);
            else if (type == EntityType.WARDEN) enter(Mode.GUARD, 65);
            else enter(Mode.APPROACH, 30);
        }

        public void draw(Graphics2D g) {
            if (type.isHostile()) {
                VectorEntityRenderer.render(g, type, (int) x, (int) y, width, height,
                        hp, type.maxHp, telegraphTicks);
                return;
            }
            if (type == EntityType.FIREWALL || type == EntityType.TECHDEBT) {
                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 200));
                g.fillRect((int) x, (int) y, width, height);
            }

            // HP bar for multi-hit enemies
            if (type.maxHp > 1 && !dead) {
                g.setColor(Color.DARK_GRAY);
                g.fillRect((int) x, (int) y - 10, width, 5);
                g.setColor(color);
                double hpRatio = (double) hp / type.maxHp;
                g.fillRect((int) x, (int) y - 10, (int) (width * hpRatio), 5);
            }

            g.setColor(Color.WHITE);

            if (type == EntityType.CONFLICT || type == EntityType.TECHDEBT) {
                g.setFont(GameText.font(new Font("JetBrains Mono", Font.BOLD, 14)));
                GameText.draw(g, symbol, (int) x + 2, (int) y + 25);
            } else if (type == EntityType.FIREWALL) {
                g.setFont(GameText.font(new Font("Segoe UI Emoji", Font.PLAIN, 40)));
                GameText.draw(g, symbol, (int) x + 10, (int) y + 70);
            } else if (type == EntityType.HEALTH) {
                g.setFont(GameText.font(new Font("Segoe UI Emoji", Font.PLAIN, 28)));
                GameText.draw(g, symbol, (int) x + 2, (int) y + 28);
            } else {
                g.setFont(GameText.font(new Font("Segoe UI Emoji", Font.PLAIN, 32)));
                GameText.draw(g, symbol, (int) x, (int) y + 35);
            }

            // Enemy personality effects
            if (!dead) {
                double t = ageTicks * STEP_SECONDS;
                if (type == EntityType.BUG) {
                    g.setColor(new Color(255, 100, 100, 100));
                    g.setFont(GameText.font(new Font("JetBrains Mono", Font.PLAIN, 9)));
                    String[] bugs = {"NullPtr", "undef", "NaN", "404"};
                    int idx = (int) ((t * 3 + motionPhase) % bugs.length);
                    GameText.draw(g, bugs[idx], (int) x + 5, (int) y - 5);
                } else if (type == EntityType.CONFLICT) {
                    g.setColor(new Color(255, 200, 120, 150));
                    g.setFont(GameText.font(new Font("JetBrains Mono", Font.BOLD, 10)));
                    GameText.draw(g, "<<< HEAD", (int) x - 5, (int) y - 5);
                } else if (type == EntityType.TECHDEBT) {
                    int pulse = (int) (Math.sin(t * 3 + motionPhase) * 40 + 80);
                    g.setColor(new Color(180, 180, 180, pulse));
                    g.setFont(GameText.font(new Font("JetBrains Mono", Font.BOLD, 12)));
                    GameText.draw(g, "// TODO: FIX ME", (int) x - 15, (int) y - 5);
                } else if (type == EntityType.CRASH) {
                    g.setColor(new Color(255, 150, 50, 150));
                    g.setFont(GameText.font(new Font("JetBrains Mono", Font.BOLD, 9)));
                    GameText.draw(g, "SIGSEGV", (int) x + 5, (int) y - 5);
                }
            }
        }

        public Rectangle getBounds() {
            if (type == EntityType.DRILLER && (mode == Mode.BURROWED || mode == Mode.EMERGE))
                return new Rectangle((int) x, (int) (y + height - 12), width, 12);
            return new Rectangle((int) x, (int) y, width, height);
        }
    }

    private final List<Enemy> enemies = new ArrayList<>();
    private final ProjectileBuffer enemyBullets = new ProjectileBuffer(EntityLimits.MAX_ENEMY_PROJECTILES);
    private static final long ENEMY_SEED_DOMAIN = 0x454E454D595F524EL;
    private final Random random;
    private final Random enemySeeds;
    private int rejectedHostiles;

    public ObstacleManager() { this(new Random().nextLong()); }

    public ObstacleManager(long seed) {
        random = new Random(seed);
        enemySeeds = new Random(seed ^ ENEMY_SEED_DOMAIN);
    }

    public void reset() {
        enemies.clear();
        enemyBullets.reset();
        rejectedHostiles = 0;
    }

    /** Rebuilds a level entrance without inheriting random draws from the previous level. */
    public void reset(long seed) {
        reset();
        random.setSeed(seed);
        enemySeeds.setSeed(seed ^ ENEMY_SEED_DOMAIN);
        rejectedHostiles = 0;
    }

    private Enemy newEnemy(double x, double y, EntityType type, int moveDir) {
        return new Enemy(x, y, type, moveDir, enemySeeds.nextLong()).protectOnSpawn();
    }

    public void clearHostiles() {
        enemies.removeIf(enemy -> enemy.getType().isHostile());
        enemyBullets.clear();
    }

    public List<Projectile> getEnemyBullets() {
        return enemyBullets;
    }

    public long getRejectedProjectiles() { return enemyBullets.rejectedProjectiles(); }
    public long getRejectedProjectileVolleys() { return enemyBullets.rejectedVolleys(); }

    public void spawnEnemy(int x, int y, EntityType type) {
        if (!canSpawn(type)) return;
        enemies.add(newEnemy(x, y, type, 1));
    }

    public void spawnEnemy(int x, int y, EntityType type, int moveDir) {
        if (!canSpawn(type)) return;
        enemies.add(newEnemy(x, y, type, moveDir));
    }

    public void spawnFromLeft(int groundY, int cameraX) {
        EntityType[] types = { EntityType.BUG, EntityType.CRASH, EntityType.LOCK };
        spawnFromLeft(groundY, cameraX, types[random.nextInt(types.length)]);
    }

    /** Spawns the encounter's requested enemy type from the left side of the viewport. */
    public void spawnFromLeft(int groundY, int cameraX, EntityType type) {
        if (!canSpawn(type)) return;
        int y = groundY - 40 - random.nextInt(120);
        if (type == EntityType.TECHDEBT) y = groundY - 80;
        else if (type == EntityType.FIREWALL) y = 0;
        else if (type.isChapterSpecialist()) y = specialistSpawnY(type, groundY, random.nextInt(70));
        enemies.add(newEnemy(cameraX - 40 - random.nextInt(100), y, type, -1));
    }

    public void spawnFormation(int startX, int groundY) {
        int count = 3 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            int y = groundY - 30 - random.nextInt(100);
            if (canSpawn(EntityType.BUG))
                enemies.add(newEnemy(startX + i * 50, y, EntityType.BUG, 1));
        }
    }

    public void spawnRandom(int panelWidth, int groundY, double difficulty, int cameraX) {
        spawnRandom(panelWidth, groundY, difficulty, cameraX, 1);
    }

    public void spawnRandom(int panelWidth, int groundY, double difficulty, int cameraX, int level) {
        double spawnChance = 2.0 + difficulty * 1.5;
        if (random.nextInt(100) < spawnChance) {
            int r = random.nextInt(100);
            EntityType type;
            int y = groundY - 40;

            // Powerups & health (fixed chance, always available)
            if (r < 2) {
                type = EntityType.PICKUP_SPREAD; y = groundY - 150 - random.nextInt(80);
            } else if (r < 4) {
                type = EntityType.PICKUP_RAPID; y = groundY - 150 - random.nextInt(80);
            } else if (r < 6) {
                type = EntityType.PICKUP_HEAVY; y = groundY - 150 - random.nextInt(80);
            } else if (r < 8) {
                type = EntityType.PICKUP_FLAME; y = groundY - 150 - random.nextInt(80);
            } else if (r < 10) {
                type = EntityType.PICKUP_LASER; y = groundY - 150 - random.nextInt(80);
            } else if (r < 14) {
                type = EntityType.POWERUP_SHIELD;
                y = groundY - 150 - random.nextInt(80);
            } else if (r < 17) {
                type = EntityType.HEALTH;
                y = groundY - 150 - random.nextInt(80);
            } else {
                EntityType[] roster = ambientRoster(level);
                type = roster[random.nextInt(roster.length)];
            }

            // Set y position based on type
            if (type == EntityType.TECHDEBT) y = groundY - 80;
            else if (type == EntityType.LOCK) y = groundY - 60 - random.nextInt(80);
            else if (type == EntityType.FIREWALL) y = 0;
            else if (type.isChapterSpecialist()) y = specialistSpawnY(type, groundY, random.nextInt(70));

            // 30% chance to spawn from left during boss fights
            boolean fromLeft = random.nextInt(100) < 30;
            int spawnX = fromLeft
                    ? cameraX - 50 - random.nextInt(100)
                    : cameraX + panelWidth + random.nextInt(200);
            int spawnDir = fromLeft ? -1 : 1;
            if (canSpawn(type)) enemies.add(newEnemy(spawnX, y, type, spawnDir));
        }
    }

    static EntityType[] ambientRoster(int level) {
        return switch (level) {
            case 1 -> new EntityType[]{EntityType.LEAK, EntityType.LEAK, EntityType.BUG,
                    EntityType.TECHDEBT, EntityType.CRASH};
            case 2 -> new EntityType[]{EntityType.SENTINEL, EntityType.WARDEN, EntityType.RIGGER};
            case 3 -> new EntityType[]{EntityType.INTERRUPT, EntityType.DRILLER, EntityType.SLAG_SPITTER};
            case 4 -> new EntityType[]{EntityType.MIRROR, EntityType.SPORE_POD, EntityType.LURKER};
            default -> new EntityType[]{EntityType.BUG, EntityType.CONFLICT, EntityType.LOCK,
                    EntityType.CRASH, EntityType.TECHDEBT};
        };
    }

    /** Every grounded actor has a real foot plane; only the three winged species spawn airborne. */
    public static int specialistSpawnY(EntityType type, int groundY, int variation) {
        return switch (type) {
            case RIGGER, INTERRUPT -> groundY - type.height - 95 - Math.max(0, Math.min(70, variation));
            case SPORE_POD -> groundY - type.height - 115 - Math.max(0, Math.min(45, variation));
            default -> groundY - type.height;
        };
    }

    /** Removes entities once they have genuinely left the current camera viewport. */
    public void update(int cameraLeft, int cameraRight) {
        Iterator<Enemy> it = enemies.iterator();
        while (it.hasNext()) {
            Enemy e = it.next();
            if (isOffScreen(e, cameraLeft, cameraRight) || e.isDead()) {
                it.remove();
            }
        }
    }

    private boolean isOffScreen(Enemy e, int cameraLeft, int cameraRight) {
        return e.getX() + e.getWidth() < cameraLeft - 100 || e.getX() > cameraRight + 100;
    }

    public void draw(Graphics2D g) {
        for (Enemy e : enemies) e.draw(g);
    }

    public List<Enemy> getEnemies() {
        return enemies;
    }

    private boolean canSpawn(EntityType type) {
        if (!type.isHostile()) return true;
        long hostiles = enemies.stream().filter(e -> !e.isDead() && e.getType().isHostile()).count();
        if (hostiles < EntityLimits.MAX_HOSTILES) return true;
        rejectedHostiles++;
        return false;
    }

    public int getRejectedHostiles() { return rejectedHostiles; }
}
