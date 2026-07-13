package com.bigphil.mergehell.mission;

import com.bigphil.mergehell.model.EntityType;

public sealed interface DirectorCommand {
    record Spawn(EntityType type, int side, int cost) implements DirectorCommand { }
    record BeginArena(String objective, int durationTicks) implements DirectorCommand { }
    record BeginRecovery(int durationTicks) implements DirectorCommand { }
    record SpawnBoss() implements DirectorCommand {
        public static final SpawnBoss INSTANCE = new SpawnBoss();
    }
}
