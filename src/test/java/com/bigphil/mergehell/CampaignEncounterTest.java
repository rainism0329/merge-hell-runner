package com.bigphil.mergehell;

import com.bigphil.mergehell.model.EntityType;
import com.bigphil.mergehell.model.ObstacleManager;
import com.bigphil.mergehell.progression.GameDifficulty;
import com.bigphil.mergehell.world.ChapterRouteController;
import com.bigphil.mergehell.world.ExplorationRoute;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Admission/settlement tests. Enemy lifetimes are fixtures; combat difficulty is probed separately. */
class CampaignEncounterTest {
    private static final int FLOOR = 480;

    @TestFactory
    List<DynamicTest> campaignEncounterContracts() {
        List<DynamicTest> tests = new ArrayList<>();
        for (int chapter : new int[]{1, 3, 4}) {
            String prefix = "chapter " + (chapter + 1) + ": ";
            tests.add(DynamicTest.dynamicTest(prefix + "distinct ground and air duties", () ->
                    everyArenaMixesItsOwnGroundAndAirRolesWithinAThreeThreatBudget(chapter)));
            tests.add(DynamicTest.dynamicTest(prefix + "safe real factory admission and complete settlement", () ->
                    allAuthoredArenasAdmitRealEnemiesSafelyAndSettleEveryReinforcement(chapter)));
            tests.add(DynamicTest.dynamicTest(prefix + "delayed or rejected admission cannot clear a room", () ->
                    clearingLivingEnemiesCannotSkipDelayedOrRejectedReinforcements(chapter)));
            tests.add(DynamicTest.dynamicTest(prefix + "existing threats consume the admission budget", () ->
                    fullBudgetCountsEnemiesAlreadyPresentBeforeTheScriptStarts(chapter)));
            tests.add(DynamicTest.dynamicTest(prefix + "safe checkpoint resumes at the next formation", () ->
                    resumedSafeCheckpointSkipsOnlyFinishedArenasAndStartsTheNextOneFromItsFirstBeat(chapter)));
            tests.add(DynamicTest.dynamicTest(prefix + "practice skip cancels future reinforcements", () ->
                    practiceSkipCancelsPendingRolesRatherThanSpawningThemAtTheNextRoom(chapter)));
        }
        return tests;
    }

    private void everyArenaMixesItsOwnGroundAndAirRolesWithinAThreeThreatBudget(int chapter) {
        LevelManager level = new LevelManager(chapter, 42);
        for (int start : starts(chapter)) {
            var arena = level.getBattleAt(start);
            assertNotNull(arena);
            assertEquals(2, arena.waves.length);
            for (var wave : arena.waves) {
                assertTrue(wave.scripted(), "No chapter arena should revert to a same-species dump");
                assertEquals(3, wave.maxConcurrent);
                assertTrue(wave.count >= 3 && wave.count <= 5);
                Set<EntityType> roles = wave.beats.stream().map(b -> b.type()).collect(Collectors.toSet());
                assertTrue(roles.size() >= 3, "Each wave needs distinct responsibilities");
                assertTrue(family(chapter).containsAll(roles), "Keep each chapter's own ecosystem");
                assertTrue(wave.beats.stream().anyMatch(b -> b.altitude() == 0));
                assertTrue(wave.beats.stream().anyMatch(b -> b.altitude() >= 100));
                assertEquals(1, wave.beats.stream().filter(b -> b.delayTicks() == 0).count());
                assertTrue(wave.beats.stream().mapToInt(b -> b.delayTicks()).max().orElseThrow() >= 100,
                        "Give the formation time to unfold rather than admitting all roles together");
            }
        }
    }

