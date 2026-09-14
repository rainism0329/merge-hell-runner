package com.bigphil.mergehell.engine;

import com.bigphil.mergehell.model.Projectile;
import com.bigphil.mergehell.model.ProjectileType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ProjectileBudgetTest {
    @Test void refusesAWholeVolleyBeforeConstructionAndNeverEvictsExistingShots() {
        ProjectileBuffer buffer = new ProjectileBuffer(3);
        Projectile existing = shot();
        buffer.add(existing);
        AtomicInteger constructed = new AtomicInteger();
        assertFalse(ProjectileBudget.emit(buffer, 3, () -> {
            constructed.incrementAndGet();
            return List.of(shot(), shot(), shot());
        }));
        assertEquals(0, constructed.get());
        assertEquals(List.of(existing), buffer);
        assertEquals(3, buffer.rejectedProjectiles());
        assertEquals(1, buffer.rejectedVolleys());
        assertTrue(ProjectileBudget.emit(buffer, 2, () -> List.of(shot(), shot())));
        assertEquals(3, buffer.size());
        assertSame(existing, buffer.get(0));
        assertFalse(ProjectileBudget.emitOne(buffer, ProjectileBudgetTest::shot));
        assertEquals(4, buffer.rejectedProjectiles());
    }

    @Test void ordinaryListInsertionThrowsAtCapacityWithoutPartialAddAllOrSilentLoss() {
        ProjectileBuffer buffer = new ProjectileBuffer(2);
        Projectile first = shot();
        assertTrue(buffer.add(first));
        assertThrows(IllegalStateException.class, () -> buffer.addAll(List.of(shot(), shot())));
        assertEquals(List.of(first), buffer);
        buffer.add(0, shot());
        assertThrows(IllegalStateException.class, () -> buffer.add(shot()));
        assertThrows(IllegalStateException.class, () -> buffer.addAll(1, List.of(shot())));
        assertEquals(2, buffer.size());
        assertSame(first, buffer.get(1));
        assertFalse(buffer.addAll(List.of()));
    }

    @Test void iteratorsAndSubListsPreserveRemovalInsertionAndFailFastContracts() {
        ProjectileBuffer buffer = new ProjectileBuffer(3);
        Projectile first = shot(), second = shot(), third = shot();
        buffer.addAll(List.of(first, second, third));
        var iterator = buffer.listIterator();
        assertSame(first, iterator.next());
        assertThrows(IllegalStateException.class, () -> iterator.add(shot()));
        assertSame(second, iterator.next());
        iterator.remove();
        iterator.add(second);
        assertEquals(List.of(first, second, third), buffer);
        var stale = buffer.iterator();
        buffer.subList(1, 3).clear();
        assertEquals(List.of(first), buffer);
        assertThrows(ConcurrentModificationException.class, stale::next);
        var sub = buffer.subList(0, 1);
        sub.add(second);
        assertEquals(List.of(first, second), buffer);
        assertTrue(buffer.removeIf(value -> value == first));
        assertEquals(List.of(second), buffer);
    }

    @Test void invalidInputAndSelfAdditionRemainAtomic() {
        ProjectileBuffer buffer = new ProjectileBuffer(4);
        Projectile first = shot();
        buffer.add(first);
        assertTrue(buffer.addAll(buffer));
        assertEquals(List.of(first, first), buffer);
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.addAll(7, List.of()));
        assertThrows(NullPointerException.class, () -> buffer.set(0, null));
        assertThrows(IllegalArgumentException.class, () -> ProjectileBudget.emit(buffer, -1, List::of));
        assertThrows(IllegalArgumentException.class, () -> ProjectileBudget.emit(buffer, 1, () -> List.of(shot(), shot())));
        assertThrows(NullPointerException.class, () -> ProjectileBudget.emitOne(buffer, () -> null));
        assertEquals(List.of(first, first), buffer);
        assertEquals(0, buffer.rejectedProjectiles());
    }

    @Test void clearKeepsDiagnosticsWhileResetStartsANewInterval() {
        ProjectileBuffer buffer = new ProjectileBuffer(1);
        buffer.add(shot());
        assertFalse(ProjectileBudget.emitOne(buffer, ProjectileBudgetTest::shot));
        buffer.clear();
        assertEquals(1, buffer.remainingCapacity());
        assertEquals(1, buffer.rejectedProjectiles());
        assertEquals(1, buffer.rejectedVolleys());
        buffer.add(shot());
        buffer.reset();
        assertTrue(buffer.isEmpty());
        assertEquals(0, buffer.rejectedProjectiles());
        assertEquals(0, buffer.rejectedVolleys());
    }

    @Test void existingArrayListCallersAndEmptyVolleysRemainSupported() {
        List<Projectile> buffer = new ArrayList<>();
        assertTrue(ProjectileBudget.emitOne(buffer, ProjectileBudgetTest::shot));
        assertTrue(ProjectileBudget.emit(buffer, 2, () -> List.of(shot(), shot())));
        assertEquals(3, buffer.size());
        assertTrue(ProjectileBudget.emit(buffer, 0, () -> { throw new AssertionError("Unneeded construction"); }));
        assertThrows(IllegalArgumentException.class, () -> ProjectileBudget.emit(buffer, 1, List::of));
        assertEquals(3, buffer.size());
    }

    @Test void admissionCheckRecordsDenialAndLeavesSuccessfulChecksFreeOfSideEffects() {
        ProjectileBuffer buffer = new ProjectileBuffer(1);
        assertTrue(ProjectileBudget.canEmit(buffer, 1));
        assertTrue(buffer.isEmpty());
        buffer.add(shot());
        assertFalse(ProjectileBudget.canEmit(buffer, 2));
        assertEquals(2, buffer.rejectedProjectiles());
        assertEquals(1, buffer.rejectedVolleys());
        assertEquals(1, buffer.size());
        assertTrue(ProjectileBudget.canEmit(new ArrayList<>(), 300));
    }

    private static Projectile shot() { return new Projectile(100, 100, 8, 0, ProjectileType.COMMIT); }
}
