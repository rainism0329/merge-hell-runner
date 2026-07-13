package com.bigphil.mergehell.engine;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;

public final class BoundedEntityStore<T> {
    private final int capacity;
    private final boolean replaceOldest;
    private final ArrayDeque<T> values = new ArrayDeque<>();
    private int rejectedCount;

    private BoundedEntityStore(int capacity, boolean replaceOldest) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
        this.replaceOldest = replaceOldest;
    }

    public static <T> BoundedEntityStore<T> rejectNew(int capacity) {
        return new BoundedEntityStore<T>(capacity, false);
    }
    public static <T> BoundedEntityStore<T> replaceOldest(int capacity) {
        return new BoundedEntityStore<T>(capacity, true);
    }

    public boolean add(T value) {
        Objects.requireNonNull(value, "value");
        if (values.size() == capacity) {
            if (!replaceOldest) { rejectedCount++; return false; }
            values.removeFirst();
        }
        values.addLast(value);
        return true;
    }

    public List<T> snapshot() { return List.copyOf(values); }
    public int rejectedCount() { return rejectedCount; }
    public int size() { return values.size(); }
}
