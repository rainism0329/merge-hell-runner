package com.bigphil.mergehell;

import com.bigphil.mergehell.combat.*;
import com.bigphil.mergehell.model.*;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Integration contracts between authored body parts, real projectile sweeps and confirmed feedback. */
class ChapterMultipartCollisionTest {
    private static final int WIDTH = 960, HEIGHT = 600, GROUND = 480;

    @Test void touchingAPersistentSpecialistDoesNotRepeatDamageFeedbackDuringInvulnerability() {
        Fixture f=new Fixture(2);
        f.enemies.getEnemies().add(new ObstacleManager.Enemy(100,402,EntityType.WARDEN,33));
        f.player.setInvincibleTimer(90);f.context.combo=7;
        f.process();assertEquals(100,f.player.getHp());assertEquals(7,f.context.combo);assertEquals(0,f.context.flashTimer);
        f.player.setInvincibleTimer(0);f.process();assertEquals(76,f.player.getHp());
        assertFalse(f.enemies.getEnemies().get(0).isDead());f.context.flashTimer=0;f.context.shakeTimer=0;
        f.process();assertEquals(76,f.player.getHp());assertEquals(0,f.context.flashTimer);assertEquals(0,f.context.shakeTimer);
    }

    @Test void finalHitThroughAnAppendageReportsTheAcceptedCoreKillInsteadOfInventingPartDamage() {
        Fixture f=new Fixture(2);
        var core=f.boss.getParts().stream().filter(p->p.id().equals("core")).findFirst().orElseThrow();
        while(f.boss.getHp()>5) {
            f.boss.damageAt(core.bounds().rectangle(), Math.max(1,(f.boss.getHp()-5)/2));
        }
        int remaining=f.boss.getHp();
        var arm=f.boss.getParts().get(0);
        assertTrue(arm.hp()>80);
        f.shots.add(sweep(arm,WeaponId.GARBAGE_COLLECTOR));f.process();
        assertEquals(0,f.boss.getHp());
        assertEquals(remaining,f.impacts().get(0).actualDamage());
        assertEquals(CombatEvent.BossPart.CORE,f.impacts().get(0).part());
        assertTrue(f.impacts().get(0).destroyed());
    }

    @Test void everyWeaponCanSweepEachPhysicalComponentAndCoreOfAllThreeBosses() {
        int cases = 0;
        for (int chapter = 2; chapter <= 4; chapter++) for (WeaponId weapon : WeaponId.values()) {
            for (int index = 0; index < 3; index++) {
                Fixture f = new Fixture(chapter);
                Boss.PartView before = f.boss.getParts().get(index);
                Projectile shot = sweep(before, weapon);
                int coreHp = f.boss.getHp();
                f.shots.add(shot);
                f.process();
                assertTrue(shot.isDead(), chapter + " " + weapon + " " + before.id());
                assertFalse(shot.getBounds().intersects(before.bounds().rectangle()), "Endpoint misses; the sweep must hit");
                assertTrue(f.boss.getHp() < coreHp);
                var hits = f.impacts();
                assertEquals(1, hits.size());
                var impact = hits.get(0);
                assertEquals(index, impact.partId());
                assertEquals(before.id().equals("core") ? CombatEvent.BossPart.CORE
                        : before.id().equals("heat-vent") ? CombatEvent.BossPart.BODY : CombatEvent.BossPart.NODE, impact.part());
                assertEquals(weapon, impact.weapon());
                assertEquals(CombatEvent.DamageKind.DIRECT, impact.source());
                assertEquals(coreHp - f.boss.getHp(), f.damages().get(0).damage());
                f.process();
                assertEquals(1, f.impacts().size(), "One projectile cannot hit a second component on the next frame");
                cases++;
            }
        }
        assertEquals(54, cases);
    }

    @Test void eachWeaponCanDamageTheMechanicallyExposedCoreWithoutTargetingRemovedParts() {
        for (int chapter = 2; chapter <= 4; chapter++) for (WeaponId weapon : WeaponId.values()) {
            Fixture f = new Fixture(chapter);
            openCore(f.boss);
            Boss.PartView core = f.boss.getParts().stream().filter(p -> p.id().equals("core")).findFirst().orElseThrow();
            assertTrue(core.weak());
            int hp = f.boss.getHp();
            Projectile shot = sweep(core, weapon);
            int raw = shot.getDamage();
            f.shots.add(shot); f.process();
            assertTrue(hp - f.boss.getHp() >= raw * 2, chapter + " " + weapon);
            assertEquals(CombatEvent.BossPart.CORE, f.impacts().get(0).part());
        }
    }

