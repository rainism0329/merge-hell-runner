package com.bigphil.mergehell;

import com.bigphil.mergehell.combat.*;
import com.bigphil.mergehell.model.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BossImpactCollisionTest {
    @Test void vulnerableBossReportsTrueDroneDamageOnceAndDoesNotShowTheOldDuplicateNumber() {
        List<CombatEvent> events = new ArrayList<>(); CollisionSystem collision = new CollisionSystem(events::add);
        Boss boss = new Boss("Heap", 2000, "!", 400, 1, 7); boss.activate(); boss.openVulnerability(50);
        var shots = new ArrayList<>(List.of(Projectile.drone(620, 150,
                new ProjectileSpec(WeaponId.GARBAGE_COLLECTOR, 25, 1, 0, false, 0, 0, 0))));
        var texts = new ArrayList<FloatingText>();
        for (int i = 0; i < 3; i++) collision.process(new CollisionSystem.Context(), shots, new ObstacleManager(), boss,
                new Player(100, 450), GameState.BOSS_FIGHT, 960, 600, 0, new ArrayList<>(), texts, ignored -> {});
        var hits = events.stream().filter(CombatEvent.BossImpact.class::isInstance).map(CombatEvent.BossImpact.class::cast).toList();
        assertEquals(1, hits.size()); assertEquals(38, hits.get(0).actualDamage()); assertEquals(1962, hits.get(0).remainingHp());
        assertEquals(CombatEvent.DamageKind.DRONE, hits.get(0).source()); assertEquals(WeaponId.GARBAGE_COLLECTOR, hits.get(0).weapon());
        assertTrue(hits.get(0).rootEventId() > 0); assertTrue(texts.isEmpty());
        var generic = events.stream().filter(CombatEvent.DamageDealt.class::isInstance).map(CombatEvent.DamageDealt.class::cast).toList();
        assertEquals(38, generic.get(0).damage());
    }

    @Test void meleeEmitsTheSameConfirmedBossImpactWithAcceptedHealthClamping() {
        List<CombatEvent> events = new ArrayList<>(); CollisionSystem collision = new CollisionSystem(events::add);
        Boss boss = new Boss("Heap", 2000, "!", 400, 1, 7); boss.activate(); boss.damage(boss.getMaxHp()-20);
        Player player = new Player(580, 150); player.melee();
        collision.process(new CollisionSystem.Context(), new ArrayList<>(), new ObstacleManager(), boss, player,
                GameState.BOSS_FIGHT, 960, 600, 0, new ArrayList<>(), new ArrayList<>(), ignored -> {});
        var hit = events.stream().filter(CombatEvent.BossImpact.class::isInstance).map(CombatEvent.BossImpact.class::cast).findFirst().orElseThrow();
        assertEquals(20, hit.actualDamage()); assertTrue(hit.destroyed()); assertEquals(CombatEvent.DamageKind.MELEE, hit.source());
    }
}
