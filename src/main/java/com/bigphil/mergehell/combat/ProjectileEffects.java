package com.bigphil.mergehell.combat;

/** Authored, bounded effects carried by a projectile; contains no live entity references. */
public record ProjectileEffects(int burnDamage, int burnTicks, int blastDamage, int blastRadius,
                                int chainDepth, int interceptions, int markTicks,
                                int residueTicks, int lifetimeTicks) {
    public static final ProjectileEffects NONE = new ProjectileEffects(0, 0, 0, 0, 0, 0, 0, 0, 240);

    public ProjectileEffects {
        if (burnDamage < 0 || burnTicks < 0 || burnTicks > 600 || blastDamage < 0
                || blastRadius < 0 || blastRadius > 200 || chainDepth < 0 || chainDepth > 3
                || interceptions < 0 || interceptions > 8 || markTicks < 0 || markTicks > 600
                || residueTicks < 0 || residueTicks > 300 || lifetimeTicks <= 0 || lifetimeTicks > 600) {
            throw new IllegalArgumentException("Projectile effects exceed their simulation budget");
        }
    }

    public static ProjectileEffects forWeapon(WeaponId weapon, boolean evolved) {
        return switch (weapon) {
            case COMMIT_CANNON -> new ProjectileEffects(0, 0, 0, 0, 0, 0, evolved ? 120 : 0, 0, 180);
            case FORCE_PUSH -> new ProjectileEffects(0, 0, evolved ? 12 : 0, evolved ? 44 : 0, 0, 0, 0, 0, 65);
            case RAPID_CI -> NONE;
            case GARBAGE_COLLECTOR -> new ProjectileEffects(0, 0, evolved ? 50 : 24,
                    evolved ? 82 : 42, evolved ? 2 : 0, 0, 0, 0, 150);
            case FIREWALL -> new ProjectileEffects(evolved ? 4 : 2, 72, 0, 0, 0,
                    evolved ? 3 : 0, 0, evolved ? 90 : 0, 28);
            case REFACTOR_BEAM -> NONE;
        };
    }
}
