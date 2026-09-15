package com.bigphil.mergehell;
import com.bigphil.mergehell.engine.ProjectileBudget;

import com.bigphil.mergehell.combat.CombatEvent;
import com.bigphil.mergehell.combat.CombatEventSink;
import com.bigphil.mergehell.combat.ProjectileEffects;
import com.bigphil.mergehell.combat.ProjectileSpec;
import com.bigphil.mergehell.combat.WeaponId;
import com.bigphil.mergehell.model.*;
import com.bigphil.mergehell.engine.EntityLimits;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

public class CollisionSystem {

    private final CombatEventSink eventSink;
    private final BooleanSupplier phaseOneDrops;
    private final IntSupplier comboGraceTicks;
    private java.util.function.BiPredicate<Projectile, Double> worldImpact = (projectile, before) -> false;
    private java.util.function.BiPredicate<Projectile, Double> enemyWorldImpact = (projectile, before) -> false;
    public void setEnemyWorldImpactHandler(java.util.function.BiPredicate<Projectile, Double> handler) {
        enemyWorldImpact=Objects.requireNonNull(handler);
    }

    /** Simulation-owned scenery claims contacts in the same swept order as enemies. */
    public void setWorldImpactHandler(java.util.function.BiPredicate<Projectile, Double> handler) {
        worldImpact = Objects.requireNonNull(handler);
    }
    private final Random dropRandom = new Random();
    private static final int MAX_BLAST_TARGETS = 16;
    private static final int MAX_RESIDUE_ZONES = 12;
    private static final int EFFECT_PULSE_TICKS = 12;

    private record DamageSource(WeaponId weapon, long root, CombatEvent.DamageKind kind, int depth) { }
    private static final class Burn {
        final int damage;
        final DamageSource source;
        final int residueTicks;
        int remaining;
        int untilPulse = EFFECT_PULSE_TICKS;
        Burn(int damage, int ticks, DamageSource source, int residueTicks) {
            this.damage = damage; this.remaining = ticks; this.source = source; this.residueTicks = residueTicks;
        }
    }
    private static final class Residue {
        final double x, y;
        final int damage;
        final DamageSource source;
        int remaining;
        int untilPulse = EFFECT_PULSE_TICKS;
        Residue(double x, double y, int damage, int ticks, DamageSource source) {
            this.x = x; this.y = y; this.damage = damage; this.remaining = ticks; this.source = source;
        }
    }
    public record EffectZoneSnapshot(double x, double y, int radius, int ticksRemaining, WeaponId weapon) { }

    public CollisionSystem() {
        this(event -> { }, () -> false, () -> 0);
    }

    public CollisionSystem(CombatEventSink eventSink) {
        this(eventSink, () -> false, () -> 0);
    }

    public CollisionSystem(CombatEventSink eventSink, BooleanSupplier phaseOneDrops) {
        this(eventSink, phaseOneDrops, () -> 0);
    }

    public CollisionSystem(CombatEventSink eventSink, BooleanSupplier phaseOneDrops,
                           IntSupplier comboGraceTicks) {
        this.eventSink = Objects.requireNonNull(eventSink, "eventSink");
        this.phaseOneDrops = Objects.requireNonNull(phaseOneDrops, "phaseOneDrops");
        this.comboGraceTicks = Objects.requireNonNull(comboGraceTicks, "comboGraceTicks");
    }

    public static class Context {
        public int score;
        public int combo;
        public int comboTimer;
        public int shakeTimer;
        public int flashTimer;
        public int newKills;
        public int hitstop;
        public String killLog;
        private final Map<ObstacleManager.Enemy, Burn> burns = new IdentityHashMap<>();
        private final Map<ObstacleManager.Enemy, Integer> marks = new IdentityHashMap<>();
        private final Map<ObstacleManager.Enemy, Integer> wallCooldowns = new IdentityHashMap<>();
        private final List<Residue> residues = new ArrayList<>();
        private long nextEventId;
        public int rejectedEffects;
        private final Set<Object> meleeContacts = Collections.newSetFromMap(new IdentityHashMap<>());
    }

    public void resetEffects(Context ctx) {
        ctx.burns.clear(); ctx.marks.clear(); ctx.residues.clear(); ctx.wallCooldowns.clear(); ctx.meleeContacts.clear();
    }

