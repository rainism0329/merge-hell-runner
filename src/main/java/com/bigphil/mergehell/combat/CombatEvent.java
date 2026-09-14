package com.bigphil.mergehell.combat;

import com.bigphil.mergehell.boss.BossPhase;
import com.bigphil.mergehell.model.EntityType;

public interface CombatEvent {
    enum DamageKind { DIRECT, BURN, BLAST, RESIDUE, MELEE, UNSPECIFIED, WALL_IMPACT, REFLECTED, DRONE, BOMB, ENVIRONMENT }
    enum BossPart { BODY, CORE, NODE }
    record BossImpact(BossPart part, int partId, int actualDamage, int remainingHp, int maxHp,
                      double x, double y, WeaponId weapon, DamageKind source, long rootEventId,
                      boolean critical, boolean blocked) implements CombatEvent {
        public BossImpact {
            java.util.Objects.requireNonNull(part, "part");
            java.util.Objects.requireNonNull(source, "source");
            if (partId < 0 || actualDamage < 0 || remainingHp < 0 || maxHp <= 0
                    || remainingHp > maxHp || actualDamage > maxHp || rootEventId < 0
                    || !Double.isFinite(x) || !Double.isFinite(y) || blocked && actualDamage != 0)
                throw new IllegalArgumentException("Invalid confirmed boss impact");
        }
        public boolean destroyed() { return actualDamage > 0 && remainingHp == 0; }
    }
    record EnemyKilled(EntityType type, int points, double x, double y,
                       WeaponId weapon, long rootEventId, DamageKind cause) implements CombatEvent {
        public EnemyKilled(EntityType type, int points, double x, double y) {
            this(type, points, x, y, null, 0, DamageKind.UNSPECIFIED);
        }
    }
    record DamageDealt(WeaponId weapon, int damage, DamageKind kind, long rootEventId,
                       int depth, double x, double y) implements CombatEvent { }
    record ProjectileReflected(WeaponId weapon, long rootEventId, int charge, double x, double y) implements CombatEvent {
        public ProjectileReflected {
            if (weapon == null || rootEventId <= 0 || charge <= 0 || charge > 12)
                throw new IllegalArgumentException("Invalid reflection reward");
        }
    }
    record BossPhaseChanged(BossPhase phase) implements CombatEvent {}
    record BossNodeDestroyed(int nodeId, int buildXp, double x, double y) implements CombatEvent {}
}
