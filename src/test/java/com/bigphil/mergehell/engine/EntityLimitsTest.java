package com.bigphil.mergehell.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityLimitsTest {
    @Test
    void logicalStoreRejectsNewEntitiesAtCapacity() {
        BoundedEntityStore<Integer> store = BoundedEntityStore.rejectNew(3);
        assertTrue(store.add(1)); assertTrue(store.add(2)); assertTrue(store.add(3));
        assertFalse(store.add(4));
        assertEquals(List.of(1, 2, 3), store.snapshot());
        assertEquals(1, store.rejectedCount());
    }

    @Test
    void visualStoreDropsOldestAtCapacity() {
        BoundedEntityStore<Integer> store = BoundedEntityStore.replaceOldest(2);
        store.add(1); store.add(2); store.add(3);
        assertEquals(List.of(2, 3), store.snapshot());
    }
}