    /** Reset only at a level entrance; decorative effects never consume this gameplay stream. */
    public void resetDropSeed(long seed) { dropRandom.setSeed(seed); }

    public boolean isMarked(Context ctx, ObstacleManager.Enemy enemy) { return ctx.marks.containsKey(enemy); }

    /** Stable list order breaks equal-distance ties; expired/dead marks never attract a drone. */
    public ObstacleManager.Enemy selectDroneTarget(Context ctx, List<ObstacleManager.Enemy> enemies,
                                                    double originX, double originY) {
        return enemies.stream().filter(enemy -> !enemy.isDead() && enemy.getType().isHostile())
                .min(Comparator.<ObstacleManager.Enemy, Boolean>comparing(enemy -> !isMarked(ctx, enemy))
                        .thenComparingDouble(enemy -> {
                            double dx = enemy.getX() - originX, dy = enemy.getY() - originY;
                            return dx * dx + dy * dy;
                        })).orElse(null);
    }

    public List<EffectZoneSnapshot> effectZones(Context ctx) {
        return ctx.residues.stream().map(zone -> new EffectZoneSnapshot(
                zone.x, zone.y, 45, zone.remaining, zone.source.weapon())).toList();
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
        process(ctx, projectiles, enemyManager, boss, player, state, panelWidth, panelHeight,
                cameraX, particles, texts, logger, List.of());
    }

    /** Terrain is the current simulation-owned platform list; only horizontal knockback hits its sides. */
    public void process(Context ctx, List<Projectile> projectiles, ObstacleManager enemyManager,
                        Boss boss, Player player, GameState state, int panelWidth, int panelHeight,
                        double cameraX, List<Particle> particles, List<FloatingText> texts,
                        Consumer<String> logger, List<Platform> terrain) {

        if (state != GameState.RUNNING && state != GameState.BOSS_FIGHT
                && state != GameState.BOSS_WARNING && state != GameState.LEVEL_CLEAR) return;
        tickEffects(ctx, enemyManager, particles, texts);
        processProjectileCollisions(ctx, projectiles, enemyManager, boss, state, panelWidth, panelHeight, cameraX, particles, texts, terrain);
        processEnemyBullets(ctx, enemyManager.getEnemyBullets(), player, projectiles, particles, texts, panelWidth, panelHeight, cameraX);
        processMeleeAttack(ctx, player, enemyManager, boss, particles, texts);
        processPlayerEnemyCollisions(ctx, player, enemyManager, particles, texts, logger);
        processPlayerBossCollision(ctx, player, boss, state, texts, logger, cameraX, panelWidth);
    }