    @Test void droneShotsHitRealPartsAndKeepSupportAttributionAcrossEveryChapter() {
        for (int chapter = 2; chapter <= 4; chapter++) for (int index = 0; index < 3; index++) {
            Fixture f = new Fixture(chapter);
            var part = f.boss.getParts().get(index);
            double cx = part.bounds().x() + part.bounds().width() / 2;
            double cy = part.bounds().y() + part.bounds().height() / 2;
            Projectile drone = Projectile.drone(cx, cy, new ProjectileSpec(WeaponId.RAPID_CI, 30, 0, 0,
                    false, 1, 0, 0));
            f.shots.add(drone); f.process();
            assertTrue(drone.isDead());
            assertEquals(1, f.impacts().size());
            assertEquals(index, f.impacts().get(0).partId());
            assertEquals(CombatEvent.DamageKind.DRONE, f.impacts().get(0).source());
            assertEquals(CombatEvent.DamageKind.DRONE, f.damages().get(0).kind());
            assertTrue(f.impacts().get(0).rootEventId() > 0);
        }
    }

    @Test void bulletsPassThroughTheEmptySpaceInsideEachBroadBossRectangle() {
        for (int chapter = 2; chapter <= 4; chapter++) {
            Fixture f = new Fixture(chapter);
            double x = f.boss.getX() + f.boss.getWidth() / 2.0 - 15;
            double y = f.boss.getY() + 3;
            Projectile shot = new Projectile(x, y, new ProjectileSpec(WeaponId.COMMIT_CANNON, 25,
                    1, 0, false, 0, 0, 0));
            assertTrue(f.boss.getBounds().contains(shot.getBounds()));
            assertFalse(f.boss.canHit(shot.getBounds()));
            int hp = f.boss.getHp();
            f.shots.add(shot); f.process();
            assertFalse(shot.isDead(), "Decoration is not an invisible hitbox");
            assertTrue(f.shots.contains(shot));
            assertEquals(hp, f.boss.getHp());
            assertTrue(f.impacts().isEmpty());
        }
    }

    @Test void componentBreakFeedbackUsesItsRemainingHealthRatherThanTransferredCoreDamage() {
        for (int chapter = 2; chapter <= 4; chapter++) {
            Fixture f = new Fixture(chapter);
            var component = f.boss.getParts().get(0);
            f.boss.damageAt(component.bounds().rectangle(), component.hp() - 1);
            var before = f.boss.getParts().get(0);
            assertEquals(1, before.hp());
            f.shots.add(new Projectile(before.bounds().x() + 8, before.bounds().y() + 18,
                    new ProjectileSpec(WeaponId.GARBAGE_COLLECTOR, 80, 0, 0, false, 0, 0, 0)));
            f.process();
            var hit = f.impacts().get(0);
            assertEquals(CombatEvent.BossPart.NODE, hit.part());
            assertEquals(1, hit.actualDamage(), "Only one point remained on this component");
            assertEquals(0, hit.remainingHp());
            assertEquals(before.maxHp(), hit.maxHp());
            assertTrue(hit.destroyed());
            assertTrue(f.boss.getParts().get(0).destroyed());
        }
    }

    @Test void meleeHitsAComponentOnlyOncePerSwingAndCanHitAgainWithANewSwing() {
        for (int chapter = 2; chapter <= 4; chapter++) {
            Fixture f = new Fixture(chapter);
            var part = f.boss.getParts().get(0);
            f.player.setX(part.bounds().x() - 48);
            f.player.setY(part.bounds().y() + 20);
            f.player.setInvincibleTimer(1000);
            int before = part.hp();
            f.player.melee();
            for (int i = 0; i < 8; i++) f.process();
            assertEquals(before - 50, f.boss.getParts().get(0).hp());
            assertEquals(1, f.impacts().size());
            assertEquals(CombatEvent.DamageKind.MELEE, f.impacts().get(0).source());
            for (int i = 0; i < 32; i++) {
                f.player.update(false, false, false, false, GROUND, WIDTH, new ArrayList<>(), List.of());
                f.player.setX(part.bounds().x() - 48); f.player.setY(part.bounds().y() + 20);
                f.process();
            }
            f.player.melee(); f.process();
            assertEquals(before - 100, f.boss.getParts().get(0).hp());
            assertEquals(2, f.impacts().size());
        }
    }

    @Test void wardenShieldDamageAndLethalOverkillPublishOnlyAcceptedEnemyHealthLoss() {
        Fixture f = new Fixture(2);
        var warden = new ObstacleManager.Enemy(380, 350, EntityType.WARDEN, 80L);
        f.enemies.getEnemies().add(warden);
        f.shots.add(new Projectile(370, 370, new ProjectileSpec(WeaponId.COMMIT_CANNON, 25,
                15, 0, false, 0, 0, 0)));
        f.process();
        assertEquals(EntityType.WARDEN.maxHp - 9, warden.getHp());
        assertEquals(9, f.damages().get(0).damage());
        warden.takeDamage(warden.getHp() - 3);
        f.events.clear(); f.shots.clear();
        f.shots.add(new Projectile(370, 370, new ProjectileSpec(WeaponId.GARBAGE_COLLECTOR, 80,
                15, 0, false, 0, 0, 0)));
        f.process();
        assertTrue(warden.isDead());
        assertEquals(3, f.damages().get(0).damage(), "Lethal damage cannot exceed the remaining three HP");
        assertEquals(1, f.events.stream().filter(CombatEvent.EnemyKilled.class::isInstance).count());
    }

