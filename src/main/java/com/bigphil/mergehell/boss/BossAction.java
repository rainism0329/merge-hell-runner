package com.bigphil.mergehell.boss;

import com.bigphil.mergehell.model.EntityType;

public sealed interface BossAction {
    record Laser(int telegraphTicks, double laneY, int height, int damage) implements BossAction { }
    record Volley(int telegraphTicks, double targetX, double targetY,
                  int count, double speed) implements BossAction { }
    record Shockwave(int telegraphTicks, int count, double speed) implements BossAction { }
    record Spawn(EntityType type, int count) implements BossAction { }
}
