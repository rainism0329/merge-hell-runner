package com.bigphil.mergehell.combat;

import com.bigphil.mergehell.boss.BossPhase;
import com.bigphil.mergehell.model.EntityType;

public interface CombatEvent {
    record EnemyKilled(EntityType type, int points, double x, double y) implements CombatEvent {}
    record BossPhaseChanged(BossPhase phase) implements CombatEvent {}
    record BossNodeDestroyed(int nodeId, int buildXp, double x, double y) implements CombatEvent {}
}