    private void processProjectileCollisions(Context ctx,
                                             List<Projectile> projectiles,
                                             ObstacleManager enemyManager,
                                             Boss boss,
                                             GameState state,
                                             int panelWidth, int panelHeight,
                                             double cameraX,
                                             List<Particle> particles,
                                             List<FloatingText> texts, List<Platform> terrain) {

        double leftBound = cameraX - 100;
        double rightBound = cameraX + panelWidth + 100;
        List<Runnable> deferredSpawns = new ArrayList<>();
        Iterator<Projectile> pIt = projectiles.iterator();
        while (pIt.hasNext()) {
            Projectile p = pIt.next();
            if (p.getRootEventId() == 0) p.assignRootEventId(++ctx.nextEventId);
            p.update();
            if (p.getX() < leftBound || p.getX() > rightBound
                    || p.getY() > panelHeight + 50 || p.getY() < -50 || p.isDead()) {
                pIt.remove();
                continue;
            }

            List<ObstacleManager.Enemy> contacts = enemyManager.getEnemies().stream()
                    .filter(en -> !en.isDead() && en.getType().isHostile() && p.canHit(en) && p.hits(en.getBounds()))
                    .sorted(java.util.Comparator.comparingDouble(en -> p.hitFraction(en.getBounds())))
                    .toList();
            for (ObstacleManager.Enemy en : contacts) {
                if (!p.isDead() && worldImpact.test(p, p.hitFraction(en.getBounds()))) break;
                if (!en.isDead() && en.getType().isHostile() && !p.isDead()
                        && p.canHit(en) && p.hits(en.getBounds())) {
                    boolean vertical = Math.abs(p.getVx())<.001 && Math.abs(p.getVy())>.001;
                    int hitDirection = vertical?0:p.getVx()>=0?1:-1;
                    ProjectileEffects effects = p.getSpec().effects();
                    int damage = p.getDamage();
                    if (ctx.marks.containsKey(en)) damage = (int) Math.ceil(damage * 1.25);
                    DamageSource source = new DamageSource(p.getWeapon(), p.getRootEventId(), p.getDamageKind(), 0);
                    double knockback = vertical ? 0 : Math.min(24, p.getKnockback());
                    double clippedKnockback = clippedKnockback(en, knockback, hitDirection, terrain);
                    boolean hitWall = clippedKnockback < knockback;
                    damageEnemy(ctx, en, damage, clippedKnockback, hitDirection,
                            source, enemyManager, particles, texts, deferredSpawns);
                    p.recordHit(en);
                    if (ctx.combo > 0 && comboGraceTicks.getAsInt() > 0 && p.consumeCriticalCombo()) {
                        ctx.comboTimer = Math.min(comboWindowTicks() + 60,
                                ctx.comboTimer + Math.min(45, Math.max(15, comboGraceTicks.getAsInt() / 2)));
                    }
                    if (hitWall && !en.isDead() && p.getWeapon() == WeaponId.FORCE_PUSH
                            && !ctx.wallCooldowns.containsKey(en) && p.consumeImpact()) {
                        ctx.wallCooldowns.put(en, 30);
                        wallBlast(ctx, en, p, source, enemyManager, particles, texts, deferredSpawns);
                    }
                    if (!en.isDead()) {
                        if (effects.markTicks() > 0) ctx.marks.put(en, effects.markTicks());
                        if (effects.burnDamage() > 0) {
                            Burn old = ctx.burns.get(en);
                            if (old == null || old.damage <= effects.burnDamage()) {
                                Burn burn = new Burn(effects.burnDamage(), effects.burnTicks(),
                                        new DamageSource(p.getWeapon(), p.getRootEventId(), CombatEvent.DamageKind.BURN, 1), effects.residueTicks());
                                if (old != null) burn.untilPulse = old.untilPulse;
                                ctx.burns.put(en, burn);
                            } else old.remaining = Math.max(old.remaining, effects.burnTicks());
                        }
                    } else if (effects.residueTicks() > 0) {
                        addResidue(ctx, en.getX(), en.getY(), effects.burnDamage(), effects.residueTicks(), source);
                    }
                    if (effects.blastDamage() > 0 && p.consumeImpact()) {
                        blast(ctx, en, effects, source, enemyManager, particles, texts, deferredSpawns);
                    }
                    if (p.isDead()) {
                        if (p.ricochetToward(enemyManager.getEnemies())) break;
                    }
                }
            }

            Boss.PartView bossPart = nearestBossPart(p, boss);
            if (!p.isDead()) worldImpact.test(p, bossPart == null
                    ? Double.POSITIVE_INFINITY : p.hitFraction(bossPart.bounds().rectangle()));
            if (!p.isDead() && state == GameState.BOSS_FIGHT && boss != null && boss.isActive()
                    && boss.getHp() > 0 && bossPart != null) {
                int previousPartHp = bossPart.hp();
                int partId = boss.getParts().indexOf(bossPart);
                int damage = boss.damageAt(bossPart.bounds().rectangle(), p.getDamage(), p.isDrone());
                Boss.PartView currentPart = boss.getParts().stream().filter(part -> part.id().equals(bossPart.id())).findFirst().orElse(null);
                boolean core = bossPart.id().equals("core") || boss.getHp() == 0;
                boolean vent = bossPart.id().equals("heat-vent");
                int partRemaining = currentPart == null ? 0 : currentPart.hp();
                eventSink.accept(new CombatEvent.DamageDealt(p.getWeapon(), damage,
                        p.getDamageKind(), p.getRootEventId(), 0, p.getX(), p.getY()));
                eventSink.accept(new CombatEvent.BossImpact(core ? CombatEvent.BossPart.CORE
                        : vent ? CombatEvent.BossPart.BODY : CombatEvent.BossPart.NODE, partId,
                        core || vent ? damage : Math.max(0, previousPartHp-partRemaining),
                        core || vent ? boss.getHp() : partRemaining,
                        core || vent ? boss.getMaxHp() : bossPart.maxHp(),
                        Math.max(bossPart.bounds().x(), Math.min(bossPart.bounds().x() + bossPart.bounds().width(), p.getX())),
                        Math.max(bossPart.bounds().y(), Math.min(bossPart.bounds().y() + bossPart.bounds().height(), p.getY())),
                        p.getWeapon(), p.getDamageKind(), p.getRootEventId(), p.isCritical(), false));
                p.setDead(true);
                ctx.shakeTimer = Math.max(ctx.shakeTimer, 4);
                spawnExplosion(particles, (int) p.getX(), (int) p.getY(), 5, Color.WHITE);
            }
        }
        // Execute deferred spawns after iteration
        for (Runnable r : deferredSpawns) r.run();
    }

