package com.bigphil.mergehell.persistence;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MergeHellStateServiceTest {
    @Test
    void onlyTheOwnerCanSaveClearOrReleaseAndContinueClaimsAtomically() {
        MergeHellStateService service = new MergeHellStateService();
        MergeHellState.ActiveRun run = CheckpointCodecTest.freshRun();
        assertTrue(service.claimRun("window-A", run.runId));
        assertTrue(service.saveCheckpoint("window-A", run));
        assertFalse(service.claimRun("window-B", UUID.randomUUID().toString()));
        assertTrue(service.claimCheckpoint("window-B").isEmpty());
        assertFalse(service.saveCheckpoint("window-B", run));
        assertFalse(service.clearCheckpoint("window-B"));
        assertFalse(service.releaseRun("window-B"));
        service.clearActiveRun();
        service.saveActiveRun(null);
        assertEquals(run.runId, service.readCheckpoint().orElseThrow().runId);
        assertTrue(service.releaseRun("window-A"));
        assertEquals(run.runId, service.claimCheckpoint("window-B").orElseThrow().runId);
        assertFalse(service.saveCheckpoint("window-A", run));
        assertTrue(service.clearCheckpoint("window-B"));
        assertTrue(service.readCheckpoint().isEmpty());
    }

    @Test
    void writesReadsAndStateLoadsNeverShareMutableCollections() {
        MergeHellStateService service = new MergeHellStateService();
        MergeHellState.ActiveRun run = CheckpointCodecTest.freshRun();
        assertTrue(service.claimRun("window", run.runId));
        assertTrue(service.saveCheckpoint("window", run));
        run.player.hp = 1;
        run.player.ammoReserve.put(com.bigphil.mergehell.model.WeaponType.HEAVY, 999);
        MergeHellState first = service.getState();
        first.activeRun.player.hp = 2;
        first.activeRun.session.ranks.clear();
        first.topScores.add(99_999);
        assertEquals(100, service.readCheckpoint().orElseThrow().player.hp);
        assertTrue(service.readCheckpoint().orElseThrow().player.ammoReserve.isEmpty());
        assertTrue(service.getState().topScores.isEmpty());
        assertTrue(service.releaseRun("window"));
        MergeHellState loaded = service.getState();
        service.loadState(loaded);
        loaded.activeRun.player.hp = 3;
        assertEquals(100, service.readCheckpoint().orElseThrow().player.hp);
    }

    @Test
    void invalidSaveAndForeignReloadDoNotDestroyTheOwnedCheckpoint() {
        MergeHellStateService service = new MergeHellStateService();
        MergeHellState.ActiveRun run = CheckpointCodecTest.freshRun();
        service.claimRun("window", run.runId);
        service.saveCheckpoint("window", run);
        run.player.hp = -1;
        assertThrows(IllegalArgumentException.class, () -> service.saveCheckpoint("window", run));
        assertEquals(100, service.readCheckpoint().orElseThrow().player.hp);
        MergeHellState other = new MergeHellState();
        other.activeRun = CheckpointCodecTest.freshRun();
        service.loadState(other);
        assertEquals(run.runId, service.readCheckpoint().orElseThrow().runId);
        assertTrue(service.ownedByAnotherWindow("other"));
    }

    @Test
    void simultaneousWindowsCannotBothAcquireOwnership() throws Exception {
        MergeHellStateService service = new MergeHellStateService();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger owners = new AtomicInteger();
        for (String owner : new String[]{"A", "B"}) {
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    if (service.claimRun(owner, UUID.randomUUID().toString())) owners.incrementAndGet();
                } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                finally { done.countDown(); }
            });
            thread.setDaemon(true);
            thread.start();
        }
        start.countDown();
        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertEquals(1, owners.get());
    }

    @Test
    void invalidSettlementCannotPartiallyPublishRewardsOrReplaceAnEntrance() {
        MergeHellStateService service = new MergeHellStateService();
        MergeHellState.ActiveRun run = CheckpointCodecTest.freshRun();
        service.claimRun("window", run.runId);
        service.saveCheckpoint("window", run);
        MergeHellState.ActiveRun wrongNext = service.readCheckpoint().orElseThrow();
        wrongNext.mission = 2;
        assertThrows(IllegalArgumentException.class,
                () -> service.settleCampaignMission("window", 0, 250, wrongNext, 999));
        assertTrue(service.getState().completedMissions.isEmpty());
        assertEquals(0, service.getState().refactorPoints);
        assertEquals(run.mission, service.readCheckpoint().orElseThrow().mission);
        assertEquals(run.runId, service.readCheckpoint().orElseThrow().runId);
    }
}