    private void allAuthoredArenasAdmitRealEnemiesSafelyAndSettleEveryReinforcement(int chapter) {
        var exploration = new ExplorationRoute(chapter);
        Predicate<Rectangle> supported = groundSupported(chapter);
        for (int start : starts(chapter)) {
            LevelManager level = new LevelManager(chapter, 42);
            var arena = level.getBattleAt(start);
            var manager = new ObstacleManager(42);
            manager.configureCombat(chapter, GameDifficulty.STANDARD);
            manager.setSolids(exploration.solids());
            Rectangle player = new Rectangle(start + 30, FLOOR - 30, 30, 30);
            Rectangle playerClearance = new Rectangle(player);
            playerClearance.grow(110, 32);
            List<Integer> admissions = new ArrayList<>();
            Set<EntityType> admittedRoles = new HashSet<>(), overlappingRoles = new HashSet<>();
            int expected = java.util.Arrays.stream(arena.waves).mapToInt(w -> w.count).sum();
            int maximumAlive = 0;
            level.enterBattle(arena);
            for (int tick = 0; tick < 2400 && level.isInBattle(); tick++) {
                List<ObstacleManager.Enemy> living = living(manager);
                if (level.needsWaveSpawn(living.size())) {
                    level.popWave();
                    level.startNextWaveTimer();
                }
                int now = tick;
                level.advanceEncounter(living.size(), FLOOR, exploration.solids(),
                        living.stream().map(CampaignEncounterTest::body).toList(), player, supported, spawn -> {
                            Rectangle bounds = spawn.bounds();
                            assertTrue(bounds.x >= arena.start && bounds.getMaxX() <= arena.end);
                            assertFalse(playerClearance.intersects(bounds), "Do not materialize on the player");
                            assertTrue(exploration.solids().stream().noneMatch(bounds::intersects));
                            assertTrue(living.stream().map(CampaignEncounterTest::body).noneMatch(bounds::intersects));
                            if (bounds.getMaxY() == FLOOR) assertTrue(supported.test(bounds), "No grounded spawn over a shaft");
                            int previous = manager.getEnemies().size();
                            manager.spawnEnemy(spawn.x(), spawn.y(), spawn.type(), spawn.moveDir());
                            assertEquals(previous + 1, manager.getEnemies().size());
                            assertEquals(bounds, body(manager.getEnemies().get(previous)),
                                    "The real factory must not eject an authored spawn from inside a wall");
                            admissions.add(now);
                            admittedRoles.add(spawn.type());
                            return true;
                        });
                var afterAdmission = living(manager);
                maximumAlive = Math.max(maximumAlive, afterAdmission.size());
                assertTrue(afterAdmission.size() <= 3, "Previously living enemies consume the same budget");
                if (afterAdmission.size() == 3) afterAdmission.forEach(e -> overlappingRoles.add(e.getType()));
                // Test only admission and settlement: retain the first formation, then free one slot per second.
                if (tick > 210 && tick % 60 == 0 && !afterAdmission.isEmpty()) afterAdmission.get(0).setDead(true);
            }
            assertFalse(level.isInBattle(), "Arena " + start + " must not deadlock on delayed or blocked roles");
            assertFalse(level.hasPendingReinforcements());
            assertEquals(expected, admissions.size(), "No reinforcement can disappear when the arena appears empty");
            assertEquals(3, maximumAlive);
            assertTrue(overlappingRoles.size() >= 3, "Different duties must coexist, not just arrive as solo waves");
            assertTrue(admittedRoles.size() >= 3);
            for (int i = 1; i < admissions.size(); i++) assertTrue(admissions.get(i) - admissions.get(i - 1) >= 24,
                    "Freed slots must not dump delayed reinforcements in one frame");
            assertNull(level.getBattleAt(start), "A settled arena must not restart under the player");
        }
    }

    private void clearingLivingEnemiesCannotSkipDelayedOrRejectedReinforcements(int chapter) {
        LevelManager level = new LevelManager(chapter, 42);
        int start = starts(chapter)[0];
        var arena = level.getBattleAt(start);
        level.enterBattle(arena);
        var wave = level.popWave();
        level.startNextWaveTimer();
        int[] rejected = {0};
        Rectangle player = new Rectangle(start + 30, FLOOR - 30, 30, 30);
        for (int tick = 0; tick < 400; tick++) {
            assertFalse(level.needsWaveSpawn(0));
            level.advanceEncounter(0, FLOOR, List.of(), List.of(), player, bounds -> true,
                    spawn -> { rejected[0]++; return false; });
        }
        assertTrue(rejected[0] > 0);
        assertTrue(level.isInBattle());
        assertTrue(level.hasPendingReinforcements());
        assertEquals(1, level.getCurrentWave());
        int[] admitted = {0};
        for (int tick = 0; tick < 200 && level.hasPendingReinforcements(); tick++) {
            assertFalse(level.needsWaveSpawn(0));
            int count = level.advanceEncounter(0, FLOOR, List.of(), List.of(), player, bounds -> true,
                    spawn -> { admitted[0]++; return true; });
            assertTrue(count <= 1);
        }
        assertEquals(wave.count, admitted[0]);
        assertFalse(level.hasPendingReinforcements());
        assertTrue(level.isInBattle());
        for (int i = 0; i < 40; i++) assertFalse(level.needsWaveSpawn(0));
        assertTrue(level.needsWaveSpawn(0), "Only now may the next formation start");
    }