    private static Boss.PartView nearestBossPart(Projectile projectile, Boss boss) {
        if (boss == null || !boss.isActive() || boss.getHp() <= 0) return null;
        return boss.getParts().stream().filter(part -> part.targetable() && !part.destroyed()
                        && projectile.hits(part.bounds().rectangle()))
                .min(Comparator.comparingDouble(part -> projectile.hitFraction(part.bounds().rectangle())))
                .orElse(null);
    }

    private static double clippedKnockback(ObstacleManager.Enemy enemy, double distance,
                                           int direction, List<Platform> terrain) {
        double allowed = distance;
        Rectangle bounds = enemy.getBounds();
        for (Platform wall : terrain) {
            if (wall.width <= 0 || wall.height <= 0
                    || bounds.getMaxY() <= wall.y || bounds.y >= wall.y + wall.height) continue;
            double gap = direction > 0 ? wall.x - (enemy.getX() + enemy.getWidth())
                    : enemy.getX() - (wall.x + wall.width);
            // Overlapping one-way platforms are not treated as walls already crossed.
            if (gap >= 0) allowed = Math.min(allowed, gap);
        }
        return allowed;
    }

    /** Environmental blasts use normal death/reward accounting, with a bounded target budget. */
    public void discharge(Context ctx, ObstacleManager manager, WeaponId weapon,
                          double x, double y, int radius, List<Particle> particles, List<FloatingText> texts) {
        DamageSource source = new DamageSource(weapon, ++ctx.nextEventId, CombatEvent.DamageKind.ENVIRONMENT, 0);
        List<Runnable> deferred = new ArrayList<>();
        int affected = 0;
        for (ObstacleManager.Enemy enemy : manager.getEnemies()) {
            if (affected >= MAX_BLAST_TARGETS) break;
            if (enemy.isDead() || !enemy.getType().isHostile()) continue;
            if (Math.hypot(enemy.getBounds().getCenterX() - x, enemy.getBounds().getCenterY() - y) > radius) continue;
            affected++;
            damageEnemy(ctx, enemy, 80, 0, enemy.getX() >= x ? 1 : -1,
                    source, manager, particles, texts, deferred);
        }
        deferred.forEach(Runnable::run);
    }

    private void wallBlast(Context ctx, ObstacleManager.Enemy impact, Projectile projectile,
                           DamageSource root, ObstacleManager manager, List<Particle> particles,
                           List<FloatingText> texts, List<Runnable> deferredSpawns) {
        double x = impact.getBounds().getCenterX(), y = impact.getBounds().getCenterY();
        DamageSource source = new DamageSource(root.weapon(), root.root(), CombatEvent.DamageKind.WALL_IMPACT, 1);
        int damage = Math.min(40, Math.max(12, projectile.getDamage() / 2));
        spawnExplosion(particles, (int) x, (int) y, 18, Color.ORANGE);
        texts.add(new FloatingText(x, y - 20, "WALL BREAK", Color.ORANGE));
        damageEnemy(ctx, impact, damage, 0, 1, source, manager, particles, texts, deferredSpawns);
        int affected = 1;
        for (ObstacleManager.Enemy enemy : manager.getEnemies()) {
            if (affected >= MAX_BLAST_TARGETS) break;
            if (enemy == impact || enemy.isDead() || !enemy.getType().isHostile()) continue;
            Rectangle bounds = enemy.getBounds();
            if (Math.hypot(bounds.getCenterX() - x, bounds.getCenterY() - y) > 56) continue;
            affected++;
            damageEnemy(ctx, enemy, damage, 0, 1, source, manager, particles, texts, deferredSpawns);
        }
    }

