package com.bigphil.mergehell.model;

import com.bigphil.mergehell.combat.WeaponId;
import com.bigphil.mergehell.combat.CombatRandom;
import com.bigphil.mergehell.engine.EntityLimits;
import com.bigphil.mergehell.engine.ProjectileBuffer;
import com.bigphil.mergehell.progression.RunBuild;
import com.bigphil.mergehell.progression.UpgradeCatalog;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlayerProjectileBudgetTest {
    @Test void allSixBaseUpgradedAndEvolvedWeaponsWaitForTheWholeVolleyWithoutChangingCombatState() {
        for (WeaponId weapon : WeaponId.values()) for (int tier = 0; tier < 3; tier++) {
            RunBuild build = build(weapon, tier);
            Player player = player(build);
            Player control = player(build(weapon, tier));
            ProjectileBuffer buffer = new ProjectileBuffer(EntityLimits.MAX_PROJECTILES);
            int pellets = build.effectiveStats().pellets();
            fill(buffer, buffer.capacity() - pellets + 1);
            List<Projectile> originals = List.copyOf(buffer);
            long randomState = randomState(player);
            for (int tick = 0; tick < 15; tick++) step(player, buffer);
            String context = weapon + " tier " + tier;
            assertEquals(originals, buffer, context);
            assertEquals(0, player.getShotSequence(), context);
            assertNull(player.getLastFiredWeaponId(), context);
            assertEquals(0, field(player, "cooldown"), context);
            assertEquals(0, player.getRapidHeat(), context);
            assertEquals(randomState, randomState(player), context);
            assertTrue(buffer.rejectedProjectiles() >= pellets, context);
            buffer.clear();
            step(player, buffer);
            assertEquals(pellets, buffer.size(), context);
            assertEquals(1, player.getShotSequence(), context);
            assertEquals(weapon, player.getLastFiredWeaponId(), context);
            List<Projectile> expected = new ArrayList<>();
            for (int tick = 0; tick < 15 && expected.isEmpty(); tick++) step(control, expected);
            for (int i = 0; i < pellets; i++) {
                assertEquals(expected.get(i).getDamage(), buffer.get(i).getDamage(), context);
                assertEquals(expected.get(i).isCritical(), buffer.get(i).isCritical(), context);
                assertEquals(expected.get(i).getVy(), buffer.get(i).getVy(), 1e-9, context);
                assertEquals(expected.get(i).getRemainingPierces(), buffer.get(i).getRemainingPierces(), context);
            }
        }
    }

    @Test void deniedTemporaryWeaponsKeepAmmoAndOnlyConsumeOneRoundAfterAdmission() {
        for (WeaponType weapon : WeaponType.values()) {
            Player player = new Player(100, 450);
            player.giveWeapon(weapon, 2);
            ProjectileBuffer buffer = new ProjectileBuffer(EntityLimits.MAX_PROJECTILES);
            fill(buffer, buffer.capacity());
            for (int tick = 0; tick < 15; tick++) step(player, buffer);
            assertEquals(2, player.getWeaponAmmo(), weapon.name());
            assertEquals(0, player.getShotSequence(), weapon.name());
            assertEquals(0, field(player, "cooldown"), weapon.name());
            assertEquals(weapon, player.getWeapon());
            buffer.clear();
            step(player, buffer);
            assertEquals(1, player.getWeaponAmmo(), weapon.name());
            assertEquals(1, player.getShotSequence(), weapon.name());
            assertFalse(buffer.isEmpty(), weapon.name());
        }
    }

    @Test void evolvedRapidHeatBurstIsAtomicAndHeatDoesNotResetWhenItCannotFit() {
        Player player = player(build(WeaponId.RAPID_CI, 2));
        List<Projectile> unlimited = new ArrayList<>();
        for (int tick = 0; tick < 1000 && player.getShotSequence() < 7; tick++) step(player, unlimited);
        assertEquals(7, player.getShotSequence());
        assertEquals(98, player.getRapidHeat());
        ProjectileBuffer buffer = new ProjectileBuffer(EntityLimits.MAX_PROJECTILES);
        fill(buffer, buffer.capacity() - 2);
        for (int tick = 0; tick < 40; tick++) step(player, buffer);
        assertEquals(7, player.getShotSequence());
        assertEquals(98, player.getRapidHeat());
        assertEquals(buffer.capacity() - 2, buffer.size());
        assertTrue(buffer.rejectedProjectiles() >= 3);
        buffer.clear();
        step(player, buffer);
        assertEquals(3, buffer.size());
        assertEquals(8, player.getShotSequence());
        assertEquals(28, player.getRapidHeat());
        assertTrue((int) field(player, "cooldown") >= 14);
    }

    @Test void chargedBeamFiresOnTheFirstFreeTickWithoutChargingAgain() {
        Player player = player(new RunBuild(WeaponId.REFACTOR_BEAM));
        ProjectileBuffer buffer = new ProjectileBuffer(1);
        fill(buffer, 1);
        for (int tick = 0; tick < 40; tick++) step(player, buffer);
        assertEquals(10, player.getBeamChargeTicks());
        assertEquals(0, player.getShotSequence());
        buffer.remove(0);
        step(player, buffer);
        assertEquals(1, player.getShotSequence());
        assertEquals(0, player.getBeamChargeTicks());
        assertEquals(WeaponId.REFACTOR_BEAM, buffer.get(0).getWeapon());
    }

    @Test void sudoAndDebugStillRespectTheProjectileBudgetWithoutFalseFeedback() {
        for (boolean debug : new boolean[]{false, true}) {
            Player player = new Player(100, 450);
            player.setDebugMode(debug);
            player.giveWeapon(WeaponType.HEAVY, 2);
            player.setSudoTimer(100);
            ProjectileBuffer buffer = new ProjectileBuffer(5);
            fill(buffer, 1);
            step(player, buffer);
            assertEquals(0, player.getShotSequence());
            assertEquals(2, player.getWeaponAmmo());
            assertEquals(0, field(player, "cooldown"));
            buffer.clear();
            step(player, buffer);
            assertEquals(5, buffer.size());
            assertEquals(WeaponId.FORCE_PUSH, player.getLastFiredWeaponId());
            assertEquals(2, player.getWeaponAmmo());
        }
    }

    private static RunBuild build(WeaponId weapon, int tier) {
        RunBuild build = new RunBuild(weapon);
        if (tier > 0) for (var upgrade : UpgradeCatalog.all()) {
            if (upgrade.isWeaponRelevant(weapon)) for (int i = 0; i < upgrade.maxRank(); i++) build.apply(upgrade);
        }
        if (tier == 2) assertTrue(build.tryEvolve());
        return build;
    }

    private static Player player(RunBuild build) {
        Player player = new Player(100, 450);
        player.setRunBuild(build);
        player.setCombatSeed(4317);
        return player;
    }

    private static void fill(ProjectileBuffer buffer, int count) {
        for (int i = 0; i < count; i++) buffer.add(new Projectile(i, 0, 8, 0, ProjectileType.COMMIT));
    }

    private static void step(Player player, List<Projectile> buffer) {
        player.update(false, false, false, true, 480, 960, buffer, List.of());
    }

    private static long randomState(Player player) {
        return ((CombatRandom) field(player, "combatRandom")).checkpointState();
    }

    private static Object field(Player player, String name) {
        try {
            var field = Player.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(player);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }
}
