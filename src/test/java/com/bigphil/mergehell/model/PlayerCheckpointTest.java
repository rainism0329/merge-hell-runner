package com.bigphil.mergehell.model;

import com.bigphil.mergehell.combat.WeaponId;
import com.bigphil.mergehell.progression.RunBuild;
import com.bigphil.mergehell.progression.UpgradeCatalog;
import com.bigphil.mergehell.progression.UpgradeId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlayerCheckpointTest {
    @Test
    void restoreKeepsResourcesSpentShieldChargesAndDefensivelyCopiedAmmo() {
        Player source = new Player(100, 450);
        RunBuild build = new RunBuild(WeaponId.FORCE_PUSH);
        build.apply(UpgradeCatalog.definition(UpgradeId.SHIELD_REBOOT));
        source.bindRunBuild(build);
        source.takeDamage(999);
        source.useBomb(); source.loseLife();
        source.giveWeapon(WeaponType.HEAVY, 9);
        source.beginNextLevel(300, 450);
        var snapshot = source.checkpoint();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.ammoReserve().clear());

        Player restored = new Player(20, 50);
        restored.setDebugMode(true);
        restored.restoreCheckpoint(snapshot, build, 300, 450);
        assertEquals(snapshot, restored.checkpoint());
        assertFalse(restored.isDebugMode());
        assertEquals(0, restored.getShieldRebootsRemaining());
        assertEquals(25, restored.getHp());
        assertEquals(2, restored.getLives());
        assertEquals(2, restored.getBombs());
        assertEquals(9, restored.getWeaponAmmo());
        source.giveWeapon(WeaponType.HEAVY, 2);
        assertEquals(9, snapshot.ammoReserve().get(WeaponType.HEAVY));
    }

    @Test
    void invalidBuildOrCoordinatesLeaveTheExistingPlayerUntouched() {
        Player original = new Player(100, 450);
        original.takeDamage(20);
        var before = original.checkpoint();
        Player force = new Player(200, 450);
        force.bindRunBuild(new RunBuild(WeaponId.FORCE_PUSH));
        var forceSnapshot = force.checkpoint();
        assertThrows(IllegalArgumentException.class, () -> original.restoreCheckpoint(
                forceSnapshot, new RunBuild(WeaponId.COMMIT_CANNON), 300, 450));
        assertThrows(IllegalArgumentException.class, () -> original.restoreCheckpoint(
                before, original.getRunBuild(), Double.NaN, 450));
        assertEquals(before, original.checkpoint());
        assertEquals(100, original.getX());
    }

    @Test
    void restoreDoesNotRerollFutureCriticalHits() {
        Player source = new Player(100, 450);
        source.setCombatSeed(619L);
        List<Projectile> discarded = new ArrayList<>();
        for (int i = 0; i < 100; i++) tick(source, discarded);
        source.beginNextLevel(300, 450);
        var checkpoint = source.checkpoint();
        Player restored = new Player(0, 0);
        restored.restoreCheckpoint(checkpoint, source.getRunBuild(), 300, 450);
        List<Projectile> a = new ArrayList<>(), b = new ArrayList<>();
        for (int i = 0; i < 500; i++) { tick(source, a); tick(restored, b); }
        assertEquals(a.stream().map(Projectile::getSpec).toList(), b.stream().map(Projectile::getSpec).toList());
    }

    @Test
    void activeMovesAndLabCannotBecomeSafeCheckpoints() {
        Player player = new Player(100, 450);
        player.dash();
        assertThrows(IllegalStateException.class, player::checkpoint);
        player.beginNextLevel(300, 450);
        assertNotNull(player.checkpoint());
        player.setDebugMode(true);
        assertThrows(IllegalStateException.class, player::checkpoint);
    }

    private static void tick(Player player, List<Projectile> shots) {
        player.update(false, false, false, true, 480, 10000, shots, List.of());
    }
}