    private boolean damageEnemy(Context ctx, ObstacleManager.Enemy enemy, int damage,
                                double knockback, int direction, DamageSource source,
                                ObstacleManager manager, List<Particle> particles, List<FloatingText> texts,
                                List<Runnable> deferredSpawns) {
        if (enemy.isDead() || !enemy.getType().isHostile() || damage <= 0) return false;
        int previousHp = enemy.getHp();
        enemy.takeHit(damage, knockback, direction);
        int actualDamage = Math.max(0, previousHp - Math.max(0, enemy.getHp()));
        eventSink.accept(new CombatEvent.DamageDealt(source.weapon(), actualDamage, source.kind(),
                source.root(), source.depth(), enemy.getX(), enemy.getY()));
        if (!enemy.isDead()) {
            spawnExplosion(particles, (int) enemy.getX(), (int) enemy.getY(), 3, Color.WHITE);
            return false;
        }
        ctx.newKills++;
        ctx.combo++;
        ctx.comboTimer = Math.max(ctx.comboTimer, comboWindowTicks());
        int points = (int) (enemy.getType().pointValue * comboMultiplier(ctx.combo));
        ctx.score += points;
        ctx.killLog = enemy.getType().name().toLowerCase() + " terminated";
        eventSink.accept(new CombatEvent.EnemyKilled(enemy.getType(), points, enemy.getX(), enemy.getY(),
                source.weapon(), source.root(), source.kind()));
        if (enemy.getType().maxHp > 2) {
            ctx.shakeTimer = Math.max(ctx.shakeTimer, 10);
            ctx.hitstop = Math.max(ctx.hitstop, 4);
        }
        spawnExplosion(particles, (int) enemy.getX(), (int) enemy.getY(), 15, enemy.getColor());
        spawnSmoke(particles, (int) enemy.getX(), (int) enemy.getY(), 5);
        texts.add(new FloatingText(enemy.getX(), enemy.getY(), "+" + points, Color.ORANGE));
        double x = enemy.getX(), y = enemy.getY();
        deferredSpawns.add(() -> maybeSpawnDrop(x, y, manager));
        Burn burn = ctx.burns.remove(enemy);
        ctx.marks.remove(enemy);
        if (burn != null && burn.residueTicks > 0) {
            addResidue(ctx, x, y, burn.damage, burn.residueTicks, burn.source);
        }
        return true;
    }

    private void blast(Context ctx, ObstacleManager.Enemy initial, ProjectileEffects effects,
                       DamageSource root, ObstacleManager manager, List<Particle> particles,
                       List<FloatingText> texts, List<Runnable> deferredSpawns) {
        record Burst(double x, double y, int depth) { }
        ArrayDeque<Burst> pending = new ArrayDeque<>();
        pending.add(new Burst(initial.getBounds().getCenterX(), initial.getBounds().getCenterY(), 1));
        Set<ObstacleManager.Enemy> hit = Collections.newSetFromMap(new IdentityHashMap<>());
        hit.add(initial); // A direct hit is not also charged as splash from the same projectile.
        int affected = 0;
        while (!pending.isEmpty() && affected < MAX_BLAST_TARGETS) {
            Burst burst = pending.removeFirst();
            spawnExplosion(particles, (int) burst.x(), (int) burst.y(), 8, Color.ORANGE);
            for (ObstacleManager.Enemy enemy : manager.getEnemies()) {
                if (affected >= MAX_BLAST_TARGETS) break;
                if (enemy.isDead() || !enemy.getType().isHostile() || hit.contains(enemy)) continue;
                Rectangle bounds = enemy.getBounds();
                if (Math.hypot(bounds.getCenterX() - burst.x(), bounds.getCenterY() - burst.y()) > effects.blastRadius()) continue;
                hit.add(enemy);
                affected++;
                DamageSource source = new DamageSource(root.weapon(), root.root(), CombatEvent.DamageKind.BLAST, burst.depth());
                if (damageEnemy(ctx, enemy, effects.blastDamage(), 0, 1, source,
                        manager, particles, texts, deferredSpawns)
                        && burst.depth() <= effects.chainDepth() && burst.depth() < 3) {
                    pending.addLast(new Burst(bounds.getCenterX(), bounds.getCenterY(), burst.depth() + 1));
                }
            }
        }
    }

