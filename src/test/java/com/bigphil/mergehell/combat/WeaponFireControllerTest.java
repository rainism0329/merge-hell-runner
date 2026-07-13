package com.bigphil.mergehell.combat;

import com.bigphil.mergehell.model.Projectile;
import com.bigphil.mergehell.progression.RunBuild;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponFireControllerTest {

    @Test
    void forcePushProducesSymmetricFivePelletSpread() {
        RunBuild build = new RunBuild(WeaponId.FORCE_PUSH);
        FireRequest request = new FireRequest(WeaponId.FORCE_PUSH,
                100, 200, 1, build.effectiveStats(), false, new Random(1));

        List<Projectile> volley = new WeaponFireController().fire(request);

        assertEquals(5, volley.size());
        assertEquals(-volley.get(0).getVy(), volley.get(4).getVy(), 0.0001);
        assertEquals(-volley.get(1).getVy(), volley.get(3).getVy(), 0.0001);
    }

    @Test
    void fireRetainsAllWeaponStateOnProjectile() {
        CombatStats stats = new CombatStats(20, 10, 1, 8, 0,
                3, 7.5, 1, 2);
        FireRequest request = new FireRequest(WeaponId.COMMIT_CANNON,
                100, 200, 1, stats, false, new Random(1));

        Projectile projectile = new WeaponFireController().fire(request).get(0);

        assertEquals(WeaponId.COMMIT_CANNON, projectile.getWeapon());
        assertEquals(40, projectile.getDamage());
        assertTrue(projectile.isCritical());
        assertEquals(3, projectile.getRemainingPierces());
        assertEquals(7.5, projectile.getKnockback(), 0.0001);
        assertEquals(2, projectile.getRemainingRicochets());
    }

    @Test
    void overclockAddsDamageAndTwoPierces() {
        CombatStats stats = new CombatStats(10, 10, 1, 8, 0,
                1, 0, 0, 0);
        FireRequest request = new FireRequest(WeaponId.COMMIT_CANNON,
                100, 200, 1, stats, true, new Random(1));

        Projectile projectile = new WeaponFireController().fire(request).get(0);

        assertEquals(14, projectile.getDamage());
        assertEquals(3, projectile.getRemainingPierces());
    }

    @Test
    void fireRequestRejectsInvalidFacing() {
        RunBuild build = new RunBuild(WeaponId.COMMIT_CANNON);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new FireRequest(WeaponId.COMMIT_CANNON,
                        100, 200, 0, build.effectiveStats(), false, new Random(1)));

        assertEquals("facing must be -1 or 1", error.getMessage());
    }
}
