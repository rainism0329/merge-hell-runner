package com.bigphil.mergehell.combat;

import java.util.random.RandomGenerator;

public record FireRequest(
        WeaponId weapon, double originX, double originY, int facing,
        CombatStats stats, boolean overclocked,
        RandomGenerator random) {
    public FireRequest {
        if (facing != -1 && facing != 1) throw new IllegalArgumentException("facing must be -1 or 1");
    }
}