    private void fullBudgetCountsEnemiesAlreadyPresentBeforeTheScriptStarts(int chapter) {
        LevelManager level = new LevelManager(chapter, 42);
        int start = starts(chapter)[0];
        level.enterBattle(level.getBattleAt(start));
        level.popWave();
        Rectangle player = new Rectangle(start + 30, FLOOR - 30, 30, 30);
        for (int tick = 0; tick < 400; tick++) assertEquals(0, level.advanceEncounter(3, FLOOR,
                List.of(), List.of(), player, b -> true, s -> { fail("Existing threats fill the budget"); return true; }));
        assertTrue(level.hasPendingReinforcements());
        assertEquals(1, level.advanceEncounter(2, FLOOR, List.of(), List.of(), player, b -> true, s -> true));
        assertEquals(0, level.advanceEncounter(2, FLOOR, List.of(), List.of(), player, b -> true,
                s -> { fail("One freed slot is not an instant reinforcement burst"); return true; }));
    }

    private void resumedSafeCheckpointSkipsOnlyFinishedArenasAndStartsTheNextOneFromItsFirstBeat(int chapter) {
        int[] starts = starts(chapter);
        for (int index = 0; index < starts.length; index++) {
            LevelManager old = new LevelManager(chapter, 42);
            double checkpoint = old.getBattleAt(starts[index]).end + 1;
            LevelManager resumed = new LevelManager(chapter, 42);
            resumed.restoreThrough(checkpoint);
            for (int prior = 0; prior <= index; prior++) assertNull(resumed.getBattleAt(starts[prior]));
            assertFalse(resumed.hasPendingReinforcements());
            assertTrue(resumed.getPendingTriggers(checkpoint).isEmpty());
            if (index + 1 < starts.length) {
                var next = resumed.getBattleAt(starts[index + 1]);
                assertNotNull(next);
                resumed.enterBattle(next);
                assertEquals(0, resumed.getCurrentWave());
                assertEquals(next.waves[0], resumed.popWave());
                assertTrue(resumed.hasPendingReinforcements());
            }
            LevelManager unfinished = new LevelManager(chapter, 42);
            unfinished.restoreThrough(starts[index] + 10);
            assertNotNull(unfinished.getBattleAt(starts[index]), "Being inside a room is not evidence it was cleared");
        }
    }

    private void practiceSkipCancelsPendingRolesRatherThanSpawningThemAtTheNextRoom(int chapter) {
        int start = starts(chapter)[0];
        for (boolean boss : new boolean[]{false, true}) {
            LevelManager level = new LevelManager(chapter, 42);
            level.enterBattle(level.getBattleAt(start));
            level.popWave();
            assertTrue(level.hasPendingReinforcements());
            if (boss) level.advanceToBossGateForTesting();
            else level.advanceToNextEncounterForTesting(start);
            assertFalse(level.isInBattle());
            assertFalse(level.hasPendingReinforcements());
            for (int tick = 0; tick < 400; tick++) assertEquals(0, level.advanceEncounter(0, FLOOR,
                    List.of(), List.of(), new Rectangle(start + 30, FLOOR - 30, 30, 30), b -> true,
                    s -> { fail("Cancelled formation leaked out of a skipped encounter"); return true; }));
        }
    }

    private static List<ObstacleManager.Enemy> living(ObstacleManager manager) {
        return manager.getEnemies().stream().filter(e -> !e.isDead()).toList();
    }

    private static Rectangle body(ObstacleManager.Enemy enemy) {
        // Burrowed drillers expose only a 12px damage box; placement must still reserve the whole body.
        return new Rectangle((int) enemy.getX(), (int) enemy.getY(), enemy.getType().width, enemy.getType().height);
    }

    private static Predicate<Rectangle> groundSupported(int chapter) {
        if (chapter == 1) return bounds -> true; // The memory chapter has no ChapterRouteController shafts.
        var route = new ChapterRouteController(chapter, FLOOR);
        return bounds -> route.groundFor(bounds.x, bounds.width) == FLOOR;
    }

    private static int[] starts(int chapter) {
        return switch (chapter) {
            case 1 -> new int[]{900, 2300, 4200, 6200};
            case 3 -> new int[]{1700, 4200, 6350};
            case 4 -> new int[]{1850, 5900};
            default -> throw new IllegalArgumentException();
        };
    }

    private static Set<EntityType> family(int chapter) {
        return switch (chapter) {
            case 1 -> Set.of(EntityType.LEAK, EntityType.TECHDEBT, EntityType.LOCK, EntityType.BUG, EntityType.CRASH);
            case 3 -> Set.of(EntityType.DRILLER, EntityType.INTERRUPT, EntityType.SLAG_SPITTER);
            case 4 -> Set.of(EntityType.LURKER, EntityType.MIRROR, EntityType.SPORE_POD);
            default -> throw new IllegalArgumentException();
        };
    }
}