    @Test void warningVolumesCannotDamageThePlayerBeforeTheyBecomeActive() {
        for (int chapter = 2; chapter <= 4; chapter++) {
            Fixture f = new Fixture(chapter);
            for (int i = 0; i < 1200 && f.boss.getAttackTelegraphs().isEmpty(); i++) f.advanceBoss();
            var warning = f.boss.getAttackTelegraphs().stream().filter(t -> t.warningTicks() > 0).findFirst().orElseThrow();
            f.player.setX(warning.bounds().x() + Math.max(0, warning.bounds().width() / 2 - 15));
            f.player.setY(warning.bounds().y() + Math.max(0, warning.bounds().height() / 2 - 15));
            f.player.setInvincibleTimer(0);
            int hp = f.player.getHp();
            f.process();
            assertEquals(hp, f.player.getHp(), "A visible warning is not yet a damaging zone");
        }
    }

    @Test void activeStampAndTendrilDamageOnlyThePublishedBounds() {
        for (int chapter : new int[]{2, 4}) {
            Fixture f = new Fixture(chapter);
            for (int i = 0; i < 1800 && f.boss.getActiveHazards().isEmpty(); i++) f.advanceBoss();
            var active = f.boss.getActiveHazards().get(0);
            f.player.setX(40); f.player.setY(100); f.player.setInvincibleTimer(0);
            int hp = f.player.getHp();
            f.process();
            assertEquals(hp, f.player.getHp());
            f.player.setX(active.bounds().x() + Math.max(0, active.bounds().width() / 2 - 15));
            f.player.setY(active.bounds().y() + Math.max(0, active.bounds().height() / 2 - 15));
            f.process();
            assertEquals(hp - active.damage(), f.player.getHp());
        }
    }

    @Test void groundedSiegeChargeUsesItsActualMovingContactBody() {
        Fixture f = new Fixture(3);
        for (int i = 0; i < 1800 && !f.boss.isDashing(); i++) f.advanceBoss();
        assertTrue(f.boss.isDashing());
        var body = f.boss.getContactBounds().get(0);
        f.player.setX(body.x() - 40); f.player.setY(body.y() + 10); f.player.setInvincibleTimer(0);
        int hp = f.player.getHp(); f.process();
        assertEquals(hp, f.player.getHp());
        f.player.setX(body.x() + 15); f.player.setY(body.y() + 20); f.process();
        assertEquals(hp - 35, f.player.getHp());
    }

    private static Projectile sweep(Boss.PartView target, WeaponId weapon) {
        var stats = WeaponCatalog.definition(weapon).baseStats().withCriticalChance(0);
        var volley = new WeaponFireController().fire(new FireRequest(weapon, 0, 0, 1, stats, false, new Random(4)));
        Projectile template = volley.get(volley.size() / 2);
        double x = target.bounds().x() + (target.bounds().width() - template.getBounds().width) / 2;
        return new Projectile(x, target.bounds().y() - 70,
                template.getSpec().withVelocity(0, target.bounds().height() + 90));
    }

    private static void openCore(Boss boss) {
        var first = boss.getParts().get(0);
        boss.damageAt(first.bounds().rectangle(), first.hp());
        var second = boss.getParts().get(1);
        boss.damageAt(second.bounds().rectangle(), boss.getBossLevel() == 3 ? first.maxHp() : second.hp());
    }

    private static final class Fixture {
        final List<CombatEvent> events = new ArrayList<>();
        final CollisionSystem collision = new CollisionSystem(events::add);
        final CollisionSystem.Context context = new CollisionSystem.Context();
        final ObstacleManager enemies = new ObstacleManager(7);
        final List<Projectile> shots = new ArrayList<>();
        final Player player = new Player(100, 450);
        final Boss boss;
        Fixture(int chapter) {
            boss = new Boss("Multipart", 40_000, "!", 900, chapter, 8);
            boss.previewArrival(1, GROUND); boss.activate(); advanceBoss();
        }
        void advanceBoss() {
            boss.update(enemies, GROUND, 280, 450, enemies.getEnemyBullets());
            enemies.getEnemyBullets().clear(); enemies.getEnemies().clear();
        }
        void process() {
            collision.process(context, shots, enemies, boss, player, GameState.BOSS_FIGHT,
                    WIDTH, HEIGHT, 0, new ArrayList<>(), new ArrayList<>(), ignored -> {});
        }
        List<CombatEvent.BossImpact> impacts() { return events.stream().filter(CombatEvent.BossImpact.class::isInstance).map(CombatEvent.BossImpact.class::cast).toList(); }
        List<CombatEvent.DamageDealt> damages() { return events.stream().filter(CombatEvent.DamageDealt.class::isInstance).map(CombatEvent.DamageDealt.class::cast).toList(); }
    }
}
