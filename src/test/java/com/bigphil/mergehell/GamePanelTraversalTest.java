package com.bigphil.mergehell;

import com.bigphil.mergehell.model.EntityType;
import com.bigphil.mergehell.model.ObstacleManager;
import com.bigphil.mergehell.model.Platform;
import com.bigphil.mergehell.model.Projectile;
import com.bigphil.mergehell.model.ProjectileType;
import com.bigphil.mergehell.persistence.MergeHellState;
import com.bigphil.mergehell.persistence.MergeHellStateService;
import com.bigphil.mergehell.world.TraversalEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GamePanelTraversalTest {
    private final List<HeapGameHarness> scenes = new ArrayList<>();
    private MergeHellState original;

    @BeforeEach void isolate() {
        original = MergeHellStateService.getInstance().getState();
        MergeHellStateService.getInstance().loadState(new MergeHellState());
    }

    @AfterEach void restore() {
        scenes.forEach(HeapGameHarness::close);
        MergeHellStateService.getInstance().loadState(original);
    }

    @Test void realWalkingAndDashInputsCreateDifferentWaterContactEffects() throws Exception {
        var h = scene();
        h.place(350, 450);
        assertTrue(h.player().isGrounded());
        assertFalse(environment(h).snapshot().ripples().isEmpty());
        h.key("RIGHT"); h.quietTicks(7); h.key("RIGHT_R"); h.tick();
        assertTrue(h.player().getX() > 375);
        assertTrue(environment(h).snapshot().ripples().stream().anyMatch(ripple -> ripple.strength() < 1));
        h.key("DASH"); h.quietTicks(8);
        assertTrue(environment(h).snapshot().ripples().stream().anyMatch(ripple -> ripple.strength() > 1.5));
        assertFalse(environment(h).snapshot().droplets().isEmpty());
        assertEquals(480, h.player().getY() + h.player().getBounds().height, 0.001);
    }

    @Test void actualBridgeOverWaterSupportsPlayerWithoutWaterFootprints() throws Exception {
        var h = scene();
        var water = environment(h).snapshot().water().get(0);
        h.place(520, 230);
        h.quietTicks(60);
        assertTrue(water.x() <= h.player().getBounds().getCenterX()
                && water.x() + water.width() >= h.player().getBounds().getCenterX());
        assertTrue(h.player().isGrounded());
        assertEquals(360, h.player().getY() + h.player().getBounds().height, 0.001);
        assertTrue(environment(h).snapshot().ripples().isEmpty());
        assertTrue(environment(h).snapshot().droplets().isEmpty());
        double standingX = h.player().getX();
        assertTrue(terrain(h).stream().anyMatch(platform -> platform.y == 360
                && platform.x <= standingX && platform.x + platform.width > standingX));
    }

    @Test void pauseFreezesWaterAndRestartDiscardsBothEffectsAndPendingRewards() throws Exception {
        var h = scene(); h.place(350, 450);
        h.key("PAUSE_P"); h.tick();
        var before = environment(h);
        var frozen = before.snapshot();
        double playerX = h.player().getX();
        h.key("RIGHT"); h.key("DASH"); h.ticks(40);
        assertEquals(GameState.PAUSED, h.state());
        assertSame(frozen, before.snapshot());
        assertEquals(playerX, h.player().getX());
        pending(h).addLast(new TraversalEnvironment.BreakEvent(999, TraversalEnvironment.PropKind.SUPPLY, 400, 450, 0));
        h.key("NEW_RANKED_RUN"); h.tick();
        h.key("START"); h.tick();
        assertEquals(GameState.RUNNING, h.state());
        assertNotSame(before, environment(h));
        assertTrue(environment(h).snapshot().ripples().isEmpty());
        assertTrue(environment(h).snapshot().droplets().isEmpty());
        assertTrue(pending(h).isEmpty());
        assertEquals(3, h.player().getBombs());
        assertEquals(0, h.get("level"));
    }

    @Test void continueRebuildsIdenticalEntranceGeometryAndUncollectedEntranceProps() throws Exception {
        var h = scene();
        var originalEnvironment = environment(h);
        var water = originalEnvironment.snapshot().water();
        var platforms = geometry(originalEnvironment.platforms());
        var props = originalEnvironment.snapshot().props();
        h.place(350, 450);
        h.shots().add(shotAt(props.get(0)));
        h.quietTicks(1);
        assertTrue(environment(h).snapshot().props().stream().noneMatch(prop -> prop.id() == props.get(0).id()));
        assertFalse(environment(h).snapshot().ripples().isEmpty());
        h.key("PAUSE_P"); h.tick(); h.key("MENU"); h.tick(); h.key("UPGRADE_REROLL"); h.tick();
        assertEquals(GameState.RUNNING, h.state());
        assertEquals(1, h.get("level"));
        assertNotSame(originalEnvironment, environment(h));
        assertEquals(water, environment(h).snapshot().water());
        assertEquals(platforms, geometry(environment(h).platforms()));
        assertEquals(props, environment(h).snapshot().props(), "Continue restores the saved entrance, not a mid-route collectible state");
        assertTrue(environment(h).snapshot().ripples().isEmpty());
        assertTrue(environment(h).snapshot().droplets().isEmpty());
        assertTrue(pending(h).isEmpty());
    }

    @Test void realSupplyShotsGrantOneBombAndFifteenHealthOnlyOnce() throws Exception {
        var h = scene();
        var supply = prop(h, TraversalEnvironment.PropKind.SUPPLY);
        h.place(supply.x() + 90, 450);
        h.enemies().clearHostiles();
        h.player().setInvincibleTimer(0); h.player().setShieldTimer(0); h.player().takeDamage(40);
        int beforeHp = h.player().getHp(); int beforeBombs = h.player().getBombs();
        Projectile first = shotAt(supply); Projectile duplicate = shotAt(supply);
        h.shots().add(first); h.shots().add(duplicate); h.tick();
        assertEquals(beforeBombs + 1, h.player().getBombs());
        assertEquals(beforeHp + 15, h.player().getHp());
        assertTrue(first.isDead());
        assertTrue(environment(h).snapshot().props().stream().noneMatch(prop -> prop.id() == supply.id()));
        h.shots().add(shotAt(supply)); h.quietTicks(2);
        assertEquals(beforeBombs + 1, h.player().getBombs());
        assertEquals(beforeHp + 15, h.player().getHp());
        assertTrue(pending(h).isEmpty());
    }

    @Test void realCapacitorShotRewardsNearbyKillsWithoutDamagingTheNearbyPlayer() throws Exception {
        var h = scene();
        var capacitor = prop(h, TraversalEnvironment.PropKind.CAPACITOR);
        h.place(capacitor.x() - 100, 450); h.enemies().clearHostiles();
        var enemy = new ObstacleManager.Enemy(capacitor.x() + 65, 440, EntityType.BUG, 43L);
        h.enemies().getEnemies().add(enemy);
        int hp = h.player().getHp(); int score = h.context().score;
        int xp = h.panel.getSession().buildProgress().currentXp();
        h.shots().add(shotAt(capacitor)); h.tick();
        assertTrue(enemy.isDead());
        assertEquals(score + 100, h.context().score);
        assertEquals(xp + 7, h.panel.getSession().buildProgress().currentXp());
        assertEquals(hp, h.player().getHp());
        h.shots().add(shotAt(capacitor)); h.quietTicks(6);
        assertEquals(score + 100, h.context().score);
        assertEquals(xp + 7, h.panel.getSession().buildProgress().currentXp());
    }

    @Test void blastTriggeredUpgradeDefersFollowingSupplyAndFreezesWorldUntilChoice() throws Exception {
        var h = scene();
        var capacitor = prop(h, TraversalEnvironment.PropKind.CAPACITOR);
        var supply = prop(h, TraversalEnvironment.PropKind.SUPPLY);
        h.place(800, 450); h.enemies().clearHostiles();
        h.panel.getSession().awardBuildXp(99);
        h.player().setInvincibleTimer(0); h.player().setShieldTimer(0); h.player().takeDamage(40);
        int hp = h.player().getHp(); int bombs = h.player().getBombs();
        var target = new ObstacleManager.Enemy(capacitor.x() + 45, 440, EntityType.BUG, 54L);
        h.enemies().getEnemies().add(target);
        h.shots().add(shotAt(capacitor)); h.shots().add(shotAt(supply)); h.tick();
        assertTrue(target.isDead());
        assertEquals(GameState.UPGRADE_SELECTION, h.state());
        assertEquals(1, pending(h).size(), "The later supply waits for the selected upgrade");
        assertEquals(bombs, h.player().getBombs()); assertEquals(hp, h.player().getHp());
        assertTrue(environment(h).snapshot().props().stream().noneMatch(prop -> prop.id() == supply.id()),
                "The supply was hit in the same collision step, but its reward is still queued");
        var frozen = environment(h).snapshot();
        double x = h.player().getX();
        h.ticks(20);
        assertSame(frozen, environment(h).snapshot()); assertEquals(x, h.player().getX());
        assertEquals(1, pending(h).size()); assertEquals(bombs, h.player().getBombs());
        h.key("UPGRADE_1"); h.quietTicks(10);
        assertEquals(GameState.RUNNING, h.state());
        assertEquals(bombs + 1, h.player().getBombs());
        assertTrue(h.player().getHp() >= hp + 15, "The chosen upgrade may itself also repair health");
        assertTrue(pending(h).isEmpty());
    }

    private HeapGameHarness scene() throws Exception {
        var h = new HeapGameHarness(); scenes.add(h); return h;
    }

    private static TraversalEnvironment environment(HeapGameHarness h) throws Exception {
        return (TraversalEnvironment) h.get("environment");
    }

    private static TraversalEnvironment.PropView prop(HeapGameHarness h, TraversalEnvironment.PropKind kind) throws Exception {
        return environment(h).snapshot().props().stream().filter(prop -> prop.kind() == kind).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked") private static ArrayDeque<TraversalEnvironment.BreakEvent> pending(HeapGameHarness h) throws Exception {
        return (ArrayDeque<TraversalEnvironment.BreakEvent>) h.get("environmentEvents");
    }

    @SuppressWarnings("unchecked") private static List<Platform> terrain(HeapGameHarness h) throws Exception {
        return (List<Platform>) h.get("combatPlatforms");
    }

    private static Projectile shotAt(TraversalEnvironment.PropView prop) {
        return new Projectile(prop.x(), prop.y() + 10, 0, 0, ProjectileType.COMMIT);
    }

    private static List<String> geometry(List<Platform> platforms) {
        return platforms.stream().map(platform -> platform.x + ":" + platform.y + ":"
                + platform.width + ":" + platform.height + ":" + platform.style).toList();
    }
}
