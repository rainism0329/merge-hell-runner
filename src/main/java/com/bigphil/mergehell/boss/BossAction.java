package com.bigphil.mergehell.boss;

import com.bigphil.mergehell.model.EntityType;

public sealed interface BossAction {
    record Laser(int telegraphTicks, double laneY, int height, int damage) implements BossAction { }
    record Volley(int telegraphTicks, double targetX, double targetY,
                  int count, double speed) implements BossAction {
        /** Shared by live projectile emission and its warning; target is locked when announced. */
        public double angleFrom(double startX, double startY, int index) {
            if (index < 0 || index >= count) throw new IllegalArgumentException("Volley index out of bounds");
            return Math.atan2(targetY + 15 - startY, targetX - startX)
                    + (index - (count - 1) / 2.0) * 0.105;
        }
    }
    record Shockwave(int telegraphTicks, int count, double speed) implements BossAction { }
    record Spawn(EntityType type, int count) implements BossAction { }
}
