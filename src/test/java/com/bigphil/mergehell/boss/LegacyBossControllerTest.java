package com.bigphil.mergehell.boss;

import org.junit.jupiter.api.Test;

import java.util.Random;
import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBossControllerTest {
    @Test
    void dependenciesShieldCoreThenExposeIt() {
        LegacyBossController boss = new LegacyBossController(new Random(20), 120);
        assertEquals(0, boss.damageCore(100));
        for (DependencyNode node : boss.nodes()) boss.damageNode(node.id(), node.maxHp());
        assertEquals(BossPhase.CORE_EXPOSED, boss.phase());
        assertEquals(100, boss.damageCore(100));
        assertEquals(BossPhase.ENRAGED, boss.phase());
    }

    @Test
    void highDamageAttackWaitsBehindReadableTelegraph() {
        LegacyBossController boss = new LegacyBossController(new Random(22), 120);
        BossAction action = null;
        for (int tick = 0; tick < 2_000 && action == null; tick++) {
            for (BossAction candidate : boss.tick(100, 200)) {
                if (candidate instanceof BossAction.Laser || candidate instanceof BossAction.Volley
                        || candidate instanceof BossAction.Shockwave) action = candidate;
            }
        }
        assertNotNull(action);
        int telegraph = action instanceof BossAction.Laser laser ? laser.telegraphTicks()
                : action instanceof BossAction.Volley volley ? volley.telegraphTicks()
                : ((BossAction.Shockwave) action).telegraphTicks();
        assertTrue(telegraph >= 24);
    }

    @Test
    void everyDependencyIsOutsideTheCoreAndPlacedInAShootableLane() {
        LegacyBossController boss = new LegacyBossController(new Random(24), 2_400);
        boss.setOrigin(760, 190);
        Rectangle core = boss.coreBounds();

        for (DependencyNode node : boss.nodes()) {
            Rectangle bounds = boss.nodeBounds(node.id());
            assertFalse(bounds.intersects(core), "link " + node.id() + " is hidden by the core");
            assertTrue(bounds.y >= 190 && bounds.y + bounds.height <= 480);
        }
    }

    @Test
    void bombStyleAreaDamageMeaningfullyBreaksAllLinks() {
        LegacyBossController boss = new LegacyBossController(new Random(26), 2_400);

        assertTrue(boss.damageAllNodes(120).isEmpty());
        assertEquals(3, boss.damageAllNodes(120).size());
        assertEquals(BossPhase.CORE_EXPOSED, boss.phase());
    }

    @Test
    void attackCycleContainsLasersVolleysAndPhysicalShockwaves() {
        LegacyBossController boss = new LegacyBossController(new Random(28), 2_400);
        boolean laser = false, volley = false, shockwave = false;
        for (int tick = 0; tick < 4_000 && !(laser && volley && shockwave); tick++) {
            for (BossAction action : boss.tick(200, 430)) {
                laser |= action instanceof BossAction.Laser;
                volley |= action instanceof BossAction.Volley;
                shockwave |= action instanceof BossAction.Shockwave;
            }
        }
        assertTrue(laser && volley && shockwave);
    }
}