    private void addResidue(Context ctx, double x, double y, int damage, int duration, DamageSource source) {
        if (damage <= 0 || duration <= 0) return;
        for (Residue zone : ctx.residues) {
            if (Math.hypot(zone.x - x, zone.y - y) < 12) {
                zone.remaining = Math.max(zone.remaining, duration);
                return;
            }
        }
        if (ctx.residues.size() == MAX_RESIDUE_ZONES) { ctx.rejectedEffects++; return; }
        ctx.residues.add(new Residue(x, y, damage, duration,
                new DamageSource(source.weapon(), source.root(), CombatEvent.DamageKind.RESIDUE, 2)));
    }

    private void tickEffects(Context ctx, ObstacleManager manager,
                             List<Particle> particles, List<FloatingText> texts) {
        ctx.wallCooldowns.entrySet().removeIf(entry -> {
            if (entry.getKey().isDead() || !manager.getEnemies().contains(entry.getKey())) return true;
            entry.setValue(entry.getValue() - 1);
            return entry.getValue() <= 0;
        });
        ctx.marks.entrySet().removeIf(entry -> {
            if (entry.getKey().isDead() || !manager.getEnemies().contains(entry.getKey())) return true;
            entry.setValue(entry.getValue() - 1);
            return entry.getValue() <= 0;
        });
        List<Runnable> deferredSpawns = new ArrayList<>();
        for (var entry : new ArrayList<>(ctx.burns.entrySet())) {
            ObstacleManager.Enemy enemy = entry.getKey();
            Burn burn = entry.getValue();
            if (enemy.isDead() || !manager.getEnemies().contains(enemy)) { ctx.burns.remove(enemy); continue; }
            if (--burn.untilPulse <= 0) {
                burn.untilPulse = EFFECT_PULSE_TICKS;
                damageEnemy(ctx, enemy, burn.damage, 0, 1, burn.source,
                        manager, particles, texts, deferredSpawns);
            }
            if (--burn.remaining <= 0) ctx.burns.remove(enemy);
        }
        for (Residue zone : new ArrayList<>(ctx.residues)) {
            if (--zone.untilPulse <= 0) {
                zone.untilPulse = EFFECT_PULSE_TICKS;
                int targets = 0;
                for (ObstacleManager.Enemy enemy : manager.getEnemies()) {
                    if (targets >= MAX_BLAST_TARGETS) break;
                    if (enemy.isDead() || !enemy.getType().isHostile()) continue;
                    // Closest point of the hurtbox, so tall grounded enemies intersect a floor patch.
                    Rectangle bounds = enemy.getBounds();
                    double nearX = Math.max(bounds.x, Math.min(zone.x, bounds.getMaxX()));
                    double nearY = Math.max(bounds.y, Math.min(zone.y, bounds.getMaxY()));
                    if (Math.hypot(nearX - zone.x, nearY - zone.y) > 45) continue;
                    targets++;
                    damageEnemy(ctx, enemy, zone.damage, 0, 1, zone.source,
                            manager, particles, texts, deferredSpawns);
                }
            }
            zone.remaining--;
        }
        ctx.residues.removeIf(zone -> zone.remaining <= 0);
        deferredSpawns.forEach(Runnable::run);
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
            if(enemyWorldImpact.test(b,b.hitFraction(player.getBounds()))) {it.remove();continue;}
            if (b.getX() < leftBound || b.getX() > rightBound
                    || b.getY() < -50 || b.getY() > panelHeight + 50 || b.isDead()) {
                it.remove();
                continue;
            }

            Rectangle melee = player.getMeleeBounds();
            if (!b.getType().undestroyable && melee != null && melee.intersects(b.getBounds())
                    && player.canReflectProjectile() && ProjectileBudget.canEmit(playerProjectiles, 1)
                    && playerProjectiles.size() < EntityLimits.MAX_PROJECTILES && player.tryConsumeMeleeReflection()) {
                double speed = Math.max(6, Math.min(18, Math.hypot(b.getVx(), b.getVy())));
                double reflectedY = Math.max(-speed * 0.5, Math.min(speed * 0.5, -b.getVy()));
                double reflectedX = Math.sqrt(speed * speed - reflectedY * reflectedY) * player.getFacingDir();
                Projectile reflected = new Projectile(b.getX(), b.getY(), new ProjectileSpec(
                        player.getRunBuild().weapon(), Math.min(80, Math.max(15, b.getDamage())),
                        reflectedX, reflectedY, false, 0, 2, 0));
                reflected.markReflected();
                reflected.assignRootEventId(++ctx.nextEventId);
                playerProjectiles.add(reflected);
                b.setDead(true);
                it.remove();
                eventSink.accept(new CombatEvent.ProjectileReflected(reflected.getWeapon(),
                        reflected.getRootEventId(), 12, b.getX(), b.getY()));
                spawnExplosion(particles, (int) b.getX(), (int) b.getY(), 8, GameColors.SHIELD_CYAN);
                continue;
            }

            // Player projectiles destroy normal enemy bullets (not critical)
            if (!b.getType().undestroyable) {
                for (Projectile pp : playerProjectiles) {
                    if (!pp.isDead() && b.getBounds().intersects(pp.getBounds())) {
                        b.setDead(true);
                        pp.interceptNormalBullet();
                        spawnExplosion(particles, (int) b.getX(), (int) b.getY(), 5, Color.ORANGE);
                        break;
                    }
                }
            }
            if (b.isDead()) { it.remove(); continue; }

            // Enemy bullet hits player
            if (b.hits(player.getBounds())) {
                b.setDead(true);
                if (player.getShieldTimer() > 0 || player.getInvincibleTimer() > 0) {
                    spawnExplosion(particles, (int) b.getX(), (int) b.getY(), 8, GameColors.SHIELD_CYAN);
                } else {
                    int hpBefore=player.getHp();player.takeDamage(b.getDamage());
                    ctx.shakeTimer = 8;
                    ctx.flashTimer = 8;
                    spawnExplosion(particles, (int) b.getX(), (int) b.getY(), 10, GameColors.DANGER_RED);
                    if(hpBefore>player.getHp())texts.add(new FloatingText(b.getX(), b.getY(), "-" + (hpBefore-player.getHp()), Color.RED));
                }
                it.remove();
            }
        }
    }

    private void processMeleeAttack(Context ctx, Player player,
                                     ObstacleManager enemyManager, Boss boss,
                                     List<Particle> particles, List<FloatingText> texts) {
        Rectangle melee = player.getMeleeBounds();
        if (melee == null) { ctx.meleeContacts.clear(); return; }
        List<Runnable> deferredSpawns = new ArrayList<>();
        DamageSource source = new DamageSource(null, ++ctx.nextEventId, CombatEvent.DamageKind.MELEE, 0);
        for (ObstacleManager.Enemy en : enemyManager.getEnemies()) {
            if (!en.isDead() && en.getType().isHostile() && melee.intersects(en.getBounds())
                    && ctx.meleeContacts.add(en)) {
                damageEnemy(ctx, en, 50, 0, player.getFacingDir(), source,
                        enemyManager, particles, texts, deferredSpawns);
            }
        }
        if (boss != null && boss.isActive() && boss.getHp() > 0 && boss.canHit(melee)
                && ctx.meleeContacts.add(boss)) {
            int damage = boss.damageAt(melee, 50);
            eventSink.accept(new CombatEvent.DamageDealt(null, damage, CombatEvent.DamageKind.MELEE,
                    source.root(), 0, melee.getCenterX(), melee.getCenterY()));
            eventSink.accept(new CombatEvent.BossImpact(CombatEvent.BossPart.BODY, 0, damage,
                    boss.getHp(), boss.getMaxHp(),
                    Math.max(boss.getX(), Math.min(boss.getX() + boss.getWidth(), melee.getCenterX())),
                    Math.max(boss.getY(), Math.min(boss.getY() + boss.getHeight(), melee.getCenterY())),
                    null, CombatEvent.DamageKind.MELEE, source.root(), false, false));
            spawnExplosion(particles, (int) (melee.x + melee.width / 2), (int) (melee.y + melee.height / 2), 12, Color.WHITE);
        }
        deferredSpawns.forEach(Runnable::run);
    }

    private void processPlayerEnemyCollisions(Context ctx,
                                              Player player,
                                              ObstacleManager enemyManager,
                                              List<Particle> particles,
                                              List<FloatingText> texts,
                                              Consumer<String> logger) {

        for (ObstacleManager.Enemy en : enemyManager.getEnemies()) {
            if (!en.isDead() && !en.isCollisionProtected()
                    && player.getBounds().intersects(en.getBounds())) {
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
                } else if (en.isContactDangerous()) {
                    if (!en.getType().isChapterSpecialist()) en.setDead(true);
                    int previousHp = player.getHp();
                    player.takeDamage(en.getDamage());
                    if (player.getHp() == previousHp) continue;
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
                                            Consumer<String> logger, double cameraX, int panelWidth) {

        if (state != GameState.BOSS_FIGHT || boss == null || !boss.isActive() || boss.getHp() <= 0) return;
        Rectangle playerBounds = player.getBounds();
        int damage = boss.getActiveHazards().stream()
                .filter(hazard -> playerBounds.intersects(hazard.bounds().rectangle()))
                .mapToInt(Boss.AttackTelegraph::damage).max().orElse(0);
        if (damage == 0 && boss.isContactDangerous()
                && boss.getContactBounds().stream().anyMatch(bounds -> playerBounds.intersects(bounds.rectangle())))
            damage = boss.isDashing() ? 35 : 18;
        if (damage == 0) return;
        if (player.getShieldTimer() > 0 || player.getInvincibleTimer() > 0) return;

        int previousHp=player.getHp();
        player.takeDamage(damage);
        int actual=previousHp-player.getHp();
        if(actual<=0)return;
        player.pushAway(boss.getBounds().getCenterX(),18,cameraX,cameraX+panelWidth-player.getBounds().width);

        if (boss.isDashing()) {
            ctx.shakeTimer = 30;
            ctx.flashTimer = 15;
            texts.add(new FloatingText(player.getX(), player.getY(), "CRITICAL ERROR!", Color.RED));
            logger.accept("CRITICAL: Hit by core dump!");
        } else {
            ctx.shakeTimer = 5;
            texts.add(new FloatingText(player.getX(), player.getY(), "CONTACT -" + actual, Color.ORANGE));
        }
    }

    private void spawnSmoke(List<Particle> particles, int x, int y, int count) {
        for (int i = 0; i < count; i++) {
            particles.add(new Particle(x, y, new Color(80, 80, 80),
                    (Math.random() - 0.5) * 3, -2 - Math.random() * 3, 0.02f));
        }
    }

    private void spawnImpactStars(List<Particle> particles, int x, int y, int count, Color c) {
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2 / count * i;
            double len = 18 + Math.random() * 30;
            particles.add(new Particle(
                    x + (int) (Math.cos(angle) * len),
                    y + (int) (Math.sin(angle) * len),
                    c, 0, 0, 0.03f));
        }
    }

    private void maybeSpawnDrop(double x, double y, ObstacleManager om) {
        if (dropRandom.nextDouble() > 0.10) return;
        if (phaseOneDrops.getAsBoolean()) {
            om.spawnEnemy((int) x, (int) y - 20,
                    dropRandom.nextDouble() < 0.65 ? EntityType.HEALTH : EntityType.POWERUP_SHIELD);
            return;
        }
        EntityType drop = switch (dropRandom.nextInt(6)) {
            case 0 -> EntityType.HEALTH;
            case 1 -> EntityType.PICKUP_RAPID;
            case 2 -> EntityType.PICKUP_SPREAD;
            case 3 -> EntityType.PICKUP_HEAVY;
            case 4 -> EntityType.PICKUP_FLAME;
            default -> EntityType.PICKUP_LASER;
        };
        om.spawnEnemy((int) x, (int) y - 20, drop);
    }

    private static double comboMultiplier(int combo) {
        if (combo >= 20) return 3.0;
        if (combo >= 10) return 2.0;
        if (combo >= 5) return 1.5;
        return 1.0;
    }

    private int comboWindowTicks() { return 100 + Math.max(0, comboGraceTicks.getAsInt()); }

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
