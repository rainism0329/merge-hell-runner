package com.bigphil.mergehell.progression;

import com.bigphil.mergehell.model.EntityType;

/** Authored production health, separate from small entity fixtures and difficulty scaling. */
public final class CombatBalance {
    public static int enemyHealth(EntityType type, int chapter, GameDifficulty difficulty) {
        if (!type.isHostile()) return type.maxHp;
        int base = switch (type) {
            case BUG -> 18;
            case CONFLICT -> 38;
            case LOCK -> 48;
            case CRASH -> 32;
            case FIREWALL -> 120;
            case LEAK -> 42;
            case TECHDEBT -> 145;
            default -> (int)Math.round(type.maxHp * 1.27);
        };
        return difficulty.health(base);
    }
    public static int bossHealth(int chapter, GameDifficulty difficulty) {
        if (chapter < 0 || chapter > 4) throw new IllegalArgumentException("Unknown chapter");
        return difficulty.health(new int[]{3600,2650,3800,4900,6100}[chapter]);
    }
    public static int recovery(int ticks, GameDifficulty difficulty) {
        return difficulty.recovery((int)Math.round(ticks * .82));
    }
    private CombatBalance() { }
}
