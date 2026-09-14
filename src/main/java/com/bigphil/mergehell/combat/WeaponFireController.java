package com.bigphil.mergehell.combat;

import com.bigphil.mergehell.model.Projectile;
import com.bigphil.mergehell.engine.ProjectileBudget;

import java.util.ArrayList;
import java.util.List;

public final class WeaponFireController {
    /** A spread/beam/heat burst is admitted whole; rejected fire consumes no randomness. */
    public boolean fireInto(FireRequest request, List<Projectile> destination) {
        return ProjectileBudget.emit(destination, request.stats().pellets(), () -> fire(request));
    }

    public List<Projectile> fire(FireRequest request) {
        CombatStats stats = request.stats();
        int baseDamage = request.overclocked()
                ? (int) Math.ceil(stats.damage() * 1.35) : stats.damage();
        int pierces = stats.pierces() + (request.overclocked() ? 2 : 0);
        List<Projectile> result = new ArrayList<>(stats.pellets());
        for (int i = 0; i < stats.pellets(); i++) {
            double offset = stats.pellets() == 1 ? 0
                    : (i - (stats.pellets() - 1) / 2.0) * stats.spreadRadians() / (stats.pellets() - 1);
            // One launch-time velocity addition preserves the authored spread in the shooter's frame.
            // Projectiles never read the player's movement again after this request.
            double vx = Math.cos(offset) * stats.speed() * request.facing() + request.shooterVelocityX();
            double vy = Math.sin(offset) * stats.speed();
            boolean critical = request.random().nextDouble() < stats.criticalChance();
            int damage = critical ? baseDamage * 2 : baseDamage;
            ProjectileSpec spec = new ProjectileSpec(
                    request.weapon(), damage, vx, vy, critical, pierces,
                    stats.knockback(), stats.ricochets(),
                    ProjectileEffects.forWeapon(request.weapon(), request.evolved()));
            result.add(new Projectile(request.originX(), request.originY(), spec));
        }
        return result;
    }
}
