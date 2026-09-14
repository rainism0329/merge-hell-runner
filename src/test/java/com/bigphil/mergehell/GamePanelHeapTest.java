package com.bigphil.mergehell;

import com.bigphil.mergehell.combat.CombatEvent;
import com.bigphil.mergehell.model.*;
import com.bigphil.mergehell.persistence.*;
import com.bigphil.mergehell.world.HeapDistrictController;
import org.junit.jupiter.api.*;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GamePanelHeapTest {
    private final List<HeapGameHarness> scenes = new ArrayList<>();
    private MergeHellState original;
    @BeforeEach void isolate() {
        original = MergeHellStateService.getInstance().getState();
        MergeHellState state = new MergeHellState(); state.settings.muted = true;
        MergeHellStateService.getInstance().loadState(state);
    }
    @AfterEach void restore() { scenes.forEach(HeapGameHarness::close); MergeHellStateService.getInstance().loadState(original); }
    private HeapGameHarness scene() throws Exception { var h = new HeapGameHarness(); scenes.add(h); return h; }

    @Test void realPurgeInputChannelsOnceAndUsedStationsSurviveRespawn() throws Exception {
        var h = scene(); h.place(745, 450);
        h.key("HEAP_PURGE"); h.tick();
        assertEquals(HeapDistrictController.NodeState.CHANNELING, h.heap().snapshot().nodes().get(0).state());
        h.quietTicks(49);
        assertEquals(HeapDistrictController.NodeState.USED, h.heap().snapshot().nodes().get(0).state());
        int score = h.context().score;
        assertEquals(300, score);
        h.key("HEAP_PURGE"); h.key("HEAP_SALVAGE"); h.quietTicks(2);
        assertEquals(score, h.context().score);
        h.player().setInvincibleTimer(0); h.player().setShieldTimer(0); h.player().takeDamage(1000); h.tick();
        assertEquals(2, h.player().getLives());
        assertTrue(h.heap().snapshot().pools().isEmpty());
        assertEquals(HeapDistrictController.NodeState.USED, h.heap().snapshot().nodes().get(0).state());
    }

    @Test void salvageAwardsOnceAndPauseFreezesTheChapter() throws Exception {
        var h = scene(); h.place(745, 450); h.key("HEAP_SALVAGE"); h.tick();
        assertEquals(600, h.context().score);
        assertTrue(h.heap().snapshot().pressure() > 25);
        assertTrue(h.heap().snapshot().pools().stream().allMatch(p -> p.phase() == HeapDistrictController.PoolPhase.WARNING));
        h.key("PAUSE_P"); h.tick(); var snapshot = h.heap().snapshot();
        h.key("HEAP_PURGE"); h.key("HEAP_SALVAGE"); h.ticks(20);
        assertSame(snapshot, h.heap().snapshot()); assertEquals(600, h.context().score);
        h.key("PAUSE_P"); h.tick(); assertTrue(h.heap().snapshot().tick() > snapshot.tick());
    }

    @Test void bossGcRemovesRealBulletsAndOpensAnActualDamageWindow() throws Exception {
        var h = scene(); h.bossPractice();
        var station = h.heap().snapshot().nodes().stream().filter(n -> n.id() == 101).findFirst().orElseThrow();
        h.place(station.bounds().centerX() - 15, 450);
        h.key("HEAP_PURGE"); h.tick();
        while (h.heap().snapshot().nodes().stream().anyMatch(n -> n.id() == 101 && n.ticksRemaining() > 1
                && n.state() == HeapDistrictController.NodeState.CHANNELING)) h.quietTicks(1);
        double camera = (double) h.get("cameraX");
        Projectile bullet = new Projectile(camera + 540, 210, 0, 0, ProjectileType.ENEMY);
        h.enemies().getEnemyBullets().add(bullet); h.tick();
        assertFalse(h.enemies().getEnemyBullets().contains(bullet));
        assertTrue(h.boss().isVulnerable());
        int hp = h.boss().getHp(); assertEquals(150, h.boss().damage(100)); assertEquals(hp - 150, h.boss().getHp());
        assertEquals(HeapDistrictController.NodeState.COOLDOWN,
                h.heap().snapshot().nodes().stream().filter(n -> n.id() == 101).findFirst().orElseThrow().state());
        h.key("LAB_TOGGLE_ALT"); h.tick();
        h.player().setInvincibleTimer(0); h.player().setShieldTimer(0); h.player().takeDamage(1000);
        for (int i = 0; i < 12 && h.player().getLives() == 3; i++) h.tick();
        assertEquals(2, h.player().getLives(), "The brief guard-break stop must release into revival");
        assertFalse(h.boss().isVulnerable(), "Revival clears both the mechanism hint and the actual damage multiplier");
        assertEquals(0, h.heap().snapshot().bossExposeTicks());
    }

    @Test void aRealProjectileSeversARefluxBlockAndFeedbackFreezesDuringPause() throws Exception {
        var h = scene(); h.bossPractice();
        // Lab relocation retains the three-second safety period before the first refill cycle starts.
        for (int i = 0; i < 420 && h.heap().snapshot().blocks().stream().noneMatch(b -> b.warningTicksRemaining() == 0); i++)
            h.quietTicks(1);
        var block = h.heap().snapshot().blocks().stream().filter(b -> b.warningTicksRemaining() == 0).findFirst().orElseThrow();
        Projectile shot = new Projectile(block.bounds().x(), block.bounds().y(), 0, 0, ProjectileType.COMMIT);
        h.shots().add(shot); h.tick();
        assertTrue(shot.isDead());
        assertTrue(h.heap().snapshot().blocks().stream().noneMatch(b -> b.id() == block.id()));
        assertTrue(h.boss().isVulnerable());
        h.key("BOMB"); h.tick();
        assertTrue(h.feedback().snapshot().impacts().stream().anyMatch(i -> i.source() == CombatEvent.DamageKind.BOMB));
        h.key("PAUSE_P"); h.tick(); var paused = h.feedback().snapshot(); h.ticks(20);
        assertEquals(paused, h.feedback().snapshot());
    }

    @Test void continueRebuildsTheSavedHeapEntranceAndAnotherWorldHasNoHeapState() throws Exception {
        var h = scene(); h.place(745, 450); h.key("HEAP_SALVAGE"); h.tick();
        h.key("PAUSE_P"); h.tick(); h.key("MENU"); h.tick(); h.key("UPGRADE_REROLL"); h.tick();
        assertEquals(1, h.get("level")); assertNotNull(h.heap()); assertEquals(0, h.context().score);
        assertTrue(h.heap().snapshot().nodes().stream().allMatch(n -> n.state() == HeapDistrictController.NodeState.READY));
        h.set("level", 2); h.invoke("advanceLevel"); h.tick(); assertNull(h.heap());
    }

    @Test void aNodeUpgradeStopsRemainingCollisionsBeforeShowingTheChoice() throws Exception {
        var h = scene(); h.place(745, 450);
        h.panel.getSession().awardBuildXp(80);
        h.player().takeDamage(99); h.player().setInvincibleTimer(0);
        Projectile hostile = new Projectile(745, 450, 0, 0, ProjectileType.ENEMY);
        h.enemies().getEnemyBullets().add(hostile);
        h.key("HEAP_SALVAGE"); h.tick();
        assertEquals(GameState.UPGRADE_SELECTION, h.state());
        assertEquals(1, h.player().getHp()); assertEquals(3, h.player().getLives());
        assertTrue(h.enemies().getEnemyBullets().contains(hostile), "The unopened combat step cannot consume or apply a bullet");
    }

    @Test void aLethalLeakIsSettledBeforeTheSameKeyCanAwardAnUpgrade() throws Exception {
        var h = scene(); h.place(745, 450);
        // Deterministic production-controller fixture: a pool turns active on the next real panel tick.
        var fixture = HeapDistrictController.standard(42);
        var input = new HeapDistrictController.Input(new HeapDistrictController.Bounds(745, 450, 30, 30),
                457, 960, 480, true, false, null, 1);
        for (int i = 0; i < 239; i++) fixture.update(input);
        fixture.drainEvents(); h.set("heap", fixture);
        var playerBounds = h.player().getBounds();
        assertTrue(fixture.snapshot().pools().stream().anyMatch(p -> p.ticksRemaining() == 1
                && p.bounds().rectangle().intersects(playerBounds)));
        h.panel.getSession().awardBuildXp(80);
        h.player().takeDamage(99); h.player().setInvincibleTimer(0);
        h.key("HEAP_SALVAGE"); h.tick();
        assertEquals(2, h.player().getLives()); assertTrue(h.player().getHp() > 0);
        assertEquals(GameState.RUNNING, h.state()); assertEquals(0, h.context().score);
        assertEquals(80, h.panel.getSession().buildProgress().currentXp());
        assertEquals(HeapDistrictController.NodeState.READY, h.heap().snapshot().nodes().get(0).state());
    }
}
