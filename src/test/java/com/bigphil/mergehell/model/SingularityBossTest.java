package com.bigphil.mergehell.model;

import com.bigphil.mergehell.engine.ProjectileBuffer;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SingularityBossTest {
    private static final class Fight {
        final Boss boss = new Boss("Rootheart", 14000, "S", 960, 4, 57L);
        final ObstacleManager enemies = new ObstacleManager();
        final ArrayList<Projectile> shots = new ArrayList<>();
        Fight() { boss.previewArrival(1, 480); boss.activate(); }
        void tick(double px) { boss.update(enemies, 480, px, 450, shots); }
        void until(String action) {
            for (int i = 0; i < 1800 && !boss.getEncounterAction().equals(action); i++) tick(200);
            assertEquals(action, boss.getEncounterAction());
        }
        Boss.PartView part(String id) { return boss.getParts().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow(); }
    }
    @Test void sporeFanLocksAllOriginsAndVelocitiesBeforeItsWholeVolley() {
        var f = new Fight(); f.until("SPORE_FAN");
        var predicted = f.boss.getSingularityPredictedShots();
        assertEquals(8, predicted.size()); assertTrue(f.shots.isEmpty()); assertEquals(80, f.boss.getSingularityWarningTicks());
        for (int i = 0; i < 79; i++) {
            f.boss.recordPlayerShot(); f.tick(1300);
            assertEquals(predicted, f.boss.getSingularityPredictedShots()); assertTrue(f.shots.isEmpty());
        }
        f.tick(1300); assertEquals(predicted.size(), f.shots.size());
        for (int i = 0; i < predicted.size(); i++) {
            var expected = predicted.get(i); var actual = f.shots.get(i);
            assertEquals(expected.x(), actual.getX()); assertEquals(expected.y(), actual.getY());
            assertEquals(expected.vx(), actual.getVx()); assertEquals(expected.vy(), actual.getVy());
        }
        assertTrue(f.boss.getSingularityPredictedShots().isEmpty());
    }
    @Test void tendrilUsesWarnedColumnAndDestroyingItsOrganCancelsThatStrike() {
        var f = new Fight(); f.until("LEFT_TENDRIL");
        var locked = f.boss.getAttackTelegraphs(); assertEquals(82, f.boss.getWarningTicks());
        for (int i = 0; i < 81; i++) {
            f.tick(1300); assertEquals(locked.get(0).bounds(), f.boss.getAttackTelegraphs().get(0).bounds());
            assertTrue(f.boss.getActiveHazards().isEmpty());
        }
        f.tick(1300); assertEquals(locked.get(0).bounds(), f.boss.getActiveHazards().get(0).bounds());
        var organ = f.part("left-organ"); f.boss.damageAt(organ.bounds().rectangle(), organ.hp());
        assertTrue(f.part("left-organ").destroyed()); assertTrue(f.boss.getActiveHazards().isEmpty());
        assertFalse(f.boss.isVulnerable());
    }
    @Test void twoDestroyedOrgansOpenHeartThenRegrowOnlyAfterHarmlessVisibleRecovery() {
        var f = new Fight(); f.tick(200);
        for (String id : List.of("left-organ", "right-organ")) {
            var part = f.part(id); f.boss.damageAt(part.bounds().rectangle(), part.hp());
        }
        assertEquals("EXPOSED", f.boss.getEncounterAction()); assertEquals(260, f.boss.getVulnerabilityTicks());
        assertTrue(f.part("core").weak()); assertFalse(f.boss.isContactDangerous());
        assertEquals(22, f.boss.damageAt(f.part("core").bounds().rectangle(), 10));
        for (int i = 0; i < 260; i++) f.tick(200);
        assertEquals("REBUILD", f.boss.getEncounterAction()); assertTrue(f.part("left-organ").destroyed());
        for (int i = 0; i < 109; i++) { f.tick(200); assertTrue(f.part("left-organ").destroyed()); assertFalse(f.boss.isContactDangerous()); }
        f.tick(200); assertFalse(f.part("left-organ").destroyed());
        assertEquals(f.part("left-organ").maxHp(), f.part("left-organ").hp());
    }
    @Test void eggsHatchOnlyAfterFullWarningAndNeverExceedSixLiveSummons() {
        var f = new Fight(); f.until("HATCH");
        assertEquals(95, f.boss.getWarningTicks()); assertTrue(f.enemies.getEnemies().isEmpty());
        for (int i = 0; i < 94; i++) { f.tick(200); assertTrue(f.enemies.getEnemies().isEmpty()); }
        f.tick(200); assertEquals(2, f.enemies.getEnemies().size());
        assertTrue(f.enemies.getEnemies().stream().allMatch(e -> e.getType() == EntityType.LURKER && e.isCollisionProtected()));
        for (int i = 0; i < 6000; i++) f.tick(200);
        assertEquals(6, f.enemies.getEnemies().size());
    }
    @Test void finalPhaseShedsOrgansAndCrawlsOnlyAfterFullWarning() {
        var f = new Fight(); f.tick(200); double x = f.boss.getX(); f.boss.damage(5000); f.tick(200);
        assertEquals(3, f.boss.getCombatStage()); assertEquals(x, f.boss.getX());
        assertEquals(90, f.boss.getSingularityRebootTicks()); assertFalse(f.boss.isContactDangerous());
        assertTrue(f.part("left-organ").destroyed()); assertTrue(f.part("right-organ").destroyed()); assertTrue(f.part("core").weak());
        f.until("HEART_CRAWL"); assertEquals(90, f.boss.getWarningTicks()); x = f.boss.getX();
        for (int i = 0; i < 89; i++) { f.tick(1300); assertEquals(x, f.boss.getX()); }
        f.tick(1300); f.tick(1300); assertTrue(f.boss.getX() < x);
        f.boss.clearVulnerability(); assertEquals(90, f.boss.getSingularityRebootTicks());
        assertTrue(f.boss.getAttackTelegraphs().isEmpty()); assertFalse(f.boss.isContactDangerous());
    }
    @Test void rejectedSporeVolleyDoesNotEvictReplayOrAcceptOldAnchorExploit() {
        var f = new Fight(); f.until("SPORE_FAN"); f.boss.interruptSingularity(180); assertFalse(f.boss.isVulnerable());
        var shots = new ProjectileBuffer(3);
        var original = List.of(new Projectile(0,0,1,0,ProjectileType.ENEMY),new Projectile(0,0,1,0,ProjectileType.ENEMY),
                new Projectile(0,0,1,0,ProjectileType.ENEMY)); shots.addAll(original);
        for (int i = 0; i < 80; i++) f.boss.update(f.enemies,480,200,450,shots);
        assertEquals(original, shots); assertEquals(8, shots.rejectedProjectiles());
        shots.clear(); f.boss.update(f.enemies,480,200,450,shots); assertTrue(shots.isEmpty());
    }
}
